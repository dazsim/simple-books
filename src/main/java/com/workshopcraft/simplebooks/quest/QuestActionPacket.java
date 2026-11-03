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

/** Client to server: a button in a quest book. action is checkbox, hand_in, claim or team_*. */
//? if >=1.20.5 {
public record QuestActionPacket(String action, String quest, String task, int number, String text) implements CustomPacketPayload {
    public static final Type<QuestActionPacket> TYPE = new Type<>(SimpleBooks.id("quest_action"));
    public static final StreamCodec<ByteBuf, QuestActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, QuestActionPacket::action,
            ByteBufCodecs.STRING_UTF8, QuestActionPacket::quest,
            ByteBufCodecs.STRING_UTF8, QuestActionPacket::task,
            ByteBufCodecs.VAR_INT, QuestActionPacket::number,
            ByteBufCodecs.STRING_UTF8, QuestActionPacket::text,
            QuestActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record QuestActionPacket(String action, String quest, String task, int number, String text) {
*///?}
    public static final net.minecraft.resources.Identifier ID = SimpleBooks.id("quest_action");

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(action);
        buf.writeUtf(quest);
        buf.writeUtf(task);
        buf.writeVarInt(number);
        buf.writeUtf(text);
    }

    public static QuestActionPacket read(FriendlyByteBuf buf) {
        return new QuestActionPacket(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf());
    }

    public void handle(ServerPlayer player) {
        if (QuestEngine.server() == null) return;
        QuestEngine.Result r = switch (action) {
            case "checkbox" -> QuestEngine.checkbox(player, quest, task);
            case "hand_in" -> QuestEngine.handIn(player, quest, task);
            case "claim" -> QuestEngine.claim(player, quest, number);
            case "team_create" -> QuestEngine.create(player, text);
            case "team_invite" -> QuestEngine.invite(player, text);
            case "team_join" -> QuestEngine.join(player, text, number == 1);
            case "team_leave" -> QuestEngine.leave(player);
            case "team_kick" -> QuestEngine.kick(player, text);
            case "team_owner" -> QuestEngine.owner(player, text);
            case "box_target" -> com.workshopcraft.simplebooks.box.HandInBoxBlock.target(player, text, quest);
            default -> null;
        };
        if (r == null) return;
        if (!r.message().isEmpty() && (!r.ok() || action.startsWith("team_") || action.equals("hand_in")))
            player.sendSystemMessage(Component.literal(r.message()));
        QuestEngine.flush();
    }
}
