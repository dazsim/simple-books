package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws one spread in spread units: 1600 x 1000 for the whole book, each page's content box 644 x 920.
 * Text scale 1 is the Minecraft font at 2 units per font pixel.
 */
public class Spread {
    public static final int W = 1600, H = 1000;
    static final int PX0 = 58, PX1 = 1542, PT = 40, PBOT = 960, SPINE = 800, FADE = 34, M = 32;
    public static final int L = PX0 + M, R = SPINE + FADE + M, CW = SPINE - FADE - M - L;
    private static final Map<String, String> DEFAULT_VARS = Map.of("ink", "#3f2a14", "soft", "#785c3c", "head", "#802c16",
            "link", "#284896", "rule", "#c4ac82", "page", "#f1e4c2", "arrow", "#5c3e22", "cover", "#60361a", "error", "#b01818");

    public record Hit(float x0, float y0, float x1, float y1, Object target) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final GuiGraphicsExtractor g;
    private final Font font;
    private final Book book;
    private final Book.@Nullable Page page;
    private final int spreadNo;
    public final List<Hit> hits = new ArrayList<>();
    private final Map<String, Integer> cursors = new HashMap<>();
    private int stepNo;
    private boolean draw = true;
    private final List<String> runtimeErrors = new ArrayList<>();

    public Spread(GuiGraphicsExtractor g, Font font, Book book, Book.@Nullable Page page, int spreadNo) {
        this.g = g;
        this.font = font;
        this.book = book;
        this.page = page;
        this.spreadNo = spreadNo;
    }

    // ---------- colours and styles ----------

