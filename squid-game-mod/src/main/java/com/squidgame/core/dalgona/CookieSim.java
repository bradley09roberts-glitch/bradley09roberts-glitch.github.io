package com.squidgame.core.dalgona;

import com.squidgame.core.util.Rng;

/**
 * The server-side physics of one cookie, shared by human players (validated strokes) and NPCs (generated strokes).
 *
 * <p>State: stress 0..100 (the cookie cracks at 100), the carved samples of the outline, licks left, a lick
 * cooldown and a short lock while the contestant licks. Input: {@link #stroke} (the needle path of one message,
 * covering {@code dt} ticks), {@link #lick()} and {@link #tick()} (once per server tick).
 *
 * <p>What a stroke does, in order, for every 5-unit step of the path:
 * <ol>
 *   <li>within {@link DalgonaRules.Params#tolerance} of the groove the needle carves the samples around it (the
 *       shape is freed - DONE - as soon as enough samples are carved) and adds a little <b>wobble stress</b> that grows
 *       with the square of its distance from the groove, per unit of path length;</li>
 *   <li>further away the needle is cutting into the cookie: stress grows with the length cut, the distance beyond the
 *       corridor (capped), the fragility of the spot and the side (into the figure hurts more than into the waste);</li>
 * </ol>
 * and, once per message, <b>speed stress</b> {@code k (ratio-1)^2 dt} when the needle speed - or the rate at which
 * new groove is carved, which also stops a client from "teleporting" the needle along the outline with single
 * clicks - exceeds the local safe speed (the safe speed drops on fragile spots), plus a rare
 * <b>micro-fracture</b> that adds a stress spike (more likely when already stressed and when rushing).
 * {@link #tick()} drains stress slowly while the needle carves calmly and faster while it rests, and refills the
 * carve budget behind the carve-rate rule.
 *
 * <p>Deterministic: the only randomness is the micro-fracture roll, drawn from the generator passed in.
 */
public final class CookieSim {
    public static final double STRESS_MAX = 100.0;
    /** Shortest duration a message is credited with (two messages in one tick cannot both carry real time). */
    public static final double MIN_DT = 0.34;
    public static final double MAX_DT = 20.0;
    /** Needle path is evaluated every this many canvas units. */
    public static final double SUBSTEP = 5.0;
    /** Ticks without a stroke after which the needle counts as resting (fast stress decay). */
    public static final int REST_AFTER_TICKS = 3;
    /** Cutting into the surrounding waste is less harmful than cutting into the figure. */
    public static final double OUTSIDE_FACTOR = 0.6;
    /** Beyond this many tolerances away from the groove the cutting stress stops growing. */
    public static final double EXCESS_CAP = 3.0;
    /** Newly carved samples this close (in samples) to the needle's nearest sample count towards the carve rate. */
    public static final int RATE_WINDOW = 6;
    /** Groove length (units) that may be carved in a burst, e.g. by the dab of the needle touching down. */
    public static final double CARVE_BURST = 60.0;

    public enum Outcome {
        /** The stroke was applied and the cookie is intact. */
        CONTINUE,
        /** This stroke freed the shape. */
        DONE,
        /** This stroke cracked the cookie. */
        CRACKED,
        /** The stroke was not applied (cookie already finished, contestant licking). */
        IGNORED
    }

    /** What one {@link #stroke} call did. */
    public static final class Result {
        public double stressBefore;
        public double stressAfter;
        public int newlyCarved;
        /** Needle speed or carve rate relative to the local safe speed (1 = exactly at the limit). */
        public double speedRatio;
        /** A micro-fracture added a stress spike. */
        public boolean spike;
        /** At least part of the path cut into the cookie away from the groove. */
        public boolean offPath;
        public Outcome outcome = Outcome.CONTINUE;

        public double stressDelta() {
            return stressAfter - stressBefore;
        }
    }

    private static final class Acc {
        double fragSum;
        int fragN;
        int rate;
        final int[] near = new int[1];
    }

    private final DalgonaShape shape;
    private final DalgonaRules.Params p;
    private final Rng rng;
    private final long[] carved;
    private final int needed;
    private final double[] arc = new double[1];

    private int carvedCount;
    private double stress;
    private int licksLeft;
    private int lickCooldown;
    private int lickLock;
    private boolean cracked;
    private boolean done;
    private int ticksSinceStroke = 1000;
    private double needleX;
    private double needleY;
    private boolean hasNeedle;
    private int spikes;
    /** Leaky bucket for the carve rate: refills every tick with the safe speed, a burst allows the touch-down dab. */
    private double carveBudget = CARVE_BURST;

    public CookieSim(DalgonaShape shape, DalgonaRules.Params params, long seed) {
        this.shape = shape;
        this.p = params;
        this.rng = new Rng(seed);
        this.carved = new long[(shape.sampleCount() + 63) / 64];
        this.needed = params.samplesNeeded(shape);
        this.licksLeft = params.licks();
    }

