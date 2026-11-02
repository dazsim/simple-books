package com.workshopcraft.simplebooks;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;
//? if >=1.20.5 {
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.PackCompatibility;
import java.util.Optional;
//?}

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The always-on packs read from config/simplebooks/data and config/simplebooks/assets. */
public final class ConfigPack {
    public static final String ID = "simplebooks_config";

    private ConfigPack() {}

    public static Path root() {
        return SimpleBooks.configDir.get().resolve(SimpleBooks.MOD_ID);
    }

    public static void createFolders() {
        try {
            Files.createDirectories(root().resolve("data"));
            Files.createDirectories(root().resolve("assets"));
        } catch (IOException e) {
            SimpleBooks.LOG.warn("Could not create {}: {}", root(), e.toString());
        }
    }

    public static RepositorySource source(PackType type) {
        return out -> {
            Pack p = create(type);
            if (p != null) out.accept(p);
        };
    }

    // one folder serves both pack types, data/ and assets/ sit side by side like in a mod jar
    private static @Nullable Pack create(PackType type) {
        Path root = root();
        if (!Files.isDirectory(root)) return null;
        Component title = Component.literal("Simple Books (pack config)");
        Component desc = Component.literal("config/simplebooks");
        //? if >=1.20.5 {
        return new Pack(new PackLocationInfo(ID, title, PackSource.BUILT_IN, Optional.empty()),
                new PathPackResources.PathResourcesSupplier(root),
                new Pack.Metadata(desc, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of()),
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        //?} else {
        /*return Pack.create(ID, title, true, n -> new PathPackResources(n, root, false),
                new Pack.Info(desc, net.minecraft.SharedConstants.getCurrentVersion().getPackVersion(type), FeatureFlagSet.of()),
                type, Pack.Position.TOP, false, PackSource.BUILT_IN);
        *///?}
    }

    /** Puts our pack right above the last built-in or mod pack, below world, server and player packs. */
    public static @Nullable List<Pack> reorder(List<Pack> selected) {
        int ours = -1;
        for (int i = 0; i < selected.size(); i++) if (selected.get(i).getId().equals(ID)) ours = i;
        if (ours < 0) return null;
        List<Pack> list = new ArrayList<>(selected);
        Pack pack = list.remove(ours);
        int at = 0;
        for (int i = 0; i < list.size(); i++) if (!outside(list.get(i).getId())) at = i + 1;
        if (at == ours) return null;
        list.add(at, pack);
        return list;
    }

    private static boolean outside(String id) {
        return id.startsWith("file/") || id.startsWith("server") || id.startsWith("world");
    }
}
