package com.terracraft.item.weapon;

import com.terracraft.entity.projectile.ProjectileKinds;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves ammunition. TerraCraft {@link AmmoItem}s describe themselves; vanilla items can be mapped too
 * (a vanilla arrow is Terraria's Wooden Arrow).
 */
public final class AmmoRegistry {
    private static final Map<Item, AmmoInfo> VANILLA = new HashMap<>();

    static {
        VANILLA.put(Items.ARROW, new AmmoInfo(AmmoType.ARROW, 5, 0.0F, 0.0F, ProjectileKinds.WOODEN_ARROW));
    }

    private AmmoRegistry() {}

    @Nullable
    public static AmmoInfo info(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof AmmoItem ammo) {
            return ammo.ammo();
        }
        if (stack.getItem() instanceof com.terracraft.item.coin.CoinItem coin) {
            return coinAmmo(coin.value());
        }
        return VANILLA.get(stack.getItem());
    }

    private static AmmoInfo coinAmmo(long value) {
        if (value >= 1_000_000) {
            return new AmmoInfo(AmmoType.COIN, 200, 4.0F, 1.0F, ProjectileKinds.PLATINUM_COIN_SHOT);
        }
        if (value >= 10_000) {
            return new AmmoInfo(AmmoType.COIN, 100, 3.0F, 0.5F, ProjectileKinds.GOLD_COIN_SHOT);
        }
        if (value >= 100) {
            return new AmmoInfo(AmmoType.COIN, 50, 2.0F, 0.0F, ProjectileKinds.SILVER_COIN_SHOT);
        }
        return new AmmoInfo(AmmoType.COIN, 25, 1.0F, 0.0F, ProjectileKinds.COPPER_COIN_SHOT);
    }

    /** Finds the first matching ammo stack: offhand, hotbar, then inventory (Terraria uses ammo slots first). */
    public static ItemStack find(Player player, AmmoType type) {
        ItemStack offhand = player.getOffhandItem();
        AmmoInfo offInfo = info(offhand);
        if (offInfo != null && offInfo.type() == type) {
            return offhand;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            AmmoInfo info = info(stack);
            if (info != null && info.type() == type) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
