package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Assembles the whole Marbles village. Build order: terrain, perimeter + sky, courts (in pair order k = 0..63 so the
 * {@code marbles.plot} regions come out in k order), houses, civic buildings, props, lighting fix-ups, markers.
 *
 * <p>Markers beyond the contract: {@code marbles.pair_line} / {@code pair_target} / {@code table} per spot,
 * {@code guard.post} (rank circle/triangle/square), {@code guard.patrol} (route=ring), {@code marbles.stage}
 * (the square's raised platform), {@code marbles.well}, {@code marbles.tree}, {@code marbles.tower}.
 */
public final class MarblesVillage {
    private MarblesVillage() {
    }

    public static void build(BuildContext c) {
        List<Layout.Slot> slots = Layout.slots();
        Terrain.build(c);
        Sky.build(c);

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

        // hall
        c.at(0, 0, Layout.HALL_ZG, 0, () -> WaitingRoomPrefab.build(c,
                new WaitingRoomPrefab.Spec(Layout.HALL_W, Layout.HALL_D, Layout.HALL_H, "MARBLES", "WAIT FOR THE GAME TO START", 144)));

        Fixup.stairShapes(c.buffer());
        // temporary bounds so the contract validates while the rest is built up
        c.region("arena.bounds", Layout.X0, -2, Layout.Z0, Layout.X1, Layout.ROOF_Y, Layout.Z1);
        c.region("marbles.square", Layout.SQ_X0, 0, Layout.SQ_Z0, Layout.SQ_X1, 8, Layout.SQ_Z1);
        c.marker("marbles.square_spawn", 0.5, 1.0, 5.5, 0f, "slot=0");
        c.marker("arena.spectator", 0.5, 30.0, 8.5, 180f);
        c.marker("arena.exit", 0.5, 1.0, -46.5, 0f);
        c.marker("guard.post", 0.5, 1.0, 20.5, 0f, "rank=circle");
    }
}
