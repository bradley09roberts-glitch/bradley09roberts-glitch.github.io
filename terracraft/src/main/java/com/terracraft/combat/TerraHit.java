package com.terracraft.combat;

/**
 * Terraria hit metadata travelling with a {@link TerraDamageSource}.
 *
 * @param damageClass  class used for bonuses (projectiles carry their weapon's class)
 * @param critChance   total crit chance in percent (weapon + bonuses), rolled when the hit lands
 * @param armorPenetration defense ignored on the target
 * @param knockback    Terraria knockback value (already including the attacker's bonuses)
 * @param preScaled    true if class damage bonuses were already applied when the hit was created
 */
public record TerraHit(DamageClass damageClass, int critChance, int armorPenetration, float knockback, boolean preScaled) {
    /** Enemy contact / projectile hit: no crits, damage is already final. */
    public static TerraHit enemy(float knockback) {
        return new TerraHit(DamageClass.GENERIC, 0, 0, knockback, true);
    }
}
