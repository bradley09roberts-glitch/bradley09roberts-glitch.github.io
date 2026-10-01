package com.starforged.entity.monster;

import com.starforged.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Mimic: a treasure chest that is very much alive. It sits perfectly still among real chests in the observatory
 * until someone tries to open it - then it lunges, chomping, and hops after its victim.
 * Defeat it for some of the best loot in the observatory.
 */
public class MimicEntity extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_DISGUISED = SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.BOOLEAN);

    private int idleTicks;
    /** Client-side chomp animation timer. */
    public int chompTicks;

    public MimicEntity(EntityType<? extends MimicEntity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 8.0)
            .add(Attributes.ARMOR, 6.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DISGUISED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new DisguisedGoal());
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35, true));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> !this.isDisguised()));
    }

    public boolean isDisguised() {
        return this.entityData.get(DATA_DISGUISED);
    }

    /** Hide as a chest facing the given yaw (snapped to 90 degrees). */
    public void disguise(float yaw) {
        this.entityData.set(DATA_DISGUISED, true);
        float snapped = Math.round(yaw / 90.0F) * 90.0F;
        this.setYRot(snapped);
        this.yBodyRot = snapped;
        this.yHeadRot = snapped;
        this.setTarget(null);
    }

    public void reveal(ServerLevel level, LivingEntity victim) {
        if (!this.isDisguised()) {
            return;
        }
        this.entityData.set(DATA_DISGUISED, false);
        this.idleTicks = 0;
        if (victim != null && !(victim instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            this.setTarget(victim);
        }
        this.setDeltaMovement(this.getDeltaMovement().add(0, 0.55, 0));
        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.MIMIC_REVEAL.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 1.0, this.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.6, this.getZ(), 20, 0.4, 0.3, 0.4, 0.2);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.isDisguised()) {
            if (this.level() instanceof ServerLevel server) {
                this.reveal(server, player);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isDisguised()) {
            this.reveal(level, source.getEntity() instanceof LivingEntity living ? living : null);
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.isDisguised()) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            if (++this.idleTicks > 300 && this.onGround()) {
                this.disguise(this.getYRot());
            }
            return;
        }
        this.idleTicks = 0;
        // Hop toward the target like a living chest.
        if (this.onGround() && this.getNavigation().isInProgress() && this.random.nextInt(8) == 0) {
            this.getJumpControl().jump();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.chompTicks > 0) {
                this.chompTicks--;
            }
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        level.broadcastEntityEvent(this, (byte) 4);
        boolean hit = super.doHurtTarget(level, target);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.MIMIC_CHOMP.get(), SoundSource.HOSTILE, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        return hit;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            this.chompTicks = 10;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean isPushable() {
        return !this.isDisguised() && super.isPushable();
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isDisguised() ? null : ModSounds.MIMIC_CHOMP.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CHEST_CLOSE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("disguised", this.isDisguised());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_DISGUISED, input.getBooleanOr("disguised", false));
    }

    private class DisguisedGoal extends Goal {
        DisguisedGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return MimicEntity.this.isDisguised();
        }

        @Override
        public void tick() {
            MimicEntity.this.setDeltaMovement(0, MimicEntity.this.getDeltaMovement().y, 0);
            MimicEntity.this.yHeadRot = MimicEntity.this.getYRot();
            MimicEntity.this.yBodyRot = MimicEntity.this.getYRot();
        }
    }
}
