package com.starforged.sun.entity;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunEntities;
import com.starforged.util.Combat;
import java.util.Comparator;
import java.util.UUID;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A miniature sun summoned by the Helios Scepter. It orbits its summoner and lances nearby enemies with sunbeams.
 */
public class HeliosOrbEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_BEAM_TARGET = SynchedEntityData.defineId(HeliosOrbEntity.class, EntityDataSerializers.INT);
    public static final int LIFETIME = 400;
    private @Nullable UUID ownerId;
    private int age;
    private int beamTicks;

    public HeliosOrbEntity(EntityType<? extends HeliosOrbEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static HeliosOrbEntity summon(ServerLevel level, LivingEntity owner) {
        HeliosOrbEntity orb = new HeliosOrbEntity(SunEntities.HELIOS_ORB.get(), level);
        orb.ownerId = owner.getUUID();
        orb.setPos(owner.getX(), owner.getY() + 3.0, owner.getZ());
        level.addFreshEntity(orb);
        return orb;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BEAM_TARGET, -1);
    }

    public int age() {
        return this.age;
    }

    public @Nullable Entity beamTarget() {
        int id = this.entityData.get(DATA_BEAM_TARGET);
        return id < 0 ? null : this.level().getEntity(id);
    }

    private @Nullable LivingEntity owner(ServerLevel level) {
        return this.ownerId == null ? null : level.getEntity(this.ownerId) instanceof LivingEntity living ? living : null;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        if (this.level() instanceof ServerLevel server) {
            LivingEntity owner = this.owner(server);
            if (owner == null || !owner.isAlive() || this.age > LIFETIME) {
                server.sendParticles(ModParticles.SOLAR_SPARK.get(), this.getX(), this.getY(), this.getZ(), 40, 0.4, 0.4, 0.4, 0.2);
                server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.2F);
                this.discard();
                return;
            }
            double angle = this.age * 0.08;
            Vec3 wanted = owner.position().add(Math.cos(angle) * 1.6, owner.getBbHeight() + 1.2 + Math.sin(this.age * 0.15) * 0.2, Math.sin(angle) * 1.6);
            Vec3 pos = this.position().lerp(wanted, 0.25);
            this.setPos(pos.x, pos.y, pos.z);

            if (this.beamTicks > 0) {
                this.beamTicks--;
                if (this.beamTicks == 0) {
                    this.entityData.set(DATA_BEAM_TARGET, -1);
                }
            }
            if (this.age % 14 == 0) {
                LivingEntity target = server.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(14.0),
                        e -> e != owner && e.isAlive() && (e instanceof Enemy || e == owner.getLastHurtByMob() || e == owner.getLastHurtMob())
                            && Combat.canHit(server, owner, e) && e.hasLineOfSight(this))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
                if (target != null) {
                    DamageSource source = ModDamageTypes.source(server, ModDamageTypes.SOLAR_BEAM, this, owner);
                    target.hurtServer(server, source, 6.0F);
                    target.igniteForSeconds(3.0F);
                    this.entityData.set(DATA_BEAM_TARGET, target.getId());
                    this.beamTicks = 6;
                    server.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 10, 0.2, 0.3, 0.2, 0.04);
                    server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 1.8F);
                }
            }
        } else if (this.random.nextInt(2) == 0) {
            this.level().addParticle(ModParticles.SOLAR_SPARK.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.6, this.getY(),
                this.getZ() + (this.random.nextDouble() - 0.5) * 0.6, 0.0, -0.02, 0.0);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.age = input.getIntOr("age", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("age", this.age);
    }
}
