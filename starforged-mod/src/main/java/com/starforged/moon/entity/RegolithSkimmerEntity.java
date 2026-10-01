package com.starforged.moon.entity;

import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModParticles;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Regolith Skimmers swim beneath the lunar dust like sharks - only a fin cutting through the regolith gives them away.
 * Close in, they erupt out of the ground in a leaping bite, then thrash on the surface for a few seconds before diving
 * again. Submerged, they cannot be hurt by blades or arrows.
 */
public class RegolithSkimmerEntity extends Monster {
    private static final EntityDataAccessor<Boolean> DATA_SUBMERGED = SynchedEntityData.defineId(RegolithSkimmerEntity.class, EntityDataSerializers.BOOLEAN);
    private int surfacedTicks;
    public float clientEmerge;

    public RegolithSkimmerEntity(EntityType<? extends RegolithSkimmerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 34.0)
            .add(Attributes.MOVEMENT_SPEED, 0.34)
            .add(Attributes.ATTACK_DAMAGE, 7.0)
            .add(Attributes.ARMOR, 4.0)
            .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SUBMERGED, true);
    }

    public boolean isSubmerged() {
        return this.entityData.get(DATA_SUBMERGED);
    }

    private void setSubmerged(boolean submerged) {
        this.entityData.set(DATA_SUBMERGED, submerged);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new EruptGoal());
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        float goal = this.isSubmerged() ? 0.0F : 1.0F;
        this.clientEmerge += (goal - this.clientEmerge) * 0.25F;
        if (this.level().isClientSide()) {
            if (this.isSubmerged() && this.getDeltaMovement().horizontalDistanceSqr() > 0.002) {
                BlockState below = this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.2, this.getZ()));
                if (!below.isAir()) {
                    for (int i = 0; i < 2; i++) {
                        this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below), this.getX() + (this.random.nextDouble() - 0.5),
                            this.getY() + 0.1, this.getZ() + (this.random.nextDouble() - 0.5), 0.0, 0.1, 0.0);
                    }
                }
            }
            return;
        }
        if (!this.isSubmerged() && ++this.surfacedTicks > 70 && this.onGround()) {
            this.setSubmerged(true);
            this.surfacedTicks = 0;
            this.playSound(MoonSounds.SKIMMER_AMBIENT.get(), 0.8F, 0.6F);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isSubmerged() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.IS_EXPLOSION)) {
            level.sendParticles(ModParticles.MOON_DUST.get(), this.getX(), this.getY() + 0.3, this.getZ(), 6, 0.4, 0.1, 0.4, 0.02);
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public boolean isInvisible() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MoonSounds.SKIMMER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MoonSounds.SKIMMER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MoonSounds.SKIMMER_DEATH.get();
    }

    /** Stalk under the dust, then burst up and bite. */
    private class EruptGoal extends Goal {
        private int cooldown = 20;
        private int airborne;

        EruptGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = RegolithSkimmerEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            RegolithSkimmerEntity skimmer = RegolithSkimmerEntity.this;
            LivingEntity target = skimmer.getTarget();
            if (target == null) {
                return;
            }
            skimmer.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.airborne > 0) {
                this.airborne++;
                if (skimmer.getBoundingBox().inflate(0.6).intersects(target.getBoundingBox()) && skimmer.level() instanceof ServerLevel server) {
                    skimmer.doHurtTarget(server, target);
                    this.airborne = 0;
                } else if (this.airborne > 30 || (skimmer.onGround() && this.airborne > 5)) {
                    this.airborne = 0;
                }
                return;
            }
            double dist = skimmer.distanceTo(target);
            skimmer.getNavigation().moveTo(target, skimmer.isSubmerged() ? 1.4 : 0.9);
            if (--this.cooldown <= 0 && dist < 4.5 && skimmer.onGround() && skimmer.level() instanceof ServerLevel server) {
                skimmer.setSubmerged(false);
                skimmer.surfacedTicks = 0;
                Vec3 dir = target.position().subtract(skimmer.position()).multiply(1, 0, 1).normalize();
                skimmer.setDeltaMovement(dir.x * 0.7, 0.6, dir.z * 0.7);
                this.airborne = 1;
                this.cooldown = 50;
                BlockState below = server.getBlockState(skimmer.blockPosition().below());
                if (!below.isAir()) {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), skimmer.getX(), skimmer.getY() + 0.3, skimmer.getZ(),
                        40, 0.8, 0.2, 0.8, 0.2);
                }
                server.playSound(null, skimmer.getX(), skimmer.getY(), skimmer.getZ(), MoonSounds.SKIMMER_ERUPT.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
            }
        }
    }
}
