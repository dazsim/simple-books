package com.workshopcraft.simplebooks.fabric;

import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.fabricmc.api.ModInitializer;

public class SimpleBooksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SimpleBooksContent.registerAllDirect();
    }
}
