package com.starforged.sun.boss;

import com.starforged.registry.ModParticles;
import com.starforged.sun.SunSounds;
import com.starforged.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Solar Pylons rise around the arena when the Sun Warden begins its Supernova. They feed it sunlight (and shield it)
 * until shattered - four hits each.
 */
public class SolarPylonEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_BOSS = SynchedEntityData.defineId(SolarPylonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HITS = SynchedEntityData.defineId(SolarPylonEntity.class, EntityDataSerializers.INT);
    public static final int HITS = 4;
    private int age;

    public SolarPylonEntity(EntityType<? extends SolarPylonEntity> type, Level level) {
        super(type, level);
    }

    public void bind(SunWardenEntity boss) {
        this.entityData.set(DATA_BOSS, boss.getId());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BOSS, -1);
        builder.define(DATA_HITS, HITS);
    }

    public @Nullable SunWardenEntity boss() {
        return this.level().getEntity(this.entityData.get(DATA_BOSS)) instanceof SunWardenEntity boss ? boss : null;
    }

    public int hitsLeft() {
        return this.entityData.get(DATA_HITS);
    }

    public int age() {
        return this.age;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        SunWardenEntity boss = this.boss();
        if (boss == null || !boss.isAlive()) {
            if (!this.level().isClientSide() && this.age > 5) {
                this.discard();
            }
            return;
        }
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.08, 0).scale(0.9));
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
        }
        if (this.level().isClientSide()) {
            if (this.random.nextInt(2) == 0) {
                this.level().addParticle(ModParticles.SOLAR_SPARK.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
                    this.getY() + this.random.nextDouble() * 3.0, this.getZ() + (this.random.nextDouble() - 0.5) * 0.8, 0.0, 0.03, 0.0);
            }
        } else if (this.age % 20 == 0 && boss.getHealth() < boss.getMaxHealth()) {
            boss.heal(2.0F);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!(source.getEntity() instanceof Player) || this.isRemoved()) {
            return false;
        }
        int hits = this.hitsLeft() - 1;
        this.entityData.set(DATA_HITS, hits);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.2F, 1.4F + hits * 0.1F);
        Fx.burst(level, ModParticles.SOLAR_SPARK.get(), this.position().add(0, 1.5, 0), 20, 0.4, 0.15);
        if (hits <= 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.PYLON_SHATTER.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
            Fx.sphere(level, ModParticles.SOLAR_SPARK.get(), this.position().add(0, 1.5, 0), 0.5, 70, 0.5);
            Fx.burst(level, ParticleTypes.EXPLOSION, this.position().add(0, 1.5, 0), 2, 0.3, 0.0);
            Fx.shake(level, this.position(), 24.0, 0.6F, 10);
            SunWardenEntity boss = this.boss();
            if (boss != null) {
                boss.onPylonShattered(level);
            }
            this.discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
