package com.squidgame.core.dalgona;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;

/**
 * The hands of an NPC contestant: it traces the outline with a simulated needle and feeds the result into its
 * {@link CookieSim} exactly like a human's validated strokes (same physics, same limits). It only uses what a human
 * at the table could see: the groove (and its fragile spots, shown to humans as caution marks), the carved parts, the
 * stress meter, the licks left and the clock - never the cookie's hidden rolls.
 *
 * <p>Personality shows in observable decisions:
 * <ul>
 *   <li>patient contestants carve slower and steadier, risk-takers and brave ones faster ({@link Profile#pace});</li>
 *   <li>skill (with the difficulty's opponent bonus) sets the hand tremor, how tightly the needle follows the groove
 *       and how far ahead the NPC looks to slow down for corners and thin spots;</li>
 *   <li>caution decides when to lick (early or only when in trouble), when to stop and let the stress drain, and how much
 *       the NPC dares when time runs out (a rush near the end is a gamble, as it is for humans);</li>
 *   <li>after a mishap (stress spike) it flinches, pauses for its reaction time and slows down for a while;</li>
 *   <li>it goes back and touches up stretches it missed.</li>
 * </ul>
 */
public final class NpcCarver {
    /** Personality (and difficulty) derived constants. */
    public static final class Profile {
        /** Fraction of the local safe speed the NPC aims for. */
        public final double pace;
        /** Standard deviation of the lateral hand tremor at its usual pace (canvas units). */
        public final double wobbleSigma;
        /** Probability per tick of a slip of the hand (a sudden lateral jerk): clumsier hands slip more often. */
        public final double slipRate;
        public final double wobbleTau;
        /** How slowly the needle follows the target point (ticks): lag makes it cut corners when going fast. */
        public final double trackTau;
        /** 0..1: how well it slows down for fragile spots that are still ahead. */
        public final double awareness;
        public final int lookAhead;
        public final double lickAt;
        public final double restAt;
        public final double restUntil;
        /** Highest speed (multiple of the nominal safe speed) it dares when time runs out. */
        public final double rush;
        public final int reaction;
        /** Ticks it carves slowly after a mishap. */
        public final int cautiousTicks;

        Profile(double pace, double wobbleSigma, double slipRate, double wobbleTau, double trackTau, double awareness,
                int lookAhead, double lickAt, double restAt, double restUntil, double rush, int reaction, int cautiousTicks) {
            this.pace = pace;
            this.wobbleSigma = wobbleSigma;
            this.slipRate = slipRate;
            this.wobbleTau = wobbleTau;
            this.trackTau = trackTau;
            this.awareness = awareness;
            this.lookAhead = lookAhead;
            this.lickAt = lickAt;
            this.restAt = restAt;
            this.restUntil = restUntil;
            this.rush = rush;
            this.reaction = reaction;
            this.cautiousTicks = cautiousTicks;
        }

        public static Profile of(Personality p, Difficulty d, Rng rng) {
            double skill = p.effectiveSkill(d);
            double caution = p.caution();
            double pace = clamp(0.85 + 0.55 * (p.riskTolerance() - 0.5) + 0.40 * (p.courage() - 0.5)
                    - 0.45 * (p.patience() - 0.5) + rng.gaussian(0, 0.05), 0.4, 1.8);
            double sigma = lerp(11, 3, skill) * lerp(1.15, 0.85, p.patience());
            double slip = SLIP_BASE * (1.15 - skill) * lerp(1.25, 0.8, p.patience());
            return new Profile(pace, sigma, slip, lerp(5, 11, p.patience()), lerp(3.2, 1.6, skill),
                    clamp(0.25 + 0.75 * skill + rng.gaussian(0, 0.05), 0, 1), (int) Math.round(3 + 12 * skill),
                    lerp(72, 32, caution), lerp(86, 52, caution), lerp(40, 18, caution), lerp(2.0, 1.25, caution),
                    p.reactionDelayTicks(rng, d), (int) Math.round(30 + 70 * p.patience()));
        }
    }

    public enum State {
        /** The needle is down and moving. */
        CARVING,
        /** Needle lifted: looking at the cookie, reacting to a mishap, or travelling to the next uncarved stretch. */
        PAUSED,
        /** Needle lifted, letting the stress drain. */
        RESTING,
        /** The lick animation is running (the contestant just licked or is still licking). */
        LICKING,
        DONE,
        CRACKED
    }

    /** What the NPC did in this step (for body language and sounds). */
    public record Step(State state, boolean lickStarted, boolean spike, double stressJump, double needleX, double needleY) {
    }

    /** Slips per tick for a hand of skill 0.15 (it scales down with skill). */
    private static final double SLIP_BASE = 0.0072;
    /** A slip jerks the needle sideways by this many canvas units (scaled by the speed it happens at). */
    private static final double SLIP_MIN = 21;
    private static final double SLIP_MAX = 54;
    /** Needle travel speed while lifted (units per tick), far below {@link StrokeValidator#MAX_LIFT_SPEED}. */
    private static final double LIFT_SPEED = 35;


