package com.longnight.sim;

import net.minecraft.core.BlockPos;

/**
 * The simulation's window onto the physical world. The server implementation places graves,
 * spawns raiders and messages players; tests use {@link #NONE}.
 */
public interface WorldHooks {
    WorldHooks NONE = new WorldHooks() {};

    /** A person died (abstractly or in the world); place a grave, remove their body. */
    default void personDied(WorldSim sim, Person p, Settlement s) {}

    /** True if this settlement currently has bodies (a player is nearby). */
    default boolean isEmbodied(Settlement s) {
        return false;
    }

    /** A raid reaches an embodied settlement: spawn the raiders instead of resolving abstractly. Return true if handled. */
    default boolean spawnRaid(WorldSim sim, Settlement target, Settlement source, int strength) {
        return false;
    }

    /** Tell nearby/citizen players something important. */
    default void announce(WorldSim sim, Settlement s, String message, int importance) {}

    /** Cheap terrain check from the world generator (no chunk loading): is this plot plausibly buildable? */
    default boolean plotLooksBuildable(BlockPos origin, int width, int depth) {
        return true;
    }

    /** True if the last plotLooksBuildable answer was a provisional "yes" given without surveying (time budget spent). */
    default boolean lastSurveyWasGuess() {
        return false;
    }

    /** A person arrived in or left a settlement (refresh their body). */
    default void personMoved(WorldSim sim, Person p) {}
}
