package com.starforged.entity.boss;

import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Eclipse Crystals orbit the Sovereign in its second phase and make it invulnerable. Three hits shatter one.
 */
public class EclipseCrystalEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_BOSS = SynchedEntityData.defineId(EclipseCrystalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INDEX = SynchedEntityData.defineId(EclipseCrystalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HITS = SynchedEntityData.defineId(EclipseCrystalEntity.class, EntityDataSerializers.INT);
    public static final int COUNT = 4;

    public EclipseCrystalEntity(EntityType<? extends EclipseCrystalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void bind(EclipseSovereignEntity boss, int index) {
        this.entityData.set(DATA_BOSS, boss.getId());
        this.entityData.set(DATA_INDEX, index);
        Vec3 pos = this.orbitPosition(boss, 0.0F);
        this.setPos(pos.x, pos.y, pos.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BOSS, -1);
        builder.define(DATA_INDEX, 0);
        builder.define(DATA_HITS, 3);
    }

    public @Nullable EclipseSovereignEntity boss() {
        return this.level().getEntity(this.entityData.get(DATA_BOSS)) instanceof EclipseSovereignEntity boss ? boss : null;
    }

    public int hitsLeft() {
        return this.entityData.get(DATA_HITS);
    }

    private Vec3 orbitPosition(EclipseSovereignEntity boss, float partial) {
        int index = this.entityData.get(DATA_INDEX);
        double time = (boss.tickCount + partial) * 0.025;
        double angle = time + index * (Math.PI * 2 / COUNT);
        double radius = 9.0;
        double bob = Math.sin(time * 3.0 + index) * 1.5;
        return boss.position().add(Math.cos(angle) * radius, 2.0 + bob, Math.sin(angle) * radius);
    }

    @Override
    public void tick() {
        super.tick();
        EclipseSovereignEntity boss = this.boss();
        if (boss == null || !boss.isAlive()) {
            if (!this.level().isClientSide() && this.tickCount > 5) {
                this.discard();
            }
            return;
        }
        Vec3 pos = this.orbitPosition(boss, 0.0F);
        this.setPos(pos.x, pos.y, pos.z);
        if (this.level().isClientSide() && this.random.nextInt(2) == 0) {
            this.level().addParticle(ModParticles.ASTRAL_GLINT.get(), this.getX() + (this.random.nextDouble() - 0.5), this.getY() + this.random.nextDouble() * 2.0,
                this.getZ() + (this.random.nextDouble() - 0.5), 0.0, 0.0, 0.0);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player) || this.isRemoved()) {
            return false;
        }
        int hits = this.hitsLeft() - 1;
        this.entityData.set(DATA_HITS, hits);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 2.0F, 0.6F + hits * 0.2F);
        Fx.burst(level, ModParticles.ASTRAL_GLINT.get(), this.position().add(0, 1, 0), 15, 0.4, 0.15);
        if (hits <= 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CRYSTAL_SHATTER.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
            Fx.sphere(level, ModParticles.ASTRAL_GLINT.get(), this.position().add(0, 1, 0), 0.5, 60, 0.5);
            Fx.burst(level, ParticleTypes.EXPLOSION, this.position().add(0, 1, 0), 2, 0.3, 0.0);
            Fx.shake(level, this.position(), 24.0, 0.6F, 10);
            EclipseSovereignEntity boss = this.boss();
            if (boss != null) {
                boss.onCrystalShattered();
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
