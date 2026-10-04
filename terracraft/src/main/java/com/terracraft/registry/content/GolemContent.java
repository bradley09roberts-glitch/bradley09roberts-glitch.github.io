package com.terracraft.registry.content;

import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.item.tool.TerrariaToolItem;
import com.terracraft.item.weapon.MagicWeaponItem;
import com.terracraft.item.weapon.ThrownWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.player.stats.Ability;
import com.terracraft.player.stats.PlayerStats;
import com.terracraft.player.stats.Stat;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.entity.player.Player;

/**
 * Golem's treasure: the Picksaw (the only pickaxe that mines Lihzahrd Brick), the Heat Ray, the Possessed Hatchet,
 * the Sun Stone (all stats up by day), the Eye of the Golem and Beetle Husks.
 */
public final class GolemContent {
    /** Picksaw: 210% pickaxe power and 125% axe power. */
    public static final RegistryObject<TerrariaToolItem> PICKSAW = ModItems.register("picksaw", TabGroup.TOOLS_ARMOR, TerrariaToolItem::new,
        p -> TerrariaToolItem.properties(p, TerraItemStats.builder().melee(34).useTime(12).knockback(5.5F).pickaxe(210).axe(125)
            .rarity(TerraRarity.YELLOW).value(216000).build(), 10.0F));
    public static final RegistryObject<MagicWeaponItem> HEAT_RAY = ModItems.register("heat_ray", TabGroup.WEAPONS,
        p -> new MagicWeaponItem(p, ProjectileKinds.HEAT_RAY, 1, 0.0F),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().magic(80).mana(8).useTime(7).knockback(3.5F).crit(4).velocity(15.0F)
            .rarity(TerraRarity.YELLOW).value(216000).build()));
    public static final RegistryObject<ThrownWeaponItem> POSSESSED_HATCHET = ModItems.register("possessed_hatchet", TabGroup.WEAPONS,
        p -> new ThrownWeaponItem(p, ProjectileKinds.POSSESSED_HATCHET, false),
        p -> WeaponProperties.stats(p.stacksTo(1), TerraItemStats.builder().melee(90).useTime(15).knockback(10.0F).crit(4).velocity(14.0F)
            .rarity(TerraRarity.YELLOW).value(216000).build()));
    /** Sun Stone: during the day, small boosts to everything (see {@link #sunStone}). */
    public static final RegistryObject<AccessoryItem> SUN_STONE = ModItems.register("sun_stone", TabGroup.ACCESSORIES,
        p -> new AccessoryItem(p, StatEffects.builder().ability(Ability.SUN_STONE).build()),
        p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.YELLOW).value(216000).build()));
    public static final RegistryObject<AccessoryItem> EYE_OF_THE_GOLEM = ModItems.register("eye_of_the_golem", TabGroup.ACCESSORIES,
        p -> new AccessoryItem(p, StatEffects.builder().add(Stat.CRIT, 10).build()),
        p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.YELLOW).value(216000).build()));
    public static final RegistryObject<TerraItem> BEETLE_HUSK = CoreItems.material("beetle_husk", TerraRarity.YELLOW, 5000);

    private GolemContent() {}

    public static void init() {}

    /** Stat source: the Sun Stone's daytime bonus (+4 defense, +10% damage and melee speed, +2% crit, +1 life regen). */
    public static void sunStone(Player player, TerraPlayerData data, PlayerStats stats) {
        if (stats.has(Ability.SUN_STONE) && player.level().isBrightOutside()) {
            stats.add(Stat.DEFENSE, 4);
            stats.add(Stat.DAMAGE, 0.10F);
            stats.add(Stat.MELEE_SPEED, 0.10F);
            stats.add(Stat.CRIT, 2);
            stats.add(Stat.LIFE_REGEN, 1);
            stats.add(Stat.MINING_SPEED, 0.10F);
        }
    }
}
