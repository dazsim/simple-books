package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.workshopcraft.simplebooks.quest.QuestClientStore;
import com.workshopcraft.simplebooks.quest.QuestDefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A small parchment toast for a finished or unlocked quest, or rewards waiting at login. */
final class QuestToast implements Toast {
    private static final long SHOW_MS = 5000;
    private final Component head, line;
    private final ItemStack icon;
    private final boolean secret;
    //? if >=26.1 {
    private Visibility wanted = Visibility.SHOW;
    //?}

    private QuestToast(Component head, Component line, ItemStack icon, boolean secret) {
        this.head = head;
        this.line = line;
        this.icon = icon;
        this.secret = secret;
    }

    static void show(JsonObject o) {
        String kind = o.has("kind") ? o.get("kind").getAsString() : "";
        String quest = o.has("quest") ? o.get("quest").getAsString() : "";
        QuestDefs.Quest q = null;
        for (QuestDefs.Book b : QuestClientStore.defs().books().values()) if (b.quests().containsKey(quest)) q = b.quests().get(quest);
        Component title = q == null ? Component.literal(quest) : Texts.of(q.title());
        String ic = q != null && !q.icon().isEmpty() ? q.icon() : "minecraft:writable_book";
        Component head;
        Component line = title;
        switch (kind) {
            case "complete" -> head = Component.translatable("simplebooks.toast.complete");
            case "unlock" -> head = Component.translatable("simplebooks.toast.unlock");
            case "secret" -> head = Component.translatable("simplebooks.toast.secret");
            case "again" -> head = Component.translatable("simplebooks.toast.again");
            case "tier" -> {
                head = Component.translatable("simplebooks.toast.tier");
                int n = o.has("count") ? o.get("count").getAsInt() : 0;
                if (q != null && n >= 1 && n <= q.tiers().size()) {
                    QuestDefs.Tier t = q.tiers().get(n - 1);
                    Component tn = t.title() != null ? Texts.of(t.title()) : Component.translatable("simplebooks.quest.tier_n", n);
                    line = title.copy().append(": ").append(tn);
                }
            }
            case "rewards" -> {
                head = Component.translatable("simplebooks.toast.rewards");
                int n = o.has("count") ? o.get("count").getAsInt() : 0;
                line = Component.translatable(n == 1 ? "simplebooks.toast.rewards_one" : "simplebooks.toast.rewards_many", n);
                ic = "minecraft:chest";
            }
            default -> {
                return;
            }
        }
        ItemStack st = ItemRef.parse(new JsonPrimitive(ic)).stack();
        if (st.isEmpty()) st = ItemRef.parse(new JsonPrimitive("minecraft:writable_book")).stack();
        QuestToast t = new QuestToast(head, line, st, kind.equals("secret"));
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        /*mc.gui.toastManager().addToast(t);
        *///?} else if >=26.1 {
        mc.getToastManager().addToast(t);
        //?} else {
        /*mc.getToasts().addToast(t);
        *///?}
    }

    private void paint(Gfx g, Font font) {
        int w = width(), h = height();
        g.fill(0, 0, w, h, secret ? 0xFF3E2456 : 0xFF5C4028);
        g.fill(1, 1, w - 1, h - 1, secret ? 0xFFC8A040 : 0xFFAA8C60);
        g.fill(2, 2, w - 2, h - 2, secret ? 0xFFEDE2F0 : 0xFFF1E4C2);
        g.fill(4, h - 4, w - 4, h - 3, secret ? 0xFFD8C8E0 : 0xFFDECEA8);
        g.item(icon, 8, 8);
        g.text(font, head, 30, 7, secret ? 0xFF5A2A86 : 0xFF802C16, false);
        String s = line.getString();
        int max = w - 36;
        if (font.width(s) > max) {
            while (s.length() > 1 && font.width(s + "...") > max) s = s.substring(0, s.length() - 1);
            s += "...";
        }
        g.text(font, s, 30, 18, 0xFF3F2A14, false);
    }

    //? if >=26.1 {
    @Override
    public Visibility getWantedVisibility() {
        return wanted;
    }

    @Override
    public void update(net.minecraft.client.gui.components.toasts.ToastManager manager, long fullyVisibleForMs) {
        wanted = fullyVisibleForMs >= SHOW_MS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        paint(new Gfx(graphics), font);
    }
    //?} else {
    /*@Override
    public Visibility render(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.components.toasts.ToastComponent toasts, long ms) {
        paint(new Gfx(graphics), toasts.getMinecraft().font);
        return ms >= SHOW_MS ? Visibility.HIDE : Visibility.SHOW;
    }
    *///?}
}
