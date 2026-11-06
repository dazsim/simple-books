package com.workshopcraft.simplebooks.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.workshopcraft.simplebooks.ConfigPack;
import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-side quest book editor: permission, validate, write into the config pack, undo, soft reload.
 */
public final class QuestEditor {
    public record Result(boolean ok, String message) {
        static Result ok(String m) { return new Result(true, m); }
        static Result no(String m) { return new Result(false, m); }
    }

    private record FileSnap(Path path, byte @Nullable [] prev, boolean existed) {}
    private record Undo(List<FileSnap> files) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<UUID, Deque<Undo>> UNDOS = new HashMap<>();
    /** Loader sets this to push quest defs packets to one player. */
    public static Consumer<ServerPlayer> sendDefs = p -> {};
    /** Loader sets this to push a lang override packet to one player. */
    public static java.util.function.BiConsumer<ServerPlayer, QuestLangPacket> sendLang = (p, pkt) -> {};

    private QuestEditor() {}

    public static boolean canEdit(ServerPlayer p) {
        return QuestMc.op(p) || p.isCreative();
    }

    public static Result handle(ServerPlayer player, String op, String id, String json) {
        if (!canEdit(player)) return Result.no("You need to be an op or in creative to edit quests.");
        if ("undo".equals(op)) return undo(player);
        return switch (op) {
            case "put_quest" -> put(player, QuestParser.Kind.QUEST, id, json);
            case "delete_quest" -> delete(player, QuestParser.Kind.QUEST, id);
            case "put_chapter" -> put(player, QuestParser.Kind.CHAPTER, id, json);
            case "delete_chapter" -> delete(player, QuestParser.Kind.CHAPTER, id);
            case "put_pool" -> put(player, QuestParser.Kind.POOL, id, json);
            case "delete_pool" -> delete(player, QuestParser.Kind.POOL, id);
            default -> Result.no("Unknown edit op " + op);
        };
    }

