package com.starforged.sun.entity;

import com.starforged.registry.ModParticles;
import com.starforged.sun.SunSounds;
import com.starforged.util.Combat;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
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
 * Ashen Knights: the burned-out guardians of the Sun Temple. Their tower shields stop anything that comes at
 * them head-on - get behind them, or bait the Ember Cleave and strike while their guard is down.
 */
public class AshenKnightEntity extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_GUARD = SynchedEntityData.defineId(AshenKnightEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CLEAVE = SynchedEntityData.defineId(AshenKnightEntity.class, EntityDataSerializers.INT);
    public static final int CLEAVE_WINDUP = 14;
    public int clientCleaveTicks;

    public AshenKnightEntity(EntityType<? extends AshenKnightEntity> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 50.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 8.0)
            .add(Attributes.ARMOR, 12.0)
            .add(Attributes.ARMOR_TOUGHNESS, 4.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
            .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GUARD, false);
        builder.define(DATA_CLEAVE, 0);
    }

    public boolean isGuarding() {
        return this.entityData.get(DATA_GUARD);
    }

    public int cleaveTicks() {
        return this.entityData.get(DATA_CLEAVE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new CleaveGoal());
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.clientCleaveTicks = this.cleaveTicks() > 0 ? this.clientCleaveTicks + 1 : 0;
            if (this.random.nextInt(4) == 0) {
                this.level().addParticle(ModParticles.ASH_FLAKE.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.6,
                    this.getY() + this.random.nextDouble() * 2.0, this.getZ() + (this.random.nextDouble() - 0.5) * 0.6, 0.0, 0.02, 0.0);
            }
            return;
        }
        LivingEntity target = this.getTarget();
        boolean guard = target != null && this.cleaveTicks() == 0 && this.distanceTo(target) > 2.6;
        if (guard != this.isGuarding()) {
            this.entityData.set(DATA_GUARD, guard);
        }
    }

    /** Is the attack coming from in front of the knight (within its shield arc)? */
    private boolean fromFront(DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        Vec3 toAttacker = from.subtract(this.position()).multiply(1, 0, 1).normalize();
        Vec3 facing = Vec3.directionFromRotation(0, this.getYRot());
        return facing.dot(toAttacker) > 0.35;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isGuarding() && this.fromFront(source) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.KNIGHT_BLOCK.get(), SoundSource.HOSTILE, 1.0F,
                0.9F + this.random.nextFloat() * 0.2F);
            level.sendParticles(ModParticles.SOLAR_SPARK.get(), this.getX(), this.getY() + 1.2, this.getZ(), 10, 0.3, 0.3, 0.3, 0.12);
            if (source.getDirectEntity() instanceof Projectile) {
                return false;
            }
            damage *= 0.3F;
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            target.igniteForSeconds(4.0F);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SunSounds.KNIGHT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SunSounds.KNIGHT_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }

    /** Ember Cleave: raise the blade (guard drops!), then a wide burning sweep that hits everything in front. */
    private class CleaveGoal extends Goal {
        private int cooldown = 60;
        private int ticks;

        CleaveGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            AshenKnightEntity knight = AshenKnightEntity.this;
            LivingEntity target = knight.getTarget();
            if (--this.cooldown > 0 || target == null || !target.isAlive()) {
                return false;
            }
            return knight.distanceTo(target) < 4.0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.ticks > 0;
        }

        @Override
        public void start() {
            this.ticks = 1;
            AshenKnightEntity.this.entityData.set(DATA_CLEAVE, 1);
            AshenKnightEntity.this.getNavigation().stop();
            AshenKnightEntity.this.playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 0.6F);
        }

        @Override
        public void stop() {
            this.ticks = 0;
            this.cooldown = 70 + AshenKnightEntity.this.random.nextInt(40);
            AshenKnightEntity.this.entityData.set(DATA_CLEAVE, 0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            AshenKnightEntity knight = AshenKnightEntity.this;
            LivingEntity target = knight.getTarget();
            if (target != null && this.ticks < CLEAVE_WINDUP) {
                knight.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            this.ticks++;
            if (this.ticks == CLEAVE_WINDUP && knight.level() instanceof ServerLevel server) {
                Vec3 facing = Vec3.directionFromRotation(0, knight.yBodyRot);
                Vec3 center = knight.position().add(0, 1.0, 0);
                for (LivingEntity victim : Combat.targetsAround(server, knight, center.add(facing.scale(1.5)), 3.2)) {
                    Vec3 to = victim.position().subtract(knight.position()).multiply(1, 0, 1).normalize();
                    if (facing.dot(to) > 0.1) {
                        victim.hurtServer(server, knight.damageSources().mobAttack(knight), 11.0F);
                        victim.igniteForSeconds(5.0F);
                        Combat.knock(victim, 1.1, to);
                    }
                }
                for (int i = -6; i <= 6; i++) {
                    double a = Math.toRadians(knight.yBodyRot + 90 + i * 12);
                    double x = knight.getX() - Math.cos(a) * 2.2;
                    double z = knight.getZ() - Math.sin(a) * 2.2;
                    server.sendParticles(ParticleTypes.FLAME, x, knight.getY() + 1.0, z, 3, 0.1, 0.2, 0.1, 0.02);
                    server.sendParticles(ParticleTypes.SWEEP_ATTACK, x, knight.getY() + 1.0, z, 1, 0, 0, 0, 0);
                }
                server.playSound(null, knight.getX(), knight.getY(), knight.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4F, 0.6F);
            }
            if (this.ticks > CLEAVE_WINDUP + 10) {
                this.ticks = 0;
            }
        }
    }
}
