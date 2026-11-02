package com.workshopcraft.simplebooks.quest;

import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;
//? if >=1.21.5 {
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
//?} else if >=1.20.5 {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixTypes;
*///?}

/** The world's quest save. A save it cannot read is kept as raw NBT and written back untouched. */
public class QuestData extends SavedData {
    public static final String NAME = "simplebooks_quests";
    private QuestSave.Data data = QuestSave.Data.EMPTY;
    private @Nullable CompoundTag raw;

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
        //? if >=1.21.5 {
        return server.getDataStorage().computeIfAbsent(TYPE);
        //?} else if >=1.20.5 {
        /*return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        *///?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(QuestData::load, QuestData::new, NAME);
        *///?}
    }


    public QuestSave.Data data() {
        return data;
    }

    /** True when the file came from a newer Simple Books or could not be read; nothing may change it then. */
    public boolean locked() {
        return raw != null;
    }

    static QuestData load(CompoundTag tag) {
        QuestData d = new QuestData();
        int version = tag.contains("version") ? number(tag.get("version")) : QuestSave.VERSION;
        if (version > QuestSave.VERSION) {
            SimpleBooks.LOG.error("Quest save is version {} from a newer Simple Books (this one reads up to {}). It is kept as it is and quests will not save until you update.",
                    version, QuestSave.VERSION);
            d.raw = tag.copy();
            return d;
        }
        var result = QuestSave.Data.CODEC.parse(NbtOps.INSTANCE, tag);
        var error = result.error();
        if (error.isPresent() || result.result().isEmpty()) {
            SimpleBooks.LOG.error("Quest save could not be read, it is kept as it is: {}", error.map(Object::toString).orElse("empty"));
            d.raw = tag.copy();
            return d;
        }
        d.data = result.result().get();
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
        if (raw != null) return raw.copy();
        Tag t = QuestSave.Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow(
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
