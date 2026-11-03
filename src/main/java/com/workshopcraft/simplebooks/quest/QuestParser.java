package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.api.SimpleBooksApi;
import com.workshopcraft.simplebooks.api.TaskProblems;
import com.workshopcraft.simplebooks.api.TaskSpec;
import com.workshopcraft.simplebooks.api.TaskType;
import com.workshopcraft.simplebooks.quest.Problems.Level;
import com.workshopcraft.simplebooks.quest.QuestDefs.*;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads quest books from data files (server) or from the cleaned entries the server sends (client, trusted).
 * Trusted input skips requires, unknown field warnings and the cross checks.
 */
public final class QuestParser {
    public enum Kind { CHAPTER, QUEST, POOL }

    public record Entry(Kind kind, String book, String id, String file, @Nullable JsonElement json, @Nullable String error) {}

    public static final Set<String> TASK_TYPES = Set.of("item", "craft", "smelt", "pickup", "advancement", "place",
            "break", "dimension", "biome", "checkbox", "kill", "use", "stat");
    static final Set<String> COUNTING = Set.of("item", "craft", "smelt", "pickup", "place", "break", "kill", "use", "stat");
    static final Set<String> ITEM_TASKS = Set.of("item", "craft", "smelt", "pickup", "use");
    static final Set<String> STATS_TASKS = Set.of("craft", "smelt", "pickup", "place", "break", "kill", "use", "stat");
    //? if >=1.20.5 {
    static final String DATA_KEY = "components", OTHER_DATA_KEY = "nbt";
    //?} else {
    /*static final String DATA_KEY = "nbt", OTHER_DATA_KEY = "components";
    *///?}
    private static final Pattern ID = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Pattern COOLDOWN = Pattern.compile("(\\d+)([smh])");

    private final boolean trusted;
    private final Problems problems;
    private String book = "", file = "";
    private boolean bad;

    private QuestParser(boolean trusted, Problems problems) {
        this.trusted = trusted;
        this.problems = problems;
    }

    public static QuestDefs parse(List<Entry> entries, boolean trusted, Problems problems) {
        QuestParser p = new QuestParser(trusted, problems);
        Map<String, List<Entry>> byBook = new TreeMap<>();
        for (Entry e : entries) byBook.computeIfAbsent(e.book(), k -> new ArrayList<>()).add(e);
        Map<String, Book> books = new LinkedHashMap<>();
        for (var b : byBook.entrySet()) books.put(b.getKey(), p.book(b.getKey(), b.getValue()));
        return new QuestDefs(Collections.unmodifiableMap(books));
    }

    // ---- per book ----

    private static final class CB {
        Entry e; JsonObject out; String title, icon, layout, visibility, text; @Nullable String pool; int sort;
        List<String> depends = new ArrayList<>();
    }

    private static final class QB {
        Entry e; JsonObject out; @Nullable String chapter, pool; int sort; boolean hasSort; int @Nullable [] pos;
        String title, subtitle, icon, text, dependsMode, visibility, taskMode, toast; @Nullable JsonObject page;
        boolean optional; List<String> depends = new ArrayList<>(); List<Task> tasks = new ArrayList<>();
        List<Reward> rewards = List.of(), chooseOne = List.of(); @Nullable Repeat repeat; List<Tier> tiers = new ArrayList<>();
        @Nullable Exclusive exclusive;
    }

    private static final class PB {
        Entry e; JsonObject out; int offers; long refresh; boolean reroll; List<Reward> cost = List.of();
        List<String[]> entries = new ArrayList<>(); // quest ref, weight, depends joined by \n
        List<Integer> weights = new ArrayList<>();
        List<List<String>> deps = new ArrayList<>();
    }

