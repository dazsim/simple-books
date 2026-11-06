package com.workshopcraft.simplebooks.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/** One parsed custom reward. Types may extend it to keep their own parsed fields. */
public class RewardSpec {
    private final String type;
    private final JsonObject json;

    public RewardSpec(String type, JsonObject json) {
        this.type = type;
        this.json = json;
    }

    /** type read from the json. */
    public static RewardSpec of(JsonObject json) {
        return new RewardSpec(str(json, "type", ""), json);
    }

    public String type() {
        return type;
    }

    public JsonObject json() {
        return json;
    }

    public String string(String key, String def) {
        return str(json, key, def);
    }

    public long number(String key, long def) {
        return num(json, key, def);
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
