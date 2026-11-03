package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Parsed quest books. Ids are plain strings ("ns:book/path") so this is the same on every version.
 * The json fields hold the cleaned entry as sent to clients.
 */
public record QuestDefs(Map<String, Book> books) {
    public static final QuestDefs EMPTY = new QuestDefs(Map.of());

    public int quests() {
        return books.values().stream().mapToInt(b -> b.quests.size()).sum();
    }

    public record Book(String id, Map<String, Chapter> chapters, Map<String, Quest> quests, Map<String, Pool> pools,
                       Map<String, String> broken) {}

    public record Chapter(String id, String title, String icon, int sort, String layout, List<String> depends,
                          String visibility, String text, @Nullable String pool, JsonObject json) {}

    public record Quest(String id, @Nullable String chapter, @Nullable String pool, int sort, int @Nullable [] pos,
                        String title, String subtitle, String icon, String text, @Nullable JsonObject page,
                        List<String> depends, String dependsMode, String visibility, boolean optional,
                        List<Task> tasks, String taskMode, List<Reward> rewards, List<Reward> chooseOne,
                        @Nullable Repeat repeat, List<Tier> tiers, @Nullable Exclusive exclusive, String toast,
                        JsonObject json) {}

    /** Custom (namespaced) types keep their fields in json; spec is what their TaskType parsed (null on a client without it). */
    public record Task(String id, String type, List<String> items, long count, boolean consume, String handIn,
                       boolean teamTotal, boolean fromStats, @Nullable JsonElement match, @Nullable String block,
                       @Nullable String entity, @Nullable String dimension, @Nullable String biome,
                       @Nullable String advancement, @Nullable String stat, @Nullable String value,
                       @Nullable String text, JsonObject json, com.workshopcraft.simplebooks.api.@Nullable TaskSpec spec) {}

    /** kind is item, xp, xp_levels, command or loot_table. */
    public record Reward(String kind, @Nullable String item, long count, @Nullable JsonElement data,
                         @Nullable String command, @Nullable String label, @Nullable String icon,
                         @Nullable String lootTable, JsonObject json) {}

    public record Repeat(long cooldown, int limit) {}

    public record Tier(long count, List<Reward> rewards, @Nullable String title) {}

    public record Exclusive(String group, String lockOn) {}

    public record Pool(String id, int offers, long refresh, boolean rerollAllowed, List<Reward> rerollCost,
                       List<PoolEntry> entries, JsonObject json) {}

    public record PoolEntry(String quest, int weight, List<String> depends) {}
}
