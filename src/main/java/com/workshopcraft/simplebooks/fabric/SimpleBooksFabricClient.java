package com.workshopcraft.simplebooks.fabric;

import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.client.BookRegistry;
import com.workshopcraft.simplebooks.client.BookStandRenderer;
import com.workshopcraft.simplebooks.client.SimpleBooksClient;
import com.workshopcraft.simplebooks.quest.QuestDefsPacket;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.server.packs.PackType;
//? if >=26.1 {
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
//?} else {
/*import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
*///?}

public class SimpleBooksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SimpleBooksClient.init();
        //? if >=26.1 {
        SimpleBooks.toServer = ClientPlayNetworking::send;
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(BookRegistry.ID, new BookRegistry());
        //?} else if >=1.20.5 {
        /*SimpleBooks.toServer = ClientPlayNetworking::send;
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new Books());
        *///?} else {
        /*SimpleBooks.toServer = p -> {
            net.minecraft.network.FriendlyByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            p.write(buf);
            ClientPlayNetworking.send(com.workshopcraft.simplebooks.PageTurn.ID, buf);
        };
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new Books());
        *///?}
        //? if >=1.20.5 {
        ClientPlayNetworking.registerGlobalReceiver(QuestDefsPacket.TYPE, (payload, ctx) -> payload.handle());
        //?} else {
        /*ClientPlayNetworking.registerGlobalReceiver(QuestDefsPacket.ID, (client, handler, buf, sender) -> QuestDefsPacket.read(buf).handle());
        *///?}
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(SimpleBooksClient.command()));
        BlockEntityRendererRegistry.register(SimpleBooksContent.STAND_ENTITY, BookStandRenderer::new);
    }

    //? if <26.1 {
    /*static class Books extends BookRegistry implements IdentifiableResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return ID;
        }
    }
    *///?}
}
