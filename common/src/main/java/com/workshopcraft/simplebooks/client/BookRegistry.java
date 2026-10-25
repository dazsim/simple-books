package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Loads books, pages and templates from assets/NS/simplebooks/ on every client resource reload. */
public class BookRegistry extends SimplePreparableReloadListener<BookRegistry.Loaded> {
    public static final Identifier ID = SimpleBooks.id("books");
    public static final Identifier DEFAULT_TEMPLATE = SimpleBooks.id("recipe");
    static final Set<String> STEP_KINDS = Set.of("craft_shapeless", "craft_shaped", "process", "assembly", "sequence",
            "text", "links", "image", "arrow", "page_break");
    private static final Set<String> ITEM_KEYS = Set.of("inputs", "output", "appliance", "on", "tool", "ingredients", "tools",
            "item", "items", "icon", "right_icon");

    public record Loaded(Map<Identifier, Book> books, Map<Identifier, int[]> images) {}

    private static Loaded loaded = new Loaded(Map.of(), Map.of());

    public static @Nullable Book get(Identifier id) {
        return loaded.books.get(id);
    }

    public static Set<Identifier> ids() {
        return loaded.books.keySet();
    }

    public static int @Nullable [] imageSize(Identifier tex) {
        return loaded.images.get(tex);
    }

