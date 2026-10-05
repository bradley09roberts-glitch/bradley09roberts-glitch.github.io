package com.squidgame.core.finale;

/**
 * Ground movement of a body that is steered by code instead of a keyboard: the headless duel simulator and the live
 * NPC bodies both move with it, so a simulated duel plays like a visible one. Own movement approaches the wished
 * velocity with a limited acceleration; knock-back is a separate velocity that decays with the ground friction (the
 * same way a vanilla entity slides after a hit); a dash overrides own movement for a few ticks.
 */
public final class Motion {
    /** Blocks per tick squared the own velocity can change by (about 2.5 ticks from standstill to walking speed). */
    private static final double ACCEL = 0.09;
    private static final double DECEL = 0.14;

    private double vx, vz;
    private double ex, ez;
    private int dashLeft;
    private double dashX, dashZ;

    /** Adds a knock-back impulse (blocks per tick). */
    public void impulse(double ix, double iz) {
        ex += ix;
        ez += iz;
    }

    /** Starts a dash: the body moves with this velocity for {@code ticks} ticks. */
    public void dash(double dx, double dz, int ticks) {
        dashX = dx;
        dashZ = dz;
        dashLeft = ticks;
    }

    public boolean dashing() {
        return dashLeft > 0;
    }

    /** Velocity of the dash in progress (blocks per tick), or null. */
    public double[] dashVelocity() {
        return dashLeft > 0 ? new double[]{dashX, dashZ} : null;
    }

    /** Ends the dash early (it would carry the body over the line). */
    public void cancelDash() {
        dashLeft = 0;
    }

    /** Clears every velocity (teleports, the start of a duel). */
    public void reset() {
        vx = vz = ex = ez = 0;
        dashLeft = 0;
    }

    /** The displacement (blocks) this tick for the wished velocity (blocks per tick). Returns {dx, dz}. */
    public double[] step(double wishX, double wishZ) {
        double dx, dz;
        if (dashLeft > 0) {
            dashLeft--;
            vx = dashX * 0.5;
            vz = dashZ * 0.5;
            dx = dashX + ex;
            dz = dashZ + ez;
        } else {
            double ax = wishX - vx, az = wishZ - vz;
            double len = Math.hypot(ax, az);
            double limit = Math.hypot(wishX, wishZ) < Math.hypot(vx, vz) ? DECEL : ACCEL;
            if (len > limit) {
                ax *= limit / len;
                az *= limit / len;
            }
            vx += ax;
            vz += az;
            dx = vx + ex;
            dz = vz + ez;
        }
        ex *= FinaleRules.FRICTION;
        ez *= FinaleRules.FRICTION;
        if (Math.abs(ex) < 1e-4) {
            ex = 0;
        }
        if (Math.abs(ez) < 1e-4) {
            ez = 0;
        }
        return new double[]{dx, dz};
    }

    /** Current own velocity magnitude plus knock-back (blocks per tick). */
    public double speed() {
        return Math.hypot(vx + ex, vz + ez);
    }
}
