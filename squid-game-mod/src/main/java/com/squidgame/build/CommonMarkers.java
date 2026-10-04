package com.squidgame.build;

import java.util.List;

/**
 * Marker / region names every game arena (not the hub) must provide. Game-specific markers are
 * documented in docs/ARENA_MARKERS.md.
 */
public final class CommonMarkers {
    private CommonMarkers() {
    }

    /** Where contestants stand while the instructions are read (>= configured max contestants, grid, yaw faces the gate). */
    public static final String WAITING_SPAWN = "waiting.spawn";
    /** Where a human arrives (first entry of the waiting room). */
    public static final String WAITING_PLAYER_ENTRY = "waiting.player_entry";
    /** Doorway between waiting room and arena: centre-bottom of the opening, yaw = direction of travel into the arena, data w=,h=. */
    public static final String GATE_DOOR = "gate.door";
    /** Spectator / eliminated viewing point. */
    public static final String SPECTATOR = "arena.spectator";
    /** Where survivors gather for the results (safe). */
    public static final String EXIT = "arena.exit";
    /** Guard post positions (data: rank=circle|triangle|square). */
    public static final String GUARD_POST = "guard.post";

    public static final String REGION_BOUNDS = "arena.bounds";
    public static final String REGION_WAITING = "waiting.bounds";

    public static final List<String> REQUIRED = List.of(WAITING_SPAWN, WAITING_PLAYER_ENTRY, GATE_DOOR, SPECTATOR, EXIT);
    public static final List<String> REQUIRED_REGIONS = List.of(REGION_BOUNDS, REGION_WAITING);
}
