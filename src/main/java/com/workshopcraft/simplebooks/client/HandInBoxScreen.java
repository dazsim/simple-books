package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.quest.QuestActionPacket;
import com.workshopcraft.simplebooks.quest.QuestDefs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Picks what a hand-in box fills: any quest in book order, or one quest. */
public class HandInBoxScreen extends Screen {
    private static final int W = 240, PAD = 8, HEAD = 34;
    private static final int PAGE = 0xFFF4EBD5, EDGE = 0xFF7A5A33, INK = 0xFF3F2A14, SOFT = 0xFF8A7556,
            PICKED = 0x50C8A060, HOVER = 0x20000000, BAR = 0xFFD8C8A8, FILL = 0xFF6A9A3A;

    private final String pos, team;
    private String target;
    private final List<Row> rows = new ArrayList<>();
    private int scroll, x0, y0, h;

    record Line(ItemStack icon, Component label, long have, long need) {}

    record Row(String quest, ItemStack icon, Component title, List<Line> lines) {
        int height() {
            return 20 + lines.size() * 12;
        }
    }

    public HandInBoxScreen(JsonObject o) {
        super(Component.translatable("block.simplebooks.hand_in_box"));
        JsonArray p = o.getAsJsonArray("pos");
        pos = p.get(0).getAsInt() + " " + p.get(1).getAsInt() + " " + p.get(2).getAsInt();
        team = o.get("team").getAsString();
        target = o.get("target").getAsString();
        rows.add(new Row("", new ItemStack(SimpleBooks.item(SimpleBooks.id("hand_in_box"))), Component.translatable("simplebooks.box.any"),
                List.of(new Line(ItemStack.EMPTY, Component.translatable("simplebooks.box.any_hint"), -1, -1))));
        for (JsonElement e : o.getAsJsonArray("quests")) {
            JsonObject jq = e.getAsJsonObject();
            String id = jq.get("id").getAsString();
            QuestDefs.Quest q = QuestSlots.def(id);
            ItemStack icon = q != null && !q.icon().isEmpty() ? ItemRef.parse(new com.google.gson.JsonPrimitive(q.icon())).stack() : ItemStack.EMPTY;
            Component title = q != null ? Texts.of(q.title()) : Component.literal(id);
            List<Line> lines = new ArrayList<>();
            for (JsonElement te : jq.getAsJsonArray("tasks")) {
                JsonObject jt = te.getAsJsonObject();
                QuestDefs.Task t = null;
                if (q != null) for (QuestDefs.Task x : q.tasks()) if (x.id().equals(jt.get("id").getAsString())) t = x;
                long need = jt.get("need").getAsLong();
                ItemRef ref = t == null ? null : QuestSlots.taskIcon(t);
                Component label = t == null ? Component.literal(jt.get("id").getAsString()) : ref != null ? ref.name() : QuestSlots.taskLabel(t, need);
                lines.add(new Line(ref == null ? ItemStack.EMPTY : ref.stack(), label, jt.get("have").getAsLong(), need));
            }
            rows.add(new Row(id, icon, title, lines));
        }
    }

    public static void open(JsonObject o) {
        HandInBoxScreen screen = new HandInBoxScreen(o);
        var mc = net.minecraft.client.Minecraft.getInstance();
        //? if >=26.2 {
        /*mc.schedule(() -> mc.gui.setScreen(screen));
        *///?} else if >=26.1 {
        mc.schedule(() -> mc.setScreen(screen));
        //?} else {
        /*mc.tell(() -> mc.setScreen(screen));
        *///?}
    }

    private int content() {
        int n = 0;
        for (Row r : rows) n += r.height();
        return n;
    }

