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

/** Simple property form for a quest: title, subtitle, text, icon. Tasks/rewards raw JSON later. */
public class QuestEditScreen extends Screen {
    private final String questId;
    private final @Nullable Screen parent;
    private EditBox title, subtitle, text, icon, tasksJson, rewardsJson;
    private String error = "";

    public QuestEditScreen(String questId, @Nullable Screen parent) {
        super(Component.literal("Edit " + QuestEdit.shortName(questId)));
        this.questId = questId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        QuestDefs.Quest q = find();
        JsonObject o = q == null ? new JsonObject() : q.json();
        int cx = width / 2, y = 30;
        title = field(cx - 150, y, "Title", str(o, "title")); y += 28;
        subtitle = field(cx - 150, y, "Subtitle", str(o, "subtitle")); y += 28;
        icon = field(cx - 150, y, "Icon", str(o, "icon")); y += 28;
        text = field(cx - 150, y, "Text", str(o, "text")); text.setWidth(300); y += 28;
        tasksJson = field(cx - 150, y, "Tasks JSON", o.has("tasks") ? o.get("tasks").toString() : "[]");
        tasksJson.setWidth(300); y += 28;
        rewardsJson = field(cx - 150, y, "Rewards JSON", o.has("rewards") ? o.get("rewards").toString() : "[]");
        rewardsJson.setWidth(300); y += 36;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(cx - 100, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.setScreen(parent))
                .bounds(cx + 10, y, 90, 20).build());
    }

    private EditBox field(int x, int y, String label, String value) {
        EditBox box = new EditBox(font, x, y, 300, 20, Component.literal(label));
        box.setValue(value == null ? "" : value);
        box.setMaxLength(10000);
        box.setHint(Component.literal(label));
        addRenderableWidget(box);
        return box;
    }

    private void save() {
        QuestDefs.Quest q = find();
        JsonObject o = q == null ? new JsonObject() : q.json().deepCopy();
        put(o, "title", title.getValue());
        put(o, "subtitle", subtitle.getValue());
        put(o, "icon", icon.getValue());
        put(o, "text", text.getValue());
        try {
            o.add("tasks", com.google.gson.JsonParser.parseString(tasksJson.getValue().isBlank() ? "[]" : tasksJson.getValue()));
            o.add("rewards", com.google.gson.JsonParser.parseString(rewardsJson.getValue().isBlank() ? "[]" : rewardsJson.getValue()));
        } catch (Exception e) {
            error = "Bad tasks/rewards JSON: " + e.getMessage();
            return;
        }
        if (o.has("chapter")) {
            String ch = o.get("chapter").getAsString();
            if (ch.contains("/")) o.addProperty("chapter", ch.substring(ch.indexOf('/') + 1));
        }
        SimpleBooks.toServerEdit.accept(new QuestEditPacket("put_quest", questId, o.toString()));
        QuestEdit.status = "Saved properties of " + QuestEdit.shortName(questId) + ".";
        minecraft.setScreen(parent);
    }

    private static void put(JsonObject o, String key, String value) {
        if (value == null || value.isBlank()) o.remove(key);
        else o.addProperty(key, value);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private QuestDefs.@Nullable Quest find() {
        for (QuestDefs.Book b : QuestClientStore.defs().books().values()) {
            QuestDefs.Quest q = b.quests().get(questId);
            if (q != null) return q;
        }
        return null;
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
        super.extractRenderState(gg, mouseX, mouseY, a);
        gg.text(font, title.getMessage(), width / 2 - 150, 18, 0xFFFFFF, false);
        if (!error.isEmpty()) gg.text(font, Component.literal(error), 20, height - 30, 0xFF5555, false);
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics gg, int mouseX, int mouseY, float a) {
        renderBackground(gg, mouseX, mouseY, a);
        super.render(gg, mouseX, mouseY, a);
        if (!error.isEmpty()) gg.drawString(font, error, 20, height - 30, 0xFF5555);
    }
    *///?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
