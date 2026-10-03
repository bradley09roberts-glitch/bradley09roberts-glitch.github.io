package com.terracraft.registry.content;

import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.item.accessory.WingsItem;
import com.terracraft.item.TerraItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.stats.Ability;
import com.terracraft.player.stats.Stat;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraftforge.registries.RegistryObject;

/** Accessories: each is a name, a rarity/value and a {@link StatEffects} bundle. */
public final class AccessoryContent {
    public static final RegistryObject<AccessoryItem> HERMES_BOOTS = accessory("hermes_boots", TerraRarity.BLUE, 10000,
        StatEffects.builder().add(Stat.MOVE_SPEED, 0.20F));
    public static final RegistryObject<AccessoryItem> CLOUD_IN_A_BOTTLE = accessory("cloud_in_a_bottle", TerraRarity.BLUE, 10000,
        StatEffects.builder().add(Stat.EXTRA_JUMPS, 1));
    public static final RegistryObject<AccessoryItem> SHINY_RED_BALLOON = accessory("shiny_red_balloon", TerraRarity.BLUE, 10000,
        StatEffects.builder().add(Stat.JUMP_HEIGHT, 0.33F));
    public static final RegistryObject<AccessoryItem> LUCKY_HORSESHOE = accessory("lucky_horseshoe", TerraRarity.BLUE, 10000,
        StatEffects.builder().ability(Ability.NO_FALL_DAMAGE));
    public static final RegistryObject<AccessoryItem> BAND_OF_REGENERATION = accessory("band_of_regeneration", TerraRarity.BLUE, 10000,
        StatEffects.builder().add(Stat.LIFE_REGEN, 1));
    public static final RegistryObject<AccessoryItem> BAND_OF_STARPOWER = accessory("band_of_starpower", TerraRarity.BLUE, 10000,
        StatEffects.builder().add(Stat.MAX_MANA, 20));
    public static final RegistryObject<AccessoryItem> MANA_REGENERATION_BAND = accessory("mana_regeneration_band", TerraRarity.GREEN, 30000,
        StatEffects.builder().add(Stat.MAX_MANA, 20).add(Stat.MANA_REGEN, 4));
    public static final RegistryObject<AccessoryItem> NATURES_GIFT = accessory("natures_gift", TerraRarity.ORANGE, 20000,
        StatEffects.builder().add(Stat.MANA_COST, -0.06F));
    public static final RegistryObject<AccessoryItem> SHACKLE = accessory("shackle", TerraRarity.WHITE, 1500,
        StatEffects.builder().add(Stat.DEFENSE, 1));
    public static final RegistryObject<AccessoryItem> AGLET = accessory("aglet", TerraRarity.WHITE, 2000,
        StatEffects.builder().add(Stat.MOVE_SPEED, 0.05F));
    public static final RegistryObject<AccessoryItem> ANKLET_OF_THE_WIND = accessory("anklet_of_the_wind", TerraRarity.GREEN, 20000,
        StatEffects.builder().add(Stat.MOVE_SPEED, 0.10F));
    public static final RegistryObject<AccessoryItem> FERAL_CLAWS = accessory("feral_claws", TerraRarity.GREEN, 20000,
        StatEffects.builder().add(Stat.MELEE_SPEED, 0.12F));
    public static final RegistryObject<AccessoryItem> OBSIDIAN_SKULL = accessory("obsidian_skull", TerraRarity.GREEN, 10000,
        StatEffects.builder().ability(Ability.FIRE_BLOCK_IMMUNE));
    public static final RegistryObject<AccessoryItem> LAVA_CHARM = accessory("lava_charm", TerraRarity.ORANGE, 20000,
        StatEffects.builder().add(Stat.LAVA_IMMUNITY_SECONDS, 7));
    public static final RegistryObject<AccessoryItem> COBALT_SHIELD = accessory("cobalt_shield", TerraRarity.GREEN, 25000,
        StatEffects.builder().add(Stat.DEFENSE, 1).ability(Ability.KNOCKBACK_IMMUNE));
    public static final RegistryObject<AccessoryItem> FLIPPER = accessory("flipper", TerraRarity.BLUE, 10000,
        StatEffects.builder().ability(Ability.SWIMMING));
    public static final RegistryObject<AccessoryItem> WATER_WALKING_BOOTS = accessory("water_walking_boots", TerraRarity.GREEN, 20000,
        StatEffects.builder().ability(Ability.WATER_WALKING));
    public static final RegistryObject<AccessoryItem> TOOLBELT = accessory("toolbelt", TerraRarity.ORANGE, 30000,
        StatEffects.builder().add(Stat.REACH, 1));

    private AccessoryContent() {}

    public static void init() {}

    public static final RegistryObject<AccessoryItem> PANIC_NECKLACE = accessory("panic_necklace", TerraRarity.GREEN, 10000,
        StatEffects.builder().ability(Ability.PANIC));

    public static final RegistryObject<AccessoryItem> HONEY_COMB = accessory("honey_comb", TerraRarity.GREEN, 20000,
        StatEffects.builder().ability(Ability.HONEY_COMB));

