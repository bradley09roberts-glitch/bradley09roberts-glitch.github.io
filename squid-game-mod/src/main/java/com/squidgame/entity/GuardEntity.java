package com.squidgame.entity;

import com.squidgame.tournament.TournamentManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A masked guard. Ranks: circle (worker), triangle (armed soldier), square (manager). Guards stand at posts
 * (turning slowly to watch the nearest contestant), patrol routes, aim and "fire" at contestants who break the
 * rules (a stylised flash and crack, no gore), open doors and inspect fallen contestants.
 */
public class GuardEntity extends PathfinderMob implements GeoEntity {
    public static final int RANK_CIRCLE = 0, RANK_TRIANGLE = 1, RANK_SQUARE = 2;

    /** Guard poses held by the "action" controller. */
    public enum Stance {
        NONE(null), ALERT(Anims.G_IDLE_ALERT), RIGID(Anims.G_IDLE_RIGID), AIMING(Anims.G_AIM), CARRY(Anims.G_CARRY_POSE),
        POINT(Anims.G_POINT_FORWARD);
        public final String animation;

        Stance(String animation) {
            this.animation = animation;
        }

        public static Stance byOrdinal(int i) {
            Stance[] v = values();
            return i >= 0 && i < v.length ? v[i] : NONE;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_RANK =
            SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STANCE =
            SynchedEntityData.defineId(GuardEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    // server-side duty state
    @Nullable
    private Vec3 postPos;
    private float postYaw;
    private final List<Vec3> patrol = new ArrayList<>();
    private int patrolIndex;
    private int patrolWait;
    @Nullable
    private LivingEntity aimTarget;
    private int aimTicks;
    @Nullable
    private Runnable onFire;
    private int scanTimer;
    private int orphanTicks;

    public GuardEntity(EntityType<? extends GuardEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.11)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RANK, RANK_TRIANGLE);
        builder.define(DATA_STANCE, 0);
    }

    public int rank() {
        return entityData.get(DATA_RANK);
    }

    public void setRank(int rank) {
        entityData.set(DATA_RANK, Mth.clamp(rank, 0, 2));
    }

    public boolean isArmed() {
        return rank() == RANK_TRIANGLE;
    }

    public Stance stance() {
        return Stance.byOrdinal(entityData.get(DATA_STANCE));
    }

    public void setStance(Stance s) {
        if (stance() != s) {
            entityData.set(DATA_STANCE, s.ordinal());
        }
    }

    // ---------------------------------------------------------------- duties

    /** Stand at a post facing {@code yaw}, scanning slowly towards nearby contestants. */
    public void assignPost(Vec3 pos, float yaw) {
        this.postPos = pos;
        this.postYaw = yaw;
        this.patrol.clear();
        setStance(Stance.RIGID);
    }

    public void assignPatrol(List<Vec3> route) {
        this.patrol.clear();
        this.patrol.addAll(route);
        this.patrolIndex = 0;
        this.postPos = null;
        setStance(Stance.NONE);
    }

    public void clearDuty() {
        this.postPos = null;
        this.patrol.clear();
        this.aimTarget = null;
        setStance(Stance.NONE);
    }

    /**
     * Raises the rifle at a target, fires after {@code delayTicks} (flash, crack, recoil animation) and then runs
     * {@code afterFire}. The target is only visually engaged here; elimination is applied by the game.
     */
    public void aimAndFire(LivingEntity target, int delayTicks, @Nullable Runnable afterFire) {
        this.aimTarget = target;
        this.aimTicks = Math.max(2, delayTicks);
        this.onFire = afterFire;
        getNavigation().stop();
        setStance(Stance.AIMING);
    }

    public boolean isBusyAiming() {
        return aimTarget != null;
    }

    public void gesture(String shortName) {
        triggerAnim("action", shortName);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (++orphanTicks == 80 && !TournamentManager.isManaged(this)) {
            discard();
            return;
        }
        if (aimTarget != null) {
            if (!aimTarget.isAlive() || aimTarget.isRemoved()) {
                aimTarget = null;
                setStance(postPos != null ? Stance.RIGID : Stance.NONE);
            } else {
                getLookControl().setLookAt(aimTarget.getX(), aimTarget.getEyeY(), aimTarget.getZ());
                faceTo(aimTarget.position());
                if (--aimTicks <= 0) {
                    fireNow();
                }
            }
            return;
        }
        if (postPos != null) {
            tickPost();
        } else if (!patrol.isEmpty()) {
            tickPatrol();
        }
    }

    private void faceTo(Vec3 p) {
        double dx = p.x - getX();
        double dz = p.z - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private void fireNow() {
        gesture("fire");
        if (level() instanceof ServerLevel sl) {
            Vec3 muzzle = position().add(Vec3.directionFromRotation(0, getYRot()).scale(0.9)).add(0, 1.4, 0);
            sl.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.01);
            sl.playSound(null, getX(), getY(), getZ(), com.squidgame.registry.ModSounds.ELIMINATION_CRACK,
                    net.minecraft.sounds.SoundSource.HOSTILE, 2.0f, 1.0f);
        }
        Runnable r = onFire;
        onFire = null;
        aimTarget = null;
        setStance(postPos != null ? Stance.RIGID : Stance.NONE);
        if (r != null) {
            r.run();
        }
    }