    private final DalgonaShape shape;
    private final CookieSim sim;
    private final Profile prof;
    private final Rng rng;
    private final double[] pt = new double[4];
    private final double[] oneX = new double[1];
    private final double[] oneY = new double[1];

    private double cursor;
    private int dir;
    private double wobble;
    private double needleX;
    private double needleY;
    private boolean down;
    private boolean fresh = true;
    private double pause;
    private boolean resting;
    private int cautious;
    /** Samples covered since the start: after one full loop the NPC goes back to touch up what it missed. */
    private double covered;

    public NpcCarver(DalgonaShape shape, CookieSim sim, Profile profile, Rng rng) {
        this.shape = shape;
        this.sim = sim;
        this.prof = profile;
        this.rng = rng;
        // start somewhere near the top of the figure (where most people begin), heading either way
        int n = shape.sampleCount();
        int best = 0;
        for (int i = 1; i < n; i++) {
            if (shape.sampleY(i) < shape.sampleY(best)) {
                best = i;
            }
        }
        this.cursor = best + rng.range(-n * 0.04, n * 0.04);
        this.dir = rng.nextBoolean() ? 1 : -1;
    }

    public Profile profile() {
        return prof;
    }

    /** A fright (a neighbour's cookie shattered, a shot rang out): the hand freezes for a moment, then carves more carefully. */
    public void startle(int pauseTicks, int cautiousTicks) {
        pause = Math.max(pause, pauseTicks);
        cautious = Math.max(cautious, cautiousTicks);
    }

    /**
     * Takes over a cookie somebody else has been carving (a disconnected player's stand-in): continues at the uncarved
     * sample closest to the needle and goes back over whatever is still missing.
     */
    public void resumeNear(double x, double y) {
        int n = shape.sampleCount();
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (!sim.isCarved(i)) {
                double d = Math.hypot(shape.sampleX(i) - x, shape.sampleY(i) - y);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
        }
        if (best >= 0) {
            cursor = best;
            dir = rng.nextBoolean() ? 1 : -1;
        }
        covered = n;
        fresh = true;
        pause = prof.reaction;
    }

    public boolean needleDown() {
        return down;
    }

    /**
     * Advances the NPC by {@code ticks} ticks, one tick at a time so that the physics does not depend on how often the
     * NPC is stepped ({@code ticksLeft}: what is left on the clock). Does not call {@link CookieSim#tick()}: the game
     * ticks every cookie once per server tick.
     */
    public Step step(int ticks, double ticksLeft) {
        Step last = null;
        boolean lick = false;
        boolean spike = false;
        double jump = 0;
        for (int i = 0; i < Math.max(1, ticks); i++) {
            last = stepOne(ticksLeft - i);
            lick |= last.lickStarted();
            spike |= last.spike();
            jump += last.stressJump();
            if (last.state() == State.DONE || last.state() == State.CRACKED) {
                break;
            }
        }
        return new Step(last.state(), lick, spike, jump, last.needleX(), last.needleY());
    }

    private Step stepOne(double ticksLeft) {
        if (sim.isDone()) {
            return finish(State.DONE);
        }
        if (sim.isCracked()) {
            return finish(State.CRACKED);
        }
        if (sim.lickLock() > 0) {
            down = false;
            fresh = true;
            return new Step(State.LICKING, false, false, 0, needleX, needleY);
        }
        double stress = sim.stress();
        double nominal = sim.params().safeSpeed();
        double remaining = Math.max(0, sim.needed() - sim.carvedCount()) * shape.sampleStep();
        double usable = Math.max(10, ticksLeft - 30);
        double required = remaining / usable;
        boolean pressed = required > prof.pace * nominal * 0.9;

        if (pause > 0) {
            pause -= 1;
            down = false;
            fresh = true;
            return new Step(State.PAUSED, false, false, 0, needleX, needleY);
        }
        // licking: when the stress gets too high, or beforehand when something nasty is coming up
        if (sim.canLick()) {
            double ahead = fragilityAhead();
            boolean pre = stress >= 0.75 * prof.lickAt && ahead >= 2.0 && prof.awareness > 0.6;
            if (stress >= prof.lickAt || pre) {
                sim.lick();
                down = false;
                fresh = true;
                pause = prof.reaction;
                return new Step(State.LICKING, true, false, 0, needleX, needleY);
            }
        }
        // no lick left: stop and let the stress drain, unless the clock does not allow it
        if (!resting && stress >= prof.restAt && !sim.canLick() && !pressed) {
            resting = true;
        }
        if (resting) {
            if (stress <= prof.restUntil || pressed) {
                resting = false;
            } else {
                down = false;
                fresh = true;
                return new Step(State.RESTING, false, false, 0, needleX, needleY);
            }
        }
        return carve(required, nominal);
    }

