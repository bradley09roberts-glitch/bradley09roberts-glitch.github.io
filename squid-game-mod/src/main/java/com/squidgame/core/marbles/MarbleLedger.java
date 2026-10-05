package com.squidgame.core.marbles;

/**
 * The marble counts of the two partners of one pair. Marbles only ever move from one side to the other, so the total
 * is constant for the whole match (the invariant every test checks).
 */
public final class MarbleLedger {
    private final int[] counts = new int[2];
    private final int total;

    public MarbleLedger(int a, int b) {
        if (a < 0 || b < 0) {
            throw new IllegalArgumentException("negative marble count");
        }
        counts[0] = a;
        counts[1] = b;
        total = a + b;
    }

    public static MarbleLedger equal(int each) {
        return new MarbleLedger(each, each);
    }

    public int count(Side s) {
        return counts[s.ordinal()];
    }

    public int total() {
        return total;
    }

    /** True once a side has no marble left (the match is lost). */
    public boolean isOut(Side s) {
        return counts[s.ordinal()] <= 0;
    }

    /**
     * Moves up to {@code n} marbles from {@code from} to its partner and returns how many really moved (never more than
     * {@code from} owns).
     */
    public int transfer(Side from, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("negative transfer");
        }
        int moved = Math.min(n, counts[from.ordinal()]);
        counts[from.ordinal()] -= moved;
        counts[from.other().ordinal()] += moved;
        return moved;
    }

    /** The side that owns more marbles, or null on a tie. */
    public Side leader() {
        if (counts[0] == counts[1]) {
            return null;
        }
        return counts[0] > counts[1] ? Side.A : Side.B;
    }

    @Override
    public String toString() {
        return counts[0] + ":" + counts[1];
    }
}
