package com.squidgame.entity;

import com.squidgame.SquidConfig;
import com.squidgame.core.Appearance;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import com.squidgame.tournament.TournamentManager;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
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

import java.util.ArrayDeque;

/**
 * An NPC tournament contestant: numbered green tracksuit, persistent {@link Personality}, pluggable game
 * {@link NpcBehavior}, vanilla ground navigation plus motor helpers, stuck recovery that never passes through
 * walls, and GeckoLib animation (a "locomotion" controller driven by real movement and an "action" controller
 * driven by the synced {@link Activity} plus server-triggered one-shots).
 */
public class ContestantEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_NUMBER =
            SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_APPEARANCE =
            SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_ACTIVITY =
            SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.INT);
    /** 0 = normal idle, 1 = nervous, 2 = confident. */
    private static final EntityDataAccessor<Integer> DATA_STYLE =
            SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.INT);
    /** Held prop for rendering: 0 none, 1 needle, 2 marble, 3 tin. */
    private static final EntityDataAccessor<Integer> DATA_HELD =
            SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.INT);
    /** Ticks since the contestant started a pull / used by animation speed; also used for run/walk lock. */
    private static final EntityDataAccessor<Float> DATA_ANIM_SPEED =
            SynchedEntityData.defineId(ContestantEntity.class, EntityDataSerializers.FLOAT);

    // blocks/tick at which the authored walk / run loops plant their feet exactly (2.46 and 5.8 blocks/s, tools/assets/models/gait_info.json)
    public static final double WALK_REF_SPEED = 0.123;
    public static final double RUN_REF_SPEED = 0.29;
    private static final double RUN_THRESHOLD = 0.19;
    private static final double MOVE_THRESHOLD = 0.02;

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    // ---- server-side brain state
    private Personality personality = Personality.generate(new Rng(0));
    private Rng rng = new Rng(0);
    @Nullable
    private NpcBehavior behavior;
    private final NpcMemory memory = new NpcMemory();
    private int contestantNumber;
    private int aiPhase;
    private boolean nearHuman = true;
    private int nearHumanCheck;

    // ---- motor state
    @Nullable
    private Vec3 moveTarget;
    private double moveSpeed;
    private boolean directMove;

    // ---- stuck recovery
    private final ArrayDeque<Vec3> breadcrumbs = new ArrayDeque<>();
    private int breadcrumbTimer;
    private Vec3 stuckRef = Vec3.ZERO;
    private int stuckTicks;
    private int stuckStage;
    private int orphanTicks;

    // ---- client-side locomotion tracking
    private boolean wasRunning;
    private int skidTicks;

    public ContestantEntity(EntityType<? extends ContestantEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.xpReward = 0;
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.10)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NUMBER, 0);
        builder.define(DATA_APPEARANCE, Appearance.defaults().pack());
        builder.define(DATA_ACTIVITY, 0);
        builder.define(DATA_STYLE, 0);
        builder.define(DATA_HELD, 0);
        builder.define(DATA_ANIM_SPEED, 1.0f);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation nav = new GroundPathNavigation(this, level);
        nav.setCanFloat(true);
        nav.setCanOpenDoors(false);
        nav.setCanPassDoors(false);
        return nav;
    }

    /**
     * Vanilla {@code Mob.setSpeed} also feeds the speed into the forward input (zza = speed), so a mob's ground speed grows with
     * the square of its movement attribute (attribute 0.1 would crawl at 0.4 blocks/s). Contestants and guards use the player
     * convention instead: full forward input, so blocks/s = 43 x attribute x speed modifier (0.1 x 1.0 = 4.3 blocks/s, the
     * walking pace of a player) and modifiers scale the pace linearly.
     */
    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed);
        setZza(speed > 0.0f ? 1.0f : 0.0f);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    // ------------------------------------------------------------------ identity

    /** Applies a contestant's identity to this body. */
    public void configure(int number, String name, Personality personality, Appearance appearance, long rngSeed) {
        this.contestantNumber = number;
        this.personality = personality;
        this.rng = new Rng(rngSeed);
        this.entityData.set(DATA_NUMBER, number);
        this.entityData.set(DATA_APPEARANCE, appearance.pack());
        int style = personality.caution() > 0.62 ? 1 : (personality.courage() > 0.68 ? 2 : 0);
        this.entityData.set(DATA_STYLE, style);
        setCustomName(net.minecraft.network.chat.Component.literal(String.format("No. %03d %s", number, name)));
        setCustomNameVisible(false);
        // speed: athletic contestants run a little faster, rookies a little slower (within human sprint range)
        double base = 0.10 * (0.93 + 0.14 * personality.skill());
        var attr = getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            attr.setBaseValue(base);
        }
    }

    public int number() {
        return entityData.get(DATA_NUMBER);
    }

    public Appearance appearance() {
        return Appearance.unpack(entityData.get(DATA_APPEARANCE));
    }

    public Personality personality() {
        return personality;
    }

    public Rng rng() {
        return rng;
    }

    public NpcMemory memory() {
        return memory;
    }

    public int contestantNumber() {
        return contestantNumber != 0 ? contestantNumber : number();
    }

    public int heldItem() {
        return entityData.get(DATA_HELD);
    }

    public void setHeldItem(int kind) {
        entityData.set(DATA_HELD, kind);
    }

    public int idleStyle() {
        return entityData.get(DATA_STYLE);
    }

    // ------------------------------------------------------------------ activity / animation triggers

    public Activity getActivity() {
        return Activity.byOrdinal(entityData.get(DATA_ACTIVITY));
    }

    public void setActivity(Activity a) {
        if (getActivity() != a) {
            entityData.set(DATA_ACTIVITY, a.ordinal());
        }
    }

    /** Plays a one-shot gesture on the "action" controller on every tracking client (name = key from {@link Anims}). */
    public void triggerAction(String shortName) {
        triggerAnim("action", shortName);
    }

    // ------------------------------------------------------------------ behaviour

    @Nullable
    public NpcBehavior behavior() {
        return behavior;
    }

    public void setBehavior(@Nullable NpcBehavior b) {
        if (this.behavior != null) {
            this.behavior.stop(this);
        }
        this.behavior = b;
        this.stuckStage = 0;
        this.stuckTicks = 0;
        if (b != null) {
            b.start(this);
        }
    }

    /** Called by the tournament when someone near this NPC was eliminated. */
    public void witnessElimination(int otherNumber, double distance) {
        if (behavior != null) {
            behavior.onWitnessElimination(this, otherNumber, distance);
        }
    }

    // ------------------------------------------------------------------ motor helpers

    /** Horizontal speed over the last tick in blocks/tick (position based, identical on client and server). */
    public double horizontalSpeed() {
        double dx = getX() - xo;
        double dz = getZ() - zo;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * True while this NPC is commanded to move: the "movement key is held" of an AI contestant, judged by the games exactly
     * like a player's input. Direct moves count for as long as the target is set (the vanilla move control consumes its wanted
     * position every tick, so {@code hasWanted()} alone is false by the time a game looks).
     */
    public boolean isTryingToMove() {
        return moveTarget != null && (directMove || getNavigation().isInProgress() || getMoveControl().hasWanted());
    }

    /**
     * Walk towards {@code target} using pathfinding. {@code speedMul} multiplies the movement-speed attribute
     * (1.0 ~ walking pace of a player, 1.3 ~ sprinting). Returns false if no path exists.
     */
    public boolean moveToward(Vec3 target, double speedMul) {
        this.moveTarget = target;
        this.moveSpeed = speedMul;
        this.directMove = false;
        Path current = getNavigation().getPath();
        if (current != null && !getNavigation().isDone()) {
            BlockPos end = current.getTarget();
            if (end != null && end.distToCenterSqr(target.x, target.y, target.z) < 1.0) {
                getNavigation().setSpeedModifier(speedMul);
                return true;
            }
        }
        return getNavigation().moveTo(target.x, target.y, target.z, speedMul);
    }

    /** Move straight at a point without pathfinding (open fields, bridges, lanes). */
    public void moveDirect(Vec3 target, double speedMul) {
        this.moveTarget = target;
        this.moveSpeed = speedMul;
        this.directMove = true;
        getNavigation().stop();
        getMoveControl().setWantedPosition(target.x, target.y, target.z, speedMul);
    }

    /** Stops walking (friction brings the body to rest within a few ticks). */
    public void stopMoving() {
        this.moveTarget = null;
        this.directMove = false;
        getNavigation().stop();
        getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
        setZza(0f);
        setXxa(0f);
    }

    /** Instantly removes horizontal momentum (used for the "frozen" pose). */
    public void killHorizontalMomentum() {
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0, v.y, 0);
    }

    public void lookAtPos(Vec3 p) {
        getLookControl().setLookAt(p.x, p.y, p.z);
    }

    /** Rotates the whole body to face a position (not just the head). */
    public void faceToward(Vec3 p) {
        double dx = p.x - getX();
        double dz = p.z - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    public void faceYaw(float yaw) {
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    /** Ballistic leap towards a point (gap hops). Horizontal speed in blocks/tick, apex boost added to vy. */
    public void leapToward(Vec3 target, double horizontalSpeed, double upSpeed) {
        Vec3 d = new Vec3(target.x - getX(), 0, target.z - getZ());
        double len = d.length();
        if (len < 1.0E-4) {
            return;
        }
        Vec3 dir = d.scale(1.0 / len);
        setDeltaMovement(dir.x * horizontalSpeed, upSpeed, dir.z * horizontalSpeed);
        this.hasImpulse = true;
        triggerAction("jump_leap");
    }

    public void teleportSafely(Vec3 p) {
        getNavigation().stop();
        moveTo(p.x, p.y, p.z, getYRot(), getXRot());
        setDeltaMovement(Vec3.ZERO);
        stuckTicks = 0;
        stuckStage = 0;
        stuckRef = p;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        long profStart = com.squidgame.tournament.Profiler.start();
        super.tick();
        if (level().isClientSide) {
            return;
        }
        com.squidgame.tournament.Profiler.end(com.squidgame.tournament.Profiler.Section.NPC_ENTITY, profStart);
        // orphan cleanup: an NPC that the tournament does not know about must not linger (e.g. after a crash)
        if (++orphanTicks == 60 || (orphanTicks > 60 && orphanTicks % 400 == 0)) {
            if (!TournamentManager.isManaged(this)) {
                discard();
                return;
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        memory.tick(level().getGameTime());

        // level of detail: NPCs far from every human decide less often (navigation itself still runs every tick)
        if (++nearHumanCheck >= 20) {
            nearHumanCheck = 0;
            nearHuman = isNearHuman();
        }
        boolean decide = nearHuman || (tickCount + getId()) % SquidConfig.get().npcFarTickInterval == 0;
        if (directMove && moveTarget != null) {
            // the vanilla move control consumes its wanted position every tick (zza falls back to 0 on the next one), so a
            // direct move that is only renewed by the throttled behaviour would run at a third of its speed far from humans
            getMoveControl().setWantedPosition(moveTarget.x, moveTarget.y, moveTarget.z, moveSpeed);
        }
        if (behavior != null && decide) {
            long profStart = com.squidgame.tournament.Profiler.start();
            behavior.tick(this);
            com.squidgame.tournament.Profiler.end(com.squidgame.tournament.Profiler.Section.NPC_BEHAVIOR, profStart);
        }
        trackBreadcrumbs();
        detectStuck();
    }

    private boolean isNearHuman() {
        double d = SquidConfig.get().npcFarDistance;
        Player p = level().getNearestPlayer(this, d);
        return p != null;
    }

    private void trackBreadcrumbs() {
        if (--breadcrumbTimer > 0 || !onGround()) {
            return;
        }
        breadcrumbTimer = 15;
        Vec3 here = position();
        Vec3 last = breadcrumbs.peekLast();
        if (last == null || last.distanceToSqr(here) > 2.25) {
            breadcrumbs.addLast(here);
            while (breadcrumbs.size() > 10) {
                breadcrumbs.removeFirst();
            }
        }
    }

    /**
     * Stuck recovery, escalating but never leaving the walkable space:
     * 1 jump, 2 sidestep and repath, 3 shove away from neighbours, 4 return to the last good breadcrumb,
     * 5 tell the behaviour ({@link NpcBehavior#onStuck}).
     */
    private void detectStuck() {
        if (!isTryingToMove() || moveTarget == null) {
            stuckTicks = 0;
            stuckStage = 0;
            stuckRef = position();
            return;
        }
        if (position().distanceToSqr(moveTarget) < 1.5 * 1.5) {
            stuckTicks = 0;
            stuckRef = position();
            return;
        }
        if (tickCount % 10 != 0) {
            return;
        }
        if (position().distanceToSqr(stuckRef) > 0.35 * 0.35) {
            stuckRef = position();
            stuckTicks = 0;
            if (stuckStage > 0 && tickCount % 40 == 0) {
                stuckStage--;
            }
            return;
        }
        stuckTicks += 10;
        if (stuckTicks < 30) {
            return;
        }
        stuckTicks = 0;
        stuckStage++;
        switch (stuckStage) {
            case 1 -> {
                getJumpControl().jump();
            }
            case 2 -> {
                Vec3 side = new Vec3(-(moveTarget.z - getZ()), 0, moveTarget.x - getX()).normalize()
                        .scale(rng.nextBoolean() ? 2.0 : -2.0);
                getNavigation().stop();
                BlockPos mid = BlockPos.containing(getX() + side.x, getY(), getZ() + side.z);
                getNavigation().moveTo(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5, Math.max(0.8, moveSpeed));
            }
            case 3 -> {
                push((rng.nextDouble() - 0.5) * 0.5, 0.3, (rng.nextDouble() - 0.5) * 0.5);
                getJumpControl().jump();
            }
            case 4 -> {
                Vec3 crumb = breadcrumbs.peekFirst();
                if (crumb != null) {
                    teleportSafely(crumb);
                }
            }
            default -> {
                if (behavior != null) {
                    behavior.onStuck(this);
                }
                stuckStage = 2;
            }
        }
    }

    public int stuckStage() {
        return stuckStage;
    }

    // ------------------------------------------------------------------ damage / interaction

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return false; // contestants are eliminated by game rules, never by vanilla damage
    }

    /** How far away (blocks, at the default "Entity Distance" of 100 %) a contestant is still drawn. */
    public static final double RENDER_DISTANCE = 176.0;

    /**
     * The crowd is the game: vanilla stops drawing a person-sized mob 64 blocks away, which on a 130 m field made every
     * runner vanish once it was a little ahead of the player. The player's "Entity Distance" video setting still scales this.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        double r = RENDER_DISTANCE * net.minecraft.world.entity.Entity.getViewScale();
        return distSqr < r * r;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            return TournamentManager.onInteractContestant(sp, this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isPushedByFluid() {
        return true;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Number", contestantNumber());
        tag.putLong("Appearance", entityData.get(DATA_APPEARANCE));
        tag.putString("Archetype", personality.archetype().name());
        tag.putFloat("Courage", personality.courage());
        tag.putFloat("Reaction", personality.reactionSpeed());
        tag.putFloat("Patience", personality.patience());
        tag.putFloat("Skill", personality.skill());
        tag.putFloat("Aggression", personality.aggression());
        tag.putFloat("Cooperation", personality.cooperation());
        tag.putFloat("Risk", personality.riskTolerance());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int n = tag.getInt("Number");
        this.contestantNumber = n;
        this.entityData.set(DATA_NUMBER, n);
        this.entityData.set(DATA_APPEARANCE, tag.getLong("Appearance"));
        Personality.Archetype arch;
        try {
            arch = Personality.Archetype.valueOf(tag.getString("Archetype"));
        } catch (IllegalArgumentException e) {
            arch = Personality.Archetype.ROOKIE;
        }
        this.personality = new Personality(arch, tag.getFloat("Courage"), tag.getFloat("Reaction"),
                tag.getFloat("Patience"), tag.getFloat("Skill"), tag.getFloat("Aggression"),
                tag.getFloat("Cooperation"), tag.getFloat("Risk"));
        this.rng = new Rng(getUUID().getLeastSignificantBits());
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "locomotion", 6, this::locomotionState)
                .setAnimationSpeedHandler(this::locomotionSpeed));
        AnimationController<ContestantEntity> action = new AnimationController<>(this, "action", 4, this::actionState);
        // one-shot gestures (names are the short names used by triggerAction)
        for (String[] a : ONE_SHOTS) {
            action.triggerableAnim(a[0], RawAnimation.begin().thenPlay(Anims.c(a[1])));
        }
        controllers.add(action);
    }

    /** short trigger name -> animation (without the "animation.contestant." prefix). */
    private static final String[][] ONE_SHOTS = {
            {"stumble", "stumble"}, {"lose_balance", "lose_balance"}, {"shocked", "shocked"}, {"relieved", "relieved"},
            {"wave", "wave"}, {"point", "point"}, {"nod", "nod"}, {"shake_head", "shake_head"}, {"interact", "interact"},
            {"pull_heave", "pull_heave"}, {"pull_slip", "pull_slip"}, {"dalgona_lick", "dalgona_lick"},
            {"dalgona_crack", "dalgona_crack"}, {"dalgona_success", "dalgona_success"}, {"marble_guess", "marble_guess"},
            {"marble_reveal", "marble_reveal"}, {"marble_release", "marble_throw_release"}, {"bridge_step", "bridge_step"},
            {"punch_left", "punch_left"}, {"punch_right", "punch_right"}, {"shove", "shove"},
            {"dodge_left", "dodge_left"}, {"dodge_right", "dodge_right"}, {"knocked_back", "knocked_back"},
            {"jump_leap", "jump_leap"}, {"land", "land"}, {"turn_left", "turn_left"}, {"turn_right", "turn_right"},
    };

    private PlayState locomotionState(AnimationState<ContestantEntity> state) {
        double v = horizontalSpeed();
        boolean airborne = !onGround() && !isInWater();
        double vy = getDeltaMovement().y;
        if (airborne && vy < -0.55) {
            return state.setAndContinue(RawAnimation.begin().thenLoop(Anims.C_FALL_LOOP));
        }
        if (v > RUN_THRESHOLD) {
            wasRunning = true;
            skidTicks = 0;
            return state.setAndContinue(RawAnimation.begin().thenLoop(Anims.C_RUN));
        }
        if (v > MOVE_THRESHOLD) {
            wasRunning = false;
            return state.setAndContinue(RawAnimation.begin().thenLoop(
                    idleStyle() == 1 && entityData.get(DATA_ANIM_SPEED) < 0.9f ? Anims.C_SNEAK_WALK : Anims.C_WALK));
        }
        if (wasRunning) {
            wasRunning = false;
            skidTicks = 12;
        }
        if (skidTicks > 0) {
            skidTicks--;
            return state.setAndContinue(RawAnimation.begin().then(Anims.C_STOP_SKID, software.bernie.geckolib.animation.Animation.LoopType.PLAY_ONCE)
                    .thenLoop(idleAnim()));
        }
        return state.setAndContinue(RawAnimation.begin().thenLoop(idleAnim()));
    }

    private String idleAnim() {
        return switch (idleStyle()) {
            case 1 -> Anims.C_IDLE_NERVOUS;
            case 2 -> Anims.C_IDLE_CONFIDENT;
            default -> Anims.C_IDLE;
        };
    }

    private double locomotionSpeed(ContestantEntity e) {
        double v = e.horizontalSpeed();
        if (v > RUN_THRESHOLD) {
            return Mth.clamp(v / RUN_REF_SPEED, 0.6, 2.0);
        }
        if (v > MOVE_THRESHOLD) {
            return Mth.clamp(v / WALK_REF_SPEED, 0.5, 2.0);
        }
        return 1.0;
    }

    private PlayState actionState(AnimationState<ContestantEntity> state) {
        Activity a = getActivity();
        if (a == Activity.NONE || a.animation == null) {
            return PlayState.STOP;
        }
        RawAnimation anim = a.loops
                ? RawAnimation.begin().thenLoop(a.animation)
                : RawAnimation.begin().thenPlayAndHold(a.animation);
        return state.setAndContinue(anim);
    }

    public static boolean isLive(@Nullable LivingEntity e) {
        return e != null && e.isAlive() && !e.isRemoved();
    }

    /** Server-level convenience. */
    public ServerLevel serverLevel() {
        return (ServerLevel) level();
    }
}
