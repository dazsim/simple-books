package com.workshopcraft.simplebooks.quest;

import com.workshopcraft.simplebooks.SimpleBooksConfig;
import com.workshopcraft.simplebooks.quest.QuestDefs.Quest;
import com.workshopcraft.simplebooks.quest.QuestDefs.Task;
import com.workshopcraft.simplebooks.quest.QuestEngine.Ref;
import com.workshopcraft.simplebooks.quest.QuestWorld.Prog;
import com.workshopcraft.simplebooks.quest.QuestWorld.Team;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The accept check and counting behind every hand-in box, shared by all item transfer wrappers. */
public final class HandInBoxes {
    public static final String CREDIT = "box";

    /** n items of the offered stack go to this task. */
    public record Alloc(String quest, String task, long n) {
        public String key() {
            return quest + "|" + task;
        }
    }

    /** What a box shows: the task it is feeding and its progress; quest is empty when there is nothing to feed. */
    public record Shown(String quest, String task, long have, long need) {
        public static final Shown NONE = new Shown("", "", 0, 0);
    }

    // team -> open box tasks not yet full, in book order; team -> item -> those of them that want it
    private static final Map<String, List<Ref>> open = new HashMap<>();
    private static final Map<String, Map<Item, List<Ref>>> byItem = new HashMap<>();

    private HandInBoxes() {}

    static void invalidate(String team) {
        open.remove(team);
        byItem.remove(team);
    }

    static void invalidateAll() {
        open.clear();
        byItem.clear();
    }

    public static boolean enabled() {
        return QuestEngine.server() != null && SimpleBooksConfig.get().handInBox();
    }

    public static @Nullable Team team(String id) {
        return id.isEmpty() ? null : QuestEngine.world().teams.get(id);
    }

    private static boolean boxTask(Task t) {
        return t.type().equals("item") && t.consume() && !t.handIn().equals("hand");
    }

    private static long have(Team t, Quest q, Task task) {
        Prog p = QuestEngine.prog(t).get(q.id());
        return p == null ? 0 : p.count(task.id());
    }

    private static List<Ref> open(Team t) {
        return open.computeIfAbsent(t.id, k -> {
            List<Ref> out = new ArrayList<>();
            for (Ref r : QuestEngine.refs("item")) {
                if (!boxTask(r.task())) continue;
                Prog p = QuestEngine.prog(t).get(r.quest().id());
                if (p != null && p.complete()) continue;
                if (!QuestEngine.open(t, r.book(), r.quest())) continue;
                if (have(t, r.quest(), r.task()) >= QuestEngine.target(r.quest(), r.task())) continue;
                out.add(r);
            }
            return out;
        });
    }

    private static List<Ref> wanting(Team t, Item item) {
        List<Ref> all = open(t);
        if (all.isEmpty()) return all;
        return byItem.computeIfAbsent(t.id, k -> new HashMap<>()).computeIfAbsent(item, i -> {
            List<Ref> out = new ArrayList<>();
            for (Ref r : all) if (QuestMc.matchesAny(BuiltInRegistries.ITEM, i, r.task().items())) out.add(r);
            return out;
        });
    }

    /**
     * Where up to amount of stack would go, changing nothing. pending holds what earlier offers in the same
     * transfer already took, keyed by Alloc.key, so one transfer never overfills a task.
     */
    public static List<Alloc> offer(String teamId, String target, ItemStack stack, long amount, @Nullable Map<String, Long> pending) {
        if (amount <= 0 || stack.isEmpty() || !enabled()) return List.of();
        Team t = team(teamId);
        if (t == null) return List.of();
        List<Alloc> out = new ArrayList<>();
        long left = amount;
        for (Ref r : wanting(t, stack.getItem())) {
            if (!target.isEmpty() && !r.quest().id().equals(target)) continue;
            String key = r.quest().id() + "|" + r.task().id();
            long room = QuestEngine.target(r.quest(), r.task()) - have(t, r.quest(), r.task())
                    - (pending == null ? 0 : pending.getOrDefault(key, 0L));
            long n = Math.min(room, left);
            if (n <= 0) continue;
            out.add(new Alloc(r.quest().id(), r.task().id(), n));
            left -= n;
            if (left == 0) break;
        }
        return out;
    }

    public static long total(List<Alloc> allocs) {
        long n = 0;
        for (Alloc a : allocs) n += a.n();
        return n;
    }

