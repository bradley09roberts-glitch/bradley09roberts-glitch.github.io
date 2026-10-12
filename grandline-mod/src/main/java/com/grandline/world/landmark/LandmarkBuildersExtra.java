package com.grandline.world.landmark;

import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Set pieces for the canon islands added later (see CanonIslands#additions): Tequila Wolf's endless bridge, the
 * moored Germa fleet, Rommel's keep, Oykot's palace, God Valley's ruins, Kyuka's resort, the Lulusia crater memorial,
 * Prodence's castle, Karai Bari's mercenary guild hall and Mogaro's royal gate. Places only; no canon characters.
 * Coordinates are local to each footprint (entrance on the local north side, z = 0).
 */
final class LandmarkBuildersExtra {
    interface Registrar {
        void reg(String id, Landmarks.Spec spec, Landmarks.LandmarkBuilder builder);
    }

    static void registerAll(Registrar r) {
        r.reg("endless_bridge", new Landmarks.Spec(11, 400, false, true, 0), LandmarkBuildersExtra::endlessBridge);
        r.reg("germa_fleet", new Landmarks.Spec(61, 61, false, true, 0), LandmarkBuildersExtra::germaFleet);
        r.reg("rommel_keep", new Landmarks.Spec(29, 29, true, false, 0), LandmarkBuildersExtra::rommelKeep);
        r.reg("oykot_palace", new Landmarks.Spec(35, 29, true, false, 0), LandmarkBuildersExtra::oykotPalace);
        r.reg("god_valley_ruins", new Landmarks.Spec(45, 45, true, false, 0), LandmarkBuildersExtra::godValleyRuins);
        r.reg("kyuka_resort", new Landmarks.Spec(33, 27, true, false, 0), LandmarkBuildersExtra::kyukaResort);
        r.reg("lulusia_memorial", new Landmarks.Spec(15, 15, true, false, 0), LandmarkBuildersExtra::lulusiaMemorial);
        r.reg("prodence_castle", new Landmarks.Spec(31, 31, true, false, 0), LandmarkBuildersExtra::prodenceCastle);
        r.reg("mercenary_guild_hall", new Landmarks.Spec(31, 25, true, false, 0), LandmarkBuildersExtra::mercenaryGuildHall);
        r.reg("mogaro_royal_gate", new Landmarks.Spec(27, 21, true, false, 0), LandmarkBuildersExtra::mogaroRoyalGate);
    }

    private static CompoundTag role(String r) {
        CompoundTag t = new CompoundTag();
        t.putString("role", r);
        return t;
    }

    // ------------------------------------------------------------------ shared helpers

    /** Level pad: foundation down to the ground, a floor at y=0 and open air above. */
    private static void pad(Builder b, Block floor, int clearTo) {
        b.foundation(0, 0, b.w - 1, b.d - 1, floor, 14);
        b.fill(0, 0, 0, b.w - 1, 0, b.d - 1, floor);
        b.clear(0, 1, 0, b.w - 1, clearTo, b.d - 1);
    }

    /** Alternating merlons along a wall top. */
    private static void battlements(Builder b, int x1, int z1, int x2, int z2, int y, Block block) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                if (((x + z) & 1) == 0) b.set(x, y, z, block);
    }

    /** Round tower from y=1 to {@code top} with a cone roof. */
    private static void tower(Builder b, int cx, int cz, int r, int top, Block wall, Block roof, boolean windows) {
        b.cylinder(cx, cz, 1, top, r, wall, true);
        b.cylinder(cx, cz, top, top, r, wall, false);
        if (windows)
            for (int y = 4; y < top - 1; y += 4) {
                b.set(cx + r, y, cz, Blocks.GLASS_PANE);
                b.set(cx - r, y, cz, Blocks.GLASS_PANE);
                b.set(cx, y, cz + r, Blocks.GLASS_PANE);
                b.set(cx, y, cz - r, Blocks.GLASS_PANE);
            }
        cone(b, cx, cz, top + 1, r + 1, roof);
    }

    private static void cone(Builder b, int cx, int cz, int y, int r, Block roof) {
        for (int i = 0; i <= r + 1; i++) {
            double rr = r - i * 0.75;
            if (rr < 0) {
                b.set(cx, y + i, cz, roof);
                break;
            }
            b.cylinder(cx, cz, y + i, y + i, (int) Math.round(rr), roof, false);
        }
    }

    /** Hipped roof (stepped pyramid of stairs and full blocks) over [x1..x2]×[z1..z2] starting at y. */
    private static void hipRoof(Builder b, int x1, int z1, int x2, int z2, int y, Block stairs, Block fill) {
        int i = 0;
        while (x1 + i <= x2 - i && z1 + i <= z2 - i) {
            int ax = x1 + i, bx = x2 - i, az = z1 + i, bz = z2 - i;
            for (int x = ax; x <= bx; x++) {
                b.stairs(x, y + i, az, stairs, Direction.SOUTH, Half.BOTTOM);
                b.stairs(x, y + i, bz, stairs, Direction.NORTH, Half.BOTTOM);
            }
            for (int z = az + 1; z < bz; z++) {
                b.stairs(ax, y + i, z, stairs, Direction.EAST, Half.BOTTOM);
                b.stairs(bx, y + i, z, stairs, Direction.WEST, Half.BOTTOM);
            }
            if (bx - ax > 1 && bz - az > 1) b.fill(ax + 1, y + i, az + 1, bx - 1, y + i, bz - 1, fill);
            i++;
        }
    }

    private static void banner(Builder b, int x, int y, int z, Block banner) {
        b.set(x, y, z, banner);
    }

    // ------------------------------------------------------------------ Tequila Wolf: the endless bridge

    /**
     * A stone bridge running from the island far out to sea (local z = 0 on land, increasing seaward), on arched piers
     * founded on the seabed, with lamp posts, labour huts and an unfinished end of scaffolding and cranes.
     */
    static void endlessBridge(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d;
        int deck = 9;
        int unfinished = d - 34;
        // ground under the land end decides the ramp
        double gh = isl.height(b.wx(w / 2, 0), b.wz(w / 2, 0));
        int g0 = Double.isNaN(gh) ? -40 : (int) Math.floor(gh) - b.oy;
        for (int z = 0; z < d; z++) {
            int dy = g0 > 0 ? Math.min(deck, g0 + 1 + z / 2) : deck;
            boolean end = z >= unfinished;
            int gap = end ? (z - unfinished) : 0;
            double gl = isl.height(b.wx(w / 2, z), b.wz(w / 2, z));
            int ground = Double.isNaN(gl) ? -40 : (int) Math.floor(gl) - b.oy;
            boolean land = ground >= dy - 1;
            // deck (broken away toward the unfinished end)
            for (int x = 0; x < w; x++) {
                boolean hole = end && ((x * 31 + z * 17) % 9 < gap / 4 || gap > 26);
                if (hole) continue;
                b.set(x, dy - 1, z, Blocks.STONE_BRICKS);
                b.set(x, dy, z, x == w / 2 ? Blocks.POLISHED_ANDESITE : ((x + z) % 7 == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS));
            }
            b.clear(0, dy + 1, z, w - 1, dy + 5, z);
            if (!end || gap < 10) {
                b.set(0, dy + 1, z, Blocks.STONE_BRICK_WALL);
                b.set(w - 1, dy + 1, z, Blocks.STONE_BRICK_WALL);
            }
            if (land) {
                // embankment over the island
                b.foundation(0, z, w - 1, z, Blocks.COBBLESTONE, 12);
                for (int x = 0; x < w; x++) for (int y = ground + 1; y < dy - 1; y++) b.set(x, y, z, Blocks.STONE_BRICKS);
                continue;
            }
            int o = z % 16;
            if (o < 3 && !(end && gap > 26)) {
                // pier founded on the seabed
                b.fill(1, 0, z, w - 2, dy - 2, z, Blocks.STONE_BRICKS);
                b.foundation(1, z, w - 2, z, Blocks.STONE_BRICKS, 45);
                if (o == 1) {
                    b.set(0, 1, z, Blocks.MOSSY_STONE_BRICKS);
                    b.set(w - 1, 1, z, Blocks.MOSSY_STONE_BRICKS);
                }
            } else if (!end || gap < 18) {
                int archTop = 3 + (int) Math.round(4 * Math.sin(Math.PI * (o - 2) / 14.0));
                b.fill(0, Math.min(archTop, dy - 2), z, w - 1, dy - 2, z, Blocks.STONE_BRICKS);
            }
            // lamp posts every 12 blocks
            if (z % 12 == 6 && (!end || gap < 10)) {
                b.set(0, dy + 2, z, Blocks.STONE_BRICK_WALL);
                b.set(0, dy + 3, z, Blocks.LANTERN);
                b.set(w - 1, dy + 2, z, Blocks.STONE_BRICK_WALL);
                b.set(w - 1, dy + 3, z, Blocks.LANTERN);
            }
        }
        // labour huts on the deck
        for (int hz : new int[]{90, 190, 290}) {
            if (hz + 6 >= unfinished) continue;
            b.fill(1, deck + 1, hz, 4, deck + 3, hz + 5, Blocks.SPRUCE_PLANKS);
            b.clear(2, deck + 1, hz + 1, 3, deck + 2, hz + 4);
            b.fill(1, deck + 4, hz, 4, deck + 4, hz + 5, Blocks.SPRUCE_SLAB);
            b.clear(4, deck + 1, hz + 2, 4, deck + 2, hz + 2);
            b.bed(2, deck + 1, hz + 4, Blocks.RED_BED, Direction.NORTH);
            b.barrel(3, deck + 1, hz + 1, Buildings.LOOT_WAREHOUSE);
            b.marker("npc", 6, deck + 1, hz + 2, role("villager"));
        }
        // stone blocks waiting to be laid, carts of rubble
        for (int i = 0; i < 6; i++) {
            int z = unfinished - 8 + i * 2;
            b.set(2 + (i % 3) * 3, deck + 1, z, i % 2 == 0 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE);
        }
        // unfinished end: timber scaffolding and two cranes
        for (int z = unfinished + 4; z < d - 2; z += 4)
            for (int x : new int[]{1, w - 2}) {
                for (int y = -2; y <= deck + 4; y++) b.set(x, y, z, y % 4 == 0 ? Blocks.SPRUCE_PLANKS : Blocks.SPRUCE_FENCE);
                b.foundation(x, z, x, z, Blocks.SPRUCE_LOG, 40);
            }
        for (int c = 0; c < 2; c++) {
            int cz = unfinished + 2 + c * 14, cx = c == 0 ? 1 : w - 2;
            for (int y = deck + 1; y <= deck + 14; y++) b.set(cx, y, cz, Blocks.SPRUCE_LOG);
            int dir = c == 0 ? 1 : -1;
            for (int i = 1; i <= 8; i++) b.set(cx, deck + 14, cz + i * dir, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            for (int y = deck + 13; y >= deck + 6; y--) b.set(cx, y, cz + 8 * dir, Blocks.CHAIN);
            b.set(cx, deck + 5, cz + 8 * dir, Blocks.STONE_BRICKS);
        }
        b.chest(w / 2, deck + 1, unfinished - 2, Direction.SOUTH, Buildings.LOOT_WAREHOUSE);
        b.marker("npc", w / 2, deck + 1, 12, role("jailer"));
        b.marker("npc", w / 2 + 2, deck + 1, unfinished - 4, role("jailer"));
        b.marker("npc", w / 2 - 2, deck + 1, unfinished - 6, role("villager"));
        b.marker("landmark", w / 2, deck + 1, unfinished, new CompoundTag());
    }

    // ------------------------------------------------------------------ Germa Kingdom: the moored fleet

    /** A ship hull along local z from z0 (bow) with its main deck at y = 1 (y = 0 is the sea surface). */
    private static void hull(Builder b, int cx, int z0, int len, int halfW, Block side, Block deck, Block trim) {
        for (int i = 0; i < len; i++) {
            double t = i / (double) (len - 1);
            double f = t < 0.3 ? Math.sin(Math.PI / 2 * t / 0.3) : 1 - 0.25 * Math.pow((t - 0.3) / 0.7, 2);
            int half = Math.max(1, (int) Math.round(halfW * f));
            int z = z0 + i;
            b.fill(cx - half + 1, -4, z, cx + half - 1, -4, z, side);
            b.fill(cx - half, -3, z, cx + half, 0, z, side);
            b.fill(cx - half, 1, z, cx + half, 1, z, deck);
            b.set(cx - half, 1, z, trim);
            b.set(cx + half, 1, z, trim);
            b.set(cx - half, 2, z, Blocks.DARK_OAK_FENCE);
            b.set(cx + half, 2, z, Blocks.DARK_OAK_FENCE);
        }
    }

    private static void mast(Builder b, int x, int z, int height, boolean sail) {
        for (int y = 2; y <= height; y++) b.set(x, y, z, Blocks.SPRUCE_LOG);
        if (!sail) return;
        for (int y = height - 8; y <= height - 2; y++)
            for (int dx = -3; dx <= 3; dx++) b.set(x + dx, y, z + 1, y == height - 2 ? Blocks.BLUE_WOOL : Blocks.WHITE_WOOL);
        b.set(x, height + 1, z, Blocks.BLUE_WOOL);
    }

    /**
     * The Germa Kingdom has no land: its nation is a fleet. Here it rides at anchor — a castle flagship flanked by four
     * escorts, lashed together with gangways.
     */
    static void germaFleet(Builder b, Island isl, BuildStyle st) {
        b.clear(0, 1, 0, b.w - 1, 40, b.d - 1);
        Block side = Blocks.DARK_OAK_PLANKS, deck = Blocks.SPRUCE_PLANKS, trim = Blocks.BLUE_CONCRETE;
        // flagship
        int fx = 30;
        hull(b, fx, 4, 53, 9, side, deck, trim);
        // escorts
        for (int[] e : new int[][]{{9, 6}, {51, 6}, {9, 33}, {51, 33}}) {
            hull(b, e[0], e[1], 24, 4, side, deck, trim);
            mast(b, e[0], e[1] + 11, 17, true);
            b.set(e[0], 2, e[1] + 19, Blocks.BARREL);
            b.marker("npc", e[0], 2, e[1] + 15, role("guard"));
        }
        // gangways at deck level
        for (int gz : new int[]{17, 44}) {
            b.fill(13, 1, gz, 21, 1, gz + 1, Blocks.SPRUCE_PLANKS);
            b.fill(39, 1, gz, 47, 1, gz + 1, Blocks.SPRUCE_PLANKS);
            for (int x = 13; x <= 21; x += 2) b.set(x, 2, gz, Blocks.SPRUCE_FENCE);
            for (int x = 39; x <= 47; x += 2) b.set(x, 2, gz, Blocks.SPRUCE_FENCE);
        }
        // the castle on the flagship
        int x1 = fx - 6, x2 = fx + 6, z1 = 20, z2 = 40;
        b.fill(x1, 2, z1, x2, 2, z2, Blocks.POLISHED_ANDESITE);
        b.walls(x1, 2, z1, x2, 13, z2, Blocks.STONE_BRICKS);
        b.fill(x1, 14, z1, x2, 14, z2, Blocks.STONE_BRICKS);
        battlements(b, x1, z1, x2, z1, 15, Blocks.STONE_BRICK_WALL);
        battlements(b, x1, z2, x2, z2, 15, Blocks.STONE_BRICK_WALL);
        battlements(b, x1, z1, x1, z2, 15, Blocks.STONE_BRICK_WALL);
        battlements(b, x2, z1, x2, z2, 15, Blocks.STONE_BRICK_WALL);
        for (int y = 5; y <= 11; y += 3)
            for (int z = z1 + 3; z < z2 - 1; z += 4) {
                b.set(x1, y, z, Blocks.BLUE_STAINED_GLASS_PANE);
                b.set(x2, y, z, Blocks.BLUE_STAINED_GLASS_PANE);
            }
        b.fill(x1, 12, z1, x2, 12, z1, Blocks.BLUE_CONCRETE);
        b.fill(x1, 12, z2, x2, 12, z2, Blocks.BLUE_CONCRETE);
        b.bigDoor(fx, 3, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            b.cylinder(c[0], c[1], 3, 19, 2, Blocks.STONE_BRICKS, true);
            b.cylinder(c[0], c[1], 19, 19, 2, Blocks.STONE_BRICKS, false);
            cone(b, c[0], c[1], 20, 3, Blocks.BLUE_CONCRETE);
        }
        // central keep tower
        b.cylinder(fx, 30, 15, 26, 3, Blocks.STONE_BRICKS, true);
        b.cylinder(fx, 30, 26, 26, 3, Blocks.STONE_BRICKS, false);
        cone(b, fx, 30, 27, 4, Blocks.BLUE_CONCRETE);
        b.set(fx, 33, 30, Blocks.GOLD_BLOCK);
        // throne hall
        b.fill(fx - 1, 3, z2 - 3, fx + 1, 3, z2 - 2, Blocks.BLUE_CARPET);
        b.stairs(fx, 3, z2 - 1, Blocks.QUARTZ_STAIRS, Direction.NORTH, Half.BOTTOM);
        for (int z = z1 + 2; z < z2 - 3; z++) b.set(fx, 3, z, Blocks.BLUE_CARPET);
        for (int z = z1 + 3; z < z2 - 2; z += 5) {
            b.hanging(fx - 3, 13, z, Blocks.LANTERN);
            b.hanging(fx + 3, 13, z, Blocks.LANTERN);
        }
        b.chest(x2 - 1, 3, z2 - 1, Direction.WEST, Buildings.LOOT_TREASURE);
        // bow and stern guns
        for (int z : new int[]{8, 52}) {
            b.set(fx - 2, 2, z, Blocks.ANVIL);
            b.set(fx + 2, 2, z, Blocks.ANVIL);
        }
        mast(b, fx, 10, 26, true);
        banner(b, fx - 4, 2, 12, Blocks.BLUE_BANNER);
        banner(b, fx + 4, 2, 12, Blocks.BLUE_BANNER);
        b.marker("npc", fx + 2, 3, z2 - 4, role("noble"));
        b.marker("npc", fx - 3, 3, z1 + 3, role("guard"));
        b.marker("npc", fx + 3, 3, z1 + 3, role("guard"));
        b.marker("npc", fx, 2, 14, role("guard"));
        b.marker("dock", fx, 1, 4, new CompoundTag());
    }

    // ------------------------------------------------------------------ Rommel Kingdom

    /** A snow-capped keep inside a curtain wall with corner towers and a gatehouse. */
    static void rommelKeep(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d;
        pad(b, Blocks.COBBLESTONE, 30);
        b.fill(1, 0, 1, w - 2, 0, d - 2, Blocks.PACKED_ICE);
        b.fill(3, 0, 3, w - 4, 0, d - 4, Blocks.STONE_BRICKS);
        // curtain wall
        b.walls(0, 1, 0, w - 1, 7, d - 1, Blocks.STONE_BRICKS);
        battlements(b, 0, 0, w - 1, 0, 8, Blocks.STONE_BRICK_WALL);
        battlements(b, 0, d - 1, w - 1, d - 1, 8, Blocks.STONE_BRICK_WALL);
        battlements(b, 0, 0, 0, d - 1, 8, Blocks.STONE_BRICK_WALL);
        battlements(b, w - 1, 0, w - 1, d - 1, 8, Blocks.STONE_BRICK_WALL);
        for (int[] c : new int[][]{{3, 3}, {w - 4, 3}, {3, d - 4}, {w - 4, d - 4}}) tower(b, c[0], c[1], 2, 12, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILES, false);
        // gatehouse with portcullis
        int gx = w / 2;
        b.clear(gx - 1, 1, 0, gx + 1, 4, 0);
        b.fill(gx - 3, 1, 0, gx - 2, 10, 2, Blocks.STONE_BRICKS);
        b.fill(gx + 2, 1, 0, gx + 3, 10, 2, Blocks.STONE_BRICKS);
        b.fill(gx - 3, 8, 0, gx + 3, 10, 2, Blocks.STONE_BRICKS);
        b.clear(gx - 1, 1, 1, gx + 1, 4, 2);
        b.fill(gx - 1, 4, 0, gx + 1, 4, 0, Blocks.IRON_BARS);
        battlements(b, gx - 3, 0, gx + 3, 0, 11, Blocks.STONE_BRICK_WALL);
        b.set(gx - 3, 1, 4, Blocks.LANTERN);
        b.set(gx + 3, 1, 4, Blocks.LANTERN);
        // keep
        int k1 = 8, k2 = w - 9, kz1 = 12, kz2 = d - 4;
        b.walls(k1, 1, kz1, k2, 15, kz2, Blocks.STONE_BRICKS);
        for (int y = 1; y <= 15; y++)
            for (int[] c : new int[][]{{k1, kz1}, {k2, kz1}, {k1, kz2}, {k2, kz2}}) b.set(c[0], y, c[1], Blocks.POLISHED_DEEPSLATE);
        b.fill(k1 + 1, 5, kz1 + 1, k2 - 1, 5, kz2 - 1, Blocks.SPRUCE_PLANKS);
        b.fill(k1 + 1, 10, kz1 + 1, k2 - 1, 10, kz2 - 1, Blocks.SPRUCE_PLANKS);
        for (int y : new int[]{3, 7, 12})
            for (int x = k1 + 2; x < k2 - 1; x += 3) {
                b.set(x, y, kz1, Blocks.GLASS_PANE);
                b.set(x, y, kz2, Blocks.GLASS_PANE);
            }
        b.bigDoor((k1 + k2) / 2, 1, kz1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.ladderUp(k2 - 1, 1, kz2 - 1, 10, Direction.NORTH);
        b.clear(k2 - 1, 5, kz2 - 1, k2 - 1, 5, kz2 - 1);
        b.clear(k2 - 1, 10, kz2 - 1, k2 - 1, 10, kz2 - 1);
        hipRoof(b, k1 - 1, kz1 - 1, k2 + 1, kz2 + 1, 16, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES);
        // great hall
        b.fill((k1 + k2) / 2 - 1, 1, kz1 + 2, (k1 + k2) / 2 + 1, 1, kz2 - 3, Blocks.RED_CARPET);
        b.stairs((k1 + k2) / 2, 1, kz2 - 1, Blocks.DARK_OAK_STAIRS, Direction.NORTH, Half.BOTTOM);
        b.set(k1 + 1, 1, kz2 - 1, Blocks.CAMPFIRE);
        b.set(k2 - 2, 1, kz1 + 1, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.SOUTH));
        b.hanging((k1 + k2) / 2, 4, (kz1 + kz2) / 2, Blocks.LANTERN);
        b.chest(k1 + 1, 6, kz2 - 1, Direction.EAST, Buildings.LOOT_TREASURE);
        // courtyard
        b.set(5, 1, 6, Blocks.HAY_BLOCK);
        b.set(5, 2, 6, Blocks.HAY_BLOCK);
        b.set(6, 1, 6, Blocks.HAY_BLOCK);
        b.barrel(w - 6, 1, 5, Buildings.LOOT_WAREHOUSE);
        b.set(w - 6, 1, 6, Blocks.ANVIL);
        banner(b, gx - 1, 1, 3, Blocks.WHITE_BANNER);
        banner(b, gx + 1, 1, 3, Blocks.WHITE_BANNER);
        b.marker("npc", gx, 1, 4, role("guard"));
        b.marker("npc", gx, 11, 1, role("guard"));
        b.marker("npc", (k1 + k2) / 2 + 2, 1, kz2 - 3, role("noble"));
        b.marker("npc", 6, 1, 8, role("blacksmith"));
    }

    // ------------------------------------------------------------------ Oykot Kingdom

    /** A white palace with teal roofs, a clock tower and a garden court with a fountain. */
    static void oykotPalace(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d;
        pad(b, Blocks.STONE_BRICKS, 32);
        // garden court
        b.fill(1, 0, 1, w - 2, 0, 11, Blocks.GRASS_BLOCK);
        b.fill(w / 2 - 1, 0, 0, w / 2 + 1, 0, 12, Blocks.SMOOTH_STONE);
        b.fill(3, 0, 6, w - 4, 0, 6, Blocks.SMOOTH_STONE);
        b.cylinder(w / 2, 6, 0, 0, 3, Blocks.QUARTZ_BRICKS, false);
        b.cylinder(w / 2, 6, 1, 1, 3, Blocks.QUARTZ_BRICKS, true);
        b.cylinder(w / 2, 6, 1, 1, 2, Blocks.WATER, false);
        b.fill(w / 2, 1, 6, w / 2, 3, 6, Blocks.QUARTZ_PILLAR);
        b.set(w / 2, 4, 6, Blocks.SEA_LANTERN);
        for (int x : new int[]{3, 6, w - 7, w - 4}) {
            b.set(x, 1, 3, Blocks.OAK_LOG);
            b.fill(x - 1, 2, 2, x + 1, 3, 4, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            b.set(x, 1, 9, Blocks.ROSE_BUSH);
            b.set(x, 2, 9, Blocks.ROSE_BUSH.defaultBlockState().setValue(DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }
        // the palace
        int z1 = 12, z2 = d - 2;
        b.walls(1, 1, z1, w - 2, 9, z2, Blocks.WHITE_TERRACOTTA);
        b.fill(2, 0, z1 + 1, w - 3, 0, z2 - 1, Blocks.POLISHED_ANDESITE);
        b.fill(2, 5, z1 + 1, w - 3, 5, z2 - 1, Blocks.DARK_OAK_PLANKS);
        for (int x = 1; x < w - 1; x += 4)
            for (int y = 1; y <= 9; y++) {
                b.set(x, y, z1, Blocks.QUARTZ_PILLAR);
                b.set(x, y, z2, Blocks.QUARTZ_PILLAR);
            }
        for (int x = 3; x < w - 2; x += 4)
            for (int y : new int[]{2, 7}) {
                b.fill(x, y, z1, x, y + 1, z1, Blocks.GLASS_PANE);
                b.fill(x, y, z2, x, y + 1, z2, Blocks.GLASS_PANE);
            }
        b.fill(1, 10, z1, w - 2, 10, z2, Blocks.QUARTZ_SLAB);
        hipRoof(b, 0, z1 - 1, w - 1, z2 + 1, 10, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_BRICKS);
        // entrance portico
        b.clear(w / 2 - 1, 1, z1, w / 2 + 1, 4, z1);
        for (int x : new int[]{w / 2 - 3, w / 2 + 3}) b.fill(x, 1, z1 - 2, x, 5, z1 - 2, Blocks.QUARTZ_PILLAR);
        b.fill(w / 2 - 3, 6, z1 - 2, w / 2 + 3, 6, z1, Blocks.QUARTZ_SLAB);
        // clock tower
        int tx = w / 2, tz = (z1 + z2) / 2;
        b.fill(tx - 2, 10, tz - 2, tx + 2, 22, tz + 2, Blocks.WHITE_TERRACOTTA);
        b.clear(tx - 1, 19, tz - 2, tx + 1, 21, tz + 2);
        b.clear(tx - 2, 19, tz - 1, tx + 2, 21, tz + 1);
        b.set(tx, 20, tz, Blocks.BELL);
        b.set(tx, 16, tz - 3, Blocks.GOLD_BLOCK);
        b.set(tx, 16, tz + 3, Blocks.GOLD_BLOCK);
        hipRoof(b, tx - 3, tz - 3, tx + 3, tz + 3, 23, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_BRICKS);
        b.set(tx, 27, tz, Blocks.LIGHTNING_ROD);
        // throne room and archive
        b.fill(tx - 1, 1, z1 + 2, tx + 1, 1, z2 - 3, Blocks.BLUE_CARPET);
        b.stairs(tx, 1, z2 - 1, Blocks.QUARTZ_STAIRS, Direction.NORTH, Half.BOTTOM);
        b.set(tx - 1, 1, z2 - 1, Blocks.GOLD_BLOCK);
        b.set(tx + 1, 1, z2 - 1, Blocks.GOLD_BLOCK);
        for (int x = 3; x < 9; x++) b.fill(x, 1, z2 - 1, x, 3, z2 - 1, Blocks.BOOKSHELF);
        for (int x = 5; x < w - 5; x += 6) b.hanging(x, 4, (z1 + z2) / 2, Blocks.LANTERN);
        b.chest(w - 3, 1, z2 - 1, Direction.WEST, Buildings.LOOT_TREASURE);
        b.marker("npc", tx + 2, 1, z2 - 3, role("noble"));
        b.marker("npc", w / 2 - 3, 1, z1 - 3, role("guard"));
        b.marker("npc", w / 2 + 3, 1, z1 - 3, role("guard"));
        b.marker("npc", 5, 1, z2 - 3, role("scholar"));
    }

    // ------------------------------------------------------------------ God Valley

    /**
     * What is left of God Valley: a scorched blast crater ringed by toppled pillars of a great stone arena, glassy
     * slag and a broken shrine gate.
     */
    static void godValleyRuins(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d, c = w / 2;
        b.foundation(0, 0, w - 1, d - 1, Blocks.COBBLED_DEEPSLATE, 12);
        b.clear(0, 1, 0, w - 1, 24, d - 1);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                double r = Math.hypot(x - c, z - c);
                int hh = (x * 73856093 ^ z * 19349663) >>> 3;
                if (r < 13) {
                    // shallow scorched bowl
                    int depth = (int) Math.round(3 * (1 - r / 13));
                    b.clear(x, 1 - depth, z, x, 0, z);
                    Block bed = (hh % 7 == 0) ? Blocks.MAGMA_BLOCK : (hh % 5 == 0) ? Blocks.TINTED_GLASS : (hh % 3 == 0) ? Blocks.BASALT : Blocks.BLACKSTONE;
                    b.set(x, -depth, z, bed);
                } else {
                    Block g = r < 16 ? ((hh & 1) == 0 ? Blocks.COARSE_DIRT : Blocks.BLACKSTONE) : (hh % 4 == 0 ? Blocks.COARSE_DIRT : Blocks.PODZOL);
                    b.set(x, 0, z, g);
                }
            }
        // ring of broken arena pillars
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            int px = c + (int) Math.round(Math.cos(a) * 19), pz = c + (int) Math.round(Math.sin(a) * 19);
            int hgt = 2 + b.rand(i * 7, 10);
            for (int y = 1; y <= hgt; y++) {
                Block bl = y == hgt ? Blocks.CHISELED_STONE_BRICKS : (b.rand(i * 31 + y, 4) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
                b.fill(px, y, pz, px + 1, y, pz + 1, bl);
            }
            if (hgt < 6) {
                // the fallen top lies beside it
                int dx = (int) Math.round(Math.cos(a + 1.6) * 1.5), dz = (int) Math.round(Math.sin(a + 1.6) * 1.5);
                Direction.Axis ax = Math.abs(dx) > Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
                for (int k = 2; k <= 5; k++) {
                    int lx = px + (ax == Direction.Axis.X ? k * Integer.signum(dx == 0 ? 1 : dx) : 0);
                    int lz = pz + (ax == Direction.Axis.Z ? k * Integer.signum(dz == 0 ? 1 : dz) : 0);
                    if (lx >= 0 && lx < w && lz >= 0 && lz < d) b.set(lx, 1, lz, Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, ax));
                }
            }
        }
        // broken shrine gate on the north side
        b.fill(c - 5, 1, 2, c - 4, 9, 3, Blocks.MOSSY_STONE_BRICKS);
        b.fill(c + 4, 1, 2, c + 5, 6, 3, Blocks.MOSSY_STONE_BRICKS);
        b.fill(c - 5, 10, 2, c, 10, 3, Blocks.STONE_BRICK_SLAB);
        b.set(c + 3, 1, 4, Blocks.CRACKED_STONE_BRICKS);
        b.set(c + 2, 1, 5, Blocks.MOSSY_COBBLESTONE);
        // a lone dead tree and scattered bones
        for (int y = 1; y <= 6; y++) b.set(4, y, d - 5, Blocks.DARK_OAK_LOG);
        b.set(5, 6, d - 5, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        b.set(3, 5, d - 5, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        b.set(c + 8, 1, c + 6, Blocks.BONE_BLOCK);
        b.set(c - 7, 1, c - 9, Blocks.BONE_BLOCK);
        b.set(c - 9, 1, c + 3, Blocks.SKELETON_SKULL);
        b.chest(c - 4, 1, 4, Direction.SOUTH, Buildings.LOOT_RUIN);
        b.marker("npc", c, -2, c, role("ruin_guardian"));
        b.marker("npc", c + 10, 1, c - 10, role("ruin_guardian"));
        b.marker("npc", c - 2, 1, 6, role("scholar")); // an archaeologist sifting the ruins
        b.marker("landmark", c, 1, c, new CompoundTag());
    }

    // ------------------------------------------------------------------ Kyuka Island

    private static void palm(Builder b, int x, int y, int z, int height, int lean) {
        for (int i = 0; i < height; i++) b.set(x + (i > height / 2 ? lean : 0), y + i, z, Blocks.JUNGLE_LOG);
        int tx = x + lean, ty = y + height;
        BlockState leaf = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        b.set(tx, ty, z, leaf);
        for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
            int len = dd[0] != 0 && dd[1] != 0 ? 2 : 3;
            for (int s = 1; s <= len; s++) b.set(tx + dd[0] * s, ty - (s == len ? 1 : 0), z + dd[1] * s, leaf);
        }
    }

    private static void parasol(Builder b, int x, int z, Block cloth) {
        b.fill(x, 1, z, x, 3, z, Blocks.BIRCH_FENCE);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) b.set(x + dx, 4, z + dz, cloth);
        b.slab(x, 5, z, Blocks.SMOOTH_QUARTZ_SLAB, SlabType.BOTTOM);
    }

    /** A beach resort hotel with balconies, a pool terrace, parasols, loungers, palms and a pool bar. */
    static void kyukaResort(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d;
        pad(b, Blocks.SMOOTH_SANDSTONE, 24);
        // terrace and pool
        b.fill(1, 0, 1, w - 2, 0, 12, Blocks.SMOOTH_QUARTZ);
        b.fill(5, -2, 3, w - 6, -1, 10, Blocks.PRISMARINE_BRICKS);
        b.fill(6, -1, 4, w - 7, 0, 9, Blocks.WATER);
        b.fill(5, 0, 3, w - 6, 0, 3, Blocks.QUARTZ_BRICKS);
        b.fill(5, 0, 10, w - 6, 0, 10, Blocks.QUARTZ_BRICKS);
        b.fill(5, 0, 3, 5, 0, 10, Blocks.QUARTZ_BRICKS);
        b.fill(w - 6, 0, 3, w - 6, 0, 10, Blocks.QUARTZ_BRICKS);
        b.set(6, -2, 6, Blocks.SEA_LANTERN);
        b.set(w - 7, -2, 7, Blocks.SEA_LANTERN);
        Block[] cloths = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.WHITE_WOOL};
        for (int i = 0; i < 4; i++) parasol(b, 3 + i * 9, 1, cloths[i]);
        for (int i = 0; i < 4; i++) {
            int x = 2 + i * 9;
            b.slab(x + 2, 1, 1, Blocks.BIRCH_SLAB, SlabType.BOTTOM);
            b.stairs(x + 3, 1, 1, Blocks.BIRCH_STAIRS, Direction.WEST, Half.BOTTOM);
        }
        palm(b, 2, 1, 11, 7, 1);
        palm(b, w - 3, 1, 11, 6, -1);
        // pool bar
        b.fill(w - 5, 1, 4, w - 2, 1, 4, Blocks.SPRUCE_PLANKS);
        b.fill(w - 5, 1, 4, w - 5, 1, 8, Blocks.SPRUCE_PLANKS);
        b.fill(w - 5, 4, 4, w - 2, 4, 8, Blocks.SPRUCE_SLAB);
        b.set(w - 2, 2, 4, Blocks.SPRUCE_FENCE);
        b.set(w - 2, 3, 4, Blocks.SPRUCE_FENCE);
        b.set(w - 2, 2, 8, Blocks.SPRUCE_FENCE);
        b.set(w - 2, 3, 8, Blocks.SPRUCE_FENCE);
        b.barrel(w - 3, 1, 7, Buildings.LOOT_TAVERN);
        // hotel: three floors with balconies
        int z1 = 14, z2 = d - 2;
        for (int f = 0; f < 3; f++) {
            int y0 = 1 + f * 4;
            b.walls(1, y0, z1, w - 2, y0 + 3, z2, Blocks.WHITE_TERRACOTTA);
            b.fill(2, y0 - 1, z1 + 1, w - 3, y0 - 1, z2 - 1, f == 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.BIRCH_PLANKS);
            for (int x = 3; x < w - 3; x += 3) {
                b.fill(x, y0 + 1, z1, x, y0 + 2, z1, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
                b.fill(x, y0 + 1, z2, x, y0 + 2, z2, Blocks.GLASS_PANE);
            }
            if (f > 0) {
                b.fill(1, y0 - 1, z1 - 2, w - 2, y0 - 1, z1 - 1, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                b.fill(1, y0, z1 - 2, w - 2, y0, z1 - 2, Blocks.BIRCH_FENCE);
            }
            b.fill(1, y0 + 3, z1, w - 2, y0 + 3, z1, Blocks.ORANGE_TERRACOTTA);
            b.hanging(w / 2, y0 + 2, (z1 + z2) / 2, Blocks.LANTERN);
        }
        b.fill(1, 13, z1, w - 2, 13, z2, Blocks.ORANGE_TERRACOTTA);
        b.fill(2, 14, z1 + 1, w - 3, 14, z2 - 1, Blocks.TERRACOTTA);
        b.bigDoor(w / 2, 1, z1, Blocks.BIRCH_DOOR, Direction.NORTH);
        for (int f = 0; f < 3; f++) b.ladderUp(w - 3, 1 + f * 4 - (f > 0 ? 1 : 0), z2 - 1, f == 0 ? 4 : 5, Direction.NORTH);
        b.clear(w - 3, 4, z2 - 1, w - 3, 4, z2 - 1);
        b.clear(w - 3, 8, z2 - 1, w - 3, 8, z2 - 1);
        b.set(3, 1, z2 - 1, Blocks.LECTERN);
        b.fill(5, 1, z1 + 2, 7, 1, z1 + 2, Blocks.SPRUCE_PLANKS);
        b.chest(2, 1, z2 - 1, Direction.EAST, Buildings.LOOT_TAVERN);
        b.marker("npc", 6, 1, z1 + 3, role("innkeeper"));
        b.marker("npc", w - 4, 1, 6, role("bartender"));
        b.marker("npc", 10, 1, 2, role("patron"));
        b.marker("npc", 20, 1, 12, role("patron"));
    }

    // ------------------------------------------------------------------ Lulusia Kingdom

    /**
     * On the rim of the crater where the kingdom was: a fragment of the city wall and a stair that now ends in the air,
     * scorched glassy ground and a memorial obelisk. The crater itself is the island's terrain (IslandShape.BLAST).
     */
    static void lulusiaMemorial(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, Blocks.BLACKSTONE, 14);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                int hh = (x * 92837111 ^ z * 689287499) >>> 4;
                b.set(x, 0, z, hh % 5 == 0 ? Blocks.TINTED_GLASS : hh % 3 == 0 ? Blocks.BASALT : Blocks.BLACKSTONE);
            }
        b.clear(0, 1, 0, w - 1, 16, d - 1);
        // wall fragment with half an arch
        b.fill(1, 1, 3, 2, 9, 9, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.fill(1, 1, 5, 2, 4, 7, Blocks.AIR);
        b.fill(1, 5, 5, 2, 5, 7, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        b.fill(1, 10, 3, 2, 10, 5, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        // stair climbing to nowhere
        for (int i = 0; i < 6; i++) {
            b.fill(5 + i, 1, d - 4, 5 + i, i, d - 2, Blocks.STONE_BRICKS);
            b.stairs(5 + i, i + 1, d - 4, Blocks.STONE_BRICK_STAIRS, Direction.EAST, Half.BOTTOM);
            b.stairs(5 + i, i + 1, d - 3, Blocks.STONE_BRICK_STAIRS, Direction.EAST, Half.BOTTOM);
            b.stairs(5 + i, i + 1, d - 2, Blocks.STONE_BRICK_STAIRS, Direction.EAST, Half.BOTTOM);
        }
        // memorial obelisk
        int c = w / 2;
        b.fill(c - 1, 1, c - 1, c + 1, 1, c + 1, Blocks.POLISHED_DEEPSLATE);
        b.fill(c, 2, c, c, 8, c, Blocks.POLISHED_DEEPSLATE);
        b.set(c, 9, c, Blocks.CHISELED_DEEPSLATE);
        b.set(c, 10, c, Blocks.LIGHTNING_ROD);
        for (int[] o : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}}) b.set(c + o[0], 1, c + o[1], Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, false));
        b.set(c + 1, 2, c, Blocks.SOUL_LANTERN);
        b.chest(w - 2, 1, 2, Direction.WEST, Buildings.LOOT_RUIN);
        b.marker("npc", c + 3, 1, c, role("keeper")); // a mourner tending the memorial
        b.marker("landmark", c, 1, c + 2, new CompoundTag());
    }

    // ------------------------------------------------------------------ Prodence Kingdom

    /** A fortified castle with round towers, a tall keep and a training yard for its fighters. */
    static void prodenceCastle(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d;
        pad(b, Blocks.STONE_BRICKS, 34);
        b.fill(1, 0, 1, w - 2, 0, d - 2, Blocks.GRAVEL);
        b.walls(0, 1, 0, w - 1, 8, d - 1, Blocks.STONE_BRICKS);
        battlements(b, 0, 0, w - 1, 0, 9, Blocks.STONE_BRICKS);
        battlements(b, 0, d - 1, w - 1, d - 1, 9, Blocks.STONE_BRICKS);
        battlements(b, 0, 0, 0, d - 1, 9, Blocks.STONE_BRICKS);
        battlements(b, w - 1, 0, w - 1, d - 1, 9, Blocks.STONE_BRICKS);
        for (int[] c : new int[][]{{4, 4}, {w - 5, 4}, {4, d - 5}, {w - 5, d - 5}}) tower(b, c[0], c[1], 3, 13, Blocks.STONE_BRICKS, Blocks.RED_TERRACOTTA, true);
        // gate
        int gx = w / 2;
        b.clear(gx - 2, 1, 0, gx + 2, 5, 0);
        b.fill(gx - 2, 6, 0, gx + 2, 6, 0, Blocks.CHISELED_STONE_BRICKS);
        b.set(gx - 3, 1, 1, Blocks.LANTERN);
        b.set(gx + 3, 1, 1, Blocks.LANTERN);
        // keep
        int k1 = 10, k2 = w - 11, kz1 = 15, kz2 = d - 5;
        b.fill(k1, 1, kz1, k2, 1, kz2, Blocks.POLISHED_ANDESITE);
        b.walls(k1, 1, kz1, k2, 20, kz2, Blocks.STONE_BRICKS);
        for (int y : new int[]{6, 11, 16}) b.fill(k1 + 1, y, kz1 + 1, k2 - 1, y, kz2 - 1, Blocks.DARK_OAK_PLANKS);
        for (int y : new int[]{3, 8, 13, 18}) {
            b.set(k1, y, (kz1 + kz2) / 2, Blocks.GLASS_PANE);
            b.set(k2, y, (kz1 + kz2) / 2, Blocks.GLASS_PANE);
            b.set((k1 + k2) / 2 - 2, y, kz1, Blocks.GLASS_PANE);
            b.set((k1 + k2) / 2 + 2, y, kz1, Blocks.GLASS_PANE);
        }
        b.fill(k1, 20, kz1, k2, 20, kz1, Blocks.RED_TERRACOTTA);
        hipRoof(b, k1 - 1, kz1 - 1, k2 + 1, kz2 + 1, 21, Blocks.DARK_OAK_STAIRS, Blocks.RED_TERRACOTTA);
        b.bigDoor((k1 + k2) / 2, 2, kz1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.ladderUp(k2 - 1, 2, kz2 - 1, 15, Direction.NORTH);
        for (int y : new int[]{6, 11, 16}) b.clear(k2 - 1, y, kz2 - 1, k2 - 1, y, kz2 - 1);
        b.fill((k1 + k2) / 2 - 1, 2, kz1 + 1, (k1 + k2) / 2 + 1, 2, kz2 - 2, Blocks.RED_CARPET);
        b.stairs((k1 + k2) / 2, 2, kz2 - 1, Blocks.QUARTZ_STAIRS, Direction.NORTH, Half.BOTTOM);
        b.hanging((k1 + k2) / 2, 5, (kz1 + kz2) / 2, Blocks.LANTERN);
        b.chest(k1 + 1, 7, kz2 - 1, Direction.EAST, Buildings.LOOT_TREASURE);
        // training yard: targets, dummies, a sparring ring
        for (int i = 0; i < 4; i++) {
            b.set(4 + i * 3, 1, 8, Blocks.TARGET);
            b.set(4 + i * 3, 2, 8, Blocks.HAY_BLOCK);
        }
        b.fill(w - 12, 0, 8, w - 5, 0, 13, Blocks.SAND);
        for (int x = w - 12; x <= w - 5; x++) {
            b.set(x, 1, 8, Blocks.OAK_FENCE);
            b.set(x, 1, 13, Blocks.OAK_FENCE);
        }
        for (int z = 8; z <= 13; z++) {
            b.set(w - 12, 1, z, Blocks.OAK_FENCE);
            b.set(w - 5, 1, z, Blocks.OAK_FENCE);
        }
        b.clear(w - 9, 1, 8, w - 8, 1, 8);
        banner(b, gx - 2, 1, 2, Blocks.RED_BANNER);
        banner(b, gx + 2, 1, 2, Blocks.RED_BANNER);
        b.marker("npc", gx, 1, 3, role("guard"));
        b.marker("npc", w - 8, 1, 11, role("master"));
        b.marker("npc", 7, 1, 10, role("guard"));
        b.marker("npc", (k1 + k2) / 2 + 2, 2, kz2 - 3, role("noble"));
    }

    // ------------------------------------------------------------------ Karai Bari Island

    /** A mercenary guild hall with a wall-sized bounty board, a tavern inside and a striped big-top tent beside it. */
    static void mercenaryGuildHall(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d;
        pad(b, Blocks.COBBLESTONE, 26);
        b.fill(1, 0, 1, w - 2, 0, 9, Blocks.PACKED_MUD);
        // the hall
        int x1 = 1, x2 = 19, z1 = 10, z2 = d - 2;
        b.walls(x1, 1, z1, x2, 9, z2, Blocks.STONE_BRICKS);
        for (int y = 1; y <= 9; y++)
            for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.set(c[0], y, c[1], Blocks.DARK_OAK_LOG);
        b.fill(x1, 5, z1, x2, 5, z2, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        b.fill(x1 + 1, 5, z1 + 1, x2 - 1, 5, z2 - 1, Blocks.SPRUCE_PLANKS);
        for (int x = 3; x < x2 - 1; x += 3) {
            b.fill(x, 2, z2, x, 3, z2, Blocks.GLASS_PANE);
            b.fill(x, 7, z1, x, 8, z1, Blocks.GLASS_PANE);
        }
        b.gableRoof(x1 - 1, z1 - 1, x2 + 1, z2 + 1, 10, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.STONE_BRICKS);
        b.bigDoor((x1 + x2) / 2, 1, z1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        // bounty board covering the front wall
        for (int x = x1 + 1; x < x2; x++)
            for (int y = 2; y <= 4; y++) {
                if (Math.abs(x - (x1 + x2) / 2) <= 1) continue;
                int hh = (x * 31 + y * 17) % 5;
                b.set(x, y, z1 - 1, hh == 0 ? Blocks.SPRUCE_PLANKS : hh == 1 ? Blocks.LIGHT_GRAY_WOOL : hh == 2 ? Blocks.WHITE_WOOL : hh == 3 ? Blocks.BIRCH_PLANKS : Blocks.WHITE_WOOL);
            }
        b.fill(x1 + 1, 5, z1 - 1, x2 - 1, 5, z1 - 1, Blocks.SPRUCE_SLAB);
        b.set((x1 + x2) / 2 - 3, 6, z1 - 1, Blocks.LANTERN);
        b.set((x1 + x2) / 2 + 3, 6, z1 - 1, Blocks.LANTERN);
        // tavern inside
        b.fill(x1 + 2, 1, z2 - 3, x2 - 2, 1, z2 - 3, Blocks.SPRUCE_PLANKS);
        b.barrel(x1 + 2, 1, z2 - 1, Buildings.LOOT_TAVERN);
        b.barrel(x1 + 3, 1, z2 - 1, Buildings.LOOT_TAVERN);
        for (int x = x1 + 3; x < x2 - 2; x += 4) {
            b.set(x, 1, z1 + 4, Blocks.SPRUCE_FENCE);
            b.set(x, 2, z1 + 4, Blocks.SPRUCE_PRESSURE_PLATE);
            b.stairs(x - 1, 1, z1 + 4, Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM);
            b.stairs(x + 1, 1, z1 + 4, Blocks.SPRUCE_STAIRS, Direction.WEST, Half.BOTTOM);
        }
        b.hanging((x1 + x2) / 2, 4, (z1 + z2) / 2, Blocks.LANTERN);
        b.chest(x2 - 1, 1, z2 - 1, Direction.WEST, Buildings.LOOT_TREASURE);
        // big-top tent
        int tx = 25, tz = 16, r = 5;
        for (int y = 1; y <= 9; y++) {
            double rr = y <= 4 ? r : r * (1 - (y - 4) / 6.0);
            int ri = (int) Math.round(rr);
            for (int dx = -ri; dx <= ri; dx++)
                for (int dz = -ri; dz <= ri; dz++) {
                    double dd = Math.hypot(dx, dz);
                    if (dd > rr + 0.5 || (y <= 4 && dd < rr - 0.6)) continue;
                    double ang = Math.atan2(dz, dx);
                    int stripe = (int) Math.floor((ang + Math.PI) / (Math.PI / 4));
                    b.set(tx + dx, y, tz + dz, (stripe & 1) == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL);
                }
        }
        b.fill(tx, 10, tz, tx, 12, tz, Blocks.OAK_FENCE);
        b.set(tx, 13, tz, Blocks.YELLOW_WOOL);
        b.clear(tx - 1, 1, tz - r, tx + 1, 3, tz - r);
        b.marker("npc", (x1 + x2) / 2, 1, z2 - 4, role("bartender"));
        b.marker("npc", (x1 + x2) / 2 + 3, 1, z1 - 3, role("merchant"));
        b.marker("npc", tx, 1, tz, role("guard"));
        b.marker("npc", 6, 1, 5, role("guard"));
    }

    // ------------------------------------------------------------------ Mogaro Kingdom

    /** A royal gate between two towers, opening onto a plaza with a crowned obelisk and twin fountains. */
    static void mogaroRoyalGate(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d, c = w / 2;
        pad(b, Blocks.QUARTZ_BRICKS, 30);
        b.fill(1, 0, 1, w - 2, 0, d - 2, Blocks.SMOOTH_STONE);
        b.fill(c - 2, 0, 0, c + 2, 0, d - 1, Blocks.POLISHED_DIORITE);
        // gate towers and arch
        for (int tx : new int[]{c - 6, c + 6}) {
            b.fill(tx - 2, 1, 1, tx + 2, 16, 5, Blocks.QUARTZ_BRICKS);
            for (int y = 4; y <= 14; y += 5) b.fill(tx, y, 1, tx, y + 1, 1, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
            b.fill(tx - 2, 16, 1, tx + 2, 16, 5, Blocks.GOLD_BLOCK);
            cone(b, tx, 3, 17, 3, Blocks.BLUE_CONCRETE);
        }
        b.fill(c - 4, 1, 2, c + 4, 13, 4, Blocks.QUARTZ_BRICKS);
        for (int x = c - 3; x <= c + 3; x++) {
            int top = 7 + (int) Math.round(2.5 * Math.cos((x - c) * Math.PI / 7.0));
            b.clear(x, 1, 2, x, top, 4);
        }
        b.fill(c - 4, 12, 2, c + 4, 12, 2, Blocks.GOLD_BLOCK);
        b.set(c, 13, 2, Blocks.CHISELED_QUARTZ_BLOCK);
        b.fill(c - 3, 1, 3, c + 3, 1, 3, Blocks.IRON_BARS);
        b.clear(c - 1, 1, 3, c + 1, 1, 3);
        // plaza: crowned obelisk and fountains
        b.fill(c - 1, 1, 13, c + 1, 1, 15, Blocks.QUARTZ_BRICKS);
        b.fill(c, 2, 14, c, 9, 14, Blocks.QUARTZ_PILLAR);
        b.set(c, 10, 14, Blocks.GOLD_BLOCK);
        for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) b.set(c + o[0], 11, 14 + o[1], Blocks.GOLD_BLOCK);
        for (int fx : new int[]{4, w - 5}) {
            b.cylinder(fx, 13, 1, 1, 2, Blocks.QUARTZ_BRICKS, true);
            b.cylinder(fx, 13, 1, 1, 1, Blocks.WATER, false);
            b.fill(fx, 1, 13, fx, 2, 13, Blocks.QUARTZ_PILLAR);
            b.set(fx, 3, 13, Blocks.SEA_LANTERN);
        }
        for (int z = 8; z < d - 1; z += 4) {
            b.set(1, 1, z, Blocks.QUARTZ_PILLAR);
            b.set(1, 2, z, Blocks.LANTERN);
            b.set(w - 2, 1, z, Blocks.QUARTZ_PILLAR);
            b.set(w - 2, 2, z, Blocks.LANTERN);
        }
        banner(b, c - 4, 1, 6, Blocks.BLUE_BANNER);
        banner(b, c + 4, 1, 6, Blocks.BLUE_BANNER);
        b.chest(c + 6, 1, 6, Direction.SOUTH, Buildings.LOOT_SHOP);
        b.marker("npc", c - 2, 1, 6, role("guard"));
        b.marker("npc", c + 2, 1, 6, role("guard"));
        b.marker("npc", c, 1, 17, role("noble"));
    }

    private LandmarkBuildersExtra() {}
}