    static void warn(String msg) {
        SimpleBooks.LOG.warn(msg);
    }

    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, JsonObject> rawTemplates = new HashMap<>();
        Map<Identifier, String> templateErrors = new HashMap<>();
        for (var e : manager.listResources("simplebooks/templates", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier id = strip(e.getKey(), "simplebooks/templates/");
            try {
                rawTemplates.put(id, read(e.getValue()).getAsJsonObject());
            } catch (Exception ex) {
                templateErrors.put(id, "template " + id + " (" + e.getKey() + "): " + ex.getMessage());
            }
        }
        Map<Identifier, Template> templates = new HashMap<>();

        Map<Identifier, JsonObject> bookJson = new TreeMap<>();
        Map<Identifier, String> bookErrors = new HashMap<>();
        Map<Identifier, Map<String, Resource>> pageFiles = new HashMap<>();
        for (var e : manager.listResources("simplebooks/books", id -> id.getPath().endsWith(".json")).entrySet()) {
            String rel = e.getKey().getPath().substring("simplebooks/books/".length());
            int slash = rel.indexOf('/');
            if (slash < 0) {
                Identifier id = Identifier.fromNamespaceAndPath(e.getKey().getNamespace(), rel.substring(0, rel.length() - 5));
                try {
                    bookJson.put(id, read(e.getValue()).getAsJsonObject());
                } catch (Exception ex) {
                    bookJson.put(id, new JsonObject());
                    bookErrors.put(id, e.getKey() + ": " + ex.getMessage());
                }
            } else if (rel.startsWith("/pages/", slash)) {
                Identifier book = Identifier.fromNamespaceAndPath(e.getKey().getNamespace(), rel.substring(0, slash));
                String page = rel.substring(slash + 7, rel.length() - 5);
                pageFiles.computeIfAbsent(book, k -> new TreeMap<>()).put(page, e.getValue());
            }
        }

        Map<Identifier, int[]> images = new HashMap<>();
        Map<Identifier, Book> books = new LinkedHashMap<>();
        for (var be : bookJson.entrySet()) {
            Identifier bookId = be.getKey();
            JsonObject json = be.getValue();
            if (!visible(json)) continue;
            List<String> bErrors = new ArrayList<>();
            if (bookErrors.containsKey(bookId)) bErrors.add(bookErrors.get(bookId));
            Map<String, String> vars = new HashMap<>();
            if (json.has("style") && json.get("style").isJsonObject())
                for (var v : json.getAsJsonObject("style").entrySet()) if (v.getValue().isJsonPrimitive()) vars.put(v.getKey(), v.getValue().getAsString());
            String defTemplate = json.has("default_template") ? json.get("default_template").getAsString() : DEFAULT_TEMPLATE.toString();
            texture(vars.get("page_texture"), "book " + bookId + " /style/page_texture", manager, images);
            List<String> sections = new ArrayList<>();
            Set<String> hiddenSections = new java.util.HashSet<>();
            if (json.has("sections")) json.getAsJsonArray("sections").forEach(e -> {
                sections.add(Book.sectionName(e));
                if (!visible(e)) hiddenSections.add(Book.sectionName(e));
            });
            Set<Identifier> hidden = new java.util.HashSet<>();

            List<Book.Page> pages = new ArrayList<>();
            for (var pe : pageFiles.getOrDefault(bookId, Map.of()).entrySet()) {
                Identifier pageId = Identifier.fromNamespaceAndPath(bookId.getNamespace(), bookId.getPath() + "/" + pe.getKey());
                String file = "assets/" + bookId.getNamespace() + "/simplebooks/books/" + bookId.getPath() + "/pages/" + pe.getKey() + ".json";
                List<String> errors = new ArrayList<>();
                JsonObject pj;
                try {
                    pj = read(pe.getValue()).getAsJsonObject();
                } catch (Exception ex) {
                    errors.add("bad JSON: " + String.valueOf(ex.getMessage()).replaceAll("\\s*See https\\S*", "").replace("com.google.gson.stream.MalformedJsonException: ", ""));
                    pages.add(new Book.Page(pageId, file, new JsonObject(), null, errors));
                    continue;
                }
                if (!visible(pj) || hiddenSections.contains(pj.has("section") ? pj.get("section").getAsString() : "")) {
                    hidden.add(pageId);
                    continue;
                }
                Identifier tid = Identifier.tryParse(pj.has("template") ? pj.get("template").getAsString() : defTemplate);
                Template t = null;
                if (tid == null) errors.add("/template: bad id");
                else {
                    if (templateErrors.containsKey(tid)) errors.add(templateErrors.get(tid));
                    else {
                        t = templates.computeIfAbsent(tid, k -> safeResolve(k, rawTemplates));
                        errors.addAll(t.errors());
                        texture(t.vars().get("page_texture"), "template " + tid + " /vars/page_texture", manager, images);
                    }
                }
                try {
                    if (t != null && errors.isEmpty()) checkPage(pj, t, errors, manager, images);
                    if (pj.has("style") && pj.get("style").isJsonObject() && pj.getAsJsonObject("style").has("page_texture"))
                        texture(pj.getAsJsonObject("style").get("page_texture").getAsString(), file + " /style/page_texture", manager, images);
                } catch (Exception ex) {
                    errors.add("bad page: " + ex.getMessage());
                }
                pages.add(new Book.Page(pageId, file, pj, t, errors));
            }
            if (sections.contains("contents")) {
                JsonObject cj = new JsonObject();
                cj.addProperty("template", "simplebooks:index");
                cj.addProperty("section", "contents");
                cj.addProperty("title", json.has("contents_title") ? json.get("contents_title").getAsString() : "simplebooks.contents");
                Template ct = templates.computeIfAbsent(SimpleBooks.id("index"), k -> safeResolve(k, rawTemplates));
                pages.add(new Book.Page(Identifier.fromNamespaceAndPath(bookId.getNamespace(), bookId.getPath() + "/contents"),
                        "(generated contents)", cj, ct, new ArrayList<>(ct.errors())));
            }
            pages.sort(Comparator.comparingInt((Book.Page p) -> {
                        int i = sections.indexOf(p.json().has("section") ? p.json().get("section").getAsString() : "");
                        return i < 0 ? sections.size() : i;
                    })
                    .thenComparingInt(p -> p.json().has("sort") ? p.json().get("sort").getAsInt() : 0)
                    .thenComparing(p -> p.id().toString()));
            Book book = new Book(bookId, json, vars, pages, bErrors, hidden);
            for (Book.Page p : pages) if (!p.broken()) checkLinks(book, p);
            for (Book.Page p : pages) for (String err : p.errors()) SimpleBooks.LOG.error("{} {}", p.file(), err);
            books.put(bookId, book);
        }
        SimpleBooks.LOG.info("Loaded {} book(s), {} template(s)", books.size(), rawTemplates.size());
        return new Loaded(books, images);
    }

    @Override
    protected void apply(Loaded data, ResourceManager manager, ProfilerFiller profiler) {
        loaded = data;
    }

    private static Template safeResolve(Identifier id, Map<Identifier, JsonObject> raw) {
        try {
            return Template.resolve(id, raw);
        } catch (Exception ex) {
            return new Template(id, Map.of(), Map.of(), Map.of(), List.of("template " + id + ": " + ex));
        }
    }

    private static void checkPage(JsonObject page, Template t, List<String> errors, ResourceManager manager, Map<Identifier, int[]> images) {
        for (var e : t.slots().entrySet()) {
            JsonObject slot = e.getValue();
            if (!slot.has("bind") || !slot.get("bind").isJsonPrimitive()) continue;
            boolean optional = slot.has("optional") && slot.get("optional").getAsBoolean();
            if (!optional && Spread.bound(page, slot.get("bind").getAsString()) == null)
                errors.add("/" + slot.get("bind").getAsString().replace("|", " or /") + ": missing, needed by slot " + e.getKey());
        }
        walk(page, "", errors, manager, images);
    }

