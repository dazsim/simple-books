package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.api.Display;
import com.workshopcraft.simplebooks.api.SimpleBooksApi;
import com.workshopcraft.simplebooks.api.TaskType;
import com.workshopcraft.simplebooks.quest.QuestActionPacket;
import com.workshopcraft.simplebooks.quest.QuestClientStore;
import com.workshopcraft.simplebooks.quest.QuestDefs;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The quest slot types: quest_list, quest_graph, quest_info, quest_lock, task_list, reward_list, quest_buttons and team_list. */
final class QuestSlots {
    /** A button: sent to the server, or for "select" and "confirm" kept on the client. */
    record Act(String action, String quest, String task, int number, String text, @Nullable Component tip) {}

    static final Map<String, Integer> chosen = new HashMap<>();
    static final Set<String> confirming = new HashSet<>();
    static float mx = -1, my = -1;

    private final Spread s;
    private final JsonObject page;

    QuestSlots(Spread s, JsonObject page) {
        this.s = s;
        this.page = page;
    }

    static float rowHeight(JsonObject slot) {
        return slot.has("row_height") ? slot.get("row_height").getAsFloat() : 58;
    }

    /** Client side of a button press; true when something happened. */
    static boolean press(Act a) {
        switch (a.action()) {
            case "select" -> {
                chosen.put(a.quest(), a.number());
                return true;
            }
            case "confirm" -> {
                confirming.add(a.text());
                return true;
            }
            default -> {
                confirming.clear();
                SimpleBooks.toServerAction.accept(new QuestActionPacket(a.action(), a.quest(), a.task(), a.number(), a.text()));
                return true;
            }
        }
    }

    void draw(String name, String type, JsonObject slot, float x, float y, float w, float h) {
        switch (type) {
            case "quest_list" -> questList(name, slot, x, y, w, h);
            case "task_list" -> taskList(x, y, w, h);
            case "reward_list" -> rewardList(x, y, w, h);
            case "quest_buttons" -> buttons(x, y, w, h);
            case "quest_graph" -> graph(x, y, w, h);
            case "quest_info" -> info(x, y, w, h);
            case "quest_lock" -> lock(x, y, w, h);
            case "team_list" -> team(slot.has("show") ? slot.get("show").getAsString() : "members", x, y, w, h);
            case "quest_board" -> board(slot, x, y, w, h);
            case "board_info" -> boardInfo(x, y, w, h);
            default -> {}
        }
    }

    // ---------- lookups ----------

    static QuestDefs.@Nullable Book bookOf(String id) {
        for (QuestDefs.Book b : QuestClientStore.defs().books().values()) {
            if (id.startsWith(b.id() + "/")) return b;
        }
        return null;
    }

    static QuestDefs.@Nullable Quest def(String id) {
        QuestDefs.Book b = bookOf(id);
        return b == null ? null : b.quests().get(id);
    }

    private static JsonObject st(String quest) {
        JsonObject o = QuestBook.quest(quest);
        return o == null ? new JsonObject() : o;
    }

    private static long count(JsonObject qs, String task) {
        return qs.has("t") && qs.getAsJsonObject("t").has(task) ? qs.getAsJsonObject("t").get(task).getAsLong() : 0;
    }

    static long target(QuestDefs.Quest q, QuestDefs.Task t) {
        if (!q.tiers().isEmpty()) return q.tiers().get(q.tiers().size() - 1).count();
        TaskType custom = t.spec() != null ? SimpleBooksApi.taskType(t.type()) : null;
        if (custom != null) return Math.max(1, custom.target(t.spec()));
        return Math.max(1, t.count());
    }

    private static boolean hasRewards(QuestDefs.Quest q) {
        if (!q.rewards().isEmpty() || !q.chooseOne().isEmpty()) return true;
        for (QuestDefs.Tier t : q.tiers()) if (!t.rewards().isEmpty()) return true;
        return false;
    }

    static boolean waiting(QuestDefs.Quest q) {
        JsonObject qs = st(q.id());
        return qs.has("claimed") && !qs.get("claimed").getAsBoolean() && hasRewards(q);
    }

    /** Seconds until a repeat quest opens again, 0 when not waiting, -1 when it will not repeat. */
    static long againLeft(JsonObject qs) {
        if (!qs.has("again")) return 0;
        long v = qs.get("again").getAsLong();
        return v < 0 ? -1 : Math.max(0, v - QuestClientStore.stateAge());
    }

    static String dur(long secs) {
        if (secs >= 3600) return secs / 3600 + "h " + secs % 3600 / 60 + "m";
        if (secs >= 60) return (secs + 59) / 60 + "m";
        return secs + "s";
    }

    /** How often a repeat quest has run and when it opens again, or null for other quests. */
    private static @Nullable Component repeatLine(QuestDefs.Quest q, JsonObject qs) {
        if (q.repeat() == null) return null;
        long left = againLeft(qs);
        int runs = qs.has("runs") ? qs.get("runs").getAsInt() : 0, limit = q.repeat().limit();
        if (left < 0) return tr("simplebooks.quest.repeat_over", runs);
        if (left > 0) return tr("simplebooks.quest.again_in", dur(left));
        if (qs.has("again")) return tr("simplebooks.quest.again_soon");
        if (runs == 0) return null;
        return limit > 0 ? tr("simplebooks.quest.runs_of", runs, limit) : tr("simplebooks.quest.runs", runs);
    }

    private static int tierOf(JsonObject qs) {
        return qs.has("tier") ? qs.get("tier").getAsInt() : 0;
    }

    private static int tiersClaimed(JsonObject qs) {
        return qs.has("ct") ? qs.get("ct").getAsInt() : 0;
    }

