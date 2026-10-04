package com.squidgame.entity;

/**
 * Pluggable per-game brain of a {@link ContestantEntity}. A game assigns one behaviour per NPC (and clears it
 * when the game ends). The entity calls {@link #tick} from {@code customServerAiStep} (reduced rate for far-away
 * NPCs) after vanilla navigation has run, so the behaviour can steer by calling the entity's motor helpers
 * ({@code moveToward}, {@code stopMoving}, {@code lookAtPos}, {@code leap}...). Behaviours must only use
 * information available to a human player in the same situation (see {@link NpcMemory}).
 */
public interface NpcBehavior {
    /** Called once when assigned. */
    default void start(ContestantEntity npc) {
    }

    /** Called every AI step. */
    void tick(ContestantEntity npc);

    /** Called when replaced / the game ends. */
    default void stop(ContestantEntity npc) {
    }

    /** Called when stuck recovery has exhausted its options (stage 5). */
    default void onStuck(ContestantEntity npc) {
    }

    /** Called when a nearby contestant was eliminated (for reactions: flinch, panic, hesitation). */
    default void onWitnessElimination(ContestantEntity npc, int otherNumber, double distance) {
    }
}
