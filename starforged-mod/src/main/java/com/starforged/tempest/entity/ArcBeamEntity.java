package com.starforged.tempest.entity;

import com.starforged.tempest.TempestEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** A short-lived visual: the Arc Cannon's beam (and Veyr's lances). Damage is dealt by whoever fires it. */
public class ArcBeamEntity extends Entity {
    private static final EntityDataAccessor<Vector3fc> DATA_END = SynchedEntityData.defineId(ArcBeamEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(ArcBeamEntity.class, EntityDataSerializers.FLOAT);
    public static final int LIFE = 8;

    public ArcBeamEntity(EntityType<? extends ArcBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void fire(ServerLevel level, Vec3 from, Vec3 to, float width) {
        ArcBeamEntity beam = new ArcBeamEntity(TempestEntities.ARC_BEAM.get(), level);
        beam.setPos(from.x, from.y, from.z);
        beam.entityData.set(DATA_END, new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z)));
        beam.entityData.set(DATA_WIDTH, width);
        level.addFreshEntity(beam);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_END, new Vector3f());
        builder.define(DATA_WIDTH, 0.5F);
    }

    /** The beam's end, relative to its start. */
    public Vec3 end() {
        Vector3fc v = this.entityData.get(DATA_END);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public float width() {
        return this.entityData.get(DATA_WIDTH);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount > LIFE) {
            this.discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
