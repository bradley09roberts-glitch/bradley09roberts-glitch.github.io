package com.starforged.moon.entity;

import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModParticles;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Something that lives on the Far Side. While anyone looks at it, it cannot move at all - turn away and it closes the
 * distance terribly fast.
 */
public class UmbralLurkerEntity extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_WATCHED = SynchedEntityData.defineId(UmbralLurkerEntity.class, EntityDataSerializers.BOOLEAN);
    private int unseenTicks;

    public UmbralLurkerEntity(EntityType<? extends UmbralLurkerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 44.0)
            .add(Attributes.MOVEMENT_SPEED, 0.46)
            .add(Attributes.ATTACK_DAMAGE, 10.0)
            .add(Attributes.ARMOR, 6.0)
            .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WATCHED, false);
    }

    public boolean isWatched() {
        return this.entityData.get(DATA_WATCHED);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    private boolean anyoneWatching(ServerLevel level) {
        Vec3 eyes = this.getEyePosition();
        Vec3 middle = this.position().add(0, this.getBbHeight() * 0.5, 0);
        for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(40.0), p -> !p.isSpectator() && p.isAlive())) {
            Vec3 view = player.getViewVector(1.0F);
            Vec3 to = middle.subtract(player.getEyePosition());
            double dist = to.length();
            if (dist < 0.1) {
                return true;
            }
            if (view.dot(to.normalize()) < 0.88 - Math.min(0.3, 1.0 / dist)) {
                continue;
            }
            HitResult hit = level.clip(new ClipContext(player.getEyePosition(), eyes, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        boolean watched = this.anyoneWatching(level);
        this.entityData.set(DATA_WATCHED, watched);
        if (watched) {
            this.getNavigation().stop();
            this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
            this.setYRot(this.yRotO);
            this.yBodyRot = this.yBodyRotO;
            this.yHeadRot = this.yHeadRotO;
            this.unseenTicks = 0;
            return;
        }
        if (++this.unseenTicks == 1 && this.getTarget() != null && this.random.nextInt(3) == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.LURKER_LUNGE.get(), SoundSource.HOSTILE, 0.7F, 0.8F);
        }
        super.customServerAiStep(level);
    }

    @Override
    public void travel(Vec3 input) {
        if (this.isWatched()) {
            super.travel(Vec3.ZERO);
            return;
        }
        super.travel(input);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (this.isWatched()) {
            return false;
        }
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0), this);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && !this.isWatched() && this.random.nextInt(3) == 0) {
            this.level().addParticle(ModParticles.ASH_FLAKE.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.6,
                this.getY() + this.random.nextDouble() * 2.2, this.getZ() + (this.random.nextDouble() - 0.5) * 0.6, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public boolean isPushable() {
        return !this.isWatched();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isWatched() ? null : MoonSounds.LURKER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MoonSounds.LURKER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MoonSounds.LURKER_DEATH.get();
    }
}
