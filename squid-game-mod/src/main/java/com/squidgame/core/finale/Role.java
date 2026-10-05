package com.squidgame.core.finale;

/** The two sides of a duel. */
public enum Role {
    /** Starts in the square and must stand in the head circle for a moment, or knock the defender out. */
    ATTACKER,
    /** Starts in the triangle and must stop the attacker, or simply survive until time runs out. */
    DEFENDER;

    public Role other() {
        return this == ATTACKER ? DEFENDER : ATTACKER;
    }
}
