package com.starforged.moon.entity;

import com.starforged.moon.MoonEntities;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A little moon.
 * <ul>
 *     <li>{@link Kind#ORB} - an Orrery Staff moon: drifts toward its target and bursts.</li>
 *     <li>{@link Kind#VOLLEY} - the Pale Matriarch's moonlets.</li>
 *     <li>{@link Kind#CRESCENT} - a fast crescent blade of moonlight (Matriarch and her echoes).</li>
 *     <li>{@link Kind#SHARD} - a selenite shard flung by a Selenite Sentinel.</li>
 *     <li>{@link Kind#MOONFALL} - the Orrery Staff's merged moon: falls from the sky; bigger the more moons went into it.</li>
 * </ul>
 */
public class MoonletEntity extends Projectile {
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(MoonletEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(MoonletEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(MoonletEntity.class, EntityDataSerializers.FLOAT);

    public enum Kind {
        ORB(2.2), VOLLEY(1.6), CRESCENT(0.0), SHARD(0.0), MOONFALL(4.0);

        public final double blastRadius;

        Kind(double blastRadius) {
            this.blastRadius = blastRadius;
        }

        static Kind byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : ORB;
        }
    }

    private float damage = 6.0F;
    private int life;

    public MoonletEntity(EntityType<? extends MoonletEntity> type, Level level) {
        super(type, level);
    }

    public static MoonletEntity shoot(ServerLevel level, @Nullable LivingEntity owner, Vec3 from, Vec3 direction, float speed, float damage,
                                      Kind kind, @Nullable LivingEntity target) {
        MoonletEntity moon = new MoonletEntity(MoonEntities.MOONLET.get(), level);
        moon.setOwner(owner);
        moon.setPos(from.x, from.y, from.z);
        moon.damage = damage;
        moon.entityData.set(DATA_KIND, kind.ordinal());
        moon.entityData.set(DATA_TARGET, target == null ? -1 : target.getId());
        moon.entityData.set(DATA_SIZE, switch (kind) {
            case ORB -> 0.7F;
            case VOLLEY -> 0.9F;
            case CRESCENT -> 1.3F;
            case SHARD -> 0.5F;
            case MOONFALL -> 3.0F;
        });
        moon.setDeltaMovement(direction.normalize().scale(speed));
        ProjectileUtil.rotateTowardsMovement(moon, 1.0F);
        level.addFreshEntity(moon);
        return moon;
    }

    public MoonletEntity withSize(float size) {
        this.entityData.set(DATA_SIZE, size);
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_KIND, 0);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_SIZE, 0.7F);
    }

    public Kind kind() {
        return Kind.byId(this.entityData.get(DATA_KIND));
    }

    public float size() {
        return this.entityData.get(DATA_SIZE);
    }

    public int life() {
        return this.life;
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        Kind kind = this.kind();
        if (!this.level().isClientSide() && this.life > (kind == Kind.MOONFALL ? 240 : 120)) {
            this.discard();
            return;
        }
        if (kind == Kind.MOONFALL) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
        } else if (kind == Kind.ORB || kind == Kind.VOLLEY) {
            this.steer(kind == Kind.ORB ? 0.22 : 0.08);
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, hit)) {
            this.hitTargetOrDeflectSelf(hit);
        }
        if (this.isRemoved()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        if (this.level().isClientSide() && (this.tickCount > 2 || kind == Kind.MOONFALL)) {
            int count = kind == Kind.MOONFALL ? 6 : 2;
            float s = this.size();
            for (int i = 0; i < count; i++) {
                double f = i / (double) count;
                this.level().addParticle(i % 2 == 0 ? ModParticles.LUNAR_GLIMMER.get() : ModParticles.MOON_DUST.get(),
                    this.getX() - motion.x * f + (this.random.nextDouble() - 0.5) * s * 0.6,
                    this.getY() - motion.y * f + (this.random.nextDouble() - 0.5) * s * 0.6,
                    this.getZ() - motion.z * f + (this.random.nextDouble() - 0.5) * s * 0.6, 0.0, 0.0, 0.0);
            }
        }
    }

    private void steer(double strength) {
        int targetId = this.entityData.get(DATA_TARGET);
        if (targetId < 0 || this.life < 4) {
            return;
        }
        Entity target = this.level().getEntity(targetId);
        if (target == null || !target.isAlive()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        double speed = Math.max(0.5, motion.length());
        Vec3 wanted = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(this.position()).normalize();
        this.setDeltaMovement(motion.normalize().scale(1.0 - strength).add(wanted.scale(strength)).normalize().scale(speed));
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || entity instanceof MoonletEntity) {
            return false;
        }
        Entity owner = this.getOwner();
        if (owner != null && this.level() instanceof ServerLevel server && entity instanceof LivingEntity living) {
            return Combat.canHit(server, owner, living);
        }
        return true;
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level() instanceof ServerLevel server) {
            hitResult.getEntity().hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.MOONLIGHT, this, this.getOwner()), this.damage);
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 at = hitResult.getLocation();
        Kind kind = this.kind();
        double radius = kind == Kind.MOONFALL ? this.size() * 1.4 : kind.blastRadius;
        if (radius > 0) {
            Entity owner = this.getOwner();
            for (LivingEntity victim : Combat.targetsAround(server, owner, at, radius)) {
                if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() == victim) {
                    continue;
                }
                victim.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.MOONLIGHT, this, owner), this.damage * 0.7F);
                Combat.blast(victim, at, 0.7, 0.5);
            }
            Fx.sphere(server, ModParticles.LUNAR_GLIMMER.get(), at, 0.3, (int) (30 * radius), 0.35);
            Fx.burst(server, ModParticles.MOON_DUST.get(), at, (int) (12 * radius), radius * 0.3, 0.05);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.5F, 0.6F);
            if (kind == Kind.MOONFALL) {
                server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
                Fx.ring(server, ModParticles.MOON_DUST.get(), at.add(0, 0.2, 0), 1.0, 60, 0.6, 0.02);
                Fx.shake(server, at, 24.0, 0.9F, 14);
            }
        } else {
            server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.08);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 0.8F, 1.4F);
        }
        this.discard();
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putFloat("damage", this.damage);
        output.putInt("kind", this.entityData.get(DATA_KIND));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.damage = input.getFloatOr("damage", 6.0F);
        this.entityData.set(DATA_KIND, input.getIntOr("kind", 0));
    }
}
