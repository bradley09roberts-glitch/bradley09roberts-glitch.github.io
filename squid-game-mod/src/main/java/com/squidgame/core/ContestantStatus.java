package com.squidgame.core;

/** Life-cycle of a contestant. {@link #ELIMINATED} is terminal: nothing may revive a contestant. */
public enum ContestantStatus {
    /** Registered and still in the tournament. */
    ALIVE,
    /** Out of the tournament (terminal). */
    ELIMINATED,
    /** Won the tournament (terminal). */
    WINNER;

    public boolean isTerminal() {
        return this != ALIVE;
    }
}
