package com.terracraft.player;

import com.terracraft.player.stats.PlayerStats;
import com.terracraft.player.stats.Stat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Terraria's mana regeneration and spending rules, ported from Terraria's player update loop.
 * Terraria runs at 60 ticks per second and Minecraft at 20, so every Minecraft tick runs three
 * Terraria regeneration steps.
 */
public final class ManaManager {
    private static final int TERRARIA_TICKS_PER_TICK = 3;

    private ManaManager() {}

    public static void tick(Player player, TerraPlayerData data) {
        PlayerStats stats = data.stats();
        int maxMana = stats.maxMana;
        if (!data.manaInitialised()) {
            data.setMana(maxMana);
            data.markSyncDirty();
        }
        if (maxMana <= 0) {
            return;
        }
        if (data.manaRegenLockout > 0) {
            data.manaRegenLockout--;
            return;
        }
        boolean still = isStill(player);
        for (int step = 0; step < TERRARIA_TICKS_PER_TICK; step++) {
            if (data.manaRegenDelay > 0) {
                data.manaRegenDelay--;
                if (still) {
                    data.manaRegenDelay--;
                }
            }
            int regen = 0;
            if (data.manaRegenDelay <= 0) {
                data.manaRegenDelay = 0;
                float base = maxMana / 7.0F + 1.0F + stats.get(Stat.MANA_REGEN);
                if (still) {
                    base += maxMana / 2.0F;
                }
                float fullness = data.mana() / maxMana * 0.8F + 0.2F;
                regen = (int) (base * fullness * 1.15F);
            }
            data.manaRegenCount += regen;
            while (data.manaRegenCount >= 120) {
                data.manaRegenCount -= 120;
                if (data.mana() < maxMana) {
                    data.setMana(data.mana() + 1.0F);
                }
            }
        }
    }

    /** Mana a weapon actually costs after the player's mana cost modifiers (min 0). */
    public static int effectiveCost(TerraPlayerData data, int baseCost) {
        return Math.max(0, Math.round(baseCost * data.stats().manaCostMultiplier()));
    }

    public static boolean canAfford(Player player, TerraPlayerData data, int baseCost) {
        return player.getAbilities().instabuild || data.mana() >= effectiveCost(data, baseCost);
    }

    /**
     * Spends mana for a magic weapon or ability. Returns false (and spends nothing) if the player does not
     * have enough. Creative players never run out.
     */
    public static boolean consume(Player player, TerraPlayerData data, int baseCost) {
        int cost = effectiveCost(data, baseCost);
        if (player.getAbilities().instabuild) {
            return true;
        }
        if (data.mana() < cost) {
            return false;
        }
        data.setMana(data.mana() - cost);
        int maxMana = Math.max(1, data.stats().maxMana);
        float delay = (1.0F - data.mana() / maxMana) * 60.0F * 4.0F + 45.0F;
        data.manaRegenDelay = (int) (delay * 0.7F);
        data.markSyncDirty();
        return true;
    }

    /** Restores mana (potions, stars, commands). */
    public static void restore(TerraPlayerData data, int amount) {
        data.setMana(data.mana() + amount);
        data.markSyncDirty();
    }

    static boolean isStill(Player player) {
        Vec3 motion = player.getKnownMovement();
        return motion.x * motion.x + motion.z * motion.z < 1.0E-4;
    }
}
