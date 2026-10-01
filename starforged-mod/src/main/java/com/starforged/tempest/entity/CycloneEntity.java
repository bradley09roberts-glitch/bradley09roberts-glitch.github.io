package com.starforged.tempest.entity;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModTags;
import com.starforged.tempest.TempestEntities;
import com.starforged.tempest.TempestSounds;
import com.starforged.util.Combat;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A cyclone.
 * <ul>
 *     <li>{@link Mode#BLADES} - sent out by the Gale Blades: races forward, sweeping creatures up and spinning them.</li>
 *     <li>{@link Mode#TRAP} - loosed by a Cyclone Emitter: drifts after the nearest player and flings them.</li>
 *     <li>{@link Mode#WALL} - one of Veyr's Tornado Wall: circles the arena, hurling anyone it catches.</li>
 * </ul>
 */
public class CycloneEntity extends Projectile {
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(CycloneEntity.class, EntityDataSerializers.FLOAT);

    public enum Mode {
        BLADES, TRAP, WALL
    }

    private Mode mode = Mode.TRAP;
    private int life;
    private int maxLife = 160;
    private float damage = 3.0F;
    private Vec3 heading = Vec3.ZERO;
    private Vec3 orbitCenter = Vec3.ZERO;
    private double orbitRadius;
    private double orbitAngle;
    private double orbitSpeed;

    public CycloneEntity(EntityType<? extends CycloneEntity> type, Level level) {
        super(type, level);
    }

    public static CycloneEntity sendOut(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 direction, float damage) {
        CycloneEntity c = create(level, owner, from, Mode.BLADES, damage, 70, 1.0F);
        c.heading = new Vec3(direction.x, 0, direction.z).normalize().scale(0.45);
        return c;
    }

    public static CycloneEntity spawnTrap(ServerLevel level, Vec3 at) {
        return create(level, null, at, Mode.TRAP, 4.0F, 200, 1.2F);
    }

    public static CycloneEntity wall(ServerLevel level, LivingEntity owner, Vec3 center, double radius, double angle, double speed, int life) {
        CycloneEntity c = create(level, owner, center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), Mode.WALL, 6.0F, life, 1.6F);
        c.orbitCenter = center;
        c.orbitRadius = radius;
        c.orbitAngle = angle;
        c.orbitSpeed = speed;
        return c;
    }

    private static CycloneEntity create(ServerLevel level, @Nullable LivingEntity owner, Vec3 at, Mode mode, float damage, int life, float size) {
        CycloneEntity c = new CycloneEntity(TempestEntities.CYCLONE.get(), level);
        c.setOwner(owner);
        c.mode = mode;
        c.damage = damage;
        c.maxLife = life;
        c.entityData.set(DATA_SIZE, size);
        c.setPos(at.x, at.y, at.z);
        level.addFreshEntity(c);
        level.playSound(null, at.x, at.y, at.z, TempestSounds.BLADES_CYCLONE.get(), SoundSource.HOSTILE, 1.0F, 1.0F / size);
        return c;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, 1.0F);
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
        float size = this.size();
        if (this.level().isClientSide()) {
            for (int i = 0; i < 3; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double h = this.random.nextDouble() * 2.6 * size;
                double r = (0.3 + h * 0.3) * size;
                this.level().addParticle(ModParticles.STORM_WISP.get(), this.getX() + Math.cos(a) * r, this.getY() + h, this.getZ() + Math.sin(a) * r,
                    -Math.sin(a) * 0.3, 0.05, Math.cos(a) * 0.3);
            }
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (this.life > this.maxLife) {
            this.discard();
            return;
        }
        switch (this.mode) {
            case BLADES -> this.setPos(this.position().add(this.heading));
            case WALL -> {
                this.orbitAngle += this.orbitSpeed;
                this.setPos(this.orbitCenter.x + Math.cos(this.orbitAngle) * this.orbitRadius, this.orbitCenter.y,
                    this.orbitCenter.z + Math.sin(this.orbitAngle) * this.orbitRadius);
            }
            case TRAP -> {
                Player target = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), 20.0, p -> !((Player) p).isCreative() && !p.isSpectator());
                Vec3 move = target == null ? new Vec3(Math.cos(this.life * 0.05), 0, Math.sin(this.life * 0.05)).scale(0.05)
                    : target.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(0.13);
                this.setPos(this.position().add(move));
            }
        }
        this.swirl(level, size);
    }

    private void swirl(ServerLevel level, float size) {
        double radius = 1.6 * size;
        AABB box = new AABB(this.getX() - radius, this.getY() - 0.5, this.getZ() - radius, this.getX() + radius, this.getY() + 3.0 * size,
            this.getZ() + radius);
        Entity owner = this.getOwner();
        List<Entity> caught = level.getEntitiesOfClass(Entity.class, box, e -> e instanceof LivingEntity || e instanceof ItemEntity);
        for (Entity e : caught) {
            if (e == owner || e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }
            if (e instanceof LivingEntity living) {
                if (owner != null ? !Combat.canHit(level, owner, living) : living.is(ModTags.STORMBORN)) {
                    continue;
                }
                if (this.life % 10 == 0) {
                    living.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.GALE, this, owner), this.damage);
                }
            }
            Vec3 rel = e.position().subtract(this.position());
            Vec3 tangent = new Vec3(-rel.z, 0, rel.x).normalize();
            Vec3 inward = new Vec3(-rel.x, 0, -rel.z).scale(0.08);
            boolean fling = this.mode != Mode.BLADES && e.getY() > this.getY() + 2.2 * size;
            Vec3 v = fling ? rel.multiply(1, 0, 1).normalize().scale(1.4).add(0, 0.6, 0)
                : tangent.scale(0.35).add(inward).add(0, 0.22, 0).add(this.mode == Mode.BLADES ? this.heading : Vec3.ZERO);
            e.setDeltaMovement(v);
            e.resetFallDistance();
            e.hurtMarked = true;
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
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }
}
