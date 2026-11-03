package com.workshopcraft.simplebooks.box;

import com.workshopcraft.simplebooks.quest.HandInBoxes;
import com.workshopcraft.simplebooks.quest.HandInBoxes.Alloc;
import com.workshopcraft.simplebooks.quest.HandInBoxes.Shown;
import com.workshopcraft.simplebooks.quest.QuestEngine;
import com.workshopcraft.simplebooks.quest.QuestMc;
import com.workshopcraft.simplebooks.quest.QuestWorld.Team;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Owner team and target, plus what the label shows. Items pass straight through into the team's counters. */
public class HandInBoxBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] SLOTS = {0};
    private String team = "", target = "";
    // shown on the label, refreshed by the server once a second
    private String teamName = "", quest = "", task = "";
    private long have, need;
    private boolean linked;
    private int signal, ticks;
    private @Nullable Object handler;

    public HandInBoxBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleBooksContent.HAND_IN_BOX_ENTITY, pos, state);
    }

    public String team() {
        return team;
    }

    public String target() {
        return target;
    }

    public String teamName() {
        return teamName;
    }

    public boolean linked() {
        return linked;
    }

    public String quest() {
        return quest;
    }

    public String task() {
        return task;
    }

    public long have() {
        return have;
    }

    public long need() {
        return need;
    }

    public int signal() {
        return signal;
    }

    public void setTeam(String id) {
        team = id;
        refresh(true);
    }

    public void setTarget(String quest) {
        target = quest;
        refresh(true);
    }

    /** Server side: the owning team, or null when it is gone. */
    public @Nullable Team owner() {
        return QuestEngine.server() == null ? null : HandInBoxes.team(team);
    }

    public List<Alloc> offer(ItemStack stack, long amount, @Nullable Map<String, Long> pending) {
        if (level == null || level.isClientSide()) return List.of();
        return HandInBoxes.offer(team, target, stack, amount, pending);
    }

    public long credit(List<Alloc> allocs) {
        long n = HandInBoxes.credit(team, allocs);
        if (n > 0) setChanged();
        return n;
    }

    /** Offers and credits at once, for callers with no transaction. Returns how many were taken. */
    public long insert(ItemStack stack, long amount, boolean simulate) {
        List<Alloc> a = offer(stack, amount, null);
        return simulate ? HandInBoxes.total(a) : credit(a);
    }

    /** The loader's item handler for this box, made once so transactions see one journal. */
    @SuppressWarnings("unchecked")
    public <T> T handler(java.util.function.Function<HandInBoxBlockEntity, T> make) {
        if (handler == null) handler = make.apply(this);
        return (T) handler;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HandInBoxBlockEntity be) {
        if (++be.ticks % 20 == 0) be.refresh(false);
    }

    private void refresh(boolean force) {
        if (level == null || level.isClientSide()) {
            setChanged();
            return;
        }
        Team t = owner();
        Shown s = t == null ? Shown.NONE : HandInBoxes.shown(team, target);
        String name = t == null ? "" : HandInBoxes.teamName(t);
        boolean changed = force || t != null != linked || !name.equals(teamName) || !s.quest().equals(quest)
                || !s.task().equals(task) || s.have() != have || s.need() != need;
        linked = t != null;
        teamName = name;
        quest = s.quest();
        task = s.task();
        have = s.have();
        need = s.need();
        int sig = HandInBoxes.signal(s);
        if (sig != signal) {
            signal = sig;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        if (changed) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean canUse(ServerPlayer p) {
        Team t = owner();
        return t == null || t.members.contains(p.getUUID().toString()) || QuestMc.op(p);
    }

    // ---- one slot that is always empty: hoppers and droppers ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return !offer(stack, 1, null).isEmpty();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!stack.isEmpty()) insert(stack, stack.getCount(), false);
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {}

    // ---- save and sync ----

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        team = in.getStringOr("team", "");
        target = in.getStringOr("target", "");
        teamName = in.getStringOr("team_name", "");
        quest = in.getStringOr("quest", "");
        task = in.getStringOr("task", "");
        have = in.getLongOr("have", 0);
        need = in.getLongOr("need", 0);
        linked = in.getBooleanOr("linked", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("team", team);
        out.putString("target", target);
        out.putString("team_name", teamName);
        out.putString("quest", quest);
        out.putString("task", task);
        out.putLong("have", have);
        out.putLong("need", need);
        out.putBoolean("linked", linked);
    }
    //?} else {
    /*private void read(CompoundTag in) {
        team = in.getString("team");
        target = in.getString("target");
        teamName = in.getString("team_name");
        quest = in.getString("quest");
        task = in.getString("task");
        have = in.getLong("have");
        need = in.getLong("need");
        linked = in.getBoolean("linked");
    }

    private void write(CompoundTag out) {
        out.putString("team", team);
        out.putString("target", target);
        out.putString("team_name", teamName);
        out.putString("quest", quest);
        out.putString("task", task);
        out.putLong("have", have);
        out.putLong("need", need);
        out.putBoolean("linked", linked);
    }
    *///?}

    //? if >=1.20.5 && <1.21.6 {
    /*@Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        read(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag);
    }
    *///?} else if <1.20.5 {
    /*@Override
    public void load(CompoundTag tag) {
        super.load(tag);
        read(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
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
}
