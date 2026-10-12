package com.grandline.world.landmark;

import com.grandline.registry.ArchBlocks;
import com.grandline.registry.ModBlocks;
import com.grandline.world.Island;
import com.grandline.world.build.Arch;
import com.grandline.world.build.Arch.Mats;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.neoforge.registries.DeferredBlock;

import static com.grandline.world.landmark.LandmarkKit.*;
import static com.grandline.world.landmark.LandmarksB.*;

/** Set pieces of the first half of Paradise: Whisky Peak, Little Garden, Alabasta and Jaya. */
final class ParadiseLandmarks {

    // ============================================================== Whisky Peak: the welcome arch

    /** Saguaro-shaped rock spire: a ribbed green trunk with a rounded crown, two upturned arms and a rubble skirt. */
    private static void cactusRock(Builder b, int x, int z, int h, int armL, int armR) {
        BlockState body = s(Blocks.GREEN_TERRACOTTA), rib = s(Blocks.MOSS_BLOCK), rock = s(Blocks.TERRACOTTA);
        for (int y = 1; y <= h; y++) {
            b.fill(x - 1, y, z - 1, x + 1, y, z + 1, body);
            for (int[] q : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.set(x + q[0], y, z + q[1], rib);
            if (y < h) for (Direction dd : Direction.Plane.HORIZONTAL) b.set(x + dd.getStepX() * 2, y, z + dd.getStepZ() * 2, body);
        }
        b.fill(x - 1, h + 1, z, x + 1, h + 1, z, body);
        b.fill(x, h + 1, z - 1, x, h + 1, z + 1, body);
        b.set(x, h + 2, z, rib);
        // arms: out, then up, each ending in a rounded tip
        b.fill(x - 3, armL, z, x - 3, armL + 4, z, body);
        b.set(x - 3, armL + 5, z, rib);
        b.fill(x + 3, armR, z, x + 3, armR + 3, z, body);
        b.set(x + 3, armR + 4, z, rib);
        // a skirt of fallen rock and scrub round the foot
        for (Direction dd : Direction.Plane.HORIZONTAL) {
            Direction cw = dd.getClockWise();
            b.set(x + dd.getStepX() * 2 + cw.getStepX(), 1, z + dd.getStepZ() * 2 + cw.getStepZ(), rock);
            b.set(x + dd.getStepX() * 2 - cw.getStepX(), 1, z + dd.getStepZ() * 2 - cw.getStepZ(), s(Blocks.DEAD_BUSH));
        }
    }

    /**
     * The bounty hunters' town gate: a festive timber welcome arch under a little tiled roof, its sign board, bunting
     * and paper lanterns, between two saguaro rock spires; the party plaza with long tables, a band stage and kegs;
     * and the two-storey party hall (plaster below, braced timber above, tiled gable roof, a balcony on posts over
     * the plaza) whose cellar is the hunters' real base: wanted posters, the bounty board, an armoury and bunks.
     */
    static void welcomeArch(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 27 x 25
        LandmarkKit.site(b, Blocks.PACKED_MUD, Blocks.DIRT, 24);
        patchGround(b, 41, 0, Blocks.PACKED_MUD, Blocks.COARSE_DIRT, Blocks.DIRT);
        b.fill(12, 0, 0, 14, 0, 13, s(Blocks.MUD_BRICKS));
        BlockState beam = up(ArchBlocks.DARK_BEAM), field = s(ArchBlocks.FIELDSTONE), ochre = s(ArchBlocks.OCHRE_PLASTER);
        BlockState redL = lantern(ArchBlocks.RED_PAPER_LANTERN, true), whiteL = lantern(ArchBlocks.WHITE_PAPER_LANTERN, true);
        // ---- the cactus rocks flanking the road
        cactusRock(b, 3, 3, 12, 5, 7);
        cactusRock(b, 23, 3, 13, 7, 5);
        // ---- the arch: beam towers on fieldstone plinths, a crossbeam, the sign board under a tiled roof
        for (int px : new int[]{7, 18}) {
            b.fill(px - 1, 1, 0, px + 2, 2, 3, field);
            b.fill(px - 1, 3, 0, px + 2, 3, 3, Arch.slab(s(ArchBlocks.FIELDSTONE_SLAB), net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
            b.fill(px, 3, 1, px + 1, 10, 2, beam);
        }
        b.fill(5, 11, 1, 21, 11, 2, LandmarksB.beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
        b.fill(9, 8, 1, 17, 10, 1, ochre);
        for (int x = 9; x <= 17; x++) {
            b.set(x, 8, 1, s(Blocks.RED_TERRACOTTA));
            b.set(x, 10, 1, s(Blocks.RED_TERRACOTTA));
        }
        for (int x = 10; x <= 16; x++) if (x != 13) b.set(x, 9, 1, (x & 1) == 0 ? s(Blocks.WHITE_TERRACOTTA) : ochre);
        b.set(13, 9, 1, s(Blocks.RED_TERRACOTTA));
        b.fill(9, 8, 2, 17, 10, 2, s(ArchBlocks.WEATHERED_PLANKS));
        Mats rm = mats(BuildStyle.VILLAGE, b.seed);
        rm.roof = s(ArchBlocks.TERRACOTTA_ROOF_TILES);
        rm.roofStairs = s(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS);
        rm.roofSlab = s(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        rm.roofKind = Arch.RoofKind.GABLE;
        rm.eaveBrackets = false;
        Arch.gable(b, rm, 6, 1, 20, 2, 12, true, 1, null);
        // bunting and paper lanterns
        Block[] flags = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL};
        for (int x = 9; x <= 17; x++) if ((x & 1) == 1) b.set(x, 7, 1, s(flags[(x / 2) % flags.length]));
        for (int x : new int[]{6, 20}) {
            b.set(x, 10, 1, chainY());
            b.set(x, 9, 1, redL);
        }
        // ---- the party plaza
        for (int[] t : new int[][]{{3, 8}, {17, 8}}) {
            for (int x = t[0]; x <= t[0] + 6; x++) {
                b.set(x, 1, t[1], decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
                if ((x - t[0]) % 3 == 1) b.set(x, 2, t[1], LandmarkKit.candles(Blocks.CANDLE, 3));
                bench(b, x, 1, t[1] - 1, Direction.NORTH);
                bench(b, x, 1, t[1] + 1, Direction.SOUTH);
            }
        }
        b.barrel(1, 1, 9, Buildings.LOOT_TAVERN);
        b.barrel(1, 1, 10, Buildings.LOOT_TAVERN);
        b.set(1, 2, 9, s(Blocks.BARREL));
        b.barrel(25, 1, 9, Buildings.LOOT_TAVERN);
        b.set(25, 1, 10, s(ArchBlocks.CARGO_CRATE));
        b.set(25, 2, 10, decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        // lantern strings between posts across the plaza
        for (int[] p : new int[][]{{2, 5}, {11, 5}, {15, 5}, {24, 5}}) {
            b.fill(p[0], 1, p[1], p[0], 5, p[1], up(ArchBlocks.DARK_BEAM));
            b.set(p[0], 6, p[1], lantern(ArchBlocks.BRASS_LANTERN, false));
        }
        LandmarkKit.line(b, 3, 5, 5, 10, 5, Blocks.DARK_OAK_FENCE);
        LandmarkKit.line(b, 16, 5, 5, 23, 5, Blocks.DARK_OAK_FENCE);
        for (int x = 3; x <= 10; x++) b.set(x, 4, 5, (x & 1) == 0 ? redL : whiteL);
        for (int x = 16; x <= 23; x++) b.set(x, 4, 5, (x & 1) == 0 ? whiteL : redL);
        // the band stage
        b.fill(2, 1, 11, 7, 1, 12, bot(ArchBlocks.DOCK_PLANK_SLAB));
        b.set(3, 2, 12, s(Blocks.NOTE_BLOCK));
        b.set(5, 2, 12, s(Blocks.JUKEBOX));
        b.set(6, 2, 12, s(Blocks.NOTE_BLOCK));
        npc(b, "patron", 6, 1, 5);
        npc(b, "patron", 20, 1, 10);
        npc(b, "resident", 21, 1, 4);
        npc(b, "child", 10, 1, 11);

        // ---- the party hall (x 3..23, z 14..24): plaster ground floor, braced timber upper floor, tiled gable
        int x1 = 3, x2 = 23, z1 = 14, z2 = 23;
        Mats m = mats(BuildStyle.VILLAGE, b.seed);
        m.wall = s(ArchBlocks.CREAM_PLASTER);
        m.upperWall = s(ArchBlocks.TIMBER_FRAME_DIAGONAL);
        m.frame = up(ArchBlocks.DARK_BEAM);
        m.base = field;
        m.floor = s(Blocks.SPRUCE_PLANKS);
        m.roof = s(ArchBlocks.TERRACOTTA_ROOF_TILES);
        m.roofStairs = s(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS);
        m.roofSlab = s(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.GABLE;
        m.eaveBrackets = true;
        m.shutter = ArchBlocks.RED_SHUTTER.get();
        m.planters = true;
        // the hunters' cellar first (its roof is the hall's floor)
        LandmarkKit.shell(b, 4, -6, 15, 22, 0, 23, Blocks.STONE_BRICKS);
        b.fill(x1, 0, z1, x2, 0, z2, m.base);
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, m.floor);
        box(b, x1, 1, z1, x2, 4, z2, m.wall);
        box(b, x1, 6, z1, x2, 8, z2, m.upperWall);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 8, z2 - 1);
        ring(b, x1, z1, x2, z2, 1, m.base);
        beamRing(b, x1, z1, x2, z2, 5, ArchBlocks.DARK_BEAM);
        b.fill(x1 + 1, 5, z1 + 1, x2 - 1, 5, z2 - 1, m.floor);
        b.fill(x1 + 1, 9, z1 + 1, x2 - 1, 9, z2 - 1, s(Blocks.SPRUCE_PLANKS));
        for (int[] q : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(q[0], 1, q[1], q[0], 8, q[1], m.frame);
        for (int x = x1 + 5; x < x2; x += 5) {
            b.fill(x, 6, z1, x, 8, z1, m.frame);
            b.fill(x, 6, z2, x, 8, z2, m.frame);
        }
        int top = Arch.gable(b, m, x1, z1, x2, z2, 9, true, 1, m.upperWall);
        Arch.chimney(b, m, x1 + 2, z2, 1, top + 1, true);
        b.set(x1 + 2, 1, z2 - 1, campfire(false));
        b.clear(x1 + 2, 2, z2 - 1, x1 + 2, 3, z2 - 1);
        b.set(x1 + 2, 1, z2, s(Blocks.BRICKS));
        // doors, windows, wall lanterns
        for (int x = 12; x <= 14; x++) b.bigDoor(x, 1, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.set(12, 4, z1, m.frame);
        b.set(14, 4, z1, m.frame);
        for (int x : new int[]{5, 8, 18, 21}) {
            Arch.window(b, m, x, 2, z1, Direction.NORTH, 2, true);
            Arch.window(b, m, x, 6, z1, Direction.NORTH, 2, true);
            Arch.window(b, m, x, 2, z2, Direction.SOUTH, 2, true);
            Arch.window(b, m, x, 6, z2, Direction.SOUTH, 2, true);
        }
        for (int z : new int[]{17, 21}) {
            Arch.window(b, m, x1, 2, z, Direction.WEST, 2, true);
            Arch.window(b, m, x2, 2, z, Direction.EAST, 2, true);
            Arch.window(b, m, x1, 6, z, Direction.WEST, 2, true);
            Arch.window(b, m, x2, 6, z, Direction.EAST, 2, true);
        }
        b.set(16, 3, z1 - 1, LandmarkKit.banner(Blocks.RED_WALL_BANNER, Direction.NORTH));
        b.set(10, 3, z1 - 1, LandmarkKit.banner(Blocks.YELLOW_WALL_BANNER, Direction.NORTH));
        // the balcony on posts over the plaza, its door
        b.fill(x1 + 1, 5, 11, x2 - 1, 5, 13, s(ArchBlocks.DOCK_PLANKS));
        for (int x : new int[]{x1 + 1, 9, 17, x2 - 1}) b.fill(x, 1, 11, x, 4, 11, up(ArchBlocks.DARK_BEAM));
        b.fill(x1 + 1, 4, 11, x2 - 1, 4, 11, s(Blocks.AIR));
        for (int x : new int[]{x1 + 1, 9, 17, x2 - 1}) b.set(x, 4, 11, up(ArchBlocks.DARK_BEAM));
        for (int x = x1 + 2; x <= x2 - 2; x++) if (x != 9 && x != 17) b.set(x, 4, 11, Arch.stair(s(ArchBlocks.WEATHERED_PLANK_STAIRS), Direction.SOUTH, Half.TOP));
        LandmarkKit.line(b, x1 + 1, 6, 11, x2 - 1, 11, Blocks.SPRUCE_FENCE);
        LandmarkKit.line(b, x1 + 1, 6, 12, x1 + 1, 13, Blocks.SPRUCE_FENCE);
        LandmarkKit.line(b, x2 - 1, 6, 12, x2 - 1, 13, Blocks.SPRUCE_FENCE);
        b.door(13, 6, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        for (int x : new int[]{7, 13, 19}) b.hanging(x, 4, 12, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "resident", 8, 6, 12);

        // ---- ground floor: the bar along the back, tables, the band corner
        counterRow(b, 5, 11, 1, 19, Direction.NORTH);
        for (int x = 5; x <= 10; x++) {
            b.barrel(x, 1, 22, Buildings.LOOT_TAVERN);
            if ((x & 1) == 1) b.set(x, 2, 22, s(Blocks.BREWING_STAND));
        }
        shelfRow(b, 5, 10, 3, 22, Direction.NORTH, 1);
        for (int x = 6; x <= 10; x += 2) b.set(x, 1, 18, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.NORTH, Half.BOTTOM));
        npc(b, "bartender", 8, 1, 20);
        for (int[] t : new int[][]{{15, 17}, {18, 17}, {15, 21}, {18, 21}}) {
            tableSet(b, t[0], 1, t[1], s(Blocks.SPRUCE_STAIRS));
            b.set(t[0], 2, t[1], LandmarkKit.candles(Blocks.CANDLE, 2));
        }
        b.set(4, 1, 15, s(Blocks.NOTE_BLOCK));
        b.set(4, 1, 16, s(Blocks.NOTE_BLOCK));
        b.set(5, 1, 15, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.WEST, Half.BOTTOM));
        for (int[] l : new int[][]{{8, 17}, {13, 18}, {17, 19}}) b.hanging(l[0], 4, l[1], ArchBlocks.BRASS_LANTERN.get());
        b.marker("quest_board", x1 + 1, 2, 18, new net.minecraft.nbt.CompoundTag());
        npc(b, "patron", 16, 1, 19);
        npc(b, "patron", 10, 1, 17);
        // stairs to the guest rooms (east side)
        flightX(b, 21, 22, 1, 22, Direction.NORTH, 5, s(Blocks.SPRUCE_STAIRS), m.floor);
        LandmarkKit.line(b, 20, 6, 19, 20, 21, Blocks.SPRUCE_FENCE);
        // upstairs: a corridor along the front, three rooms behind
        b.fill(x1 + 1, 6, 17, 20, 8, 17, m.upperWall);
        for (int x : new int[]{9, 15, 20}) b.fill(x, 6, 18, x, 8, z2 - 1, m.upperWall);
        for (int x : new int[]{6, 12, 17}) b.door(x, 6, 17, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.bed(5, 6, 21, Blocks.RED_BED, Direction.SOUTH);
        b.chest(7, 6, 22, Direction.WEST, Buildings.LOOT_HOUSE);
        b.bed(11, 6, 21, Blocks.YELLOW_BED, Direction.SOUTH);
        b.bed(13, 6, 21, Blocks.YELLOW_BED, Direction.SOUTH);
        b.bed(17, 6, 21, Blocks.GREEN_BED, Direction.SOUTH);
        b.barrel(19, 6, 22, Buildings.LOOT_HOUSE);
        for (int x : new int[]{6, 12, 17}) b.hanging(x, 8, 20, ArchBlocks.BRASS_LANTERN.get());
        b.hanging(10, 8, 15, ArchBlocks.BRASS_LANTERN.get());
        LandmarkKit.carpet(b, x1 + 1, 6, 15, x2 - 1, 16, Blocks.RED_CARPET);

        // ---- the hidden base: hatch behind the bar, wanted posters, the bounty board, armoury, bunks
        LandmarkKit.hatch(b, 11, 0, 22, -5, Direction.NORTH, Blocks.SPRUCE_TRAPDOOR, null);
        b.fill(5, -6, 16, 21, -6, 22, s(Blocks.SPRUCE_PLANKS));
        for (int x = 6; x <= 20; x += 3) b.set(x, -3, 16, LandmarkKit.banner(Blocks.WHITE_WALL_BANNER, Direction.SOUTH));
        b.fill(12, -4, 16, 14, -4, 16, s(ArchBlocks.WEATHERED_PLANKS));
        b.marker("bounty_board", 13, -4, 16, new net.minecraft.nbt.CompoundTag());
        for (int x = 8; x <= 14; x++) {
            b.set(x, -5, 19, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
            if ((x & 1) == 0) {
                bench(b, x, -5, 18, Direction.NORTH);
                bench(b, x, -5, 20, Direction.SOUTH);
            }
        }
        b.set(11, -4, 19, LandmarkKit.candles(Blocks.CANDLE, 3));
        b.set(16, -5, 17, Blocks.CARTOGRAPHY_TABLE);
        b.set(5, -5, 17, s(Blocks.GRINDSTONE).setValue(GrindstoneBlock.FACE, AttachFace.FLOOR));
        b.set(5, -5, 18, s(Blocks.ANVIL));
        b.set(5, -5, 19, Blocks.SMITHING_TABLE);
        b.barrel(5, -5, 20, Buildings.LOOT_SMITH);
        b.set(5, -5, 21, decor(ArchBlocks.DECK_CANNON, Direction.EAST));
        for (int x = 15; x <= 19; x += 2) b.bed(x, -5, 21, Blocks.BLACK_BED, Direction.SOUTH);
        b.chest(21, -5, 20, Direction.WEST, Buildings.LOOT_TREASURE);
        for (int x : new int[]{8, 14, 19}) b.hanging(x, -1, 18, Blocks.LANTERN);
        npc(b, "bandit", 9, -5, 17);
        npc(b, "bandit", 17, -5, 20);
        mark(b, "treasure", 21, -5, 20);
        b.marker("legend", 13, 1, 3, tag("story", "welcome_arch"));
    }

    // ============================================================== Little Garden: the giants' duel ground

    /**
     * Giant-scale hut (11x11, door on local north): a fieldstone plinth, weathered-plank walls on spruce-log posts, a
     * steep thatch gable with brackets facing the door, giant lattice windows, a fur bed, a table with a cask, a fire
     * pit and the owner's great weapon leaning by the door (a sword or an axe).
     */
    private static void giantHut(Builder h, String loot, boolean sword) {
        Mats m = mats(BuildStyle.GIANT, h.seed);
        BlockState log = up(Blocks.SPRUCE_LOG);
        h.foundation(0, 0, 10, 10, ArchBlocks.FIELDSTONE.get(), 6);
        h.fill(0, 0, 0, 10, 0, 10, m.base);
        h.fill(1, 0, 1, 9, 0, 9, m.floor);
        box(h, 0, 1, 0, 10, 8, 10, m.wall);
        h.clear(1, 1, 1, 9, 8, 9);
        ring(h, 0, 0, 10, 10, 1, m.base);
        ring(h, 0, 0, 10, 10, 2, m.base);
        for (int[] q : new int[][]{{0, 0}, {10, 0}, {0, 10}, {10, 10}, {0, 5}, {10, 5}, {5, 10}}) h.fill(q[0], 1, q[1], q[0], 8, q[1], log);
        h.fill(0, 8, 0, 10, 8, 0, beam(Blocks.SPRUCE_LOG, Direction.Axis.X));
        h.fill(0, 8, 10, 10, 8, 10, beam(Blocks.SPRUCE_LOG, Direction.Axis.X));
        h.fill(0, 8, 1, 0, 8, 9, beam(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        h.fill(10, 8, 1, 10, 8, 9, beam(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        // the giant doorway: three wide, six tall, under a log lintel
        h.clear(4, 1, 0, 6, 6, 0);
        h.fill(3, 7, 0, 7, 7, 0, beam(Blocks.SPRUCE_LOG, Direction.Axis.X));
        h.fill(3, 1, 0, 3, 6, 0, log);
        h.fill(7, 1, 0, 7, 6, 0, log);
        // giant lattice windows on the sides and back
        for (int z : new int[]{2, 7}) {
            for (int x : new int[]{0, 10}) h.fill(x, 4, z, x, 6, z + 1, Arch.along(m.window, Direction.Axis.Z));
        }
        h.fill(4, 4, 10, 6, 6, 10, Arch.along(m.window, Direction.Axis.X));
        Arch.gable(h, m, 0, 0, 10, 10, 9, false, 1, m.wall);
        h.set(5, 11, 0, Arch.along(m.window, Direction.Axis.X));
        h.fill(1, 9, 5, 9, 9, 5, beam(Blocks.SPRUCE_LOG, Direction.Axis.X));
        h.hanging(3, 8, 5, ArchBlocks.BRASS_LANTERN.get());
        h.hanging(7, 8, 5, ArchBlocks.BRASS_LANTERN.get());
        // giant bed of furs
        h.fill(1, 1, 2, 3, 1, 9, s(Blocks.BROWN_WOOL));
        h.fill(1, 2, 2, 3, 2, 8, s(Blocks.BROWN_CARPET));
        h.fill(1, 2, 9, 3, 2, 9, s(Blocks.WHITE_WOOL));
        // giant table with a mug and a cask
        for (int[] l : new int[][]{{6, 6}, {9, 6}, {6, 9}, {9, 9}}) h.fill(l[0], 1, l[1], l[0], 3, l[1], log);
        h.fill(6, 4, 6, 9, 4, 9, s(Blocks.SPRUCE_PLANKS));
        h.set(7, 5, 7, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        h.barrel(8, 5, 8, Buildings.LOOT_TAVERN);
        // fire pit
        h.set(8, 0, 3, s(Blocks.COBBLESTONE));
        h.set(8, 1, 3, campfire(true));
        // the great weapon leaning by the door
        if (sword) {
            h.fill(9, 1, 1, 9, 6, 1, s(Blocks.IRON_BLOCK));
            h.fill(8, 7, 1, 9, 7, 1, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
            h.set(9, 8, 1, up(ArchBlocks.DARK_BEAM));
        } else {
            h.fill(9, 1, 1, 9, 7, 1, log);
            h.fill(8, 5, 1, 8, 7, 1, s(Blocks.IRON_BLOCK));
            h.set(9, 8, 1, s(Blocks.IRON_BLOCK));
        }
        h.chest(1, 1, 1, Direction.EAST, loot);
        h.barrel(2, 1, 1, Buildings.LOOT_HOUSE);
        npc(h, "giant_warrior", 5, 1, 3);
    }

    /** A jungle clearing where two giants have duelled for a century: giant huts, a beast skeleton and tally stones. */
    static void giantDuelGround(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 22; // 45 x 45
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 32);
        patchGround(b, 51, 0, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.PODZOL, Blocks.COARSE_DIRT);
        scatter(b, 52, 40, 0, 30, w - 1, d - 1, 1, Blocks.FERN.defaultBlockState());
        scatter(b, 53, 20, 0, 0, w - 1, 12, 1, Blocks.SHORT_GRASS.defaultBlockState());
        // the trampled arena with craters from a hundred years of blows
        disc(b, c, c, 0, 10.5, Blocks.COARSE_DIRT.defaultBlockState());
        for (int i = 0; i < 5; i++) {
            int cx = c - 6 + b.rand(60 + i, 13), cz = c - 6 + b.rand(70 + i, 13);
            disc(b, cx, cz, 0, 1.5 + b.rand(80 + i, 2), Blocks.ROOTED_DIRT.defaultBlockState());
            b.set(cx, -1, cz, Blocks.DIRT);
        }
        for (double a = 0; a < Math.PI * 2 - 0.01; a += Math.PI / 4) {
            if (Math.abs(Math.cos(a)) > 0.9) continue; // leave the hut sides open
            int sx = c + (int) Math.round(Math.cos(a) * 11), sz = c + (int) Math.round(Math.sin(a) * 11);
            int hgt = 3 + b.rand((int) (a * 100), 3);
            b.fill(sx, 1, sz, sx, hgt, sz, Blocks.MOSSY_COBBLESTONE);
            b.set(sx, hgt + 1, sz, Blocks.MOSSY_COBBLESTONE_WALL);
        }
        b.marker("arc_anchor", c, 1, c, tag("arc", "little_garden_duel"));
        // the two camps facing each other across the arena
        giantHut(sub(b, 1, 0, 17, 11, 11, Rotation.CLOCKWISE_90), Buildings.LOOT_TREASURE, true);
        giantHut(sub(b, 33, 0, 17, 11, 11, Rotation.COUNTERCLOCKWISE_90), Buildings.LOOT_HOUSE, false);
        // each camp's giant bonfire and drying rack facing the arena
        for (int fx : new int[]{14, 30}) {
            LandmarkKit.ring(b, fx, 22, 1, 1.5, s(ArchBlocks.FIELDSTONE));
            b.set(fx, 0, 22, s(Blocks.MAGMA_BLOCK));
            b.set(fx, 1, 22, campfire(true));
            for (int dz : new int[]{-3, 3}) b.fill(fx, 1, 22 + dz, fx, 4, 22 + dz, up(Blocks.SPRUCE_LOG));
            b.fill(fx, 5, 19, fx, 5, 25, beam(Blocks.SPRUCE_LOG, Direction.Axis.Z));
            b.set(fx, 4, 21, decor(ArchBlocks.FISHING_NET, Direction.EAST));
        }
        // a great beast's skeleton along the north edge
        for (int x = 8; x <= 32; x++) {
            int sy = 4 + (int) Math.round(3 * Math.sin(Math.PI * (x - 8) / 24.0));
            b.set(x, sy, 6, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
            if (x >= 13 && x <= 28 && (x & 1) == 0) {
                for (int k = 1; k <= 3; k++) {
                    int ry = sy - (k * k) / 2;
                    if (ry < 1) ry = 1;
                    b.set(x, ry, 6 - k, Blocks.BONE_BLOCK);
                    b.set(x, ry, 6 + k, Blocks.BONE_BLOCK);
                }
                b.fill(x, 1, 3, x, sy - 4 < 1 ? 1 : sy - 4, 3, Blocks.BONE_BLOCK);
                b.fill(x, 1, 9, x, sy - 4 < 1 ? 1 : sy - 4, 9, Blocks.BONE_BLOCK);
            }
        }
        for (int[] leg : new int[][]{{15, 4}, {15, 8}, {26, 4}, {26, 8}}) b.fill(leg[0], 1, leg[1], leg[0], 3, leg[1], Blocks.BONE_BLOCK);
        b.fill(33, 2, 4, 36, 5, 8, Blocks.BONE_BLOCK);
        b.clear(35, 4, 4, 35, 4, 4);
        b.clear(35, 4, 8, 35, 4, 8);
        b.fill(34, 1, 5, 37, 1, 7, Blocks.BONE_BLOCK);
        for (int z = 5; z <= 7; z++) b.set(37, 2, z, Blocks.CALCITE);
        b.set(36, 6, 4, Blocks.BONE_BLOCK);
        b.set(36, 7, 3, Blocks.BONE_BLOCK);
        // buried under the skull: a cache left by the first explorers
        b.fill(33, -3, 5, 35, -1, 7, Blocks.DIRT);
        b.chest(34, -2, 6, Direction.NORTH, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 34, -2, 6);
        // the tally wall: a century of drawn duels cut into a long fieldstone wall, moss creeping over it
        for (int x = 10; x <= 34; x++) {
            b.fill(x, 1, 38, x, 2, 38, s(ArchBlocks.FIELDSTONE));
            b.set(x, 3, 38, x % 5 == 4 ? s(Blocks.CRACKED_STONE_BRICKS) : s(Blocks.CHISELED_STONE_BRICKS));
            b.set(x, 1, 37, Arch.stair(s(ArchBlocks.FIELDSTONE_STAIRS), Direction.SOUTH, Half.BOTTOM));
            if (b.rand(x * 13, 3) == 0) b.set(x, 4, 38, s(Blocks.MOSS_CARPET));
        }
        for (int x : new int[]{10, 22, 34}) b.fill(x, 1, 38, x, 4, 38, s(Blocks.MOSSY_STONE_BRICKS));
        b.marker("legend", c, 1, 37, tag("story", "giant_duel"));
        // jungle around the edges
        for (int[] p : new int[][]{{5, 36}, {40, 40}, {16, 40}, {29, 40}, {39, 33}, {4, 10}}) palm(b, p[0], 1, p[1], 7 + b.rand(p[0] * 7 + p[1], 3), p[0] * 13 + p[1]);
        tree(b, 41, 1, 9, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, 8, 3, 91, null);
        npc(b, "scholar", 22, 1, 34);
    }

    // ============================================================== Little Garden: the volcano vent

    private static final Block[] LAVA_ROCK = {Blocks.BASALT, Blocks.BLACKSTONE, Blocks.TUFF, Blocks.SMOOTH_BASALT, Blocks.BLACKSTONE, Blocks.BASALT};

    /** The volcano whose eruptions start each duel: a smoking cone with a lava crater and a lava-tube cave inside. */
    static void volcanoVent(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = 13; // 27 x 27
        site(b, Blocks.COARSE_DIRT, Blocks.DIRT, 26);
        patchGround(b, 61, 0, Blocks.COARSE_DIRT, Blocks.BLACKSTONE, Blocks.TUFF, Blocks.GRAVEL);
        // the cone: concave flanks, five buttress ridges running down from the rim, an obsidian-crusted crater lip
        for (int y = 1; y <= 17; y++) {
            double t = y / 17.0, base = 12.2 - 8.6 * Math.pow(t, 0.75);
            for (int x = 0; x < w; x++)
                for (int z = 0; z < w; z++) {
                    double dx = x - c, dz = z - c, dd = Math.sqrt(dx * dx + dz * dz), ang = Math.atan2(dz, dx);
                    double ridge = 1.1 * Math.max(0, Math.cos(5 * ang + 0.7)) * (1 - t);
                    if (dd > base + ridge + b.rand(x * 31 + z * 17 + y, 3) * 0.35) continue;
                    b.set(x, y, z, LAVA_ROCK[b.rand(x * 7 + y * 131 + z * 13, LAVA_ROCK.length)]);
                }
        }
        // cooled lava streams down the south-east and west flanks: magma and obsidian in the surface
        for (int[] vein : new int[][]{{1, 1}, {-1, 0}}) {
            for (int y = 2; y <= 15; y++) {
                double t = y / 17.0, r = 12.2 - 8.6 * Math.pow(t, 0.75);
                int vx = c + (int) Math.round(vein[0] * r * (vein[1] == 1 ? 0.7 : 1.0)), vz = c + (int) Math.round(vein[1] * r * 0.7);
                if (vx < 1 || vx > w - 2 || vz < 1 || vz > w - 2) continue;
                b.set(vx, y, vz, (y & 1) == 0 ? s(Blocks.MAGMA_BLOCK) : s(Blocks.OBSIDIAN));
                b.set(vx, y - 1, vz, s(Blocks.MAGMA_BLOCK));
            }
        }
        // crater with a lava lake and smoke
        for (int y = 13; y <= 18; y++) disc(b, c, c, y, 4.4, Blocks.AIR.defaultBlockState());
        disc(b, c, c, 12, 4.4, Blocks.BASALT.defaultBlockState());
        disc(b, c, c, 11, 3.5, Blocks.MAGMA_BLOCK.defaultBlockState());
        disc(b, c, c, 12, 2.5, Blocks.LAVA.defaultBlockState());
        b.set(c + 4, 13, c, Blocks.CAMPFIRE);
        b.set(c - 4, 13, c, Blocks.CAMPFIRE);
        b.set(c, 13, c + 4, Blocks.CAMPFIRE);
        // the lava tube: tunnel from the north into a domed chamber
        carveEllipsoid(b, c, 2, c, 6.2, 5.2, 6.2, -1);
        b.fill(c - 6, 0, c - 6, c + 6, 0, c + 6, Blocks.SMOOTH_BASALT);
        b.clear(c - 1, 1, 0, c + 1, 3, c - 4);
        b.fill(c - 1, 0, 0, c + 1, 0, c - 4, Blocks.SMOOTH_BASALT);
        // a timbered portal and shoring frames along the tube, like a mine adit
        for (int z = 0; z <= c - 5; z += 3) {
            b.fill(c - 2, 1, z, c - 2, 3, z, up(ArchBlocks.WEATHERED_BEAM));
            b.fill(c + 2, 1, z, c + 2, 3, z, up(ArchBlocks.WEATHERED_BEAM));
            b.fill(c - 2, 4, z, c + 2, 4, z, beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        }
        b.fill(c - 3, 5, 0, c + 3, 5, 0, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
        b.set(c - 3, 4, 0, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
        b.set(c + 3, 4, 0, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
        b.set(c, 3, 1, lantern(ArchBlocks.BRASS_LANTERN, true));
        b.set(c, 4, 1, beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        b.set(c, 3, 6, lantern(ArchBlocks.BRASS_LANTERN, true));
        b.set(c - 1, 1, 1, Blocks.SKELETON_SKULL);
        b.set(c + 3, 1, 0, decor(ArchBlocks.CARGO_CRATE, Direction.NORTH));
        b.set(c - 3, 1, 0, s(Blocks.BARREL));
        // chamber dressing: glowing seams, a lava pocket behind glass, crystals
        for (int[] g : new int[][]{{c, c}, {c - 3, c - 2}, {c + 3, c + 2}, {c - 2, c + 3}}) b.set(g[0], 7, g[1], Blocks.SHROOMLIGHT);
        b.set(c, 6, c, lantern(Blocks.LANTERN, true));
        b.fill(c - 6, 2, c - 1, c - 6, 3, c + 1, Blocks.TINTED_GLASS);
        b.fill(c - 7, 2, c - 1, c - 7, 3, c + 1, Blocks.LAVA);
        b.fill(c - 8, 1, c - 2, c - 8, 4, c + 2, Blocks.BASALT);
        b.fill(c - 7, 1, c - 2, c - 7, 1, c + 2, Blocks.BASALT);
        b.fill(c - 7, 4, c - 2, c - 7, 4, c + 2, Blocks.BASALT);
        b.set(c - 7, 2, c - 2, Blocks.BASALT);
        b.set(c - 7, 3, c - 2, Blocks.BASALT);
        b.set(c - 7, 2, c + 2, Blocks.BASALT);
        b.set(c - 7, 3, c + 2, Blocks.BASALT);
        b.set(c + 2, 1, c - 5, Blocks.AMETHYST_CLUSTER);
        b.set(c - 4, 1, c + 4, Blocks.AMETHYST_CLUSTER);
        // a volcanologist's camp under a canvas awning on poles
        for (int[] p : new int[][]{{c + 1, c - 3}, {c + 1, c + 3}}) b.fill(p[0], 1, p[1], p[0], 3, p[1], up(ArchBlocks.WEATHERED_BEAM));
        for (int z = c - 3; z <= c + 3; z++) {
            b.set(c + 1, 4, z, decor(ArchBlocks.YELLOW_AWNING, Direction.WEST));
            b.set(c + 2, 4, z, decor(ArchBlocks.YELLOW_AWNING, Direction.WEST));
            b.set(c + 3, 5, z, s(ArchBlocks.WEATHERED_PLANKS));
        }
        b.bed(c + 2, 1, c + 2, Blocks.ORANGE_BED, Direction.SOUTH);
        b.set(c + 4, 1, c + 1, Blocks.CRAFTING_TABLE);
        b.set(c + 4, 1, c, Blocks.BREWING_STAND);
        b.set(c + 4, 1, c - 1, facing(Blocks.LECTERN, Direction.WEST));
        b.chest(c + 4, 1, c - 2, Direction.WEST, Buildings.LOOT_HOUSE);
        b.set(c + 3, 1, c - 2, decor(ArchBlocks.PLANK_TABLE, Direction.WEST));
        b.set(c + 3, 2, c - 2, LandmarkKit.candles(Blocks.CANDLE, 2));
        b.set(c + 2, 1, c - 3, decor(ArchBlocks.CARGO_SACKS, Direction.EAST));
        b.set(c - 2, 1, c + 4, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        npc(b, "scholar", c + 1, 1, c - 1);
        // the sealed grotto behind a cracked seam (east)
        b.set(c + 6, 1, c + 3, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.set(c + 6, 2, c + 3, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.clear(c + 5, 1, c + 3, c + 5, 2, c + 3);
        b.clear(c + 7, 1, c + 1, c + 8, 3, c + 4);
        b.fill(c + 7, 0, c + 1, c + 8, 0, c + 4, Blocks.OBSIDIAN);
        b.set(c + 8, 1, c + 1, Blocks.AMETHYST_BLOCK);
        b.set(c + 8, 2, c + 1, Blocks.AMETHYST_CLUSTER);
        b.set(c + 7, 3, c + 4, Blocks.CRYING_OBSIDIAN);
        b.chest(c + 8, 1, c + 3, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(c + 8, 3, c + 2, Blocks.OCHRE_FROGLIGHT);
        mark(b, "treasure", c + 8, 1, c + 3);
        b.marker("legend", c, 1, 2, tag("story", "volcano_vent"));
    }

    // ============================================================== Alabasta: Alubarna palace

    /**
     * The royal palace of the desert kingdom, read from the city square: a paved plaza with the clock tower (its
     * hidden gunners' room behind the clock face), a grand staircase between two domed gate pylons up to the palace
     * terrace, a courtyard of reflecting pools, and the palace itself: a cream-plaster block on a dressed-sandstone
     * plinth with column pilasters, four round corner towers under onion domes, a columned porch carrying the royal
     * balcony under a tall pishtaq, and the great onion dome over the double-height throne hall. Wings: barracks and
     * kitchen below, the princess's rooms and the library above. The royal tomb with the historical poneglyph lies
     * beneath the throne.
     */
    static void alubarnaPalace(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = 27; // 55 x 55
        final int P = 5; // palace terrace level (top of the platform)
        BlockState plaster = s(ArchBlocks.CREAM_PLASTER), ochre = s(ArchBlocks.OCHRE_PLASTER), white = s(ArchBlocks.WHITE_PLASTER);
        BlockState dressed = s(ArchBlocks.DRESSED_SANDSTONE), dStair = s(ArchBlocks.DRESSED_SANDSTONE_STAIRS), dSlab = bot(ArchBlocks.DRESSED_SANDSTONE_SLAB);
        BlockState col = up(ArchBlocks.SANDSTONE_COLUMN), smooth = s(Blocks.SMOOTH_SANDSTONE), cut = s(Blocks.CUT_SANDSTONE), chis = s(Blocks.CHISELED_SANDSTONE);
        BlockState gold = s(Blocks.GOLD_BLOCK), lattice = s(ArchBlocks.LATTICE_WINDOW), brass = s(ArchBlocks.BRASS_LANTERN), blue = s(ArchBlocks.SKY_PLASTER);
        Mats m = desert(b.seed, plaster);
        m.window = lattice;
        LandmarkKit.site(b, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, 48);

        // ---- the city square: paving grid, the palace axis, a fountain, palms, a little market
        for (int x = 3; x < w; x += 6) b.fill(x, 0, 0, x, 0, 12, cut);
        for (int z = 0; z <= 12; z += 6) b.fill(0, 0, z, w - 1, 0, z, cut);
        b.fill(c - 2, 0, 0, c + 2, 0, 12, dressed);
        LandmarkKit.disc(b, c, 6, 0, 3.6, dressed);
        LandmarkKit.disc(b, c, 6, 0, 2.6, s(Blocks.WATER));
        LandmarkKit.ring(b, c, 6, 1, 3, dSlab);
        b.set(c, 0, 6, chis);
        b.fill(c, 1, 6, c, 2, 6, col);
        b.set(c, 3, 6, chis);
        b.set(c, 4, 6, lantern(ArchBlocks.BRASS_LANTERN, false));
        for (int[] p : new int[][]{{4, 10}, {18, 4}, {36, 11}, {50, 10}}) {
            b.set(p[0], 0, p[1], Blocks.COARSE_DIRT);
            LandmarkKit.palm(b, p[0], 1, p[1], 7, p[0] * 31 + p[1]);
        }
        for (int x : new int[]{21, 33}) for (int z : new int[]{2, 9}) post(b, x, 1, z, s(ArchBlocks.DRESSED_SANDSTONE_WALL), 2, brass);
        // two awning stalls on the west side of the square
        for (int sx : new int[]{4, 11}) {
            for (int[] p : new int[][]{{sx, 3}, {sx + 4, 3}, {sx, 6}, {sx + 4, 6}}) b.fill(p[0], 1, p[1], p[0], 3, p[1], up(Blocks.STRIPPED_ACACIA_LOG));
            for (int z = 2; z <= 7; z++) awningsX(b, sx, sx + 4, 4, z, sx == 4 ? ArchBlocks.YELLOW_AWNING : ArchBlocks.RED_AWNING, z < 5 ? Direction.NORTH : Direction.SOUTH);
            counterRow(b, sx + 1, sx + 3, 1, 3, Direction.NORTH);
            b.set(sx + 1, 1, 6, decor(ArchBlocks.CARGO_SACKS, Direction.SOUTH));
            b.set(sx + 3, 1, 6, s(ArchBlocks.CARGO_CRATE));
            b.set(sx + 2, 2, 3, Blocks.DECORATED_POT);
        }
        npc(b, "merchant", 6, 1, 5);
        npc(b, "resident", 20, 1, 6);
        npc(b, "child", 33, 1, 11);

        // ---- the clock tower (x 43..49, z 2..8): plaster shaft, clock faces, belfry, onion dome; gunners' room inside
        int tx1 = 43, tx2 = 49, tz1 = 2, tz2 = 8, tcx = 46, tcz = 5;
        b.fill(tx1, 0, tz1, tx2, 0, tz2, dressed);
        box(b, tx1, 1, tz1, tx2, 25, tz2, plaster);
        b.clear(tx1 + 1, 1, tz1 + 1, tx2 - 1, 21, tz2 - 1);
        for (int[] q : new int[][]{{tx1, tz1}, {tx2, tz1}, {tx1, tz2}, {tx2, tz2}}) b.fill(q[0], 1, q[1], q[0], 25, q[1], dressed);
        for (int y : new int[]{1, 8, 15}) ring(b, tx1, tz1, tx2, tz2, y, dressed);
        plinth(b, tx1, tz1, tx2, tz2, 1, dStair);
        cornice(b, tx1, tz1, tx2, tz2, 15, dStair, dressed);
        b.fill(tx1 + 1, 16, tz1 + 1, tx2 - 1, 16, tz2 - 1, s(Blocks.ACACIA_PLANKS));
        b.fill(tx1 + 1, 22, tz1 + 1, tx2 - 1, 22, tz2 - 1, dressed);
        // clock faces on the north and west sides
        for (int k = 0; k < 2; k++)
            for (int u = -2; u <= 2; u++)
                for (int v = -2; v <= 2; v++) {
                    if (u * u + v * v > 5) continue;
                    BlockState face = (u == 0 && v >= 0) || (v == 0 && u >= 0 && u <= 1) ? s(Blocks.BLACK_CONCRETE) : s(Blocks.WHITE_CONCRETE);
                    if (u == 0 && v == 0) face = gold;
                    if (k == 0) b.set(tcx + u, 19 + v, tz1, face);
                    else b.set(tx1, 19 + v, tcz + u, face);
                }
        windowRowX(b, m, tcx, tcx, 1, 4, tz1, Direction.NORTH, 2, false);
        windowRowX(b, m, tcx, tcx, 1, 10, tz1, Direction.NORTH, 2, false);
        windowRowZ(b, m, tcz, tcz, 1, 10, tx2, Direction.EAST, 2, false);
        windowRowX(b, m, tcx, tcx, 1, 18, tz2, Direction.SOUTH, 2, false);
        // belfry: pointed openings on all four sides, a bell
        for (int y = 23; y <= 25; y++) {
            b.clear(tcx - 1, y, tz1, tcx + 1, y, tz2);
            b.clear(tx1, y, tcz - 1, tx2, y, tcz + 1);
        }
        b.fill(tcx - 1, 25, tz1, tcx + 1, 25, tz2, plaster);
        b.fill(tx1, 25, tcz - 1, tx2, 25, tcz + 1, plaster);
        for (int z : new int[]{tz1, tz2}) {
            b.set(tcx - 1, 25, z, Arch.stair(dStair, Direction.EAST, Half.TOP));
            b.set(tcx + 1, 25, z, Arch.stair(dStair, Direction.WEST, Half.TOP));
        }
        for (int x : new int[]{tx1, tx2}) {
            b.set(x, 25, tcz - 1, Arch.stair(dStair, Direction.SOUTH, Half.TOP));
            b.set(x, 25, tcz + 1, Arch.stair(dStair, Direction.NORTH, Half.TOP));
        }
        b.fill(tx1, 26, tz1, tx2, 26, tz2, dressed);
        cornice(b, tx1, tz1, tx2, tz2, 26, dStair, dressed);
        b.set(tcx, 25, tcz, dressed);
        b.set(tcx, 24, tcz, LandmarkKit.bellCeiling());
        Arch.dome(b, tcx, 27, tcz, 3, white, gold, true);
        b.bigDoor(tx1, 1, tcz, Blocks.ACACIA_DOOR, Direction.WEST);
        b.set(tx1, 4, tcz - 1, Arch.stair(dStair, Direction.SOUTH, Half.TOP));
        b.set(tx1, 4, tcz + 1, Arch.stair(dStair, Direction.NORTH, Half.TOP));
        b.ladderUp(tx2 - 1, 1, tcz, 16, Direction.WEST);
        b.hanging(tcx, 15, tcz, ArchBlocks.BRASS_LANTERN.get());
        // the gunners' nest behind the clock face
        b.chest(tx1 + 1, 17, tz1 + 1, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.set(tx1 + 2, 17, tz1 + 1, decor(ArchBlocks.DECK_CANNON, Direction.NORTH));
        b.set(tx1 + 1, 17, tz2 - 1, decor(ArchBlocks.CARGO_SACKS, Direction.EAST));
        b.set(tcx, 21, tcz, lantern(ArchBlocks.BRASS_LANTERN, true));
        npc(b, "guard", tcx, 17, tcz + 1);

        // ---- the grand staircase between the gate pylons, up to the terrace
        flightX(b, 22, 32, 1, 13, Direction.SOUTH, P, dStair, cut);
        for (int i = 0; i < P; i++) for (int x : new int[]{21, 33}) b.fill(x, 1, 13 + i, x, 2 + i, 13 + i, dressed);
        for (int x : new int[]{21, 33}) {
            b.fill(x, 1, 12, x, 2, 12, dressed);
            b.set(x, 3, 12, lantern(ArchBlocks.BRASS_LANTERN, false));
        }
        // terrace: dressed-sandstone retaining walls, pilasters, a balustrade
        b.fill(4, 1, 18, 50, P - 1, 54, cut);
        box(b, 4, 1, 18, 50, P - 1, 54, dressed);
        b.fill(4, P, 18, 50, P, 54, smooth);
        ring(b, 4, 18, 50, 54, P, dressed);
        for (int x = 6; x <= 48; x += 4) if (x < 21 || x > 33) b.fill(x, 1, 18, x, P - 1, 18, col);
        for (int z = 22; z <= 54; z += 4) {
            b.fill(4, 1, z, 4, P - 1, z, col);
            b.fill(50, 1, z, 50, P - 1, z, col);
        }
        for (int x = 8; x <= 46; x += 4) b.fill(x, 1, 54, x, P - 1, 54, col);
        LandmarkKit.line(b, 4, P + 1, 18, 18, 18, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        LandmarkKit.line(b, 36, P + 1, 18, 50, 18, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        LandmarkKit.line(b, 4, P + 1, 19, 4, 54, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        LandmarkKit.line(b, 50, P + 1, 19, 50, 54, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        LandmarkKit.line(b, 5, P + 1, 54, 49, 54, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        // the gate pylons with little onion domes and the kingdom's banners
        for (int px : new int[]{20, 34}) {
            b.fill(px - 1, P + 1, 18, px + 1, P + 8, 20, dressed);
            b.fill(px - 1, P + 1, 18, px - 1, P + 7, 18, col);
            b.fill(px + 1, P + 1, 18, px + 1, P + 7, 18, col);
            b.fill(px - 1, P + 1, 20, px - 1, P + 7, 20, col);
            b.fill(px + 1, P + 1, 20, px + 1, P + 7, 20, col);
            cornice(b, px - 1, 18, px + 1, 20, P + 8, dStair, dressed);
            b.set(px, P + 4, 17, LandmarkKit.banner(Blocks.LIGHT_BLUE_WALL_BANNER, Direction.NORTH));
            b.set(px, P + 6, 17, LandmarkKit.banner(Blocks.LIGHT_BLUE_WALL_BANNER, Direction.NORTH));
            Arch.dome(b, px, P + 9, 19, 2, white, gold, true);
        }

        // ---- courtyard: the processional way, two reflecting pools, palms in planters, the gate guards
        b.fill(25, P, 18, 29, P, 28, dressed);
        for (int[] pool : new int[][]{{8, 20}, {34, 46}}) {
            b.fill(pool[0], P, 21, pool[1], P, 24, dressed);
            b.fill(pool[0] + 1, P, 22, pool[1] - 1, P, 23, s(Blocks.WATER));
            ring(b, pool[0], 21, pool[1], 24, P + 1, dSlab);
        }
        for (int px : new int[]{6, 48}) {
            b.set(px, P, 23, s(Blocks.COARSE_DIRT));
            LandmarkKit.palm(b, px, P + 1, 23, 6, px * 7 + 3);
        }
        for (int px : new int[]{23, 31}) {
            b.set(px, P + 1, 21, decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
            b.set(px, P + 1, 23, Blocks.POTTED_CACTUS);
        }
        npc(b, "guard", 24, P + 1, 20);
        npc(b, "guard", 30, P + 1, 20);

        // ---- the palace block (x 10..44, z 29..50): ground floor P+1..P+5, upper floor P+7..P+10, roof deck P+11
        int x1 = 10, x2 = 44, z1 = 29, z2 = 50;
        box(b, x1, P + 1, z1, x2, P + 10, z2, plaster);
        b.clear(x1 + 1, P + 1, z1 + 1, x2 - 1, P + 10, z2 - 1);
        ring(b, x1, z1, x2, z2, P + 1, dressed);
        ring(b, x1, z1, x2, z2, P + 6, dressed);
        plinth(b, x1, z1, x2, z2, P + 1, dStair);
        cornice(b, x1, z1, x2, z2, P + 6, dStair, null);
        b.fill(x1 + 1, P, z1 + 1, x2 - 1, P, z2 - 1, smooth);
        for (int x = x1 + 1; x < x2; x++) for (int z = z1 + 1; z < z2; z++) if ((x + z) % 2 == 0) b.set(x, P, z, cut);
        b.fill(x1 + 1, P + 6, z1 + 1, x2 - 1, P + 6, z2 - 1, cut);
        // pilasters and windows on every elevation
        for (int x : new int[]{14, 20, 34, 40}) {
            b.fill(x, P + 2, z1, x, P + 10, z1, col);
            b.fill(x, P + 2, z2, x, P + 10, z2, col);
        }
        for (int x : new int[]{16, 18, 36, 38}) {
            Arch.window(b, m, x, P + 2, z1, Direction.NORTH, 3, false);
            Arch.window(b, m, x, P + 8, z1, Direction.NORTH, 2, false);
            Arch.window(b, m, x, P + 2, z2, Direction.SOUTH, 3, false);
            Arch.window(b, m, x, P + 8, z2, Direction.SOUTH, 2, false);
        }
        for (int z : new int[]{34, 38, 42, 46}) {
            b.fill(x1, P + 2, z, x1, P + 10, z, col);
            b.fill(x2, P + 2, z, x2, P + 10, z, col);
        }
        for (int z : new int[]{36, 40, 44}) {
            Arch.window(b, m, x1, P + 2, z, Direction.WEST, 3, false);
            Arch.window(b, m, x1, P + 8, z, Direction.WEST, 2, false);
            Arch.window(b, m, x2, P + 2, z, Direction.EAST, 3, false);
            Arch.window(b, m, x2, P + 8, z, Direction.EAST, 2, false);
        }
        // the throne hall's tall rear windows
        for (int x : new int[]{24, 27, 30}) Arch.window(b, m, x, P + 3, z2, Direction.SOUTH, 6, false);
        BlockState roofWall = m.wall;
        m.wall = dressed;
        Arch.flat(b, m, x1, z1, x2, z2, P + 11, true, true);
        m.wall = roofWall;

        // ---- the porch: six columns carrying the balcony deck
        for (int x : new int[]{21, 23, 25, 29, 31, 33}) {
            b.fill(x, P + 1, 25, x, P + 4, 25, col);
            b.set(x, P + 5, 25, chis);
        }
        b.fill(21, P + 6, 25, 33, P + 6, 28, dressed);
        for (int x = 21; x <= 33; x++) b.set(x, P + 5, 26, Arch.stair(dStair, Direction.SOUTH, Half.TOP));
        LandmarkKit.line(b, 21, P + 7, 25, 33, 25, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        LandmarkKit.line(b, 21, P + 7, 26, 21, 28, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        LandmarkKit.line(b, 33, P + 7, 26, 33, 28, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        for (int x : new int[]{23, 31}) b.hanging(x, P + 5, 27, ArchBlocks.BRASS_LANTERN.get());
        b.clear(25, P + 1, 28, 29, P + 1, 28);

        // ---- the pishtaq: a tall dressed-sandstone frontispiece over the entrance with the royal balcony
        b.fill(21, P + 1, z1, 33, P + 16, z1, dressed);
        b.fill(22, P + 7, z1, 32, P + 15, z1, ochre);
        b.fill(21, P + 17, z1, 33, P + 17, z1, dressed);
        for (int x = 21; x <= 33; x += 2) b.set(x, P + 18, z1, dressed);
        for (int x : new int[]{21, 33}) {
            b.fill(x, P + 1, z1 - 1, x, P + 18, z1 - 1, col);
            b.set(x, P + 19, z1 - 1, gold);
            b.set(x, P + 20, z1 - 1, s(Blocks.LIGHTNING_ROD));
        }
        // a pointed arch panel framing the balcony door and the lattice light above it
        b.fill(24, P + 7, z1, 30, P + 11, z1, blue);
        pointedArchX(b, 23, 31, P + 12, z1, dStair, gold, blue);
        Arch.window(b, m, 27, P + 10, z1, Direction.NORTH, 2, false);
        b.door(27, P + 7, z1, Blocks.ACACIA_DOOR, Direction.NORTH);
        b.set(25, P + 9, z1 - 1, LandmarkKit.banner(Blocks.LIGHT_BLUE_WALL_BANNER, Direction.NORTH));
        b.set(29, P + 9, z1 - 1, LandmarkKit.banner(Blocks.LIGHT_BLUE_WALL_BANNER, Direction.NORTH));
        // the grand doors under a pointed arch
        for (int x = 26; x <= 28; x++) b.bigDoor(x, P + 1, z1, Blocks.ACACIA_DOOR, Direction.NORTH);
        pointedArchX(b, 25, 29, P + 4, z1, dStair, chis, null);

        // ---- interior partitions
        b.fill(x1 + 1, P + 1, 34, x2 - 1, P + 10, 34, plaster);
        b.fill(19, P + 1, 35, 19, P + 10, z2 - 1, plaster);
        b.fill(35, P + 1, 35, 35, P + 10, z2 - 1, plaster);
        b.clear(25, P + 1, 34, 29, P + 4, 34);
        pointedArchX(b, 24, 30, P + 4, 34, dStair, chis, null);
        b.set(c, P + 6, 34, chis);
        b.clear(24, P + 7, 34, 30, P + 10, 34);
        for (int x = 24; x <= 30; x++) b.set(x, P + 7, 34, Arch.along(s(ArchBlocks.DRESSED_SANDSTONE_WALL), Direction.Axis.X));
        for (int x : new int[]{15, 39}) {
            b.bigDoor(x, P + 1, 34, Blocks.ACACIA_DOOR, Direction.NORTH);
            b.door(x, P + 7, 34, Blocks.ACACIA_DOOR, Direction.NORTH);
        }
        b.clear(19, P + 1, 41, 19, P + 3, 42);
        b.clear(35, P + 1, 41, 35, P + 3, 42);
        // the throne hall is double height: no upper floor over it
        b.clear(20, P + 6, 35, 34, P + 6, z2 - 1);

        // ---- corner towers with onion domes (front pair taller); their bases open into the rooms behind
        int[][] towers = {{x1, z1, P + 17}, {x2, z1, P + 17}, {x1, z2, P + 15}, {x2, z2, P + 15}};
        for (int[] t : towers) {
            boolean front = t[1] == z1;
            roundTower(b, t[0], t[1], P + 1, t[2], 3, plaster, dressed, new int[]{P + 1, P + 6, P + 11}, cut,
                    front && t[0] == x1 ? new int[]{P + 6, P + 11, P + 16} : new int[]{P + 6, P + 11}, lattice,
                    new int[]{P + 3, P + 8, P + 13, t[2]}, white, gold, 3, true);
            b.hanging(t[0], P + 5, t[1], ArchBlocks.BRASS_LANTERN.get());
        }
        // each tower opens into the rooms behind it through a doorway in its ring, on both floors; stores below, a
        // sitting room above
        for (int[] t : towers) {
            int dx = t[0] == x1 ? 2 : -2, dzz = t[1] == z1 ? 2 : -2;
            b.clear(t[0] + dx, P + 1, t[1] + dzz, t[0] + dx, P + 3, t[1] + dzz);
            b.clear(t[0] + dx, P + 7, t[1] + dzz, t[0] + dx, P + 9, t[1] + dzz);
            Arch.cargo(b, t[0] - dx / 2, P + 1, t[1] - dzz / 2, t[0] + t[1]);
            b.set(t[0] - dx / 2, P + 1, t[1], decor(ArchBlocks.CARGO_SACKS, Direction.NORTH));
            b.set(t[0] - dx / 2, P + 7, t[1] - dzz / 2, decor(ArchBlocks.PLANK_BENCH, Direction.NORTH));
            b.set(t[0], P + 7, t[1] - dzz / 2, decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        }
        // the north-west lookout: a ladder up the tower to the room under its dome
        LandmarkKit.ladder(b, x1, P + 1, z1 + 2, 16, Direction.NORTH, ArchBlocks.CREAM_PLASTER.get());
        npc(b, "guard", x1 - 1, P + 17, z1);

        // ---- the great dome over the throne hall: drum with lattice lights, a blue band, a white onion
        int dz = 42;
        LandmarkKit.disc(b, c, dz, P + 11, 6.4, s(Blocks.AIR));
        cyl(b, c, dz, P + 12, P + 14, 7, plaster, true);
        cyl(b, c, dz, P + 12, P + 12, 7, dressed, true);
        cyl(b, c, dz, P + 14, P + 14, 8, blue, true);
        for (int[] o : new int[][]{{7, 0}, {-7, 0}, {0, 7}, {0, -7}, {5, 5}, {-5, 5}, {5, -5}, {-5, -5}}) b.set(c + o[0], P + 13, dz + o[1], lattice);
        Arch.dome(b, c, P + 15, dz, 7, white, gold, true);
        b.set(c, P + 26, dz, white);
        for (int y = P + 13; y <= P + 25; y++) b.set(c, y, dz, chainY());
        b.set(c, P + 12, dz, lantern(ArchBlocks.BRASS_LANTERN, true));

        // ---- the throne hall
        LandmarkKit.carpet(b, c - 1, P + 1, 35, c + 1, 46, Blocks.RED_CARPET);
        for (int z : new int[]{37, 40, 43, 46}) {
            for (int x : new int[]{21, 33}) {
                b.fill(x, P + 1, z, x, P + 9, z, col);
                b.set(x, P + 10, z, chis);
            }
        }
        b.fill(23, P + 1, 47, 31, P + 1, 49, dressed);
        for (int x = 23; x <= 31; x++) b.set(x, P + 1, 46, Arch.stair(dStair, Direction.SOUTH, Half.BOTTOM));
        b.set(c, P + 2, 49, Arch.stair(s(Blocks.SMOOTH_SANDSTONE_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.set(c - 1, P + 2, 49, gold);
        b.set(c + 1, P + 2, 49, gold);
        b.fill(c, P + 3, 49, c, P + 4, 49, gold);
        b.set(c, P + 5, 49, s(Blocks.LIGHTNING_ROD));
        for (int x : new int[]{24, 30}) {
            b.set(x, P + 2, 49, Blocks.POTTED_CACTUS);
            b.set(x, P + 6, z2 - 1, LandmarkKit.banner(Blocks.LIGHT_BLUE_WALL_BANNER, Direction.NORTH));
        }
        npc(b, "noble", c, P + 2, 47);
        npc(b, "guard", 24, P + 1, 44);
        npc(b, "guard", 30, P + 1, 44);
        b.marker("arc_anchor", c, P + 1, 38, tag("arc", "desert_kingdom"));

        // ---- the front hall (foyer) and the gallery above it
        LandmarkKit.carpet(b, 12, P + 1, 31, 42, 32, Blocks.BLUE_CARPET);
        LandmarkKit.carpet(b, c - 1, P + 1, 30, c + 1, 33, Blocks.RED_CARPET);
        for (int x : new int[]{17, 23, 31, 37}) {
            b.fill(x, P + 1, 30, x, P + 4, 30, col);
            b.set(x, P + 5, 30, chis);
        }
        for (int x : new int[]{15, 22, 32, 39}) b.hanging(x, P + 5, 32, ArchBlocks.BRASS_LANTERN.get());
        for (int x : new int[]{12, 42}) b.set(x, P + 1, 33, Blocks.POTTED_CACTUS);
        LandmarkKit.carpet(b, 12, P + 7, 31, 42, 32, Blocks.BLUE_CARPET);
        for (int x : new int[]{15, 22, 32, 39}) b.hanging(x, P + 10, 31, ArchBlocks.BRASS_LANTERN.get());
        bench(b, 18, P + 7, 33, Direction.SOUTH);
        bench(b, 36, P + 7, 33, Direction.SOUTH);
        npc(b, "priest", 24, P + 7, 31);

        // ---- west wing: guards' barracks below (stair up along the hall wall), the princess's rooms above
        for (int z : new int[]{36, 39, 42}) {
            b.bed(12, P + 1, z, Blocks.LIGHT_BLUE_BED, Direction.WEST);
            b.set(11, P + 1, z + 1, s(Blocks.BARREL));
        }
        b.chest(11, P + 1, 46, Direction.EAST, Buildings.LOOT_MARINE);
        b.set(15, P + 1, 35, Blocks.SMITHING_TABLE);
        b.set(16, P + 1, 35, Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE, AttachFace.FLOOR));
        b.set(14, P + 1, 41, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        bench(b, 14, P + 1, 42, Direction.SOUTH);
        b.hanging(15, P + 5, 39, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "guard", 15, P + 1, 44);
        flightX(b, 17, 18, P + 1, 48, Direction.NORTH, 6, s(Blocks.SANDSTONE_STAIRS), cut);
        LandmarkKit.line(b, 16, P + 7, 44, 16, 47, Blocks.ACACIA_FENCE);
        LandmarkKit.line(b, 17, P + 7, 47, 18, 47, Blocks.ACACIA_FENCE);
        // princess's apartment
        LandmarkKit.carpet(b, 11, P + 7, 35, 18, 41, Blocks.ORANGE_CARPET);
        b.bed(12, P + 7, 36, Blocks.WHITE_BED, Direction.NORTH);
        b.bed(14, P + 7, 36, Blocks.WHITE_BED, Direction.NORTH);
        for (int[] p : new int[][]{{11, 37}, {15, 37}}) b.fill(p[0], P + 7, p[1], p[0], P + 8, p[1], s(Blocks.ACACIA_FENCE));
        b.fill(11, P + 9, 35, 15, P + 9, 37, s(Blocks.WHITE_WOOL));
        b.chest(11, P + 7, 41, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(18, P + 7, 35, LandmarkKit.facing(Blocks.LOOM, Direction.SOUTH));
        b.set(17, P + 7, 35, decor(ArchBlocks.FLOWER_PLANTER, Direction.SOUTH));
        b.set(11, P + 7, 44, Blocks.BOOKSHELF);
        b.set(11, P + 7, 45, decor(ArchBlocks.PLANK_TABLE, Direction.EAST));
        b.hanging(14, P + 10, 39, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "noble", 16, P + 7, 38);

        // ---- east wing: dining room and kitchen below, the library above
        LandmarkKit.longTableZ(b, 36, 40, P + 1, 40, Blocks.ACACIA_SLAB, Blocks.ACACIA_STAIRS, true);
        b.fill(38, P + 1, 42, 43, P + 5, 42, plaster);
        b.bigDoor(41, P + 1, 42, Blocks.ACACIA_DOOR, Direction.NORTH);
        LandmarkKit.kitchenX(b, 38, P + 1, 49, Direction.NORTH, Buildings.LOOT_TAVERN);
        b.barrel(43, P + 1, 49, Buildings.LOOT_TAVERN);
        counterRow(b, 43, 43, P + 1, 44, Direction.WEST);
        counterRow(b, 43, 43, P + 1, 45, Direction.WEST);
        b.set(43, P + 1, 46, decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        b.set(43, P + 1, 47, s(ArchBlocks.CARGO_CRATE));
        b.set(40, P + 1, 45, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.hanging(40, P + 5, 38, ArchBlocks.BRASS_LANTERN.get());
        b.hanging(40, P + 5, 46, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "cook", 40, P + 1, 47);
        npc(b, "waiter", 38, P + 1, 37);
        flightX(b, 36, 37, P + 1, 48, Direction.NORTH, 6, s(Blocks.SANDSTONE_STAIRS), cut);
        LandmarkKit.line(b, 38, P + 7, 44, 38, 47, Blocks.ACACIA_FENCE);
        LandmarkKit.line(b, 36, P + 7, 47, 37, 47, Blocks.ACACIA_FENCE);
        // library
        b.fill(43, P + 7, 35, 43, P + 9, 41, s(Blocks.BOOKSHELF));
        b.fill(36, P + 7, 35, 42, P + 9, 35, s(Blocks.BOOKSHELF));
        b.set(39, P + 7, 38, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(39, P + 8, 38, LandmarkKit.candles(Blocks.CANDLE, 3));
        b.set(38, P + 7, 38, Arch.stair(s(Blocks.ACACIA_STAIRS), Direction.WEST, Half.BOTTOM));
        b.set(41, P + 7, 44, Blocks.ENCHANTING_TABLE);
        b.set(42, P + 7, 47, LandmarkKit.facing(Blocks.LECTERN, Direction.WEST));
        LandmarkKit.carpet(b, 39, P + 7, 43, 42, 46, Blocks.LIGHT_BLUE_CARPET);
        b.hanging(39, P + 10, 40, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "scholar", 40, P + 7, 40);

        // ---- the royal tomb beneath the throne: a hatch beside the dais, a ladder down through the platform
        LandmarkKit.shell(b, 19, -10, 35, 35, -1, 50, Blocks.SANDSTONE);
        b.fill(20, -10, 36, 34, -10, 49, cut);
        LandmarkKit.hatch(b, 32, P + 1, 48, -9, Direction.NORTH, Blocks.ACACIA_TRAPDOOR, Blocks.SANDSTONE);
        for (int x = 20; x <= 34; x++)
            for (int y = -9; y <= -2; y++) if ((x + y) % 4 == 0) b.set(x, y, 35, Blocks.CHISELED_RED_SANDSTONE);
        for (int z = 39; z <= 47; z += 4) {
            b.fill(22, -9, z, 22, -3, z, col);
            b.fill(32, -9, z, 32, -3, z, col);
            b.set(22, -2, z, chis);
            b.set(32, -2, z, chis);
        }
        for (int z = 40; z <= 46; z += 3) {
            b.fill(24, -9, z, 25, -9, z, chis);
            b.fill(24, -8, z, 25, -8, z, s(Blocks.SMOOTH_SANDSTONE_SLAB));
            b.fill(29, -9, z, 30, -9, z, chis);
            b.fill(29, -8, z, 30, -8, z, s(Blocks.SMOOTH_SANDSTONE_SLAB));
        }
        b.set(c, -9, 37, ModBlocks.PONEGLYPH.get());
        b.set(c, -8, 37, ModBlocks.PONEGLYPH.get());
        b.marker("poneglyph", c, -9, 38, tag("kind", "historical"));
        b.chest(21, -9, 38, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(33, -9, 38, Direction.WEST, Buildings.LOOT_TREASURE);
        for (int z = 39; z <= 47; z += 4) b.hanging(c, -2, z, Blocks.SOUL_LANTERN);
        mark(b, "treasure", 21, -9, 38);
    }

    // ============================================================== Alabasta: Nanohana port

    /** A market stall: four posts under a ridged striped awning, a counter at the front, goods behind. */
    private static void awningStall(Builder b, int x, int z, DeferredBlock<Block> aw, String loot) {
        for (int[] c : new int[][]{{x, z}, {x + 4, z}, {x, z + 3}, {x + 4, z + 3}}) b.fill(c[0], 2, c[1], c[0], 4, c[1], up(Blocks.STRIPPED_ACACIA_LOG));
        for (int zz = z - 1; zz <= z + 4; zz++) awningsX(b, x, x + 4, 5, zz, aw, zz <= z + 1 ? Direction.NORTH : Direction.SOUTH);
        for (int i = 1; i <= 3; i++) b.set(x + i, 2, z, decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        b.barrel(x + 1, 2, z + 3, loot);
        b.set(x + 2, 2, z + 3, decor(ArchBlocks.CARGO_SACKS, Direction.SOUTH));
        b.set(x + 3, 2, z + 3, s(Blocks.DECORATED_POT));
        b.set(x + 2, 3, z, s(Blocks.FLOWER_POT));
        npc(b, "merchant", x + 2, 2, z + 2);
    }

    /**
     * The desert port: a harbour basin with two plank jetties on beam pilings, a harbour-stone quay wall with
     * bollards and ladders at sea level + 1, a market quay of striped-awning stalls, a domed minaret watch-tower, and
     * a street front of flat-roofed plaster buildings behind: the spice shop, the domed tavern (rooms upstairs) and a
     * tall warehouse with an arched loading bay and a hoist.
     */
    static void nanohanaPort(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 41 x 35, y=0 is sea level, quay surface at y=1
        int q = 14; // quay edge
        BlockState harbour = s(ArchBlocks.HARBOUR_STONE), dock = s(ArchBlocks.DOCK_PLANKS), pile = up(ArchBlocks.WEATHERED_BEAM);
        BlockState dressed = s(ArchBlocks.DRESSED_SANDSTONE), dStair = s(ArchBlocks.DRESSED_SANDSTONE_STAIRS), lattice = s(ArchBlocks.LATTICE_WINDOW);
        BlockState white = s(ArchBlocks.WHITE_PLASTER), gold = s(Blocks.GOLD_BLOCK), brass = s(ArchBlocks.BRASS_LANTERN);
        b.clear(0, 1, 0, w - 1, 34, d - 1);
        b.fill(0, -6, 0, w - 1, -6, q - 1, s(Blocks.SAND));
        b.fill(0, -5, 0, w - 1, 0, q - 1, s(Blocks.WATER));
        b.fill(0, -6, q, w - 1, 0, d - 1, s(Blocks.SANDSTONE));
        b.fill(0, 1, q, w - 1, 1, d - 1, s(Blocks.SMOOTH_SANDSTONE));
        for (int x = 0; x < w; x++) for (int z = q + 3; z < d; z++) if ((x * 3 + z * 5) % 11 == 0) b.set(x, 1, z, s(Blocks.CUT_SANDSTONE));
        // ---- the quay wall: harbour stone from the sea bed up, a coping course, bollards and ladders
        b.fill(0, -6, q, w - 1, 1, q + 1, harbour);
        for (int x = 2; x < w; x += 6) b.set(x, 2, q, decor(ArchBlocks.BOLLARD, Direction.NORTH));
        for (int x : new int[]{14, 26}) LandmarkKit.ladder(b, x, -3, q - 1, 5, Direction.NORTH, null);
        // ---- two jetties of dock planks on weathered-beam pilings
        for (int px : new int[]{5, 31}) {
            b.fill(px, 1, 1, px + 3, 1, q - 1, dock);
            for (int z = 1; z < q; z += 3)
                for (int x : new int[]{px, px + 3}) b.fill(x, -5, z, x, 0, z, pile);
            b.fill(px, 1, 1, px + 3, 1, 1, beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
            b.set(px, 2, 1, decor(ArchBlocks.BOLLARD, Direction.NORTH));
            b.set(px + 3, 2, 1, decor(ArchBlocks.BOLLARD, Direction.NORTH));
            post(b, px, 2, 7, s(Blocks.ACACIA_FENCE), 2, brass);
            b.set(px + 3, 2, 5, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
            b.set(px, 2, 10, s(ArchBlocks.CARGO_CRATE));
            b.set(px, 3, 10, decor(ArchBlocks.CARGO_SACKS, Direction.EAST));
            b.fill(px + 1, -5, 1, px + 1, 0, 1, pile);
            LandmarkKit.ladder(b, px + 1, -2, 0, 4, Direction.NORTH, null);
        }
        npc(b, "dockworker", 7, 2, 5);
        npc(b, "dockworker", 33, 2, 8);
        b.marker("ship_spawn", 20, 0, 6, new net.minecraft.nbt.CompoundTag());
        b.marker("dock", 7, 2, 2, new net.minecraft.nbt.CompoundTag());
        // a smuggler's barrel lashed under the east pier
        b.barrel(33, 0, 10, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 33, 0, 10);

        // ---- the market quay
        awningStall(b, 10, 16, ArchBlocks.RED_AWNING, Buildings.LOOT_SHOP);
        awningStall(b, 17, 16, ArchBlocks.YELLOW_AWNING, Buildings.LOOT_SHOP);
        awningStall(b, 24, 16, ArchBlocks.BLUE_AWNING, Buildings.LOOT_SHOP);
        for (int[] cg : new int[][]{{2, 16}, {3, 16}, {2, 17}, {29, 15}, {30, 15}}) Arch.cargo(b, cg[0], 2, cg[1], cg[0] + cg[1]);
        b.set(4, 2, 15, decor(ArchBlocks.FISHING_NET, Direction.SOUTH));
        npc(b, "marine", 8, 2, 17);
        npc(b, "marine", 30, 2, 17);
        npc(b, "child", 19, 2, 20);
        b.marker("legend", 20, 2, 15, tag("story", "nanohana_port"));

        // ---- the minaret watch at the east end of the quay: round shaft, balcony, onion dome
        int mx = 36, mz = 17;
        cyl(b, mx, mz, 1, 1, 3, dressed, false);
        roundTower(b, mx, mz, 2, 16, 2, s(ArchBlocks.CREAM_PLASTER), dressed, new int[]{2, 8}, s(Blocks.CUT_SANDSTONE), new int[]{13}, lattice, new int[]{4, 10, 15}, white, gold, 2, true);
        cyl(b, mx, mz, 13, 13, 4, dressed, false);
        ringRail(b, mx, mz, 14, 4, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        b.clear(mx, 14, mz - 2, mx, 15, mz - 2);
        b.bigDoor(mx, 2, mz - 2, Blocks.ACACIA_DOOR, Direction.NORTH);
        LandmarkKit.ladder(b, mx, 2, mz + 1, 12, Direction.NORTH, null);
        b.hanging(mx, 12, mz, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "marine", mx, 14, mz - 3);

        // ---- the street front: spice shop, domed tavern, warehouse
        Mats m = desert(b.seed, s(ArchBlocks.CREAM_PLASTER));
        m.window = lattice;
        // spice shop (x 1..9, z 22..31): two storeys, shopfront with an awning
        Mats ms = desert(b.seed + 1, s(ArchBlocks.OCHRE_PLASTER));
        ms.window = lattice;
        desertBlock(b, ms, 1, 22, 9, 31, 1, 2, 4, false);
        // a shaded roof terrace: four posts under a striped canvas
        for (int[] p : new int[][]{{3, 25}, {7, 25}, {3, 29}, {7, 29}}) b.fill(p[0], 10, p[1], p[0], 11, p[1], up(Blocks.STRIPPED_ACACIA_LOG));
        for (int z = 25; z <= 29; z++) awningsX(b, 3, 7, 12, z, ArchBlocks.GREEN_AWNING, z < 27 ? Direction.NORTH : Direction.SOUTH);
        b.set(5, 10, 27, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(4, 10, 27, decor(ArchBlocks.PLANK_BENCH, Direction.EAST));
        desertDoor(b, ms, 5, 2, 22, Direction.NORTH, false);
        awningsX(b, 2, 8, 5, 21, ArchBlocks.YELLOW_AWNING, Direction.NORTH);
        for (int x : new int[]{3, 7}) {
            Arch.window(b, ms, x, 2, 22, Direction.NORTH, 2, false);
            Arch.window(b, ms, x, 6, 22, Direction.NORTH, 2, false);
        }
        for (int z : new int[]{25, 28}) {
            Arch.window(b, ms, 1, 6, z, Direction.WEST, 2, false);
            Arch.window(b, ms, 9, 6, z, Direction.EAST, 2, false);
        }
        Arch.window(b, ms, 5, 6, 31, Direction.SOUTH, 2, false);
        counterRow(b, 3, 7, 2, 27, Direction.NORTH);
        shelfRow(b, 2, 8, 2, 30, Direction.NORTH, 2);
        b.barrel(2, 2, 28, Buildings.LOOT_SHOP);
        b.barrel(8, 2, 24, Buildings.LOOT_SHOP);
        b.set(8, 2, 25, decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        b.set(8, 2, 26, s(ArchBlocks.CARGO_CRATE));
        b.set(5, 3, 27, s(Blocks.DECORATED_POT));
        npc(b, "shopkeeper", 5, 2, 28);
        b.hanging(5, 4, 25, ArchBlocks.BRASS_LANTERN.get());
        flightX(b, 2, 2, 2, 23, Direction.SOUTH, 4, s(Blocks.SANDSTONE_STAIRS), null);
        LandmarkKit.line(b, 3, 6, 23, 3, 25, Blocks.ACACIA_FENCE);
        b.bed(7, 6, 29, Blocks.ORANGE_BED, Direction.SOUTH);
        b.set(8, 6, 27, decor(ArchBlocks.PLANK_TABLE, Direction.WEST));
        b.set(2, 6, 30, s(Blocks.BOOKSHELF));
        b.set(5, 6, 30, decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        b.hanging(5, 8, 27, ArchBlocks.BRASS_LANTERN.get());

        // tavern (x 11..23, z 22..32): two storeys, front terrace under an awning, a dome on a drum
        desertBlock(b, m, 11, 22, 23, 32, 1, 2, 4, true);
        desertDoor(b, m, 17, 2, 22, Direction.NORTH, false);
        awningsX(b, 11, 23, 5, 21, ArchBlocks.RED_AWNING, Direction.NORTH);
        for (int x : new int[]{13, 15, 19, 21}) Arch.window(b, m, x, 2, 22, Direction.NORTH, 2, false);
        for (int x : new int[]{13, 17, 21}) Arch.window(b, m, x, 6, 22, Direction.NORTH, 2, false);
        for (int z : new int[]{25, 29}) {
            Arch.window(b, m, 11, 6, z, Direction.WEST, 2, false);
            Arch.window(b, m, 23, 6, z, Direction.EAST, 2, false);
            Arch.window(b, m, 23, 2, z, Direction.EAST, 2, false);
        }
        for (int x : new int[]{14, 20}) Arch.window(b, m, x, 6, 32, Direction.SOUTH, 2, false);
        bench(b, 12, 2, 20, Direction.NORTH);
        bench(b, 22, 2, 20, Direction.NORTH);
        b.set(13, 2, 20, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(21, 2, 20, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        counterRow(b, 13, 20, 2, 28, Direction.NORTH);
        for (int x = 13; x <= 19; x += 2) {
            b.barrel(x, 2, 31, Buildings.LOOT_TAVERN);
            b.set(x + 1, 2, 31, s(Blocks.BARREL));
            b.set(x, 3, 31, s(Blocks.BARREL));
        }
        for (int x = 14; x <= 20; x += 3) b.set(x, 2, 27, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.NORTH, Half.BOTTOM));
        npc(b, "bartender", 17, 2, 29);
        tableSet(b, 14, 2, 25, s(Blocks.SPRUCE_STAIRS));
        tableSet(b, 20, 2, 25, s(Blocks.SPRUCE_STAIRS));
        npc(b, "patron", 14, 2, 24);
        npc(b, "patron", 20, 2, 24);
        b.marker("quest_board", 12, 3, 26, new net.minecraft.nbt.CompoundTag());
        for (int x : new int[]{14, 20}) b.hanging(x, 4, 26, ArchBlocks.BRASS_LANTERN.get());
        flightX(b, 22, 22, 2, 23, Direction.SOUTH, 4, s(Blocks.SANDSTONE_STAIRS), null);
        LandmarkKit.line(b, 21, 6, 23, 21, 25, Blocks.ACACIA_FENCE);
        for (int x = 12; x <= 18; x += 3) b.bed(x, 6, 30, Blocks.WHITE_BED, Direction.SOUTH);
        b.set(13, 6, 31, s(Blocks.BARREL));
        b.set(16, 6, 31, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(19, 6, 31, s(Blocks.BARREL));
        for (int x : new int[]{14, 20}) b.hanging(x, 8, 27, ArchBlocks.BRASS_LANTERN.get());
        cyl(b, 17, 27, 10, 11, 3, white, true);
        cyl(b, 17, 27, 10, 10, 3, dressed, true);
        for (int[] o : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) b.set(17 + o[0], 11, 27 + o[1], lattice);
        Arch.dome(b, 17, 12, 27, 3, white, gold, true);

        // warehouse (x 25..33, z 21..33): one tall storey, arched loading bay, a hoist beam over the quay
        Mats mw = desert(b.seed + 2, s(ArchBlocks.WHITE_PLASTER));
        mw.window = lattice;
        desertBlock(b, mw, 25, 21, 33, 33, 1, 1, 7, false);
        b.clear(28, 2, 21, 30, 5, 21);
        pointedArchX(b, 27, 31, 5, 21, dStair, dressed, null);
        for (int z : new int[]{24, 28, 32}) {
            Arch.window(b, mw, 25, 5, z, Direction.WEST, 2, false);
            Arch.window(b, mw, 33, 5, z, Direction.EAST, 2, false);
        }
        b.fill(29, 8, 18, 29, 8, 21, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
        for (int y = 5; y <= 7; y++) b.set(29, y, 18, chainY());
        b.set(29, 4, 18, s(ArchBlocks.CARGO_CRATE));
        awningsX(b, 26, 32, 7, 20, ArchBlocks.GREEN_AWNING, Direction.NORTH);
        for (int x = 26; x <= 32; x++) {
            if (x >= 28 && x <= 30) continue;
            for (int z = 22; z <= 31; z += 3) Arch.cargo(b, x, 2, z, x * 7 + z);
        }
        b.barrel(26, 2, 32, Buildings.LOOT_WAREHOUSE);
        b.barrel(32, 2, 32, Buildings.LOOT_WAREHOUSE);
        shelfRow(b, 28, 30, 2, 32, Direction.NORTH, 2);
        b.hanging(29, 7, 26, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "dockworker", 29, 2, 25);
        for (int[] cg : new int[][]{{31, 19}, {32, 19}, {32, 18}, {31, 20}}) Arch.cargo(b, cg[0], 2, cg[1], cg[0] + cg[1] * 3);

        // ---- the east end: palms and a water trough behind the minaret
        for (int[] p : new int[][]{{36, 24}, {35, 30}}) LandmarkKit.palm(b, p[0], 2, p[1], 6 + b.rand(p[0], 2), p[0] * 13 + p[1]);
        b.fill(36, 2, 27, 39, 2, 28, dressed);
        b.fill(37, 2, 27, 38, 2, 28, s(Blocks.WATER_CAULDRON));
        b.set(38, 2, 21, decor(ArchBlocks.PLANK_BENCH, Direction.NORTH));
    }

    // ============================================================== Alabasta: Rainbase casino

    /**
     * Rain Dinners: a three-tier stepped casino of cream plaster and dressed sandstone with gold-trimmed cornices and
     * balustraded terraces, the giant crocodile sprawled over its crown (jaws open over the front, tail sliding down
     * the back terraces), a moat with a bridge to a gilded porch. Inside: the gaming floor (roulette, card tables,
     * slots, the bar), the VIP floor with the owner's office and the vault, the crown suite, and below it all the
     * trap basement with its seastone cells and the banana-gator pool.
     */
    static void rainbaseCasino(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = 18; // 37 x 37
        BlockState plaster = s(ArchBlocks.CREAM_PLASTER), dressed = s(ArchBlocks.DRESSED_SANDSTONE), dStair = s(ArchBlocks.DRESSED_SANDSTONE_STAIRS);
        BlockState col = up(ArchBlocks.SANDSTONE_COLUMN), gold = s(Blocks.GOLD_BLOCK), band = s(Blocks.YELLOW_TERRACOTTA), lattice = s(ArchBlocks.LATTICE_WINDOW);
        BlockState green = s(Blocks.GREEN_TERRACOTTA), lime = s(Blocks.LIME_TERRACOTTA), wall = s(Blocks.SANDSTONE), brass = s(ArchBlocks.BRASS_LANTERN);
        Mats m = desert(b.seed, plaster);
        m.window = lattice;
        LandmarkKit.site(b, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, 30);

        // ---- the moat in front, rimmed in dressed sandstone, the bridge and the forecourt
        b.fill(1, -3, 0, w - 2, 0, 2, dressed);
        b.fill(2, -2, 1, w - 3, 0, 2, s(Blocks.WATER));
        b.fill(15, -2, 0, 21, 0, 5, dressed);
        b.fill(16, 0, 0, 20, 0, 5, s(Blocks.SMOOTH_SANDSTONE));
        LandmarkKit.line(b, 14, 1, 0, 14, 2, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        LandmarkKit.line(b, 22, 1, 0, 22, 2, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        for (int x : new int[]{14, 22}) post(b, x, 1, 3, dressed, 1, brass);
        b.fill(2, 0, 3, w - 3, 0, 5, dressed);
        LandmarkKit.carpet(b, 17, 1, 0, 19, 5, Blocks.RED_CARPET);

        // ---- the basement first (its roof is the gaming floor): cells, the gator pool
        LandmarkKit.shell(b, 3, -7, 14, 33, 0, 34, Blocks.SANDSTONE);

        // ---- three tiers: plaster walls on a dressed plinth, column pilasters, a yellow frieze, cornice and balustrade
        int[][] tiers = {{2, 6, 34, 34, 1, 6}, {6, 10, 30, 30, 8, 12}, {10, 14, 26, 26, 14, 17}};
        for (int[] t : tiers) {
            int x1 = t[0], z1 = t[1], x2 = t[2], z2 = t[3], lo = t[4], hi = t[5];
            if (lo > 1) b.fill(x1, lo - 1, z1, x2, lo - 1, z2, s(Blocks.SMOOTH_SANDSTONE));
            box(b, x1, lo, z1, x2, hi, z2, plaster);
            b.clear(x1 + 1, lo, z1 + 1, x2 - 1, hi, z2 - 1);
            ring(b, x1, z1, x2, z2, lo, dressed);
            ring(b, x1, z1, x2, z2, hi, band);
            for (int[] q : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
                b.fill(q[0], lo, q[1], q[0], hi - 1, q[1], dressed);
                b.set(q[0], hi, q[1], gold);
            }
            for (int x = x1 + 4; x <= x2 - 4; x += 4) {
                b.fill(x, lo + 1, z1, x, hi - 1, z1, col);
                b.fill(x, lo + 1, z2, x, hi - 1, z2, col);
            }
            for (int z = z1 + 4; z <= z2 - 4; z += 4) {
                b.fill(x1, lo + 1, z, x1, hi - 1, z, col);
                b.fill(x2, lo + 1, z, x2, hi - 1, z, col);
            }
            for (int x = x1 + 2; x <= x2 - 2; x += 4) {
                if (lo == 1 && Math.abs(x - c) <= 3) continue;
                Arch.window(b, m, x, lo + 1, z1, Direction.NORTH, hi - lo - 2, false);
                Arch.window(b, m, x, lo + 1, z2, Direction.SOUTH, hi - lo - 2, false);
            }
            for (int z = z1 + 2; z <= z2 - 2; z += 4) {
                Arch.window(b, m, x1, lo + 1, z, Direction.WEST, hi - lo - 2, false);
                Arch.window(b, m, x2, lo + 1, z, Direction.EAST, hi - lo - 2, false);
            }
            b.fill(x1, hi + 1, z1, x2, hi + 1, z2, dressed);
            cornice(b, x1, z1, x2, z2, hi + 1, dStair, gold);
            LandmarkKit.outline(b, x1, hi + 2, z1, x2, z2, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
            for (int[] q : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
                b.fill(q[0], hi + 2, q[1], q[0], hi + 4, q[1], col);
                b.set(q[0], hi + 5, q[1], gold);
                b.set(q[0], hi + 6, q[1], lantern(ArchBlocks.BRASS_LANTERN, false));
            }
        }
        // the gaming floor's own floor and the VIP / suite floors
        b.fill(3, 0, 7, 33, 0, 33, s(Blocks.SMOOTH_SANDSTONE));
        for (int x = 3; x <= 33; x++) for (int z = 7; z <= 33; z++) if ((x + z) % 2 == 0) b.set(x, 0, z, s(Blocks.CUT_SANDSTONE));

        // ---- the gilded porch and the doors
        b.fill(14, 1, 6, 22, 6, 6, dressed);
        for (int x : new int[]{15, 21}) {
            b.fill(x, 1, 3, x, 4, 3, col);
            b.set(x, 5, 3, gold);
        }
        b.fill(14, 5, 3, 22, 5, 5, dressed);
        for (int x = 14; x <= 22; x++) b.set(x, 5, 2, Arch.stair(dStair, Direction.SOUTH, Half.TOP));
        awningsX(b, 14, 22, 6, 3, ArchBlocks.RED_AWNING, Direction.NORTH);
        // the casino's marquee board over the porch
        b.fill(13, 6, 5, 23, 10, 5, gold);
        b.fill(14, 7, 5, 22, 9, 5, band);
        for (int x = 15; x <= 21; x++) if (x != 18) b.set(x, 8, 5, (x & 1) == 1 ? s(Blocks.RED_TERRACOTTA) : s(Blocks.ORANGE_TERRACOTTA));
        b.set(18, 8, 5, green);
        for (int x : new int[]{13, 23}) b.set(x, 11, 5, lantern(ArchBlocks.BRASS_LANTERN, false));
        for (int[] p : new int[][]{{4, 4}, {32, 4}}) {
            b.set(p[0], 0, p[1], s(Blocks.COARSE_DIRT));
            LandmarkKit.palm(b, p[0], 1, p[1], 7, p[0] * 11);
        }
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 1, 6, Blocks.ACACIA_DOOR, Direction.NORTH);
        b.fill(c - 1, 4, 6, c + 1, 4, 6, gold);
        b.set(16, 4, 6, Arch.stair(dStair, Direction.EAST, Half.TOP));
        b.set(20, 4, 6, Arch.stair(dStair, Direction.WEST, Half.TOP));
        for (int x : new int[]{16, 20}) b.hanging(x, 4, 4, ArchBlocks.BRASS_LANTERN.get());
        for (int x : new int[]{12, 24}) b.set(x, 5, 5, LandmarkKit.banner(Blocks.YELLOW_WALL_BANNER, Direction.NORTH));

        // ---- the gaming floor (y 1..6)
        LandmarkKit.carpet(b, c - 1, 1, 7, c + 1, 15, Blocks.RED_CARPET);
        for (int[] t : new int[][]{{6, 10}, {27, 10}, {8, 28}, {21, 28}}) {
            for (int x = t[0]; x <= t[0] + 2; x++) {
                b.set(x, 1, t[1], decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
                b.set(x, 2, t[1], s(Blocks.GREEN_CARPET));
            }
            for (int x = t[0]; x <= t[0] + 2; x += 2) {
                b.set(x, 1, t[1] - 1, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.NORTH, Half.BOTTOM));
                b.set(x, 1, t[1] + 1, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.SOUTH, Half.BOTTOM));
            }
            b.hanging(t[0] + 1, 6, t[1], ArchBlocks.BRASS_LANTERN.get());
        }
        // the roulette wheel
        LandmarkKit.disc(b, c, 19, 1, 2.3, s(Blocks.POLISHED_BLACKSTONE));
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) if (x * x + z * z <= 5) b.set(c + x, 2, 19 + z, ((x + z) & 1) == 0 ? s(Blocks.RED_CARPET) : s(Blocks.BLACK_CARPET));
        b.set(c, 2, 19, gold);
        for (int[] o : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) b.set(c + o[0], 1, 19 + o[1], Arch.stair(s(Blocks.DARK_OAK_STAIRS), o[0] < 0 ? Direction.WEST : o[0] > 0 ? Direction.EAST : o[1] < 0 ? Direction.NORTH : Direction.SOUTH, Half.BOTTOM));
        pendant(b, c, 7, 19, brass, 2);
        // slot machines along the west wall
        for (int z = 9; z <= 21; z += 2) {
            b.set(3, 1, z, LandmarkKit.facing(Blocks.DISPENSER, Direction.EAST));
            b.set(3, 2, z, s(Blocks.GLOWSTONE));
            b.set(4, 1, z, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.EAST, Half.BOTTOM));
        }
        // the bar along the east wall
        counterRow(b, 31, 31, 1, 18, Direction.WEST);
        for (int z = 18; z <= 28; z++) b.set(31, 1, z, decor(ArchBlocks.SHOP_COUNTER, Direction.WEST));
        for (int z = 18; z <= 28; z += 2) {
            b.barrel(33, 1, z, Buildings.LOOT_TAVERN);
            b.set(33, 1, z + 1, s(Blocks.BARREL));
            b.set(30, 1, z, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.WEST, Half.BOTTOM));
        }
        shelfRow(b, 33, 33, 2, 18, Direction.WEST, 1);
        npc(b, "bartender", 32, 1, 23);
        // gilded columns carrying the VIP floor
        for (int[] p : new int[][]{{12, 13}, {24, 13}, {12, 25}, {24, 25}}) {
            b.fill(p[0], 1, p[1], p[0], 5, p[1], gold);
            b.set(p[0], 6, p[1], s(Blocks.CHISELED_SANDSTONE));
        }
        for (int[] l : new int[][]{{c, 10}, {c, 25}, {9, 18}, {28, 18}}) b.hanging(l[0], 6, l[1], ArchBlocks.BRASS_LANTERN.get());
        npc(b, "patron", 9, 1, 14);
        npc(b, "patron", 27, 1, 20);
        npc(b, "patron", 16, 1, 23);
        npc(b, "guard", 15, 1, 8);
        b.marker("arc_anchor", c, 1, 9, tag("arc", "desert_casino"));
        // stairs: down to the basement (west, back) and up to the VIP floor (east, back)
        flightX(b, 4, 5, -6, 23, Direction.SOUTH, 7, s(Blocks.SANDSTONE_STAIRS), wall);
        LandmarkKit.line(b, 6, 1, 25, 6, 28, Blocks.ACACIA_FENCE);
        LandmarkKit.line(b, 4, 1, 25, 5, 25, Blocks.ACACIA_FENCE);
        flightX(b, 27, 28, 1, 33, Direction.NORTH, 7, s(Blocks.DARK_OAK_STAIRS), plaster);
        LandmarkKit.line(b, 26, 8, 28, 26, 29, Blocks.ACACIA_FENCE);
        LandmarkKit.line(b, 29, 8, 28, 29, 29, Blocks.ACACIA_FENCE);

        // ---- the VIP floor (y 8..12): the owner's office, the vault, the lounge
        LandmarkKit.carpet(b, 7, 8, 11, 29, 29, Blocks.PURPLE_CARPET);
        b.fill(17, 8, 11, 17, 12, 18, plaster);
        b.fill(7, 8, 19, 17, 12, 19, plaster);
        b.door(17, 8, 14, Blocks.DARK_OAK_DOOR, Direction.EAST);
        LandmarkKit.carpet(b, 8, 8, 12, 16, 18, Blocks.GREEN_CARPET);
        b.fill(8, 8, 18, 16, 10, 18, s(Blocks.BOOKSHELF));
        b.set(10, 8, 12, LandmarkKit.facing(Blocks.LECTERN, Direction.SOUTH));
        b.set(12, 8, 14, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(13, 8, 14, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(12, 8, 15, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.set(13, 9, 14, LandmarkKit.candles(Blocks.CANDLE, 3));
        b.set(8, 8, 12, Blocks.CARTOGRAPHY_TABLE);
        b.hanging(12, 12, 14, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "pirate_captain", 10, 8, 15);
        // the vault behind an iron door (its lever in the lounge)
        b.fill(7, 8, 22, 13, 12, 22, plaster);
        b.fill(13, 8, 23, 13, 12, 29, plaster);
        b.door(10, 8, 22, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(11, 9, 21, LandmarkKit.lever(Direction.NORTH));
        LandmarkKit.carpet(b, 7, 8, 23, 12, 29, Blocks.AIR);
        b.fill(7, 7, 23, 12, 7, 29, gold);
        for (int x = 8; x <= 12; x += 2) b.set(x, 8, 29, gold);
        b.chest(8, 8, 26, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(12, 8, 26, Direction.WEST, Buildings.LOOT_TREASURE);
        b.hanging(10, 12, 26, ArchBlocks.BRASS_LANTERN.get());
        mark(b, "treasure", 10, 8, 27);
        // the lounge: sofas, a bar, guards
        for (int x = 19; x <= 27; x++) b.set(x, 8, 11, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.NORTH, Half.BOTTOM));
        for (int x : new int[]{21, 25}) b.set(x, 8, 13, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(21, 9, 13, Blocks.POTTED_CACTUS);
        counterRow(b, 23, 27, 8, 20, Direction.NORTH);
        b.barrel(24, 8, 21, Buildings.LOOT_TAVERN);
        b.set(25, 8, 21, s(Blocks.BARREL));
        b.set(26, 8, 21, s(Blocks.BARREL));
        for (int[] l : new int[][]{{22, 15}, {25, 24}, {16, 25}}) b.hanging(l[0], 12, l[1], ArchBlocks.BRASS_LANTERN.get());
        npc(b, "guard", 21, 8, 17);
        npc(b, "guard", 15, 8, 21);
        // up to the crown suite
        flightX(b, 20, 21, 8, 25, Direction.NORTH, 6, s(Blocks.DARK_OAK_STAIRS), plaster);
        LandmarkKit.line(b, 19, 14, 21, 19, 24, Blocks.ACACIA_FENCE);
        LandmarkKit.line(b, 22, 14, 21, 22, 24, Blocks.ACACIA_FENCE);
        LandmarkKit.line(b, 20, 14, 24, 21, 24, Blocks.ACACIA_FENCE);

        // ---- the crown suite (y 14..17)
        LandmarkKit.carpet(b, 11, 14, 15, 25, 25, Blocks.YELLOW_CARPET);
        b.bed(12, 14, 17, Blocks.YELLOW_BED, Direction.NORTH);
        b.bed(14, 14, 17, Blocks.YELLOW_BED, Direction.NORTH);
        b.chest(11, 14, 19, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(25, 14, 15, decor(ArchBlocks.FLOWER_PLANTER, Direction.WEST));
        b.set(18, 14, 16, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(18, 15, 16, LandmarkKit.candles(Blocks.CANDLE, 2));
        b.hanging(18, 17, 20, ArchBlocks.BRASS_LANTERN.get());

        // ---- the crocodile sprawled over the crown: open jaws over the front, legs gripping the edges, tail down the back
        int by = 19;
        for (int z = 15; z <= 25; z++) {
            b.fill(15, by, z, 21, by + 1, z, green);
            b.fill(16, by + 2, z, 20, by + 2, z, green);
            b.set(18, by + 3, z, (z & 1) == 0 ? lime : green);
            if ((z & 1) == 1) {
                b.set(16, by + 3, z, lime);
                b.set(20, by + 3, z, lime);
            }
        }
        for (int z = 8; z <= 14; z++) {
            int hw = z <= 9 ? 1 : 2;
            b.fill(18 - hw, by, z, 18 + hw, by, z, green);
            b.fill(18 - hw, by + 2, z, 18 + hw, by + 2, z, green);
            if (z >= 12) b.fill(18 - hw, by + 1, z, 18 + hw, by + 1, z, green);
            else {
                b.fill(18 - hw + 1, by + 1, z, 18 + hw - 1, by + 1, z, s(Blocks.AIR));
                b.set(18 - hw, by + 1, z, s(Blocks.WHITE_CONCRETE));
                b.set(18 + hw, by + 1, z, s(Blocks.WHITE_CONCRETE));
            }
        }
        b.fill(18, by, 10, 18, by, 11, s(Blocks.RED_TERRACOTTA));
        b.set(17, by + 1, 12, s(Blocks.RED_TERRACOTTA));
        b.set(19, by + 1, 12, s(Blocks.RED_TERRACOTTA));
        b.set(18, by + 1, 12, s(Blocks.RED_TERRACOTTA));
        for (int x : new int[]{16, 20}) {
            b.set(x, by + 3, 13, s(Blocks.YELLOW_CONCRETE));
            b.set(x, by + 3, 12, s(Blocks.BLACK_CONCRETE));
            b.set(x, by + 3, 14, green);
        }
        b.set(17, by + 3, 8, green);
        b.set(19, by + 3, 8, green);
        for (int z : new int[]{16, 23}) {
            b.fill(13, by, z, 14, by + 1, z, green);
            b.fill(22, by, z, 23, by + 1, z, green);
            b.fill(12, by, z - 1, 12, by, z + 1, green);
            b.fill(24, by, z - 1, 24, by, z + 1, green);
        }
        b.fill(16, by, 26, 20, by + 1, 26, green);
        b.set(18, by + 2, 26, lime);
        b.fill(17, by - 2, 27, 19, by, 27, green);
        b.fill(17, by - 5, 28, 19, by - 3, 28, green);
        b.fill(17, 14, 29, 19, 14, 29, green);
        b.fill(20, 14, 29, 24, 14, 29, green);
        b.set(18, 15, 29, lime);
        b.set(22, 15, 29, lime);
        b.set(25, 14, 28, green);
        b.set(26, 14, 27, lime);

        // ---- the trap basement (y -6..-1): seastone cells, the gator pool behind bars, a thugs' storeroom
        b.fill(4, -7, 15, 32, -7, 33, wall);
        for (int x : new int[]{6, 9, 12, 15}) b.fill(x, -6, 15, x, -4, 18, wall);
        for (int x = 6; x <= 15; x++) for (int y = -6; y <= -4; y++) b.set(x, y, 18, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
        for (int x : new int[]{6, 9, 12, 15}) b.fill(x, -6, 18, x, -4, 18, wall);
        b.fill(7, -7, 15, 14, -7, 17, s(ModBlocks.SEASTONE.get()));
        for (int x = 7; x <= 13; x += 3) mark(b, "cell", x, -6, 16);
        b.fill(17, -11, 19, 32, -8, 33, wall);
        b.fill(18, -10, 20, 31, -7, 32, s(Blocks.WATER));
        LandmarkKit.line(b, 17, -6, 19, 32, 19, Blocks.IRON_BARS);
        LandmarkKit.line(b, 17, -6, 20, 17, 33, Blocks.IRON_BARS);
        b.barrel(32, -6, 15, Buildings.LOOT_WAREHOUSE);
        b.barrel(31, -6, 15, Buildings.LOOT_WAREHOUSE);
        b.set(30, -6, 15, s(ArchBlocks.CARGO_CRATE));
        b.set(29, -6, 15, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        b.set(10, -6, 25, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        bench(b, 10, -6, 26, Direction.SOUTH);
        for (int[] l : new int[][]{{10, 22}, {24, 25}, {10, 30}}) b.hanging(l[0], -1, l[1], Blocks.LANTERN);
        npc(b, "bandit", 10, -6, 22);
        npc(b, "bandit", 12, -6, 30);
    }

    // ============================================================== Alabasta: Yuba oasis

    /**
     * The oasis town the sandstorms buried: dunes drifted over everything, the tops of buried walls, an arcade and a
     * domed bath-house poking out of the sand; the inn, its lower storey under the sand, reached by a shored trench
     * dug down to its door (common room below, guest rooms with sand drifting through the windows above); the well
     * one old man keeps digging, with its crank, spoil heaps and his tent; and the forgotten cistern found at the
     * bottom of the well.
     */
    static void yubaOasis(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 29 x 29
        BlockState dressed = s(ArchBlocks.DRESSED_SANDSTONE), dStair = s(ArchBlocks.DRESSED_SANDSTONE_STAIRS), sand = s(Blocks.SAND), sst = s(Blocks.SANDSTONE);
        BlockState lattice = s(ArchBlocks.LATTICE_WINDOW), white = s(ArchBlocks.WHITE_PLASTER), wbeam = up(ArchBlocks.WEATHERED_BEAM);
        LandmarkKit.site(b, Blocks.SAND, Blocks.SANDSTONE, 22);
        // ---- dunes first (the buildings and the dig cut their own space out of them)
        heap(b, 71, 4, 4, 1, 3, 3, Blocks.SAND);
        heap(b, 72, 13, 3, 1, 3, 2, Blocks.SAND);
        heap(b, 73, 25, 4, 1, 3, 2, Blocks.SAND);
        heap(b, 74, 3, 16, 1, 3, 3, Blocks.SAND);
        heap(b, 75, 4, 25, 1, 3, 3, Blocks.SAND);
        heap(b, 76, 10, 25, 1, 3, 2, Blocks.SAND);
        heap(b, 77, 17, 25, 1, 3, 2, Blocks.SAND);
        heap(b, 78, 24, 24, 1, 4, 3, Blocks.SAND);
        heap(b, 79, 15, 18, 1, 2, 2, Blocks.SAND);
        LandmarkKit.scatter(b, 81, 10, 0, 0, w - 1, d - 1, 1, s(Blocks.DEAD_BUSH));

        // ---- the inn: lower storey buried (floor -4), upper storey just above the sand (floor 0), roof at 4
        Mats m = desert(b.seed, s(ArchBlocks.OCHRE_PLASTER));
        m.window = lattice;
        int x1 = 3, x2 = 13, z1 = 14, z2 = 25;
        b.fill(x1, -5, z1, x2, -5, z2, sst);
        desertBlock(b, m, x1, z1, x2, z2, -4, 2, 4, true);
        for (int x : new int[]{6, 10}) Arch.window(b, m, x, 1, z1, Direction.NORTH, 2, false);
        for (int z : new int[]{17, 21}) {
            Arch.window(b, m, x2, 1, z, Direction.EAST, 2, false);
            Arch.window(b, m, x1, 1, z, Direction.WEST, 1, false);
        }
        Arch.window(b, m, 8, 1, z2, Direction.SOUTH, 2, false);
        // sand drifted onto the roof and in through the west windows
        for (int z = z1; z <= z2; z++) if (b.rand(z * 7, 3) != 0) b.set(x1, 5, z, sand);
        for (int x = x1; x <= x2; x++) if (b.rand(x * 11, 2) == 0) b.set(x, 5, z2, sand);
        b.fill(x1 + 1, 5, z2 - 3, x1 + 3, 5, z2 - 1, sand);
        b.fill(x1 + 1, 1, 20, x1 + 1, 1, 22, sand);
        b.set(x1 + 2, 1, 21, Arch.slab(s(Blocks.SANDSTONE_SLAB), net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
        // the trench dug down to the lower door, shored with weathered timber
        b.clear(6, -3, 6, 10, 6, 13);
        b.fill(6, -4, 6, 10, -4, 13, sst);
        for (int x : new int[]{6, 10}) b.fill(x, -4, 6, x, 1, 13, s(ArchBlocks.WEATHERED_PLANKS));
        for (int z = 7; z <= 13; z += 3)
            for (int x : new int[]{6, 10}) b.fill(x, -3, z, x, 1, z, wbeam);
        for (int z = 7; z <= 13; z += 3) b.fill(6, 2, z, 10, 2, z, beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        flightX(b, 7, 9, -3, 9, Direction.NORTH, 4, s(Blocks.SANDSTONE_STAIRS), sst);
        b.fill(7, -4, 10, 9, -4, 13, sst);
        b.clear(7, -3, 10, 9, 1, 13);
        b.set(9, -3, 13, sand);
        b.set(7, -3, 11, decor(ArchBlocks.CARGO_SACKS, Direction.EAST));
        b.bigDoor(8, -3, z1, Blocks.ACACIA_DOOR, Direction.NORTH);
        b.hanging(8, 1, 13, ArchBlocks.BRASS_LANTERN.get());
        // lower storey: the common room, the back store, the stair up
        b.fill(x1 + 1, -3, 20, x2 - 1, -1, 20, s(ArchBlocks.OCHRE_PLASTER));
        b.bigDoor(9, -3, 20, Blocks.ACACIA_DOOR, Direction.NORTH);
        counterRow(b, 10, 12, -3, 17, Direction.NORTH);
        b.barrel(11, -3, 19, Buildings.LOOT_TAVERN);
        b.barrel(12, -3, 19, Buildings.LOOT_TAVERN);
        b.set(10, -3, 19, s(Blocks.BARREL));
        b.set(12, -2, 19, decor(ArchBlocks.GOODS_SHELF, Direction.NORTH));
        tableSet(b, 6, -3, 17, s(Blocks.ACACIA_STAIRS));
        b.set(6, -2, 17, LandmarkKit.candles(Blocks.CANDLE, 2));
        b.set(4, -3, 15, sand);
        b.set(12, -3, 15, sand);
        b.set(4, -3, 16, Arch.slab(s(Blocks.SANDSTONE_SLAB), net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
        b.hanging(8, -1, 17, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "innkeeper", 11, -3, 18);
        for (int x = 7; x <= 12; x++) for (int z = 22; z <= 24; z += 2) if ((x + z) % 3 != 0) Arch.cargo(b, x, -3, z, x * 5 + z);
        flightX(b, 4, 4, -3, 24, Direction.NORTH, 4, s(Blocks.SANDSTONE_STAIRS), sst);
        LandmarkKit.line(b, 5, 1, 22, 5, 24, Blocks.ACACIA_FENCE);
        b.hanging(8, -1, 22, ArchBlocks.BRASS_LANTERN.get());
        // upper storey: guest rooms
        for (int x = 7; x <= 11; x += 2) b.bed(x, 1, 23, Blocks.ORANGE_BED, Direction.SOUTH);
        b.chest(12, 1, 24, Direction.WEST, Buildings.LOOT_HOUSE);
        b.set(12, 1, 15, decor(ArchBlocks.PLANK_TABLE, Direction.WEST));
        b.set(11, 1, 15, decor(ArchBlocks.PLANK_BENCH, Direction.EAST));
        b.set(4, 1, 15, s(Blocks.BARREL));
        b.hanging(8, 3, 19, ArchBlocks.BRASS_LANTERN.get());

        // ---- the well: dug by hand, a crank over it, spoil heaps, a ladder down to the cistern
        int wx = 20, wz = 8;
        b.fill(wx - 2, -13, wz - 2, wx + 2, 0, wz + 2, sst);
        b.clear(wx - 1, -12, wz - 1, wx + 1, 6, wz + 1);
        b.fill(wx - 1, -12, wz - 1, wx + 1, -11, wz + 1, s(Blocks.WATER));
        b.clear(wx - 2, 1, wz - 2, wx + 2, 6, wz + 2);
        LandmarkKit.outline(b, wx - 2, 1, wz - 2, wx + 2, wz + 2, ArchBlocks.DRESSED_SANDSTONE_WALL.get());
        b.clear(wx + 2, 1, wz + 1, wx + 2, 1, wz + 1);
        for (int x : new int[]{wx - 2, wx + 2}) b.fill(x, 2, wz, x, 3, wz, s(Blocks.ACACIA_FENCE));
        b.fill(wx - 2, 4, wz, wx + 2, 4, wz, beam(Blocks.STRIPPED_ACACIA_LOG, Direction.Axis.X));
        b.set(wx, 3, wz, chainY());
        b.set(wx, 2, wz, lantern(ArchBlocks.BRASS_LANTERN, true));
        b.ladderUp(wx + 1, -10, wz + 1, 11, Direction.WEST);
        heap(b, 82, 16, 4, 1, 2, 2, Blocks.SAND);
        heap(b, 83, 24, 5, 1, 2, 2, Blocks.SAND);
        b.set(18, 1, 11, s(Blocks.COMPOSTER));
        b.set(22, 1, 11, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        b.set(17, 1, 9, s(Blocks.GRINDSTONE).setValue(GrindstoneBlock.FACE, AttachFace.FLOOR));
        // the cistern: a vaulted water chamber off the bottom of the well
        b.clear(wx + 1, -10, wz + 2, wx + 1, -9, wz + 5);
        LandmarkKit.shell(b, wx - 4, -11, wz + 6, wx + 4, -6, wz + 12, Blocks.SANDSTONE);
        b.clear(wx + 1, -10, wz + 6, wx + 1, -9, wz + 6);
        for (int z = wz + 7; z <= wz + 11; z += 2) b.fill(wx - 3, -7, z, wx + 3, -7, z, dressed);
        b.fill(wx - 3, -11, wz + 8, wx + 1, -11, wz + 11, s(Blocks.WATER));
        b.fill(wx - 3, -10, wz + 9, wx - 1, -10, wz + 11, s(Blocks.WATER));
        b.chest(wx + 3, -10, wz + 11, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(wx + 3, -10, wz + 7, Blocks.DECORATED_POT);
        b.hanging(wx, -8, wz + 9, Blocks.LANTERN);
        b.set(wx, -7, wz + 9, dressed);
        mark(b, "treasure", wx + 3, -10, wz + 11);
        b.marker("legend", wx, 1, wz - 3, tag("story", "yuba_oasis"));

        // ---- the digger's camp: a canvas tent, a fire, his bedroll
        b.clear(21, 1, 12, 28, 5, 19);
        b.fill(21, 0, 12, 28, 0, 19, sand);
        for (int z = 14; z <= 18; z++) {
            b.set(22, 1, z, s(Blocks.WHITE_WOOL));
            b.set(26, 1, z, s(Blocks.WHITE_WOOL));
            b.set(23, 2, z, s(Blocks.WHITE_WOOL));
            b.set(25, 2, z, s(Blocks.WHITE_WOOL));
            b.set(24, 3, z, s(Blocks.WHITE_WOOL));
        }
        b.fill(23, 1, 18, 25, 1, 18, s(Blocks.WHITE_WOOL));
        b.set(24, 2, 18, s(Blocks.WHITE_WOOL));
        for (int z : new int[]{13, 19}) b.set(24, 1, z, s(Blocks.ACACIA_FENCE));
        b.bed(24, 1, 16, Blocks.BROWN_BED, Direction.SOUTH);
        b.barrel(23, 1, 15, Buildings.LOOT_HOUSE);
        b.set(25, 1, 15, decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        b.set(24, 0, 11, sst);
        b.set(24, 1, 11, campfire(true));
        bench(b, 22, 1, 11, Direction.EAST);
        npc(b, "elder", 19, 1, 12);
        npc(b, "merchant", 23, 1, 10);

        // ---- the buried town: a bath-house dome, an arcade's arch tops, dead palms and one stubborn living palm
        cyl(b, 24, 24, -2, 1, 3, white, true);
        Arch.dome(b, 24, 2, 24, 3, white, s(Blocks.GOLD_BLOCK), true);
        b.set(27, 3, 24, lattice);
        for (int x = 15; x <= 19; x += 2) {
            b.fill(x, 1, 27, x, 2, 27, up(ArchBlocks.SANDSTONE_COLUMN));
            b.set(x, 3, 27, dressed);
        }
        b.fill(15, 3, 27, 19, 3, 27, dressed);
        b.set(16, 2, 27, Arch.stair(dStair, Direction.EAST, Half.TOP));
        b.set(18, 2, 27, Arch.stair(dStair, Direction.WEST, Half.TOP));
        b.fill(15, 1, 26, 19, 1, 26, sand);
        for (int[] p : new int[][]{{2, 9}, {14, 4}, {27, 21}, {1, 23}}) b.fill(p[0], 1, p[1], p[0], 3 + b.rand(p[0], 3), p[1], s(Blocks.STRIPPED_JUNGLE_LOG));
        LandmarkKit.palm(b, 16, 1, 13, 6, 77);
        b.set(16, 0, 13, s(Blocks.COARSE_DIRT));
    }

    // ============================================================== Jaya: the half house

    /**
     * The explorer's house that is only a front: from the shore it is a pastel two-storey villa with a gabled entrance
     * bay, a columned porch carrying a balcony, a round corner turret under a conical roof and shuttered windows; from
     * the side it is a slice three blocks deep, its roof a half gable cut off at the ridge, its back a boarded-up cut
     * propped with timber. Behind it: the salvage crew's camp (tent, fire, diving bell, anchor, winch and salvage)
     * and the practice dive pool.
     */
    static void cricketHouse(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 21 x 19
        LandmarkKit.site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 20);
        patchGround(b, 81, 0, Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.COARSE_DIRT);
        BlockState face = s(ArchBlocks.SKY_PLASTER), trim = s(ArchBlocks.WHITE_PLASTER), base = s(ArchBlocks.PALE_MASONRY), boards = s(ArchBlocks.WEATHERED_PLANKS);
        BlockState col = up(ArchBlocks.MARBLE_COLUMN), roof = s(ArchBlocks.TERRACOTTA_ROOF_TILES), roofSt = s(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS);
        Mats m = mats(BuildStyle.VILLAGE, b.seed);
        m.wall = face;
        m.upperWall = face;
        m.window = s(ArchBlocks.MULLION_WINDOW);
        m.shutter = ArchBlocks.OAK_SHUTTER.get();
        m.planters = true;
        m.roof = roof;
        m.roofStairs = roofSt;
        m.roofSlab = s(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.GABLE;
        m.eaveBrackets = false;
        int x1 = 2, x2 = 18, z1 = 3, z2 = 6; // the slice: two storeys, interior only z 4..5
        // ---- the shell of the slice and the entrance bay (x 8..12, projecting to z 1)
        b.fill(x1, 0, z1, x2, 0, z2, s(Blocks.SPRUCE_PLANKS));
        b.fill(8, 0, 1, 12, 0, 3, s(Blocks.SPRUCE_PLANKS));
        box(b, x1, 1, z1, x2, 7, z2, face);
        box(b, 8, 1, 1, 12, 7, 3, face);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 7, z2 - 1);
        b.clear(9, 1, 2, 11, 7, 5);
        ring(b, x1, z1, x2, z2, 1, base);
        ring(b, 8, 1, 12, 3, 1, base);
        ring(b, x1, z1, x2, z2, 4, trim);
        ring(b, 8, 1, 12, 3, 4, trim);
        b.fill(x1 + 1, 4, z1 + 1, x2 - 1, 4, z2 - 1, s(Blocks.SPRUCE_PLANKS));
        b.fill(9, 4, 2, 11, 4, 3, s(Blocks.SPRUCE_PLANKS));
        for (int[] q : new int[][]{{x1, z1}, {x2, z1}, {8, 1}, {12, 1}}) b.fill(q[0], 1, q[1], q[0], 7, q[1], trim);
        // the back is the cut: boards patched over the raw edge, gaps showing the rooms, floor joists sticking out
        for (int x = x1; x <= x2; x++)
            for (int y = 1; y <= 7; y++) {
                int r = b.rand(x * 7 + y * 31, 9);
                b.set(x, y, z2, r == 0 ? s(Blocks.AIR) : r == 1 ? face : boards);
            }
        b.fill(x1, 1, z2, x2, 1, z2, boards);
        for (int x = x1; x <= x2; x += 2) b.set(x, 4, z2 + 1, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
        // ---- the roof: a half gable rising from the front eave to the ridge at the cut, a cross gable over the bay
        for (int z = 2; z <= z2; z++) {
            int y = 8 + (z - 2);
            for (int x = x1 - 1; x <= x2 + 1; x++) b.set(x, y, z, Arch.stair(roofSt, Direction.SOUTH, Half.BOTTOM));
            for (int x : new int[]{x1, x2}) b.fill(x, 8, z, x, y - 1, z, face);
        }
        for (int x = x1 - 1; x <= x2 + 1; x++) b.set(x, 13, z2, Arch.slab(m.roofSlab, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
        for (int y = 8; y <= 11; y++) b.fill(x1 + 1, y, z2, x2 - 1, y, z2, boards);
        b.fill(x1, 8, z1, x2, 8, z1, face);
        Arch.gable(b, m, 8, 1, 12, 4, 8, false, 1, face);
        b.set(10, 9, 1, s(ArchBlocks.LATTICE_WINDOW));
        b.set(10, 10, 1, trim);
        // ---- the corner turret with a conical roof
        roundTower(b, 16, 3, 1, 9, 2, face, trim, new int[]{1, 4}, s(Blocks.SPRUCE_PLANKS), new int[]{4}, null, new int[]{}, null, null, 0, false);
        for (int y : new int[]{2, 6}) {
            Arch.window(b, m, 16, y, 1, Direction.NORTH, 2, false);
            Arch.window(b, m, 18, y, 3, Direction.EAST, 2, false);
        }
        for (int i = 0; i < 6; i++) {
            double r = 3.2 - i * 0.6;
            if (r < 0.5) b.set(16, 10 + i, 3, roof);
            else LandmarkKit.disc(b, 16, 3, 10 + i, r, roof);
        }
        b.set(16, 16, 3, s(Blocks.LIGHTNING_ROD));
        // ---- the porch: two columns, a balcony on top, the front door
        b.fill(8, 0, 0, 12, 0, 0, base);
        for (int x : new int[]{8, 12}) b.fill(x, 1, 0, x, 3, 0, col);
        b.fill(8, 4, 0, 12, 4, 0, trim);
        LandmarkKit.line(b, 8, 5, 0, 12, 0, Blocks.BIRCH_FENCE);
        b.bigDoor(10, 1, 1, Blocks.BIRCH_DOOR, Direction.NORTH);
        b.door(10, 5, 1, Blocks.BIRCH_DOOR, Direction.NORTH);
        b.hanging(10, 3, 0, ArchBlocks.BRASS_LANTERN.get());
        for (int x : new int[]{9, 11}) Arch.window(b, m, x, 6, 1, Direction.NORTH, 1, false);
        // windows with shutters and planters
        for (int x : new int[]{4, 6}) {
            Arch.window(b, m, x, 2, z1, Direction.NORTH, 2, true);
            Arch.window(b, m, x, 6, z1, Direction.NORTH, 2, true);
        }
        Arch.window(b, m, x1, 6, 4, Direction.WEST, 2, false);
        Arch.wallLantern(b, m, 7, 2, z1, Direction.NORTH);
        // ---- inside: the parlour (logbooks, the old man's chair) and the stair; the bedroom and study above
        flightZ(b, 4, 5, 1, 3, Direction.EAST, 4, s(Blocks.OAK_STAIRS), null);
        b.fill(13, 1, 5, 15, 2, 5, s(Blocks.BOOKSHELF));
        b.set(12, 1, 5, LandmarkKit.facing(Blocks.LECTERN, Direction.NORTH));
        b.set(8, 1, 5, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.set(9, 1, 5, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(9, 2, 5, LandmarkKit.candles(Blocks.CANDLE, 2));
        b.set(16, 1, 3, s(Blocks.CRAFTING_TABLE));
        b.set(17, 1, 4, s(Blocks.BARREL));
        b.hanging(12, 3, 4, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "elder", 11, 1, 4);
        b.bed(13, 5, 4, Blocks.LIGHT_BLUE_BED, Direction.EAST);
        b.chest(17, 5, 4, Direction.WEST, Buildings.LOOT_HOUSE);
        b.set(9, 5, 5, Blocks.CARTOGRAPHY_TABLE);
        b.set(10, 5, 5, s(Blocks.BOOKSHELF));
        b.set(16, 5, 2, LandmarkKit.facing(Blocks.LECTERN, Direction.NORTH));
        b.hanging(10, 7, 4, ArchBlocks.BRASS_LANTERN.get());

        // ---- the slice is propped from behind with timber shores
        for (int x : new int[]{4, 10, 16}) {
            b.fill(x, 1, 8, x, 3, 8, up(ArchBlocks.WEATHERED_BEAM));
            b.set(x, 4, 8, Arch.stair(s(ArchBlocks.WEATHERED_PLANK_STAIRS), Direction.NORTH, Half.BOTTOM));
            b.set(x, 4, 7, beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.Z));
            b.set(x, 5, 7, Arch.stair(s(ArchBlocks.WEATHERED_PLANK_STAIRS), Direction.NORTH, Half.BOTTOM));
        }
        b.fill(6, 6, 7, 8, 7, 7, decor(ArchBlocks.BLUE_AWNING, Direction.SOUTH));

        // ---- the salvage crew's camp
        for (int z = 11; z <= 14; z++) {
            b.set(2, 1, z, s(Blocks.WHITE_WOOL));
            b.set(6, 1, z, s(Blocks.WHITE_WOOL));
            b.set(3, 2, z, s(Blocks.WHITE_WOOL));
            b.set(5, 2, z, s(Blocks.WHITE_WOOL));
            b.set(4, 3, z, s(Blocks.WHITE_WOOL));
        }
        b.fill(3, 1, 14, 5, 1, 14, s(Blocks.WHITE_WOOL));
        b.set(4, 2, 14, s(Blocks.WHITE_WOOL));
        b.bed(4, 1, 12, Blocks.BROWN_BED, Direction.SOUTH);
        b.barrel(3, 1, 12, Buildings.LOOT_HOUSE);
        b.set(12, 1, 10, Blocks.CARTOGRAPHY_TABLE);
        b.set(13, 1, 10, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(13, 2, 10, LandmarkKit.candles(Blocks.CANDLE, 1));
        b.set(14, 0, 13, s(Blocks.COBBLESTONE));
        b.set(14, 1, 13, campfire(true));
        bench(b, 12, 1, 13, Direction.EAST);
        bench(b, 16, 1, 13, Direction.WEST);
        // diving bell, anchor, winch, salvage
        LandmarkKit.ellipsoid(b, 17, 2, 16, 1.6, 1.6, 1.6, s(Blocks.GLASS), true, -1);
        b.set(17, 4, 16, s(Blocks.IRON_BLOCK));
        b.set(17, 5, 16, chainY());
        b.fill(19, 1, 10, 19, 1, 10, s(Blocks.IRON_BLOCK));
        b.fill(19, 2, 10, 19, 4, 10, chainY());
        b.set(18, 1, 10, s(Blocks.ANVIL));
        b.set(19, 1, 11, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        b.barrel(19, 1, 13, Buildings.LOOT_WAREHOUSE);
        b.set(19, 1, 14, s(ArchBlocks.CARGO_CRATE));
        b.set(19, 2, 14, decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        b.set(18, 1, 18, decor(ArchBlocks.FISHING_NET, Direction.NORTH));
        npc(b, "dockworker", 11, 1, 12);
        npc(b, "dockworker", 15, 1, 16);
        // the practice dive pool: something glints at the bottom
        b.fill(7, -8, 14, 11, 0, 18, s(ArchBlocks.HARBOUR_STONE));
        b.fill(8, -7, 15, 10, 0, 17, s(Blocks.WATER));
        for (int x = 7; x <= 11; x++) b.set(x, 0, 14, s(ArchBlocks.DOCK_PLANKS));
        b.set(7, 1, 14, decor(ArchBlocks.BOLLARD, Direction.NORTH));
        b.chest(9, -7, 16, Direction.NORTH, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 9, -7, 16);
        b.marker("legend", 10, 1, 8, tag("story", "half_house"));
    }

    private ParadiseLandmarks() {}
}
