package com.squidgame.game.bridge;

import com.squidgame.SquidConfig;
import com.squidgame.SquidGameMod;
import com.squidgame.core.bridge.BridgeLayout;
import com.squidgame.core.bridge.HopPlanner;
import com.squidgame.entity.ContestantEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Low-level body control of one NPC on the bridge, run every server tick by the game (not throttled like the
 * behaviour's thinking). The vanilla mob move control is far too sloppy for a 2x2 panel above a 70 block drop, so the
 * motor drives the body directly:
 * <ul>
 *   <li><b>walking</b> sets the horizontal velocity every tick towards a target, slows down on arrival and clamps the
 *       step so the body can never leave the rectangle it was given (the panel it stands on);</li>
 *   <li><b>leaping</b> takes off with an up speed and re-aims the horizontal speed every tick at the landing point,
 *       using the number of ticks the vertical motion still needs ({@link HopPlanner}), so the touchdown is exact and a
 *       shove or a bump in flight is corrected; gravity and collisions stay vanilla.</li>
 * </ul>
 * Everything else (what to do, when, which lane) is the {@link BridgeNpcBehavior}'s business.
 */
final class BridgeMotor {
    enum Mode { IDLE, WALK, LEAP }

    /** What finished last; read (and cleared) with {@link #takeResult()}. */
    enum Result { NONE, ARRIVED, LANDED, FAILED }

    private static final double ARRIVE_EPS = 0.06;
    private static final float TURN_RATE = 32f;

    private Mode mode = Mode.IDLE;
    private Result result = Result.NONE;

    // walking
    private Vec3 target = Vec3.ZERO;
    private double speed;
    private BridgeLayout.Rect bounds;

    // leaping
    private BridgeLayout.Vec2 landing;
    private double landingTop;
    private int airTicks;
    private boolean wasAirborne;
    private int attempts;
    private double plannedDistance;
    private int plannedTicks;

    Mode mode() {
        return mode;
    }

    boolean busy() {
        return mode != Mode.IDLE;
    }

    Result takeResult() {
        Result r = result;
        result = Result.NONE;
        return r;
    }

    /** Error (blocks) between the planned and the actual touchdown of the last leap, for diagnostics. */
    double lastLandingError;

    /** Walks to {@code target} (x/z) at most {@code speed} blocks per tick, never leaving {@code bounds} (null = free). */
    void walkTo(Vec3 target, double speed, BridgeLayout.Rect bounds) {
        this.bounds = bounds;
        if (bounds != null) {
            BridgeLayout.Vec2 c = bounds.clamp(target.x, target.z);
            target = new Vec3(c.x(), target.y, c.z());
        }
        this.target = target;
        this.speed = speed;
        this.mode = Mode.WALK;
        this.result = Result.NONE;
    }

    /** Stops walking at once (a leap in progress is not interrupted). */
    void stop(ContestantEntity npc) {
        if (mode == Mode.WALK) {
            halt(npc);
            mode = Mode.IDLE;
        }
    }

    /** Starts a leap from where the body stands to {@code landing} on a surface at height {@code topY}. */
    void leapTo(ContestantEntity npc, BridgeLayout.Vec2 landing, double topY) {
        this.landing = landing;
        this.landingTop = topY;
        this.airTicks = 0;
        this.wasAirborne = false;
        this.attempts = 0;
        double dist = Math.hypot(landing.x() - npc.getX(), landing.z() - npc.getZ());
        HopPlanner.Plan plan = HopPlanner.plan(dist);
        this.plannedDistance = dist;
        this.plannedTicks = plan.ticks();
        this.mode = Mode.LEAP;
        this.result = Result.NONE;
        if (SquidConfig.get().debug) {
            SquidGameMod.LOGGER.info("[bridge] hop {} ({}, {}) -> ({}, {}): {} blocks, {} ticks, up {}", npc.number(),
                    String.format("%.2f", npc.getX()), String.format("%.2f", npc.getZ()),
                    String.format("%.2f", landing.x()), String.format("%.2f", landing.z()),
                    String.format("%.2f", dist), plan.ticks(), plan.upSpeed());
        }
        launch(npc, plan);
    }

    private void launch(ContestantEntity npc, HopPlanner.Plan plan) {
        double dx = landing.x() - npc.getX(), dz = landing.z() - npc.getZ();
        double d = Math.hypot(dx, dz);
        double s = d < 1e-6 ? 0 : plan.speed() / d;
        npc.setDeltaMovement(dx * s, plan.upSpeed(), dz * s);
        npc.hasImpulse = true;
        faceTowards(npc, landing.x(), landing.z(), 180f);
        npc.triggerAction("jump_leap");
    }

    /** Called once per server tick for every NPC with an active command. */
    void tick(ContestantEntity npc) {
        switch (mode) {
            case WALK -> tickWalk(npc);
            case LEAP -> tickLeap(npc);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ walking

    private void tickWalk(ContestantEntity npc) {
        double dx = target.x - npc.getX(), dz = target.z - npc.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist < ARRIVE_EPS) {
            halt(npc);
            mode = Mode.IDLE;
            result = Result.ARRIVED;
            return;
        }
        double step = Math.min(speed, Math.max(0.012, dist * 0.55));
        step = Math.min(step, dist);
        double vx = dx / dist * step, vz = dz / dist * step;
        if (bounds != null) {
            // never step out of the allowed rectangle, whatever pushed us
            double nx = Mth.clamp(npc.getX() + vx, bounds.minX(), bounds.maxX());
            double nz = Mth.clamp(npc.getZ() + vz, bounds.minZ(), bounds.maxZ());
            vx = nx - npc.getX();
            vz = nz - npc.getZ();
        }
        Vec3 v = npc.getDeltaMovement();
        npc.setDeltaMovement(vx, v.y, vz);
        if (dist > 0.12) {
            faceTowards(npc, target.x, target.z, TURN_RATE);
        }
    }

    // ------------------------------------------------------------------ leaping

    private void tickLeap(ContestantEntity npc) {
        airTicks++;
        if (!npc.onGround()) {
            wasAirborne = true;
            Vec3 v = npc.getDeltaMovement();
            double rx = landing.x() - npc.getX(), rz = landing.z() - npc.getZ();
            double rd = Math.hypot(rx, rz);
            int remaining = HopPlanner.remainingTicks(v.y, npc.getY() - landingTop);
            double s = Math.min(HopPlanner.MAX_SPEED * 1.25, rd / remaining);
            if (rd < 1e-4) {
                npc.setDeltaMovement(0, v.y, 0);
            } else {
                npc.setDeltaMovement(rx / rd * s, v.y, rz / rd * s);
            }
            npc.hasImpulse = true;
            if (airTicks > 90) {
                mode = Mode.IDLE;
                result = Result.FAILED;
            }
            return;
        }
        if (wasAirborne) {
            // touched down
            lastLandingError = Math.hypot(landing.x() - npc.getX(), landing.z() - npc.getZ());
            halt(npc);
            mode = Mode.IDLE;
            result = Result.LANDED;
            if (SquidConfig.get().debug) {
                SquidGameMod.LOGGER.info("[bridge] landed {} at ({}, {}): error {} blocks after {} ticks (planned {})", npc.number(),
                        String.format("%.2f", npc.getX()), String.format("%.2f", npc.getZ()),
                        String.format("%.3f", lastLandingError), airTicks, plannedTicks);
            }
            if (lastLandingError > 0.6) {
                SquidGameMod.LOGGER.warn("Glass bridge: {} landed {} blocks from the aim point (planned {} blocks in {} ticks, took {})",
                        npc.getDisplayName().getString(), String.format("%.2f", lastLandingError),
                        String.format("%.2f", plannedDistance), plannedTicks, airTicks);
            }
            return;
        }
        if (airTicks >= 2) {
            // never left the ground: try once more, then give up
            if (++attempts > 1) {
                mode = Mode.IDLE;
                result = Result.FAILED;
                return;
            }
            airTicks = 0;
            launch(npc, HopPlanner.plan(Math.hypot(landing.x() - npc.getX(), landing.z() - npc.getZ())));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void halt(ContestantEntity npc) {
        Vec3 v = npc.getDeltaMovement();
        npc.setDeltaMovement(0, v.y, 0);
    }

    /** Turns the whole body towards a point, at most {@code maxStep} degrees this tick. */
    static void faceTowards(ContestantEntity npc, double x, double z, float maxStep) {
        double dx = x - npc.getX(), dz = z - npc.getZ();
        if (dx * dx + dz * dz < 1e-6) {
            return;
        }
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float diff = Mth.wrapDegrees(want - npc.getYRot());
        npc.faceYaw(npc.getYRot() + Mth.clamp(diff, -maxStep, maxStep));
    }
}
