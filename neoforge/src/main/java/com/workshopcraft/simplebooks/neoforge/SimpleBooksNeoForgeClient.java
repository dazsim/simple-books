package com.workshopcraft.simplebooks.neoforge;

import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.client.BookRegistry;
import com.workshopcraft.simplebooks.client.BookStandRenderer;
import com.workshopcraft.simplebooks.client.SimpleBooksClient;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SimpleBooks.MOD_ID, dist = Dist.CLIENT)
public class SimpleBooksNeoForgeClient {
    public SimpleBooksNeoForgeClient(IEventBus modBus) {
        SimpleBooksClient.init();
        SimpleBooks.toServer = ClientPacketDistributor::sendToServer;
        modBus.addListener((AddClientReloadListenersEvent e) -> e.addListener(BookRegistry.ID, new BookRegistry()));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerBlockEntityRenderer(SimpleBooksContent.STAND_ENTITY, BookStandRenderer::new));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(SimpleBooksClient.command()));
    }
}
