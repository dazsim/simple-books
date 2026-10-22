package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

public record Book(Identifier id, JsonObject json, Map<String, String> vars, List<Page> pages, List<String> errors) {
    public record Page(Identifier id, String file, JsonObject json, Template template, List<String> errors) {
        public boolean broken() {
            return !errors.isEmpty();
        }
    }

    public int indexOf(Identifier page) {
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).id().equals(page)) return i;
        return -1;
    }

    public String title() {
        return json.has("title") ? json.get("title").getAsString() : id.toString();
    }
}
