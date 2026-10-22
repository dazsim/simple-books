package com.workshopcraft.simplebooks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public final class SimpleBooks {
    public static final String MOD_ID = "simplebooks";
    public static final Logger LOG = LoggerFactory.getLogger("Simple Books");

    public interface Opener {
        /** hand is 0 or 1 for a held book, -1 for a book on the stand at pos. */
        void open(Identifier book, int spread, int hand, @Nullable BlockPos stand);
    }

    // set by the client entrypoint so common code never touches client classes
    public static Opener opener = (book, spread, hand, stand) -> {};
    public static Consumer<CustomPacketPayload> toServer = p -> {};

    private SimpleBooks() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
