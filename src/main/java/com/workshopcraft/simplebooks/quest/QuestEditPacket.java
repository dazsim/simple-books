package com.workshopcraft.simplebooks.quest;

import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/** Client to server: a quest book edit. op is put_quest, delete_quest, put_chapter, delete_chapter, put_pool, delete_pool or undo. */
//? if >=1.20.5 {
public record QuestEditPacket(String op, String id, String json) implements CustomPacketPayload {
    public static final Type<QuestEditPacket> TYPE = new Type<>(SimpleBooks.id("quest_edit"));
    public static final StreamCodec<ByteBuf, QuestEditPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, QuestEditPacket::op,
            ByteBufCodecs.STRING_UTF8, QuestEditPacket::id,
            ByteBufCodecs.STRING_UTF8, QuestEditPacket::json,
            QuestEditPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record QuestEditPacket(String op, String id, String json) {
*///?}
    public static final net.minecraft.resources.Identifier ID = SimpleBooks.id("quest_edit");

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(op);
        buf.writeUtf(id);
        buf.writeUtf(json);
    }

    public static QuestEditPacket read(FriendlyByteBuf buf) {
        return new QuestEditPacket(buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public void handle(ServerPlayer player) {
        QuestEditor.Result r = QuestEditor.handle(player, op, id, json);
        if (r != null && !r.message().isEmpty())
            player.sendSystemMessage(Component.literal(r.message()));
    }
}
