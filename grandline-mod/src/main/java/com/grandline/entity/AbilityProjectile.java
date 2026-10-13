package com.grandline.entity;

import com.grandline.combat.Hits;
import com.grandline.net.GLNetwork;
import com.grandline.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Generic projectile for bullets, air blades, fire fists, ice spears, cannonballs, etc. The visual kind and size are
 * synced; damage behaviour lives on the server instance only (not saved: in-flight projectiles vanish on unload).
 */
public class AbilityProjectile extends Projectile {
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(AbilityProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(AbilityProjectile.class, EntityDataSerializers.FLOAT);

    public float damage = 4f;
    public Hits.Info info = Hits.Info.physical(0.5f);
    public int life = 60;
    public double gravity = 0;
    public boolean pierce = false;
    public float explosion = 0;
    public boolean breaksBlocks = false;
    @Nullable public OnHit onHit;
    /** A ship this projectile must not hit (the one that fired it). */
    @Nullable public Entity ignoreShip;
    private final java.util.Set<Integer> hitIds = new java.util.HashSet<>();

    @FunctionalInterface
    public interface OnHit {
        void apply(AbilityProjectile p, @Nullable LivingEntity target, Vec3 pos);
    }

    public AbilityProjectile(EntityType<? extends AbilityProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public static AbilityProjectile shoot(LivingEntity owner, String kind, float size, Vec3 from, Vec3 dir, double speed, float damage, Hits.Info info) {
        return shoot(owner.level(), owner, kind, size, from, dir, speed, damage, info);
    }

    public static AbilityProjectile shoot(Level level, @Nullable LivingEntity owner, String kind, float size, Vec3 from, Vec3 dir, double speed, float damage, Hits.Info info) {
        AbilityProjectile p = new AbilityProjectile(ModEntities.ABILITY_PROJECTILE.get(), level);
        if (owner != null) p.setOwner(owner);
        p.setKind(kind);
        p.setSize(size);
        p.damage = damage;
        p.info = info;
        p.setPos(from.x, from.y, from.z);
        p.setDeltaMovement(dir.normalize().scale(speed));
        p.updateRotation();
        level.addFreshEntity(p);
        return p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, "bullet");
        b.define(SIZE, 0.25f);
    }

    public String kind() {
        return entityData.get(KIND);
    }

    public void setKind(String k) {
        entityData.set(KIND, k);
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public void setSize(float s) {
        entityData.set(SIZE, s);
    }

    /**
     * Flight is swept: this tick's path is first cut short at the first solid block, then everything the projectile
     * meets along the remaining segment is hit in order, so fast shots never skip through a wall or a body, and a
     * piercing shot stops at the wall behind its last victim. Wide projectiles also catch bodies beside the path, but
     * only those in the open from the path (see {@link com.grandline.combat.Cover}).
     */
    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (!level().isClientSide) {
            if (--life <= 0) {
                impact(null, position());
                return;
            }
            Vec3 from = position(), to = from.add(motion);
            BlockHitResult wall = level().clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
            for (Entity e : struck(from, end)) {
                if (e instanceof LivingEntity || e instanceof net.neoforged.neoforge.entity.PartEntity<?> || e instanceof com.grandline.ship.ShipPart) {
                    onHitEntity(new EntityHitResult(e));
                    if (isRemoved()) return;
                }
            }
            if (wall.getType() != HitResult.Type.MISS) {
                onHitBlock(wall);
                if (isRemoved()) return;
            }
        }
        if (level().isClientSide && tickCount > 1) com.grandline.client.ClientFx.trail(this, kind(), size());
        Vec3 next = position().add(motion);
        setPos(next.x, next.y, next.z);
        if (gravity != 0) setDeltaMovement(motion.add(0, -gravity, 0));
        if (isInWater() && !kind().startsWith("water")) setDeltaMovement(getDeltaMovement().scale(0.8));
        updateRotation();
    }

    /** Hit radius: thin shots use the vanilla arrow margin, wide ones half their visual size. */
    private double radius() {
        return Math.max(0.3, size() * 0.5);
    }

    /** Everything this tick's segment meets, nearest first. */
    private java.util.List<Entity> struck(Vec3 from, Vec3 end) {
        double r = radius();
        java.util.List<Entity> out = new java.util.ArrayList<>();
        java.util.Map<Entity, Double> at = new java.util.HashMap<>();
        for (Entity e : level().getEntities(this, new net.minecraft.world.phys.AABB(from, end).inflate(r + 1), this::canHitEntity)) {
            net.minecraft.world.phys.AABB body = e.getBoundingBox().inflate(r);
            var clip = body.clip(from, end);
            double d;
            if (clip.isPresent()) d = clip.get().distanceToSqr(from);
            else if (body.contains(from)) d = 0;
            else continue;
            // a wide shot grazing a body round a corner: the body must be in the open from the path
            if (r > 0.3 && !e.getBoundingBox().inflate(0.3).clip(from, end).isPresent() && !e.getBoundingBox().inflate(0.3).contains(from)) {
                Vec3 c = e.getBoundingBox().getCenter(), ab = end.subtract(from);
                double t = ab.lengthSqr() < 1e-6 ? 0 : Math.max(0, Math.min(1, c.subtract(from).dot(ab) / ab.lengthSqr()));
                if (!com.grandline.combat.Cover.exposed(level(), from.add(ab.scale(t)), e)) continue;
            }
            at.put(e, d);
            out.add(e);
        }
        out.sort(java.util.Comparator.comparingDouble(at::get));
        return out;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (e instanceof com.grandline.ship.ShipPart part) {
            Entity ship = part.getParent();
            if (ship == ignoreShip || (getOwner() != null && getOwner().getVehicle() == ship)) return false;
            if (getOwner() != null && ship instanceof com.grandline.ship.ShipEntity se && se.carries(getOwner())) return false;
            return !hitIds.contains(e.getId());
        }
        // body segments of multipart creatures (Sea Kings) are hits on the creature
        if (e instanceof net.neoforged.neoforge.entity.PartEntity<?> pe && pe.getParent() instanceof LivingEntity owner)
            return owner.isAlive() && owner != getOwner() && !hitIds.contains(owner.getId());
        return super.canHitEntity(e) && e instanceof LivingEntity && !hitIds.contains(e.getId()) && e != getOwner();
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        if (r.getEntity() instanceof LivingEntity le) hitEntity(le);
        else if (r.getEntity() instanceof net.neoforged.neoforge.entity.PartEntity<?> pe && pe.getParent() instanceof LivingEntity owner) hitEntity(owner);
        else if (r.getEntity() instanceof com.grandline.ship.ShipPart part && hitIds.add(part.getId())) {
            part.hurt(com.grandline.registry.ModDamageTypes.source(level(), com.grandline.registry.ModDamageTypes.CANNON, this, getOwner()), damage);
            impact(null, position());
        }
    }

    private void hitEntity(LivingEntity le) {
        if (!hitIds.add(le.getId())) return;
        Entity owner = getOwner();
        // the swept flight already proved the path from the muzzle to this body is open
        if (owner instanceof LivingEntity lo) {
            com.grandline.combat.Cover.markCleared(lo, le);
            com.grandline.combat.Cover.from(position(), () -> Hits.hit(lo, le, damage, info));
        } else le.hurt(damageSources().magic(), damage);
        if (onHit != null) onHit.apply(this, le, position());
        if (!pierce) impact(le, position());
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        if (breaksBlocks && level() instanceof ServerLevel sl && com.grandline.world.TownProtection.canModify(sl, r.getBlockPos())) {
            BlockPos bp = r.getBlockPos();
            float hardness = level().getBlockState(bp).getDestroySpeed(level(), bp);
            if (hardness >= 0 && hardness < 3f) com.grandline.fruit.TerrainChanges.breakTemporarily(sl, bp);
        }
        impact(null, r.getLocation());
    }

    private void impact(@Nullable LivingEntity hit, Vec3 pos) {
        if (level() instanceof ServerLevel sl) {
            if (explosion > 0) {
                Entity owner = getOwner();
                // the blast spreads from just in front of the wall it struck, and walls shelter those behind them
                Vec3 back = getDeltaMovement().lengthSqr() > 1e-6 ? getDeltaMovement().normalize().scale(-0.3) : Vec3.ZERO;
                Vec3 centre = com.grandline.combat.Cover.freeOrigin(sl, pos.add(back), null);
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(explosion))) {
                    if (e == owner || e == hit || !e.isAlive()) continue;
                    if (!com.grandline.combat.Cover.exposed(sl, centre, e)) continue;
                    if (owner instanceof LivingEntity lo) {
                        com.grandline.combat.Cover.markCleared(lo, e);
                        Hits.hit(lo, e, damage * 0.7f, info);
                    }
                }
                GLNetwork.fx(this, "explosion", pos.x, pos.y, pos.z, explosion, 0, 0);
                sl.playSound(null, BlockPos.containing(pos), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
                if (breaksBlocks) com.grandline.fruit.TerrainChanges.crater(sl, BlockPos.containing(pos), Math.min(4, (int) explosion), getOwner());
            }
            if (onHit != null && hit == null) onHit.apply(this, null, pos);
            GLNetwork.fx(this, "impact_" + kind(), pos.x, pos.y, pos.z, size(), 0, 0);
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return false;
    }
}
