package com.starforged;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config for Starforged. Values are read directly from the spec so they
 * always reflect the latest file contents.
 */
public final class StarforgedConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue STARFALL_CHANCE = BUILDER
        .comment("Chance (0-1) that any given night becomes a Starfall (meteor shower).")
        .defineInRange("starfallChance", 0.25, 0.0, 1.0);

    public static final ForgeConfigSpec.BooleanValue FIRST_NIGHT_STARFALL = BUILDER
        .comment("If true, the very first night of a world is always a Starfall.")
        .define("firstNightStarfall", true);

    public static final ForgeConfigSpec.IntValue METEOR_INTERVAL = BUILDER
        .comment("Average number of ticks between meteors near each player during a Starfall.")
        .defineInRange("meteorInterval", 140, 20, 2400);

    public static final ForgeConfigSpec.BooleanValue METEOR_CRATERS = BUILDER
        .comment("If true, natural Starfall meteors blast craters into the terrain.")
        .define("meteorCraters", true);

    public static final ForgeConfigSpec.BooleanValue BOSS_BREAKS_DOME = BUILDER
        .comment("If true, the Eclipse Sovereign shatters Starglass around the altar when it arrives.")
        .define("bossBreaksDome", true);

    public static final ForgeConfigSpec.BooleanValue SCREEN_SHAKE = BUILDER
        .comment("Enable camera shake effects (slams, meteor impacts, boss attacks).")
        .define("screenShake", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private StarforgedConfig() {
    }
}
