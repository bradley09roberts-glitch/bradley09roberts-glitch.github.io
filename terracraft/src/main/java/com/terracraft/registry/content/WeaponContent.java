package com.terracraft.registry.content;

import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.AmmoInfo;
import com.terracraft.item.weapon.AmmoItem;
import com.terracraft.item.weapon.AmmoType;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.MeleeWeaponItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.item.weapon.ThrownWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.registries.RegistryObject;

/**
 * Weapons and ammunition. Each weapon is one line: name + Terraria stats (damage, use time in 1/60 s,
 * knockback, crit, mana, velocity, rarity, value in copper).
 */
public final class WeaponContent {
    // ---------------------------------------------------------------- melee (pre-hardmode ore swords)
    public static final RegistryObject<MeleeWeaponItem> WOODEN_SWORD = sword("wooden_sword", 7, 25, 5.0F, 0, TerraRarity.WHITE, 20);
    public static final RegistryObject<MeleeWeaponItem> COPPER_SHORTSWORD = sword("copper_shortsword", 5, 13, 4.0F, 0, TerraRarity.WHITE, 70);
    public static final RegistryObject<MeleeWeaponItem> COPPER_BROADSWORD = sword("copper_broadsword", 8, 23, 5.0F, 0, TerraRarity.WHITE, 90);
    public static final RegistryObject<MeleeWeaponItem> TIN_BROADSWORD = sword("tin_broadsword", 9, 22, 5.0F, 0, TerraRarity.WHITE, 135);
    public static final RegistryObject<MeleeWeaponItem> IRON_BROADSWORD = sword("iron_broadsword", 10, 21, 5.5F, 0, TerraRarity.WHITE, 270);
    public static final RegistryObject<MeleeWeaponItem> LEAD_BROADSWORD = sword("lead_broadsword", 11, 21, 5.5F, 0, TerraRarity.WHITE, 405);
    public static final RegistryObject<MeleeWeaponItem> SILVER_BROADSWORD = sword("silver_broadsword", 11, 20, 6.0F, 0, TerraRarity.WHITE, 540);
    public static final RegistryObject<MeleeWeaponItem> TUNGSTEN_BROADSWORD = sword("tungsten_broadsword", 12, 20, 6.0F, 0, TerraRarity.WHITE, 810);
    public static final RegistryObject<MeleeWeaponItem> GOLD_BROADSWORD = sword("gold_broadsword", 13, 20, 6.0F, 0, TerraRarity.WHITE, 1080);
    public static final RegistryObject<MeleeWeaponItem> PLATINUM_BROADSWORD = sword("platinum_broadsword", 15, 19, 6.0F, 0, TerraRarity.WHITE, 1620);

    // evil ore swords
    public static final RegistryObject<MeleeWeaponItem> LIGHTS_BANE = sword("lights_bane", 17, 20, 5.0F, 0, TerraRarity.BLUE, 2700);
    public static final RegistryObject<MeleeWeaponItem> BLOOD_BUTCHERER = sword("blood_butcherer", 22, 25, 5.0F, 0, TerraRarity.BLUE, 2700);

    // ---------------------------------------------------------------- ranged
    public static final RegistryObject<RangedWeaponItem> WOODEN_BOW = bow("wooden_bow", 4, 28, 0.0F, 6.6F, TerraRarity.WHITE, 100);
    public static final RegistryObject<RangedWeaponItem> COPPER_BOW = bow("copper_bow", 6, 27, 0.0F, 6.6F, TerraRarity.WHITE, 150);
    public static final RegistryObject<RangedWeaponItem> IRON_BOW = bow("iron_bow", 8, 26, 0.0F, 6.6F, TerraRarity.WHITE, 400);
    public static final RegistryObject<RangedWeaponItem> GOLD_BOW = bow("gold_bow", 9, 25, 0.0F, 6.8F, TerraRarity.WHITE, 1600);
    public static final RegistryObject<RangedWeaponItem> FLINTLOCK_PISTOL = ModItems.register("flintlock_pistol", TabGroup.WEAPONS,
        p -> new RangedWeaponItem(p, AmmoType.BULLET, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0F, 1, 0.0F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().ranged(10).useTime(13).knockback(1.5F).crit(6).velocity(6.0F)
            .rarity(TerraRarity.BLUE).value(2, 0, 0).build()));

