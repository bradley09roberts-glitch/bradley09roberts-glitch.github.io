package com.terracraft.player.stats;

import com.terracraft.effect.TerraBuffEffect;
import com.terracraft.player.TerraPlayerData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/** Applies the stat effects of active Terraria buffs. */
public final class BuffStatSource implements StatCalculator.StatSource {
    @Override
    public void contribute(Player player, TerraPlayerData data, PlayerStats stats) {
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (instance.getEffect().value() instanceof TerraBuffEffect buff) {
                buff.effects().applyTo(stats);
            }
        }
    }
}
