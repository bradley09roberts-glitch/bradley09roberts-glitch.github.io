package com.terracraft.economy;

import com.terracraft.item.coin.CoinItem;
import com.terracraft.registry.content.CoreItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Terraria currency: 100 copper = 1 silver, 100 silver = 1 gold, 100 gold = 1 platinum.
 * Minecraft stacks cap at 99, so 100 coins of a denomination are converted to the next one automatically.
 */
public final class Coins {
    public static final long SILVER = 100;
    public static final long GOLD = 10_000;
    public static final long PLATINUM = 1_000_000;

    private Coins() {}

    /** Coin items from smallest to largest. */
    public static List<CoinItem> denominations() {
        return List.of(CoreItems.COPPER_COIN.get(), CoreItems.SILVER_COIN.get(), CoreItems.GOLD_COIN.get(), CoreItems.PLATINUM_COIN.get());
    }

    /** Splits a copper amount into the fewest coin stacks. */
    public static List<ItemStack> toStacks(long copper) {
        List<ItemStack> stacks = new ArrayList<>();
        List<CoinItem> coins = denominations();
        for (int i = coins.size() - 1; i >= 0 && copper > 0; i--) {
            CoinItem coin = coins.get(i);
            long count = copper / coin.value();
            copper -= count * coin.value();
            while (count > 0) {
                int stack = (int) Math.min(count, coin.getDefaultMaxStackSize());
                stacks.add(new ItemStack(coin, stack));
                count -= stack;
            }
        }
        return stacks;
    }

    public static long value(ItemStack stack) {
        return stack.getItem() instanceof CoinItem coin ? coin.value() * stack.getCount() : 0;
    }

    /** Total copper value of a player's inventory. */
    public static long total(Player player) {
        long total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            total += value(inventory.getItem(slot));
        }
        return total;
    }

    /**
     * Converts every full hundred of a denomination into one coin of the next, in place where possible.
     * Returns true if anything changed.
     */
    public static boolean compact(Player player) {
        boolean changed = false;
        List<CoinItem> coins = denominations();
        Inventory inventory = player.getInventory();
        for (int d = 0; d < coins.size() - 1; d++) {
            Item coin = coins.get(d);
            int count = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(coin)) {
                    count += stack.getCount();
                }
            }
            int upgrades = count / 100;
            if (upgrades <= 0) {
                continue;
            }
            int toRemove = upgrades * 100;
            for (int slot = inventory.getContainerSize() - 1; slot >= 0 && toRemove > 0; slot--) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(coin)) {
                    int take = Math.min(toRemove, stack.getCount());
                    stack.shrink(take);
                    toRemove -= take;
                }
            }
            ItemStack next = new ItemStack(coins.get(d + 1), upgrades);
            if (!inventory.add(next) && !next.isEmpty()) {
                player.drop(next, false);
            }
            changed = true;
        }
        return changed;
    }

    /**
     * Removes up to {@code copper} worth of coins (breaking larger coins into change) and returns the amount
     * actually removed.
     */
    public static long remove(Player player, long copper) {
        long available = total(player);
        long amount = Math.min(copper, available);
        if (amount <= 0) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).getItem() instanceof CoinItem) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        for (ItemStack change : toStacks(available - amount)) {
            if (!inventory.add(change)) {
                player.drop(change, false);
            }
        }
        return amount;
    }

    /** "1 gold 20 silver 5 copper" with Terraria's coin colours. */
    public static Component format(long copper) {
        MutableComponent text = Component.empty();
        long[] values = {PLATINUM, GOLD, SILVER, 1};
        String[] keys = {"platinum", "gold", "silver", "copper"};
        ChatFormatting[] colors = {ChatFormatting.WHITE, ChatFormatting.GOLD, ChatFormatting.GRAY, ChatFormatting.RED};
        boolean any = false;
        for (int i = 0; i < values.length; i++) {
            long amount = copper / values[i];
            copper %= values[i];
            if (amount > 0) {
                if (any) {
                    text.append(" ");
                }
                text.append(Component.translatable("coin.terracraft." + keys[i], amount).withStyle(colors[i]));
                any = true;
            }
        }
        return any ? text : Component.translatable("coin.terracraft.copper", 0).withStyle(ChatFormatting.RED);
    }
}