    private void layout() {
        h = Math.min(HEAD + content() + PAD, height - 20);
        x0 = (width - W) / 2;
        y0 = (height - h) / 2;
        scroll = Math.max(0, Math.min(scroll, HEAD + content() + PAD - h));
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

    private void draw(Gfx g, int mx, int my) {
        layout();
        Font f = font;
        g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + h + 2, EDGE);
        g.fill(x0, y0, x0 + W, y0 + h, PAGE);
        Component head = title;
        g.text(f, head, x0 + (W - f.width(head)) / 2, y0 + 7, INK, false);
        Component sub = team.isEmpty() ? Component.translatable("simplebooks.box.no_team") : Component.translatable("simplebooks.box.team", team);
        g.text(f, sub, x0 + (W - f.width(sub)) / 2, y0 + 19, SOFT, false);
        g.fill(x0 + PAD, y0 + HEAD - 3, x0 + W - PAD, y0 + HEAD - 2, EDGE);
        g.enableScissor(x0, y0 + HEAD, x0 + W, y0 + h - 2);
        int y = y0 + HEAD - scroll;
        for (Row r : rows) {
            int rh = r.height();
            boolean picked = r.quest().equals(target), over = mx >= x0 && mx < x0 + W && my >= y && my < y + rh && my >= y0 + HEAD && my < y0 + h;
            if (picked) g.fill(x0 + 3, y, x0 + W - 3, y + rh - 2, PICKED);
            else if (over) g.fill(x0 + 3, y, x0 + W - 3, y + rh - 2, HOVER);
            if (over) g.pointer();
            radio(g, x0 + PAD, y + 5, picked);
            if (!r.icon().isEmpty()) g.item(r.icon(), x0 + PAD + 12, y + 1);
            g.text(f, f.plainSubstrByWidth(r.title().getString(), W - PAD * 2 - 32), x0 + PAD + 32, y + 5, INK, false);
            int ly = y + 19;
            for (Line l : r.lines()) {
                int lx = x0 + PAD + 32;
                if (!l.icon().isEmpty()) {
                    g.push();
                    g.translate(lx, ly - 1);
                    g.scale(0.625F, 0.625F);
                    g.item(l.icon(), 0, 0);
                    g.pop();
                    lx += 12;
                }
                if (l.need() < 0) {
                    g.text(f, l.label(), lx, ly, SOFT, false);
                } else {
                    String num = HandInBoxRenderer.amount(l.have()) + " / " + HandInBoxRenderer.amount(l.need());
                    int bx = x0 + W - PAD - 50;
                    g.text(f, f.plainSubstrByWidth(l.label().getString(), bx - lx - f.width(num) - 8), lx, ly, SOFT, false);
                    g.text(f, num, bx - 4 - f.width(num), ly, INK, false);
                    g.fill(bx, ly + 2, bx + 50, ly + 6, BAR);
                    g.fill(bx, ly + 2, bx + (int) (50 * Math.min(1, l.have() / (double) Math.max(1, l.need()))), ly + 6, FILL);
                }
                ly += 12;
            }
            y += rh;
        }
        g.disableScissor();
    }

    private static void radio(Gfx g, int x, int y, boolean on) {
        g.fill(x, y, x + 8, y + 8, EDGE);
        g.fill(x + 1, y + 1, x + 7, y + 7, PAGE);
        if (on) g.fill(x + 2, y + 2, x + 6, y + 6, INK);
    }

    //? if >=26.1 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) {
        return click(e.x(), e.y(), e.button()) || super.mouseClicked(e, doubleClick);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double x, double y, int button) {
        return click(x, y, button) || super.mouseClicked(x, y, button);
    }
    *///?}

    private boolean click(double mx, double my, int button) {
        if (button != 0 || mx < x0 || mx >= x0 + W || my < y0 + HEAD || my >= y0 + h) return false;
        int y = y0 + HEAD - scroll;
        for (Row r : rows) {
            if (my >= y && my < y + r.height()) {
                if (!r.quest().equals(target)) {
                    target = r.quest();
                    SimpleBooks.toServerAction.accept(new QuestActionPacket("box_target", target, "", 0, pos));
                }
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            y += r.height();
        }
        return false;
    }

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        scroll -= (int) (sy * 12);
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double x, double y, double sy) {
        scroll -= (int) (sy * 12);
        return true;
    }
    *///?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
