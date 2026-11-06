package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.api.TeamSource;
import com.workshopcraft.simplebooks.GuideItem;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.SimpleBooksConfig;
import com.workshopcraft.simplebooks.api.RewardContext;
import com.workshopcraft.simplebooks.api.RewardSpec;
import com.workshopcraft.simplebooks.api.RewardType;
import com.workshopcraft.simplebooks.api.SimpleBooksApi;
import com.workshopcraft.simplebooks.api.TaskSpec;
import com.workshopcraft.simplebooks.api.TaskType;
import com.workshopcraft.simplebooks.quest.QuestDefs.Book;
import com.workshopcraft.simplebooks.quest.QuestDefs.Chapter;
import com.workshopcraft.simplebooks.quest.QuestDefs.Quest;
import com.workshopcraft.simplebooks.quest.QuestDefs.Reward;
import com.workshopcraft.simplebooks.quest.QuestDefs.Task;
import com.workshopcraft.simplebooks.quest.QuestWorld.Prog;
import com.workshopcraft.simplebooks.quest.QuestWorld.Team;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Runs quests on the server: teams, task counting, completion, claims and the state sent to clients. */
public final class QuestEngine {
    public record Ref(Book book, Quest quest, Task task) {}

    public record Result(boolean ok, String message) {
        static Result ok(String m) {
            return new Result(true, m);
        }

        static Result no(String m) {
            return new Result(false, m);
        }
    }

    public static final ThreadLocal<Boolean> SMELTING = ThreadLocal.withInitial(() -> false);
    public static BiConsumer<ServerPlayer, QuestSyncPacket> sender = (p, x) -> {};
    static final Pattern TEAM_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private static @Nullable MinecraftServer server;
    private static @Nullable QuestData data;
    private static QuestWorld world = new QuestWorld();
    private static Map<String, List<Ref>> byType = Map.of();
    private static Map<String, List<Ref>> byStat = Map.of();
    private static final Map<Task, List<Stat<?>>> statCache = new HashMap<>();
    private static final Set<String> dirty = new HashSet<>();
    // team -> "quest|task" -> member -> items held; not saved
    private static final Map<String, Map<String, Map<String, Long>>> shares = new HashMap<>();
    private static int ticks;

    private QuestEngine() {}

    // ---- lifecycle ----

    public static void start(MinecraftServer s) {
        server = s;
        data = QuestData.get(s);
        world = data.world();
        dirty.clear();
        shares.clear();
        defsChanged();
    }

    public static void stop() {
        server = null;
        data = null;
        world = new QuestWorld();
        dirty.clear();
        shares.clear();
        HandInBoxes.invalidateAll();
    }

    public static @Nullable MinecraftServer server() {
        return server;
    }

    public static QuestWorld world() {
        return world;
    }

    /** New definitions were loaded. */
    public static void defsChanged() {
        Map<String, List<Ref>> types = new HashMap<>(), stats = new HashMap<>();
        for (Book b : defs().books().values())
            for (Quest q : b.quests().values())
                for (Task t : q.tasks()) {
                    Ref r = new Ref(b, q, t);
                    types.computeIfAbsent(t.type(), k -> new ArrayList<>()).add(r);
                    if (t.type().equals("stat") && !t.fromStats())
                        stats.computeIfAbsent(t.stat() + " " + t.value(), k -> new ArrayList<>()).add(r);
                }
        byType = types;
        byStat = stats;
        statCache.clear();
        HandInBoxes.invalidateAll();
        dirty.addAll(world.teams.keySet());
    }

    static QuestDefs defs() {
        return QuestManager.defs();
    }

    static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    static void changed(Team t) {
        touched(t);
        HandInBoxes.invalidate(t.id);
    }

    /** Progress moved without changing what is open or full: send and save, keep the box lookup. */
    static void touched(Team t) {
        dirty.add(t.id);
        if (data != null) data.setDirty();
    }

    static List<Ref> refs(String type) {
        return byType.getOrDefault(type, List.of());
    }

    public static void login(ServerPlayer p) {
        if (server == null) return;
        Team t = team(p);
        String uuid = p.getUUID().toString();
        String book = SimpleBooksConfig.get().firstJoinBook();
        if (!book.isEmpty()) {
            Set<String> given = world.booksGiven.computeIfAbsent(uuid, k -> new LinkedHashSet<>());
            Identifier id = QuestMc.id(book);
            if (id != null && given.add(book)) {
                QuestMc.give(p, GuideItem.of(id));
                data.setDirty();
            }
        }
        refresh(t);
        sendState(p);
        int waiting = unclaimed(p, t);
        if (waiting > 0) toast(p, "rewards", "", waiting);
        changed(t);
    }

    public static void logout(ServerPlayer p) {
        if (server == null) return;
        Team t = world.teamOf(p.getUUID().toString());
        if (t == null) return;
        Map<String, Map<String, Long>> s = shares.get(t.id);
        if (s != null) s.values().forEach(m -> m.remove(p.getUUID().toString()));
        changed(t);
    }

