package com.workshopcraft.simplebooks.quest;

import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
//? if >=1.21.5 {
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
//?} else if >=1.20.5 {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixTypes;
*///?}

/** The world's quest save. A save it cannot read is copied to a backup and replaced with a fresh one. */
public class QuestData extends SavedData {
    public static final String NAME = "simplebooks_quests";
    private QuestWorld world = QuestWorld.of(QuestSave.Data.EMPTY);
    private @Nullable String bad;
    private static @Nullable MinecraftServer checked;

    //? if >=1.21.5 {
    public static final SavedDataType<QuestData> TYPE = new SavedDataType<>(SimpleBooks.id("quests"), QuestData::new,
            CompoundTag.CODEC.xmap(QuestData::load, QuestData::toTag), fix());
    //?} else if >=1.20.5 {
    /*public static final SavedData.Factory<QuestData> FACTORY = new SavedData.Factory<>(QuestData::new, (tag, p) -> load(tag), fix());
    *///?}

    //? if >=1.20.5 {
    // a null fix type crashes vanilla's loader on Fabric; this one has no fixes, so the tag passes through as is
    private static DataFixTypes fix() {
        //? if fabric {
        return DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES;
        //?} else {
        /*return null;
        *///?}
    }
    //?}

    public static QuestData get(MinecraftServer server) {
        if (checked != server) {
            checked = server;
            checkFile(server);
        }
        //? if >=1.21.5 {
        QuestData d = server.getDataStorage().computeIfAbsent(TYPE);
        //?} else if >=1.20.5 {
        /*QuestData d = server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        *///?} else {
        /*QuestData d = server.overworld().getDataStorage().computeIfAbsent(QuestData::load, QuestData::new, NAME);
        *///?}
        if (d.bad != null) {
            backup(server, d.bad);
            d.bad = null;
            d.setDirty();
        }
        return d;
    }

    static Path file(MinecraftServer server) {
        Path data = server.getWorldPath(LevelResource.ROOT).resolve("data");
        //? if >=1.21.5 {
        return data.resolve("simplebooks").resolve("quests.dat");
        //?} else {
        /*return data.resolve(NAME + ".dat");
        *///?}
    }

    // vanilla drops a save it cannot parse and overwrites it later, so set it aside first
    private static void checkFile(MinecraftServer server) {
        Path file = file(server);
        if (!Files.isRegularFile(file)) return;
        try {
            byte[] b = Files.readAllBytes(file);
            if (b.length > 1 && (b[0] & 0xff) == 0x1f && (b[1] & 0xff) == 0x8b) {
                //? if >=1.20.2 {
                NbtIo.readCompressed(new ByteArrayInputStream(b), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                //?} else {
                /*NbtIo.readCompressed(new ByteArrayInputStream(b));
                *///?}
            } else {
                NbtIo.read(new DataInputStream(new ByteArrayInputStream(b)));
            }
        } catch (Exception | StackOverflowError e) {
            backup(server, "could not be read (" + e + ")");
            try {
                Files.delete(file);
            } catch (IOException ignored) {
            }
        }
    }

    private static void backup(MinecraftServer server, String why) {
        Path file = file(server);
        Path copy = file.resolveSibling(file.getFileName() + "." + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".bak");
        try {
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            SimpleBooks.LOG.warn("Quest save {}. A copy is at {} and quests start fresh.", why, copy);
        } catch (Exception e) {
            SimpleBooks.LOG.warn("Quest save {}. Could not copy it to {} ({}), quests start fresh.", why, copy, e.toString());
        }
    }

    public QuestWorld world() {
        return world;
    }

    public QuestSave.Data data() {
        return world.save();
    }

    static QuestData load(CompoundTag tag) {
        QuestData d = new QuestData();
        int version = tag.contains("version") ? number(tag.get("version")) : QuestSave.VERSION;
        if (version > QuestSave.VERSION) {
            d.bad = "is version " + version + " from a newer Simple Books (this one reads up to " + QuestSave.VERSION + ")";
            return d;
        }
        try {
            var result = QuestSave.Data.CODEC.parse(NbtOps.INSTANCE, tag);
            var error = result.error();
            if (error.isPresent() || result.result().isEmpty()) {
                d.bad = "could not be read (" + error.map(Object::toString).orElse("empty") + ")";
                return d;
            }
            d.world = QuestWorld.of(result.result().get());
        } catch (Exception e) {
            d.bad = "could not be read (" + e + ")";
        }
        return d;
    }

    private static int number(@Nullable Tag t) {
        return t instanceof net.minecraft.nbt.NumericTag n ?
                //? if >=1.21.5 {
                n.intValue()
                //?} else {
                /*n.getAsInt()
                *///?}
                : -1;
    }

    CompoundTag toTag() {
        Tag t = QuestSave.Data.CODEC.encodeStart(NbtOps.INSTANCE, world.save()).getOrThrow(
                //? if <1.20.5 {
                /*false, e -> SimpleBooks.LOG.error("Quest save could not be written: {}", e)
                *///?}
        );
        return (CompoundTag) t;
    }

    //? if >=1.20.5 && <1.21.5 {
    /*@Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return save(tag);
    }
    *///?}

    //? if <1.21.5 {
    /*public CompoundTag save(CompoundTag tag) {
        CompoundTag t = toTag();
        for (String k : t.getAllKeys()) tag.put(k, t.get(k));
        return tag;
    }
    *///?}
}
