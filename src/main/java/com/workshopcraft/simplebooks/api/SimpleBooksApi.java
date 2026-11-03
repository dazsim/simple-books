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

    private SimpleBooksApi() {}

    /** Register during mod init (or from a SimpleBooksPlugin), before data packs load. */
    public static void registerTaskType(String id, TaskType type) {
        if (!id.contains(":")) throw new IllegalArgumentException("task type id needs a namespace: " + id);
        if (TYPES.putIfAbsent(id, type) != null) throw new IllegalStateException("task type " + id + " is already registered");
    }

    public static @Nullable TaskType taskType(String id) {
        return TYPES.get(id);
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
