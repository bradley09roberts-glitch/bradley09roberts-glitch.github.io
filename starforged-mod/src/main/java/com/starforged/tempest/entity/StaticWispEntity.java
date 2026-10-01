package com.starforged.tempest.entity;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.world.StormNetwork;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
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
 * Static Wisps drift through the Static Grove in swarms. Alone, a wisp only stings (<b>Arc Pulse</b>); together they
 * form a <b>Static Network</b> - live arcs jump between every pair of wisps closer than six blocks, and anyone caught
 * on an arc is shocked.
 */
public class StaticWispEntity extends Monster {
    private static final double LINK_RANGE = 6.0;

    public StaticWispEntity(EntityType<? extends StaticWispEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.setNoGravity(true);
        this.xpReward = 4;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 18.0)
            .add(Attributes.FLYING_SPEED, 0.55)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new PulseGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, StaticWispEntity.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if ((this.tickCount + this.getId()) % 10 != 0) {
            return;
        }
        // Static Network: arc to every wisp with a higher id in range (so each pair is drawn once).
        List<StaticWispEntity> others = level.getEntitiesOfClass(StaticWispEntity.class, this.getBoundingBox().inflate(LINK_RANGE),
            w -> w.getId() > this.getId() && w.isAlive());
        for (StaticWispEntity other : others) {
            Vec3 a = this.position().add(0, 0.3, 0);
            Vec3 b = other.position().add(0, 0.3, 0);
            StormNetwork.arcTo(level, a, b);
            for (Player player : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(a, b).inflate(1.0),
                p -> !p.isCreative() && !p.isSpectator())) {
                if (distanceToSegment(player.position().add(0, 1.0, 0), a, b) < 0.9) {
                    player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STORM, this), 3.0F);
                }
            }
        }
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / Math.max(1.0E-6, ab.lengthSqr())));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.random.nextInt(2) == 0) {
            this.level().addParticle(ModParticles.STATIC_SPARK.get(), this.getRandomX(0.6), this.getY() + this.random.nextDouble() * 0.6,
                this.getRandomZ(0.6), 0.0, -0.02, 0.0);
        }
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return TempestSounds.WISP_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TempestSounds.WISP_ARC.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TempestSounds.WISP_DEATH.get();
    }

    /** Hovers a few blocks off the target and stings it with arcs. */
    private class PulseGoal extends Goal {
        private int cooldown = 20;

        PulseGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = StaticWispEntity.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            StaticWispEntity wisp = StaticWispEntity.this;
            LivingEntity target = wisp.getTarget();
            if (target == null || !(wisp.level() instanceof ServerLevel server)) {
                return;
            }
            double angle = wisp.tickCount * 0.04 + wisp.getId() * 1.7;
            Vec3 orbit = target.position().add(Math.cos(angle) * 3.5, 1.8 + Math.sin(wisp.tickCount * 0.1) * 0.5, Math.sin(angle) * 3.5);
            wisp.getMoveControl().setWantedPosition(orbit.x, orbit.y, orbit.z, 1.1);
            wisp.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (--this.cooldown <= 0 && wisp.distanceTo(target) < 6.0 && wisp.hasLineOfSight(target)) {
                TempestFx.zap(server, wisp.position().add(0, 0.3, 0), target, wisp, 3.0F);
                server.playSound(null, wisp.getX(), wisp.getY(), wisp.getZ(), TempestSounds.WISP_ARC.get(), SoundSource.HOSTILE, 0.8F, 1.2F);
                this.cooldown = 30 + wisp.random.nextInt(20);
            }
        }
    }
}
