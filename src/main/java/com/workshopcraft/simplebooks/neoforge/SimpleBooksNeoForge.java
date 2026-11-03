package com.workshopcraft.simplebooks.neoforge;

import com.workshopcraft.simplebooks.ConfigPack;
import com.workshopcraft.simplebooks.PageTurn;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.quest.QuestActionPacket;
import com.workshopcraft.simplebooks.quest.QuestDefsPacket;
import com.workshopcraft.simplebooks.quest.QuestEngine;
import com.workshopcraft.simplebooks.quest.QuestManager;
import com.workshopcraft.simplebooks.quest.QuestPlugins;
import com.workshopcraft.simplebooks.quest.QuestSyncPacket;
import com.workshopcraft.simplebooks.quest.QuestReload;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
//? if >=26.1 {
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
//?} else {
/*import net.neoforged.neoforge.event.AddReloadListenerEvent;
*///?}

@Mod(SimpleBooks.MOD_ID)
public class SimpleBooksNeoForge {
    public SimpleBooksNeoForge(IEventBus modBus) {
        SimpleBooks.modLoaded = net.neoforged.fml.ModList.get()::isLoaded;
        SimpleBooks.configDir = FMLPaths.CONFIGDIR::get;
        //? if >=26.1 {
        SimpleBooks.dev = !net.neoforged.fml.loading.FMLEnvironment.isProduction();
        //?} else {
        /*SimpleBooks.dev = !net.neoforged.fml.loading.FMLEnvironment.production;
        *///?}
        QuestEngine.sender = (p, x) -> {
            if (p.connection.hasChannel(QuestSyncPacket.TYPE)) PacketDistributor.sendToPlayer(p, x);
        };
        ConfigPack.createFolders();
        modBus.addListener((RegisterEvent e) -> {
            e.register(Registries.DATA_COMPONENT_TYPE, h -> SimpleBooksContent.registerComponents(h::register));
            e.register(Registries.BLOCK, h -> SimpleBooksContent.registerBlocks(h::register));
            e.register(Registries.ITEM, h -> SimpleBooksContent.registerItems(h::register));
            e.register(Registries.BLOCK_ENTITY_TYPE, h -> SimpleBooksContent.registerBlockEntities(h::register));
            e.register(Registries.RECIPE_SERIALIZER, h -> SimpleBooksContent.registerRecipeSerializers(h::register));
        });
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("2")
                .playToServer(PageTurn.TYPE, PageTurn.STREAM_CODEC, (payload, ctx) -> payload.handle((ServerPlayer) ctx.player()))
                .playToServer(QuestActionPacket.TYPE, QuestActionPacket.STREAM_CODEC, (payload, ctx) -> payload.handle((ServerPlayer) ctx.player()))
                .playToClient(QuestDefsPacket.TYPE, QuestDefsPacket.STREAM_CODEC, (payload, ctx) -> payload.handle())
                .playToClient(QuestSyncPacket.TYPE, QuestSyncPacket.STREAM_CODEC, (payload, ctx) -> payload.handle()));
        modBus.addListener((InterModProcessEvent e) -> QuestPlugins.run(
                InterModComms.getMessages(SimpleBooks.MOD_ID, "plugin"::equals).map(m -> m.messageSupplier().get()).toList()));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == SimpleBooksContent.FUNCTIONAL_TAB) {
                e.accept(SimpleBooksContent.STAND_ITEM);
                e.accept(SimpleBooksContent.HAND_IN_BOX_ITEM);
            }
        });
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> e.registerBlockEntity(
                //? if >=26.1 {
                net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                //?} else {
                /*net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                *///?}
                SimpleBooksContent.HAND_IN_BOX_ENTITY, (be, side) -> be.handler(BoxHandler::new)));
        modBus.addListener((AddPackFindersEvent e) -> {
            if (!e.isTrusted()) e.addRepositorySource(ConfigPack.source(e.getPackType()));
        });
        //? if >=26.1 {
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent e) -> e.addListener(QuestReload.ID, new QuestReload()));
        //?} else {
        /*NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new QuestReload()));
        *///?}
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> QuestManager.serverStarted(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> QuestEngine.stop());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> QuestEngine.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) QuestEngine.login(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) QuestEngine.logout(p);
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> QuestManager.registerCommands(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> {
            if (e.getPlayer() == null) QuestManager.reloaded(e.getPlayerList().getServer());
            e.getRelevantPlayers().forEach(p -> {
                if (p.connection.hasChannel(QuestDefsPacket.TYPE))
                    for (QuestDefsPacket q : QuestManager.packets()) PacketDistributor.sendToPlayer(p, q);
            });
        });
    }
}
