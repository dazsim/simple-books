package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.quest.QuestClientStore;
import com.workshopcraft.simplebooks.quest.QuestDefs;
import com.workshopcraft.simplebooks.quest.QuestEditPacket;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Edit a bounty pool's quests list (and the rest of the pool JSON). */
public class QuestPoolEditScreen extends Screen {
    private final String poolId;
    private final @Nullable Screen parent;
    private EditBox body;
    private String error = "";

    public QuestPoolEditScreen(String poolId, @Nullable Screen parent) {
        super(Component.literal("Pool " + QuestEdit.shortName(poolId)));
        this.poolId = poolId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        QuestDefs.Pool p = find();
        String raw = p == null ? "{\n  \"offers\": 3,\n  \"quests\": []\n}" : pretty(p.json());
        body = new EditBox(font, 20, 40, width - 40, 20, Component.literal("Pool JSON"));
        body.setValue(raw);
        body.setMaxLength(100000);
        addRenderableWidget(body);
        // taller multi-line feel: one long edit box is limited; use full width
        body.setWidth(width - 40);
        int y = height - 40;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(width / 2 - 100, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.setScreen(parent)).bounds(width / 2 + 10, y, 90, 20).build());
    }

    private static String pretty(JsonObject o) {
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o);
    }

    private void save() {
        try {
            JsonObject o = JsonParser.parseString(body.getValue()).getAsJsonObject();
            SimpleBooks.toServerEdit.accept(new QuestEditPacket("put_pool", poolId, o.toString()));
            QuestEdit.status = "Saved pool " + QuestEdit.shortName(poolId) + ".";
            minecraft.setScreen(parent);
        } catch (Exception e) {
            error = "Bad JSON: " + e.getMessage();
        }
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
        gg.text(font, Component.literal("Pool JSON (quests list, offers, refresh, reroll)"), 20, 20, 0xFFFFFF, false);
        if (!error.isEmpty()) gg.text(font, Component.literal(error), 20, height - 60, 0xFF5555, false);
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics gg, int mouseX, int mouseY, float a) {
        renderBackground(gg, mouseX, mouseY, a);
        super.render(gg, mouseX, mouseY, a);
        if (!error.isEmpty()) gg.drawString(font, error, 20, height - 60, 0xFF5555);
    }
    *///?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
