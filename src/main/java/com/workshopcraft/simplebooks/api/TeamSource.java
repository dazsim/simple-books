package com.workshopcraft.simplebooks.api;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Where quest teams come from when config/simplebooks.json sets "team_source" to this source's id. Simple Books asks
 * once a second for each online player; players with the same team id share progress, and a player with none plays
 * on their own. Leaving a team keeps a copy of its progress, the same as with Simple Books' own teams.
 */
public interface TeamSource {
    /** The player's team id in this source, or null when they are in no team. Server thread only. */
    @Nullable String teamOf(ServerPlayer player);

    /** The name shown in the book for a team id. */
    default String name(String team) {
        return team;
    }
}
