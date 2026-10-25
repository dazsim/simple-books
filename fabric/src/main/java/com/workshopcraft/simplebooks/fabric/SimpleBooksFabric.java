package com.workshopcraft.simplebooks.fabric;

import com.workshopcraft.simplebooks.PageTurn;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class SimpleBooksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        com.workshopcraft.simplebooks.SimpleBooks.modLoaded = net.fabricmc.loader.api.FabricLoader.getInstance()::isModLoaded;
        SimpleBooksContent.registerAllDirect();
        PayloadTypeRegistry.serverboundPlay().register(PageTurn.TYPE, PageTurn.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PageTurn.TYPE, (payload, ctx) -> payload.handle(ctx.player()));
        CreativeModeTabEvents.modifyOutputEvent(SimpleBooksContent.FUNCTIONAL_TAB).register(e -> e.accept(SimpleBooksContent.STAND_ITEM));
    }
}
