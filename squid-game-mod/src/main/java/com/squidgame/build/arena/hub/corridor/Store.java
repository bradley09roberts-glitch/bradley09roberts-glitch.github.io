package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * Storage room (x[8,21], z[-71,-63]): stacks of the pink, ribbon-tied coffin boxes along the south wall, wooden crate
 * shelving along the north wall, loose coffins on pallets, a hand cart, pendant lamps. Aisles lead from the two doors.
 */
final class Store {
    private Store() {
    }

    static void build(BuildContext c) {
        // coffin stacks along the south wall: lying along x, 4 long x 2 deep
        stack(c, 8, -64, 3);
        stack(c, 13, -64, 2);
        stack(c, 17, -64, 3);
        // crate shelves along the north wall, leaving the door gap x 12..16
        shelf(c, 8, -71, 4);
        shelf(c, 17, -71, 5);
        // loose coffins on pallets in the room's middle
        pallet(c, 9, -68);
        coffin(c, 9, 1, -68, true);
        pallet(c, 9, -66);
        coffin(c, 9, 1, -66, true);
        c.set(19, 0, -68, "minecraft:barrel[facing=up,open=false]");
        c.set(20, 0, -68, "minecraft:barrel[facing=up,open=false]");
        c.set(20, 0, -67, "minecraft:barrel[facing=up,open=false]");
        // ceiling lamps
        for (int[] p : new int[][]{{10, -69}, {10, -65}, {19, -69}, {19, -65}, {14, -67}, {14, -70}, {17, -67}, {16, -65}}) {
            c.fill(p[0], 4, p[1], p[0], 5, p[1], Pal.chain("y"));
            c.set(p[0], 3, p[1], Pal.lantern(true));
        }
        for (int x = 9; x <= 20; x += 3) {
            c.set(x, 6, -67, Pal.LIGHT_WHITE);
        }
        c.text(14.5, 4.4, -72 + 1.04 - 0.0 + 0.0, "STORAGE", "white", 1.2f, 0f, false);
        Props.camera(c, 21, 5, -71, "south");
    }

    /** A coffin box lying along x (4 long, 2 deep) with a white ribbon band, h layers high starting at y=0. */
    static void stack(BuildContext c, int x0, int z0, int h) {
        for (int y = 0; y < h; y++) {
            coffin(c, x0, y, z0, false);
        }
    }

    /** One coffin: 4 x 1 x 2 (x by y by z). {@code single}: 2 long x 1 x 1 (a small box). */
    static void coffin(BuildContext c, int x0, int y, int z0, boolean small) {
        int len = small ? 2 : 4, dep = small ? 1 : 2;
        for (int i = 0; i < len; i++) {
            for (int k = 0; k < dep; k++) {
                boolean ribbon = i == (len == 4 ? 1 : 0);
                c.set(x0 + i, y, z0 + k, ribbon ? Pal.WHITE : Pal.PINK);
            }
        }
    }

    private static void pallet(BuildContext c, int x, int z) {
        c.set(x, 0, z, "minecraft:spruce_slab[type=bottom]");
        c.set(x + 1, 0, z, "minecraft:spruce_slab[type=bottom]");
    }

    /** Shelving unit of the given length along x against the north wall: three shelves with crates. */
    private static void shelf(BuildContext c, int x0, int z, int len) {
        for (int i = 0; i < len; i++) {
            int x = x0 + i;
            c.set(x, 0, z, "minecraft:spruce_planks");
            c.set(x, 1, z, (i & 1) == 0 ? "minecraft:barrel[facing=south,open=false]" : "minecraft:spruce_planks");
            c.set(x, 2, z, Pal.slab("minecraft:spruce_slab", false));
            c.set(x, 3, z, (i & 1) == 1 ? "minecraft:barrel[facing=south,open=false]" : "minecraft:air");
            c.set(x, 4, z, Pal.slab("minecraft:spruce_slab", false));
        }
        // end posts
        for (int y = 0; y <= 4; y++) {
            c.set(x0 - 1 < 8 ? x0 : x0 - 1, y, z, "minecraft:dark_oak_fence");
        }
    }
}
