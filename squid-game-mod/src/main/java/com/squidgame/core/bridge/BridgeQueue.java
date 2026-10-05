package com.squidgame.core.bridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The crossing order and the gate rules. The order is a random permutation drawn once at the start; the gate calls
 * contestants strictly in that order, nobody skips ahead and nobody can be called twice. A contestant that
 * is eliminated before being called (disconnect, admin) is skipped.
 *
 * <p>Lifecycle of an entry: {@code QUEUED -> CALLED -> ON_BRIDGE -> FINISHED}, or {@code OUT} from any state.
 */
public final class BridgeQueue {
    public enum State {
        /** Waiting on the start platform. */
        QUEUED,
        /** The gate is open for this contestant who has not stepped onto the bridge yet. */
        CALLED,
        /** Past the gate, on the bridge. */
        ON_BRIDGE,
        /** Reached the far platform. */
        FINISHED,
        /** Eliminated (or gone). */
        OUT
    }

    /**
     * Snapshot of the situation on the bridge the gate decides on.
     *
     * @param someoneCalled      a contestant was called and has not stepped onto the bridge yet
     * @param crossersOnBridge   contestants currently on the bridge
     * @param ticksSinceLastCall time since the previous contestant was called
     * @param hasPrevious        somebody was called before
     * @param previousGone       the most recently called contestant finished or is out
     * @param previousRow        the row that contestant is on (0-based); ignored when {@code previousGone}
     */
    public record Flow(boolean someoneCalled, int crossersOnBridge, long ticksSinceLastCall,
                       boolean hasPrevious, boolean previousGone, int previousRow) {
    }

    /**
     * True when the next contestant may be called now: nobody is still at the gate, fewer than {@code maxCrossers}
     * are crossing, a minimum spacing has passed and the previous contestant is at least {@code releaseGapRows}
     * rows into the bridge (or finished / fallen).
     */
    public static boolean mayRelease(BridgeRules.Params p, Flow f) {
        if (f.someoneCalled() || f.crossersOnBridge() >= p.maxCrossers()) {
            return false;
        }
        if (f.hasPrevious() && f.ticksSinceLastCall() < p.minReleaseSpacingTicks()) {
            return false;
        }
        if (!f.hasPrevious() || f.previousGone()) {
            return true;
        }
        return f.previousRow() >= p.releaseGapRows() - 1;
    }

    /**
     * The crossing order of a game resumed after a server restart: the saved order restricted to the contestants who
     * are still in the game (those eliminated before the restart are left out, everybody else keeps their relative
     * place). Returns {@code null} when the saved order does not cover every contestant of {@code alive} (a different
     * roster: the caller draws a fresh order).
     */
    public static List<Integer> restoreOrder(int[] saved, java.util.Collection<Integer> alive) {
        if (saved == null) {
            return null;
        }
        java.util.Set<Integer> wanted = new java.util.HashSet<>(alive);
        List<Integer> kept = new ArrayList<>();
        for (int n : saved) {
            if (wanted.remove(n)) {
                kept.add(n);
            }
        }
        return wanted.isEmpty() ? kept : null;
    }

    private final int[] order;
    private final State[] states;
    private final Map<Integer, Integer> indexOf = new HashMap<>();
    private int lastCalled = -1;

    public BridgeQueue(List<Integer> order) {
        this.order = new int[order.size()];
        this.states = new State[order.size()];
        for (int i = 0; i < order.size(); i++) {
            int n = order.get(i);
            if (indexOf.put(n, i) != null) {
                throw new IllegalArgumentException("duplicate contestant " + n);
            }
            this.order[i] = n;
            this.states[i] = State.QUEUED;
        }
    }

    public int size() {
        return order.length;
    }

    /** The contestant numbers in crossing order. */
    public List<Integer> order() {
        List<Integer> l = new ArrayList<>(order.length);
        for (int n : order) {
            l.add(n);
        }
        return l;
    }

    public boolean contains(int number) {
        return indexOf.containsKey(number);
    }

    /** 0-based place in the crossing order, or -1 if the contestant is not part of the queue. */
    public int positionOf(int number) {
        return indexOf.getOrDefault(number, -1);
    }

    public State state(int number) {
        Integer i = indexOf.get(number);
        return i == null ? State.OUT : states[i];
    }

    /** Contestants in front of {@code number} that have not stepped onto the bridge yet (still queued or at the gate). */
    public int aheadOf(int number) {
        Integer idx = indexOf.get(number);
        if (idx == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < idx; i++) {
            if (states[i] == State.QUEUED || states[i] == State.CALLED) {
                n++;
            }
        }
        return n;
    }

    /** The next contestant to be called (first still queued in order), or -1 when everybody has been called. */
    public int next() {
        for (int i = 0; i < order.length; i++) {
            if (states[i] == State.QUEUED) {
                return order[i];
            }
        }
        return -1;
    }

    /** Calls a queued contestant. Only the contestant returned by {@link #next()} may be called. */
    public boolean call(int number) {
        Integer i = indexOf.get(number);
        if (i == null || states[i] != State.QUEUED || order[i] != next()) {
            return false;
        }
        states[i] = State.CALLED;
        lastCalled = i;
        return true;
    }

    public void onBridge(int number) {
        move(number, State.CALLED, State.ON_BRIDGE);
    }

    public void finished(int number) {
        Integer i = indexOf.get(number);
        if (i != null && (states[i] == State.ON_BRIDGE || states[i] == State.CALLED)) {
            states[i] = State.FINISHED;
        }
    }

    /** Marks a contestant as eliminated / gone, whatever state it was in. */
    public void out(int number) {
        Integer i = indexOf.get(number);
        if (i != null && states[i] != State.FINISHED) {
            states[i] = State.OUT;
        }
    }

    private void move(int number, State from, State to) {
        Integer i = indexOf.get(number);
        if (i != null && states[i] == from) {
            states[i] = to;
        }
    }

    public int count(State s) {
        int n = 0;
        for (State st : states) {
            if (st == s) {
                n++;
            }
        }
        return n;
    }

    /** The contestant called most recently, or -1. */
    public int lastCalled() {
        return lastCalled < 0 ? -1 : order[lastCalled];
    }

    public boolean someoneCalled() {
        return count(State.CALLED) > 0;
    }
}
