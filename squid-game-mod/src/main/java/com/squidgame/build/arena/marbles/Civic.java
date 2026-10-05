package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

/**
 * Special plots and the houses framing the square: the exit courtyard (behind the hall), the lookout tower (spectator
 * vantage), the small shrine, and two-storey shop houses on the west and east edges of the square.
 */
final class Civic {
    private Civic() {
    }

    static final int TOWER_DECK_Y = 16;       // standing height on the tower deck

    // ------------------------------------------------------------------ exit courtyard (court frame)

    static void exitCourt(BuildContext c, House.Spec house) {
        Court.walls(c);
        Court.gate(c, "REST");
        // garden floor: stone path in the middle, moss and earth beds at the sides
        c.pattern(-3, 0, 0, 3, 0, 11, (x, y, z) -> {
            double r = U.rand(c.worldX(x, z), c.worldZ(x, z), 601);
            if (Math.abs(x) <= 1) {
                return r < 0.2 ? Mat.SB_CRACK : r < 0.35 ? Mat.P_ANDESITE : Mat.SB;
            }
            return r < 0.5 ? "minecraft:moss_block" : r < 0.8 ? Mat.COARSE : Mat.MOSSY_COBBLE;
        });
        // pond on the west bed
        c.fill(-3, 0, 4, -2, 0, 8, "minecraft:water");
        c.fill(-3, 1, 3, -2, 1, 3, Mat.slabB(Mat.SB_SL));
        c.fill(-3, 1, 9, -2, 1, 9, Mat.slabB(Mat.SB_SL));
        c.set(-3, 1, 6, "minecraft:lily_pad");
        c.set(-2, 1, 5, "minecraft:lily_pad");
        // two small trees
        smallTree(c, 3, 5);
        smallTree(c, -3, 11);
        // stone lamps along the path
        for (int z : new int[]{2, 6, 10}) {
            Props.stoneLamp(c, -2, 1, z == 6 ? 9 : z);
            Props.stoneLamp(c, 2, 1, z);
        }
        // bench by the pond
        c.set(2, 1, 8, Mat.stair("minecraft:spruce_stairs", "east"));
        c.set(2, 1, 9, Mat.stair("minecraft:spruce_stairs", "east"));
        // the house at the far end, with a sign
        house.plaque = true;
        house.label = "EXIT";
        c.at(0, 0, Layout.COURT_LEN, 0, () -> House.build(c, house));
        c.marker("arena.exit", 0.5, 1.0, 5.5, U.yaw(c, 0f));
        c.marker("marbles.exit_gather", 1.5, 1.0, 6.5, U.yaw(c, 0f), "slot=0");
    }

    static void smallTree(BuildContext c, int x, int z) {
        c.fill(x, 1, z, x, 4, z, Mat.log("minecraft:cherry_log", "y"));
        c.pattern(x - 2, 4, z - 2, x + 2, 7, z + 2, (xx, y, zz) -> {
            double d = Math.hypot(xx - x, zz - z) + Math.abs(y - 5.5) * 0.9;
            if (d > 2.6 || (xx == x && zz == z && y < 6)) {
                return null;
            }
            return U.rand(xx, y * 3 + zz, 612) < 0.7 ? Mat.leaves("minecraft:cherry_leaves") : Mat.leaves("minecraft:azalea_leaves");
        });
    }

    // ------------------------------------------------------------------ lookout tower (court frame)