    public static void tick(MinecraftServer s) {
        if (server != s) return;
        if (++ticks % 20 != 0) return;
        Set<Team> teams = new LinkedHashSet<>();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) teams.add(team(p));
        if (defs().quests() > 0) for (Team t : teams) refresh(t);
        flush();
    }

    /** Sends fresh state to the online members of every changed team. */
    public static void flush() {
        if (server == null || dirty.isEmpty()) return;
        Set<String> ids = new HashSet<>(dirty);
        dirty.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Team t = world.teamOf(p.getUUID().toString());
            if (t != null && ids.contains(t.id)) sendState(p);
        }
    }

    // ---- teams ----

    /** True when teams come from a team source (scoreboard or a plugin) instead of our own. */
    static boolean external() {
        return source() != null;
    }

    static @Nullable TeamSource source() {
        String id = SimpleBooksConfig.get().teamSource();
        return id.equals("simplebooks") ? null : com.workshopcraft.simplebooks.api.SimpleBooksApi.teamSource(id);
    }

    /** The player's team, made on first sight. With a team source (scoreboard or a plugin) this follows that source. */
    public static Team team(ServerPlayer p) {
        String uuid = p.getUUID().toString();
        Team cur = world.teamOf(uuid);
        if (!external()) {
            if (cur == null) {
                cur = world.newTeam(UUID.randomUUID().toString(), "", uuid, "simplebooks");
                world.add(cur, uuid, now());
                changed(cur);
            }
            return cur;
        }
        String src = SimpleBooksConfig.get().teamSource();
        TeamSource ts = source();
        String sb = ts.teamOf(p);
        String want = sb == null ? null : src + ":" + sb;
        if (cur != null && (want == null ? !cur.id.contains(":") : cur.id.equals(want))) return cur;
        Team next;
        if (want == null && cur == null) {
            next = world.newTeam(UUID.randomUUID().toString(), "", uuid, src);
            world.add(next, uuid, now());
        } else if (want == null) {
            // left the source team: keep a copy of what it had
            next = world.splitOff(cur, uuid, now(), src);
        } else {
            next = world.teams.get(want);
            if (next == null) {
                next = world.newTeam(want, ts.name(sb), uuid, src);
                if (cur != null) copyProgress(cur, next);
            }
            world.add(next, uuid, now());
            if (cur != null && world.teams.containsKey(cur.id)) changed(cur);
        }
        changed(next);
        return next;
    }

    private static void copyProgress(Team from, Team to) {
        Map<String, Prog> dst = world.progress(to.id);
        world.progress(from.id).forEach((k, v) -> dst.put(k, v.copy()));
        Map<String, String> ex = world.exclusive.get(from.id);
        if (ex != null) world.exclusive.put(to.id, new HashMap<>(ex));
        Map<String, QuestSave.Pool> po = world.pools.get(from.id);
        if (po != null) world.pools.put(to.id, new HashMap<>(po));
    }

    static List<ServerPlayer> online(Team t) {
        List<ServerPlayer> out = new ArrayList<>();
        if (server == null) return out;
        for (String m : t.members) {
            try {
                ServerPlayer p = QuestMc.player(server, UUID.fromString(m));
                if (p != null) out.add(p);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    static String name(String uuid) {
        if (uuid.equals("box")) return "box";
        try {
            return server == null ? uuid : QuestMc.name(server, UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return uuid;
        }
    }

    private static Result externalOnly() {
        String src = SimpleBooksConfig.get().teamSource();
        return Result.no(src.equals("scoreboard") ? "Teams follow scoreboard teams on this server, use /team" : "Teams come from " + src + " on this server");
    }

    public static Result create(ServerPlayer p, String name) {
        if (external()) return externalOnly();
        if (!TEAM_NAME.matcher(name).matches()) return Result.no("Team names are 1 to 32 letters, digits, _ or -");
        if (world.byName(name) != null) return Result.no("There is already a team called " + name);
        Team t = team(p);
        if (t.members.size() > 1) return Result.no("Leave your team first");
        t.name = name;
        changed(t);
        return Result.ok("Created team " + name + ", your progress comes with you");
    }

    public static Result invite(ServerPlayer p, String who) {
        if (external()) return externalOnly();
        Team t = team(p);
        if (t.solo()) return Result.no("Make a team first with /sbteam create <name>");
        if (!t.owner.equals(p.getUUID().toString())) return Result.no("Only the team owner can invite");
        UUID id = QuestMc.byName(server, who);
        if (id == null) return Result.no("No player called " + who);
        if (t.members.contains(id.toString())) return Result.no(who + " is already in the team");
        t.invites.add(id.toString());
        changed(t);
        ServerPlayer o = QuestMc.player(server, id);
        if (o != null) {
            o.sendSystemMessage(Component.literal(p.getScoreboardName() + " invited you to team " + t.name + ", use /sbteam join " + t.name));
            Team ot = world.teamOf(id.toString());
            if (ot != null) changed(ot);
        }
        return Result.ok("Invited " + who);
    }

    public static Result join(ServerPlayer p, String name, boolean confirm) {
        if (external()) return externalOnly();
        Team t = world.byName(name);
        String uuid = p.getUUID().toString();
        if (t == null) return Result.no("No team called " + name);
        Team cur = team(p);
        if (cur == t) return Result.no("You are already in " + t.name);
        if (!t.invites.contains(uuid)) return Result.no("You have no invite from " + t.name);
        if (cur.members.size() == 1 && !world.progress(cur.id).isEmpty() && !confirm)
            return Result.no("Joining swaps your own quest progress for the team's. Run /sbteam join " + t.name + " confirm to go ahead");
        world.add(t, uuid, now());
        if (world.teams.containsKey(cur.id)) changed(cur);
        changed(t);
        for (ServerPlayer o : online(t)) if (o != p) o.sendSystemMessage(Component.literal(p.getScoreboardName() + " joined the team"));
        return Result.ok("Joined " + t.name);
    }

    public static Result leave(ServerPlayer p) {
        if (external()) return externalOnly();
        Team t = team(p);
        if (t.solo()) return Result.no("You are not in a team");
        if (t.members.size() == 1) {
            String was = t.name;
            t.name = "";
            changed(t);
            return Result.ok("You left " + was + " and are on your own now");
        }
        split(t, p.getUUID().toString());
        return Result.ok("You left " + t.name + " and kept a copy of its progress");
    }

    public static Result kick(ServerPlayer p, String who) {
        if (external()) return externalOnly();
        Team t = team(p);
        if (!t.owner.equals(p.getUUID().toString())) return Result.no("Only the team owner can kick");
        String id = member(t, who);
        if (id == null) return Result.no(who + " is not in the team");
        if (id.equals(t.owner)) return Result.no("You can't kick yourself, use /sbteam leave");
        split(t, id);
        ServerPlayer o = QuestMc.player(server, UUID.fromString(id));
        if (o != null) o.sendSystemMessage(Component.literal("You were removed from " + t.name + " and kept a copy of its progress"));
        return Result.ok("Removed " + who);
    }

    public static Result owner(ServerPlayer p, String who) {
        Team t = team(p);
        if (!t.owner.equals(p.getUUID().toString())) return Result.no("Only the team owner can hand over the team");
        return setOwner(t, who);
    }

    public static Result setOwner(Team t, String who) {
        String id = member(t, who);
        if (id == null) return Result.no(who + " is not in the team");
        t.owner = id;
        changed(t);
        return Result.ok(name(id) + " now owns " + (t.name.isEmpty() ? "the team" : t.name));
    }

    private static @Nullable String member(Team t, String who) {
        for (String m : t.members) if (m.equals(who) || name(m).equalsIgnoreCase(who)) return m;
        return null;
    }

    private static void split(Team t, String member) {
        Team n = world.splitOff(t, member, now(), "simplebooks");
        n.name = freeName(name(member));
        if (world.teams.containsKey(t.id)) changed(t);
        changed(n);
    }

    /** The name a new team gets by default: the player's name, with a number added if taken. */
    static String freeName(String base) {
        if (!TEAM_NAME.matcher(base).matches()) base = "team";
        String n = base;
        for (int i = 2; world.byName(n) != null; i++) n = base.substring(0, Math.min(base.length(), 28)) + "_" + i;
        return n;
    }

    public static List<String> info(ServerPlayer p) {
        Team t = team(p);
        List<String> out = new ArrayList<>();
        out.add(t.solo() ? "You are on your own" : "Team " + t.name + (t.id.contains(":") ? " (" + t.id.substring(0, t.id.indexOf(':')) + ")" : ""));
        for (String m : t.members) {
            boolean on = server != null && QuestMc.player(server, UUID.fromString(m)) != null;
            out.add("  " + name(m) + (m.equals(t.owner) ? " (owner)" : "") + (on ? "" : " (offline)"));
        }
        if (!t.invites.isEmpty()) out.add("Invited: " + String.join(", ", t.invites.stream().map(QuestEngine::name).toList()));
        List<String> mine = invitesFor(p.getUUID().toString());
        if (!mine.isEmpty()) out.add("Invites for you: " + String.join(", ", mine));
        return out;
    }

    static List<String> invitesFor(String uuid) {
        List<String> out = new ArrayList<>();
        for (Team t : world.teams.values()) if (t.invites.contains(uuid)) out.add(t.name);
        return out;
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        for (Team t : world.teams.values()) if (!t.solo()) out.add(t.name + " (" + t.members.size() + ")");
        return out;
    }

    // ---- quest state ----

    static Map<String, Prog> prog(Team t) {
        return world.progress(t.id);
    }

    static boolean questComplete(Team t, String quest) {
        return done(prog(t).get(quest));
    }

    /** Done for depends: complete now, an earlier repeat cycle done, or the first tier reached. */
    static boolean done(@Nullable Prog p) {
        return p != null && (p.complete() || p.cycle > 0 || p.tier >= 1);
    }

    /** The newest finished cycle, or -1 if none. */
    static int lastCycle(Prog p) {
        return p.complete() ? p.cycle : p.cycle - 1;
    }

    /** Seconds until a repeat quest opens again; 0 when not waiting, -1 when the limit is used up. */
    static long cooldownLeft(Quest q, Prog p) {
        if (q.repeat() == null || !p.complete()) return 0;
        if (q.repeat().limit() > 0 && p.cycle + 1 >= q.repeat().limit()) return -1;
        return Math.max(0, p.completedAt + q.repeat().cooldown() - now());
    }

    /** A repeat quest whose cooldown ran out starts a new cycle. */
    static boolean reopen(Team t, Quest q, Prog p) {
        if (q.repeat() == null || !p.complete() || cooldownLeft(q, p) != 0) return false;
        p.cycle++;
        p.state = "in_progress";
        p.openAt = now();
        p.tasks.clear();
        p.given.clear();
        Map<String, Map<String, Long>> s = shares.get(t.id);
        if (s != null) s.keySet().removeIf(k -> k.startsWith(q.id() + "|"));
        changed(t);
        if (q.toast().equals("unlock") || q.toast().equals("both"))
            for (ServerPlayer o : online(t)) toast(o, "again", q.id(), 0);
        return true;
    }

    static boolean chapterDone(Team t, Book b, String chapter) {
        for (Quest q : b.quests().values())
            if (chapter.equals(q.chapter()) && !q.optional() && !closed(t, q) && !questComplete(t, q.id())) return false;
        return true;
    }

    static boolean chapterOpen(Team t, Book b, Chapter c) {
        for (String d : c.depends()) {
            if (b.chapters().containsKey(d)) {
                if (!chapterDone(t, b, d)) return false;
            } else if (!questComplete(t, d)) return false;
        }
        return true;
    }

    static boolean depsMet(Team t, Book b, Quest q) {
        return met(t, b, q.depends(), q.dependsMode().equals("any"));
    }

    static boolean met(Team t, Book b, List<String> deps, boolean any) {
        if (deps.isEmpty()) return true;
        for (String d : deps) {
            String base = QuestParser.base(d);
            int tier = 0;
            if (d.contains("#")) {
                try {
                    tier = Integer.parseInt(d.substring(d.indexOf('#') + 1));
                } catch (NumberFormatException ignored) {
                }
            }
            Prog p = b.quests().containsKey(base) ? prog(t).get(base) : null;
            boolean met = tier > 0 ? p != null && (p.complete() || p.tier >= tier) : done(p);
            if (any && met) return true;
            if (!any && !met) return false;
        }
        return !any;
    }

    /** Open for progress: reachable, not closed by a rival path and, for a chosen path, taken. */
    public static boolean open(Team t, Book b, Quest q) {
        return reachable(t, b, q) && !closed(t, q) && !unpicked(t, q);
    }

    /** Chapter, depends and story order allow it, or the board offers it. */
    static boolean reachable(Team t, Book b, Quest q) {
        if (q.pool() != null) return offered(t, q.pool(), q.id()) && boardOpen(t, b, q.pool()) && depsMet(t, b, q);
        if (q.chapter() == null) return false;
        Chapter c = b.chapters().get(q.chapter());
        if (c == null || !chapterOpen(t, b, c) || !depsMet(t, b, q)) return false;
        Quest prev = b.storyPrev(q);
        return prev == null || questComplete(t, prev.id());
    }

    /** open, locked or hidden; hidden only for a chapter with visibility hidden that is not open yet. */
    static String chapterStatus(Team t, Book b, Chapter c) {
        if (chapterOpen(t, b, c)) return "open";
        return c.visibility().equals("hidden") || c.visibility().equals("secret") ? "hidden" : "locked";
    }

    public static String status(Team t, Book b, Quest q) {
        Prog p = prog(t).get(q.id());
        if (p != null && p.complete()) return "complete";
        if (q.pool() != null && !reachable(t, b, q)) return "hidden";
        boolean reach = reachable(t, b, q);
        if (closed(t, q) && (reach || q.visibility().equals("visible") || q.visibility().equals("locked"))) return "closed";
        if (reach) return p != null && p.tasks.values().stream().anyMatch(v -> v > 0) ? "in_progress" : "available";
        return q.visibility().equals("hidden") || q.visibility().equals("secret") ? "hidden" : "locked";
    }

    static long target(Quest q, Task t) {
        if (!q.tiers().isEmpty()) return q.tiers().get(q.tiers().size() - 1).count();
        return Math.max(1, t.count());
    }

    static boolean taskDone(Quest q, Task t, Prog p) {
        return p.count(t.id()) >= target(q, t);
    }

    static boolean questDone(Quest q, Prog p) {
        if (q.tasks().isEmpty()) return true;
        boolean any = q.taskMode().equals("any");
        for (Task t : q.tasks()) {
            boolean d = taskDone(q, t, p);
            if (any && d) return true;
            if (!any && !d) return false;
        }
        return !any;
    }

    static @Nullable Prog openProg(Team t, Book b, Quest q) {
        Map<String, Prog> m = prog(t);
        Prog p = m.get(q.id());
        if (p != null && p.complete()) return null;
        if (!open(t, b, q)) return null;
        if (p == null) {
            p = new Prog("in_progress");
            p.openAt = now();
            m.put(q.id(), p);
            changed(t);
            if (!q.depends().isEmpty() && (q.toast().equals("unlock") || q.toast().equals("both")))
                for (ServerPlayer o : online(t)) toast(o, "unlock", q.id(), 0);
        }
        return p;
    }

    /** Opens what is newly open, polls what can be polled, completes what is done, until nothing changes. */
    public static void refresh(Team t) {
        if (server == null) return;
        List<ServerPlayer> on = online(t);
        for (int guard = 0; guard < 64; guard++) {
            boolean more = false;
            for (Book b : defs().books().values())
                for (QuestDefs.Pool pool : b.pools().values()) more |= roll(t, b, pool, false, Set.of());
            for (Book b : defs().books().values())
                for (Quest q : b.quests().values()) {
                    if (q.repeat() != null && q.pool() == null) {
                        Prog old = prog(t).get(q.id());
                        if (old != null) reopen(t, q, old);
                    }
                    Prog p = openProg(t, b, q);
                    if (p == null) continue;
                    if (!on.isEmpty()) poll(t, b, q, p, on);
                    if (questDone(q, p)) {
                        complete(t, q, p);
                        more = true;
                    }
                }
            if (!more) break;
        }
    }

    private static void set(Team t, Prog p, String task, long v) {
        if (p.count(task) == v) return;
        p.tasks.put(task, v);
        changed(t);
    }

    static void tier(Team t, Quest q, Prog p) {
        if (q.tiers().isEmpty() || q.tasks().isEmpty()) return;
        long c = p.count(q.tasks().get(0).id());
        int n = 0;
        for (var tier : q.tiers()) if (c >= tier.count()) n++;
        if (n > p.tier) {
            int was = p.tier;
            p.tier = n;
            changed(t);
            if (n >= q.tiers().size() || q.toast().equals("none")) return;
            String kind = was == 0 && q.visibility().equals("secret") ? "secret" : "tier";
            for (ServerPlayer o : online(t)) toast(o, kind, q.id(), n);
        }
    }

    private static void poll(Team t, Book b, Quest q, Prog p, List<ServerPlayer> on) {
        for (Task task : q.tasks()) {
            long need = target(q, task), have = p.count(task.id());
            if (have >= need && !task.type().equals("item")) continue;
            switch (task.type()) {
                case "item" -> {
                    if (task.consume()) continue;
                    Map<String, Long> share = new LinkedHashMap<>();
                    long total = 0, max = 0;
                    for (ServerPlayer o : on) {
                        long n = count(o, task.items());
                        share.put(o.getUUID().toString(), n);
                        total += n;
                        max = Math.max(max, n);
                    }
                    Map<String, Map<String, Long>> ts = shares.computeIfAbsent(t.id, k -> new HashMap<>());
                    Map<String, Long> old = ts.get(q.id() + "|" + task.id());
                    if (!share.equals(old)) {
                        ts.put(q.id() + "|" + task.id(), share);
                        dirty.add(t.id);
                    }
                    if (have < need) set(t, p, task.id(), Math.min(task.teamTotal() ? total : max, need));
                }
                case "advancement" -> {
                    for (ServerPlayer o : on) if (QuestMc.advancementDone(o, task.advancement())) set(t, p, task.id(), 1);
                }
                case "dimension" -> {
                    for (ServerPlayer o : on) if (QuestMc.dimension(o).equals(task.dimension())) set(t, p, task.id(), 1);
                }
                case "biome" -> {
                    for (ServerPlayer o : on)
                        if (QuestMc.biomeMatches(QuestMc.level(o).getBiome(o.blockPosition()), List.of(task.biome()))) set(t, p, task.id(), 1);
                }
                default -> {
                    if (task.fromStats()) {
                        long sum = 0;
                        for (ServerPlayer o : on) for (Stat<?> s : stats(task)) sum += o.getStats().getValue(s);
                        if (sum > have) set(t, p, task.id(), Math.min(sum, need));
                    } else if (task.spec() != null) {
                        TaskType type = SimpleBooksApi.taskType(task.type());
                        if (type == null) continue;
                        long sum = -1;
                        for (ServerPlayer o : on) {
                            long v = type.poll(o, task.spec());
                            if (v >= 0) sum = Math.max(sum, 0) + v;
                        }
                        if (sum >= 0) set(t, p, task.id(), Math.min(sum, need));
                    }
                }
            }
        }
        tier(t, q, p);
    }

    static long count(ServerPlayer p, List<String> items) {
        long n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (QuestMc.itemMatches(s, items)) n += s.getCount();
        }
        ItemStack off = inv.getItem(40);
        if (QuestMc.itemMatches(off, items)) n += off.getCount();
        return n;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Stat<?>> stats(Task t) {
        return statCache.computeIfAbsent(t, k -> {
            List<Stat<?>> out = new ArrayList<>();
            switch (k.type()) {
                case "craft", "smelt", "pickup", "use" -> {
                    var type = k.type().equals("pickup") ? Stats.ITEM_PICKED_UP : k.type().equals("use") ? Stats.ITEM_USED : Stats.ITEM_CRAFTED;
                    for (Item i : BuiltInRegistries.ITEM) if (QuestMc.matchesAny(BuiltInRegistries.ITEM, i, k.items())) out.add(type.get(i));
                }
                case "place" -> {
                    for (Item i : BuiltInRegistries.ITEM)
                        if (i instanceof BlockItem bi && QuestMc.matches(BuiltInRegistries.BLOCK, bi.getBlock(), k.block()))
                            out.add(Stats.ITEM_USED.get(i));
                }
                case "break" -> {
                    for (Block b : BuiltInRegistries.BLOCK) if (QuestMc.matches(BuiltInRegistries.BLOCK, b, k.block())) out.add(Stats.BLOCK_MINED.get(b));
                }
                case "kill" -> {
                    for (EntityType<?> e : BuiltInRegistries.ENTITY_TYPE)
                        if (QuestMc.matches((net.minecraft.core.Registry) BuiltInRegistries.ENTITY_TYPE, e, k.entity()))
                            out.add(Stats.ENTITY_KILLED.get(e));
                }
                case "stat" -> {
                    Stat<?> s = QuestMc.stat(k.stat(), k.value());
                    if (s != null) out.add(s);
                }
                default -> {}
            }
            return out;
        });
    }

    private static void complete(Team t, Quest q, Prog p) {
        int was = p.tier;
        p.state = "complete";
        p.completedAt = now();
        if (q.exclusive() != null && q.pool() == null) world.exclusive.computeIfAbsent(t.id, k -> new HashMap<>()).putIfAbsent(q.exclusive().group(), q.id());
        tier(t, q, p);
        changed(t);
        boolean secret = q.visibility().equals("secret") && p.cycle == 0 && was == 0;
        boolean toast = secret ? !q.toast().equals("none") : q.toast().equals("complete") || q.toast().equals("both");
        for (ServerPlayer o : online(t)) if (toast) toast(o, secret ? "secret" : "complete", q.id(), 0);
    }

    static @Nullable Ref find(String quest) {
        for (Book b : defs().books().values()) {
            Quest q = b.quests().get(quest);
            if (q != null) return new Ref(b, q, null);
        }
        return null;
    }

    // ---- paths ----

    static @Nullable String pathOf(Team t, String group) {
        Map<String, String> ex = world.exclusive.get(t.id);
        return ex == null ? null : ex.get(group);
    }

    /** A rival of this quest was taken, or finished first. */
    static boolean closed(Team t, Quest q) {
        if (q.exclusive() == null || q.pool() != null) return false;
        String c = pathOf(t, q.exclusive().group());
        return c != null && !c.equals(q.id());
    }

    /** A "choose" path the owner has not picked yet. */
    static boolean unpicked(Team t, Quest q) {
        return q.exclusive() != null && q.pool() == null && q.exclusive().lockOn().equals("choose") && pathOf(t, q.exclusive().group()) == null;
    }

    public static Result choose(ServerPlayer player, String quest) {
        Ref r = find(quest);
        if (r == null) return Result.no("No quest " + quest);
        Quest q = r.quest();
        if (q.exclusive() == null || q.pool() != null || !q.exclusive().lockOn().equals("choose")) return Result.no("That quest is not a path to pick");
        Team t = team(player);
        if (!t.owner.equals(player.getUUID().toString())) return Result.no("Only the team owner can pick the path");
        String cur = pathOf(t, q.exclusive().group());
        if (cur != null) return Result.no(cur.equals(q.id()) ? "You already took this path" : "Your team already took another path");
        if (!reachable(t, r.book(), q)) return Result.no("That quest is not open yet");
        world.exclusive.computeIfAbsent(t.id, k -> new HashMap<>()).put(q.exclusive().group(), q.id());
        changed(t);
        for (ServerPlayer o : online(t))
            if (o != player) o.sendSystemMessage(Component.literal(player.getScoreboardName() + " took the path ").append(Component.translatable(q.title())));
        refresh(t);
        flush();
        return Result.ok("Took the path " + q.title());
    }

    /** Group ids in the loaded books, for suggestions. */
    public static Set<String> groups() {
        Set<String> out = new java.util.TreeSet<>();
        for (Book b : defs().books().values())
            for (Quest q : b.quests().values()) if (q.exclusive() != null) out.add(q.exclusive().group());
        return out;
    }

    public static boolean adminPathReset(Team t, String group) {
        Map<String, String> ex = world.exclusive.get(t.id);
        if (ex == null || ex.remove(group) == null) return false;
        changed(t);
        refresh(t);
        flush();
        return true;
    }

    // ---- pools ----

    static Map<String, QuestSave.Pool> pools(Team t) {
        return world.pools.computeIfAbsent(t.id, k -> new HashMap<>());
    }

    static List<String> offers(Team t, String pool) {
        QuestSave.Pool s = world.pools.getOrDefault(t.id, Map.of()).get(pool);
        return s == null ? List.of() : s.offers();
    }

    static boolean offered(Team t, String pool, String quest) {
        return offers(t, pool).contains(quest);
    }

    /** The pool's board chapters: open when any is, or when the pool has none. */
    static boolean boardOpen(Team t, Book b, String pool) {
        boolean any = false;
        for (Chapter c : b.chapters().values()) {
            if (!pool.equals(c.pool())) continue;
            if (chapterOpen(t, b, c)) return true;
            any = true;
        }
        return !any;
    }

    static QuestDefs.@Nullable Pool pool(String id) {
        for (Book b : defs().books().values()) {
            QuestDefs.Pool p = b.pools().get(id);
            if (p != null) return p;
        }
        return null;
    }

    static @Nullable Book bookOf(QuestDefs.Pool pool) {
        for (Book b : defs().books().values()) if (b.pools().get(pool.id()) == pool) return b;
        return null;
    }

    /** Seconds until a done offer is replaced; 0 when it is not done, -1 when the pool never refreshes. */
    static long offerLeft(QuestDefs.Pool pool, @Nullable Prog p) {
        if (p == null || !p.complete()) return 0;
        if (pool.refresh() <= 0) return -1;
        return Math.max(0, p.completedAt + pool.refresh() - now());
    }

    /**
     * Keeps a team's offers up to date: drops offers the pool no longer has, replaces done offers whose
     * refresh time ran out and fills empty slots. force replaces every offer. Returns true when anything changed.
     */
    static boolean roll(Team t, Book b, QuestDefs.Pool pool, boolean force, Set<String> avoid) {
        if (!boardOpen(t, b, pool.id())) return false;
        QuestSave.Pool s = pools(t).get(pool.id());
        List<String> offers = s == null ? new ArrayList<>() : new ArrayList<>(s.offers());
        boolean ch = s == null;
        Set<String> gone = new HashSet<>(avoid);
        for (var it = offers.iterator(); it.hasNext(); ) {
            String id = it.next();
            Prog p = prog(t).get(id);
            boolean known = pool.entries().stream().anyMatch(e -> e.quest().equals(id)) && b.quests().containsKey(id);
            if (force || !known || offerLeft(pool, p) == 0 && p != null && p.complete()) {
                it.remove();
                gone.add(id);
                if (p != null && !p.complete()) drop(t, id, p);
                ch = true;
            }
        }
        while (offers.size() < pool.offers()) {
            String pick = pick(t, b, pool, offers, gone);
            if (pick == null) pick = pick(t, b, pool, offers, Set.of());
            if (pick == null) break;
            offers.add(pick);
            fresh(t, pick);
            ch = true;
        }
        if (!ch) return false;
        pools(t).put(pool.id(), new QuestSave.Pool(now(), List.copyOf(offers)));
        changed(t);
        return true;
    }

    /** Unfinished progress on an offer that left the board goes. */
    private static void drop(Team t, String quest, Prog p) {
        if (p.cycle == 0) prog(t).remove(quest);
        else {
            p.tasks.clear();
            p.given.clear();
            p.tier = 0;
        }
        Map<String, Map<String, Long>> sh = shares.get(t.id);
        if (sh != null) sh.keySet().removeIf(k -> k.startsWith(quest + "|"));
    }

    /** A quest coming onto the board starts again; one done before counts as a new cycle so it can be claimed again. */
    private static void fresh(Team t, String quest) {
        Prog p = prog(t).get(quest);
        if (p == null) return;
        if (p.complete()) p.cycle++;
        p.state = "in_progress";
        p.openAt = now();
        p.tasks.clear();
        p.given.clear();
        p.tier = 0;
    }

    private static @Nullable String pick(Team t, Book b, QuestDefs.Pool pool, List<String> taken, Set<String> avoid) {
        List<QuestDefs.PoolEntry> can = new ArrayList<>();
        int total = 0;
        for (QuestDefs.PoolEntry e : pool.entries()) {
            Quest q = b.quests().get(e.quest());
            if (q == null || e.weight() <= 0 || taken.contains(e.quest()) || avoid.contains(e.quest())) continue;
            if (!met(t, b, e.depends(), false) || !depsMet(t, b, q)) continue;
            can.add(e);
            total += e.weight();
        }
        if (total <= 0) return null;
        int r = java.util.concurrent.ThreadLocalRandom.current().nextInt(total);
        for (QuestDefs.PoolEntry e : can) {
            r -= e.weight();
            if (r < 0) return e.quest();
        }
        return null;
    }

    /** Swaps the unfinished offers for new ones, paid for by the player. */
    public static Result reroll(ServerPlayer player, String poolId) {
        QuestDefs.Pool pool = pool(poolId);
        Book b = pool == null ? null : bookOf(pool);
        if (pool == null || b == null) return Result.no("No board " + poolId);
        if (!pool.rerollAllowed()) return Result.no("This board can't be rerolled");
        Team t = team(player);
        if (!boardOpen(t, b, poolId)) return Result.no("That board is not open yet");
        List<String> offers = new ArrayList<>(offers(t, poolId)), keep = new ArrayList<>(), out = new ArrayList<>();
        for (String id : offers) (done(prog(t).get(id)) && prog(t).get(id).complete() ? keep : out).add(id);
        if (out.isEmpty()) return Result.no("Nothing to reroll, every offer is done");
        for (Reward c : pool.rerollCost()) {
            if (c.kind().equals("item") && c.item() != null && count(player, List.of(c.item())) < c.count())
                return Result.no("A reroll costs " + costText(pool));
            if (c.kind().equals("xp_levels") && player.experienceLevel < c.count()) return Result.no("A reroll costs " + costText(pool));
        }
        for (Reward c : pool.rerollCost()) {
            if (c.kind().equals("xp_levels")) player.giveExperienceLevels((int) -c.count());
            if (!c.kind().equals("item") || c.item() == null) continue;
            long left = c.count();
            var inv = player.getInventory();
            for (int i = 0; i < 41 && left > 0; i++) {
                if (i >= 36 && i != 40) continue;
                ItemStack st = inv.getItem(i);
                if (!QuestMc.itemMatches(st, List.of(c.item()))) continue;
                int n = (int) Math.min(st.getCount(), left);
                inv.removeItem(i, n);
                left -= n;
            }
        }
        for (String id : out) {
            Prog p = prog(t).get(id);
            if (p != null) drop(t, id, p);
        }
        pools(t).put(poolId, new QuestSave.Pool(now(), List.copyOf(keep)));
        roll(t, b, pool, false, new HashSet<>(out));
        changed(t);
        refresh(t);
        flush();
        return Result.ok("Rerolled the board");
    }

    static String costText(QuestDefs.Pool pool) {
        List<String> parts = new ArrayList<>();
        for (Reward c : pool.rerollCost()) {
            if (c.kind().equals("item") && c.item() != null) parts.add(c.count() + " " + c.item());
            else if (c.kind().equals("xp_levels")) parts.add(c.count() + " levels");
        }
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }

    /** New offers for a team, free; every current offer goes. */
    public static boolean adminReroll(Team t, String poolId) {
        QuestDefs.Pool pool = pool(poolId);
        Book b = pool == null ? null : bookOf(pool);
        if (pool == null || b == null || !roll(t, b, pool, true, Set.of())) return false;
        refresh(t);
        flush();
        return true;
    }

    public static Set<String> poolIds() {
        Set<String> out = new java.util.TreeSet<>();
        for (Book b : defs().books().values()) out.addAll(b.pools().keySet());
        return out;
    }

    // ---- events ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void stat(ServerPlayer player, Stat<?> stat, int amount) {
        if (server == null || amount <= 0 || byType.isEmpty()) return;
        Object v = stat.getValue();
        var type = stat.getType();
        if (type == Stats.ITEM_CRAFTED) {
            Item i = (Item) v;
            event(player, SMELTING.get() ? "smelt" : "craft", amount, r -> QuestMc.matchesAny(BuiltInRegistries.ITEM, i, r.task().items()));
        } else if (type == Stats.ITEM_PICKED_UP) {
            Item i = (Item) v;
            event(player, "pickup", amount, r -> QuestMc.matchesAny(BuiltInRegistries.ITEM, i, r.task().items()));
        } else if (type == Stats.ITEM_USED) {
            Item i = (Item) v;
            event(player, "use", amount, r -> QuestMc.matchesAny(BuiltInRegistries.ITEM, i, r.task().items()));
            if (i instanceof BlockItem bi)
                event(player, "place", amount, r -> QuestMc.matches(BuiltInRegistries.BLOCK, bi.getBlock(), r.task().block()));
        } else if (type == Stats.BLOCK_MINED) {
            Block b = (Block) v;
            event(player, "break", amount, r -> QuestMc.matches(BuiltInRegistries.BLOCK, b, r.task().block()));
        } else if (type == Stats.ENTITY_KILLED) {
            EntityType<?> e = (EntityType<?>) v;
            event(player, "kill", amount, r -> QuestMc.matches((net.minecraft.core.Registry) BuiltInRegistries.ENTITY_TYPE, e, r.task().entity()));
        }
        if (!byStat.isEmpty()) {
            String[] k = QuestMc.statKey(stat);
            List<Ref> refs = byStat.get(k[0] + " " + k[1]);
            if (refs != null) add(team(player), refs, amount, r -> true);
        }
    }

    private static void event(ServerPlayer player, String type, long amount, Predicate<Ref> filter) {
        List<Ref> refs = byType.get(type);
        if (refs != null) add(team(player), refs, amount, filter);
    }

    private static void add(Team t, List<Ref> refs, long amount, Predicate<Ref> filter) {
        boolean done = false;
        for (Ref r : refs) {
            if (r.task().fromStats() || !filter.test(r)) continue;
            Prog p = openProg(t, r.book(), r.quest());
            if (p == null) continue;
            long need = target(r.quest(), r.task()), have = p.count(r.task().id());
            if (have >= need) continue;
            set(t, p, r.task().id(), Math.min(need, have + amount));
            tier(t, r.quest(), p);
            if (questDone(r.quest(), p)) done = true;
        }
        if (done) refresh(t);
    }

    public static void advancement(ServerPlayer player, String id) {
        if (server == null) return;
        List<Ref> refs = byType.get("advancement");
        if (refs != null) add(team(player), refs, 1, r -> id.equals(r.task().advancement()));
    }

    /** The task API entry point; see SimpleBooksApi.progress. */
    public static void custom(MinecraftServer s, UUID member, String type, long amount, Predicate<TaskSpec> filter) {
        if (server != s || amount <= 0) return;
        List<Ref> refs = byType.get(type);
        if (refs == null) return;
        ServerPlayer p = QuestMc.player(s, member);
        Team t = p != null ? team(p) : world.teamOf(member.toString());
        if (t == null) return;
        add(t, refs, amount, r -> r.task().spec() != null && filter.test(r.task().spec()));
    }

    // ---- player actions ----

    static @Nullable Task task(Quest q, String id) {
        for (Task t : q.tasks()) if (t.id().equals(id)) return t;
        return null;
    }

    public static Result checkbox(ServerPlayer player, String quest, String taskId) {
        Ref r = find(quest);
        if (r == null) return Result.no("No quest " + quest);
        Task task = task(r.quest(), taskId);
        if (task == null || !task.type().equals("checkbox")) return Result.no("No checkbox " + taskId);
        Team t = team(player);
        Prog p = openProg(t, r.book(), r.quest());
        if (p == null) return Result.no("That quest is not open");
        set(t, p, taskId, 1);
        refresh(t);
        flush();
        return Result.ok("");
    }

    /** Takes matching items from the player towards a consume task. */
    public static Result handIn(ServerPlayer player, String quest, String taskId) {
        Ref r = find(quest);
        if (r == null) return Result.no("No quest " + quest);
        Task task = task(r.quest(), taskId);
        if (task == null || !task.type().equals("item") || !task.consume()) return Result.no("Nothing to hand in for " + taskId);
        if (task.handIn().equals("box")) return Result.no("Use a hand-in box for this task");
        Team t = team(player);
        Prog p = openProg(t, r.book(), r.quest());
        if (p == null) return Result.no("That quest is not open");
        long need = target(r.quest(), task) - p.count(taskId);
        if (need <= 0) return Result.no("That task is done");
        long took = 0;
        var inv = player.getInventory();
        for (int i = 0; i < 41 && took < need; i++) {
            if (i >= 36 && i != 40) continue;
            ItemStack s = inv.getItem(i);
            if (!QuestMc.itemMatches(s, task.items())) continue;
            int n = (int) Math.min(s.getCount(), need - took);
            inv.removeItem(i, n);
            took += n;
        }
        if (took == 0) return Result.no("You have none to hand in");
        contribute(t, new Ref(r.book(), r.quest(), task), p, took, player.getUUID().toString());
        return Result.ok("Handed in " + took);
    }

    /** Adds handed-in items to a consume task. contributor is a member uuid, or "box" for a hand-in box. */
    public static long contribute(String teamId, String quest, String taskId, long amount, String contributor) {
        Team t = world.teams.get(teamId);
        Ref r = find(quest);
        if (t == null || r == null || amount <= 0) return 0;
        Task task = task(r.quest(), taskId);
        if (task == null || !task.consume()) return 0;
        Prog p = openProg(t, r.book(), r.quest());
        if (p == null) return 0;
        long room = target(r.quest(), task) - p.count(taskId);
        long n = Math.min(room, amount);
        if (n <= 0) return 0;
        contribute(t, new Ref(r.book(), r.quest(), task), p, n, contributor);
        return n;
    }

    private static void contribute(Team t, Ref r, Prog p, long n, String who) {
        String taskId = r.task().id();
        set(t, p, taskId, p.count(taskId) + n);
        p.given.computeIfAbsent(taskId, k -> new LinkedHashMap<>()).merge(who, n, Long::sum);
        changed(t);
        refresh(t);
        flush();
    }

    public static Result claim(ServerPlayer player, String quest, int choice) {
        Ref r = find(quest);
        if (r == null) return Result.no("No quest " + quest);
        Quest q = r.quest();
        Team t = team(player);
        Prog p = prog(t).get(quest);
        if (p == null || !done(p)) return Result.no("That quest is not complete");
        Map<String, QuestSave.Claim> mine = world.claims(player.getUUID().toString());
        QuestSave.Claim c = mine.get(quest);
        List<Reward> give = new ArrayList<>();
        boolean pick = !q.chooseOne().isEmpty();
        if (!q.tiers().isEmpty()) {
            int next = (c == null ? 0 : c.tier()) + 1;
            if (p.tier < next) return Result.no("Already claimed");
            boolean last = next >= q.tiers().size();
            pick &= last;
            if (pick && (choice < 0 || choice >= q.chooseOne().size())) return Result.no("Pick one reward first");
            give.addAll(q.tiers().get(next - 1).rewards());
            if (last) give.addAll(q.rewards());
            if (pick) give.add(q.chooseOne().get(choice));
            mine.put(quest, new QuestSave.Claim(0, next, pick ? choice : -1));
        } else {
            int last = lastCycle(p);
            if (c != null && c.cycle() == last) return Result.no("Already claimed");
            if (pick && (choice < 0 || choice >= q.chooseOne().size())) return Result.no("Pick one reward first");
            give.addAll(q.rewards());
            if (pick) give.add(q.chooseOne().get(choice));
            mine.put(quest, new QuestSave.Claim(last, 0, pick ? choice : -1));
        }
        changed(t);
        for (Reward w : give) reward(player, t, w);
        flush();
        return Result.ok("Claimed " + q.title());
    }

    static void reward(ServerPlayer p, Team t, Reward w) {
        try {
            if (w.kind().contains(":")) {
                RewardType rt = SimpleBooksApi.rewardType(w.kind());
                RewardSpec spec = w.spec();
                if (rt == null || spec == null) {
                    SimpleBooks.LOG.warn("Quest reward {} skipped for {}: type not available", w.json(), p.getScoreboardName());
                    return;
                }
                rt.grant(new RewardContext(server, p, t.id, t.name), spec);
                return;
            }
            switch (w.kind()) {
                case "item" -> {
                    ItemStack one = QuestMc.stack(server, w.item(), w.data());
                    if (one.isEmpty()) return;
                    long left = w.count();
                    while (left > 0) {
                        int n = (int) Math.min(left, one.getMaxStackSize());
                        ItemStack s = one.copy();
                        s.setCount(n);
                        QuestMc.give(p, s);
                        left -= n;
                    }
                }
                case "xp" -> p.giveExperiencePoints((int) Math.min(Integer.MAX_VALUE, w.count()));
                case "xp_levels" -> p.giveExperienceLevels((int) Math.min(Integer.MAX_VALUE, w.count()));
                case "command" -> {
                    String cmd = w.command();
                    if (cmd.startsWith("/")) cmd = cmd.substring(1);
                    QuestMc.run(server, QuestMc.source(p, SimpleBooksConfig.get().commandRewardPermission()), cmd);
                }
                case "loot_table" -> {
                    for (ItemStack s : QuestMc.loot(p, w.lootTable())) QuestMc.give(p, s);
                }
                default -> {}
            }
        } catch (Exception e) {
            SimpleBooks.LOG.warn("Quest reward {} failed for {}: {}", w.json(), p.getScoreboardName(), e.toString());
        }
    }

    static int unclaimed(ServerPlayer player, Team t) {
        Map<String, QuestSave.Claim> mine = world.claims(player.getUUID().toString());
        int n = 0;
        for (Book b : defs().books().values())
            for (Quest q : b.quests().values()) {
                Prog p = prog(t).get(q.id());
                if (p != null && hasRewards(q) && claimable(q, p, mine.get(q.id()))) n++;
            }
        return n;
    }

    static boolean claimable(Quest q, Prog p, QuestSave.@Nullable Claim c) {
        if (!q.tiers().isEmpty()) return p.tier > (c == null ? 0 : c.tier());
        int last = lastCycle(p);
        return last >= 0 && (c == null || c.cycle() != last);
    }

    static boolean hasRewards(Quest q) {
        return !q.rewards().isEmpty() || !q.chooseOne().isEmpty() || q.tiers().stream().anyMatch(x -> !x.rewards().isEmpty());
    }

    // ---- admin ----

    public static boolean adminComplete(Team t, String quest) {
        Ref r = find(quest);
        if (r == null) return false;
        Map<String, Prog> m = prog(t);
        Prog p = m.computeIfAbsent(quest, k -> new Prog("in_progress"));
        if (p.complete()) return false;
        Quest q = r.quest();
        if (q.exclusive() != null && q.pool() == null) world.exclusive.computeIfAbsent(t.id, k -> new HashMap<>()).putIfAbsent(q.exclusive().group(), q.id());
        for (Task task : r.quest().tasks()) p.tasks.put(task.id(), target(r.quest(), task));
        complete(t, r.quest(), p);
        refresh(t);
        flush();
        return true;
    }

    /** Back to not started: progress and every member's claims for it go. */
    public static boolean adminReset(Team t, String quest) {
        boolean had = prog(t).remove(quest) != null;
        for (String m : t.members) world.claims(m).remove(quest);
        Map<String, Map<String, Long>> s = shares.get(t.id);
        if (s != null) s.keySet().removeIf(k -> k.startsWith(quest + "|"));
        changed(t);
        refresh(t);
        flush();
        return had;
    }

    public static boolean adminUncomplete(Team t, String quest) {
        Prog p = prog(t).get(quest);
        if (p == null || !p.complete() && p.tier == 0) return false;
        p.state = "in_progress";
        p.completedAt = 0;
        p.tasks.clear();
        p.given.clear();
        p.tier = 0;
        changed(t);
        flush();
        return true;
    }

    public static void adminResetClaims(String player, @Nullable String quest) {
        if (quest == null) world.claims(player).clear();
        else world.claims(player).remove(quest);
        Team t = world.teamOf(player);
        if (t != null) changed(t);
        if (data != null) data.setDirty();
        flush();
    }

    public static void adminProgress(Team t, String quest, String task, long value) {
        Ref r = find(quest);
        if (r == null) return;
        Prog p = prog(t).computeIfAbsent(quest, k -> new Prog("in_progress"));
        set(t, p, task, value);
        refresh(t);
        flush();
    }

    /** Drops progress and claims for quests that no longer exist. Returns how many entries went. */
    public static int prune() {
        Set<String> live = new HashSet<>();
        for (Book b : defs().books().values()) live.addAll(b.quests().keySet());
        int n = 0;
        for (Map<String, Prog> m : world.progress.values()) {
            int before = m.size();
            m.keySet().removeIf(k -> !live.contains(k));
            n += before - m.size();
        }
        for (Map<String, QuestSave.Claim> m : world.claims.values()) {
            int before = m.size();
            m.keySet().removeIf(k -> !live.contains(k));
            n += before - m.size();
        }
        if (data != null) data.setDirty();
        dirty.addAll(world.teams.keySet());
        flush();
        return n;
    }

    // ---- sync ----

    static void toast(ServerPlayer p, String kind, String quest, int count) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("quest", quest);
        o.addProperty("count", count);
        send(p, "toast", o);
    }

    public static void openBook(ServerPlayer p, String book, int spread) {
        JsonObject o = new JsonObject();
        o.addProperty("book", book);
        o.addProperty("spread", spread);
        send(p, "open", o);
    }

    static void send(ServerPlayer p, String kind, JsonObject o) {
        if (SimpleBooks.dev && QuestDevBots.isBot(p)) {
            QuestDevBots.got(p, kind, o);
            return;
        }
        sender.accept(p, new QuestSyncPacket(kind, o.toString().getBytes(StandardCharsets.UTF_8)));
    }

    public static void sendState(ServerPlayer p) {
        send(p, "state", state(p));
    }

    static JsonObject state(ServerPlayer player) {
        Team t = team(player);
        String me = player.getUUID().toString();
        JsonObject o = new JsonObject();
        JsonObject team = new JsonObject();
        team.addProperty("id", t.id);
        team.addProperty("name", t.name);
        team.addProperty("owner", name(t.owner));
        team.addProperty("owner_is_me", t.owner.equals(me));
        team.addProperty("source", t.id.contains(":") ? t.id.substring(0, t.id.indexOf(':')) : "simplebooks");
        team.addProperty("mode", SimpleBooksConfig.get().teamSource());
        team.addProperty("solo", t.solo());
        team.addProperty("scoreboard_mode", external());
        JsonArray members = new JsonArray();
        for (String m : t.members) {
            JsonObject mo = new JsonObject();
            mo.addProperty("name", name(m));
            mo.addProperty("online", server != null && QuestMc.player(server, UUID.fromString(m)) != null);
            mo.addProperty("owner", m.equals(t.owner));
            members.add(mo);
        }
        team.add("members", members);
        JsonArray inv = new JsonArray();
        for (String i : t.invites) inv.add(name(i));
        team.add("invites", inv);
        o.add("team", team);
        JsonArray forMe = new JsonArray();
        for (String n : invitesFor(me)) forMe.add(n);
        o.add("invited", forMe);

        JsonObject quests = new JsonObject();
        Map<String, Map<String, Long>> ts = shares.getOrDefault(t.id, Map.of());
        Map<String, QuestSave.Claim> claims = world.claims(me);
        for (Book b : defs().books().values())
            for (Quest q : b.quests().values()) {
                JsonObject qo = new JsonObject();
                String st = status(t, b, q);
                Prog p = prog(t).get(q.id());
                if (q.visibility().equals("secret") && !done(p)) st = "hidden";
                qo.addProperty("s", st);
                if (unpicked(t, q) && !st.equals("complete")) qo.addProperty("pick", true);
                if (q.pool() != null && p != null) {
                    QuestDefs.Pool pool = b.pools().get(q.pool());
                    long left = pool == null || !offered(t, q.pool(), q.id()) ? 0 : offerLeft(pool, p);
                    if (left != 0) qo.addProperty("again", left);
                }
                if (p != null) {
                    JsonObject tasks = new JsonObject();
                    p.tasks.forEach(tasks::addProperty);
                    qo.add("t", tasks);
                    if (p.tier > 0) qo.addProperty("tier", p.tier);
                    if (!p.given.isEmpty()) qo.add("g", byName(p.given));
                    QuestSave.Claim c = claims.get(q.id());
                    if (done(p)) {
                        qo.addProperty("claimed", !claimable(q, p, c));
                        if (c != null && c.choice() >= 0 && !claimable(q, p, c)) qo.addProperty("choice", c.choice());
                    }
                    if (!q.tiers().isEmpty()) qo.addProperty("ct", c == null ? 0 : c.tier());
                    if (q.repeat() != null) {
                        qo.addProperty("runs", lastCycle(p) + 1);
                        long left = cooldownLeft(q, p);
                        if (left != 0) qo.addProperty("again", left);
                    }
                }
                Map<String, Map<String, Long>> sh = new LinkedHashMap<>();
                for (Task task : q.tasks()) {
                    Map<String, Long> m = ts.get(q.id() + "|" + task.id());
                    if (m != null && !m.isEmpty()) sh.put(task.id(), m);
                }
                if (!sh.isEmpty()) qo.add("sh", byName(sh));
                quests.add(q.id(), qo);
            }
        o.add("quests", quests);
        JsonObject chapters = new JsonObject();
        for (Book b : defs().books().values())
            for (Chapter c : b.chapters().values()) chapters.addProperty(c.id(), chapterStatus(t, b, c));
        o.add("chapters", chapters);
        JsonObject boards = new JsonObject();
        for (Book b : defs().books().values())
            for (QuestDefs.Pool pool : b.pools().values()) {
                JsonArray a = new JsonArray();
                for (String id : offers(t, pool.id())) a.add(id);
                boards.add(pool.id(), a);
            }
        o.add("pools", boards);
        JsonObject paths = new JsonObject();
        world.exclusive.getOrDefault(t.id, Map.of()).forEach(paths::addProperty);
        o.add("paths", paths);
        return o;
    }

    private static JsonObject byName(Map<String, Map<String, Long>> m) {
        JsonObject out = new JsonObject();
        m.forEach((task, who) -> {
            JsonObject w = new JsonObject();
            who.forEach((u, n) -> w.addProperty(name(u), n));
            out.add(task, w);
        });
        return out;
    }
}
