package com.squidgame.entity;

import com.squidgame.registry.ModEntities;
import com.squidgame.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * A thrown marble (target-throw variant of the marbles game). It flies with the vanilla projectile motion - the same
 * integration {@code core/marbles/ThrowModel} uses to plan throws - and comes to rest on the first upward-facing
 * surface at floor level that it touches. Everything else is ignored on purpose: entities, hanging laundry, wall tops,
 * props at the side of a court. A throw is a lob across a yard full of scenery, and a stray cloth must never decide a
 * match, so the flight is exactly the predicted arc. The game learns about the landing through the listener; scoring
 * and results are handled there. The same code runs on the client (the floor level is synchronised) so the marble
 * never sinks into the floor while the server's rest position is on its way.
 */
public class MarbleProjectile extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> DATA_TINT =
            SynchedEntityData.defineId(MarbleProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_FLOOR =
            SynchedEntityData.defineId(MarbleProjectile.class, EntityDataSerializers.FLOAT);
    /** A surface within this distance of the floor level counts as the floor (a table slab, a painted line). */
    private static final double FLOOR_TOLERANCE = 0.6;
    /** Ticks in the air after which a marble is given up as lost. */
    private static final int MAX_FLIGHT_TICKS = 240;
    /** A resting marble removes itself after this long if the game forgot it. */
    private static final int REST_LIFETIME = 1200;
    /** The sprite is centred on the entity: lift it so the marble sits on the floor. */
    private static final double REST_HEIGHT = 0.1;

    private boolean landed;
    private Vec3 landing = Vec3.ZERO;
    private int flightTicks;
    private int restTicks;
    @Nullable
    private Consumer<MarbleProjectile> landListener;

    public MarbleProjectile(EntityType<? extends MarbleProjectile> type, Level level) {
        super(type, level);
    }

    public MarbleProjectile(Level level, LivingEntity owner) {
        super(ModEntities.MARBLE, owner, level);
    }

    /** A marble at a position, not owned by anybody (the throw is attributed by the game, not by vanilla ownership). */
    public MarbleProjectile(Level level, double x, double y, double z) {
        super(ModEntities.MARBLE, x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.MARBLE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TINT, 0xFFFFFF);
        builder.define(DATA_FLOOR, Float.NaN);
    }

    /** Sets the colour of the flight trail (the thrower's pad colour) as 0xRRGGBB. */
    public void setTint(int rgb) {
        this.entityData.set(DATA_TINT, rgb & 0xFFFFFF);
    }

    public int tint() {
        return this.entityData.get(DATA_TINT);
    }

    /**
     * Throws the marble: it lands on the floor at {@code floorY}; the listener runs once on the server when it comes to
     * rest (or is given up as lost).
     */
    public void launch(Vec3 velocity, double floorY, Consumer<MarbleProjectile> listener) {
        this.landListener = listener;
        this.entityData.set(DATA_FLOOR, (float) floorY);
        setDeltaMovement(velocity);
        this.hasImpulse = true;
    }

    public boolean hasLanded() {
        return landed;
    }

    /** Where the marble came to rest (the point it first touched the floor). */
    public Vec3 landingPos() {
        return landing;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (landed || hit.getDirection() != Direction.UP) {
            return;
        }
        float floor = this.entityData.get(DATA_FLOOR);
        Vec3 loc = hit.getLocation();
        if (Float.isNaN(floor) || Math.abs(loc.y - floor) <= FLOOR_TOLERANCE) {
            land(loc);
        }
    }

    private void land(Vec3 at) {
        landed = true;
        landing = at;
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        setPos(at.x, at.y + REST_HEIGHT, at.z);
        notifyLanded();
    }

    private void notifyLanded() {
        Consumer<MarbleProjectile> l = landListener;
        landListener = null;
        if (!level().isClientSide && l != null) {
            l.accept(this);
        }
    }

    @Override
    public void tick() {
        if (landed) {
            if (!level().isClientSide && ++restTicks > REST_LIFETIME) {
                discard();
            }
            return;
        }
        super.tick();
        if (landed) {
            return;
        }
        if (level().isClientSide) {
            int rgb = tint();
            Vector3f c = new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
            level().addParticle(new DustParticleOptions(c, 0.55f), getX(), getY() + 0.1, getZ(), 0, 0, 0);
        } else if (++flightTicks > MAX_FLIGHT_TICKS) {
            landed = true;
            landing = position();
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(true);
            notifyLanded();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Tint", tint());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTint(tag.getInt("Tint"));
    }
}
