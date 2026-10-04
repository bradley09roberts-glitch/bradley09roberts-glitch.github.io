package com.terracraft.registry.content;

import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.consumable.EventSummonItem;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.ProjectileSwordItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import com.terracraft.world.event.TerrariaEvents;
import net.minecraft.sounds.SoundEvents;

/**
 * The Solar Eclipse's treasures (Death Sickle, Butcher's Chainsaw, Nail Gun, Toxic Flask, the Deadly Sphere Staff in
 * SummonContent, Broken Hero Sword) and what they lead to: the Solar Tablet that calls an eclipse, True Excalibur
 * and the Terra Blade.
 */
public final class EclipseContent {
    public static final RegistryObject<TerraItem> SOLAR_TABLET_FRAGMENT = CoreItems.material("solar_tablet_fragment", TerraRarity.LIME, 2000);
    public static final RegistryObject<TerraItem> BROKEN_HERO_SWORD = CoreItems.material("broken_hero_sword", TerraRarity.YELLOW, 50000);
    public static final RegistryObject<EventSummonItem> SOLAR_TABLET = ModItems.register("solar_tablet", TabGroup.CONSUMABLES,
        p -> new EventSummonItem(p, () -> TerrariaEvents.SOLAR_ECLIPSE, true),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.LIME, 30000)));

    public static final RegistryObject<ProjectileSwordItem> DEATH_SICKLE = ModItems.register("death_sickle", TabGroup.WEAPONS,
        p -> new ProjectileSwordItem(p, () -> ProjectileKinds.DEATH_SICKLE, 1.0F, 1, 0.0F),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(70).useTime(25).knockback(7.0F).crit(4).velocity(9.0F)
            .rarity(TerraRarity.YELLOW).value(200000).build()));
    public static final RegistryObject<ProjectileSwordItem> BUTCHERS_CHAINSAW = ModItems.register("butchers_chainsaw", TabGroup.WEAPONS,
        p -> new ProjectileSwordItem(p, () -> ProjectileKinds.TRUE_EXCALIBUR_BEAM, 0.0F, 0, 0.0F),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(80).useTime(8).knockback(2.0F).crit(4).velocity(1.0F)
            .rarity(TerraRarity.YELLOW).value(200000).build()));
    public static final RegistryObject<MagicWeaponItem> NAIL_GUN = ModItems.register("nail_gun", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.PLAYER_NAIL, 1, 0.0F, SoundEvents.CROSSBOW_SHOOT),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().ranged(85).useTime(18).knockback(3.0F).crit(4).velocity(12.0F)
            .rarity(TerraRarity.YELLOW).value(200000).build()));
    public static final RegistryObject<MagicWeaponItem> TOXIC_FLASK = ModItems.register("toxic_flask", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.PLAYER_TOXIC_FLASK, 1, 0.0F, SoundEvents.SPLASH_POTION_THROW),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(40).mana(10).useTime(25).knockback(2.0F).crit(4)
            .velocity(9.0F).rarity(TerraRarity.YELLOW).value(200000).build()));
    public static final RegistryObject<ProjectileSwordItem> TRUE_EXCALIBUR = ModItems.register("true_excalibur", TabGroup.WEAPONS,
        p -> new ProjectileSwordItem(p, () -> ProjectileKinds.TRUE_EXCALIBUR_BEAM, 0.75F, 1, 0.0F),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(72).useTime(18).knockback(4.5F).crit(4).velocity(11.0F)
            .rarity(TerraRarity.YELLOW).value(400000).build()));
    public static final RegistryObject<ProjectileSwordItem> TERRA_BLADE = ModItems.register("terra_blade", TabGroup.WEAPONS,
        p -> new ProjectileSwordItem(p, () -> ProjectileKinds.TERRA_BEAM, 1.0F, 1, 0.0F),
        p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(95).useTime(14).knockback(6.5F).crit(4).velocity(15.0F)
            .rarity(TerraRarity.YELLOW).value(500000).build()));

    private EclipseContent() {}

    public static void init() {}
}
