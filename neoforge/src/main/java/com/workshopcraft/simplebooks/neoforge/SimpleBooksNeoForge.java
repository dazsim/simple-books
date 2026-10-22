package com.workshopcraft.simplebooks.neoforge;

import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(SimpleBooks.MOD_ID)
public class SimpleBooksNeoForge {
    public SimpleBooksNeoForge(IEventBus modBus) {
        modBus.addListener((RegisterEvent e) -> {
            e.register(Registries.DATA_COMPONENT_TYPE, h -> SimpleBooksContent.registerComponents(h::register));
            e.register(Registries.ITEM, h -> SimpleBooksContent.registerItems(h::register));
        });
    }
}
