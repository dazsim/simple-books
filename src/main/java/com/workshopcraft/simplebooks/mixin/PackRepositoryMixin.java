package com.workshopcraft.simplebooks.mixin;

import com.google.common.collect.ImmutableList;
import com.workshopcraft.simplebooks.ConfigPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
//? if fabric {
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;
//?}

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    //? if fabric {
    @Shadow @Final @Mutable private Set<RepositorySource> sources;

    // Fabric API has no hook for packs outside a mod jar
    @Inject(method = "<init>", at = @At("RETURN"))
    private void simplebooks$addSource(RepositorySource[] list, CallbackInfo ci) {
        PackType type = null;
        for (RepositorySource s : sources) if (s instanceof ServerPacksSource) type = PackType.SERVER_DATA;
        if (type == null) for (RepositorySource s : sources) if (s instanceof BuiltInPackSource) type = PackType.CLIENT_RESOURCES;
        if (type == null) return;
        Set<RepositorySource> all = new LinkedHashSet<>(sources);
        all.add(ConfigPack.source(type));
        sources = all;
    }
    //?}

    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void simplebooks$order(Collection<String> names, CallbackInfoReturnable<List<Pack>> cir) {
        List<Pack> moved = ConfigPack.reorder(cir.getReturnValue());
        if (moved != null) cir.setReturnValue(ImmutableList.copyOf(moved));
    }
}