    int color(@Nullable String spec) {
        if (spec == null) return 0xFF000000;
        if (spec.startsWith("$")) {
            String k = spec.substring(1);
            String v = book.vars().get(k);
            if (v == null && page != null && page.template() != null) v = page.template().vars().get(k);
            if (v == null) v = DEFAULT_VARS.getOrDefault(k, "#000000");
            spec = v;
        }
        try {
            return 0xFF000000 | Integer.parseInt(spec.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xFFFF00FF;
        }
    }

    private static int rgb(int r, int gr, int b) {
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private JsonObject style(@Nullable String name) {
        if (name == null || page == null || page.template() == null) return new JsonObject();
        JsonObject s = page.template().styles().get(name);
        return s == null ? new JsonObject() : s;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static float num(JsonObject o, String k, float def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsFloat() : def;
    }

    /** Field lookup with "a|b" fallbacks. */
    static @Nullable JsonElement bound(JsonObject page, String bind) {
        for (String k : bind.split("\\|")) {
            JsonElement e = page.get(k.trim());
            if (e != null && !e.isJsonNull()) return e;
        }
        return null;
    }

    // ---------- primitives ----------

    void fill(float x0, float y0, float x1, float y1, int col) {
        if (!draw) return;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.fill(0, 0, Math.round(x1 - x0), Math.round(y1 - y0), col);
        g.pose().popMatrix();
    }

    float textW(Component c, float sc) {
        return font.width(c) * 2 * sc;
    }

    float textW(FormattedCharSequence c, float sc) {
        return font.width(c) * 2 * sc;
    }

    void text(FormattedCharSequence s, float x, float y, float sc, int col) {
        if (!draw) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(2 * sc, 2 * sc);
        g.text(font, s, 0, 0, col, false);
        g.pose().popMatrix();
    }

    void text(Component c, float x, float y, float sc, int col) {
        text(c.getVisualOrderText(), x, y, sc, col);
    }

    void ctext(Component c, float cx, float y, float sc, int col) {
        text(c, cx - textW(c, sc) / 2, y, sc, col);
    }

    /** Word wrap that never splits a word, like the mockup renderer. */
    List<FormattedCharSequence> wrap(Component c, float maxw, float sc) {
        int max = Math.max(1, (int) (maxw / (2 * sc)));
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String para : c.getString().split("\n")) {
            String cur = "";
            for (String w : para.split(" ")) {
                String t = cur.isEmpty() ? w : cur + " " + w;
                if (font.width(t) - 1 <= max || cur.isEmpty()) cur = t;
                else {
                    addLine(out, cur, max);
                    cur = w;
                }
            }
            addLine(out, cur, max);
        }
        return out;
    }

    /** Wrapped paragraph; returns the height used. */
    float para(Component c, float x, float y, float maxw, float sc, int col, float lh, String align) {
        List<FormattedCharSequence> lines = wrap(c, maxw, sc);
        for (int i = 0; i < lines.size(); i++) {
            float lx = switch (align) {
                case "center" -> x + (maxw - textW(lines.get(i), sc)) / 2;
                case "right" -> x + maxw - textW(lines.get(i), sc);
                default -> x;
            };
            text(lines.get(i), lx, y + i * lh, sc, col);
        }
        return lines.size() * lh;
    }

    private void addLine(List<FormattedCharSequence> out, String line, int max) {
        if (font.width(line) - 1 <= max) out.add(Component.literal(line).getVisualOrderText());
        else out.addAll(font.split(Component.literal(line), max));
    }

    void rule(float x0, float x1, float y) {
        fill(x0, y - 1, x1, y + 1, color("$rule"));
    }

    void arrow(float x, float y, float length, boolean down, float u, int col) {
        float head = 4 * u;
        if (!down) {
            fill(x, y - u, x + length - head, y + u, col);
            for (int i = 0; i < 4; i++) {
                float hx = x + length - head + i * u, h = (4 - i) * u;
                fill(hx, y - h, hx + u, y + h, col);
            }
        } else {
            fill(x - u, y, x + u, y + length - head, col);
            for (int i = 0; i < 4; i++) {
                float hy = y + length - head + i * u, h = (4 - i) * u;
                fill(x - h, hy, x + h, hy + u, col);
            }
        }
    }

    void slot(float x, float y, float s) {
        float k = Math.max(1, s / 18);
        fill(x, y, x + s, y + s, rgb(139, 139, 139));
        fill(x, y, x + s, y + k, rgb(55, 55, 55));
        fill(x, y, x + k, y + s, rgb(55, 55, 55));
        fill(x, y + s - k, x + s, y + s, rgb(255, 255, 255));
        fill(x + s - k, y, x + s, y + s, rgb(255, 255, 255));
    }

    void item(ItemRef ref, float x, float y, float size) {
        ItemStack stack = ref.stack();
        if (stack.isEmpty()) {
            float h = size / 2;
            fill(x, y, x + h, y + h, rgb(248, 0, 248));
            fill(x + h, y, x + size, y + h, rgb(0, 0, 0));
            fill(x, y + h, x + h, y + size, rgb(0, 0, 0));
            fill(x + h, y + h, x + size, y + size, rgb(248, 0, 248));
            ctext(Component.literal("?"), x + h, y + h - 8, 1, 0xFFFFFFFF);
            hits.add(new Hit(x, y, x + size, y + size, ref.name().copy().append(" (missing)")));
            return;
        }
        if (draw) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(size / 16f, size / 16f);
            g.item(stack, 0, 0);
            if (ref.count() > 1) g.itemDecorations(font, stack, 0, 0, String.valueOf(ref.count()));
            g.pose().popMatrix();
        }
        hits.add(new Hit(x, y, x + size, y + size, stack));
    }

    void slotItem(ItemRef ref, float x, float y, float s) {
        slot(x, y, s);
        float isz = s <= 36 ? s - 4 : s - 12;
        item(ref, x + (s - isz) / 2, y + (s - isz) / 2, isz);
    }

    void badge(float x, float y, int n) {
        fill(x, y, x + 26, y + 26, color("$head"));
        int edge = rgb(196, 120, 90);
        fill(x + 2, y + 2, x + 24, y + 3, edge);
        fill(x + 2, y + 23, x + 24, y + 24, edge);
        fill(x + 2, y + 2, x + 3, y + 24, edge);
        fill(x + 23, y + 2, x + 24, y + 24, edge);
        ctext(Component.literal(String.valueOf(n)), x + 13, y + 5, 1, rgb(250, 238, 210));
    }

    void image(@Nullable String tex, float x, float y, float w, float h, @Nullable String label) {
        int[] size = tex == null ? null : BookRegistry.imageSize(Identifier.parse(tex));
        if (size == null) {
            placeholder(x, y, w, h, Texts.of(label != null ? label : tex == null ? "?" : "Missing image: " + tex));
            return;
        }
        fill(x - 4, y - 4, x + w + 4, y + h + 4, rgb(92, 64, 40));
        fill(x - 2, y - 2, x + w + 2, y + h + 2, rgb(30, 22, 16));
        float r = Math.max(w / size[0], h / size[1]);
        float uw = w / r / size[0], vh = h / r / size[1];
        float u0 = (1 - uw) / 2, v0 = (1 - vh) / 2;
        if (draw) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(0.5f, 0.5f);
            g.blit(Identifier.parse(tex), 0, 0, Math.round(w * 2), Math.round(h * 2), u0, u0 + uw, v0, v0 + vh);
            g.pose().popMatrix();
        }
    }

