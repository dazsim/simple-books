package com.workshopcraft.simplebooks.registry;

import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.GuideItem;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import java.util.function.BiConsumer;

public final class SimpleBooksContent {
    public static final DataComponentType<Identifier> BOOK = DataComponentType.<Identifier>builder()
            .persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build();
    public static final ResourceKey<CreativeModeTab> TOOLS_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));

    public static Item GUIDE;

    private SimpleBooksContent() {}

    public static void registerComponents(BiConsumer<Identifier, DataComponentType<?>> r) {
        r.accept(SimpleBooks.id("book"), BOOK);
    }

    public static void registerItems(BiConsumer<Identifier, Item> r) {
        GUIDE = new GuideItem(new Item.Properties().stacksTo(1)
                .setId(ResourceKey.create(Registries.ITEM, SimpleBooks.id("guide"))));
        r.accept(SimpleBooks.id("guide"), GUIDE);
    }

    public static <T> BiConsumer<Identifier, T> direct(Registry<? super T> registry) {
        return (id, value) -> Registry.register(registry, id, value);
    }

    public static void registerAllDirect() {
        registerComponents(direct(BuiltInRegistries.DATA_COMPONENT_TYPE));
        registerItems(direct(BuiltInRegistries.ITEM));
    }
}
