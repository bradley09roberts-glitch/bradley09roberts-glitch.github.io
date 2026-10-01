package com.starforged.tempest.entity;

import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestEntities;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestSounds;
import com.starforged.util.Combat;
import java.util.Comparator;
import java.util.List;
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
 * The Stormhook's chain. Hook a surface and it zips you to it. Hook a creature and it is hauled to you (something huge
 * hauls you to it instead) - and while the chain is on it, <b>Live Wire</b> runs lightning down the line and arcs it on
 * to up to three more enemies nearby. Use the hook again (or sneak) to let go.
 */
public class StormhookEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> DATA_ANCHORED = SynchedEntityData.defineId(StormhookEntity.class, EntityDataSerializers.BOOLEAN);
    private int life;
    private int anchoredTicks;
    private @Nullable Entity hooked;
    private boolean pullPlayer;

    public StormhookEntity(EntityType<? extends StormhookEntity> type, Level level) {
        super(type, level);
    }

    public static StormhookEntity fire(ServerLevel level, Player owner) {
        StormhookEntity hook = new StormhookEntity(TempestEntities.STORMHOOK.get(), level);
        hook.setOwner(owner);
        Vec3 eye = owner.getEyePosition();
        hook.setPos(eye.x, eye.y - 0.1, eye.z);
        hook.setDeltaMovement(owner.getLookAngle().scale(2.8));
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
        if (this.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        Entity owner = this.getOwner();
        if (!(owner instanceof Player player) || !player.isAlive() || player.level() != level || player.distanceTo(this) > 48.0) {
            this.discard();
            return;
        }
        if (!this.isAnchored()) {
            if (this.life > 16) {
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
        if (player.isShiftKeyDown() || this.anchoredTicks > 80) {
            this.discard();
            return;
        }
        if (this.hooked != null) {
            if (!this.hooked.isAlive()) {
                this.discard();
                return;
            }
            this.setPos(this.hooked.getX(), this.hooked.getY() + this.hooked.getBbHeight() * 0.5, this.hooked.getZ());
            if (this.anchoredTicks % 10 == 1 && this.hooked instanceof LivingEntity living) {
                this.liveWire(level, player, living);
            }
            if (!this.pullPlayer) {
                Vec3 to = player.position().subtract(this.hooked.position());
                if (to.length() < 2.2) {
                    this.discard();
                    return;
                }
                this.hooked.setDeltaMovement(to.normalize().scale(1.0).add(0, 0.12, 0));
                this.hooked.hurtMarked = true;
                return;
            }
        }
        Vec3 d = this.position().subtract(player.position().add(0, 1.0, 0));
        if (d.length() < 2.0) {
            this.discard();
            return;
        }
        player.setDeltaMovement(d.normalize().scale(Math.min(1.8, 0.8 + this.anchoredTicks * 0.06)));
        player.resetFallDistance();
        player.hurtMarked = true;
    }

    private void liveWire(ServerLevel level, Player owner, LivingEntity first) {
        TempestFx.zap(level, owner.position().add(0, 1.2, 0), first, owner, 3.0F);
        LivingEntity from = first;
        List<LivingEntity> near = Combat.targetsAround(level, owner, first.position(), 6.0);
        near.remove(first);
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(first)));
        for (int i = 0; i < Math.min(3, near.size()); i++) {
            LivingEntity next = near.get(i);
            TempestFx.zap(level, from.position().add(0, from.getBbHeight() * 0.5, 0), next, owner, 4.0F);
            from = next;
        }
        level.playSound(null, first.getX(), first.getY(), first.getZ(), TempestSounds.LIVE_WIRE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity != this.getOwner() && entity instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        Entity target = hitResult.getEntity();
        this.hooked = target;
        this.pullPlayer = target.getBbWidth() > 1.8 || (target instanceof LivingEntity living && living.getMaxHealth() > 120.0F);
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
        if (this.level() instanceof ServerLevel server) {
            server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
            server.sendParticles(ModParticles.STATIC_SPARK.get(), this.getX(), this.getY(), this.getZ(), 12, 0.1, 0.1, 0.1, 0.1);
        }
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
