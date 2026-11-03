package com.workshopcraft.simplebooks.mixin;

import com.workshopcraft.simplebooks.quest.QuestEngine;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Items taken from a furnace count as smelted, not crafted. */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
    @Inject(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void simplebooks$start(ItemStack stack, CallbackInfo ci) {
        QuestEngine.SMELTING.set(true);
    }

    @Inject(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void simplebooks$end(ItemStack stack, CallbackInfo ci) {
        QuestEngine.SMELTING.set(false);
    }
}
