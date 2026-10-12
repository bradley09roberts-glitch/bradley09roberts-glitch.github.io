package com.longnight;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side (per-world) configuration: longnight-server.toml in the world's serverconfig folder. */
public final class Config {
    private Config() {}

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue DAYS_PER_YEAR;
    public static final ModConfigSpec.IntValue ADULT_AGE;
    public static final ModConfigSpec.DoubleValue BIRTH_RATE;
    public static final ModConfigSpec.DoubleValue DEATH_RATE;
    public static final ModConfigSpec.IntValue START_SETTLEMENTS;
    public static final ModConfigSpec.IntValue START_CAMPS;
    public static final ModConfigSpec.IntValue START_BANDIT_CAMPS;
    public static final ModConfigSpec.IntValue WORLD_RADIUS;
    public static final ModConfigSpec.IntValue SAFE_SPAWN_RADIUS;
    public static final ModConfigSpec.IntValue EMBODY_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_BODIES_PER_SETTLEMENT;
    public static final ModConfigSpec.IntValue MAX_BODIES_TOTAL;
    public static final ModConfigSpec.IntValue PLACE_BLOCKS_PER_TICK;
    public static final ModConfigSpec.BooleanValue RAIDS;
    public static final ModConfigSpec.IntValue FIRST_RAID_DAY;
    public static final ModConfigSpec.IntValue RAID_WARNING_DAYS;
    public static final ModConfigSpec.BooleanValue MONSTERS_HUNT_CITIZENS;
    public static final ModConfigSpec.BooleanValue WARS;
    public static final ModConfigSpec.IntValue MAX_CATCHUP_DAYS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("pacing");
        DAYS_PER_YEAR = b.comment("In-game days per simulated year. 8 means one Minecraft day per month of the 8-month calendar.",
                "Lower values make generations pass faster (children grow up sooner).").defineInRange("daysPerYear", 8, 2, 96);
        ADULT_AGE = b.comment("Age (years) at which people count as adults (work, marry, fight).").defineInRange("adultAge", 16, 10, 30);
        BIRTH_RATE = b.comment("Multiplier on the chance of births.").defineInRange("birthRate", 1.0, 0.0, 10.0);
        DEATH_RATE = b.comment("Multiplier on deaths from age and illness (not violence).").defineInRange("deathRate", 1.0, 0.0, 10.0);
        MAX_CATCHUP_DAYS = b.comment("If the server was off or time was skipped, at most this many days are simulated at once.")
                .defineInRange("maxCatchUpDays", 10, 1, 100);
        b.pop();
        b.push("world");
        START_SETTLEMENTS = b.comment("Established settlements (villages/towns, grouped into realms) created with a new world.")
                .defineInRange("startingSettlements", 7, 0, 40);
        START_CAMPS = b.comment("Small founding camps of settlers looking for a home.").defineInRange("startingCamps", 2, 0, 20);
        START_BANDIT_CAMPS = b.comment("Outlaw camps in the wilds.").defineInRange("startingBanditCamps", 2, 0, 20);
        WORLD_RADIUS = b.comment("Settlements are placed within this many blocks of world spawn.").defineInRange("worldRadius", 1200, 300, 10000);
        SAFE_SPAWN_RADIUS = b.comment("No settlement or bandit camp is placed this close to world spawn.").defineInRange("safeSpawnRadius", 160, 32, 2000);
        b.pop();
        b.push("performance");
        EMBODY_DISTANCE = b.comment("Settlements within this many blocks of a player get bodies (detailed simulation).")
                .defineInRange("embodyDistance", 112, 48, 256);
        MAX_BODIES_PER_SETTLEMENT = b.comment("Maximum citizen entities per settlement; the rest are simulated abstractly.")
                .defineInRange("maxBodiesPerSettlement", 40, 4, 200);
        MAX_BODIES_TOTAL = b.comment("Global cap on citizen entities.").defineInRange("maxBodiesTotal", 160, 8, 1000);
        PLACE_BLOCKS_PER_TICK = b.comment("Blocks placed per tick when the world catches up with construction done in the distance.")
                .defineInRange("catchUpBlocksPerTick", 48, 1, 1000);
        b.pop();
        b.push("threats");
        RAIDS = b.comment("Outlaw raids on settlements.").define("raids", true);
        FIRST_RAID_DAY = b.comment("No raid strikes before this day of the world.").defineInRange("firstRaidDay", 5, 1, 1000);
        RAID_WARNING_DAYS = b.comment("Days of warning (rumours, scouts, smoke) before a raid strikes.").defineInRange("raidWarningDays", 2, 1, 10);
        MONSTERS_HUNT_CITIZENS = b.comment("Zombies and similar monsters attack citizens who are outdoors at night.")
                .define("monstersHuntCitizens", true);
        WARS = b.comment("Realms may declare war on each other.").define("wars", true);
        b.pop();
        SPEC = b.build();
    }
}
