package com.starforged.tempest.entity;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.EnumSet;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Thunderjaws: plated storm-beasts that roam the Thunderhead Steppe. They rear up for a <b>Ground Stamp</b> that hurls
 * everything nearby, and lower their horns for a <b>Thunder Charge</b> at anything that keeps its distance.
 * <b>Charge Storage</b>: lightning that lands near a Thunderjaw charges it for thirty seconds - faster, harder-hitting,
 * and every stamp calls lightning down.
 */
public class ThunderjawEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_STAMP = SynchedEntityData.defineId(ThunderjawEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHARGED = SynchedEntityData.defineId(ThunderjawEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RUSHING = SynchedEntityData.defineId(ThunderjawEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int STAMP_LENGTH = 22;
    public static final int STAMP_HIT = 14;

    public ThunderjawEntity(EntityType<? extends ThunderjawEntity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 90.0)
            .add(Attributes.ARMOR, 12.0)
            .add(Attributes.MOVEMENT_SPEED, 0.24)
            .add(Attributes.ATTACK_DAMAGE, 10.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
            .add(Attributes.STEP_HEIGHT, 1.5)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STAMP, 0);
        builder.define(DATA_CHARGED, 0);
        builder.define(DATA_RUSHING, false);
    }

    public int stamp() {
        return this.entityData.get(DATA_STAMP);
    }

    public boolean isCharged() {
        return this.entityData.get(DATA_CHARGED) > 0;
    }

    public boolean isRushing() {
        return this.entityData.get(DATA_RUSHING);
    }

    /** Charge Storage: called when lightning lands within reach. */
    public void charge(ServerLevel level) {
        if (!this.isCharged()) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.THUNDERJAW_AMBIENT.get(), SoundSource.HOSTILE, 2.0F, 1.4F);
            Fx.sphere(level, ModParticles.STATIC_SPARK.get(), this.position().add(0, 1.5, 0), 1.2, 60, 0.3);
        }
        this.entityData.set(DATA_CHARGED, 600);
    }

    /** Charges every Thunderjaw within {@code radius} of a lightning strike at {@code at}. */
    public static void chargeNear(ServerLevel level, Vec3 at, double radius) {
        for (ThunderjawEntity jaw : level.getEntitiesOfClass(ThunderjawEntity.class, new AABB(at, at).inflate(radius))) {
            jaw.charge(level);
        }
    }

    protected float stampRadius() {
        return 5.0F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new BeastGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        int charged = this.entityData.get(DATA_CHARGED);
        if (charged > 0 && !this.level().isClientSide()) {
            this.entityData.set(DATA_CHARGED, charged - 1);
        }
        if (this.level().isClientSide() && this.isCharged()) {
            for (int i = 0; i < 2; i++) {
                this.level().addParticle(ModParticles.STATIC_SPARK.get(), this.getRandomX(1.0), this.getRandomY(), this.getRandomZ(1.0), 0, 0, 0);
            }
        }
    }

    /** Rear up and slam: everything around is hurt and hurled into the air. */
    protected void groundStamp(ServerLevel level) {
        float radius = this.stampRadius();
        Vec3 at = this.position();
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (this.isCharged() ? 1.4F : 1.0F);
        for (LivingEntity victim : Combat.targetsAround(level, this, at, radius)) {
            victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SHOCKWAVE, this), damage);
            Combat.blast(victim, at, 0.9, 0.7);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(this.blockPosition().below())), at.x, at.y + 0.2, at.z,
            60, radius * 0.4, 0.1, radius * 0.4, 0.2);
        Fx.ring(level, ModParticles.STORM_WISP.get(), at.add(0, 0.2, 0), 1.0, 40, 0.5, 0.02);
        level.playSound(null, at.x, at.y, at.z, TempestSounds.THUNDERJAW_STAMP.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
        Fx.shake(level, at, 16.0, 0.6F, 8);
        if (this.isCharged()) {
            for (int i = 0; i < 3; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                TempestFx.strike(level, at.add(Math.cos(a) * radius * 0.7, 0, Math.sin(a) * radius * 0.7), this, 6.0F, 2.0);
            }
        }
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return TempestSounds.THUNDERJAW_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TempestSounds.THUNDERJAW_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TempestSounds.THUNDERJAW_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("charged", this.entityData.get(DATA_CHARGED));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_CHARGED, input.getIntOr("charged", 0));
    }

    /** Hook for the Alpha's extra attacks; return true if it used its turn. */
    protected boolean specialAttack(ServerLevel level, LivingEntity target, double dist) {
        return false;
    }

    /** Stamp up close, charge from afar. */
    private class BeastGoal extends Goal {
        private int stampCooldown;
        private int rushCooldown = 80;
        private int rush;

        BeastGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = ThunderjawEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            ThunderjawEntity.this.entityData.set(DATA_STAMP, 0);
            ThunderjawEntity.this.entityData.set(DATA_RUSHING, false);
            this.rush = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            ThunderjawEntity jaw = ThunderjawEntity.this;
            LivingEntity target = jaw.getTarget();
            if (target == null || !(jaw.level() instanceof ServerLevel server)) {
                return;
            }
            jaw.getLookControl().setLookAt(target, 20.0F, 20.0F);
            double dist = jaw.distanceTo(target);
            int stamp = jaw.stamp();
            if (stamp > 0) {
                jaw.getNavigation().stop();
                jaw.entityData.set(DATA_STAMP, stamp + 1);
                if (stamp == STAMP_HIT) {
                    jaw.groundStamp(server);
                }
                if (stamp >= STAMP_LENGTH) {
                    jaw.entityData.set(DATA_STAMP, 0);
                    this.stampCooldown = jaw.isCharged() ? 25 : 45;
                }
                return;
            }
            if (this.rush > 0) {
                this.rush++;
                Vec3 dir = target.position().subtract(jaw.position()).multiply(1, 0, 1).normalize();
                double speed = jaw.isCharged() ? 0.65 : 0.5;
                jaw.setDeltaMovement(dir.x * speed, jaw.getDeltaMovement().y, dir.z * speed);
                jaw.setYRot((float) (Math.atan2(dir.z, dir.x) * 180.0 / Math.PI) - 90.0F);
                if (dist < 2.8) {
                    jaw.doHurtTarget(server, target);
                    Combat.blast(target, jaw.position(), 1.6, 0.6);
                    this.endRush();
                } else if (this.rush > 40) {
                    this.endRush();
                }
                return;
            }
            if (jaw.specialAttack(server, target, dist)) {
                return;
            }
            jaw.getNavigation().moveTo(target, jaw.isCharged() ? 1.4 : 1.1);
            if (--this.stampCooldown <= 0 && dist < jaw.stampRadius() - 1.0) {
                jaw.entityData.set(DATA_STAMP, 1);
            } else if (--this.rushCooldown <= 0 && dist > 8.0 && dist < 22.0) {
                this.rush = 1;
                jaw.entityData.set(DATA_RUSHING, true);
                server.playSound(null, jaw.getX(), jaw.getY(), jaw.getZ(), TempestSounds.THUNDERJAW_AMBIENT.get(), SoundSource.HOSTILE, 2.0F, 0.7F);
            }
        }

        private void endRush() {
            this.rush = 0;
            this.rushCooldown = 100 + ThunderjawEntity.this.random.nextInt(60);
            ThunderjawEntity.this.entityData.set(DATA_RUSHING, false);
        }
    }
}
