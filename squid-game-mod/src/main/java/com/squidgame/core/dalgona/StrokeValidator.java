package com.squidgame.core.dalgona;

/**
 * Server-side gatekeeper for the needle strokes a client sends (one instance per human contestant). Nothing the client
 * claims is trusted; a message is checked for
 * <ul>
 *   <li><b>rate</b>: a token bucket (about 1.5 messages per tick on average, bursts of {@value #BUCKET_CAP});</li>
 *   <li><b>size and bounds</b>: 1..{@value #MAX_POINTS} points, every coordinate inside the canvas;</li>
 *   <li><b>time</b>: the client states how long the message's motion took, but the server only credits what its own
 *       clock allows (credit accrues with the time that really passed on the server, which the caller passes in
 *       ticks of 50 ms; real time, not game ticks, so a lagging server does not punish an honest client), so a client
 *       can neither slow time down to hide a fast stroke nor bank more than {@value #CREDIT_CAP} ticks;</li>
 *   <li><b>speed</b>: a needle that is down may not move faster than {@value #MAX_PATH_SPEED} units per tick (the path
 *       is cut off at the allowed length and the cookie takes a penalty), a lifted needle not faster than
 *       {@value #MAX_LIFT_SPEED} units per tick between two strokes (a "teleport": penalty).</li>
 * </ul>
 * Repeated violations (strikes decay over time) finally mark the contestant as tampering. A well-behaved client
 * never trips any of this: the legitimate limit that matters is the safe speed of {@link CookieSim}, which cracks the
 * cookie long before these bounds are reached.
 */
public final class StrokeValidator {
    public static final int MAX_POINTS = 40;
    public static final double MAX_PATH_SPEED = 260.0;
    public static final double MAX_LIFT_SPEED = 700.0;
    /** Burst allowance (messages): a server hiccup makes seconds of an honest client's messages arrive at once. */
    public static final double BUCKET_CAP = 100.0;
    public static final double BUCKET_REFILL = 1.5;
    /** Most time (ticks) that can be banked; it lets the messages of a server hiccup be credited what they claim. */
    public static final double CREDIT_CAP = 100.0;
    /** Credit a fresh stroke starts with at most (ticks). */
    public static final double START_CREDIT = 4.0;
    public static final double MAX_CLAIM_TICKS = 12.0;
    public static final int STRIKE_LIMIT = 6;
    public static final int STRIKE_DECAY_TICKS = 100;
    public static final double TELEPORT_PENALTY = 25.0;

    public enum Reject {
        NONE, RATE, EMPTY, TOO_MANY, BOUNDS
    }

    /** The sanitised message (or the reason it was dropped). */
    public static final class Verdict {
        public boolean accepted;
        public Reject reject = Reject.NONE;
        public double[] xs = new double[0];
        public double[] ys = new double[0];
        public int n;
        /** Ticks the motion is credited with. */
        public double dt;
        public boolean newStroke;
        /** Stress to add for a violation (teleport, over-fast path); 0 for a clean message. */
        public double penalty;
        /** Too many recent violations: treat the contestant as tampering. */
        public boolean tamper;
    }

    private double bucket = BUCKET_CAP;
    private double credit;
    private long lastTick = -1;
    private long lastEndTick = -1000;
    private long lastStrikeTick;
    private int strikes;
    private boolean inStroke;

    /** Forgets stroke continuity (e.g. when control of the cookie changes hands). */
    public void reset() {
        inStroke = false;
        lastEndTick = -1000;
    }

    public int strikes() {
        return strikes;
    }

    /**
     * Checks one message. {@code packed} holds the points as {@code x << 10 | y}; {@code claimedMs} is how long the
     * motion took according to the client; {@code start}/{@code end}: the needle went down with / came up after this
     * message. {@code simHasNeedle}/{@code simX}/{@code simY} describe where the simulation thinks the needle is.
     */
    public Verdict check(long now, int[] packed, int n, int claimedMs, boolean start, boolean end,
                         boolean simHasNeedle, double simX, double simY) {
        Verdict v = new Verdict();
        long elapsed = lastTick < 0 ? 1 : Math.max(0, now - lastTick);
        lastTick = now;
        bucket = Math.min(BUCKET_CAP, bucket + elapsed * BUCKET_REFILL);
        credit = Math.min(CREDIT_CAP, credit + elapsed);
        if (strikes > 0 && now - lastStrikeTick > STRIKE_DECAY_TICKS) {
            strikes--;
            lastStrikeTick = now;
        }
        if (bucket < 1.0) {
            v.reject = Reject.RATE;
            return v;
        }
        bucket -= 1.0;
        if (packed == null || n < 1 || packed.length < 1) {
            v.reject = Reject.EMPTY;
            return v;
        }
        if (n > MAX_POINTS || packed.length < n) {
            v.reject = Reject.TOO_MANY;
            strike(v, now);
            return v;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            int x = (packed[i] >>> 10) & 0x3FFFFF;
            int y = packed[i] & 0x3FF;
            if (x > DalgonaShape.MAX_COORD || packed[i] < 0) {
                v.reject = Reject.BOUNDS;
                strike(v, now);
                return v;
            }
            xs[i] = x;
            ys[i] = y;
        }
        boolean newStroke = start || !inStroke;
        double claimed = Math.max(CookieSim.MIN_DT, Math.min(MAX_CLAIM_TICKS, claimedMs / 50.0));
        if (newStroke) {
            credit = Math.min(credit, START_CREDIT);
        }
        double usable = Math.min(claimed, Math.max(CookieSim.MIN_DT, credit));
        credit = Math.max(0, credit - usable);

        // a lifted needle that jumps to the start of a new stroke
        if (newStroke && simHasNeedle) {
            double gap = Math.max(1, now - lastEndTick);
            if (Math.hypot(xs[0] - simX, ys[0] - simY) / gap > MAX_LIFT_SPEED) {
                v.penalty += TELEPORT_PENALTY;
                strike(v, now);
            }
        }
        // the needle that is down may only move so fast: cut the path off at the allowed length
        double allowed = MAX_PATH_SPEED * usable;
        double px = simX;
        double py = simY;
        boolean have = !newStroke && simHasNeedle;
        double len = 0;
        int keep = n;
        for (int i = 0; i < n; i++) {
            if (have) {
                double seg = Math.hypot(xs[i] - px, ys[i] - py);
                if (len + seg > allowed) {
                    double t = seg <= 0 ? 0 : (allowed - len) / seg;
                    xs[i] = px + (xs[i] - px) * t;
                    ys[i] = py + (ys[i] - py) * t;
                    keep = i + 1;
                    v.penalty += TELEPORT_PENALTY;
                    strike(v, now);
                    break;
                }
                len += seg;
            }
            px = xs[i];
            py = ys[i];
            have = true;
        }
        v.accepted = true;
        v.xs = keep == n ? xs : java.util.Arrays.copyOf(xs, keep);
        v.ys = keep == n ? ys : java.util.Arrays.copyOf(ys, keep);
        v.n = keep;
        v.dt = usable;
        v.newStroke = newStroke;
        inStroke = !end && keep == n;
        if (end) {
            lastEndTick = now;
        }
        v.tamper = strikes >= STRIKE_LIMIT;
        return v;
    }

    private void strike(Verdict v, long now) {
        strikes++;
        lastStrikeTick = now;
        v.tamper = strikes >= STRIKE_LIMIT;
    }

    /** Packs canvas coordinates the way the client sends them. */
    public static int pack(int x, int y) {
        return (x << 10) | y;
    }
}
