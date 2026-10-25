package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Draws one spread in spread units: 1600 x 1000 for the whole book, each page's content box 644 x 920.
 * Text scale 1 is the Minecraft font at 2 units per font pixel.
 */
public class Spread {
    public static final int W = 1600, H = 1000;
    static final int PX0 = 58, PX1 = 1542, PT = 40, PBOT = 960, SPINE = 800, FADE = 34, M = 32;
    public static final int L = PX0 + M, R = SPINE + FADE + M, CW = SPINE - FADE - M - L;
    private static final Map<String, String> DEFAULT_VARS = Map.ofEntries(Map.entry("ink", "#3f2a14"), Map.entry("soft", "#785c3c"),
            Map.entry("head", "#802c16"), Map.entry("link", "#284896"), Map.entry("rule", "#c4ac82"), Map.entry("page", "#f1e4c2"),
            Map.entry("arrow", "#5c3e22"), Map.entry("cover", "#60361a"), Map.entry("error", "#b01818"),
            Map.entry("edge", "#aa8c60"), Map.entry("cover_edge", "#3a200e"), Map.entry("badge", "$head"),
            Map.entry("badge_edge", "#c4785a"), Map.entry("badge_text", "#faeed2"), Map.entry("frame", "#5c4028"),
            Map.entry("frame_inner", "#1e1610"), Map.entry("placeholder", "#decea8"), Map.entry("placeholder_hatch", "#d4c298"),
            Map.entry("slot", "#8b8b8b"), Map.entry("slot_shadow", "#373737"), Map.entry("slot_light", "#ffffff"),
            Map.entry("mark", "#802c16"), Map.entry("mark_hover", "#a83a1e"), Map.entry("mark_line", "#e0b080"),
            Map.entry("tabs", "#802c16,#2c5a80,#3c6a2c,#8a6a1c,#5a2c70"), Map.entry("tab_text", "$page"), Map.entry("tab_edge", "$cover_edge"),
            Map.entry("button_tint", "#ffffff"));
    public static final int TAB_ROOT = 1556, TAB_OUT = 34, TAB_MARGIN = 48;
    /** Real pixels per spread unit on screen, set by the book screen. */
    public static float renderScale = 1;

    /** A click target that turns to a spread, with a label for its tooltip. */
    public record Jump(int spread, Component label) {}

    public record Hit(float x0, float y0, float x1, float y1, Object target) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final @Nullable GuiGraphicsExtractor g;
    private final Font font;
    private final Book book;
    private final Book.@Nullable Page page;
    private final int spreadNo;
    public final List<Hit> hits = new ArrayList<>();
    private final Map<String, Integer> cursors = new HashMap<>();
    private int stepNo;
    private boolean draw = true;
    private final List<String> runtimeErrors = new ArrayList<>();
    private final List<String> arrows = new ArrayList<>();
    private record Box(float x0, float y0, float x1, float y1, String what, boolean icon) {}
    private final List<Box> boxes = new ArrayList<>();
    private final @Nullable FontDescription fontDesc;

    public Spread(@Nullable GuiGraphicsExtractor g, Font font, Book book, Book.@Nullable Page page, int spreadNo) {
        this.g = g;
        this.font = font;
        this.book = book;
        this.page = page;
        this.spreadNo = spreadNo;
        String f = var("font");
        Identifier fid = f == null ? null : Identifier.tryParse(f);
        this.fontDesc = fid == null || fid.equals(Identifier.withDefaultNamespace("default")) ? null : new FontDescription.Resource(fid);
    }

    /** A style value: the page's own style, then the book's, then the template's vars, then the defaults. */
    public @Nullable String var(String k) {
        if (page != null && page.json().has("style") && page.json().get("style").isJsonObject()) {
            JsonElement v = page.json().getAsJsonObject("style").get(k);
            if (v != null && v.isJsonPrimitive()) return v.getAsString();
        }
        String v = book.vars().get(k);
        if (v == null && page != null && page.template() != null) v = page.template().vars().get(k);
        return v != null ? v : DEFAULT_VARS.get(k);
    }

    Component styled(Component c) {
        return fontDesc == null ? c : c.copy().withStyle(st -> st.withFont(fontDesc));
    }

    private int width(String s) {
        return fontDesc == null ? font.width(s) : font.width(styled(Component.literal(s)));
    }

    // ---------- colours and styles ----------

