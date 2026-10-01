package com.starforged.tempest.entity;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestEntities;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestSounds;
import com.starforged.util.Combat;
import java.util.HashSet;
import java.util.Set;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown Tempest Javelin. Whatever it impales is struck by lightning, and where it lands it stands as a lightning rod:
 * every second and a half a bolt hits it, shocking enemies around it (up to six times). Recall it and it tears back to
 * your hand straight through anything in the way.
 */
public class TempestJavelinEntity extends Projectile {
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(TempestJavelinEntity.class, EntityDataSerializers.INT);
    public static final int FLYING = 0;
    public static final int STUCK = 1;
    public static final int RETURNING = 2;
    private static final int MAX_ROD_STRIKES = 6;

    private int life;
    private int stuckTicks;
    private int rodStrikes;
    private final Set<Integer> hitOnReturn = new HashSet<>();

    public TempestJavelinEntity(EntityType<? extends TempestJavelinEntity> type, Level level) {
        super(type, level);
    }

    public static TempestJavelinEntity throwFrom(ServerLevel level, Player owner, float power) {
        TempestJavelinEntity javelin = new TempestJavelinEntity(TempestEntities.TEMPEST_JAVELIN.get(), level);
        javelin.setOwner(owner);
        Vec3 eye = owner.getEyePosition();
        javelin.setPos(eye.x, eye.y - 0.1, eye.z);
        javelin.setDeltaMovement(owner.getLookAngle().scale(1.6 + power * 1.6));
        ProjectileUtil.rotateTowardsMovement(javelin, 1.0F);
        level.addFreshEntity(javelin);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), TempestSounds.JAVELIN_THROW.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return javelin;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, FLYING);
    }

    public int state() {
        return this.entityData.get(DATA_STATE);
    }

    public void recall() {
        if (this.state() != RETURNING) {
            this.entityData.set(DATA_STATE, RETURNING);
            this.hitOnReturn.clear();
            this.noPhysics = true;
            if (this.level() instanceof ServerLevel server) {
                server.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.JAVELIN_RECALL.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        if (this.level().isClientSide()) {
            if (this.state() != STUCK || this.random.nextInt(3) == 0) {
                this.level().addParticle(ModParticles.STATIC_SPARK.get(), this.getX(), this.getY() + (this.state() == STUCK ? 0.8 : 0), this.getZ(),
                    0, 0, 0);
            }
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        Entity owner = this.getOwner();
        if (!(owner instanceof Player player) || !player.isAlive() || player.level() != level || player.distanceTo(this) > 96.0) {
            this.discard();
            return;
        }
        switch (this.state()) {
            case FLYING -> this.tickFlying(level);
            case STUCK -> this.tickRod(level, player);
            default -> this.tickReturning(level, player);
        }
    }

    private void tickFlying(ServerLevel level) {
        if (this.life > 100) {
            this.recall();
            return;
        }
        this.setDeltaMovement(this.getDeltaMovement().add(0, -0.03, 0));
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.hitTargetOrDeflectSelf(hit);
        }
        if (this.state() == FLYING) {
            Vec3 m = this.getDeltaMovement();
            this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
            ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        }
    }

    private void tickRod(ServerLevel level, Player owner) {
        this.stuckTicks++;
        if (this.stuckTicks % 30 == 0) {
            TempestFx.strike(level, this.position(), owner, 7.0F, 4.0);
            if (++this.rodStrikes >= MAX_ROD_STRIKES) {
                this.recall();
            }
        }
    }

    private void tickReturning(ServerLevel level, Player owner) {
        Vec3 to = owner.position().add(0, 1.2, 0).subtract(this.position());
        if (to.length() < 1.5) {
            level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0F, 1.2F);
            this.discard();
            return;
        }
        Vec3 step = to.normalize().scale(Math.min(to.length(), 1.8));
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(this.position(), this.position().add(step)).inflate(0.8),
            e -> e != owner && Combat.canHit(level, owner, e))) {
            if (this.hitOnReturn.add(victim.getId())) {
                victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STORM, this, owner), 8.0F);
                level.sendParticles(ModParticles.STATIC_SPARK.get(), victim.getX(), victim.getY() + 1, victim.getZ(), 12, 0.3, 0.5, 0.3, 0.1);
            }
        }
        this.setDeltaMovement(step);
        this.setPos(this.position().add(step));
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity owner = this.getOwner();
        return super.canHitEntity(entity) && entity != owner
            && (!(entity instanceof LivingEntity living && owner != null && this.level() instanceof ServerLevel server) || Combat.canHit(server, owner, living));
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Entity target = hitResult.getEntity();
        Entity owner = this.getOwner();
        target.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.STORM, this, owner), 10.0F);
        TempestFx.strike(server, target.position(), owner, 6.0F, 2.5);
        this.setPos(target.getX(), target.getY(), target.getZ());
        this.stick();
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        Vec3 at = hitResult.getLocation();
        this.setPos(at.x, at.y, at.z);
        this.stick();
        this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 1.0F);
    }

    private void stick() {
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(DATA_STATE, STUCK);
        this.stuckTicks = 0;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }
}
