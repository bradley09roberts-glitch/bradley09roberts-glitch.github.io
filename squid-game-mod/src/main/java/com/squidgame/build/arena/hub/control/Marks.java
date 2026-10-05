package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

/** Markers of the control room. */
final class Marks {
    private Marks() {
    }

    static void build(BuildContext c) {
        // spectator viewpoint: standing on the gallery in front of the north window, looking west over the dormitory
        c.marker("hub.spectator", 45.5, 14.0, -9.5, 90f);
    }
}
