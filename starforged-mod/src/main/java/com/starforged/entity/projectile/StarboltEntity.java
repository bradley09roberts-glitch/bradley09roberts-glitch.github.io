package com.starforged.entity.projectile;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModEntities;
import com.starforged.registry.ModItems;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModTags;
import com.starforged.util.Combat;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A bolt of living starlight. Used by the Constellation Bow (homing), Starlings, Astral Wraiths (void variant)
 * and the Astral Golem (crystal variant).
 */
public class StarboltEntity extends Projectile implements ItemSupplier {
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(StarboltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(StarboltEntity.class, EntityDataSerializers.INT);

    public enum Variant {
        STAR, VOID, CRYSTAL;

        static Variant byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : STAR;
        }
    }

    private float damage = 5.0F;
    private float speed = 1.5F;
    private int life;
    private @Nullable ItemStack cachedItem;
    private int cachedVariant = -1;

    public StarboltEntity(EntityType<? extends StarboltEntity> type, Level level) {
        super(type, level);
    }

    public static StarboltEntity shoot(ServerLevel level, @Nullable LivingEntity owner, Vec3 from, Vec3 direction, float speed, float damage,
                                       Variant variant, @Nullable LivingEntity target) {
        StarboltEntity bolt = new StarboltEntity(ModEntities.STARBOLT.get(), level);
        bolt.setOwner(owner);
        bolt.setPos(from.x, from.y, from.z);
        bolt.damage = damage;
        bolt.speed = speed;
        bolt.entityData.set(DATA_VARIANT, variant.ordinal());
        bolt.entityData.set(DATA_TARGET, target == null ? -1 : target.getId());
        bolt.setDeltaMovement(direction.normalize().scale(speed));
        ProjectileUtil.rotateTowardsMovement(bolt, 1.0F);
        level.addFreshEntity(bolt);
        return bolt;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_TARGET, -1);
    }

    public Variant variant() {
        return Variant.byId(this.entityData.get(DATA_VARIANT));
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        if (!this.level().isClientSide() && this.life > 120) {
            this.discard();
            return;
        }

        this.steer();

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, hit)) {
            this.hitTargetOrDeflectSelf(hit);
        }
        if (this.isRemoved()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);

        if (this.level().isClientSide()) {
            ParticleOptions trail = switch (this.variant()) {
                case STAR -> ModParticles.STAR_SPARKLE.get();
                case VOID -> ModParticles.VOID_MOTE.get();
                case CRYSTAL -> ModParticles.ASTRAL_GLINT.get();
            };
            for (int i = 0; i < 2; i++) {
                double f = i / 2.0;
                this.level().addParticle(trail, this.getX() - motion.x * f, this.getY() + 0.15 - motion.y * f, this.getZ() - motion.z * f,
                    (this.random.nextDouble() - 0.5) * 0.02, (this.random.nextDouble() - 0.5) * 0.02, (this.random.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    /** Gently bends the flight path toward the homing target. */
    private void steer() {
        int targetId = this.entityData.get(DATA_TARGET);
        if (targetId < 0 || this.life < 3) {
            return;
        }
        Entity target = this.level().getEntity(targetId);
        if (target == null || !target.isAlive()) {
            if (!this.level().isClientSide()) {
                this.entityData.set(DATA_TARGET, -1);
            }
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        double currentSpeed = Math.max(0.6, motion.length());
        Vec3 wanted = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(this.position()).normalize();
        Vec3 steered = motion.normalize().scale(0.82).add(wanted.scale(0.18)).normalize().scale(currentSpeed);
        this.setDeltaMovement(steered);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity)) {
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
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Entity target = hitResult.getEntity();
        Entity owner = this.getOwner();
        float amount = this.damage;
        if (this.variant() == Variant.STAR && target.getType().is(ModTags.VOIDBORN)) {
            amount *= 1.5F;
        }
        if (target.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.STARLIGHT, this, owner), amount) && target instanceof LivingEntity living) {
            if (this.variant() == Variant.VOID) {
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), owner);
            } else if (this.variant() == Variant.CRYSTAL) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), owner);
            }
            Vec3 push = this.getDeltaMovement().multiply(1, 0, 1).normalize();
            living.knockback(0.25, -push.x, -push.z);
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel server) {
            ParticleOptions burst = switch (this.variant()) {
                case STAR -> ModParticles.STAR_SPARKLE.get();
                case VOID -> ModParticles.VOID_MOTE.get();
                case CRYSTAL -> ModParticles.ASTRAL_GLINT.get();
            };
            server.sendParticles(burst, this.getX(), this.getY(), this.getZ(), 14, 0.15, 0.15, 0.15, 0.12);
            if (this.variant() == Variant.CRYSTAL) {
                server.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
            }
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
    }

    @Override
    public ItemStack getItem() {
        int variant = this.entityData.get(DATA_VARIANT);
        if (this.cachedItem == null || this.cachedVariant != variant) {
            this.cachedVariant = variant;
            this.cachedItem = new ItemStack(switch (Variant.byId(variant)) {
                case STAR -> ModItems.STARBOLT.get();
                case VOID -> ModItems.VOID_BOLT.get();
                case CRYSTAL -> ModItems.CRYSTAL_SHARD_BOLT.get();
            });
        }
        return this.cachedItem;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putFloat("damage", this.damage);
        output.putFloat("speed", this.speed);
        output.putInt("variant", this.entityData.get(DATA_VARIANT));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.damage = input.getFloatOr("damage", 5.0F);
        this.speed = input.getFloatOr("speed", 1.5F);
        this.entityData.set(DATA_VARIANT, input.getIntOr("variant", 0));
    }
}
