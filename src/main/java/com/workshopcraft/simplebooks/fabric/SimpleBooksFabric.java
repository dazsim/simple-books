package com.workshopcraft.simplebooks.fabric;

import com.workshopcraft.simplebooks.PageTurn;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//? if >=26.1 {
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
//?} else {
/*import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
*///?}
//? if >=1.20.5
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class SimpleBooksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        com.workshopcraft.simplebooks.SimpleBooks.modLoaded = net.fabricmc.loader.api.FabricLoader.getInstance()::isModLoaded;
        SimpleBooksContent.registerAllDirect();
        //? if >=26.1 {
        PayloadTypeRegistry.serverboundPlay().register(PageTurn.TYPE, PageTurn.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PageTurn.TYPE, (payload, ctx) -> payload.handle(ctx.player()));
        CreativeModeTabEvents.modifyOutputEvent(SimpleBooksContent.FUNCTIONAL_TAB).register(e -> e.accept(SimpleBooksContent.STAND_ITEM));
        //?} else if >=1.20.5 {
        /*PayloadTypeRegistry.playC2S().register(PageTurn.TYPE, PageTurn.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PageTurn.TYPE, (payload, ctx) -> payload.handle(ctx.player()));
        ItemGroupEvents.modifyEntriesEvent(SimpleBooksContent.FUNCTIONAL_TAB).register(e -> e.accept(SimpleBooksContent.STAND_ITEM));
        *///?} else {
        /*ServerPlayNetworking.registerGlobalReceiver(PageTurn.ID, (server, player, handler, buf, sender) -> {
            PageTurn p = PageTurn.read(buf);
            server.execute(() -> p.handle(player));
        });
        ItemGroupEvents.modifyEntriesEvent(SimpleBooksContent.FUNCTIONAL_TAB).register(e -> e.accept(SimpleBooksContent.STAND_ITEM));
        *///?}
    }
}
