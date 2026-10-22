package com.workshopcraft.simplebooks.registry;

import com.workshopcraft.simplebooks.GuideItem;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.stand.BookStandBlock;
import com.workshopcraft.simplebooks.stand.BookStandBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.Set;
import java.util.function.BiConsumer;

public final class SimpleBooksContent {
    public static final DataComponentType<Identifier> BOOK = DataComponentType.<Identifier>builder()
            .persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build();
    public static final DataComponentType<Integer> SPREAD = DataComponentType.<Integer>builder()
            .persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build();
    public static final ResourceKey<CreativeModeTab> TOOLS_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));
    public static final ResourceKey<CreativeModeTab> FUNCTIONAL_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));

    public static Item GUIDE;
    public static Item STAND_ITEM;
    public static Block STAND;
    public static BlockEntityType<BookStandBlockEntity> STAND_ENTITY;

    private SimpleBooksContent() {}

    public static void registerComponents(BiConsumer<Identifier, DataComponentType<?>> r) {
        r.accept(SimpleBooks.id("book"), BOOK);
        r.accept(SimpleBooks.id("spread"), SPREAD);
    }

    public static void registerBlocks(BiConsumer<Identifier, Block> r) {
        STAND = new BookStandBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD)
                .noOcclusion().setId(ResourceKey.create(Registries.BLOCK, SimpleBooks.id("book_stand"))));
        r.accept(SimpleBooks.id("book_stand"), STAND);
    }

    public static void registerItems(BiConsumer<Identifier, Item> r) {
        GUIDE = new GuideItem(new Item.Properties().stacksTo(1)
                .setId(ResourceKey.create(Registries.ITEM, SimpleBooks.id("guide"))));
        r.accept(SimpleBooks.id("guide"), GUIDE);
        STAND_ITEM = new BlockItem(STAND, new Item.Properties().useBlockDescriptionPrefix()
                .setId(ResourceKey.create(Registries.ITEM, SimpleBooks.id("book_stand"))));
        r.accept(SimpleBooks.id("book_stand"), STAND_ITEM);
    }

    public static void registerBlockEntities(BiConsumer<Identifier, BlockEntityType<?>> r) {
        STAND_ENTITY = new BlockEntityType<>(BookStandBlockEntity::new, Set.of(STAND));
        r.accept(SimpleBooks.id("book_stand"), STAND_ENTITY);
    }

    public static <T> BiConsumer<Identifier, T> direct(Registry<? super T> registry) {
        return (id, value) -> Registry.register(registry, id, value);
    }

    public static void registerAllDirect() {
        registerComponents(direct(BuiltInRegistries.DATA_COMPONENT_TYPE));
        registerBlocks(direct(BuiltInRegistries.BLOCK));
        registerItems(direct(BuiltInRegistries.ITEM));
        registerBlockEntities(direct(BuiltInRegistries.BLOCK_ENTITY_TYPE));
    }
}
