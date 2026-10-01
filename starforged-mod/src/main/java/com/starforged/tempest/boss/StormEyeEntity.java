package com.starforged.tempest.boss;

import com.starforged.registry.ModParticles;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** An Eye of the Storm: a drifting circle of calm during Veyr's Last Thunder. Inside it, the lightning can't reach you. */
public class StormEyeEntity extends Entity {
    public static final double RADIUS = 3.5;
    private Vec3 center = Vec3.ZERO;
    private double orbit;
    private double angle;
    private double speed;
    private int life;
    private int maxLife = 400;

    public StormEyeEntity(EntityType<? extends StormEyeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void drift(Vec3 center, double orbit, double angle, double speed, int life) {
        this.center = center;
        this.orbit = orbit;
        this.angle = angle;
        this.speed = speed;
        this.maxLife = life;
        this.place();
    }

    private void place() {
        this.setPos(this.center.x + Math.cos(this.angle) * this.orbit, this.center.y, this.center.z + Math.sin(this.angle) * this.orbit);
    }

    public boolean contains(Vec3 pos) {
        double dx = pos.x - this.getX();
        double dz = pos.z - this.getZ();
        return dx * dx + dz * dz < RADIUS * RADIUS && Math.abs(pos.y - this.getY()) < 6.0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            for (int i = 0; i < 3; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                this.level().addParticle(ModParticles.STORM_WISP.get(), this.getX() + Math.cos(a) * RADIUS, this.getY() + 0.1,
                    this.getZ() + Math.sin(a) * RADIUS, -Math.sin(a) * 0.08, 0.04, Math.cos(a) * 0.08);
            }
            return;
        }
        if (++this.life > this.maxLife) {
            this.discard();
            return;
        }
        this.angle += this.speed;
        this.place();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
