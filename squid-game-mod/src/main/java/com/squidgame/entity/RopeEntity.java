package com.squidgame.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The tug-of-war rope. The entity sits at the middle of the rope, which runs along {@link #axisYaw} from the rear-most hand of
 * team A to the rear-most hand of team B; the game moves nothing but the synced values and the client renderer draws the
 * rope from them: sag in the gap that shrinks with the strain, vibration, a travelling wave for every team pulse, the flag
 * at the current offset and the fall of the losing end.
 */
public class RopeEntity extends Entity {
    /** Rope offset in [-1, 1]: where the flag is between the platforms (+1 = at team B's edge). */
    public static final EntityDataAccessor<Float> DATA_OFFSET =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    /** How taut the rope is, 0 (slack) to 1 (humming). */
    public static final EntityDataAccessor<Float> DATA_STRAIN =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    /** Total length of the rope in blocks (the sum of both ends). */
    public static final EntityDataAccessor<Float> DATA_LENGTH =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    /** Rope axis yaw in degrees (the rope runs along this horizontal direction through the entity position). */
    public static final EntityDataAccessor<Float> DATA_AXIS_YAW =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    /** Distance from the entity to team A's end of the rope (towards -axis) and to team B's end (+axis), in blocks. */
    public static final EntityDataAccessor<Float> DATA_END_A =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_END_B =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    /** Half the width of the gap between the platforms: the flag travels this far to either side. */
    public static final EntityDataAccessor<Float> DATA_GAP_HALF =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    /** Last team pulse: its strength 0..1, signed by the side (negative = team A, positive = team B), and the game tick it happened. */
    public static final EntityDataAccessor<Float> DATA_PULSE =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Integer> DATA_PULSE_TICK =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.INT);
    /** 0 = in play, 1 = team A has lost and falls, 2 = team B has lost and falls; and the game tick that started the fall. */
    public static final EntityDataAccessor<Integer> DATA_FALL =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> DATA_FALL_TICK =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.INT);

    public RopeEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        // the rope is up to 100 blocks long but its bounding box is a point: never cull it by the box
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OFFSET, 0f);
        builder.define(DATA_STRAIN, 0f);
        builder.define(DATA_LENGTH, 24f);
        builder.define(DATA_AXIS_YAW, 90f);
        builder.define(DATA_END_A, 12f);
        builder.define(DATA_END_B, 12f);
        builder.define(DATA_GAP_HALF, 7f);
        builder.define(DATA_PULSE, 0f);
        builder.define(DATA_PULSE_TICK, 0);
        builder.define(DATA_FALL, 0);
        builder.define(DATA_FALL_TICK, 0);
    }

    public float offset() {
        return entityData.get(DATA_OFFSET);
    }

    public void setOffset(float v) {
        entityData.set(DATA_OFFSET, v);
    }

    public float strain() {
        return entityData.get(DATA_STRAIN);
    }

    public void setStrain(float v) {
        entityData.set(DATA_STRAIN, v);
    }

    public float ropeLength() {
        return entityData.get(DATA_LENGTH);
    }

    public void setRopeLength(float v) {
        entityData.set(DATA_LENGTH, v);
    }

    public float axisYaw() {
        return entityData.get(DATA_AXIS_YAW);
    }

    public void setAxisYaw(float v) {
        entityData.set(DATA_AXIS_YAW, v);
    }

    public float endA() {
        return entityData.get(DATA_END_A);
    }

    public float endB() {
        return entityData.get(DATA_END_B);
    }

    /** Sets both ends (distances from the entity along the axis) and the total length. */
    public void setEnds(float a, float b) {
        entityData.set(DATA_END_A, a);
        entityData.set(DATA_END_B, b);
        setRopeLength(a + b);
    }

    public float gapHalf() {
        return entityData.get(DATA_GAP_HALF);
    }

    public void setGapHalf(float v) {
        entityData.set(DATA_GAP_HALF, v);
    }

    public float pulse() {
        return entityData.get(DATA_PULSE);
    }

    public int pulseTick() {
        return entityData.get(DATA_PULSE_TICK);
    }

    /** A team pulse of strength {@code strength} (0..1) from team A ({@code teamB} false) or B at game tick {@code tick}. */
    public void firePulse(boolean teamB, float strength, long tick) {
        entityData.set(DATA_PULSE, teamB ? strength : -strength);
        entityData.set(DATA_PULSE_TICK, (int) tick);
    }

    /** 0 = in play, 1 = team A falls, 2 = team B falls. */
    public int fall() {
        return entityData.get(DATA_FALL);
    }

    public int fallTick() {
        return entityData.get(DATA_FALL_TICK);
    }

    public void setFall(int state, long tick) {
        entityData.set(DATA_FALL, state);
        entityData.set(DATA_FALL_TICK, (int) tick);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setRopeLength(tag.contains("Length") ? tag.getFloat("Length") : 24f);
        setAxisYaw(tag.contains("AxisYaw") ? tag.getFloat("AxisYaw") : 90f);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Length", ropeLength());
        tag.putFloat("AxisYaw", axisYaw());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 256.0 * 256.0;
    }
}
