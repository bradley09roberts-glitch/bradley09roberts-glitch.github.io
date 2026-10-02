package com.terracraft.item.weapon;

import com.terracraft.item.TerraItem;

/**
 * Swords, shortswords and other swung weapons. Damage, speed and knockback are main-hand attribute
 * modifiers created by {@link WeaponProperties#melee}; crits and class bonuses come from the damage pipeline.
 */
public class MeleeWeaponItem extends TerraItem {
    public MeleeWeaponItem(Properties properties) {
        super(properties);
    }
}
