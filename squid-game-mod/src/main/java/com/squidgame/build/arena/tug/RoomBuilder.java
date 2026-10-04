package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;

/**
 * The two annex rooms outside the hall's end walls, both at deck level (floor y = 40) on concrete piers:
 * the shared guards' waiting room (prefab) behind the west plateau, and the survivors' exit lounge behind the east one.
 * Both are 43 x 24 blocks (gate wall at x = -76 / 75) so the hall silhouette stays symmetric.
 */
final class RoomBuilder {
    private RoomBuilder() {
    }

    static final int GATE_W = -76;           // gate wall plane (block x) of the west room
    static final int GATE_E = 75;            // gate wall plane of the east lounge

    static void build(BuildContext c) {
        piers(c);
        // waiting room: the prefab's gate wall faces +Z locally, so rotate three quarter turns to face east (+X)
        c.at(GATE_W, Geo.DECK, 0, 3, () -> WaitingRoomPrefab.build(c, WaitingRoomPrefab.Spec.of("TUG OF WAR")));
        waitingRoomLight(c);
        lounge(c);
    }

    /** Solid concrete foundations under both annex rooms, paneled on the outside faces. */
    private static void piers(BuildContext c) {
        c.fill(-100, Geo.FOUND, -22, -77, 37, 22, Pal.CONC_D);
        c.fill(76, Geo.FOUND, -22, 99, 37, 22, Pal.CONC_D);
        // exterior faces: outer end wall and the two long sides
        Sym.pattern(c, -100, Geo.FOUND + 1, -22, -100, 37, 22, (x, y, z) -> Surf.wall(z, y - Geo.FOUND));
        Sym.pattern(c, -100, Geo.FOUND + 1, -22, -77, 37, -22, (x, y, z) -> Surf.wall(x, y - Geo.FOUND));
        Sym.pattern(c, -100, Geo.FOUND + 1, 22, -77, 37, 22, (x, y, z) -> Surf.wall(x, y - Geo.FOUND));
    }

    /**
     * The prefab's ceiling panels hang 8 blocks above the floor (only level 7 at floor level), so the shared room gets a
     * grid of invisible light sources just above head height (no collision, nothing to see) to reach level >= 10.
     */
    private static void waitingRoomLight(BuildContext c) {
        for (int x : new int[]{-96, -91, -86, -81, -77}) {
            for (int z : new int[]{-18, -13, -8, -3, 2, 7, 12, 17, 20}) {
                c.set(x, Geo.DECK + 2, z, "minecraft:light[level=15]");
            }
        }
    }

    // ------------------------------------------------------------------ exit lounge (east)

    private static void lounge(BuildContext c) {
        final int gx = GATE_E, bx = 98;                 // gate wall / back wall
        final int hz = 21;                               // side walls at z = +-21
        String wall = "minecraft:white_concrete";
        String trim = Pal.BLACK;
        // stone base under the shell, floor, shell
        c.fill(gx, 38, -22, bx + 1, 39, 22, Pal.BRICKS);
        c.fill(gx, 40, -hz, bx, 40, hz, "squidgame:tile_white");
        c.checker(gx + 1, 40, -hz + 1, bx - 1, hz - 1, "squidgame:tile_white", "squidgame:tile_black");
        c.fill(gx, 50, -hz, bx, 51, hz, wall);
        c.fill(gx, 41, -hz, bx, 49, hz, wall);                    // solid, then carve
        c.clear(gx + 1, 41, -hz + 1, bx - 1, 49, hz - 1);
        // trim bands and dado
        for (int x = gx; x <= bx; x++) {
            c.set(x, 41, -hz, trim);
            c.set(x, 42, -hz, Pal.YELLOW);
            c.set(x, 41, hz, trim);
            c.set(x, 42, hz, Pal.YELLOW);
        }
        for (int z = -hz; z <= hz; z++) {
            c.set(bx, 41, z, trim);
            c.set(bx, 42, z, Pal.YELLOW);
            c.set(gx, 41, z, trim);
            c.set(gx, 42, z, Pal.YELLOW);
        }
        // flush floor lights on a 5-block grid keep the whole lounge above level 10
        for (int x : new int[]{77, 82, 87, 92, 97}) {
            for (int z : new int[]{-18, -13, -8, -3, 2, 7, 12, 17, 20}) {
                c.set(x, 40, z, Pal.PANEL);
            }
        }
        // ceiling lights
        for (int x = gx + 4; x <= bx - 4; x += 6) {
            for (int z = -hz + 4; z <= hz - 4; z += 6) {
                c.fill(x, 49, z, x + 1, 49, z + 1, Pal.PANEL);
            }
        }
        // gate wall: central doorway 7 x 5 with black frame, glazed bays either side (mirror of the waiting room)
        c.clear(gx, 41, -3, gx, 45, 3);
        c.fill(gx, 41, -4, gx, 45, -4, trim);
        c.fill(gx, 41, 4, gx, 45, 4, trim);
        c.fill(gx, 46, -4, gx, 46, 4, trim);
        for (int side = -1; side <= 1; side += 2) {
            for (int z = 7; z <= 18; z++) {
                for (int y = 43; y <= 47; y++) {
                    c.set(gx, y, side * z, "minecraft:light_gray_stained_glass");
                }
            }
        }
        // results wall: monitors on the back wall under a title
        for (int z = -4; z <= 4; z++) {
            for (int y = 43; y <= 45; y++) {
                c.set(bx - 1, y, z, "squidgame:monitor[facing=west]");
            }
        }
        c.fill(bx - 1, 42, -5, bx - 1, 42, 5, trim);
        c.fill(bx - 1, 46, -5, bx - 1, 46, 5, trim);
        c.fill(bx - 1, 43, -5, bx - 1, 45, -5, trim);
        c.fill(bx - 1, 43, 5, bx - 1, 45, 5, trim);
        c.text(bx - 1.45, 47.6, 0.5, "SURVIVORS", "#FFD84A", 3.2f, 90f, false);
        c.text(bx - 1.45, 41.6 + 4.0, 0.5, "", "white", 1f, 90f, false);
        // benches along the side walls
        for (int x = gx + 4; x <= bx - 6; x += 3) {
            for (int side = -1; side <= 1; side += 2) {
                int z = side * (hz - 1);
                c.set(x, 41, z, "minecraft:dark_oak_slab[type=bottom]");
                c.set(x + 1, 41, z, "minecraft:dark_oak_slab[type=bottom]");
            }
        }
    }
}
