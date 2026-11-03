package com.workshopcraft.simplebooks.neoforge;

import com.workshopcraft.simplebooks.box.HandInBoxBlockEntity;
//? if >=26.1 {
import com.workshopcraft.simplebooks.quest.HandInBoxes;
import com.workshopcraft.simplebooks.quest.HandInBoxes.Alloc;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Insert only, one always empty slot: items offered in a transaction are counted when the root transaction commits. */
public final class BoxHandler extends SnapshotJournal<Integer> implements ResourceHandler<ItemResource> {
    private final HandInBoxBlockEntity box;
    private final List<Alloc> allocs = new ArrayList<>();
    private final Map<String, Long> pending = new HashMap<>();

    public BoxHandler(HandInBoxBlockEntity box) {
        this.box = box;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return !resource.isEmpty() && !box.offer(resource.toStack(1), 1, pending).isEmpty();
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        List<Alloc> a = box.offer(resource.toStack(1), amount, pending);
        if (a.isEmpty()) return 0;
        updateSnapshots(tx);
        for (Alloc x : a) pending.merge(x.key(), x.n(), Long::sum);
        allocs.addAll(a);
        return (int) HandInBoxes.total(a);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
        return 0;
    }

    @Override
    protected Integer createSnapshot() {
        return allocs.size();
    }

    @Override
    protected void revertToSnapshot(Integer size) {
        while (allocs.size() > size) {
            Alloc x = allocs.remove(allocs.size() - 1);
            pending.merge(x.key(), -x.n(), Long::sum);
        }
    }

    @Override
    protected void onRootCommit(Integer original) {
        List<Alloc> done = new ArrayList<>(allocs);
        allocs.clear();
        pending.clear();
        box.credit(done);
    }
}
//?} else {
/*import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/^* One always empty slot that counts what goes in. *^/
public final class BoxHandler implements IItemHandler {
    private final HandInBoxBlockEntity box;

    public BoxHandler(HandInBoxBlockEntity box) {
        this.box = box;
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
*///?}
