package com.squidgame.core;

/**
 * Tournament phases. The full sequence is
 * <pre>
 * LOBBY -> REGISTRATION -> [ INSTRUCTIONS -> COUNTDOWN -> GAME -> ELIMINATIONS -> RESULTS -> TRANSITION ]* -> FINAL_WINNER -> RESTART
 * </pre>
 * where the bracketed block repeats once per game. {@code TRANSITION} leads either into the
 * next game's {@code INSTRUCTIONS} or, after the last game, into {@code FINAL_WINNER}.
 */
public enum Phase {
    LOBBY("lobby", false),
    REGISTRATION("registration", false),
    INSTRUCTIONS("instructions", true),
    COUNTDOWN("countdown", true),
    GAME("game", true),
    ELIMINATIONS("eliminations", true),
    RESULTS("results", true),
    TRANSITION("transition", false),
    FINAL_WINNER("final_winner", false),
    RESTART("restart", false);

    public final String id;
    /** True while contestants are inside a game arena (restrictions apply, chunks stay loaded). */
    public final boolean inArena;

    Phase(String id, boolean inArena) {
        this.id = id;
        this.inArena = inArena;
    }

    public String translationKey() {
        return "squidgame.phase." + id;
    }

    public static Phase byOrdinal(int ordinal) {
        Phase[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : LOBBY;
    }
}
