package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
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

/** Chapter settings: title, lines, background, decorations JSON. */
public class QuestChapterEditScreen extends Screen {
    private final String chapterId;
    private final @Nullable Screen parent;
    private EditBox title, lines, background, decorations;
    private String error = "";

    public QuestChapterEditScreen(String chapterId, @Nullable Screen parent) {
        super(Component.literal("Chapter " + QuestEdit.shortName(chapterId)));
        this.chapterId = chapterId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        QuestDefs.Chapter c = find();
        JsonObject o = c == null ? new JsonObject() : c.json();
        int cx = width / 2, y = 40;
        title = box(cx - 150, y, str(o, "title")); y += 28;
        lines = box(cx - 150, y, str(o, "lines").isEmpty() ? "elbow" : str(o, "lines")); y += 28;
        background = box(cx - 150, y, o.has("background") ? o.get("background").toString() : ""); y += 28;
        decorations = box(cx - 150, y, o.has("decorations") ? o.get("decorations").toString() : "[]");
        decorations.setWidth(300); y += 36;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(cx - 100, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.setScreen(parent)).bounds(cx + 10, y, 90, 20).build());
    }

    private EditBox box(int x, int y, String v) {
        EditBox b = new EditBox(font, x, y, 300, 20, Component.literal(""));
        b.setValue(v == null ? "" : v);
        b.setMaxLength(20000);
        addRenderableWidget(b);
        return b;
    }

    private void save() {
        QuestDefs.Chapter c = find();
        JsonObject o = c == null ? new JsonObject() : c.json().deepCopy();
        if (!title.getValue().isBlank()) o.addProperty("title", title.getValue());
        String ln = lines.getValue().trim();
        if (ln.equals("elbow") || ln.isEmpty()) o.remove("lines");
        else o.addProperty("lines", ln);
        try {
            if (background.getValue().isBlank()) o.remove("background");
            else o.add("background", JsonParser.parseString(background.getValue().startsWith("{") || background.getValue().startsWith("\"")
                    ? background.getValue() : "\"" + background.getValue() + "\""));
            if (decorations.getValue().isBlank() || decorations.getValue().equals("[]")) o.remove("decorations");
            else o.add("decorations", JsonParser.parseString(decorations.getValue()));
        } catch (Exception e) {
            error = "Bad JSON: " + e.getMessage();
            return;
        }
        SimpleBooks.toServerEdit.accept(new QuestEditPacket("put_chapter", chapterId, o.toString()));
        QuestEdit.status = "Saved chapter " + QuestEdit.shortName(chapterId) + ".";
        minecraft.setScreen(parent);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private QuestDefs.@Nullable Chapter find() {
        for (QuestDefs.Book b : QuestClientStore.defs().books().values()) {
            QuestDefs.Chapter c = b.chapters().get(chapterId);
            if (c != null) return c;
        }
        return null;
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gg, int mouseX, int mouseY, float a) {
        super.extractRenderState(gg, mouseX, mouseY, a);
        int x = width / 2 - 150;
        gg.text(font, Component.literal("Title / lines (elbow|diagonal) / background / decorations JSON"), x, 20, 0xFFFFFF, false);
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
