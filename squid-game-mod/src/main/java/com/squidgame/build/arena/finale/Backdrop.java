package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.finale.Layout.*;

/**
 * The high painted sunset wall that encloses the whole stage ("all around"): a 3 thick flat of dark concrete whose
 * inner plane carries the {@link Mural}; a dark cornice caps it and the back carries timber battens like the back of a
 * film-set flat. The wall is the outer limit of the arena (nobody can leave or fall out of the world).
 */
final class Backdrop {
    private Backdrop() {
    }

    static void build(BuildContext c) {
        final int h = BACKDROP_H;
        final int w = BX1 - BX0, d = BZ1 - BZ0;           // 152, 170
        final int perimeter = 2 * (w + d);                 // paint-plane perimeter measured on the centre line
        // the sun hangs low on the west wall, straight behind the court
        int sunZ = (ZC + ZQ) / 2;
        double sunS = (w) + (d) + (w) + (BZ1 - sunZ);
        Mural m = new Mural(perimeter, h, sunS);

        // backing slabs (dark), 2 thick behind the paint plane, from the footing to the top
        c.fill(BX0 - 2, -8, BZ0 - 2, BX1 + 2, h + 1, BZ0 - 1, "minecraft:gray_concrete");       // north
        c.fill(BX0 - 2, -8, BZ1 + 1, BX1 + 2, h + 1, BZ1 + 2, "minecraft:gray_concrete");       // south
        c.fill(BX0 - 2, -8, BZ0 - 2, BX0 - 1, h + 1, BZ1 + 2, "minecraft:gray_concrete");       // west
        c.fill(BX1 + 1, -8, BZ0 - 2, BX1 + 2, h + 1, BZ1 + 2, "minecraft:gray_concrete");       // east

        // paint plane
        for (int y = 1; y <= h; y++) {
            for (int x = BX0; x <= BX1; x++) {
                c.set(x, y, BZ0, m.block(x - BX0, y));                          // north: west -> east, s = 0..w
                c.set(x, y, BZ1, m.block(w + d + (BX1 - x), y));                // south: east -> west
            }
            for (int z = BZ0 + 1; z < BZ1; z++) {
                c.set(BX1, y, z, m.block(w + (z - BZ0), y));                    // east: north -> south
                c.set(BX0, y, z, m.block(w + d + w + (BZ1 - z), y));            // west: south -> north
            }
        }
        // a dark plinth line at the foot of the painted plane (below the skyline)
        for (int x = BX0; x <= BX1; x++) {
            c.set(x, 0, BZ0, "minecraft:black_concrete");
            c.set(x, 0, BZ1, "minecraft:black_concrete");
        }
        for (int z = BZ0; z <= BZ1; z++) {
            c.set(BX0, 0, z, "minecraft:black_concrete");
            c.set(BX1, 0, z, "minecraft:black_concrete");
        }
        // cornice: a dark coping that overhangs the painted plane by one block
        c.fill(BX0 - 2, h + 2, BZ0 - 2, BX1 + 2, h + 2, BZ0 + 1, "minecraft:polished_blackstone");
        c.fill(BX0 - 2, h + 2, BZ1 - 1, BX1 + 2, h + 2, BZ1 + 2, "minecraft:polished_blackstone");
        c.fill(BX0 - 2, h + 2, BZ0 - 2, BX0 + 1, h + 2, BZ1 + 2, "minecraft:polished_blackstone");
        c.fill(BX1 - 1, h + 2, BZ0 - 2, BX1 + 2, h + 2, BZ1 + 2, "minecraft:polished_blackstone");
        // back of the flats: vertical timber battens every 10 blocks (visible only from outside)
        for (int x = BX0 - 2; x <= BX1 + 2; x += 10) {
            c.fill(x, 1, BZ0 - 3, x, h + 1, BZ0 - 3, "minecraft:dark_oak_log[axis=y]");
            c.fill(x, 1, BZ1 + 3, x, h + 1, BZ1 + 3, "minecraft:dark_oak_log[axis=y]");
        }
        for (int z = BZ0 - 2; z <= BZ1 + 2; z += 10) {
            c.fill(BX0 - 3, 1, z, BX0 - 3, h + 1, z, "minecraft:dark_oak_log[axis=y]");
            c.fill(BX1 + 3, 1, z, BX1 + 3, h + 1, z, "minecraft:dark_oak_log[axis=y]");
        }
    }
}
