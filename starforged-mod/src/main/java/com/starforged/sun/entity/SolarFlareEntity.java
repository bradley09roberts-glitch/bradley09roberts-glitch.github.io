package com.starforged.sun.entity;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunEntities;
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
 * A ball of living sunfire.
 * <ul>
 *     <li>{@link Kind#SPARK} - Cinder Imp shots: small, sets the target alight.</li>
 *     <li>{@link Kind#FLARE} - the Flare Greatsword's thrown flare: bursts into a fiery explosion (no block damage).</li>
 *     <li>{@link Kind#PHOENIX} - Phoenix Bow arrows: home in on their target and burst.</li>
 *     <li>{@link Kind#SUNFALL} - the Sun Warden's barrage: falls from the sky and explodes on the ground.</li>
 * </ul>
 */
public class SolarFlareEntity extends Projectile {
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(SolarFlareEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(SolarFlareEntity.class, EntityDataSerializers.INT);

    public enum Kind {
        SPARK(0.45F, 0.0), FLARE(0.9F, 2.8), PHOENIX(0.7F, 2.4), SUNFALL(1.6F, 3.6);

        public final float size;
        public final double blastRadius;

        Kind(float size, double blastRadius) {
            this.size = size;
            this.blastRadius = blastRadius;
        }

        static Kind byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : SPARK;
        }
    }

    private float damage = 4.0F;
    private int life;

    public SolarFlareEntity(EntityType<? extends SolarFlareEntity> type, Level level) {
        super(type, level);
    }

    public static SolarFlareEntity shoot(ServerLevel level, @Nullable LivingEntity owner, Vec3 from, Vec3 direction, float speed, float damage,
                                         Kind kind, @Nullable LivingEntity target) {
        SolarFlareEntity flare = new SolarFlareEntity(SunEntities.SOLAR_FLARE.get(), level);
        flare.setOwner(owner);
        flare.setPos(from.x, from.y, from.z);
        flare.damage = damage;
        flare.entityData.set(DATA_KIND, kind.ordinal());
        flare.entityData.set(DATA_TARGET, target == null ? -1 : target.getId());
        flare.setDeltaMovement(direction.normalize().scale(speed));
        ProjectileUtil.rotateTowardsMovement(flare, 1.0F);
        level.addFreshEntity(flare);
        return flare;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_KIND, 0);
        builder.define(DATA_TARGET, -1);
    }

    public Kind kind() {
        return Kind.byId(this.entityData.get(DATA_KIND));
    }

    public int life() {
        return this.life;
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        if (!this.level().isClientSide() && this.life > (this.kind() == Kind.SUNFALL ? 200 : 100)) {
            this.discard();
            return;
        }
        if (this.kind() == Kind.SUNFALL) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.06, 0));
        } else if (this.kind() == Kind.PHOENIX) {
            this.steer();
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
        if (this.level().isClientSide()) {
            int count = this.kind() == Kind.SPARK ? 1 : 3;
            for (int i = 0; i < count; i++) {
                double f = i / (double) count;
                this.level().addParticle(i % 2 == 0 ? ParticleTypes.FLAME : ModParticles.SOLAR_SPARK.get(),
                    this.getX() - motion.x * f + (this.random.nextDouble() - 0.5) * this.kind().size * 0.5,
                    this.getY() - motion.y * f + (this.random.nextDouble() - 0.5) * this.kind().size * 0.5,
                    this.getZ() - motion.z * f + (this.random.nextDouble() - 0.5) * this.kind().size * 0.5, 0.0, 0.01, 0.0);
            }
            if (this.kind() == Kind.SUNFALL) {
                this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.05, 0.0);
            }
        }
    }

    private void steer() {
        int targetId = this.entityData.get(DATA_TARGET);
        if (targetId < 0 || this.life < 3) {
            return;
        }
        Entity target = this.level().getEntity(targetId);
        if (target == null || !target.isAlive()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        double speed = Math.max(0.8, motion.length());
        Vec3 wanted = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(this.position()).normalize();
        this.setDeltaMovement(motion.normalize().scale(0.8).add(wanted.scale(0.2)).normalize().scale(speed));
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || entity instanceof SolarFlareEntity) {
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
            Entity target = hitResult.getEntity();
            if (target.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.SUNFIRE, this, this.getOwner()), this.damage)) {
                target.igniteForSeconds(4.0F);
            }
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
        if (kind.blastRadius > 0) {
            Entity owner = this.getOwner();
            for (LivingEntity victim : Combat.targetsAround(server, owner, at, kind.blastRadius)) {
                if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() == victim) {
                    continue;
                }
                victim.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.SUNFIRE, this, owner), this.damage * 0.6F);
                victim.igniteForSeconds(4.0F);
                Combat.blast(victim, at, 0.6, 0.3);
            }
            Fx.sphere(server, ModParticles.SOLAR_SPARK.get(), at, 0.3, (int) (25 * kind.blastRadius), 0.35);
            Fx.burst(server, ParticleTypes.FLAME, at, (int) (12 * kind.blastRadius), kind.blastRadius * 0.3, 0.08);
            server.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, kind == Kind.SUNFALL ? 1.6F : 0.9F,
                1.3F + this.random.nextFloat() * 0.2F);
            if (kind == Kind.SUNFALL) {
                Fx.ring(server, ParticleTypes.FLAME, at.add(0, 0.2, 0), 1.0, 30, 0.35, 0.02);
                Fx.shake(server, at, 18.0, 0.5F, 10);
            }
        } else {
            server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.05);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.5F, 1.5F);
        }
        this.discard();
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isOnFire() {
        return false;
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
        this.damage = input.getFloatOr("damage", 4.0F);
        this.entityData.set(DATA_KIND, input.getIntOr("kind", 0));
    }
}
