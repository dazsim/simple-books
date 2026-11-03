package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.quest.QuestClientStore;
import com.workshopcraft.simplebooks.quest.QuestDefs;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds quest pages to books: a checklist and one detail spread per quest for each "quests" section,
 * and a team spread for "team_page". Books with quests but no book file get a default book.
 * Rebuilt whenever the server sends new definitions or progress.
 */
final class QuestBook {
    static final Identifier LIST = SimpleBooks.id("quest_list"), DETAIL = SimpleBooks.id("quest"), TEAM = SimpleBooks.id("team"),
            TREE = SimpleBooks.id("quest_tree"), STORY = SimpleBooks.id("quest_story");

    private static final Map<Identifier, Book> cache = new HashMap<>();
    private static final Set<Identifier> none = new HashSet<>();
    private static int version = -1;
    private static @Nullable Object source;

    private QuestBook() {}

    static Set<Identifier> ids() {
        Set<Identifier> out = new HashSet<>();
        for (String id : QuestClientStore.defs().books().keySet()) {
            Identifier i = Identifier.tryParse(id);
            if (i != null) out.add(i);
        }
        return out;
    }

    static synchronized @Nullable Book get(Identifier id, @Nullable Book base, Object loaded) {
        int v = QuestClientStore.version();
        if (v != version || loaded != source) {
            cache.clear();
            none.clear();
            version = v;
            source = loaded;
        }
        if (none.contains(id)) return base;
        Book b = cache.get(id);
        if (b != null) return b;
        try {
            b = build(id, base);
        } catch (Exception ex) {
            SimpleBooks.LOG.error("Could not build the quest pages of {}", id, ex);
            b = null;
        }
        if (b == null) {
            none.add(id);
            return base;
        }
        cache.put(id, b);
        return b;
    }

    static String status(String quest) {
        JsonObject q = quest(quest);
        return q != null && q.has("s") ? q.get("s").getAsString() : "locked";
    }

