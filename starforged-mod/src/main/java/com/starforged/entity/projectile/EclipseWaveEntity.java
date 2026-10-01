package com.starforged.entity.projectile;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModEntities;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModTags;
import com.starforged.util.Combat;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A crescent of eclipse-light flung by the Eclipse Blade. It pierces every enemy in its path.
 */
public class EclipseWaveEntity extends Projectile {
    public static final int LIFETIME = 30;
    private final Set<Integer> alreadyHit = new HashSet<>();
    private float damage = 12.0F;
    private int life;

    public EclipseWaveEntity(EntityType<? extends EclipseWaveEntity> type, Level level) {
        super(type, level);
    }

    public static EclipseWaveEntity fire(ServerLevel level, LivingEntity owner, float damage) {
        EclipseWaveEntity wave = new EclipseWaveEntity(ModEntities.ECLIPSE_WAVE.get(), level);
        Vec3 look = owner.getViewVector(1.0F);
        Vec3 start = owner.getEyePosition().add(look.scale(1.2)).add(0, -0.45, 0);
        wave.setOwner(owner);
        wave.damage = damage;
        wave.setPos(start.x, start.y, start.z);
        wave.setYRot(owner.getYRot());
        wave.setXRot(owner.getXRot());
        wave.setDeltaMovement(look.scale(1.3));
        level.addFreshEntity(wave);
        return wave;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public int life() {
        return this.life;
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        Vec3 pos = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 next = pos.add(motion);

        if (this.level() instanceof ServerLevel server) {
            if (this.life > LIFETIME) {
                this.discard();
                return;
            }
            Entity owner = this.getOwner();
            AABB sweep = this.getBoundingBox().expandTowards(motion).inflate(1.0, 0.4, 1.0);
            for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, sweep, e -> e.isAlive() && !this.alreadyHit.contains(e.getId()))) {
                if (owner != null && !Combat.canHit(server, owner, target)) {
                    continue;
                }
                this.alreadyHit.add(target.getId());
                float amount = target.is(ModTags.VOIDBORN) ? this.damage * 1.5F : this.damage;
                if (target.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.ECLIPSE_BEAM, this, owner), amount)) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0), owner);
                    Vec3 push = motion.multiply(1, 0, 1).normalize();
                    com.starforged.util.Combat.knock(target, 0.6, push);
                }
                server.sendParticles(ModParticles.ECLIPSE_FLARE.get(), target.getX(), target.getY(0.5), target.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
            }
            BlockHitResult blockHit = server.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (blockHit.getType() != HitResult.Type.MISS) {
                Vec3 at = blockHit.getLocation();
                server.sendParticles(ModParticles.ECLIPSE_FLARE.get(), at.x, at.y, at.z, 6, 0.4, 0.4, 0.4, 0.05);
                server.sendParticles(ModParticles.VOID_MOTE.get(), at.x, at.y, at.z, 20, 0.5, 0.5, 0.5, 0.1);
                this.discard();
                return;
            }
        }
        this.setPos(next.x, next.y, next.z);

        if (this.level().isClientSide()) {
            float yaw = this.getYRot() * ((float) Math.PI / 180F);
            Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            for (int i = 0; i < 4; i++) {
                double side = (this.random.nextDouble() - 0.5) * 3.2;
                double back = side * side * 0.18;
                Vec3 p = this.position().add(right.scale(side)).subtract(motion.normalize().scale(back));
                this.level().addParticle(i == 0 ? ModParticles.STAR_SPARKLE.get() : ModParticles.VOID_MOTE.get(), p.x, p.y + 0.2, p.z, 0.0, 0.0, 0.0);
            }
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.2, this.getZ(), motion.x * 0.1, 0.0, motion.z * 0.1);
            }
        }
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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.damage = input.getFloatOr("damage", 12.0F);
    }
}
