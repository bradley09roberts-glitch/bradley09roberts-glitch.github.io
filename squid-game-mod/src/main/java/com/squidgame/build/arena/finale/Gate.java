package com.squidgame.build.arena.finale;

import com.squidgame.build.BlockBuffer;
import com.squidgame.build.BuildContext;
import com.squidgame.build.Marker;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;

import java.util.ArrayList;

import static com.squidgame.build.arena.finale.Layout.*;

/**
 * The south end: the shared waiting room (prefab, rotated to face north and raised two blocks), its landing, the
 * half-block ramp that leads through the rim wall down to the sand, and the symbol marquee over the gate.
 */
final class Gate {
    private Gate() {
    }

    private static final String STEP = "minecraft:polished_andesite";
    private static final String STEP_SLAB = "minecraft:polished_andesite_slab[type=bottom]";
    private static final String EDGE = "minecraft:stone_bricks";

    static void build(BuildContext c) {
        // the room: gate wall at z = GATE_Z, room extends towards +Z, floor block at y = ROOM_DY
        c.at(0, ROOM_DY, GATE_Z, 2, () -> WaitingRoomPrefab.build(c,
                new WaitingRoomPrefab.Spec(41, 22, 9, "THE FINAL", "ATTACKERS REACH THE HEAD CIRCLE - DEFENDERS HOLD THE LINE", 144)));

        // the prefab puts its two worker posts on the block edge between two bench cells; rotated by half a turn they land on
        // a bench slab, so free the cells of any guard post inside the room
        for (Marker m : new ArrayList<>(c.buffer().markers("guard.post"))) {
            if (m.z() > c.originZ() + GATE_Z) {
                for (int dy = 0; dy < 2; dy++) {
                    if (c.buffer().isSolidSet(m.bx(), m.by() + dy, m.bz())) {
                        c.buffer().set(m.bx(), m.by() + dy, m.bz(), BlockBuffer.AIR);
                    }
                }
            }
        }

        final int d = ROOM_DY;
        // open the rim wall for the ramp
        c.clear(-RAMP_HALF - 1, 1, WZ1 - 1, RAMP_HALF + 1, 8, WZ1);
        // solid body of landing + ramp
        c.fill(-9, -2, 63, 9, d - 1, 65, EDGE);
        c.fill(-RAMP_HALF, -2, 60, RAMP_HALF, 0, 62, EDGE);
        // landing floor (flush with the room floor)
        for (int x = -9; x <= 9; x++) {
            for (int z = 63; z <= 65; z++) {
                boolean border = Math.abs(x) == 9 || z == 63;
                c.set(x, d, z, border ? "minecraft:stone_bricks" : STEP);
            }
        }
        // ramp: 3.0 (landing) -> 2.5 -> 2.0 -> 1.5 -> 1.0 (sand)
        for (int x = -RAMP_HALF; x <= RAMP_HALF; x++) {
            c.set(x, 1, 62, EDGE);
            c.set(x, d, 62, STEP_SLAB);
            c.set(x, 1, 61, STEP);
            c.set(x, 1, 60, STEP_SLAB);
        }
        c.fill(-RAMP_HALF, 0, 60, RAMP_HALF, 0, 62, EDGE);
        // piers at the ramp mouth
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            int x0 = sgn < 0 ? -RAMP_HALF - 2 : RAMP_HALF + 1;
            c.fill(x0, 1, WZ1 - 1, x0 + 1, 4, WZ1, "minecraft:stone_bricks");
            c.fill(x0, 5, WZ1 - 1, x0 + 1, 5, WZ1, "minecraft:deepslate_tile_slab[type=bottom]");
            c.set(x0, 6, WZ1 - 1, "minecraft:lantern[hanging=false]");
            c.set(x0 + 1, 6, WZ1, "minecraft:lantern[hanging=false]");
        }
        // railings along the landing sides and a lamp post at each corner
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            int x = sgn * 9;
            c.fill(x, d + 1, 63, x, d + 1, 65, "minecraft:iron_bars");
            c.fill(x, d + 1, 65, x, d + 5, 65, "minecraft:stone_brick_wall");
            c.set(x, d + 6, 65, "minecraft:lantern[hanging=false]");
        }
        // the symbol marquee above the door (outside face of the gate wall)
        c.fill(-9, d + 7, 65, 9, d + 9, 65, "minecraft:black_concrete");
        c.fill(-9, d + 7, 65, 9, d + 7, 65, "minecraft:pink_concrete");
        c.fill(-9, d + 9, 65, 9, d + 9, 65, "minecraft:pink_concrete");
        // seen from the yard (looking south) +x is on the left: circle, triangle, square read left to right
        c.set(4, d + 8, 65, "squidgame:symbol_circle");
        c.set(0, d + 8, 65, "squidgame:symbol_triangle");
        c.set(-4, d + 8, 65, "squidgame:symbol_square");

        billboard(c);

        // circle-rank workers at the gate (stand on the landing)
        for (double x : new double[]{-7.5, -4.5, 5.5, 8.5}) {
            c.marker("guard.post", x, d + 1.0, 64.5, 180f, "rank=circle");
        }
    }

    /** A black board on the roof edge of the waiting room carrying the three symbols in big pink outlines. */
    private static void billboard(BuildContext c) {
        final String pink = "minecraft:pink_concrete";
        final int y0 = ROOM_DY + 12, h = 9, cy = y0 + 4, z = GATE_Z;
        c.fill(-15, y0, z, 15, y0 + h - 1, z + 1, "minecraft:black_concrete");
        c.fill(-15, y0, z, 15, y0, z, pink);
        c.fill(-15, y0 + h - 1, z, 15, y0 + h - 1, z, pink);
        c.fill(-15, y0, z, -15, y0 + h - 1, z, pink);
        c.fill(15, y0, z, 15, y0 + h - 1, z, pink);
        // circle
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 3.6 && d > 2.5) {
                    c.set(9 + dx, cy + dy, z, pink);
                }
            }
        }
        // triangle
        for (int dx = -4; dx <= 4; dx++) {
            c.set(dx, cy - 3, z, pink);
        }
        c.line(0, cy + 3, z, -4, cy - 3, z, pink);
        c.line(0, cy + 3, z, 4, cy - 3, z, pink);
        // square
        for (int i = -3; i <= 3; i++) {
            c.set(-9 + i, cy + 3, z, pink);
            c.set(-9 + i, cy - 3, z, pink);
            c.set(-9 - 3, cy + i, z, pink);
            c.set(-9 + 3, cy + i, z, pink);
        }
        // spotlights along the top edge
        for (int x = -12; x <= 12; x += 6) {
            c.set(x, y0 + h, z, "minecraft:shroomlight");
        }
    }
}
