package com.workshopcraft.simplebooks;

import com.google.gson.JsonObject;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
//? if >=1.20.5 {
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
//?} else {
/*import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
*///?}

/**
 * Recipe type simplebooks:guide. A vanilla shaped (with "pattern") or shapeless recipe whose result is a guide
 * for "book". Parsed by rewriting the json into the vanilla recipe, so the loaded recipe is a plain vanilla one.
 */
public final class GuideRecipe {
    private GuideRecipe() {}

    //? if >=1.20.5 {
    public static final MapCodec<CraftingRecipe> CODEC = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of("book", "count", "pattern", "key", "ingredients", "result").map(ops::createString);
        }

        @Override
        public <T> DataResult<CraftingRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
            T bookValue = input.get("book");
            if (bookValue == null) return DataResult.error(() -> "simplebooks:guide recipe needs a book");
            return ops.getStringValue(bookValue).flatMap(book -> {
                T countValue = input.get("count");
                int count = countValue == null ? 1 : ops.getNumberValue(countValue, 1).intValue();
                JsonObject components = new JsonObject();
                components.addProperty("simplebooks:book", book);
                JsonObject result = new JsonObject();
                result.addProperty("id", SimpleBooks.id("guide").toString());
                result.addProperty("count", count);
                result.add("components", components);
                Map<T, T> entries = new LinkedHashMap<>();
                input.entries().forEach(p -> {
                    String key = ops.getStringValue(p.getFirst()).result().orElse("");
                    if (!key.equals("book") && !key.equals("count") && !key.equals("type")) entries.put(p.getFirst(), p.getSecond());
                });
                entries.put(ops.createString("result"), JsonOps.INSTANCE.convertTo(ops, result));
                boolean shaped = input.get("pattern") != null;
                return ops.getMap(ops.createMap(entries)).flatMap(map -> shaped
                        ? shaped().decode(ops, map).map(r -> (CraftingRecipe) r)
                        : shapeless().decode(ops, map).map(r -> (CraftingRecipe) r));
            });
        }

        @Override
        public <T> RecordBuilder<T> encode(CraftingRecipe recipe, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            if (recipe instanceof ShapedRecipe r) return shaped().encode(r, ops, prefix);
            if (recipe instanceof ShapelessRecipe r) return shapeless().encode(r, ops, prefix);
            return prefix.withErrorsFrom(DataResult.error(() -> "not a guide recipe"));
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, r) -> { throw new UnsupportedOperationException("guide recipes sync as vanilla recipes"); },
            buf -> { throw new UnsupportedOperationException("guide recipes sync as vanilla recipes"); });

    //?}

    //? if >=26.1 {
    private static MapCodec<ShapedRecipe> shaped() {
        return ShapedRecipe.SERIALIZER.codec();
    }

    private static MapCodec<ShapelessRecipe> shapeless() {
        return ShapelessRecipe.SERIALIZER.codec();
    }

    public static final RecipeSerializer<CraftingRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);
    //?} else if >=1.20.5 {
    /*private static MapCodec<ShapedRecipe> shaped() {
        return RecipeSerializer.SHAPED_RECIPE.codec();
    }

    private static MapCodec<ShapelessRecipe> shapeless() {
        return RecipeSerializer.SHAPELESS_RECIPE.codec();
    }

    public static final RecipeSerializer<CraftingRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override
        public MapCodec<CraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CraftingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    };
    *///?} else {
    /*public static final RecipeSerializer<CraftingRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override
        public CraftingRecipe fromJson(Identifier id, JsonObject json) {
            if (!json.has("book")) throw new com.google.gson.JsonSyntaxException("simplebooks:guide recipe needs a book");
            JsonObject o = json.deepCopy();
            Identifier book = SimpleBooks.parse(o.remove("book").getAsString());
            int count = o.has("count") ? o.remove("count").getAsInt() : 1;
            JsonObject result = new JsonObject();
            result.addProperty("item", SimpleBooks.id("guide").toString());
            result.addProperty("count", count);
            o.add("result", result);
            CraftingRecipe recipe = o.has("pattern") ? RecipeSerializer.SHAPED_RECIPE.fromJson(id, o) : RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, o);
            GuideItem.setBook(recipe.getResultItem(null), book);
            return recipe;
        }

        @Override
        public CraftingRecipe fromNetwork(Identifier id, FriendlyByteBuf buf) {
            throw new UnsupportedOperationException("guide recipes sync as vanilla recipes");
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, CraftingRecipe recipe) {
            throw new UnsupportedOperationException("guide recipes sync as vanilla recipes");
        }
    };
    *///?}
}
