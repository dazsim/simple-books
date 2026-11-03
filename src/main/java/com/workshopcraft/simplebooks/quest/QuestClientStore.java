package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.workshopcraft.simplebooks.SimpleBooks;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Quest definitions and the player's team and progress, as the server sent them. */
public final class QuestClientStore {
    private static final List<QuestParser.Entry> pending = new ArrayList<>();
    private static volatile QuestDefs defs = QuestDefs.EMPTY;
    private static volatile JsonObject state = new JsonObject();
    private static volatile int version;
    /** Set by the client entrypoint: shows a toast, opens a book. */
    public static Consumer<JsonObject> toasts = o -> {};
    public static Consumer<JsonObject> opens = o -> {};

    /** Team and quest progress; see QuestEngine.state for the shape. */
    public static JsonObject state() {
        return state;
    }

    /** Goes up whenever defs or state change, so screens know to redraw. */
    public static int version() {
        return version;
    }

    /** On disconnect: nothing from the last server is kept. */
    public static synchronized void clear() {
        pending.clear();
        defs = QuestDefs.EMPTY;
        state = new JsonObject();
        version++;
    }

    static void accept(QuestSyncPacket p) {
        JsonObject o;
        try {
            o = JsonParser.parseString(new String(p.json(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            SimpleBooks.LOG.error("Bad quest {} packet: {}", p.kind(), e.toString());
            return;
        }
        switch (p.kind()) {
            case "state" -> {
                state = o;
                version++;
            }
            case "toast" -> toasts.accept(o);
            case "open" -> opens.accept(o);
            default -> {}
        }
    }

    private QuestClientStore() {}

    public static QuestDefs defs() {
        return defs;
    }

    static synchronized void accept(QuestDefsPacket p) {
        if (p.index() == 0) {
            pending.clear();
        }
        try {
            JsonObject o = JsonParser.parseString(new String(p.json(), StandardCharsets.UTF_8)).getAsJsonObject();
            String book = o.has("book") ? o.get("book").getAsString() : null;
            if (book != null) {
                add(o, "chapters", QuestParser.Kind.CHAPTER, book);
                add(o, "quests", QuestParser.Kind.QUEST, book);
                add(o, "pools", QuestParser.Kind.POOL, book);
                if (o.has("broken")) for (var e : o.getAsJsonObject("broken").entrySet())
                    pending.add(new QuestParser.Entry(QuestParser.Kind.QUEST, book, e.getKey(), "server", null, e.getValue().getAsString()));
            }
        } catch (Exception e) {
            SimpleBooks.LOG.error("Bad quest definitions chunk {} of {}: {}", p.index() + 1, p.total(), e.toString());
        }
        if (p.index() + 1 >= p.total()) {
            Problems problems = new Problems(false);
            defs = QuestParser.parse(List.copyOf(pending), true, problems);
            pending.clear();
            version++;
            int quests = 0;
            for (QuestDefs.Book b : defs.books().values()) quests += b.quests().size();
            SimpleBooks.LOG.info("Quest definitions from server: {} books, {} quests, {} problems",
                    defs.books().size(), quests, problems.count(Problems.Level.ERROR, null));
        }
    }

    private static void add(JsonObject o, String key, QuestParser.Kind kind, String book) {
        if (!o.has(key)) return;
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet())
            pending.add(new QuestParser.Entry(kind, book, e.getKey(), "server", e.getValue(), null));
    }
}
