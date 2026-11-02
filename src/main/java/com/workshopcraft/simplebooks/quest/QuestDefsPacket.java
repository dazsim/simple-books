package com.workshopcraft.simplebooks.quest;

import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/** Server sends quest definitions, one chunk per chapter. Chunk 0 starts over, the last one commits. */
//? if >=1.20.5 {
public record QuestDefsPacket(int index, int total, byte[] json) implements CustomPacketPayload {
    public static final Type<QuestDefsPacket> TYPE = new Type<>(SimpleBooks.id("quest_defs"));
    public static final StreamCodec<ByteBuf, QuestDefsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestDefsPacket::index,
            ByteBufCodecs.VAR_INT, QuestDefsPacket::total,
            ByteBufCodecs.BYTE_ARRAY, QuestDefsPacket::json,
            QuestDefsPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record QuestDefsPacket(int index, int total, byte[] json) {
*///?}
    public static final net.minecraft.resources.Identifier ID = SimpleBooks.id("quest_defs");

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(index);
        buf.writeVarInt(total);
        buf.writeByteArray(json);
    }

    public static QuestDefsPacket read(FriendlyByteBuf buf) {
        return new QuestDefsPacket(buf.readVarInt(), buf.readVarInt(), buf.readByteArray());
    }

    public void handle() {
        QuestClientStore.accept(this);
    }
}