    int color(@Nullable String spec) {
        if (spec == null) return 0xFF000000;
        for (int i = 0; i < 8 && spec.startsWith("$"); i++) {
            String v = var(spec.substring(1));
            spec = v == null ? "#000000" : v;
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

    private static boolean flag(JsonObject o, String k) {
        return !o.has(k) || o.get(k).getAsBoolean();
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
        if (!draw || g == null) return;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.fill(0, 0, Math.round(x1 - x0), Math.round(y1 - y0), col);
        g.pose().popMatrix();
    }

    /**
     * Custom fonts like uniform have one glyph pixel per spread unit at scale 1, so a screen scale just
     * under 1 drops whole glyph rows. Those sizes snap up to exactly one real pixel per glyph pixel.
     */
    float fs(float sc) {
        float r = renderScale * sc;
        return fontDesc != null && r >= 0.8f && r < 1 ? 1 / renderScale : sc;
    }

    float textW(Component c, float sc) {
        return font.width(styled(c)) * 2 * fs(sc);
    }

    float textW(FormattedCharSequence c, float sc) {
        return font.width(c) * 2 * fs(sc);
    }

    void text(FormattedCharSequence s, float x, float y, float sc, int col) {
        if (draw && g == null) note(s, x, y, sc);
        if (!draw || g == null) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(2 * fs(sc), 2 * fs(sc));
        g.text(font, s, 0, 0, col, false);
        g.pose().popMatrix();
    }

    void text(Component c, float x, float y, float sc, int col) {
        text(styled(c).getVisualOrderText(), x, y, sc, col);
    }

    /** Validation only: remembers where text went so overlaps can be reported. */
    private void note(FormattedCharSequence s, float x, float y, float sc) {
        StringBuilder b = new StringBuilder();
        s.accept((i, st, cp) -> {
            b.appendCodePoint(cp);
            return true;
        });
        String t = b.toString().strip();
        if (t.isEmpty()) return;
        if (t.matches("[a-z0-9_]+(\\.[a-z0-9_]+){2,}")) runtimeErrors.add("untranslated text '" + t + "'");
        boxes.add(new Box(x, y, x + textW(s, sc), y + (font.lineHeight - 1) * 2 * fs(sc), t, false));
    }

    private void checkLayout() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (int i = 0; i < boxes.size(); i++) {
            Box a = boxes.get(i);
            if (!a.icon) {
                float lo = a.x0 < SPINE ? L : R;
                if (a.x0 < lo - 6 || a.x1 > lo + CW + 6) out.add("text '" + a.what + "' runs off the page");
            }
            for (int j = i + 1; j < boxes.size(); j++) {
                Box b = boxes.get(j);
                if (a.icon && b.icon) continue;
                if (Math.min(a.x1, b.x1) - Math.max(a.x0, b.x0) > 2 && Math.min(a.y1, b.y1) - Math.max(a.y0, b.y0) > 2)
                    out.add("'" + a.what + "' overlaps '" + b.what + "'");
            }
        }
        runtimeErrors.addAll(out);
    }

    private float widest(List<FormattedCharSequence> ls, float sc) {
        float m = 0;
        for (FormattedCharSequence l : ls) m = Math.max(m, textW(l, sc));
        return m;
    }

    private void cut(Component c, int lines, int max) {
        if (draw && g == null && lines > max) runtimeErrors.add("'" + c.getString() + "' needs " + lines + " lines, only " + max + " shown");
    }

    void ctext(Component c, float cx, float y, float sc, int col) {
        text(c, cx - textW(c, sc) / 2, y, sc, col);
    }

    /** Word wrap that never splits a word, like the mockup renderer. */
    List<FormattedCharSequence> wrap(Component c, float maxw, float sc) {
        int max = Math.max(1, (int) (maxw / (2 * fs(sc))));
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String para : c.getString().split("\n")) {
            String cur = "";
            for (String w : para.split(" ")) {
                String t = cur.isEmpty() ? w : cur + " " + w;
                if (width(t) - 1 <= max || cur.isEmpty()) cur = t;
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
        if (width(line) - 1 <= max + 12) out.add(styled(Component.literal(line)).getVisualOrderText());
        else out.addAll(font.split(styled(Component.literal(line)), max));
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
        int dark = color("$slot_shadow"), light = color("$slot_light");
        fill(x, y, x + s, y + s, color("$slot"));
        fill(x, y, x + s, y + k, dark);
        fill(x, y, x + k, y + s, dark);
        fill(x, y + s - k, x + s, y + s, light);
        fill(x + s - k, y, x + s, y + s, light);
    }

    void item(ItemRef ref, float x, float y, float size) {
        ItemStack stack = ref.stack();
        if (draw && g == null) {
            if (stack.isEmpty()) runtimeErrors.add("nothing to show for " + String.join(" or ", ref.ids()));
            else boxes.add(new Box(x, y, x + size, y + size, ref.name().getString(), true));
        }
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
        if (draw && g != null) {
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
        fill(x, y, x + 26, y + 26, color("$badge"));
        int edge = color("$badge_edge");
        fill(x + 2, y + 2, x + 24, y + 3, edge);
        fill(x + 2, y + 23, x + 24, y + 24, edge);
        fill(x + 2, y + 2, x + 3, y + 24, edge);
        fill(x + 23, y + 2, x + 24, y + 24, edge);
        ctext(Component.literal(String.valueOf(n)), x + 13, y + 5, 1, color("$badge_text"));
    }

    void image(@Nullable String tex, float x, float y, float w, float h, @Nullable String label) {
        image(tex, x, y, w, h, label, true);
    }

    void image(@Nullable String tex, float x, float y, float w, float h, @Nullable String label, boolean frame) {
        int[] size = tex == null ? null : BookRegistry.imageSize(Identifier.parse(tex));
        if (size == null) {
            placeholder(x, y, w, h, Texts.of(label != null ? label : tex == null ? "?" : "Missing image: " + tex));
            return;
        }
        if (frame) {
            fill(x - 4, y - 4, x + w + 4, y + h + 4, color("$frame"));
            fill(x - 2, y - 2, x + w + 2, y + h + 2, color("$frame_inner"));
        }
        float r = Math.max(w / size[0], h / size[1]);
        float uw = w / r / size[0], vh = h / r / size[1];
        float u0 = (1 - uw) / 2, v0 = (1 - vh) / 2;
        if (draw && g != null) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(0.5f, 0.5f);
            g.blit(Identifier.parse(tex), 0, 0, Math.round(w * 2), Math.round(h * 2), u0, u0 + uw, v0, v0 + vh);
            g.pose().popMatrix();
        }
    }

    void placeholder(float x, float y, float w, float h, Component label) {
        int bg = color("$placeholder");
        fill(x, y, x + w, y + h, color("$frame"));
        fill(x + 3, y + 3, x + w - 3, y + h - 3, bg);
        int hatch = color("$placeholder_hatch");
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
        int cover = color("$cover"), edge = color("$cover_edge");
        roundRect(22, 18, W - 22, H - 18, 14, edge);
        roundRect(26, 22, W - 26, H - 22, 10, cover);
        paperHalf(true);
        paperHalf(false);
        edges();
    }

    /** One page's paper: colour, optional texture, and the shading towards the spine. */
    public void paperHalf(boolean left) {
        int paper = color("$page");
        int x0 = left ? PX0 : SPINE, x1 = left ? SPINE : PX1;
        fill(x0, PT, x1, PBOT, paper);
        String tex = var("page_texture");
        Identifier texId = tex == null ? null : Identifier.tryParse(tex);
        int[] size = texId == null ? null : BookRegistry.imageSize(texId);
        if (size != null) {
            String tile = var("page_tile");
            paper(texId, x0, x1, tile == null ? 0 : Float.parseFloat(tile), size);
        }
        int pr = paper >> 16 & 255, pg = paper >> 8 & 255, pb = paper & 255;
        for (int off = 0; off <= FADE; off++) {
            double k = Math.pow(1 - off / (double) FADE, 2.2) * 0.28;
            int c = size != null ? (int) (k * 255) << 24 : rgb((int) (pr - k * pr), (int) (pg - k * pg), (int) (pb - k * pb));
            if (left) fill(SPINE - off, PT, SPINE - off + 1, PBOT, c);
            else fill(SPINE + off, PT, SPINE + off + 1, PBOT, c);
        }
    }

    public void edges() {
        int line = color("$edge");
        fill(PX0, PT, PX1, PT + 2, line);
        fill(PX0, PBOT - 2, PX1, PBOT, line);
        fill(PX0, PT, PX0 + 2, PBOT, line);
        fill(PX1 - 2, PT, PX1, PBOT, line);
    }

    /** Page texture over one page, stretched, or tiled when tw (tile width in spread units) is set. */
    private void paper(Identifier tex, int x0, int x1, float tw, int[] size) {
        if (!draw || g == null) return;
        if (tw <= 0) {
            g.blit(tex, x0, PT, x1, PBOT, 0, 1, 0, 1);
            return;
        }
        float th = tw * size[1] / size[0];
        for (float y = PT; y < PBOT; y += th)
            for (float x = x0; x < x1; x += tw) {
                float w = Math.min(tw, x1 - x), h = Math.min(th, PBOT - y);
                g.blit(tex, Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), 0, w / tw, 0, h / th);
            }
    }

    // ---------- chapter tabs ----------

    /** Tabs on the fore edge, drawn before the frame so their roots sit under the cover. */
    public void tabs(List<Book.Tab> tabs, float mx, float my) {
        if (tabs.isEmpty()) return;
        String[] palette = String.valueOf(var("tabs")).split(",");
        int current = -1;
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).spread() <= spreadNo) current = i;
        float top = PT + 20, avail = PBOT - 20 - top, gap = 8;
        float h = Math.min(150, (avail - gap * (tabs.size() - 1)) / tabs.size());
        int edge = color("$tab_edge"), ink = color("$tab_text");
        for (int i = 0; i < tabs.size(); i++) {
            Book.Tab t = tabs.get(i);
            float y = top + i * (h + gap);
            Component label = sectionLabel(book, t.section());
            boolean hover = mx >= W - 22 && mx < W - 22 + TAB_OUT + 10 && my >= y && my < y + h;
            float out = TAB_OUT + (i == current || hover ? 10 : 0), x1 = W - 22 + out;
            int col = color(t.color() != null ? t.color() : palette[i % palette.length].trim());
            fill(TAB_ROOT, y + 2, x1 - 2, y + h - 2, edge);
            fill(TAB_ROOT, y, x1 - 4, y + 2, edge);
            fill(TAB_ROOT, y + h - 2, x1 - 4, y + h, edge);
            fill(x1 - 2, y + 4, x1, y + h - 4, edge);
            fill(TAB_ROOT, y + 2, x1 - 4, y + h - 2, col);
            fill(x1 - 4, y + 4, x1 - 2, y + h - 4, col);
            float cx = W - 22 + out / 2;
            if (t.icon() != null) item(ItemRef.parse(t.icon()), cx - 14, y + h / 2 - 14, 28);
            else if (draw && g != null) {
                String str = font.plainSubstrByWidth(label.getString(), (int) ((h - 16) / (2 * fs(1))));
                Component c = styled(Component.literal(str));
                g.pose().pushMatrix();
                g.pose().translate(cx + 8, y + (h - textW(c, 1)) / 2);
                g.pose().rotate((float) (Math.PI / 2));
                g.pose().scale(2 * fs(1), 2 * fs(1));
                g.text(font, c, 0, 0, ink, false);
                g.pose().popMatrix();
            }
            hits.add(0, new Hit(W - 22, y, x1, y + h, new Jump(t.spread(), label)));
        }
    }

    static Component sectionLabel(Book book, String sec) {
        JsonObject so = book.section(sec);
        if (so.has("title")) return Texts.of(so.get("title").getAsString());
        String key = "book." + book.id().getNamespace() + "." + book.id().getPath().replace('/', '.') + ".section." + sec;
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key)
                : Component.literal(sec.isEmpty() ? "Other" : Character.toUpperCase(sec.charAt(0)) + sec.substring(1).replace('_', ' '));
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
        stepNo = (int) num(page.json(), "first_step", 1) - 1;
        for (var e : t.slots().entrySet()) {
            try {
                slot(e.getKey(), e.getValue());
            } catch (Exception ex) {
                runtimeErrors.add("slot " + e.getKey() + ": " + ex);
            }
        }
        for (var c : cursors.entrySet()) {
            if (c.getKey().startsWith("$")) continue;
            JsonElement list = bound(page.json(), c.getKey());
            int size = list != null && list.isJsonArray() ? list.getAsJsonArray().size() : 0;
            if (c.getValue() < size) runtimeErrors.add("/" + c.getKey() + ": " + (size - c.getValue()) + " item(s) did not fit on the spread");
        }
        if (g == null) checkLayout();
        if (!runtimeErrors.isEmpty()) {
            float y = PBOT - 40 - runtimeErrors.size() * 18;
            for (String err : runtimeErrors) {
                text(Component.literal(err), R, y, 1, color("$error"));
                y += 18;
            }
        }
    }

    public List<String> arrows() {
        return arrows;
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
                JsonElement icon = slot.has("icon") ? bound(json, slot.get("icon").getAsString()) : null;
                float room = w - (icon != null ? 2 * (num(slot, "icon_size", 40) + 10) : 0), sc0 = sc;
                if (textW(c, sc) > room) sc = Math.max(sc * 0.6f, sc * room / textW(c, sc));
                float tw = textW(c, sc), cx = align.equals("center") ? x + w / 2 : x + tw / 2;
                text(c, cx - tw / 2, y + 9 * (sc0 - sc), sc, col);
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
            case "need_list", "ingredient_list", "tool_list" -> needList(slot, x, y, w, h);
            case "step_rows" -> {
                // stop above a link list on the same side when the page has links
                for (var o : page.template().slots().entrySet()) {
                    JsonObject os = o.getValue();
                    if (!o.getKey().startsWith(left ? "left." : "right.") || !str(os, "type", "").equals("link_list")) continue;
                    JsonElement lv = os.has("bind") ? bound(json, os.get("bind").getAsString()) : null;
                    if (lv != null && lv.isJsonArray() && !lv.getAsJsonArray().isEmpty()) h = Math.min(h, PT + os.getAsJsonArray("box").get(1).getAsFloat() - 10 - y);
                }
                stepRows(slot, x, y, w, h);
            }
            case "link_list" -> {
                if (val != null) linkGrid(val.getAsJsonArray(), x, y, w, (int) num(slot, "columns", 4));
            }
            case "image_box" -> {
                if (val != null) image(val.getAsString(), x, y, w, h, str(slot, "placeholder", null), flag(slot, "frame"));
            }
            case "item_icon" -> {
                if (val != null) slotItem(ItemRef.parse(val), x, y, Math.min(w, h));
            }
            case "contents" -> contents(x, y, w, h, (int) num(slot, "columns", 1));
            case "grid" -> {
                if (val != null && val.isJsonObject()) grid(val.getAsJsonObject(), x, y);
            }
            default -> {}
        }
    }

    // ---------- ingredient and tool lists ----------

    private void needList(JsonObject slot, float x, float y, float w, float h) {
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
        if (yy > y + h + 4) runtimeErrors.add("need list is " + Math.round(yy - y - h) + " taller than its box");
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
        float nw = Math.max(nameW, 40), sc = 1;
        List<FormattedCharSequence> ls = wrap(ref.name(), nw, 1);
        if (ls.size() > 2 || widest(ls, 1) > nw + 2) {
            sc = 0.8f;
            ls = wrap(ref.name(), nw, sc);
        }
        int max = sc < 1 ? 3 : 2, n = Math.min(max, ls.size());
        cut(ref.name(), ls.size(), max);
        float lh = 17 * sc, ty = y + 18 - n * lh / 2 + (n == 1 ? 1 : 0);
        for (int j = 0; j < n; j++) text(ls.get(j), x + 42, ty + j * lh, sc, color("$ink"));
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
        JsonObject prev = null;
        boolean placed = false;
        while (i < a.size()) {
            JsonObject el = a.get(i).getAsJsonObject();
            if (!BookRegistry.visible(el)) {
                i++;
                continue;
            }
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
            boolean apart = placed && el.has("join") && !el.get("join").getAsBoolean();
            boolean joined = el.has("join") ? el.get("join").getAsBoolean() : follows(prev, el);
            float gap = apart ? 16 : 0;
            if (placed && yy + gap + eh > bottom + 8) break;
            if (apart) {
                rule(x + 40, x + w - 40, yy + 4);
                yy += gap;
                prevBadge = -1;
            }
            if (g == null && numbered && prevBadge >= 0) arrows.add((joined ? "kept " : "none ") + stepTitle(prev).getString() + " / " + stepTitle(el).getString());
            if (numbered && prevBadge >= 0 && joined) arrow(x + 13, prevBadge + 32, yy - prevBadge - 36, true, 2, color("$arrow"));
            element(el, kind, x, yy, w, rowH);
            prevBadge = numbered ? yy : -1;
            prev = numbered ? el : null;
            yy += eh;
            placed = true;
            i++;
        }
        cursors.put(bind, i);
    }

    /** A step only gets an arrow from the one above when it uses what that step made. */
    private static boolean follows(@Nullable JsonObject prev, JsonObject el) {
        if (prev == null) return false;
        java.util.Set<net.minecraft.world.item.Item> made = new java.util.HashSet<>();
        if (prev.has("output")) made.addAll(refItems(prev.get("output")));
        else if (prev.has("cells") && !prev.getAsJsonArray("cells").isEmpty()) {
            JsonArray c = prev.getAsJsonArray("cells");
            made.addAll(refItems(c.get(c.size() - 1)));
        }
        if (made.isEmpty()) return false;
        java.util.Set<net.minecraft.world.item.Item> used = new java.util.HashSet<>();
        for (String k : new String[] {"inputs", "cells"})
            if (el.has(k) && el.get(k).isJsonArray()) used.addAll(refItems(el.get(k)));
        if (el.has("on")) used.addAll(refItems(el.get("on")));
        if (el.has("key") && el.get("key").isJsonObject())
            for (var e : el.getAsJsonObject("key").entrySet()) used.addAll(refItems(e.getValue()));
        used.retainAll(made);
        return !used.isEmpty();
    }

    private static java.util.Set<net.minecraft.world.item.Item> refItems(JsonElement e) {
        java.util.Set<net.minecraft.world.item.Item> out = new java.util.HashSet<>();
        if (e.isJsonArray()) e.getAsJsonArray().forEach(x -> out.addAll(refItems(x)));
        else if (e.isJsonPrimitive() || e.isJsonObject()) out.addAll(ItemRef.parse(e).items());
        return out;
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
                image(str(el, "image", null), x + (w - iw) / 2, y + 4, iw, ih, str(el, "placeholder", null), flag(el, "frame"));
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
        if (el.has("image")) image(el.get("image").getAsString(), x, y, w, h, str(el, "placeholder", null), flag(el, "frame"));
        else if (el.has("placeholder")) placeholder(x, y, w, h, Texts.of(el.get("placeholder").getAsString()));
    }

    private float rowStep(JsonObject el, String kind, float x, float y, float w, float rowH) {
        int n = ++stepNo;
        badge(x, y, n);
        float label = num(el, "label_width", 209);
        List<FormattedCharSequence> title = wrap(stepTitle(el), label - 40, 1);
        for (int j = 0; j < title.size(); j++) text(title.get(j), x + 34, y + 5 + j * 18, 1, color("$head"));
        String noteKey = switch (kind) {
            case "craft_shapeless" -> "simplebooks.shapeless";
            case "craft_shaped" -> "simplebooks.shaped";
            default -> null;
        };
        String note = str(el, "note", noteKey);
        float bottom = Math.max(y + 26, y + 8 + title.size() * 18), noteY = y + 12 + title.size() * 18;
        if (note != null) bottom = Math.max(bottom, noteY + para(Texts.of(note), x + 34, noteY, label - 34, 1, color("$soft"), 17, "left"));
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

    /** Icons joined by symbols, wrapping onto another row when the page is too narrow; returns the bottom edge. */
    private float chain(float x, float y, List<Object> cells, float maxX) {
        final float size = 36, gap = 8;
        List<Object> cs = new ArrayList<>();
        for (Object c : cells) if (c != null) cs.add(c);
        List<List<FormattedCharSequence>> labels = new ArrayList<>();
        float[] widths = new float[cs.size()];
        float word = 0;
        for (Object c : cs) if (c instanceof ItemRef ref) for (String wd : ref.name().getString().split(" ")) word = Math.max(word, textW(Component.literal(wd), 1));
        for (float lw0 : new float[]{100, 84, 68, 56, 84}) {
            float lw = Math.max(lw0, word);
            labels.clear();
            float total = -gap;
            for (int i = 0; i < cs.size(); i++) {
                Object c = cs.get(i);
                List<FormattedCharSequence> ls = c instanceof ItemRef ref ? wrap(ref.name(), lw, 1) : List.of();
                labels.add(ls);
                float cw = size;
                for (FormattedCharSequence ln : ls) cw = Math.max(cw, textW(ln, 1) - 2);
                if (c instanceof String s) cw = s.equals(">") ? 30 : Math.max(16, textW(Component.literal(s), 1));
                else if (c instanceof Component comp) cw = textW(comp, 1);
                else if (c instanceof JsonObject) cw = 3 * size;
                widths[i] = cw;
                total += cw + gap;
            }
            if (x + total <= maxX) break;
        }
        float cx = x, rowY = y, bottom = y + size;
        boolean wide = false;
        for (int i = 0; i < cs.size(); i++) {
            Object c = cs.get(i);
            float need = widths[i];
            if (!(c instanceof ItemRef) && !(c instanceof JsonObject) && i + 1 < cs.size()) need += gap + widths[i + 1];
            if (cx > x && cx + need > maxX) {
                cx = x;
                rowY = bottom + 10;
                bottom = rowY + size;
            }
            float cw = widths[i];
            if (c instanceof String s && s.equals(">")) {
                arrow(cx, rowY + size / 2, 30, false, 2, color("$arrow"));
            } else if (c instanceof String s) {
                ctext(Component.literal(s), cx + cw / 2, rowY + size / 2 - 8, 1, color("$soft"));
            } else if (c instanceof Component comp) {
                text(comp, cx, rowY + size / 2 - 8, 1, color("$soft"));
            } else if (c instanceof JsonObject shaped) {
                bottom = Math.max(bottom, grid(shaped, cx, rowY));
            } else if (c instanceof ItemRef ref) {
                slotItem(ref, cx + (cw - size) / 2, rowY, size);
                List<FormattedCharSequence> ls = labels.get(i);
                for (int j = 0; j < ls.size(); j++) text(ls.get(j), cx + cw / 2 - (textW(ls.get(j), 1) - 2) / 2, rowY + size + 4 + j * 17, 1, color("$ink"));
                bottom = Math.max(bottom, rowY + size + 4 + ls.size() * 17);
            }
            cx += cw + gap;
            wide |= cx - gap > maxX + 8;
        }
        if (draw && wide) runtimeErrors.add("step " + stepNo + ": row is wider than the page");
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

    private float bigCells(List<ItemRef> in, float x, float y, float avail) {
        if (in.isEmpty()) return y;
        final float size = 52;
        boolean mix = "result".equals(in.getLast().note());
        List<ItemRef> cells = new ArrayList<>();
        for (ItemRef r : in) {
            ItemRef p = cells.isEmpty() ? null : cells.getLast();
            // the same ingredient twice in a mix shows once with a count
            if (mix && p != null && p.note() == null && r.note() == null && p.ids().equals(r.ids()) && Objects.equals(p.label(), r.label()))
                cells.set(cells.size() - 1, new ItemRef(p.ids(), p.label(), Math.max(1, p.count()) + Math.max(1, r.count()), null));
            else cells.add(r);
        }
        float step = cells.size() > 1 ? Math.min(avail - size, 148 * (cells.size() - 1)) / (cells.size() - 1) : 0;
        float bottom = y + size, lw0 = cells.size() > 1 ? Math.max(52, Math.min(130, step - 16)) : 130, sc = 1;
        float lastRight = -1e9f;
        for (int i = 0; i < cells.size(); i++) {
            float lw = widest(wrap(cells.get(i).name(), lw0, 1), 1), cx = x + i * step, left = cx < SPINE ? L : R;
            float lx = Math.max(left, Math.min(left + CW - lw, cx + size / 2 - lw / 2));
            if (lw > step - 8 || lx < lastRight + 6) sc = 0.8f;
            lastRight = lx + lw;
        }
        for (int i = 0; i < cells.size(); i++) {
            ItemRef ref = cells.get(i);
            float cx = x + i * step;
            slotItem(ref, cx, y, size);
            List<FormattedCharSequence> ls = wrap(ref.name(), lw0, sc);
            float ty = y + size + 4;
            float left = cx < SPINE ? L : R;
            for (FormattedCharSequence ln : ls) {
                float lw = textW(ln, sc);
                text(ln, Math.max(left, Math.min(left + CW - lw, cx + size / 2 - lw / 2)), ty, sc, color("$ink"));
                ty += 17 * sc;
            }
            if (ref.note() != null) {
                Component nt = Texts.of(ref.note());
                float nw = textW(nt, sc);
                text(nt, Math.max(left, Math.min(left + CW - nw, cx + size / 2 - nw / 2)), ty, sc, color("$soft"));
                ty += 17 * sc;
            }
            bottom = Math.max(bottom, ty);
            if (i < cells.size() - 1) {
                float g0 = cx + size, g1 = cx + step, len = Math.min(42, g1 - g0 - 12);
                if (mix && i < cells.size() - 2) ctext(Component.literal("+"), (g0 + g1) / 2, y + size / 2 - 8, 1, color("$soft"));
                else arrow((g0 + g1) / 2 - len / 2, y + size / 2, len, false, 3, color("$arrow"));
            }
        }
        return bottom;
    }

    private float linkGrid(JsonArray all, float x, float y, float w, int cols) {
        JsonArray links = new JsonArray();
        for (JsonElement e : all) {
            if (!BookRegistry.visible(e)) continue;
            JsonObject l = e.getAsJsonObject();
            Identifier to = l.has("page") ? BookRegistry.resolveLink(book, l.get("page").getAsString()) : null;
            if (to == null || !book.hidden().contains(to)) links.add(e);
        }
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
            float lw = tc - (tx - lx) - 4, sc = 1;
            List<FormattedCharSequence> ls = wrap(label, lw, 1);
            if (ls.size() > 2 || widest(ls, 1) > lw + 2) {
                sc = 0.8f;
                ls = wrap(label, lw, sc);
            }
            int max = sc < 1 ? 3 : 2, n = Math.min(max, ls.size());
            cut(label, ls.size(), max);
            float lh = 17 * sc, yy = ly + 18 - n * lh / 2 + 1;
            int col = color("$link");
            Identifier target = l.has("page") ? BookRegistry.resolveLink(book, l.get("page").getAsString()) : null;
            for (int j = 0; j < n; j++) {
                float tw = textW(ls.get(j), sc), ty = yy + j * lh;
                text(ls.get(j), tx, ty, sc, col);
                fill(tx, ty + 16 * sc, tx + tw - 2, ty + 16 * sc + 2 * sc, col);
                if (target != null) hits.add(new Hit(tx, ty - 2, tx + tw, ty + 16 * sc + 2, target));
            }
            if (target != null && ref != null) hits.add(new Hit(lx, ly, lx + 36, ly + 36, target));
        }
        return rows * 42;
    }

    // ---------- contents ----------

    private int contentLines(Book.Page p, float colW) {
        String t = p.json().has("title") ? p.json().get("title").getAsString() : p.id().getPath();
        Component num = Component.literal(String.valueOf(book.indexOf(p.id()) * 2 + 1));
        return Math.min(2, wrap(Texts.of(t), colW - 24 - textW(num, 1) - 12, 1).size());
    }

    private void contents(float x, float y, float w, float h, int cols) {
        List<Object> rows = new ArrayList<>();
        String last = null;
        for (Book.Page p : book.pages()) {
            String sec = p.json().has("section") ? p.json().get("section").getAsString() : "";
            if (sec.equals("contents")) continue;
            if (p.json().has("contents") && !p.json().get("contents").getAsBoolean()) continue;
            if (!sec.equals(last)) {
                rows.add(sec);
                last = sec;
            }
            rows.add(p);
        }
        int i = cursors.getOrDefault("$contents", 0);
        float colW = w / cols;
        for (int c = 0; c < cols && i < rows.size(); c++) {
            float cx = x + c * colW, yy = y;
            while (i < rows.size()) {
                Object r = rows.get(i);
                float rh = r instanceof String ? (yy == y ? 26 : 34) : 20 * contentLines((Book.Page) r, colW);
                if (yy + rh > y + h) break;
                if (r instanceof String sec) {
                    if (yy > y) yy += 8;
                    Component label = sectionLabel(book, sec);
                    text(label, cx, yy, 1, color("$head"));
                    rule(cx, cx + colW - 16, yy + 19);
                    yy += 26;
                } else {
                    Book.Page p = (Book.Page) r;
                    String t = p.json().has("title") ? p.json().get("title").getAsString() : p.id().getPath();
                    Component label = Texts.of(t);
                    int idx = book.indexOf(p.id());
                    Component num = Component.literal(String.valueOf(idx * 2 + 1));
                    float nw = textW(num, 1), maxw = colW - 24 - nw;
                    List<FormattedCharSequence> ls = wrap(label, maxw - 12, 1);
                    cut(label, ls.size(), 2);
                    int col = p.broken() ? color("$error") : color("$link");
                    int n = Math.min(2, ls.size());
                    for (int j = 0; j < n; j++) {
                        float tw = Math.min(textW(ls.get(j), 1), maxw);
                        text(ls.get(j), cx + 8 + (j > 0 ? 12 : 0), yy + j * 20, 1, col);
                        fill(cx + 8 + (j > 0 ? 12 : 0), yy + j * 20 + 16, cx + 8 + (j > 0 ? 12 : 0) + tw - 2, yy + j * 20 + 18, col);
                    }
                    text(num, cx + colW - 16 - nw, yy + (n - 1) * 20, 1, color("$soft"));
                    hits.add(new Hit(cx, yy - 2, cx + colW - 16, yy + n * 20 - 2, p.id()));
                    yy += 20 * n;
                }
                i++;
            }
        }
        cursors.put("$contents", i);
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
