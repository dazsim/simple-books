package com.workshopcraft.simplebooks.forge;

import com.workshopcraft.simplebooks.box.HandInBoxBlockEntity;
import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;

/** One always empty slot that counts what goes in. */
public final class BoxHandler implements IItemHandler {
    private final HandInBoxBlockEntity box;

    public BoxHandler(HandInBoxBlockEntity box) {
        this.box = box;
    }

    public static void attach(AttachCapabilitiesEvent<BlockEntity> e) {
        if (!(e.getObject() instanceof HandInBoxBlockEntity box)) return;
        LazyOptional<IItemHandler> opt = LazyOptional.of(() -> box.handler(BoxHandler::new));
        e.addCapability(SimpleBooks.id("hand_in_box"), new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                return ForgeCapabilities.ITEM_HANDLER.orEmpty(cap, opt);
            }
        });
        e.addListener(opt::invalidate);
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return stack;
        long n = box.insert(stack, stack.getCount(), simulate);
        return n <= 0 ? stack : stack.copyWithCount(stack.getCount() - (int) n);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return box.insert(stack, 1, true) > 0;
    }
}
