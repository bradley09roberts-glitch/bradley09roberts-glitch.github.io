package com.terracraft.combat;

import com.terracraft.config.TerraConfig;
import net.minecraft.util.RandomSource;

/** Terraria's damage formulas. */
public final class DamageCalc {
    /** Base critical strike chance every attack has, in percent. */
    public static final int BASE_CRIT = 4;
    /** Terraria knockback -> Minecraft knockback strength. */
    public static final float KNOCKBACK_SCALE = 0.08F;

    private DamageCalc() {}

    /** Applies Terraria's +/-15% random variance (if enabled). */
    public static float variance(float damage, RandomSource random) {
        if (!TerraConfig.COMMON.damageVariance.get()) {
            return damage;
        }
        return damage * (1.0F + (random.nextInt(31) - 15) / 100.0F);
    }

    public static boolean rollCrit(int critChance, RandomSource random) {
        return critChance > 0 && random.nextInt(100) < critChance;
    }

    /**
     * Terraria defense: damage minus defense x effectiveness (0.5 Classic, 0.75 Expert, 1.0 Master),
     * never below 1.
     */
    public static float applyDefense(float damage, int defense, int armorPenetration, float effectiveness) {
        int effectiveDefense = Math.max(0, defense - armorPenetration);
        return Math.max(1.0F, damage - effectiveDefense * effectiveness);
    }

    public static float applyEndurance(float damage, float endurance) {
        return damage * Math.max(0.0F, 1.0F - Math.min(endurance, 0.95F));
    }
}
