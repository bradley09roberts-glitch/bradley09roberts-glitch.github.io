package com.terracraft.registry.content;

import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.coin.CoinItem;
import com.terracraft.item.consumable.PermanentUpgradeItem;
import com.terracraft.item.consumable.TerraPotionItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModEffects;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/** Coins, life/mana upgrades, potions and general materials. */
public final class CoreItems {
    // ---------------------------------------------------------------- coins
    public static final RegistryObject<CoinItem> COPPER_COIN = coin("copper_coin", 1);
    public static final RegistryObject<CoinItem> SILVER_COIN = coin("silver_coin", 100);
    public static final RegistryObject<CoinItem> GOLD_COIN = coin("gold_coin", 10_000);
    public static final RegistryObject<CoinItem> PLATINUM_COIN = coin("platinum_coin", 1_000_000);

    // ---------------------------------------------------------------- permanent upgrades
    public static final RegistryObject<PermanentUpgradeItem> LIFE_CRYSTAL = ModItems.register("life_crystal", TabGroup.CONSUMABLES,
        p -> new PermanentUpgradeItem(p, PermanentUpgradeItem.Kind.LIFE_CRYSTAL),
        p -> WeaponProperties.stats(p.stacksTo(99), stats(TerraRarity.GREEN, 7500)));
    public static final RegistryObject<PermanentUpgradeItem> LIFE_FRUIT = ModItems.register("life_fruit", TabGroup.CONSUMABLES,
        p -> new PermanentUpgradeItem(p, PermanentUpgradeItem.Kind.LIFE_FRUIT),
        p -> WeaponProperties.stats(p.stacksTo(99), stats(TerraRarity.LIME, 15000)));
    public static final RegistryObject<PermanentUpgradeItem> MANA_CRYSTAL = ModItems.register("mana_crystal", TabGroup.CONSUMABLES,
        p -> new PermanentUpgradeItem(p, PermanentUpgradeItem.Kind.MANA_CRYSTAL),
        p -> WeaponProperties.stats(p.stacksTo(99), stats(TerraRarity.GREEN, 5000)));

    // ---------------------------------------------------------------- materials
    public static final RegistryObject<TerraItem> FALLEN_STAR = material("fallen_star", TerraRarity.BLUE, 500);
    public static final RegistryObject<TerraItem> GEL = material("gel", TerraRarity.WHITE, 1);
    public static final RegistryObject<TerraItem> LENS = material("lens", TerraRarity.WHITE, 100);
    public static final RegistryObject<TerraItem> AMETHYST = material("amethyst", TerraRarity.WHITE, 750);

    // ---------------------------------------------------------------- potions
    public static final RegistryObject<TerraPotionItem> LESSER_HEALING_POTION = potion("lesser_healing_potion", 50, 0, null, 0, TerraRarity.BLUE, 300);
    public static final RegistryObject<TerraPotionItem> HEALING_POTION = potion("healing_potion", 100, 0, null, 0, TerraRarity.ORANGE, 2000);
    public static final RegistryObject<TerraPotionItem> GREATER_HEALING_POTION = potion("greater_healing_potion", 150, 0, null, 0, TerraRarity.ORANGE, 5000);
    public static final RegistryObject<TerraPotionItem> LESSER_MANA_POTION = potion("lesser_mana_potion", 0, 50, null, 0, TerraRarity.BLUE, 250);
    public static final RegistryObject<TerraPotionItem> MANA_POTION = potion("mana_potion", 0, 100, null, 0, TerraRarity.ORANGE, 500);
    public static final RegistryObject<TerraPotionItem> GREATER_MANA_POTION = potion("greater_mana_potion", 0, 200, null, 0, TerraRarity.ORANGE, 1000);
    public static final RegistryObject<TerraPotionItem> IRONSKIN_POTION = buffPotion("ironskin_potion", ModEffects.IRONSKIN, 8);
    public static final RegistryObject<TerraPotionItem> SWIFTNESS_POTION = buffPotion("swiftness_potion", ModEffects.SWIFTNESS, 8);
    public static final RegistryObject<TerraPotionItem> REGENERATION_POTION = buffPotion("regeneration_potion", ModEffects.REGENERATION, 8);
    public static final RegistryObject<TerraPotionItem> MANA_REGENERATION_POTION = buffPotion("mana_regeneration_potion", ModEffects.MANA_REGENERATION, 7);
    public static final RegistryObject<TerraPotionItem> MAGIC_POWER_POTION = buffPotion("magic_power_potion", ModEffects.MAGIC_POWER, 2);
    public static final RegistryObject<TerraPotionItem> ARCHERY_POTION = buffPotion("archery_potion", ModEffects.ARCHERY, 4);
    public static final RegistryObject<TerraPotionItem> MINING_POTION = buffPotion("mining_potion", ModEffects.MINING, 8);
    public static final RegistryObject<TerraPotionItem> OBSIDIAN_SKIN_POTION = buffPotion("obsidian_skin_potion", ModEffects.OBSIDIAN_SKIN, 4);
    public static final RegistryObject<TerraPotionItem> WATER_WALKING_POTION = buffPotion("water_walking_potion", ModEffects.WATER_WALKING, 5);
    public static final RegistryObject<TerraPotionItem> ENDURANCE_POTION = buffPotion("endurance_potion", ModEffects.ENDURANCE, 4);
    public static final RegistryObject<TerraPotionItem> WRATH_POTION = buffPotion("wrath_potion", ModEffects.WRATH, 4);
    public static final RegistryObject<TerraPotionItem> RAGE_POTION = buffPotion("rage_potion", ModEffects.RAGE, 4);

    private CoreItems() {}

    public static void init() {}

    static TerraItemStats stats(TerraRarity rarity, int value) {
        return TerraItemStats.builder().rarity(rarity).value(value).build();
    }

    private static RegistryObject<CoinItem> coin(String name, long value) {
        return ModItems.register(name, TabGroup.MATERIALS, p -> new CoinItem(p, value),
            p -> WeaponProperties.stats(p, stats(TerraRarity.WHITE, (int) Math.min(Integer.MAX_VALUE, value * 5))));
    }

    static RegistryObject<TerraItem> material(String name, TerraRarity rarity, int value) {
        return ModItems.register(name, TabGroup.MATERIALS, TerraItem::new, p -> WeaponProperties.stats(p.stacksTo(99), stats(rarity, value)));
    }

    private static RegistryObject<TerraPotionItem> potion(String name, int heal, int mana, RegistryObject<MobEffect> buff, int minutes,
                                                          TerraRarity rarity, int value) {
        return ModItems.register(name, TabGroup.CONSUMABLES, p -> new TerraPotionItem(p, heal, mana, buff, minutes * 60 * 20),
            p -> WeaponProperties.stats(TerraPotionItem.drink(p), stats(rarity, value)));
    }

    private static RegistryObject<TerraPotionItem> buffPotion(String name, RegistryObject<MobEffect> buff, int minutes) {
        return potion(name, 0, 0, buff, minutes, TerraRarity.BLUE, 1000);
    }
}
