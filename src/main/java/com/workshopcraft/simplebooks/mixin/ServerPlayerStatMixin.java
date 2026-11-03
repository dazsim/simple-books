package com.workshopcraft.simplebooks.mixin;

import com.workshopcraft.simplebooks.quest.QuestEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerStatMixin {
    @Inject(method = "awardStat(Lnet/minecraft/stats/Stat;I)V", at = @At("HEAD"))
    private void simplebooks$stat(Stat<?> stat, int amount, CallbackInfo ci) {
        QuestEngine.stat((ServerPlayer) (Object) this, stat, amount);
    }
}