    void placeholder(float x, float y, float w, float h, Component label) {
        int bg = rgb(222, 206, 168);
        fill(x, y, x + w, y + h, rgb(92, 64, 40));
        fill(x + 3, y + 3, x + w - 3, y + h - 3, bg);
        int hatch = rgb(212, 194, 152);
        for (float i = -h; i < w; i += 18)
            for (float t = Math.max(0, -i); t < Math.min(h, w - i); t += 2)
                if (i + t > 3 && i + t < w - 5 && t > 3 && t < h - 5) fill(x + i + t, y + t, x + i + t + 2, y + t + 2, hatch);
        List<FormattedCharSequence> ls = wrap(label, w - 20, 1);
        float ty = y + (h - ls.size() * 20) / 2;
        for (FormattedCharSequence ln : ls) {
            float tw = textW(ln, 1);
            fill(x + (w - tw) / 2 - 3, ty - 2, x + (w + tw) / 2 + 2, ty + 17, bg);
            text(ln, x + (w - tw) / 2, ty, 1, color("$soft"));
            ty += 20;
        }
    }

    // ---------- frame ----------

    public void frame() {
        int cover = color("$cover"), edge = rgb(58, 32, 14), paper = color("$page");
        roundRect(22, 18, W - 22, H - 18, 14, edge);
        roundRect(26, 22, W - 26, H - 22, 10, cover);
        fill(PX0, PT, PX1, PBOT, paper);
        int pr = paper >> 16 & 255, pg = paper >> 8 & 255, pb = paper & 255;
        for (int off = 0; off <= FADE; off++) {
            double k = Math.pow(1 - off / (double) FADE, 2.2) * 0.28;
            int c = rgb((int) (pr - k * pr), (int) (pg - k * pg), (int) (pb - k * pb));
            fill(SPINE - off, PT, SPINE - off + 1, PBOT, c);
            fill(SPINE + off, PT, SPINE + off + 1, PBOT, c);
        }
        int line = rgb(170, 140, 96);
        fill(PX0, PT, PX1, PT + 2, line);
        fill(PX0, PBOT - 2, PX1, PBOT, line);
        fill(PX0, PT, PX0 + 2, PBOT, line);
        fill(PX1 - 2, PT, PX1, PBOT, line);
    }

    private void roundRect(float x0, float y0, float x1, float y1, float r, int col) {
        fill(x0, y0 + r, x1, y1 - r, col);
        for (int i = 0; i < r; i++) {
            float dx = (float) (r - Math.sqrt(r * r - (r - i - 0.5) * (r - i - 0.5)));
            fill(x0 + dx, y0 + i, x1 - dx, y0 + i + 1, col);
            fill(x0 + dx, y1 - i - 1, x1 - dx, y1 - i, col);
        }
    }

    // ---------- page ----------

