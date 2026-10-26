package com.workshopcraft.simplebooks.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Page flow for books with "flow": true. Each page file takes as many single pages as its content needs:
 * the template's left side first, then its right side again for every page after that. A file that fits
 * on one page shares a spread with its neighbour, and links go at the bottom of the file's last page.
 */
public final class Flow {
    private static final int MAX_PARTS = 8;
    private static final Set<String> OWN_CONTENT = Set.of("text", "image_box", "item_icon", "grid", "need_list",
            "ingredient_list", "tool_list", "contents");

    /** One page of a file: which template side it uses, where its steps start, and whether it is the file's last page. */
    public record Half(Book.Page page, boolean rightSide, int part, boolean last, Map<String, Integer> cursors, int stepNo) {}

    /** Holds a book's flow once it has been worked out; a reload makes new books, so it never goes stale. */
    public static final class Cache {
        private @Nullable Flow flow, rough;
        private boolean building;

        /**
         * While measuring, page numbers come from the previous attempt (file order on the first one),
         * so the contents is measured again until the numbers settle.
         */
        @Nullable Flow get(Book book) {
            if (flow == null && !building) {
                building = true;
                try {
                    Font font = Minecraft.getInstance().font;
                    Flow f = new Flow(book, font);
                    for (int n = 0; n < 4; n++) {
                        rough = f;
                        Flow again = new Flow(book, font);
                        boolean same = again.first.equals(f.first) && again.halves.size() == f.halves.size();
                        f = again;
                        if (same) break;
                    }
                    flow = f;
                } finally {
                    building = false;
                    rough = null;
                }
            }
            return building ? rough : flow;
        }
    }

    private final List<@Nullable Half> halves = new ArrayList<>();
    private final Map<Identifier, Integer> first = new HashMap<>();
    private final Map<Book.Page, List<String>> problems = new HashMap<>();

    private Flow(Book book, Font font) {
        List<Book.Page> pages = book.pages();
        List<List<Half>> parts = new ArrayList<>();
        for (Book.Page p : pages) parts.add(parts(book, p, font));
        boolean[] used = new boolean[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            if (used[i]) continue;
            // a file that needs a spread starts on a left page; a later one-page file of the same section fills the gap
            if (parts.get(i).size() > 1 && halves.size() % 2 == 1) {
                for (int j = i + 1; j < pages.size() && pages.get(j).section().equals(pages.get(i).section()); j++) {
                    if (used[j] || parts.get(j).size() != 1) continue;
                    place(parts.get(j));
                    used[j] = true;
                    break;
                }
            }
            place(parts.get(i));
            used[i] = true;
        }
        if (halves.size() % 2 == 1) halves.add(null);
    }

    private void place(List<Half> ps) {
        if (ps.isEmpty()) return;
        first.put(ps.getFirst().page().id(), halves.size());
        halves.addAll(ps);
    }

    private List<Half> parts(Book book, Book.Page p, Font font) {
        List<Half> out = new ArrayList<>();
        Map<String, Integer> cur = Map.of();
        int stepNo = (int) (p.json().has("first_step") ? p.json().get("first_step").getAsFloat() : 1) - 1;
        if (p.broken() || p.template() == null) {
            out.add(new Half(p, false, 0, false, cur, stepNo));
            out.add(new Half(p, true, 1, true, cur, stepNo));
            return out;
        }
        boolean needRight = rightContent(p);
        for (int part = 0; part < MAX_PARTS; part++) {
            boolean right = part > 0;
            if (!(part == 0 && needRight)) {
                Half last = new Half(p, right, part, true, cur, stepNo);
                Spread m = new Spread(null, font, book, p, 0, last, right, 0);
                m.measure();
                if (m.finished()) {
                    out.add(last);
                    return out;
                }
            }
            Half mid = new Half(p, right, part, false, cur, stepNo);
            Spread m = new Spread(null, font, book, p, 0, mid, right, 0);
            m.measure();
            out.add(mid);
            if (m.cursorState().equals(cur) && part > 0) {
                problems.computeIfAbsent(p, k -> new ArrayList<>()).add("a step is too tall to fit on a page");
                break;
            }
            cur = m.cursorState();
            stepNo = m.stepCount();
        }
        if (out.size() >= MAX_PARTS) problems.computeIfAbsent(p, k -> new ArrayList<>()).add("needs more than " + MAX_PARTS + " pages");
        Half end = out.removeLast();
        out.add(new Half(end.page(), end.rightSide(), end.part(), true, end.cursors(), end.stepNo()));
        return out;
    }

    /** True when the template's right side has something of its own, beyond steps carried over and links. */
    private static boolean rightContent(Book.Page p) {
        for (var e : p.template().slots().entrySet()) {
            if (!e.getKey().startsWith("right.")) continue;
            JsonObject s = e.getValue();
            String type = s.has("type") ? s.get("type").getAsString() : "";
            if (!OWN_CONTENT.contains(type)) continue;
            if (type.equals("contents") || type.endsWith("_list")) return true;
            if (s.has("bind") && Spread.bound(p.json(), s.get("bind").getAsString()) != null) return true;
        }
        return false;
    }

    public int spreads() {
        return Math.max(1, halves.size() / 2);
    }

    public int pages() {
        int n = halves.size();
        while (n > 0 && halves.get(n - 1) == null) n--;
        return n;
    }

    /** The page at this position (0 is the first left page), or null for an empty page. */
    public @Nullable Half half(int index) {
        return index >= 0 && index < halves.size() ? halves.get(index) : null;
    }

    public int pageOf(Identifier page) {
        return first.getOrDefault(page, -1);
    }

    public int spreadOf(Identifier page) {
        int i = pageOf(page);
        return i < 0 ? -1 : i / 2;
    }

    public List<String> problems(Book.Page p) {
        return problems.getOrDefault(p, List.of());
    }
}
