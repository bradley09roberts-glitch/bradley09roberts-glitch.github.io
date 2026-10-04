package com.terracraft.registry.content;

import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.accessory.WingsItem;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.ProjectileSwordItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;

/** The Empress of Light's treasure: Nightglow, Starlight and the Empress Wings. */
public final class EmpressContent {
    /** Nightglow: a staff that releases homing lights. */
    public static final RegistryObject<MagicWeaponItem> NIGHTGLOW = ModItems.register("nightglow", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.NIGHTGLOW, 3, 0.5F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(60).mana(10).useTime(20).knockback(2.0F).crit(4).velocity(8.0F)
            .rarity(TerraRarity.YELLOW).value(250000).build()));
    /** Starlight: rapid thrusts that throw shards of light. */
    public static final RegistryObject<ProjectileSwordItem> STARLIGHT = ModItems.register("starlight", TabGroup.WEAPONS,
        p -> new ProjectileSwordItem(p, () -> ProjectileKinds.STARLIGHT, 0.6F, 2, 0.1F),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(90).useTime(8).knockback(2.5F).crit(4).velocity(14.0F)
            .rarity(TerraRarity.YELLOW).value(250000).build()));
    public static final RegistryObject<WingsItem> EMPRESS_WINGS = ModItems.register("empress_wings", TabGroup.ACCESSORIES,
        p -> new WingsItem(p, StatEffects.NONE, new WingsItem.Flight("empress", 80, 0.5F)),
        p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.YELLOW).value(400000).build()));

    private EmpressContent() {}

    public static void init() {}
}
