package com.terracraft.entity.mob;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Terraria worm AI (Devourer, Giant Worm, Eater of Worlds): a head that burrows through blocks toward its
 * target (it can only steer while inside terrain and falls in an arc when it breaks out) followed by a chain of
 * body segments, each a separate entity that trails the one ahead.
 * <ul>
 *     <li>Shared-life worms (Devourer): hits on any segment damage the head; the whole worm dies with it.</li>
 *     <li>Split worms (Eater of Worlds): every segment has its own life; when a segment dies, the segment
 *     behind it becomes a new head.</li>
 * </ul>
 */
public class WormMob extends TerrariaMob {
    public static final int HEAD = 0;
    public static final int BODY = 1;
    public static final int TAIL = 2;
    private static final EntityDataAccessor<Integer> DATA_PART = SynchedEntityData.defineId(WormMob.class, EntityDataSerializers.INT);

    /** Shape and behaviour of a worm species. */
    public record Spec(int length, double spacing, double speed, double turn, boolean sharedLife, boolean flies) {}

    private final Spec spec;
    private @Nullable UUID leaderId;
    private @Nullable UUID headId;
    private boolean built;
    private int orphanTicks;

    public WormMob(EntityType<? extends WormMob> type, Level level, Spec spec) {
        super(type, level);
        this.spec = spec;
        this.noPhysics = true;
        setNoGravity(true);
    }

    public Spec spec() {
        return spec;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PART, HEAD);
    }

    public int part() {
        return entityData.get(DATA_PART);
    }

    public void setPart(int part) {
        entityData.set(DATA_PART, part);
    }

    public boolean isHead() {
        return part() == HEAD;
    }

    @Override
    public String spriteVariant() {
        return switch (part()) {
            case BODY -> "body";
            case TAIL -> "tail";
            default -> "head";
        };
    }

    @Override
    public boolean showsHealthBar() {
        return isHead() || !spec.sharedLife();
    }

    @Override
    public boolean countsTowardSpawnCap() {
        return isHead();
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public long coinValue() {
        return isHead() || !spec.sharedLife() ? super.coinValue() : 0;
    }

    /** Spawns the body chain behind a freshly spawned head. */
    protected void buildBody(ServerLevel level) {
        built = true;
        WormMob previous = this;
        for (int i = 1; i < spec.length(); i++) {
            WormMob segment = (WormMob) getType().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (segment == null) {
                return;
            }
            segment.built = true;
            segment.setPart(i == spec.length() - 1 ? TAIL : BODY);
            segment.leaderId = previous.getUUID();
            segment.headId = getUUID();
            segment.snapTo(getX(), getY() - i * spec.spacing() * 0.5, getZ(), getYRot(), getXRot());
            segment.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            onSegmentSpawned(segment, i);
            level.addFreshEntity(segment);
            previous = segment;
        }
    }

    /** Hook for bosses (per-segment life, defense...). */
    protected void onSegmentSpawned(WormMob segment, int index) {
    }

    @Override
    public void travel(Vec3 input) {
        // worms move themselves (no friction, no gravity from vanilla physics)
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (isHead()) {
            if (!built) {
                buildBody(level);
            }
            headAi(level);
        } else {
            followAi(level);
        }
    }

    private boolean inTerrain() {
        AABB box = getBoundingBox().inflate(0.3);
        return !level().noBlockCollision(this, box) || level().containsAnyLiquid(box);
    }

    protected void headAi(ServerLevel level) {
        LivingEntity target = getTarget();
        Vec3 motion = getDeltaMovement();
        if (spec.flies() || inTerrain()) {
            Vec3 goal = target != null && target.isAlive() ? target.getEyePosition()
                : position().add(Mth.sin(tickCount * 0.02F) * 12, -6, Mth.cos(tickCount * 0.02F) * 12);
            Vec3 desired = goal.subtract(position()).normalize().scale(spec.speed());
            motion = motion.add(desired.subtract(motion).scale(spec.turn()));
        } else {
            motion = new Vec3(motion.x * 0.99, motion.y - 0.04, motion.z * 0.99);
        }
        double max = spec.speed() * 1.6;
        if (motion.lengthSqr() > max * max) {
            motion = motion.normalize().scale(max);
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        face(motion);
    }

    protected void followAi(ServerLevel level) {
        Entity leader = leaderId != null ? level.getEntity(leaderId) : null;
        if (!(leader instanceof WormMob ahead) || !ahead.isAlive()) {
            if (spec.sharedLife()) {
                if (++orphanTicks > 2) {
                    discard();
                }
            } else {
                // Eater of Worlds: the segment behind a destroyed one becomes a new head
                setPart(HEAD);
                leaderId = null;
                built = true;
            }
            return;
        }
        orphanTicks = 0;
        Vec3 toLeader = ahead.position().subtract(position());
        double distance = toLeader.length();
        Vec3 step = Vec3.ZERO;
        if (distance > spec.spacing()) {
            step = toLeader.normalize().scale(distance - spec.spacing());
            move(MoverType.SELF, step);
        }
        setDeltaMovement(step);
        face(toLeader);
        if (ahead.getTarget() != null) {
            setTarget(ahead.getTarget());
        }
    }

    private void face(Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6) {
            return;
        }
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        setXRot((float) -(Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (spec.sharedLife() && !isHead() && headId != null && level.getEntity(headId) instanceof WormMob head && head.isAlive()) {
            return head.hurtServer(level, source, amount);
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return isHead() && super.removeWhenFarAway(distanceSq);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("WormPart", part());
        output.putBoolean("WormBuilt", built);
        if (leaderId != null) {
            output.store("WormLeader", UUIDUtil.CODEC, leaderId);
        }
        if (headId != null) {
            output.store("WormHead", UUIDUtil.CODEC, headId);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setPart(input.getIntOr("WormPart", HEAD));
        built = input.getBooleanOr("WormBuilt", false);
        leaderId = input.read("WormLeader", UUIDUtil.CODEC).orElse(null);
        headId = input.read("WormHead", UUIDUtil.CODEC).orElse(null);
    }
}
