package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.workshopcraft.simplebooks.SimpleBooks;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Quest definitions as the server sent them. Nothing reads them yet in phase 0. */
public final class QuestClientStore {
    private static final List<QuestParser.Entry> pending = new ArrayList<>();
    private static volatile QuestDefs defs = QuestDefs.EMPTY;

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
