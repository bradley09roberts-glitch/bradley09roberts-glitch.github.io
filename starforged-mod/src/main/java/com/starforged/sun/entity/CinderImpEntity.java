package com.starforged.sun.entity;

import com.starforged.registry.ModParticles;
import com.starforged.sun.SunSounds;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cinder Imps: cackling little fire-devils that flit around their prey in swarms, hurling bursts of sparks.
 * They hate water.
 */
public class CinderImpEntity extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(CinderImpEntity.class, EntityDataSerializers.BOOLEAN);

    public CinderImpEntity(EntityType<? extends CinderImpEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.setNoGravity(true);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 14.0)
            .add(Attributes.FLYING_SPEED, 0.65)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGING, false);
    }

    public boolean isCharging() {
        return this.entityData.get(DATA_CHARGING);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SparkVolleyGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.9));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            if (this.random.nextInt(2) == 0) {
                this.level().addParticle(ParticleTypes.FLAME, this.getX() + (this.random.nextDouble() - 0.5) * 0.4, this.getY() + 0.2,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.4, 0.0, -0.02, 0.0);
            }
            if (this.isCharging()) {
                this.level().addParticle(ModParticles.SOLAR_SPARK.get(), this.getX(), this.getY() + 0.9, this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.1, 0.05, (this.random.nextDouble() - 0.5) * 0.1);
            }
        }
        if (!this.level().isClientSide() && this.isInWaterOrRain() && this.tickCount % 10 == 0 && this.level() instanceof ServerLevel server) {
            this.hurtServer(server, this.damageSources().drown(), 2.0F);
        }
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SunSounds.IMP_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SunSounds.IMP_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SunSounds.IMP_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 1.1F + this.random.nextFloat() * 0.3F;
    }

    /** Circle the target, wind up, then loose three quick sparks. */
    private class SparkVolleyGoal extends Goal {
        private int cooldown = 30;
        private int charge;

        SparkVolleyGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = CinderImpEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            CinderImpEntity.this.entityData.set(DATA_CHARGING, false);
            this.charge = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            CinderImpEntity imp = CinderImpEntity.this;
            LivingEntity target = imp.getTarget();
            if (target == null) {
                return;
            }
            imp.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double angle = imp.tickCount * 0.06 + imp.getId();
            double radius = 6.0 + Math.sin(imp.tickCount * 0.05) * 1.5;
            Vec3 desired = target.position().add(Math.cos(angle) * radius, 2.5 + Math.sin(imp.tickCount * 0.1) * 0.8, Math.sin(angle) * radius);
            imp.getMoveControl().setWantedPosition(desired.x, desired.y, desired.z, 1.0);

            if (this.charge > 0) {
                this.charge++;
                if ((this.charge == 12 || this.charge == 16 || this.charge == 20) && imp.level() instanceof ServerLevel server) {
                    Vec3 hand = imp.position().add(0, 0.7, 0);
                    Vec3 aim = target.getEyePosition().subtract(hand).add((imp.random.nextDouble() - 0.5) * 0.8, 0, (imp.random.nextDouble() - 0.5) * 0.8);
                    SolarFlareEntity.shoot(server, imp, hand, aim, 0.9F, 4.0F, SolarFlareEntity.Kind.SPARK, null);
                    server.playSound(null, imp.getX(), imp.getY(), imp.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.6F, 1.6F);
                }
                if (this.charge > 24) {
                    this.charge = 0;
                    imp.entityData.set(DATA_CHARGING, false);
                    this.cooldown = 40 + imp.random.nextInt(40);
                }
            } else if (--this.cooldown <= 0 && imp.distanceTo(target) < 20.0 && imp.hasLineOfSight(target)) {
                this.charge = 1;
                imp.entityData.set(DATA_CHARGING, true);
            }
        }
    }
}
