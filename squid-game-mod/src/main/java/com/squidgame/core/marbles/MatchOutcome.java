package com.squidgame.core.marbles;

/** How a pair's match ended: who won (the loser is eliminated) and why. */
public record MatchOutcome(Side winner, Reason reason) {
    public enum Reason {
        /** The loser has no marble left. */
        OUT_OF_MARBLES,
        /** Time (or the round budget) ran out: more marbles wins. */
        MORE_MARBLES,
        /** Time ran out with equal marbles: one decisive round / throw settled it. */
        SUDDEN_DEATH,
        /** Nothing else could decide it: a coin flip, announced to both. */
        COIN_FLIP,
        /** The other partner left the tournament. */
        FORFEIT
    }

    public Side loser() {
        return winner.other();
    }
}
