package com.workshopcraft.simplebooks.fabric;

import com.workshopcraft.simplebooks.ConfigPack;
import com.workshopcraft.simplebooks.PageTurn;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.api.SimpleBooksPlugin;
import com.workshopcraft.simplebooks.quest.QuestActionPacket;
import com.workshopcraft.simplebooks.quest.QuestEditPacket;
import com.workshopcraft.simplebooks.quest.QuestEditor;
import com.workshopcraft.simplebooks.quest.QuestDefsPacket;
import com.workshopcraft.simplebooks.quest.QuestEngine;
import com.workshopcraft.simplebooks.quest.QuestManager;
import com.workshopcraft.simplebooks.quest.QuestPlugins;
import com.workshopcraft.simplebooks.quest.QuestSyncPacket;
import com.workshopcraft.simplebooks.quest.QuestReload;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
//? if >=26.1 {
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
//?} else {
/*import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
*///?}
//? if >=1.20.5
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class SimpleBooksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SimpleBooks.modLoaded = FabricLoader.getInstance()::isModLoaded;
        SimpleBooks.configDir = FabricLoader.getInstance()::getConfigDir;
        ConfigPack.createFolders();
        SimpleBooksContent.registerAllDirect();
        SimpleBooks.dev = FabricLoader.getInstance().isDevelopmentEnvironment();
        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlockEntity((be, side) -> be.handler(BoxStorage::new), SimpleBooksContent.HAND_IN_BOX_ENTITY);
        //? if >=26.1 {
        PayloadTypeRegistry.serverboundPlay().register(PageTurn.TYPE, PageTurn.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestActionPacket.TYPE, QuestActionPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestEditPacket.TYPE, QuestEditPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuestDefsPacket.TYPE, QuestDefsPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuestSyncPacket.TYPE, QuestSyncPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PageTurn.TYPE, (payload, ctx) -> payload.handle(ctx.player()));
        ServerPlayNetworking.registerGlobalReceiver(QuestActionPacket.TYPE, (payload, ctx) -> payload.handle(ctx.player()));
        ServerPlayNetworking.registerGlobalReceiver(QuestEditPacket.TYPE, (payload, ctx) -> payload.handle(ctx.player()));
        CreativeModeTabEvents.modifyOutputEvent(SimpleBooksContent.FUNCTIONAL_TAB).register(e -> {
            e.accept(SimpleBooksContent.STAND_ITEM);
            e.accept(SimpleBooksContent.HAND_IN_BOX_ITEM);
        });
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(QuestReload.ID, new QuestReload());
        //?} else if >=1.20.5 {
        /*PayloadTypeRegistry.playC2S().register(PageTurn.TYPE, PageTurn.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(QuestActionPacket.TYPE, QuestActionPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(QuestDefsPacket.TYPE, QuestDefsPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(QuestSyncPacket.TYPE, QuestSyncPacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PageTurn.TYPE, (payload, ctx) -> payload.handle(ctx.player()));
        ServerPlayNetworking.registerGlobalReceiver(QuestActionPacket.TYPE, (payload, ctx) -> payload.handle(ctx.player()));
        ServerPlayNetworking.registerGlobalReceiver(QuestEditPacket.TYPE, (payload, ctx) -> payload.handle(ctx.player()));
        ItemGroupEvents.modifyEntriesEvent(SimpleBooksContent.FUNCTIONAL_TAB).register(e -> {
            e.accept(SimpleBooksContent.STAND_ITEM);
            e.accept(SimpleBooksContent.HAND_IN_BOX_ITEM);
        });
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new Quests());
        *///?} else {
        /*ServerPlayNetworking.registerGlobalReceiver(PageTurn.ID, (server, player, handler, buf, sender) -> {
            PageTurn p = PageTurn.read(buf);
            server.execute(() -> p.handle(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(QuestActionPacket.ID, (server, player, handler, buf, sender) -> {
            QuestActionPacket p = QuestActionPacket.read(buf);
            server.execute(() -> p.handle(player));
        });
        ItemGroupEvents.modifyEntriesEvent(SimpleBooksContent.FUNCTIONAL_TAB).register(e -> {
            e.accept(SimpleBooksContent.STAND_ITEM);
            e.accept(SimpleBooksContent.HAND_IN_BOX_ITEM);
        });
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new Quests());
        *///?}
        ServerLifecycleEvents.SERVER_STARTED.register(QuestManager::serverStarted);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, rm, ok) -> QuestManager.reloaded(server));
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> sendQuests(player));
        CommandRegistrationCallback.EVENT.register((d, ctx, env) -> QuestManager.registerCommands(d));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> QuestEngine.stop());
        ServerTickEvents.END_SERVER_TICK.register(QuestEngine::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> QuestEngine.login(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> QuestEngine.logout(handler.player));
        QuestEngine.sender = SimpleBooksFabric::sendSync;
        QuestEditor.sendDefs = SimpleBooksFabric::sendQuests;
        QuestPlugins.run(FabricLoader.getInstance().getEntrypoints("simplebooks", SimpleBooksPlugin.class));
    }

    private static void sendSync(ServerPlayer player, QuestSyncPacket p) {
        //? if >=1.20.5 {
        if (ServerPlayNetworking.canSend(player, QuestSyncPacket.TYPE)) ServerPlayNetworking.send(player, p);
        //?} else {
        /*net.minecraft.network.FriendlyByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        p.write(buf);
        ServerPlayNetworking.send(player, QuestSyncPacket.ID, buf);
        *///?}
    }

    private static void sendQuests(ServerPlayer player) {
        //? if >=1.20.5 {
        if (!ServerPlayNetworking.canSend(player, QuestDefsPacket.TYPE)) return;
        for (QuestDefsPacket p : QuestManager.packets()) ServerPlayNetworking.send(player, p);
        //?} else {
        /*// channels are not known yet at join on 1.20.1, so this goes out unguarded
        for (QuestDefsPacket p : QuestManager.packets()) {
            net.minecraft.network.FriendlyByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            p.write(buf);
            ServerPlayNetworking.send(player, QuestDefsPacket.ID, buf);
        }
        *///?}
    }

    //? if <26.1 {
    /*static class Quests extends QuestReload implements IdentifiableResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return ID;
        }
    }
    *///?}
}