    private static Result put(ServerPlayer player, QuestParser.Kind kind, String id, String json) {
        JsonObject body;
        try {
            JsonElement el = JsonParser.parseString(json == null ? "" : json);
            if (!el.isJsonObject()) return Result.no("Edit body must be a JSON object.");
            body = el.getAsJsonObject();
        } catch (Exception e) {
            return Result.no("Bad JSON: " + e.getMessage());
        }
        JsonObject langUpdates = null;
        if (body.has("_lang") && body.get("_lang").isJsonObject()) {
            langUpdates = body.getAsJsonObject("_lang").deepCopy();
            body.remove("_lang");
        }
        Path path;
        try {
            path = configPath(kind, id);
        } catch (IllegalArgumentException e) {
            return Result.no(e.getMessage());
        }
        List<QuestParser.Entry> entries = currentEntries();
        String book = bookOf(id);
        String file = "data/" + pathLabel(path);
        entries.removeIf(e -> e.id().equals(id));
        entries.add(new QuestParser.Entry(kind, book, id, file, body, null));
        Problems problems = new Problems(false);
        QuestDefs next = QuestParser.parse(entries, false, problems);
        List<String> errors = new ArrayList<>();
        for (Problems.Problem p : problems.all())
            if (p.level() == Problems.Level.ERROR && (p.book().equals(book) || p.file().equals(file)))
                errors.add((p.ptr().isEmpty() ? "" : p.ptr() + ": ") + p.msg());
        if (next.books().containsKey(book) && next.books().get(book).broken().containsKey(id))
            errors.add(next.books().get(book).broken().get(id));
        if (!errors.isEmpty()) return Result.no("Not saved:\n" + String.join("\n", errors));

        List<FileSnap> snaps = new ArrayList<>();
        String langNote = writeLang(langUpdates, book, snaps);
        boolean copied = !Files.isRegularFile(path);
        snaps.add(new FileSnap(path, readBytes(path), Files.isRegularFile(path)));
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(body) + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Result.no("Could not write " + path + ": " + e);
        }
        pushUndo(player, new Undo(snaps));
        apply(next);
        String note = copied
                ? "Saved a copy to config/simplebooks/" + pathLabel(path) + " (was not in the config pack)."
                : "Saved " + shortId(id) + ".";
        if (langNote != null) note = note + " " + langNote;
        return Result.ok(note);
    }

    private static Result delete(ServerPlayer player, QuestParser.Kind kind, String id) {
        Path path;
        try {
            path = configPath(kind, id);
        } catch (IllegalArgumentException e) {
            return Result.no(e.getMessage());
        }
        List<QuestParser.Entry> entries = currentEntries();
        if (entries.stream().noneMatch(e -> e.id().equals(id))) return Result.no("No " + kind.name().toLowerCase() + " " + id);
        String book = bookOf(id);
        entries.removeIf(e -> e.id().equals(id));
        Problems problems = new Problems(false);
        QuestDefs next = QuestParser.parse(entries, false, problems);
        List<String> errors = new ArrayList<>();
        for (Problems.Problem p : problems.all())
            if (p.level() == Problems.Level.ERROR && p.book().equals(book))
                errors.add((p.ptr().isEmpty() ? "" : p.ptr() + ": ") + p.msg());
        if (!errors.isEmpty()) return Result.no("Not deleted:\n" + String.join("\n", errors));

        byte[] prev = readBytes(path);
        boolean existed = prev != null || Files.isRegularFile(path);
        try {
            if (Files.isRegularFile(path)) Files.delete(path);
        } catch (Exception e) {
            return Result.no("Could not delete " + path + ": " + e);
        }
        pushUndo(player, new Undo(List.of(new FileSnap(path, prev, existed))));
        apply(next);
        return Result.ok(existed && prev != null
                ? "Deleted " + shortId(id) + " from the config pack."
                : "Removed " + shortId(id) + " from the loaded book (no config file to delete).");
    }

    private static Result undo(ServerPlayer player) {
        Deque<Undo> stack = UNDOS.get(player.getUUID());
        if (stack == null || stack.isEmpty()) return Result.no("Nothing to undo.");
        Undo u = stack.pop();
        try {
            for (FileSnap f : u.files()) {
                if (f.existed() && f.prev() != null) {
                    Files.createDirectories(f.path().getParent());
                    Files.write(f.path(), f.prev());
                } else if (Files.isRegularFile(f.path())) {
                    Files.delete(f.path());
                }
            }
        } catch (Exception e) {
            return Result.no("Undo failed: " + e);
        }
        Problems problems = new Problems(false);
        QuestDefs next = QuestParser.parse(currentEntriesFromDiskAndMemory(), false, problems);
        apply(next);
        return Result.ok("Undid last edit.");
    }

    private static void apply(QuestDefs next) {
        QuestManager.set(next);
        MinecraftServer server = QuestEngine.server();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendDefs.accept(p);
    }

    private static void pushUndo(ServerPlayer player, Undo u) {
        UNDOS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>()).push(u);
        Deque<Undo> d = UNDOS.get(player.getUUID());
        while (d.size() > 32) d.removeLast();
    }

    private static byte @Nullable [] readBytes(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Build parser entries from the currently loaded defs' json bodies. */
    static List<QuestParser.Entry> currentEntries() {
        List<QuestParser.Entry> out = new ArrayList<>();
        for (QuestDefs.Book b : QuestManager.defs().books().values()) {
            for (QuestDefs.Chapter c : b.chapters().values())
                out.add(new QuestParser.Entry(QuestParser.Kind.CHAPTER, b.id(), c.id(), "memory:" + c.id(), c.json().deepCopy(), null));
            for (QuestDefs.Quest q : b.quests().values())
                out.add(new QuestParser.Entry(QuestParser.Kind.QUEST, b.id(), q.id(), "memory:" + q.id(), q.json().deepCopy(), null));
            for (QuestDefs.Pool p : b.pools().values())
                out.add(new QuestParser.Entry(QuestParser.Kind.POOL, b.id(), p.id(), "memory:" + p.id(), p.json().deepCopy(), null));
        }
        return out;
    }

    /** After undo, prefer config-pack files on disk, then fall back to memory for anything else. */
    private static List<QuestParser.Entry> currentEntriesFromDiskAndMemory() {
        Map<String, QuestParser.Entry> byId = new HashMap<>();
        for (QuestParser.Entry e : currentEntries()) byId.put(e.id(), e);
        Path data = ConfigPack.root().resolve("data");
        if (Files.isDirectory(data)) {
            try (var walk = Files.walk(data)) {
                walk.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                    try {
                        String rel = data.relativize(p).toString().replace('\\', '/');
                        // ns/simplebooks/quests/book/(chapters|quests|pools)/rest.json
                        String[] parts = rel.split("/");
                        if (parts.length < 6 || !"simplebooks".equals(parts[1]) || !"quests".equals(parts[2])) return;
                        QuestParser.Kind kind = switch (parts[4]) {
                            case "chapters" -> QuestParser.Kind.CHAPTER;
                            case "quests" -> QuestParser.Kind.QUEST;
                            case "pools" -> QuestParser.Kind.POOL;
                            default -> null;
                        };
                        if (kind == null) return;
                        String ns = parts[0];
                        String bookName = parts[3];
                        String rest = String.join("/", java.util.Arrays.copyOfRange(parts, 5, parts.length));
                        if (!rest.endsWith(".json")) return;
                        rest = rest.substring(0, rest.length() - 5);
                        String book = ns + ":" + bookName;
                        String id = book + "/" + rest;
                        JsonElement el = JsonParser.parseString(Files.readString(p));
                        byId.put(id, new QuestParser.Entry(kind, book, id, "data/" + rel, el, null));
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }
        // drop entries whose config file was deleted and that only lived in config: keep memory ones
        // that still exist as files or never had a config file. Simplest: memory first, disk overlays.
        return new ArrayList<>(byId.values());
    }

    static Path configPath(QuestParser.Kind kind, String id) {
        int slash = id.indexOf('/');
        int colon = id.indexOf(':');
        if (colon < 0 || slash < 0 || slash < colon)
            throw new IllegalArgumentException("Bad id " + id + " (want ns:book/path)");
        String ns = id.substring(0, colon);
        String book = id.substring(colon + 1, slash);
        String rest = id.substring(slash + 1);
        String folder = switch (kind) {
            case CHAPTER -> "chapters";
            case QUEST -> "quests";
            case POOL -> "pools";
        };
        return ConfigPack.root().resolve("data").resolve(ns).resolve("simplebooks").resolve("quests")
                .resolve(book).resolve(folder).resolve(rest + ".json");
    }

    static String bookOf(String id) {
        int slash = id.indexOf('/');
        if (slash < 0) throw new IllegalArgumentException("Bad id " + id);
        return id.substring(0, slash);
    }

    private static String pathLabel(Path path) {
        Path root = ConfigPack.root();
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (Exception e) {
            return path.toString();
        }
    }

    private static String shortId(String id) {
        int slash = id.indexOf('/');
        return slash < 0 ? id : id.substring(slash + 1);
    }

    /** Write _lang key->text into assets/<bookNs>/lang/en_us.json. */
    private static @Nullable String writeLang(@Nullable JsonObject lang, String bookId, List<FileSnap> snaps) {
        if (lang == null || lang.size() == 0) return null;
        int colon = bookId.indexOf(':');
        String ns = colon < 0 ? "minecraft" : bookId.substring(0, colon);
        Path langPath = ConfigPack.root().resolve("assets").resolve(ns).resolve("lang").resolve("en_us.json");
        JsonObject file = new JsonObject();
        byte[] prev = readBytes(langPath);
        if (prev != null) {
            try {
                JsonElement el = JsonParser.parseString(new String(prev, StandardCharsets.UTF_8));
                if (el.isJsonObject()) file = el.getAsJsonObject();
            } catch (Exception ignored) {}
        }
        snaps.add(new FileSnap(langPath, prev, prev != null));
        for (var e : lang.entrySet())
            if (e.getValue().isJsonPrimitive()) file.addProperty(e.getKey(), e.getValue().getAsString());
        try {
            Files.createDirectories(langPath.getParent());
            Files.writeString(langPath, GSON.toJson(file) + "\n", StandardCharsets.UTF_8);
        } catch (Exception ex) {
            SimpleBooks.LOG.warn("Could not write {}: {}", langPath, ex.toString());
            return null;
        }
        QuestLangPacket pkt = new QuestLangPacket(lang.toString());
        MinecraftServer server = QuestEngine.server();
        if (server != null)
            for (ServerPlayer p : server.getPlayerList().getPlayers()) sendLang.accept(p, pkt);
        return "Updated " + ns + "/lang/en_us.json.";
    }

    public static void clearUndos(UUID player) {
        UNDOS.remove(player);
    }
}