    // ------------------------------------------------------------------ carving helper (shared with the client)

    /**
     * Marks the outline samples within {@code r} of (x, y) in {@code bits} and returns how many were new. When
     * {@code near} is not null, {@code near[0]} is incremented for every new sample within {@code window} samples
     * (circular index distance) of {@code centre}.
     */
    public static int carve(DalgonaShape shape, long[] bits, double x, double y, double r, int centre, int window, int[] near) {
        int n = shape.sampleCount();
        double r2 = r * r;
        int fresh = 0;
        for (int i = 0; i < n; i++) {
            double dx = shape.sampleX(i) - x;
            double dy = shape.sampleY(i) - y;
            if (dx * dx + dy * dy > r2) {
                continue;
            }
            long m = 1L << (i & 63);
            if ((bits[i >> 6] & m) != 0) {
                continue;
            }
            bits[i >> 6] |= m;
            fresh++;
            if (near != null) {
                int d = Math.abs(i - centre);
                d = Math.min(d, n - d);
                if (d <= window) {
                    near[0]++;
                }
            }
        }
        return fresh;
    }

    // ------------------------------------------------------------------ input

    /**
     * Applies one message of needle positions (canvas units) covering {@code dt} ticks. {@code newStroke} = the
     * needle was lifted before the first point (no cutting between the previous position and the first point).
     */
    public Result stroke(double[] xs, double[] ys, int n, double dt, boolean newStroke) {
        Result r = new Result();
        r.stressBefore = stress;
        if (n <= 0 || cracked || done) {
            r.outcome = Outcome.IGNORED;
            r.stressAfter = stress;
            return r;
        }
        if (lickLock > 0) {
            needleX = xs[n - 1];
            needleY = ys[n - 1];
            hasNeedle = true;
            r.outcome = Outcome.IGNORED;
            r.stressAfter = stress;
            return r;
        }
        dt = Math.max(MIN_DT, Math.min(MAX_DT, dt));
        // Time this message covers that no tick() has refilled the carve budget for yet: messages that a laggy server
        // delivers in a bunch (the ticks in between never ran) must not be judged as if they were all carved at once.
        carveBudget = Math.min(CARVE_BURST, carveBudget + p.safeSpeed() * Math.max(0.0, dt - ticksSinceStroke));
        ticksSinceStroke = 0;
        boolean have = hasNeedle && !newStroke;
        double px = needleX;
        double py = needleY;
        double pathLen = 0;
        {
            double ax = px;
            double ay = py;
            boolean h = have;
            for (int i = 0; i < n; i++) {
                if (h) {
                    pathLen += Math.hypot(xs[i] - ax, ys[i] - ay);
                }
                ax = xs[i];
                ay = ys[i];
                h = true;
            }
        }
        double speed = pathLen / dt;
        Acc acc = new Acc();
        boolean terminal = false;
        for (int i = 0; i < n && !terminal; i++) {
            double x = xs[i];
            double y = ys[i];
            if (!have) {
                terminal = point(x, y, 0, r, acc);
            } else {
                double segLen = Math.hypot(x - px, y - py);
                int m = Math.max(1, (int) Math.ceil(segLen / SUBSTEP));
                double sub = segLen / m;
                for (int j = 1; j <= m && !terminal; j++) {
                    double t = (double) j / m;
                    terminal = point(px + (x - px) * t, py + (y - py) * t, sub, r, acc);
                }
            }
            px = x;
            py = y;
            have = true;
        }
        needleX = xs[n - 1];
        needleY = ys[n - 1];
        hasNeedle = true;
        if (!terminal) {
            double fragAvg = acc.fragN > 0 ? acc.fragSum / acc.fragN : 1.0;
            double safe = p.localSafeSpeed(fragAvg);
            // carve rate: groove carved faster than the safe speed (after the burst allowance) counts as speeding,
            // which also stops single clicks "teleporting" the needle along the outline; fragile spots cost more
            double used = acc.rate * shape.sampleStep() * (p.safeSpeed() / safe);
            double carveRatio = 0;
            if (used <= carveBudget) {
                carveBudget -= used;
            } else {
                carveRatio = 1.0 + (used - carveBudget) / (p.safeSpeed() * dt);
                carveBudget = 0;
            }
            double ratio = Math.max(speed / safe, carveRatio);
            r.speedRatio = ratio;
            if (ratio > 1.0) {
                stress += p.speedStress() * (ratio - 1.0) * (ratio - 1.0) * dt * shape.brittleness;
            }
            double s = 0.3 + stress / STRESS_MAX;
            double chance = p.microChance() * dt * s * s * Math.pow(Math.max(1.0, ratio), 1.5);
            if (rng.chance(chance)) {
                stress += rng.range(p.microMin(), p.microMax());
                r.spike = true;
                spikes++;
            }
            if (stress >= STRESS_MAX) {
                stress = STRESS_MAX;
                cracked = true;
            }
        } else {
            // the message that finished (or broke) the cookie: report the ratio for the feedback anyway
            r.speedRatio = speed / p.localSafeSpeed(1.0);
        }
        r.stressAfter = stress;
        r.outcome = done ? Outcome.DONE : cracked ? Outcome.CRACKED : Outcome.CONTINUE;
        return r;
    }

