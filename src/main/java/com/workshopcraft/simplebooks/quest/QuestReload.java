package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonParser;
import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Loads data/NS/simplebooks/quests/BOOK/{chapters,quests,pools}/**.json on every data reload. */
public class QuestReload extends SimplePreparableReloadListener<QuestDefs> {
    public static final Identifier ID = SimpleBooks.id("quests");
    static final String ROOT = "simplebooks/quests";

    @Override
    protected QuestDefs prepare(ResourceManager manager, ProfilerFiller profiler) {
        return load(manager, new Problems(true));
    }

    @Override
    protected void apply(QuestDefs defs, ResourceManager manager, ProfilerFiller profiler) {
        QuestManager.set(defs);
    }

    static QuestDefs load(ResourceManager manager, Problems problems) {
        List<QuestParser.Entry> entries = new ArrayList<>();
        for (var e : manager.listResources(ROOT, id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier key = e.getKey();
            String file = "data/" + key.getNamespace() + "/" + key.getPath() + " (" + e.getValue().sourcePackId() + ")";
            String rel = key.getPath().substring(ROOT.length() + 1, key.getPath().length() - 5);
            String[] parts = rel.split("/", 3);
            QuestParser.Kind kind = parts.length < 3 ? null : switch (parts[1]) {
                case "chapters" -> QuestParser.Kind.CHAPTER;
                case "quests" -> QuestParser.Kind.QUEST;
                case "pools" -> QuestParser.Kind.POOL;
                default -> null;
            };
            String book = key.getNamespace() + ":" + parts[0];
            if (kind == null) {
                problems.add(Problems.Level.WARN, book, file, "", "not in a chapters, quests or pools folder, ignored");
                continue;
            }
            String id = book + "/" + parts[2];
            try (Reader r = e.getValue().openAsReader()) {
                entries.add(new QuestParser.Entry(kind, book, id, file, JsonParser.parseReader(r), null));
            } catch (Exception ex) {
                entries.add(new QuestParser.Entry(kind, book, id, file, null, ex.getMessage()));
            }
        }
        return QuestParser.parse(entries, false, problems);
    }
}
