package com.squidgame.core.bridge;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tick-level simulation of a whole NPC-only glass bridge game on the pure rule classes: random order, gate flow,
 * public knowledge, hesitation, slips, freezing, the stall timer, panel judging and the time limit. The Minecraft side
 * is modelled with fixed durations (walk, hop) so tests can check pass rates, learning, throughput and deadlock
 * freedom without a server. NPC decisions only look at {@link BridgeKnowledge}; only the judge touches the route.
 */
final class BridgeSim {
    enum Fate { FINISHED, FELL, STALLED, TIMEOUT }

    /**
     * @param fateByPosition {@link Fate} ordinal of the contestant at each queue position
     * @param endTick        tick at which everybody was resolved (or the limit)
     */
    record Result(int finished, int fell, int stalled, int timedOut, long endTick, int limitTicks, int[] fateByPosition) {
        int total() {
            return finished + fell + stalled + timedOut;
        }
    }

    private enum S { QUEUED, WALKING, THINKING, WAITING, HOPPING, FROZEN, CONDEMNED, DONE }

    private static final int ROWS = BridgeRoute.DEFAULT_ROWS;
    private static final int HOP_TICKS = 13;
    private static final double EDGE_WALK_BLOCKS = 1.2;
    private static final int FIRST_CALL_TICK = 60;

    private final BridgeRoute route;
    private final BridgeKnowledge know = new BridgeKnowledge(ROWS);
    private final BridgeQueue queue;
    private final BridgeRules.Params params;
    private final Difficulty difficulty;
    private final int n;
    private final C[] cs;
    private final int[][] occupant = new int[ROWS][2];
    private final long[][] rebuildUntil = new long[ROWS][2];
    /** Panels that shattered for good (a hole everybody can see). */
    private final boolean[][] broken = new boolean[ROWS][2];
    private final List<long[]> scheduled = new ArrayList<>(); // {tick, kind (0 shatter, 1 held), row, lane, number}
    private long lastCall = Long.MIN_VALUE / 2;

    private static final class C {
        int number;
        int position;
        Personality p;
        Rng rng;
        S state = S.QUEUED;
        int row = -2; // -2 not called yet, -1 on the platform, 0.. on a panel, ROWS = finished
        int lane;
        int targetRow, targetLane;
        long until;
        long stagedAt;
        long leaveAt;
        long arrived;
        int cursor;
        final int[] known = new int[ROWS];
        double fear;
        int lastLane = -1;
        /** The lane for the target row was chosen with knowledge of the row (decisions are made once, not re-rolled while waiting). */
        boolean decidedKnown;
        Fate fate;
    }

