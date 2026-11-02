package com.workshopcraft.simplebooks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SimpleBooks {
    public static final String MOD_ID = "simplebooks";
    public static final Logger LOG = LoggerFactory.getLogger("Simple Books");

    public interface Opener {
        /** hand is 0 or 1 for a held book, -1 for a book on the stand at pos. */
        void open(Identifier book, int spread, int hand, @Nullable BlockPos stand);
    }

    // set by the client entrypoint so common code never touches client classes
    public static Opener opener = (book, spread, hand, stand) -> {};
    public static Consumer<PageTurn> toServer = p -> {};
    public static Predicate<String> modLoaded = id -> true;

    private SimpleBooks() {}

    public static Identifier id(String path) {
        return of(MOD_ID, path);
    }

    public static Identifier of(String namespace, String path) {
        //? if >=1.21 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new Identifier(namespace, path);
        *///?}
    }

    public static Identifier mc(String path) {
        return of("minecraft", path);
    }

    public static Identifier parse(String id) {
        //? if >=1.21 {
        return Identifier.parse(id);
        //?} else {
        /*return new Identifier(id);
        *///?}
    }

    public static Item item(Identifier id) {
        //? if >=1.21.2 {
        return BuiltInRegistries.ITEM.getValue(id);
        //?} else {
        /*return BuiltInRegistries.ITEM.get(id);
        *///?}
    }

    public static long millis() {
        //? if >=26.1 {
        return net.minecraft.util.Util.getMillis();
        //?} else {
        /*return net.minecraft.Util.getMillis();
        *///?}
    }
}
