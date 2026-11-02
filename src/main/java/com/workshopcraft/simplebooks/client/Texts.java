package com.workshopcraft.simplebooks.client;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class Texts {
    private Texts() {}

    /** A lang key if one exists, otherwise the literal text. */
    public static Component of(String s) {
        return Language.getInstance().has(s) ? Component.translatable(s) : Component.literal(s);
    }

    /** A style that uses the given font. */
    public static Style font(Identifier id) {
        //? if >=26.1 {
        return Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(id));
        //?} else {
        /*return Style.EMPTY.withFont(id);
        *///?}
    }
}