    private static void walk(JsonElement el, String ptr, List<String> errors, ResourceManager manager, Map<Identifier, int[]> images) {
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) walk(a.get(i), ptr + "/" + i, errors, manager, images);
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        if (o.has("kind") && !STEP_KINDS.contains(o.get("kind").getAsString()))
            errors.add(ptr + "/kind: unknown step kind '" + o.get("kind").getAsString() + "'");
        for (var e : o.entrySet()) {
            String p = ptr + "/" + e.getKey();
            if (ITEM_KEYS.contains(e.getKey())) items(e.getValue(), p);
            if (e.getValue().isJsonPrimitive() && (e.getKey().equals("image") || e.getKey().endsWith("_image")
                    || e.getValue().getAsString().endsWith(".png"))) {
                Identifier tex = Identifier.tryParse(e.getValue().getAsString());
                if (tex == null) errors.add(p + ": bad texture id");
                else if (!images.containsKey(tex)) {
                    int[] size = pngSize(manager, tex);
                    if (size == null) warn("missing image " + tex + " at " + p);
                    else images.put(tex, size);
                }
            }
            walk(e.getValue(), p, errors, manager, images);
        }
    }

    private static void items(JsonElement e, String ptr) {
        if (e.isJsonArray()) {
            for (int i = 0; i < e.getAsJsonArray().size(); i++) items(e.getAsJsonArray().get(i), ptr + "/" + i);
        } else if (e.isJsonPrimitive()) {
            if (!ItemRef.known(e.getAsString())) warn("unknown item " + e.getAsString() + " at " + ptr);
        }
    }

    /** True when every mod in the element's "requires" (a mod id or a list of them) is loaded. */
    public static boolean visible(JsonElement el) {
        if (!el.isJsonObject() || !el.getAsJsonObject().has("requires")) return true;
        JsonElement r = el.getAsJsonObject().get("requires");
        if (r.isJsonPrimitive()) return SimpleBooks.modLoaded.test(r.getAsString());
        for (JsonElement m : r.getAsJsonArray()) if (!SimpleBooks.modLoaded.test(m.getAsString())) return false;
        return true;
    }

    private static void texture(@Nullable String tex, String where, ResourceManager manager, Map<Identifier, int[]> images) {
        if (tex == null) return;
        Identifier id = Identifier.tryParse(tex);
        if (id == null) warn(where + ": bad texture id " + tex);
        else if (!images.containsKey(id)) {
            int[] size = pngSize(manager, id);
            if (size == null) warn(where + ": missing texture " + id);
            else images.put(id, size);
        }
    }

    private static void checkLinks(Book book, Book.Page page) {
        List<String> missing = new ArrayList<>();
        collectLinks(page.json(), "", book, missing);
        page.errors().addAll(missing);
    }

    private static void collectLinks(JsonElement el, String ptr, Book book, List<String> out) {
        if (el.isJsonArray()) {
            for (int i = 0; i < el.getAsJsonArray().size(); i++) collectLinks(el.getAsJsonArray().get(i), ptr + "/" + i, book, out);
        } else if (el.isJsonObject() && visible(el)) {
            for (var e : el.getAsJsonObject().entrySet()) {
                if (e.getKey().equals("page") && e.getValue().isJsonPrimitive()) {
                    Identifier target = resolveLink(book, e.getValue().getAsString());
                    if (target == null || book.indexOf(target) < 0 && !book.hidden().contains(target)) out.add(ptr + "/page: link to missing page '" + e.getValue().getAsString() + "'");
                } else collectLinks(e.getValue(), ptr + "/" + e.getKey(), book, out);
            }
        }
    }

    public static @Nullable Identifier resolveLink(Book book, String target) {
        if (target.contains(":")) return Identifier.tryParse(target);
        return Identifier.tryParse(book.id().getNamespace() + ":" + book.id().getPath() + "/" + target);
    }

    private static int @Nullable [] pngSize(ResourceManager manager, Identifier tex) {
        var res = manager.getResource(tex);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) {
            byte[] h = in.readNBytes(24);
            if (h.length < 24) return null;
            int w = ((h[16] & 255) << 24) | ((h[17] & 255) << 16) | ((h[18] & 255) << 8) | (h[19] & 255);
            int ht = ((h[20] & 255) << 24) | ((h[21] & 255) << 16) | ((h[22] & 255) << 8) | (h[23] & 255);
            return new int[]{w, ht};
        } catch (Exception ex) {
            return null;
        }
    }

    private static Identifier strip(Identifier file, String prefix) {
        String p = file.getPath();
        return Identifier.fromNamespaceAndPath(file.getNamespace(), p.substring(prefix.length(), p.length() - 5));
    }

    private static JsonElement read(Resource r) throws Exception {
        try (Reader reader = r.openAsReader()) {
            return JsonParser.parseReader(reader);
        }
    }
}
