package com.grandline.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative gameplay configuration (saved per world in serverconfig/grandline-server.toml). */
public final class GLConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue PERMADEATH;
    public static final ModConfigSpec.IntValue KNOCKDOWN_SECONDS;
    public static final ModConfigSpec.DoubleValue CONQUEROR_CHANCE;
    public static final ModConfigSpec.DoubleValue PVP_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue PVP_ALWAYS_ON;
    public static final ModConfigSpec.BooleanValue PROTECT_TOWNS;
    public static final ModConfigSpec.BooleanValue FRUIT_TERRAIN_CHANGES;
    public static final ModConfigSpec.IntValue FRUIT_TERRAIN_REVERT_SECONDS;
    public static final ModConfigSpec.BooleanValue REPLACE_VANILLA_HOSTILES;
    public static final ModConfigSpec.IntValue PROCEDURAL_ISLANDS;
    public static final ModConfigSpec.IntValue MAX_LEVEL;
    public static final ModConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MASTERY_MULTIPLIER;
    public static final ModConfigSpec.IntValue NPC_SIM_RADIUS;
    public static final ModConfigSpec.IntValue MAX_NPCS_PER_SETTLEMENT;
    public static final ModConfigSpec.BooleanValue ALLOW_DUPLICATE_FRUITS;
    public static final ModConfigSpec.BooleanValue LIVING_SEA;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("character");
        PERMADEATH = b.comment("One life per character. A true death erases the character (agreed default: true).")
                .define("permadeath", true);
        KNOCKDOWN_SECONDS = b.comment("Seconds a defeated player stays knocked down before bleeding out.")
                .defineInRange("knockdownSeconds", 30, 5, 300);
        CONQUEROR_CHANCE = b.comment("Chance a new character is born with Conqueror's Haki.")
                .defineInRange("conquerorChance", 0.10, 0.0, 1.0);
        MAX_LEVEL = b.defineInRange("maxLevel", 100, 10, 1000);
        XP_MULTIPLIER = b.defineInRange("xpMultiplier", 1.0, 0.0, 100.0);
        MASTERY_MULTIPLIER = b.defineInRange("masteryMultiplier", 1.0, 0.0, 100.0);
        b.pop();
        b.push("pvp");
        PVP_DAMAGE_MULTIPLIER = b.defineInRange("pvpDamageMultiplier", 0.6, 0.0, 10.0);
        PVP_ALWAYS_ON = b.comment("If false, PvP only applies between flagged players or in contested waters.")
                .define("pvpAlwaysOn", false);
        b.pop();
        b.push("world");
        PROTECT_TOWNS = b.define("protectTowns", true);
        REPLACE_VANILLA_HOSTILES = b.define("replaceVanillaHostiles", true);
        PROCEDURAL_ISLANDS = b.comment("Number of generated original islands (in addition to canon islands). Changing this after world creation changes the map.")
                .defineInRange("proceduralIslands", 170, 0, 600);
        NPC_SIM_RADIUS = b.defineInRange("npcSimulationRadius", 160, 48, 512);
        MAX_NPCS_PER_SETTLEMENT = b.defineInRange("maxNpcsPerSettlement", 14, 0, 64);
        LIVING_SEA = b.comment("Merchant traffic, NPC sea battles and harbour raids around players (all NPC ships share one server-wide cap).")
                .define("livingSea", true);
        b.pop();
        b.push("fruits");
        FRUIT_TERRAIN_CHANGES = b.define("terrainChanges", true);
        FRUIT_TERRAIN_REVERT_SECONDS = b.defineInRange("terrainRevertSeconds", 90, 5, 3600);
        ALLOW_DUPLICATE_FRUITS = b.comment("Canon rule is false: each fruit exists once per world.")
                .define("allowDuplicateFruits", false);
        b.pop();
        SPEC = b.build();
    }

    private GLConfig() {}
}
