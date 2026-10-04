package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Standing-spot grids: the start queue, the finish gathering spots and the deck panel markers. */
final class Spots {
    private Spots() {
    }

    record Cell(int x, int z) {
    }

    // ------------------------------------------------------------------ deck

    static void panels(BuildContext c) {
        for (int r = 0; r < Geo.ROWS; r++) {
            for (int lane = 0; lane < 2; lane++) {
                int x0 = Deck.laneX(lane);
                int z0 = Deck.rowZ(r);
                // top-centre of the 2x2 panel
                c.marker("bridge.panel", x0 + 1.0, Geo.STAND, z0 + 1.0, 0f, "row=" + r + ",lane=" + lane);
            }
        }
    }

    // ------------------------------------------------------------------ queue (start platform)

    /** Queue cells sorted by distance to the gate door centre; slot 0 is right at the gate. */
    static List<Cell> queueCells() {
        List<Cell> out = new ArrayList<>();
        for (int x = -11; x <= 10; x++) {
            for (int z = Geo.START_Z0; z <= Geo.GATE_Z - 1; z++) {
                if (Platforms.isSlot(x, z)) {
                    out.add(new Cell(x, z));
                }
            }
        }
        final double gx = 0.0, gz = Geo.GATE_Z + 0.5;
        out.sort(Comparator.<Cell>comparingDouble(k -> sq(k.x() + 0.5 - gx) + sq(k.z() + 0.5 - gz))
                .thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        return out;
    }

    static void queue(BuildContext c) {
        int n = 0;
        for (Cell k : queueCells()) {
            c.marker("bridge.queue", k.x() + 0.5, Geo.STAND, k.z() + 0.5, 0f, "slot=" + n++);
        }
    }

    // ------------------------------------------------------------------ finish (end platform + lounge)

    static boolean benchCell(int x, int z) {
        // benches along the exit wall either side of the door (viewing area): z 78..79, |x| >= 6
        return z >= 78 && (x <= -6 || x >= 5);
    }

    /** Finish cells on the end platform sorted by distance to the exit door (nearest first). */
    static List<Cell> finishPlatformCells() {
        List<Cell> out = new ArrayList<>();
        for (int x = -11; x <= 10; x++) {
            for (int z = Geo.END_Z0 + 1; z <= Geo.END_Z1; z++) {
                if (benchCell(x, z)) {
                    continue;
                }
                if (Platforms.isSlot(x, Geo.mz(z))) {
                    out.add(new Cell(x, z));
                }
            }
        }
        final double dx = 0.0, dz = Geo.END_Z1 + 1.5;
        out.sort(Comparator.<Cell>comparingDouble(k -> sq(k.x() + 0.5 - dx) + sq(k.z() + 0.5 - dz))
                .thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        return out;
    }

    /** Lounge cells (quincunx), nearest to the exit door first. */
    static List<Cell> loungeCells(int max) {
        List<Cell> out = new ArrayList<>();
        for (int x = -15; x <= 15; x++) {
            for (int z = Geo.LZ0 + 4; z <= Geo.LZ1 - 6; z++) {
                int xm = x >= 0 ? x : Geo.mx(x);
                if (((xm + z) & 1) == 0 && !Lounge.furniture(x, z)) {
                    out.add(new Cell(x, z));
                }
            }
        }
        final double dx = 0.0, dz = Geo.LZ0;
        out.sort(Comparator.<Cell>comparingDouble(k -> sq(k.x() + 0.5 - dx) + sq(k.z() + 0.5 - dz))
                .thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        return out.size() > max ? out.subList(0, max) : out;
    }

    static void finish(BuildContext c) {
        int n = 0;
        for (Cell k : finishPlatformCells()) {
            c.marker("bridge.finish_spawn", k.x() + 0.5, Geo.STAND, k.z() + 0.5, 180f, "slot=" + n++);
        }
        for (Cell k : loungeCells(90)) {
            c.marker("bridge.finish_spawn", k.x() + 0.5, Geo.STAND, k.z() + 0.5, 180f, "slot=" + n++);
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
