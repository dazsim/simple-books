package com.workshopcraft.simplebooks.stand;

//? if >=1.20.5
import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplebooks.GuideItem;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class BookStandBlock extends BaseEntityBlock {
    //? if >=1.20.5 && <26.3
    public static final MapCodec<BookStandBlock> CODEC = simpleCodec(BookStandBlock::new);
    //? if >=1.21.2 {
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    //?} else {
    /*public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    *///?}
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(6, 2, 6, 10, 12, 10), Block.box(1, 12, 1, 15, 15, 15));

    public BookStandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    //? if >=1.20.5 && <26.3 {
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    //?}

    //? if <1.21.2 {
    /*@Override
    public RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
    *///?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BookStandBlockEntity(pos, state);
    }

    private boolean place(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof BookStandBlockEntity stand)) return false;
        if (!stand.book().isEmpty() || !stack.is(SimpleBooksContent.GUIDE)) return false;
        if (!level.isClientSide()) {
            //? if >=1.20.5 {
            stand.setBook(stack.consumeAndReturn(1, player));
            //?} else {
            /*ItemStack one = stack.copyWithCount(1);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            stand.setBook(one);
            *///?}
            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1, 1);
        }
        return true;
    }

    private InteractionResult interact(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof BookStandBlockEntity stand) || stand.book().isEmpty()) return InteractionResult.PASS;
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide()) {
                ItemStack book = stand.book();
                stand.setBook(ItemStack.EMPTY);
                //? if >=26.3 {
                /*if (!player.getInventory().add(book)) player.drop(book, false, net.minecraft.util.Prediction.SERVER_ONLY);
                *///?} else {
                if (!player.getInventory().add(book)) player.drop(book, false);
                //?}
                level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1, 0.8F);
            }
            return success(level);
        }
        if (level.isClientSide()) {
            Identifier id = GuideItem.book(stand.book());
            if (id != null) SimpleBooks.opener.open(id, stand.spread(), -1, pos);
        }
        return success(level);
    }

    private static InteractionResult success(Level level) {
        //? if >=1.21.2 {
        return InteractionResult.SUCCESS;
        //?} else {
        /*return InteractionResult.sidedSuccess(level.isClientSide());
        *///?}
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (place(stack, level, pos, player)) return InteractionResult.SUCCESS;
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player);
    }
    //?} else if >=1.20.5 {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (place(stack, level, pos, player)) return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BookStandBlockEntity stand) stand.dropBook(pos);
        super.onRemove(state, level, pos, newState, moved);
    }
    *///?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (place(player.getItemInHand(hand), level, pos, player)) return InteractionResult.sidedSuccess(level.isClientSide());
        return interact(level, pos, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BookStandBlockEntity stand) stand.dropBook(pos);
        super.onRemove(state, level, pos, newState, moved);
    }
    *///?}
}
