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
 * A thrown marble (target-throw variant of the marbles game). It flies with the vanilla projectile motion (the same
 * integration {@code core/marbles/ThrowModel} uses to plan throws), ignores entities, rebounds off walls and roofs
 * and comes to rest on the first upward-facing surface it hits. The game learns about the landing through the
 * listener; scoring and results are handled there. The same code runs on the client so the marble never sinks into
 * the floor while the server's rest position is on its way.
 */
public class MarbleProjectile extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> DATA_TINT =
            SynchedEntityData.defineId(MarbleProjectile.class, EntityDataSerializers.INT);
    /** Share of the speed along a wall's normal that survives a rebound (the marble is dull on stone, not a rubber ball). */
    private static final double WALL_RESTITUTION = 0.45;
    private static final double WALL_FRICTION = 0.8;
    private static final int MAX_WALL_BOUNCES = 3;
    /** Ticks in the air after which a marble is given up as lost. */
    private static final int MAX_FLIGHT_TICKS = 240;
    /** A resting marble removes itself after this long if the game forgot it. */
    private static final int REST_LIFETIME = 1200;
    /** The sprite is centred on the entity: lift it so the marble sits on the floor. */
    private static final double REST_HEIGHT = 0.1;

    private boolean landed;
    private Vec3 landing = Vec3.ZERO;
    private int bounces;
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
    }

    /** Sets the colour of the flight trail (the thrower's pad colour) as 0xRRGGBB. */
    public void setTint(int rgb) {
        this.entityData.set(DATA_TINT, rgb & 0xFFFFFF);
    }

    public int tint() {
        return this.entityData.get(DATA_TINT);
    }

    /** Throws the marble: the listener runs once on the server when it comes to rest (or is given up as lost). */
    public void launch(Vec3 velocity, Consumer<MarbleProjectile> listener) {
        this.landListener = listener;
        setDeltaMovement(velocity);
        this.hasImpulse = true;
    }

    public boolean hasLanded() {
        return landed;
    }

    /** Where the marble came to rest (the point it first touched an upward-facing surface). */
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
        if (landed) {
            return;
        }
        Vec3 loc = hit.getLocation();
        Direction face = hit.getDirection();
        if (face == Direction.UP || bounces >= MAX_WALL_BOUNCES) {
            land(loc);
            return;
        }
        bounces++;
        Vec3 v = getDeltaMovement();
        Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        double vn = v.dot(n);
        Vec3 along = v.subtract(n.scale(vn));
        setPos(loc.add(n.scale(0.06)));
        setDeltaMovement(along.scale(WALL_FRICTION).add(n.scale(-vn * WALL_RESTITUTION)));
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
