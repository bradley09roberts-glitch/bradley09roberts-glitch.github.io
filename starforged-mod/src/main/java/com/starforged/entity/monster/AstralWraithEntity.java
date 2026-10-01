package com.starforged.entity.monster;

import com.starforged.entity.projectile.StarboltEntity;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import java.util.EnumSet;
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
 * Astral Wraiths: the restless ghosts of the last astronomers, still haunting their observatories.
 * They drift above the floor and hurl bolts of corrupted starlight.
 */
public class AstralWraithEntity extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_CASTING = SynchedEntityData.defineId(AstralWraithEntity.class, EntityDataSerializers.BOOLEAN);

    public AstralWraithEntity(EntityType<? extends AstralWraithEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.setNoGravity(true);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 32.0)
            .add(Attributes.FLYING_SPEED, 0.45)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.ATTACK_DAMAGE, 5.0)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.ARMOR, 2.0);
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
        builder.define(DATA_CASTING, false);
    }

    public boolean isCasting() {
        return this.entityData.get(DATA_CASTING);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new CastGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            if (this.random.nextInt(2) == 0) {
                this.level().addParticle(ModParticles.VOID_MOTE.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.5, this.getY() + 0.1,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.5, 0.0, -0.02, 0.0);
            }
            if (this.isCasting() && this.random.nextInt(2) == 0) {
                this.level().addParticle(ModParticles.STAR_SPARKLE.get(), this.getX(), this.getY() + 2.1, this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.1, 0.05, (this.random.nextDouble() - 0.5) * 0.1);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive() && this.random.nextFloat() < 0.3F) {
            Vec3 from = this.position();
            Vec3 to = from.add((this.random.nextDouble() - 0.5) * 10, this.random.nextDouble() * 2, (this.random.nextDouble() - 0.5) * 10);
            if (this.randomTeleport(to.x, to.y, to.z, false)) {
                level.sendParticles(ModParticles.VOID_MOTE.get(), from.x, from.y + 1, from.z, 20, 0.3, 0.6, 0.3, 0.05);
                level.playSound(null, from.x, from.y, from.z, ModSounds.STALKER_TELEPORT.get(), SoundSource.HOSTILE, 0.8F, 1.5F);
            }
        }
        return hurt;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.WRAITH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.WRAITH_DEATH.get();
    }

    /** Hover at range and pelt the target with void bolts. */
    private class CastGoal extends Goal {
        private int cooldown = 30;
        private int castTicks;

        CastGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = AstralWraithEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            AstralWraithEntity.this.entityData.set(DATA_CASTING, false);
            this.castTicks = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            AstralWraithEntity wraith = AstralWraithEntity.this;
            LivingEntity target = wraith.getTarget();
            if (target == null) {
                return;
            }
            wraith.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double dist = wraith.distanceTo(target);
            Vec3 desired;
            if (dist > 11.0 || !wraith.hasLineOfSight(target)) {
                desired = target.position().add(0, 2.5, 0);
            } else if (dist < 5.0) {
                desired = wraith.position().add(wraith.position().subtract(target.position()).normalize().scale(4.0)).add(0, 1.0, 0);
            } else {
                double angle = wraith.tickCount * 0.03;
                desired = target.position().add(Math.cos(angle) * 8.0, 2.5, Math.sin(angle) * 8.0);
            }
            wraith.getMoveControl().setWantedPosition(desired.x, desired.y, desired.z, 1.0);

            if (this.castTicks > 0) {
                this.castTicks++;
                if (this.castTicks == 14 && wraith.level() instanceof ServerLevel server) {
                    Vec3 hand = wraith.position().add(0, 1.6, 0);
                    StarboltEntity.shoot(server, wraith, hand, target.getEyePosition().subtract(hand), 1.1F, 5.0F, StarboltEntity.Variant.VOID, target);
                    server.playSound(null, wraith.getX(), wraith.getY(), wraith.getZ(), ModSounds.WRAITH_CAST.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
                }
                if (this.castTicks > 22) {
                    this.castTicks = 0;
                    wraith.entityData.set(DATA_CASTING, false);
                    this.cooldown = 35 + wraith.random.nextInt(30);
                }
            } else if (--this.cooldown <= 0 && dist < 20.0 && wraith.hasLineOfSight(target)) {
                this.castTicks = 1;
                wraith.entityData.set(DATA_CASTING, true);
            }
        }
    }
}
