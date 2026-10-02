package com.terracraft.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        COMMON = new Common(common);
        COMMON_SPEC = common.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        CLIENT = new Client(client);
        CLIENT_SPEC = client.build();
    }

    private TerraConfig() {}

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "terracraft-common.toml");
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "terracraft-client.toml");
    }

    /** How coins are lost on death (Terraria's softcore rule is HALF). */
    public enum CoinDeathPenalty { NONE, HALF, ALL }

    /** Terraria world difficulty. AUTO maps Minecraft Hard difficulty to Expert. */
    public enum TerrariaDifficulty { AUTO, CLASSIC, EXPERT, MASTER }

    public static final class Common {
        // ---------------- player ----------------
        public final ForgeConfigSpec.IntValue baseMaxLife;
        public final ForgeConfigSpec.IntValue lifeCrystalLife;
        public final ForgeConfigSpec.IntValue maxLifeCrystals;
        public final ForgeConfigSpec.IntValue lifeFruitLife;
        public final ForgeConfigSpec.IntValue maxLifeFruit;
        public final ForgeConfigSpec.IntValue baseMaxMana;
        public final ForgeConfigSpec.IntValue manaCrystalMana;
        public final ForgeConfigSpec.IntValue maxManaCrystals;
        public final ForgeConfigSpec.IntValue accessorySlots;
        public final ForgeConfigSpec.BooleanValue terrariaLifeRegen;
        public final ForgeConfigSpec.DoubleValue environmentalDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue vanillaMobDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue vanillaArmorDefenseScale;
        public final ForgeConfigSpec.EnumValue<CoinDeathPenalty> coinDeathPenalty;
        public final ForgeConfigSpec.EnumValue<TerrariaDifficulty> difficulty;
        public final ForgeConfigSpec.BooleanValue damageVariance;

        // ---------------- vanilla suppression ----------------
        public final ForgeConfigSpec.BooleanValue disableVillagers;
        public final ForgeConfigSpec.BooleanValue disableWanderingTraders;
        public final ForgeConfigSpec.BooleanValue disableVanillaHostileSpawns;
        public final ForgeConfigSpec.BooleanValue disableNether;
        public final ForgeConfigSpec.BooleanValue disableEnd;
        public final ForgeConfigSpec.BooleanValue disableEnchanting;
        public final ForgeConfigSpec.BooleanValue disableVanillaBrewing;
        public final ForgeConfigSpec.BooleanValue disableDiamondGear;
        public final ForgeConfigSpec.BooleanValue disableNetheriteGear;

        // ---------------- economy ----------------
        public final ForgeConfigSpec.BooleanValue vanillaMobsDropCoins;
        public final ForgeConfigSpec.BooleanValue autoCompactCoins;

        // ---------------- world ----------------
        public final ForgeConfigSpec.DoubleValue secondaryOreFrequency;
        public final ForgeConfigSpec.BooleanValue biomeSpread;
        public final ForgeConfigSpec.DoubleValue biomeSpreadSpeed;

        // ---------------- debug ----------------
        public final ForgeConfigSpec.BooleanValue verboseLogging;

        Common(ForgeConfigSpec.Builder b) {
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
            coinDeathPenalty = b.comment("Coins dropped when a player dies (Terraria softcore: HALF).")
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
        public final ForgeConfigSpec.BooleanValue terrariaHud;
        public final ForgeConfigSpec.BooleanValue showLifeText;
        public final ForgeConfigSpec.BooleanValue showManaText;
        public final ForgeConfigSpec.BooleanValue showDefense;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("Heads-up display").push("hud");
            terrariaHud = b.comment("Replace the vanilla hearts with Terraria-style life hearts and mana stars.")
                .define("terrariaHud", true);
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
