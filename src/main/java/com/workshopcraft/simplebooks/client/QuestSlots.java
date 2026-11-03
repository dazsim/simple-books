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

/** The quest slot types: quest_list, task_list, reward_list, quest_buttons and team_list. */
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
            case "team_list" -> team(slot.has("show") ? slot.get("show").getAsString() : "members", x, y, w, h);
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
        return "complete".equals(str(qs, "s")) && qs.has("claimed") && !qs.get("claimed").getAsBoolean() && hasRewards(q);
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
        if (qs.isEmpty() && name.startsWith("left.")) {
            s.text(tr("simplebooks.quest.none"), x, y + 8, 1, s.color("$soft"));
            return;
        }
        for (int i = 0; i < rows && from + i < qs.size(); i++) row(qb, qs.get(from + i), x, y + i * rh, w, rh);
    }

    private void row(QuestDefs.Book qb, QuestDefs.Quest q, float x, float y, float w, float rh) {
        JsonObject qs = st(q.id());
        String status = str(qs, "s");
        boolean done = status.equals("complete"), locked = status.equals("locked");
        int ink = s.color(done ? "$done" : locked ? "$locked" : "$ink");
        float by = y + (rh - 28) / 2;
        boolean tick = !done && !locked && q.tasks().size() == 1 && q.tasks().get(0).type().equals("checkbox");
        if (tick && over(x, by, x + 28, by + 28)) s.fill(x - 3, by - 3, x + 31, by + 31, s.color("$rule"));
        box(x, by, 28, done, ink);
        if (tick) s.hits.add(new Spread.Hit(x, by, x + 28, by + 28,
                new Act("checkbox", q.id(), q.tasks().get(0).id(), 0, "", tr("simplebooks.quest.tick"))));
        float tx = x + 44;
        if (!q.icon().isEmpty()) {
            s.item(ref(q.icon()), tx, y + (rh - 32) / 2, 32);
            tx += 44;
        }
        boolean wait = waiting(q);
        float right = x + w - (wait ? 40 : 4);
        Component title = Texts.of(q.title());
        if (q.optional()) title = title.copy().append(" ").append(tr("simplebooks.quest.optional"));
        s.text(Component.literal(fit(title, right - tx)), tx, y + rh / 2 - 19, 1, ink);
        Component sub = sub(qb, q, qs, status);
        int subCol = s.color(wait ? "$claim" : locked ? "$locked" : "$soft");
        s.text(Component.literal(fit(sub, right - tx)), tx, y + rh / 2 + 2, 1, subCol);
        if (wait) {
            float bx = x + w - 30, cy = y + (rh - 26) / 2;
            s.roundRect(bx, cy, bx + 26, cy + 26, 13, s.color("$claim"));
            s.ctext(Component.literal("!"), bx + 13, cy + 5, 1, s.color("$badge_text"));
        }
        if (over(x, y, x + w, y + rh) && !(tick && over(x, by, x + 28, by + 28))) s.rule(tx, right, y + rh - 4);
        s.hits.add(new Spread.Hit(x, y, x + w, y + rh, QuestBook.detailId(q.id())));
    }

    private Component sub(QuestDefs.Book qb, QuestDefs.Quest q, JsonObject qs, String status) {
        switch (status) {
            case "locked" -> {
                List<String> need = new ArrayList<>();
                for (String d : q.depends()) {
                    if (str(st(d), "s").equals("complete")) continue;
                    QuestDefs.Quest dq = qb.quests().get(d);
                    need.add(dq == null || str(st(d), "s").equals("hidden") ? tr("simplebooks.quest.hidden_dep").getString() : Texts.of(dq.title()).getString());
                }
                if (need.isEmpty()) return tr("simplebooks.quest.locked");
                return tr(q.dependsMode().equals("any") ? "simplebooks.quest.needs_any" : "simplebooks.quest.needs", String.join(", ", need));
            }
            case "complete" -> {
                if (waiting(q)) return tr("simplebooks.quest.rewards_waiting");
                return tr("simplebooks.quest.done");
            }
            default -> {
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
        boolean open = status.equals("available") || status.equals("in_progress");
        Component state = switch (status) {
            case "complete" -> tr("simplebooks.quest.state.complete");
            case "in_progress" -> tr("simplebooks.quest.state.in_progress");
            case "available" -> tr("simplebooks.quest.state.available");
            default -> sub(qb, q, qs, "locked");
        };
        s.text(Component.literal(fit(state, w)), x, y, 1, s.color(status.equals("complete") ? "$done" : status.equals("locked") ? "$locked" : "$soft"));
        y += 30;
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
        for (QuestDefs.Reward r : q.rewards()) {
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
        for (QuestDefs.Tier t : q.tiers()) {
            if (t.rewards().isEmpty() || y + 80 > end) continue;
            y += 10;
            Component head = t.title() != null ? Texts.of(t.title()) : tr("simplebooks.quest.tier", t.count());
            s.text(head, x, y, 1, s.color("$head"));
            s.rule(x, x + w, y + 20);
            y += 30;
            for (QuestDefs.Reward r : t.rewards()) {
                if (y + 50 > end) return;
                reward(r, x, y, w);
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
        if (q == null || !hasRewards(q)) return;
        JsonObject qs = st(quest);
        boolean done = str(qs, "s").equals("complete");
        boolean claimed = qs.has("claimed") && qs.get("claimed").getAsBoolean();
        Component lb = tr("simplebooks.quest.claim");
        float bw = Math.max(240, buttonW(lb)), bx = x + (w - bw) / 2, by = y + (h - 52) / 2;
        if (claimed) {
            s.ctext(tr("simplebooks.quest.claimed"), x + w / 2, by + 18, 1, s.color("$done"));
            return;
        }
        if (!done) {
            button(bx, by, bw, 52, lb, null, tr("simplebooks.quest.claim_later"));
            return;
        }
        int pick = chosen.getOrDefault(quest, -1);
        if (!q.chooseOne().isEmpty() && (pick < 0 || pick >= q.chooseOne().size())) {
            button(bx, by, bw, 52, lb, null, tr("simplebooks.quest.pick_first"));
            return;
        }
        button(bx, by, bw, 52, lb, new Act("claim", quest, "", pick, "", null), null);
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
        String me = myName();
        Set<String> members = new HashSet<>();
        JsonArray ms = t.has("members") ? t.getAsJsonArray("members") : new JsonArray();
        for (JsonElement m : ms) members.add(m.getAsJsonObject().get("name").getAsString());
        float end = y + h;
        if (show.equals("players")) {
            if (board) {
                s.para(tr("simplebooks.team.scoreboard"), x, y + 8, w, 1, s.color("$soft"), 20, "left");
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
            s.para(tr("simplebooks.team.scoreboard_note"), x, Math.max(y + 10, end - 110), w, 1, s.color("$soft"), 20, "left");
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
