package com.workshopcraft.simplebooks.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Version shims for the quest engine. */
public final class QuestMc {
    private QuestMc() {}

    public static ServerLevel level(ServerPlayer p) {
        //? if >=26.1 {
        return p.level();
        //?} else {
        /*return p.serverLevel();
        *///?}
    }

    public static MinecraftServer server(ServerPlayer p) {
        return level(p).getServer();
    }

    public static String key(ResourceKey<?> k) {
        //? if >=1.21.11 {
        return k.identifier().toString();
        //?} else {
        /*return k.location().toString();
        *///?}
    }

    static @Nullable Identifier id(String s) {
        try {
            return SimpleBooks.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** ref is an id or "#tag". */
    public static <T> boolean matches(Registry<T> reg, T value, String ref) {
        if (ref.startsWith("#")) {
            Identifier t = id(ref.substring(1));
            return t != null && reg.wrapAsHolder(value).is(TagKey.create(reg.key(), t));
        }
        Identifier k = reg.getKey(value);
        return k != null && k.toString().equals(ref);
    }

    public static <T> boolean matchesAny(Registry<T> reg, T value, List<String> refs) {
        for (String r : refs) if (matches(reg, value, r)) return true;
        return false;
    }

    public static boolean itemMatches(ItemStack stack, List<String> refs) {
        return !stack.isEmpty() && matchesAny(BuiltInRegistries.ITEM, stack.getItem(), refs);
    }

    public static boolean biomeMatches(Holder<net.minecraft.world.level.biome.Biome> h, List<String> refs) {
        for (String r : refs) {
            Identifier i = id(r.startsWith("#") ? r.substring(1) : r);
            if (i == null) continue;
            if (r.startsWith("#") ? h.is(TagKey.create(Registries.BIOME, i)) : h.is(ResourceKey.create(Registries.BIOME, i))) return true;
        }
        return false;
    }

    public static String dimension(ServerPlayer p) {
        return key(p.level().dimension());
    }

    public static boolean advancementDone(ServerPlayer p, String id) {
        Identifier i = id(id);
        if (i == null) return false;
        //? if >=1.20.2 {
        net.minecraft.advancements.AdvancementHolder a = server(p).getAdvancements().get(i);
        //?} else {
        /*net.minecraft.advancements.Advancement a = server(p).getAdvancements().getAdvancement(i);
        *///?}
        return a != null && p.getAdvancements().getOrStartProgress(a).isDone();
    }

    /** The stat for "minecraft:mined" + "minecraft:stone", or a custom stat when value is the stat id. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Stat<?> stat(String type, @Nullable String value) {
        Identifier t = id(type);
        if (t == null) return null;
        Optional<StatType<?>> st = BuiltInRegistries.STAT_TYPE.getOptional(t);
        if (st.isEmpty() || value == null) return null;
        Identifier v = id(value);
        if (v == null) return null;
        Optional<?> val = ((Registry) st.get().getRegistry()).getOptional(v);
        if (val.isEmpty()) return null;
        return ((StatType) st.get()).get(val.get());
    }

    /** {type id, value id} of a stat. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String[] statKey(Stat<?> stat) {
        StatType type = stat.getType();
        Identifier t = BuiltInRegistries.STAT_TYPE.getKey(type);
        Identifier v = ((Registry) type.getRegistry()).getKey(stat.getValue());
        return new String[]{t == null ? "" : t.toString(), v == null ? "" : v.toString()};
    }

    public static CommandSourceStack source(ServerPlayer p, int level) {
        //? if >=26.1 {
        return p.createCommandSourceStack().withPermission(
                net.minecraft.server.permissions.LevelBasedPermissionSet.forLevel(net.minecraft.server.permissions.PermissionLevel.byId(level)))
                .withSuppressedOutput();
        //?} else {
        /*return p.createCommandSourceStack().withPermission(level).withSuppressedOutput();
        *///?}
    }

    public static void run(MinecraftServer server, CommandSourceStack src, String command) {
        server.getCommands().performPrefixedCommand(src, command);
    }

    public static void give(ServerPlayer p, ItemStack stack) {
        //? if >=26.3 {
        /*p.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        p.getInventory().placeItemBackInInventory(stack);
        //?}
    }

    /** One stack of the item with the given components (or nbt on 1.20.1), or empty when it can't be built. */
    public static ItemStack stack(MinecraftServer server, String item, @Nullable JsonElement data) {
        Identifier i = id(item);
        if (i == null || !BuiltInRegistries.ITEM.containsKey(i)) return ItemStack.EMPTY;
        if (data == null) return new ItemStack(SimpleBooks.item(i));
        try {
            //? if >=1.20.5 {
            JsonObject o = new JsonObject();
            o.addProperty("id", item);
            o.addProperty("count", 1);
            o.add("components", data);
            return ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), o)
                    .result().orElse(ItemStack.EMPTY);
            //?} else {
            /*ItemStack s = new ItemStack(SimpleBooks.item(i));
            net.minecraft.nbt.CompoundTag tag = data.isJsonPrimitive()
                    ? net.minecraft.nbt.TagParser.parseTag(data.getAsString())
                    : (net.minecraft.nbt.CompoundTag) JsonOps.INSTANCE.convertTo(net.minecraft.nbt.NbtOps.INSTANCE, data);
            s.setTag(tag);
            return s;
            *///?}
        } catch (Exception e) {
            SimpleBooks.LOG.warn("Could not build reward item {}: {}", item, e.toString());
            return ItemStack.EMPTY;
        }
    }

    public static List<ItemStack> loot(ServerPlayer p, String table) {
        List<ItemStack> out = new ArrayList<>();
        Identifier i = id(table);
        if (i == null) return out;
        MinecraftServer server = server(p);
        //? if >=1.20.5 {
        LootTable t = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, i));
        //?} else {
        /*LootTable t = server.getLootData().getLootTable(i);
        *///?}
        LootParams params = new LootParams.Builder(level(p))
                .withParameter(LootContextParams.THIS_ENTITY, p)
                .withParameter(LootContextParams.ORIGIN, p.position())
                .withLuck(p.getLuck())
                .create(LootContextParamSets.ADVANCEMENT_REWARD);
        t.getRandomItems(params, out::add);
        return out;
    }

    public static @Nullable ServerPlayer player(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id);
    }

    public static String name(MinecraftServer server, UUID id) {
        ServerPlayer p = player(server, id);
        if (p != null) return p.getScoreboardName();
        try {
            //? if >=26.1 {
            Optional<String> n = server.services().nameToIdCache().get(id).map(x -> x.name());
            //?} else {
            /*Optional<String> n = server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(id).map(x -> x.getName());
            *///?}
            if (n.isPresent()) return n.get();
        } catch (Exception ignored) {
        }
        return id.toString().substring(0, 8);
    }

    public static @Nullable UUID byName(MinecraftServer server, String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p != null) return p.getUUID();
        try {
            //? if >=26.1 {
            return server.services().nameToIdCache().get(name).map(x -> x.id()).orElse(null);
            //?} else {
            /*return server.getProfileCache() == null ? null : server.getProfileCache().get(name).map(x -> x.getId()).orElse(null);
            *///?}
        } catch (Exception e) {
            return null;
        }
    }

    /** The scoreboard team name of an online player, or null. */
    public static @Nullable String scoreboardTeam(ServerPlayer p) {
        net.minecraft.world.scores.PlayerTeam t = server(p).getScoreboard().getPlayersTeam(p.getScoreboardName());
        return t == null ? null : t.getName();
    }
}
