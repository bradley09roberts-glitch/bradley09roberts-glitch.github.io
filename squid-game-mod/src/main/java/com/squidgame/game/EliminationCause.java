package com.squidgame.game;

/** Why a contestant was eliminated: drives the visual effect and the announcement text. */
public enum EliminationCause {
    /** Shot by a guard (stylised flash + crack, collapse). */
    SHOT("shot", Effect.COLLAPSE),
    /** Fell (glass bridge, tug of war platform). The body keeps falling; no extra effect. */
    FELL("fell", Effect.NONE),
    /** Ran out of time. */
    TIMEOUT("timeout", Effect.COLLAPSE),
    /** Broke the dalgona. */
    BROKE_COOKIE("broke_cookie", Effect.COLLAPSE),
    /** Lost a head-to-head match (marbles). */
    LOST_MATCH("lost_match", Effect.COLLAPSE),
    /** Knocked out in the final fight. */
    KNOCKED_OUT("knocked_out", Effect.COLLAPSE),
    /** Left the arena. */
    OUT_OF_BOUNDS("out_of_bounds", Effect.COLLAPSE),
    /** Absent too long. */
    DISCONNECTED("disconnected", Effect.VANISH),
    /** Removed by an operator. */
    ADMIN("admin", Effect.VANISH),
    /** Lost the tug of war (team fell). */
    LOST_TEAM("lost_team", Effect.NONE);

    public enum Effect {NONE, COLLAPSE, VANISH}

    public final String id;
    public final Effect effect;

    EliminationCause(String id, Effect effect) {
        this.id = id;
        this.effect = effect;
    }

    public String translationKey() {
        return "squidgame.cause." + id;
    }
}
