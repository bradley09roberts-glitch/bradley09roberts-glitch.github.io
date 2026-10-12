package com.grandline.player;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Captain's Handbook: a written book every new character starts with. Pages are translation keys (so they read in
 * the player's language) and name the player's own key bindings through keybind components.
 */
public final class Handbook {
    public static final int PAGES = 11;

    public static ItemStack create() {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 1; i <= PAGES; i++) pages.add(Filterable.passThrough(page(i)));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(Component.translatable("grandline.handbook.title").getString()), "Grand Line Odyssey", 0, pages, true));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("grandline.handbook.title"));
        return book;
    }

    private static Component page(int i) {
        String k = "grandline.handbook.p" + i;
        return switch (i) {
            case 2 -> Component.translatable(k, key("combat_mode"), key("next_set"), key("dodge"), key("lock_on"));
            case 3 -> Component.translatable(k, key("observation"), key("armament"), key("conqueror"));
            case 4 -> Component.translatable(k, key("transform"));
            case 5 -> Component.translatable(k, key("anchor"), key("coup_de_burst"));
            case 6 -> Component.translatable(k, key("chart"));
            case 9 -> Component.translatable(k, key("character"));
            default -> Component.translatable(k);
        };
    }

    private static Component key(String id) {
        return Component.keybind("key.grandline." + id).withStyle(net.minecraft.ChatFormatting.DARK_BLUE);
    }

    public static void give(ServerPlayer sp) {
        if (!sp.getInventory().add(create())) sp.drop(create(), false);
    }

    private Handbook() {}
}
