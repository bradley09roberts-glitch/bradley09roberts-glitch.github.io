package com.terracraft.entity.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Terraria slime AI: sits still, then hops toward its target (alternating small and big hops); without a
 * target it hops around aimlessly. All damage is contact damage.
 */
public class SlimeMob extends TerrariaMob {
    private int jumpDelay = 20;
    private float hopDirection;

    public SlimeMob(EntityType<? extends SlimeMob> type, Level level) {
        super(type, level);
        this.hopDirection = random.nextFloat() * 360.0F;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!onGround()) {
            return;
        }
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(motion.x * 0.5, motion.y, motion.z * 0.5);
        if (--jumpDelay > 0) {
            return;
        }
        LivingEntity target = getTarget();
        boolean chasing = target != null && target.isAlive();
        if (chasing) {
            hopDirection = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        } else if (random.nextInt(3) == 0) {
            hopDirection += (random.nextFloat() - 0.5F) * 120.0F;
        }
        boolean bigHop = random.nextInt(3) == 0;
        double forward = (bigHop ? 0.2 : 0.3) * (chasing ? 1.0 : 0.6) * getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) / 0.25;
        double up = bigHop ? 0.62 : 0.45;
        float rad = hopDirection * Mth.DEG_TO_RAD;
        setDeltaMovement(-Mth.sin(rad) * forward, up, Mth.cos(rad) * forward);
        needsSync = true;
        setYRot(hopDirection);
        yBodyRot = hopDirection;
        jumpDelay = chasing ? 18 + random.nextInt(14) : 40 + random.nextInt(50);
    }

    @Override
    public int getMaxHeadXRot() {
        return 0;
    }
}
