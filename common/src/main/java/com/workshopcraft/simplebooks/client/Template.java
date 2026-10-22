package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A template with its parent chain merged in. */
public record Template(Identifier id, Map<String, JsonObject> slots, Map<String, JsonObject> styles,
                       Map<String, String> vars, List<String> errors) {
    public static final int PAGE_W = 644, PAGE_H = 920;
    public static final Set<String> SLOT_TYPES = Set.of("title", "subtitle", "text", "rule", "need_list", "ingredient_list",
            "tool_list", "step_rows", "image_box", "link_list", "item_icon", "grid", "page_number", "arrow", "contents");

    static Template resolve(Identifier id, Map<Identifier, JsonObject> raw) {
        List<String> errors = new ArrayList<>();
        List<JsonObject> chain = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();
        Identifier cur = id;
        while (cur != null) {
            if (!seen.add(cur)) {
                errors.add("template " + id + ": parent cycle at " + cur);
                break;
            }
            JsonObject t = raw.get(cur);
            if (t == null) {
                errors.add(cur.equals(id) ? "unknown template " + id : "template " + id + ": unknown parent " + cur);
                break;
            }
            chain.add(0, t);
            String parent = t.has("extends") ? t.get("extends").getAsString() : t.has("parent") ? t.get("parent").getAsString() : null;
            cur = parent == null ? null : Identifier.tryParse(parent);
            if (parent != null && cur == null) errors.add("template " + id + ": bad parent id " + parent);
        }
        Map<String, JsonObject> slots = new LinkedHashMap<>();
        Map<String, JsonObject> styles = new LinkedHashMap<>();
        Map<String, String> vars = new LinkedHashMap<>();
        for (JsonObject t : chain) {
            if (t.has("vars")) for (var e : t.getAsJsonObject("vars").entrySet()) vars.put(e.getKey(), e.getValue().getAsString());
            if (t.has("styles")) for (var e : t.getAsJsonObject("styles").entrySet()) {
                JsonObject merged = styles.containsKey(e.getKey()) ? styles.get(e.getKey()).deepCopy() : new JsonObject();
                for (var f : e.getValue().getAsJsonObject().entrySet()) merged.add(f.getKey(), f.getValue());
                styles.put(e.getKey(), merged);
            }
            if (t.has("slots")) for (var e : t.getAsJsonObject("slots").entrySet()) {
                if (e.getValue().isJsonNull()) {
                    slots.remove(e.getKey());
                    continue;
                }
                JsonObject merged = slots.containsKey(e.getKey()) ? slots.get(e.getKey()).deepCopy() : new JsonObject();
                for (var f : e.getValue().getAsJsonObject().entrySet()) merged.add(f.getKey(), f.getValue());
                slots.put(e.getKey(), merged);
            }
        }
        for (var e : slots.entrySet()) checkSlot(id, e.getKey(), e.getValue(), errors);
        JsonObject lt = slots.get("left.title"), rt = slots.get("right.title");
        if (lt != null && rt != null && !String.valueOf(lt.get("box")).equals(String.valueOf(rt.get("box"))))
            BookRegistry.warn("template " + id + ": left and right title boxes differ");
        return new Template(id, slots, styles, vars, errors);
    }

    private static void checkSlot(Identifier id, String name, JsonObject slot, List<String> errors) {
        String where = "template " + id + " /slots/" + name;
        if (!name.startsWith("left.") && !name.startsWith("right.")) errors.add(where + ": slot names must start with left. or right.");
        String type = slot.has("type") ? slot.get("type").getAsString() : null;
        if (type == null) errors.add(where + ": missing type");
        else if (!SLOT_TYPES.contains(type)) errors.add(where + ": unknown slot type '" + type + "'");
        JsonElement box = slot.get("box");
        if (box == null || !box.isJsonArray() || box.getAsJsonArray().size() != 4) {
            errors.add(where + "/box: needs [x, y, width, height]");
            return;
        }
        var b = box.getAsJsonArray();
        int x = b.get(0).getAsInt(), y = b.get(1).getAsInt(), w = b.get(2).getAsInt(), h = b.get(3).getAsInt();
        if (x < 0 || y < 0 || w < 0 || h < 0 || x + w > PAGE_W || y + h > PAGE_H)
            errors.add(where + "/box: outside the page (0..." + PAGE_W + " x 0..." + PAGE_H + ")");
    }

    public int box(JsonObject slot, int i) {
        return slot.getAsJsonArray("box").get(i).getAsInt();
    }
}
