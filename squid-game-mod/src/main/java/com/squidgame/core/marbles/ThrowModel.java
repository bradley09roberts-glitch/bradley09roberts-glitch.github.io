package com.squidgame.core.marbles;

import com.squidgame.core.util.Rng;

/**
 * The physics and the controls of a marble throw, shared by the server (which launches the projectile), the NPCs
 * (which plan their throws with it) and the client (which draws the charge bar with the sweet spot).
 *
 * <p><b>Controls.</b> You look at the spot on the ground where the marble should land; holding the use key charges
 * the throw, and the charge sets the launch speed ({@link Params#minSpeed} .. {@link Params#maxSpeed}). The marble
 * always leaves the hand on a fixed lob angle ({@link Params#arcPitchDeg}) towards the aimed spot, so the spot you
 * look at is exactly where it lands if you release at the {@link #idealCharge ideal charge}. Releasing early or late
 * drops it short or throws it long; a small random tremor (yaw and speed) keeps even a perfect throw from being
 * a certainty.
 *
 * <p><b>Flight.</b> {@link #landing} integrates the same motion as vanilla's {@code ThrowableProjectile}: the position
 * advances by the velocity, then the velocity is multiplied by the air drag (0.99) and loses 0.03 per tick to
 * gravity, and the marble comes to rest where the swept path crosses the floor plane.
 */
public final class ThrowModel {
    public static final double GRAVITY = 0.03;
    /** Vanilla stores the drag as a float: 0.99F widened to double. */
    public static final double DRAG = (double) 0.99F;
    public static final int MAX_FLIGHT_TICKS = 400;
    /** Aim points closer than this to the thrower are pushed out (a marble cannot be dropped at the feet). */
    public static final double MIN_AIM_RANGE = 1.5;

    private ThrowModel() {
    }

    /** A plain 3D vector (this package has no Minecraft dependency). */
    public record Vec(double x, double y, double z) {
        public double horizontalDistance(Vec o) {
            return Math.hypot(x - o.x, z - o.z);
        }
    }

    /**
     * @param maxChargeTicks ticks of holding that give full power (shorter = harder timing)
     * @param minChargeTicks a release before this is a cancelled throw, not a weak one
     * @param minSpeed       launch speed at zero charge (blocks per tick)
     * @param maxSpeed       launch speed at full charge
     * @param arcPitchDeg    fixed launch elevation
     * @param jitterYawDeg   standard deviation of the random sideways tremor
     * @param jitterSpeed    standard deviation of the random relative speed tremor
     */
    public record Params(int maxChargeTicks, int minChargeTicks, double minSpeed, double maxSpeed, double arcPitchDeg,
                         double jitterYawDeg, double jitterSpeed) {
    }

    /** Where a flight ends: the point on the floor plane and the (fractional) tick of the impact. */
    public record Landing(double x, double y, double z, double ticks) {
    }

    // ------------------------------------------------------------------ charge <-> speed

    public static double chargeFraction(Params p, double chargeTicks) {
        return Rng.clamp01(chargeTicks / p.maxChargeTicks());
    }

    public static double speed(Params p, double chargeTicks) {
        return p.minSpeed() + (p.maxSpeed() - p.minSpeed()) * chargeFraction(p, chargeTicks);
    }

    /** Inverse of {@link #speed}: the (fractional, unclamped) charge that produces {@code speed}. */
    public static double chargeFor(Params p, double speed) {
        return (speed - p.minSpeed()) / (p.maxSpeed() - p.minSpeed()) * p.maxChargeTicks();
    }

    // ------------------------------------------------------------------ flight

