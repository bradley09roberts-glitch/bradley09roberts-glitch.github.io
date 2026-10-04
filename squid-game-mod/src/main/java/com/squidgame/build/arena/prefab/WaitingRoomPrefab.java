package com.squidgame.build.arena.prefab;

import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;

/**
 * The standard guards' waiting room used in front of every game arena: pink-and-white tiled hall with black trim,
 * benches along the side walls, a rules board with the three symbols, a glazed gate wall and lit ceiling.
 *
 * <p>Local frame: the gate wall is the plane z = 0 and the room extends towards -Z (the arena is at +Z); x is
 * centred on 0; floor block at y = 0 (stand height 1.0). Place it with {@code ctx.at(dx, dy, dz, rot, ...)}.
 * Emits waiting.spawn (grid, >= capacity), waiting.player_entry, gate.door, waiting.bounds, guard.post.
 */
public final class WaitingRoomPrefab {
    public record Spec(int width, int depth, int height, String title, String subtitle, int capacity) {
        public Spec {
            if (width % 2 == 0) {
                width++;
            }
        }

        public static Spec of(String title) {
            return new Spec(41, 22, 9, title, "WAIT FOR THE GAME TO START", 144);
        }
    }

    private static final String WALL = "minecraft:white_concrete";
    private static final String TRIM = "minecraft:black_concrete";
    private static final String PINK = "minecraft:pink_concrete";

    private WaitingRoomPrefab() {
    }

    public static void build(BuildContext c, Spec s) {
        int hw = s.width() / 2;
        int d = s.depth();
        int h = s.height();
        // shell
        c.fill(-hw - 2, -2, -d - 2, hw + 2, -1, 1, "minecraft:stone_bricks");
        c.fill(-hw - 1, h + 1, -d - 1, hw + 1, h + 2, 0, WALL);
        c.fill(-hw - 1, 0, -d - 1, hw + 1, 0, 0, "squidgame:tile_white");
        c.checker(-hw, 0, -d, hw, -1, "squidgame:tile_white", "squidgame:tile_pink");
        c.clear(-hw, 1, -d, hw, h, -1);
        c.fill(-hw - 1, 1, -d - 1, -hw - 1, h, 0, WALL);
        c.fill(hw + 1, 1, -d - 1, hw + 1, h, 0, WALL);
        c.fill(-hw - 1, 1, -d - 1, hw + 1, h, -d - 1, WALL);
        c.fill(-hw - 1, 1, 0, hw + 1, h, 0, WALL);
        // trim bands and pink dado
        for (int x = -hw - 1; x <= hw + 1; x++) {
            c.set(x, 1, -d - 1, TRIM);
            c.set(x, 2, -d - 1, PINK);
            c.set(x, 1, 0, TRIM);
            c.set(x, 2, 0, PINK);
        }
        for (int z = -d - 1; z <= 0; z++) {
            c.set(-hw - 1, 1, z, TRIM);
            c.set(-hw - 1, 2, z, PINK);
            c.set(hw + 1, 1, z, TRIM);
            c.set(hw + 1, 2, z, PINK);
        }
        // ceiling lights
        for (int x = -hw + 3; x <= hw - 3; x += 6) {
            for (int z = -d + 3; z <= -3; z += 6) {
                c.fill(x, h, z, x + 1, h, z + 1, "squidgame:panel_light_white");
            }
        }
        // gate wall: central doorway 7 x 5, windows either side, black lintel
        c.clear(-3, 1, 0, 3, 5, 0);
        c.fill(-4, 1, 0, -4, 5, 0, TRIM);
        c.fill(4, 1, 0, 4, 5, 0, TRIM);
        c.fill(-3, 6, 0, 3, 6, 0, TRIM);
        for (int side = -1; side <= 1; side += 2) {
            for (int x = 7; x <= hw - 3; x++) {
                for (int y = 3; y <= h - 2; y++) {
                    c.set(side * x, y, 0, "minecraft:light_gray_stained_glass");
                }
            }
        }
        // rules board on the back wall with the three symbols above
        c.fill(-12, 3, -d, 12, 7, -d, TRIM);
        c.fill(-11, 4, -d, 11, 6, -d, "minecraft:black_concrete");
        c.set(-4, h, -d, "squidgame:symbol_circle");
        c.set(0, h, -d, "squidgame:symbol_triangle");
        c.set(4, h, -d, "squidgame:symbol_square");
        c.text(0.5, 6.0, -d + 0.45, s.title(), "#FFD84A", 2.6f, 0f, false);
        c.text(0.5, 4.6, -d + 0.45, s.subtitle(), "white", 1.4f, 0f, false);
        // benches along the side walls
        for (int z = -d + 3; z <= -6; z += 3) {
            for (int side = -1; side <= 1; side += 2) {
                int x = side * (hw - 1);
                c.set(x, 1, z, "minecraft:dark_oak_slab[type=bottom]");
                c.set(x, 1, z + 1, "minecraft:dark_oak_slab[type=bottom]");
            }
        }
        // spawn grid facing the gate (+Z)
        int slot = 0;
        outer:
        for (double z = -4.5; z > -d + 1; z -= 1.6) {
            for (double x = -hw + 4.5; x <= hw - 4; x += 1.6) {
                c.marker(CommonMarkers.WAITING_SPAWN, Math.floor(x) + 0.5, 1.0, Math.floor(z) + 0.5, 0f, "slot=" + slot++);
                if (slot >= Math.max(s.capacity(), 128) + 12) {
                    break outer;
                }
            }
        }
        c.marker(CommonMarkers.WAITING_PLAYER_ENTRY, 0.5, 1.0, -d + 1.5, 0f);
        c.marker(CommonMarkers.GATE_DOOR, 0.5, 1.0, 0.5, 0f, "w=7,h=5");
        c.region(CommonMarkers.REGION_WAITING, -hw - 1, 0, -d - 1, hw + 1, h + 2, 0);
        // guards: armed soldiers by the gate, a square-masked manager at the back, workers by the walls
        c.marker(CommonMarkers.GUARD_POST, -6.5, 1.0, -2.5, 0f, "rank=triangle");
        c.marker(CommonMarkers.GUARD_POST, 7.5, 1.0, -2.5, 0f, "rank=triangle");
        c.marker(CommonMarkers.GUARD_POST, 0.5, 1.0, -d + 2.5, 0f, "rank=square");
        c.marker(CommonMarkers.GUARD_POST, -hw + 1.5, 1.0, -d / 2.0, -90f, "rank=circle");
        c.marker(CommonMarkers.GUARD_POST, hw - 0.5, 1.0, -d / 2.0, 90f, "rank=circle");
    }
}
