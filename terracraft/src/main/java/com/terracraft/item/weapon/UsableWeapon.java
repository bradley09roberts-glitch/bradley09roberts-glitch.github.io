package com.terracraft.item.weapon;

import com.terracraft.item.TerraItemStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A weapon that fires on use. Implemented by ranged, magic, thrown and summon weapons. Both the vanilla
 * right-click path and TerraCraft's Terraria-style left-click path ({@code UseWeaponPacket}) end in
 * {@link #fire}, so the two controls behave identically.
 */
public interface UsableWeapon {
    /** Performs one shot on the server. Returns true if the weapon was used (cooldown starts). */
    boolean fire(ServerPlayer player, ItemStack stack);

    /** Shared use() implementation with Terraria use-time cooldowns. */
    static InteractionResult use(UsableWeapon weapon, Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return tryFire(weapon, serverPlayer, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    static boolean tryFire(UsableWeapon weapon, ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(stack)) {
            return false;
        }
        if (!weapon.fire(player, stack)) {
            return false;
        }
        player.getCooldowns().addCooldown(stack, TerraItemStats.of(stack).useTicks());
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }
}
