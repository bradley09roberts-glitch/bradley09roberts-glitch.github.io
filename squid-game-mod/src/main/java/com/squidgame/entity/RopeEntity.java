package com.squidgame.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The tug-of-war rope: a fixed pair of end anchors (set by the game) with a red flag whose offset along the rope and
 * strain are synced; the client renders the sagging rope. Owned and fleshed out by the Tug of War game.
 */
public class RopeEntity extends Entity {
    public static final EntityDataAccessor<Float> DATA_OFFSET =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_STRAIN =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> DATA_LENGTH =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);
    /** Rope axis yaw in degrees (the rope runs along this horizontal direction through the entity position). */
    public static final EntityDataAccessor<Float> DATA_AXIS_YAW =
            SynchedEntityData.defineId(RopeEntity.class, EntityDataSerializers.FLOAT);

    public RopeEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OFFSET, 0f);
        builder.define(DATA_STRAIN, 0f);
        builder.define(DATA_LENGTH, 24f);
        builder.define(DATA_AXIS_YAW, 90f);
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
