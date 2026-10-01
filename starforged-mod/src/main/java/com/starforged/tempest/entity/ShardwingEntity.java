package com.starforged.tempest.entity;

import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestSounds;
import com.starforged.util.Combat;
import java.util.EnumSet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
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
 * Shardwings: crystal-feathered raptors of the Shardwind Cliffs. They circle high over their prey, rake it with a
 * <b>Crystal Volley</b>, then fold their wings and <b>Shard Dive</b> straight down onto it.
 */
public class ShardwingEntity extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_DIVING = SynchedEntityData.defineId(ShardwingEntity.class, EntityDataSerializers.BOOLEAN);

    public ShardwingEntity(EntityType<? extends ShardwingEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 10, true);
        this.setNoGravity(true);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 36.0)
            .add(Attributes.FLYING_SPEED, 0.75)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 7.0)
            .add(Attributes.ARMOR, 4.0)
            .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DIVING, false);
    }

    public boolean isDiving() {
        return this.entityData.get(DATA_DIVING);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new HuntGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 0.9));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && (this.isDiving() || this.random.nextInt(4) == 0)) {
            this.level().addParticle(ModParticles.STATIC_SPARK.get(), this.getRandomX(1.2), this.getRandomY(), this.getRandomZ(1.2), 0, 0, 0);
        }
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return TempestSounds.SHARDWING_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TempestSounds.SHARDWING_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TempestSounds.SHARDWING_DEATH.get();
    }

    /** Circle, volley, dive, climb again. */
    private class HuntGoal extends Goal {
        private int timer = 60;
        private int dive;

        HuntGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = ShardwingEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            ShardwingEntity.this.entityData.set(DATA_DIVING, false);
            this.dive = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            ShardwingEntity wing = ShardwingEntity.this;
            LivingEntity target = wing.getTarget();
            if (target == null || !(wing.level() instanceof ServerLevel server)) {
                return;
            }
            wing.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.dive > 0) {
                this.dive++;
                Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(wing.position());
                wing.setDeltaMovement(to.normalize().scale(1.1));
                if (to.length() < 1.8) {
                    wing.doHurtTarget(server, target);
                    Combat.blast(target, wing.position(), 0.6, 0.4);
                    server.sendParticles(ModParticles.STATIC_SPARK.get(), target.getX(), target.getY() + 1, target.getZ(), 20, 0.4, 0.4, 0.4, 0.15);
                    this.endDive();
                } else if (this.dive > 50) {
                    this.endDive();
                }
                return;
            }
            double angle = wing.tickCount * 0.03 + wing.getId();
            Vec3 circle = target.position().add(Math.cos(angle) * 10.0, 9.0, Math.sin(angle) * 10.0);
            wing.getMoveControl().setWantedPosition(circle.x, circle.y, circle.z, 1.0);
            if (--this.timer <= 0) {
                if (wing.random.nextBoolean() && wing.hasLineOfSight(target)) {
                    Vec3 from = wing.position().add(0, 0.4, 0);
                    for (int i = -1; i <= 1; i++) {
                        Vec3 aim = target.getEyePosition().subtract(from).normalize().yRot(i * 0.15F);
                        StormShardEntity.shoot(server, wing, from, aim, 1.3F, 4.0F);
                    }
                    server.playSound(null, wing.getX(), wing.getY(), wing.getZ(), TempestSounds.SHARDWING_AMBIENT.get(), SoundSource.HOSTILE, 1.2F, 1.5F);
                    this.timer = 50;
                } else {
                    this.dive = 1;
                    wing.entityData.set(DATA_DIVING, true);
                    server.playSound(null, wing.getX(), wing.getY(), wing.getZ(), TempestSounds.SHARDWING_DIVE.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
                }
            }
        }

        private void endDive() {
            this.dive = 0;
            this.timer = 70 + ShardwingEntity.this.random.nextInt(40);
            ShardwingEntity.this.entityData.set(DATA_DIVING, false);
            ShardwingEntity.this.setDeltaMovement(ShardwingEntity.this.getDeltaMovement().add(0, 0.8, 0));
        }
    }
}
