package com.workshopcraft.simplebooks.api;

import com.workshopcraft.simplebooks.quest.QuestEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** The public quest API. Ids are plain strings such as "mymod:power_produced". */
public final class SimpleBooksApi {
    private static final Map<String, TaskType> TYPES = new ConcurrentHashMap<>();
    private static final Map<String, TeamSource> TEAM_SOURCES = new ConcurrentHashMap<>();

    static {
        TEAM_SOURCES.put("scoreboard", com.workshopcraft.simplebooks.quest.QuestMc::scoreboardTeam);
    }

    private SimpleBooksApi() {}

    /** Register during mod init (or from a SimpleBooksPlugin), before data packs load. */
    public static void registerTaskType(String id, TaskType type) {
        if (!id.contains(":")) throw new IllegalArgumentException("task type id needs a namespace: " + id);
        if (TYPES.putIfAbsent(id, type) != null) throw new IllegalStateException("task type " + id + " is already registered");
    }

    public static @Nullable TaskType taskType(String id) {
        return TYPES.get(id);
    }

    /**
     * Adds a team source that packs can pick with "team_source" in config/simplebooks.json, for example a party mod.
     * Register during mod init or from a SimpleBooksPlugin, before the server starts. "simplebooks" is reserved.
     */
    public static void registerTeamSource(String id, TeamSource source) {
        if (id.equals("simplebooks") || !id.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("bad team source id: " + id);
        if (TEAM_SOURCES.putIfAbsent(id, source) != null) throw new IllegalStateException("team source " + id + " is already registered");
    }

    public static @Nullable TeamSource teamSource(String id) {
        return TEAM_SOURCES.get(id);
    }

    public static java.util.Set<String> teamSources() {
        return java.util.Set.copyOf(TEAM_SOURCES.keySet());
    }

    /** Adds amount to the open tasks of this type in the player's team that the filter accepts. Server thread only. */
    public static void progress(ServerPlayer player, String type, long amount, Predicate<TaskSpec> filter) {
        QuestEngine.custom(com.workshopcraft.simplebooks.quest.QuestMc.server(player), player.getUUID(), type, amount, filter);
    }

    /** Same, for whichever team the player with this UUID is in, online or not. */
    public static void progressTeam(MinecraftServer server, UUID member, String type, long amount, Predicate<TaskSpec> filter) {
        QuestEngine.custom(server, member, type, amount, filter);
    }
}
