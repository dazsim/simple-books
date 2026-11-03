package com.workshopcraft.simplebooks.box;

//? if >=1.20.5
import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplebooks.quest.HandInBoxes;
import com.workshopcraft.simplebooks.quest.QuestEngine;
import com.workshopcraft.simplebooks.quest.QuestWorld.Team;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class HandInBoxBlock extends BaseEntityBlock {
    //? if >=1.20.5 && <26.3
    public static final MapCodec<HandInBoxBlock> CODEC = simpleCodec(HandInBoxBlock::new);

    public HandInBoxBlock(Properties properties) {
        super(properties);
    }

    //? if >=1.20.5 && <26.3 {
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    //?}

    //? if <1.21.2 {
    /*@Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
    *///?}

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HandInBoxBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SimpleBooksContent.HAND_IN_BOX_ENTITY, HandInBoxBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer p && QuestEngine.server() != null && level.getBlockEntity(pos) instanceof HandInBoxBlockEntity box)
            box.setTeam(QuestEngine.team(p).id);
    }

    /** Server side right click: links a box with no team, opens the screen for members and ops. */
    public static void use(ServerPlayer p, BlockPos pos) {
        if (QuestEngine.server() == null || !(p.level().getBlockEntity(pos) instanceof HandInBoxBlockEntity box)) return;
        Team owner = box.owner();
        if (owner == null) {
            Team t = QuestEngine.team(p);
            box.setTeam(t.id);
            p.sendSystemMessage(Component.translatable("simplebooks.box.linked", HandInBoxes.teamName(t)));
        } else if (!box.canUse(p)) {
            p.sendSystemMessage(Component.translatable("simplebooks.box.not_yours", HandInBoxes.teamName(owner)));
            return;
        }
        HandInBoxes.screen(p, pos, box.team(), box.target());
    }

    /** From the box screen: text is "x y z", quest is empty for any quest. */
    public static QuestEngine.Result target(ServerPlayer p, String text, String quest) {
        String[] xyz = text.trim().split(" ");
        BlockPos pos;
        try {
            pos = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        } catch (RuntimeException e) {
            return new QuestEngine.Result(false, "");
        }
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64
                || !(p.level().getBlockEntity(pos) instanceof HandInBoxBlockEntity box) || box.owner() == null || !box.canUse(p)
                || !HandInBoxes.isTarget(box.team(), quest))
            return new QuestEngine.Result(false, "");
        box.setTarget(quest);
        HandInBoxes.screen(p, pos, box.team(), box.target());
        return new QuestEngine.Result(true, "");
    }

    private InteractionResult interact(Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer sp) use(sp, pos);
        //? if >=1.21.2 {
        return InteractionResult.SUCCESS;
        //?} else {
        /*return InteractionResult.sidedSuccess(level.isClientSide());
        *///?}
    }

    //? if >=1.20.5 {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player);
    }
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(level, pos, player);
    }
    *///?}

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    //? if >=26.1 {
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof HandInBoxBlockEntity box ? box.signal() : 0;
    }
    //?} else {
    /*@Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HandInBoxBlockEntity box ? box.signal() : 0;
    }
    *///?}
}