    // Wall of Flesh emblems
    public static final RegistryObject<AccessoryItem> WARRIOR_EMBLEM = accessory("warrior_emblem", TerraRarity.LIGHT_RED, 100000,
        StatEffects.builder().add(Stat.MELEE_DAMAGE, 0.15F));
    public static final RegistryObject<AccessoryItem> RANGER_EMBLEM = accessory("ranger_emblem", TerraRarity.LIGHT_RED, 100000,
        StatEffects.builder().add(Stat.RANGED_DAMAGE, 0.15F));
    public static final RegistryObject<AccessoryItem> SORCERER_EMBLEM = accessory("sorcerer_emblem", TerraRarity.LIGHT_RED, 100000,
        StatEffects.builder().add(Stat.MAGIC_DAMAGE, 0.15F));
    public static final RegistryObject<AccessoryItem> SUMMONER_EMBLEM = accessory("summoner_emblem", TerraRarity.LIGHT_RED, 100000,
        StatEffects.builder().add(Stat.SUMMON_DAMAGE, 0.15F));

    // Tinkerer's Workshop combinations
    public static final RegistryObject<AccessoryItem> OBSIDIAN_HORSESHOE = accessory("obsidian_horseshoe", TerraRarity.ORANGE, 30000,
        StatEffects.builder().ability(Ability.NO_FALL_DAMAGE).ability(Ability.FIRE_BLOCK_IMMUNE));
    public static final RegistryObject<AccessoryItem> CLOUD_IN_A_BALLOON = accessory("cloud_in_a_balloon", TerraRarity.GREEN, 30000,
        StatEffects.builder().add(Stat.EXTRA_JUMPS, 1).add(Stat.JUMP_HEIGHT, 0.33F));
    public static final RegistryObject<AccessoryItem> OBSIDIAN_SHIELD = accessory("obsidian_shield", TerraRarity.ORANGE, 35000,
        StatEffects.builder().add(Stat.DEFENSE, 2).ability(Ability.KNOCKBACK_IMMUNE).ability(Ability.FIRE_BLOCK_IMMUNE));
    public static final RegistryObject<AccessoryItem> OBSIDIAN_WATER_WALKING_BOOTS = accessory("obsidian_water_walking_boots", TerraRarity.ORANGE, 30000,
        StatEffects.builder().ability(Ability.WATER_WALKING).ability(Ability.FIRE_BLOCK_IMMUNE));
    public static final RegistryObject<AccessoryItem> LAVA_WADERS = accessory("lava_waders", TerraRarity.LIGHT_RED, 50000,
        StatEffects.builder().ability(Ability.WATER_WALKING).ability(Ability.LAVA_WALKING).ability(Ability.FIRE_BLOCK_IMMUNE)
            .add(Stat.LAVA_IMMUNITY_SECONDS, 7));
    public static final RegistryObject<AccessoryItem> MANA_FLOWER = accessory("mana_flower", TerraRarity.ORANGE, 30000,
        StatEffects.builder().add(Stat.MANA_COST, -0.08F).ability(Ability.MANA_FLOWER));

    // Wings: flight ticks (Terraria's flight time / 3) and maximum rising speed
    /** Pre-Hardmode wings (Terraria: Skyware chests; here: underground chests). Mostly for gliding. */
    public static final RegistryObject<WingsItem> FLEDGLING_WINGS = wings("fledgling_wings", "fledgling", TerraRarity.GREEN, 20000, 12, 0.3F);
    public static final RegistryObject<WingsItem> ANGEL_WINGS = wings("angel_wings", "angel", TerraRarity.PINK, 80000, 34, 0.42F);
    public static final RegistryObject<WingsItem> DEMON_WINGS = wings("demon_wings", "demon", TerraRarity.PINK, 80000, 34, 0.42F);
    public static final RegistryObject<WingsItem> LEAF_WINGS = wings("leaf_wings", "leaf", TerraRarity.LIME, 160000, 54, 0.45F);

    public static final RegistryObject<TerraItem> PIXIE_DUST = CoreItems.material("pixie_dust", TerraRarity.ORANGE, 100);
    public static final RegistryObject<TerraItem> UNICORN_HORN = CoreItems.material("unicorn_horn", TerraRarity.ORANGE, 300);
    public static final RegistryObject<AccessoryItem> TITAN_GLOVE = accessory("titan_glove", TerraRarity.LIGHT_RED, 100000,
        StatEffects.builder().add(Stat.KNOCKBACK, 1.0F).ability(Ability.AUTO_REUSE));
    public static final RegistryObject<TerraItem> SOUL_OF_LIGHT = CoreItems.material("soul_of_light", TerraRarity.ORANGE, 800);
    public static final RegistryObject<TerraItem> SOUL_OF_NIGHT = CoreItems.material("soul_of_night", TerraRarity.ORANGE, 800);
    public static final RegistryObject<TerraItem> SOUL_OF_FLIGHT = CoreItems.material("soul_of_flight", TerraRarity.ORANGE, 800);

    private static RegistryObject<WingsItem> wings(String name, String style, TerraRarity rarity, int value, int flightTicks, float ascent) {
        WingsItem.Flight flight = new WingsItem.Flight(style, flightTicks, ascent);
        return ModItems.register(name, TabGroup.ACCESSORIES, p -> new WingsItem(p, StatEffects.NONE, flight),
            p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(rarity).value(value).build()));
    }

    private static RegistryObject<AccessoryItem> accessory(String name, TerraRarity rarity, int value, StatEffects.Builder effects) {
        StatEffects built = effects.build();
        return ModItems.register(name, TabGroup.ACCESSORIES, p -> new AccessoryItem(p, built),
            p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(rarity).value(value).build()));
    }
}