    /** Adds what offer allowed to the team's counters, credited to the box; returns how many were counted. */
    public static long credit(String teamId, List<Alloc> allocs) {
        Team t = team(teamId);
        if (t == null || allocs.isEmpty()) return 0;
        long counted = 0;
        boolean full = false;
        for (Alloc a : allocs) {
            Ref r = QuestEngine.find(a.quest());
            Task task = r == null ? null : QuestEngine.task(r.quest(), a.task());
            if (task == null) continue;
            Prog p = QuestEngine.openProg(t, r.book(), r.quest());
            if (p == null) continue;
            long need = QuestEngine.target(r.quest(), task), have = p.count(task.id());
            long n = Math.min(a.n(), need - have);
            if (n <= 0) continue;
            p.tasks.put(task.id(), have + n);
            p.given.computeIfAbsent(task.id(), k -> new java.util.LinkedHashMap<>()).merge(CREDIT, n, Long::sum);
            QuestEngine.tier(t, r.quest(), p);
            counted += n;
            if (have + n >= need) full = true;
        }
        if (full) {
            QuestEngine.changed(t);
            QuestEngine.refresh(t);
        } else {
            QuestEngine.touched(t);
        }
        return counted;
    }

    /** The task a box shows: the first open one of its target quest, or of any quest, else the target's last task. */
    public static Shown shown(String teamId, String target) {
        Team t = team(teamId);
        if (t == null || QuestEngine.server() == null) return Shown.NONE;
        for (Ref r : open(t)) if (target.isEmpty() || r.quest().id().equals(target))
            return new Shown(r.quest().id(), r.task().id(), have(t, r.quest(), r.task()), QuestEngine.target(r.quest(), r.task()));
        if (target.isEmpty()) return Shown.NONE;
        Ref r = QuestEngine.find(target);
        if (r == null) return Shown.NONE;
        Shown last = Shown.NONE;
        for (Task task : r.quest().tasks())
            if (boxTask(task)) last = new Shown(target, task.id(), have(t, r.quest(), task), QuestEngine.target(r.quest(), task));
        return last;
    }

    /** Comparator strength: 0 to 15 by how full the shown task is, 15 when full. */
    public static int signal(Shown s) {
        if (s.need() <= 0 || s.have() <= 0) return 0;
        if (s.have() >= s.need()) return 15;
        return 1 + (int) Math.min(13, s.have() * 14 / s.need());
    }

    /** Quests the box could be pointed at: open quests with a box task, in book order. */
    public static List<Quest> targets(String teamId) {
        Team t = team(teamId);
        List<Quest> out = new ArrayList<>();
        if (t == null) return out;
        for (Ref r : QuestEngine.refs("item")) {
            if (!boxTask(r.task()) || out.contains(r.quest())) continue;
            if (!QuestEngine.open(t, r.book(), r.quest())) continue;
            out.add(r.quest());
        }
        return out;
    }

    /** Sends the box screen: its target and every task it could fill, with progress. */
    public static void screen(ServerPlayer p, BlockPos pos, String teamId, String target) {
        Team t = team(teamId);
        JsonObject o = new JsonObject();
        JsonArray at = new JsonArray();
        at.add(pos.getX());
        at.add(pos.getY());
        at.add(pos.getZ());
        o.add("pos", at);
        o.addProperty("team", t == null ? "" : teamName(t));
        o.addProperty("target", target);
        JsonArray quests = new JsonArray();
        if (t != null) for (Quest q : targets(teamId)) {
            JsonObject jq = new JsonObject();
            jq.addProperty("id", q.id());
            JsonArray tasks = new JsonArray();
            for (Task task : q.tasks()) {
                if (!boxTask(task)) continue;
                JsonObject jt = new JsonObject();
                jt.addProperty("id", task.id());
                jt.addProperty("have", have(t, q, task));
                jt.addProperty("need", QuestEngine.target(q, task));
                tasks.add(jt);
            }
            jq.add("tasks", tasks);
            quests.add(jq);
        }
        o.add("quests", quests);
        QuestEngine.send(p, "box", o);
    }

    public static boolean isTarget(String teamId, String quest) {
        if (quest.isEmpty()) return true;
        for (Quest q : targets(teamId)) if (q.id().equals(quest)) return true;
        return false;
    }

    public static String teamName(Team t) {
        return !t.name.isEmpty() ? t.name : QuestEngine.name(t.owner);
    }

    static long have(String teamId, Quest q, Task task) {
        Team t = team(teamId);
        return t == null ? 0 : have(t, q, task);
    }

    static boolean isBoxTask(Task t) {
        return boxTask(t);
    }
}
