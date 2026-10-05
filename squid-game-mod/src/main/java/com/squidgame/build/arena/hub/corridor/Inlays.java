package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.arena.hub.corridor.Plan.Kind;
import com.squidgame.build.arena.hub.corridor.Plan.Space;

/**
 * Floor art of the long corridors: 7x7 symbol inlays (white tile outline on black tile, the apex / top of the symbol
 * pointing along the direction of travel) and chevron arrows on the pink runner showing the way to the stairway.
 */
final class Inlays {
    private Inlays() {
    }

    /** kind 0 circle / 1 triangle / 2 square; (fx,fz) = unit vector the symbol points to. */
    private record Inlay(int kind, int cx, int cz, int fx, int fz) {
    }

    private static final Inlay[] LIST = {
            new Inlay(0, 14, -48, 1, 0),     // E1 east
            new Inlay(0, -14, -48, -1, 0),   // E1 west
            new Inlay(1, 26, -58, 0, -1),    // N1
            new Inlay(2, 26, -66, 0, -1),
            new Inlay(1, -26, -58, 0, -1),   // N2
            new Inlay(2, -26, -66, 0, -1),
            new Inlay(1, 15, -76, -1, 0),    // E2 east / west
            new Inlay(1, -15, -76, 1, 0),
    };

    /** The inlay block at a floor cell or null if the cell is not covered. */
    static String at(int x, int z) {
        for (Inlay in : LIST) {
            int dx = x - in.cx, dz = z - in.cz;
            if (Math.abs(dx) > 3 || Math.abs(dz) > 3) {
                continue;
            }
            int rx = -in.fz, rz = in.fx;
            double du = dx * rx + dz * rz;
            double dv = dx * in.fx + dz * in.fz;
            return Murals.onSymbol(in.kind, du, dv, 3.0, 1.15) ? Pal.TILE_WHITE : Pal.TILE_BLACK;
        }
        return null;
    }

    /** +1 / -1 = direction of travel along the corridor's u axis towards the stairway; 0 = none (dead end). */
    static int routeDir(Space s, int x, int z) {
        return switch (s.name) {
            case "e1" -> x > 0 ? 1 : -1;
            case "n1", "n2" -> -1;
            case "e2" -> x > 0 ? -1 : 1;
            default -> 0;
        };
    }

    /** Chevron arrow cell on the runner (v in -1..1) or null. */
    static String arrow(Space s, int x, int z) {
        int dir = routeDir(s, x, z);
        if (dir == 0) {
            return null;
        }
        int u = s.kind == Kind.CORRIDOR_X ? x : z;
        int v = Surfaces.v(s, x, z);
        int t = u * dir;
        int m = Math.floorMod(t, 6);
        if ((m == 3 && Math.abs(v) == 1) || (m == 4 && v == 0)) {
            return Pal.TILE_WHITE;
        }
        return null;
    }
}
