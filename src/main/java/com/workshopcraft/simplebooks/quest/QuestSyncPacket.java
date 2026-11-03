package com.workshopcraft.simplebooks.quest;

import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/** Server to client: kind is state (team and quest progress), toast or open, json is the body. */
//? if >=1.20.5 {
public record QuestSyncPacket(String kind, byte[] json) implements CustomPacketPayload {
    public static final Type<QuestSyncPacket> TYPE = new Type<>(SimpleBooks.id("quest_sync"));
    public static final StreamCodec<ByteBuf, QuestSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, QuestSyncPacket::kind,
            ByteBufCodecs.BYTE_ARRAY, QuestSyncPacket::json,
            QuestSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record QuestSyncPacket(String kind, byte[] json) {
*///?}
    public static final net.minecraft.resources.Identifier ID = SimpleBooks.id("quest_sync");

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(kind);
        buf.writeByteArray(json);
    }

    public static QuestSyncPacket read(FriendlyByteBuf buf) {
        return new QuestSyncPacket(buf.readUtf(), buf.readByteArray());
    }

    public void handle() {
        QuestClientStore.accept(this);
    }
}
