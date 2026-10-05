package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * The manager's office (x[-6,6], z[-71,-63], 7 high): black tile floor with a pink rug, a broad desk and a tall pink
 * and black chair behind it facing the door, visitor chairs, bookshelves on the side walls, a square mask emblem on the
 * back panel and pendant lamps.
 */
final class Office {
    private Office() {
    }

    static void build(BuildContext c) {
        // rug
        for (int x = -4; x <= 4; x++) {
            for (int z = -69; z <= -64; z++) {
                boolean edge = x == -4 || x == 4 || z == -69 || z == -64;
                c.set(x, 0, z, edge ? "minecraft:white_carpet" : "minecraft:pink_carpet");
            }
        }
        // back panel (inside the room, 1 deep) with a big square emblem
        for (int x = -5; x <= 5; x++) {
            for (int y = 0; y <= 5; y++) {
                c.set(x, y, -71, y == 0 ? Pal.BLACK : Pal.PINK);
            }
        }
        Murals.paint(c, 2, true, -71, -2, 2, 1, 5, 0.5, 3.5, 2.0, Pal.WHITE, Pal.BLACK);
        // desk
        for (int x = -3; x <= 3; x++) {
            c.set(x, 0, -68, Pal.BLACK);
            c.set(x, 0, -67, Pal.BLACK);
            c.set(x, 1, -68, "minecraft:smooth_quartz_slab[type=bottom]");
            c.set(x, 1, -67, "minecraft:smooth_quartz_slab[type=bottom]");
        }
        c.set(-2, 2, -68, Pal.monitor("south"));
        c.set(2, 2, -68, Pal.monitor("south"));
        c.set(0, 2, -67, Pal.lantern(false));
        // manager chair: stair seat, armrests, tall back
        c.set(0, 0, -69, Pal.stairs("squidgame:pastel_pink_stairs", "north", false));
        c.set(-1, 0, -69, "squidgame:pastel_pink_slab[type=bottom]");
        c.set(1, 0, -69, "squidgame:pastel_pink_slab[type=bottom]");
        c.set(0, 1, -70, Pal.BLACK);
        c.set(0, 2, -70, Pal.PINK);
        // visitor chairs
        Props.chairBlack(c, -1, 0, -65, "north");
        Props.chairBlack(c, 1, 0, -65, "north");
        // bookshelves along the side walls (leave the walkway x -5..5 in front of the door free)
        for (int z = -70; z <= -64; z++) {
            for (int side = -1; side <= 1; side += 2) {
                c.set(side * 6, 0, z, "minecraft:bookshelf");
                c.set(side * 6, 1, z, "minecraft:bookshelf");
                c.set(side * 6, 2, z, (z & 1) == 0 ? "minecraft:bookshelf" : "minecraft:chiseled_bookshelf[facing=" + (side < 0 ? "east" : "west") + "]");
            }
        }
        // standing lamps in the corners of the rug zone
        for (int[] p2 : new int[][]{{-5, -64}, {5, -64}, {-5, -70}, {5, -70}}) {
            c.set(p2[0], 0, p2[1], "minecraft:dark_oak_fence");
            c.set(p2[0], 1, p2[1], "minecraft:dark_oak_fence");
            c.set(p2[0], 2, p2[1], Pal.lantern(false));
        }
        // ceiling lamps
        for (int[] p : new int[][]{{-3, -66}, {3, -66}, {0, -69}, {-2, -70}, {2, -70}}) {
            c.fill(p[0], 4, p[1], p[0], 6, p[1], Pal.chain("y"));
            c.set(p[0], 3, p[1], Pal.lantern(true));
        }
        for (int x = -4; x <= 4; x += 4) {
            c.set(x, 7, -66, Pal.LIGHT_WARM);
        }
        c.text(0.5, 6.0, -62.04 - 0.0, "MANAGER", "white", 1.2f, 180f, false);
        Props.camera(c, 6, 6, -63, "west");
    }
}
