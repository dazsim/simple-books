package com.workshopcraft.simplebooks.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who is claiming a custom reward. teamId is the Simple Books team id (stable across renames);
 * teamName is what the book shows (empty for a solo player).
 */
public record RewardContext(MinecraftServer server, ServerPlayer player, String teamId, String teamName) {}
