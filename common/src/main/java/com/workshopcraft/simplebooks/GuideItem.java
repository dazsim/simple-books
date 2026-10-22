package com.workshopcraft.simplebooks;

import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GuideItem extends Item {
    public GuideItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Identifier book) {
        ItemStack stack = new ItemStack(SimpleBooksContent.GUIDE);
        stack.set(SimpleBooksContent.BOOK, book);
        return stack;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Identifier book = player.getItemInHand(hand).get(SimpleBooksContent.BOOK);
        if (book == null) return InteractionResult.PASS;
        if (level.isClientSide()) SimpleBooks.opener.accept(book);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier book = stack.get(SimpleBooksContent.BOOK);
        if (book == null) return super.getName(stack);
        return Component.translatableWithFallback("book." + book.getNamespace() + "." + book.getPath().replace('/', '.') + ".title",
                super.getName(stack).getString());
    }
}
