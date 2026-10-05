package com.squidgame.core.finale;

/**
 * The time warp of a ceremony. A duel that nobody fights live is simulated headless and then shown on the court in a
 * few seconds: this maps the clock of the show onto the clock of the duel so that the walking and circling pass
 * quickly while every blow, block, dodge and the finish is seen at about natural speed (never slower than it was).
 *
 * <p>Every simulated tick has a weight: 1 near an event (the swing and its impact, a dodge, a block...) and in the last
 * moments of the duel, a small fraction elsewhere. The show advances evenly through the accumulated weight.
 */
public final class Replay {
    /** Ticks before and after an event that are shown at natural speed (the wind-up is part of the blow). */
    static final int BEFORE = 6, AFTER = 4;
    /** The last ticks of the duel (the finishing blow, the push, the last moments in the circle) are shown at natural speed. */
    static final int FINISH = 16;
    /** Weight of a tick in which nothing happens: such stretches pass 20 times faster than the blows. */
    static final double IDLE = 1.0 / 20.0;
    /** The blows are never shown faster than this (a busy duel makes the show longer instead). */
    public static final double MAX_SPEED = 4.0;

    private final int ticks;
    private final int showTicks;
    /** Accumulated weight at the start of every simulated tick (and at the end of the last one). */
    private final double[] weight;
    private final double speed;

    /**
     * @param result    the simulated duel
     * @param showTicks the length of the show in server ticks; a duel with little in it is shown in less, a duel with
     *                  so much in it that the blows would have to be shown faster than {@link #MAX_SPEED} in more
     */
    public Replay(DuelSimulator.Result result, int showTicks) {
        this.ticks = Math.max(1, result.ticks());
        this.showTicks = Math.max(1, showTicks);
        boolean[] hot = new boolean[ticks + 1];
        for (DuelSimulator.Timed t : result.log()) {
            if (!isBlow(t.event().type())) {
                continue;
            }
            for (int k = t.tick() - BEFORE; k <= t.tick() + AFTER; k++) {
                if (k >= 0 && k <= ticks) {
                    hot[k] = true;
                }
            }
        }
        for (int k = Math.max(0, ticks - FINISH); k <= ticks; k++) {
            hot[k] = true;
        }
        weight = new double[ticks + 1];
        for (int t = 0; t < ticks; t++) {
            weight[t + 1] = weight[t] + (hot[t] ? 1.0 : IDLE);
        }
        // never slower than real time (a short duel is over before the show is) and never faster than MAX_SPEED
        this.speed = Math.max(1.0, Math.min(MAX_SPEED, weight[ticks] / this.showTicks));
    }

    private static boolean isBlow(CombatEvent.Type type) {
        return switch (type) {
            case GUARD_UP, GUARD_DOWN, RECOVERED, EXHAUSTED -> false;
            default -> true;
        };
    }

    /** Ticks of the duel (the end of the replayed time). */
    public int duelTicks() {
        return ticks;
    }

    /** The tick the show is over: about {@code showTicks} for a duel with a lot in it, earlier for a short one. */
    public int lengthTicks() {
        return (int) Math.ceil(weight[ticks] / speed - 1e-9);
    }

    /** The simulated time (in ticks, fractional) to show at {@code showTick}: 0 at the start, the duel's length at the end. */
    public double simTime(double showTick) {
        double target = Math.max(0, showTick) * speed;
        if (target >= weight[ticks]) {
            return ticks;
        }
        int lo = 0, hi = ticks;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (weight[mid] <= target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double w = weight[lo + 1] - weight[lo];
        return lo + (w <= 0 ? 0 : (target - weight[lo]) / w);
    }

    /** How many simulated ticks pass per server tick at {@code showTick}: about 1 at a blow, many while nothing happens. */
    public double speedAt(double showTick) {
        int t = (int) Math.min(ticks - 1, Math.floor(simTime(showTick)));
        double w = weight[t + 1] - weight[t];
        return w <= 0 ? 1 : speed / w;
    }
}