    public static final RegistryObject<RangedWeaponItem> DEMON_BOW = bow("demon_bow", 14, 25, 0.0F, 6.7F, TerraRarity.BLUE, 5400);
    public static final RegistryObject<RangedWeaponItem> TENDON_BOW = bow("tendon_bow", 19, 25, 0.0F, 6.7F, TerraRarity.BLUE, 5400);
    public static final RegistryObject<RangedWeaponItem> MUSKET = ranged("musket", AmmoType.BULLET, SoundEvents.FIREWORK_ROCKET_BLAST, 31, 36, 5.25F, 9.0F,
        TerraRarity.GREEN, 20000);
    public static final RegistryObject<RangedWeaponItem> THE_UNDERTAKER = ranged("the_undertaker", AmmoType.BULLET, SoundEvents.FIREWORK_ROCKET_BLAST, 18, 22,
        2.0F, 9.0F, TerraRarity.GREEN, 20000);

    // ---------------------------------------------------------------- thrown
    public static final RegistryObject<ThrownWeaponItem> SHURIKEN = thrown("shuriken", ProjectileKinds.SHURIKEN, true, 12, 15, 2.0F, 9.0F, 15);
    public static final RegistryObject<ThrownWeaponItem> THROWING_KNIFE = thrown("throwing_knife", ProjectileKinds.THROWING_KNIFE, true, 12, 15, 2.0F, 10.0F, 15);
    public static final RegistryObject<ThrownWeaponItem> WOODEN_BOOMERANG = thrown("wooden_boomerang", ProjectileKinds.WOODEN_BOOMERANG, false, 8, 15, 8.0F, 6.5F, 1000);

    // ---------------------------------------------------------------- magic
    public static final RegistryObject<MagicWeaponItem> WAND_OF_SPARKING = magic("wand_of_sparking", ProjectileKinds.SPARK, 9, 2, 30, 0.0F, 9.0F, TerraRarity.WHITE, 1, 0, 0);
    public static final RegistryObject<MagicWeaponItem> AMETHYST_STAFF = magic("amethyst_staff", ProjectileKinds.AMETHYST_BOLT, 14, 3, 37, 3.25F, 6.0F, TerraRarity.WHITE, 0, 20, 0);
    public static final RegistryObject<MagicWeaponItem> MAGIC_MISSILE = magic("magic_missile", ProjectileKinds.MAGIC_MISSILE, 22, 10, 25, 3.0F, 6.0F, TerraRarity.GREEN, 2, 0, 0);

    public static final RegistryObject<MagicWeaponItem> VILETHORN = magic("vilethorn", ProjectileKinds.VILETHORN, 10, 10, 28, 1.0F, 8.0F, TerraRarity.GREEN, 2, 0, 0);

    // ---------------------------------------------------------------- dungeon
    public static final RegistryObject<MeleeWeaponItem> MURAMASA = sword("muramasa", 19, 20, 1.0F, 0, TerraRarity.GREEN, 50000);
    public static final RegistryObject<RangedWeaponItem> HANDGUN = ranged("handgun", AmmoType.BULLET, SoundEvents.FIREWORK_ROCKET_BLAST, 17, 12,
        3.0F, 10.0F, TerraRarity.GREEN, 50000);
    public static final RegistryObject<MagicWeaponItem> AQUA_SCEPTER = magic("aqua_scepter", ProjectileKinds.AQUA_STREAM, 15, 7, 16, 5.0F, 12.5F,
        TerraRarity.GREEN, 5, 0, 0);
    public static final RegistryObject<MagicWeaponItem> WATER_BOLT = magic("water_bolt", ProjectileKinds.WATER_BOLT, 19, 10, 17, 5.0F, 4.5F,
        TerraRarity.GREEN, 5, 0, 0);
    public static final RegistryObject<MagicWeaponItem> BOOK_OF_SKULLS = magic("book_of_skulls", ProjectileKinds.BOOK_SKULL, 29, 18, 26, 7.5F, 3.5F,
        TerraRarity.GREEN, 5, 0, 0);

