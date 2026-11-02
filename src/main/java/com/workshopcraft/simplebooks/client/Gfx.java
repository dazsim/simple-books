package com.workshopcraft.simplebooks.client;

import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** The few drawing calls the book screen needs, so the screen code reads the same on every Minecraft version. */
public final class Gfx {
    //? if >=26.1 {
    public final net.minecraft.client.gui.GuiGraphicsExtractor g;

    public Gfx(net.minecraft.client.gui.GuiGraphicsExtractor g) {
        this.g = g;
    }
    //?} else {
    /*public final net.minecraft.client.gui.GuiGraphics g;

    public Gfx(net.minecraft.client.gui.GuiGraphics g) {
        this.g = g;
    }
    *///?}

    public void push() {
        //? if >=26.1 {
        g.pose().pushMatrix();
        //?} else {
        /*g.pose().pushPose();
        *///?}
    }

    public void pop() {
        //? if >=26.1 {
        g.pose().popMatrix();
        //?} else {
        /*g.pose().popPose();
        *///?}
    }

    public void translate(float x, float y) {
        //? if >=26.1 {
        g.pose().translate(x, y);
        //?} else {
        /*g.pose().translate(x, y, 0);
        *///?}
    }

    public void scale(float x, float y) {
        //? if >=26.1 {
        g.pose().scale(x, y);
        //?} else {
        /*g.pose().scale(x, y, 1);
        *///?}
    }

    public void rotate(float radians) {
        //? if >=26.1 {
        g.pose().rotate(radians);
        //?} else {
        /*g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(radians));
        *///?}
    }

    public void fill(int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
    }

    public void text(Font font, String s, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        g.text(font, s, x, y, color, shadow);
        //?} else {
        /*g.drawString(font, s, x, y, color, shadow);
        *///?}
    }

    public void text(Font font, Component s, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        g.text(font, s, x, y, color, shadow);
        //?} else {
        /*g.drawString(font, s, x, y, color, shadow);
        *///?}
    }

    public void text(Font font, FormattedCharSequence s, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        g.text(font, s, x, y, color, shadow);
        //?} else {
        /*g.drawString(font, s, x, y, color, shadow);
        *///?}
    }

    public void item(ItemStack stack, int x, int y) {
        //? if >=26.1 {
        g.item(stack, x, y);
        //?} else {
        /*g.renderItem(stack, x, y);
        *///?}
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y, String text) {
        //? if >=26.1 {
        g.itemDecorations(font, stack, x, y, text);
        //?} else {
        /*g.renderItemDecorations(font, stack, x, y, text);
        *///?}
    }

    /** Draws a texture into the box x0..x1, y0..y1 using the texture coordinates u0..u1, v0..v1 (0 to 1). */
    public void blit(Identifier tex, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1) {
        //? if >=26.1 {
        g.blit(tex, x0, y0, x1, y1, u0, u1, v0, v1);
        //?} else {
        /*int n = 1 << 16;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(tex, x0, y0, x1 - x0, y1 - y0, u0 * n, v0 * n, Math.round((u1 - u0) * n), Math.round((v1 - v0) * n), n, n);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        *///?}
    }

    /** The vanilla book's page turn arrow. */
    public void pageButton(boolean forward, boolean hover, int x, int y, int w, int h, int color) {
        //? if >=26.1 {
        Identifier sprite = SimpleBooks.mc("widget/page_" + (forward ? "forward" : "backward") + (hover ? "_highlighted" : ""));
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, color);
        //?} else if >=1.20.2 {
        /*Identifier sprite = SimpleBooks.mc("widget/page_" + (forward ? "forward" : "backward") + (hover ? "_highlighted" : ""));
        tint(color);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blitSprite(sprite, x, y, w, h);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        g.setColor(1, 1, 1, 1);
        *///?} else {
        /*tint(color);
        float u = hover ? 23 : 0, v = forward ? 192 : 205;
        blit(SimpleBooks.mc("textures/gui/book.png"), x, y, x + w, y + h, u / 256, (u + 23) / 256, v / 256, (v + 13) / 256);
        g.setColor(1, 1, 1, 1);
        *///?}
    }

    //? if <26.1 {
    /*private void tint(int c) {
        g.setColor((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f);
    }
    *///?}

    /** Clips to a box given in the current pose's coordinates. */
    public void enableScissor(int x0, int y0, int x1, int y1) {
        //? if >=26.1 {
        g.enableScissor(x0, y0, x1, y1);
        //?} else {
        /*org.joml.Matrix4f m = g.pose().last().pose();
        org.joml.Vector3f a = m.transformPosition(x0, y0, 0, new org.joml.Vector3f());
        org.joml.Vector3f b = m.transformPosition(x1, y1, 0, new org.joml.Vector3f());
        g.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y)));
        *///?}
    }

    public void disableScissor() {
        g.disableScissor();
    }

    public void tooltip(Font font, Component c, int x, int y) {
        //? if >=26.1 {
        g.setTooltipForNextFrame(font, c, x, y);
        //?} else {
        /*g.renderTooltip(font, c, x, y);
        *///?}
    }

    public void tooltip(Font font, ItemStack stack, int x, int y) {
        //? if >=26.1 {
        g.setTooltipForNextFrame(font, stack, x, y);
        //?} else {
        /*g.renderTooltip(font, stack, x, y);
        *///?}
    }

    /** Shows the pointing hand cursor where the version supports it. */
    public void pointer() {
        //? if >=26.1
        g.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
    }
}
