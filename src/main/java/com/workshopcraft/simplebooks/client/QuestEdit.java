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

/** Client-side quest book edit mode: toggle, selection, tree ops, and sends to the server. */
final class QuestEdit {
    record Node(String id) {}
    record Cell(String chapter, int col, int row) {}
    record Tool(String op) {}

    static boolean on;
    static @Nullable String selected;
    static @Nullable String linkFrom;
    static @Nullable String dragId;
    static @Nullable String bookId;
    static @Nullable String status;

    private QuestEdit() {}

    static boolean canEdit() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
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
        status = on ? "Edit mode on. Drag nodes, click empty cell to add, L to link, Del to delete, U to undo." : "Edit mode off.";
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
        return false;
    }

    static void dragEnd(@Nullable Cell cell) {
        if (!active() || dragId == null) return;
        if (cell != null) moveQuest(dragId, cell.col(), cell.row());
        dragId = null;
    }

    static void key(int key) {
        if (!active()) return;
        // Del / Backspace delete, L link, U undo, E props
        if (key == 261 || key == 259) { // delete / backspace
            if (selected != null) deleteQuest(selected);
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
        o.add("tasks", new JsonArray());
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
