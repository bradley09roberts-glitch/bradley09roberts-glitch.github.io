package com.grandline.world.landmark;

import com.grandline.registry.ArchBlocks;
import com.grandline.world.Island;
import com.grandline.world.build.Arch;
import com.grandline.world.build.Arch.Mats;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import static com.grandline.world.landmark.LandmarkKit.*;
import static com.grandline.world.landmark.LandmarksD1.*;

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

    // ------------------------------------------------------------------ shared helpers

    /** Level pad: foundation down to the ground, a floor at y=0 and open air above. */
    private static void pad(Builder b, Block floor, int clearTo) {
        b.foundation(0, 0, b.w - 1, b.d - 1, floor, 14);
        b.fill(0, 0, 0, b.w - 1, 0, b.d - 1, floor);
        b.clear(0, 1, 0, b.w - 1, clearTo, b.d - 1);
    }

    /**
     * Round tower centred (cx, cz), radius r, walls from y=1 to {@code top} in {@code wall} with slit windows, a
     * floor at the top, and a steep conical roof of {@code roof} (with a {@code cap} ring at the eave) and a finial.
     */
    private static void roundTower(Builder b, int cx, int cz, int r, int top, BlockState wall, BlockState roof, BlockState cap, BlockState finial) {
        for (int y = 1; y <= top; y++) ring(b, cx, cz, y, r, wall);
        for (int y = 1; y < top; y++) disc(b, cx, cz, y, r - 0.9, Blocks.AIR.defaultBlockState());
        disc(b, cx, cz, top, r - 0.5, wall);
        for (int y = 4; y < top - 1; y += 4) {
            b.set(cx + r, y, cz, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.Z));
            b.set(cx - r, y, cz, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.Z));
            b.set(cx, y, cz + r, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
            b.set(cx, y, cz - r, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
        }
        if (cap != null) ring(b, cx, cz, top + 1, r + 0.4, cap);
        spire(b, cx, cz, top + 1, r + 0.6, 0.55, roof, finial);
    }

    // ------------------------------------------------------------------ Tequila Wolf: the endless bridge

    private static final int BRIDGE_DECK = 9;

    /** Deck block height of the bridge at local z (it ramps up from the island where the ground is high). */
    private static int bridgeDeck(Builder b, Island isl, int z) {
        double gh = isl.height(b.wx(b.w / 2, 0), b.wz(b.w / 2, 0));
        int g0 = Double.isNaN(gh) ? -40 : (int) Math.floor(gh) - b.oy;
        return g0 > 0 ? Math.min(BRIDGE_DECK, g0 + 1 + z / 2) : BRIDGE_DECK;
    }

    /** A labourers' hut on the deck: weathered planks on dark posts under a slate lean-to, a door onto the road. */
    private static void labourHut(Builder b, int dy, int hz) {
        BlockState wp = k(ArchBlocks.WEATHERED_PLANKS);
        b.fill(1, dy + 1, hz, 4, dy + 3, hz + 5, wp);
        b.clear(2, dy + 1, hz + 1, 3, dy + 3, hz + 4);
        for (int[] p : new int[][]{{1, hz}, {4, hz}, {1, hz + 5}, {4, hz + 5}}) b.fill(p[0], dy + 1, p[1], p[0], dy + 3, p[1], beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
        Arch.leanTo(b, Mats.of(BuildStyle.STONE_TOWN, hz), 1, hz, 5, hz + 5, dy + 5, Direction.EAST);
        b.fill(1, dy + 4, hz, 1, dy + 4, hz + 5, wp);
        b.set(2, dy + 4, hz, wp);
        b.set(2, dy + 4, hz + 5, wp);
        b.door(4, dy + 1, hz + 2, Blocks.SPRUCE_DOOR, Direction.EAST);
        b.set(4, dy + 2, hz + 4, Arch.along(k(ArchBlocks.LATTICE_WINDOW), Direction.Axis.Z));
        b.bed(2, dy + 1, hz + 4, Blocks.RED_BED, Direction.NORTH);
        b.barrel(3, dy + 1, hz + 1, Buildings.LOOT_WAREHOUSE);
        b.set(2, dy + 1, hz + 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        hang(b, 3, dy + 3, hz + 3, s(Blocks.LANTERN));
    }

    /**
     * Tequila Wolf's bridge, built by forced labour for centuries: a stone causeway from the island far out to sea
     * (local z = 0 on land, increasing seaward) on round arches between piers with cutwaters, a parapet with lamp
     * posts, a central cart lane, toll gates straddling the road, labourers' huts, stacked blocks and rubble, and an
     * unfinished end where piers rise out of the water amid scaffolding and cranes.
     */
    static void endlessBridge(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d, deck = BRIDGE_DECK;
        int unfinished = d - 34;
        BlockState brick = s(Blocks.STONE_BRICKS), cracked = s(Blocks.CRACKED_STONE_BRICKS), mossy = s(Blocks.MOSSY_STONE_BRICKS);
        BlockState lane = s(Blocks.POLISHED_ANDESITE), wallB = s(Blocks.STONE_BRICK_WALL), stairs = s(Blocks.STONE_BRICK_STAIRS);
        for (int z = 0; z < d; z++) {
            int dy = bridgeDeck(b, isl, z);
            boolean end = z >= unfinished;
            int gap = end ? (z - unfinished) : 0;
            double gl = isl.height(b.wx(w / 2, z), b.wz(w / 2, z));
            int ground = Double.isNaN(gl) ? -40 : (int) Math.floor(gl) - b.oy;
            boolean land = ground >= dy - 1;
            int o = z % 16;
            boolean pier = o < 3;
            // the deck (broken away toward the unfinished end) on a string course of corbels
            for (int x = 1; x <= w - 2; x++) {
                boolean hole = end && ((x * 31 + z * 17) % 9 < gap / 4 || gap > 26);
                if (hole) continue;
                b.set(x, dy - 1, z, brick);
                BlockState top = x == w / 2 ? lane : (x == w / 2 - 2 || x == w / 2 + 2) && (z & 3) == 0 ? cracked : brick;
                if (x == 1 || x == w - 2) top = brick;
                b.set(x, dy, z, top);
            }
            b.clear(0, dy + 1, z, w - 1, dy + 6, z);
            if (!end || gap < 24) {
                b.set(0, dy - 1, z, Arch.stair(stairs, Direction.EAST, Half.TOP));
                b.set(w - 1, dy - 1, z, Arch.stair(stairs, Direction.WEST, Half.TOP));
            }
            // parapet with pilasters over the piers and lamp posts between
            if (!end || gap < 10) {
                for (int x : new int[]{1, w - 2}) {
                    if (pier && o == 1) {
                        b.set(x, dy + 1, z, brick);
                        b.set(x, dy + 2, z, Arch.slab(s(Blocks.STONE_BRICK_SLAB), SlabType.BOTTOM));
                    } else b.set(x, dy + 1, z, Arch.connect(wallB, true, false, true, false));
                    if (o == 9) {
                        b.set(x, dy + 1, z, Arch.connect(wallB, true, false, true, false).setValue(WallBlock.UP, true));
                        b.set(x, dy + 2, z, Arch.connect(wallB, false, false, false, false));
                        b.set(x, dy + 3, z, Arch.lantern(k(ArchBlocks.BRASS_LANTERN), false));
                    }
                }
            }
            if (land) {
                // embankment over the island
                b.foundation(0, z, w - 1, z, Blocks.COBBLESTONE, 12);
                for (int x = 0; x < w; x++) for (int y = ground + 1; y < dy - 1; y++) b.set(x, y, z, x == 0 || x == w - 1 ? mossy : brick);
                continue;
            }
            if (pier && !(end && gap > 26)) {
                // pier founded on the seabed, with pointed cutwaters on both faces
                b.fill(1, 0, z, w - 2, dy - 2, z, b.rand(z, 5) == 0 ? mossy : brick);
                b.foundation(1, z, w - 2, z, Blocks.STONE_BRICKS, 45);
                if (o == 1) {
                    b.fill(0, 0, z, 0, dy - 4, z, brick);
                    b.fill(w - 1, 0, z, w - 1, dy - 4, z, brick);
                    b.set(0, dy - 3, z, Arch.stair(stairs, Direction.EAST, Half.BOTTOM));
                    b.set(w - 1, dy - 3, z, Arch.stair(stairs, Direction.WEST, Half.BOTTOM));
                    b.foundation(0, z, 0, z, Blocks.STONE_BRICKS, 45);
                    b.foundation(w - 1, z, w - 1, z, Blocks.STONE_BRICKS, 45);
                    b.set(0, 1, z, mossy);
                    b.set(w - 1, 1, z, mossy);
                } else {
                    for (int y = 0; y <= dy - 4; y++) {
                        b.set(0, y, z, Arch.stair(stairs, o == 0 ? Direction.SOUTH : Direction.NORTH, Half.BOTTOM));
                        b.set(w - 1, y, z, Arch.stair(stairs, o == 0 ? Direction.SOUTH : Direction.NORTH, Half.BOTTOM));
                    }
                }
            } else if (!end || gap < 18) {
                // a round arch between the piers: an andesite ring over the opening, brick spandrels to the deck
                double rr = Math.sqrt(Math.max(0, 36 - (o - 9) * (o - 9)));
                int archTop = Math.min(dy - 2, 1 + (int) Math.round(rr));
                b.fill(1, archTop + 1, z, w - 2, dy - 2, z, brick);
                b.fill(1, archTop, z, w - 2, archTop, z, lane);
            }
        }
        // boat landings: ladders up both faces of a pier near each end, the parapet opened above them
        for (int lz : new int[]{32, unfinished - 30}) {
            int dy = bridgeDeck(b, isl, lz);
            double gl = isl.height(b.wx(w / 2, lz), b.wz(w / 2, lz));
            if (!Double.isNaN(gl) && (int) Math.floor(gl) - b.oy >= dy - 1) continue; // on land: no landing needed
            for (int x : new int[]{0, w - 1}) {
                Direction out = x == 0 ? Direction.WEST : Direction.EAST;
                b.ladderUp(x, -1, lz, dy + 3, out);
                b.clear(x - out.getStepX(), dy + 1, lz, x - out.getStepX(), dy + 2, lz);
            }
        }
        // toll gates straddling the road, with the labourers' huts between them
        for (int gz : new int[]{140, 240}) {
            if (gz + 4 >= unfinished) continue;
            int dy = bridgeDeck(b, isl, gz);
            b.fill(0, dy + 1, gz, w - 1, dy + 8, gz + 3, brick);
            for (int z = gz; z <= gz + 3; z++)
                for (int x = 3; x <= w - 4; x++) {
                    int dx = Math.abs(x - w / 2);
                    int topY = dy + 3 + (dx <= 1 ? 1 : 0);
                    b.clear(x, dy + 1, z, x, topY, z);
                }
            b.fill(0, dy + 9, gz, w - 1, dy + 9, gz + 3, lane);
            merlons(b, 0, gz, w - 1, gz + 3, dy + 10, brick);
            for (int x : new int[]{2, w - 3}) {
                b.set(x, dy + 6, gz, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
                b.set(x, dy + 6, gz + 3, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
            }
            b.set(w / 2, dy + 6, gz - 1, banner(Blocks.WHITE_WALL_BANNER, Direction.NORTH));
            b.set(w / 2, dy + 6, gz + 4, banner(Blocks.WHITE_WALL_BANNER, Direction.SOUTH));
            hang(b, w / 2, dy + 3, gz + 1, s(Blocks.LANTERN));
        }
        for (int hz : new int[]{90, 190, 290}) {
            if (hz + 6 >= unfinished) continue;
            int dy = bridgeDeck(b, isl, hz);
            labourHut(b, dy, hz);
            b.marker("npc", 6, dy + 1, hz + 2, LandmarkKit.role("villager"));
        }
        // stone blocks waiting to be laid, carts of rubble, a chain-gang post
        int dyE = bridgeDeck(b, isl, unfinished);
        for (int i = 0; i < 6; i++) {
            int z = unfinished - 10 + i * 2;
            int x = 2 + (i % 3) * 3;
            b.set(x, dyE + 1, z, i % 2 == 0 ? brick : s(Blocks.COBBLESTONE));
            if (i % 3 == 0) b.set(x, dyE + 2, z, brick);
        }
        Arch.cargo(b, 8, dyE + 1, unfinished - 12, 0);
        Arch.cargo(b, 2, dyE + 1, unfinished - 13, 2);
        b.set(8, dyE + 1, unfinished - 5, Arch.decor(ArchBlocks.CARGO_SACKS, Direction.NORTH));
        b.set(2, dyE + 1, unfinished - 3, Arch.decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        // the unfinished end: piers rising out of the water inside timber scaffolding, two cranes
        for (int z = unfinished + 4; z < d - 2; z += 4)
            for (int x : new int[]{1, w - 2}) {
                for (int y = -2; y <= deck + 4; y++) b.set(x, y, z, y % 4 == 0 ? s(Blocks.SPRUCE_PLANKS) : Arch.connect(s(Blocks.SPRUCE_FENCE), false, false, false, false));
                b.foundation(x, z, x, z, Blocks.SPRUCE_LOG, 40);
            }
        for (int z = unfinished + 4; z < d - 2; z += 4)
            for (int y = 4; y <= deck + 4; y += 4) b.fill(2, y, z, w - 3, y, z, Arch.slab(s(Blocks.SPRUCE_SLAB), SlabType.TOP));
        for (int z = d - 6; z <= d - 4; z++) { // a new pier rising out of the water
            b.fill(2, 0, z, w - 3, 2 + (z % 2), z, b.rand(z * 3, 3) == 0 ? mossy : brick);
            b.foundation(2, z, w - 3, z, Blocks.STONE_BRICKS, 45);
        }
        for (int cr = 0; cr < 2; cr++) {
            int cz = unfinished + 2 + cr * 14, cx = cr == 0 ? 1 : w - 2;
            for (int y = deck + 1; y <= deck + 14; y++) b.set(cx, y, cz, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
            int dir = cr == 0 ? 1 : -1;
            for (int i = 1; i <= 8; i++) b.set(cx, deck + 14, cz + i * dir, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
            b.set(cx, deck + 13, cz + dir, Arch.stair(s(Blocks.SPRUCE_STAIRS), dir > 0 ? Direction.NORTH : Direction.SOUTH, Half.TOP));
            for (int y = deck + 13; y >= deck + 6; y--) b.set(cx, y, cz + 8 * dir, Blocks.CHAIN);
            b.set(cx, deck + 5, cz + 8 * dir, brick);
        }
        b.chest(w / 2, dyE + 1, unfinished - 1, Direction.SOUTH, Buildings.LOOT_WAREHOUSE);
        b.marker("npc", w / 2, bridgeDeck(b, isl, 12) + 1, 12, LandmarkKit.role("jailer"));
        b.marker("npc", w / 2 + 2, dyE + 1, unfinished - 4, LandmarkKit.role("jailer"));
        b.marker("npc", w / 2 - 2, dyE + 1, unfinished - 6, LandmarkKit.role("villager"));
        b.marker("landmark", w / 2, deck + 1, unfinished, new CompoundTag());
    }

    // ------------------------------------------------------------------ Germa Kingdom: the moored fleet

    /**
     * A ship hull along local z from z0 (bow) to z0+len-1 (stern), centred on cx with half-beam hw: a solid dark hull
     * with a Germa-blue stripe at the waterline, a spruce deck at y = 2 rising one block at the forecastle and the
     * quarterdeck, and a railed gunwale. y = 0 is the sea surface.
     */
    private static void hull(Builder b, int cx, int z0, int len, int hw) {
        BlockState side = s(Blocks.DARK_OAK_PLANKS), deck = s(Blocks.SPRUCE_PLANKS), stripe = s(Blocks.BLUE_CONCRETE), rail = s(Blocks.DARK_OAK_FENCE);
        for (int i = 0; i < len; i++) {
            double t = i / (double) (len - 1);
            double bow = t < 0.28 ? Math.pow(Math.sin(Math.PI / 2 * t / 0.28), 0.85) : 1;
            double stern = t > 0.85 ? 1 - 0.35 * (t - 0.85) / 0.15 : 1;
            int half = Math.max(1, (int) Math.round(hw * bow * stern));
            int dy = (t < 0.12 || t > 0.9) ? 1 : 0;
            int z = z0 + i;
            if (half >= 3) b.fill(cx - half + 2, -4, z, cx + half - 2, -4, z, side);
            b.fill(cx - half + 1, -3, z, cx + half - 1, -3, z, side);
            b.fill(cx - half, -2, z, cx + half, 1 + dy, z, side);
            b.set(cx - half, 1, z, stripe);
            b.set(cx + half, 1, z, stripe);
            b.fill(cx - half + 1, 2 + dy, z, cx + half - 1, 2 + dy, z, deck);
            b.set(cx - half, 2 + dy, z, side);
            b.set(cx + half, 2 + dy, z, side);
            b.set(cx - half, 3 + dy, z, Arch.connect(rail, true, false, true, false));
            b.set(cx + half, 3 + dy, z, Arch.connect(rail, true, false, true, false));
            if (i == 0) b.fill(cx - half, 3 + dy, z, cx + half, 3 + dy, z, Arch.connect(rail, false, true, false, true));
            if (i == len - 1) b.fill(cx - half, 3 + dy, z, cx + half, 3 + dy, z, Arch.connect(rail, false, true, false, true));
        }
    }

    /** A boarding ladder up the hull side at (x, z), facing {@code out}, with the rail opened above it. */
    private static void boardingLadder(Builder b, int x, int z, Direction out) {
        int lx = x + out.getStepX(), lz = z + out.getStepZ();
        ladder(b, lx, -1, lz, 5, out, null);
        b.clear(x, 3, z, x, 4, z);
    }

    /** A mast stepped on the deck at (x, z) up to {@code top}, with a square sail (blue head) and a blue pennant. */
    private static void mast(Builder b, int x, int z, int deckY, int top, int span) {
        for (int y = deckY + 1; y <= top; y++) b.set(x, y, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        b.fill(x - span - 1, top - 1, z, x + span + 1, top - 1, z, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        for (int y = top - 8; y <= top - 2; y++)
            for (int dx = -span; dx <= span; dx++) b.set(x + dx, y, z + 1, y == top - 2 ? s(Blocks.BLUE_WOOL) : s(Blocks.WHITE_WOOL));
        for (int dx = -1; dx <= 1; dx++) b.set(x + dx, top - 5, z + 1, s(Blocks.BLUE_WOOL));
        b.set(x, top + 1, z, s(Blocks.BLUE_WOOL));
        b.set(x, top + 2, z, s(Blocks.BLUE_WOOL));
    }

    /**
     * One of the giant sea snails that tow the Germa ships, facing north with its head at z0: a cream body low in the
     * water, eye stalks, a coiled shell (ochre with a dark spiral) standing on its back under a blue harness.
     */
    private static void seaSnail(Builder b, int cx, int z0) {
        BlockState body = k(ArchBlocks.CREAM_PLASTER), shellA = k(ArchBlocks.OCHRE_PLASTER), shellB = s(Blocks.BROWN_TERRACOTTA);
        for (int z = z0; z <= z0 + 7; z++) {
            int hw = z == z0 || z == z0 + 7 ? 0 : 1;
            int top = z <= z0 + 1 ? 2 : 1;
            b.fill(cx - hw, -1, z, cx + hw, top, z, body);
        }
        b.set(cx, 0, z0, s(Blocks.BROWN_TERRACOTTA));
        for (int ex : new int[]{cx - 1, cx + 1}) {
            b.fill(ex, 3, z0, ex, 4, z0, Arch.connect(s(Blocks.BIRCH_FENCE), false, false, false, false));
            b.set(ex, 5, z0, s(Blocks.BLACK_CONCRETE));
        }
        double sy = 4, sz = z0 + 4.5;
        for (int y = 0; y <= 8; y++)
            for (int z = z0 + 1; z <= z0 + 7; z++) {
                double dy = y - sy, dz = z - sz, r = Math.sqrt(dy * dy + dz * dz);
                if (r > 3.6) continue;
                double a = Math.atan2(dy, dz) / (2 * Math.PI) + 0.5;
                boolean line = ((r * 0.75 + a) % 1.0) < 0.28;
                for (int x = cx - 2; x <= cx + 2; x++) {
                    if (Math.abs(x - cx) == 2 && r > 2.6) continue;
                    b.set(x, y, z, line ? shellB : shellA);
                }
            }
        for (int x = cx - 2; x <= cx + 2; x++) b.set(x, 1, z0 + 3, s(Blocks.BLUE_CONCRETE));
        for (int x = cx - 2; x <= cx + 2; x++) b.set(x, 1, z0 + 6, s(Blocks.BLUE_CONCRETE));
    }

    /**
     * The Germa Kingdom has no land: its nation is a fleet towed by giant sea snails. Here it rides at anchor: the
     * castle flagship (a white castle with blue spires and a throne hall) drawn by two snails, flanked by four escort
     * ships each with its own snail, all lashed together with railed gangways; cannons, cargo and a stern cabin.
     */
    static void germaFleet(Builder b, Island isl, BuildStyle st) {
        b.clear(0, 1, 0, b.w - 1, 40, b.d - 1);
        BlockState pale = k(ArchBlocks.PALE_MASONRY), blue = s(Blocks.BLUE_CONCRETE), gold = s(Blocks.GOLD_BLOCK);
        int fx = 30;
        // ---- the flagship and its two snails
        hull(b, fx, 8, 51, 10);
        for (int sx : new int[]{fx - 5, fx + 5}) {
            seaSnail(b, sx, 1);
            for (int z = 9; z <= 12; z++) b.set(sx, 2, z, log(Blocks.CHAIN, Direction.Axis.Z));
        }
        b.fill(fx, 3, 8, fx, 4, 8, gold);
        b.set(fx, 5, 8, blue);
        // ---- the escorts, each with a snail
        for (int[] e : new int[][]{{9, 9}, {51, 9}, {9, 38}, {51, 38}}) {
            hull(b, e[0], e[1], 20, 4);
            seaSnail(b, e[0], e[1] - 8);
            mast(b, e[0], e[1] + 9, 2, 17, 3);
            // stern cabin
            int cz = e[1] + 14;
            b.fill(e[0] - 2, 3, cz, e[0] + 2, 5, cz + 2, Blocks.DARK_OAK_PLANKS);
            b.clear(e[0] - 1, 3, cz + 1, e[0] + 1, 4, cz + 1);
            b.door(e[0], 3, cz, Blocks.SPRUCE_DOOR, Direction.NORTH);
            b.fill(e[0] - 3, 6, cz - 1, e[0] + 3, 6, cz + 3, Arch.slab(s(Blocks.DARK_OAK_SLAB), SlabType.BOTTOM));
            b.fill(e[0] - 2, 6, cz, e[0] + 2, 6, cz + 2, blue);
            b.set(e[0] + 1, 3, cz + 1, Blocks.BARREL);
            for (int z = e[1] + 4; z <= e[1] + 12; z += 4) {
                b.set(e[0] - 3, 3, z, Arch.decor(ArchBlocks.DECK_CANNON, Direction.WEST));
                b.set(e[0] + 3, 3, z, Arch.decor(ArchBlocks.DECK_CANNON, Direction.EAST));
            }
            b.set(e[0] - 1, 3, e[1] + 6, Arch.decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
            b.set(e[0] + 1, 3, e[1] + 12, k(ArchBlocks.CARGO_CRATE));
            b.marker("npc", e[0], 3, e[1] + 12, LandmarkKit.role("guard"));
            boolean west = e[0] < fx;
            boardingLadder(b, e[0] + (west ? -4 : 4), e[1] + 10, west ? Direction.WEST : Direction.EAST);
        }
        // gangways at deck level, railed (the ships' rails are opened where they land)
        for (int gz : new int[]{18, 47}) {
            b.clear(12, 3, gz, 22, 3, gz + 1);
            b.clear(38, 3, gz, 48, 3, gz + 1);
            for (int[] span : new int[][]{{14, 20}, {40, 46}}) {
                b.fill(span[0], 2, gz, span[1], 2, gz + 1, k(ArchBlocks.DOCK_PLANKS));
                for (int x = span[0]; x <= span[1]; x++) {
                    b.set(x, 3, gz - 1, Arch.connect(s(Blocks.SPRUCE_FENCE), false, x < span[1], false, x > span[0]));
                    b.set(x, 3, gz + 2, Arch.connect(s(Blocks.SPRUCE_FENCE), false, x < span[1], false, x > span[0]));
                }
            }
        }
        // ---- the castle on the flagship: white walls, a blue band, crenellations, four spired towers and a keep
        int x1 = fx - 8, x2 = fx + 8, z1 = 24, z2 = 44;
        b.fill(x1, 2, z1, x2, 2, z2, Blocks.POLISHED_ANDESITE);
        b.fill(x1, 3, z1, x2, 12, z2, pale);
        b.clear(x1 + 1, 3, z1 + 1, x2 - 1, 11, z2 - 1);
        b.fill(x1, 12, z1, x2, 12, z2, pale);
        for (int x = x1; x <= x2; x++) {
            b.set(x, 11, z1, blue);
            b.set(x, 11, z2, blue);
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1, 11, z, blue);
            b.set(x2, 11, z, blue);
        }
        rail(b, x1, 13, z1, x2, z2, pale);
        merlons(b, x1, z1, x2, z2, 14, pale);
        for (int z = z1 + 3; z <= z2 - 3; z += 4)
            for (int x : new int[]{x1, x2}) b.fill(x, 5, z, x, 9, z, Arch.along(s(Blocks.BLUE_STAINED_GLASS_PANE), Direction.Axis.Z));
        for (int x = x1 + 3; x <= x2 - 3; x += 4)
            if (Math.abs(x - fx) > 2) b.fill(x, 5, z1, x, 9, z1, Arch.along(s(Blocks.BLUE_STAINED_GLASS_PANE), Direction.Axis.X));
        for (int x = x1 + 3; x <= x2 - 3; x += 4) b.fill(x, 5, z2, x, 9, z2, Arch.along(s(Blocks.BLUE_STAINED_GLASS_PANE), Direction.Axis.X));
        for (int x = fx - 1; x <= fx + 1; x++) b.bigDoor(x, 3, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.fill(fx - 2, 6, z1 - 1, fx + 2, 6, z1 - 1, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.SOUTH, Half.TOP));
        b.set(fx, 8, z1 - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        Builder deck = sub(b, 0, 2, 0, b.w, b.d, Rotation.NONE);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) roundTower(deck, c[0], c[1], 3, 15, pale, blue, blue, gold);
        Builder roof = sub(b, 0, 12, 0, b.w, b.d, Rotation.NONE);
        roundTower(roof, fx, 36, 4, 12, pale, blue, blue, gold);
        // the throne hall
        b.fill(fx - 1, 3, z1 + 1, fx + 1, 3, z2 - 5, Blocks.BLUE_CARPET);
        b.fill(fx - 4, 3, z2 - 4, fx + 4, 3, z2 - 1, Arch.slab(s(Blocks.SMOOTH_QUARTZ_SLAB), SlabType.TOP));
        b.set(fx, 4, z2 - 2, Arch.stair(s(Blocks.QUARTZ_STAIRS), Direction.NORTH, Half.BOTTOM));
        b.fill(fx - 1, 4, z2 - 1, fx + 1, 4, z2 - 1, gold);
        b.fill(fx, 5, z2 - 1, fx, 7, z2 - 1, gold);
        for (int x : new int[]{fx - 3, fx + 3}) b.set(x, 7, z2 - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        for (int[] p : new int[][]{{fx - 4, 28}, {fx + 4, 28}, {fx - 4, 34}, {fx + 4, 34}}) b.fill(p[0], 3, p[1], p[0], 11, p[1], k(ArchBlocks.MARBLE_COLUMN));
        for (int z = 29; z <= 35; z += 6) {
            b.fill(fx, 9, z, fx, 11, z, Blocks.CHAIN);
            b.set(fx, 8, z, Arch.lantern(s(Blocks.LANTERN), true));
        }
        b.chest(x2 - 1, 3, z2 - 1, Direction.WEST, Buildings.LOOT_TREASURE);
        b.marker("npc", fx + 2, 4, z2 - 3, LandmarkKit.role("noble"));
        b.marker("npc", fx - 3, 3, z1 + 3, LandmarkKit.role("guard"));
        b.marker("npc", fx + 3, 3, z1 + 3, LandmarkKit.role("guard"));
        // ---- the flagship's decks: masts, cannons, cargo, a stern cabin
        mast(b, fx, 15, 2, 28, 5);
        mast(b, fx, 45, 2, 24, 4);
        for (int z = 14; z <= 20; z += 3) {
            b.set(fx - 8, 3, z, Arch.decor(ArchBlocks.DECK_CANNON, Direction.WEST));
            b.set(fx + 8, 3, z, Arch.decor(ArchBlocks.DECK_CANNON, Direction.EAST));
        }
        for (int z = 46; z <= 50; z += 4) {
            b.set(fx - 9, 3, z, Arch.decor(ArchBlocks.DECK_CANNON, Direction.WEST));
            b.set(fx + 9, 3, z, Arch.decor(ArchBlocks.DECK_CANNON, Direction.EAST));
        }
        Arch.cargo(b, fx - 4, 3, 19, 0);
        Arch.cargo(b, fx + 4, 3, 20, 2);
        b.set(fx + 5, 3, 19, Arch.decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        int sz = 48;
        b.fill(fx - 3, 3, sz, fx + 3, 6, sz + 3, Blocks.DARK_OAK_PLANKS);
        b.clear(fx - 2, 3, sz + 1, fx + 2, 5, sz + 2);
        b.bigDoor(fx, 3, sz, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.fill(fx - 4, 7, sz - 1, fx + 4, 7, sz + 4, Arch.slab(s(Blocks.DARK_OAK_SLAB), SlabType.BOTTOM));
        b.fill(fx - 3, 6, sz, fx + 3, 6, sz, blue);
        for (int x = fx - 3; x <= fx + 3; x += 2) b.set(x, 4, sz + 3, Arch.along(s(Blocks.BLUE_STAINED_GLASS_PANE), Direction.Axis.X));
        b.set(fx - 2, 3, sz + 2, Blocks.CARTOGRAPHY_TABLE);
        b.set(fx + 2, 3, sz + 2, Blocks.BARREL);
        hang(b, fx, 5, sz + 1, s(Blocks.LANTERN));
        boardingLadder(b, fx - 10, 33, Direction.WEST);
        boardingLadder(b, fx + 10, 33, Direction.EAST);
        b.marker("npc", fx, 3, 21, LandmarkKit.role("guard"));
        b.marker("dock", fx, 1, 4, new CompoundTag());
    }

    // ------------------------------------------------------------------ Rommel Kingdom

    /**
     * A snowbound North Blue keep: a fieldstone curtain wall with stone battlements and four round towers under steep
     * slate cones, a gatehouse with a raised portcullis, a courtyard with a smithy, a well and a pine, and a
     * three-storey keep under a slate roof with dormers and a smoking chimney: the great hall with its hearth and the
     * lord's seat, the lord's chamber above and a store in the attic, joined by stairs; a stair to the wall walk.
     */
    static void rommelKeep(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d, gx = w / 2;
        Mats m = Mats.of(BuildStyle.SNOW, b.seed);
        m.shutter = ArchBlocks.RED_SHUTTER.get();
        BlockState field = k(ArchBlocks.FIELDSTONE), brick = s(Blocks.STONE_BRICKS), wallB = s(Blocks.STONE_BRICK_WALL);
        BlockState slate = k(ArchBlocks.SLATE_ROOF_TILES), slateSlab = Arch.slab(k(ArchBlocks.SLATE_ROOF_TILE_SLAB), SlabType.BOTTOM);
        pad(b, Blocks.SNOW_BLOCK, 34);
        for (int z = 0; z <= 11; z++) b.fill(gx - 1, 0, z, gx + 1, 0, z, Blocks.GRAVEL);
        b.fill(gx - 1, 0, 0, gx + 1, 0, 4, Blocks.STONE_BRICKS);
        // ---- curtain wall: two thick, walk at y = 6, stone battlements
        for (int x = 1; x <= w - 2; x++)
            for (int z = 1; z <= d - 2; z++) {
                boolean ring = x <= 2 || x >= w - 3 || z <= 2 || z >= d - 3;
                if (!ring) continue;
                boolean quoin = (x == 1 || x == w - 2) && (z == 1 || z == d - 2);
                for (int y = 1; y <= 6; y++) b.set(x, y, z, quoin || y == 1 ? brick : field);
                b.set(x, 6, z, Blocks.SNOW_BLOCK);
            }
        for (int x = 1; x <= w - 2; x++)
            for (int z = 1; z <= d - 2; z++) {
                boolean outer = x == 1 || x == w - 2 || z == 1 || z == d - 2;
                if (!outer) continue;
                boolean ns = z == 1 || z == d - 2;
                b.set(x, 7, z, Arch.connect(wallB, !ns, ns, !ns, ns));
                if (((x + z) & 1) == 0) b.set(x, 8, z, Arch.connect(wallB, false, false, false, false));
            }
        // stairs up to the walk inside the north wall, a guard posted on it
        flight(b, s(Blocks.STONE_BRICK_STAIRS), w - 4, 1, 8, Direction.SOUTH, 6, 1, brick, 1);
        b.marker("npc", w - 3, 7, 15, LandmarkKit.role("guard"));
        // four round towers with snow-capped slate cones
        for (int[] c : new int[][]{{3, 3}, {w - 4, 3}, {3, d - 4}, {w - 4, d - 4}})
            roundTower(b, c[0], c[1], 3, 10, field, slate, slateSlab, s(Blocks.SNOW_BLOCK));
        // the gatehouse: a raised portcullis, battlements, banners
        b.fill(gx - 3, 1, 0, gx + 3, 9, 3, field);
        for (int z = 0; z <= 3; z++) b.clear(gx - 1, 1, z, gx + 1, 4, z);
        b.fill(gx - 1, 4, 0, gx + 1, 4, 0, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
        b.fill(gx - 3, 9, 0, gx + 3, 9, 3, brick);
        for (int x = gx - 3; x <= gx + 3; x++)
            for (int z = 0; z <= 3; z++) if ((x == gx - 3 || x == gx + 3 || z == 0 || z == 3) && ((x + z) & 1) == 0) b.set(x, 10, z, Arch.connect(wallB, false, false, false, false));
        for (int x : new int[]{gx - 2, gx + 2}) b.set(x, 7, 0, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
        b.set(gx - 2, 6, 4, banner(Blocks.WHITE_WALL_BANNER, Direction.SOUTH));
        b.set(gx + 2, 6, 4, banner(Blocks.WHITE_WALL_BANNER, Direction.SOUTH));
        b.set(gx - 2, 3, 4, Arch.lantern(k(ArchBlocks.BRASS_LANTERN), false));
        b.set(gx + 2, 3, 4, Arch.lantern(k(ArchBlocks.BRASS_LANTERN), false));
        b.set(gx - 2, 2, 4, Arch.connect(s(Blocks.SPRUCE_FENCE), false, false, false, false));
        b.set(gx + 2, 2, 4, Arch.connect(s(Blocks.SPRUCE_FENCE), false, false, false, false));
        b.set(gx - 2, 1, 4, Arch.connect(s(Blocks.SPRUCE_FENCE), false, false, false, false));
        b.set(gx + 2, 1, 4, Arch.connect(s(Blocks.SPRUCE_FENCE), false, false, false, false));
        b.marker("npc", gx, 1, 5, LandmarkKit.role("guard"));
        // ---- the courtyard: a smithy under a lean-to, a well, hay, a pine
        Mats shed = Mats.of(BuildStyle.STONE_TOWN, b.seed + 1);
        b.fill(3, 1, 13, 3, 3, 19, field);
        Arch.leanTo(b, shed, 3, 13, 7, 19, 5, Direction.EAST);
        for (int z : new int[]{13, 19}) b.fill(7, 1, z, 7, 2, z, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
        b.set(4, 1, 14, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlastFurnaceBlock.FACING, Direction.EAST));
        b.set(4, 2, 14, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        b.set(4, 1, 15, Blocks.LAVA_CAULDRON);
        b.set(5, 1, 16, Blocks.ANVIL);
        b.set(4, 1, 18, Blocks.GRINDSTONE);
        b.barrel(4, 1, 17, Buildings.LOOT_WAREHOUSE);
        b.set(6, 1, 18, k(ArchBlocks.CARGO_CRATE));
        b.marker("npc", 6, 1, 15, LandmarkKit.role("blacksmith"));
        b.fill(21, 1, 6, 23, 1, 8, Blocks.COBBLESTONE_WALL);
        b.set(22, 1, 7, Blocks.WATER);
        b.fill(21, 2, 6, 21, 3, 6, Blocks.SPRUCE_FENCE);
        b.fill(23, 2, 8, 23, 3, 8, Blocks.SPRUCE_FENCE);
        b.fill(21, 4, 6, 23, 4, 8, slateSlab);
        b.set(5, 1, 6, Blocks.HAY_BLOCK);
        b.set(5, 2, 6, Blocks.HAY_BLOCK);
        b.set(6, 1, 6, Blocks.HAY_BLOCK);
        b.set(6, 1, 7, Blocks.SNOW_BLOCK);
        tree(b, 23, 1, 20, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, 6, 2, 31, null);
        // ---- the keep (x 8..20, z 12..24): floors at 0, 5, 10; a steep slate roof above
        int k1 = 8, k2 = 20, kz1 = 12, kz2 = 24, kc = (k1 + k2) / 2;
        for (int f = 0; f < 3; f++) {
            int y0 = f * 5;
            b.fill(k1 + 1, y0, kz1 + 1, k2 - 1, y0, kz2 - 1, f == 0 ? s(Blocks.STONE_BRICKS) : s(Blocks.SPRUCE_PLANKS));
            for (int x = k1; x <= k2; x++)
                for (int z = kz1; z <= kz2; z++) {
                    if (!(x == k1 || x == k2 || z == kz1 || z == kz2)) continue;
                    boolean corner = (x == k1 || x == k2) && (z == kz1 || z == kz2);
                    for (int y = y0 + 1; y <= y0 + 5; y++) b.set(x, y, z, corner && (y & 1) == 0 ? brick : field);
                }
            b.clear(k1 + 1, y0 + 1, kz1 + 1, k2 - 1, y0 + 4, kz2 - 1);
            for (int x = k1 + 2; x <= k2 - 2; x += 4) {
                if (f == 0 && Math.abs(x - kc) <= 1) continue;
                Arch.window(b, m, x, y0 + 2, kz1, Direction.NORTH, 2, true);
                Arch.window(b, m, x, y0 + 2, kz2, Direction.SOUTH, 2, true);
            }
            for (int z = kz1 + 3; z <= kz2 - 3; z += 3) {
                Arch.window(b, m, k1, y0 + 2, z, Direction.WEST, 2, true);
                if (!(f == 0 && z >= 16 && z <= 20)) Arch.window(b, m, k2, y0 + 2, z, Direction.EAST, 2, true);
            }
        }
        int ridge = Arch.gable(b, m, k1, kz1, k2, kz2, 16, false, 1, field);
        b.fill(kc, ridge, kz1 - 1, kc, ridge, kz2 + 1, Blocks.SNOW_BLOCK);
        Arch.chimney(b, m, k2 - 1, 18, 4, ridge + 1, true);
        b.bigDoor(kc, 1, kz1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        Arch.wallLantern(b, m, kc - 2, 3, kz1, Direction.NORTH);
        Arch.wallLantern(b, m, kc + 2, 3, kz1, Direction.NORTH);
        // great hall: hearth on the east wall, long table, the lord's seat, banners
        b.fill(k2 - 1, 1, 16, k2 - 1, 3, 20, brick);
        b.set(k2 - 1, 1, 18, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        b.set(k2 - 1, 2, 18, Blocks.AIR);
        longTableZ(b, 15, 20, 1, kc - 2, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        b.set(kc, 1, kz2 - 1, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.set(kc - 1, 2, kz2 - 1, banner(Blocks.WHITE_WALL_BANNER, Direction.NORTH));
        b.set(kc + 1, 2, kz2 - 1, banner(Blocks.WHITE_WALL_BANNER, Direction.NORTH));
        carpet(b, kc - 1, 1, kz1 + 1, kc + 1, kz1 + 2, Blocks.RED_CARPET);
        hang(b, kc, 4, 18, s(Blocks.LANTERN));
        b.marker("npc", kc + 2, 1, kz2 - 2, LandmarkKit.role("noble"));
        flight(b, s(Blocks.SPRUCE_STAIRS), k1 + 1, 1, kz2 - 1, Direction.NORTH, 5, 2, s(Blocks.SPRUCE_PLANKS), 1);
        // lord's chamber
        b.bed(k2 - 2, 6, kz2 - 2, Blocks.RED_BED, Direction.SOUTH);
        b.chest(k2 - 1, 6, kz2 - 1, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(k2 - 1, 6, kz1 + 1, facing(Blocks.LECTERN, Direction.WEST));
        b.set(kc + 2, 6, 16, k(ArchBlocks.PLANK_TABLE));
        chair(b, kc + 2, 6, 17, Blocks.SPRUCE_STAIRS, Direction.SOUTH);
        carpet(b, kc - 1, 6, 17, kc + 2, 20, Blocks.WHITE_CARPET);
        hang(b, kc, 9, 18, s(Blocks.LANTERN));
        flight(b, s(Blocks.SPRUCE_STAIRS), k1 + 2, 6, kz1 + 1, Direction.EAST, 5, 2, s(Blocks.SPRUCE_PLANKS), 6);
        // attic store
        for (int x = k1 + 4; x <= k2 - 2; x += 2) Arch.cargo(b, x, 11, kz2 - 1, x);
        b.set(k2 - 1, 11, kz1 + 1, Blocks.BARREL);
        b.set(k2 - 1, 11, kz1 + 2, Blocks.BARREL);
        b.set(k2 - 1, 12, kz1 + 1, Arch.lantern(s(Blocks.LANTERN), false));
    }

    // ------------------------------------------------------------------ Oykot Kingdom

    /** Walls of a two-storey palace range: pale masonry below a quartz string course, white plaster above, a cornice. */
    private static void palaceRange(Builder b, Mats m, int x1, int z1, int x2, int z2, int storeys) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (!(x == x1 || x == x2 || z == z1 || z == z2)) continue;
                for (int f = 0; f < storeys; f++) {
                    int y0 = f * 5;
                    for (int y = y0 + 1; y <= y0 + 4; y++) b.set(x, y, z, f == 0 ? m.wall : m.upperWall);
                    b.set(x, y0 + 5, z, s(Blocks.QUARTZ_BRICKS));
                }
            }
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(c[0], 1, c[1], c[0], storeys * 5, c[1], m.frame);
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, m.floor);
        for (int f = 1; f < storeys; f++) b.fill(x1 + 1, f * 5, z1 + 1, x2 - 1, f * 5, z2 - 1, m.floor);
        for (int f = 0; f < storeys; f++) b.clear(x1 + 1, f * 5 + 1, z1 + 1, x2 - 1, f * 5 + 4, z2 - 1);
    }

    /**
     * Oykot's white palace in its garden: a two-storey range under teal hip roofs, a three-storey central pavilion
     * fronted by a portico of quartz columns carrying a balcony and crowned by a pedimented roof, and a clock tower
     * with a belfry rising behind. Formal gardens with two fountains, hedges and flower beds line the approach. Inside:
     * the throne hall, a library (west) and a dining hall (east), state rooms and the king's study above, linked by
     * stairs, and a door from the top floor onto the portico balcony.
     */
    static void oykotPalace(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d, c = w / 2; // 35 x 29
        Mats m = Mats.of(BuildStyle.PALACE, b.seed);
        BlockState pillar = s(Blocks.QUARTZ_PILLAR), qb = s(Blocks.QUARTZ_BRICKS), gold = s(Blocks.GOLD_BLOCK);
        BlockState hedge = leaves(Blocks.OAK_LEAVES), stairs = s(Blocks.QUARTZ_STAIRS);
        pad(b, Blocks.GRASS_BLOCK, 36);
        // ---- gardens: the approach, two fountains, hedges, flower beds, lamp posts
        for (int z = 0; z <= 9; z++) b.fill(c - 2, 0, z, c + 2, 0, z, Blocks.SMOOTH_STONE);
        b.fill(1, 0, 4, w - 2, 0, 4, Blocks.GRAVEL);
        for (int fx : new int[]{7, w - 8}) {
            disc(b, fx, 7, 0, 3.4, qb);
            ring(b, fx, 7, 1, 3, qb);
            disc(b, fx, 7, 1, 2.3, s(Blocks.WATER));
            b.fill(fx, 1, 7, fx, 2, 7, pillar);
            b.set(fx, 3, 7, s(Blocks.SEA_LANTERN));
            b.set(fx, 4, 7, Arch.slab(s(Blocks.QUARTZ_SLAB), SlabType.BOTTOM));
            for (int x = fx - 5; x <= fx + 5; x++) {
                if (Math.abs(x - fx) <= 1) continue;
                b.set(x, 1, 1, hedge);
                b.set(x, 1, 12, hedge);
            }
            for (int[] f : new int[][]{{fx - 4, 10}, {fx + 4, 10}, {fx - 4, 2}, {fx + 4, 2}}) {
                b.set(f[0], 1, f[1], Blocks.ROSE_BUSH);
                b.set(f[0], 2, f[1], Blocks.ROSE_BUSH.defaultBlockState().setValue(DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            }
        }
        for (int[] f : new int[][]{{c - 4, 2}, {c + 4, 2}, {c - 4, 11}, {c + 4, 11}}) b.set(f[0], 1, f[1], Blocks.RED_TULIP);
        for (int[] l : new int[][]{{c - 3, 1}, {c + 3, 1}, {1, 4}, {w - 2, 4}}) {
            b.fill(l[0], 1, l[1], l[0], 2, l[1], k(ArchBlocks.MARBLE_COLUMN));
            b.set(l[0], 3, l[1], Arch.lantern(k(ArchBlocks.BRASS_LANTERN), false));
        }
        // ---- the range (x 3..31, z 14..26) and the central pavilion (x 12..22, z 9..26)
        palaceRange(b, m, 3, 14, w - 4, d - 3, 2);
        Arch.hip(b, m, 3, 14, w - 4, d - 3, 11, 1);
        int p1 = c - 5, p2 = c + 5, pz1 = 9, pz2 = d - 3;
        b.clear(p1, 11, pz1, p2, 22, pz2);
        palaceRange(b, m, p1, pz1, p2, pz2, 3);
        b.fill(p1 + 1, 15, pz1 + 1, p2 - 1, 15, pz2 - 1, m.floor);
        int ridge = Arch.gable(b, m, p1, pz1, p2, pz2, 16, false, 1, m.upperWall);
        b.set(c, 18, pz1, gold);
        b.set(c, 17, pz1, Blocks.CHISELED_QUARTZ_BLOCK);
        // windows with blue shutters and planters
        for (int f = 0; f < 2; f++) {
            int y = f * 5 + 2;
            for (int x = 5; x <= w - 6; x += 3) {
                if (x >= p1 - 1 && x <= p2 + 1) continue;
                Arch.window(b, m, x, y, 14, Direction.NORTH, 2, true);
                Arch.window(b, m, x, y, d - 3, Direction.SOUTH, 2, true);
            }
            for (int z = 17; z <= d - 5; z += 4) {
                Arch.window(b, m, 3, y, z, Direction.WEST, 2, true);
                Arch.window(b, m, w - 4, y, z, Direction.EAST, 2, true);
            }
        }
        for (int f = 0; f < 3; f++) {
            int y = f * 5 + 2;
            for (int x = p1 + 2; x <= p2 - 2; x += 3) {
                if (f == 0 && Math.abs(x - c) <= 1) continue;
                if (f == 2 && Math.abs(x - c) <= 1) continue;
                Arch.window(b, m, x, y, pz1, Direction.NORTH, 2, f != 0);
            }
            for (int z = pz1 + 2; z <= 13; z += 3) {
                Arch.window(b, m, p1, y, z, Direction.WEST, 2, true);
                Arch.window(b, m, p2, y, z, Direction.EAST, 2, true);
            }
        }
        // ---- the portico: six quartz columns carrying a balustraded balcony at the top floor's level
        b.fill(p1 - 1, 0, 5, p2 + 1, 0, 8, qb);
        for (int x = p1; x <= p2; x += 2) {
            b.set(x, 1, 6, qb);
            b.fill(x, 2, 6, x, 9, 6, pillar);
        }
        b.fill(p1 - 1, 10, 5, p2 + 1, 10, 8, qb);
        for (int x = p1 - 2; x <= p2 + 2; x++) b.set(x, 10, 4, Arch.stair(stairs, Direction.SOUTH, Half.TOP));
        run(b, p1 - 1, 11, 5, p2 + 1, 5, k(ArchBlocks.PALE_MASONRY_WALL));
        run(b, p1 - 1, 11, 6, p1 - 1, 8, k(ArchBlocks.PALE_MASONRY_WALL));
        run(b, p2 + 1, 11, 6, p2 + 1, 8, k(ArchBlocks.PALE_MASONRY_WALL));
        b.set(p1 - 1, 11, 5, Arch.connect(k(ArchBlocks.PALE_MASONRY_WALL), false, true, true, false));
        b.set(p2 + 1, 11, 5, Arch.connect(k(ArchBlocks.PALE_MASONRY_WALL), false, false, true, true));
        b.bigDoor(c, 1, pz1, m.door.getBlock(), Direction.NORTH);
        b.bigDoor(c, 11, pz1, m.door.getBlock(), Direction.NORTH);
        hang(b, c, 9, 7, k(ArchBlocks.BRASS_LANTERN));
        b.marker("npc", c - 3, 1, 3, LandmarkKit.role("guard"));
        b.marker("npc", c + 3, 1, 3, LandmarkKit.role("guard"));
        // ---- the clock tower rising through the pavilion's ridge: clock faces, an open belfry, a teal spire
        int t1 = c - 2, t2 = c + 2, tz1 = 18, tz2 = 22;
        for (int y = 16; y <= 28; y++)
            for (int x = t1; x <= t2; x++)
                for (int z = tz1; z <= tz2; z++) {
                    if (!(x == t1 || x == t2 || z == tz1 || z == tz2)) continue;
                    boolean corner = (x == t1 || x == t2) && (z == tz1 || z == tz2);
                    b.set(x, y, z, corner ? m.frame : m.upperWall);
                }
        b.clear(t1 + 1, 16, tz1 + 1, t2 - 1, 28, tz2 - 1);
        b.fill(t1, 22, tz1, t2, 22, tz2, qb);
        b.clear(t1 + 1, 22, tz1 + 1, t2 - 1, 22, tz2 - 1);
        for (int y = 26; y <= 28; y++) { // open belfry
            b.clear(t1 + 1, y, tz1, t2 - 1, y, tz1);
            b.clear(t1 + 1, y, tz2, t2 - 1, y, tz2);
            b.clear(t1, y, tz1 + 1, t1, y, tz2 - 1);
            b.clear(t2, y, tz1 + 1, t2, y, tz2 - 1);
        }
        b.fill(t1, 29, tz1, t2, 29, tz2, qb);
        b.set(c, 28, 20, bellCeiling());
        for (int a = -1; a <= 1; a++)
            for (int y = 23; y <= 25; y++) {
                b.set(c + a, y, tz1, s(Blocks.SMOOTH_QUARTZ));
                b.set(c + a, y, tz2, s(Blocks.SMOOTH_QUARTZ));
                b.set(t1, y, 20 + a, s(Blocks.SMOOTH_QUARTZ));
                b.set(t2, y, 20 + a, s(Blocks.SMOOTH_QUARTZ));
            }
        for (int[] f : new int[][]{{c, tz1}, {c, tz2}, {t1, 20}, {t2, 20}}) {
            b.set(f[0], 24, f[1], gold);
            b.set(f[0], 25, f[1], s(Blocks.BLACK_TERRACOTTA));
        }
        Mats roofM = Mats.of(BuildStyle.PALACE, b.seed);
        int spire = Arch.hip(b, roofM, t1, tz1, t2, tz2, 30, 1);
        b.set(c, spire + 1, 20, gold);
        b.set(c, spire + 2, 20, Blocks.LIGHTNING_ROD);
        // ---- the throne hall in the pavilion
        b.fill(c - 1, 1, pz1 + 1, c + 1, 1, pz2 - 4, Blocks.BLUE_CARPET);
        b.fill(c - 2, 1, pz2 - 3, c + 2, 1, pz2 - 1, qb);
        for (int x = c - 2; x <= c + 2; x++) b.set(x, 1, pz2 - 4, Arch.stair(stairs, Direction.SOUTH, Half.BOTTOM));
        b.set(c, 2, pz2 - 1, Arch.stair(stairs, Direction.NORTH, Half.BOTTOM));
        b.set(c - 1, 2, pz2 - 1, gold);
        b.set(c + 1, 2, pz2 - 1, gold);
        b.set(c, 3, pz2 - 1, gold);
        for (int x : new int[]{c - 3, c + 3}) b.set(x, 3, pz2 - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        for (int[] p : new int[][]{{c - 2, 13}, {c + 2, 13}, {c - 2, 19}, {c + 2, 19}}) b.fill(p[0], 1, p[1], p[0], 4, p[1], k(ArchBlocks.MARBLE_COLUMN));
        hang(b, c, 4, 16, s(Blocks.LANTERN));
        hang(b, c, 4, 21, s(Blocks.LANTERN));
        b.marker("npc", c + 1, 2, pz2 - 2, LandmarkKit.role("noble"));
        // doors from the hall into the wings
        for (int f = 0; f < 2; f++) {
            b.bigDoor(p1, f * 5 + 1, 18, m.door.getBlock(), Direction.WEST);
            b.bigDoor(p2, f * 5 + 1, 18, m.door.getBlock(), Direction.EAST);
        }
        // the library (west wing)
        for (int z = 16; z <= 24; z += 2) b.fill(4, 1, z, 6, 3, z, Blocks.BOOKSHELF);
        b.set(9, 1, 20, facing(Blocks.LECTERN, Direction.WEST));
        b.set(9, 1, 23, k(ArchBlocks.PLANK_TABLE));
        chair(b, 10, 1, 23, Blocks.SPRUCE_STAIRS, Direction.EAST);
        hang(b, 8, 4, 19, s(Blocks.LANTERN));
        b.marker("npc", 8, 1, 21, LandmarkKit.role("scholar"));
        // the dining hall (east wing)
        longTableZ(b, 16, 24, 1, 27, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        b.set(30, 1, 16, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, Direction.WEST));
        b.set(30, 1, 17, Blocks.BARREL);
        hang(b, 27, 4, 20, s(Blocks.LANTERN));
        // stairs: hall -> state floor -> top floor
        flight(b, s(Blocks.SPRUCE_STAIRS), p1 + 2, 1, pz1 + 2, Direction.SOUTH, 5, 2, m.floor, 1);
        flight(b, s(Blocks.SPRUCE_STAIRS), p2 - 1, 6, pz1 + 2, Direction.SOUTH, 5, 2, m.floor, 6);
        run(b, p1 + 3, 6, pz1 + 3, p1 + 3, pz1 + 5, s(Blocks.IRON_BARS));
        run(b, p2 - 3, 11, pz1 + 3, p2 - 3, pz1 + 5, s(Blocks.IRON_BARS));
        // state rooms and the king's study above
        carpet(b, c - 2, 6, 15, c + 2, 22, Blocks.BLUE_CARPET);
        b.set(c, 6, 24, k(ArchBlocks.PLANK_TABLE));
        b.set(c - 1, 6, 24, Blocks.POTTED_RED_TULIP);
        for (int z = 16; z <= 24; z += 4) b.bed(5, 6, z, Blocks.BLUE_BED, Direction.WEST);
        b.chest(w - 5, 6, d - 4, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(w - 5, 6, 16, facing(Blocks.LECTERN, Direction.WEST));
        b.set(w - 7, 6, 20, k(ArchBlocks.PLANK_TABLE));
        chair(b, w - 7, 6, 21, Blocks.SPRUCE_STAIRS, Direction.SOUTH);
        hang(b, 27, 9, 20, s(Blocks.LANTERN));
        hang(b, 7, 9, 20, s(Blocks.LANTERN));
        b.set(c - 2, 11, 24, k(ArchBlocks.GOODS_SHELF));
        b.set(c + 2, 11, 24, k(ArchBlocks.GOODS_SHELF));
        b.set(c, 11, 22, Blocks.CARTOGRAPHY_TABLE);
        hang(b, c, 14, 18, s(Blocks.LANTERN));
    }

    // ------------------------------------------------------------------ God Valley

    /**
     * What is left of God Valley: a scorched blast crater glazed with slag where the island's heart was, ringed by the
     * shattered arcade of the great arena where the hunt was held (broken piers with carved capitals, fallen arches,
     * toppled marble drums, tiers of seating), and on its north side the roofless royal hall, its barrel vault fallen
     * in, still holding an altar, the last relics and an archaeologist's notes. Dead trees and bones lie about.
     */
    static void godValleyRuins(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d, c = w / 2;
        BlockState brick = s(Blocks.STONE_BRICKS), cracked = s(Blocks.CRACKED_STONE_BRICKS), mossy = s(Blocks.MOSSY_STONE_BRICKS);
        BlockState chis = s(Blocks.CHISELED_STONE_BRICKS), marble = k(ArchBlocks.MARBLE_COLUMN), stairs = s(Blocks.STONE_BRICK_STAIRS);
        b.foundation(0, 0, w - 1, d - 1, Blocks.COBBLED_DEEPSLATE, 12);
        b.clear(0, 1, 0, w - 1, 24, d - 1);
        // ---- the ground: crater bowl, scorched ring, dead earth beyond
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                double r = Math.hypot(x - c, z - c);
                int hh = (x * 73856093 ^ z * 19349663) >>> 3;
                if (r < 13) {
                    int depth = (int) Math.round(3 * (1 - r / 13));
                    b.clear(x, 1 - depth, z, x, 0, z);
                    Block bed = (hh % 17 == 0) ? Blocks.MAGMA_BLOCK : (hh % 5 == 0) ? Blocks.TINTED_GLASS : (hh % 3 == 0) ? Blocks.BASALT : Blocks.BLACKSTONE;
                    b.set(x, -depth, z, bed);
                } else {
                    Block g = r < 16 ? ((hh & 1) == 0 ? Blocks.COARSE_DIRT : Blocks.BLACKSTONE) : (hh % 4 == 0 ? Blocks.COARSE_DIRT : hh % 7 == 0 ? Blocks.GRAVEL : Blocks.PODZOL);
                    b.set(x, 0, z, g);
                }
            }
        // a jagged spur of slag glass where the blast struck
        for (int[] p : new int[][]{{c + 3, c + 2, 4}, {c + 4, c + 2, 2}, {c + 3, c + 3, 2}, {c - 4, c - 3, 3}}) {
            int depth = (int) Math.round(3 * (1 - Math.hypot(p[0] - c, p[1] - c) / 13));
            b.fill(p[0], 1 - depth, p[1], p[0], -depth + p[2], p[1], Blocks.TINTED_GLASS);
        }
        b.set(c + 3, -1 + 5, c + 2, Blocks.OBSIDIAN);
        // ---- the arena: sixteen piers on a ring (r = 19) with carved capitals, a few arches still standing
        int[] hts = new int[16];
        int[][] pos = new int[16][2];
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            pos[i][0] = c + (int) Math.round(Math.cos(a) * 19);
            pos[i][1] = c + (int) Math.round(Math.sin(a) * 19);
            hts[i] = 2 + b.rand(i * 7, 10);
            if (pos[i][1] < 10 && Math.abs(pos[i][0] - c) < 8) hts[i] = 0; // the royal hall stands here
        }
        for (int i = 0; i < 16; i++) {
            int px = pos[i][0], pz = pos[i][1], hgt = hts[i];
            if (hgt == 0) continue;
            for (int y = 1; y <= hgt; y++) {
                BlockState bl = y == hgt ? chis : (b.rand(i * 31 + y, 4) == 0 ? cracked : y <= 2 && b.rand(i * 13 + y, 3) == 0 ? mossy : brick);
                b.fill(px, y, pz, px + 1, y, pz + 1, bl);
            }
            int j = (i + 1) % 16;
            if (hgt >= 8 && hts[j] >= 8) { // an arch still spans to the next pier
                int qx = pos[j][0], qz = pos[j][1];
                int n = Math.max(Math.abs(qx - px), Math.abs(qz - pz));
                for (int k = 1; k < n + 1; k++) {
                    int ax = px + (qx - px) * k / n, az = pz + (qz - pz) * k / n;
                    int ay = 7 + (int) Math.round(1.5 * Math.sin(Math.PI * k / n));
                    b.fill(ax, ay, az, ax + 1, ay, az + 1, brick);
                    b.fill(ax, ay - 1, az, ax + 1, ay - 1, az + 1, Arch.slab(s(Blocks.STONE_BRICK_SLAB), SlabType.TOP));
                }
            }
            if (hgt < 6) {
                // the fallen drum of its column lies beside it
                double a = i * Math.PI * 2 / 16 + 1.6;
                int dx = (int) Math.round(Math.cos(a) * 1.5), dz = (int) Math.round(Math.sin(a) * 1.5);
                Direction.Axis ax = Math.abs(dx) > Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
                for (int k = 2; k <= 5; k++) {
                    int lx = px + (ax == Direction.Axis.X ? k * Integer.signum(dx == 0 ? 1 : dx) : 0);
                    int lz = pz + (ax == Direction.Axis.Z ? k * Integer.signum(dz == 0 ? 1 : dz) : 0);
                    if (lx >= 0 && lx < w && lz >= 0 && lz < d) b.set(lx, 1, lz, Arch.axis(marble, ax));
                }
            }
        }
        // broken tiers of seating facing the arena floor, and the stub of the outer wall
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                double r = Math.hypot(x - c, z - c);
                double a = Math.atan2(z - c, x - c);
                boolean sector = Math.sin(a * 3 + 0.7) > 0.1 && z > 10;
                if (sector && r >= 15.5 && r < 18) {
                    int tier = r < 16.3 ? 1 : r < 17.1 ? 2 : 3;
                    if (b.rand(x * 17 + z * 5, 6) == 0) continue;
                    for (int y = 1; y < tier; y++) b.set(x, y, z, brick);
                    Direction in = Math.abs(x - c) > Math.abs(z - c) ? (x > c ? Direction.WEST : Direction.EAST) : (z > c ? Direction.NORTH : Direction.SOUTH);
                    b.set(x, tier, z, Arch.stair(stairs, in.getOpposite(), Half.BOTTOM));
                }
                if (r >= 21 && r < 21.9 && Math.sin(a * 5) > -0.3) {
                    int h = 1 + b.rand(x * 3 + z, 2);
                    for (int y = 1; y <= h; y++) b.set(x, y, z, b.rand(x + z * 7 + y, 3) == 0 ? mossy : brick);
                }
            }
        // ---- the royal hall on the north side: roofless walls, a fallen barrel vault, the altar
        int hx1 = c - 6, hx2 = c + 6, hz1 = 1, hz2 = 9;
        b.fill(hx1, 0, hz1, hx2, 0, hz2, Blocks.POLISHED_ANDESITE);
        for (int x = hx1; x <= hx2; x++)
            for (int z = hz1; z <= hz2; z++) {
                if (!(x == hx1 || x == hx2 || z == hz1 || z == hz2)) continue;
                int top = 6 + b.rand(x * 11 + z * 3, 4) - (x == hx1 || x == hx2 ? 0 : b.rand(x, 3));
                for (int y = 1; y <= top; y++) b.set(x, y, z, b.rand(x * 7 + y * 5 + z, 5) == 0 ? cracked : y == 1 ? mossy : brick);
            }
        for (int[] p : new int[][]{{hx1, hz1}, {hx2, hz1}, {hx1, hz2}, {hx2, hz2}}) {
            b.fill(p[0], 1, p[1], p[0], 9, p[1], brick);
            b.set(p[0], 10, p[1], chis);
        }
        archX(b, c, 1, 1, hz1, hz1, 2);
        archZ(b, 5, 1, 2, hx1, hx1, 2);
        archZ(b, 5, 1, 2, hx2, hx2, 2);
        // what remains of the vault: ribs of stairs over the west half, the rest fallen into a heap
        for (int x = hx1 + 1; x <= c - 1; x++) {
            b.set(x, 7, hz1 + 1, Arch.stair(stairs, Direction.SOUTH, Half.BOTTOM));
            b.set(x, 7, hz2 - 1, Arch.stair(stairs, Direction.NORTH, Half.BOTTOM));
            b.fill(x, 8, hz1 + 2, x, 8, hz2 - 2, x % 3 == 0 ? Blocks.AIR.defaultBlockState() : brick);
            b.set(x, 8, hz1 + 2, Arch.stair(stairs, Direction.SOUTH, Half.BOTTOM));
            b.set(x, 8, hz2 - 2, Arch.stair(stairs, Direction.NORTH, Half.BOTTOM));
        }
        heap(b, 77, c + 4, 6, 1, 2, 2, Blocks.COBBLESTONE, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.GRAVEL);
        b.fill(c - 1, 1, hz2 - 1, c + 1, 1, hz2 - 1, chis);
        b.set(c, 2, hz2 - 1, Blocks.SKELETON_SKULL);
        b.set(c - 1, 2, hz2 - 1, candles(Blocks.CANDLE, 3));
        b.set(c + 1, 2, hz2 - 1, candles(Blocks.CANDLE, 2));
        b.chest(c - 4, 1, hz2 - 1, Direction.SOUTH, Buildings.LOOT_RUIN);
        b.set(c - 4, 1, 4, facing(Blocks.LECTERN, Direction.EAST));
        b.set(c - 5, 1, 7, Blocks.COBWEB);
        b.set(hx2 - 1, 5, hz1 + 1, Blocks.COBWEB);
        b.set(c - 3, 1, 3, Arch.decor(ArchBlocks.CARGO_SACKS, Direction.NORTH));
        b.marker("npc", c - 2, 1, 6, LandmarkKit.role("scholar")); // an archaeologist sifting the ruins
        // ---- dead trees, bones, skulls
        deadTree(b, 4, 1, d - 5, 6, 3, Blocks.DARK_OAK_LOG);
        deadTree(b, w - 5, 1, d - 8, 5, 9, Blocks.DARK_OAK_LOG);
        deadTree(b, 3, 1, 14, 4, 17, Blocks.DARK_OAK_LOG);
        for (int[] p : new int[][]{{c + 8, c + 6}, {c - 7, c - 9}, {c + 11, c - 3}, {c - 10, c + 8}}) b.set(p[0], 1, p[1], Arch.axis(s(Blocks.BONE_BLOCK), Direction.Axis.X));
        b.set(c - 9, 1, c + 3, Blocks.SKELETON_SKULL);
        b.set(c + 9, 1, c + 9, Blocks.SKELETON_SKULL);
        b.marker("npc", c, -2, c, LandmarkKit.role("ruin_guardian"));
        b.marker("npc", c + 10, 1, c - 10, LandmarkKit.role("ruin_guardian"));
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
        b.set(tx + 1, ty - 1, z, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.WEST).setValue(CocoaBlock.AGE, 2));
    }

    private static void parasol(Builder b, int x, int z, Block cloth) {
        b.fill(x, 1, z, x, 3, z, Blocks.BIRCH_FENCE);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) b.set(x + dx, 4, z + dz, Math.abs(dx) + Math.abs(dz) == 2 ? s(Blocks.WHITE_WOOL) : s(cloth));
        b.slab(x, 5, z, Blocks.SMOOTH_QUARTZ_SLAB, SlabType.BOTTOM);
    }

    /**
     * Kyuka's beach resort: a three-storey stucco hotel under a low terracotta roof, its pool front lined with white
     * balconies on slim columns (rooms open onto them), striped awnings over the lobby windows; before it a pool with
     * a white coping and underwater lights, loungers under parasols, palms and a thatched pool bar. Inside: the
     * lobby with its reception and a restaurant, a stair hall, and guest rooms on both upper floors.
     */
    static void kyukaResort(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d; // 33 x 27
        Mats m = Mats.of(BuildStyle.VENETIAN, b.seed);
        m.wall = k(ArchBlocks.SALMON_PLASTER);
        m.upperWall = m.wall;
        m.shutter = ArchBlocks.GREEN_SHUTTER.get();
        BlockState wt = s(Blocks.WHITE_TERRACOTTA), deck = s(Blocks.SMOOTH_SANDSTONE), white = k(ArchBlocks.WHITE_PLASTER);
        pad(b, Blocks.SMOOTH_SANDSTONE, 24);
        // ---- the pool terrace
        b.fill(1, 0, 0, w - 2, 0, 12, deck);
        for (int z = 8; z <= 12; z++) b.set(w / 2, 0, z, wt);
        b.fill(6, -3, 2, 25, -3, 6, Blocks.PRISMARINE_BRICKS);
        b.fill(7, -2, 2, 24, 0, 6, Blocks.WATER);
        for (int x = 6; x <= 25; x++)
            for (int z = 1; z <= 7; z++) if (x == 6 || x == 25 || z == 1 || z == 7) b.set(x, 0, z, wt);
        b.fill(6, -2, 2, 6, -1, 6, Blocks.PRISMARINE_BRICKS);
        b.fill(25, -2, 2, 25, -1, 6, Blocks.PRISMARINE_BRICKS);
        for (int x = 9; x <= 22; x += 4) b.set(x, -3, 4, Blocks.SEA_LANTERN);
        b.set(15, 1, 1, Arch.slab(s(Blocks.SMOOTH_QUARTZ_SLAB), SlabType.BOTTOM));
        b.set(15, 1, 2, Arch.slab(s(Blocks.SMOOTH_QUARTZ_SLAB), SlabType.BOTTOM));
        Block[] cloths = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.ORANGE_WOOL};
        for (int i = 0; i < 3; i++) {
            int px = 5 + i * 9;
            parasol(b, px, 10, cloths[i]);
            b.slab(px + 1, 1, 9, Blocks.BIRCH_SLAB, SlabType.BOTTOM);
            b.stairs(px + 2, 1, 9, Blocks.BIRCH_STAIRS, Direction.WEST, Half.BOTTOM);
        }
        palm(b, 3, 1, 3, 7, 0);
        palm(b, 3, 1, 9, 5, 0);
        for (int[] p : new int[][]{{1, 1}, {26, 11}}) b.set(p[0], 1, p[1], Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        // the thatched pool bar at the east end
        int bx = 27;
        for (int z = 3; z <= 8; z++) b.set(bx, 1, z, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.WEST));
        for (int x = bx; x <= w - 2; x++) b.set(x, 1, 3, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        for (int[] p : new int[][]{{bx, 2}, {w - 2, 2}, {bx, 9}, {w - 2, 9}}) b.fill(p[0], 1, p[1], p[0], 3, p[1], Blocks.BIRCH_FENCE);
        b.fill(bx - 1, 4, 1, w - 1, 4, 10, Arch.slab(k(ArchBlocks.THATCH_SLAB), SlabType.BOTTOM));
        b.fill(bx, 4, 2, w - 2, 4, 9, k(ArchBlocks.THATCH));
        b.fill(bx + 1, 5, 3, w - 3, 5, 8, Arch.slab(k(ArchBlocks.THATCH_SLAB), SlabType.BOTTOM));
        for (int z = 4; z <= 7; z += 3) b.set(bx - 1, 1, z, Arch.stair(s(Blocks.BIRCH_STAIRS), Direction.WEST, Half.BOTTOM));
        b.barrel(w - 3, 1, 8, Buildings.LOOT_TAVERN);
        b.set(w - 2, 1, 8, Blocks.BARREL);
        b.set(w - 2, 1, 6, k(ArchBlocks.GOODS_SHELF));
        hang(b, bx + 2, 3, 5, k(ArchBlocks.WHITE_PAPER_LANTERN));
        b.marker("npc", w - 3, 1, 5, LandmarkKit.role("bartender"));
        b.marker("npc", 11, 1, 8, LandmarkKit.role("patron"));
        b.marker("npc", 20, 1, 8, LandmarkKit.role("patron"));
        // ---- the hotel (x 2..30, z 14..25): floors at y = 0, 4, 8, roof above 12
        int x1 = 2, x2 = w - 3, z1 = 14, z2 = d - 2;
        for (int f = 0; f < 3; f++) {
            int y0 = f * 4;
            b.fill(x1 + 1, y0, z1 + 1, x2 - 1, y0, z2 - 1, f == 0 ? s(Blocks.SMOOTH_QUARTZ) : s(Blocks.STRIPPED_OAK_WOOD));
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) if (x == x1 || x == x2 || z == z1 || z == z2) b.fill(x, y0 + 1, z, x, y0 + 4, z, m.wall);
            b.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + 3, z2 - 1);
            for (int x = x1; x <= x2; x++) {
                b.set(x, y0 + 4, z1, wt);
                b.set(x, y0 + 4, z2, wt);
            }
            for (int z = z1; z <= z2; z++) {
                b.set(x1, y0 + 4, z, wt);
                b.set(x2, y0 + 4, z, wt);
            }
        }
        b.fill(x1 + 1, 12, z1 + 1, x2 - 1, 12, z2 - 1, s(Blocks.STRIPPED_OAK_WOOD));
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(c[0], 1, c[1], c[0], 12, c[1], white);
        m.roofKind = Arch.RoofKind.LOW_GABLE;
        Arch.gable(b, m, x1, z1, x2, z2, 13, true, 1, m.wall);
        // balconies along the pool front on slim white columns, iron railings
        for (int fy : new int[]{4, 8}) {
            b.fill(x1, fy, z1 - 2, x2, fy, z1 - 1, wt);
            for (int x = x1; x <= x2; x++) b.set(x, fy + 1, z1 - 2, Arch.connect(s(Blocks.IRON_BARS), false, x < x2, false, x > x1));
            for (int z = z1 - 2; z <= z1 - 1; z++) {
                b.set(x1, fy + 1, z, Arch.connect(s(Blocks.IRON_BARS), z > z1 - 2, false, z < z1 - 1, false));
                b.set(x2, fy + 1, z, Arch.connect(s(Blocks.IRON_BARS), z > z1 - 2, false, z < z1 - 1, false));
            }
            b.set(x1, fy + 1, z1 - 2, Arch.connect(s(Blocks.IRON_BARS), false, true, true, false));
            b.set(x2, fy + 1, z1 - 2, Arch.connect(s(Blocks.IRON_BARS), false, false, true, true));
        }
        for (int x = x1; x <= x2; x += 4) b.fill(x, 1, z1 - 2, x, 3, z1 - 2, white);
        b.fill(x1, 12, z1 - 2, x2, 12, z1 - 1, Arch.slab(k(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB), SlabType.BOTTOM));
        for (int x = x1; x <= x2; x += 4) {
            b.fill(x, 5, z1 - 2, x, 7, z1 - 2, white);
            b.fill(x, 9, z1 - 2, x, 11, z1 - 2, white);
        }
        // the lobby front: a big door, windows with striped awnings; rooms open onto the balconies
        int c = w / 2;
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 1, z1, Blocks.BIRCH_DOOR, Direction.NORTH);
        for (int x = x1 + 3; x <= x2 - 3; x += 4) {
            if (Math.abs(x - c) <= 2) continue;
            Arch.window(b, m, x, 1, z1, Direction.NORTH, 2, false);
            b.set(x, 3, z1 - 1, Arch.facing(k(ArchBlocks.RED_AWNING), Direction.NORTH));
        }
        for (int x = x1 + 2; x <= x2 - 2; x += 3) {
            for (int fy : new int[]{1, 5, 9}) Arch.window(b, m, x, fy + 1, z2, Direction.SOUTH, 2, true);
        }
        for (int z = z1 + 3; z <= z2 - 3; z += 4)
            for (int fy : new int[]{1, 5, 9}) {
                Arch.window(b, m, x1, fy + 1, z, Direction.WEST, 2, true);
                Arch.window(b, m, x2, fy + 1, z, Direction.EAST, 2, true);
            }
        // ground floor: reception, lounge, restaurant
        for (int x = c - 2; x <= c + 2; x++) b.set(x, 1, 20, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        b.chest(c + 1, 1, 22, Direction.NORTH, Buildings.LOOT_TAVERN);
        b.set(c - 1, 1, 22, k(ArchBlocks.GOODS_SHELF));
        b.set(c, 2, 20, Blocks.BELL.defaultBlockState());
        b.marker("npc", c, 1, 21, LandmarkKit.role("innkeeper"));
        for (int[] t : new int[][]{{21, 17}, {25, 17}, {21, 22}, {25, 22}}) Arch.tableSet(b, t[0], 1, t[1], s(Blocks.BIRCH_STAIRS));
        for (int z = 17; z <= 22; z += 5) {
            b.set(8, 1, z, k(ArchBlocks.PLANK_BENCH));
            b.set(10, 1, z, Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        }
        b.set(x2 - 1, 1, z2 - 1, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, Direction.NORTH));
        b.set(x2 - 2, 1, z2 - 1, Blocks.BARREL);
        for (int x = 6; x <= x2 - 4; x += 8) hang(b, x, 3, 19, k(ArchBlocks.WHITE_PAPER_LANTERN));
        // stair hall (west): ground -> first -> second floor
        b.fill(6, 1, z1 + 1, 6, 11, z2 - 1, m.wall);
        for (int fy : new int[]{1, 5, 9}) b.clear(6, fy, 19, 6, fy + 1, 20);
        flight(b, s(Blocks.SPRUCE_STAIRS), 4, 1, 16, Direction.SOUTH, 4, 2, s(Blocks.STRIPPED_OAK_WOOD), 1);
        flight(b, s(Blocks.SPRUCE_STAIRS), 4, 5, 21, Direction.SOUTH, 4, 2, s(Blocks.STRIPPED_OAK_WOOD), 5);
        for (int fy : new int[]{5, 9}) b.door(4, fy, z1, Blocks.BIRCH_DOOR, Direction.NORTH);
        // guest rooms on the upper floors, each opening onto the balcony
        for (int fy : new int[]{4, 8}) {
            for (int px : new int[]{13, 20}) {
                b.fill(px, fy + 1, z1 + 1, px, fy + 3, z2 - 1, m.wall);
                b.clear(px, fy + 1, 19, px, fy + 2, 19);
            }
            for (int rx : new int[]{9, 16, 25}) {
                b.door(rx, fy + 1, z1, Blocks.BIRCH_DOOR, Direction.NORTH);
                b.bed(rx + 1, fy + 1, z2 - 2, fy == 4 ? Blocks.LIGHT_BLUE_BED : Blocks.YELLOW_BED, Direction.SOUTH);
                b.set(rx - 1, fy + 1, z2 - 1, Blocks.BARREL);
                b.set(rx + 2, fy + 1, z1 + 1, k(ArchBlocks.PLANK_TABLE));
                hang(b, rx + 1, fy + 3, 20, k(ArchBlocks.WHITE_PAPER_LANTERN));
            }
            for (int wx : new int[]{11, 18, 23, 28}) Arch.window(b, m, wx, fy + 1, z1, Direction.NORTH, 2, true);
        }
    }

    // ------------------------------------------------------------------ Lulusia Kingdom

    /**
     * On the rim of the crater where the kingdom was: a fragment of the city wall with a broken arch and crenels, a
     * stair that now climbs into the air, scorched glassy ground, and a memorial obelisk of polished deepslate on a
     * stepped plinth, with candles, flowers and soul lanterns left by mourners. The crater itself is the island's
     * terrain (IslandShape.BLAST).
     */
    static void lulusiaMemorial(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d, c = w / 2;
        BlockState pbb = s(Blocks.POLISHED_BLACKSTONE_BRICKS), crk = s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), deep = s(Blocks.POLISHED_DEEPSLATE);
        b.foundation(0, 0, w - 1, d - 1, Blocks.BLACKSTONE, 14);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                int hh = (x * 92837111 ^ z * 689287499) >>> 4;
                b.set(x, 0, z, hh % 5 == 0 ? Blocks.TINTED_GLASS : hh % 3 == 0 ? Blocks.BASALT : Blocks.BLACKSTONE);
            }
        b.clear(0, 1, 0, w - 1, 16, d - 1);
        // the wall fragment: crenels on top, a broken arch, rubble at its foot
        for (int z = 2; z <= 10; z++) {
            int top = 9 - Math.max(0, z - 7) * 2 + (z == 4 ? 1 : 0);
            for (int y = 1; y <= top; y++) {
                b.set(1, y, z, b.rand(z * 7 + y, 4) == 0 ? crk : pbb);
                b.set(2, y, z, b.rand(z * 11 + y, 4) == 0 ? crk : pbb);
            }
            if (top >= 9 && (z & 1) == 0) b.set(1, top + 1, z, pbb);
        }
        archZ(b, 6, 1, 1, 1, 2, 2);
        b.set(3, 1, 9, crk);
        b.set(3, 1, 10, Arch.slab(s(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB), SlabType.BOTTOM));
        b.set(4, 1, 9, Arch.slab(s(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB), SlabType.BOTTOM));
        // the stair to nowhere
        for (int i = 0; i < 6; i++) {
            int x = 5 + i;
            b.fill(x, 1, d - 4, x, i, d - 2, Blocks.STONE_BRICKS);
            for (int z = d - 4; z <= d - 2; z++) {
                if (i == 5 && z == d - 2) continue;
                b.stairs(x, i + 1, z, Blocks.STONE_BRICK_STAIRS, Direction.EAST, Half.BOTTOM);
            }
        }
        b.set(11, 1, d - 3, Blocks.CRACKED_STONE_BRICKS);
        b.set(12, 1, d - 2, Arch.slab(s(Blocks.STONE_BRICK_SLAB), SlabType.BOTTOM));
        // the memorial: a stepped plinth, an obelisk, candles, flowers, lanterns
        b.fill(c - 2, 1, c - 2, c + 2, 1, c + 2, pbb);
        for (int x = c - 2; x <= c + 2; x++) {
            b.set(x, 1, c - 3, Arch.stair(s(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS), Direction.SOUTH, Half.BOTTOM));
            b.set(x, 1, c + 3, Arch.stair(s(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS), Direction.NORTH, Half.BOTTOM));
        }
        b.fill(c - 1, 2, c - 1, c + 1, 2, c + 1, deep);
        b.fill(c, 3, c, c, 10, c, deep);
        b.set(c, 11, c, Blocks.CHISELED_DEEPSLATE);
        b.set(c, 12, c, Blocks.LIGHTNING_ROD);
        for (int[] o : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.set(c + o[0], 3, c + o[1], Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        for (int[] o : new int[][]{{-2, 0}, {2, 0}, {0, 2}}) b.set(c + o[0], 2, c + o[1], Blocks.POTTED_WITHER_ROSE);
        b.set(c, 2, c - 2, Blocks.POTTED_WHITE_TULIP);
        b.set(c + 1, 3, c, Blocks.SOUL_LANTERN);
        for (int[] l : new int[][]{{c - 4, c - 4}, {c + 4, c - 4}, {c - 4, c + 4}, {c + 4, c + 4}}) {
            b.set(l[0], 1, l[1], Arch.connect(s(Blocks.POLISHED_BLACKSTONE_WALL), false, false, false, false));
            b.set(l[0], 2, l[1], Arch.lantern(s(Blocks.SOUL_LANTERN), false));
        }
        b.chest(w - 2, 1, 2, Direction.WEST, Buildings.LOOT_RUIN);
        b.set(w - 2, 1, 3, Arch.decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        b.marker("npc", c + 3, 1, c, LandmarkKit.role("keeper")); // a mourner tending the memorial
        b.marker("landmark", c, 1, c + 2, new CompoundTag());
    }

    // ------------------------------------------------------------------ Prodence Kingdom

    /**
     * The castle of a fighting kingdom: a stone curtain wall with four round towers under red-tiled cones and a gate
     * between twin turrets; a training yard with dummies, targets, weapon racks and a sand sparring ring; and a
     * three-storey keep with a red hip roof and a taller stair tower. Inside: the throne hall, the barracks and armoury,
     * and the royal chamber, joined by stairs.
     */
    static void prodenceCastle(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d, gx = w / 2;
        Mats m = Mats.of(BuildStyle.STONE_TOWN, b.seed);
        m.shutter = ArchBlocks.RED_SHUTTER.get();
        m.roof = k(ArchBlocks.TERRACOTTA_ROOF_TILES);
        m.roofStairs = k(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS);
        m.roofSlab = k(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        BlockState brick = s(Blocks.STONE_BRICKS), field = k(ArchBlocks.FIELDSTONE), wallB = s(Blocks.STONE_BRICK_WALL);
        BlockState tile = k(ArchBlocks.TERRACOTTA_ROOF_TILES), tileSlab = Arch.slab(k(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB), SlabType.BOTTOM);
        pad(b, Blocks.GRASS_BLOCK, 34);
        b.fill(2, 0, 2, w - 3, 0, d - 3, Blocks.GRAVEL);
        for (int z = 0; z <= 14; z++) b.fill(gx - 1, 0, z, gx + 1, 0, z, Blocks.STONE_BRICKS);
        // ---- curtain wall with a walk at y = 7 and battlements
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean ring = x <= 1 || x >= w - 2 || z <= 1 || z >= d - 2;
                if (!ring) continue;
                for (int y = 1; y <= 7; y++) b.set(x, y, z, y <= 2 ? field : brick);
                boolean outer = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                if (outer) {
                    b.set(x, 8, z, brick);
                    if (((x + z) & 1) == 0) b.set(x, 9, z, brick);
                }
            }
        flight(b, s(Blocks.STONE_BRICK_STAIRS), 2, 1, 21, Direction.NORTH, 7, 1, brick, 1);
        for (int[] c : new int[][]{{3, 3}, {w - 4, 3}, {3, d - 4}, {w - 4, d - 4}})
            roundTower(b, c[0], c[1], 3, 12, brick, tile, tileSlab, s(Blocks.GOLD_BLOCK));
        // the gate between twin turrets, red banners
        b.clear(gx - 2, 1, 0, gx + 2, 4, 1);
        archX(b, gx, 2, 1, 0, 1, 2);
        b.fill(gx - 2, 5, 0, gx + 2, 5, 0, Blocks.CHISELED_STONE_BRICKS);
        for (int tx : new int[]{gx - 4, gx + 4}) {
            b.fill(tx - 1, 1, 0, tx + 1, 11, 2, brick);
            b.fill(tx - 1, 12, 0, tx + 1, 12, 2, tileSlab);
            b.set(tx, 12, 1, tile);
            b.set(tx, 13, 1, tileSlab);
            b.set(tx, 9, 0, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
            b.set(tx, 6, 3, banner(Blocks.RED_WALL_BANNER, Direction.SOUTH));
        }
        b.set(gx - 3, 1, 3, Arch.lantern(k(ArchBlocks.BRASS_LANTERN), false));
        b.set(gx + 3, 1, 3, Arch.lantern(k(ArchBlocks.BRASS_LANTERN), false));
        b.marker("npc", gx, 1, 3, LandmarkKit.role("guard"));
        // ---- the training yard: dummies and targets, weapon racks, a sand sparring ring
        for (int i = 0; i < 4; i++) {
            int x = 6 + i * 2;
            b.set(x, 1, 5, Blocks.TARGET);
            b.set(x, 2, 5, Blocks.TARGET);
            if (i == 3) continue;
            int dx = 6 + i * 3;
            b.set(dx, 1, 8, Arch.connect(s(Blocks.OAK_FENCE), false, false, false, false));
            b.set(dx, 2, 8, Blocks.HAY_BLOCK);
            b.set(dx, 3, 8, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.NORTH));
        }
        for (int x = 4; x <= 12; x += 2) {
            b.set(x, 1, 12, Arch.connect(s(Blocks.SPRUCE_FENCE), false, false, false, false));
            b.set(x, 2, 12, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
        }
        b.set(13, 1, 12, k(ArchBlocks.PLANK_BENCH));
        int rx1 = w - 14, rx2 = w - 7, rz1 = 6, rz2 = 10;
        b.fill(rx1, 0, rz1, rx2, 0, rz2, Blocks.SAND);
        rail(b, rx1, 1, rz1, rx2, rz2, s(Blocks.OAK_FENCE));
        b.clear(rx1 + 3, 1, rz1, rx1 + 4, 1, rz1);
        b.set(rx1 + 2, 1, rz1 + 2, Blocks.HAY_BLOCK);
        for (int[] p : new int[][]{{rx1, rz1}, {rx2, rz1}, {rx1, rz2}, {rx2, rz2}}) {
            b.set(p[0], 2, p[1], Arch.connect(s(Blocks.OAK_FENCE), false, false, false, false));
            b.set(p[0], 3, p[1], Arch.lantern(s(Blocks.LANTERN), false));
        }
        b.set(rx2 + 1, 1, rz1 + 2, k(ArchBlocks.PLANK_BENCH));
        b.marker("npc", rx1 + 4, 1, rz1 + 2, LandmarkKit.role("master"));
        b.marker("npc", 7, 1, 10, LandmarkKit.role("guard"));
        // ---- the keep (x 9..21, z 15..26): floors at y = 0, 5, 10, red hip roof; a stair tower at its NE corner
        int k1 = 9, k2 = w - 10, kz1 = 15, kz2 = d - 5, kc = (k1 + k2) / 2;
        for (int f = 0; f < 3; f++) {
            int y0 = f * 5;
            b.fill(k1 + 1, y0, kz1 + 1, k2 - 1, y0, kz2 - 1, f == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.DARK_OAK_PLANKS));
            for (int x = k1; x <= k2; x++)
                for (int z = kz1; z <= kz2; z++) {
                    if (!(x == k1 || x == k2 || z == kz1 || z == kz2)) continue;
                    for (int y = y0 + 1; y <= y0 + 5; y++) b.set(x, y, z, f == 0 && y <= 2 ? field : brick);
                }
            b.clear(k1 + 1, y0 + 1, kz1 + 1, k2 - 1, y0 + 4, kz2 - 1);
            for (int x = k1 + 2; x <= k2 - 2; x += 3) {
                if (f == 0 && Math.abs(x - kc) <= 1) continue;
                Arch.window(b, m, x, y0 + 2, kz1, Direction.NORTH, 2, true);
                Arch.window(b, m, x, y0 + 2, kz2, Direction.SOUTH, 2, true);
            }
            for (int z = kz1 + 3; z <= kz2 - 3; z += 3) {
                Arch.window(b, m, k1, y0 + 2, z, Direction.WEST, 2, true);
                Arch.window(b, m, k2, y0 + 2, z, Direction.EAST, 2, true);
            }
        }
        for (int[] c : new int[][]{{k1, kz1}, {k2, kz1}, {k1, kz2}, {k2, kz2}}) b.fill(c[0], 1, c[1], c[0], 15, c[1], s(Blocks.POLISHED_ANDESITE));
        m.roofKind = Arch.RoofKind.HIP;
        Arch.hip(b, m, k1, kz1, k2, kz2, 16, 1);
        b.bigDoor(kc, 1, kz1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.fill(kc - 2, 5, kz1 - 1, kc + 2, 5, kz1 - 1, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
        b.set(kc - 2, 4, kz1 - 1, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.SOUTH, Half.TOP));
        b.set(kc + 2, 4, kz1 - 1, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.SOUTH, Half.TOP));
        b.set(kc - 1, 3, kz1 - 1, banner(Blocks.RED_WALL_BANNER, Direction.NORTH));
        b.set(kc + 1, 3, kz1 - 1, banner(Blocks.RED_WALL_BANNER, Direction.NORTH));
        // the stair tower at the keep's north-east corner: a ladder serves the floors, doorways open into the keep
        int tx1 = k2 - 2, tx2 = k2 + 2, tz1 = kz1 - 4, tz2 = kz1;
        for (int y = 1; y <= 21; y++)
            for (int x = tx1; x <= tx2; x++)
                for (int z = tz1; z <= tz2; z++) {
                    if (!(x == tx1 || x == tx2 || z == tz1 || z == tz2)) continue;
                    b.set(x, y, z, y <= 2 ? field : brick);
                }
        b.clear(tx1 + 1, 1, tz1 + 1, tx2 - 1, 21, tz2 - 1);
        for (int fy : new int[]{5, 10, 15}) b.fill(tx1 + 1, fy, tz1 + 1, tx2 - 1, fy, tz2 - 1, s(Blocks.DARK_OAK_PLANKS));
        ladder(b, tx2 - 1, 1, tz2 - 1, 19, Direction.WEST, null);
        for (int f = 0; f < 3; f++) b.clear(tx1 + 1, f * 5 + 1, kz1, tx1 + 1, f * 5 + 3, kz1);
        b.bigDoor(tx1 + 2, 1, tz1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        for (int y = 7; y <= 19; y += 4) {
            b.set(tx2, y, tz1 + 2, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.Z));
            b.set(tx1 + 2, y, tz1, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
        }
        Mats tm = Mats.of(BuildStyle.STONE_TOWN, b.seed);
        tm.roof = tile;
        tm.roofStairs = k(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS);
        tm.roofSlab = k(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        int cap = Arch.hip(b, tm, tx1, tz1, tx2, tz2, 22, 1);
        b.fill(k2, cap + 1, tz1 + 2, k2, cap + 4, tz1 + 2, Blocks.OAK_FENCE);
        b.set(k2, cap + 4, tz1 + 3, banner(Blocks.RED_WALL_BANNER, Direction.SOUTH));
        // ---- throne hall
        b.fill(kc - 1, 1, kz1 + 1, kc + 1, 1, kz2 - 3, Blocks.RED_CARPET);
        b.fill(kc - 2, 1, kz2 - 2, kc + 2, 1, kz2 - 1, Arch.slab(s(Blocks.POLISHED_ANDESITE_SLAB), SlabType.TOP));
        b.set(kc, 2, kz2 - 1, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.NORTH, Half.BOTTOM));
        b.set(kc - 1, 2, kz2 - 1, Blocks.GOLD_BLOCK);
        b.set(kc + 1, 2, kz2 - 1, Blocks.GOLD_BLOCK);
        b.set(kc, 3, kz2 - 1, banner(Blocks.RED_WALL_BANNER, Direction.NORTH));
        for (int z = kz1 + 3; z <= kz2 - 4; z += 4) {
            b.fill(k1 + 3, 1, z, k1 + 3, 4, z, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
            b.fill(k2 - 3, 1, z, k2 - 3, 4, z, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
        }
        hang(b, kc, 4, (kz1 + kz2) / 2, s(Blocks.LANTERN));
        b.marker("npc", kc + 2, 2, kz2 - 2, LandmarkKit.role("noble"));
        flight(b, s(Blocks.DARK_OAK_STAIRS), k1 + 1, 1, kz2 - 2, Direction.NORTH, 5, 2, s(Blocks.DARK_OAK_PLANKS), 1);
        flight(b, s(Blocks.DARK_OAK_STAIRS), k2 - 1, 6, kz1 + 2, Direction.SOUTH, 5, 2, s(Blocks.DARK_OAK_PLANKS), 6);
        // barracks and armoury
        for (int z : new int[]{kz2 - 4, kz2 - 1}) b.bed(k2 - 2, 6, z, Blocks.RED_BED, Direction.EAST);
        for (int x = kc - 2; x <= kc + 1; x++) {
            b.set(x, 6, kz2 - 1, Arch.connect(s(Blocks.DARK_OAK_FENCE), false, x < kc + 1, false, x > kc - 2));
            b.set(x, 7, kz2 - 1, Arch.connect(s(Blocks.IRON_BARS), false, x < kc + 1, false, x > kc - 2));
        }
        b.set(kc, 6, kz1 + 3, Blocks.ANVIL);
        b.set(kc + 1, 6, kz1 + 3, Blocks.GRINDSTONE);
        b.set(k1 + 1, 6, kz1 + 1, Blocks.BARREL);
        hang(b, kc, 9, (kz1 + kz2) / 2, s(Blocks.LANTERN));
        // the royal chamber
        b.bed(kc + 2, 11, kz2 - 2, Blocks.RED_BED, Direction.SOUTH);
        b.chest(kc + 4, 11, kz2 - 1, Direction.WEST, Buildings.LOOT_TREASURE);
        run(b, k2 - 3, 11, kz1 + 3, k2 - 3, kz1 + 5, s(Blocks.DARK_OAK_FENCE));
        b.set(kc - 2, 11, kz2 - 1, facing(Blocks.LECTERN, Direction.NORTH));
        b.set(kc, 11, kz1 + 3, k(ArchBlocks.PLANK_TABLE));
        carpet(b, kc - 1, 11, kz1 + 5, kc + 2, kz2 - 3, Blocks.RED_CARPET);
        hang(b, kc, 14, (kz1 + kz2) / 2, s(Blocks.LANTERN));
    }

    // ------------------------------------------------------------------ Karai Bari Island

    /**
     * The mercenary guild hall of Karai Bari and the circus tent beside it: a two-storey hall (fieldstone below,
     * timber framing above, a slate roof) whose front is one great bounty board under a lean-to canopy, with the
     * tavern on the ground floor and the guild office and bunk room upstairs; and a red-and-white big top with a
     * scalloped valance, flags and a sand ring inside, a cannon parked at its door. A yard of crates and barrels.
     */
    static void mercenaryGuildHall(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d;
        Mats m = Mats.of(BuildStyle.STONE_TOWN, b.seed);
        m.upperWall = k(ArchBlocks.TIMBER_FRAME_CROSS);
        m.shutter = ArchBlocks.RED_SHUTTER.get();
        BlockState field = k(ArchBlocks.FIELDSTONE), post = beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y);
        pad(b, Blocks.PACKED_MUD, 26);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) if (b.rand(x * 13 + z * 7, 6) == 0) b.set(x, 0, z, Blocks.COARSE_DIRT);
        // ---- the hall (x 1..17, z 8..22): floors at 0 and 5, roof from 11
        int x1 = 1, x2 = 17, z1 = 8, z2 = d - 3, hc = (x1 + x2) / 2;
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, Blocks.SPRUCE_PLANKS);
        b.fill(x1 + 1, 5, z1 + 1, x2 - 1, 5, z2 - 1, Blocks.SPRUCE_PLANKS);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (!(x == x1 || x == x2 || z == z1 || z == z2)) continue;
                b.fill(x, 1, z, x, 4, z, field);
                b.set(x, 5, z, x == x1 || x == x2 ? beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z) : beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
                b.fill(x, 6, z, x, 9, z, m.upperWall);
                b.set(x, 10, z, x == x1 || x == x2 ? beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z) : beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
            }
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(c[0], 1, c[1], c[0], 10, c[1], post);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 4, z2 - 1);
        b.clear(x1 + 1, 6, z1 + 1, x2 - 1, 9, z2 - 1);
        Arch.gable(b, m, x1, z1, x2, z2, 11, true, 1, m.upperWall);
        b.fill(x1 + 1, 10, z1 + 1, x2 - 1, 10, z2 - 1, Blocks.SPRUCE_PLANKS);
        Arch.chimney(b, m, x2 - 1, z1 + 7, 4, 17, true);
        for (int x = x1 + 3; x <= x2 - 3; x += 4) Arch.window(b, m, x, 7, z1, Direction.NORTH, 2, true);
        for (int x = x1 + 2; x <= x2 - 2; x += 3) {
            Arch.window(b, m, x, 2, z2, Direction.SOUTH, 2, true);
            Arch.window(b, m, x, 7, z2, Direction.SOUTH, 2, true);
        }
        for (int z = z1 + 3; z <= z2 - 3; z += 4) {
            Arch.window(b, m, x1, 2, z, Direction.WEST, 2, true);
            Arch.window(b, m, x1, 7, z, Direction.WEST, 2, true);
        }
        for (int x = hc - 1; x <= hc + 1; x++) b.bigDoor(x, 1, z1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        // the bounty board across the front under a lean-to canopy
        for (int x = x1 + 1; x < x2; x++)
            for (int y = 1; y <= 4; y++) {
                if (Math.abs(x - hc) <= 1 && y <= 3) continue;
                int hh = (x * 31 + y * 17) % 7;
                b.set(x, y, z1 - 1, hh == 0 || y == 1 ? s(Blocks.SPRUCE_PLANKS) : hh == 1 ? s(Blocks.LIGHT_GRAY_WOOL) : hh == 2 ? s(Blocks.BIRCH_PLANKS) : hh == 3 ? s(Blocks.YELLOW_WOOL) : s(Blocks.WHITE_WOOL));
            }
        Arch.leanTo(b, m, x1, z1 - 3, x2, z1 - 1, 6, Direction.NORTH);
        for (int x : new int[]{x1, hc - 3, hc + 3, x2}) b.fill(x, 1, z1 - 3, x, 4, z1 - 3, post);
        b.set(hc - 3, 5, z1 - 2, Arch.lantern(s(Blocks.LANTERN), true));
        b.set(hc + 3, 5, z1 - 2, Arch.lantern(s(Blocks.LANTERN), true));
        b.marker("npc", hc + 4, 1, z1 - 2, LandmarkKit.role("merchant"));
        // ground floor: the tavern
        for (int x = x1 + 2; x <= x1 + 8; x++) b.set(x, 1, z2 - 3, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        b.barrel(x1 + 2, 1, z2 - 1, Buildings.LOOT_TAVERN);
        b.barrel(x1 + 3, 1, z2 - 1, Buildings.LOOT_TAVERN);
        b.set(x1 + 4, 1, z2 - 1, k(ArchBlocks.GOODS_SHELF));
        b.set(x1 + 5, 1, z2 - 1, k(ArchBlocks.GOODS_SHELF));
        for (int[] t : new int[][]{{x1 + 4, z1 + 3}, {x1 + 9, z1 + 3}, {x1 + 9, z1 + 7}}) Arch.tableSet(b, t[0], 1, t[1], s(Blocks.SPRUCE_STAIRS));
        b.fill(x2 - 1, 1, z1 + 6, x2 - 1, 3, z1 + 9, field);
        b.set(x2 - 1, 1, z1 + 7, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        b.clear(x2 - 1, 2, z1 + 7, x2 - 1, 2, z1 + 8);
        b.set(x2 - 1, 1, z1 + 8, Blocks.AIR);
        hang(b, hc, 4, z1 + 5, s(Blocks.LANTERN));
        hang(b, x1 + 5, 4, z2 - 5, s(Blocks.LANTERN));
        b.marker("npc", x1 + 5, 1, z2 - 2, LandmarkKit.role("bartender"));
        // upstairs: the guild office and the bunk room
        flight(b, s(Blocks.SPRUCE_STAIRS), x2 - 4, 1, z2 - 2, Direction.NORTH, 5, 2, s(Blocks.SPRUCE_PLANKS), 1);
        run(b, x2 - 5, 6, z2 - 5, x2 - 5, z2 - 3, s(Blocks.SPRUCE_FENCE));
        b.set(x1 + 3, 6, z1 + 2, k(ArchBlocks.PLANK_TABLE));
        chair(b, x1 + 3, 6, z1 + 3, Blocks.SPRUCE_STAIRS, Direction.SOUTH);
        b.set(x1 + 2, 6, z1 + 1, facing(Blocks.LECTERN, Direction.EAST));
        b.set(x1 + 5, 6, z1 + 1, Blocks.CARTOGRAPHY_TABLE);
        b.chest(x1 + 1, 6, z1 + 4, Direction.EAST, Buildings.LOOT_TREASURE);
        for (int z = z1 + 7; z <= z2 - 2; z += 3) {
            b.bed(x1 + 2, 6, z, Blocks.BROWN_BED, Direction.WEST);
            b.bed(x1 + 6, 6, z, Blocks.BROWN_BED, Direction.EAST);
        }
        hang(b, hc, 9, z1 + 4, s(Blocks.LANTERN));
        hang(b, hc - 3, 9, z2 - 4, s(Blocks.LANTERN));
        // ---- the big top
        int tx = 24, tz = 14, r = 5;
        for (int y = 1; y <= 10; y++) {
            double rr = y <= 4 ? r : r * (1 - (y - 4) / 7.0);
            int ri = (int) Math.ceil(rr);
            for (int dx = -ri - 1; dx <= ri + 1; dx++)
                for (int dz = -ri - 1; dz <= ri + 1; dz++) {
                    double dd = Math.hypot(dx, dz);
                    if (dd > rr + 0.5 || (y <= 4 && dd < rr - 0.6) || (y > 4 && dd < rr - 1.2)) continue;
                    double ang = Math.atan2(dz, dx);
                    int stripe = (int) Math.floor((ang + Math.PI) / (Math.PI / 6));
                    b.set(tx + dx, y, tz + dz, (stripe & 1) == 0 ? s(Blocks.RED_WOOL) : s(Blocks.WHITE_WOOL));
                }
        }
        for (int dx = -r - 1; dx <= r + 1; dx++) // the scalloped valance round the eave
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double dd = Math.hypot(dx, dz);
                if (dd > r + 1.5 || dd <= r + 0.5) continue;
                double ang = Math.atan2(dz, dx);
                int stripe = (int) Math.floor((ang + Math.PI) / (Math.PI / 6));
                b.set(tx + dx, 5, tz + dz, (stripe & 1) == 0 ? s(Blocks.RED_WOOL) : s(Blocks.YELLOW_WOOL));
                if ((stripe & 1) == 0) b.set(tx + dx, 4, tz + dz, s(Blocks.RED_WOOL));
            }
        b.fill(tx, 1, tz, tx, 13, tz, Blocks.OAK_FENCE);
        b.set(tx, 14, tz, s(Blocks.YELLOW_WOOL));
        b.set(tx + 1, 13, tz, s(Blocks.RED_WOOL));
        b.set(tx + 2, 13, tz, s(Blocks.RED_WOOL));
        b.set(tx + 1, 12, tz, s(Blocks.RED_WOOL));
        disc(b, tx, tz, 0, r - 1.5, s(Blocks.SAND));
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int bx = tx + (int) Math.round(Math.cos(a) * (r - 1)), bz = tz + (int) Math.round(Math.sin(a) * (r - 1));
            if (bz < tz - 2) continue; // keep the entrance side clear
            b.set(bx, 1, bz, k(ArchBlocks.PLANK_BENCH));
        }
        b.clear(tx - 1, 1, tz - r - 1, tx + 1, 3, tz - r + 1);
        b.set(tx - 2, 1, tz - r - 2, Arch.decor(ArchBlocks.DECK_CANNON, Direction.NORTH));
        b.set(tx + 2, 1, tz - r - 2, Arch.decor(ArchBlocks.DECK_CANNON, Direction.NORTH));
        b.set(tx + 2, 1, tz - 2, Blocks.HAY_BLOCK);
        hang(b, tx, 8, tz + 1, s(Blocks.LANTERN));
        b.marker("npc", tx + 1, 1, tz + 2, LandmarkKit.role("guard"));
        // ---- the yard: crates, barrels, a sign
        Arch.cargo(b, 20, 1, 2, 0);
        Arch.cargo(b, 21, 1, 2, 2);
        Arch.cargo(b, 27, 1, 3, 1);
        b.set(28, 1, 2, k(ArchBlocks.CARGO_CRATE));
        b.set(28, 2, 2, k(ArchBlocks.CARGO_CRATE));
        b.set(26, 1, 23, Blocks.BARREL);
        b.set(27, 1, 23, Blocks.BARREL);
        b.marker("npc", 6, 1, 3, LandmarkKit.role("guard"));
    }

    // ------------------------------------------------------------------ Mogaro Kingdom

    /**
     * Mogaro's royal gate: a triumphal arch between two square towers of pale masonry with marble corner columns,
     * gold bands and teal pyramid roofs; a gold keystone and crown over the arch, a guard room in the attic above it
     * reached by a ladder in the west tower, a store in the east tower. Beyond lies a paved plaza with a crowned
     * obelisk, twin fountains, lamp posts, planters and benches.
     */
    static void mogaroRoyalGate(Builder b, Island isl, BuildStyle st) {
        int w = b.w, d = b.d, c = w / 2; // 27 x 21
        Mats m = Mats.of(BuildStyle.PALACE, b.seed);
        BlockState pale = k(ArchBlocks.PALE_MASONRY), marble = k(ArchBlocks.MARBLE_COLUMN), gold = s(Blocks.GOLD_BLOCK), qb = s(Blocks.QUARTZ_BRICKS);
        pad(b, Blocks.QUARTZ_BRICKS, 30);
        // ---- the plaza: a patterned pavement, the processional way
        for (int x = 1; x < w - 1; x++)
            for (int z = 1; z < d - 1; z++) b.set(x, 0, z, ((x + z) & 1) == 0 ? s(Blocks.POLISHED_DIORITE) : s(Blocks.SMOOTH_STONE));
        b.fill(c - 2, 0, 0, c + 2, 0, d - 1, Blocks.POLISHED_ANDESITE);
        ring(b, c, 14, 0, 4.5, s(Blocks.POLISHED_ANDESITE));
        // ---- the two towers (x 1..7 and 19..25, z 1..7)
        for (int tx1 : new int[]{1, w - 8}) {
            int tx2 = tx1 + 6;
            for (int x = tx1; x <= tx2; x++)
                for (int z = 1; z <= 7; z++) {
                    if (!(x == tx1 || x == tx2 || z == 1 || z == 7)) continue;
                    for (int y = 1; y <= 15; y++) b.set(x, y, z, y <= 1 ? qb : pale);
                }
            b.clear(tx1 + 1, 1, 2, tx2 - 1, 15, 6);
            for (int[] p : new int[][]{{tx1, 1}, {tx2, 1}, {tx1, 7}, {tx2, 7}}) b.fill(p[0], 1, p[1], p[0], 15, p[1], marble);
            for (int x = tx1; x <= tx2; x++) {
                b.set(x, 16, 1, gold);
                b.set(x, 16, 7, gold);
            }
            for (int z = 1; z <= 7; z++) {
                b.set(tx1, 16, z, gold);
                b.set(tx2, 16, z, gold);
            }
            b.fill(tx1 + 1, 16, 2, tx2 - 1, 16, 6, qb);
            b.fill(tx1 + 1, 10, 2, tx2 - 1, 10, 6, s(Blocks.POLISHED_DIORITE));
            int mx = tx1 + 3;
            for (int y : new int[]{4, 12}) {
                b.fill(mx, y, 1, mx, y + 1, 1, Arch.along(s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), Direction.Axis.X));
                b.fill(mx, y, 7, mx, y + 1, 7, Arch.along(s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), Direction.Axis.X));
                b.fill(tx1 == 1 ? tx1 : tx2, y, 4, tx1 == 1 ? tx1 : tx2, y + 1, 4, Arch.along(s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), Direction.Axis.Z));
            }
            int top = Arch.hip(b, m, tx1, 1, tx2, 7, 17, 1);
            b.set(mx, top + 1, 4, gold);
            b.set(mx, top + 2, 4, Blocks.LIGHTNING_ROD);
            b.bigDoor(mx, 1, 7, m.door.getBlock(), Direction.SOUTH);
            hang(b, mx, 9, 4, k(ArchBlocks.BRASS_LANTERN));
        }
        // ---- the arch between them, gold keystone and crown, the attic guard room
        b.fill(8, 1, 2, w - 9, 15, 6, pale);
        for (int x = 8; x <= w - 9; x++) {
            int dx = Math.abs(x - c);
            if (dx > 3) continue;
            int top = 5 + (int) Math.round(Math.sqrt(Math.max(0, 12.5 - dx * dx)));
            b.clear(x, 1, 2, x, top, 6);
        }
        b.fill(8, 0, 2, w - 9, 0, 6, Blocks.POLISHED_ANDESITE);
        b.set(c, 10, 2, gold);
        b.set(c, 10, 6, gold);
        for (int x = 8; x <= w - 9; x++) {
            b.set(x, 13, 1, Arch.stair(s(Blocks.QUARTZ_STAIRS), Direction.SOUTH, Half.TOP));
            b.set(x, 13, 7, Arch.stair(s(Blocks.QUARTZ_STAIRS), Direction.NORTH, Half.TOP));
        }
        for (int x : new int[]{c - 4, c + 4}) {
            b.fill(x, 1, 1, x, 12, 1, marble);
            b.fill(x, 1, 7, x, 12, 7, marble);
        }
        b.fill(c - 1, 16, 4, c + 1, 16, 4, gold);
        b.set(c, 17, 4, gold);
        b.set(c - 1, 17, 4, Blocks.LIGHT_BLUE_STAINED_GLASS);
        b.set(c + 1, 17, 4, Blocks.LIGHT_BLUE_STAINED_GLASS);
        b.set(c, 18, 4, gold);
        merlons(b, 8, 2, w - 9, 6, 16, qb);
        b.fill(c - 1, 16, 2, c + 1, 16, 6, qb);
        b.fill(c - 1, 16, 4, c + 1, 16, 4, gold);
        // attic guard room (y 11..13) over the arch, reached from the west tower's upper floor
        b.clear(9, 11, 3, w - 10, 13, 5);
        b.fill(9, 10, 3, w - 10, 10, 5, s(Blocks.POLISHED_DIORITE));
        b.clear(7, 11, 4, 8, 13, 4);
        b.clear(w - 9, 11, 4, w - 8, 13, 4);
        for (int x = 10; x <= w - 11; x += 3) {
            b.set(x, 12, 2, Arch.along(s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), Direction.Axis.X));
            b.set(x, 12, 6, Arch.along(s(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), Direction.Axis.X));
        }
        b.set(c - 2, 11, 5, k(ArchBlocks.PLANK_TABLE));
        b.set(c - 1, 11, 5, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.EAST, Half.BOTTOM));
        b.set(c + 3, 11, 3, Blocks.BARREL);
        hang(b, c, 13, 4, k(ArchBlocks.BRASS_LANTERN));
        ladder(b, 2, 1, 4, 9, Direction.EAST, null);
        b.set(2, 10, 4, trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.EAST, Half.TOP, false));
        // the east tower's store
        b.chest(w - 5, 1, 6, Direction.WEST, Buildings.LOOT_SHOP);
        b.set(w - 5, 1, 2, k(ArchBlocks.CARGO_CRATE));
        b.set(w - 4, 1, 2, k(ArchBlocks.CARGO_CRATE));
        b.set(w - 4, 2, 2, Arch.decor(ArchBlocks.CARGO_SACKS, Direction.NORTH));
        // banners
        b.set(c - 6, 9, 8, banner(Blocks.BLUE_WALL_BANNER, Direction.SOUTH));
        b.set(c + 6, 9, 8, banner(Blocks.BLUE_WALL_BANNER, Direction.SOUTH));
        b.marker("npc", c - 2, 1, 8, LandmarkKit.role("guard"));
        b.marker("npc", c + 2, 1, 8, LandmarkKit.role("guard"));
        // ---- the plaza: the crowned obelisk, twin fountains, lamp posts, planters, benches
        b.fill(c - 1, 1, 13, c + 1, 1, 15, qb);
        for (int x = c - 1; x <= c + 1; x++) {
            b.set(x, 1, 12, Arch.stair(s(Blocks.QUARTZ_STAIRS), Direction.SOUTH, Half.BOTTOM));
            b.set(x, 1, 16, Arch.stair(s(Blocks.QUARTZ_STAIRS), Direction.NORTH, Half.BOTTOM));
        }
        b.fill(c, 2, 14, c, 9, 14, Blocks.QUARTZ_PILLAR);
        b.set(c, 10, 14, gold);
        for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) b.set(c + o[0], 11, 14 + o[1], gold);
        b.set(c, 12, 14, gold);
        for (int fx : new int[]{5, w - 6}) {
            disc(b, fx, 14, 0, 2.6, qb);
            ring(b, fx, 14, 1, 2.4, qb);
            disc(b, fx, 14, 1, 1.5, s(Blocks.WATER));
            b.fill(fx, 1, 14, fx, 2, 14, Blocks.QUARTZ_PILLAR);
            b.set(fx, 3, 14, Blocks.SEA_LANTERN);
            b.set(fx, 4, 14, gold);
        }
        for (int z = 10; z < d - 1; z += 5)
            for (int x : new int[]{1, w - 2}) {
                b.fill(x, 1, z, x, 2, z, marble);
                b.set(x, 3, z, Arch.lantern(k(ArchBlocks.BRASS_LANTERN), false));
            }
        for (int x : new int[]{c - 4, c + 4}) {
            b.set(x, 1, 19, Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
            b.set(x, 1, 10, Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        }
        b.set(c - 6, 1, 18, k(ArchBlocks.PLANK_BENCH));
        b.set(c + 6, 1, 18, k(ArchBlocks.PLANK_BENCH));
        b.marker("npc", c, 1, 18, LandmarkKit.role("noble"));
    }

    private LandmarkBuildersExtra() {}
}