    public void content() {
        if (page == null) return;
        if (page.broken()) {
            errorPage(page.file(), page.errors());
            return;
        }
        Template t = page.template();
        for (var e : t.slots().entrySet()) {
            try {
                slot(e.getKey(), e.getValue());
            } catch (Exception ex) {
                runtimeErrors.add("slot " + e.getKey() + ": " + ex);
            }
        }
        for (var c : cursors.entrySet()) {
            JsonElement list = bound(page.json(), c.getKey());
            int size = list != null && list.isJsonArray() ? list.getAsJsonArray().size() : 0;
            if (c.getValue() < size) runtimeErrors.add("/" + c.getKey() + ": " + (size - c.getValue()) + " item(s) did not fit on the spread");
        }
        if (!runtimeErrors.isEmpty()) {
            float y = PBOT - 40 - runtimeErrors.size() * 18;
            for (String err : runtimeErrors) {
                text(Component.literal(err), R, y, 1, color("$error"));
                y += 18;
            }
        }
    }

    public List<String> runtimeErrors() {
        return runtimeErrors;
    }

    private void slot(String name, JsonObject slot) {
        boolean left = name.startsWith("left.");
        JsonArray b = slot.getAsJsonArray("box");
        float x = (left ? L : R) + b.get(0).getAsFloat(), y = PT + b.get(1).getAsFloat(), w = b.get(2).getAsFloat(), h = b.get(3).getAsFloat();
        String type = slot.get("type").getAsString();
        JsonObject json = page.json();
        JsonElement val = slot.has("bind") && slot.get("bind").isJsonPrimitive() ? bound(json, slot.get("bind").getAsString()) : null;
        JsonObject st = style(str(slot, "style", null));
        int col = color(str(st, "color", "$ink"));
        float sc = num(st, "scale", 1);
        String align = str(st, "align", "left");
        switch (type) {
            case "title" -> {
                if (val == null) return;
                Component c = Texts.of(val.getAsString());
                float tw = textW(c, sc), cx = align.equals("center") ? x + w / 2 : x + tw / 2;
                text(c, cx - tw / 2, y, sc, col);
                JsonElement icon = slot.has("icon") ? bound(json, slot.get("icon").getAsString()) : null;
                if (icon != null) {
                    ItemRef ref = ItemRef.parse(icon);
                    float isz = num(slot, "icon_size", 40);
                    item(ref, cx - tw / 2 - isz - 10, y - 6, isz);
                    item(ref, cx + tw / 2 + 10, y - 6, isz);
                }
            }
            case "subtitle", "text" -> {
                if (val == null) return;
                para(Texts.of(val.getAsString()), x, y, w, sc, col, num(st, "line_height", 18 * sc), align);
            }
            case "rule" -> rule(x, x + w, y);
            case "arrow" -> arrow(x, y, w > 0 ? w : h, w == 0, num(slot, "unit", 3), color("$arrow"));
            case "page_number" -> ctext(Component.translatable("simplebooks.page", spreadNo * 2 + (left ? 1 : 2)), x + w / 2, y, 1, color("$soft"));
            case "need_list", "ingredient_list", "tool_list" -> needList(slot, x, y, w);
            case "step_rows" -> stepRows(slot, x, y, w, h);
            case "link_list" -> {
                if (val != null) linkGrid(val.getAsJsonArray(), x, y, w, (int) num(slot, "columns", 4));
            }
            case "image_box" -> {
                if (val != null) image(val.getAsString(), x, y, w, h, str(slot, "placeholder", null));
            }
            case "item_icon" -> {
                if (val != null) slotItem(ItemRef.parse(val), x, y, Math.min(w, h));
            }
            case "grid" -> {
                if (val != null && val.isJsonObject()) grid(val.getAsJsonObject(), x, y);
            }
            default -> {}
        }
    }

    // ---------- ingredient and tool lists ----------

