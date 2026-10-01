package com.starforged.entity.monster;

import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Void Stalkers: gaunt shadows that slipped through the cracks in the sky. They fade from sight at a distance,
 * blink behind their prey, and their claws drown the world in darkness. Starlight burns them.
 */
public class VoidStalkerEntity extends Monster {
    private int blinkCooldown = 60;

    public VoidStalkerEntity(EntityType<? extends VoidStalkerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 7.0)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.ARMOR, 4.0)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.blinkCooldown > 0) {
            this.blinkCooldown--;
        }
        LivingEntity target = this.getTarget();
        if (target != null && this.blinkCooldown <= 0) {
            double dist = this.distanceTo(target);
            if (dist > 4.0 && dist < 32.0 && this.random.nextInt(10) == 0) {
                Vec3 look = target.getViewVector(1.0F).multiply(1, 0, 1);
                if (look.lengthSqr() < 1.0E-4) {
                    look = new Vec3(1, 0, 0);
                }
                Vec3 behind = target.position().subtract(look.normalize().scale(2.3));
                if (this.blinkTo(level, behind)) {
                    this.blinkCooldown = 90 + this.random.nextInt(80);
                    this.lookAt(target, 180.0F, 90.0F);
                }
            }
        }
    }

    private boolean blinkTo(ServerLevel level, Vec3 dest) {
        Vec3 from = this.position();
        if (!this.randomTeleport(dest.x, dest.y, dest.z, false)) {
            return false;
        }
        level.sendParticles(ModParticles.VOID_MOTE.get(), from.x, from.y + 1.3, from.z, 30, 0.3, 0.8, 0.3, 0.05);
        level.sendParticles(ModParticles.VOID_MOTE.get(), this.getX(), this.getY() + 1.3, this.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
        level.playSound(null, from.x, from.y, from.z, ModSounds.STALKER_TELEPORT.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.STALKER_TELEPORT.get(), SoundSource.HOSTILE, 1.0F, 0.8F);
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // It sees arrows coming and steps through the void to dodge them.
        if (source.getDirectEntity() instanceof Projectile && this.random.nextFloat() < 0.5F) {
            for (int i = 0; i < 8; i++) {
                Vec3 dest = this.position().add((this.random.nextDouble() - 0.5) * 12, this.random.nextInt(5) - 2, (this.random.nextDouble() - 0.5) * 12);
                if (this.blinkTo(level, dest)) {
                    return false;
                }
            }
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.random.nextInt(3) == 0) {
            this.level().addParticle(ModParticles.VOID_MOTE.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
                this.getY() + this.random.nextDouble() * 2.6, this.getZ() + (this.random.nextDouble() - 0.5) * 0.8, 0.0, 0.01, 0.0);
        }
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.STALKER_WHISPER.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.STALKER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.STALKER_DEATH.get();
    }
}
