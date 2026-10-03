package com.terracraft.entity.mob;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Hornet: a slow, persistent flyer that hovers near its target and fires poisonous stingers. */
public class HornetMob extends FlyerMob {
    private final float stingerDamage;
    private int shootTimer = 60;

    public HornetMob(EntityType<? extends HornetMob> type, Level level, float stingerDamage) {
        super(type, level, Style.CHASER);
        this.stingerDamage = stingerDamage;
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
            TerrariaProjectile.shoot(level, this, ProjectileKinds.STINGER, from, aim, 8.0F, 1.0F,
                stingerDamage * TerrariaDifficulty.enemyDamageMultiplier(level), DamageClass.GENERIC, 0, 1.0F);
            playSound(SoundEvents.BEE_STING, 1.0F, 1.2F);
            shootTimer = 60 + random.nextInt(40);
        }
    }
}
