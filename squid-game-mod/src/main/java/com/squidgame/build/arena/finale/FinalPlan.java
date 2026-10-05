package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

/**
 * Orders the construction steps of the Final arena. Public entry point used by
 * {@link com.squidgame.build.arena.FinalBuilder}.
 *
 * <ol>
 *   <li>{@link Yard#ground} sand, dry outer ground, foundation;</li>
 *   <li>{@link Backdrop#build} the 45 high painted sunset wall ({@link Mural});</li>
 *   <li>{@link Yard#rimWall} whitewashed rim wall, corner bastions, lanterns;</li>
 *   <li>{@link Gallery#build} east grandstand, terrace, broadcast booth, audience markers;</li>
 *   <li>{@link Gate#build} waiting room prefab, landing, ramp, symbol billboard;</li>
 *   <li>{@link School#build} school, podium, flag poles;</li>
 *   <li>{@link Playground#build} props and trees; {@link Towers#build} floodlight and judges' towers;</li>
 *   <li>{@link Court#paint} / {@link Court#markers} the squid and its markers; {@link Guards#markers};</li>
 *   <li>{@link Decor#build} footprints, sand piles, tufts (last: it avoids everything already standing).</li>
 * </ol>
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
        Playground.build(c);
        Towers.build(c);
        Court.paint(c);
        Court.markers(c);
        Guards.markers(c);
        Decor.build(c);
    }
}
