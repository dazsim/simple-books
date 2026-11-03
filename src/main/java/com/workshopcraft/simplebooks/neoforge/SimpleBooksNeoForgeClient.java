package com.workshopcraft.simplebooks.neoforge;

import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.client.BookRegistry;
import com.workshopcraft.simplebooks.client.BookStandRenderer;
import com.workshopcraft.simplebooks.client.SimpleBooksClient;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
//? if >=26.1 {
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?} else {
/*import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
*///?}
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SimpleBooks.MOD_ID, dist = Dist.CLIENT)
public class SimpleBooksNeoForgeClient {
    public SimpleBooksNeoForgeClient(IEventBus modBus) {
        SimpleBooksClient.init();
        //? if >=26.1 {
        SimpleBooks.toServer = ClientPacketDistributor::sendToServer;
        SimpleBooks.toServerAction = ClientPacketDistributor::sendToServer;
        modBus.addListener((AddClientReloadListenersEvent e) -> e.addListener(BookRegistry.ID, new BookRegistry()));
        //?} else {
        /*SimpleBooks.toServer = p -> PacketDistributor.sendToServer(p);
        SimpleBooks.toServerAction = p -> PacketDistributor.sendToServer(p);
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(new BookRegistry()));
        *///?}
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerBlockEntityRenderer(SimpleBooksContent.STAND_ENTITY, BookStandRenderer::new));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
                com.workshopcraft.simplebooks.quest.QuestClientStore.clear());
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(SimpleBooksClient.command()));
    }
}
