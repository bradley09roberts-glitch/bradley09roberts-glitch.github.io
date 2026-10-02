package com.terracraft.combat;

import com.terracraft.config.TerraConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/** Resolves Terraria's Classic / Expert / Master rules for a level. */
public final class TerrariaDifficulty {
    public enum Mode { CLASSIC, EXPERT, MASTER }

    private TerrariaDifficulty() {}

    public static Mode of(Level level) {
        return switch (TerraConfig.COMMON.difficulty.get()) {
            case CLASSIC -> Mode.CLASSIC;
            case EXPERT -> Mode.EXPERT;
            case MASTER -> Mode.MASTER;
            case AUTO -> level.getDifficulty() == Difficulty.HARD ? Mode.EXPERT : Mode.CLASSIC;
        };
    }

    public static boolean isExpert(Level level) {
        return of(level) != Mode.CLASSIC;
    }

    /** Fraction of defense that is subtracted from incoming damage (Terraria: 0.5 / 0.75 / 1.0). */
    public static float defenseEffectiveness(Level level) {
        return switch (of(level)) {
            case CLASSIC -> 0.5F;
            case EXPERT -> 0.75F;
            case MASTER -> 1.0F;
        };
    }

    /** Multiplier applied to enemy health (bosses use their own scaling on top). */
    public static float enemyHealthMultiplier(Level level) {
        return switch (of(level)) {
            case CLASSIC -> 1.0F;
            case EXPERT -> 2.0F;
            case MASTER -> 3.0F;
        };
    }

    /** Multiplier applied to enemy contact/projectile damage. */
    public static float enemyDamageMultiplier(Level level) {
        return switch (of(level)) {
            case CLASSIC -> 1.0F;
            case EXPERT -> 2.0F;
            case MASTER -> 3.0F;
        };
    }
}
