package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dev runs only: offline test players on a dedicated server, and the sync packets they would have got. */
public final class QuestDevBots {
    private QuestDevBots() {}

    private static final Map<UUID, List<String>> GOT = new HashMap<>();

    public static boolean isBot(ServerPlayer p) {
        return GOT.containsKey(p.getUUID());
    }

    static void got(ServerPlayer p, String kind, JsonObject o) {
        List<String> l = GOT.get(p.getUUID());
        l.add(kind + " " + o);
        if (l.size() > 50) l.remove(0);
    }

    static List<String> received(ServerPlayer p, String kind) {
        List<String> out = new ArrayList<>();
        for (String s : GOT.getOrDefault(p.getUUID(), List.of())) if (s.startsWith(kind + " ")) out.add(s.substring(kind.length() + 1));
        return out;
    }

    static String join(MinecraftServer server, String name) {
        //? if >=26.1 {
        try {
            UUID id = net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name);
            if (server.getPlayerList().getPlayer(id) != null) return name + " is already online";
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(id, name);
            net.minecraft.network.Connection con = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            java.lang.reflect.Field f = net.minecraft.network.Connection.class.getDeclaredField("channel");
            f.setAccessible(true);
            f.set(con, new io.netty.channel.embedded.EmbeddedChannel());
            ServerPlayer p = new ServerPlayer(server, server.overworld(), profile, net.minecraft.server.level.ClientInformation.createDefault());
            GOT.put(id, new ArrayList<>());
            server.getPlayerList().placeNewPlayer(con, p, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
            return name + " joined";
        } catch (Exception e) {
            return "Could not add " + name + ": " + e;
        }
        //?} else {
        /*return "Test players need 26.1 or newer";
        *///?}
    }

    static String leave(MinecraftServer server, String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p == null || !isBot(p)) return name + " is not a test player";
        //? if >=26.1 {
        p.connection.onDisconnect(new net.minecraft.network.DisconnectionDetails(net.minecraft.network.chat.Component.literal("bye")));
        //?}
        GOT.remove(p.getUUID());
        return name + " left";
    }
}
