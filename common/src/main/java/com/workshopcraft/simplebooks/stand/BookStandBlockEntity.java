package com.workshopcraft.simplebooks.stand;

import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BookStandBlockEntity extends BlockEntity {
    private ItemStack book = ItemStack.EMPTY;

    public BookStandBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleBooksContent.STAND_ENTITY, pos, state);
    }

    public ItemStack book() {
        return book;
    }

    public int spread() {
        return book.getOrDefault(SimpleBooksContent.SPREAD, 0);
    }

    public void setBook(ItemStack stack) {
        book = stack;
        sync();
    }

    public void setSpread(int spread) {
        if (book.isEmpty()) return;
        book.set(SimpleBooksContent.SPREAD, spread);
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        book = input.read("book", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!book.isEmpty()) output.store("book", ItemStack.CODEC, book);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !book.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, book.copy());
    }
}
