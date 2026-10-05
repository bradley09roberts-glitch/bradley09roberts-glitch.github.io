package com.squidgame.core.marbles;

/** One of the two partners of a pair: A stands at the marker {@code marbles.pair_a}, B at {@code marbles.pair_b}. */
public enum Side {
    A, B;

    public Side other() {
        return this == A ? B : A;
    }
}
