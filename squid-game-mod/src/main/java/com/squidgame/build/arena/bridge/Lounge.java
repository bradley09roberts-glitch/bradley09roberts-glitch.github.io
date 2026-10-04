package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

/**
 * The finish lounge behind the exit door: a calm, bright mint-and-white hall (41 x 24 x 12) that survivors enter
 * through the 9 wide exit tunnel in the front wall. Floor lines of white light give level >= 12 everywhere; benches
 * stand along the walls, a monitor wall and a SAFE sign on the back wall.
 */
final class Lounge {
    private Lounge() {
    }

    private static final String MINT = "squidgame:pastel_mint";
    private static final String MINT_STAIRS = "squidgame:pastel_mint_stairs";

    /** True for cells covered by lounge furniture (no spawn spots there). */
    static boolean furniture(int x, int z) {
        return Math.abs(x) >= 17 || z >= Geo.LZ1 - 4;
    }

    static void build(BuildContext c) {
        int z0 = Geo.LZ0, z1 = Geo.LZ1;
        int xa = Geo.LX0, xb = Geo.LX1;
        // solid shell then carve the interior (floor layer y = 40, interior y 41..52)
        c.fill(xa - 2, 38, z0, xb + 2, 54, z1 + 2, Pal.BLK);
        c.clear(xa, 41, z0, xb, 52, z1);
        // floor: mint and white tiles with white light lines every 4 blocks
        c.pattern(xa, 40, z0, xb, 40, z1, (x, y, z) -> {
            if (Math.floorMod(x, 4) == 0) {
                return Pal.GREEN_LIGHT;
            }
            return ((x + z) & 1) == 0 ? Pal.TILE_W : MINT;
        });
        // walls: mint plates with white seams, black skirting, light trim
        wall(c, xa - 1, 41, z0, xa - 1, 52, z1);
        wall(c, xb + 1, 41, z0, xb + 1, 52, z1);
        wall(c, xa, 41, z1 + 1, xb, 52, z1 + 1);
        wall(c, xa, 41, Geo.FZ, xb, 52, Geo.FZ); // the hall's front wall seen from the lounge
        // re-open the exit tunnel (x -4..4, y 41..47) through the front wall layer
        c.clear(-4, 41, Geo.FZ - 2, 4, 47, Geo.FZ);
        // glowing green frame continues to the lounge side of the tunnel
        c.fill(-5, 41, Geo.FZ, -5, 48, Geo.FZ, Pal.GREEN_LIGHT);
        c.fill(5, 41, Geo.FZ, 5, 48, Geo.FZ, Pal.GREEN_LIGHT);
        c.fill(-5, 48, Geo.FZ, 5, 48, Geo.FZ, Pal.GREEN_LIGHT);
        // skirting, trim, green accent line
        for (int z = z0; z <= z1; z++) {
            for (int x : new int[]{xa - 1, xb + 1}) {
                c.set(x, 41, z, Pal.BLK);
                c.set(x, 42, z, Pal.GREEN_LIGHT);
                c.set(x, 52, z, Pal.PBS);
            }
        }
        for (int x = xa; x <= xb; x++) {
            for (int z : new int[]{z1 + 1, Geo.FZ}) {
                if (z == Geo.FZ && x >= -5 && x <= 5) {
                    continue; // tunnel mouth
                }
                c.set(x, 41, z, Pal.BLK);
                c.set(x, 42, z, Pal.GREEN_LIGHT);
                c.set(x, 52, z, Pal.PBS);
            }
        }
        // ceiling: black with white light panels and a mint border
        c.fill(xa, 53, z0, xb, 53, z1, Pal.BLK);
        for (int x = xa + 3; x <= xb - 3; x += 6) {
            for (int z = z0 + 3; z <= z1 - 3; z += 6) {
                c.fill(x, 52, z, x + 1, 52, z + 1, Pal.WHITE_LIGHT);
            }
        }
        // benches along both side walls (back against the wall) with gaps for passage
        for (int[] seg : new int[][]{{87, 92}, {96, 101}}) {
            for (int z = seg[0]; z <= seg[1]; z++) {
                c.set(xa, 41, z, Pal.stairs("dark_oak", "west", false));
                c.set(xb, 41, z, Pal.stairs("dark_oak", "east", false));
            }
        }
        // sofas along the back wall
        for (int[] seg : new int[][]{{-15, -7}, {7, 15}}) {
            for (int x = seg[0]; x <= seg[1]; x++) {
                c.set(x, 41, z1, MINT_STAIRS + "[facing=south,half=bottom,shape=straight]");
            }
        }
        // monitor wall (8 x 2) on the back wall, framed in black, with the SAFE sign above
        for (int x = -4; x <= 3; x++) {
            c.set(x, 46, z1 + 1, "squidgame:monitor[facing=north]");
            c.set(x, 47, z1 + 1, "squidgame:monitor[facing=north]");
        }
        c.fill(-5, 45, z1 + 1, 4, 45, z1 + 1, Pal.BLK);
        c.fill(-5, 48, z1 + 1, 4, 48, z1 + 1, Pal.BLK);
        c.fill(-5, 46, z1 + 1, -5, 47, z1 + 1, Pal.BLK);
        c.fill(4, 46, z1 + 1, 4, 47, z1 + 1, Pal.BLK);
        c.text(0.0, 50.2, z1 + 0.45, "SAFE", Hex.GREEN, 9f, 180f, false);
        c.text(0.0, 43.6, z1 + 0.45, "YOU CROSSED THE GLASS BRIDGE", "white", 2.4f, 180f, false);
        // sign over the tunnel mouth, seen from inside
        c.text(0.0, 49.5, Geo.FZ - 0.55, "THIS WAY BACK TO THE BRIDGE", "#9AA0A6", 1.6f, 0f, false);
    }

    private static void wall(BuildContext c, int x1, int y1, int z1, int x2, int y2, int z2) {
        c.pattern(x1, y1, z1, x2, y2, z2, (x, y, z) -> {
            int a = (x1 == x2) ? z : x;
            if (Math.floorMod(a, 6) == 0 || y == 51) {
                return Pal.WHITE;
            }
            return ((Pal.hash(a / 6, y / 4, 5) & 3) == 0) ? Pal.TILE_W : MINT;
        });
    }

    /** Colour constants for text. */
    static final class Hex {
        static final String GREEN = "#7DFF9A";

        private Hex() {
        }
    }
}
