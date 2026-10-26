package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A loaded book. {@code hidden} holds pages left out because a mod they require is missing. */
public record Book(Identifier id, JsonObject json, Map<String, String> vars, List<Page> pages, List<String> errors,
                   Set<Identifier> hidden, Flow.Cache cache) {
    public Book(Identifier id, JsonObject json, Map<String, String> vars, List<Page> pages, List<String> errors, Set<Identifier> hidden) {
        this(id, json, vars, pages, errors, hidden, new Flow.Cache());
    }

    public record Page(Identifier id, String file, JsonObject json, Template template, List<String> errors) {
        public boolean broken() {
            return !errors.isEmpty();
        }

        public String section() {
            return json.has("section") ? json.get("section").getAsString() : "";
        }
    }

    /** One chapter tab: the section, its first spread and its colour (null for the book's palette). */
    public record Tab(String section, int spread, @org.jspecify.annotations.Nullable String color, @org.jspecify.annotations.Nullable JsonElement icon) {}

    /** Position in the page file list. */
    public int pageIndex(Identifier page) {
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).id().equals(page)) return i;
        return -1;
    }

    /** True when the book asks for page flow: short pages share a spread and long ones run on. */
    public boolean flows() {
        return json.has("flow") && json.get("flow").getAsBoolean();
    }

    public @org.jspecify.annotations.Nullable Flow flow() {
        return flows() ? cache.get(this) : null;
    }

    public int spreadCount() {
        Flow f = flow();
        return f != null ? f.spreads() : pages.size();
    }

    /** The spread a page file starts on. */
    public int indexOf(Identifier page) {
        Flow f = flow();
        return f != null ? f.spreadOf(page) : pageIndex(page);
    }

    /** The printed number of the first page a file is on. */
    public int pageNumber(Identifier page) {
        Flow f = flow();
        return f != null ? f.pageOf(page) + 1 : pageIndex(page) * 2 + 1;
    }

    /** The file shown on the left of a spread, for the book stand. */
    public @org.jspecify.annotations.Nullable Page pageAt(int spread) {
        Flow f = flow();
        if (f == null) return pages.isEmpty() ? null : pages.get(Math.max(0, Math.min(spread, pages.size() - 1)));
        Flow.Half h = f.half(Math.max(0, Math.min(spread, f.spreads() - 1)) * 2);
        return h != null ? h.page() : pages.isEmpty() ? null : pages.getFirst();
    }

    public int contentsIndex() {
        return indexOf(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "/contents"));
    }

    /** Section names in order; entries may be a name or an object with "id". */
    public List<String> sections() {
        List<String> out = new ArrayList<>();
        if (json.has("sections")) json.getAsJsonArray("sections").forEach(e -> out.add(sectionName(e)));
        return out;
    }

    public JsonObject section(String name) {
        if (json.has("sections")) for (JsonElement e : json.getAsJsonArray("sections"))
            if (e.isJsonObject() && sectionName(e).equals(name)) return e.getAsJsonObject();
        return new JsonObject();
    }

    static String sectionName(JsonElement e) {
        if (!e.isJsonObject()) return e.getAsString();
        JsonObject o = e.getAsJsonObject();
        return o.has("id") ? o.get("id").getAsString() : o.has("name") ? o.get("name").getAsString() : "";
    }

    public List<Tab> tabs() {
        List<Tab> out = new ArrayList<>();
        if (!json.has("tabs") || !json.get("tabs").getAsBoolean()) return out;
        for (String sec : sections()) {
            if (sec.equals("contents")) continue;
            JsonObject so = section(sec);
            if (so.has("tab") && !so.get("tab").getAsBoolean()) continue;
            int at = -1;
            for (Page p : pages) {
                if (!p.section().equals(sec)) continue;
                int i = indexOf(p.id());
                if (i >= 0 && (at < 0 || i < at)) at = i;
                if (!flows()) break;
            }
            if (at >= 0) out.add(new Tab(sec, at, so.has("color") ? so.get("color").getAsString() : null, so.get("icon")));
        }
        return out;
    }

    public String title() {
        return json.has("title") ? json.get("title").getAsString() : id.toString();
    }
}
