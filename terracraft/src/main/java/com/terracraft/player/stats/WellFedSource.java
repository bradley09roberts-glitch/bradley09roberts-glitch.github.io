package com.terracraft.player.stats;

import com.terracraft.player.LifeRegenManager;
import com.terracraft.player.TerraPlayerData;
import net.minecraft.world.entity.player.Player;

/**
 * Minecraft's hunger bar doubles as Terraria's "Well Fed" buff: a nearly full food bar grants Terraria's
 * minor all-round bonuses (and, in Expert, avoids the halved natural life regeneration).
 */
public final class WellFedSource implements StatCalculator.StatSource {
    public static final StatEffects WELL_FED = StatEffects.builder()
        .add(Stat.DEFENSE, 2)
        .add(Stat.CRIT, 2)
        .add(Stat.DAMAGE, 0.05F)
        .add(Stat.MELEE_SPEED, 0.05F)
        .add(Stat.KNOCKBACK, 0.05F)
        .add(Stat.MOVE_SPEED, 0.05F)
        .add(Stat.MINING_SPEED, 0.10F)
        .build();

    @Override
    public void contribute(Player player, TerraPlayerData data, PlayerStats stats) {
        if (player.getFoodData().getFoodLevel() >= LifeRegenManager.WELL_FED_FOOD_LEVEL) {
            WELL_FED.applyTo(stats);
        }
    }
}
