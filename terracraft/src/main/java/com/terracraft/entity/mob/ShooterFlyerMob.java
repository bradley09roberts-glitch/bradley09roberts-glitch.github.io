package com.terracraft.entity.mob;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TerrariaProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A flyer that hovers near its target and shoots at it: Hornets (poison stingers), Demons and Voodoo Demons
 * (demon scythes).
 */
public class ShooterFlyerMob extends FlyerMob {
    private final java.util.function.Supplier<ProjectileKind> projectile;
    private final float projectileDamage;
    private final float projectileSpeed;
    private final SoundEvent sound;
    private int shootTimer = 60;

    public ShooterFlyerMob(EntityType<? extends ShooterFlyerMob> type, Level level, java.util.function.Supplier<ProjectileKind> projectile,
                           float projectileDamage, float projectileSpeed, SoundEvent sound) {
        super(type, level, Style.CHASER);
        this.projectile = projectile;
        this.projectileDamage = projectileDamage;
        this.projectileSpeed = projectileSpeed;
        this.sound = sound;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        LivingEntity target = getTarget();
        super.customServerAiStep(level);
        if (target == null || !target.isAlive()) {
            return;
        }
        double distance = distanceTo(target);
        if (distance < 5.0) {
            // keep a little distance instead of ramming like a Demon Eye
            setDeltaMovement(getDeltaMovement().add(position().subtract(target.position()).normalize().scale(0.03)));
        }
        if (--shootTimer <= 0 && distance < 18.0 && hasLineOfSight(target)) {
            Vec3 from = position().add(0, getBbHeight() * 0.3, 0);
            Vec3 aim = target.getEyePosition().subtract(from).normalize();
            TerrariaProjectile.shoot(level, this, projectile.get(), from, aim, projectileSpeed, 1.0F,
                projectileDamage * TerrariaDifficulty.enemyDamageMultiplier(level), DamageClass.GENERIC, 0, 1.0F);
            playSound(sound, 1.0F, 1.2F);
            shootTimer = 60 + random.nextInt(40);
        }
    }
}
