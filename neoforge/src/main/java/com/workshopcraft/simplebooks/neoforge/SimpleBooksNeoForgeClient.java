package com.workshopcraft.simplebooks.neoforge;

import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.client.BookRegistry;
import com.workshopcraft.simplebooks.client.SimpleBooksClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = SimpleBooks.MOD_ID, dist = Dist.CLIENT)
public class SimpleBooksNeoForgeClient {
    public SimpleBooksNeoForgeClient(IEventBus modBus) {
        SimpleBooksClient.init();
        modBus.addListener((AddClientReloadListenersEvent e) -> e.addListener(BookRegistry.ID, new BookRegistry()));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(SimpleBooksClient.command()));
    }
}
