package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Assembles the whole Marbles village. Build order: terrain, perimeter + sky, the square, courts (in pair order
 * k = 0..63 so the {@code marbles.plot} regions come out in k order) with their houses, special plots, the houses
 * framing the square, the hall, street furniture, guards, spawn markers, and finally the stair-shape fix-up.
 *
 * <p>Markers beyond the contract: {@code marbles.pair_line} / {@code pair_target} / {@code table} per spot,
 * {@code guard.post} (rank circle/triangle/square), {@code guard.patrol} (route=square), {@code marbles.stage},
 * {@code marbles.well}, {@code marbles.tree}, {@code marbles.tower}, {@code marbles.shrine}, {@code marbles.exit_gather}
 * (non-standing ones carry {@code stand=0}). Plaques show k+1 (1..64).
 */
public final class MarblesVillage {
    private MarblesVillage() {
    }

    public static void build(BuildContext c) {
        List<Layout.Slot> slots = Layout.slots();
        Terrain.build(c);
        Sky.build(c);
        Square.build(c);

        // courts in k order
        List<Layout.Slot> courts = new ArrayList<>();
        for (Layout.Slot s : slots) {
            if (s.kind == Layout.Kind.COURT) {
                courts.add(s);
            }
        }
        courts.sort(Comparator.comparingInt(s -> s.k));
        for (Layout.Slot s : courts) {
            House.Spec h = Houses.specFor(s);
            c.at(s.ox, 0, s.oz, s.rot, () -> Court.build(c, s, h));
        }
        // special plots in the same grid
        for (Layout.Slot s : slots) {
            House.Spec h = Houses.specFor(s);
            switch (s.kind) {
                case EXIT -> c.at(s.ox, 0, s.oz, s.rot, () -> Civic.exitCourt(c, h));
                case TOWER -> c.at(s.ox, 0, s.oz, s.rot, () -> Civic.tower(c, h));
                case SHRINE -> c.at(s.ox, 0, s.oz, s.rot, () -> Civic.shrine(c, h));
                default -> {
                }
            }
        }
        Civic.frameHouses(c);

        // hall: the standard waiting room prefab, then its village dressing
        c.at(0, 0, Layout.HALL_ZG, 0, () -> WaitingRoomPrefab.build(c,
                new WaitingRoomPrefab.Spec(Layout.HALL_W, Layout.HALL_D, Layout.HALL_H, "MARBLES", "WAIT FOR THE GAME TO START", 144)));
        Hall.build(c);

        Alleys.build(c, slots);
        Guards.build(c);

        // regions and the square's spawn grid
        c.region("arena.bounds", Layout.X0, -2, Layout.Z0, Layout.X1, Layout.ROOF_Y - 1, Layout.Z1);
        c.region("marbles.square", Layout.SQ_X0, 0, Layout.SQ_Z0, Layout.SQ_X1, 10, Layout.SQ_Z1);
        Lighting.ensure(c);
        List<int[]> spots = Square.freeSpots(c);
        spots.sort(Comparator.comparingDouble(p -> Math.hypot(p[0] - Square.CX, (p[1] - Square.CZ) * 1.1)));
        int slot = 0;
        for (int[] p : spots) {
            c.marker("marbles.square_spawn", p[0] + 0.5, 1.0, p[1] + 0.5, 0f, "slot=" + slot++);
        }

        Fixup.stairShapes(c.buffer());
    }
}
