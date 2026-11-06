package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.workshopcraft.simplebooks.quest.QuestParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;


/** Shared form bits for built-in task and reward editors. */
final class QuestEditForms {
    static final String[] TASK_TYPES = {"item", "craft", "smelt", "pickup", "advancement", "place",
            "break", "dimension", "biome", "checkbox", "kill", "use", "stat"};
    static final String[] REWARD_KINDS = {"item", "xp", "xp_levels", "command", "loot_table", "custom"};

    private QuestEditForms() {}

    static boolean isLangKey(String s) {
        if (s == null || s.isBlank() || s.contains(" ")) return false;
        return net.minecraft.locale.Language.getInstance().has(s) || s.chars().filter(c -> c == '.').count() >= 2;
    }

    static String displayText(String raw) {
        if (raw == null) return "";
        if (isLangKey(raw) && net.minecraft.locale.Language.getInstance().has(raw))
            return net.minecraft.locale.Language.getInstance().getOrDefault(raw);
        return raw;
    }

    /** Keep lang keys in the field; collect display edits into langOut. */
    static void putText(JsonObject out, JsonObject langOut, String field, String originalKeyOrLiteral, String edited) {
        if (edited == null || edited.isBlank()) {
            out.remove(field);
            return;
        }
        if (originalKeyOrLiteral != null && isLangKey(originalKeyOrLiteral) && !edited.equals(originalKeyOrLiteral)) {
            out.addProperty(field, originalKeyOrLiteral);
            langOut.addProperty(originalKeyOrLiteral, edited);
        } else {
            out.addProperty(field, edited);
        }
    }

    static String itemError(String raw) {
        if (raw == null || raw.isBlank()) return "item required";
        String id = raw.trim();
        if (id.startsWith("#")) {
            return Identifier.tryParse(id.substring(1)) == null ? "bad tag id" : null;
        }
        Identifier i = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (i == null) return "bad item id";
        return BuiltInRegistries.ITEM.containsKey(i) ? null : "unknown item " + i;
    }

    static String normalizeItem(String raw) {
        if (raw == null) return "";
        String id = raw.trim();
        if (id.startsWith("#") || id.contains(":")) return id;
        return id.isEmpty() ? "" : "minecraft:" + id;
    }

    /** One task row's editable state. */
    static final class TaskRow {
        String type;
        final EditBox id, item, count, text, block, entity, dimension, biome, advancement, stat, raw;
        boolean consume, teamTotal, fromStats, useRaw;
        String handIn = "both";

        TaskRow(Font font, int x, int y, @Nullable JsonObject src) {
            JsonObject o = src == null ? new JsonObject() : src;
            type = o.has("type") ? o.get("type").getAsString() : "item";
            boolean custom = type.contains(":");
            useRaw = custom;
            id = box(font, x, y, 80, o.has("id") ? o.get("id").getAsString() : "");
            item = box(font, x + 90, y, 140, firstItem(o));
            count = box(font, x + 240, y, 40, o.has("count") ? o.get("count").getAsString() : "1");
            text = box(font, x, y + 22, 280, o.has("text") ? displayText(o.get("text").getAsString()) : "");
            block = box(font, x, y + 22, 140, str(o, "block"));
            entity = box(font, x, y + 22, 140, str(o, "entity"));
            dimension = box(font, x, y + 22, 140, str(o, "dimension"));
            biome = box(font, x, y + 22, 140, str(o, "biome"));
            advancement = box(font, x, y + 22, 200, str(o, "advancement"));
            stat = box(font, x, y + 22, 200, str(o, "stat"));
            raw = box(font, x, y + 22, 280, o.toString());
            consume = o.has("consume") && o.get("consume").getAsBoolean();
            teamTotal = o.has("team_total") && o.get("team_total").getAsBoolean();
            fromStats = o.has("from_stats") && o.get("from_stats").getAsBoolean();
            if (o.has("hand_in")) handIn = o.get("hand_in").getAsString();
            // type cycle + flags added by parent layout
        }

        static EditBox box(Font font, int x, int y, int w, String v) {
            EditBox b = new EditBox(font, x, y, w, 18, Component.literal(""));
            b.setMaxLength(20000);
            b.setValue(v == null ? "" : v);
            return b;
        }

        static String str(JsonObject o, String k) {
            return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
        }

        static String firstItem(JsonObject o) {
            if (o.has("item")) return o.get("item").getAsString();
            if (o.has("items") && o.get("items").isJsonArray() && o.getAsJsonArray("items").size() > 0)
                return o.getAsJsonArray("items").get(0).getAsString();
            return "";
        }

        void cycleType() {
            if (useRaw) return;
            int i = indexOf(TASK_TYPES, type);
            type = TASK_TYPES[(i + 1) % TASK_TYPES.length];
        }

        @Nullable String validate() {
            if (useRaw) {
                try {
                    JsonParser.parseString(raw.getValue());
                    return null;
                } catch (Exception e) {
                    return "task JSON: " + e.getMessage();
                }
            }
            if (!QuestParser.TASK_TYPES.contains(type) && !type.contains(":")) return "bad task type " + type;
            if (QuestParser.ITEM_TASKS.contains(type) || type.equals("item")) {
                String err = itemError(item.getValue());
                if (err != null) return err;
            }
            return null;
        }

