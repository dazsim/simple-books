package com.workshopcraft.simplebooks.forge;

import com.workshopcraft.simplebooks.PageTurn;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.RegisterEvent;

@Mod(SimpleBooks.MOD_ID)
public class SimpleBooksForge {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(SimpleBooks.id("main"), () -> "1", "1"::equals, "1"::equals);

    public SimpleBooksForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        SimpleBooks.modLoaded = ModList.get()::isLoaded;
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
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == SimpleBooksContent.FUNCTIONAL_TAB) e.accept(SimpleBooksContent.STAND_ITEM);
        });
        if (FMLEnvironment.dist == Dist.CLIENT) SimpleBooksForgeClient.init(modBus);
    }
}
