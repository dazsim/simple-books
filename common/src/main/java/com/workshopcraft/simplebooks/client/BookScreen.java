package com.workshopcraft.simplebooks.client;

import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class BookScreen extends Screen {
    private static final Identifier FWD = Identifier.withDefaultNamespace("widget/page_forward");
    private static final Identifier FWD_HI = Identifier.withDefaultNamespace("widget/page_forward_highlighted");
    private static final Identifier BACK = Identifier.withDefaultNamespace("widget/page_backward");
    private static final Identifier BACK_HI = Identifier.withDefaultNamespace("widget/page_backward_highlighted");
    private static final int BTN_W = 69, BTN_H = 39, BTN_Y = 916, MARK_X = 1460, MARK_Y = 4, MARK_W = 34, MARK_H = 70;

    private final Identifier bookId;
    private final int hand;
    private final net.minecraft.core.@Nullable BlockPos stand;
    private int spread;
    private final Deque<Integer> history = new ArrayDeque<>();
    private List<Spread.Hit> hits = List.of();
    private float scale = 1, ox, oy;
    private static final long TURN_NS = 220_000_000L;
    private int turnFrom = -1;
    private long turnStart;

    public BookScreen(Identifier bookId, int spread, int hand, net.minecraft.core.@Nullable BlockPos stand) {
        super(Component.literal(bookId.toString()));
        this.bookId = bookId;
        this.spread = spread;
        this.hand = hand;
        this.stand = stand;
    }

    public static void open(Identifier book, @Nullable Identifier page) {
        int idx = 0;
        Book b = BookRegistry.get(book);
        if (b != null && page != null) idx = Math.max(0, b.indexOf(page));
        open(book, idx, -2, null);
    }

    public static void open(Identifier book, int spread, int hand, net.minecraft.core.@Nullable BlockPos stand) {
        BookScreen screen = new BookScreen(book, Math.max(0, spread), hand, stand);
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.schedule(() -> mc.setScreen(screen));
    }

    private @Nullable Book book() {
        return BookRegistry.get(bookId);
    }

    private int spreads() {
        Book b = book();
        return b == null ? 1 : Math.max(1, b.spreadCount());
    }

    private void layout() {
        double gui = minecraft.getWindow().getGuiScale();
        Book b = book();
        int w = b != null && !b.tabs().isEmpty() ? Spread.W + 2 * Spread.TAB_MARGIN : Spread.W;
        double fit = Math.min(width * gui / w, height * gui / Spread.H);
        double real = fit >= 1 ? Math.floor(fit * 2) / 2 : fit;
        scale = (float) (real / gui);
        Spread.renderScale = (float) real;
        ox = (width - Spread.W * scale) / 2;
        oy = (height - Spread.H * scale) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        layout();
        Book b = book();
        spread = Math.min(spread, spreads() - 1);
        float mx = (mouseX - ox) / scale, my = (mouseY - oy) / scale;
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(scale, scale);
        Spread s;
        List<Spread> parts = List.of();
        if (b == null) {
            s = new Spread(g, font, emptyBook(), null, 0);
            s.frame();
            s.errorPage("assets/" + bookId.getNamespace() + "/simplebooks/books/" + bookId.getPath() + ".json",
                    List.of("unknown book " + bookId));
        } else {
            boolean empty = b.pages().isEmpty();
            parts = Spread.sheet(g, font, b, spread);
            s = parts.getFirst();
            s.tabs(b.tabs(), mx, my);
            float t = turnFrom < 0 ? 1 : (System.nanoTime() - turnStart) / (float) TURN_NS;
            if (t >= 1 || turnFrom >= spreads() || empty || !b.errors().isEmpty()) turnFrom = -1;
            s.frame();
            if (!b.errors().isEmpty()) s.errorPage("book " + bookId, b.errors());
            else if (empty) s.errorPage("book " + bookId, List.of("the book has no pages"));
            else if (turnFrom < 0) for (Spread p : parts) p.content();
            else {
                turning(g, b, t);
                s.edges();
            }
        }
        if (spread > 0) {
            boolean hi = in(mx, my, Spread.L, BTN_Y, BTN_W, BTN_H);
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, hi ? BACK_HI : BACK, Spread.L, BTN_Y, BTN_W, BTN_H, s.color("$button_tint"));
        }
        if (spread < spreads() - 1) {
            int x = Spread.R + Spread.CW - BTN_W;
            boolean hi = in(mx, my, x, BTN_Y, BTN_W, BTN_H);
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, hi ? FWD_HI : FWD, x, BTN_Y, BTN_W, BTN_H, s.color("$button_tint"));
        }
        boolean markHover = false;
        if (b != null && b.contentsIndex() >= 0) {
            markHover = in(mx, my, MARK_X, MARK_Y, MARK_W, MARK_H);
            int c = s.color(markHover ? "$mark_hover" : "$mark");
            g.fill(MARK_X, MARK_Y, MARK_X + MARK_W, MARK_Y + MARK_H - 10, c);
            for (int i = 0; i < 10; i++) {
                g.fill(MARK_X, MARK_Y + MARK_H - 10 + i, MARK_X + MARK_W / 2 - i * 17 / 10, MARK_Y + MARK_H - 9 + i, c);
                g.fill(MARK_X + MARK_W / 2 + i * 17 / 10, MARK_Y + MARK_H - 10 + i, MARK_X + MARK_W, MARK_Y + MARK_H - 9 + i, c);
            }
            g.fill(MARK_X + 4, MARK_Y + 4, MARK_X + MARK_W - 4, MARK_Y + 6, s.color("$mark_line"));
        }
        g.pose().popMatrix();
        if (markHover) g.setTooltipForNextFrame(font, Component.translatable("simplebooks.contents"), mouseX, mouseY);
        if (turnFrom >= 0 || parts.size() < 2) hits = turnFrom >= 0 ? List.of() : s.hits;
        else {
            hits = new java.util.ArrayList<>();
            for (Spread p : parts) hits.addAll(p.hits);
        }
        for (Spread.Hit h : hits) {
            if (!h.contains(mx, my)) continue;
            if (h.target() instanceof Spread.Jump j) {
                g.setTooltipForNextFrame(font, j.label(), mouseX, mouseY);
                g.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
                break;
            }
            if (h.target() instanceof ItemStack st) g.setTooltipForNextFrame(font, st, mouseX, mouseY);
            else if (h.target() instanceof Component c) g.setTooltipForNextFrame(font, c, mouseX, mouseY);
            else if (h.target() instanceof Identifier) g.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
            break;
        }
    }

    /**
     * Page turn: the old and new spreads each keep one still half, and the turning sheet folds
     * towards the spine (old page) and then opens out on the other side (new page).
     */
    private void turning(GuiGraphicsExtractor g, Book b, float t) {
        boolean fwd = spread > turnFrom;
        List<Spread> from = Spread.sheet(g, font, b, turnFrom);
        List<Spread> to = Spread.sheet(g, font, b, spread);
        half(g, fwd ? from : to, true, 1);
        half(g, fwd ? to : from, false, 1);
        float e = t * t * (3 - 2 * t), k = (float) Math.cos(Math.PI * e);
        if (k > 0) half(g, from, !fwd, k);
        else half(g, to, fwd, -k);
    }

    private void half(GuiGraphicsExtractor g, List<Spread> sheet, boolean left, float k) {
        if (k < 0.02f) return;
        int x0 = left ? Spread.PX0 : Spread.SPINE, x1 = left ? Spread.SPINE : Spread.PX1;
        g.pose().pushMatrix();
        g.pose().translate(Spread.SPINE, 0);
        g.pose().scale(k, 1);
        g.pose().translate(-Spread.SPINE, 0);
        g.enableScissor(x0, Spread.PT, x1, Spread.PBOT);
        sheet.getFirst().paperHalf(left);
        for (Spread s : sheet) s.content();
        if (k < 1) g.fill(x0, Spread.PT, x1, Spread.PBOT, (int) ((1 - k) * 0.35f * 255) << 24);
        g.disableScissor();
        g.pose().popMatrix();
    }

    private Book emptyBook() {
        return new Book(bookId, new com.google.gson.JsonObject(), java.util.Map.of(), List.of(), List.of(), java.util.Set.of());
    }

    private static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        float mx = (float) ((e.x() - ox) / scale), my = (float) ((e.y() - oy) / scale);
        if (e.button() == 0) {
            if (spread > 0 && in(mx, my, Spread.L, BTN_Y, BTN_W, BTN_H)) return turn(spread - 1, false);
            if (spread < spreads() - 1 && in(mx, my, Spread.R + Spread.CW - BTN_W, BTN_Y, BTN_W, BTN_H)) return turn(spread + 1, false);
            Book bk = book();
            if (bk != null && bk.contentsIndex() >= 0 && in(mx, my, MARK_X, MARK_Y, MARK_W, MARK_H))
                return spread == bk.contentsIndex() || turn(bk.contentsIndex(), true);
            for (Spread.Hit h : hits) {
                if (h.contains(mx, my) && h.target() instanceof Spread.Jump j) return j.spread() == spread || turn(j.spread(), true);
                if (h.contains(mx, my) && h.target() instanceof Identifier target) {
                    Book b = book();
                    int idx = b == null ? -1 : b.indexOf(target);
                    if (idx >= 0) return turn(idx, true);
                    SimpleBooks.LOG.warn("Link to missing page {}", target);
                }
            }
        }
        if (e.button() == 1 && !history.isEmpty()) return turn(history.pop(), false);
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if ((k == GLFW.GLFW_KEY_RIGHT || k == GLFW.GLFW_KEY_PAGE_DOWN) && spread < spreads() - 1) return turn(spread + 1, false);
        if ((k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_PAGE_UP) && spread > 0) return turn(spread - 1, false);
        if (k == GLFW.GLFW_KEY_BACKSPACE && !history.isEmpty()) return turn(history.pop(), false);
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (sy < 0 && spread < spreads() - 1) return turn(spread + 1, false);
        if (sy > 0 && spread > 0) return turn(spread - 1, false);
        return false;
    }

    private boolean turn(int to, boolean remember) {
        if (remember) history.push(spread);
        Book b = book();
        if (to != spread && b != null && (!b.json().has("page_turn") || b.json().get("page_turn").getAsBoolean())) {
            turnFrom = spread;
            turnStart = System.nanoTime();
        }
        spread = to;
        if (stand != null) SimpleBooks.toServer.accept(new com.workshopcraft.simplebooks.PageTurn(-1, stand, spread));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        return true;
    }

    @Override
    public void removed() {
        if (hand >= 0) SimpleBooks.toServer.accept(new com.workshopcraft.simplebooks.PageTurn(hand, net.minecraft.core.BlockPos.ZERO, spread));
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