    static void tower(BuildContext c, House.Spec house) {
        Court.walls(c);
        Court.gate(c, "LOOKOUT");
        c.pattern(-3, 0, 0, 3, 0, 11, (x, y, z) -> {
            double r = U.rand(c.worldX(x, z), c.worldZ(x, z), 621);
            return r < 0.4 ? Mat.SB : r < 0.6 ? Mat.COBBLE : r < 0.75 ? Mat.P_ANDESITE : r < 0.9 ? Mat.GRAVEL : Mat.SB_MOSS;
        });
        // plinths and pillars
        int[][] ps = {{-3, 4}, {3, 4}, {-3, 10}, {3, 10}};
        for (int[] p : ps) {
            c.fill(p[0] - 1, 1, p[1] - 1, p[0] + 1, 1, p[1] + 1, Mat.SB);
            c.fill(p[0], 2, p[1], p[0], 14, p[1], Mat.log(Mat.LOG, "y"));
            c.set(p[0], 2, p[1], Mat.SB);
        }
        // cross beams at two levels with x braces
        for (int y : new int[]{7, 13}) {
            c.fill(-3, y, 4, 3, y, 4, Mat.log(Mat.LOG_S, "x"));
            c.fill(-3, y, 10, 3, y, 10, Mat.log(Mat.LOG_S, "x"));
            c.fill(-3, y, 5, -3, y, 9, Mat.log(Mat.LOG_S, "z"));
            c.fill(3, y, 5, 3, y, 9, Mat.log(Mat.LOG_S, "z"));
        }
        // deck
        c.fill(-4, 15, 3, 4, 15, 11, Mat.PLANKS);
        c.fill(-4, 15, 3, 4, 15, 3, Mat.log(Mat.LOG_S, "x"));
        c.fill(-4, 15, 11, 4, 15, 11, Mat.log(Mat.LOG_S, "x"));
        c.fill(-4, 15, 3, -4, 15, 11, Mat.log(Mat.LOG_S, "z"));
        c.fill(4, 15, 3, 4, 15, 11, Mat.log(Mat.LOG_S, "z"));
        c.clear(-2, 15, 10, -2, 15, 10);
        // railing
        c.fill(-4, 16, 3, 4, 16, 3, Mat.FENCE);
        c.fill(-4, 16, 11, 4, 16, 11, Mat.FENCE);
        c.fill(-4, 16, 3, -4, 16, 11, Mat.FENCE);
        c.fill(4, 16, 3, 4, 16, 11, Mat.FENCE);
        // ladder through the hatch on the west pillar side
        c.fill(-2, 1, 10, -2, 15, 10, "minecraft:ladder[facing=east]");
        // roof posts and roof
        for (int[] p : new int[][]{{-4, 3}, {4, 3}, {-4, 11}, {4, 11}}) {
            c.fill(p[0], 17, p[1], p[0], 19, p[1], Mat.log(Mat.LOG, "y"));
        }
        c.fill(-4, 19, 3, 4, 19, 3, Mat.log(Mat.LOG_S, "x"));
        c.fill(-4, 19, 11, 4, 19, 11, Mat.log(Mat.LOG_S, "x"));
        c.fill(-4, 19, 3, -4, 19, 11, Mat.log(Mat.LOG_S, "z"));
        c.fill(4, 19, 3, 4, 19, 11, Mat.log(Mat.LOG_S, "z"));
        Roofs.hip(c, -6, 1, 6, 13, 20, Roofs.COPPER, 19, Roofs.COPPER.block());
        c.set(0, 27, 7, "minecraft:lightning_rod[facing=up]");
        // lanterns under the roof: ring and a big paper lantern in the middle
        c.set(-4, 18, 7, Mat.lantern(true));
        c.set(4, 18, 7, Mat.lantern(true));
        c.set(0, 18, 3, Mat.lantern(true));
        c.set(0, 18, 11, Mat.lantern(true));
        c.set(0, 19, 7, Mat.CHAIN_Y);
        c.set(0, 18, 7, Mat.lantern(true));
        // ground lamps and benches under the tower
        Props.stoneLamp(c, -3, 1, 1);
        Props.stoneLamp(c, 3, 1, 1);
        Props.stoneLamp(c, -3, 1, 8);
        Props.stoneLamp(c, 3, 1, 8);
        c.set(-1, 1, 7, Mat.stair("minecraft:spruce_stairs", "east"));
        c.set(1, 1, 7, Mat.stair("minecraft:spruce_stairs", "west"));
        house.plaque = true;
        house.label = "LOOKOUT";
        c.at(0, 0, Layout.COURT_LEN, 0, () -> House.build(c, house));
        c.marker("arena.spectator", 0.5, TOWER_DECK_Y, 7.5, U.yaw(c, 180f));
        c.marker("marbles.tower", 0.5, 1.0, 7.5, 0f, "stand=0");
    }

    // ------------------------------------------------------------------ shrine (court frame)

