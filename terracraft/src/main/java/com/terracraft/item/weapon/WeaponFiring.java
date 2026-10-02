package com.terracraft.item.weapon;

import com.terracraft.combat.DamageCalc;
import com.terracraft.combat.DamageClass;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TerrariaProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared projectile-launching logic for ranged, magic and thrown weapons. */
public final class WeaponFiring {
    private WeaponFiring() {}

    /** Where shots leave the player: just below the eyes, slightly forward. */
    public static Vec3 muzzle(Player player) {
        return player.getEyePosition().add(player.getLookAngle().scale(0.5)).subtract(0, 0.15, 0);
    }

    /**
     * Fires {@code count} projectiles spread evenly over {@code spreadDegrees}. Damage is the weapon's
     * pre-bonus damage: class bonuses, variance and crits are applied by the damage pipeline on impact.
     */
    public static void fire(ServerLevel level, Player player, ProjectileKind kind, float speed, int count, float spreadDegrees,
                            float inaccuracy, float damage, DamageClass damageClass, int weaponCrit, float knockback) {
        Vec3 look = player.getLookAngle();
        int crit = DamageCalc.BASE_CRIT + weaponCrit;
        for (int i = 0; i < count; i++) {
            Vec3 direction = look;
            if (count > 1 && spreadDegrees > 0) {
                float offset = (i / (float) (count - 1) - 0.5F) * spreadDegrees;
                direction = look.yRot((float) Math.toRadians(offset));
            }
            TerrariaProjectile.shoot(level, player, kind, muzzle(player), direction, speed, inaccuracy, damage, damageClass, crit, knockback);
        }
    }

    public static void sound(Player player, SoundEvent sound, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8F,
            pitch + (player.getRandom().nextFloat() - 0.5F) * 0.2F);
    }
}
