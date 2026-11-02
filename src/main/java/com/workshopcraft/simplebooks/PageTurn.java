package com.workshopcraft.simplebooks;

import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import com.workshopcraft.simplebooks.stand.BookStandBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/** Client tells the server which spread a held book or a stand's book is open at. */
//? if >=1.20.5 {
public record PageTurn(int hand, BlockPos pos, int spread) implements CustomPacketPayload {
    public static final Type<PageTurn> TYPE = new Type<>(SimpleBooks.id("page_turn"));
    public static final StreamCodec<ByteBuf, PageTurn> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PageTurn::hand,
            BlockPos.STREAM_CODEC, PageTurn::pos,
            ByteBufCodecs.VAR_INT, PageTurn::spread,
            PageTurn::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record PageTurn(int hand, BlockPos pos, int spread) {
*///?}
    public static final net.minecraft.resources.Identifier ID = SimpleBooks.id("page_turn");

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(hand);
        buf.writeBlockPos(pos);
        buf.writeVarInt(spread);
    }

    public static PageTurn read(FriendlyByteBuf buf) {
        return new PageTurn(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt());
    }

    public void handle(ServerPlayer player) {
        int s = Mth.clamp(spread, 0, 9999);
        if (hand == 0 || hand == 1) {
            ItemStack stack = player.getItemInHand(hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            if (stack.is(SimpleBooksContent.GUIDE)) GuideItem.setSpread(stack, s);
        } else if (inRange(player) && player.level().isLoaded(pos)
                && player.level().getBlockEntity(pos) instanceof BookStandBlockEntity stand) {
            stand.setSpread(s);
        }
    }

    private boolean inRange(ServerPlayer player) {
        //? if >=1.20.5 {
        return player.isWithinBlockInteractionRange(pos, 4.0);
        //?} else {
        /*return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 81.0;
        *///?}
    }
}