    private void tickPost() {
        Vec3 post = postPos;
        if (post.distanceToSqr(position()) > 1.5 * 1.5) {
            getNavigation().moveTo(post.x, post.y, post.z, 1.0);
            return;
        }
        getNavigation().stop();
        // watch the nearest contestant, otherwise return to the post's facing
        if (++scanTimer % 10 == 0) {
            ContestantEntity nearest = null;
            double best = 24 * 24;
            for (ContestantEntity c : level().getEntitiesOfClass(ContestantEntity.class,
                    getBoundingBox().inflate(24, 8, 24))) {
                double d = c.distanceToSqr(this);
                if (d < best && !c.getActivity().isDown()) {
                    best = d;
                    nearest = c;
                }
            }
            if (nearest != null) {
                getLookControl().setLookAt(nearest, 20f, 20f);
                setStance(Stance.ALERT);
            } else {
                setYHeadRot(postYaw);
                setStance(Stance.RIGID);
            }
        }
        float diff = Mth.wrapDegrees(postYaw - getYRot());
        if (Math.abs(diff) > 2) {
            setYRot(getYRot() + Mth.clamp(diff, -6f, 6f));
            setYBodyRot(getYRot());
        }
    }

    private void tickPatrol() {
        Vec3 target = patrol.get(patrolIndex);
        if (patrolWait > 0) {
            patrolWait--;
            return;
        }
        if (target.distanceToSqr(position()) < 1.6 * 1.6) {
            patrolIndex = (patrolIndex + 1) % patrol.size();
            patrolWait = 40 + random.nextInt(60);
            getNavigation().stop();
            return;
        }
        if (!getNavigation().isInProgress()) {
            getNavigation().moveTo(target.x, target.y, target.z, 0.9);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Rank", rank());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_RANK, tag.getInt("Rank"));
    }

    // ---------------------------------------------------------------- animation

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "locomotion", 6, this::locomotionState)
                .setAnimationSpeedHandler(g -> {
                    double v = Math.sqrt(Math.pow(g.getX() - g.xo, 2) + Math.pow(g.getZ() - g.zo, 2));
                    if (v > 0.19) {
                        return Mth.clamp(v / 0.30, 0.6, 2.0);
                    }
                    if (v > 0.02) {
                        return Mth.clamp(v / 0.12, 0.5, 2.0);
                    }
                    return 1.0;
                }));
        AnimationController<GuardEntity> action = new AnimationController<>(this, "action", 4, this::actionState);
        action.triggerableAnim("fire", RawAnimation.begin().thenPlay(Anims.G_FIRE));
        action.triggerableAnim("lower", RawAnimation.begin().thenPlay(Anims.G_LOWER));
        action.triggerableAnim("salute", RawAnimation.begin().thenPlay(Anims.G_SALUTE));
        action.triggerableAnim("open_door", RawAnimation.begin().thenPlay(Anims.G_OPEN_DOOR));
        action.triggerableAnim("inspect", RawAnimation.begin().thenPlay(Anims.G_INSPECT));
        action.triggerableAnim("clap", RawAnimation.begin().thenPlay(Anims.G_CLAP));
        action.triggerableAnim("wave_on", RawAnimation.begin().thenPlay(Anims.G_WAVE_ON));
        action.triggerableAnim("point_down", RawAnimation.begin().thenPlay(Anims.G_POINT_DOWN));
        action.triggerableAnim("turn_left", RawAnimation.begin().thenPlay(Anims.G_TURN_LEFT));
        action.triggerableAnim("turn_right", RawAnimation.begin().thenPlay(Anims.G_TURN_RIGHT));
        controllers.add(action);
    }

    private PlayState locomotionState(AnimationState<GuardEntity> state) {
        double v = Math.sqrt(Math.pow(getX() - xo, 2) + Math.pow(getZ() - zo, 2));
        if (v > 0.19) {
            return state.setAndContinue(RawAnimation.begin().thenLoop(Anims.G_RUN));
        }
        if (v > 0.02) {
            return state.setAndContinue(RawAnimation.begin().thenLoop(Anims.G_WALK));
        }
        return state.setAndContinue(RawAnimation.begin().thenLoop(Anims.G_IDLE));
    }

    private PlayState actionState(AnimationState<GuardEntity> state) {
        Stance s = stance();
        if (s == Stance.NONE || s.animation == null) {
            return PlayState.STOP;
        }
        // moving guards keep their legs animating via the locomotion controller; stances only cover standing poses
        if (s == Stance.AIMING || s == Stance.POINT || s == Stance.CARRY || s == Stance.ALERT || s == Stance.RIGID) {
            return state.setAndContinue(RawAnimation.begin().thenLoop(s.animation));
        }
        return PlayState.STOP;
    }
}
