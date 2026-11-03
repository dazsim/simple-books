package com.workshopcraft.simplebooks.forge;

import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.client.BookRegistry;
import com.workshopcraft.simplebooks.client.BookStandRenderer;
import com.workshopcraft.simplebooks.client.SimpleBooksClient;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

final class SimpleBooksForgeClient {
    private SimpleBooksForgeClient() {}

    static void init(IEventBus modBus) {
        SimpleBooksClient.init();
        SimpleBooks.toServer = SimpleBooksForge.CHANNEL::sendToServer;
        SimpleBooks.toServerAction = SimpleBooksForge.CHANNEL::sendToServer;
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
                com.workshopcraft.simplebooks.quest.QuestClientStore.clear());
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(new BookRegistry()));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerBlockEntityRenderer(SimpleBooksContent.STAND_ENTITY, BookStandRenderer::new));
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(SimpleBooksClient.command()));
    }
}
