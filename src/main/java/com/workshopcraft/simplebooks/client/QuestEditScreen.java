package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.quest.QuestClientStore;
import com.workshopcraft.simplebooks.quest.QuestDefs;
import com.workshopcraft.simplebooks.quest.QuestEditPacket;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Quest property editor with built-in task and reward forms. */
public class QuestEditScreen extends Screen {
    private final String questId;
    private final @Nullable Screen parent;
    private EditBox title, subtitle, text, icon;
    private String titleKey = "", subtitleKey = "", textKey = "";
    private QuestEditForms.@Nullable TaskRow taskRow;
    private QuestEditForms.@Nullable RewardRow rewardRow;
    private JsonObject draft;
    private int taskIndex, rewardIndex;
    private String error = "";
    private final List<int[]> labels = new ArrayList<>();
    private final List<String> labelNames = new ArrayList<>();

    public QuestEditScreen(String questId, @Nullable Screen parent) {
        super(Component.literal("Edit " + QuestEdit.shortName(questId)));
        this.questId = questId;
        this.parent = parent;
        QuestDefs.Quest q = find();
        draft = q == null ? new JsonObject() : q.json().deepCopy();
    }

    @Override
    protected void init() {
        clearWidgets();
        labels.clear();
        labelNames.clear();
        titleKey = str(draft, "title");
        subtitleKey = str(draft, "subtitle");
        textKey = str(draft, "text");
        int x = 20, y = 16;
        title = labeled("Title", x, y, 360, QuestEditForms.displayText(titleKey)); y += 32;
        subtitle = labeled("Subtitle", x, y, 360, QuestEditForms.displayText(subtitleKey)); y += 32;
        icon = labeled("Icon", x, y, 360, str(draft, "icon")); y += 32;
        text = labeled("Text", x, y, 360, QuestEditForms.displayText(textKey)); y += 34;

        JsonArray tarr = arr(draft, "tasks");
        if (tarr.isEmpty()) tarr.add(new JsonObject());
        taskIndex = Math.floorMod(taskIndex, tarr.size());
        addRenderableWidget(Button.builder(Component.literal("Task " + (taskIndex + 1) + "/" + tarr.size()), b -> {
            commitRows();
            taskIndex++;
            rebuild();
        }).bounds(x, y, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            commitRows();
            arr(draft, "tasks").add(new JsonObject());
            taskIndex = arr(draft, "tasks").size() - 1;
            rebuild();
        }).bounds(x + 115, y, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            commitRows();
            JsonArray a = arr(draft, "tasks");
            if (a.size() <= 1) return;
            a.remove(taskIndex);
            taskIndex = Math.min(taskIndex, a.size() - 1);
            rebuild();
        }).bounds(x + 144, y, 24, 20).build());
        y += 24;
        taskRow = new QuestEditForms.TaskRow(font, x, y, tarr.get(taskIndex).getAsJsonObject());
        addRenderableWidget(Button.builder(Component.literal("Type: " + taskRow.type), b -> {
            taskRow.cycleType();
            commitRows();
            rebuild();
        }).bounds(x + 290, y - 24, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal(taskRow.useRaw ? "Raw JSON" : "Form"), b -> {
            taskRow.useRaw = !taskRow.useRaw;
            commitRows();
            rebuild();
        }).bounds(x + 420, y - 24, 80, 20).build());
        addTaskLabels(x, y, taskRow);
        addTaskFields(x, y, taskRow);
        y += 72;

        JsonArray rarr = arr(draft, "rewards");
        if (rarr.isEmpty()) rarr.add(new JsonObject());
        rewardIndex = Math.floorMod(rewardIndex, rarr.size());
        addRenderableWidget(Button.builder(Component.literal("Reward " + (rewardIndex + 1) + "/" + rarr.size()), b -> {
            commitRows();
            rewardIndex++;
            rebuild();
        }).bounds(x, y, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            commitRows();
            arr(draft, "rewards").add(new JsonObject());
            rewardIndex = arr(draft, "rewards").size() - 1;
            rebuild();
        }).bounds(x + 135, y, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            commitRows();
            JsonArray a = arr(draft, "rewards");
            if (a.size() <= 1) return;
            a.remove(rewardIndex);
            rewardIndex = Math.min(rewardIndex, a.size() - 1);
            rebuild();
        }).bounds(x + 164, y, 24, 20).build());
        y += 24;
        rewardRow = new QuestEditForms.RewardRow(font, x, y, rarr.get(rewardIndex).getAsJsonObject());
        addRenderableWidget(Button.builder(Component.literal("Kind: " + rewardRow.kind), b -> {
            rewardRow.cycleKind();
            commitRows();
            rebuild();
        }).bounds(x + 290, y - 24, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal(rewardRow.useRaw ? "Raw JSON" : "Form"), b -> {
            rewardRow.useRaw = !rewardRow.useRaw;
            commitRows();
            rebuild();
        }).bounds(x + 420, y - 24, 80, 20).build());
        addRewardLabels(x, y, rewardRow);
        addRewardFields(x, y, rewardRow);

        int by = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(width / 2 - 100, by, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.setScreen(parent)).bounds(width / 2 + 10, by, 90, 20).build());
    }

    private EditBox labeled(String name, int x, int y, int w, String value) {
        mark(name, x, y);
        return addField(x, y + 10, w, value);
    }

    private void mark(String name, int x, int y) {
        labelNames.add(name);
        labels.add(new int[]{x, y});
    }

    private void addTaskLabels(int x, int y, QuestEditForms.TaskRow row) {
        if (row.useRaw) { mark("Raw JSON", x, y - 10); return; }
        mark("Id", x, y - 10);
        switch (row.type) {
            case "item", "craft", "smelt", "pickup", "use" -> {
                mark("Item", x + 90, y - 10);
                mark("Count", x + 240, y - 10);
            }
            case "place", "break" -> { mark("Block", x, y + 12); mark("Count", x + 150, y + 12); }
            case "kill" -> { mark("Entity", x, y + 12); mark("Count", x + 150, y + 12); }
            case "dimension" -> mark("Dimension", x, y + 12);
            case "biome" -> mark("Biome", x, y + 12);
            case "advancement" -> mark("Advancement", x, y + 12);
            case "stat" -> { mark("Stat", x, y + 12); mark("Count", x + 210, y + 12); }
            case "checkbox" -> mark("Checkbox text", x, y + 12);
            default -> mark("Raw JSON", x, y + 12);
        }
    }

    private void addRewardLabels(int x, int y, QuestEditForms.RewardRow row) {
        if (row.useRaw) { mark("Raw JSON", x, y - 10); return; }
        switch (row.kind) {
            case "item" -> { mark("Item", x, y - 10); mark("Count", x + 150, y - 10); }
            case "xp", "xp_levels" -> mark("Amount", x, y - 10);
            case "command" -> { mark("Command", x, y - 10); mark("Label", x, y + 12); mark("Icon", x + 150, y + 12); }
            case "loot_table" -> mark("Loot table", x, y - 10);
            default -> mark("Raw JSON", x, y - 10);
        }
    }

    private void addTaskFields(int x, int y, QuestEditForms.TaskRow row) {
        if (row.useRaw) {
            add(row.raw);
            return;
        }
        add(row.id);
        switch (row.type) {
            case "item", "craft", "smelt", "pickup", "use" -> {
                add(row.item);
                add(row.count);
                if (row.type.equals("item")) {
                    addRenderableWidget(Button.builder(Component.literal(row.consume ? "consume: on" : "consume: off"), b -> {
                        row.consume = !row.consume;
                        b.setMessage(Component.literal(row.consume ? "consume: on" : "consume: off"));
                    }).bounds(x, y + 44, 100, 20).build());
                    addRenderableWidget(Button.builder(Component.literal("hand_in: " + row.handIn), b -> {
                        row.handIn = switch (row.handIn) { case "both" -> "hand"; case "hand" -> "box"; default -> "both"; };
                        b.setMessage(Component.literal("hand_in: " + row.handIn));
                    }).bounds(x + 110, y + 44, 110, 20).build());
                    addRenderableWidget(Button.builder(Component.literal(row.teamTotal ? "team: on" : "team: off"), b -> {
                        row.teamTotal = !row.teamTotal;
                        b.setMessage(Component.literal(row.teamTotal ? "team: on" : "team: off"));
                    }).bounds(x + 230, y + 44, 90, 20).build());
                }
            }
            case "place", "break" -> { add(row.block); add(row.count); }
            case "kill" -> { add(row.entity); add(row.count); }
            case "dimension" -> add(row.dimension);
            case "biome" -> add(row.biome);
            case "advancement" -> add(row.advancement);
            case "stat" -> { add(row.stat); add(row.count); }
            case "checkbox" -> add(row.text);
            default -> add(row.raw);
        }
    }

    private void addRewardFields(int x, int y, QuestEditForms.RewardRow row) {
        if (row.useRaw) { add(row.raw); return; }
        switch (row.kind) {
            case "item" -> { add(row.item); add(row.count); }
            case "xp", "xp_levels" -> add(row.count);
            case "command" -> { add(row.command); add(row.label); add(row.icon); }
            case "loot_table" -> add(row.loot);
            default -> add(row.raw);
        }
    }

    private void add(AbstractWidget w) {
        addRenderableWidget(w);
    }

    private EditBox addField(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(""));
        box.setMaxLength(10000);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    private void commitRows() {
        JsonObject lang = new JsonObject();
        QuestEditForms.putText(draft, lang, "title", titleKey, title.getValue());
        QuestEditForms.putText(draft, lang, "subtitle", subtitleKey, subtitle.getValue());
        QuestEditForms.putText(draft, lang, "text", textKey, text.getValue());
        if (icon.getValue().isBlank()) draft.remove("icon");
        else draft.addProperty("icon", icon.getValue().trim());
        titleKey = str(draft, "title");
        subtitleKey = str(draft, "subtitle");
        textKey = str(draft, "text");
        if (lang.size() > 0) {
            JsonObject existing = draft.has("_lang") && draft.get("_lang").isJsonObject()
                    ? draft.getAsJsonObject("_lang") : new JsonObject();
            for (var e : lang.entrySet()) existing.add(e.getKey(), e.getValue());
            draft.add("_lang", existing);
        }
        if (taskRow != null) {
            JsonArray a = arr(draft, "tasks");
            while (a.size() <= taskIndex) a.add(new JsonObject());
            a.set(taskIndex, taskRow.toJson(lang));
        }
        if (rewardRow != null) {
            JsonArray a = arr(draft, "rewards");
            while (a.size() <= rewardIndex) a.add(new JsonObject());
            a.set(rewardIndex, rewardRow.toJson());
        }
    }

    private void rebuild() {
        init();
    }

    private void save() {
        error = "";
        commitRows();
        prunePlaceholders(arr(draft, "tasks"), true);
        prunePlaceholders(arr(draft, "rewards"), false);
        if (!draft.has("tasks") || !draft.get("tasks").isJsonArray() || draft.getAsJsonArray("tasks").isEmpty())
            draft.remove("tasks");
        if (!draft.has("rewards") || !draft.get("rewards").isJsonArray() || draft.getAsJsonArray("rewards").isEmpty())
            draft.remove("rewards");
        if (draft.has("tasks") && taskRow != null) {
            String err = taskRow.validate();
            if (err != null) { error = err; return; }
        }
        if (draft.has("rewards") && rewardRow != null) {
            String err = rewardRow.validate();
            if (err != null) { error = err; return; }
        }
        if (draft.has("chapter")) {
            String ch = draft.get("chapter").getAsString();
            if (ch.contains("/")) draft.addProperty("chapter", ch.substring(ch.indexOf('/') + 1));
        }
        SimpleBooks.toServerEdit.accept(new QuestEditPacket("put_quest", questId, draft.toString()));
        QuestEdit.status = "Saved " + QuestEdit.shortName(questId) + ".";
        minecraft.setScreen(parent);
    }

    private static void prunePlaceholders(JsonArray a, boolean tasks) {
        for (int i = a.size() - 1; i >= 0; i--) {
            if (!a.get(i).isJsonObject()) continue;
            JsonObject o = a.get(i).getAsJsonObject();
            if (o.size() == 0) { a.remove(i); continue; }
            if (!tasks && o.has("item") && o.get("item").getAsString().isBlank()
                    && !o.has("xp") && !o.has("xp_levels") && !o.has("command") && !o.has("loot_table") && !o.has("type"))
                a.remove(i);
            else if (tasks && !o.has("type") && !o.has("id"))
                a.remove(i);
        }
    }

    private static JsonArray arr(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) o.add(key, new JsonArray());
        return o.getAsJsonArray(key);
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
        for (int i = 0; i < labels.size(); i++) {
            int[] p = labels.get(i);
            gg.text(font, Component.literal(labelNames.get(i)), p[0], p[1], 0xA0A0A0, false);
        }
        if (taskRow != null && !taskRow.item.getValue().isBlank()) {
            String err = QuestEditForms.itemError(taskRow.item.getValue());
            if (err != null) gg.text(font, Component.literal(err), 400, 150, 0xFF5555, false);
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
