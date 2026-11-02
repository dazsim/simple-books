package com.workshopcraft.simplebooks;

import com.workshopcraft.simplebooks.registry.SimpleBooksContent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
//? if >=1.21.2 {
import net.minecraft.world.InteractionResult;
//?} else {
/*import net.minecraft.world.InteractionResultHolder;
*///?}

public class GuideItem extends Item {
    public static final String BOOK_TAG = "simplebooks:book";
    public static final String SPREAD_TAG = "simplebooks:spread";

    public GuideItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Identifier book) {
        ItemStack stack = new ItemStack(SimpleBooksContent.GUIDE);
        setBook(stack, book);
        return stack;
    }

    public static @Nullable Identifier book(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.get(SimpleBooksContent.BOOK);
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(BOOK_TAG, net.minecraft.nbt.Tag.TAG_STRING)) return null;
        return Identifier.tryParse(tag.getString(BOOK_TAG));
        *///?}
    }

    public static void setBook(ItemStack stack, Identifier book) {
        //? if >=1.20.5 {
        stack.set(SimpleBooksContent.BOOK, book);
        //?} else {
        /*stack.getOrCreateTag().putString(BOOK_TAG, book.toString());
        *///?}
    }

    public static int spread(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.getOrDefault(SimpleBooksContent.SPREAD, 0);
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getInt(SPREAD_TAG));
        *///?}
    }

    public static void setSpread(ItemStack stack, int spread) {
        //? if >=1.20.5 {
        stack.set(SimpleBooksContent.SPREAD, spread);
        //?} else {
        /*stack.getOrCreateTag().putInt(SPREAD_TAG, spread);
        *///?}
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!open(level, stack, hand)) return InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!open(level, stack, hand)) return InteractionResultHolder.pass(stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
    *///?}

    private static boolean open(Level level, ItemStack stack, InteractionHand hand) {
        Identifier book = book(stack);
        if (book == null) return false;
        if (level.isClientSide())
            SimpleBooks.opener.open(book, spread(stack), hand == InteractionHand.MAIN_HAND ? 0 : 1, null);
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier book = book(stack);
        if (book == null) return super.getName(stack);
        return Component.translatableWithFallback("book." + book.getNamespace() + "." + book.getPath().replace('/', '.') + ".title",
                super.getName(stack).getString());
    }
}
