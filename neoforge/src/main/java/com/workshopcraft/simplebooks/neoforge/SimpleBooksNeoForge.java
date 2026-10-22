package com.workshopcraft.simplebooks.neoforge;

import com.workshopcraft.simplebooks.PageTurn;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(SimpleBooks.MOD_ID)
public class SimpleBooksNeoForge {
    public SimpleBooksNeoForge(IEventBus modBus) {
        modBus.addListener((RegisterEvent e) -> {
            e.register(Registries.DATA_COMPONENT_TYPE, h -> SimpleBooksContent.registerComponents(h::register));
            e.register(Registries.BLOCK, h -> SimpleBooksContent.registerBlocks(h::register));
            e.register(Registries.ITEM, h -> SimpleBooksContent.registerItems(h::register));
            e.register(Registries.BLOCK_ENTITY_TYPE, h -> SimpleBooksContent.registerBlockEntities(h::register));
        });
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToServer(PageTurn.TYPE, PageTurn.STREAM_CODEC,
                (payload, ctx) -> payload.handle((ServerPlayer) ctx.player())));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == SimpleBooksContent.FUNCTIONAL_TAB) e.accept(SimpleBooksContent.STAND_ITEM);
        });
    }
}
