package com.workshopcraft.simplebooks;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public final class SimpleBooks {
    public static final String MOD_ID = "simplebooks";
    public static final Logger LOG = LoggerFactory.getLogger("Simple Books");

    // set by the client entrypoint so common code never touches client classes
    public static Consumer<Identifier> opener = id -> {};

    private SimpleBooks() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
