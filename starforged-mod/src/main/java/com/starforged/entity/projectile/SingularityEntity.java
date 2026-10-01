package com.starforged.entity.projectile;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModEntities;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.UUID;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A miniature black hole. It drags creatures, items and projectiles into its event horizon, then collapses
 * in a violent burst of light.
 */
public class SingularityEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_LIFETIME = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_POWER = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);

    private @Nullable UUID ownerId;
    private int age;

    public SingularityEntity(EntityType<? extends SingularityEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SingularityEntity spawn(ServerLevel level, Vec3 pos, @Nullable Entity owner, int lifetime, float power) {
        SingularityEntity hole = new SingularityEntity(ModEntities.SINGULARITY.get(), level);
        hole.setPos(pos.x, pos.y, pos.z);
        hole.ownerId = owner == null ? null : owner.getUUID();
        hole.entityData.set(DATA_LIFETIME, lifetime);
        hole.entityData.set(DATA_POWER, power);
        level.addFreshEntity(hole);
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.SINGULARITY_HUM.get(), SoundSource.NEUTRAL, 2.0F, 0.8F);
        return hole;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFETIME, 100);
        builder.define(DATA_POWER, 1.0F);
    }

    public int lifetime() {
        return this.entityData.get(DATA_LIFETIME);
    }

    public float power() {
        return this.entityData.get(DATA_POWER);
    }

    public int age() {
        return this.age;
    }

    public double radius() {
        return 6.0 + this.power() * 2.0;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        Vec3 center = this.position();
        double radius = this.radius();

        if (this.level().isClientSide()) {
            // Accretion disk: portal particles converge on their origin, so spawning them at the centre with an
            // offset makes them spiral inward.
            for (int i = 0; i < 6; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2;
                double r = 1.2 + this.random.nextDouble() * (radius * 0.6);
                double tilt = Math.sin(angle) * 0.35;
                this.level().addParticle(ParticleTypes.PORTAL, center.x, center.y, center.z, Math.cos(angle) * r, tilt * r * 0.4, Math.sin(angle) * r);
            }
            if (this.random.nextInt(2) == 0) {
                this.level().addParticle(ModParticles.VOID_MOTE.get(), center.x + (this.random.nextDouble() - 0.5) * 3,
                    center.y + (this.random.nextDouble() - 0.5) * 3, center.z + (this.random.nextDouble() - 0.5) * 3, 0.0, 0.0, 0.0);
            }
            return;
        }

        ServerLevel level = (ServerLevel) this.level();
        Entity owner = this.ownerId == null ? null : level.getEntity(this.ownerId);
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.SINGULARITY, this, owner);
        for (Entity entity : level.getEntities(this, new AABB(center, center).inflate(radius), e -> e.isAlive() && !e.isSpectator() && !(e instanceof SingularityEntity))) {
            if (entity instanceof LivingEntity living && !Combat.canHit(level, owner, living)) {
                continue;
            }
            if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity) && !(entity instanceof Projectile)) {
                continue;
            }
            Vec3 toCenter = center.subtract(entity.position().add(0, entity.getBbHeight() * 0.5, 0));
            double dist = toCenter.length();
            if (dist > radius || dist < 1.0E-3) {
                continue;
            }
            double strength = 0.035 + 0.14 * (1.0 - dist / radius) * this.power();
            Vec3 tangent = new Vec3(-toCenter.z, 0, toCenter.x).normalize().scale(strength * 0.35);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.88).add(toCenter.normalize().scale(strength)).add(tangent));
            entity.hurtMarked = true;
            entity.resetFallDistance();
            if (entity instanceof LivingEntity living && dist < 2.0 && this.age % 8 == 0) {
                living.hurtServer(level, source, 3.0F * this.power());
            }
        }

        if (this.age % 30 == 0) {
            level.playSound(null, center.x, center.y, center.z, ModSounds.SINGULARITY_HUM.get(), SoundSource.NEUTRAL, 1.5F, 0.7F + this.random.nextFloat() * 0.2F);
        }
        if (this.age >= this.lifetime()) {
            this.collapse(level, owner, source);
        }
    }

    private void collapse(ServerLevel level, @Nullable Entity owner, DamageSource source) {
        Vec3 center = this.position();
        double radius = this.radius();
        for (LivingEntity target : Combat.targetsAround(level, owner, center, radius * 0.7)) {
            target.hurtServer(level, source, 7.0F * this.power());
            Combat.blast(target, center, 1.6, 0.6);
        }
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFE6D5FF), true, true, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        Fx.sphere(level, ParticleTypes.END_ROD, center, 0.5, 60, 0.6);
        Fx.sphere(level, ModParticles.VOID_MOTE.get(), center, 1.0, 50, 0.4);
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, center, 80, 0.5, 1.2);
        Fx.ring(level, ModParticles.ECLIPSE_FLARE.get(), center, 0.8, 24, 0.7, 0.0);
        level.playSound(null, center.x, center.y, center.z, ModSounds.SINGULARITY_COLLAPSE.get(), SoundSource.NEUTRAL, 3.0F, 1.0F);
        Fx.shake(level, center, 30.0, 0.9F, 12);
        this.discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
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
        this.entityData.set(DATA_LIFETIME, input.getIntOr("lifetime", 100));
        this.entityData.set(DATA_POWER, input.getFloatOr("power", 1.0F));
        this.age = input.getIntOr("age", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("lifetime", this.lifetime());
        output.putFloat("power", this.power());
        output.putInt("age", this.age);
    }
}
