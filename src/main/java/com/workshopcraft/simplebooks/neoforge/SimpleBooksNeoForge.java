package com.workshopcraft.simplebooks.neoforge;

import com.workshopcraft.simplebooks.ConfigPack;
import com.workshopcraft.simplebooks.PageTurn;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.quest.QuestDefsPacket;
import com.workshopcraft.simplebooks.quest.QuestManager;
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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
                .playToClient(QuestDefsPacket.TYPE, QuestDefsPacket.STREAM_CODEC, (payload, ctx) -> payload.handle()));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == SimpleBooksContent.FUNCTIONAL_TAB) e.accept(SimpleBooksContent.STAND_ITEM);
        });
        modBus.addListener((AddPackFindersEvent e) -> {
            if (!e.isTrusted()) e.addRepositorySource(ConfigPack.source(e.getPackType()));
        });
        //? if >=26.1 {
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent e) -> e.addListener(QuestReload.ID, new QuestReload()));
        //?} else {
        /*NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new QuestReload()));
        *///?}
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> QuestManager.serverStarted(e.getServer()));
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
