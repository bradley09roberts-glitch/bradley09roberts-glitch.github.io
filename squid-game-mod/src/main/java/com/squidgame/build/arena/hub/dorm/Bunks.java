package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The bunk-tower field: twelve towers on a regular grid (4 columns x 3 rows) either side of the central avenue, and the
 * contestants' standing spots ({@code dorm.npc_spawn}) on the floor beside them, numbered so that any prefix of the slot
 * list is spread evenly over the whole hall.
 */
final class Bunks {
    private Bunks() {
    }

    /** One standing spot: block cell + facing (towards the aisle). */
    record Spot(int x, int z, float yaw) {
    }

    /** Tower order used for slot numbering (spreads any prefix over the hall). */
    private static final int[] SPREAD = {0, 11, 5, 2, 8, 7, 1, 10, 4, 3, 9, 6};

    static void build(BuildContext c) {
        for (Layout.Tower t : Layout.towers()) {
            c.at(t.cx(), 0, t.cz(), 0, () -> BunkTower.build(c, t));
        }
    }

    /**
     * Emits the standing spots; call after everything else is built so a spot that something else occupies is skipped
     * (floor below, two free blocks above).
     */
    static void emitSpawns(BuildContext c) {
        int slot = 0;
        List<List<Spot>> perTower = new ArrayList<>();
        List<Layout.Tower> towers = Layout.towers();
        for (int i : SPREAD) {
            perTower.add(spots(towers.get(i)));
        }
        for (int r = 0; r < 18; r++) {
            for (List<Spot> l : perTower) {
                if (r < l.size()) {
                    Spot s = l.get(r);
                    if (standable(c, s.x(), s.z())) {
                        c.marker("dorm.npc_spawn", s.x() + 0.5, 0.0, s.z() + 0.5, s.yaw(), "slot=" + slot++);
                    }
                }
            }
        }
    }

    /** All candidate standing cells (the lighting keeps its floor tiles off them). */
    static java.util.Set<Long> candidateCells() {
        java.util.Set<Long> out = new java.util.HashSet<>();
        for (Layout.Tower t : Layout.towers()) {
            for (Spot s : spots(t)) {
                out.add(key(s.x(), s.z()));
            }
        }
        return out;
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static boolean standable(BuildContext c, int x, int z) {
        String floor = c.get(x, -1, z);
        String feet = c.get(x, 0, z);
        String head = c.get(x, 1, z);
        return floor != null && !floor.equals(Pal.AIR)
                && (feet == null || feet.equals(Pal.AIR)) && (head == null || head.equals(Pal.AIR));
    }

    /** Eighteen spots round one tower: five along each long face, four at each end. */
    static List<Spot> spots(Layout.Tower t) {
        List<Spot> out = new ArrayList<>();
        int cx = t.cx(), cz = t.cz();
        for (int k = 0; k < 5; k++) {
            out.add(new Spot(cx + 5, cz - 4 + 2 * k, -90f));
            out.add(new Spot(cx - 5, cz + 4 - 2 * k, 90f));
            if (k < 4) {
                out.add(new Spot(cx - 3 + 2 * k, cz + 5, 0f));
                out.add(new Spot(cx + 3 - 2 * k, cz - 5, 180f));
            }
        }
        return out;
    }
}