    private Book book(String bookId, List<Entry> entries) {
        book = bookId;
        Map<String, Entry> chapterFiles = new HashMap<>(), questFiles = new HashMap<>(), poolFiles = new HashMap<>();
        for (Entry e : entries) (e.kind() == Kind.CHAPTER ? chapterFiles : e.kind() == Kind.QUEST ? questFiles : poolFiles).put(e.id(), e);
        Set<String> skipped = new HashSet<>();
        Map<String, String> broken = new TreeMap<>();
        Map<String, CB> chapters = new TreeMap<>();
        Map<String, PB> pools = new TreeMap<>();
        Map<String, QB> quests = new TreeMap<>();

        for (Entry e : sorted(chapterFiles)) {
            JsonObject o = open(e, skipped);
            if (o == null) continue;
            CB c = chapter(e, o);
            if (c != null) chapters.put(e.id(), c);
        }
        for (Entry e : sorted(poolFiles)) {
            JsonObject o = open(e, skipped);
            if (o == null) continue;
            PB pb = pool(e, o);
            if (pb != null) pools.put(e.id(), pb);
        }
        for (Entry e : sorted(questFiles)) {
            JsonObject o = open(e, skipped);
            if (o == null) {
                if (!skipped.contains(e.id())) broken.put(e.id(), trusted && e.json() == null ? String.valueOf(e.error()) : "unreadable file");
                continue;
            }
            QB q = quest(e, o, skipped);
            if (q == null) {
                if (!skipped.contains(e.id())) broken.put(e.id(), "broken, see the log");
                continue;
            }
            file = e.file();
            if (q.pool != null) {
                String pid = resolve(q.pool, e.id(), poolFiles.keySet());
                if (pid != null && skipped.contains(pid)) {
                    info("/pool", "pool " + q.pool + " is skipped, so is this quest");
                    skipped.add(e.id());
                    continue;
                }
                if (pid == null || !pools.containsKey(pid)) {
                    error("/pool", pid == null ? "no pool " + q.pool : "pool " + q.pool + " is broken");
                    broken.put(e.id(), "bad pool");
                    continue;
                }
                q.pool = pid;
                q.out.addProperty("pool", pid);
                if (q.chapter != null) {
                    warn("/chapter", "ignored, pool quests are only shown on the board");
                    q.chapter = null;
                    q.out.remove("chapter");
                }
            } else if (q.chapter == null) {
                error("", "chapter is required (or pool)");
                broken.put(e.id(), "no chapter");
                continue;
            } else {
                String cid = resolve(q.chapter, e.id(), chapterFiles.keySet());
                if (cid != null && skipped.contains(cid)) {
                    info("/chapter", "chapter " + q.chapter + " is skipped, so is this quest");
                    skipped.add(e.id());
                    continue;
                }
                if (cid == null || !chapters.containsKey(cid)) {
                    error("/chapter", cid == null ? "no chapter " + q.chapter : "chapter " + q.chapter + " is broken");
                    broken.put(e.id(), "bad chapter");
                    continue;
                }
                q.chapter = cid;
                q.out.addProperty("chapter", cid);
            }
            quests.put(e.id(), q);
        }

        // quest depends
        for (QB q : new ArrayList<>(quests.values())) {
            file = q.e.file();
            List<String> deps = new ArrayList<>();
            boolean ok = true;
            for (int i = 0; i < q.depends.size(); i++) {
                String d = dep(q.depends.get(i), q.e.id(), questFiles.keySet(), skipped, quests, "/depends/" + i);
                if (d == null) ok = false;
                else if (!d.isEmpty()) deps.add(d);
            }
            if (!ok) {
                quests.remove(q.e.id());
                broken.put(q.e.id(), "bad depends");
                continue;
            }
            q.depends = deps;
            JsonArray a = new JsonArray();
            deps.forEach(a::add);
            q.out.add("depends", a);
        }
        // loops
        if (!trusted) {
            Map<String, List<String>> graph = new HashMap<>();
            for (QB q : quests.values()) graph.put(q.e.id(), q.depends.stream().map(QuestParser::base).toList());
            for (List<String> loop : loops(graph)) for (String id : loop) {
                QB q = quests.remove(id);
                if (q == null) continue;
                file = q.e.file();
                error("/depends", "dependency loop: " + String.join(" -> ", loop));
                broken.put(id, "dependency loop");
            }
        }

        // chapter depends and board pools
        for (CB c : chapters.values()) {
            file = c.e.file();
            List<String> deps = new ArrayList<>();
            for (int i = 0; i < c.depends.size(); i++) {
                String ref = c.depends.get(i);
                String id = resolve(ref, c.e.id(), chapterFiles.keySet());
                if (id == null) id = resolve(ref, c.e.id(), questFiles.keySet());
                if (id != null && skipped.contains(id)) {
                    info("/depends/" + i, ref + " is skipped, counted as met");
                    continue;
                }
                if (id == null) {
                    warn("/depends/" + i, "no chapter or quest " + ref + ", the chapter can never unlock");
                    id = ref;
                }
                deps.add(id);
            }
            c.depends = deps;
            JsonArray a = new JsonArray();
            deps.forEach(a::add);
            c.out.add("depends", a);
            if (c.layout.equals("board")) {
                String ref = c.pool != null ? c.pool : c.e.id().substring(bookId.length() + 1);
                String pid = resolve(ref, c.e.id(), poolFiles.keySet());
                if (pid == null || !pools.containsKey(pid)) {
                    if (!trusted && (pid == null || !skipped.contains(pid))) warn("/pool", "board chapter has no pool " + ref);
                    c.pool = null;
                } else c.pool = pid;
                if (c.pool != null) c.out.addProperty("pool", c.pool);
                else c.out.remove("pool");
            } else if (c.pool != null) {
                warn("/pool", "only used by board chapters");
            }
        }

        // pool entries
        Map<String, Pool> poolDefs = new TreeMap<>();
        for (PB pb : pools.values()) {
            file = pb.e.file();
            List<PoolEntry> list = new ArrayList<>();
            JsonArray arr = new JsonArray();
            for (int i = 0; i < pb.entries.size(); i++) {
                String ptr = "/quests/" + i;
                String ref = pb.entries.get(i)[0];
                String id = resolve(ref, pb.e.id(), questFiles.keySet());
                if (id != null && skipped.contains(id)) continue;
                QB q = id == null ? null : quests.get(id);
                if (q == null) {
                    warn(ptr + "/quest", (id == null ? "no quest " : "broken quest ") + ref + ", entry ignored");
                    continue;
                }
                if (!pb.e.id().equals(q.pool)) warn(ptr + "/quest", ref + " does not have \"pool\" set to this pool");
                List<String> deps = new ArrayList<>();
                for (int j = 0; j < pb.deps.get(i).size(); j++) {
                    String d = dep(pb.deps.get(i).get(j), pb.e.id(), questFiles.keySet(), skipped, quests, ptr + "/depends/" + j);
                    if (d != null && !d.isEmpty()) deps.add(d);
                }
                list.add(new PoolEntry(id, pb.weights.get(i), List.copyOf(deps)));
                JsonObject eo = new JsonObject();
                eo.addProperty("quest", id);
                eo.addProperty("weight", pb.weights.get(i));
                JsonArray da = new JsonArray();
                deps.forEach(da::add);
                eo.add("depends", da);
                arr.add(eo);
            }
            pb.out.add("quests", arr);
            poolDefs.put(pb.e.id(), new Pool(pb.e.id(), pb.offers, pb.refresh, pb.reroll, pb.cost, List.copyOf(list), pb.out));
        }

        if (!trusted) checks(chapters, quests, poolDefs, broken);

        Map<String, Chapter> chapterDefs = new TreeMap<>();
        for (CB c : chapters.values())
            chapterDefs.put(c.e.id(), new Chapter(c.e.id(), c.title, c.icon, c.sort, c.layout, List.copyOf(c.depends), c.visibility, c.text, c.pool, c.out));
        Map<String, Quest> questDefs = new TreeMap<>();
        for (QB q : quests.values())
            questDefs.put(q.e.id(), new Quest(q.e.id(), q.chapter, q.pool, q.sort, q.pos, q.title, q.subtitle, q.icon, q.text, q.page,
                    List.copyOf(q.depends), q.dependsMode, q.visibility, q.optional, List.copyOf(q.tasks), q.taskMode,
                    q.rewards, q.chooseOne, q.repeat, List.copyOf(q.tiers), q.exclusive, q.toast, q.out));
        if (!trusted && problems.logging()) {
            int n = questDefs.size();
            SimpleBooks.LOG.info("Quest book {}: {} chapters, {} quests, {} pools, {} broken, {} skipped", bookId,
                    chapterDefs.size(), n, poolDefs.size(), broken.size(), skipped.size());
        }
        return new Book(bookId, Collections.unmodifiableMap(chapterDefs), Collections.unmodifiableMap(questDefs),
                Collections.unmodifiableMap(poolDefs), Collections.unmodifiableMap(broken));
    }

