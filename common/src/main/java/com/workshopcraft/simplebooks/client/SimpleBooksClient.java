package com.workshopcraft.simplebooks.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.workshopcraft.simplebooks.SimpleBooks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class SimpleBooksClient {
    private SimpleBooksClient() {}

    public static void init() {
        SimpleBooks.opener = BookScreen::open;
    }

    /** /simplebooks open|validate|list, built for any client command source type. */
    public static <S> LiteralArgumentBuilder<S> command() {
        SuggestionProvider<S> books = (ctx, b) -> SharedSuggestionProvider.suggest(BookRegistry.ids().stream().map(Identifier::toString), b);
        return LiteralArgumentBuilder.<S>literal("simplebooks")
                .then(LiteralArgumentBuilder.<S>literal("open")
                        .then(RequiredArgumentBuilder.<S, String>argument("book", StringArgumentType.greedyString()).suggests(books)
                                .executes(ctx -> open(StringArgumentType.getString(ctx, "book")))))
                .then(LiteralArgumentBuilder.<S>literal("validate")
                        .then(RequiredArgumentBuilder.<S, String>argument("book", StringArgumentType.greedyString()).suggests(books)
                                .executes(ctx -> validate(StringArgumentType.getString(ctx, "book")))))
                .then(LiteralArgumentBuilder.<S>literal("list").executes(ctx -> {
                    say(Component.literal(BookRegistry.ids().size() + " book(s): " + String.join(", ", BookRegistry.ids().stream().map(Identifier::toString).toList())));
                    return 1;
                }));
    }

    private static int open(String arg) {
        String[] parts = arg.trim().split(" ");
        Identifier id = Identifier.tryParse(parts[0]);
        if (parts.length == 1 && (id == null || BookRegistry.get(id) == null) && parts[0].lastIndexOf('/') > parts[0].indexOf(':')) {
            int cut = parts[0].lastIndexOf('/');
            parts = new String[] {parts[0].substring(0, cut), parts[0].substring(cut + 1)};
            id = Identifier.tryParse(parts[0]);
        }
        if (id == null || BookRegistry.get(id) == null) {
            say(Component.translatable("simplebooks.unknown_book", parts[0]).withStyle(ChatFormatting.RED));
            return 0;
        }
        Identifier page = parts.length > 1 ? BookRegistry.resolveLink(BookRegistry.get(id), parts[1]) : null;
        BookScreen.open(id, page);
        return 1;
    }

    private static int validate(String arg) {
        Identifier id = Identifier.tryParse(arg.trim());
        Book b = id == null ? null : BookRegistry.get(id);
        if (b == null) {
            say(Component.translatable("simplebooks.unknown_book", arg).withStyle(ChatFormatting.RED));
            return 0;
        }
        int bad = 0;
        for (String e : b.errors()) say(Component.literal(e).withStyle(ChatFormatting.RED));
        var font = Minecraft.getInstance().font;
        Flow f = b.errors().isEmpty() ? b.flow() : null;
        int last = f != null ? f.pages() - 1 : b.pages().size() * 2 - 1;
        java.util.Map<Book.Page, Float> room = new java.util.HashMap<>();
        for (int i = 0; i < b.pages().size(); i++) {
            Book.Page p = b.pages().get(i);
            List<String> errs = new ArrayList<>(p.errors());
            if (!p.broken() && b.errors().isEmpty()) {
                List<Spread> parts = new ArrayList<>();
                if (f == null) parts.add(new Spread(null, font, b, p, i));
                else {
                    errs.addAll(f.problems(p));
                    for (int h = f.pageOf(p.id()); h >= 0 && f.half(h) != null && f.half(h).page() == p; h++)
                        parts.add(new Spread(null, font, b, p, h / 2, f.half(h), h % 2 == 1, h + 1));
                }
                for (Spread s : parts) {
                    s.content();
                    errs.addAll(s.runtimeErrors());
                    for (String c : s.pageChecks()) if (!c.startsWith("page " + (last + 1) + " ")) errs.add(c);
                    for (String a : s.arrows()) com.mojang.logging.LogUtils.getLogger().info("[simplebooks arrows] {} {}", p.file(), a);
                }
                if (!parts.isEmpty()) room.put(p, parts.getLast().roomLeft());
                // steps carried on in another file while this one's last page still had room for them
                if (i > 0 && p.json().has("first_step") && room.containsKey(b.pages().get(i - 1))) {
                    float first = new Spread(null, font, b, p, i).firstStepHeight();
                    if (first > 0 && room.get(b.pages().get(i - 1)) >= first)
                        errs.add("its first step would fit at the end of " + b.pages().get(i - 1).id().getPath());
                }
            }
            for (String e : errs) {
                say(Component.literal(p.file() + " " + e).withStyle(ChatFormatting.RED));
                bad++;
            }
        }
        String count = f != null ? f.pages() + " page(s) on " + f.spreads() + " spread(s), " : b.pages().size() + " page(s), ";
        say(Component.literal(count + (bad + b.errors().size()) + " problem(s)"));
        return 1;
    }

    private static void say(Component c) {
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(c);
    }
}
