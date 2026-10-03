package com.terracraft.entity.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Terraria's Man Eater / Snatcher: a carnivorous plant rooted to the block it spawned on. Its head sways on a
 * vine within {@code reach} blocks of the root and lunges at anything that comes close.
 */
public class SnapperMob extends TerrariaMob {
    private final double reach;
    private @Nullable BlockPos root;
    private int lungeTimer;

    public SnapperMob(EntityType<? extends SnapperMob> type, Level level, double reach) {
        super(type, level);
        this.reach = reach;
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public @Nullable BlockPos root() {
        return root;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData groupData) {
        root = blockPosition().below();
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (root == null) {
            root = blockPosition().below();
        }
        Vec3 anchor = Vec3.atCenterOf(root).add(0, 1.2, 0);
        LivingEntity target = getTarget();
        Vec3 goal;
        if (target != null && target.isAlive() && target.distanceToSqr(anchor) < (reach + 4) * (reach + 4)) {
            Vec3 toward = target.getEyePosition().subtract(anchor);
            boolean lunging = --lungeTimer < 10;
            if (lungeTimer <= 0) {
                lungeTimer = 30 + random.nextInt(20);
            }
            double length = Math.min(toward.length(), lunging ? reach : reach * 0.45);
            goal = anchor.add(toward.normalize().scale(length));
            float yaw = (float) (Mth.atan2(toward.z, toward.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            setXRot((float) -(Mth.atan2(toward.y, toward.horizontalDistance()) * Mth.RAD_TO_DEG));
        } else {
            goal = anchor.add(Mth.sin(tickCount * 0.05F) * 1.2, 1.0 + Mth.sin(tickCount * 0.08F) * 0.4, Mth.cos(tickCount * 0.04F) * 1.2);
        }
        Vec3 motion = goal.subtract(position()).scale(0.25);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (root != null) {
            output.putLong("Root", root.asLong());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        root = input.getLong("Root").map(BlockPos::of).orElse(null);
    }
}
