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

import java.util.ArrayList;
import java.util.List;

/** Chapter settings: title, lines, background, decorations JSON. */
public class QuestChapterEditScreen extends Screen {
    private final String chapterId;
    private final @Nullable Screen parent;
    private EditBox title, lines, background, decorations;
    private String error = "";
    private final List<int[]> labels = new ArrayList<>();
    private final List<String> labelNames = new ArrayList<>();

    public QuestChapterEditScreen(String chapterId, @Nullable Screen parent) {
        super(Component.literal("Chapter " + QuestEdit.shortName(chapterId)));
        this.chapterId = chapterId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        labelNames.clear();
        QuestDefs.Chapter c = find();
        JsonObject o = c == null ? new JsonObject() : c.json();
        int cx = width / 2, y = 28;
        int x = cx - 150;
        mark("Title", x, y); y += 10;
        title = box(x, y, QuestEditForms.displayText(str(o, "title"))); y += 28;
        mark("Lines (elbow|diagonal)", x, y); y += 10;
        lines = box(x, y, str(o, "lines").isEmpty() ? "elbow" : str(o, "lines")); y += 28;
        mark("Background", x, y); y += 10;
        background = box(x, y, o.has("background") ? o.get("background").toString() : ""); y += 28;
        mark("Decorations JSON", x, y); y += 10;
        decorations = box(x, y, o.has("decorations") ? o.get("decorations").toString() : "[]");
        decorations.setWidth(300); y += 36;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(cx - 100, y, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.setScreen(parent)).bounds(cx + 10, y, 90, 20).build());
    }

    private void mark(String name, int x, int y) {
        labelNames.add(name);
        labels.add(new int[]{x, y});
    }

    private EditBox box(int x, int y, String v) {
        EditBox b = new EditBox(font, x, y, 300, 20, Component.literal(""));
        b.setMaxLength(20000);
        b.setValue(v == null ? "" : v);
        addRenderableWidget(b);
        return b;
    }

    private void save() {
        QuestDefs.Chapter c = find();
        JsonObject o = c == null ? new JsonObject() : c.json().deepCopy();
        JsonObject lang = new JsonObject();
        String titleKey = c != null ? (c.json().has("title") && c.json().get("title").isJsonPrimitive() ? c.json().get("title").getAsString() : "") : "";
        QuestEditForms.putText(o, lang, "title", titleKey, title.getValue());
        if (lang.size() > 0) o.add("_lang", lang);
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
        for (int i = 0; i < labels.size(); i++) {
            int[] p = labels.get(i);
            gg.text(font, Component.literal(labelNames.get(i)), p[0], p[1], 0xA0A0A0, false);
        }
        if (!error.isEmpty()) gg.text(font, Component.literal(error), 20, height - 30, 0xFF5555, false);
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
        if (!error.isEmpty()) gg.drawString(font, error, 20, height - 30, 0xFF5555);
    }
    *///?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
