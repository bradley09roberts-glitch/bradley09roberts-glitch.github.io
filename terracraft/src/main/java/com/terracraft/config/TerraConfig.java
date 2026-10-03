package com.terracraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;

/**
 * All TerraCraft configuration.
 * <p>
 * {@link Common} holds gameplay rules. They are read on the logical server; anything the client needs
 * (max mana, max life, flags...) is synchronised through our own packets, so a dedicated server's
 * config is always authoritative.
 * {@link Client} holds purely visual preferences.
 */
public final class TerraConfig {
    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        COMMON = new Common(common);
        COMMON_SPEC = common.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        CLIENT = new Client(client);
        CLIENT_SPEC = client.build();
    }

    private TerraConfig() {}

    public static void register(ModContainer context) {
        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "terracraft-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "terracraft-client.toml");
    }

    /** How coins are lost on death (Terraria's softcore rule is HALF). */
    public enum CoinDeathPenalty { NONE, HALF, ALL }

    /** Terraria world difficulty. AUTO maps Minecraft Hard difficulty to Expert. */
    public enum TerrariaDifficulty { AUTO, CLASSIC, EXPERT, MASTER }

    public static final class Common {
        // ---------------- player ----------------
        public final ModConfigSpec.IntValue baseMaxLife;
        public final ModConfigSpec.IntValue lifeCrystalLife;
        public final ModConfigSpec.IntValue maxLifeCrystals;
        public final ModConfigSpec.IntValue lifeFruitLife;
        public final ModConfigSpec.IntValue maxLifeFruit;
        public final ModConfigSpec.IntValue baseMaxMana;
        public final ModConfigSpec.IntValue manaCrystalMana;
        public final ModConfigSpec.IntValue maxManaCrystals;
        public final ModConfigSpec.IntValue accessorySlots;
        public final ModConfigSpec.BooleanValue terrariaLifeRegen;
        public final ModConfigSpec.DoubleValue environmentalDamageMultiplier;
        public final ModConfigSpec.DoubleValue vanillaMobDamageMultiplier;
        public final ModConfigSpec.DoubleValue vanillaArmorDefenseScale;
        public final ModConfigSpec.BooleanValue softcoreDeaths;
        public final ModConfigSpec.EnumValue<CoinDeathPenalty> coinDeathPenalty;
        public final ModConfigSpec.EnumValue<TerrariaDifficulty> difficulty;
        public final ModConfigSpec.BooleanValue damageVariance;

        // ---------------- vanilla suppression ----------------
        public final ModConfigSpec.BooleanValue disableVillagers;
        public final ModConfigSpec.BooleanValue disableWanderingTraders;
        public final ModConfigSpec.BooleanValue disableVanillaHostileSpawns;
        public final ModConfigSpec.BooleanValue disableNether;
        public final ModConfigSpec.BooleanValue disableEnd;
        public final ModConfigSpec.BooleanValue disableEnchanting;
        public final ModConfigSpec.BooleanValue disableVanillaBrewing;
        public final ModConfigSpec.BooleanValue disableDiamondGear;
        public final ModConfigSpec.BooleanValue disableNetheriteGear;

        // ---------------- economy ----------------
        public final ModConfigSpec.BooleanValue terrariaSpawning;
        public final ModConfigSpec.DoubleValue spawnRateMultiplier;
        public final ModConfigSpec.DoubleValue maxSpawnsMultiplier;
        public final ModConfigSpec.BooleanValue vanillaMobsDropCoins;
        public final ModConfigSpec.BooleanValue autoCompactCoins;

        // ---------------- world ----------------
        public final ModConfigSpec.DoubleValue secondaryOreFrequency;
        public final ModConfigSpec.BooleanValue biomeSpread;
        public final ModConfigSpec.DoubleValue biomeSpreadSpeed;

        // ---------------- debug ----------------
        public final ModConfigSpec.BooleanValue verboseLogging;

        Common(ModConfigSpec.Builder b) {
            b.comment("Terraria-style player statistics").push("player");
            baseMaxLife = b.comment("Starting maximum life (Terraria: 100).")
                .defineInRange("baseMaxLife", 100, 20, 1000);
            lifeCrystalLife = b.comment("Maximum life gained per Life Crystal (Terraria: 20).")
                .defineInRange("lifeCrystalLife", 20, 1, 200);
            maxLifeCrystals = b.comment("How many Life Crystals can be consumed (Terraria: 15).")
                .defineInRange("maxLifeCrystals", 15, 0, 100);
            lifeFruitLife = b.comment("Maximum life gained per Life Fruit (Terraria: 5).")
                .defineInRange("lifeFruitLife", 5, 1, 100);
            maxLifeFruit = b.comment("How many Life Fruit can be consumed (Terraria: 20).")
                .defineInRange("maxLifeFruit", 20, 0, 100);
            baseMaxMana = b.comment("Starting maximum mana (Terraria: 20).")
                .defineInRange("baseMaxMana", 20, 0, 1000);
            manaCrystalMana = b.comment("Maximum mana gained per Mana Crystal (Terraria: 20).")
                .defineInRange("manaCrystalMana", 20, 1, 200);
            maxManaCrystals = b.comment("How many Mana Crystals can be consumed (Terraria: 9).")
                .defineInRange("maxManaCrystals", 9, 0, 100);
            accessorySlots = b.comment("Number of accessory slots every player has (Terraria Classic: 5).")
                .defineInRange("accessorySlots", 5, 1, 10);
            terrariaLifeRegen = b.comment("Replace food-based health regeneration with Terraria's natural life regeneration.",
                    "When enabled the natural_health_regeneration game rule is turned off.")
                .define("terrariaLifeRegen", true);
            environmentalDamageMultiplier = b.comment("Multiplier applied to vanilla environmental damage (falling, lava, drowning...) against players.",
                    "Player life uses Terraria numbers (100+), so vanilla damage values must be scaled to stay dangerous.")
                .defineInRange("environmentalDamageMultiplier", 5.0, 0.0, 100.0);
            vanillaMobDamageMultiplier = b.comment("Multiplier applied to damage dealt to players by vanilla (non-TerraCraft) mobs.")
                .defineInRange("vanillaMobDamageMultiplier", 5.0, 0.0, 100.0);
            vanillaArmorDefenseScale = b.comment("Terraria defense granted per vanilla armor point. Vanilla armor's own damage reduction is disabled for players.")
                .defineInRange("vanillaArmorDefenseScale", 0.5, 0.0, 10.0);
            softcoreDeaths = b.comment("Terraria softcore deaths: players keep their items (keep_inventory is enabled) and only drop coins.",
                    "Disable for vanilla behaviour (everything drops).")
                .define("softcoreDeaths", true);
            coinDeathPenalty = b.comment("Coins dropped when a player dies with softcore deaths (Terraria softcore: HALF).")
                .defineEnum("coinDeathPenalty", CoinDeathPenalty.HALF);
            difficulty = b.comment("Terraria difficulty rules (defense effectiveness, enemy scaling). AUTO: Hard = Expert, otherwise Classic.")
                .defineEnum("difficulty", TerrariaDifficulty.AUTO);
            damageVariance = b.comment("Apply Terraria's +/-15% random damage variance.")
                .define("damageVariance", true);
            b.pop();

            b.comment("Removal of vanilla systems that would bypass Terraria progression").push("vanilla");
            disableVillagers = b.comment("Prevent villagers and zombie villagers from existing; Terraria NPCs replace them.")
                .define("disableVillagers", true);
            disableWanderingTraders = b.comment("Prevent wandering traders and trader llamas from spawning.")
                .define("disableWanderingTraders", true);
            disableVanillaHostileSpawns = b.comment("Stop vanilla hostile mobs (zombies, skeletons, creepers...) from spawning naturally.",
                    "Terraria enemies form the hostile ecosystem instead.")
                .define("disableVanillaHostileSpawns", true);
            disableNether = b.comment("Block travel to the Nether. TerraCraft's Underworld lives at the bottom of the overworld.")
                .define("disableNether", true);
            disableEnd = b.comment("Block travel to the End.")
                .define("disableEnd", true);
            disableEnchanting = b.comment("Disable the enchanting table (recipe and use). Reforging replaces enchanting.")
                .define("disableEnchanting", true);
            disableVanillaBrewing = b.comment("Disable the brewing stand (recipe and use). Terraria potions are crafted at alchemy stations.")
                .define("disableVanillaBrewing", true);
            disableDiamondGear = b.comment("Disable crafting of diamond tools, weapons and armor.")
                .define("disableDiamondGear", true);
            disableNetheriteGear = b.comment("Disable netherite upgrades.")
                .define("disableNetheriteGear", true);
            b.pop();

            b.comment("Terraria enemy spawning (rules: data/<namespace>/terracraft/spawns/*.json)").push("spawning");
            terrariaSpawning = b.comment("Spawn Terraria enemies around players.")
                .define("terrariaSpawning", true);
            spawnRateMultiplier = b.comment("Multiplier on how often spawn attempts succeed.")
                .defineInRange("spawnRateMultiplier", 1.0, 0.0, 10.0);
            maxSpawnsMultiplier = b.comment("Multiplier on how many enemies may be near each player at once.")
                .defineInRange("maxSpawnsMultiplier", 1.0, 0.0, 10.0);
            b.pop();

            b.comment("Coins and economy").push("economy");
            vanillaMobsDropCoins = b.comment("Vanilla mobs that still exist (animals, etc.) drop a few copper coins.")
                .define("vanillaMobsDropCoins", true);
            autoCompactCoins = b.comment("Automatically convert 100 coins into the next denomination.")
                .define("autoCompactCoins", true);
            b.pop();

            b.comment("World generation and world transformation").push("world");
            secondaryOreFrequency = b.comment("Each world picks one ore of every Terraria ore pair (Copper/Tin, Iron/Lead...).",
                    "This is the relative frequency of the ore that was NOT picked. 0 = strict Terraria behaviour.")
                .defineInRange("secondaryOreFrequency", 0.25, 0.0, 1.0);
            biomeSpread = b.comment("Allow Corruption/Crimson/Hallow to spread (Hardmode). Uses capped random-tick conversion.")
                .define("biomeSpread", true);
            biomeSpreadSpeed = b.comment("Biome spread speed multiplier.")
                .defineInRange("biomeSpreadSpeed", 1.0, 0.0, 10.0);
            b.pop();

            b.push("debug");
            verboseLogging = b.comment("Log extra diagnostic information.")
                .define("verboseLogging", false);
            b.pop();
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue terrariaHud;
        public final ModConfigSpec.BooleanValue flatSprites;
        public final ModConfigSpec.BooleanValue showLifeText;
        public final ModConfigSpec.BooleanValue showManaText;
        public final ModConfigSpec.BooleanValue showDefense;

        Client(ModConfigSpec.Builder b) {
            b.comment("Heads-up display").push("hud");
            terrariaHud = b.comment("Replace the vanilla hearts with Terraria-style life hearts and mana stars.")
                .define("terrariaHud", true);
            flatSprites = b.comment("Draw enemies, bosses and NPCs as flat 2D Terraria-style sprites instead of 3D models",
                    "(use this with a Terraria sprite resource pack). Applies after a resource reload (F3+T).")
                .define("flatSprites", false);
            showLifeText = b.comment("Show the 'Life: x/y' text above the hearts.")
                .define("showLifeText", true);
            showManaText = b.comment("Show the mana number next to the mana stars.")
                .define("showManaText", true);
            showDefense = b.comment("Show the defense shield next to the hearts.")
                .define("showDefense", true);
            b.pop();
        }
    }
}
