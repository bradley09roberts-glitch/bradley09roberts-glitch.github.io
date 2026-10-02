package com.terracraft.item.weapon;

import com.terracraft.entity.projectile.ProjectileKind;

/**
 * What a piece of ammunition adds to a shot: Terraria adds ammo damage and knockback to the weapon's,
 * and the ammo decides which projectile is fired (Flaming Arrows, Meteor Shot...).
 */
public record AmmoInfo(AmmoType type, int damage, float knockback, float velocityBonus, ProjectileKind projectile) {
}
