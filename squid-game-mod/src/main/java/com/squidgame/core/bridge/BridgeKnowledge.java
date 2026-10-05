package com.squidgame.core.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What everybody on the bridge can know: only things that publicly happened. A panel is seen shattering
 * ({@link Outcome#BROKE}: the other lane of that row is the safe one) or a contestant is seen standing on a panel
 * that did not give way ({@link Outcome#HELD}: this lane is the safe one). NPC behaviours learn exclusively from this
 * log and the optional human overlay shows exactly this; the hidden {@link BridgeRoute} is never consulted.
 *
 * <p>The event log is append-only so late observers (an AI stand-in taking over for a disconnected human) can
 * replay everything that has happened. The model is consistent by construction: contradicting reports are
 * ignored.
 */
public final class BridgeKnowledge {
    public static final int UNKNOWN = -1;

    public enum Outcome {
        /** Somebody stood on this panel and it held: the lane is safe. */
        HELD,
        /** This panel shattered under somebody: the lane is fragile, the other lane is safe. */
        BROKE
    }

    /** What is publicly known about one panel. */
    public enum LaneState {
        UNKNOWN, SAFE, WEAK, BROKEN
    }

    /** One public observation, numbered in order of occurrence. */
    public record Event(int index, long tick, int row, int lane, Outcome outcome) {
    }

    private final int rows;
    private final int[] safeLane;
    private final boolean[][] broken;
    private final List<Event> events = new ArrayList<>();

    public BridgeKnowledge(int rows) {
        this.rows = rows;
        this.safeLane = new int[rows];
        this.broken = new boolean[rows][BridgeRoute.LANES];
        java.util.Arrays.fill(safeLane, UNKNOWN);
    }

    public int rows() {
        return rows;
    }

    /**
     * Records a public observation. Returns true when it taught something new (a row became known, or a known
     * fragile panel is now seen broken). Out-of-range or contradicting reports are ignored.
     */
    public boolean record(int row, int lane, Outcome outcome, long tick) {
        if (row < 0 || row >= rows || lane < 0 || lane >= BridgeRoute.LANES) {
            return false;
        }
        int impliedSafe = outcome == Outcome.HELD ? lane : 1 - lane;
        if (safeLane[row] != UNKNOWN && safeLane[row] != impliedSafe) {
            return false; // contradicts what is already known
        }
        boolean news = false;
        if (safeLane[row] == UNKNOWN) {
            safeLane[row] = impliedSafe;
            news = true;
        }
        if (outcome == Outcome.BROKE && !broken[row][lane]) {
            broken[row][lane] = true;
            news = true;
        }
        if (news) {
            events.add(new Event(events.size(), tick, row, lane, outcome));
        }
        return news;
    }

    /** The lane known to hold in {@code row}, or {@link #UNKNOWN}. */
    public int safeLane(int row) {
        return row < 0 || row >= rows ? UNKNOWN : safeLane[row];
    }

    public boolean isKnown(int row) {
        return safeLane(row) != UNKNOWN;
    }

    public LaneState laneState(int row, int lane) {
        if (row < 0 || row >= rows || lane < 0 || lane >= BridgeRoute.LANES) {
            return LaneState.UNKNOWN;
        }
        if (broken[row][lane]) {
            return LaneState.BROKEN;
        }
        if (safeLane[row] == UNKNOWN) {
            return LaneState.UNKNOWN;
        }
        return safeLane[row] == lane ? LaneState.SAFE : LaneState.WEAK;
    }

    /** The first row whose safe lane is not known yet ({@link #rows()} when the whole route is known). */
    public int frontier() {
        for (int r = 0; r < rows; r++) {
            if (safeLane[r] == UNKNOWN) {
                return r;
            }
        }
        return rows;
    }

    public int knownRows() {
        int n = 0;
        for (int s : safeLane) {
            if (s != UNKNOWN) {
                n++;
            }
        }
        return n;
    }

    /** Number of observations so far; it grows whenever something new became known (overlays resend on change). */
    public int eventCount() {
        return events.size();
    }

    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    public Event event(int index) {
        return events.get(index);
    }

    /** Snapshot for overlays: row-major, two entries per row, ordinal of {@link LaneState}. */
    public byte[] snapshot() {
        byte[] out = new byte[rows * BridgeRoute.LANES];
        for (int r = 0; r < rows; r++) {
            for (int l = 0; l < BridgeRoute.LANES; l++) {
                out[r * BridgeRoute.LANES + l] = (byte) laneState(r, l).ordinal();
            }
        }
        return out;
    }
}
