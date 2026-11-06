package com.workshopcraft.simplebooks.client;

import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class BookScreen extends Screen {
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
    private @Nullable Book shown;

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
        //? if >=26.2 {
        /*mc.schedule(() -> mc.gui.setScreen(screen));
        *///?} else if >=26.1 {
        mc.schedule(() -> mc.setScreen(screen));
        //?} else {
        /*mc.tell(() -> mc.setScreen(screen));
        *///?}
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

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
        super.extractRenderState(gg, mouseX, mouseY, a);
        draw(new Gfx(gg), mouseX, mouseY);
    }
    //?} else if >=1.20.2 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics gg, int mouseX, int mouseY, float a) {
        super.render(gg, mouseX, mouseY, a);
        draw(new Gfx(gg), mouseX, mouseY);
    }
    *///?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics gg, int mouseX, int mouseY, float a) {
        renderBackground(gg);
        super.render(gg, mouseX, mouseY, a);
        draw(new Gfx(gg), mouseX, mouseY);
    }
    *///?}

    private void draw(Gfx g, int mouseX, int mouseY) {
        layout();
        Book b = book();
        if (b != shown) {
            // quest progress rebuilds the book; stay on the same page when pages come or go
            if (shown != null && b != null && b.flow() == null && shown.flow() == null) {
                Book.Page was = shown.pageAt(spread);
                int idx = was == null ? -1 : b.indexOf(was.id());
                if (idx >= 0) spread = idx;
            }
            shown = b;
        }
        spread = Math.min(spread, spreads() - 1);
        float mx = (mouseX - ox) / scale, my = (mouseY - oy) / scale;
        QuestSlots.mx = mx;
        QuestSlots.my = my;
        g.push();
        g.translate(ox, oy);
        g.scale(scale, scale);
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
            s = parts.get(0);
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
            g.pageButton(false, hi, Spread.L, BTN_Y, BTN_W, BTN_H, s.color("$button_tint"));
        }
        if (spread < spreads() - 1) {
            int x = Spread.R + Spread.CW - BTN_W;
            boolean hi = in(mx, my, x, BTN_Y, BTN_W, BTN_H);
            g.pageButton(true, hi, x, BTN_Y, BTN_W, BTN_H, s.color("$button_tint"));
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
        if (isQuestBook() && QuestEdit.canEdit()) {
            float ey = 8;
            String[][] tools = QuestEdit.on
                    ? new String[][]{{"Done", "toggle"}, {"Link", "link"}, {"Unlink", "unlink"}, {"Del", "delete"}, {"Edit", "props"}, {"Chapter", "chapter"}, {"Pool", "pool"}, {"Undo", "undo"}}
                    : new String[][]{{"Edit", "toggle"}};
            float tx = Spread.L;
            for (String[] t : tools) {
                float tw = font.width(t[0]) * 2 + 16;
                boolean hi = in(mx, my, tx, ey, tw, 24);
                s.fill(tx, ey, tx + tw, ey + 24, s.color(hi ? "$mark_hover" : "$mark"));
                s.ctext(Component.literal(t[0]), tx + tw / 2, ey + 4, 1, s.color("$page"));
                s.hits.add(new Spread.Hit(tx, ey, tx + tw, ey + 24, "toggle".equals(t[1]) ? "edit_toggle" : new QuestEdit.Tool(t[1])));
                tx += tw + 8;
            }
            if (QuestEdit.status != null && !QuestEdit.status.isEmpty())
                s.text(Component.literal(QuestEdit.status), Spread.L, ey + 28, 0.85f, s.color("$ink"));
        }
        g.pop();
        if (markHover) g.tooltip(font, Component.translatable("simplebooks.contents"), mouseX, mouseY);
        if (turnFrom >= 0 || parts.size() < 2) hits = turnFrom >= 0 ? List.of() : s.hits;
        else {
            hits = new java.util.ArrayList<>();
            for (Spread p : parts) hits.addAll(p.hits);
        }
        for (Spread.Hit h : hits) {
            if (!h.contains(mx, my)) continue;
            if (h.target() instanceof Spread.Jump j) {
                g.tooltip(font, j.label(), mouseX, mouseY);
                g.pointer();
                break;
            }
            if (h.target() instanceof ItemStack st) g.tooltip(font, st, mouseX, mouseY);
            else if (h.target() instanceof Component c) g.tooltip(font, c, mouseX, mouseY);
            else if (h.target() instanceof Identifier) g.pointer();
            else if (h.target() instanceof QuestSlots.Act a) {
                if (a.tip() != null) g.tooltip(font, a.tip(), mouseX, mouseY);
                g.pointer();
            }
            break;
        }
    }

    /**
     * Page turn: the old and new spreads each keep one still half, and the turning sheet folds
     * towards the spine (old page) and then opens out on the other side (new page).
     */
    private void turning(Gfx g, Book b, float t) {
        boolean fwd = spread > turnFrom;
        List<Spread> from = Spread.sheet(g, font, b, turnFrom);
        List<Spread> to = Spread.sheet(g, font, b, spread);
        half(g, fwd ? from : to, true, 1);
        half(g, fwd ? to : from, false, 1);
        float e = t * t * (3 - 2 * t), k = (float) Math.cos(Math.PI * e);
        if (k > 0) half(g, from, !fwd, k);
        else half(g, to, fwd, -k);
    }

    private void half(Gfx g, List<Spread> sheet, boolean left, float k) {
        if (k < 0.02f) return;
        int x0 = left ? Spread.PX0 : Spread.SPINE, x1 = left ? Spread.SPINE : Spread.PX1;
        g.push();
        g.translate(Spread.SPINE, 0);
        g.scale(k, 1);
        g.translate(-Spread.SPINE, 0);
        g.enableScissor(x0, Spread.PT, x1, Spread.PBOT);
        sheet.get(0).paperHalf(left);
        for (Spread s : sheet) s.content();
        if (k < 1) g.fill(x0, Spread.PT, x1, Spread.PBOT, (int) ((1 - k) * 0.35f * 255) << 24);
        g.disableScissor();
        g.pop();
    }

    private Book emptyBook() {
        return new Book(bookId, new com.google.gson.JsonObject(), java.util.Map.of(), List.of(), List.of(), java.util.Set.of());
    }

    private static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    //? if >=26.1 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) {
        return click(e.x(), e.y(), e.button()) || super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        return key(e.key()) || super.keyPressed(e);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double x, double y, int button) {
        return click(x, y, button) || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return key(key) || super.keyPressed(key, scan, mods);
    }
    *///?}

    private boolean click(double x, double y, int button) {
        float mx = (float) ((x - ox) / scale), my = (float) ((y - oy) / scale);
        if (button == 0) {
            if (spread > 0 && in(mx, my, Spread.L, BTN_Y, BTN_W, BTN_H)) return turn(spread - 1, false);
            if (spread < spreads() - 1 && in(mx, my, Spread.R + Spread.CW - BTN_W, BTN_Y, BTN_W, BTN_H)) return turn(spread + 1, false);
            Book bk = book();
            if (bk != null && bk.contentsIndex() >= 0 && in(mx, my, MARK_X, MARK_Y, MARK_W, MARK_H))
                return spread == bk.contentsIndex() || turn(bk.contentsIndex(), true);
            for (Spread.Hit h : hits) {
                if (!h.contains(mx, my)) continue;
                if ("edit_toggle".equals(h.target())) {
                    QuestEdit.toggle(bookId);
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                if (QuestEdit.press(h.target())) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                if (h.target() instanceof QuestSlots.Act a && QuestSlots.press(a)) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
                if (h.target() instanceof Spread.Jump j) return j.spread() == spread || turn(j.spread(), true);
                if (h.target() instanceof Identifier target) {
                    Book b = book();
                    int idx = b == null ? -1 : b.indexOf(target);
                    if (idx >= 0) return turn(idx, true);
                    SimpleBooks.LOG.warn("Link to missing page {}", target);
                }
            }
        }
        if (button == 1 && !history.isEmpty()) return turn(history.pop(), false);
        return false;
    }

    private boolean key(int k) {
        if (QuestEdit.active()) {
            QuestEdit.key(k);
            if (k == 261 || k == 76 || k == 85 || k == 69) return true; // del, L, U, E
        }
        if ((k == InputConstants.KEY_RIGHT || k == InputConstants.KEY_PAGEDOWN) && spread < spreads() - 1) return turn(spread + 1, false);
        if ((k == InputConstants.KEY_LEFT || k == InputConstants.KEY_PAGEUP) && spread > 0) return turn(spread - 1, false);
        if (k == InputConstants.KEY_BACKSPACE && !history.isEmpty() && !QuestEdit.active()) return turn(history.pop(), false);
        return false;
    }

    private boolean isQuestBook() {
        return com.workshopcraft.simplebooks.quest.QuestClientStore.defs().books().containsKey(bookId.toString());
    }

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        return scroll(x, y, sy);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double x, double y, double sy) {
        return scroll(x, y, sy);
    }
    *///?}

    //? if >=26.1 {
    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) {
        return drag(e.x(), e.y(), dx, dy) || super.mouseDragged(e, dx, dy);
    }
    //?} else {
    /*@Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return drag(x, y, dx, dy) || super.mouseDragged(x, y, button, dx, dy);
    }
    *///?}

    //? if >=26.1 {
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        return release(e.x(), e.y()) || super.mouseReleased(e);
    }
    //?} else {
    /*@Override
    public boolean mouseReleased(double x, double y, int button) {
        return release(x, y) || super.mouseReleased(x, y, button);
    }
    *///?}

    private boolean release(double x, double y) {
        if (!QuestEdit.active() || QuestEdit.dragId == null) return false;
        float mx = (float) ((x - ox) / scale), my = (float) ((y - oy) / scale);
        QuestEdit.Cell cell = null;
        for (Spread.Hit h : hits) {
            if (h.contains(mx, my) && h.target() instanceof QuestEdit.Cell c) cell = c;
            if (h.contains(mx, my) && h.target() instanceof QuestEdit.Node) {
                // dropped on a node: cancel move
                QuestEdit.dragId = null;
                return true;
            }
        }
        QuestEdit.dragEnd(cell);
        return true;
    }

    private QuestSlots.@org.jspecify.annotations.Nullable Pan panAt(double x, double y) {
        float mx = (float) ((x - ox) / scale), my = (float) ((y - oy) / scale);
        for (Spread.Hit h : hits) if (h.contains(mx, my) && h.target() instanceof QuestSlots.Pan p) return p;
        return null;
    }

    private boolean drag(double x, double y, double dx, double dy) {
        if (QuestEdit.active() && QuestEdit.dragId != null) return true;
        QuestSlots.Pan p = panAt(x, y);
        return p != null && QuestSlots.pan(p, -dx / scale, -dy / scale);
    }

    private boolean scroll(double x, double y, double sy) {
        QuestSlots.Pan p = panAt(x, y);
        if (p != null && QuestSlots.pan(p, p.maxY() > 0 ? 0 : -sy * QuestSlots.CELL_W / 2, p.maxY() > 0 ? -sy * QuestSlots.CELL_H / 2 : 0)) return true;
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
