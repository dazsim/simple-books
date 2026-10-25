package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import com.workshopcraft.simplebooks.stand.BookStandBlock;
import com.workshopcraft.simplebooks.stand.BookStandBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The open book on a stand: parchment pages with each page's title and the spread's item icons, or the book's own stand pictures. */
public class BookStandRenderer implements BlockEntityRenderer<BookStandBlockEntity, BookStandRenderer.State> {
    private static final Identifier PAGE = SimpleBooks.id("textures/block/book_page.png");
    private static final Identifier COVER = SimpleBooks.id("textures/block/book_cover.png");

    public static class State extends BlockEntityRenderState {
        boolean book;
        Identifier cover = COVER, leftPage = PAGE, rightPage = PAGE;
        Direction facing = Direction.NORTH;
        @Nullable Title leftTitle, rightTitle;
        int ink = 0xFF3F2A14;
        final List<ItemStackRenderState> left = new ArrayList<>(), right = new ArrayList<>();
    }

    /** A title fitted to the page: one or two lines at a scale of 1 or less. */
    record Title(List<FormattedCharSequence> lines, float scale) {}

    private static final int TITLE_W = 90;
    private final Font font;
    private final ItemModelResolver items;

    public BookStandRenderer(BlockEntityRendererProvider.Context ctx) {
        font = ctx.font();
        items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BookStandBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumbling);
        s.facing = be.getBlockState().getValue(BookStandBlock.FACING);
        s.book = !be.book().isEmpty();
        s.leftTitle = s.rightTitle = null;
        s.cover = COVER;
        s.leftPage = s.rightPage = PAGE;
        s.left.clear();
        s.right.clear();
        if (!s.book) return;
        Identifier id = be.book().get(SimpleBooksContent.BOOK);
        Book book = id == null ? null : BookRegistry.get(id);
        if (book == null || book.pages().isEmpty()) return;
        Book.Page page = book.pages().get(Math.min(be.spread(), book.pages().size() - 1));
        JsonObject j = page.json();
        s.cover = texture(book.json(), "stand_cover", COVER);
        Identifier paper = texture(book.json(), "stand_page", PAGE);
        s.leftPage = texture(j, "stand_left", paper);
        s.rightPage = texture(j, "stand_right", paper);
        if (j.has("stand_left") || j.has("stand_right")) return;
        String ink = book.vars().get("stand_ink");
        s.ink = parse(ink, 0xFF3F2A14);
        String f = book.vars().get("font");
        Identifier fid = f == null ? null : Identifier.tryParse(f);
        Style style = fid == null ? Style.EMPTY : Style.EMPTY.withFont(new FontDescription.Resource(fid));
        s.leftTitle = title(Spread.bound(j, "title"), style);
        s.rightTitle = title(Spread.bound(j, "right_title|title"), style);
        icons(Spread.bound(j, "ingredients"), s.left, be, 10);
        icons(Spread.bound(j, "tools"), s.left, be, 15);
        JsonElement steps = j.get("steps");
        if (steps != null && steps.isJsonArray()) {
            for (JsonElement st : steps.getAsJsonArray()) {
                if (st.isJsonObject() && st.getAsJsonObject().has("output")) icons(st.getAsJsonObject().get("output"), s.right, be, 15);
            }
        }
    }

    private static Identifier texture(JsonObject o, String key, Identifier def) {
        if (!o.has(key)) return def;
        Identifier id = Identifier.tryParse(o.get(key).getAsString());
        return id == null ? def : id.withPath(p -> (p.startsWith("textures/") ? p : "textures/" + p) + (p.endsWith(".png") ? "" : ".png"));
    }

    private static int parse(@Nullable String hex, int def) {
        if (hex == null || hex.startsWith("$")) return def;
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    /** Shrinks a long title a little, then wraps it to two lines, then shrinks again; only past that is it cut short. */
    private @Nullable Title title(@Nullable JsonElement e, Style style) {
        if (e == null || !e.isJsonPrimitive()) return null;
        Component c = Component.literal(Texts.of(e.getAsString()).getString()).withStyle(style);
        int w = font.width(c);
        if (w <= TITLE_W / 0.8F) return new Title(List.of(c.getVisualOrderText()), Math.min(1, TITLE_W / (float) w));
        for (float k = 1; k >= 0.6F; k -= 0.1F) {
            List<FormattedText> ls = font.getSplitter().splitLines(c, (int) (TITLE_W / k), style);
            if (ls.size() <= 2) return new Title(ls.stream().map(Language.getInstance()::getVisualOrder).toList(), k);
        }
        int max = (int) (TITLE_W / 0.6F);
        List<FormattedText> ls = font.getSplitter().splitLines(c, max, style);
        StringBuilder rest = new StringBuilder();
        for (int i = 1; i < ls.size(); i++) rest.append(i > 1 ? " " : "").append(ls.get(i).getString());
        String second = font.plainSubstrByWidth(rest.toString(), max - font.width("...")).stripTrailing() + "...";
        return new Title(List.of(Language.getInstance().getVisualOrder(ls.get(0)),
                Component.literal(second).withStyle(style).getVisualOrderText()), 0.6F);
    }

    private void icons(@Nullable JsonElement e, List<ItemStackRenderState> out, BookStandBlockEntity be, int max) {
        if (e == null) return;
        List<JsonElement> list = new ArrayList<>();
        if (e.isJsonArray()) e.getAsJsonArray().forEach(list::add);
        else list.add(e);
        for (JsonElement el : list) {
            if (out.size() >= max) return;
            ItemStack stack = ItemRef.parse(el).stack();
            if (stack.isEmpty()) continue;
            ItemStackRenderState r = new ItemStackRenderState();
            items.updateForTopItem(r, stack, ItemDisplayContext.GUI, be.getLevel(), null, out.size());
            out.add(r);
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!s.book) return;
        int light = s.lightCoords;
        pose.pushPose();
        pose.translate(0.5, 15.2 / 16, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(22.5F));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(s.cover), (p, b) -> quad(p, b, -7.5F, -5.5F, 7.5F, 5.5F, 0, light));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(s.leftPage), (p, b) -> quad(p, b, -7F, -5F, -0.15F, 5F, 0.35F, light));
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(s.rightPage), (p, b) -> quad(p, b, 0.15F, -5F, 7F, 5F, 0.35F, light));
        side(s.leftTitle, s.left, -3.6F, pose, collector, light, s.ink);
        side(s.rightTitle, s.right, 3.6F, pose, collector, light, s.ink);
        pose.popPose();
    }

    private void side(@Nullable Title title, List<ItemStackRenderState> icons, float cx, PoseStack pose,
                      SubmitNodeCollector collector, int light, int ink) {
        if (title != null) {
            float sc = 0.6F / 16 / 8 * title.scale(), lh = 9 * 0.6F / 8 * title.scale();
            float z = title.lines().size() > 1 ? -4.6F : -4.4F + (1 - title.scale()) * 0.3F;
            for (FormattedCharSequence line : title.lines()) {
                pose.pushPose();
                pose.translate(cx / 16 - font.width(line) * sc / 2, 0.4F / 16, z / 16);
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                pose.scale(sc, -sc, sc);
                collector.submitText(pose, 0, 0, line, false, Font.DisplayMode.POLYGON_OFFSET, light, ink, 0, 0);
                pose.popPose();
                z += lh;
            }
        }
        int cols = 5;
        for (int i = 0; i < icons.size(); i++) {
            pose.pushPose();
            float x = cx + ((i % cols) - (cols - 1) / 2F) * 1.25F, z = -2.6F + (i / cols) * 1.35F;
            pose.translate(x / 16, 0.45F / 16, z / 16);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(1.0F / 16, 1.0F / 16, 0.01F);
            icons.get(i).submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    private static void quad(PoseStack.Pose p, VertexConsumer b, float x0, float z0, float x1, float z1, float y, int light) {
        v(p, b, x0, y, z0, 0, 0, light);
        v(p, b, x0, y, z1, 0, 1, light);
        v(p, b, x1, y, z1, 1, 1, light);
        v(p, b, x1, y, z0, 1, 0, light);
    }

    private static void v(PoseStack.Pose p, VertexConsumer b, float x, float y, float z, float u, float vv, int light) {
        b.addVertex(p, x / 16, y / 16, z / 16).setColor(-1).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
    }
}
