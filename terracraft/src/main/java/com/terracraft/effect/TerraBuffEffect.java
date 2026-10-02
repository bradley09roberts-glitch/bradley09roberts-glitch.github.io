package com.terracraft.effect;

import com.terracraft.player.stats.StatEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A Terraria buff or debuff. Its gameplay effect is a {@link StatEffects} bundle applied by the stat
 * calculator while the effect is active, so buffs share the exact stat model of armor and accessories.
 */
public class TerraBuffEffect extends MobEffect {
    private final StatEffects effects;

    public TerraBuffEffect(MobEffectCategory category, int color, StatEffects effects) {
        super(category, color);
        this.effects = effects;
    }

    public StatEffects effects() {
        return effects;
    }
}