        JsonObject toJson(JsonObject langOut) {
            if (useRaw) return JsonParser.parseString(raw.getValue().isBlank() ? "{}" : raw.getValue()).getAsJsonObject();
            JsonObject o = new JsonObject();
            if (!id.getValue().isBlank()) o.addProperty("id", id.getValue().trim());
            o.addProperty("type", type);
            switch (type) {
                case "item", "craft", "smelt", "pickup", "use" -> {
                    o.addProperty("item", normalizeItem(item.getValue()));
                    long c = parseLong(count.getValue(), 1);
                    if (c != 1) o.addProperty("count", c);
                    if (type.equals("item")) {
                        if (consume) o.addProperty("consume", true);
                        if (!handIn.equals("both")) o.addProperty("hand_in", handIn);
                        if (teamTotal) o.addProperty("team_total", true);
                    }
                    if (fromStats && QuestParser.STATS_TASKS.contains(type)) o.addProperty("from_stats", true);
                }
                case "place", "break" -> {
                    if (!block.getValue().isBlank()) o.addProperty("block", block.getValue().trim());
                    long c = parseLong(count.getValue(), 1);
                    if (c != 1) o.addProperty("count", c);
                }
                case "kill" -> {
                    if (!entity.getValue().isBlank()) o.addProperty("entity", entity.getValue().trim());
                    long c = parseLong(count.getValue(), 1);
                    if (c != 1) o.addProperty("count", c);
                }
                case "dimension" -> { if (!dimension.getValue().isBlank()) o.addProperty("dimension", dimension.getValue().trim()); }
                case "biome" -> { if (!biome.getValue().isBlank()) o.addProperty("biome", biome.getValue().trim()); }
                case "advancement" -> { if (!advancement.getValue().isBlank()) o.addProperty("advancement", advancement.getValue().trim()); }
                case "stat" -> {
                    if (!stat.getValue().isBlank()) o.addProperty("stat", stat.getValue().trim());
                    long c = parseLong(count.getValue(), 1);
                    if (c != 1) o.addProperty("count", c);
                }
                case "checkbox" -> {
                    if (!text.getValue().isBlank()) o.addProperty("text", text.getValue());
                }
                default -> {}
            }
            return o;
        }

    }

    static final class RewardRow {
        String kind;
        final EditBox item, count, command, label, icon, loot, raw;
        boolean useRaw;

        RewardRow(Font font, int x, int y, @Nullable JsonObject src) {
            JsonObject o = src == null ? new JsonObject() : src;
            kind = kindOf(o);
            useRaw = kind.equals("custom") || (o.has("type") && o.get("type").getAsString().contains(":"));
            item = TaskRow.box(font, x, y, 140, TaskRow.str(o, "item"));
            count = TaskRow.box(font, x + 150, y, 40, o.has("count") ? o.get("count").getAsString() : o.has("xp") ? o.get("xp").getAsString() : o.has("xp_levels") ? o.get("xp_levels").getAsString() : "1");
            command = TaskRow.box(font, x, y + 22, 280, TaskRow.str(o, "command"));
            label = TaskRow.box(font, x, y + 44, 140, TaskRow.str(o, "label"));
            icon = TaskRow.box(font, x + 150, y + 44, 140, TaskRow.str(o, "icon"));
            loot = TaskRow.box(font, x, y + 22, 280, TaskRow.str(o, "loot_table"));
            raw = TaskRow.box(font, x, y + 22, 280, o.toString());
        }

        static String kindOf(JsonObject o) {
            if (o.has("type") && o.get("type").getAsString().contains(":")) return "custom";
            if (o.has("item")) return "item";
            if (o.has("xp")) return "xp";
            if (o.has("xp_levels")) return "xp_levels";
            if (o.has("command")) return "command";
            if (o.has("loot_table")) return "loot_table";
            if (o.has("type")) return "custom";
            return "item";
        }

        void cycleKind() {
            int i = indexOf(REWARD_KINDS, kind);
            kind = REWARD_KINDS[(i + 1) % REWARD_KINDS.length];
            useRaw = kind.equals("custom");
            if (useRaw) raw.setValue("{\"type\":\"custom:json\"}");
        }

        @Nullable String validate() {
            if (useRaw) {
                try { JsonParser.parseString(raw.getValue()); return null; }
                catch (Exception e) { return "reward JSON: " + e.getMessage(); }
            }
            return switch (kind) {
                case "item" -> itemError(item.getValue());
                case "command" -> command.getValue().isBlank() ? "command required" : null;
                case "loot_table" -> loot.getValue().isBlank() ? "loot_table required" : null;
                default -> null;
            };
        }

        JsonObject toJson() {
            if (useRaw) {
                String v = raw.getValue().isBlank() ? "{\"type\":\"custom:json\"}" : raw.getValue();
                return JsonParser.parseString(v).getAsJsonObject();
            }
            JsonObject o = new JsonObject();
            long c = parseLong(count.getValue(), 1);
            switch (kind) {
                case "item" -> {
                    o.addProperty("item", normalizeItem(item.getValue()));
                    if (c != 1) o.addProperty("count", c);
                }
                case "xp" -> o.addProperty("xp", c);
                case "xp_levels" -> o.addProperty("xp_levels", c);
                case "command" -> {
                    o.addProperty("command", command.getValue());
                    if (!label.getValue().isBlank()) o.addProperty("label", label.getValue());
                    if (!icon.getValue().isBlank()) o.addProperty("icon", icon.getValue());
                }
                case "loot_table" -> o.addProperty("loot_table", loot.getValue().trim());
                default -> {}
            }
            return o;
        }
    }

    static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    static long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
