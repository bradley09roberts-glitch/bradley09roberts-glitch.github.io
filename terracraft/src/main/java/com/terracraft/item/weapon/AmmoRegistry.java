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
        return VANILLA.get(stack.getItem());
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