    private static Component tierName(QuestDefs.Quest q, int i) {
        QuestDefs.Tier t = q.tiers().get(i);
        return t.title() != null ? Texts.of(t.title()) : tr("simplebooks.quest.tier_n", i + 1);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static Component named(String prefix, @Nullable String id) {
        if (id == null) return Component.literal("?");
        if (id.startsWith("#")) id = id.substring(1);
        Identifier i = Identifier.tryParse(id);
        if (i == null) return Component.literal(id);
        String key = prefix + "." + i.getNamespace() + "." + i.getPath().replace('/', '.');
        if (Language.getInstance().has(key)) return Component.translatable(key);
        String p = i.getPath().substring(i.getPath().lastIndexOf('/') + 1).replace('_', ' ');
        return Component.literal(p.isEmpty() ? id : Character.toUpperCase(p.charAt(0)) + p.substring(1));
    }

    private static ItemRef ref(String id) {
        return ItemRef.parse(new JsonPrimitive(id));
    }

    // ---------- shared drawing ----------

    private void box(float x, float y, float sz, boolean ticked, int col) {
        s.fill(x, y, x + sz, y + sz, col);
        s.fill(x + 3, y + 3, x + sz - 3, y + sz - 3, s.color("$page"));
        if (!ticked) return;
        int c = s.color("$done");
        float u = sz / 14;
        for (int i = 0; i < 3; i++) s.fill(x + (3 + i) * u, y + (7 + i) * u, x + (5 + i) * u, y + (9 + i) * u, c);
        for (int i = 0; i < 6; i++) s.fill(x + (6 + i) * u, y + (8 - i) * u, x + (8 + i) * u, y + (10 - i) * u, c);
    }

    /** A dashed frame, for side quests. */
    private void dashed(float x0, float y0, float x1, float y1, int bg) {
        int c = s.color("$soft");
        for (float d = x0; d < x1; d += 8) {
            float e = Math.min(x1, d + 4);
            s.fill(d, y0, e, y0 + 2, c);
            s.fill(d, y1 - 2, e, y1, c);
        }
        for (float d = y0; d < y1; d += 8) {
            float e = Math.min(y1, d + 4);
            s.fill(x0, d, x0 + 2, e, c);
            s.fill(x1 - 2, d, x1, e, c);
        }
    }

    /** A quest title, with a softer side quest tag after it. */
    private void titled(QuestDefs.Quest q, float x, float y, float w, int ink) {
        Component tag = q.optional() ? tr("simplebooks.quest.optional") : null;
        float tw = tag == null ? 0 : s.textW(tag, 1) + 8;
        String t = fit(Texts.of(q.title()), w - tw);
        s.text(Component.literal(t), x, y, 1, ink);
        if (tag != null) s.text(tag, x + s.textW(Component.literal(t), 1) + 8, y, 1, s.color("$soft"));
    }

    private static boolean over(float x0, float y0, float x1, float y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    /** A rounded button; a disabled one shows its tip on hover instead of acting. */
    private float button(float x, float y, float w, float h, Component label, @Nullable Act act, @Nullable Component tip) {
        boolean hi = act != null && over(x, y, x + w, y + h);
        int bg = s.color(act == null ? "$closed" : hi ? "$claim_hover" : "$claim");
        s.roundRect(x, y + 3, x + w, y + h + 3, 6, s.color("$frame_inner") & 0x40FFFFFF);
        s.roundRect(x, y, x + w, y + h, 6, bg);
        s.ctext(label, x + w / 2, y + (h - 16) / 2, 1, s.color("$badge_text"));
        if (act != null) s.hits.add(new Spread.Hit(x, y, x + w, y + h, act));
        else if (tip != null) s.hits.add(new Spread.Hit(x, y, x + w, y + h, tip));
        return w;
    }

    private float buttonW(Component label) {
        return s.textW(label, 1) + 36;
    }

    private void bar(float x, float y, float w, long n, long of) {
        s.fill(x, y, x + w, y + 6, s.color("$line"));
        float k = of <= 0 ? 0 : Math.min(1, n / (float) of);
        if (k > 0) s.fill(x, y, x + w * k, y + 6, s.color("$line_done"));
    }

    private String fit(Component c, float maxw) {
        String t = c.getString();
        if (s.textW(c, 1) <= maxw) return t;
        while (t.length() > 1 && s.textW(Component.literal(t + "..."), 1) > maxw) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    // ---------- checklist ----------

    private void questList(String name, JsonObject slot, float x, float y, float w, float h) {
        String chapter = str(page, "chapter");
        QuestDefs.Book qb = bookOf(chapter);
        if (qb == null) return;
        List<QuestDefs.Quest> qs = QuestBook.visible(qb, chapter);
        float rh = rowHeight(slot);
        int rows = Math.max(1, (int) (h / rh));
        int from = page.has("from") ? page.get("from").getAsInt() : 0;
        if (name.startsWith("right.") && page.has("template")) {
            Template t = BookRegistry.template(Identifier.tryParse(page.get("template").getAsString()));
            for (var e : t.slots().entrySet())
                if (e.getKey().startsWith("left.") && "quest_list".equals(str(e.getValue(), "type")))
                    from += Math.max(1, (int) (e.getValue().getAsJsonArray("box").get(3).getAsFloat() / rowHeight(e.getValue())));
        }
        if (page.has("locked")) {
            if (name.startsWith("left.")) lock(x, y, w, h);
            return;
        }
        if (qs.isEmpty() && name.startsWith("left.")) {
            s.text(tr("simplebooks.quest.none"), x, y + 8, 1, s.color("$soft"));
            return;
        }
        for (int i = 0; i < rows && from + i < qs.size(); i++) row(qb, qs.get(from + i), x, y + i * rh, w, rh);
    }

    private void row(QuestDefs.Book qb, QuestDefs.Quest q, float x, float y, float w, float rh) {
        JsonObject qs = st(q.id());
        String status = str(qs, "s");
        boolean done = status.equals("complete"), closed = status.equals("closed"), locked = status.equals("locked") || closed;
        int ink = s.color(done ? "$done" : closed ? "$closed" : locked ? "$locked" : "$ink");
        float by = y + (rh - 28) / 2;
        boolean tick = !done && !locked && !qs.has("pick") && q.tasks().size() == 1 && q.tasks().get(0).type().equals("checkbox");
        if (tick && over(x, by, x + 28, by + 28)) s.fill(x - 3, by - 3, x + 31, by + 31, s.color("$rule"));
        box(x, by, 28, done, ink);
        if (closed) s.fill(x + 6, by + 12, x + 22, by + 16, ink);
        if (q.optional()) dashed(x - 4, by - 4, x + 32, by + 32, s.color("$page"));
        if (tick) s.hits.add(new Spread.Hit(x, by, x + 28, by + 28,
                new Act("checkbox", q.id(), q.tasks().get(0).id(), 0, "", tr("simplebooks.quest.tick"))));
        float tx = x + 44;
        if (!q.icon().isEmpty()) {
            s.item(ref(q.icon()), tx, y + (rh - 32) / 2, 32);
            tx += 44;
        }
        boolean wait = waiting(q);
        float right = x + w - (wait ? 40 : 4);
        titled(q, tx, y + rh / 2 - 19, right - tx, ink);
        Component sub = sub(qb, q, qs, status);
        int subCol = s.color(wait ? "$claim" : closed ? "$closed" : locked ? "$locked" : "$soft");
        s.text(Component.literal(fit(sub, right - tx)), tx, y + rh / 2 + 2, 1, subCol);
        if (closed) s.fill(tx, y + rh / 2 - 10, tx + Math.min(right - tx, s.textW(Texts.of(q.title()), 1)), y + rh / 2 - 8, ink);
        if (wait) {
            float bx = x + w - 30, cy = y + (rh - 26) / 2;
            s.roundRect(bx, cy, bx + 26, cy + 26, 13, s.color("$claim"));
            s.ctext(Component.literal("!"), bx + 13, cy + 5, 1, s.color("$badge_text"));
        }
        if (over(x, y, x + w, y + rh) && !(tick && over(x, by, x + 28, by + 28))) s.rule(tx, right, y + rh - 4);
        s.hits.add(new Spread.Hit(x, y, x + w, y + rh, QuestBook.detailId(q.id())));
    }

    private static boolean owner() {
        JsonObject st = QuestClientStore.state();
        return st.has("team") && st.getAsJsonObject("team").has("owner_is_me") && st.getAsJsonObject("team").get("owner_is_me").getAsBoolean();
    }

    private Component sub(QuestDefs.Book qb, QuestDefs.Quest q, JsonObject qs, String status) {
        if (status.equals("closed")) return tr("simplebooks.quest.closed");
        if (qs.has("pick") && !status.equals("locked")) return tr(owner() ? "simplebooks.quest.pick_path" : "simplebooks.quest.owner_picks");
        if (q.pool() != null && status.equals("complete") && qs.has("again")) {
            long left = againLeft(qs);
            if (!waiting(q)) return left > 0 ? tr("simplebooks.board.new_in", dur(left)) : left < 0 ? tr("simplebooks.board.stays") : tr("simplebooks.board.new_soon");
        }
        switch (status) {
            case "locked" -> {
                List<String> need = new ArrayList<>();
                for (String d : q.depends()) {
                    int hash = d.indexOf('#'), tier = 0;
                    if (hash > 0) {
                        try {
                            tier = Integer.parseInt(d.substring(hash + 1));
                        } catch (NumberFormatException e) {
                            tier = 0;
                        }
                        d = d.substring(0, hash);
                    }
                    JsonObject ds = st(d);
                    if (str(ds, "s").equals("complete") || tier > 0 && tierOf(ds) >= tier || tier == 0 && ds.has("claimed")) continue;
                    QuestDefs.Quest dq = qb.quests().get(d);
                    if (dq == null || str(ds, "s").equals("hidden")) need.add(tr("simplebooks.quest.hidden_dep").getString());
                    else if (tier > 0 && tier <= dq.tiers().size()) need.add(Texts.of(dq.title()).getString() + " (" + tierName(dq, tier - 1).getString() + ")");
                    else need.add(Texts.of(dq.title()).getString());
                }
                QuestDefs.Quest prev = qb.storyPrev(q);
                if (need.isEmpty() && prev != null && !str(st(prev.id()), "s").equals("complete"))
                    return tr("simplebooks.quest.story_after", Texts.of(prev.title()));
                if (need.isEmpty()) return tr("simplebooks.quest.locked");
                return tr(q.dependsMode().equals("any") ? "simplebooks.quest.needs_any" : "simplebooks.quest.needs", String.join(", ", need));
            }
            case "complete" -> {
                if (waiting(q)) return tr("simplebooks.quest.rewards_waiting");
                Component r = repeatLine(q, qs);
                return r != null ? r : tr("simplebooks.quest.done");
            }
            default -> {
                if (waiting(q)) return tr("simplebooks.quest.rewards_waiting");
                if (!q.tiers().isEmpty() && tierOf(qs) > 0) {
                    QuestDefs.Task t = q.tasks().get(0);
                    return tr("simplebooks.quest.tier_progress", tierName(q, tierOf(qs) - 1), count(qs, t.id()) + "/" + target(q, t));
                }
                if (!q.subtitle().isEmpty() && status.equals("available")) return Texts.of(q.subtitle());
                int done = 0;
                for (QuestDefs.Task t : q.tasks()) if (count(qs, t.id()) >= target(q, t)) done++;
                if (q.tasks().size() == 1) {
                    QuestDefs.Task t = q.tasks().get(0);
                    return Component.literal(count(qs, t.id()) + "/" + target(q, t)).append(" ").append(taskLabel(t, target(q, t)));
                }
                return tr("simplebooks.quest.tasks_done", done, q.tasks().size());
            }
        }
    }

    // ---------- tree ----------

    /** Wheel and drag target over a quest graph; max is how far it can move. */
    record Pan(String chapter, float maxX, float maxY) {}

    static final Map<String, float[]> panned = new HashMap<>();
    static final Map<String, String> focus = new HashMap<>();
    static final float CELL_W = 124, CELL_H = 112, NODE = 64, PAD = 30;

    /** Moves a graph by (dx, dy) spread units; false when it has nothing to scroll. */
    static boolean pan(Pan p, double dx, double dy) {
        if (p.maxX() <= 0 && p.maxY() <= 0) return false;
        float[] o = panned.computeIfAbsent(p.chapter(), k -> new float[2]);
        o[0] = Math.max(0, Math.min(p.maxX(), o[0] + (float) dx));
        o[1] = Math.max(0, Math.min(p.maxY(), o[1] + (float) dy));
        return true;
    }

    /** Grid cells [column, row]: pos when given, else column by dependency depth and the first free row. */
    static Map<String, int[]> layout(QuestDefs.Book qb, String chapter) {
        List<QuestDefs.Quest> all = new ArrayList<>();
        for (QuestDefs.Quest q : qb.quests().values()) if (chapter.equals(q.chapter())) all.add(q);
        all.sort(java.util.Comparator.comparingInt(QuestDefs.Quest::sort).thenComparing(QuestDefs.Quest::id));
        Map<String, int[]> at = new java.util.LinkedHashMap<>();
        Set<Long> used = new HashSet<>();
        for (QuestDefs.Quest q : all)
            if (q.pos() != null && q.pos().length >= 2) {
                at.put(q.id(), new int[]{q.pos()[0], q.pos()[1]});
                used.add(cell(q.pos()[0], q.pos()[1]));
            }
        Map<String, Integer> cols = new HashMap<>();
        java.util.TreeMap<Integer, List<QuestDefs.Quest>> byCol = new java.util.TreeMap<>();
        for (QuestDefs.Quest q : all)
            if (!at.containsKey(q.id())) byCol.computeIfAbsent(column(qb, q, chapter, cols, new HashSet<>()), k -> new ArrayList<>()).add(q);
        for (var e : byCol.entrySet()) {
            List<QuestDefs.Quest> qs = e.getValue();
            Map<String, Float> pull = new HashMap<>();
            for (QuestDefs.Quest q : qs) {
                float sum = 0;
                int n = 0;
                for (String d : q.depends()) {
                    int[] c = at.get(d);
                    if (c != null) {
                        sum += c[1];
                        n++;
                    }
                }
                pull.put(q.id(), n == 0 ? Float.MAX_VALUE : sum / n);
            }
            qs.sort(java.util.Comparator.<QuestDefs.Quest>comparingDouble(q -> pull.get(q.id()))
                    .thenComparingInt(QuestDefs.Quest::sort).thenComparing(QuestDefs.Quest::id));
            int row = 0;
            for (QuestDefs.Quest q : qs) {
                float want = pull.get(q.id());
                if (want != Float.MAX_VALUE) row = Math.max(row, Math.round(want));
                while (used.contains(cell(e.getKey(), row))) row++;
                at.put(q.id(), new int[]{e.getKey(), row});
                used.add(cell(e.getKey(), row));
                row++;
            }
        }
        return at;
    }

    private static long cell(int c, int r) {
        return ((long) c << 32) ^ (r & 0xFFFFFFFFL);
    }

    private static int column(QuestDefs.Book qb, QuestDefs.Quest q, String chapter, Map<String, Integer> cols, Set<String> seen) {
        if (q.pos() != null && q.pos().length >= 2) return q.pos()[0];
        Integer c = cols.get(q.id());
        if (c != null) return c;
        if (!seen.add(q.id())) return 0;
        int best = 0;
        for (String d : q.depends()) {
            QuestDefs.Quest dq = qb.quests().get(d);
            if (dq != null && chapter.equals(dq.chapter())) best = Math.max(best, column(qb, dq, chapter, cols, seen) + 1);
        }
        cols.put(q.id(), best);
        return best;
    }

    private void graph(float x, float y, float w, float h) {
        String chapter = str(page, "chapter");
        QuestDefs.Book qb = bookOf(chapter);
        if (qb == null) return;
        List<QuestDefs.Quest> vis = QuestBook.visible(qb, chapter);
        if (vis.isEmpty()) {
            s.text(tr("simplebooks.quest.none"), x, y + 8, 1, s.color("$soft"));
            return;
        }
        Map<String, int[]> at = layout(qb, chapter);
        Set<String> shown = new HashSet<>();
        int minC = Integer.MAX_VALUE, minR = Integer.MAX_VALUE, maxC = 0, maxR = 0;
        for (QuestDefs.Quest q : vis) {
            int[] c = at.get(q.id());
            shown.add(q.id());
            minC = Math.min(minC, c[0]);
            minR = Math.min(minR, c[1]);
            maxC = Math.max(maxC, c[0]);
            maxR = Math.max(maxR, c[1]);
        }
        float gw = (maxC - minC + 1) * CELL_W, gh = (maxR - minR + 1) * CELL_H;
        float maxX = Math.max(0, gw + 2 * PAD - w), maxY = Math.max(0, gh + 2 * PAD - h);
        float[] o = panned.computeIfAbsent(chapter, k -> new float[2]);
        o[0] = Math.min(o[0], maxX);
        o[1] = Math.min(o[1], maxY);
        float ox = (maxX > 0 ? x + PAD - o[0] : x + (w - gw) / 2) - minC * CELL_W;
        float oy = (maxY > 0 ? y + PAD - o[1] : y + PAD) - minR * CELL_H;
        s.clip(x, y, x + w, y + h);
        for (QuestDefs.Quest q : vis)
            for (String d : q.depends())
                if (shown.contains(d)) link(at.get(d), at.get(q.id()), ox, oy, str(st(d), "s").equals("complete"));
        int start = s.hits.size();
        List<Spread.Hit> nodes = new ArrayList<>();
        for (QuestDefs.Quest q : vis) {
            int[] c = at.get(q.id());
            float nx = ox + c[0] * CELL_W + (CELL_W - NODE) / 2, ny = oy + c[1] * CELL_H + (CELL_H - NODE) / 2;
            boolean in = nx + NODE > x && nx < x + w && ny + NODE > y && ny < y + h;
            boolean hov = in && over(Math.max(x, nx), Math.max(y, ny), Math.min(x + w, nx + NODE), Math.min(y + h, ny + NODE));
            if (hov) focus.put(chapter, q.id());
            node(qb, q, nx, ny, hov);
            if (in) {
                Component tip = Texts.of(q.title()).copy().append(": ").append(sub(qb, q, st(q.id()), str(st(q.id()), "s")));
                nodes.add(new Spread.Hit(Math.max(x, nx), Math.max(y, ny), Math.min(x + w, nx + NODE), Math.min(y + h, ny + NODE),
                        s.link(QuestBook.detailId(q.id()), tip)));
            }
        }
        s.hits.subList(start, s.hits.size()).clear();
        s.hits.addAll(nodes);
        s.unclip();
        int bar = s.color("$rule");
        if (maxX > 0) {
            float k = w / (gw + 2 * PAD);
            s.fill(x + o[0] * k, y + h - 6, x + (o[0] + w) * k, y + h - 2, bar);
        }
        if (maxY > 0) {
            float k = h / (gh + 2 * PAD);
            s.fill(x + w - 6, y + o[1] * k, x + w - 2, y + (o[1] + h) * k, bar);
        }
        s.hits.add(new Spread.Hit(x, y, x + w, y + h, new Pan(chapter, maxX, maxY)));
    }

    private void link(int[] a, int[] b, float ox, float oy, boolean done) {
        int col = s.color(done ? "$line_done" : "$line_todo");
        float ax = ox + a[0] * CELL_W + CELL_W / 2, ay = oy + a[1] * CELL_H + CELL_H / 2;
        float bx = ox + b[0] * CELL_W + CELL_W / 2, by = oy + b[1] * CELL_H + CELL_H / 2;
        float t = 2, e = NODE / 2;
        if (a[0] < b[0]) {
            float x0 = ax + e, x1 = bx - e, m = b[0] - a[0] == 1 ? (x0 + x1) / 2 : x1 - (CELL_W - NODE) / 2;
            s.fill(x0, ay - t, m + t, ay + t, col);
            s.fill(m - t, Math.min(ay, by) - t, m + t, Math.max(ay, by) + t, col);
            s.fill(m - t, by - t, x1, by + t, col);
            arrowHead(x1, by, col);
        } else if (a[0] == b[0]) {
            float y0 = ay < by ? ay + e : ay - e, y1 = ay < by ? by - e : by + e;
            s.fill(ax - t, Math.min(y0, y1), ax + t, Math.max(y0, y1), col);
            arrowDown(bx, y1, by > ay, col);
        } else {
            float y0 = ay + (by >= ay ? e : -e), m = (ay + by) / 2, y1 = by + (by >= ay ? -e : e);
            s.fill(ax - t, Math.min(y0, m), ax + t, Math.max(y0, m), col);
            s.fill(Math.min(ax, bx) - t, m - t, Math.max(ax, bx) + t, m + t, col);
            s.fill(bx - t, Math.min(m, y1), bx + t, Math.max(m, y1), col);
            arrowDown(bx, y1, by >= ay, col);
        }
    }

    private void arrowDown(float x, float y, boolean down, int col) {
        for (int i = 0; i < 5; i++) {
            float a = down ? y - 2 - 2 * i : y + 2 * i, b = a + 2;
            s.fill(x - 2 - 2 * i, a, x + 2 + 2 * i, b, col);
        }
    }

    private void arrowHead(float x, float y, int col) {
        for (int i = 0; i < 5; i++) s.fill(x - 2 - 2 * i, y - 2 - 2 * i, x - 2 * i, y + 2 + 2 * i, col);
    }

    private void node(QuestDefs.Book qb, QuestDefs.Quest q, float x, float y, boolean hov) {
        JsonObject qs = st(q.id());
        String status = str(qs, "s");
        boolean done = status.equals("complete"), locked = status.equals("locked");
        int frame = s.color(hov ? "$link" : done ? "$done" : locked ? "$locked" : "$ink");
        float x1 = x + NODE, y1 = y + NODE;
        if (q.optional()) {
            s.fill(x, y, x1, y1, s.color("$page"));
            for (float d = 0; d < NODE; d += 12) {
                float e = Math.min(NODE, d + 7);
                s.fill(x + d, y, x + e, y + 4, frame);
                s.fill(x + d, y1 - 4, x + e, y1, frame);
                s.fill(x, y + d, x + 4, y + e, frame);
                s.fill(x1 - 4, y + d, x1, y + e, frame);
            }
        } else {
            s.roundRect(x, y, x1, y1, 10, frame);
            s.roundRect(x + 4, y + 4, x1 - 4, y1 - 4, 7, s.color(done ? "$tree_done" : "$page"));
        }
        ItemRef icon = !q.icon().isEmpty() ? ref(q.icon()) : !q.tasks().isEmpty() ? taskIcon(q.tasks().get(0)) : null;
        if (icon == null) icon = ref("minecraft:writable_book");
        s.item(icon, x + 12, y + 12, 40);
        if (locked) s.fill(x + 6, y + 6, x1 - 6, y1 - 6, s.color("$page") & 0xFFFFFF | 0xA0000000);
        if (done) {
            s.roundRect(x1 - 20, y1 - 20, x1 + 6, y1 + 6, 13, s.color("$done"));
            int c = s.color("$badge_text");
            float u = 2, bx = x1 - 20 + 3, by = y1 - 20 + 2;
            for (int i = 0; i < 3; i++) s.fill(bx + (3 + i) * u, by + (7 + i) * u, bx + (5 + i) * u, by + (9 + i) * u, c);
            for (int i = 0; i < 6; i++) s.fill(bx + (6 + i) * u, by + (8 - i) * u, bx + (8 + i) * u, by + (10 - i) * u, c);
        }
        if (waiting(q)) {
            s.roundRect(x1 - 20, y - 6, x1 + 6, y + 20, 13, s.color("$claim"));
            s.ctext(Component.literal("!"), x1 - 7, y - 1, 1, s.color("$badge_text"));
        }
        if (status.equals("in_progress") && !q.tasks().isEmpty()) {
            long n = 0, of = 0;
            for (QuestDefs.Task t : q.tasks()) {
                of += target(q, t);
                n += Math.min(count(qs, t.id()), target(q, t));
            }
            bar(x + 4, y1 + 6, NODE - 8, n, of);
        }
    }

    /** The left page of a tree: the quest last pointed at, or a hint. */
    private void info(float x, float y, float w, float h) {
        String chapter = str(page, "chapter");
        QuestDefs.Book qb = bookOf(chapter);
        if (qb == null) return;
        String id = focus.get(chapter);
        QuestDefs.Quest q = id == null ? null : qb.quests().get(id);
        if (q == null || str(st(q.id()), "s").equals("hidden")) {
            int[] p = QuestBook.progress(chapter);
            if (p != null) s.text(tr("simplebooks.quest.tree_done", p[0], p[1]), x, y, 1, s.color("$head"));
            s.para(tr("simplebooks.quest.tree_hint"), x, y + 30, w, 1, s.color("$soft"), 18, "left");
            return;
        }
        JsonObject qs = st(q.id());
        String status = str(qs, "s");
        float end = y + h;
        ItemRef icon = !q.icon().isEmpty() ? ref(q.icon()) : !q.tasks().isEmpty() ? taskIcon(q.tasks().get(0)) : null;
        float tx = x;
        if (icon != null) {
            s.slotItem(icon, x, y, 44);
            tx += 56;
        }
        titled(q, tx, y + 2, x + w - tx, s.color("$ink"));
        s.text(Component.literal(fit(sub(qb, q, qs, status), x + w - tx)), tx, y + 24, 1,
                s.color(waiting(q) ? "$claim" : status.equals("locked") ? "$locked" : status.equals("complete") ? "$done" : "$soft"));
        y += 60;
        s.rule(x, x + w, y);
        y += 14;
        if (!q.text().isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> ls = s.wrap(Texts.of(q.text()), w, 1);
            for (int i = 0; i < Math.min(5, ls.size()) && y + 18 < end - 80; i++, y += 18) s.text(ls.get(i), x, y, 1, s.color("$ink"));
            y += 12;
        }
        for (QuestDefs.Task t : q.tasks()) {
            if (y + 34 > end - 30) break;
            long n = count(qs, t.id()), of = target(q, t);
            boolean done = n >= of || status.equals("complete");
            ItemRef ti = taskIcon(t);
            if (ti != null) s.item(ti, x, y, 28);
            else box(x + 2, y + 2, 24, done, s.color(done ? "$done" : "$ink"));
            Component prog = Component.literal(Math.min(n, of) + "/" + of);
            float pw = s.textW(prog, 1);
            s.text(Component.literal(fit(taskLabel(t, of), w - 52 - pw)), x + 40, y + 6, 1, s.color(done ? "$done" : "$ink"));
            s.text(prog, x + w - pw, y + 6, 1, s.color(done ? "$done" : "$soft"));
            y += 36;
        }
        s.text(tr("simplebooks.quest.tree_open"), x, end - 18, 1, s.color("$soft"));
    }

    /** A locked chapter's note, or a locked story spread's. */
    private void lock(float x, float y, float w, float h) {
        List<String> need = new ArrayList<>();
        Component head;
        if (page.has("locked")) {
            String chapter = str(page, "chapter");
            QuestDefs.Book qb = bookOf(chapter);
            QuestDefs.Chapter ch = qb == null ? null : qb.chapters().get(chapter);
            if (ch == null) return;
            for (String d : ch.depends()) {
                if (qb.quests().containsKey(d)) {
                    if (!str(st(d), "s").equals("complete")) need.add(Texts.of(qb.quests().get(d).title()).getString());
                } else if (qb.chapters().containsKey(d)) {
                    int[] p = QuestBook.progress(d);
                    if (p == null || p[0] < p[1]) need.add(Texts.of(qb.chapters().get(d).title()).getString());
                }
            }
            head = tr("simplebooks.quest.chapter_locked");
        } else {
            String quest = str(page, "quest");
            QuestDefs.Book qb = bookOf(quest);
            QuestDefs.Quest q = qb == null ? null : qb.quests().get(quest);
            if (q == null || !str(st(quest), "s").equals("locked")) return;
            head = sub(qb, q, st(quest), "locked");
        }
        s.fill(x, y, x + w, y + 2, s.color("$rule"));
        lockIcon(x, y + 18, s.color("$locked"));
        s.text(head, x + 44, y + 20, 1, s.color("$head"));
        if (!need.isEmpty()) s.para(tr("simplebooks.quest.needs", String.join(", ", need)), x + 44, y + 44, w - 44, 1, s.color("$soft"), 18, "left");
    }

    private void lockIcon(float x, float y, int col) {
        s.fill(x + 6, y, x + 22, y + 4, col);
        s.fill(x + 6, y, x + 10, y + 14, col);
        s.fill(x + 18, y, x + 22, y + 14, col);
        s.fill(x, y + 12, x + 28, y + 34, col);
        s.fill(x + 12, y + 18, x + 16, y + 28, s.color("$page"));
    }

    // ---------- detail ----------

    static Component taskLabel(QuestDefs.Task t, long n) {
        if (t.text() != null && !t.text().isEmpty()) return Texts.of(t.text());
        String items = t.items().isEmpty() ? null : t.items().get(0);
        return switch (t.type()) {
            case "item" -> tr(t.consume() ? "simplebooks.task.hand_in" : "simplebooks.task.item", n, itemName(items));
            case "craft", "smelt", "pickup", "use" -> tr("simplebooks.task." + t.type(), n, itemName(items));
            case "place", "break" -> tr("simplebooks.task." + t.type(), n, t.block() != null ? named("block", t.block()) : itemName(items));
            case "kill" -> tr("simplebooks.task.kill", n, named("entity", t.entity()));
            case "advancement" -> {
                Identifier a = t.advancement() == null ? null : Identifier.tryParse(t.advancement());
                String key = a == null ? "" : "advancements." + a.getPath().replace('/', '.') + ".title";
                yield tr("simplebooks.task.advancement", Language.getInstance().has(key) ? Component.translatable(key) : named("advancement", t.advancement()));
            }
            case "dimension" -> tr("simplebooks.task.dimension", named("dimension", t.dimension()));
            case "biome" -> tr("simplebooks.task.biome", named("biome", t.biome()));
            case "checkbox" -> tr("simplebooks.task.checkbox");
            case "stat" -> {
                String v = t.value() == null ? "" : t.value();
                Identifier vi = Identifier.tryParse(v);
                String key = vi == null ? "" : "stat." + vi.getNamespace() + "." + vi.getPath();
                yield tr("simplebooks.task.stat", Language.getInstance().has(key) ? Component.translatable(key) : named("stat", v), n);
            }
            default -> {
                TaskType custom = t.spec() != null ? SimpleBooksApi.taskType(t.type()) : null;
                Display d = custom != null ? custom.display(t.spec()) : null;
                yield d != null && !d.text().isEmpty() ? Texts.of(d.text()) : Component.literal(t.type() + " x" + n);
            }
        };
    }

    private static Component itemName(@Nullable String id) {
        if (id == null) return Component.literal("?");
        if (!id.startsWith("#")) return ref(id).name();
        return tr("simplebooks.quest.tag", named("tag.item", id));
    }

    static @Nullable ItemRef taskIcon(QuestDefs.Task t) {
        if (!t.items().isEmpty()) return new ItemRef(t.items(), null, 0, null);
        String id = switch (t.type()) {
            case "kill" -> t.entity() != null && ItemRef.known(t.entity() + "_spawn_egg") ? t.entity() + "_spawn_egg" : "minecraft:iron_sword";
            case "place", "break" -> t.block() != null && ItemRef.known(t.block()) ? t.block() : "minecraft:iron_pickaxe";
            case "advancement" -> "minecraft:knowledge_book";
            case "dimension" -> "minecraft:obsidian";
            case "biome" -> "minecraft:filled_map";
            case "stat" -> "minecraft:paper";
            case "checkbox" -> null;
            default -> {
                TaskType custom = t.spec() != null ? SimpleBooksApi.taskType(t.type()) : null;
                Display d = custom != null ? custom.display(t.spec()) : null;
                yield d != null && !d.icon().isEmpty() && ItemRef.known(d.icon()) ? d.icon() : "minecraft:writable_book";
            }
        };
        return id == null ? null : ref(id);
    }

    private void taskList(float x, float y, float w, float h) {
        String quest = str(page, "quest");
        QuestDefs.Book qb = bookOf(quest);
        QuestDefs.Quest q = qb == null ? null : qb.quests().get(quest);
        if (q == null) return;
        JsonObject qs = st(quest);
        String status = str(qs, "s");
        boolean open = (status.equals("available") || status.equals("in_progress")) && !qs.has("pick");
        Component state = switch (status) {
            case "available" -> qs.has("pick") ? tr(owner() ? "simplebooks.quest.pick_path" : "simplebooks.quest.owner_picks") : tr("simplebooks.quest.state.available");
            case "closed" -> tr("simplebooks.quest.state.closed");
            case "complete" -> tr("simplebooks.quest.state.complete");
            case "in_progress" -> tr("simplebooks.quest.state.in_progress");
            default -> sub(qb, q, qs, "locked");
        };
        Component rep = repeatLine(q, qs);
        if (rep != null && !status.equals("locked")) state = state.copy().append(", ").append(rep);
        s.text(Component.literal(fit(state, w)), x, y, 1, s.color(status.equals("complete") ? "$done" : status.equals("closed") ? "$closed" : status.equals("locked") ? "$locked" : "$soft"));
        y += 30;
        List<Component> notes = new ArrayList<>();
        if (q.optional()) notes.add(tr("simplebooks.quest.side_note"));
        if (q.exclusive() != null && q.pool() == null)
            notes.add(tr(q.exclusive().lockOn().equals("choose") ? "simplebooks.quest.path_note" : "simplebooks.quest.race_note"));
        if (q.repeat() != null) notes.add(tr(q.repeat().limit() > 0 ? "simplebooks.quest.repeat_limit" : "simplebooks.quest.repeat_every",
                dur(q.repeat().cooldown()), q.repeat().limit()));
        for (Component n : notes) {
            s.text(Component.literal(fit(n, w - 22)), x + 22, y - 4, 1, s.color("$soft"));
            if (q.optional() && n == notes.get(0)) dashed(x, y - 2, x + 12, y + 10, s.color("$page"));
            else s.fill(x + 3, y + 2, x + 9, y + 8, s.color("$soft"));
            y += 22;
        }
        if (!notes.isEmpty()) y += 8;
        Component head = tr(q.tasks().size() > 1 && q.taskMode().equals("any") ? "simplebooks.quest.tasks_any" : "simplebooks.quest.tasks");
        s.text(head, x, y, 1, s.color("$head"));
        s.rule(x, x + w, y + 20);
        y += 30;
        float end = y + h - 60;
        for (QuestDefs.Task t : q.tasks()) {
            if (y + 50 > end) break;
            long n = count(qs, t.id()), of = target(q, t);
            boolean done = n >= of || status.equals("complete");
            float tx = x + 52;
            if (t.type().equals("checkbox")) {
                boolean can = open && !done;
                if (can && over(x + 6, y + 6, x + 38, y + 38)) s.fill(x + 3, y + 3, x + 41, y + 41, s.color("$rule"));
                box(x + 6, y + 6, 32, done, s.color(done ? "$done" : "$ink"));
                if (can) s.hits.add(new Spread.Hit(x + 6, y + 6, x + 38, y + 38, new Act("checkbox", quest, t.id(), 0, "", tr("simplebooks.quest.tick"))));
            } else {
                ItemRef icon = taskIcon(t);
                if (icon != null) s.slotItem(icon, x, y, 44);
            }
            float right = x + w;
            Component prog = Component.literal(Math.min(n, of) + "/" + of);
            boolean hand = t.type().equals("item") && t.consume() && !t.handIn().equals("box") && open && !done;
            if (hand) {
                Component lb = tr("simplebooks.quest.hand_in");
                float bw = buttonW(lb);
                button(right - bw, y + 4, bw, 36, lb, new Act("hand_in", quest, t.id(), 0, "", null), null);
                right -= bw + 14;
            }
            if (!t.type().equals("checkbox") && !t.type().equals("advancement") && !t.type().equals("dimension") && !t.type().equals("biome")) {
                float pw = s.textW(prog, 1);
                s.text(prog, right - pw, y + 6, 1, s.color(done ? "$done" : "$ink"));
                right -= pw + 14;
            }
            Component label = taskLabel(t, of);
            s.text(Component.literal(fit(label, right - tx)), tx, y + 6, 1, s.color(done ? "$done" : "$ink"));
            if (!t.type().equals("checkbox")) bar(tx, y + 30, right - tx, Math.min(n, of), of);
            for (int i = 0; i + 1 < q.tiers().size(); i++) {
                float mx0 = tx + (right - tx) * Math.min(1, q.tiers().get(i).count() / (float) of);
                s.fill(mx0 - 1, y + 27, mx0 + 2, y + 39, s.color(n >= q.tiers().get(i).count() ? "$page" : "$ink"));
            }
            y += 50;
            String who = shares(qs, t.id());
            if (!who.isEmpty() && y + 20 <= end) {
                s.text(Component.literal(fit(Component.literal(who), w - 52)), tx, y - 4, 1, s.color("$soft"));
                y += 20;
            }
            y += 6;
        }
    }

    private static int teamSize() {
        JsonObject st = QuestClientStore.state();
        if (!st.has("team") || !st.getAsJsonObject("team").has("members")) return 1;
        return st.getAsJsonObject("team").getAsJsonArray("members").size();
    }

    private static String shares(JsonObject qs, String task) {
        JsonObject m = null;
        if (qs.has("g") && qs.getAsJsonObject("g").has(task)) m = qs.getAsJsonObject("g").getAsJsonObject(task);
        else if (qs.has("sh") && qs.getAsJsonObject("sh").has(task)) m = qs.getAsJsonObject("sh").getAsJsonObject(task);
        if (m == null || m.size() < 1 || teamSize() < 2 && !m.has(com.workshopcraft.simplebooks.quest.HandInBoxes.CREDIT)) return "";
        List<String> parts = new ArrayList<>();
        for (var e : m.entrySet()) {
            if (e.getValue().getAsLong() <= 0) continue;
            String who = e.getKey().equals(com.workshopcraft.simplebooks.quest.HandInBoxes.CREDIT) ? Language.getInstance().getOrDefault("simplebooks.box.credit") : e.getKey();
            parts.add(who + " " + e.getValue().getAsLong());
        }
        return String.join(", ", parts);
    }

    // ---------- rewards ----------

    private void rewardList(float x, float y, float w, float h) {
        String quest = str(page, "quest");
        QuestDefs.Quest q = def(quest);
        if (q == null) return;
        JsonObject qs = st(quest);
        float end = y + h;
        if (!hasRewards(q)) {
            s.text(tr("simplebooks.quest.no_rewards"), x, y + 8, 1, s.color("$soft"));
            return;
        }
        boolean tiered = !q.tiers().isEmpty();
        int ct = tiersClaimed(qs), reached = tierOf(qs);
        if (tiered) for (int i = 0; i < q.tiers().size(); i++) {
            QuestDefs.Tier t = q.tiers().get(i);
            if (y + 80 > end) return;
            boolean got = i < ct, ready = !got && i < reached;
            Component head = tierName(q, i).copy().append("  ").append(tr("simplebooks.quest.tier", t.count()));
            Component tag = got ? tr("simplebooks.quest.tier_claimed") : ready ? tr("simplebooks.quest.tier_ready") : Component.empty();
            s.text(Component.literal(fit(head, w - s.textW(tag, 1) - 12)), x, y, 1, s.color(got ? "$done" : ready ? "$claim" : "$head"));
            s.text(tag, x + w - s.textW(tag, 1), y, 1, s.color(got ? "$done" : "$claim"));
            s.rule(x, x + w, y + 20);
            y += 30;
            List<QuestDefs.Reward> rs = new ArrayList<>(t.rewards());
            if (i == q.tiers().size() - 1) rs.addAll(q.rewards());
            for (QuestDefs.Reward r : rs) {
                if (y + 50 > end) return;
                reward(r, x, y, w);
                y += 52;
            }
            y += 6;
        }
        else for (QuestDefs.Reward r : q.rewards()) {
            if (y + 50 > end) return;
            reward(r, x, y, w);
            y += 52;
        }
        if (!q.chooseOne().isEmpty() && y + 80 <= end) {
            y += 10;
            s.text(tr("simplebooks.quest.choose_one"), x, y, 1, s.color("$head"));
            s.rule(x, x + w, y + 20);
            y += 30;
            boolean claimed = qs.has("claimed") && qs.get("claimed").getAsBoolean();
            int pick = claimed ? (qs.has("choice") ? qs.get("choice").getAsInt() : -1) : chosen.getOrDefault(quest, -1);
            for (int i = 0; i < q.chooseOne().size(); i++) {
                if (y + 50 > end) return;
                boolean sel = i == pick, hov = !claimed && over(x, y - 4, x + w, y + 48);
                if (sel || hov) {
                    s.fill(x - 6, y - 4, x + w, y + 48, s.color(sel ? "$claim" : "$rule"));
                    s.fill(x - 3, y - 1, x + w - 3, y + 45, s.color("$page"));
                }
                reward(q.chooseOne().get(i), x, y, w);
                if (!claimed) s.hits.add(new Spread.Hit(x - 6, y - 4, x + w, y + 48, new Act("select", quest, "", i, "", tr("simplebooks.quest.pick"))));
                y += 52;
            }
        }
    }

    private void reward(QuestDefs.Reward r, float x, float y, float w) {
        String icon;
        Component label;
        switch (r.kind()) {
            case "item" -> {
                icon = r.item() == null ? "minecraft:barrier" : r.item();
                label = r.count() > 1 ? tr("simplebooks.count", r.count(), ref(icon).name()) : ref(icon).name();
            }
            case "xp" -> {
                icon = "minecraft:experience_bottle";
                label = tr("simplebooks.reward.xp", r.count());
            }
            case "xp_levels" -> {
                icon = "minecraft:experience_bottle";
                label = tr("simplebooks.reward.levels", r.count());
            }
            case "loot_table" -> {
                icon = "minecraft:chest";
                label = tr("simplebooks.reward.loot");
            }
            default -> {
                icon = "minecraft:paper";
                label = tr("simplebooks.reward.command");
            }
        }
        if (r.icon() != null && ItemRef.known(r.icon())) icon = r.icon();
        if (r.label() != null && !r.label().isEmpty()) label = Texts.of(r.label());
        s.slotItem(ref(icon), x, y, 44);
        s.text(Component.literal(fit(label, w - 60)), x + 58, y + 14, 1, s.color("$ink"));
    }

    private void buttons(float x, float y, float w, float h) {
        String quest = str(page, "quest");
        QuestDefs.Quest q = def(quest);
        if (q == null) return;
        JsonObject qs = st(quest);
        if (qs.has("pick") && !str(qs, "s").equals("locked")) {
            float by = y + (h - 52) / 2;
            if (!owner()) {
                s.ctext(tr("simplebooks.quest.owner_picks"), x + w / 2, by + 18, 1, s.color("$soft"));
                return;
            }
            String key = "path:" + quest;
            boolean conf = confirming.contains(key);
            Component lb = tr(conf ? "simplebooks.quest.take_confirm" : "simplebooks.quest.take_path");
            float bw = Math.max(240, buttonW(lb));
            button(x + (w - bw) / 2, by, bw, 52, lb, conf ? new Act("choose", quest, "", 0, "", null) : new Act("confirm", "", "", 0, key, null), null);
            return;
        }
        if (!hasRewards(q)) return;
        boolean done = qs.has("claimed");
        boolean claimed = done && qs.get("claimed").getAsBoolean();
        int tiers = q.tiers().size(), next = tiersClaimed(qs);
        boolean tiered = tiers > 0;
        Component lb = tiered && next < tiers ? tr("simplebooks.quest.claim_tier", tierName(q, next)) : tr("simplebooks.quest.claim");
        float bw = Math.max(240, buttonW(lb)), bx = x + (w - bw) / 2, by = y + (h - 52) / 2;
        if (claimed && (!tiered || next >= tiers)) {
            s.ctext(tr("simplebooks.quest.claimed"), x + w / 2, by + 18, 1, s.color("$done"));
            return;
        }
        if (!done || claimed) {
            button(bx, by, bw, 52, lb, null, tr(tiered ? "simplebooks.quest.claim_tier_later" : "simplebooks.quest.claim_later"));
            return;
        }
        int pick = chosen.getOrDefault(quest, -1);
        if (!q.chooseOne().isEmpty() && (!tiered || next == tiers - 1) && (pick < 0 || pick >= q.chooseOne().size())) {
            button(bx, by, bw, 52, lb, null, tr("simplebooks.quest.pick_first"));
            return;
        }
        button(bx, by, bw, 52, lb, new Act("claim", quest, "", pick, "", null), null);
    }

    // ---------- board ----------

    static List<String> offers(String pool) {
        JsonObject st = QuestClientStore.state();
        List<String> out = new ArrayList<>();
        if (st.has("pools") && st.getAsJsonObject("pools").has(pool))
            for (JsonElement e : st.getAsJsonObject("pools").getAsJsonArray(pool)) out.add(e.getAsString());
        return out;
    }

    /** Done pool quests no longer offered whose rewards are still waiting. */
    static List<QuestDefs.Quest> unclaimed(QuestDefs.Book qb, String pool) {
        List<String> on = offers(pool);
        List<QuestDefs.Quest> out = new ArrayList<>();
        for (QuestDefs.Quest q : qb.quests().values())
            if (pool.equals(q.pool()) && !on.contains(q.id()) && waiting(q)) out.add(q);
        out.sort(java.util.Comparator.comparing(QuestDefs.Quest::id));
        return out;
    }

    private void board(JsonObject slot, float x, float y, float w, float h) {
        String pool = str(page, "pool");
        QuestDefs.Book qb = bookOf(pool);
        if (qb == null) return;
        if (page.has("locked")) {
            lock(x, y, w, h);
            return;
        }
        float rh = rowHeight(slot);
        List<String> on = offers(pool);
        s.text(tr("simplebooks.board.offers"), x, y, 1, s.color("$head"));
        s.rule(x, x + w, y + 20);
        y += 34;
        if (on.isEmpty()) {
            s.text(tr("simplebooks.board.empty"), x, y + 8, 1, s.color("$soft"));
            return;
        }
        for (int i = 0; i < on.size() && (i + 1) * rh <= h - 34; i++) {
            QuestDefs.Quest q = qb.quests().get(on.get(i));
            if (q == null) continue;
            float ry = y + i * rh;
            s.fill(x, ry + 4, x + w, ry + rh - 4, s.color("$line") & 0x60FFFFFF);
            row(qb, q, x + 10, ry, w - 20, rh);
        }
    }

    private void boardInfo(float x, float y, float w, float h) {
        String pool = str(page, "pool");
        QuestDefs.Book qb = bookOf(pool);
        QuestDefs.Pool p = qb == null ? null : qb.pools().get(pool);
        if (p == null || page.has("locked")) return;
        float end = y + h;
        s.para(p.refresh() > 0 ? tr("simplebooks.board.refresh", dur(p.refresh())) : tr("simplebooks.board.no_refresh"), x, y + 4, w, 1, s.color("$soft"), 20, "left");
        y += 56;
        if (p.rerollAllowed()) {
            List<String> parts = new ArrayList<>();
            for (QuestDefs.Reward c : p.rerollCost()) {
                if (c.kind().equals("item") && c.item() != null) parts.add(tr("simplebooks.count", c.count(), ref(c.item()).name()).getString());
                else if (c.kind().equals("xp_levels")) parts.add(tr("simplebooks.reward.levels", c.count()).getString());
            }
            Component lb = tr("simplebooks.board.reroll");
            float bw = Math.max(200, buttonW(lb));
            boolean any = false;
            for (String id : offers(pool)) if (!str(st(id), "s").equals("complete")) any = true;
            button(x, y, bw, 48, lb, any ? new Act("reroll", pool, "", 0, "", null) : null, tr("simplebooks.board.empty"));
            Component note = parts.isEmpty() ? tr("simplebooks.board.reroll_free") : tr("simplebooks.board.reroll_cost", String.join(", ", parts));
            s.para(note, x + bw + 18, y + 4, w - bw - 18, 1, s.color("$soft"), 20, "left");
            y += 80;
        } else {
            s.text(tr("simplebooks.board.no_reroll"), x, y, 1, s.color("$soft"));
            y += 40;
        }
        List<QuestDefs.Quest> wait = unclaimed(qb, pool);
        if (wait.isEmpty()) return;
        s.text(tr("simplebooks.board.waiting"), x, y, 1, s.color("$head"));
        s.rule(x, x + w, y + 20);
        y += 30;
        for (QuestDefs.Quest q : wait) {
            if (y + 58 > end) break;
            row(qb, q, x, y, w, 58);
            y += 58;
        }
    }

    // ---------- team ----------

    private void team(String show, float x, float y, float w, float h) {
        JsonObject state = QuestClientStore.state();
        if (!state.has("team")) {
            s.text(tr("simplebooks.team.offline"), x, y + 8, 1, s.color("$soft"));
            return;
        }
        JsonObject t = state.getAsJsonObject("team");
        boolean owner = t.has("owner_is_me") && t.get("owner_is_me").getAsBoolean();
        boolean solo = t.has("solo") && t.get("solo").getAsBoolean();
        boolean board = t.has("scoreboard_mode") && t.get("scoreboard_mode").getAsBoolean();
        String mode = t.has("mode") ? t.get("mode").getAsString() : "scoreboard";
        String me = myName();
        Set<String> members = new HashSet<>();
        JsonArray ms = t.has("members") ? t.getAsJsonArray("members") : new JsonArray();
        for (JsonElement m : ms) members.add(m.getAsJsonObject().get("name").getAsString());
        float end = y + h;
        if (show.equals("players")) {
            if (board) {
                s.para(mode.equals("scoreboard") ? tr("simplebooks.team.scoreboard") : tr("simplebooks.team.external", mode), x, y + 8, w, 1, s.color("$soft"), 20, "left");
                return;
            }
            Set<String> invited = new HashSet<>();
            if (t.has("invites")) for (JsonElement e : t.getAsJsonArray("invites")) invited.add(e.getAsString());
            List<String> others = new ArrayList<>();
            for (String n : onlineNames()) if (!members.contains(n) && !n.equals(me)) others.add(n);
            if (others.isEmpty()) {
                s.text(tr("simplebooks.team.nobody"), x, y + 8, 1, s.color("$soft"));
                return;
            }
            for (String n : others) {
                if (y + 44 > end) break;
                s.text(Component.literal(fit(Component.literal(n), w - 200)), x, y + 12, 1, s.color("$ink"));
                if (invited.contains(n)) s.text(tr("simplebooks.team.invited"), x + w - s.textW(tr("simplebooks.team.invited"), 1), y + 12, 1, s.color("$soft"));
                else if (owner) {
                    Component lb = tr("simplebooks.team.invite");
                    float bw = buttonW(lb);
                    button(x + w - bw, y + 2, bw, 36, lb, new Act("team_invite", "", "", 0, n, null), null);
                }
                y += 48;
            }
            if (!owner && y + 44 <= end) s.para(tr("simplebooks.team.owner_invites"), x, y + 8, w, 1, s.color("$soft"), 20, "left");
            return;
        }
        Component name = solo || str(t, "name").isEmpty() ? tr("simplebooks.team.solo") : Texts.of(str(t, "name"));
        s.text(Component.literal(fit(name, w)), x, y, 1, s.color("$head"));
        s.rule(x, x + w, y + 20);
        y += 34;
        for (JsonElement e : ms) {
            if (y + 44 > end - 120) break;
            JsonObject m = e.getAsJsonObject();
            String n = m.get("name").getAsString();
            boolean on = m.has("online") && m.get("online").getAsBoolean(), mo = m.has("owner") && m.get("owner").getAsBoolean();
            s.fill(x + 2, y + 16, x + 14, y + 28, s.color(on ? "$done" : "$locked"));
            Component label = Component.literal(n);
            if (mo) label = label.copy().append(" ").append(tr("simplebooks.team.owner"));
            float right = x + w;
            if (owner && !board && !n.equals(me)) {
                Component kick = tr("simplebooks.team.kick"), give = tr("simplebooks.team.make_owner");
                float kw = buttonW(kick), gw = buttonW(give);
                button(right - kw, y + 4, kw, 36, kick, new Act("team_kick", "", "", 0, n, null), null);
                button(right - kw - 10 - gw, y + 4, gw, 36, give, new Act("team_owner", "", "", 0, n, null), null);
                right -= kw + gw + 24;
            }
            s.text(Component.literal(fit(label, right - x - 28)), x + 26, y + 14, 1, s.color(on ? "$ink" : "$soft"));
            y += 48;
        }
        if (board) {
            s.para(mode.equals("scoreboard") ? tr("simplebooks.team.scoreboard_note") : tr("simplebooks.team.external_note", mode), x, Math.max(y + 10, end - 110), w, 1, s.color("$soft"), 20, "left");
            return;
        }
        List<String> inv = new ArrayList<>();
        if (state.has("invited")) for (JsonElement e : state.getAsJsonArray("invited")) inv.add(e.getAsString());
        if (!inv.isEmpty()) {
            y += 10;
            s.text(tr("simplebooks.team.invites"), x, y, 1, s.color("$head"));
            s.rule(x, x + w, y + 20);
            y += 32;
            boolean progress = solo && hasProgress();
            for (String n : inv) {
                if (y + 44 > end - 70) break;
                boolean conf = confirming.contains(n);
                Component lb = tr(conf ? "simplebooks.team.confirm" : "simplebooks.team.join");
                float bw = buttonW(lb);
                Act act = progress && !conf ? new Act("confirm", "", "", 0, n, null) : new Act("team_join", "", "", 1, n, null);
                button(x + w - bw, y + 2, bw, 36, lb, act, null);
                s.text(Component.literal(fit(Texts.of(n), w - bw - 20)), x, y + 12, 1, s.color("$ink"));
                y += 48;
                if (conf) {
                    s.text(Component.literal(fit(tr("simplebooks.team.replace"), w)), x, y - 4, 1, s.color("$error"));
                    y += 22;
                }
            }
        }
        float by = end - 52;
        if (solo) {
            Component lb = tr("simplebooks.team.create");
            button(x, by, buttonW(lb), 44, lb, new Act("team_create", "", "", 0, me, null), null);
            s.text(Component.literal(fit(tr("simplebooks.team.create_hint"), w - buttonW(lb) - 20)), x + buttonW(lb) + 16, by + 14, 1, s.color("$soft"));
        } else {
            Component lb = tr("simplebooks.team.leave");
            button(x, by, buttonW(lb), 44, lb, new Act("team_leave", "", "", 0, "", null), null);
        }
    }

    private static boolean hasProgress() {
        JsonObject st = QuestClientStore.state();
        if (!st.has("quests")) return false;
        for (var e : st.getAsJsonObject("quests").entrySet()) {
            String s = str(e.getValue().getAsJsonObject(), "s");
            if (s.equals("complete") || s.equals("in_progress")) return true;
        }
        return false;
    }

    private static String myName() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return "";
        //? if >=26.1 {
        return mc.player.getGameProfile().name();
        //?} else {
        /*return mc.player.getGameProfile().getName();
        *///?}
    }

    private static List<String> onlineNames() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        List<String> out = new ArrayList<>();
        if (mc.getConnection() == null) return out;
        for (var pi : mc.getConnection().getOnlinePlayers()) {
            //? if >=26.1 {
            out.add(pi.getProfile().name());
            //?} else {
            /*out.add(pi.getProfile().getName());
            *///?}
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }
}
