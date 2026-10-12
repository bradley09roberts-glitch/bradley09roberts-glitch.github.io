package com.longnight.game;

import com.longnight.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Copper pennies, silver marks (10p) and gold crowns (100p). */
public final class Coins {
    private Coins() {}

    public static int valueOf(Item item) {
        if (item == ModItems.COPPER_PENNY.get()) return 1;
        if (item == ModItems.SILVER_MARK.get()) return 10;
        if (item == ModItems.GOLD_CROWN.get()) return 100;
        return 0;
    }

    public static int purse(ServerPlayer p) {
        int total = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            total += valueOf(s.getItem()) * s.getCount();
        }
        return total;
    }

    /** Takes exactly {@code amount} pennies' worth, giving change. Returns false and takes nothing if short. */
    public static boolean take(ServerPlayer p, int amount) {
        if (amount <= 0) return true;
        if (purse(p) < amount) return false;
        Inventory inv = p.getInventory();
        int taken = 0;
        // smallest coins first
        for (Item coin : new Item[]{ModItems.COPPER_PENNY.get(), ModItems.SILVER_MARK.get(), ModItems.GOLD_CROWN.get()}) {
            int v = valueOf(coin);
            for (int i = 0; i < inv.getContainerSize() && taken < amount; i++) {
                ItemStack s = inv.getItem(i);
                if (s.getItem() != coin) continue;
                while (!s.isEmpty() && taken < amount) {
                    s.shrink(1);
                    taken += v;
                }
            }
        }
        if (taken > amount) give(p, taken - amount);
        p.containerMenu.broadcastChanges();
        return true;
    }

    public static void give(ServerPlayer p, int amount) {
        if (amount <= 0) return;
        int crowns = amount / 100;
        int marks = (amount % 100) / 10;
        int pennies = amount % 10;
        add(p, new ItemStack(ModItems.GOLD_CROWN.get(), crowns));
        add(p, new ItemStack(ModItems.SILVER_MARK.get(), marks));
        add(p, new ItemStack(ModItems.COPPER_PENNY.get(), pennies));
    }

    static void add(ServerPlayer p, ItemStack s) {
        while (s.getCount() > 0) {
            int n = Math.min(s.getCount(), s.getMaxStackSize());
            ItemStack part = s.copyWithCount(n);
            s.shrink(n);
            if (!p.getInventory().add(part)) p.drop(part, false);
        }
    }

    public static String format(int pennies) {
        int c = pennies / 100, m = (pennies % 100) / 10, d = pennies % 10;
        StringBuilder sb = new StringBuilder();
        if (c > 0) sb.append(c).append("c ");
        if (m > 0) sb.append(m).append("m ");
        if (d > 0 || sb.length() == 0) sb.append(d).append("p");
        return sb.toString().trim();
    }
}
