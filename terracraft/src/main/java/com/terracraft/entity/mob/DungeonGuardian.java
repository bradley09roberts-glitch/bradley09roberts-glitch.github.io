package com.terracraft.entity.mob;

import com.terracraft.world.dungeon.DungeonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Dungeon Guardian: appears when someone enters the Dungeon before Skeletron has been defeated, flies
 * through walls, spins toward its victim and kills almost instantly. It cannot really be hurt and leaves once
 * its victim is dead or out of the Dungeon.
 */
public class DungeonGuardian extends TerrariaMob {
    private @Nullable UUID victim;
    private int lostTicks;

    public DungeonGuardian(EntityType<? extends DungeonGuardian> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        setPersistenceRequired();
    }

    public void hunt(Player player) {
        victim = player.getUUID();
        setTarget(player);
    }

    public @Nullable UUID victim() {
        return victim;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean countsTowardSpawnCap() {
        return false;
    }

    @Override
    public float spriteSpin(float partialTicks) {
        return (tickCount + partialTicks) * 25.0F;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(DamageTypes.GENERIC_KILL) || super.isInvulnerableTo(level, source);
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Player target = victim != null ? level.getPlayerByUUID(victim) : null;
        if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator() || !DungeonManager.guardianShouldHunt(level, target)) {
            if (++lostTicks > 60) {
                discard();
            }
            setDeltaMovement(getDeltaMovement().scale(0.9).add(0, 0.05, 0));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        lostTicks = 0;
        Vec3 toward = target.getEyePosition().subtract(0, 0.6, 0).subtract(position()).normalize();
        Vec3 motion = getDeltaMovement().scale(0.92).add(toward.scale(0.08));
        if (motion.length() > 0.9) {
            motion = motion.normalize().scale(0.9);
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        Vec3 look = target.getEyePosition().subtract(position());
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        if (tickCount % 40 == 0) {
            playSound(SoundEvents.SKELETON_AMBIENT, 2.0F, 0.4F);
        }
    }

    @Override
    public boolean showsHealthBar() {
        return false;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (victim != null) {
            output.store("Victim", UUIDUtil.CODEC, victim);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        victim = input.read("Victim", UUIDUtil.CODEC).orElse(null);
    }
}
