package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

/**
 * Server racks along the south wall (z = 17..18) and under the mezzanine on the north side: black cabinet frames with
 * iron-bar fronts and glowing LED slats behind them (functional light for the service aisle), cable ducts with chain
 * looms running up to the ceiling.
 */
final class Racks {
    private Racks() {
    }

    static void build(BuildContext c) {
        // south wall: racks facing north, 8 high
        for (int x0 = 50; x0 <= 78; x0 += 4) {
            rack(c, x0, 18, "north", 19);
            loom(c, x0 + 3, 17, 20, 32);
        }
        // north side under the mezzanine: racks facing south, 6 high (deck at y=19)
        for (int x0 = 52; x0 <= 72; x0 += 4) {
            rack(c, x0, -18, "south", 17);
        }
    }

    /**
     * One rack 3 wide at x0..x0+2 against the wall at z = wallZ (the LED row), front bars one block in front.
     * {@code top} = y of the cap block.
     */
    private static void rack(BuildContext c, int x0, int wallZ, String facing, int top) {
        int fz = facing.equals("north") ? wallZ - 1 : wallZ + 1;
        for (int y = 12; y <= top; y++) {
            for (int dx = 0; dx <= 2; dx++) {
                int x = x0 + dx;
                boolean frame = dx != 1 || y == 12 || y == top;
                // LED row behind the bars
                String led = (y % 2 == 0) ? Pal.BLACK : (((x0 / 4 + y) & 1) == 0 ? Pal.SEA : Pal.LIGHT_PINK);
                c.set(x, y, wallZ, frame ? Pal.BLACK : led);
                c.set(x, y, fz, frame ? Pal.PBS : "minecraft:iron_bars");
            }
        }
    }

    private static void loom(BuildContext c, int x, int z, int y0, int y1) {
        c.fill(x, y0, z, x, y1, z, Pal.chain("y"));
        c.fill(x, y0, z + 1, x, y1, z + 1, Pal.chain("y"));
        c.set(x, 12, z, Pal.PBS);
        c.fill(x, 12, z + 1, x, y0 - 1, z + 1, Pal.BLACK);
    }
}