    private Step carve(double required, double nominal) {
        int here = shape.sampleAtArc(cursor);
        // after the first loop: lift the needle and move on to the next stretch that was missed
        if (covered >= shape.sampleCount() && sim.isCarved(here) && sim.isCarved(here + dir) && sim.isCarved(here + 2 * dir)) {
            int target = nearestUncarved(here);
            if (target >= 0) {
                double dist = Math.hypot(shape.sampleX(target) - needleX, shape.sampleY(target) - needleY);
                int n = shape.sampleCount();
                int fwd = ((target - here) % n + n) % n;
                dir = fwd <= n - fwd ? 1 : -1;
                cursor = target;
                pause = 2 + dist / LIFT_SPEED;
                down = false;
                fresh = true;
                return new Step(State.PAUSED, false, false, 0, needleX, needleY);
            }
        }
        double fragHere = shape.fragility(here);
        double fragEff = fragHere + prof.awareness * Math.max(0, fragilityAhead() - fragHere);
        double safe = sim.params().localSafeSpeed(fragEff);
        double speed = prof.pace * safe;
        if (cautious > 0) {
            cautious--;
            speed *= 0.55;
        }
        if (required > speed) {
            // out of time: speed up, ignoring the caution zones, as far as it dares
            speed = Math.min(required * 1.1, prof.rush * nominal);
        }
        cursor += dir * speed / shape.sampleStep();
        covered += speed / shape.sampleStep();
        pointAt(cursor);
        double sigma = prof.wobbleSigma * (0.55 + 0.45 * Math.min(2.0, speed / (0.8 * nominal)));
        double a = Math.exp(-1.0 / prof.wobbleTau);
        wobble = wobble * a + sigma * Math.sqrt(1 - a * a) * rng.gaussian();
        double tx = pt[0] + pt[2] * wobble;
        double ty = pt[1] + pt[3] * wobble;
        if (fresh) {
            needleX = tx;
            needleY = ty;
        } else {
            double k = 1 - Math.exp(-1.0 / prof.trackTau);
            needleX += (tx - needleX) * k;
            needleY += (ty - needleY) * k;
        }
        // hands slip more often the harder the movement is: the chance grows with the square of speed over the local limit
        double effort = Math.max(0.3, Math.min(2.5, speed / safe));
        if (rng.chance(prof.slipRate * (0.7 + 0.3 * fragEff) * effort * effort)) {
            // the hand slips: the needle jerks sideways at once and needs a moment to come back
            double jerk = (rng.nextBoolean() ? 1 : -1) * rng.range(SLIP_MIN, SLIP_MAX) * (0.8 + 0.4 * Math.min(2.0, speed / nominal));
            needleX += pt[2] * jerk;
            needleY += pt[3] * jerk;
            wobble += jerk * 0.4;
        }
        needleX = clamp(needleX, 0, DalgonaShape.MAX_COORD);
        needleY = clamp(needleY, 0, DalgonaShape.MAX_COORD);
        oneX[0] = needleX;
        oneY[0] = needleY;
        CookieSim.Result r = sim.stroke(oneX, oneY, 1, 1.0, fresh);
        fresh = false;
        down = true;
        boolean mishap = r.spike || r.stressDelta() > 9;
        if (mishap) {
            pause = prof.reaction + rng.nextInt(6);
            cautious = prof.cautiousTicks;
        }
        State st = r.outcome == CookieSim.Outcome.DONE ? State.DONE
                : r.outcome == CookieSim.Outcome.CRACKED ? State.CRACKED : State.CARVING;
        return new Step(st, false, r.spike, r.stressDelta(), needleX, needleY);
    }

    private Step finish(State s) {
        down = false;
        return new Step(s, false, false, 0, needleX, needleY);
    }

    /** Highest fragility within the NPC's look-ahead along its direction of travel. */
    private double fragilityAhead() {
        double worst = 1.0;
        for (int k = 0; k <= prof.lookAhead; k += 2) {
            worst = Math.max(worst, shape.fragility((int) Math.round(cursor) + dir * k));
        }
        return worst;
    }

    /** The uncarved sample closest (along the outline) to {@code from}, or -1 when everything is carved. */
    private int nearestUncarved(int from) {
        int n = shape.sampleCount();
        for (int k = 1; k <= n / 2 + 1; k++) {
            int a = ((from + k) % n + n) % n;
            int b = ((from - k) % n + n) % n;
            boolean first = dir > 0;
            int c1 = first ? a : b;
            int c2 = first ? b : a;
            if (!sim.isCarved(c1)) {
                return c1;
            }
            if (!sim.isCarved(c2)) {
                return c2;
            }
        }
        return -1;
    }

    private void pointAt(double c) {
        int n = shape.sampleCount();
        double cc = ((c % n) + n) % n;
        int i = (int) Math.floor(cc);
        double t = cc - i;
        int j = (i + 1) % n;
        pt[0] = shape.sampleX(i) + (shape.sampleX(j) - shape.sampleX(i)) * t;
        pt[1] = shape.sampleY(i) + (shape.sampleY(j) - shape.sampleY(i)) * t;
        int a = ((i - 2) % n + n) % n;
        int b = (i + 3) % n;
        double tx = shape.sampleX(b) - shape.sampleX(a);
        double ty = shape.sampleY(b) - shape.sampleY(a);
        double len = Math.hypot(tx, ty);
        if (len < 1e-9) {
            pt[2] = 0;
            pt[3] = 1;
        } else {
            pt[2] = -ty / len;
            pt[3] = tx / len;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
