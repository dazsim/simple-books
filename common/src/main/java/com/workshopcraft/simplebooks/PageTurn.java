package com.workshopcraft.simplebooks;

import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import com.workshopcraft.simplebooks.stand.BookStandBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Client tells the server which spread a held book or a stand's book is open at. */
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

    public void handle(ServerPlayer player) {
        int s = Math.clamp(spread, 0, 9999);
        if (hand == 0 || hand == 1) {
            ItemStack stack = player.getItemInHand(hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            if (stack.is(SimpleBooksContent.GUIDE)) stack.set(SimpleBooksContent.SPREAD, s);
        } else if (player.isWithinBlockInteractionRange(pos, 4.0) && player.level().isLoaded(pos)
                && player.level().getBlockEntity(pos) instanceof BookStandBlockEntity stand) {
            stand.setSpread(s);
        }
    }
}