    static @Nullable JsonObject quest(String quest) {
        JsonObject st = QuestClientStore.state();
        if (!st.has("quests")) return null;
        JsonElement e = st.getAsJsonObject("quests").get(quest);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static @Nullable String chapterId(Identifier book, String ref) {
        return ref.contains(":") ? ref : book + "/" + ref;
    }

    /** The chapter's quests a player can see, in checklist order. */
    static List<QuestDefs.Quest> visible(QuestDefs.Book qb, String chapter) {
        List<QuestDefs.Quest> out = new ArrayList<>();
        for (QuestDefs.Quest q : qb.quests().values())
            if (chapter.equals(q.chapter()) && !status(q.id()).equals("hidden")) out.add(q);
        out.sort(Comparator.comparingInt(QuestDefs.Quest::sort).thenComparing(QuestDefs.Quest::id));
        return out;
    }

    /** {done, total} over the quests a player can see in a chapter, or null. */
    static int @Nullable [] progress(String chapter) {
        for (QuestDefs.Book qb : QuestClientStore.defs().books().values()) {
            if (!qb.chapters().containsKey(chapter)) continue;
            int done = 0, total = 0;
            for (QuestDefs.Quest q : visible(qb, chapter)) {
                if (q.optional()) continue;
                total++;
                if (status(q.id()).equals("complete")) done++;
            }
            return new int[]{done, total};
        }
        return null;
    }

    /** Rows the quest_list slots of a template hold on one spread. */
    static int rowsPerSpread(Template t) {
        int n = 0;
        for (JsonObject sl : t.slots().values())
            if (sl.has("type") && sl.get("type").getAsString().equals("quest_list"))
                n += Math.max(1, (int) (sl.getAsJsonArray("box").get(3).getAsFloat() / QuestSlots.rowHeight(sl)));
        return Math.max(1, n);
    }

    private static @Nullable Book build(Identifier id, @Nullable Book base) {
        QuestDefs.Book qb = QuestClientStore.defs().books().get(id.toString());
        if (base == null && qb == null) return null;
        JsonObject json = base != null ? base.json() : defaultJson(id, qb);
        boolean any = false;
        if (json.has("sections")) for (JsonElement e : json.getAsJsonArray("sections"))
            if (e.isJsonObject() && (e.getAsJsonObject().has("quests") || e.getAsJsonObject().has("team_page"))) any = true;
        if (!any) return null;
        List<Book.Page> pages = base != null ? new ArrayList<>(base.pages()) : new ArrayList<>();
        List<String> sections = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("sections")) sections.add(Book.sectionName(e));
        if (base == null && sections.contains("contents"))
            pages.add(BookRegistry.contentsPage(id, json, BookRegistry.template(SimpleBooks.id("index"))));
        String prefix = id.getPath() + "/quest/";
        for (JsonElement e : json.getAsJsonArray("sections")) {
            if (!e.isJsonObject() || !BookRegistry.visible(e)) continue;
            JsonObject so = e.getAsJsonObject();
            String sec = Book.sectionName(e);
            if (so.has("quests")) {
                String cid = chapterId(id, so.get("quests").getAsString());
                QuestDefs.Chapter ch = qb == null ? null : qb.chapters().get(cid);
                if (ch == null) {
                    JsonObject pj = new JsonObject();
                    pj.addProperty("section", sec);
                    pages.add(new Book.Page(SimpleBooks.of(id.getNamespace(), prefix + sec), "(quests " + cid + ")", pj, null,
                            List.of(qb == null ? "no quests loaded for this book" : "no chapter " + cid)));
                    continue;
                }
                String cst = chapterStatus(cid);
                if (cst.equals("hidden")) continue;
                Template lt = BookRegistry.template(LIST);
                Template dt = BookRegistry.template(DETAIL);
                List<QuestDefs.Quest> qs = visible(qb, cid);
                if (cst.equals("locked")) {
                    JsonObject pj = listPage(sec, ch, cid, 0);
                    pj.addProperty("locked", true);
                    pages.add(new Book.Page(SimpleBooks.of(id.getNamespace(), prefix + sec + "/list0"), "(quest list " + cid + ")",
                            pj, lt, new ArrayList<>(lt.errors())));
                    qs = List.of();
                } else if (ch.layout().equals("story")) {
                    story(pages, sec, ch, qs);
                    qs = List.of();
                } else if (ch.layout().equals("tree")) {
                    Template tt = BookRegistry.template(TREE);
                    JsonObject pj = listPage(sec, ch, cid, 0);
                    pj.addProperty("template", TREE.toString());
                    pages.add(new Book.Page(SimpleBooks.of(id.getNamespace(), prefix + sec + "/tree"), "(quest tree " + cid + ")",
                            pj, tt, new ArrayList<>(tt.errors())));
                }
                int per = rowsPerSpread(lt), parts = Math.max(1, (qs.size() + per - 1) / per);
                boolean listed = cst.equals("open") && !ch.layout().equals("tree") && !ch.layout().equals("story");
                for (int p = 0; p < parts && listed; p++) {
                    JsonObject pj = listPage(sec, ch, cid, p);
                    pj.addProperty("from", p * per);
                    pages.add(new Book.Page(SimpleBooks.of(id.getNamespace(), prefix + sec + "/list" + p), "(quest list " + cid + ")",
                            pj, lt, new ArrayList<>(lt.errors())));
                }
                for (int i = 0; i < qs.size(); i++) {
                    QuestDefs.Quest q = qs.get(i);
                    JsonObject pj = new JsonObject();
                    if (q.page() != null && !status(q.id()).equals("locked"))
                        for (var pe : q.page().entrySet())
                            if (!Set.of("template", "section", "sort", "contents", "quest", "chapter", "id", "title", "right_title").contains(pe.getKey())) pj.add(pe.getKey(), pe.getValue());
                    pj.addProperty("template", DETAIL.toString());
                    pj.addProperty("section", sec);
                    pj.addProperty("title", q.title());
                    if (!q.subtitle().isEmpty()) pj.addProperty("subtitle", q.subtitle());
                    if (!q.icon().isEmpty()) pj.addProperty("icon", q.icon());
                    if (!q.text().isEmpty() && !pj.has("text")) pj.addProperty("text", q.text());
                    pj.addProperty("right_title", "simplebooks.quest.rewards");
                    pj.addProperty("quest", q.id());
                    pj.addProperty("sort", -90000 + i);
                    pj.addProperty("contents", false);
                    pages.add(new Book.Page(detailId(q.id()), "(quest " + q.id() + ")", pj, dt, new ArrayList<>(dt.errors())));
                }
            }
            if (so.has("team_page") && so.get("team_page").getAsBoolean()) {
                Template tt = BookRegistry.template(TEAM);
                JsonObject pj = new JsonObject();
                pj.addProperty("template", TEAM.toString());
                pj.addProperty("section", sec);
                pj.addProperty("title", "simplebooks.team.title");
                pj.addProperty("right_title", "simplebooks.team.players");
                pj.addProperty("sort", -80000);
                pages.add(new Book.Page(SimpleBooks.of(id.getNamespace(), prefix + sec + "/team"), "(team)", pj, tt, new ArrayList<>(tt.errors())));
            }
        }
        BookRegistry.sortPages(pages, sections);
        return new Book(id, json, base != null ? base.vars() : new HashMap<>(), pages,
                base != null ? base.errors() : List.of(), base != null ? base.hidden() : Set.of());
    }