    static void shrine(BuildContext c, House.Spec house) {
        Court.walls(c);
        Court.gate(c, "SHRINE");
        // raked gravel garden with a stone path
        c.pattern(-3, 0, 0, 3, 0, 11, (x, y, z) -> {
            if (Math.abs(x) <= 1) {
                return U.rand(x, z, 633) < 0.25 ? Mat.SB_CRACK : Mat.P_ANDESITE;
            }
            boolean rake = Math.floorMod(x + z, 2) == 0;
            return rake ? Mat.GRAVEL : Mat.ANDESITE;
        });
        // red gate (hongsalmun) over the path
        for (int s = -1; s <= 1; s += 2) {
            c.fill(2 * s, 1, 3, 2 * s, 5, 3, "minecraft:stripped_crimson_stem[axis=y]");
        }
        c.fill(-3, 5, 3, 3, 5, 3, "minecraft:stripped_crimson_stem[axis=x]");
        c.fill(-3, 6, 3, 3, 6, 3, "minecraft:crimson_planks");
        c.fill(-2, 7, 3, 2, 7, 3, Mat.slabB(Mat.TILE_SL));
        // small shrine hall at the far end (replaces the house), gable towards the court
        int z0 = 6, z1 = 11;
        c.fill(-3, 1, z0, 3, 1, z1, Mat.SB);
        c.fill(-3, 2, z0, 3, 5, z1, Mat.PLASTER);
        c.clear(-1, 2, z0, 1, 4, z0 + 1);
        c.fill(-1, 2, z0 + 2, 1, 4, z0 + 2, Mat.SMOOTH);
        c.set(0, 3, z0 + 1, "minecraft:candle[candles=3,lit=true]");
        c.set(0, 2, z0 + 1, Mat.SMOOTH);
        c.fill(-3, 2, z0, -3, 4, z0, "minecraft:stripped_crimson_stem[axis=y]");
        c.fill(3, 2, z0, 3, 4, z0, "minecraft:stripped_crimson_stem[axis=y]");
        c.fill(-3, 5, z0, 3, 5, z0, "minecraft:stripped_crimson_stem[axis=x]");
        c.fill(-3, 2, z0 + 1, -3, 4, z0 + 1, Mat.PLASTER);
        Roofs.gableZ(c, -4, 4, z0 - 1, z1, 6, Roofs.TILE, 5, Roofs.TILE.block(), Mat.PLASTER);
        c.set(0, 3, z0 + 3, Mat.lantern(false));
        Props.stoneLamp(c, -3, 1, 2);
        Props.stoneLamp(c, 3, 1, 2);
        Props.stoneLamp(c, -3, 1, 5);
        Props.stoneLamp(c, 3, 1, 5);
        // cairn and ribbons on a sacred tree
        c.fill(-2, 1, 8, -1, 1, 9, Mat.COBBLE);
        c.fill(-2, 2, 8, -2, 2, 8, Mat.COBBLE);
        smallTree(c, -3, 9);
        for (int i = 0; i < 3; i++) {
            c.set(-3 + 1, 4 + i % 2, 9 + i - 1, Mat.wool(i == 0 ? "red" : i == 1 ? "yellow" : "light_blue"));
        }
        house.plaque = true;
        house.label = "SHRINE";
        c.at(0, 0, Layout.COURT_LEN, 0, () -> House.build(c, house));
        c.marker("marbles.shrine", 0.5, 1.0, 2.5, 0f, "stand=0");
    }

    // ------------------------------------------------------------------ houses framing the square

    /** Shop houses along the west (rot 1) and east (rot 3) edge of the square; HF z=0 on the square's edge column. */
    static void frameHouses(BuildContext c) {
        String[] signsW = {"TEA HOUSE", "STATIONERY", "NOODLES"};
        String[] signsE = {"BARBER", "BAKERY", "HARDWARE"};
        for (int side = -1; side <= 1; side += 2) {
            // lots: {centre z, half width}
            int[][] lots = {{-3, 3}, {8, 2}, {13, 2}};
            for (int i = 0; i < lots.length; i++) {
                int oz = lots[i][0], hw = lots[i][1];
                House.Spec h = new House.Spec();
                U.Rnd r = new U.Rnd(900 + i * 7 + (side + 1) * 31L);
                h.kind = House.Kind.TOWN;
                h.hw = hw;
                h.depth = 6;
                h.seed = r.next();
                h.wall = i == 1 ? Mat.CREAM : Mat.PLASTER;
                h.roof = i == 2 ? Roofs.COBDS : Roofs.TILE;
                h.number = 100 + i * 12 + (side > 0 ? 6 : 0);
                h.chimney = i != 0;
                h.laundry = i == 2;
                String sign = (side < 0 ? signsW : signsE)[i];
                int ox = side < 0 ? -16 : 16;
                int rot = side < 0 ? 1 : 3;
                c.at(ox, 0, oz, rot, () -> {
                    House.build(c, h);
                    shopFront(c, h, sign);
                    // two steps up to the raised porch, on the square's edge column
                    c.set(0, 1, -1, Mat.stair("minecraft:spruce_stairs", "south"));
                });
            }
        }
    }

    /** awning and sign on the ground floor of a TOWN house (HF frame, facade at z = 1). */
    private static void shopFront(BuildContext c, House.Spec h, String sign) {
        int hw = h.hw;
        String a = "minecraft:red_wool", b = "minecraft:white_wool";
        for (int x = -hw + 1; x <= hw - 1; x++) {
            c.set(x, 4, -1, (x & 1) == 0 ? a : b);
        }
        c.text(0.5, 4.5, -1.02, sign, "#FFE08A", 1.0f, 180f, false);
    }
}
