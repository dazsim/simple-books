package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.SimpleBooksConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server side quest state: the loaded definitions, sync chunks, registry checks and /sbquests. */
public final class QuestManager {
    private static final int CHUNK_CHARS = 200_000;
    private static volatile QuestDefs defs = QuestDefs.EMPTY;
    private static volatile @Nullable List<QuestDefsPacket> packets;

    private QuestManager() {}

    public static QuestDefs defs() {
        return defs;
    }

    static void set(QuestDefs d) {
        defs = d;
        packets = null;
        QuestEngine.defsChanged();
    }

    public static void serverStarted(MinecraftServer server) {
        SimpleBooksConfig config = SimpleBooksConfig.load();
        Problems p = new Problems(true);
        checkRegistries(server, defs, null, p);
        checkConfig(config, defs, p);
        QuestEngine.start(server);
        QuestWorld w = QuestEngine.world();
        SimpleBooks.LOG.info("Quest save: {} teams, {} with progress", w.teams.size(), w.progress.size());
    }

    /** After /reload, once tags and loot tables are in place. */
    public static void reloaded(MinecraftServer server) {
        checkRegistries(server, defs, null, new Problems(true));
    }

    // ---- sync ----

    public static List<QuestDefsPacket> packets() {
        List<QuestDefsPacket> p = packets;
        if (p == null) packets = p = build(defs);
        return p;
    }

