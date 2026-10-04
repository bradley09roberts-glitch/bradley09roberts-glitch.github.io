package com.terracraft.registry.content;

import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.item.consumable.EventSummonItem;
import com.terracraft.item.weapon.AmmoType;
import com.terracraft.item.weapon.MeleeWeaponItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.item.weapon.ThrownWeaponItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.player.stats.Ability;
import com.terracraft.player.stats.StatEffects;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import com.terracraft.world.event.TerrariaEvents;
import net.minecraft.sounds.SoundEvents;

/**
 * The Pirate Invasion (Hardmode): the Pirate Map that calls it, the pirates' rare drops and the Pirate's wares.
 * <ul>
 *     <li>Gold Ring pulls coins in from further away, Lucky Coin shakes coins out of enemies you hit, Discount Card
 *     makes shops 20% cheaper;</li>
 *     <li>the Coin Gun fires the coins in your inventory (copper to platinum, each hits harder);</li>
 *     <li>Cannonballs are thrown bombs; the pirate costume is vanity.</li>
 * </ul>
 */
public final class PirateContent {
    public static final RegistryObject<EventSummonItem> PIRATE_MAP = ModItems.register("pirate_map", TabGroup.CONSUMABLES,
        p -> new EventSummonItem(p, () -> TerrariaEvents.PIRATE_INVASION, true), p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.PINK, 0)));

    public static final RegistryObject<MeleeWeaponItem> CUTLASS = WeaponContent.sword("cutlass", 49, 13, 3.0F, 4, TerraRarity.PINK, 36000);
    public static final RegistryObject<RangedWeaponItem> COIN_GUN = WeaponContent.ranged("coin_gun", AmmoType.COIN, SoundEvents.CHAIN_PLACE,
        0, 8, 2.0F, 13.0F, TerraRarity.LIGHT_PURPLE, 300000);
    public static final RegistryObject<ThrownWeaponItem> CANNONBALL = WeaponContent.thrown("cannonball", ProjectileKinds.CANNONBALL, true,
        30, 40, 6.0F, 7.0F, 1500);

    public static final RegistryObject<AccessoryItem> GOLD_RING = accessory("gold_ring", 100000, Ability.COIN_MAGNET);
    public static final RegistryObject<AccessoryItem> LUCKY_COIN = accessory("lucky_coin", 100000, Ability.LUCKY_COIN);
    public static final RegistryObject<AccessoryItem> DISCOUNT_CARD = accessory("discount_card", 100000, Ability.DISCOUNT);

    /** The Pirate's costume (vanity: no defense). */
    public static final ArmorContent.ArmorPieces PIRATE_COSTUME = ArmorContent.vanity("pirate", "pirate_hat", "pirate_shirt", "pirate_pants");

    private PirateContent() {}

    public static void init() {}

    private static RegistryObject<AccessoryItem> accessory(String name, int value, Ability ability) {
        StatEffects effects = StatEffects.builder().ability(ability).build();
        return ModItems.register(name, TabGroup.ACCESSORIES, p -> new AccessoryItem(p, effects),
            p -> WeaponProperties.stats(p, TerraItemStats.builder().rarity(TerraRarity.PINK).value(value).build()));
    }
}
