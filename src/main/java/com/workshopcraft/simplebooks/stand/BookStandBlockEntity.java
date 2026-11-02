package com.workshopcraft.simplebooks.stand;

import com.workshopcraft.simplebooks.GuideItem;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

public class BookStandBlockEntity extends BlockEntity {
    private ItemStack book = ItemStack.EMPTY;

    public BookStandBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleBooksContent.STAND_ENTITY, pos, state);
    }

    public ItemStack book() {
        return book;
    }

    public int spread() {
        return GuideItem.spread(book);
    }

    public void setBook(ItemStack stack) {
        book = stack;
        sync();
    }

    public void setSpread(int spread) {
        if (book.isEmpty()) return;
        GuideItem.setSpread(book, spread);
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void dropBook(BlockPos pos) {
        if (level != null && !book.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, book.copy());
    }

    //? if >=1.21.6 {
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
    //?} else if >=1.20.5 {
    /*@Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        book = tag.contains("book") ? ItemStack.parseOptional(registries, tag.getCompound("book")) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!book.isEmpty()) tag.put("book", book.save(registries));
    }
    *///?} else {
    /*@Override
    public void load(CompoundTag tag) {
        super.load(tag);
        book = tag.contains("book") ? ItemStack.of(tag.getCompound("book")) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!book.isEmpty()) tag.put("book", book.save(new CompoundTag()));
    }
    *///?}

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    //? if >=1.20.5 {
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
    //?} else {
    /*@Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }
    *///?}

    //? if >=1.21.5 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        dropBook(pos);
    }
    //?}
}
