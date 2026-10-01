package com.starforged.moon.entity;

import com.starforged.moon.MoonEntities;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.world.MoonGravity;
import com.starforged.registry.ModParticles;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Tether Hook. Bite into a surface and it reels you in - in low gravity you swing around the anchor on the line
 * instead. Hook a small creature and it is dragged to you; hook something huge and you are dragged to it.
 * Use the hook again (or sneak) to let go.
 */
public class TetherHookEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> DATA_ANCHORED = SynchedEntityData.defineId(TetherHookEntity.class, EntityDataSerializers.BOOLEAN);
    private int life;
    private int anchoredTicks;
    private double ropeLength;
    private @Nullable Entity hooked;
    private boolean pullPlayer;

    public TetherHookEntity(EntityType<? extends TetherHookEntity> type, Level level) {
        super(type, level);
    }

    public static TetherHookEntity fire(ServerLevel level, Player owner) {
        TetherHookEntity hook = new TetherHookEntity(MoonEntities.TETHER_HOOK.get(), level);
        hook.setOwner(owner);
        Vec3 eye = owner.getEyePosition();
        hook.setPos(eye.x, eye.y - 0.1, eye.z);
        hook.setDeltaMovement(owner.getLookAngle().scale(2.4));
        level.addFreshEntity(hook);
        return hook;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ANCHORED, false);
    }

    public boolean isAnchored() {
        return this.entityData.get(DATA_ANCHORED);
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        Entity owner = this.getOwner();
        if (this.level().isClientSide()) {
            return;
        }
        if (!(owner instanceof Player player) || !player.isAlive() || player.level() != this.level() || player.distanceTo(this) > 48.0) {
            this.discard();
            return;
        }
        if (!this.isAnchored()) {
            if (this.life > 18) {
                this.discard();
                return;
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                this.hitTargetOrDeflectSelf(hit);
            }
            if (!this.isAnchored()) {
                Vec3 motion = this.getDeltaMovement();
                this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
            }
            return;
        }
        this.anchoredTicks++;
        if (this.hooked != null) {
            if (!this.hooked.isAlive()) {
                this.discard();
                return;
            }
            this.setPos(this.hooked.getX(), this.hooked.getY() + this.hooked.getBbHeight() * 0.5, this.hooked.getZ());
        }
        if (player.isShiftKeyDown() || this.anchoredTicks > 120) {
            this.discard();
            return;
        }
        if (this.hooked != null && !this.pullPlayer) {
            Vec3 to = player.position().subtract(this.hooked.position());
            if (to.length() < 2.0) {
                this.discard();
                return;
            }
            this.hooked.setDeltaMovement(to.normalize().scale(0.9).add(0, 0.1, 0));
            this.hooked.hurtMarked = true;
            return;
        }
        Vec3 anchor = this.position();
        Vec3 body = player.position().add(0, 1.0, 0);
        Vec3 d = body.subtract(anchor);
        double dist = d.length();
        boolean swing = MoonGravity.worldFactor((ServerLevel) this.level()) < 1.0 && this.hooked == null;
        player.resetFallDistance();
        if (swing) {
            // Pendulum: the line cannot stretch; it slowly reels in.
            this.ropeLength = Math.max(3.0, this.ropeLength - 0.08);
            Vec3 v = player.getDeltaMovement();
            if (dist > this.ropeLength) {
                Vec3 n = d.scale(1.0 / dist);
                double radial = v.dot(n);
                if (radial > 0) {
                    v = v.subtract(n.scale(radial));
                }
                v = v.subtract(n.scale((dist - this.ropeLength) * 0.35));
            }
            player.setDeltaMovement(v);
        } else {
            if (dist < 2.2) {
                this.discard();
                return;
            }
            Vec3 pull = d.scale(-1.0 / dist).scale(Math.min(1.5, 0.6 + this.anchoredTicks * 0.05));
            player.setDeltaMovement(pull);
        }
        player.hurtMarked = true;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity != this.getOwner() && entity instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        Entity target = hitResult.getEntity();
        this.hooked = target;
        boolean huge = target.getBbWidth() > 1.8 || (target instanceof LivingEntity living && living.getMaxHealth() > 120.0F);
        this.pullPlayer = huge;
        this.anchor();
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        Vec3 at = hitResult.getLocation();
        this.setPos(at.x, at.y, at.z);
        this.anchor();
    }

    private void anchor() {
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(DATA_ANCHORED, true);
        Entity owner = this.getOwner();
        this.ropeLength = owner == null ? 6.0 : Math.max(3.0, owner.position().add(0, 1.0, 0).distanceTo(this.position()));
        if (this.level() instanceof ServerLevel server) {
            server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
            server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), this.getX(), this.getY(), this.getZ(), 10, 0.1, 0.1, 0.1, 0.1);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.level() instanceof ServerLevel server && this.isAnchored()) {
            server.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.HOOK_FIRE.get(), SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        super.remove(reason);
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

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
