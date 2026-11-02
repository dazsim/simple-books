package com.workshopcraft.simplebooks.registry;

import com.workshopcraft.simplebooks.GuideItem;
import com.workshopcraft.simplebooks.GuideRecipe;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.stand.BookStandBlock;
import com.workshopcraft.simplebooks.stand.BookStandBlockEntity;
import net.minecraft.core.Registry;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.BiConsumer;

public final class SimpleBooksContent {
    //? if >=1.20.5 {
    public static final DataComponentType<Identifier> BOOK = DataComponentType.<Identifier>builder()
            .persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build();
    public static final DataComponentType<Integer> SPREAD = DataComponentType.<Integer>builder()
            .persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build();
    //?}
    public static final ResourceKey<CreativeModeTab> TOOLS_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, SimpleBooks.mc("tools_and_utilities"));
    public static final ResourceKey<CreativeModeTab> FUNCTIONAL_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, SimpleBooks.mc("functional_blocks"));

    public static Item GUIDE;
    public static Item STAND_ITEM;
    public static Block STAND;
    public static BlockEntityType<BookStandBlockEntity> STAND_ENTITY;

    private SimpleBooksContent() {}

    //? if >=1.20.5 {
    public static void registerComponents(BiConsumer<Identifier, DataComponentType<?>> r) {
        r.accept(SimpleBooks.id("book"), BOOK);
        r.accept(SimpleBooks.id("spread"), SPREAD);
    }
    //?}

    public static void registerBlocks(BiConsumer<Identifier, Block> r) {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion();
        //? if >=1.21.2
        props = props.setId(ResourceKey.create(Registries.BLOCK, SimpleBooks.id("book_stand")));
        STAND = new BookStandBlock(props);
        r.accept(SimpleBooks.id("book_stand"), STAND);
    }

    public static void registerItems(BiConsumer<Identifier, Item> r) {
        Item.Properties guide = new Item.Properties().stacksTo(1);
        Item.Properties stand = new Item.Properties();
        //? if >=1.21.2 {
        guide = guide.setId(ResourceKey.create(Registries.ITEM, SimpleBooks.id("guide")));
        stand = stand.useBlockDescriptionPrefix().setId(ResourceKey.create(Registries.ITEM, SimpleBooks.id("book_stand")));
        //?}
        GUIDE = new GuideItem(guide);
        r.accept(SimpleBooks.id("guide"), GUIDE);
        STAND_ITEM = new BlockItem(STAND, stand);
        r.accept(SimpleBooks.id("book_stand"), STAND_ITEM);
    }

    public static void registerBlockEntities(BiConsumer<Identifier, BlockEntityType<?>> r) {
        //? if >=1.21.2 {
        STAND_ENTITY = new BlockEntityType<>(BookStandBlockEntity::new, java.util.Set.of(STAND));
        //?} else {
        /*STAND_ENTITY = BlockEntityType.Builder.of(BookStandBlockEntity::new, STAND).build(null);
        *///?}
        r.accept(SimpleBooks.id("book_stand"), STAND_ENTITY);
    }

    public static void registerRecipeSerializers(BiConsumer<Identifier, RecipeSerializer<?>> r) {
        r.accept(SimpleBooks.id("guide"), GuideRecipe.SERIALIZER);
    }

    public static <T> BiConsumer<Identifier, T> direct(Registry<? super T> registry) {
        return (id, value) -> Registry.register(registry, id, value);
    }

    public static void registerAllDirect() {
        //? if >=1.20.5
        registerComponents(direct(BuiltInRegistries.DATA_COMPONENT_TYPE));
        registerBlocks(direct(BuiltInRegistries.BLOCK));
        registerItems(direct(BuiltInRegistries.ITEM));
        registerBlockEntities(direct(BuiltInRegistries.BLOCK_ENTITY_TYPE));
        registerRecipeSerializers(direct(BuiltInRegistries.RECIPE_SERIALIZER));
    }
}