    /**
     * Simulates the flight from {@code from} with {@code velocity} until the path crosses the floor plane
     * {@code y = floorY}. A marble that never gets there (cannot happen with gravity) is cut after {@link #MAX_FLIGHT_TICKS}.
     */
    public static Landing landing(Vec from, Vec velocity, double floorY) {
        double x = from.x(), y = from.y(), z = from.z();
        double vx = velocity.x(), vy = velocity.y(), vz = velocity.z();
        if (y <= floorY) {
            return new Landing(x, floorY, z, 0);
        }
        for (int tick = 0; tick < MAX_FLIGHT_TICKS; tick++) {
            double ny = y + vy;
            if (ny <= floorY) {
                double t = (y - floorY) / (y - ny);
                return new Landing(x + vx * t, floorY, z + vz * t, tick + t);
            }
            x += vx;
            y = ny;
            z += vz;
            vx *= DRAG;
            vy = vy * DRAG - GRAVITY;
            vz *= DRAG;
        }
        return new Landing(x, floorY, z, MAX_FLIGHT_TICKS);
    }

    /** Launch velocity towards {@code aim} (horizontal direction) on the fixed arc, at {@code speed}, yawed by the tremor. */
    public static Vec velocity(Params p, Vec from, Vec aim, double speed, double yawJitterDeg) {
        double dx = aim.x() - from.x(), dz = aim.z() - from.z();
        double yaw = Math.hypot(dx, dz) < 1.0E-6 ? 0.0 : Math.atan2(dz, dx);
        yaw += Math.toRadians(yawJitterDeg);
        double pitch = Math.toRadians(p.arcPitchDeg());
        double h = Math.cos(pitch) * speed;
        return new Vec(Math.cos(yaw) * h, Math.sin(pitch) * speed, Math.sin(yaw) * h);
    }

    /** The launch speed whose flight lands exactly on {@code aim} (the floor plane is {@code aim.y()}). */
    public static double idealSpeed(Params p, Vec from, Vec aim) {
        double range = from.horizontalDistance(aim);
        double lo = 0.02, hi = 4.0;
        for (int i = 0; i < 48; i++) {
            double mid = 0.5 * (lo + hi);
            Vec v = velocity(p, from, aim, mid, 0);
            Landing l = landing(from, v, aim.y());
            if (Math.hypot(l.x() - from.x(), l.z() - from.z()) < range) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    /** The (fractional) charge to release at for a throw that lands on {@code aim}; may lie outside [0, max]. */
    public static double idealCharge(Params p, Vec from, Vec aim) {
        return chargeFor(p, idealSpeed(p, from, aim));
    }

    /** Horizontal range of a full-power throw launched {@code height} blocks above the floor. */
    public static double maxRange(Params p, double height) {
        Vec from = new Vec(0, height, 0);
        Vec aim = new Vec(1, 0, 0);
        Landing l = landing(from, velocity(p, from, aim, p.maxSpeed(), 0), 0);
        return Math.hypot(l.x(), l.z());
    }

    /**
     * The spot on the floor a player is looking at, clamped to the throwable range. Looking at or above the horizon
     * (no floor under the crosshair) aims at the maximum range.
     *
     * @param eye   where the marble leaves the hand
     * @param look  the view direction (any length)
     * @param floorY the floor plane
     */
    public static Vec aimFromLook(Params p, Vec eye, Vec look, double floorY) {
        double hl = Math.hypot(look.x(), look.z());
        double ux = hl < 1.0E-6 ? 0.0 : look.x() / hl;
        double uz = hl < 1.0E-6 ? 1.0 : look.z() / hl;
        double max = maxRange(p, Math.max(0.2, eye.y() - floorY));
        double range = max;
        if (look.y() < -1.0E-4) {
            range = (eye.y() - floorY) / -look.y() * hl;
        }
        range = Rng.clamp(range, MIN_AIM_RANGE, max);
        return new Vec(eye.x() + ux * range, floorY, eye.z() + uz * range);
    }

    /**
     * The real launch velocity of a throw: speed from the charge, tremor applied when {@code rng} is given (a null
     * generator gives the exact, tremor-free launch used for the client's sweet-spot preview).
     */
    public static Vec launch(Params p, Vec from, Vec aim, double chargeTicks, Rng rng) {
        double speed = speed(p, chargeTicks);
        double yawJitter = 0;
        if (rng != null) {
            speed *= 1.0 + rng.gaussian(0, p.jitterSpeed());
            yawJitter = rng.gaussian(0, p.jitterYawDeg());
        }
        return velocity(p, from, aim, speed, yawJitter);
    }
}
