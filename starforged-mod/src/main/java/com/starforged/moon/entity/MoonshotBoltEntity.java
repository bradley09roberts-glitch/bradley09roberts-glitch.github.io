package com.starforged.moon.entity;

import com.starforged.moon.MoonEntities;
import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.util.Combat;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Moonshot Crossbow bolt of hard moonlight. Normal bolts ricochet off surfaces and enemies up to five times. A
 * Gravity Beacon bolt sticks where it lands and drags everything nearby toward it for four seconds.
 */
public class MoonshotBoltEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> DATA_BEACON = SynchedEntityData.defineId(MoonshotBoltEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_STUCK = SynchedEntityData.defineId(MoonshotBoltEntity.class, EntityDataSerializers.BOOLEAN);
    private float damage = 7.0F;
    private int bounces;
    private int life;
    private int stuckTicks;
    private int lastHit = -1;

    public MoonshotBoltEntity(EntityType<? extends MoonshotBoltEntity> type, Level level) {
        super(type, level);
    }

    public static void fire(ServerLevel level, LivingEntity owner, Vec3 dir, float damage, boolean beacon) {
        MoonshotBoltEntity bolt = new MoonshotBoltEntity(MoonEntities.MOONSHOT_BOLT.get(), level);
        bolt.setOwner(owner);
        Vec3 eye = owner.getEyePosition().add(dir.scale(0.6)).add(0, -0.1, 0);
        bolt.setPos(eye.x, eye.y, eye.z);
        bolt.damage = damage;
        bolt.entityData.set(DATA_BEACON, beacon);
        bolt.setDeltaMovement(dir.normalize().scale(beacon ? 2.0 : 2.6));
        level.addFreshEntity(bolt);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BEACON, false);
        builder.define(DATA_STUCK, false);
    }

    public boolean isBeacon() {
        return this.entityData.get(DATA_BEACON);
    }

    public boolean isStuck() {
        return this.entityData.get(DATA_STUCK);
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        if (this.isStuck()) {
            this.tickBeacon();
            return;
        }
        if (!this.level().isClientSide() && this.life > 100) {
            this.discard();
            return;
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, hit)) {
            this.hitTargetOrDeflectSelf(hit);
        }
        if (this.isRemoved() || this.isStuck()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);
        // No trail for the first ticks: right in front of the shooter's eyes the sparks would fill the screen.
        if (this.level().isClientSide() && this.tickCount > 2) {
            this.level().addParticle(ModParticles.LUNAR_GLIMMER.get(), this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    private void tickBeacon() {
        if (this.level() instanceof ServerLevel server) {
            Entity owner = this.getOwner();
            Vec3 at = this.position();
            for (LivingEntity victim : Combat.targetsAround(server, owner, at, 9.0)) {
                Vec3 pull = at.subtract(victim.position()).normalize().scale(0.18);
                victim.setDeltaMovement(victim.getDeltaMovement().scale(0.6).add(pull));
                victim.hurtMarked = true;
            }
            if (this.stuckTicks % 4 == 0) {
                double r = 9.0 - (this.stuckTicks % 20) * 0.4;
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8;
                    server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), at.x + Math.cos(a) * r, at.y + 0.3, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            if (++this.stuckTicks > 80) {
                server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.2);
                this.discard();
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || entity.getId() == this.lastHit) {
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
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Entity target = hitResult.getEntity();
        target.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.MOONLIGHT, this, this.getOwner()), this.damage);
        this.lastHit = target.getId();
        if (this.isBeacon()) {
            this.stick(hitResult.getLocation());
            return;
        }
        // Glance off the target in a new direction.
        Vec3 motion = this.getDeltaMovement();
        Vec3 away = motion.multiply(-0.4, 0.2, -0.4).add(this.random.nextGaussian() * 0.8, 0.15, this.random.nextGaussian() * 0.8);
        this.bounce(away.normalize().scale(motion.length()), server);
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        if (this.isBeacon()) {
            this.stick(hitResult.getLocation());
            return;
        }
        Direction face = hitResult.getDirection();
        Vec3 motion = this.getDeltaMovement();
        Vec3 reflected = switch (face.getAxis()) {
            case X -> new Vec3(-motion.x, motion.y, motion.z);
            case Y -> new Vec3(motion.x, -motion.y, motion.z);
            case Z -> new Vec3(motion.x, motion.y, -motion.z);
        };
        Vec3 at = hitResult.getLocation();
        this.setPos(at.x + face.getStepX() * 0.1, at.y + face.getStepY() * 0.1, at.z + face.getStepZ() * 0.1);
        this.lastHit = -1;
        this.bounce(reflected, server);
    }

    private void bounce(Vec3 motion, ServerLevel server) {
        if (++this.bounces > 5) {
            server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), this.getX(), this.getY(), this.getZ(), 10, 0.1, 0.1, 0.1, 0.1);
            this.discard();
            return;
        }
        this.setDeltaMovement(motion);
        this.hurtMarked = true;
        server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), this.getX(), this.getY(), this.getZ(), 8, 0.1, 0.1, 0.1, 0.1);
        server.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.BOLT_RICOCHET.get(), SoundSource.PLAYERS, 0.8F,
            1.0F + this.bounces * 0.12F);
    }

    private void stick(Vec3 at) {
        this.setPos(at.x, at.y, at.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(DATA_STUCK, true);
        this.level().playSound(null, at.x, at.y, at.z, MoonSounds.BOLT_RICOCHET.get(), SoundSource.PLAYERS, 1.2F, 0.5F);
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