    private void checks(Map<String, CB> chapters, Map<String, QB> quests, Map<String, Pool> pools, Map<String, String> broken) {
        // never unlock, optional depends, exclusive rivals
        Set<String> never = new HashSet<>(broken.keySet());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (QB q : quests.values()) {
                if (never.contains(q.e.id()) || q.depends.isEmpty()) continue;
                long dead = q.depends.stream().filter(d -> never.contains(base(d))).count();
                if (q.dependsMode.equals("all") ? dead > 0 : dead == q.depends.size()) {
                    never.add(q.e.id());
                    changed = true;
                    file = q.e.file();
                    warn("/depends", "can never unlock, it needs a broken or locked out quest");
                }
            }
        }
        for (QB q : quests.values()) {
            file = q.e.file();
            Map<String, String> groups = new HashMap<>();
            for (int i = 0; i < q.depends.size(); i++) {
                QB t = quests.get(base(q.depends.get(i)));
                if (t == null) continue;
                if (t.optional) warn("/depends/" + i, t.e.id() + " is optional");
                if (t.exclusive != null && q.dependsMode.equals("all")) {
                    String other = groups.put(t.exclusive.group(), t.e.id());
                    if (other != null) warn("/depends", "needs both " + other + " and " + t.e.id() + " from exclusive group " + t.exclusive.group() + ", can never unlock");
                }
            }
        }
        Map<String, List<QB>> groups = new TreeMap<>();
        for (QB q : quests.values()) if (q.exclusive != null) groups.computeIfAbsent(q.exclusive.group(), k -> new ArrayList<>()).add(q);
        for (var g : groups.entrySet()) if (g.getValue().size() == 1) {
            file = g.getValue().get(0).e.file();
            warn("/exclusive/group", "group " + g.getKey() + " has only this quest");
        }
        // pool quests not in their pool
        for (QB q : quests.values()) {
            if (q.pool == null) continue;
            Pool p = pools.get(q.pool);
            if (p != null && p.entries().stream().noneMatch(en -> en.quest().equals(q.e.id()))) {
                file = q.e.file();
                warn("/pool", "not listed in " + q.pool + ", it will never be offered");
            }
        }
        // layouts
        for (CB c : chapters.values()) {
            List<QB> in = quests.values().stream().filter(q -> c.e.id().equals(q.chapter)).toList();
            if (c.layout.equals("tree")) {
                Map<String, String> seen = new HashMap<>();
                for (QB q : in) {
                    if (q.pos == null) continue;
                    String other = seen.put(q.pos[0] + "," + q.pos[1], q.e.id());
                    if (other != null) {
                        file = q.e.file();
                        warn("/pos", "same position as " + other);
                    }
                }
            } else if (c.layout.equals("story")) {
                Map<Integer, String> seen = new HashMap<>();
                for (QB q : in) {
                    file = q.e.file();
                    if (!q.hasSort) warn("/sort", "story chapter " + c.e.id() + " needs a sort on every quest");
                    else {
                        String other = seen.put(q.sort, q.e.id());
                        if (other != null) warn("/sort", "same sort as " + other + " in story chapter " + c.e.id());
                    }
                }
            } else if (c.layout.equals("board") && !in.isEmpty()) {
                file = c.e.file();
                warn("/layout", "board chapters show pool quests, " + in.size() + " quests with chapter set here are not shown");
            }
        }
    }

    /** Returns the full dependency id (with #tier), "" when skipped, null when missing. */
    private @Nullable String dep(String raw, String from, Set<String> files, Set<String> skipped, Map<String, QB> quests, String ptr) {
        String ref = raw;
        int tier = 0;
        int hash = raw.indexOf('#');
        if (hash >= 0) {
            ref = raw.substring(0, hash);
            try {
                tier = Integer.parseInt(raw.substring(hash + 1));
            } catch (NumberFormatException ex) {
                tier = -1;
            }
            if (tier < 1) {
                error(ptr, "bad tier in " + raw + ", use name#2");
                return null;
            }
        }
        String id = resolve(ref, from, files);
        if (id != null && skipped.contains(id)) {
            info(ptr, ref + " is skipped, counted as met");
            return "";
        }
        if (id == null) {
            if (trusted) return raw;
            error(ptr, "no quest " + ref);
            return null;
        }
        QB t = quests.get(id);
        if (tier > 0 && t != null && t.tiers.size() < tier) {
            error(ptr, id + " has " + t.tiers.size() + " tiers, not " + tier);
            return null;
        }
        return tier > 0 ? id + "#" + tier : id;
    }

    static String base(String dep) {
        int hash = dep.indexOf('#');
        return hash < 0 ? dep : dep.substring(0, hash);
    }

    /** Full ids stay as they are; short refs try the referring file's folder, then the book root. */
    private @Nullable String resolve(String ref, String from, Set<String> ids) {
        if (ref.contains(":")) return ids.contains(ref) ? ref : null;
        String rest = from.substring(book.length() + 1);
        int slash = rest.lastIndexOf('/');
        if (slash >= 0) {
            String near = book + "/" + rest.substring(0, slash) + "/" + ref;
            if (ids.contains(near)) return near;
        }
        String root = book + "/" + ref;
        return ids.contains(root) ? root : null;
    }

    private static List<List<String>> loops(Map<String, List<String>> graph) {
        List<List<String>> out = new ArrayList<>();
        Map<String, Integer> state = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        for (String start : new TreeSet<>(graph.keySet())) visit(start, graph, state, stack, out);
        return out;
    }

    private static void visit(String n, Map<String, List<String>> g, Map<String, Integer> state, Deque<String> stack, List<List<String>> out) {
        Integer s = state.get(n);
        if (s != null) {
            if (s == 1) {
                List<String> loop = new ArrayList<>();
                boolean on = false;
                for (Iterator<String> it = stack.descendingIterator(); it.hasNext(); ) {
                    String x = it.next();
                    if (x.equals(n)) on = true;
                    if (on) loop.add(x);
                }
                loop.add(n);
                out.add(loop);
            }
            return;
        }
        if (!g.containsKey(n)) return;
        state.put(n, 1);
        stack.push(n);
        for (String d : g.get(n)) visit(d, g, state, stack, out);
        stack.pop();
        state.put(n, 2);
    }

    // ---- files ----

    private static List<Entry> sorted(Map<String, Entry> m) {
        return new TreeMap<>(m).values().stream().toList();
    }

    /** The file as an object, or null when skipped (recorded in skipped) or unreadable. */
    private @Nullable JsonObject open(Entry e, Set<String> skipped) {
        file = e.file();
        if (e.json() == null) {
            if (!trusted) error("", "bad JSON: " + e.error());
            return null;
        }
        if (!e.json().isJsonObject()) {
            error("", "must be a JSON object");
            return null;
        }
        JsonObject o = e.json().getAsJsonObject();
        if (o.has("disabled")) {
            JsonElement d = o.get("disabled");
            if (!d.isJsonPrimitive() || !d.getAsJsonPrimitive().isBoolean()) error("/disabled", "must be true or false");
            else if (d.getAsBoolean()) {
                info("", "disabled");
                skipped.add(e.id());
                return null;
            }
        }
        if (!requiresMet(o, "")) {
            info("", "skipped, needs " + o.get("requires"));
            skipped.add(e.id());
            return null;
        }
        return o;
    }

    private boolean requiresMet(JsonObject o, String ptr) {
        if (trusted || !o.has("requires")) return true;
        JsonElement r = o.get("requires");
        List<String> mods = new ArrayList<>();
        if (r.isJsonPrimitive()) mods.add(r.getAsString());
        else if (r.isJsonArray()) for (JsonElement m : r.getAsJsonArray()) {
            if (m.isJsonPrimitive()) mods.add(m.getAsString());
            else error(ptr + "/requires", "must be mod ids");
        }
        else error(ptr + "/requires", "must be a mod id or a list of them");
        for (String m : mods) if (!SimpleBooks.modLoaded.test(m)) return false;
        return true;
    }

    private @Nullable CB chapter(Entry e, JsonObject o) {
        bad = false;
        R r = new R(o, "");
        CB c = new CB();
        c.e = e;
        c.title = r.str("title", "");
        c.icon = r.str("icon", "");
        if (!c.icon.isEmpty()) checkId(c.icon, "/icon", false);
        c.sort = r.integer("sort", 0);
        c.layout = r.oneOf("layout", "checklist", "checklist", "tree", "story", "board");
        c.depends = r.strs("depends");
        c.visibility = r.oneOf("visibility", "locked", "visible", "locked", "hidden");
        c.text = r.str("text", "");
        c.pool = r.opt("pool");
        r.skip("requires", "disabled");
        r.finish();
        if (bad) return null;
        c.out = clean(o);
        return c;
    }

    private @Nullable PB pool(Entry e, JsonObject o) {
        bad = false;
        R r = new R(o, "");
        PB p = new PB();
        p.e = e;
        p.offers = r.integer("offers", 3);
        if (p.offers < 1) error("/offers", "must be at least 1");
        JsonObject refresh = r.obj("refresh");
        if (refresh != null) {
            R rr = new R(refresh, "/refresh");
            p.refresh = cooldown(rr.str("cooldown", ""), "/refresh/cooldown");
            rr.finish();
        }
        JsonObject reroll = r.obj("reroll");
        JsonObject out = clean(o);
        if (reroll != null) {
            R rr = new R(reroll, "/reroll");
            p.reroll = rr.bool("allowed", false);
            JsonArray cost = rr.arr("cost");
            JsonArray costOut = new JsonArray();
            if (cost != null) p.cost = rewards(cost, "/reroll/cost", costOut);
            rr.finish();
            out.getAsJsonObject("reroll").add("cost", costOut);
        }
        JsonArray qs = r.arr("quests");
        if (qs == null || qs.isEmpty()) warn("/quests", "pool has no quests");
        else for (int i = 0; i < qs.size(); i++) {
            String ptr = "/quests/" + i;
            if (!qs.get(i).isJsonObject()) {
                error(ptr, "must be an object");
                continue;
            }
            JsonObject eo = qs.get(i).getAsJsonObject();
            if (!requiresMet(eo, ptr)) continue;
            R er = new R(eo, ptr);
            String q = er.str("quest", "");
            if (q.isEmpty()) error(ptr + "/quest", "missing");
            int w = er.integer("weight", 1);
            if (w < 1) error(ptr + "/weight", "must be at least 1");
            List<String> deps = er.strs("depends");
            er.skip("requires");
            er.finish();
            p.entries.add(new String[]{q});
            p.weights.add(w);
            p.deps.add(deps);
        }
        r.skip("requires", "disabled");
        r.finish();
        if (bad) return null;
        p.out = out;
        return p;
    }

    private @Nullable QB quest(Entry e, JsonObject o, Set<String> skipped) {
        bad = false;
        R r = new R(o, "");
        QB q = new QB();
        q.e = e;
        q.chapter = r.opt("chapter");
        q.pool = r.opt("pool");
        q.hasSort = o.has("sort");
        q.sort = r.integer("sort", 0);
        JsonArray pos = r.arr("pos");
        if (pos != null) {
            if (pos.size() != 2 || !num(pos.get(0)) || !num(pos.get(1))) error("/pos", "must be [column, row]");
            else q.pos = new int[]{pos.get(0).getAsInt(), pos.get(1).getAsInt()};
        }
        q.title = r.str("title", "");
        q.subtitle = r.str("subtitle", "");
        q.icon = r.str("icon", "");
        if (!q.icon.isEmpty()) checkId(q.icon, "/icon", false);
        q.text = r.str("text", "");
        q.page = r.obj("page");
        q.depends = r.strs("depends");
        q.dependsMode = r.oneOf("depends_mode", "all", "all", "any");
        q.visibility = r.oneOf("visibility", "locked", "visible", "locked", "hidden", "secret");
        q.optional = r.bool("optional", false);
        q.taskMode = r.oneOf("task_mode", "all", "all", "any");
        q.toast = r.oneOf("toast", "complete", "complete", "unlock", "both", "none");
        JsonObject out = clean(o);

        JsonArray tasks = r.arr("tasks");
        JsonArray tasksOut = new JsonArray();
        Set<String> ids = new HashSet<>();
        if (tasks != null) for (int i = 0; i < tasks.size(); i++) {
            String ptr = "/tasks/" + i;
            if (!tasks.get(i).isJsonObject()) {
                error(ptr, "must be an object");
                continue;
            }
            JsonObject to = tasks.get(i).getAsJsonObject();
            if (!requiresMet(to, ptr)) continue;
            String type = to.has("type") && to.get("type").isJsonPrimitive() ? to.get("type").getAsString() : "";
            if (type.contains(":") && !trusted) {
                String ns = type.substring(0, type.indexOf(':'));
                if (!ns.equals("minecraft") && !ns.equals(SimpleBooks.MOD_ID) && !SimpleBooks.modLoaded.test(ns)) {
                    file = e.file();
                    info(ptr + "/type", "task type " + type + " is from " + ns + ", which is not loaded; quest skipped");
                    skipped.add(e.id());
                    return null;
                }
            }
            Task t = task(to, ptr, String.valueOf(i));
            if (t == null) continue;
            if (!ids.add(t.id())) error(ptr + "/id", "duplicate task id " + t.id());
            q.tasks.add(t);
            tasksOut.add(t.json());
        }
        out.add("tasks", tasksOut);
        if (q.tasks.isEmpty() && !trusted) warn("/tasks", "no tasks, done as soon as it unlocks");

        JsonArray rw = r.arr("rewards"), rwOut = new JsonArray();
        if (rw != null) q.rewards = rewards(rw, "/rewards", rwOut);
        out.add("rewards", rwOut);
        JsonArray c1 = r.arr("choose_one"), c1Out = new JsonArray();
        if (c1 != null) q.chooseOne = rewards(c1, "/choose_one", c1Out);
        out.add("choose_one", c1Out);

        JsonObject rep = r.obj("repeat");
        if (rep != null) {
            R rr = new R(rep, "/repeat");
            long cd = cooldown(rr.str("cooldown", ""), "/repeat/cooldown");
            int limit = rr.integer("limit", 0);
            if (limit < 0) error("/repeat/limit", "must be 0 (no limit) or more");
            rr.finish();
            q.repeat = new Repeat(cd, limit);
        }

        JsonArray tiers = r.arr("tiers");
        if (tiers != null) {
            JsonArray tiersOut = new JsonArray();
            long last = 0;
            for (int i = 0; i < tiers.size(); i++) {
                String ptr = "/tiers/" + i;
                if (!tiers.get(i).isJsonObject()) {
                    error(ptr, "must be an object");
                    continue;
                }
                JsonObject tio = tiers.get(i).getAsJsonObject();
                R tr = new R(tio, ptr);
                long count = tr.lng("count", 0);
                if (count < 1) error(ptr + "/count", "must be at least 1");
                else if (count <= last) warn(ptr + "/count", "tier counts should go up");
                last = Math.max(last, count);
                JsonArray trw = tr.arr("rewards"), trwOut = new JsonArray();
                List<Reward> list = trw == null ? List.of() : rewards(trw, ptr + "/rewards", trwOut);
                String title = tr.opt("title");
                tr.finish();
                q.tiers.add(new Tier(count, list, title));
                JsonObject tOut = tio.deepCopy();
                tOut.add("rewards", trwOut);
                tiersOut.add(tOut);
            }
            out.add("tiers", tiersOut);
            if (!q.tiers.isEmpty() && (q.tasks.size() != 1 || !counting(q.tasks.get(0).type())))
                error("/tiers", "tiers need exactly one counting task");
            if (!q.tiers.isEmpty() && q.tasks.size() == 1 && q.tasks.get(0).json().has("count") && !trusted)
                warn("/tasks/0/count", "ignored, the tier counts are used");
        }

        JsonObject ex = r.obj("exclusive");
        if (ex != null) {
            R er = new R(ex, "/exclusive");
            String g = er.str("group", "");
            if (g.isEmpty()) error("/exclusive/group", "missing");
            String lock = er.oneOf("lock_on", "choose", "choose", "complete");
            er.finish();
            String full = g.contains(":") ? g : book + "/" + g;
            q.exclusive = new Exclusive(full, lock);
            out.getAsJsonObject("exclusive").addProperty("group", full);
        }
        r.skip("requires", "disabled");
        r.finish();
        if (bad) return null;
        q.out = out;
        return q;
    }

    private static boolean counting(String type) {
        return COUNTING.contains(type) || type.contains(":");
    }

    private @Nullable Task task(JsonObject o, String ptr, String index) {
        R r = new R(o, ptr);
        String id = r.str("id", index);
        String type = r.str("type", "");
        boolean custom = type.contains(":");
        if (type.isEmpty()) {
            error(ptr + "/type", "missing");
            return null;
        }
        if (!custom && !TASK_TYPES.contains(type)) {
            error(ptr + "/type", "unknown task type " + type);
            return null;
        }
        JsonObject out = o.deepCopy();
        out.remove("requires");
        out.addProperty("id", id);
        if (custom) {
            long count = o.has("count") && num(o.get("count")) ? o.get("count").getAsLong() : 1;
            TaskType tt = SimpleBooksApi.taskType(type);
            TaskSpec spec = null;
            if (tt == null && !trusted) {
                error(ptr + "/type", "no task type " + type + " is registered");
                return null;
            }
            if (tt != null) {
                boolean[] failed = {false};
                TaskProblems tp = new TaskProblems() {
                    public void error(String field, String message) {
                        failed[0] = true;
                        if (!trusted) QuestParser.this.error(ptr + "/" + field, message);
                    }

                    public void warn(String field, String message) {
                        if (!trusted) QuestParser.this.warn(ptr + "/" + field, message);
                    }
                };
                try {
                    spec = tt.parse(out, tp);
                    if (spec != null) count = tt.target(spec);
                } catch (Exception ex) {
                    tp.error("type", type + " could not read this task: " + ex);
                }
                if (failed[0] || spec == null) {
                    if (!trusted) {
                        if (!failed[0]) error(ptr + "/type", type + " rejected this task");
                        return null;
                    }
                    spec = null;
                }
            }
            return new Task(id, type, List.of(), count, false, "both", true, false, null, null, null, null, null, null, null, null, null, out, spec);
        }
        List<String> items = new ArrayList<>();
        if (ITEM_TASKS.contains(type)) {
            String item = r.opt("item");
            List<String> list = r.strs("items");
            if (item != null) items.add(item);
            items.addAll(list);
            if (items.isEmpty()) error(ptr + "/item", "missing");
            if (item != null && !list.isEmpty()) warn(ptr + "/items", "use item or items, not both");
            for (int i = 0; i < items.size(); i++) checkId(items.get(i), ptr + (item != null && i == 0 ? "/item" : "/items"), true);
        }
        boolean countable = COUNTING.contains(type);
        long count = countable ? r.lng("count", 1) : 1;
        if (countable && count < 1) error(ptr + "/count", "must be at least 1");
        boolean consume = false, teamTotal = true, fromStats = false;
        String handIn = "both";
        JsonElement match = null;
        if (type.equals("item")) {
            consume = r.bool("consume", false);
            handIn = r.oneOf("hand_in", "both", "both", "hand", "box");
            if (o.has("hand_in") && !consume) warn(ptr + "/hand_in", "only used with consume");
            teamTotal = r.bool("team_total", true);
        }
        if (ITEM_TASKS.contains(type)) match = r.raw("match");
        if (STATS_TASKS.contains(type)) fromStats = r.bool("from_stats", false);
        String block = null, entity = null, dimension = null, biome = null, adv = null, stat = null, value = null;
        switch (type) {
            case "place", "break" -> block = need(r, "block", ptr, true);
            case "kill" -> entity = need(r, "entity", ptr, true);
            case "dimension" -> dimension = need(r, "dimension", ptr, false);
            case "biome" -> biome = need(r, "biome", ptr, true);
            case "advancement" -> adv = need(r, "advancement", ptr, false);
            case "stat" -> {
                stat = need(r, "stat", ptr, false);
                value = need(r, "value", ptr, false);
            }
            default -> {}
        }
        String text = r.opt("text");
        if (type.equals("checkbox") && text == null) warn(ptr + "/text", "checkbox without text");
        if (!type.equals("item")) for (String k : new String[]{"consume", "hand_in", "team_total"})
            if (o.has(k)) {
                warn(ptr + "/" + k, "only used by item tasks");
                r.skip(k);
            }
        if (!countable && o.has("count")) {
            warn(ptr + "/count", "not used by " + type + " tasks");
            r.skip("count");
        }
        r.skip("requires");
        r.finish();
        return new Task(id, type, List.copyOf(items), count, consume, handIn, teamTotal, fromStats, match, block, entity,
                dimension, biome, adv, stat, value, text, out, null);
    }

    private @Nullable String need(R r, String key, String ptr, boolean tag) {
        String v = r.opt(key);
        if (v == null) error(ptr + "/" + key, "missing");
        else checkId(v, ptr + "/" + key, tag);
        return v;
    }

    private List<Reward> rewards(JsonArray arr, String ptr, JsonArray out) {
        List<Reward> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            String p = ptr + "/" + i;
            if (!arr.get(i).isJsonObject()) {
                error(p, "must be an object");
                continue;
            }
            JsonObject o = arr.get(i).getAsJsonObject();
            if (!requiresMet(o, p)) continue;
            Reward rw = reward(o, p);
            if (rw == null) continue;
            list.add(rw);
            out.add(rw.json());
        }
        return List.copyOf(list);
    }

    private @Nullable Reward reward(JsonObject o, String ptr) {
        List<String> kinds = new ArrayList<>();
        for (String k : new String[]{"item", "xp", "xp_levels", "command", "loot_table"}) if (o.has(k)) kinds.add(k);
        if (kinds.isEmpty()) {
            error(ptr, "unknown reward, use item, xp, xp_levels, command or loot_table");
            return null;
        }
        if (kinds.size() > 1) {
            error(ptr, "one reward per entry, found " + String.join(" and ", kinds));
            return null;
        }
        R r = new R(o, ptr);
        String kind = kinds.get(0);
        String item = null, command = null, label = null, icon = null, loot = null;
        JsonElement data = null;
        long count = 1;
        switch (kind) {
            case "item" -> {
                item = r.str("item", "");
                if (item.startsWith("#")) error(ptr + "/item", "rewards need an item, not a tag");
                else checkId(item, ptr + "/item", false);
                count = r.lng("count", 1);
                if (count < 1) error(ptr + "/count", "must be at least 1");
                data = r.raw(DATA_KEY);
                if (o.has(OTHER_DATA_KEY)) {
                    warn(ptr + "/" + OTHER_DATA_KEY, "not used on this Minecraft version, use " + DATA_KEY);
                    r.skip(OTHER_DATA_KEY);
                }
            }
            case "xp", "xp_levels" -> {
                count = r.lng(kind, 0);
                if (count < 1) error(ptr + "/" + kind, "must be at least 1");
            }
            case "command" -> {
                command = r.str("command", "");
                if (command.isBlank()) error(ptr + "/command", "empty");
                label = r.opt("label");
                icon = r.opt("icon");
                if (icon != null) checkId(icon, ptr + "/icon", false);
            }
            default -> {
                loot = r.str("loot_table", "");
                checkId(loot, ptr + "/loot_table", false);
            }
        }
        r.skip("requires");
        r.finish();
        JsonObject out = o.deepCopy();
        out.remove("requires");
        return new Reward(kind, item, count, data, command, label, icon, loot, out);
    }

    private long cooldown(String text, String ptr) {
        Matcher m = COOLDOWN.matcher(text);
        long total = 0;
        int end = 0;
        while (m.find() && m.start() == end) {
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) {
                case "s" -> 1L;
                case "m" -> 60L;
                default -> 3600L;
            };
            end = m.end();
        }
        if (end != text.length() || total <= 0) {
            error(ptr, "bad cooldown '" + text + "', use s, m or h, like 90s, 20m, 6h or 1h30m");
            return 0;
        }
        return total;
    }

    private void checkId(String id, String ptr, boolean tagOk) {
        String s = id;
        if (s.startsWith("#")) {
            if (!tagOk) {
                error(ptr, "a tag is not allowed here");
                return;
            }
            s = s.substring(1);
        }
        if (!ID.matcher(s).matches()) error(ptr, "bad id '" + id + "'");
    }

    private static boolean num(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static JsonObject clean(JsonObject o) {
        JsonObject c = o.deepCopy();
        c.remove("requires");
        c.remove("disabled");
        return c;
    }

    // ---- problems ----

    private void error(String ptr, String msg) {
        bad = true;
        problems.add(Level.ERROR, book, file, ptr, msg);
    }

    private void warn(String ptr, String msg) {
        if (!trusted) problems.add(Level.WARN, book, file, ptr, msg);
    }

    private void info(String ptr, String msg) {
        if (!trusted) problems.add(Level.INFO, book, file, ptr, msg);
    }

    /** Field reader that remembers which keys were read, for the unknown field warning. */
    private final class R {
        final JsonObject o;
        final String ptr;
        final Set<String> used = new HashSet<>();

        R(JsonObject o, String ptr) {
            this.o = o;
            this.ptr = ptr;
        }

        void skip(String... keys) {
            used.addAll(Arrays.asList(keys));
        }

        @Nullable JsonElement raw(String k) {
            used.add(k);
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k) : null;
        }

        @Nullable String opt(String k) {
            JsonElement e = raw(k);
            if (e == null) return null;
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) return e.getAsString();
            error(ptr + "/" + k, "must be a string");
            return null;
        }

        String str(String k, String def) {
            String s = opt(k);
            return s == null ? def : s;
        }

        long lng(String k, long def) {
            JsonElement e = raw(k);
            if (e == null) return def;
            if (num(e)) {
                double d = e.getAsDouble();
                if (d == Math.rint(d)) return e.getAsLong();
            }
            error(ptr + "/" + k, "must be a whole number");
            return def;
        }

        int integer(String k, long def) {
            long v = lng(k, def);
            if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
                error(ptr + "/" + k, "too big");
                return (int) def;
            }
            return (int) v;
        }

        boolean bool(String k, boolean def) {
            JsonElement e = raw(k);
            if (e == null) return def;
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
            error(ptr + "/" + k, "must be true or false");
            return def;
        }

        String oneOf(String k, String def, String... allowed) {
            String s = str(k, def);
            if (Arrays.asList(allowed).contains(s)) return s;
            error(ptr + "/" + k, "'" + s + "' is not one of " + String.join(", ", allowed));
            return def;
        }

        List<String> strs(String k) {
            JsonElement e = raw(k);
            List<String> out = new ArrayList<>();
            if (e == null) return out;
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) out.add(e.getAsString());
            else if (e.isJsonArray()) {
                for (JsonElement x : e.getAsJsonArray()) {
                    if (x.isJsonPrimitive() && x.getAsJsonPrimitive().isString()) out.add(x.getAsString());
                    else error(ptr + "/" + k, "must be a list of strings");
                }
            } else error(ptr + "/" + k, "must be a string or a list of strings");
            return out;
        }

        @Nullable JsonObject obj(String k) {
            JsonElement e = raw(k);
            if (e == null) return null;
            if (e.isJsonObject()) return e.getAsJsonObject();
            error(ptr + "/" + k, "must be an object");
            return null;
        }

        @Nullable JsonArray arr(String k) {
            JsonElement e = raw(k);
            if (e == null) return null;
            if (e.isJsonArray()) return e.getAsJsonArray();
            error(ptr + "/" + k, "must be a list");
            return null;
        }

        void finish() {
            if (trusted) return;
            for (String k : o.keySet()) if (!used.contains(k)) warn(ptr + "/" + k, "unknown field");
        }
    }

    static JsonPrimitive str(String s) {
        return new JsonPrimitive(s);
    }
}
