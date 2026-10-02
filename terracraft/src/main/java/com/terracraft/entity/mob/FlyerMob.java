package com.terracraft.entity.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Flying enemies.
 * <ul>
 *     <li>{@link Style#CHASER} (Demon Eye): accelerates straight at the target, overshoots, bounces off walls
 *     and off the player after a hit.</li>
 *     <li>{@link Style#ERRATIC} (bats): picks a new jittery heading every few ticks, biased toward the target.</li>
 * </ul>
 */
public class FlyerMob extends TerrariaMob {
    public enum Style { CHASER, ERRATIC }

    private final Style style;
    private int headingTimer;

    public FlyerMob(EntityType<? extends FlyerMob> type, Level level, Style style) {
        super(type, level);
        this.style = style;
        setNoGravity(true);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    private double acceleration() {
        return getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.14;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Vec3 motion = getDeltaMovement();
        LivingEntity target = getTarget();
        boolean fleeing = definition().despawnsAtDay() && level.isBrightOutside();
        if (fleeing) {
            motion = motion.add(0.0, acceleration() * 0.6, 0.0);
        } else if (style == Style.CHASER) {
            Vec3 goal = target != null ? target.getEyePosition() : position().add(Mth.sin(tickCount * 0.05F) * 4.0, 0.0, Mth.cos(tickCount * 0.05F) * 4.0);
            Vec3 toward = goal.subtract(position()).normalize();
            motion = motion.add(toward.scale(target != null ? acceleration() : acceleration() * 0.3));
        } else if (--headingTimer <= 0) {
            headingTimer = 8 + random.nextInt(12);
            Vec3 heading = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).normalize();
            if (target != null && random.nextFloat() < 0.65F) {
                heading = heading.add(target.getEyePosition().subtract(position()).normalize().scale(1.5)).normalize();
            }
            motion = heading.scale(acceleration() * 6.0);
        }
        if (horizontalCollision) {
            motion = new Vec3(-motion.x * 0.7, motion.y, -motion.z * 0.7);
        }
        if (verticalCollision) {
            motion = new Vec3(motion.x, -motion.y * 0.7, motion.z);
        }
        setDeltaMovement(motion);
        if (motion.horizontalDistanceSqr() > 1.0E-4) {
            float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
        }
        setXRot((float) -(Mth.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    @Override
    protected void onContactHit(LivingEntity victim) {
        if (style == Style.CHASER) {
            Vec3 away = position().subtract(victim.position()).normalize();
            setDeltaMovement(away.scale(0.35));
        }
    }

    @Override
    protected void leaveAtDawn(ServerLevel level) {
        if (level.getNearestPlayer(this, 32.0) == null || getY() > level.getMaxY() - 8) {
            discard();
        }
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }
}