    private BridgeSim(long seed, Difficulty d, int n, int revealed) {
        Rng master = new Rng(seed);
        this.difficulty = d;
        this.params = BridgeRules.params(d);
        this.n = n;
        this.route = BridgeRoute.generate(master.fork(1).seed(), ROWS);
        List<Integer> order = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            order.add(i);
        }
        master.fork(2).shuffle(order);
        this.queue = new BridgeQueue(order);
        for (int r = 0; r < Math.min(revealed, ROWS); r++) {
            know.record(r, route.safeLane(r), BridgeKnowledge.Outcome.SHOWN, 0); // the guards show the first rows up front
        }
        this.cs = new C[n + 1];
        for (int i = 0; i < order.size(); i++) {
            C c = new C();
            c.number = order.get(i);
            c.position = i;
            c.p = Personality.generate(master.fork(1000 + c.number));
            c.rng = master.fork(5000 + c.number);
            Arrays.fill(c.known, BridgeKnowledge.UNKNOWN);
            cs[c.number] = c;
        }
    }

    /** The bare bridge: nothing is shown up front (the show-like baseline, used for crowds of 12 and more). */
    static Result run(long seed, Difficulty d, int n) {
        return new BridgeSim(seed, d, n, 0).simulate(-1);
    }

    /** The first {@code revealed} rows are shown to everybody before the first contestant is called. */
    static Result run(long seed, Difficulty d, int n, int revealed) {
        return new BridgeSim(seed, d, n, revealed).simulate(-1);
    }

    /** The game as played: the guards show as many rows as {@link BridgeRules#revealedRows} gives for this field. */
    static Result runAsPlayed(long seed, Difficulty d, int n) {
        return run(seed, d, n, BridgeRules.revealedRows(d, n, ROWS));
    }

    /** Variant where the contestant at queue position {@code frozenPosition} freezes on its first unrevealed row. */
    static Result runWithFreezer(long seed, Difficulty d, int n, int frozenPosition) {
        return new BridgeSim(seed, d, n, 0).simulate(frozenPosition);
    }

    private Result simulate(int freezer) {
        int limit = limitOverride > 0 ? limitOverride : BridgeRules.timeLimitTicks(difficulty, n);
        long end = limit;
        for (long t = 0; t < limit; t++) {
            processScheduled(t);
            gate(t);
            for (C c : cs) {
                if (c != null && c.state != S.QUEUED && c.state != S.DONE) {
                    step(c, t, freezer);
                }
            }
            if (queue.count(BridgeQueue.State.FINISHED) + queue.count(BridgeQueue.State.OUT) == n) {
                end = t;
                break;
            }
        }
        int[] fate = new int[n];
        int[] counts = new int[Fate.values().length];
        for (C c : cs) {
            if (c == null) {
                continue;
            }
            if (c.fate == null) {
                c.fate = Fate.TIMEOUT;
            }
            fate[c.position] = c.fate.ordinal();
            counts[c.fate.ordinal()]++;
        }
        return new Result(counts[Fate.FINISHED.ordinal()], counts[Fate.FELL.ordinal()], counts[Fate.STALLED.ordinal()],
                counts[Fate.TIMEOUT.ordinal()], end, limit, fate);
    }

    // ------------------------------------------------------------------ gate

    private void gate(long t) {
        int next = queue.next();
        if (next < 0 || t < FIRST_CALL_TICK) {
            return;
        }
        int prev = queue.lastCalled();
        boolean hasPrev = prev > 0;
        boolean gone = false;
        int prevRow = -1;
        if (hasPrev) {
            BridgeQueue.State st = queue.state(prev);
            gone = st == BridgeQueue.State.FINISHED || st == BridgeQueue.State.OUT;
            prevRow = cs[prev].row;
        }
        BridgeQueue.Flow f = new BridgeQueue.Flow(queue.someoneCalled(), queue.count(BridgeQueue.State.ON_BRIDGE),
                t - lastCall, hasPrev, gone, prevRow);
        if (BridgeQueue.mayRelease(params, f)) {
            C c = cs[next];
            queue.call(next);
            lastCall = t;
            c.state = S.WALKING;
            c.row = -1;
            // the contestant was already stepping up to the gate while the previous one crossed
            c.until = Math.max(t + BridgeNpcRules.callReactionTicks(c.p, difficulty, c.rng), c.stagedAt);
            int onDeck = queue.next();
            if (onDeck > 0) {
                C d = cs[onDeck];
                d.stagedAt = t + (long) ((2.5 + d.position * 0.12) / BridgeNpcRules.walkSpeed(d.p));
            }
        }
    }

    // ------------------------------------------------------------------ contestants

    private void learn(C c) {
        double attention = BridgeNpcRules.attention(c.p, difficulty);
        while (c.cursor < know.eventCount()) {
            BridgeKnowledge.Event e = know.event(c.cursor++);
            if (e.outcome() == BridgeKnowledge.Outcome.BROKE) {
                c.fear = Math.min(1.0, c.fear + 0.25);
            }
            if (e.outcome() == BridgeKnowledge.Outcome.SHOWN || (!blind && c.rng.chance(attention))) {
                c.known[e.row()] = know.safeLane(e.row()); // what the guards show cannot be missed
            }
        }
        c.fear *= 0.9995;
    }

    private void step(C c, long t, int freezer) {
        learn(c);
        switch (c.state) {
            case WALKING -> {
                if (t >= c.until) {
                    decide(c, t, freezer);
                }
            }
            case THINKING -> {
                if (t >= c.until) {
                    reserveOrWait(c, t);
                }
            }
            case WAITING -> reserveOrWait(c, t);
            case HOPPING -> {
                if (c.row >= 0 && t >= c.leaveAt && occupant[c.row][c.lane] == c.number) {
                    occupant[c.row][c.lane] = 0; // airborne: the panel is free for the next contestant
                }
                if (t >= c.until) {
                    land(c, t, freezer);
                }
            }
            default -> {
            }
        }
        if (c.row >= 0 && (c.state == S.THINKING || c.state == S.WAITING || c.state == S.FROZEN)) {
            stall(c, t);
        }
    }

    /** At the gate or on a panel: pick the next lane and how long to think about it. */
    private void decide(C c, long t, int freezer) {
        c.targetRow = c.row + 1;
        c.state = S.THINKING;
        if (c.targetRow >= ROWS) {
            c.targetLane = c.lane;
            c.until = t + BridgeNpcRules.settleTicks(c.p, c.rng);
            return;
        }
        int k = c.known[c.targetRow];
        if (trace) {
            System.out.println("t=" + t + " #" + c.number + " (pos " + c.position + ") at row " + c.row + " decides row "
                    + c.targetRow + " known=" + k + " cursor=" + c.cursor + "/" + know.eventCount());
        }
        c.decidedKnown = k != BridgeKnowledge.UNKNOWN;
        if (c.decidedKnown) {
            double pressure = BridgeRules.stallPressure(params, BridgeRules.stallRemaining(params, c.arrived, t));
            boolean slip = c.rng.chance(BridgeNpcRules.slipChance(c.p, difficulty, pressure, c.fear));
            c.targetLane = slip ? 1 - k : k;
            c.until = t + BridgeNpcRules.settleTicks(c.p, c.rng);
        } else {
            boolean freeze = (c.position == freezer && c.row >= 0) || BridgeNpcRules.freezes(c.p, difficulty, true, c.rng);
            if (freeze && c.row >= 0) {
                c.state = S.FROZEN;
                return;
            }
            c.targetLane = BridgeNpcRules.guessLane(c.p, c.lastLane, c.rng);
            c.until = t + BridgeNpcRules.hesitationTicks(c.p, params, c.fear, c.rng);
        }
    }

    private void reserveOrWait(C c, long t) {
        if (c.targetRow < ROWS && broken[c.targetRow][c.targetLane]) {
            // a hole in the glass is plain to see, even for somebody who missed the crash: that was the weak panel,
            // so the other one holds (look again, walk to the other edge)
            c.known[c.targetRow] = 1 - c.targetLane;
            c.targetLane = 1 - c.targetLane;
            c.decidedKnown = true;
            c.state = S.THINKING;
            c.until = t + BridgeNpcRules.settleTicks(c.p, c.rng);
            return;
        }
        if (c.targetRow < ROWS) {
            int tr = c.targetRow;
            int k = c.known[tr];
            if (!c.decidedKnown && k != BridgeKnowledge.UNKNOWN) {
                // the row was revealed while we were thinking: take the lane everybody has seen holding (decided once)
                double pressure = BridgeRules.stallPressure(params, BridgeRules.stallRemaining(params, c.arrived, t));
                c.targetLane = c.rng.chance(BridgeNpcRules.slipChance(c.p, difficulty, pressure, c.fear)) ? 1 - k : k;
                c.decidedKnown = true;
            } else if (!c.decidedKnown && (occupant[tr][0] != 0 || occupant[tr][1] != 0)) {
                // somebody just stepped onto an unrevealed row: wait for the verdict instead of gambling on the other lane
                c.state = S.WAITING;
                return;
            }
            int tl = c.targetLane;
            if (occupant[tr][tl] != 0 || rebuildUntil[tr][tl] > t) {
                c.state = S.WAITING;
                return;
            }
            occupant[tr][tl] = c.number; // reservation, becomes occupation on landing
        }
        c.state = S.HOPPING;
        int walk = c.row < 0 ? 6 : (int) (EDGE_WALK_BLOCKS / BridgeNpcRules.walkSpeed(c.p));
        c.leaveAt = t + walk;
        c.until = t + walk + HOP_TICKS;
    }

    private void land(C c, long t, int freezer) {
        if (c.row >= 0 && occupant[c.row][c.lane] == c.number) {
            occupant[c.row][c.lane] = 0;
        }
        if (c.targetRow >= ROWS) {
            c.row = ROWS;
            queue.finished(c.number);
            c.state = S.DONE;
            c.fate = Fate.FINISHED;
            return;
        }
        if (c.row < 0) {
            queue.onBridge(c.number);
        }
        if (broken[c.targetRow][c.targetLane]) {
            holeLandings++;
        }
        c.row = c.targetRow;
        c.lane = c.targetLane;
        c.lastLane = c.lane;
        c.arrived = t;
        if (!route.isSafe(c.row, c.lane)) {
            if (know.isKnown(c.row)) {
                avoidableDeaths++;
            } else {
                gambleDeaths++;
            }
            c.state = S.CONDEMNED;
            int delay = c.rng.rangeInt(params.shatterDelayMin(), params.shatterDelayMax());
            scheduled.add(new long[]{t + delay, 0, c.row, c.lane, c.number});
            return;
        }
        scheduled.add(new long[]{t + params.confirmTicks(), 1, c.row, c.lane, c.number});
        decide(c, t, freezer);
    }

    /** Stall timer: held while another contestant stands in the next row, breaks the glass when it runs out. */
    private void stall(C c, long t) {
        boolean blocked = c.row + 1 < ROWS && (occupant[c.row + 1][0] != 0 || occupant[c.row + 1][1] != 0);
        if (blocked) {
            c.arrived++;
        }
        if (BridgeRules.stallExpired(params, c.arrived, t)) {
            if (c.state != S.FROZEN) {
                activeStalls++;
            }
            occupant[c.row][c.lane] = 0;
            rebuildUntil[c.row][c.lane] = t + BridgeRules.REBUILD_TICKS;
            queue.out(c.number);
            c.state = S.DONE;
            c.fate = Fate.STALLED;
        }
    }

    static boolean trace;
    /** Test hook: nobody ever registers a public event (only holes in the glass tell them anything). */
    static boolean blind;
    /** Landings on a panel that had already shattered (must stay 0: a hole is plain to see). */
    static int holeLandings;
    /** Stall expiries of contestants who were not frozen with fear, i.e. who waited for something that never came. */
    static int activeStalls;
    /** Calibration only: when positive, replaces the rule table's time limit (ticks). */
    static int limitOverride = -1;
    /** Deaths on a row the whole field already knew (slips / missed events) versus real gambles on an unrevealed row. */
    static int avoidableDeaths, gambleDeaths;

    private void processScheduled(long t) {
        for (int i = 0; i < scheduled.size(); i++) {
            long[] e = scheduled.get(i);
            if (e[0] > t) {
                continue;
            }
            scheduled.remove(i--);
            int row = (int) e[2], lane = (int) e[3];
            C c = cs[(int) e[4]];
            if (trace) {
                System.out.println("t=" + t + " " + (e[1] == 0 ? "BROKE" : "HELD") + " row " + row + " lane " + lane
                        + " by #" + c.number + " (pos " + c.position + ")");
            }
            if (e[1] == 0) {
                know.record(row, lane, BridgeKnowledge.Outcome.BROKE, t);
                broken[row][lane] = true;
                occupant[row][lane] = 0;
                queue.out(c.number);
                c.state = S.DONE;
                c.fate = Fate.FELL;
            } else {
                know.record(row, lane, BridgeKnowledge.Outcome.HELD, t);
            }
        }
    }
}