    /** One evaluated step of the needle path. Returns true when the cookie was finished or cracked. */
    private boolean point(double x, double y, double subLen, Result r, Acc a) {
        double d = shape.nearest(x, y, arc);
        int idx = shape.sampleAtArc(arc[0]);
        double frag = shape.fragility(idx);
        double tol = p.tolerance();
        if (d <= 2 * tol) {
            a.fragSum += frag;
            a.fragN++;
        }
        if (d <= tol) {
            a.near[0] = 0;
            int c = carve(shape, carved, x, y, tol, idx, RATE_WINDOW, a.near);
            if (c > 0) {
                carvedCount += c;
                r.newlyCarved += c;
                a.rate += a.near[0];
                if (carvedCount >= needed) {
                    done = true;
                    return true;
                }
            }
            if (subLen > 0) {
                double f = d / tol;
                stress += p.wobbleStress() * f * f * subLen * shape.brittleness;
            }
        } else if (subLen > 0) {
            double excess = Math.min(d - tol, EXCESS_CAP * tol) / tol;
            double side = shape.contains(x, y) ? 1.0 : OUTSIDE_FACTOR;
            stress += (p.wobbleStress() + p.offPathStress() * Math.pow(excess, 1.5) * frag * side) * subLen * shape.brittleness;
            r.offPath = true;
            if (stress >= STRESS_MAX) {
                stress = STRESS_MAX;
                cracked = true;
                return true;
            }
        }
        return false;
    }

    /** Licks the cookie: removes stress, locks the needle for a moment. Returns false if not possible right now. */
    public boolean lick() {
        if (!canLick()) {
            return false;
        }
        licksLeft--;
        stress = Math.max(0, stress - p.lickRelief());
        lickCooldown = p.lickCooldownTicks();
        lickLock = p.lickLockTicks();
        return true;
    }

    public boolean canLick() {
        return !cracked && !done && licksLeft > 0 && lickCooldown == 0 && lickLock == 0;
    }

    /** Adds stress for a rule violation (e.g. a teleporting needle) and cracks the cookie when it overflows. */
    public void penalize(double amount) {
        if (cracked || done) {
            return;
        }
        stress += amount;
        if (stress >= STRESS_MAX) {
            stress = STRESS_MAX;
            cracked = true;
        }
    }

    /** Breaks the cookie right away (tampering). */
    public void crack() {
        if (!done) {
            stress = STRESS_MAX;
            cracked = true;
        }
    }

    /** Once per server tick: cooldowns and passive stress decay. */
    public void tick() {
        if (cracked || done) {
            return;
        }
        ticksSinceStroke++;
        if (lickCooldown > 0) {
            lickCooldown--;
        }
        if (lickLock > 0) {
            lickLock--;
        }
        carveBudget = Math.min(CARVE_BURST, carveBudget + p.safeSpeed());
        double decay = ticksSinceStroke >= REST_AFTER_TICKS ? p.restDecay() : p.carveDecay();
        stress = Math.max(0, stress - decay);
    }

    // ------------------------------------------------------------------ queries

    public DalgonaShape shape() {
        return shape;
    }

    public DalgonaRules.Params params() {
        return p;
    }

    public double stress() {
        return stress;
    }

    public int carvedCount() {
        return carvedCount;
    }

    public int sampleCount() {
        return shape.sampleCount();
    }

    public int needed() {
        return needed;
    }

    /** Fraction of the groove carved (0..1). */
    public double progress() {
        return carvedCount / (double) shape.sampleCount();
    }

    public int licksLeft() {
        return licksLeft;
    }

    public int lickCooldown() {
        return lickCooldown;
    }

    public int lickLock() {
        return lickLock;
    }

    public boolean isCracked() {
        return cracked;
    }

    public boolean isDone() {
        return done;
    }

    public boolean isResolved() {
        return cracked || done;
    }

    public boolean isCarved(int sample) {
        int n = shape.sampleCount();
        int i = ((sample % n) + n) % n;
        return (carved[i >> 6] & (1L << (i & 63))) != 0;
    }

    public long[] carvedBits() {
        return carved.clone();
    }

    public boolean hasNeedle() {
        return hasNeedle;
    }

    public double needleX() {
        return needleX;
    }

    public double needleY() {
        return needleY;
    }

    public int spikes() {
        return spikes;
    }

    /** Ticks since the last processed stroke. */
    public int ticksSinceStroke() {
        return ticksSinceStroke;
    }
}
