package com.workshopcraft.simplebooks.stand;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BookStandBlock extends BaseEntityBlock {
    public static final MapCodec<BookStandBlock> CODEC = simpleCodec(BookStandBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(6, 2, 6, 10, 12, 10), Block.box(1, 12, 1, 15, 15, 15));

    public BookStandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BookStandBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BookStandBlockEntity stand)) return InteractionResult.PASS;
        if (stand.book().isEmpty() && stack.is(SimpleBooksContent.GUIDE)) {
            if (!level.isClientSide()) {
                stand.setBook(stack.consumeAndReturn(1, player));
                level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1, 1);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BookStandBlockEntity stand) || stand.book().isEmpty()) return InteractionResult.PASS;
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide()) {
                ItemStack book = stand.book();
                stand.setBook(ItemStack.EMPTY);
                if (!player.getInventory().add(book)) player.drop(book, false);
                level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1, 0.8F);
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            Identifier id = stand.book().get(SimpleBooksContent.BOOK);
            if (id != null) SimpleBooks.opener.open(id, stand.spread(), -1, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
