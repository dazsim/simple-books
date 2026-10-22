package com.workshopcraft.simplebooks.fabric;

import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.client.BookRegistry;
import com.workshopcraft.simplebooks.client.BookStandRenderer;
import com.workshopcraft.simplebooks.client.SimpleBooksClient;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

public class SimpleBooksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SimpleBooksClient.init();
        SimpleBooks.toServer = ClientPlayNetworking::send;
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(BookRegistry.ID, new BookRegistry());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(SimpleBooksClient.command()));
        BlockEntityRendererRegistry.register(SimpleBooksContent.STAND_ENTITY, BookStandRenderer::new);
    }
}
