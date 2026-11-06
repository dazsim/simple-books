package com.workshopcraft.simplebooks.quest;

import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/** Server to client: JSON object of lang key -> text overrides from the editor. */
//? if >=1.20.5 {
public record QuestLangPacket(String json) implements CustomPacketPayload {
    public static final Type<QuestLangPacket> TYPE = new Type<>(SimpleBooks.id("quest_lang"));
    public static final StreamCodec<ByteBuf, QuestLangPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, QuestLangPacket::json,
            QuestLangPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record QuestLangPacket(String json) {
*///?}
    public static final net.minecraft.resources.Identifier ID = SimpleBooks.id("quest_lang");

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(json);
    }

    public static QuestLangPacket read(FriendlyByteBuf buf) {
        return new QuestLangPacket(buf.readUtf());
    }

    public void handle() {
        QuestClientStore.acceptLang(this);
    }
}
