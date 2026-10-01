package com.starforged.sun.entity;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunEntities;
import com.starforged.util.Combat;
import java.util.HashSet;
import java.util.Set;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown Cinder Chakram: a spinning ring of fire that slices through every enemy in its path,
 * ricochets off walls, then flies back to its owner's hand.
 */
public class CinderChakramEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> DATA_RETURNING = SynchedEntityData.defineId(CinderChakramEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int OUTBOUND_TICKS = 18;
    private final Set<Integer> hitOutbound = new HashSet<>();
    private final Set<Integer> hitReturning = new HashSet<>();
    private float damage = 9.0F;
    private int life;

    public CinderChakramEntity(EntityType<? extends CinderChakramEntity> type, Level level) {
        super(type, level);
    }

    public static CinderChakramEntity toss(ServerLevel level, LivingEntity owner, float damage) {
        CinderChakramEntity chakram = new CinderChakramEntity(SunEntities.CINDER_CHAKRAM.get(), level);
        chakram.setOwner(owner);
        Vec3 eye = owner.getEyePosition().add(0, -0.2, 0);
        chakram.setPos(eye.x, eye.y, eye.z);
        chakram.damage = damage;
        chakram.setDeltaMovement(owner.getLookAngle().scale(1.6));
        level.addFreshEntity(chakram);
        return chakram;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RETURNING, false);
    }

    public boolean returning() {
        return this.entityData.get(DATA_RETURNING);
    }

    public int life() {
        return this.life;
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        Entity owner = this.getOwner();
        if (!this.level().isClientSide()) {
            if (owner == null || !owner.isAlive() || this.life > 120) {
                this.discard();
                return;
            }
            if (!this.returning() && this.life > OUTBOUND_TICKS) {
                this.entityData.set(DATA_RETURNING, true);
            }
        }
        Vec3 motion = this.getDeltaMovement();
        if (this.returning() && owner != null) {
            Vec3 home = owner.getEyePosition().add(0, -0.4, 0);
            Vec3 toOwner = home.subtract(this.position());
            if (toOwner.lengthSqr() < 1.6 && !this.level().isClientSide()) {
                this.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.6F);
                if (owner instanceof Player player) {
                    player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
                }
                this.discard();
                return;
            }
            motion = motion.scale(0.6).add(toOwner.normalize().scale(0.9));
            if (motion.length() > 1.8) {
                motion = motion.normalize().scale(1.8);
            }
        } else if (!this.level().isClientSide()) {
            // Ricochet off walls on the way out.
            HitResult block = this.level().clip(new ClipContext(this.position(), this.position().add(motion), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
            if (block.getType() == HitResult.Type.BLOCK) {
                this.entityData.set(DATA_RETURNING, true);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 2.0F);
                motion = motion.scale(-0.5);
            }
        }
        this.setDeltaMovement(motion);
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);

        if (this.level() instanceof ServerLevel server) {
            Set<Integer> hit = this.returning() ? this.hitReturning : this.hitOutbound;
            for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.6),
                e -> e != owner && e.isAlive() && Combat.canHit(server, owner, e))) {
                if (hit.add(victim.getId())) {
                    victim.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.SUNFIRE, this, owner), this.damage);
                    victim.igniteForSeconds(5.0F);
                    server.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 10, 0.2, 0.3, 0.2, 0.05);
                    server.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.4F);
                }
            }
        } else {
            this.level().addParticle(ModParticles.SOLAR_SPARK.get(), this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            this.level().addParticle(ParticleTypes.FLAME, this.getX() + (this.random.nextDouble() - 0.5) * 0.6, this.getY(),
                this.getZ() + (this.random.nextDouble() - 0.5) * 0.6, 0.0, 0.0, 0.0);
        }
        if (this.life % 4 == 0) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.4F, 2.0F);
        }
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putFloat("damage", this.damage);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.damage = input.getFloatOr("damage", 9.0F);
    }
}
