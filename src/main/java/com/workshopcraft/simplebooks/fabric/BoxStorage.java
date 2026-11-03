package com.workshopcraft.simplebooks.fabric;

import com.workshopcraft.simplebooks.box.HandInBoxBlockEntity;
import com.workshopcraft.simplebooks.quest.HandInBoxes;
import com.workshopcraft.simplebooks.quest.HandInBoxes.Alloc;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Insert only: items offered in a transaction are counted when the outer transaction commits. */
public final class BoxStorage extends SnapshotParticipant<Integer> implements InsertionOnlyStorage<ItemVariant> {
    private final HandInBoxBlockEntity box;
    private final List<Alloc> allocs = new ArrayList<>();
    private final Map<String, Long> pending = new HashMap<>();

    public BoxStorage(HandInBoxBlockEntity box) {
        this.box = box;
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext tx) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        List<Alloc> a = box.offer(resource.toStack(1), maxAmount, pending);
        if (a.isEmpty()) return 0;
        updateSnapshots(tx);
        for (Alloc x : a) pending.merge(x.key(), x.n(), Long::sum);
        allocs.addAll(a);
        return HandInBoxes.total(a);
    }

    @Override
    protected Integer createSnapshot() {
        return allocs.size();
    }

    @Override
    protected void readSnapshot(Integer size) {
        while (allocs.size() > size) {
            Alloc x = allocs.remove(allocs.size() - 1);
            pending.merge(x.key(), -x.n(), Long::sum);
        }
    }

    @Override
    protected void onFinalCommit() {
        List<Alloc> done = new ArrayList<>(allocs);
        allocs.clear();
        pending.clear();
        box.credit(done);
    }
}
