package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * Hall floor: basket-weave parquet of 2 wide boards in 4x4 tiles (spruce / oak / dark oak mix, tile tones
 * alternating like a chess board), worn lighter patches from smooth noise, a darker border strip along the
 * walls and a mangrove runner from the door to the podium.
 */
public final class Floor {
    private Floor() {
    }

    public static void build(BuildContext c) {
        for (int z = -92; z <= -1; z++) {
            for (int x = -36; x <= 36; x++) {
                c.set(x, 0, z, block(x, z));
            }
        }
    }

    static String block(int x, int z) {
        int ax = Math.abs(x);
        int d = Math.min(35 - ax, Math.min(z - Geo.Z_FRONT, Geo.Z_REAR - z));
        if (ax >= 36 || z <= -92 || z >= -1 || d <= 1) {
            return Pal.DARK_OAK;
        }
        if (d == 2) {
            return Pal.SPRUCE;
        }
        // runner down the central aisle
        if (z >= -84 && z <= -3) {
            if (ax <= 1) {
                return Noise.hash(x, z, 5) < 0.06 ? Pal.DARK_OAK : Pal.MANGROVE;
            }
            if (ax == 2) {
                return Pal.DARK_OAK;
            }
        }
        // basket weave parquet: 4x4 tiles of two 2 wide boards, boards alternate spruce / oak
        int tx = Math.floorDiv(x + 36, 4);
        int tz = Math.floorDiv(z + 92, 4);
        boolean alongX = ((tx + tz) & 1) == 0;
        int board = alongX ? Math.floorDiv(z + 92, 2) & 1 : Math.floorDiv(x + 36, 2) & 1;
        String wood = ((board + ((tx + tz) >> 1)) & 1) == 0 ? Pal.SPRUCE : Pal.OAK;
        double r = Noise.hash(tx, tz * 2 + board, 9);
        if (r < 0.07) {
            wood = Pal.DARK_OAK;
        } else if (r < 0.13) {
            wood = wood.equals(Pal.OAK) ? Pal.BIRCH : Pal.OAK;
        }
        // worn lighter patches and a few dark stains
        double wear = Noise.fbm2(x, z, 13, 31) + Math.max(0, 1 - ax / 10.0) * 0.12;
        if (wear > 0.7) {
            wood = lighter(wood);
        } else if (wear < 0.2) {
            wood = darker(wood);
        }
        return wood;
    }

    private static String lighter(String w) {
        return switch (w) {
            case Pal.DARK_OAK -> Pal.SPRUCE;
            case Pal.SPRUCE -> Pal.OAK;
            case Pal.OAK -> Pal.BIRCH;
            default -> w;
        };
    }

    private static String darker(String w) {
        return switch (w) {
            case Pal.BIRCH -> Pal.OAK;
            case Pal.OAK -> Pal.SPRUCE;
            case Pal.SPRUCE -> Pal.DARK_OAK;
            default -> w;
        };
    }
}
