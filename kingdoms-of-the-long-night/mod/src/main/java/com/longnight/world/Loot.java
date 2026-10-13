package com.longnight.world;

import com.longnight.content.Content;
import com.longnight.registry.ModItems;
import com.longnight.sim.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Written lore and the things left behind in ruins. */
public final class Loot {
    private Loot() {}

    public static ItemStack book(Content.LoreBook b) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = new ArrayList<>();
        for (String p : b.pages()) pages.add(Filterable.passThrough(Component.literal(p)));
        String title = b.title().length() > 32 ? b.title().substring(0, 32) : b.title();
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), b.author(), 1, pages, true));
        return stack;
    }

    /** A random lore book suited to a place; rarer texts are rarer. */
    public static ItemStack randomBook(String culture, Random r, boolean ruin) {
        List<Content.LoreBook> pool = new ArrayList<>();
        for (Content.LoreBook b : Content.LORE.values()) {
            boolean fits = b.cultures().isEmpty() || b.cultures().contains(culture);
            if (!fits && r.nextFloat() > 0.2f) continue;
            if (b.rarity().equals("starter")) continue;
            int weight = switch (b.rarity()) {
                case "common" -> 6;
                case "uncommon" -> 3;
                case "rare" -> ruin ? 3 : 1;
                case "forbidden" -> ruin ? 2 : 0;
                default -> 1;
            };
            for (int i = 0; i < weight; i++) pool.add(b);
        }
        if (pool.isEmpty()) return ItemStack.EMPTY;
        return book(pool.get(r.nextInt(pool.size())));
    }

    public static void ruinChest(ServerLevel level, BlockPos p, Settlement s, Random r) {
        if (!Places.ready(level, p)) return;
        level.setBlock(p, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
            int slot = 0;
            for (int i = 0; i < 2; i++) {
                ItemStack book = randomBook(s.culture, r, true);
                if (!book.isEmpty()) chest.setItem(slot++, book);
            }
            chest.setItem(slot++, new ItemStack(ModItems.SILVER_MARK.get(), 2 + r.nextInt(6)));
            if (r.nextFloat() < 0.35f) chest.setItem(slot++, new ItemStack(ModItems.NIGHT_SHARD.get()));
            if (r.nextFloat() < 0.5f) chest.setItem(slot++, new ItemStack(ModItems.WARD_CHALK.get(), 2));
            if (r.nextFloat() < 0.3f) chest.setItem(slot, new ItemStack(ModItems.STAR_CHART.get()));
            chest.setChanged();
        }
    }
}