    private static JsonObject listPage(String sec, QuestDefs.Chapter ch, String cid, int p) {
        JsonObject pj = new JsonObject();
        pj.addProperty("template", LIST.toString());
        pj.addProperty("section", sec);
        pj.addProperty("title", ch.title().isEmpty() ? "simplebooks.quest.list" : ch.title());
        if (!ch.icon().isEmpty()) pj.addProperty("icon", ch.icon());
        if (!ch.text().isEmpty() && p == 0) pj.addProperty("text", ch.text());
        pj.addProperty("chapter", cid);
        pj.addProperty("sort", -100000 + p);
        if (p > 0) pj.addProperty("contents", false);
        return pj;
    }

    /** One spread per quest; a locked one keeps its page content back. */
    private static void story(List<Book.Page> pages, String sec, QuestDefs.Chapter ch, List<QuestDefs.Quest> qs) {
        Template t = BookRegistry.template(STORY);
        for (int i = 0; i < qs.size(); i++) {
            QuestDefs.Quest q = qs.get(i);
            JsonObject pj = new JsonObject();
            boolean open = !status(q.id()).equals("locked");
            if (open && q.page() != null)
                for (var e : q.page().entrySet())
                    if (!Set.of("template", "section", "sort", "contents", "quest", "chapter", "id").contains(e.getKey())) pj.add(e.getKey(), e.getValue());
            pj.addProperty("template", STORY.toString());
            pj.addProperty("section", sec);
            pj.addProperty("title", q.title());
            if (!q.subtitle().isEmpty()) pj.addProperty("subtitle", q.subtitle());
            if (!q.icon().isEmpty()) pj.addProperty("icon", q.icon());
            if (open && !q.text().isEmpty() && !pj.has("text")) pj.addProperty("text", q.text());
            if (!open) pj.remove("text");
            pj.addProperty("part", net.minecraft.network.chat.Component.translatable("simplebooks.quest.part", i + 1, qs.size()).getString());
            pj.addProperty("rewards_head", "simplebooks.quest.rewards");
            pj.addProperty("quest", q.id());
            pj.addProperty("chapter", ch.id());
            pj.addProperty("sort", -100000 + i);
            if (i == 0) pj.addProperty("contents_title", ch.title().isEmpty() ? q.title() : ch.title());
            else pj.addProperty("contents", false);
            pages.add(new Book.Page(detailId(q.id()), "(story " + q.id() + ")", pj, t, new ArrayList<>(t.errors())));
        }
    }

    /** "open", "locked" or "hidden"; open until the server says otherwise. */
    static String chapterStatus(String chapter) {
        JsonObject st = QuestClientStore.state();
        if (!st.has("chapters")) return "open";
        JsonElement e = st.getAsJsonObject("chapters").get(chapter);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "open";
    }

    static Identifier detailId(String quest) {
        Identifier q = Identifier.tryParse(quest);
        return q == null ? SimpleBooks.id("quest/unknown") : SimpleBooks.of(q.getNamespace(), "quest/" + q.getPath());
    }

    private static JsonObject defaultJson(Identifier id, QuestDefs.Book qb) {
        JsonObject o = new JsonObject();
        String key = "book." + id.getNamespace() + "." + id.getPath().replace('/', '.') + ".title";
        String name = id.getPath().substring(id.getPath().lastIndexOf('/') + 1).replace('_', ' ');
        o.addProperty("title", net.minecraft.locale.Language.getInstance().has(key) ? key
                : name.isEmpty() ? id.toString() : Character.toUpperCase(name.charAt(0)) + name.substring(1));
        o.addProperty("tabs", true);
        JsonArray secs = new JsonArray();
        secs.add("contents");
        List<QuestDefs.Chapter> chs = new ArrayList<>(qb.chapters().values());
        chs.sort(Comparator.comparingInt(QuestDefs.Chapter::sort).thenComparing(QuestDefs.Chapter::id));
        String pre = id + "/";
        for (QuestDefs.Chapter c : chs) {
            JsonObject s = new JsonObject();
            String shortId = c.id().startsWith(pre) ? c.id().substring(pre.length()) : c.id();
            s.addProperty("id", shortId.replace(':', '_'));
            s.addProperty("quests", c.id().startsWith(pre) ? shortId : c.id());
            s.addProperty("title", c.title());
            if (!c.icon().isEmpty()) s.addProperty("icon", c.icon());
            secs.add(s);
        }
        JsonObject team = new JsonObject();
        team.addProperty("id", "team");
        team.addProperty("team_page", true);
        team.addProperty("title", "simplebooks.team.title");
        team.addProperty("icon", "minecraft:player_head");
        secs.add(team);
        o.add("sections", secs);
        return o;
    }
}
