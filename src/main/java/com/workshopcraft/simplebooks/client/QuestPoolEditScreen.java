package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.quest.QuestClientStore;
import com.workshopcraft.simplebooks.quest.QuestDefs;
import com.workshopcraft.simplebooks.quest.QuestEditPacket;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Bounty pool editor: offers/refresh/reroll fields and a quest list with weights. */
public class QuestPoolEditScreen extends Screen {
    private final String poolId;
    private final @Nullable Screen parent;
    private final JsonObject draft;
    private EditBox offers, cooldown, rerollAllowed, rerollCost;
    private final List<EditBox> questIds = new ArrayList<>();
    private final List<EditBox> weights = new ArrayList<>();
    private String error = "";
    private int scroll;
    private final List<int[]> labels = new ArrayList<>();
    private final List<String> labelNames = new ArrayList<>();

    public QuestPoolEditScreen(String poolId, @Nullable Screen parent) {
        super(Component.literal("Pool " + QuestEdit.shortName(poolId)));
        this.poolId = poolId;
        this.parent = parent;
        QuestDefs.Pool p = find();
        draft = p == null ? new JsonObject() : p.json().deepCopy();
    }

    @Override
    protected void init() {
        clearWidgets();
        questIds.clear();
        weights.clear();
        labels.clear();
        labelNames.clear();
        int x = 20, y = 16;
        mark("Offers", x, y);
        mark("Cooldown", x + 80, y);
        mark("Reroll", x + 180, y);
        mark("Reroll cost JSON", x + 260, y);
        y += 10;
        offers = field(x, y, 60, draft.has("offers") ? draft.get("offers").getAsString() : "3");
        String cd = "";
        if (draft.has("refresh") && draft.get("refresh").isJsonObject() && draft.getAsJsonObject("refresh").has("cooldown"))
            cd = draft.getAsJsonObject("refresh").get("cooldown").getAsString();
        cooldown = field(x + 80, y, 80, cd);
        boolean allowed = true;
        String cost = "[]";
        if (draft.has("reroll") && draft.get("reroll").isJsonObject()) {
            JsonObject r = draft.getAsJsonObject("reroll");
            if (r.has("allowed")) allowed = r.get("allowed").getAsBoolean();
            if (r.has("cost")) cost = r.get("cost").toString();
        }
        rerollAllowed = field(x + 180, y, 60, allowed ? "true" : "false");
        rerollCost = field(x + 260, y, 220, cost);
        y += 28;
        addRenderableWidget(Button.builder(Component.literal("+ Quest"), b -> {
            commitHeader();
            JsonArray qs = quests();
            JsonObject e = new JsonObject();
            e.addProperty("quest", "");
            e.addProperty("weight", 1);
            qs.add(e);
            rebuild();
        }).bounds(x, y, 80, 20).build());
        y += 24;
        JsonArray qs = quests();
        int start = scroll;
        int end = Math.min(qs.size(), start + Math.max(1, (height - y - 50) / 34));
        mark("Quest id", x, y);
        mark("Weight", x + 230, y);
        y += 10;
        for (int i = start; i < end; i++) {
            JsonObject e = qs.get(i).getAsJsonObject();
            EditBox q = field(x, y, 220, e.has("quest") ? e.get("quest").getAsString() : "");
            EditBox w = field(x + 230, y, 50, e.has("weight") ? e.get("weight").getAsString() : "1");
            questIds.add(q);
            weights.add(w);
            int idx = i;
            addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                commitHeader();
                commitRows();
                quests().remove(idx);
                if (scroll > 0 && scroll >= quests().size()) scroll = Math.max(0, quests().size() - 1);
                rebuild();
            }).bounds(x + 290, y, 24, 20).build());
            y += 24;
        }
        if (qs.size() > end) {
            addRenderableWidget(Button.builder(Component.literal("More"), b -> { scroll++; rebuild(); }).bounds(x, y, 60, 20).build());
        }
        if (scroll > 0) {
            addRenderableWidget(Button.builder(Component.literal("Up"), b -> { scroll = Math.max(0, scroll - 1); rebuild(); }).bounds(x + 70, y, 40, 20).build());
        }
        int by = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(width / 2 - 100, by, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.setScreen(parent)).bounds(width / 2 + 10, by, 90, 20).build());
    }

    private void mark(String name, int x, int y) {
        labelNames.add(name);
        labels.add(new int[]{x, y});
    }

    private JsonArray quests() {
        if (!draft.has("quests") || !draft.get("quests").isJsonArray()) draft.add("quests", new JsonArray());
        return draft.getAsJsonArray("quests");
    }

    private EditBox field(int x, int y, int w, String v) {
        EditBox b = new EditBox(font, x, y, w, 20, Component.literal(""));
        b.setMaxLength(10000);
        b.setValue(v == null ? "" : v);
        addRenderableWidget(b);
        return b;
    }

    private void commitHeader() {
        draft.addProperty("offers", QuestEditForms.parseInt(offers.getValue(), 3));
        JsonObject refresh = draft.has("refresh") && draft.get("refresh").isJsonObject()
                ? draft.getAsJsonObject("refresh") : new JsonObject();
        if (!cooldown.getValue().isBlank()) refresh.addProperty("cooldown", cooldown.getValue().trim());
        else refresh.remove("cooldown");
        draft.add("refresh", refresh);
        JsonObject reroll = draft.has("reroll") && draft.get("reroll").isJsonObject()
                ? draft.getAsJsonObject("reroll") : new JsonObject();
        reroll.addProperty("allowed", rerollAllowed.getValue().trim().equalsIgnoreCase("true"));
        try {
            if (!rerollCost.getValue().isBlank()) reroll.add("cost", com.google.gson.JsonParser.parseString(rerollCost.getValue()));
            else reroll.remove("cost");
        } catch (Exception ignored) {}
        draft.add("reroll", reroll);
    }

    private void commitRows() {
        JsonArray qs = quests();
        for (int i = 0; i < questIds.size(); i++) {
            int idx = scroll + i;
            if (idx >= qs.size()) break;
            JsonObject e = qs.get(idx).getAsJsonObject();
            e.addProperty("quest", questIds.get(i).getValue().trim());
            e.addProperty("weight", QuestEditForms.parseInt(weights.get(i).getValue(), 1));
        }
    }

    private void rebuild() {
        init();
    }

    private void save() {
        error = "";
        commitHeader();
        commitRows();
        for (int i = 0; i < quests().size(); i++) {
            JsonObject e = quests().get(i).getAsJsonObject();
            if (!e.has("quest") || e.get("quest").getAsString().isBlank()) {
                error = "Quest " + (i + 1) + " needs an id.";
                return;
            }
        }
        SimpleBooks.toServerEdit.accept(new QuestEditPacket("put_pool", poolId, draft.toString()));
        QuestEdit.status = "Saved pool " + QuestEdit.shortName(poolId) + ".";
        minecraft.setScreen(parent);
    }

    private QuestDefs.@Nullable Pool find() {
        for (QuestDefs.Book b : QuestClientStore.defs().books().values()) {
            QuestDefs.Pool p = b.pools().get(poolId);
            if (p != null) return p;
        }
        return null;
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
        super.extractRenderState(gg, mouseX, mouseY, a);
        for (int i = 0; i < labels.size(); i++) {
            int[] p = labels.get(i);
            gg.text(font, Component.literal(labelNames.get(i)), p[0], p[1], 0xA0A0A0, false);
        }
        if (!error.isEmpty()) gg.text(font, Component.literal(error), 20, height - 44, 0xFF5555, false);
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics gg, int mouseX, int mouseY, float a) {
        renderBackground(gg, mouseX, mouseY, a);
        super.render(gg, mouseX, mouseY, a);
        for (int i = 0; i < labels.size(); i++) {
            int[] p = labels.get(i);
            gg.drawString(font, labelNames.get(i), p[0], p[1], 0xA0A0A0);
        }
        if (!error.isEmpty()) gg.drawString(font, error, 20, height - 44, 0xFF5555);
    }
    *///?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