    // ---------------------------------------------------------------- ammo
    public static final RegistryObject<AmmoItem> FLAMING_ARROW = ammo("flaming_arrow", AmmoType.ARROW, 7, 2.0F, 0.5F, ProjectileKinds.FLAMING_ARROW, 15);
    public static final RegistryObject<AmmoItem> MUSKET_BALL = ammo("musket_ball", AmmoType.BULLET, 7, 2.0F, 4.0F, ProjectileKinds.MUSKET_BALL, 7);

    private WeaponContent() {}

    public static void init() {}

    private static RegistryObject<MeleeWeaponItem> sword(String name, int damage, int useTime, float knockback, int crit, TerraRarity rarity, int value) {
        TerraItemStats stats = TerraItemStats.builder().melee(damage).useTime(useTime).knockback(knockback).crit(crit).rarity(rarity).value(value).build();
        return ModItems.register(name, TabGroup.WEAPONS, MeleeWeaponItem::new, p -> WeaponProperties.melee(p, stats));
    }

    private static RegistryObject<RangedWeaponItem> bow(String name, int damage, int useTime, float knockback, float velocity, TerraRarity rarity, int value) {
        return ranged(name, AmmoType.ARROW, SoundEvents.ARROW_SHOOT, damage, useTime, knockback, velocity, rarity, value);
    }

    private static RegistryObject<RangedWeaponItem> ranged(String name, AmmoType ammo, SoundEvent sound, int damage, int useTime, float knockback,
                                                          float velocity, TerraRarity rarity, int value) {
        TerraItemStats stats = TerraItemStats.builder().ranged(damage).useTime(useTime).knockback(knockback).velocity(velocity).rarity(rarity).value(value).build();
        return ModItems.register(name, TabGroup.WEAPONS, p -> new RangedWeaponItem(p, ammo, sound, 1.0F, 1, 0.0F),
            p -> WeaponProperties.stats(p.stacksTo(1), stats));
    }

    private static RegistryObject<ThrownWeaponItem> thrown(String name, ProjectileKind kind, boolean consumable, int damage, int useTime,
                                                          float knockback, float velocity, int value) {
        TerraItemStats stats = TerraItemStats.builder().ranged(damage).useTime(useTime).knockback(knockback).velocity(velocity).value(value).build();
        return ModItems.register(name, TabGroup.WEAPONS, p -> new ThrownWeaponItem(p, kind, consumable),
            p -> WeaponProperties.stats(p.stacksTo(consumable ? 99 : 1), stats));
    }

    private static RegistryObject<MagicWeaponItem> magic(String name, ProjectileKind kind, int damage, int mana, int useTime, float knockback,
                                                        float velocity, TerraRarity rarity, int gold, int silver, int copper) {
        TerraItemStats stats = TerraItemStats.builder().magic(damage).mana(mana).useTime(useTime).knockback(knockback).velocity(velocity)
            .rarity(rarity).value(gold, silver, copper).build();
        return ModItems.register(name, TabGroup.WEAPONS, p -> new MagicWeaponItem(p, kind, 1, 0.0F), p -> WeaponProperties.stats(p.stacksTo(1), stats));
    }

    private static RegistryObject<AmmoItem> ammo(String name, AmmoType type, int damage, float knockback, float velocity, ProjectileKind kind, int value) {
        TerraItemStats stats = TerraItemStats.builder().ranged(damage).knockback(knockback).value(value).build();
        return ModItems.register(name, TabGroup.WEAPONS, p -> new AmmoItem(p, new AmmoInfo(type, damage, knockback, velocity, kind)),
            p -> WeaponProperties.stats(p.stacksTo(99), stats));
    }
}