    private void needList(JsonObject slot, float x, float y, float w) {
        JsonObject json = page.json();
        String header = str(slot, "header", "simplebooks.need_header");
        text(Texts.of(header), x, y, 1, color("$ink"));
        float yy = y + 28;
        JsonArray groups = slot.has("groups") ? slot.getAsJsonArray("groups") : defaultGroups();
        int cols = (int) num(slot, "columns", 5);
        float colw = w / cols;
        for (JsonElement ge : groups) {
            JsonObject gr = ge.getAsJsonObject();
            JsonElement list = bound(json, str(gr, "bind", "ingredients"));
            if (list == null || !list.isJsonArray() || list.getAsJsonArray().isEmpty()) continue;
            text(Texts.of(str(gr, "header", "")), x, yy, 1, color("$head"));
            rule(x, x + w, yy + 18);
            JsonArray a = list.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) tile(ItemRef.parse(a.get(i)), x + (i % cols) * colw, yy + 26 + (i / cols) * 42, colw - 44);
            yy += 26 + ((a.size() + cols - 1) / cols) * 42 + 8;
        }
        JsonElement note = slot.has("note") ? bound(json, slot.get("note").getAsString()) : null;
        if (note != null) {
            yy -= 2;
            text(Texts.of(note.getAsString()), x, yy, 1, color("$soft"));
            yy += 24;
        }
        rule(x, x + w, yy);
    }

    private static JsonArray defaultGroups() {
        JsonArray a = new JsonArray();
        for (String[] g : new String[][]{{"ingredients", "simplebooks.ingredients"}, {"tools", "simplebooks.tools"}}) {
            JsonObject o = new JsonObject();
            o.addProperty("bind", g[0]);
            o.addProperty("header", g[1]);
            a.add(o);
        }
        return a;
    }

    private void tile(ItemRef ref, float x, float y, float nameW) {
        slotItem(ref, x, y, 36);
        List<FormattedCharSequence> ls = wrap(ref.name(), Math.max(nameW, 40), 1);
        float ty = ls.size() == 1 ? y + 10 : y + 1;
        for (int j = 0; j < Math.min(2, ls.size()); j++) text(ls.get(j), x + 42, ty + j * 17, 1, color("$ink"));
    }

    // ---------- steps ----------

    private void stepRows(JsonObject slot, float x, float y, float w, float h) {
        String bind = slot.get("bind").getAsString();
        JsonElement list = bound(page.json(), bind);
        if (list == null || !list.isJsonArray()) return;
        JsonArray a = list.getAsJsonArray();
        int i = cursors.getOrDefault(bind, 0);
        float rowH = num(slot, "row_height", 92), yy = y, bottom = y + h;
        float prevBadge = -1;
        boolean placed = false;
        while (i < a.size()) {
            JsonObject el = a.get(i).getAsJsonObject();
            String kind = str(el, "kind", "text");
            if (kind.equals("page_break")) {
                i++;
                if (placed) break;
                continue;
            }
            boolean numbered = isStep(kind);
            draw = false;
            int saveNo = stepNo;
            int hitCount = hits.size();
            float eh = element(el, kind, x, yy, w, rowH);
            stepNo = saveNo;
            while (hits.size() > hitCount) hits.removeLast();
            draw = true;
            if (placed && yy + eh > bottom + 8) break;
            if (numbered && prevBadge >= 0) arrow(x + 13, prevBadge + 32, yy - prevBadge - 36, true, 2, color("$arrow"));
            element(el, kind, x, yy, w, rowH);
            prevBadge = numbered ? yy : -1;
            yy += eh;
            placed = true;
            i++;
        }
        cursors.put(bind, i);
    }

    private static boolean isStep(String kind) {
        return switch (kind) {
            case "craft_shapeless", "craft_shaped", "process", "assembly", "sequence" -> true;
            default -> false;
        };
    }

    private float element(JsonObject el, String kind, float x, float y, float w, float rowH) {
        return switch (kind) {
            case "craft_shapeless", "craft_shaped", "process" -> rowStep(el, kind, x, y, w, rowH);
            case "assembly", "sequence" -> wideStep(el, kind, x, y, w);
            case "links" -> {
                float hh = 0;
                if (el.has("header")) {
                    text(Texts.of(el.get("header").getAsString()), x, y, 1, color("$head"));
                    hh = 24;
                }
                yield hh + linkGrid(el.getAsJsonArray("links"), x, y + hh, w, (int) num(el, "columns", 4)) + 8;
            }
            case "image" -> {
                float iw = el.has("size") ? el.getAsJsonArray("size").get(0).getAsFloat() : w;
                float ih = el.has("size") ? el.getAsJsonArray("size").get(1).getAsFloat() : w * 0.625f;
                image(str(el, "image", null), x + (w - iw) / 2, y + 4, iw, ih, str(el, "placeholder", null));
                yield ih + 16;
            }
            case "arrow" -> {
                arrow(x + w / 2, y, 26, true, 3, color("$arrow"));
                yield 34;
            }
            default -> {
                JsonObject st = style(str(el, "style", "note"));
                float sc = num(st, "scale", 1);
                yield para(Texts.of(str(el, "text", "")), x, y, w, sc, color(str(st, "color", "$soft")), 17, str(st, "align", "left")) + 12;
            }
        };
    }

    private Component stepTitle(JsonObject el) {
        if (el.has("title")) return Texts.of(el.get("title").getAsString());
        if (el.has("output")) return ItemRef.parse(el.get("output")).name();
        return Component.empty();
    }

    private void stepImage(JsonObject el, float x, float y, float w, float h) {
        if (el.has("image")) image(el.get("image").getAsString(), x, y, w, h, str(el, "placeholder", null));
        else if (el.has("placeholder")) placeholder(x, y, w, h, Texts.of(el.get("placeholder").getAsString()));
    }

    private float rowStep(JsonObject el, String kind, float x, float y, float w, float rowH) {
        int n = ++stepNo;
        badge(x, y, n);
        text(stepTitle(el), x + 34, y + 5, 1, color("$head"));
        String noteKey = switch (kind) {
            case "craft_shapeless" -> "simplebooks.shapeless";
            case "craft_shaped" -> "simplebooks.shaped";
            default -> null;
        };
        String note = str(el, "note", noteKey);
        float label = num(el, "label_width", 209);
        float bottom = y + 26;
        if (note != null) bottom = Math.max(bottom, y + 30 + para(Texts.of(note), x + 34, y + 30, label - 34, 1, color("$soft"), 17, "left"));
        float iw = 0;
        if (el.has("image") || el.has("placeholder")) {
            float[] sz = size(el, 104, 76);
            iw = sz[0] + 8;
            stepImage(el, x + w - sz[0], y + 2, sz[0], sz[1]);
            bottom = Math.max(bottom, y + 2 + sz[1]);
        }
        List<Object> cells = new ArrayList<>();
        if (kind.equals("craft_shaped")) {
            cells.add(el.has("pattern") ? el : null);
        } else {
            JsonArray in = el.has("inputs") ? el.getAsJsonArray("inputs") : new JsonArray();
            for (int i = 0; i < in.size(); i++) {
                if (i > 0) cells.add("+");
                cells.add(ItemRef.parse(in.get(i)));
            }
            if (el.has("appliance")) {
                if (!cells.isEmpty()) cells.add(Component.translatable("simplebooks.in"));
                cells.add(ItemRef.parse(el.get("appliance")));
            }
            if (el.has("on")) {
                cells.add(Component.translatable("simplebooks.on"));
                cells.add(ItemRef.parse(el.get("on")));
            }
        }
        if (el.has("output")) {
            cells.add(">");
            cells.add(ItemRef.parse(el.get("output")));
        }
        bottom = Math.max(bottom, chain(x + label, y + 2, cells, x + w - iw));
        return Math.max(rowH, bottom - y + 10);
    }

    private static float[] size(JsonObject el, float w, float h) {
        if (!el.has("image_size")) return new float[]{w, h};
        JsonArray a = el.getAsJsonArray("image_size");
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    /** Icons joined by symbols; returns the bottom edge. */
    private float chain(float x, float y, List<Object> cells, float maxX) {
        final float cell = 96, size = 36;
        float bottom = y + size;
        for (Object c : cells) {
            if (c == null) continue;
            if (c instanceof String s && s.equals(">")) {
                arrow(x, y + size / 2, 30, false, 2, color("$arrow"));
                x += 34;
            } else if (c instanceof String s) {
                ctext(Component.literal(s), x + 14, y + size / 2 - 8, 1, color("$soft"));
                x += 28;
            } else if (c instanceof Component comp) {
                float tw = textW(comp, 1);
                text(comp, x + 4, y + size / 2 - 8, 1, color("$soft"));
                x += tw + 10;
            } else if (c instanceof JsonObject shaped) {
                bottom = Math.max(bottom, grid(shaped, x, y));
                x += 3 * size + 10;
            } else if (c instanceof ItemRef ref) {
                slotItem(ref, x + (cell - size) / 2 - 20, y, size);
                float cx = x + cell / 2 - 20;
                List<FormattedCharSequence> ls = wrap(ref.name(), cell - 6, 1);
                for (int j = 0; j < ls.size(); j++) text(ls.get(j), cx - textW(ls.get(j), 1) / 2, y + size + 4 + j * 17, 1, color("$ink"));
                bottom = Math.max(bottom, y + size + 4 + ls.size() * 17);
                x += cell - 40;
            }
        }
        if (draw && x > maxX + 8) runtimeErrors.add("step " + stepNo + ": row is wider than the page");
        return bottom;
    }

    private float grid(JsonObject shaped, float x, float y) {
        JsonArray pattern = shaped.getAsJsonArray("pattern");
        JsonObject key = shaped.has("key") ? shaped.getAsJsonObject("key") : new JsonObject();
        for (int r = 0; r < 3; r++) {
            String row = r < pattern.size() ? pattern.get(r).getAsString() : "";
            for (int c = 0; c < 3; c++) {
                slot(x + c * 36, y + r * 36, 36);
                String k = c < row.length() ? String.valueOf(row.charAt(c)) : " ";
                if (key.has(k)) {
                    ItemRef ref = ItemRef.parse(key.get(k));
                    item(ref, x + c * 36 + 2, y + r * 36 + 2, 32);
                }
            }
        }
        return y + 108;
    }

    private float wideStep(JsonObject el, String kind, float x, float y, float w) {
        int n = ++stepNo;
        badge(x, y, n);
        text(stepTitle(el), x + 34, y + 5, 1, color("$head"));
        boolean textFirst = el.has("text_first") ? el.get("text_first").getAsBoolean() : kind.equals("assembly");
        float[] sz = size(el, kind.equals("assembly") ? 168 : 230, kind.equals("assembly") ? 94 : 112);
        boolean img = el.has("image") || el.has("placeholder");
        if (img) stepImage(el, x + w - sz[0], y + (textFirst ? 2 : 8), sz[0], sz[1]);
        Component body = el.has("text") ? Texts.of(el.get("text").getAsString()) : null;
        List<ItemRef> cells = new ArrayList<>();
        String cellKey = el.has("cells") ? "cells" : "inputs";
        if (el.has(cellKey)) el.getAsJsonArray(cellKey).forEach(c -> cells.add(ItemRef.parse(c)));
        if (!el.has("cells") && el.has("output")) cells.add(ItemRef.parse(el.get("output")));
        float yy;
        if (textFirst) {
            float ph = body == null ? 0 : para(body, x + 34, y + 32, w - (img ? sz[0] + 50 : 34), 1, color("$ink"), 18, "left");
            yy = y + Math.max(32 + ph, img ? sz[1] + 2 : 26) + 20;
            yy = bigCells(cells, x, yy, w) + 15;
        } else {
            float rowX = x + 34, avail = w - 34 - (img ? sz[0] + 16 : 0);
            yy = bigCells(cells, rowX, y + 38, avail);
            if (img) yy = Math.max(yy, y + 8 + sz[1]);
            yy += 14;
            if (body != null) yy += para(body, x + 34, yy, w - 34, 1, color("$ink"), 17, "left") + 14;
        }
        return yy - y;
    }

    private float bigCells(List<ItemRef> cells, float x, float y, float avail) {
        if (cells.isEmpty()) return y;
        final float size = 52;
        float step = cells.size() > 1 ? Math.min(avail - size, 148 * (cells.size() - 1)) / (cells.size() - 1) : 0;
        float bottom = y + size;
        for (int i = 0; i < cells.size(); i++) {
            ItemRef ref = cells.get(i);
            float cx = x + i * step;
            slotItem(ref, cx, y, size);
            List<FormattedCharSequence> ls = wrap(ref.name(), Math.max(110, step - 20), 1);
            float ty = y + size + 4;
            for (FormattedCharSequence ln : ls) {
                text(ln, cx + size / 2 - textW(ln, 1) / 2, ty, 1, color("$ink"));
                ty += 17;
            }
            if (ref.note() != null) {
                ctext(Texts.of(ref.note()), cx + size / 2, ty, 1, color("$soft"));
                ty += 17;
            }
            bottom = Math.max(bottom, ty);
            if (i < cells.size() - 1) {
                float g0 = cx + size, g1 = cx + step, len = Math.min(42, g1 - g0 - 12);
                arrow((g0 + g1) / 2 - len / 2, y + size / 2, len, false, 3, color("$arrow"));
            }
        }
        return bottom;
    }

    private float linkGrid(JsonArray links, float x, float y, float w, int cols) {
        float tc = w / cols;
        int rows = 0;
        for (int i = 0; i < links.size(); i++) {
            JsonObject l = links.get(i).getAsJsonObject();
            int c = i % cols, r = i / cols;
            rows = r + 1;
            float lx = x + c * tc, ly = y + r * 42;
            boolean hasIcon = l.has("item") || l.has("items");
            ItemRef ref = hasIcon ? ItemRef.parse(l) : null;
            if (ref != null) slotItem(ref, lx, ly, 36);
            Component label = l.has("label") ? Texts.of(l.get("label").getAsString()) : ref != null ? ref.name() : Component.literal("?");
            float tx = ref != null ? lx + 42 : lx;
            List<FormattedCharSequence> ls = wrap(label, tc - (tx - lx) - 4, 1);
            float yy = ls.size() == 1 ? ly + 10 : ly + 1;
            int col = color("$link");
            Identifier target = l.has("page") ? BookRegistry.resolveLink(book, l.get("page").getAsString()) : null;
            for (int j = 0; j < Math.min(2, ls.size()); j++) {
                float tw = textW(ls.get(j), 1);
                text(ls.get(j), tx, yy + j * 17, 1, col);
                fill(tx, yy + j * 17 + 16, tx + tw - 2, yy + j * 17 + 18, col);
                if (target != null) hits.add(new Hit(tx, yy + j * 17 - 2, tx + tw, yy + j * 17 + 18, target));
            }
            if (target != null && ref != null) hits.add(new Hit(lx, ly, lx + 36, ly + 36, target));
        }
        return rows * 42;
    }

    // ---------- errors ----------

    public void errorPage(String file, List<String> errors) {
        int red = color("$error");
        float[] xs = {L, R};
        int idx = 0;
        for (int p = 0; p < 2; p++) {
            float x = xs[p], y = PT + 20, x1 = x + CW, y1 = PBOT - 60;
            fill(x - 8, y - 8, x1 + 8, y - 4, red);
            fill(x - 8, y1 + 4, x1 + 8, y1 + 8, red);
            fill(x - 8, y - 8, x - 4, y1 + 8, red);
            fill(x1 + 4, y - 8, x1 + 8, y1 + 8, red);
            float yy = y + 8;
            if (p == 0) {
                text(Component.translatable("simplebooks.error.title"), x + 8, yy, 1.5f, red);
                yy += 34;
                yy += para(Component.literal(file), x + 8, yy, CW - 16, 1, color("$soft"), 17, "left") + 12;
            }
            while (idx < errors.size()) {
                List<FormattedCharSequence> ls = wrap(Component.literal("- " + errors.get(idx)), CW - 16, 1);
                if (yy + ls.size() * 17 > y1 - 8) break;
                for (FormattedCharSequence ln : ls) {
                    text(ln, x + 8, yy, 1, color("$ink"));
                    yy += 17;
                }
                yy += 6;
                idx++;
            }
        }
    }
}
