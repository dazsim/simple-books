package com.workshopcraft.simplebooks.mixin;

import com.workshopcraft.simplebooks.quest.QuestEngine;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    //? if >=1.20.2 {
    private void simplebooks$award(net.minecraft.advancements.AdvancementHolder a, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ((PlayerAdvancements) (Object) this).getOrStartProgress(a).isDone())
            QuestEngine.advancement(player, a.id().toString());
    }
    //?} else {
    /*private void simplebooks$award(net.minecraft.advancements.Advancement a, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ((PlayerAdvancements) (Object) this).getOrStartProgress(a).isDone())
            QuestEngine.advancement(player, a.getId().toString());
    }
    *///?}
}
