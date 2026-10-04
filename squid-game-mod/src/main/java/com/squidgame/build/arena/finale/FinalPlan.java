package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

/**
 * Orders the construction steps of the Final arena. Public entry point used by
 * {@link com.squidgame.build.arena.FinalBuilder}.
 */
public final class FinalPlan {
    private FinalPlan() {
    }

    public static void build(BuildContext c) {
        Yard.ground(c);
        Backdrop.build(c);
        Yard.rimWall(c);
        Gallery.build(c);
        Gate.build(c);
        School.build(c);
        Court.paint(c);
        Court.markers(c);
    }
}
