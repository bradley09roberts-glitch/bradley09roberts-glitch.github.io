package com.terracraft.player;

import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.player.stats.PlayerStats;
import com.terracraft.player.stats.Stat;
import net.minecraft.world.entity.player.Player;

/**
 * Terraria's natural life regeneration (replaces vanilla food healing when enabled in the config).
 * <p>
 * Regeneration ramps up the longer the player goes without taking damage, is faster when standing still,
 * scales with maximum life and is boosted by regeneration stats (Band of Regeneration, Regeneration potion...).
 * In Expert mode it is halved unless the player has a food buff (see {@link FoodManager}).
 */
public final class LifeRegenManager {
    private static final int TERRARIA_TICKS_PER_TICK = 3;

    private LifeRegenManager() {}

    public static void onHurt(TerraPlayerData data) {
        data.lifeRegenTime = 0;
    }

    public static void tick(Player player, TerraPlayerData data) {
        if (player.isDeadOrDying()) {
            return;
        }
        PlayerStats stats = data.stats();
        boolean wellFed = FoodManager.isWellFed(player);
        boolean still = ManaManager.isStill(player);
        boolean expert = TerrariaDifficulty.isExpert(player.level());
        int bonusRegen = stats.getInt(Stat.LIFE_REGEN);

        for (int step = 0; step < TERRARIA_TICKS_PER_TICK; step++) {
            float natural = 0.0F;
            {
                data.lifeRegenTime++;
                int t = data.lifeRegenTime;
                if (t >= 300) natural += 1.0F;
                if (t >= 600) natural += 1.0F;
                if (t >= 900) natural += 1.0F;
                if (t >= 1200) natural += 1.0F;
                if (t >= 1500) natural += 1.0F;
                if (t >= 1800) natural += 1.0F;
                if (t >= 2400) natural += 1.0F;
                if (t >= 3000) natural += 1.0F;
                if (t >= 3600) {
                    natural += 1.0F;
                    data.lifeRegenTime = 3600;
                }
                if (player.isSleeping()) {
                    data.lifeRegenTime += 10;
                    natural *= 1.5F;
                }
                natural *= still ? 1.25F : 0.5F;
                if (expert && !wellFed) {
                    natural *= 0.5F;
                }
                natural *= stats.maxLife / 400.0F * 0.85F + 0.15F;
            }
            int regen = Math.round(natural) + bonusRegen;
            data.lifeRegenCount += regen;
            while (data.lifeRegenCount >= 120) {
                data.lifeRegenCount -= 120;
                if (player.getHealth() < player.getMaxHealth()) {
                    player.heal(1.0F);
                }
            }
            while (data.lifeRegenCount <= -120) {
                // Negative regeneration (poison-style debuffs) is applied as direct damage by the buff system.
                data.lifeRegenCount += 120;
            }
        }
    }
}
