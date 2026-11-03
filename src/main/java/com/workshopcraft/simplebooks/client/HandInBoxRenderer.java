package com.workshopcraft.simplebooks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.workshopcraft.simplebooks.box.HandInBoxBlockEntity;
import com.workshopcraft.simplebooks.quest.QuestDefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
//? if >=26.1 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
*///?}
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// The label above a hand-in box the player looks at from up to 8 blocks: the task's icon, its quest and how far it is.
//? if >=26.1 {
public class HandInBoxRenderer implements BlockEntityRenderer<HandInBoxBlockEntity, HandInBoxRenderer.State> {
//?} else {
/*public class HandInBoxRenderer implements BlockEntityRenderer<HandInBoxBlockEntity> {
*///?}
    private static final double RANGE = 8;

    //? if >=26.1 {
    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState icon = new ItemStackRenderState();
    //?} else {
    /*public static class State {
        ItemStack icon = ItemStack.EMPTY;
    *///?}
        boolean shown, hasIcon;
        @Nullable FormattedCharSequence title, amount;
    }

    private final Font font;
    //? if >=26.1 {
    private final ItemModelResolver items;

    public HandInBoxRenderer(BlockEntityRendererProvider.Context ctx) {
        font = ctx.font();
        items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HandInBoxBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumbling);
        extract(be, s, partial);
    }
    //?} else {
    /*private final ItemRenderer items;
    private final State state = new State();

    public HandInBoxRenderer(BlockEntityRendererProvider.Context ctx) {
        font = ctx.getFont();
        items = ctx.getItemRenderer();
    }
    *///?}

    /** 41.2K, 100K, 3.5M: short enough for a label. */
    public static String amount(long n) {
        if (n < 1000) return Long.toString(n);
        String[] units = {"K", "M", "B", "T"};
        double v = n;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        String s = v >= 100 ? Long.toString((long) v) : String.format(java.util.Locale.ROOT, "%.1f", Math.floor(v * 10) / 10);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + units[u];
    }

    private static boolean lookedAt(HandInBoxBlockEntity be, float partial) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.3 {
        /*boolean hidden = mc.gui.hud.isHidden();
        *///?} else {
        boolean hidden = mc.options.hideGui;
        //?}
        if (mc.player == null || hidden) return false;
        HitResult hit = mc.player.pick(RANGE, partial, false);
        return hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK && b.getBlockPos().equals(be.getBlockPos());
    }

    private void extract(HandInBoxBlockEntity be, State s, float partial) {
        s.shown = lookedAt(be, partial);
        s.hasIcon = false;
        s.title = s.amount = null;
        if (!s.shown) return;
        ItemStack icon = ItemStack.EMPTY;
        Component title;
        if (!be.linked()) title = Component.translatable("simplebooks.box.no_team");
        else if (be.quest().isEmpty()) title = Component.translatable("simplebooks.box.idle");
        else {
            QuestDefs.Quest q = QuestSlots.def(be.quest());
            title = q == null ? Component.literal(be.quest()) : Texts.of(q.title());
            if (q != null) for (QuestDefs.Task t : q.tasks()) {
                if (!t.id().equals(be.task())) continue;
                ItemRef ref = QuestSlots.taskIcon(t);
                if (ref != null) icon = ref.stack();
            }
            s.amount = Component.literal(amount(be.have()) + " / " + amount(be.need())).getVisualOrderText();
        }
        s.title = Component.literal(font.plainSubstrByWidth(title.getString(), 160)).getVisualOrderText();
        s.hasIcon = !icon.isEmpty();
        //? if >=26.1 {
        if (s.hasIcon) items.updateForTopItem(s.icon, icon, ItemDisplayContext.GUI, be.getLevel(), null, 0);
        //?} else {
        /*s.icon = icon;
        *///?}
    }

    //? if >=26.1 {
    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!s.shown || s.title == null) return;
        int light = 0xF000F0;
        pose.pushPose();
        pose.translate(0.5, s.hasIcon ? 1.75 : 1.45, 0.5);
        pose.mulPose(new org.joml.Matrix4f().rotation(camera.orientation));
        if (s.hasIcon) {
            pose.pushPose();
            pose.scale(0.4F, 0.4F, 0.01F);
            s.icon.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
            pose.translate(0, -0.3, 0);
        }
        pose.scale(0.025F, -0.025F, 0.025F);
        collector.submitText(pose, -font.width(s.title) / 2F, 0, s.title, false, Font.DisplayMode.NORMAL, light, -1, 0x60000000, 0);
        if (s.amount != null)
            collector.submitText(pose, -font.width(s.amount) / 2F, 11, s.amount, false, Font.DisplayMode.NORMAL, light, 0xFFFFE080, 0x60000000, 0);
        pose.popPose();
    }
    //?} else {
    /*@Override
    public void render(HandInBoxBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        State s = state;
        extract(be, s, partial);
        if (!s.shown || s.title == null) return;
        light = 0xF000F0;
        pose.pushPose();
        pose.translate(0.5, s.hasIcon ? 1.75 : 1.45, 0.5);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        if (s.hasIcon) {
            pose.pushPose();
            pose.scale(0.4F, 0.4F, 0.01F);
            items.renderStatic(s.icon, ItemDisplayContext.GUI, light, OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
            pose.popPose();
            pose.translate(0, -0.3, 0);
        }
        pose.scale(0.025F, -0.025F, 0.025F);
        font.drawInBatch(s.title, -font.width(s.title) / 2F, 0, -1, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x60000000, light);
        if (s.amount != null)
            font.drawInBatch(s.amount, -font.width(s.amount) / 2F, 11, 0xFFFFE080, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x60000000, light);
        pose.popPose();
    }
    *///?}
}
