package com.squidgame.build.arena.hub;

import com.squidgame.build.BuildContext;

import java.util.List;

/** One part of the connected hub complex (dormitory, corridors, stairway maze, control room). See docs/ARENA_MARKERS.md. */
public interface HubPart {
    /** Builds into the shared hub frame (local floor y = 0, origin = hub origin). */
    void build(BuildContext c);

    /** Bump when the part's output changes. */
    default int version() {
        return 1;
    }

    /** Marker names this part is responsible for (validated by {@link com.squidgame.build.arena.HubBuilder}). */
    default List<String> markers() {
        return List.of();
    }

    /** Region names this part is responsible for. */
    default List<String> regions() {
        return List.of();
    }
}
