package com.workshopcraft.simplebooks.forge;

import com.workshopcraft.simplebooks.ConfigPack;
import com.workshopcraft.simplebooks.PageTurn;
import com.workshopcraft.simplebooks.quest.QuestDefsPacket;
import com.workshopcraft.simplebooks.quest.QuestManager;
import com.workshopcraft.simplebooks.quest.QuestReload;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.RegisterEvent;

import java.util.List;

@Mod(SimpleBooks.MOD_ID)
public class SimpleBooksForge {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(SimpleBooks.id("main"), () -> "2", "2"::equals, "2"::equals);

    public SimpleBooksForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        SimpleBooks.modLoaded = ModList.get()::isLoaded;
        SimpleBooks.configDir = FMLPaths.CONFIGDIR::get;
        ConfigPack.createFolders();
        modBus.addListener((RegisterEvent e) -> {
            e.register(Registries.BLOCK, h -> SimpleBooksContent.registerBlocks(h::register));
            e.register(Registries.ITEM, h -> SimpleBooksContent.registerItems(h::register));
            e.register(Registries.BLOCK_ENTITY_TYPE, h -> SimpleBooksContent.registerBlockEntities(h::register));
            e.register(Registries.RECIPE_SERIALIZER, h -> SimpleBooksContent.registerRecipeSerializers(h::register));
        });
        CHANNEL.messageBuilder(PageTurn.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PageTurn::write).decoder(PageTurn::read)
                .consumerMainThread((msg, ctx) -> {
                    if (ctx.get().getSender() != null) msg.handle(ctx.get().getSender());
                })
                .add();
        CHANNEL.messageBuilder(QuestDefsPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuestDefsPacket::write).decoder(QuestDefsPacket::read)
                .consumerMainThread((msg, ctx) -> msg.handle())
                .add();
        modBus.addListener((AddPackFindersEvent e) -> e.addRepositorySource(ConfigPack.source(e.getPackType())));
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new QuestReload()));
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> QuestManager.serverStarted(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> QuestManager.registerCommands(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) -> {
            if (e.getPlayer() == null) QuestManager.reloaded(e.getPlayerList().getServer());
            List<ServerPlayer> players = e.getPlayer() != null ? List.of(e.getPlayer()) : e.getPlayerList().getPlayers();
            for (ServerPlayer p : players) {
                if (!CHANNEL.isRemotePresent(p.connection.connection)) continue;
                for (QuestDefsPacket q : QuestManager.packets()) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), q);
            }
        });
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == SimpleBooksContent.FUNCTIONAL_TAB) e.accept(SimpleBooksContent.STAND_ITEM);
        });
        if (FMLEnvironment.dist == Dist.CLIENT) SimpleBooksForgeClient.init(modBus);
    }
}
