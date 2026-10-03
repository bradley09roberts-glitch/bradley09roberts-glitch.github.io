package com.terracraft.entity.mob;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TerrariaProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * Terraria "caster" AI (Dark Caster, later Goblin Sorcerer, Fire Imp): stands still, teleports to a spot near
 * its target every few seconds and casts a slow homing spell at it.
 */
public class CasterMob extends TerrariaMob {
    private final Supplier<ProjectileKind> spell;
    private final float spellDamage;
    private int castTimer = 60;
    private int casts;

    public CasterMob(EntityType<? extends CasterMob> type, Level level, Supplier<ProjectileKind> spell, float spellDamage) {
        super(type, level);
        this.spell = spell;
        this.spellDamage = spellDamage;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        getLookControl().setLookAt(target);
        if (--castTimer > 0) {
            return;
        }
        if (casts >= 3 || distanceToSqr(target) > 20 * 20) {
            casts = 0;
            teleportNear(level, target);
            castTimer = 30;
            return;
        }
        Vec3 from = getEyePosition();
        Vec3 aim = target.getEyePosition().subtract(from).normalize();
        TerrariaProjectile.shoot(level, this, spell.get(), from.add(aim.scale(0.6)), aim, 3.0F, 0.0F,
            spellDamage * TerrariaDifficulty.enemyDamageMultiplier(level), DamageClass.GENERIC, 0, 2.0F);
        playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.3F);
        casts++;
        castTimer = 50 + random.nextInt(20);
    }

    /** Picks a standable spot 6-14 blocks from the target with a clear line of sight. */
    private void teleportNear(ServerLevel level, LivingEntity target) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 6 + random.nextDouble() * 8;
            int x = Mth.floor(target.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(target.getZ() + Math.sin(angle) * distance);
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos feet = new BlockPos(x, target.getBlockY() + dy, z);
                if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                    level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.05);
                    teleportTo(x + 0.5, feet.getY(), z + 0.5);
                    level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.05);
                    playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.4F);
                    if (hasLineOfSight(target)) {
                        return;
                    }
                }
            }
        }
    }
}
