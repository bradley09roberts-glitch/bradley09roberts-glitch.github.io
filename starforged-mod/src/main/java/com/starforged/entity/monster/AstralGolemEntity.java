package com.starforged.entity.monster;

import com.starforged.entity.projectile.StarboltEntity;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Astral Golem: a guardian of stone and starlight. In observatory vaults it sleeps as a statue until someone
 * walks too close. It smashes the ground in shockwaves and fires volleys of crystal shards.
 */
public class AstralGolemEntity extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(AstralGolemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(AstralGolemEntity.class, EntityDataSerializers.INT);

    public static final int ACTION_NONE = 0;
    public static final int ACTION_SLAM = 1;
    public static final int ACTION_BARRAGE = 2;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), Component.translatable("entity.starforged.astral_golem"),
        BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int actionTicks;
    private int slamCooldown = 60;
    private int barrageCooldown = 40;
    private int attackAnimationTick;
    /** Client-side: ticks since the current action started (for animation). */
    public int clientActionTicks;

    public AstralGolemEntity(EntityType<? extends AstralGolemEntity> type, Level level) {
        super(type, level);
        this.xpReward = 60;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 180.0)
            .add(Attributes.MOVEMENT_SPEED, 0.24)
            .add(Attributes.ATTACK_DAMAGE, 14.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ARMOR, 12.0)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DORMANT, false);
        builder.define(DATA_ACTION, ACTION_NONE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new DormantGoal());
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return AstralGolemEntity.this.getAction() == ACTION_NONE && super.canUse();
            }
        });
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean isDormant() {
        return this.entityData.get(DATA_DORMANT);
    }

    public void setDormant(boolean dormant) {
        this.entityData.set(DATA_DORMANT, dormant);
        this.bossEvent.setVisible(!dormant);
    }

    public int getAction() {
        return this.entityData.get(DATA_ACTION);
    }

    private void setAction(int action) {
        this.entityData.set(DATA_ACTION, action);
        this.actionTicks = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_ACTION.equals(accessor)) {
            this.clientActionTicks = 0;
        }
    }

    public int getAttackAnimationTick() {
        return this.attackAnimationTick;
    }

    private void wake(ServerLevel level) {
        if (!this.isDormant()) {
            return;
        }
        this.setDormant(false);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GOLEM_DEATH.get(), SoundSource.HOSTILE, 2.0F, 1.6F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 2.0F, 0.5F);
        Fx.sphere(level, ModParticles.ASTRAL_GLINT.get(), this.position().add(0, 2, 0), 1.0, 50, 0.4);
        Fx.shake(level, this.position(), 20.0, 0.6F, 12);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.clientActionTicks++;
            if (!this.isDormant() && this.random.nextInt(4) == 0) {
                this.level().addParticle(ModParticles.ASTRAL_GLINT.get(), this.getX() + (this.random.nextDouble() - 0.5) * 2.0,
                    this.getY() + 2.0 + this.random.nextDouble() * 1.2, this.getZ() + (this.random.nextDouble() - 0.5) * 2.0, 0.0, 0.03, 0.0);
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.attackAnimationTick > 0) {
            this.attackAnimationTick--;
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.isDormant()) {
            if (this.tickCount % 10 == 0) {
                Player near = level.getNearestPlayer(this, 6.0);
                if (near != null && !near.isCreative() && !near.isSpectator()) {
                    this.wake(level);
                    this.setTarget(near);
                }
            }
            return;
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.slamCooldown > 0) {
            this.slamCooldown--;
        }
        if (this.barrageCooldown > 0) {
            this.barrageCooldown--;
        }
        LivingEntity target = this.getTarget();
        int action = this.getAction();
        if (action == ACTION_NONE) {
            if (target != null && target.isAlive()) {
                double dist = this.distanceTo(target);
                if (dist < 6.5 && this.slamCooldown <= 0 && this.onGround()) {
                    this.setAction(ACTION_SLAM);
                    this.getNavigation().stop();
                    level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.5F, 0.6F);
                } else if (dist > 7.0 && dist < 28.0 && this.barrageCooldown <= 0 && this.hasLineOfSight(target)) {
                    this.setAction(ACTION_BARRAGE);
                    this.getNavigation().stop();
                }
            }
            return;
        }
        this.actionTicks++;
        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        if (target != null) {
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        if (action == ACTION_SLAM) {
            if (this.actionTicks == 22) {
                this.slam(level);
            }
            if (this.actionTicks >= 32) {
                this.setAction(ACTION_NONE);
                this.slamCooldown = 120 + this.random.nextInt(60);
            }
        } else if (action == ACTION_BARRAGE) {
            if (target != null && (this.actionTicks == 10 || this.actionTicks == 16 || this.actionTicks == 22)) {
                float side = this.actionTicks == 16 ? 0.0F : (this.actionTicks == 10 ? -1.0F : 1.0F);
                float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
                Vec3 shoulder = this.position().add(Math.cos(yaw) * side * 1.1, 2.9, Math.sin(yaw) * side * 1.1);
                Vec3 dir = target.getEyePosition().subtract(shoulder);
                StarboltEntity.shoot(level, this, shoulder, dir, 1.3F, 7.0F, StarboltEntity.Variant.CRYSTAL, target);
                level.playSound(null, shoulder.x, shoulder.y, shoulder.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.4F, 1.4F);
            }
            if (this.actionTicks >= 28) {
                this.setAction(ACTION_NONE);
                this.barrageCooldown = 70 + this.random.nextInt(50);
            }
        }
    }

    private void slam(ServerLevel level) {
        Vec3 center = this.position();
        for (LivingEntity victim : Combat.targetsAround(level, this, center, 6.5)) {
            victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SHOCKWAVE, this), 12.0F);
            Combat.blast(victim, center, 0.9, 0.9);
        }
        BlockState below = level.getBlockState(BlockPos.containing(center).below());
        if (!below.isAir()) {
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, below);
            for (int r = 1; r <= 3; r++) {
                Fx.ring(level, debris, center.add(0, 0.2, 0), r * 2.0, 20 + r * 10, 0.15, 0.3);
            }
        }
        Fx.ring(level, ModParticles.ASTRAL_GLINT.get(), center.add(0, 0.3, 0), 1.0, 40, 0.6, 0.05);
        Fx.burst(level, ParticleTypes.EXPLOSION, center, 3, 1.0, 0.0);
        level.playSound(null, center.x, center.y, center.z, ModSounds.GOLEM_SLAM.get(), SoundSource.HOSTILE, 2.5F, 1.0F);
        Fx.shake(level, center, 24.0, 1.0F, 14);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        this.attackAnimationTick = 10;
        level.broadcastEntityEvent(this, (byte) 4);
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.55, 0));
            target.hurtMarked = true;
            this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.7F);
        }
        return hit;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            this.attackAnimationTick = 10;
            this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.7F);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isDormant()) {
            this.wake(level);
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            damage *= 0.7F;
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
        this.bossEvent.setVisible(!this.isDormant());
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("dormant", this.isDormant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setDormant(input.getBooleanOr("dormant", false));
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GOLEM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GOLEM_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 0.7F);
    }

    private class DormantGoal extends Goal {
        DormantGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP, Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return AstralGolemEntity.this.isDormant();
        }
    }
}
