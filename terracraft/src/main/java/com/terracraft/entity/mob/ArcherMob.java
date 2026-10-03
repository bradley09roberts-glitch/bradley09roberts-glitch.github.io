package com.terracraft.entity.mob;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TerrariaProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** A walker that stops to shoot when its target is in range and in sight (Goblin Archer, later Skeleton Archers). */
public class ArcherMob extends WalkerMob {
    private final Supplier<ProjectileKind> arrow;
    private final float arrowDamage;
    private int shootTimer = 40;

    public ArcherMob(EntityType<? extends ArcherMob> type, Level level, Supplier<ProjectileKind> arrow, float arrowDamage) {
        super(type, level);
        this.arrow = arrow;
        this.arrowDamage = arrowDamage;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        double distance = distanceTo(target);
        if (distance < 18.0 && hasLineOfSight(target)) {
            if (distance < 12.0) {
                getNavigation().stop();
            }
            getLookControl().setLookAt(target);
            if (--shootTimer <= 0) {
                Vec3 from = getEyePosition();
                // aim a little high to make up for the arrow's drop
                Vec3 aim = target.getEyePosition().subtract(from).add(0, distance * 0.08, 0).normalize();
                TerrariaProjectile.shoot(level, this, arrow.get(), from, aim, 9.0F, 2.0F,
                    arrowDamage * TerrariaDifficulty.enemyDamageMultiplier(level), DamageClass.GENERIC, 0, 1.0F);
                playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.2F);
                shootTimer = 40 + random.nextInt(20);
            }
        }
    }
}
