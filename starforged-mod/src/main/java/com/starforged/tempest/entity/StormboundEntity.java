package com.starforged.tempest.entity;

import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.world.StormNetwork;
import com.starforged.util.Combat;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.phys.Vec3;

/**
 * The Stormbound: ancient spear-warriors of Stormreach, bound to their armour by lightning. Up close they strike in a
 * three-hit <b>Spear Combo</b>; keep your distance and they <b>Thunderstep</b> - vanish in a bolt and land behind you.
 */
public class StormboundEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_COMBO = SynchedEntityData.defineId(StormboundEntity.class, EntityDataSerializers.INT);

    public StormboundEntity(EntityType<? extends StormboundEntity> type, Level level) {
        super(type, level);
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 52.0)
            .add(Attributes.ARMOR, 10.0)
            .add(Attributes.ARMOR_TOUGHNESS, 2.0)
            .add(Attributes.MOVEMENT_SPEED, 0.27)
            .add(Attributes.ATTACK_DAMAGE, 6.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COMBO, 0);
    }

    /** Ticks into the current spear combo (0 = not attacking). */
    public int combo() {
        return this.entityData.get(DATA_COMBO);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new WarriorGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.random.nextInt(3) == 0) {
            this.level().addParticle(ModParticles.STATIC_SPARK.get(), this.getRandomX(0.8), this.getY() + 0.5 + this.random.nextDouble() * 1.8,
                this.getRandomZ(0.8), 0, 0, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return TempestSounds.STORMBOUND_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TempestSounds.STORMBOUND_DEATH.get();
    }

    /** Close in for the combo; Thunderstep behind targets that keep away. */
    private class WarriorGoal extends Goal {
        private int stepCooldown = 60;
        private int comboCooldown;

        WarriorGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = StormboundEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void stop() {
            StormboundEntity.this.entityData.set(DATA_COMBO, 0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            StormboundEntity warrior = StormboundEntity.this;
            LivingEntity target = warrior.getTarget();
            if (target == null || !(warrior.level() instanceof ServerLevel server)) {
                return;
            }
            warrior.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double dist = warrior.distanceTo(target);
            int combo = warrior.combo();
            if (combo > 0) {
                warrior.entityData.set(DATA_COMBO, combo + 1);
                if ((combo == 6 || combo == 14 || combo == 24) && dist < 4.0) {
                    warrior.swing(InteractionHand.MAIN_HAND);
                    warrior.doHurtTarget(server, target);
                    if (combo == 24) {
                        Combat.blast(target, warrior.position(), 1.0, 0.4);
                        server.sendParticles(ModParticles.STATIC_SPARK.get(), target.getX(), target.getY() + 1, target.getZ(), 15, 0.3, 0.5, 0.3, 0.1);
                    }
                }
                if (combo > 30) {
                    warrior.entityData.set(DATA_COMBO, 0);
                    this.comboCooldown = 25;
                }
                return;
            }
            if (dist > 2.5) {
                warrior.getNavigation().moveTo(target, 1.1);
            } else {
                warrior.getNavigation().stop();
            }
            if (--this.comboCooldown <= 0 && dist < 3.5) {
                warrior.entityData.set(DATA_COMBO, 1);
                warrior.getNavigation().stop();
                return;
            }
            if (--this.stepCooldown <= 0 && dist > 6.0 && dist < 24.0) {
                this.thunderstep(server, warrior, target);
                this.stepCooldown = 120 + warrior.random.nextInt(60);
            }
        }

        private void thunderstep(ServerLevel server, StormboundEntity warrior, LivingEntity target) {
            Vec3 behind = target.position().subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(2.0));
            BlockPos land = BlockPos.containing(behind);
            if (!server.getBlockState(land).getCollisionShape(server, land).isEmpty()
                || server.getBlockState(land.below()).getCollisionShape(server, land.below()).isEmpty()) {
                behind = target.position().add(target.getX() > warrior.getX() ? 1.5 : -1.5, 0, 0);
            }
            StormNetwork.visualBolt(server, warrior.blockPosition());
            warrior.teleportTo(behind.x, behind.y, behind.z);
            TempestFx.strike(server, behind, warrior, 5.0F, 2.0);
            server.playSound(null, behind.x, behind.y, behind.z, TempestSounds.STORMBOUND_STEP.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
            warrior.entityData.set(DATA_COMBO, 1);
        }
    }
}
