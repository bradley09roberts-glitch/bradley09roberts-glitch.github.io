package com.terracraft.entity.boss;

import com.terracraft.entity.mob.TerrariaMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A separate piece of a boss (Plantera's hooks and tentacles, Golem's head and fists): flies with no physics, is
 * chained to its boss ({@link #setTether}), and vanishes when the boss is gone.
 */
public abstract class BossPart<B extends TerrariaBoss> extends TerrariaMob {
    private final Class<B> ownerClass;
    protected @Nullable UUID ownerId;

    protected BossPart(EntityType<? extends BossPart<B>> type, Level level, Class<B> ownerClass) {
        super(type, level);
        this.ownerClass = ownerClass;
        setNoGravity(true);
        noPhysics = true;
    }

    public void attach(B owner) {
        ownerId = owner.getUUID();
        setTether(owner);
    }

    protected @Nullable B owner(ServerLevel level) {
        return ownerId != null && ownerClass.isInstance(level.getEntity(ownerId)) && level.getEntity(ownerId).isAlive()
            ? ownerClass.cast(level.getEntity(ownerId)) : null;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean countsTowardSpawnCap() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || super.isInvulnerableTo(level, source);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        B owner = owner(level);
        if (owner == null) {
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 8, 0.3, 0.3, 0.3, 0.05);
            discard();
            return;
        }
        if (tetheredToOwner() && tetherId() != owner.getId()) {
            setTether(owner);
        }
        tickPart(level, owner);
    }

    /** Whether the renderer draws a vine/chain to the boss. */
    protected boolean tetheredToOwner() {
        return true;
    }

    protected abstract void tickPart(ServerLevel level, B owner);

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (ownerId != null) {
            output.store("Owner", UUIDUtil.CODEC, ownerId);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }
}
