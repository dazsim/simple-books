package com.workshopcraft.simplebooks.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** One parsed custom task. Types may extend it to keep their own parsed fields. */
public class TaskSpec {
    private final String type;
    private final JsonObject json;
    private final long count;

    public TaskSpec(String type, JsonObject json, long count) {
        this.type = type;
        this.json = json;
        this.count = count;
    }

    /** type and count (default 1) read from the json. */
    public static TaskSpec of(JsonObject json) {
        return new TaskSpec(str(json, "type", ""), json, num(json, "count", 1));
    }

    public String type() {
        return type;
    }

    public JsonObject json() {
        return json;
    }

    public long count() {
        return count;
    }

    public String string(String key, String def) {
        return str(json, key, def);
    }

    public long number(String key, long def) {
        return num(json, key, def);
    }

    /** True when the field is missing, or names the block's id or one of its tags ("#ns:tag"). */
    public boolean matchesBlock(String key, BlockState state) {
        String ref = string(key, "");
        if (ref.isEmpty()) return true;
        if (ref.startsWith("#")) return state.is(TagKey.create(Registries.BLOCK, id(ref.substring(1))));
        return ref.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    public boolean matchesBlock(BlockState state) {
        return matchesBlock("block", state);
    }

    /** True when the field is missing, or names the item's id or one of its tags. */
    public boolean matchesItem(String key, ItemStack stack) {
        String ref = string(key, "");
        if (ref.isEmpty()) return true;
        if (ref.startsWith("#")) return stack.is(TagKey.create(Registries.ITEM, id(ref.substring(1))));
        return ref.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public boolean matchesItem(ItemStack stack) {
        return matchesItem("item", stack);
    }

    private static net.minecraft.resources.Identifier id(String s) {
        return com.workshopcraft.simplebooks.SimpleBooks.parse(s);
    }

    private static String str(JsonObject o, String key, String def) {
        @Nullable JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static long num(JsonObject o, String key, long def) {
        @Nullable JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : def;
    }
}
