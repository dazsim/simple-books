package com.workshopcraft.simplebooks.quest;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The live, changeable form of the save. Uuids are kept as strings, like the file. */
public final class QuestWorld {
    public static final class Team {
        public final String id;
        public String name;
        public String owner;
        public final List<String> members = new ArrayList<>();
        public final Map<String, Long> joined = new LinkedHashMap<>();
        public final Set<String> invites = new LinkedHashSet<>();
        public String source;

        Team(String id, String name, String owner, String source) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.source = source;
        }

        public boolean solo() {
            return name.isEmpty() && !id.startsWith("scoreboard:");
        }
    }

    public static final class Prog {
        public String state;
        public long completedAt, openAt;
        public int cycle, tier;
        public final Map<String, Long> tasks = new LinkedHashMap<>();
        public final Map<String, Map<String, Long>> given = new LinkedHashMap<>();

        Prog(String state) {
            this.state = state;
        }

        Prog copy() {
            Prog p = new Prog(state);
            p.completedAt = completedAt;
            p.openAt = openAt;
            p.cycle = cycle;
            p.tier = tier;
            p.tasks.putAll(tasks);
            given.forEach((k, v) -> p.given.put(k, new LinkedHashMap<>(v)));
            return p;
        }

        public long count(String task) {
            return tasks.getOrDefault(task, 0L);
        }

        public boolean complete() {
            return state.equals("complete");
        }
    }

    public final Map<String, Team> teams = new LinkedHashMap<>();
    public final Map<String, Map<String, Prog>> progress = new HashMap<>();
    public final Map<String, Map<String, String>> exclusive = new HashMap<>();
    public final Map<String, Map<String, QuestSave.Pool>> pools = new HashMap<>();
    public final Map<String, Map<String, QuestSave.Claim>> claims = new HashMap<>();
    public final Map<String, Set<String>> booksGiven = new HashMap<>();
    private final Map<String, String> byMember = new HashMap<>();

    public static QuestWorld of(QuestSave.Data d) {
        QuestWorld w = new QuestWorld();
        d.teams().forEach((id, t) -> {
            Team n = new Team(id, t.name(), t.owner(), t.source());
            n.members.addAll(t.members());
            n.joined.putAll(t.joined());
            n.invites.addAll(t.invites());
            w.teams.put(id, n);
        });
        d.progress().forEach((team, qs) -> {
            Map<String, Prog> m = w.progress(team);
            qs.forEach((q, p) -> {
                Prog n = new Prog(p.state());
                n.completedAt = p.completedAt();
                n.openAt = p.openAt();
                n.cycle = p.cycle();
                n.tier = p.tier();
                n.tasks.putAll(p.tasks());
                p.given().forEach((k, v) -> n.given.put(k, new LinkedHashMap<>(v)));
                m.put(q, n);
            });
        });
        d.exclusive().forEach((k, v) -> w.exclusive.put(k, new HashMap<>(v)));
        d.pools().forEach((k, v) -> w.pools.put(k, new HashMap<>(v)));
        d.claims().forEach((k, v) -> w.claims.put(k, new LinkedHashMap<>(v)));
        d.booksGiven().forEach((k, v) -> w.booksGiven.put(k, new LinkedHashSet<>(v)));
        w.reindex();
        return w;
    }

    public QuestSave.Data save() {
        Map<String, QuestSave.Team> t = new LinkedHashMap<>();
        teams.forEach((id, n) -> t.put(id, new QuestSave.Team(n.name, n.owner, List.copyOf(n.members), Map.copyOf(n.joined),
                List.copyOf(n.invites), n.source)));
        Map<String, Map<String, QuestSave.Progress>> p = new LinkedHashMap<>();
        progress.forEach((team, qs) -> {
            if (qs.isEmpty()) return;
            Map<String, QuestSave.Progress> m = new LinkedHashMap<>();
            qs.forEach((q, x) -> {
                Map<String, Map<String, Long>> g = new LinkedHashMap<>();
                x.given.forEach((k, v) -> g.put(k, Map.copyOf(v)));
                m.put(q, new QuestSave.Progress(x.state, x.completedAt, x.cycle, x.openAt, x.tier, Map.copyOf(x.tasks), g));
            });
            p.put(team, m);
        });
        Map<String, Map<String, String>> ex = new LinkedHashMap<>();
        exclusive.forEach((k, v) -> ex.put(k, Map.copyOf(v)));
        Map<String, Map<String, QuestSave.Pool>> po = new LinkedHashMap<>();
        pools.forEach((k, v) -> po.put(k, Map.copyOf(v)));
        Map<String, Map<String, QuestSave.Claim>> c = new LinkedHashMap<>();
        claims.forEach((k, v) -> {
            if (!v.isEmpty()) c.put(k, Map.copyOf(v));
        });
        Map<String, List<String>> b = new LinkedHashMap<>();
        booksGiven.forEach((k, v) -> b.put(k, List.copyOf(v)));
        return new QuestSave.Data(QuestSave.VERSION, t, p, ex, po, c, b);
    }

    void reindex() {
        byMember.clear();
        for (Team t : teams.values()) for (String m : t.members) byMember.put(m, t.id);
    }

    public @Nullable Team teamOf(String member) {
        String id = byMember.get(member);
        return id == null ? null : teams.get(id);
    }

    public @Nullable Team byName(String name) {
        for (Team t : teams.values()) if (!t.name.isEmpty() && t.name.equalsIgnoreCase(name)) return t;
        return null;
    }

    public Map<String, Prog> progress(String team) {
        return progress.computeIfAbsent(team, k -> new HashMap<>());
    }

    public Map<String, QuestSave.Claim> claims(String player) {
        return claims.computeIfAbsent(player, k -> new LinkedHashMap<>());
    }

    Team newTeam(String id, String name, String owner, String source) {
        Team t = new Team(id, name, owner, source);
        teams.put(id, t);
        return t;
    }

    void add(Team t, String member, long now) {
        Team old = teamOf(member);
        if (old != null && old != t) remove(old, member);
        if (!t.members.contains(member)) t.members.add(member);
        t.joined.put(member, now);
        t.invites.remove(member);
        if (t.owner.isEmpty()) t.owner = member;
        byMember.put(member, t.id);
    }

    /** Takes the member out; the team is deleted when empty, and a new owner picked if needed. */
    void remove(Team t, String member) {
        t.members.remove(member);
        t.joined.remove(member);
        if (byMember.get(member) != null && byMember.get(member).equals(t.id)) byMember.remove(member);
        if (t.members.isEmpty()) {
            teams.remove(t.id);
            progress.remove(t.id);
            exclusive.remove(t.id);
            pools.remove(t.id);
            return;
        }
        if (t.owner.equals(member)) {
            String next = t.members.get(0);
            for (String m : t.members) if (t.joined.getOrDefault(m, Long.MAX_VALUE) < t.joined.getOrDefault(next, Long.MAX_VALUE)) next = m;
            t.owner = next;
        }
    }

    /** A new team of one for the member, holding a copy of what from has now. */
    Team splitOff(Team from, String member, long now, String source) {
        Team n = newTeam(UUID.randomUUID().toString(), "", member, source);
        Map<String, Prog> dst = progress(n.id);
        progress(from.id).forEach((k, v) -> dst.put(k, v.copy()));
        Map<String, String> ex = exclusive.get(from.id);
        if (ex != null) exclusive.put(n.id, new HashMap<>(ex));
        Map<String, QuestSave.Pool> po = pools.get(from.id);
        if (po != null) pools.put(n.id, new HashMap<>(po));
        add(n, member, now);
        return n;
    }
}
