package com.starforged.entity.monster;

import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Star Mites: skittering crystal-backed bugs that pour out of fresh meteorites in swarms.
 * Hurt one and the whole swarm turns on you. They burst into stardust when they die.
 */
public class StarMiteEntity extends Monster {
    public StarMiteEntity(EntityType<? extends StarMiteEntity> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 10.0)
            .add(Attributes.MOVEMENT_SPEED, 0.32)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 24.0)
            .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.25, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.random.nextInt(6) == 0) {
            this.level().addParticle(ModParticles.ASTRAL_GLINT.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.5, this.getY() + 0.45,
                this.getZ() + (this.random.nextDouble() - 0.5) * 0.5, 0.0, 0.02, 0.0);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.STAR_SPARKLE.get(), this.getX(), this.getY() + 0.3, this.getZ(), 20, 0.3, 0.2, 0.3, 0.1);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MITE_CHITTER.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MITE_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SILVERFISH_STEP, 0.15F, 1.4F);
    }
}
