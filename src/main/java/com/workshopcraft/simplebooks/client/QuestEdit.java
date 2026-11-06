package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.quest.QuestClientStore;
import com.workshopcraft.simplebooks.quest.QuestDefs;
import com.workshopcraft.simplebooks.quest.QuestEditPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Client-side quest book edit mode: toggle, selection, tree ops, and sends to the server. */
final class QuestEdit {
    record Node(String id) {}
    record Cell(String chapter, int col, int row) {}
    record Tool(String op) {}
    record Deco(String chapter, int index) {}
    record DecoResize(String chapter, int index) {}

    static boolean on;
    static @Nullable String selected;
    static @Nullable String linkFrom;
    static @Nullable String dragId;
    static @Nullable String bookId;
    static @Nullable String status;
    static @Nullable String decoChapter;
    static int decoSel = -1;
    static boolean decoDragging, decoResizing;
    /** Live overrides while dragging: index -> {x,y,w,h}. */
    static final Map<Integer, float[]> decoPos = new HashMap<>();

    private QuestEdit() {}

    static boolean canEdit() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        JsonObject st = QuestClientStore.state();
        if (st.has("can_edit")) return st.get("can_edit").getAsBoolean();
        // fallback before the first state packet
        if (p.isCreative()) return true;
        //? if >=26.1 {
        var mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            var s = mc.getSingleplayerServer();
            return s != null && s.getPlayerList().isOp(p.nameAndId());
        }
        return false;
        //?} else {
        /*return p.hasPermissions(2);
        *///?}
    }

    static boolean active() {
        return on && canEdit();
    }

    static void toggle(Identifier book) {
        if (!canEdit()) {
            status = "Need creative or op to edit.";
            return;
        }
        on = !on;
        bookId = book.toString();
        selected = null;
        linkFrom = null;
        dragId = null;
        decoChapter = null;
        decoSel = -1;
        decoDragging = decoResizing = false;
        decoPos.clear();
        status = on ? "Edit mode on. Drag nodes or decorations, click empty cell to add, L to link, Del to delete, U to undo." : "Edit mode off.";
    }

    static boolean press(Object target) {
        if (!active()) return false;
        if (target instanceof Tool t) {
            switch (t.op()) {
                case "undo" -> {
                    SimpleBooks.toServerEdit.accept(new QuestEditPacket("undo", "", ""));
                    status = "Undo sent.";
                }
                case "link" -> {
                    if (selected == null) status = "Select a quest first, then Link, then the target.";
                    else {
                        linkFrom = selected;
                        status = "Click a quest to depend on " + shortName(selected) + ".";
                    }
                }
                case "unlink" -> {
                    if (selected == null || linkFrom == null && selected != null) {
                        // unlink: if two selected via linkFrom->selected
                    }
                    if (linkFrom != null && selected != null && !linkFrom.equals(selected)) {
                        unlink(linkFrom, selected);
                        linkFrom = null;
                    } else if (selected != null) {
                        linkFrom = selected;
                        status = "Click a dependency of " + shortName(selected) + " to unlink.";
                    } else status = "Select a quest, Unlink, then its dependency.";
                }
                case "delete" -> {
                    if (selected == null) status = "Select a quest to delete.";
                    else deleteQuest(selected);
                }
                case "props" -> {
                    if (selected == null) status = "Select a quest to edit.";
                    else Minecraft.getInstance().setScreen(new QuestEditScreen(selected, Minecraft.getInstance().screen));
                }
                case "chapter" -> {
                    String ch = selected == null ? null : chapterOf(selected);
                    if (ch == null) status = "Select a quest in the chapter to edit.";
                    else Minecraft.getInstance().setScreen(new QuestChapterEditScreen(ch, Minecraft.getInstance().screen));
                }
                case "pool" -> {
                    String pool = selected == null ? null : poolOf(selected);
                    if (pool == null) status = "Select a board/pool quest, or a quest in a board chapter.";
                    else Minecraft.getInstance().setScreen(new QuestPoolEditScreen(pool, Minecraft.getInstance().screen));
                }
                case "add_image" -> addDecoration(true);
                case "add_text" -> addDecoration(false);
                case "deco_layer" -> toggleDecoLayer();
                case "deco_delete" -> deleteDeco();
                default -> {}
            }
            return true;
        }
        if (target instanceof Node n) {
            if (linkFrom != null && !linkFrom.equals(n.id())) {
                if (isDependency(n.id(), linkFrom) || status != null && status.startsWith("Click a dependency"))
                    unlink(n.id(), linkFrom);
                else link(linkFrom, n.id());
                linkFrom = null;
                selected = n.id();
                return true;
            }
            selected = n.id();
            dragId = n.id();
            status = shortName(n.id()) + " selected.";
            return true;
        }
        if (target instanceof Cell c) {
            if (dragId != null) {
                moveQuest(dragId, c.col(), c.row());
                dragId = null;
                return true;
            }
            addQuest(c.chapter(), c.col(), c.row());
            return true;
        }
        if (target instanceof Deco d) {
            decoChapter = d.chapter();
            decoSel = d.index();
            decoDragging = true;
            decoResizing = false;
            status = "Decoration " + (d.index() + 1) + " selected.";
            return true;
        }
        if (target instanceof DecoResize d) {
            decoChapter = d.chapter();
            decoSel = d.index();
            decoDragging = false;
            decoResizing = true;
            status = "Resize decoration " + (d.index() + 1) + ".";
            return true;
        }
        return false;
    }

    static void dragEnd(@Nullable Cell cell) {
        if (!active()) return;
        if (dragId != null) {
            if (cell != null) moveQuest(dragId, cell.col(), cell.row());
            dragId = null;
        }
        if (decoDragging || decoResizing) {
            saveDecorations();
            decoDragging = decoResizing = false;
        }
    }

    /** Tree-space drag delta for the selected decoration. */
    static void decoDrag(float treeDx, float treeDy) {
        if (!active() || decoSel < 0 || decoChapter == null) return;
        float[] cur = decoPos.computeIfAbsent(decoSel, i -> {
            QuestDefs.Decoration d = decoAt(decoChapter, i);
            return d == null ? new float[]{0, 0, 64, 64} : new float[]{d.x(), d.y(), Math.max(8, d.width()), Math.max(8, d.height())};
        });
        if (decoResizing) {
            cur[2] = Math.max(8, cur[2] + treeDx);
            cur[3] = Math.max(8, cur[3] + treeDy);
        } else if (decoDragging) {
            cur[0] += treeDx;
            cur[1] += treeDy;
        }
    }

    private static void addDecoration(boolean image) {
        String ch = decoChapter != null ? decoChapter : (selected == null ? null : chapterOf(selected));
        if (ch == null) { status = "Select a quest in the chapter first."; return; }
        decoChapter = ch;
        QuestDefs.Chapter c = chapter(ch);
        if (c == null) return;
        JsonObject o = c.json().deepCopy();
        JsonArray arr = o.has("decorations") && o.get("decorations").isJsonArray() ? o.getAsJsonArray("decorations") : new JsonArray();
        JsonObject d = new JsonObject();
        if (image) {
            d.addProperty("image", "minecraft:textures/gui/sprites/widget/button.png");
            d.addProperty("x", 0);
            d.addProperty("y", -40);
            d.addProperty("width", 64);
            d.addProperty("height", 32);
            d.addProperty("layer", "behind");
        } else {
            d.addProperty("text", "Label");
            d.addProperty("x", 40);
            d.addProperty("y", -20);
            d.addProperty("centred", true);
            d.addProperty("layer", "front");
        }
        arr.add(d);
        o.add("decorations", arr);
        send("put_chapter", ch, o);
        decoSel = arr.size() - 1;
        decoPos.clear();
        status = "Added " + (image ? "image" : "text") + " decoration.";
    }

    private static void toggleDecoLayer() {
        if (decoChapter == null || decoSel < 0) { status = "Select a decoration first."; return; }
        QuestDefs.Chapter c = chapter(decoChapter);
        if (c == null) return;
        JsonObject o = c.json().deepCopy();
        if (!o.has("decorations") || !o.get("decorations").isJsonArray()) return;
        JsonArray arr = o.getAsJsonArray("decorations");
        if (decoSel >= arr.size()) return;
        JsonObject d = arr.get(decoSel).getAsJsonObject();
        String layer = d.has("layer") ? d.get("layer").getAsString() : "behind";
        d.addProperty("layer", layer.equals("front") ? "behind" : "front");
        send("put_chapter", decoChapter, o);
        status = "Decoration layer: " + d.get("layer").getAsString() + ".";
    }

    private static void deleteDeco() {
        if (decoChapter == null || decoSel < 0) { status = "Select a decoration first."; return; }
        QuestDefs.Chapter c = chapter(decoChapter);
        if (c == null) return;
        JsonObject o = c.json().deepCopy();
        if (!o.has("decorations") || !o.get("decorations").isJsonArray()) return;
        JsonArray arr = o.getAsJsonArray("decorations");
        if (decoSel >= arr.size()) return;
        arr.remove(decoSel);
        if (arr.size() == 0) o.remove("decorations");
        else o.add("decorations", arr);
        send("put_chapter", decoChapter, o);
        decoSel = -1;
        decoPos.clear();
        status = "Deleted decoration.";
    }

    private static void saveDecorations() {
        if (decoChapter == null || decoPos.isEmpty()) return;
        QuestDefs.Chapter c = chapter(decoChapter);
        if (c == null) return;
        JsonObject o = c.json().deepCopy();
        if (!o.has("decorations") || !o.get("decorations").isJsonArray()) return;
        JsonArray arr = o.getAsJsonArray("decorations");
        for (var e : decoPos.entrySet()) {
            int i = e.getKey();
            if (i < 0 || i >= arr.size()) continue;
            JsonObject d = arr.get(i).getAsJsonObject();
            float[] p = e.getValue();
            d.addProperty("x", Math.round(p[0]));
            d.addProperty("y", Math.round(p[1]));
            if (d.has("image")) {
                d.addProperty("width", Math.round(p[2]));
                d.addProperty("height", Math.round(p[3]));
            }
        }
        send("put_chapter", decoChapter, o);
        decoPos.clear();
        status = "Saved decorations.";
    }

    static QuestDefs.@Nullable Decoration decoAt(String chapterId, int index) {
        QuestDefs.Chapter c = chapter(chapterId);
        if (c == null || index < 0 || index >= c.decorations().size()) return null;
        return c.decorations().get(index);
    }

    private static QuestDefs.@Nullable Chapter chapter(String id) {
        for (QuestDefs.Book b : QuestClientStore.defs().books().values()) {
            QuestDefs.Chapter c = b.chapters().get(id);
            if (c != null) return c;
        }
        return null;
    }

    static void key(int key) {
        if (!active()) return;
        // Del / Backspace delete, L link, U undo, E props
        if (key == 261 || key == 259) { // delete / backspace
            if (decoSel >= 0) deleteDeco();
            else if (selected != null) deleteQuest(selected);
        } else if (key == 76) { // L
            press(new Tool("link"));
        } else if (key == 85) { // U
            press(new Tool("undo"));
        } else if (key == 69) { // E
            press(new Tool("props"));
        }
    }

    private static void addQuest(String chapterId, int col, int row) {
        String shortCh = chapterId.contains("/") ? chapterId.substring(chapterId.indexOf('/') + 1) : chapterId;
        String base = chapterId + "/node_" + col + "_" + row;
        String id = base;
        boolean taken = false;
        for (QuestDefs.Book b : QuestClientStore.defs().books().values())
            if (b.quests().containsKey(base)) { taken = true; break; }
        if (taken) id = base + "_" + (System.currentTimeMillis() % 1000);
        JsonObject o = new JsonObject();
        o.addProperty("chapter", shortCh);
        o.addProperty("title", "New quest");
        o.addProperty("icon", "minecraft:book");
        JsonArray pos = new JsonArray();
        pos.add(col);
        pos.add(row);
        o.add("pos", pos);
        JsonArray tasks = new JsonArray();
        JsonObject t = new JsonObject();
        t.addProperty("id", "todo");
        t.addProperty("type", "checkbox");
        t.addProperty("text", "Finish this quest");
        tasks.add(t);
        o.add("tasks", tasks);
        send("put_quest", id, o);
        selected = id;
        status = "Added " + shortName(id) + " at " + col + "," + row + ".";
    }

    private static void moveQuest(String id, int col, int row) {
        QuestDefs.Quest q = quest(id);
        if (q == null) return;
        JsonObject o = q.json().deepCopy();
        JsonArray pos = new JsonArray();
        pos.add(col);
        pos.add(row);
        o.add("pos", pos);
        // keep chapter as short name in file when possible
        if (o.has("chapter")) {
            String ch = o.get("chapter").getAsString();
            if (ch.contains("/")) o.addProperty("chapter", ch.substring(ch.indexOf('/') + 1));
        }
        send("put_quest", id, o);
        status = "Moved " + shortName(id) + " to " + col + "," + row + ".";
    }

    private static void deleteQuest(String id) {
        // drop depends pointing at this from other quests first (best-effort local fixes)
        QuestDefs.Quest q = quest(id);
        if (q == null) return;
        String book = id.substring(0, id.indexOf('/'));
        QuestDefs.Book b = QuestClientStore.defs().books().get(book);
        if (b != null) {
            for (QuestDefs.Quest other : b.quests().values()) {
                if (other.id().equals(id)) continue;
                if (!other.depends().contains(id) && !other.depends().contains(shortName(id))) continue;
                JsonObject o = other.json().deepCopy();
                if (o.has("depends") && o.get("depends").isJsonArray()) {
                    JsonArray next = new JsonArray();
                    String shortDep = shortName(id);
                    for (var e : o.getAsJsonArray("depends")) {
                        String d = e.getAsString();
                        if (!d.equals(id) && !d.equals(shortDep) && !id.endsWith("/" + d)) next.add(e);
                    }
                    o.add("depends", next);
                    if (o.has("chapter")) {
                        String ch = o.get("chapter").getAsString();
                        if (ch.contains("/")) o.addProperty("chapter", ch.substring(ch.indexOf('/') + 1));
                    }
                    send("put_quest", other.id(), o);
                }
            }
        }
        SimpleBooks.toServerEdit.accept(new QuestEditPacket("delete_quest", id, ""));
        selected = null;
        status = "Deleted " + shortName(id) + ".";
    }

    private static void link(String from, String to) {
        // to depends on from
        QuestDefs.Quest q = quest(to);
        if (q == null) return;
        JsonObject o = q.json().deepCopy();
        JsonArray deps = o.has("depends") && o.get("depends").isJsonArray() ? o.getAsJsonArray("depends").deepCopy() : new JsonArray();
        String shortFrom = shortName(from);
        for (var e : deps) if (e.getAsString().equals(from) || e.getAsString().equals(shortFrom)) {
            status = shortName(to) + " already depends on " + shortFrom + ".";
            return;
        }
        deps.add(shortFrom);
        o.add("depends", deps);
        if (o.has("chapter")) {
            String ch = o.get("chapter").getAsString();
            if (ch.contains("/")) o.addProperty("chapter", ch.substring(ch.indexOf('/') + 1));
        }
        send("put_quest", to, o);
        status = "Linked " + shortFrom + " -> " + shortName(to) + ".";
    }

    private static void unlink(String dep, String questId) {
        QuestDefs.Quest q = quest(questId);
        if (q == null) return;
        JsonObject o = q.json().deepCopy();
        if (!o.has("depends") || !o.get("depends").isJsonArray()) return;
        JsonArray next = new JsonArray();
        String shortDep = shortName(dep);
        boolean removed = false;
        for (var e : o.getAsJsonArray("depends")) {
            String d = e.getAsString();
            if (d.equals(dep) || d.equals(shortDep) || dep.endsWith("/" + d)) { removed = true; continue; }
            next.add(e);
        }
        if (!removed) {
            status = shortName(questId) + " does not depend on " + shortDep + ".";
            return;
        }
        o.add("depends", next);
        if (o.has("chapter")) {
            String ch = o.get("chapter").getAsString();
            if (ch.contains("/")) o.addProperty("chapter", ch.substring(ch.indexOf('/') + 1));
        }
        send("put_quest", questId, o);
        status = "Unlinked " + shortDep + " from " + shortName(questId) + ".";
    }

    private static boolean isDependency(String dep, String questId) {
        QuestDefs.Quest q = quest(questId);
        if (q == null) return false;
        String shortDep = shortName(dep);
        for (String d : q.depends()) if (d.equals(dep) || d.equals(shortDep) || dep.endsWith("/" + d)) return true;
        return false;
    }

    private static void send(String op, String id, JsonObject body) {
        SimpleBooks.toServerEdit.accept(new QuestEditPacket(op, id, body.toString()));
    }

    private static QuestDefs.@Nullable Quest quest(String id) {
        for (QuestDefs.Book b : QuestClientStore.defs().books().values()) {
            QuestDefs.Quest q = b.quests().get(id);
            if (q != null) return q;
        }
        return null;
    }

    private static @Nullable String chapterOf(String questId) {
        QuestDefs.Quest q = quest(questId);
        return q == null ? null : q.chapter();
    }

    private static @Nullable String poolOf(String questId) {
        QuestDefs.Quest q = quest(questId);
        if (q == null) return null;
        if (q.pool() != null) return q.pool();
        String chId = q.chapter();
        if (chId == null) return null;
        for (QuestDefs.Book b : QuestClientStore.defs().books().values()) {
            QuestDefs.Chapter c = b.chapters().get(chId);
            if (c != null && c.pool() != null) return c.pool();
            if (c != null && "board".equals(c.layout())) return chId; // pool id often matches chapter
        }
        return null;
    }

    static String shortName(String id) {
        int slash = id.lastIndexOf('/');
        return slash < 0 ? id : id.substring(slash + 1);
    }

    static @Nullable Component tip() {
        return status == null || status.isEmpty() ? null : Component.literal(status);
    }
}
