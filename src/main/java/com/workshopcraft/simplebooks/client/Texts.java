package com.workshopcraft.simplebooks.client;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public final class Texts {
    private Texts() {}

    /** A lang key if one exists, otherwise the literal text. */
    public static Component of(String s) {
        return Language.getInstance().has(s) ? Component.translatable(s) : Component.literal(s);
    }
}
