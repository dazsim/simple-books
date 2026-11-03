package com.workshopcraft.simplebooks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** config/simplebooks.json, read on every server start. */
public record SimpleBooksConfig(String teamSource, String firstJoinBook, boolean handInBox, int commandRewardPermission) {
    public static final SimpleBooksConfig DEFAULT = new SimpleBooksConfig("simplebooks", "", true, 2);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SimpleBooksConfig current = DEFAULT;

    public static SimpleBooksConfig get() {
        return current;
    }

    public static Path file() {
        return SimpleBooks.configDir.get().resolve("simplebooks.json");
    }

    public static SimpleBooksConfig load() {
        Path file = file();
        if (!Files.exists(file)) {
            current = DEFAULT;
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(DEFAULT.toJson(), w);
            } catch (Exception e) {
                SimpleBooks.LOG.warn("Could not write {}: {}", file, e.toString());
            }
            return current;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            current = read(JsonParser.parseReader(r).getAsJsonObject());
        } catch (Exception e) {
            SimpleBooks.LOG.error("Could not read {}, using defaults: {}", file, e.toString());
            current = DEFAULT;
        }
        return current;
    }

    static SimpleBooksConfig read(JsonObject o) {
        for (String k : o.keySet())
            if (!Set.of("team_source", "first_join_book", "hand_in_box", "command_reward_permission").contains(k))
                SimpleBooks.LOG.warn("simplebooks.json: unknown key {}", k);
        String team = str(o, "team_source", DEFAULT.teamSource);
        if (!team.equals("simplebooks") && com.workshopcraft.simplebooks.api.SimpleBooksApi.teamSource(team) == null) {
            SimpleBooks.LOG.warn("simplebooks.json: team_source {} is not simplebooks or one of {}, using simplebooks", team,
                    com.workshopcraft.simplebooks.api.SimpleBooksApi.teamSources());
            team = DEFAULT.teamSource;
        }
        boolean box = DEFAULT.handInBox;
        JsonElement b = o.get("hand_in_box");
        if (b instanceof JsonPrimitive p && p.isBoolean()) box = p.getAsBoolean();
        else if (b != null) SimpleBooks.LOG.warn("simplebooks.json: hand_in_box must be true or false");
        int perm = DEFAULT.commandRewardPermission;
        JsonElement n = o.get("command_reward_permission");
        if (n instanceof JsonPrimitive p && p.isNumber() && p.getAsInt() >= 0 && p.getAsInt() <= 4) perm = p.getAsInt();
        else if (n != null) SimpleBooks.LOG.warn("simplebooks.json: command_reward_permission must be 0 to 4");
        return new SimpleBooksConfig(team, str(o, "first_join_book", DEFAULT.firstJoinBook), box, perm);
    }

    private static String str(JsonObject o, String k, String def) {
        JsonElement e = o.get(k);
        if (e == null) return def;
        if (e instanceof JsonPrimitive p && p.isString()) return p.getAsString();
        SimpleBooks.LOG.warn("simplebooks.json: {} must be a string", k);
        return def;
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("team_source", teamSource);
        o.addProperty("first_join_book", firstJoinBook);
        o.addProperty("hand_in_box", handInBox);
        o.addProperty("command_reward_permission", commandRewardPermission);
        return o;
    }
}
