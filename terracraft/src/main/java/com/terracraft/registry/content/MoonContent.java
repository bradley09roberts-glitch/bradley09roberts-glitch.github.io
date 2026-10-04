package com.terracraft.registry.content;

import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.consumable.EventSummonItem;
import com.terracraft.item.weapon.AmmoType;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.ProjectileSwordItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import com.terracraft.world.event.TerrariaEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;

/**
 * Pumpkin Moon and Frost Moon: the Pumpkin Moon Medallion and the Naughty Present that call them (at night, in
 * Hardmode) and the treasures of their bosses - Mourning Wood, Pumpking, Everscream, Santa-NK1 and the Ice Queen.
 */
public final class MoonContent {
    public static final RegistryObject<EventSummonItem> PUMPKIN_MOON_MEDALLION = ModItems.register("pumpkin_moon_medallion", TabGroup.CONSUMABLES,
        p -> new EventSummonItem(p, () -> TerrariaEvents.PUMPKIN_MOON, true), p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.YELLOW, 0)));
    public static final RegistryObject<EventSummonItem> NAUGHTY_PRESENT = ModItems.register("naughty_present", TabGroup.CONSUMABLES,
        p -> new EventSummonItem(p, () -> TerrariaEvents.FROST_MOON, true), p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.YELLOW, 0)));
    public static final RegistryObject<TerraItem> SPOOKY_WOOD = CoreItems.material("spooky_wood", TerraRarity.YELLOW, 50);

    // Pumpkin Moon
    /** Mourning Wood: Stake Launcher (here it fires arrows hard and fast). */
    public static final RegistryObject<RangedWeaponItem> STAKE_LAUNCHER = gun("stake_launcher", AmmoType.ARROW, SoundEvents.CROSSBOW_SHOOT, 75, 20, 6.5F, 13.0F);
    /** Pumpking: The Horseman's Blade, whose swings throw flaming pumpkin heads. */
    public static final RegistryObject<ProjectileSwordItem> THE_HORSEMANS_BLADE = sword("the_horsemans_blade", () -> ProjectileKinds.PUMPKIN_HEAD, 75, 20, 7.5F);
    public static final RegistryObject<MagicWeaponItem> BAT_SCEPTER = staff("bat_scepter", ProjectileKinds.BAT, 3, 0.4F, 45, 6, 20);
    public static final RegistryObject<RangedWeaponItem> CANDY_CORN_RIFLE = gun("candy_corn_rifle", AmmoType.BULLET, SoundEvents.CROSSBOW_SHOOT, 44, 9, 2.0F, 12.0F);
    // Frost Moon
    public static final RegistryObject<ProjectileSwordItem> CHRISTMAS_TREE_SWORD = sword("christmas_tree_sword", () -> ProjectileKinds.ORNAMENT, 100, 23, 7.0F);
    public static final RegistryObject<MagicWeaponItem> RAZORPINE = staff("razorpine", ProjectileKinds.PINE_NEEDLE, 1, 0.0F, 57, 5, 6);
    public static final RegistryObject<RangedWeaponItem> CHAIN_GUN = gun("chain_gun", AmmoType.BULLET, SoundEvents.CROSSBOW_SHOOT, 31, 4, 1.0F, 14.0F);
    public static final RegistryObject<MagicWeaponItem> ELF_MELTER = staff("elf_melter", ProjectileKinds.ELF_FLAME, 1, 0.1F, 50, 2, 5);
    public static final RegistryObject<ProjectileSwordItem> NORTH_POLE = sword("north_pole", () -> ProjectileKinds.SNOWFLAKE, 90, 20, 6.0F);
    public static final RegistryObject<MagicWeaponItem> BLIZZARD_STAFF = staff("blizzard_staff", ProjectileKinds.ICE_SHARD, 3, 0.3F, 58, 7, 10);

    private MoonContent() {}

    public static void init() {}

    private static RegistryObject<ProjectileSwordItem> sword(String name, Supplier<ProjectileKind> kind, int damage, int useTime, float knockback) {
        return ModItems.register(name, TabGroup.WEAPONS, p -> new ProjectileSwordItem(p, kind, 0.8F, 1, 0.0F),
            p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(damage).useTime(useTime).knockback(knockback).crit(4).velocity(10.0F)
                .rarity(TerraRarity.YELLOW).value(200000).build()));
    }

    private static RegistryObject<RangedWeaponItem> gun(String name, AmmoType ammo, SoundEvent sound, int damage, int useTime, float knockback, float velocity) {
        return ModItems.register(name, TabGroup.WEAPONS, p -> new RangedWeaponItem(p, ammo, sound, 2.0F, 1, 0.0F),
            p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().ranged(damage).useTime(useTime).knockback(knockback).crit(4)
                .velocity(velocity).rarity(TerraRarity.YELLOW).value(200000).build()));
    }

    private static RegistryObject<MagicWeaponItem> staff(String name, ProjectileKind kind, int count, float spread, int damage, int mana, int useTime) {
        return ModItems.register(name, TabGroup.WEAPONS, p -> new MagicWeaponItem(p, kind, count, spread),
            p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(damage).mana(mana).useTime(useTime).knockback(3.0F).crit(4)
                .velocity(12.0F).rarity(TerraRarity.YELLOW).value(200000).build()));
    }
}
