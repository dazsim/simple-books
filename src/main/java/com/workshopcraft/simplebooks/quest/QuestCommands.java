package com.workshopcraft.simplebooks.quest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.workshopcraft.simplebooks.SimpleBooks;
import com.workshopcraft.simplebooks.SimpleBooksConfig;
import com.workshopcraft.simplebooks.quest.QuestDefs.Book;
import com.workshopcraft.simplebooks.quest.QuestDefs.Quest;
import com.workshopcraft.simplebooks.quest.QuestWorld.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /sbteam for players and the team and progress parts of /sbquests. */
public final class QuestCommands {
    private QuestCommands() {}

    private static final SuggestionProvider<CommandSourceStack> QUESTS = (c, b) -> {
        List<String> ids = new ArrayList<>();
        for (Book bk : QuestManager.defs().books().values()) ids.addAll(bk.quests().keySet());
        return SharedSuggestionProvider.suggest(ids, b);
    };
    private static final SuggestionProvider<CommandSourceStack> SCOPES = (c, b) -> {
        List<String> ids = new ArrayList<>();
        for (Book bk : QuestManager.defs().books().values()) {
            ids.add(bk.id());
            ids.addAll(bk.chapters().keySet());
            ids.addAll(bk.quests().keySet());
        }
        return SharedSuggestionProvider.suggest(ids, b);
    };
    private static final SuggestionProvider<CommandSourceStack> BOOKS =
            (c, b) -> SharedSuggestionProvider.suggest(QuestManager.defs().books().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> PLAYERS =
            (c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b);
    private static final SuggestionProvider<CommandSourceStack> TEAMS = (c, b) -> {
        List<String> names = new ArrayList<>();
        for (Team t : QuestEngine.world().teams.values()) if (!t.name.isEmpty()) names.add(t.name);
        names.addAll(c.getSource().getOnlinePlayerNames());
        return SharedSuggestionProvider.suggest(names, b);
    };

    public static void registerTeam(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("sbteam")
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> reply(c, QuestEngine.create(player(c), StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("invite").then(Commands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
                        .executes(c -> reply(c, QuestEngine.invite(player(c), StringArgumentType.getString(c, "player"))))))
                .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word()).suggests(TEAMS)
                        .executes(c -> reply(c, QuestEngine.join(player(c), StringArgumentType.getString(c, "name"), false)))
                        .then(Commands.literal("confirm")
                                .executes(c -> reply(c, QuestEngine.join(player(c), StringArgumentType.getString(c, "name"), true))))))
                .then(Commands.literal("leave").executes(c -> reply(c, QuestEngine.leave(player(c)))))
                .then(Commands.literal("kick").then(Commands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
                        .executes(c -> reply(c, QuestEngine.kick(player(c), StringArgumentType.getString(c, "player"))))))
                .then(Commands.literal("owner").then(Commands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
                        .executes(c -> reply(c, QuestEngine.owner(player(c), StringArgumentType.getString(c, "player"))))))
                .then(Commands.literal("list").executes(c -> {
                    List<String> l = QuestEngine.list();
                    lines(c, l.isEmpty() ? List.of("No teams yet") : l);
                    return l.size();
                }))
                .then(Commands.literal("info").executes(c -> {
                    lines(c, QuestEngine.info(player(c)));
                    return 1;
                })));
    }

    /** The /sbquests subcommands beyond validate. */
    public static void addAdmin(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("complete").then(Commands.argument("who", StringArgumentType.word()).suggests(TEAMS)
                        .then(Commands.argument("quest", IdentifierArgument.id()).suggests(QUESTS)
                                .executes(c -> each(c, "Completed", (t, q) -> QuestEngine.adminComplete(t, q.id()))))))
                .then(Commands.literal("uncomplete").then(Commands.argument("who", StringArgumentType.word()).suggests(TEAMS)
                        .then(Commands.argument("quest", IdentifierArgument.id()).suggests(QUESTS)
                                .executes(c -> each(c, "Uncompleted", (t, q) -> QuestEngine.adminUncomplete(t, q.id()))))))
                .then(Commands.literal("reset").then(Commands.argument("who", StringArgumentType.word()).suggests(TEAMS)
                        .executes(c -> resetAll(c))
                        .then(Commands.argument("quest", IdentifierArgument.id()).suggests(SCOPES)
                                .executes(c -> each(c, "Reset", (t, q) -> QuestEngine.adminReset(t, q.id()))))))
                .then(Commands.literal("claims").then(Commands.literal("reset").then(Commands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
                        .executes(c -> claims(c, null))
                        .then(Commands.argument("quest", IdentifierArgument.id()).suggests(QUESTS)
                                .executes(c -> claims(c, IdentifierArgument.getId(c, "quest").toString()))))))
                .then(Commands.literal("progress").then(Commands.argument("who", StringArgumentType.word()).suggests(TEAMS)
                        .executes(QuestCommands::progress)
                        .then(Commands.argument("quest", IdentifierArgument.id()).suggests(QUESTS)
                                .then(Commands.argument("task", StringArgumentType.word())
                                        .then(Commands.argument("value", LongArgumentType.longArg(0))
                                                .executes(QuestCommands::setProgress))))))
                .then(Commands.literal("prune").executes(c -> {
                    int n = QuestEngine.prune();
                    ok(c, "Pruned " + n + " entries for quests that no longer exist");
                    return n;
                }))
                .then(Commands.literal("open").then(Commands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
                        .then(Commands.argument("book", IdentifierArgument.id()).suggests(BOOKS)
                                .executes(c -> open(c, 0))
                                .then(Commands.argument("spread", IntegerArgumentType.integer(0))
                                        .executes(c -> open(c, IntegerArgumentType.getInteger(c, "spread")))))))
                .then(Commands.literal("team").then(Commands.literal("owner")
                        .then(Commands.argument("who", StringArgumentType.word()).suggests(TEAMS)
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
                                        .executes(c -> {
                                            Team t = team(c);
                                            if (t == null) return 0;
                                            return reply(c, QuestEngine.setOwner(t, StringArgumentType.getString(c, "player")));
                                        })))))
                .then(dev())
                .then(Commands.literal("reload-config").executes(c -> {
                    SimpleBooksConfig cfg = SimpleBooksConfig.load();
                    ok(c, "Config: team_source " + cfg.teamSource() + ", first_join_book " + (cfg.firstJoinBook().isEmpty() ? "off" : cfg.firstJoinBook())
                            + ", command_reward_permission " + cfg.commandRewardPermission());
                    return 1;
                }));
    }

    private static int devAction(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String text) {
        ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c, "name"));
        if (p == null) return 0;
        new QuestActionPacket(StringArgumentType.getString(c, "action"), StringArgumentType.getString(c, "quest"),
                StringArgumentType.getString(c, "task"), IntegerArgumentType.getInteger(c, "number"), text).handle(p);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dev() {
        return Commands.literal("dev").requires(s -> SimpleBooks.dev)
                .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> {
                            ok(c, QuestDevBots.join(c.getSource().getServer(), StringArgumentType.getString(c, "name")));
                            return 1;
                        })))
                .then(Commands.literal("leave").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> {
                            ok(c, QuestDevBots.leave(c.getSource().getServer(), StringArgumentType.getString(c, "name")));
                            return 1;
                        })))
                .then(Commands.literal("got").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("kind", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c, "name"));
                            if (p == null) return 0;
                            List<String> l = QuestDevBots.received(p, StringArgumentType.getString(c, "kind"));
                            lines(c, l.isEmpty() ? List.of("nothing") : l.subList(Math.max(0, l.size() - 3), l.size()));
                            return l.size();
                        }))))
                .then(Commands.literal("action").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("action", StringArgumentType.word())
                                .then(Commands.argument("quest", StringArgumentType.string())
                                        .then(Commands.argument("task", StringArgumentType.string())
                                                .then(Commands.argument("number", IntegerArgumentType.integer())
                                                        .executes(c -> devAction(c, ""))
                                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                                .executes(c -> devAction(c, StringArgumentType.getString(c, "text"))))))))))
                .then(Commands.literal("use").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos()).executes(c -> {
                            ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c, "name"));
                            if (p == null) return 0;
                            com.workshopcraft.simplebooks.box.HandInBoxBlock.use(p, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(c, "pos"));
                            return 1;
                        }))))
                .then(Commands.literal("stat").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("type", IdentifierArgument.id()).then(Commands.argument("value", IdentifierArgument.id())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(c -> {
                                    ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c, "name"));
                                    if (p == null) return 0;
                                    var st = QuestMc.stat(IdentifierArgument.getId(c, "type").toString(), IdentifierArgument.getId(c, "value").toString());
                                    if (st == null) return 0;
                                    p.awardStat(st, IntegerArgumentType.getInteger(c, "amount"));
                                    ok(c, "Awarded " + st.getName());
                                    return 1;
                                }))))));
    }

    interface Op {
        boolean apply(Team t, Quest q);
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    private static int reply(CommandContext<CommandSourceStack> c, QuestEngine.Result r) {
        if (r.ok()) ok(c, r.message());
        else c.getSource().sendFailure(Component.literal(r.message()));
        return r.ok() ? 1 : 0;
    }

    private static void ok(CommandContext<CommandSourceStack> c, String m) {
        c.getSource().sendSuccess(() -> Component.literal(m), false);
    }

    private static void lines(CommandContext<CommandSourceStack> c, List<String> l) {
        for (String s : l) ok(c, s);
    }

    /** A team name, or a player name meaning that player's team. */
    private static @Nullable Team team(CommandContext<CommandSourceStack> c) {
        String who = StringArgumentType.getString(c, "who");
        MinecraftServer server = c.getSource().getServer();
        QuestWorld w = QuestEngine.world();
        Team t = w.byName(who);
        if (t == null) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(who);
            if (p != null) t = QuestEngine.team(p);
            else {
                UUID id = QuestMc.byName(server, who);
                if (id != null) t = w.teamOf(id.toString());
            }
        }
        if (t == null) c.getSource().sendFailure(Component.literal("No team or player " + who));
        return t;
    }

    private static String label(Team t) {
        return t.name.isEmpty() ? QuestEngine.name(t.owner) : t.name;
    }

    private static int each(CommandContext<CommandSourceStack> c, String verb, Op op) {
        Team t = team(c);
        if (t == null) return 0;
        String ref = IdentifierArgument.getId(c, "quest").toString();
        int n = 0;
        for (Book b : QuestManager.defs().books().values())
            for (Quest q : b.quests().values())
                if (q.id().equals(ref) || ref.equals(q.chapter()) || ref.equals(b.id())) if (op.apply(t, q)) n++;
        if (n == 0) {
            c.getSource().sendFailure(Component.literal("Nothing to change for " + ref));
            return 0;
        }
        ok(c, verb + " " + n + " quest" + (n == 1 ? "" : "s") + " for " + label(t));
        return n;
    }

    private static int resetAll(CommandContext<CommandSourceStack> c) {
        Team t = team(c);
        if (t == null) return 0;
        int n = 0;
        for (String q : new ArrayList<>(QuestEngine.world().progress(t.id).keySet())) if (QuestEngine.adminReset(t, q)) n++;
        ok(c, "Reset " + n + " quests for " + label(t));
        return n;
    }

    private static int claims(CommandContext<CommandSourceStack> c, @Nullable String quest) {
        String who = StringArgumentType.getString(c, "player");
        UUID id = QuestMc.byName(c.getSource().getServer(), who);
        if (id == null) {
            c.getSource().sendFailure(Component.literal("No player " + who));
            return 0;
        }
        QuestEngine.adminResetClaims(id.toString(), quest);
        ok(c, "Reset " + (quest == null ? "all claims" : "the claim for " + quest) + " of " + who);
        return 1;
    }

    private static int progress(CommandContext<CommandSourceStack> c) {
        Team t = team(c);
        if (t == null) return 0;
        ok(c, "Team " + label(t) + ", " + t.members.size() + " member" + (t.members.size() == 1 ? "" : "s"));
        int n = 0;
        for (Book b : QuestManager.defs().books().values())
            for (Quest q : b.quests().values()) {
                String s = QuestEngine.status(t, b, q);
                if (s.equals("hidden") || s.equals("locked")) continue;
                QuestWorld.Prog p = QuestEngine.world().progress(t.id).get(q.id());
                StringBuilder line = new StringBuilder(q.id() + " " + s);
                if (p != null && !s.equals("complete"))
                    for (QuestDefs.Task task : q.tasks())
                        line.append(" ").append(task.id()).append("=").append(p.count(task.id())).append("/").append(QuestEngine.target(q, task));
                String text = line.toString();
                c.getSource().sendSuccess(() -> Component.literal(text).withStyle(s.equals("complete") ? ChatFormatting.GREEN : ChatFormatting.WHITE), false);
                n++;
            }
        for (Book b : QuestManager.defs().books().values())
            for (QuestDefs.Chapter ch : b.chapters().values()) {
                String s = QuestEngine.chapterStatus(t, b, ch);
                if (!s.equals("open")) c.getSource().sendSuccess(() -> Component.literal("chapter " + ch.id() + " " + s).withStyle(ChatFormatting.GRAY), false);
            }
        return n;
    }

    private static int setProgress(CommandContext<CommandSourceStack> c) {
        Team t = team(c);
        if (t == null) return 0;
        String quest = IdentifierArgument.getId(c, "quest").toString(), task = StringArgumentType.getString(c, "task");
        long v = LongArgumentType.getLong(c, "value");
        QuestEngine.Ref r = QuestEngine.find(quest);
        if (r == null || r.quest().tasks().stream().noneMatch(x -> x.id().equals(task))) {
            c.getSource().sendFailure(Component.literal("No task " + task + " in " + quest));
            return 0;
        }
        QuestEngine.adminProgress(t, quest, task, v);
        ok(c, "Set " + quest + " " + task + " to " + v + " for " + label(t));
        return 1;
    }

    private static int open(CommandContext<CommandSourceStack> c, int spread) {
        String who = StringArgumentType.getString(c, "player");
        ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayerByName(who);
        if (p == null) {
            c.getSource().sendFailure(Component.literal(who + " is not online"));
            return 0;
        }
        String book = IdentifierArgument.getId(c, "book").toString();
        QuestEngine.openBook(p, book, spread);
        ok(c, "Opened " + book + " for " + who);
        return 1;
    }
}
