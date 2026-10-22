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
    private int spread;
    private final Deque<Integer> history = new ArrayDeque<>();
    private List<Spread.Hit> hits = List.of();
    private float scale = 1, ox, oy;

    public BookScreen(Identifier bookId, int spread) {
        super(Component.literal(bookId.toString()));
        this.bookId = bookId;
        this.spread = spread;
    }

    public static void open(Identifier book, @Nullable Identifier page) {
        int idx = 0;
        Book b = BookRegistry.get(book);
        if (b != null && page != null) idx = Math.max(0, b.indexOf(page));
        BookScreen screen = new BookScreen(book, idx);
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.schedule(() -> mc.setScreen(screen));
    }

    private @Nullable Book book() {
        return BookRegistry.get(bookId);
    }

    private int spreads() {
        Book b = book();
        return b == null ? 1 : Math.max(1, b.pages().size());
    }

    private void layout() {
        double gui = minecraft.getWindow().getGuiScale();
        double fit = Math.min(width * gui / Spread.W, height * gui / Spread.H);
        double real = fit >= 1 ? Math.floor(fit * 2) / 2 : fit;
        scale = (float) (real / gui);
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
        if (b == null) {
            s = new Spread(g, font, emptyBook(), null, 0);
            s.frame();
            s.errorPage("assets/" + bookId.getNamespace() + "/simplebooks/books/" + bookId.getPath() + ".json",
                    List.of("unknown book " + bookId));
        } else {
            Book.Page page = b.pages().isEmpty() ? null : b.pages().get(spread);
            s = new Spread(g, font, b, page, spread);
            s.frame();
            if (!b.errors().isEmpty()) s.errorPage("book " + bookId, b.errors());
            else if (page == null) s.errorPage("book " + bookId, List.of("the book has no pages"));
            else s.content();
        }
        if (spread > 0) {
            boolean hi = in(mx, my, Spread.L, BTN_Y, BTN_W, BTN_H);
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, hi ? BACK_HI : BACK, Spread.L, BTN_Y, BTN_W, BTN_H);
        }
        if (spread < spreads() - 1) {
            int x = Spread.R + Spread.CW - BTN_W;
            boolean hi = in(mx, my, x, BTN_Y, BTN_W, BTN_H);
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, hi ? FWD_HI : FWD, x, BTN_Y, BTN_W, BTN_H);
        }
        boolean markHover = false;
        if (b != null && b.contentsIndex() >= 0) {
            markHover = in(mx, my, MARK_X, MARK_Y, MARK_W, MARK_H);
            int c = markHover ? 0xFFA83A1E : 0xFF802C16;
            g.fill(MARK_X, MARK_Y, MARK_X + MARK_W, MARK_Y + MARK_H - 10, c);
            for (int i = 0; i < 10; i++) {
                g.fill(MARK_X, MARK_Y + MARK_H - 10 + i, MARK_X + MARK_W / 2 - i * 17 / 10, MARK_Y + MARK_H - 9 + i, c);
                g.fill(MARK_X + MARK_W / 2 + i * 17 / 10, MARK_Y + MARK_H - 10 + i, MARK_X + MARK_W, MARK_Y + MARK_H - 9 + i, c);
            }
            g.fill(MARK_X + 4, MARK_Y + 4, MARK_X + MARK_W - 4, MARK_Y + 6, 0xFFE0B080);
        }
        g.pose().popMatrix();
        if (markHover) g.setTooltipForNextFrame(font, Component.translatable("simplebooks.contents"), mouseX, mouseY);
        hits = s.hits;
        for (Spread.Hit h : hits) {
            if (!h.contains(mx, my)) continue;
            if (h.target() instanceof ItemStack st) g.setTooltipForNextFrame(font, st, mouseX, mouseY);
            else if (h.target() instanceof Component c) g.setTooltipForNextFrame(font, c, mouseX, mouseY);
            else if (h.target() instanceof Identifier) g.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
            break;
        }
    }

    private Book emptyBook() {
        return new Book(bookId, new com.google.gson.JsonObject(), java.util.Map.of(), List.of(), List.of());
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
        spread = to;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
