package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * Orchestrates the guard compound: shell and surfaces, doorways, then the detailing passes.
 *
 * <p>Markers: {@code guard.post} (rank=triangle|circle|square) and {@code guard.patrol} (route=a|b|c, i=N);
 * region {@code corridor.bounds} (the envelope x[-30,30] y[-6,11] z[-80,-35]).
 */
public final class Compound {
    private Compound() {
    }

    public static void build(BuildContext c) {
        Shell.build(c);
        Doors.buildAll(c);
        Corridors.build(c);
        Halls.build(c);
        Checkpoint.build(c);
        Barracks.build(c);
        MonitorRoom.build(c);
        Armory.build(c);
        Office.build(c);
        Store.build(c);
        Canteen.build(c);
        Infirmary.build(c);
        c.region("corridor.bounds", Plan.X0, -6, Plan.Z0, Plan.X1, Plan.ROOF_Y, Plan.Z1);
        Marks.build(c);
    }
}
