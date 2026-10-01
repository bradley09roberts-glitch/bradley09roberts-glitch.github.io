package com.starforged.moon.entity;

import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModParticles;
import com.starforged.util.Combat;
import java.util.EnumSet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A construct of living selenite that guards the Hollows and the Orreries. Its crystal body reflects every projectile
 * straight back at the shooter - fight it up close, with explosions, or with gravity.
 */
public class SeleniteSentinelEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(SeleniteSentinelEntity.class, EntityDataSerializers.INT);

    public SeleniteSentinelEntity(EntityType<? extends SeleniteSentinelEntity> type, Level level) {
        super(type, level);
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 70.0)
            .add(Attributes.ARMOR, 10.0)
            .add(Attributes.MOVEMENT_SPEED, 0.22)
            .add(Attributes.ATTACK_DAMAGE, 11.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGE, 0);
    }

    public int charge() {
        return this.entityData.get(DATA_CHARGE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new SentinelAttackGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getDirectEntity() instanceof Projectile projectile && !(projectile instanceof MoonletEntity m && m.getOwner() == this)) {
            Vec3 back = projectile.getDeltaMovement().scale(-1.1);
            projectile.setDeltaMovement(back);
            projectile.setOwner(this);
            projectile.hurtMarked = true;
            level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.SENTINEL_REFLECT.get(), SoundSource.HOSTILE, 1.0F, 1.2F);
            level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), projectile.getX(), projectile.getY(), projectile.getZ(), 14, 0.2, 0.2, 0.2, 0.1);
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.random.nextInt(4) == 0) {
            this.level().addParticle(ModParticles.LUNAR_GLIMMER.get(), this.getX() + (this.random.nextDouble() - 0.5) * 1.2,
                this.getY() + 1.0 + this.random.nextDouble() * 1.6, this.getZ() + (this.random.nextDouble() - 0.5) * 1.2, 0.0, 0.01, 0.0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MoonSounds.SENTINEL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MoonSounds.SENTINEL_REFLECT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MoonSounds.SENTINEL_DEATH.get();
    }

    /** Walks into melee range for a crushing slam; at range, charges up and flings a volley of selenite shards. */
    private class SentinelAttackGoal extends Goal {
        private int cooldown = 30;
        private int slamCooldown;

        SentinelAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = SeleniteSentinelEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            SeleniteSentinelEntity.this.entityData.set(DATA_CHARGE, 0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            SeleniteSentinelEntity sentinel = SeleniteSentinelEntity.this;
            LivingEntity target = sentinel.getTarget();
            if (target == null || !(sentinel.level() instanceof ServerLevel server)) {
                return;
            }
            sentinel.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double dist = sentinel.distanceTo(target);
            int charge = sentinel.charge();
            if (charge > 0) {
                sentinel.getNavigation().stop();
                sentinel.entityData.set(DATA_CHARGE, charge + 1);
                if (charge == 20) {
                    Vec3 from = sentinel.position().add(0, 2.0, 0);
                    for (int i = -2; i <= 2; i++) {
                        Vec3 aim = target.getEyePosition().subtract(from).normalize().yRot(i * 0.12F);
                        MoonletEntity.shoot(server, sentinel, from, aim, 1.1F, 5.0F, MoonletEntity.Kind.SHARD, null);
                    }
                    server.playSound(null, sentinel.getX(), sentinel.getY(), sentinel.getZ(), MoonSounds.SENTINEL_AMBIENT.get(), SoundSource.HOSTILE,
                        1.5F, 1.4F);
                }
                if (charge > 30) {
                    sentinel.entityData.set(DATA_CHARGE, 0);
                    this.cooldown = 60 + sentinel.random.nextInt(30);
                }
                return;
            }
            if (dist > 3.0) {
                sentinel.getNavigation().moveTo(target, 1.0);
            } else {
                sentinel.getNavigation().stop();
            }
            if (--this.slamCooldown <= 0 && dist < 3.2) {
                sentinel.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                sentinel.doHurtTarget(server, target);
                Combat.blast(target, sentinel.position(), 0.9, 0.6);
                this.slamCooldown = 30;
            }
            if (--this.cooldown <= 0 && dist > 5.0 && dist < 22.0 && sentinel.hasLineOfSight(target)) {
                sentinel.entityData.set(DATA_CHARGE, 1);
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }
}
