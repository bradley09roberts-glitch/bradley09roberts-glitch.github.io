package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

/**
 * Tile roofs built from stairs and slabs in the current frame. Stair shapes (inner / outer corners) are set
 * afterwards for the whole buffer by {@link Fixup#stairShapes}, so stairs are always placed with shape=straight.
 */
final class Roofs {
    private Roofs() {
    }

    /** A roof family: stairs / slab / full block ids. */
    record Family(String stair, String slab, String block) {
    }

    static final Family TILE = new Family(Mat.TILE_ST, Mat.TILE_SL, Mat.TILE);
    static final Family COBDS = new Family(Mat.COBDS_ST, Mat.COBDS_SL, Mat.COBDS);
    static final Family BLACK = new Family(Mat.BLK_ST, Mat.BLK_SL, Mat.BLACKSTONE);
    static final Family STONE = new Family(Mat.SB_ST, Mat.SB_SL, Mat.SB);
    static final Family MUD = new Family(Mat.MUDB_ST, Mat.MUDB_SL, Mat.MUDB);

    /**
     * Gable roof whose ridge runs along x. Columns z = zf..zb rise one block per column from y0 towards the centre.
     * {@code fill} is the solid material written under the stairs down to {@code wallTop+1} (the gable mass); the
     * end columns x0/x1 use {@code endFill}.
     */
    static void gableX(BuildContext c, int x0, int x1, int zf, int zb, int y0, Family f, int wallTop, String fill, String endFill) {
        int n = zb - zf + 1;
        for (int i = 0; i < n; i++) {
            int z = zf + i;
            int rise = Math.min(i, n - 1 - i);
            boolean ridgeOdd = n % 2 == 1 && i == n / 2;
            int h = y0 + rise;
            if (ridgeOdd) {
                c.fill(x0, h, z, x1, h, z, f.block());
            } else {
                String facing = i < n / 2 ? "south" : "north";
                c.fill(x0, h, z, x1, h, z, Mat.stair(f.stair(), facing));
            }
            // mass below
            if (h - 1 >= wallTop + 1) {
                c.fill(x0 + 1, wallTop + 1, z, x1 - 1, h - 1, z, fill);
                c.fill(x0, wallTop + 1, z, x0, h - 1, z, endFill);
                c.fill(x1, wallTop + 1, z, x1, h - 1, z, endFill);
            }
        }
    }

    /** Gable roof whose ridge runs along z (gable end towards -z). Columns x = x0..x1 rise towards the centre. */
    static void gableZ(BuildContext c, int x0, int x1, int z0, int z1, int y0, Family f, int wallTop, String fill, String endFill) {
        int n = x1 - x0 + 1;
        for (int i = 0; i < n; i++) {
            int x = x0 + i;
            int rise = Math.min(i, n - 1 - i);
            boolean ridgeOdd = n % 2 == 1 && i == n / 2;
            int h = y0 + rise;
            if (ridgeOdd) {
                c.fill(x, h, z0, x, h, z1, f.block());
            } else {
                String facing = i < n / 2 ? "east" : "west";
                c.fill(x, h, z0, x, h, z1, Mat.stair(f.stair(), facing));
            }
            if (h - 1 >= wallTop + 1) {
                c.fill(x, wallTop + 1, z0 + 1, x, h - 1, z1 - 1, fill);
                c.fill(x, wallTop + 1, z0, x, h - 1, z0, endFill);
                c.fill(x, wallTop + 1, z1, x, h - 1, z1, endFill);
            }
        }
    }

    /** Hip roof over the rectangle (x0..x1, z0..z1) starting at y0, one block rise per ring. */
    static void hip(BuildContext c, int x0, int z0, int x1, int z1, int y0, Family f, int wallTop, String fill) {
        int y = y0;
        while (x0 <= x1 && z0 <= z1) {
            int w = x1 - x0 + 1, d = z1 - z0 + 1;
            if (w >= 3 && d >= 3) {
                // ring of stairs facing inward-up
                c.fill(x0, y, z0, x1, y, z0, Mat.stair(f.stair(), "south"));
                c.fill(x0, y, z1, x1, y, z1, Mat.stair(f.stair(), "north"));
                c.fill(x0, y, z0 + 1, x0, y, z1 - 1, Mat.stair(f.stair(), "east"));
                c.fill(x1, y, z0 + 1, x1, y, z1 - 1, Mat.stair(f.stair(), "west"));
                if (y >= wallTop + 1) {
                    c.fill(x0 + 1, wallTop + 1, z0 + 1, x1 - 1, y, z1 - 1, fill);
                }
                x0++;
                z0++;
                x1--;
                z1--;
                y++;
            } else if (d == 2 && w >= 2) {
                // two stairs back to back along x, ends closed with stairs facing along x
                c.fill(x0, y, z0, x1, y, z0, Mat.stair(f.stair(), "south"));
                c.fill(x0, y, z1, x1, y, z1, Mat.stair(f.stair(), "north"));
                if (y - 1 >= wallTop + 1) {
                    c.fill(x0, wallTop + 1, z0, x1, y - 1, z1, fill);
                }
                break;
            } else if (w == 2 && d >= 2) {
                c.fill(x0, y, z0, x0, y, z1, Mat.stair(f.stair(), "east"));
                c.fill(x1, y, z0, x1, y, z1, Mat.stair(f.stair(), "west"));
                if (y - 1 >= wallTop + 1) {
                    c.fill(x0, wallTop + 1, z0, x1, y - 1, z1, fill);
                }
                break;
            } else {
                c.fill(x0, y, z0, x1, y, z1, f.block());
                if (y - 1 >= wallTop + 1) {
                    c.fill(x0, wallTop + 1, z0, x1, y - 1, z1, fill);
                }
                break;
            }
        }
    }
}
