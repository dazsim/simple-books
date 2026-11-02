package com.workshopcraft.simplebooks.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/** Save format version 1. Keyed by team, quest and task id; unknown ids are kept as they are. */
public final class QuestSave {
    public static final int VERSION = 1;

    private QuestSave() {}

    private static <V> Codec<Map<String, V>> map(Codec<V> v) {
        return Codec.unboundedMap(Codec.STRING, v);
    }

    public record Team(String name, String owner, List<String> members, Map<String, Long> joined, List<String> invites, String source) {
        public static final Codec<Team> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name", "").forGetter(Team::name),
                Codec.STRING.optionalFieldOf("owner", "").forGetter(Team::owner),
                Codec.STRING.listOf().optionalFieldOf("members", List.of()).forGetter(Team::members),
                map(Codec.LONG).optionalFieldOf("joined", Map.of()).forGetter(Team::joined),
                Codec.STRING.listOf().optionalFieldOf("invites", List.of()).forGetter(Team::invites),
                Codec.STRING.optionalFieldOf("source", "simplebooks").forGetter(Team::source)
        ).apply(i, Team::new));
    }

    public record Progress(String state, long completedAt, int cycle, long openAt, int tier, Map<String, Long> tasks,
                           Map<String, Map<String, Long>> given) {
        public static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("state", "locked").forGetter(Progress::state),
                Codec.LONG.optionalFieldOf("completed_at", 0L).forGetter(Progress::completedAt),
                Codec.INT.optionalFieldOf("cycle", 0).forGetter(Progress::cycle),
                Codec.LONG.optionalFieldOf("open_at", 0L).forGetter(Progress::openAt),
                Codec.INT.optionalFieldOf("tier", 0).forGetter(Progress::tier),
                map(Codec.LONG).optionalFieldOf("tasks", Map.of()).forGetter(Progress::tasks),
                map(map(Codec.LONG)).optionalFieldOf("given", Map.of()).forGetter(Progress::given)
        ).apply(i, Progress::new));
    }

    public record Pool(long rolledAt, List<String> offers) {
        public static final Codec<Pool> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("rolled_at", 0L).forGetter(Pool::rolledAt),
                Codec.STRING.listOf().optionalFieldOf("offers", List.of()).forGetter(Pool::offers)
        ).apply(i, Pool::new));
    }

    public record Claim(int cycle, int tier, int choice) {
        public static final Codec<Claim> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("cycle", 0).forGetter(Claim::cycle),
                Codec.INT.optionalFieldOf("tier", 0).forGetter(Claim::tier),
                Codec.INT.optionalFieldOf("choice", -1).forGetter(Claim::choice)
        ).apply(i, Claim::new));
    }

    public record Data(int version, Map<String, Team> teams, Map<String, Map<String, Progress>> progress,
                       Map<String, Map<String, String>> exclusive, Map<String, Map<String, Pool>> pools,
                       Map<String, Map<String, Claim>> claims, Map<String, List<String>> booksGiven) {
        public static final Data EMPTY = new Data(VERSION, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("version").forGetter(Data::version),
                map(Team.CODEC).optionalFieldOf("teams", Map.of()).forGetter(Data::teams),
                map(map(Progress.CODEC)).optionalFieldOf("progress", Map.of()).forGetter(Data::progress),
                map(map(Codec.STRING)).optionalFieldOf("exclusive", Map.of()).forGetter(Data::exclusive),
                map(map(Pool.CODEC)).optionalFieldOf("pools", Map.of()).forGetter(Data::pools),
                map(map(Claim.CODEC)).optionalFieldOf("claims", Map.of()).forGetter(Data::claims),
                map(Codec.STRING.listOf()).optionalFieldOf("books_given", Map.of()).forGetter(Data::booksGiven)
        ).apply(i, Data::new));
    }
}
