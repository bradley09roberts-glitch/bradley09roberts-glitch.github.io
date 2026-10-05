package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * Weapons room (x[-21,-8], z[-71,-63]): rifle racks along the south wall above ammo cabinets with a hazard-striped
 * floor lane, a cleaning table with chairs, a mask board with the three guard masks, an ammunition cage.
 */
final class Armory {
    private Armory() {
    }

    static void build(BuildContext c) {
        // hazard stripe lane in front of the racks (floor layer)
        for (int x = -20; x <= -9; x++) {
            c.set(x, -1, -64, ((x & 1) == 0) ? "minecraft:yellow_concrete" : Pal.BLACK);
        }
        // ammo cabinets (y=0..1) along the south wall, rifles on rails above
        for (int x = -20; x <= -9; x++) {
            c.set(x, 0, -63, Pal.BLACK);
            c.set(x, 1, -63, (x & 1) == 0 ? "minecraft:barrel[facing=north,open=false]" : Pal.GRAY);
        }
        for (int x0 : new int[]{-20, -14}) {
            for (int level = 0; level < 2; level++) {
                int y = 3 + level * 2;
                rifleOnRail(c, x0, y, -63);
            }
        }
        // posts between the rack groups
        for (int y = 2; y <= 5; y++) {
            c.set(-15, y, -63, "minecraft:polished_blackstone_wall");
        }
        // mask board on the east wall (x=-7 plane): black panel with the three symbols
        for (int z = -70; z <= -64; z++) {
            for (int y = 1; y <= 4; y++) {
                c.set(-8, y, z, (y == 1 || y == 4 || z == -70 || z == -64) ? Pal.PINK : Pal.BLACK);
            }
        }
        c.set(-8, 3, -68, Pal.SYM_CIRCLE);
        c.set(-8, 3, -67, Pal.SYM_CIRCLE);
        c.set(-8, 3, -66, Pal.SYM_TRIANGLE);
        c.set(-8, 3, -65, Pal.SYM_SQUARE);
        c.set(-8, 2, -67, Pal.LIGHT_WHITE);
        c.text(-7.55 - 0.45 + 0.0, 4.8, -67.0, "MASKS", "#FFD84A", 1.0f, 90f, false);
        // cleaning table in the middle
        for (int x = -16; x <= -12; x++) {
            c.set(x, 0, -67, Pal.BLACK);
            c.set(x, 0, -66, Pal.BLACK);
            c.set(x, 1, -67, "minecraft:smooth_quartz_slab[type=bottom]");
            c.set(x, 1, -66, "minecraft:smooth_quartz_slab[type=bottom]");
        }
        for (int x : new int[]{-15, -13}) {
            Props.chairBlack(c, x, 0, -68, "south");
            Props.chairBlack(c, x, 0, -65, "north");
        }
        c.set(-14, 2, -66, Pal.lantern(false));
        // ammunition cage in the north-east corner (open to the south-west)
        for (int x = -11; x <= -8; x++) {
            c.fill(x, 0, -71, x, 3, -71, "minecraft:iron_bars");
        }
        for (int z = -71; z <= -69; z++) {
            c.fill(-11, 0, z, -11, 3, z, "minecraft:iron_bars");
        }
        c.fill(-11, 0, -69, -10, 3, -69, "minecraft:iron_bars");
        c.set(-10, 0, -70, "minecraft:barrel[facing=up,open=false]");
        c.set(-9, 0, -70, "minecraft:barrel[facing=up,open=false]");
        c.set(-9, 1, -70, "minecraft:barrel[facing=up,open=false]");
        c.set(-8, 0, -70, "minecraft:barrel[facing=up,open=false]");
        c.set(-10, 0, -71 + 0, "minecraft:barrel[facing=up,open=false]");
        // west side: lockers for gear
        for (int z : new int[]{-71, -70, -64, -63}) {
            final int zz = z;
            c.at(-21, 0, z, 3, () -> Props.locker(c, 1 + (zz & 1)));
        }
        // ceiling: strip lights, pendant lamps
        for (int x = -19; x <= -10; x += 3) {
            c.set(x, 6, -67, Pal.LIGHT_WHITE);
        }
        for (int[] p : new int[][]{{-17, -68}, {-12, -68}, {-17, -64}, {-12, -64}}) {
            c.set(p[0], 5, p[1], Pal.chain("y"));
            c.set(p[0], 4, p[1], Pal.lantern(true));
        }
        Props.camera(c, -8, 5, -63, "west");
    }

    /** A rifle lying on a slab rail along +x: stock, receiver, barrel. */
    static void rifleOnRail(BuildContext c, int x0, int y, int z) {
        for (int i = 0; i < 6; i++) {
            c.set(x0 + i, y - 1, z, "minecraft:polished_blackstone_slab[type=top]");
        }
        c.set(x0, y, z, "minecraft:dark_oak_slab[type=bottom]");
        c.set(x0 + 1, y, z, "minecraft:dark_oak_slab[type=bottom]");
        c.set(x0 + 2, y, z, "minecraft:polished_blackstone_slab[type=bottom]");
        c.set(x0 + 3, y, z, "minecraft:polished_blackstone_slab[type=bottom]");
        c.set(x0 + 4, y, z, Pal.chain("x"));
        c.set(x0 + 5, y, z, Pal.chain("x"));
    }
}
