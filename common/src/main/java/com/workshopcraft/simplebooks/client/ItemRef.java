package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** An item mentioned on a page: a plain id, a #tag, or an object with item(s), name, count and note. */
public record ItemRef(List<String> ids, @Nullable String label, int count, @Nullable String note) {
    public static ItemRef parse(JsonElement e) {
        if (e.isJsonPrimitive()) return new ItemRef(List.of(e.getAsString()), null, 0, null);
        JsonObject o = e.getAsJsonObject();
        List<String> ids = new ArrayList<>();
        if (o.has("item")) ids.add(o.get("item").getAsString());
        if (o.has("items")) o.getAsJsonArray("items").forEach(i -> ids.add(i.getAsString()));
        return new ItemRef(ids, o.has("name") ? o.get("name").getAsString() : null,
                o.has("count") ? o.get("count").getAsInt() : 0, o.has("note") ? o.get("note").getAsString() : null);
    }

    public static boolean known(String id) {
        if (id.startsWith("#")) return Identifier.tryParse(id.substring(1)) != null;
        Identifier i = Identifier.tryParse(id);
        return i != null && BuiltInRegistries.ITEM.containsKey(i);
    }

    private List<ItemStack> stacks() {
        List<ItemStack> out = new ArrayList<>();
        for (String id : ids) {
            if (id.startsWith("#")) {
                Identifier tag = Identifier.tryParse(id.substring(1));
                if (tag != null) for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag)))
                    out.add(new ItemStack(h));
            } else if (known(id)) {
                out.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))));
            }
        }
        return out;
    }

    /** The stack to draw now (cycling through tags and lists), or empty when nothing resolves. */
    public ItemStack stack() {
        List<ItemStack> s = stacks();
        if (s.isEmpty()) return ItemStack.EMPTY;
        ItemStack st = s.get((int) (Util.getMillis() / 1000 % s.size()));
        if (count > 1) st = st.copyWithCount(Math.min(count, st.getMaxStackSize()));
        return st;
    }

    public Component name() {
        if (label != null) return Texts.of(label);
        ItemStack s = stack();
        if (!s.isEmpty()) return s.getHoverName();
        String id = ids.isEmpty() ? "?" : ids.get(0);
        return Component.literal(id.substring(id.indexOf(':') + 1).replace('_', ' '));
    }
}