    static List<QuestDefsPacket> build(QuestDefs d) {
        List<String> chunks = new ArrayList<>();
        for (QuestDefs.Book b : d.books().values()) {
            Map<String, List<QuestDefs.Quest>> byChapter = new LinkedHashMap<>();
            for (String c : b.chapters().keySet()) byChapter.put(c, new ArrayList<>());
            List<QuestDefs.Quest> rest = new ArrayList<>();
            for (QuestDefs.Quest q : b.quests().values()) {
                List<QuestDefs.Quest> l = q.chapter() == null ? null : byChapter.get(q.chapter());
                (l == null ? rest : l).add(q);
            }
            for (var c : byChapter.entrySet()) {
                JsonObject chunk = start(b.id());
                chunk.getAsJsonObject("chapters").add(c.getKey(), b.chapters().get(c.getKey()).json());
                quests(chunks, chunk, b.id(), c.getValue());
            }
            JsonObject chunk = start(b.id());
            for (QuestDefs.Pool pool : b.pools().values()) chunk.getAsJsonObject("pools").add(pool.id(), pool.json());
            for (var e : b.broken().entrySet()) chunk.getAsJsonObject("broken").addProperty(e.getKey(), e.getValue());
            quests(chunks, chunk, b.id(), rest);
        }
        if (chunks.isEmpty()) chunks.add("{}");
        List<QuestDefsPacket> out = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++)
            out.add(new QuestDefsPacket(i, chunks.size(), chunks.get(i).getBytes(StandardCharsets.UTF_8)));
        return List.copyOf(out);
    }

    private static JsonObject start(String book) {
        JsonObject o = new JsonObject();
        o.addProperty("book", book);
        o.add("chapters", new JsonObject());
        o.add("quests", new JsonObject());
        o.add("pools", new JsonObject());
        o.add("broken", new JsonObject());
        return o;
    }

    // a huge chapter goes out in several chunks so no packet gets near the size limit
    private static void quests(List<String> chunks, JsonObject chunk, String book, List<QuestDefs.Quest> quests) {
        int size = chunk.toString().length();
        for (QuestDefs.Quest q : quests) {
            int len = q.json().toString().length() + q.id().length() + 4;
            if (size + len > CHUNK_CHARS && chunk.getAsJsonObject("quests").size() > 0) {
                chunks.add(chunk.toString());
                chunk = start(book);
                size = chunk.toString().length();
            }
            chunk.getAsJsonObject("quests").add(q.id(), q.json());
            size += len;
        }
        chunks.add(chunk.toString());
    }

    // ---- checks that need the running server ----

    static void checkConfig(SimpleBooksConfig config, QuestDefs d, Problems out) {
        String book = config.firstJoinBook();
        if (!book.isEmpty() && !d.books().containsKey(book))
            out.add(Problems.Level.WARN, book, "config/simplebooks.json", "/first_join_book", "no quest book " + book);
    }

    static void checkRegistries(MinecraftServer server, QuestDefs d, @Nullable String only, Problems out) {
        for (QuestDefs.Book b : d.books().values()) {
            if (only != null && !only.equals(b.id())) continue;
            for (QuestDefs.Chapter c : b.chapters().values()) icon(out, b.id(), c.id(), "/icon", c.icon());
            for (QuestDefs.Quest q : b.quests().values()) {
                String id = q.id();
                icon(out, b.id(), id, "/icon", q.icon());
                for (int i = 0; i < q.tasks().size(); i++) {
                    QuestDefs.Task t = q.tasks().get(i);
                    String ptr = "/tasks/" + t.id();
                    for (String item : t.items()) check(out, b.id(), id, ptr + "/item", item, BuiltInRegistries.ITEM, "item");
                    if (t.block() != null) check(out, b.id(), id, ptr + "/block", t.block(), BuiltInRegistries.BLOCK, "block");
                    if (t.entity() != null) check(out, b.id(), id, ptr + "/entity", t.entity(), BuiltInRegistries.ENTITY_TYPE, "entity");
                    if (t.biome() != null) check(out, b.id(), id, ptr + "/biome", t.biome(), biomes(server), "biome");
                    if (t.dimension() != null && !server.levelKeys().contains(ResourceKey.create(Registries.DIMENSION, parse(t.dimension()))))
                        out.add(Problems.Level.WARN, b.id(), id, ptr + "/dimension", "no dimension " + t.dimension());
                    if (t.advancement() != null && !advancement(server, parse(t.advancement())))
                        out.add(Problems.Level.WARN, b.id(), id, ptr + "/advancement", "no advancement " + t.advancement());
                    if (t.stat() != null) {
                        var type = BuiltInRegistries.STAT_TYPE.getOptional(parse(t.stat()));
                        if (type.isEmpty()) out.add(Problems.Level.WARN, b.id(), id, ptr + "/stat", "no stat type " + t.stat());
                        else if (t.value() != null && !type.get().getRegistry().containsKey(parse(t.value())))
                            out.add(Problems.Level.WARN, b.id(), id, ptr + "/value", "no " + t.value() + " in stat type " + t.stat());
                    }
                }
                rewards(server, out, b.id(), id, "/rewards", q.rewards());
                rewards(server, out, b.id(), id, "/choose_one", q.chooseOne());
                for (int i = 0; i < q.tiers().size(); i++) rewards(server, out, b.id(), id, "/tiers/" + i + "/rewards", q.tiers().get(i).rewards());
            }
            for (QuestDefs.Pool p : b.pools().values()) rewards(server, out, b.id(), p.id(), "/reroll/cost", p.rerollCost());
        }
    }

    private static void rewards(MinecraftServer server, Problems out, String book, String id, String ptr, List<QuestDefs.Reward> list) {
        for (int i = 0; i < list.size(); i++) {
            QuestDefs.Reward r = list.get(i);
            if (r.item() != null) check(out, book, id, ptr + "/" + i + "/item", r.item(), BuiltInRegistries.ITEM, "item");
            icon(out, book, id, ptr + "/" + i + "/icon", r.icon());
            if (r.lootTable() != null && !lootTable(server, parse(r.lootTable())))
                out.add(Problems.Level.WARN, book, id, ptr + "/" + i + "/loot_table", "no loot table " + r.lootTable());
        }
    }

    private static void icon(Problems out, String book, String id, String ptr, @Nullable String icon) {
        if (icon != null && !icon.isEmpty()) check(out, book, id, ptr, icon, BuiltInRegistries.ITEM, "item");
    }

    private static <T> void check(Problems out, String book, String id, String ptr, String ref, Registry<T> reg, String what) {
        if (!has(reg, ref)) out.add(Problems.Level.WARN, book, id, ptr, (ref.startsWith("#") ? "no " + what + " tag " : "no " + what + " ") + ref);
    }

    private static <T> boolean has(Registry<T> reg, String ref) {
        if (!ref.startsWith("#")) return reg.containsKey(parse(ref));
        TagKey<T> tag = TagKey.create(reg.key(), parse(ref.substring(1)));
        //? if >=1.21.2 {
        return reg.get(tag).isPresent();
        //?} else {
        /*return reg.getTag(tag).isPresent();
        *///?}
    }

    private static Registry<net.minecraft.world.level.biome.Biome> biomes(MinecraftServer server) {
        //? if >=1.21.2 {
        return server.registryAccess().lookupOrThrow(Registries.BIOME);
        //?} else {
        /*return server.registryAccess().registryOrThrow(Registries.BIOME);
        *///?}
    }

    private static boolean advancement(MinecraftServer server, Identifier id) {
        //? if >=1.20.2 {
        return server.getAdvancements().get(id) != null;
        //?} else {
        /*return server.getAdvancements().getAdvancement(id) != null;
        *///?}
    }

    private static boolean lootTable(MinecraftServer server, Identifier id) {
        //? if >=1.21.2 {
        return server.reloadableRegistries().lookup().lookup(Registries.LOOT_TABLE)
                .flatMap(r -> r.get(ResourceKey.create(Registries.LOOT_TABLE, id))).isPresent();
        //?} else if >=1.20.5 {
        /*return server.reloadableRegistries().getKeys(Registries.LOOT_TABLE).contains(id);
        *///?} else {
        /*return server.getLootData().getKeys(net.minecraft.world.level.storage.loot.LootDataType.TABLE).contains(id);
        *///?}
    }

    private static Identifier parse(String id) {
        try {
            return SimpleBooks.parse(id);
        } catch (Exception e) {
            return SimpleBooks.mc("missing");
        }
    }

    // ---- commands ----

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        QuestCommands.registerTeam(d);
        var root = Commands.literal("sbquests")
                .requires(
                        //? if >=26.1 {
                        Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)
                        //?} else {
                        /*s -> s.hasPermission(2)
                        *///?}
                )
                .then(Commands.literal("validate")
                        .executes(c -> validate(c, null))
                        .then(Commands.argument("book", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(defs.books().keySet(), b))
                                .executes(c -> validate(c, StringArgumentType.getString(c, "book")))));
        QuestCommands.addAdmin(root);
        d.register(root);
    }

    private static final int SHOW = 30;

    static int validate(CommandContext<CommandSourceStack> c, @Nullable String only) {
        CommandSourceStack src = c.getSource();
        MinecraftServer server = src.getServer();
        Problems p = new Problems(false);
        QuestDefs fresh = QuestReload.load(server.getResourceManager(), p);
        if (only != null && !fresh.books().containsKey(only)) {
            src.sendFailure(Component.literal("No quest book " + only));
            return 0;
        }
        checkRegistries(server, fresh, only, p);
        checkConfig(SimpleBooksConfig.get(), fresh, p);
        List<Problems.Problem> shown = new ArrayList<>();
        for (Problems.Problem x : p.all())
            if (x.level() != Problems.Level.INFO && (only == null || x.book().equals(only))) shown.add(x);
        int errors = 0;
        for (QuestDefs.Book b : fresh.books().values()) {
            if (only != null && !only.equals(b.id())) continue;
            long e = p.count(Problems.Level.ERROR, b.id()), w = p.count(Problems.Level.WARN, b.id());
            errors += (int) e;
            String line = b.id() + ": " + b.chapters().size() + " chapters, " + b.quests().size() + " quests, "
                    + b.pools().size() + " pools, " + b.broken().size() + " broken, " + e + " errors, " + w + " warnings";
            src.sendSuccess(() -> Component.literal(line).withStyle(e > 0 ? ChatFormatting.RED : w > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN), false);
        }
        if (fresh.books().isEmpty()) src.sendSuccess(() -> Component.literal("No quest books found"), false);
        for (int i = 0; i < Math.min(SHOW, shown.size()); i++) {
            Problems.Problem x = shown.get(i);
            src.sendSuccess(() -> Component.literal(x.toString()).withStyle(x.level() == Problems.Level.ERROR ? ChatFormatting.RED : ChatFormatting.YELLOW), false);
        }
        if (shown.size() > SHOW) src.sendSuccess(() -> Component.literal("... and " + (shown.size() - SHOW) + " more, see the server log"), false);
        src.sendSuccess(() -> Component.literal("Checked the files as they are now; /reload applies them").withStyle(ChatFormatting.GRAY), false);
        return errors == 0 ? 1 : 0;
    }
}
