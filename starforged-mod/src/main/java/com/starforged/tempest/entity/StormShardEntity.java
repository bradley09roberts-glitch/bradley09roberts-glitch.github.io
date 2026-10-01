package com.starforged.tempest.entity;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestEntities;
import com.starforged.util.Combat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.SynchedEntityData;
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

/** A shard of charged crystal, flung by Shardwings (Crystal Volley) and the Thunderjaw Alpha. */
public class StormShardEntity extends Projectile {
    private float damage = 4.0F;
    private int life;

    public StormShardEntity(EntityType<? extends StormShardEntity> type, Level level) {
        super(type, level);
    }

    public static void shoot(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 direction, float speed, float damage) {
        StormShardEntity shard = new StormShardEntity(TempestEntities.STORM_SHARD.get(), level);
        shard.setOwner(owner);
        shard.setPos(from.x, from.y, from.z);
        shard.damage = damage;
        shard.setDeltaMovement(direction.normalize().scale(speed));
        ProjectileUtil.rotateTowardsMovement(shard, 1.0F);
        level.addFreshEntity(shard);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && ++this.life > 80) {
            this.discard();
            return;
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
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        if (this.level().isClientSide() && this.tickCount > 1) {
            this.level().addParticle(ModParticles.STATIC_SPARK.get(), this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || entity instanceof StormShardEntity) {
            return false;
        }
        Entity owner = this.getOwner();
        return !(owner != null && this.level() instanceof ServerLevel server && entity instanceof LivingEntity living) || Combat.canHit(server, owner, living);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level() instanceof ServerLevel server) {
            hitResult.getEntity().hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.STORM, this, this.getOwner()), this.damage);
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel server) {
            Vec3 at = hitResult.getLocation();
            server.sendParticles(ModParticles.STATIC_SPARK.get(), at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.1);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 0.7F, 1.6F);
        }
        this.discard();
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }
}
