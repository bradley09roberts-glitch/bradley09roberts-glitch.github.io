package com.terracraft.registry.content;

import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.accessory.WingsItem;
import com.terracraft.item.consumable.BossSummonItem;
import com.terracraft.item.tool.TerrariaToolItem;
import com.terracraft.item.weapon.AmmoType;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.ProjectileSwordItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.item.weapon.ThrownWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.stats.Stat;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

/**
 * The endgame: the Ancient Manipulator (the Lunatic Cultist's crafting station), the four Celestial Fragments the
 * pillars drop, Luminite from Moon Lord, the Celestial Sigil, the four fragment armors, weapons, picks and wings,
 * and Moon Lord's treasures.
 */
public final class LunarContent {
    public static final RegistryObject<Block> ANCIENT_MANIPULATOR = ModBlocks.register("ancient_manipulator", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(3.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 10));

    public static final RegistryObject<TerraItem> SOLAR_FRAGMENT = CoreItems.material("solar_fragment", TerraRarity.CYAN, 2000);
    public static final RegistryObject<TerraItem> VORTEX_FRAGMENT = CoreItems.material("vortex_fragment", TerraRarity.CYAN, 2000);
    public static final RegistryObject<TerraItem> NEBULA_FRAGMENT = CoreItems.material("nebula_fragment", TerraRarity.CYAN, 2000);
    public static final RegistryObject<TerraItem> STARDUST_FRAGMENT = CoreItems.material("stardust_fragment", TerraRarity.CYAN, 2000);
    public static final RegistryObject<TerraItem> LUMINITE = CoreItems.material("luminite", TerraRarity.CYAN, 3000);
    public static final RegistryObject<TerraItem> LUMINITE_BAR = CoreItems.material("luminite_bar", TerraRarity.CYAN, 12000);
    public static final RegistryObject<BossSummonItem> CELESTIAL_SIGIL = ModItems.register("celestial_sigil", TabGroup.CONSUMABLES,
        p -> new BossSummonItem(p, MobContent.MOON_LORD, BossSummoning.Arrival.NEARBY, false),
        p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.CYAN, 0)));

    // armor - set bonuses are plain stat boosts here
    public static final ArmorContent.ArmorPieces SOLAR_FLARE = armor("solar_flare", new int[]{24, 34, 20}, Stat.MELEE_DAMAGE,
        StatEffects.builder().add(Stat.ENDURANCE, 0.30F).add(Stat.MELEE_SPEED, 0.15F).build());
    public static final ArmorContent.ArmorPieces VORTEX = armor("vortex", new int[]{14, 22, 16}, Stat.RANGED_DAMAGE,
        StatEffects.builder().add(Stat.RANGED_CRIT, 20).add(Stat.RANGED_DAMAGE, 0.20F).build());
    public static final ArmorContent.ArmorPieces NEBULA = armor("nebula", new int[]{14, 18, 14}, Stat.MAGIC_DAMAGE,
        StatEffects.builder().add(Stat.MAX_MANA, 100).add(Stat.MANA_REGEN, 10).add(Stat.MAGIC_DAMAGE, 0.15F).build());
    public static final ArmorContent.ArmorPieces STARDUST = armor("stardust", new int[]{12, 18, 14}, Stat.SUMMON_DAMAGE,
        StatEffects.builder().add(Stat.MAX_MINIONS, 2).add(Stat.SUMMON_DAMAGE, 0.22F).build());

    // fragment weapons
    public static final RegistryObject<ProjectileSwordItem> SOLAR_ERUPTION = sword("solar_eruption", () -> ProjectileKinds.SOLAR_ERUPTION, 105, 16, 1.0F, 1);
    public static final RegistryObject<ThrownWeaponItem> DAYBREAK = ModItems.register("daybreak", TabGroup.WEAPONS,
        p -> new ThrownWeaponItem(p, ProjectileKinds.DAYBREAK, false),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().melee(150).useTime(16).knockback(6.0F).crit(4).velocity(18.0F)
            .rarity(TerraRarity.RED).value(500000).build()));
    public static final RegistryObject<RangedWeaponItem> VORTEX_BEATER = gun("vortex_beater", AmmoType.BULLET, 50, 5, 1, 0.0F);
    public static final RegistryObject<RangedWeaponItem> PHANTASM = gun("phantasm", AmmoType.ARROW, 50, 12, 4, 0.08F);
    public static final RegistryObject<MagicWeaponItem> NEBULA_BLAZE = staff("nebula_blaze", ProjectileKinds.NEBULA_BLAZE, 1, 0.0F, 110, 14, 30);
    public static final RegistryObject<MagicWeaponItem> NEBULA_ARCANUM = staff("nebula_arcanum", ProjectileKinds.NEBULA_ARCANUM, 1, 0.0F, 98, 20, 30);
    // the Stardust Dragon and Stardust Cell staffs are summon staffs (SummonContent)
    // tools
    public static final RegistryObject<TerrariaToolItem> SOLAR_FLARE_PICKAXE = ToolContent.hardmodePickaxe("solar_flare_pickaxe", 225, 80, TerraRarity.RED, 500000);
    public static final RegistryObject<TerrariaToolItem> VORTEX_PICKAXE = ToolContent.hardmodePickaxe("vortex_pickaxe", 225, 80, TerraRarity.RED, 500000);
    public static final RegistryObject<TerrariaToolItem> NEBULA_PICKAXE = ToolContent.hardmodePickaxe("nebula_pickaxe", 225, 80, TerraRarity.RED, 500000);
    public static final RegistryObject<TerrariaToolItem> STARDUST_PICKAXE = ToolContent.hardmodePickaxe("stardust_pickaxe", 225, 80, TerraRarity.RED, 500000);
    // wings
    public static final RegistryObject<WingsItem> SOLAR_WINGS = wings("solar_wings", "solar");
    public static final RegistryObject<WingsItem> VORTEX_WINGS = wings("vortex_wings", "vortex");
    public static final RegistryObject<WingsItem> NEBULA_WINGS = wings("nebula_wings", "nebula");
    public static final RegistryObject<WingsItem> STARDUST_WINGS = wings("stardust_wings", "stardust");

    // Moon Lord's treasure
    public static final RegistryObject<ProjectileSwordItem> MEOWMERE = sword("meowmere", () -> ProjectileKinds.MEOWMERE, 200, 16, 6.5F, 1);
    public static final RegistryObject<ProjectileSwordItem> STAR_WRATH = sword("star_wrath", () -> ProjectileKinds.STAR_WRATH, 110, 16, 6.5F, 3);
    public static final RegistryObject<RangedWeaponItem> SDMG = gun("sdmg", AmmoType.BULLET, 85, 4, 1, 0.02F);
    public static final RegistryObject<MagicWeaponItem> LAST_PRISM = staff("last_prism", ProjectileKinds.LAST_PRISM, 1, 0.0F, 100, 6, 4);
    public static final RegistryObject<MagicWeaponItem> LUNAR_FLARE = staff("lunar_flare", ProjectileKinds.LUNAR_FLARE, 3, 0.4F, 90, 13, 20);

    private LunarContent() {}

    public static void init() {}

    private static ArmorContent.ArmorPieces armor(String set, int[] defense, Stat damage, StatEffects bonus) {
        return ArmorContent.named(set, new String[]{set + "_helmet", set + "_breastplate", set + "_leggings"}, defense,
            new StatEffects[]{StatEffects.builder().add(damage, 0.17F).add(Stat.CRIT, 7).build(),
                StatEffects.builder().add(damage, 0.22F).add(Stat.CRIT, 7).build(),
                StatEffects.builder().add(damage, 0.15F).add(Stat.MOVE_SPEED, 0.15F).build()},
            bonus, TerraRarity.RED, 500000);
    }

    private static RegistryObject<ProjectileSwordItem> sword(String name, Supplier<ProjectileKind> kind, int damage, int useTime, float knockback,
                                                             int count) {
        return ModItems.register(name, TabGroup.WEAPONS, p -> new ProjectileSwordItem(p, kind, 1.0F, count, 0.15F),
            p -> WeaponProperties.melee(p, TerraItemStats.builder().melee(damage).useTime(useTime).knockback(knockback).crit(10).velocity(12.0F)
                .rarity(TerraRarity.RED).value(500000).build()));
    }

    private static RegistryObject<RangedWeaponItem> gun(String name, AmmoType ammo, int damage, int useTime, int count, float spread) {
        return ModItems.register(name, TabGroup.WEAPONS, p -> new RangedWeaponItem(p, ammo, SoundEvents.CROSSBOW_SHOOT, 1.0F, count, spread),
            p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().ranged(damage).useTime(useTime).knockback(2.0F).crit(10).velocity(16.0F)
                .rarity(TerraRarity.RED).value(500000).build()));
    }

    private static RegistryObject<MagicWeaponItem> staff(String name, ProjectileKind kind, int count, float spread, int damage, int mana, int useTime) {
        return ModItems.register(name, TabGroup.WEAPONS, p -> new MagicWeaponItem(p, kind, count, spread),
            p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(damage).mana(mana).useTime(useTime).knockback(3.0F).crit(10)
                .velocity(12.0F).rarity(TerraRarity.RED).value(500000).build()));
    }

    private static RegistryObject<WingsItem> wings(String name, String style) {
        return ModItems.register(name, TabGroup.ACCESSORIES, p -> new WingsItem(p, StatEffects.NONE, new WingsItem.Flight(style, 90, 0.55F)),
            p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.RED).value(800000).build()));
    }
}
