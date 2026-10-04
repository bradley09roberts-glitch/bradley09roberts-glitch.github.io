package com.squidgame.build;

import java.util.List;

/**
 * Builds one structure into a {@link BlockBuffer} via the {@link BuildContext} DSL. Implementations
 * are pure Java (no Minecraft types) and deterministic for a given seed; they run on a background
 * thread. See docs/ARENA_MARKERS.md for the marker contract each arena must satisfy.
 */
public interface ArenaBuilder {
    ArenaId id();

    /** Fills the buffer. Local (0,0,0) is the arena origin (world x/z from {@link ArenaId}, y = 64). */
    void build(BuildContext ctx);

    /** Marker names that must exist (>= 1) after {@link #build}; validated by tests and at build time. */
    List<String> requiredMarkers();

    /** Region names that must exist after {@link #build}. */
    default List<String> requiredRegions() {
        return List.of();
    }

    /** Bump when the structure changes so existing worlds rebuild it. */
    default int version() {
        return 1;
    }
}
