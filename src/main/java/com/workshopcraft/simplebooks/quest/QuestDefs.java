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
                       Map<String, String> broken) {
        /** In a story chapter, the quest a reader must finish first: the closest earlier quest that is not optional. */
        public @Nullable Quest storyPrev(Quest q) {
            Chapter c = q.chapter() == null ? null : chapters.get(q.chapter());
            if (c == null || !c.layout().equals("story")) return null;
            Quest best = null;
            for (Quest o : quests.values()) {
                if (o == q || o.optional() || !q.chapter().equals(o.chapter()) || order(o, q) >= 0) continue;
                if (best == null || order(o, best) > 0) best = o;
            }
            return best;
        }

        private static int order(Quest a, Quest b) {
            return a.sort() != b.sort() ? Integer.compare(a.sort(), b.sort()) : a.id().compareTo(b.id());
        }
    }

    public record Chapter(String id, String title, String icon, int sort, String layout, List<String> depends,
                          String visibility, String text, @Nullable String pool, @Nullable Background background,
                          List<Decoration> decorations, JsonObject json) {}

    public record Quest(String id, @Nullable String chapter, @Nullable String pool, int sort, int @Nullable [] pos,
                        String title, String subtitle, String icon, String text, @Nullable JsonObject page,
                        List<String> depends, String dependsMode, String visibility, boolean optional,
                        List<Task> tasks, String taskMode, List<Reward> rewards, List<Reward> chooseOne,
                        @Nullable Repeat repeat, List<Tier> tiers, @Nullable Exclusive exclusive, String toast,
                        @Nullable Background background, JsonObject json) {}

    /** Custom (namespaced) types keep their fields in json; spec is what their TaskType parsed (null on a client without it). */
    public record Task(String id, String type, List<String> items, long count, boolean consume, String handIn,
                       boolean teamTotal, boolean fromStats, @Nullable JsonElement match, @Nullable String block,
                       @Nullable String entity, @Nullable String dimension, @Nullable String biome,
                       @Nullable String advancement, @Nullable String stat, @Nullable String value,
                       @Nullable String text, JsonObject json, com.workshopcraft.simplebooks.api.@Nullable TaskSpec spec) {}

    /**
     * kind is item, xp, xp_levels, command, loot_table, or a namespaced custom type id.
     * spec is set for custom types when the providing mod is installed (null on a client without it).
     */
    public record Reward(String kind, @Nullable String item, long count, @Nullable JsonElement data,
                         @Nullable String command, @Nullable String label, @Nullable String icon,
                         @Nullable String lootTable, JsonObject json,
                         com.workshopcraft.simplebooks.api.@Nullable RewardSpec spec) {}

    public record Tier(long count, List<Reward> rewards, @Nullable String title) {}

    public record Repeat(long cooldown, int limit) {}

    public record Exclusive(String group, String lockOn) {}

    public record Pool(String id, int offers, long refresh, boolean rerollAllowed, List<Reward> rerollCost,
                       List<PoolEntry> entries, JsonObject json) {}

    public record PoolEntry(String quest, int weight, List<String> depends) {}

    /** Optional page or chapter backdrop. mode is stretch or tile; tile is the tile width in spread units when tiling. */
    public record Background(String texture, String mode, float tile) {}

    /**
     * A free-placed image or text on a tree chapter. Coordinates are in the tree's own space (same units as
     * quest pos cells: cell (0,0) is at 0,0), so they scroll with the graph. layer is behind or front of link lines.
     */
    public record Decoration(@Nullable String image, @Nullable String text, float x, float y, float width, float height,
                            float scale, @Nullable String colour, boolean centred, String layer) {}
}
