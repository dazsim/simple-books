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
        for (int i = 0; i < b.pages().size(); i++) {
            Book.Page p = b.pages().get(i);
            List<String> errs = new ArrayList<>(p.errors());
            if (!p.broken() && b.errors().isEmpty()) {
                Spread s = new Spread(null, Minecraft.getInstance().font, b, p, i);
                s.content();
                errs.addAll(s.runtimeErrors());
                for (String a : s.arrows()) com.mojang.logging.LogUtils.getLogger().info("[simplebooks arrows] {} {}", p.file(), a);
            }
            for (String e : errs) {
                say(Component.literal(p.file() + " " + e).withStyle(ChatFormatting.RED));
                bad++;
            }
        }
        say(Component.literal(b.pages().size() + " page(s), " + (bad + b.errors().size()) + " problem(s)"));
        return 1;
    }

    private static void say(Component c) {
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(c);
    }
}
