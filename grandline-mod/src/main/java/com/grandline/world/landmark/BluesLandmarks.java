package com.grandline.world.landmark;

import com.grandline.registry.ArchBlocks;
import com.grandline.world.Island;
import com.grandline.world.build.Arch;
import com.grandline.world.build.Arch.Mats;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import static com.grandline.world.landmark.LandmarkKit.*;
import static com.grandline.world.landmark.LandmarksB.*;

/**
 * Set pieces of the four Blues: Dawn (Gray Terminal), Syrup (the hilltop mansion), Cocoyasi (the tangerine grove),
 * Flevance (the White City), Lvneel (the Explorers' Monument), Spider Miles (the junkyard fort) and Ilisia (the royal
 * castle). Built with the architectural kit (ArchBlocks) and the Arch roofs and windows through the LandmarksB helpers.
 */
final class BluesLandmarks {

    private static final Block[] JUNK = {Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.TUFF, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.SPRUCE_PLANKS,
            Blocks.BARREL, Blocks.COMPOSTER, Blocks.BONE_BLOCK, Blocks.WEATHERED_CUT_COPPER, Blocks.DIRT, Blocks.PACKED_MUD};

    /**
     * A rusty tin shed roof over a box [x1..x2]x[z1..z2] whose walls top out at h: slabs of weathered cut copper rising
     * one block per three away from the {@code low} side, one block of overhang all round, the gable sides and the
     * high wall filled up to the roof with {@code infill}.
     */
    private static void tinRoof(Builder b, int x1, int z1, int x2, int z2, int h, Direction low, BlockState infill) {
        boolean alongX = low.getAxis() == Direction.Axis.X;
        boolean lowAtMin = low == Direction.WEST || low == Direction.NORTH;
        int a1 = alongX ? x1 - 1 : z1 - 1, a2 = alongX ? x2 + 1 : z2 + 1;
        int o1 = alongX ? z1 - 1 : x1 - 1, o2 = alongX ? z2 + 1 : x2 + 1;
        for (int a = a1; a <= a2; a++) {
            int i = lowAtMin ? a - a1 : a2 - a;
            int hh = 2 * (h + 1) + (i * 2) / 3, yy = hh / 2;
            BlockState tin = s(Math.floorMod(a * 7 + h, 5) == 0 ? Blocks.WEATHERED_CUT_COPPER_SLAB : Blocks.EXPOSED_CUT_COPPER_SLAB);
            BlockState sl = Arch.slab(tin, hh % 2 == 0 ? net.minecraft.world.level.block.state.properties.SlabType.BOTTOM : net.minecraft.world.level.block.state.properties.SlabType.TOP);
            for (int o = o1; o <= o2; o++) {
                if (alongX) b.set(a, yy, o, sl);
                else b.set(o, yy, a, sl);
            }
            if (a > a1 && a < a2)
                for (int y = h + 1; y < yy; y++)
                    for (int o : new int[]{o1 + 1, o2 - 1}) {
                        if (alongX) b.set(a, y, o, infill);
                        else b.set(o, y, a, infill);
                    }
            if (a == (lowAtMin ? a2 - 1 : a1 + 1))
                for (int y = h + 1; y < yy; y++)
                    for (int o = o1 + 1; o <= o2 - 1; o++) {
                        if (alongX) b.set(a, y, o, infill);
                        else b.set(o, y, a, infill);
                    }
        }
    }

    /**
     * A junk shack: weathered-plank walls patched with odd planks and rusty sheet on a dark-beam frame and a rubble
     * footing, a rusty tin lean-to roof (sloping toward {@code low}) with a tarp patch, a door and a barred window.
     * Interior y = 1..h.
     */
    private static void shack(Builder b, int x1, int z1, int x2, int z2, int h, int doorX, int doorZ, Direction doorFacing, Direction low, int salt) {
        BlockState plank = s(ArchBlocks.WEATHERED_PLANKS), post = up(ArchBlocks.DARK_BEAM);
        BlockState[] patch = {s(Blocks.SPRUCE_PLANKS), s(Blocks.WEATHERED_CUT_COPPER), s(Blocks.DARK_OAK_PLANKS)};
        b.fill(x1, 0, z1, x2, 0, z2, s(ArchBlocks.DOCK_PLANKS));
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                for (int y = 1; y <= h; y++) {
                    if (!edge) {
                        b.set(x, y, z, s(Blocks.AIR));
                        continue;
                    }
                    int r = b.rand(salt + x * 31 + y * 7 + z * 13, 7);
                    b.set(x, y, z, y == 1 ? s(Blocks.MOSSY_COBBLESTONE) : r < patch.length ? patch[r] : plank);
                }
            }
        for (int[] q : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(q[0], 1, q[1], q[0], h, q[1], post);
        tinRoof(b, x1, z1, x2, z2, h, low, plank);
        b.door(doorX, 1, doorZ, Blocks.SPRUCE_DOOR, doorFacing);
        Direction.Axis wa = doorFacing.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
        int wx = doorFacing.getAxis() == Direction.Axis.Z ? (doorX == x1 + 1 ? x2 - 1 : x1 + 1) : doorX;
        int wz = doorFacing.getAxis() == Direction.Axis.Z ? doorZ : (doorZ == z1 + 1 ? z2 - 1 : z1 + 1);
        b.set(wx, 2, wz, Arch.along(s(Blocks.IRON_BARS), wa));
    }

    // ============================================================== Dawn Island: Gray Terminal

    /**
     * The garbage-heap shanty town under the kingdom's wall: the pale masonry wall itself (battered footing,
     * buttresses, a string course, a crenellated wall walk, the barred gate between two gate towers and a guardroom in
     * its thickness); mounds of junk with shacks of weathered planks and rusty tin built against them; a junk trader's
     * awning stall, a communal fire, smouldering heaps; and the old oak at the edge holding the brothers' treehouse
     * with their flag and a cache in its trunk.
     */
    static void grayTerminal(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 37 x 33
        BlockState pale = s(ArchBlocks.PALE_MASONRY), paleSt = s(ArchBlocks.PALE_MASONRY_STAIRS), field = s(ArchBlocks.FIELDSTONE);
        site(b, Blocks.COARSE_DIRT, Blocks.DIRT, 26);
        patchGround(b, 11, 0, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT);
        scatter(b, 25, 14, 1, 1, 15, 25, 1, Blocks.SHORT_GRASS.defaultBlockState());
        scatter(b, 26, 6, 21, 9, 34, 25, 1, Blocks.DEAD_BUSH.defaultBlockState());
        // the track from the slum to the gate
        b.fill(17, 0, 0, 19, 0, 27, Blocks.DIRT_PATH);

        // ---- the kingdom's wall (z 28..32): fieldstone footing, pale masonry, buttresses, string course, wall walk
        int wy = 13;
        b.fill(0, 1, 28, w - 1, wy, d - 1, pale);
        b.fill(0, 1, 28, w - 1, 2, d - 1, field);
        for (int x = 0; x < w; x++) {
            b.set(x, 1, 27, Arch.stair(s(ArchBlocks.FIELDSTONE_STAIRS), Direction.SOUTH, Half.BOTTOM));
            b.set(x, 7, 27, Arch.stair(paleSt, Direction.SOUTH, Half.TOP));
            if (b.rand(x * 41, 5) == 0) b.set(x, 3 + b.rand(x, 8), 28, s(Blocks.CRACKED_STONE_BRICKS));
        }
        for (int x = 2; x < w; x += 6) {
            if (x >= 12 && x <= 24) continue;
            b.fill(x, 1, 26, x + 1, 9, 27, pale);
            b.fill(x, 1, 26, x + 1, 2, 27, field);
            b.set(x, 10, 26, Arch.stair(paleSt, Direction.SOUTH, Half.BOTTOM));
            b.set(x + 1, 10, 26, Arch.stair(paleSt, Direction.SOUTH, Half.BOTTOM));
        }
        for (int x = 0; x < w; x++) b.set(x, wy, 27, Arch.stair(paleSt, Direction.SOUTH, Half.TOP));
        for (int x = 0; x < w; x++) {
            b.set(x, wy + 1, 28, (x & 1) == 0 ? pale : s(Blocks.AIR));
            b.set(x, wy + 1, d - 1, (x & 1) == 0 ? pale : s(Blocks.AIR));
        }
        // the gate towers and the barred gate between them
        for (int tx : new int[]{13, 21}) {
            b.fill(tx, 1, 26, tx + 2, wy + 4, d - 1, pale);
            b.fill(tx, 1, 26, tx + 2, 2, 26, field);
            for (int x = tx; x <= tx + 2; x++) b.set(x, wy + 4, 25, Arch.stair(paleSt, Direction.SOUTH, Half.TOP));
            for (int z = 26; z < d; z++) {
                b.set(tx - 1, wy + 4, z, Arch.stair(paleSt, Direction.EAST, Half.TOP));
                b.set(tx + 3, wy + 4, z, Arch.stair(paleSt, Direction.WEST, Half.TOP));
            }
            for (int x = tx; x <= tx + 2; x++) for (int z = 26; z <= d - 1; z++)
                if (x == tx || x == tx + 2 || z == 26 || z == d - 1) if (((x + z) & 1) == 0) b.set(x, wy + 5, z, pale);
            Arch.window(b, mats(BuildStyle.MARINE, 0), tx + 1, wy - 2, 26, Direction.NORTH, 2, false);
            b.set(tx + 1, wy, 25, LandmarkKit.banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        }
        b.clear(16, 1, 26, 20, 8, 27);
        LandmarkKit.archX(b, 18, 2, 1, 28, d - 1, 4);
        for (int y = 1; y <= 6; y++) LandmarkKit.line(b, 16, y, 30, 20, 30, Blocks.IRON_BARS);
        LandmarkKit.line(b, 17, 7, 30, 19, 30, Blocks.IRON_BARS);
        b.fill(15, 9, 27, 21, 9, 27, s(Blocks.CHISELED_STONE_BRICKS));
        b.set(18, 8, 27, s(Blocks.CHISELED_STONE_BRICKS));
        // passages through the gate towers at wall-walk level
        b.clear(13, wy + 1, 30, 23, wy + 2, 30);
        b.fill(16, wy + 1, 30, 20, wy + 1, 30, s(Blocks.AIR));
        b.fill(16, 0, 26, 20, 0, d - 1, s(ArchBlocks.HARBOUR_STONE));
        // the guardroom inside the wall, a ladder shaft to the wall walk
        b.clear(24, 1, 29, 30, 4, 31);
        b.fill(24, 0, 29, 30, 0, 31, s(Blocks.SPRUCE_PLANKS));
        b.door(29, 1, 28, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(29, 1, 27, s(Blocks.AIR));
        b.set(28, 2, 27, LandmarkKit.button(Blocks.STONE_BUTTON, Direction.NORTH));
        b.set(30, 2, 29, LandmarkKit.button(Blocks.STONE_BUTTON, Direction.SOUTH));
        b.bed(25, 1, 29, Blocks.BLUE_BED, Direction.SOUTH);
        b.chest(30, 1, 31, Direction.WEST, Buildings.LOOT_HOUSE);
        b.set(27, 1, 31, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(27, 2, 31, LandmarkKit.candles(Blocks.CANDLE, 2));
        b.set(26, 1, 31, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.WEST, Half.BOTTOM));
        b.set(28, 1, 31, Blocks.LECTERN);
        b.hanging(27, 4, 30, ArchBlocks.BRASS_LANTERN.get());
        b.clear(24, 5, 30, 24, wy, 30);
        b.ladderUp(24, 1, 30, wy, Direction.EAST);
        b.clear(24, wy + 1, 30, 24, wy + 1, 30);
        npc(b, "guard", 18, wy + 1, 30);
        npc(b, "guard", 16, 1, 25);
        npc(b, "guard", 27, 1, 30);

        // ---- junk mounds with smouldering fires
        heap(b, 21, 6, 4, 1, 4, 4, JUNK);
        heap(b, 22, 31, 22, 1, 3, 4, JUNK);
        heap(b, 23, 12, 3, 1, 3, 3, JUNK);
        heap(b, 24, 13, 21, 1, 3, 3, JUNK);
        heap(b, 27, 27, 9, 1, 2, 2, JUNK);
        for (int[] f : new int[][]{{6, 4}, {31, 22}}) {
            b.set(f[0], 5, f[1], s(Blocks.COBBLESTONE));
            b.set(f[0], 6, f[1], campfire(true));
        }
        b.set(27, 0, 21, s(Blocks.COBBLESTONE));
        b.set(27, 1, 21, campfire(true));

        // ---- the communal fire pit
        b.set(13, 0, 10, s(Blocks.COBBLESTONE));
        b.set(13, 1, 10, campfire(true));
        bench(b, 11, 1, 10, Direction.EAST);
        bench(b, 15, 1, 10, Direction.WEST);
        b.set(13, 1, 12, LandmarksB.beam(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
        b.set(14, 1, 8, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
        npc(b, "resident", 12, 1, 9);

        // ---- shacks
        shack(b, 2, 12, 8, 17, 3, 8, 14, Direction.EAST, Direction.WEST, 31);
        b.bed(3, 1, 13, Blocks.BROWN_BED, Direction.SOUTH);
        b.barrel(7, 1, 16, Buildings.LOOT_HOUSE);
        b.set(3, 1, 16, Blocks.CRAFTING_TABLE);
        b.set(4, 1, 16, LandmarkKit.candles(Blocks.CANDLE, 1));
        b.hanging(5, 4, 14, Blocks.LANTERN);
        npc(b, "resident", 5, 1, 14);

        shack(b, 24, 12, 30, 17, 3, 24, 15, Direction.WEST, Direction.EAST, 47);
        b.bed(29, 1, 13, Blocks.LIGHT_GRAY_BED, Direction.SOUTH);
        b.bed(27, 1, 13, Blocks.LIGHT_GRAY_BED, Direction.SOUTH);
        b.barrel(29, 1, 16, Buildings.LOOT_HOUSE);
        b.set(26, 1, 16, Blocks.COMPOSTER);
        npc(b, "child", 27, 1, 15);
        // a lean-to of tarps against the east mound
        for (int z = 19; z <= 21; z++) b.set(29, 3, z, decor(ArchBlocks.BLUE_AWNING, Direction.WEST));
        b.fill(30, 1, 19, 30, 2, 21, up(ArchBlocks.WEATHERED_BEAM));

        // the bandits' hideout: a bigger shack with a lookout perch and their flag
        shack(b, 2, 20, 9, 26, 4, 9, 23, Direction.EAST, Direction.WEST, 61);
        b.chest(3, 1, 25, Direction.EAST, Buildings.LOOT_TREASURE);
        b.barrel(3, 1, 21, Buildings.LOOT_TAVERN);
        b.barrel(4, 1, 21, Buildings.LOOT_TAVERN);
        b.set(6, 1, 23, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(5, 1, 23, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.WEST, Half.BOTTOM));
        b.set(6, 2, 23, lantern(Blocks.LANTERN, false));
        b.bed(7, 1, 25, Blocks.RED_BED, Direction.WEST);
        b.fill(3, 6, 22, 3, 9, 22, up(ArchBlocks.WEATHERED_BEAM));
        b.set(3, 9, 21, LandmarkKit.banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        npc(b, "bandit", 5, 1, 21);
        npc(b, "bandit", 7, 1, 22);

        // the junk trader's awning stall
        for (int[] c : new int[][]{{21, 3}, {25, 3}, {21, 7}, {25, 7}}) b.fill(c[0], 1, c[1], c[0], 3, c[1], up(ArchBlocks.WEATHERED_BEAM));
        for (int z = 2; z <= 8; z++) awningsX(b, 21, 25, 4, z, ArchBlocks.GREEN_AWNING, z <= 5 ? Direction.NORTH : Direction.SOUTH);
        counterRow(b, 22, 24, 1, 3, Direction.NORTH);
        b.barrel(22, 1, 7, Buildings.LOOT_SHOP);
        b.barrel(24, 1, 7, Buildings.LOOT_SHOP);
        b.set(23, 1, 7, decor(ArchBlocks.GOODS_SHELF, Direction.NORTH));
        b.set(23, 2, 3, Blocks.FLOWER_POT);
        b.set(26, 1, 4, s(ArchBlocks.CARGO_CRATE));
        b.set(26, 1, 5, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        npc(b, "merchant", 23, 1, 5);

        // ---- the old oak with the brothers' treehouse and a cache hidden inside its trunk
        tree(b, 31, 1, 5, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 19, 5, 71, null);
        b.fill(30, 1, 4, 32, 16, 6, Blocks.OAK_LOG);
        b.fill(27, 8, 1, 35, 8, 9, s(ArchBlocks.WEATHERED_PLANKS));
        for (int[] q : new int[][]{{27, 1}, {35, 1}, {27, 9}, {35, 9}}) b.fill(q[0], 1, q[1], q[0], 7, q[1], up(ArchBlocks.WEATHERED_BEAM));
        b.walls(28, 9, 2, 34, 11, 8, ArchBlocks.WEATHERED_PLANKS.get());
        b.clear(29, 9, 3, 33, 11, 7);
        b.fill(30, 9, 4, 32, 11, 6, Blocks.OAK_LOG);
        Mats tm = mats(BuildStyle.VILLAGE, 7);
        tm.roof = s(ArchBlocks.THATCH);
        tm.roofStairs = s(ArchBlocks.THATCH_STAIRS);
        tm.roofSlab = s(ArchBlocks.THATCH_SLAB);
        tm.roofKind = Arch.RoofKind.GABLE;
        tm.eaveBrackets = false;
        Arch.gable(b, tm, 28, 2, 34, 8, 12, true, 1, s(ArchBlocks.WEATHERED_PLANKS));
        LandmarkKit.line(b, 27, 9, 1, 35, 1, Blocks.SPRUCE_FENCE);
        windowsX(b, 30, 32, 9, 8, 2, Blocks.GLASS_PANE);
        b.set(34, 10, 5, connected(Blocks.GLASS_PANE, Direction.Axis.Z));
        b.clear(31, 9, 2, 31, 10, 2);
        b.clear(31, 8, 3, 31, 8, 3);
        b.ladderUp(31, 1, 3, 8, Direction.NORTH);
        b.chest(29, 9, 7, Direction.EAST, Buildings.LOOT_HOUSE);
        b.bed(33, 9, 3, Blocks.ORANGE_BED, Direction.SOUTH);
        b.set(29, 9, 3, Blocks.CRAFTING_TABLE);
        b.set(29, 10, 3, LandmarkKit.candles(Blocks.CANDLE, 2));
        for (int y = 9; y <= 17; y++) b.set(35, y, 2, s(Blocks.SPRUCE_FENCE));
        b.set(35, 17, 1, LandmarkKit.banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        b.set(35, 16, 1, LandmarkKit.banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        npc(b, "child", 33, 9, 6);
        // the hollow in the trunk (hint: one stripped log on the south side)
        b.set(31, 1, 6, Blocks.STRIPPED_OAK_LOG);
        b.clear(31, 2, 5, 31, 2, 5);
        b.chest(31, 1, 5, Direction.SOUTH, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 31, 1, 5);
        b.marker("legend", 18, 1, 20, tag("story", "gray_terminal"));
    }

    // ============================================================== Syrup Village: the hilltop mansion

    /**
     * The heiress's mansion on the hill: a symmetrical white two-storey house on a pale masonry plinth with quoins, a
     * string course, tall shuttered windows, a slate hip roof with eave brackets, a pedimented centre bay and two
     * smoking chimneys; a marble-columned portico carrying a balcony; a walled garden with an iron railing, gate
     * piers, a gravel drive between hedges, a fountain and flower beds; and the old tree whose branch reaches the
     * bedroom window. Inside: hall with the grand stair, dining room, parlour, kitchen; upstairs the bedroom, guest
     * room and study, whose shelves hide the butler's secret den.
     */
    static void hilltopMansion(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 33 x 29
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 28);
        BlockState pale = s(ArchBlocks.PALE_MASONRY), paleSt = s(ArchBlocks.PALE_MASONRY_STAIRS), white = s(ArchBlocks.WHITE_PLASTER);
        BlockState col = up(ArchBlocks.MARBLE_COLUMN), floor = s(Blocks.DARK_OAK_PLANKS), brass = s(ArchBlocks.BRASS_LANTERN);
        Mats m = mats(BuildStyle.PALACE, b.seed);
        m.wall = white;
        m.upperWall = white;
        m.window = s(ArchBlocks.MULLION_WINDOW);
        m.shutter = ArchBlocks.BLUE_SHUTTER.get();
        m.planters = true;
        m.roof = s(ArchBlocks.SLATE_ROOF_TILES);
        m.roofStairs = s(ArchBlocks.SLATE_ROOF_TILE_STAIRS);
        m.roofSlab = s(ArchBlocks.SLATE_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.HIP;
        m.eaveBrackets = true;
        m.chimney = pale;

        // ---- the garden: a masonry wall with an iron railing, gate piers, the drive, hedges, a fountain, beds
        for (int x = 0; x < w; x++) b.set(x, 1, 0, pale);
        for (int z = 1; z < d; z++) {
            b.set(0, 1, z, pale);
            b.set(w - 1, 1, z, pale);
        }
        LandmarkKit.line(b, 0, 2, 0, w - 1, 0, Blocks.IRON_BARS);
        LandmarkKit.line(b, 0, 2, 1, 0, d - 1, Blocks.IRON_BARS);
        LandmarkKit.line(b, w - 1, 2, 1, w - 1, d - 1, Blocks.IRON_BARS);
        for (int x = 0; x < w; x += 6) {
            b.fill(x, 1, 0, x, 3, 0, pale);
            b.set(x, 4, 0, bot(ArchBlocks.PALE_MASONRY_SLAB));
        }
        b.clear(15, 1, 0, 17, 2, 0);
        for (int px : new int[]{14, 18}) {
            b.fill(px, 1, 0, px, 4, 0, pale);
            b.set(px, 5, 0, Arch.lantern(brass, false));
        }
        b.fill(15, 0, 0, 17, 0, 10, s(Blocks.GRAVEL));
        b.fill(14, 0, 1, 14, 0, 9, s(Blocks.SMOOTH_STONE));
        b.fill(18, 0, 1, 18, 0, 9, s(Blocks.SMOOTH_STONE));
        for (int z = 2; z <= 5; z++) {
            b.set(12, 1, z, LandmarkKit.leaves(Blocks.AZALEA_LEAVES));
            b.set(20, 1, z, LandmarkKit.leaves(Blocks.AZALEA_LEAVES));
        }
        // fountain on the west lawn
        LandmarkKit.disc(b, 6, 5, 0, 2.6, pale);
        LandmarkKit.disc(b, 6, 5, 0, 1.6, s(Blocks.WATER));
        LandmarkKit.ring(b, 6, 5, 1, 2, bot(ArchBlocks.PALE_MASONRY_SLAB));
        b.set(6, 0, 5, pale);
        b.fill(6, 1, 5, 6, 2, 5, col);
        b.set(6, 3, 5, Blocks.POTTED_RED_TULIP);
        // flower beds on the east lawn
        for (int x = 22; x <= 29; x++) {
            b.set(x, 0, 4, s(Blocks.ROOTED_DIRT));
            b.set(x, 1, 4, (x & 1) == 0 ? s(Blocks.POPPY) : s(Blocks.ALLIUM));
            b.set(x, 0, 7, s(Blocks.ROOTED_DIRT));
            b.set(x, 1, 7, (x & 1) == 0 ? s(Blocks.PINK_TULIP) : s(Blocks.WHITE_TULIP));
        }
        for (int x = 2; x <= 9; x++) if (x < 4 || x > 8) b.set(x, 1, 8, (x & 1) == 0 ? s(Blocks.RED_TULIP) : s(Blocks.PINK_TULIP));
        bench(b, 24, 1, 9, Direction.NORTH);
        npc(b, "guard", 16, 1, 2);
        npc(b, "farmer", 25, 1, 6);

        // ---- the house (x 4..26, z 11..24): ground floor y 1..4, upper floor 6..9, hip roof from 10
        int x1 = 4, x2 = 26, z1 = 11, z2 = 24, cx = 15;
        b.fill(x1, 0, z1, x2, 0, z2, pale);
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, floor);
        box(b, x1, 1, z1, x2, 9, z2, white);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 9, z2 - 1);
        ring(b, x1, z1, x2, z2, 1, pale);
        plinth(b, x1, z1, x2, z2, 1, paleSt);
        ring(b, x1, z1, x2, z2, 5, pale);
        cornice(b, x1, z1, x2, z2, 5, paleSt, null);
        b.fill(x1 + 1, 5, z1 + 1, x2 - 1, 5, z2 - 1, floor);
        for (int[] q : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) for (int y = 1; y <= 9; y += 2) b.set(q[0], y, q[1], pale);
        // the pedimented centre bay, projecting one block
        b.fill(12, 1, z1 - 1, 18, 9, z1 - 1, white);
        b.fill(12, 1, z1 - 1, 18, 1, z1 - 1, pale);
        b.fill(12, 5, z1 - 1, 18, 5, z1 - 1, pale);
        for (int y = 1; y <= 9; y += 2) {
            b.set(12, y, z1 - 1, pale);
            b.set(18, y, z1 - 1, pale);
        }
        int top = Arch.hip(b, m, x1, z1, x2, z2, 10, 1);
        Mats gm = mats(BuildStyle.PALACE, b.seed);
        gm.roof = m.roof;
        gm.roofStairs = m.roofStairs;
        gm.roofSlab = m.roofSlab;
        gm.roofKind = Arch.RoofKind.GABLE;
        gm.eaveBrackets = false;
        Arch.gable(b, gm, 12, z1 - 1, 18, z1 + 3, 10, false, 1, white);
        Arch.window(b, m, cx, 11, z1 - 1, Direction.NORTH, 1, false);
        Arch.chimney(b, m, x1, 17, 1, top + 1, true);
        Arch.chimney(b, m, x2, 21, 1, top + 1, true);
        // windows on every side
        for (int x : new int[]{6, 9, 21, 24}) {
            Arch.window(b, m, x, 2, z1, Direction.NORTH, 3, true);
            Arch.window(b, m, x, 7, z1, Direction.NORTH, 2, true);
            Arch.window(b, m, x, 2, z2, Direction.SOUTH, 3, true);
            Arch.window(b, m, x, 7, z2, Direction.SOUTH, 2, true);
        }
        for (int x : new int[]{13, 17}) Arch.window(b, m, x, 7, z1 - 1, Direction.NORTH, 2, true);
        for (int x : new int[]{12, 15, 18}) Arch.window(b, m, x, 7, z2, Direction.SOUTH, 2, false);
        for (int z : new int[]{14, 20}) {
            Arch.window(b, m, x1, 2, z, Direction.WEST, 3, true);
            Arch.window(b, m, x1, 7, z, Direction.WEST, 2, true);
            Arch.window(b, m, x2, 2, z, Direction.EAST, 3, true);
            Arch.window(b, m, x2, 7, z, Direction.EAST, 2, true);
        }
        Arch.window(b, m, x2, 7, 16, Direction.EAST, 2, true);
        // the portico: four marble columns, an entablature, the balcony and its door
        b.fill(11, 0, 6, 19, 0, 9, pale);
        for (int x : new int[]{12, 14, 16, 18}) b.fill(x, 1, 6, x, 4, 6, col);
        b.fill(11, 5, 6, 19, 5, 9, pale);
        for (int x = 11; x <= 19; x++) b.set(x, 4, 7, Arch.stair(paleSt, Direction.SOUTH, Half.TOP));
        LandmarkKit.line(b, 11, 6, 6, 19, 6, ArchBlocks.PALE_MASONRY_WALL.get());
        LandmarkKit.line(b, 11, 6, 7, 11, 9, ArchBlocks.PALE_MASONRY_WALL.get());
        LandmarkKit.line(b, 19, 6, 7, 19, 9, ArchBlocks.PALE_MASONRY_WALL.get());
        b.bigDoor(cx, 1, z1 - 1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.clear(cx, 1, z1, cx, 3, z1);
        for (int x : new int[]{cx - 1, cx + 1}) b.fill(x, 2, z1 - 1, x, 3, z1 - 1, Arch.along(m.window, Direction.Axis.X));
        b.door(cx, 6, z1 - 1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.clear(cx, 6, z1, cx, 7, z1);
        b.hanging(cx, 4, 8, ArchBlocks.BRASS_LANTERN.get());
        for (int x : new int[]{13, 17}) b.set(x, 6, 8, decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));

        // ---- ground floor: hall with the grand stair, dining room (west), parlour and kitchen (east)
        b.fill(11, 1, z1 + 1, 11, 4, z2 - 1, white);
        b.fill(19, 1, z1 + 1, 19, 4, z2 - 1, white);
        b.fill(20, 1, 18, x2 - 1, 4, 18, white);
        b.clear(11, 1, 14, 11, 3, 15);
        b.clear(19, 1, 14, 19, 3, 15);
        b.clear(19, 1, 21, 19, 3, 21);
        LandmarkKit.carpet(b, cx - 1, 1, z1 + 1, cx + 1, 17, Blocks.RED_CARPET);
        flightX(b, cx - 1, cx + 1, 1, 22, Direction.NORTH, 5, s(Blocks.DARK_OAK_STAIRS), floor);
        for (int x : new int[]{12, 18}) {
            b.set(x, 1, 13, Blocks.POTTED_FERN);
            b.set(x, 1, 23, Blocks.POTTED_FERN);
        }
        b.hanging(cx, 4, 14, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "waiter", cx + 2, 1, 15);
        // dining room with the fireplace
        LandmarkKit.longTableZ(b, 13, 21, 1, 8, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
        b.set(x1 + 1, 1, 17, campfire(false));
        b.set(x1 + 1, 1, 16, s(Blocks.BRICKS));
        b.set(x1 + 1, 1, 18, s(Blocks.BRICKS));
        b.fill(x1 + 1, 2, 16, x1 + 1, 2, 18, Arch.stair(s(Blocks.BRICK_STAIRS), Direction.EAST, Half.TOP));
        b.barrel(5, 1, 23, Buildings.LOOT_TAVERN);
        b.set(5, 1, 12, s(Blocks.BOOKSHELF));
        b.set(10, 1, 23, decor(ArchBlocks.GOODS_SHELF, Direction.NORTH));
        b.hanging(8, 4, 15, ArchBlocks.BRASS_LANTERN.get());
        b.hanging(8, 4, 20, ArchBlocks.BRASS_LANTERN.get());
        // parlour
        LandmarkKit.carpet(b, 20, 1, 12, 25, 17, Blocks.GREEN_CARPET);
        b.set(22, 1, 14, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(22, 2, 14, Blocks.POTTED_RED_TULIP);
        b.set(21, 1, 14, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.WEST, Half.BOTTOM));
        b.set(23, 1, 14, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.EAST, Half.BOTTOM));
        b.fill(25, 1, 12, 25, 2, 13, s(Blocks.BOOKSHELF));
        b.set(20, 1, 12, s(Blocks.NOTE_BLOCK));
        b.set(21, 1, 12, s(Blocks.NOTE_BLOCK));
        b.hanging(22, 4, 15, ArchBlocks.BRASS_LANTERN.get());
        // kitchen
        LandmarkKit.kitchenX(b, 20, 1, 23, Direction.NORTH, Buildings.LOOT_HOUSE);
        b.barrel(25, 1, 23, Buildings.LOOT_TAVERN);
        b.barrel(25, 1, 22, Buildings.LOOT_TAVERN);
        b.set(22, 1, 20, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(25, 1, 19, decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        b.hanging(22, 4, 21, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "cook", 23, 1, 21);

        // ---- upper floor: landing round the stairwell, bedroom (east front), guest room (east back), study (west)
        LandmarkKit.line(b, cx - 2, 6, 18, cx - 2, 22, Blocks.DARK_OAK_FENCE);
        LandmarkKit.line(b, cx + 2, 6, 18, cx + 2, 22, Blocks.DARK_OAK_FENCE);
        LandmarkKit.line(b, cx - 1, 6, 22, cx + 1, 22, Blocks.DARK_OAK_FENCE);
        b.fill(11, 6, z1 + 1, 11, 9, z2 - 1, white);
        b.fill(19, 6, z1 + 1, 19, 9, z2 - 1, white);
        b.fill(20, 6, 19, x2 - 1, 9, 19, white);
        b.fill(x1 + 1, 6, 18, 10, 9, 18, s(Blocks.BOOKSHELF));
        b.door(11, 6, 13, Blocks.DARK_OAK_DOOR, Direction.WEST);
        b.door(19, 6, 13, Blocks.DARK_OAK_DOOR, Direction.EAST);
        b.door(19, 6, 21, Blocks.DARK_OAK_DOOR, Direction.EAST);
        LandmarkKit.carpet(b, 12, 6, z1 + 1, 18, 17, Blocks.RED_CARPET);
        b.hanging(cx, 9, 14, ArchBlocks.BRASS_LANTERN.get());
        b.fill(x1 + 1, 10, z1 + 1, x2 - 1, 10, z2 - 1, floor);
        // the heiress's bedroom, its window toward the old tree
        LandmarkKit.carpet(b, 20, 6, 12, 25, 18, Blocks.PINK_CARPET);
        b.bed(23, 6, 15, Blocks.PINK_BED, Direction.EAST);
        for (int[] q : new int[][]{{23, 14}, {23, 16}, {25, 14}, {25, 16}}) b.fill(q[0], 6, q[1], q[0], 7, q[1], s(Blocks.BIRCH_FENCE));
        b.fill(23, 8, 14, 25, 8, 16, s(Blocks.WHITE_WOOL));
        b.set(20, 6, 12, s(Blocks.BOOKSHELF));
        b.set(21, 6, 12, LandmarkKit.facing(Blocks.LOOM, Direction.SOUTH));
        b.chest(20, 6, 18, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(25, 6, 18, Blocks.POTTED_ALLIUM);
        b.hanging(22, 9, 16, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "noble", 21, 6, 16);
        npc(b, "doctor", 21, 6, 13);
        // guest room
        b.bed(21, 6, 22, Blocks.WHITE_BED, Direction.SOUTH);
        b.bed(24, 6, 22, Blocks.WHITE_BED, Direction.SOUTH);
        b.barrel(25, 6, 20, Buildings.LOOT_HOUSE);
        b.hanging(22, 9, 21, ArchBlocks.BRASS_LANTERN.get());
        // the study, its shelves hiding the den behind (one chiseled shelf is the panel)
        b.set(8, 6, 18, LandmarkKit.facing(Blocks.CHISELED_BOOKSHELF, Direction.NORTH));
        b.set(6, 6, 13, LandmarkKit.facing(Blocks.LECTERN, Direction.SOUTH));
        b.set(8, 6, 14, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(8, 6, 13, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.NORTH, Half.BOTTOM));
        b.set(8, 7, 14, LandmarkKit.candles(Blocks.CANDLE, 3));
        LandmarkKit.carpet(b, 5, 6, 15, 10, 16, Blocks.BROWN_CARPET);
        b.hanging(8, 9, 15, ArchBlocks.BRASS_LANTERN.get());
        // the hidden den: the butler's disguise kept here
        b.chest(5, 6, 23, Direction.NORTH, Buildings.LOOT_TREASURE);
        b.set(6, 6, 23, Blocks.LECTERN);
        b.set(8, 6, 23, Blocks.SMITHING_TABLE);
        b.set(9, 7, 23, LandmarkKit.banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        b.set(5, 6, 20, s(Blocks.BARREL));
        b.set(7, 9, 21, Arch.lantern(s(Blocks.SOUL_LANTERN), true));
        mark(b, "treasure", 5, 6, 23);

        // ---- the old tree by the bedroom window, one branch reaching for the sill
        tree(b, 30, 1, 16, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 8, 2, 91, null);
        b.fill(28, 7, 16, 29, 7, 16, LandmarksB.beam(Blocks.OAK_LOG, Direction.Axis.X));
        b.set(27, 7, 16, LandmarkKit.leaves(Blocks.OAK_LEAVES));
    }

    // ============================================================== Cocoyasi: the tangerine grove

    /**
     * The mother's orchard cottage on the hill: a white-plaster cottage on a fieldstone footing under a terracotta
     * gable with a smoking chimney, green shutters and window planters, a hooded door with a bench, a harvest shed of
     * crates against its side; the fenced tangerine orchard in rows with a ladder against a tree and the day's
     * picking; and her grave on the little rise looking out to sea. The family's savings lie in a chest under the rug.
     */
    static void tangerineGrove(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 25 x 21
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 16);
        BlockState fruit = s(Blocks.ORANGE_CONCRETE), field = s(ArchBlocks.FIELDSTONE);
        // ---- the orchard: fence, gate, paths, two rows of mikan trees
        outline(b, 13, 1, 1, w - 1, d - 2, Blocks.OAK_FENCE);
        b.set(13, 1, 10, gate(Blocks.OAK_FENCE_GATE, Direction.EAST, true));
        b.fill(18, 0, 2, 18, 0, d - 3, s(Blocks.DIRT_PATH));
        b.fill(14, 0, 10, w - 2, 0, 10, s(Blocks.DIRT_PATH));
        int i = 0;
        for (int tx : new int[]{15, 21})
            for (int tz : new int[]{3, 7, 13, 17}) {
                b.set(tx, 0, tz, s(Blocks.ROOTED_DIRT));
                fruitTree(b, tx, 1, tz, 200 + i++ * 37, fruit);
            }
        b.ladderUp(14, 1, 7, 3, Direction.WEST);
        b.set(14, 0, 7, s(Blocks.COARSE_DIRT));
        // the day's picking at the crossing
        b.barrel(19, 1, 9, Buildings.LOOT_HOUSE);
        b.set(17, 1, 9, s(ArchBlocks.CARGO_CRATE));
        b.set(17, 2, 9, s(Blocks.ORANGE_CARPET));
        b.set(19, 1, 11, s(ArchBlocks.CARGO_CRATE));
        b.set(19, 2, 11, s(Blocks.ORANGE_CARPET));
        b.set(17, 1, 11, s(Blocks.COMPOSTER));
        npc(b, "farmer", 18, 1, 6);

        // ---- the cottage (x 1..9, z 4..12), door north
        Mats m = mats(BuildStyle.VILLAGE, b.seed);
        m.wall = s(ArchBlocks.WHITE_PLASTER);
        m.upperWall = m.wall;
        m.frame = up(ArchBlocks.OAK_BEAM);
        m.base = field;
        m.floor = s(Blocks.SPRUCE_PLANKS);
        m.roof = s(ArchBlocks.TERRACOTTA_ROOF_TILES);
        m.roofStairs = s(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS);
        m.roofSlab = s(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.GABLE;
        m.shutter = ArchBlocks.GREEN_SHUTTER.get();
        m.planters = true;
        m.eaveBrackets = true;
        m.chimney = field;
        int x1 = 1, x2 = 9, z1 = 4, z2 = 12;
        b.fill(x1, 0, z1, x2, 0, z2, field);
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, m.floor);
        box(b, x1, 1, z1, x2, 3, z2, m.wall);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 3, z2 - 1);
        ring(b, x1, z1, x2, z2, 1, field);
        beamRing(b, x1, z1, x2, z2, 4, ArchBlocks.OAK_BEAM);
        for (int[] q : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(q[0], 1, q[1], q[0], 4, q[1], m.frame);
        int top = Arch.gable(b, m, x1, z1, x2, z2, 5, true, 1, m.wall);
        Arch.chimney(b, m, x1, 10, 1, top + 1, true);
        b.bigDoor(5, 1, z1, Blocks.OAK_DOOR, Direction.NORTH);
        for (int k = -1; k <= 1; k++) b.set(5 + k, 4, z1 - 1, Arch.slab(m.roofSlab, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
        b.set(4, 3, z1 - 1, Arch.stair(m.roofStairs, Direction.SOUTH, Half.TOP));
        b.set(6, 3, z1 - 1, Arch.stair(m.roofStairs, Direction.SOUTH, Half.TOP));
        b.set(5, 0, z1 - 1, Arch.stair(s(ArchBlocks.FIELDSTONE_STAIRS), Direction.SOUTH, Half.BOTTOM));
        Arch.wallLantern(b, m, 7, 2, z1, Direction.NORTH);
        bench(b, 2, 1, z1 - 1, Direction.NORTH);
        b.set(8, 1, z1 - 1, Blocks.POTTED_ORANGE_TULIP);
        Arch.window(b, m, 3, 2, z1, Direction.NORTH, 1, true);
        Arch.window(b, m, 3, 2, z2, Direction.SOUTH, 1, true);
        Arch.window(b, m, 7, 2, z2, Direction.SOUTH, 1, true);
        Arch.window(b, m, x2, 2, 7, Direction.EAST, 1, true);
        Arch.window(b, m, x1, 2, 7, Direction.WEST, 1, true);
        Arch.window(b, m, x1, 6, 8, Direction.WEST, 1, false);
        Arch.window(b, m, x2, 6, 8, Direction.EAST, 1, false);
        // inside: kitchen along the back, the family table, two little beds, the rug over the savings
        LandmarkKit.kitchenX(b, 2, 1, z2 - 1, Direction.NORTH, Buildings.LOOT_HOUSE);
        b.set(5, 1, 8, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(5, 2, 8, Blocks.POTTED_ORANGE_TULIP);
        b.set(4, 1, 8, Arch.stair(s(Blocks.OAK_STAIRS), Direction.WEST, Half.BOTTOM));
        b.set(6, 1, 8, Arch.stair(s(Blocks.OAK_STAIRS), Direction.EAST, Half.BOTTOM));
        b.bed(8, 1, 5, Blocks.ORANGE_BED, Direction.SOUTH);
        b.bed(8, 1, 8, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.set(8, 1, 7, s(Blocks.SPRUCE_PLANKS));
        b.set(8, 2, 7, LandmarkKit.candles(Blocks.CANDLE, 1));
        b.set(2, 1, 5, s(Blocks.BOOKSHELF));
        b.set(2, 2, 5, Blocks.POTTED_CACTUS);
        b.fill(x1 + 1, 4, 8, x2 - 1, 4, 8, LandmarksB.beam(ArchBlocks.OAK_BEAM, Direction.Axis.X));
        b.hanging(5, 3, 8, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "resident", 3, 1, 8);
        b.chest(6, 0, 10, Direction.NORTH, Buildings.LOOT_TREASURE);
        LandmarkKit.carpet(b, 5, 1, 9, 7, 10, Blocks.CYAN_CARPET);
        mark(b, "treasure", 6, 0, 10);
        // the harvest shed against the east wall
        for (int z : new int[]{6, 10}) b.fill(12, 1, z, 12, 2, z, up(ArchBlocks.OAK_BEAM));
        Arch.leanTo(b, m, 10, 5, 12, 11, 4, Direction.EAST);
        for (int z = 6; z <= 10; z++) {
            b.set(11, 1, z, s(ArchBlocks.CARGO_CRATE));
            if ((z & 1) == 0) b.set(11, 2, z, s(Blocks.ORANGE_CARPET));
            else b.set(11, 2, z, decor(ArchBlocks.CARGO_SACKS, Direction.EAST));
        }
        b.set(10, 1, 8, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));

        // ---- the grave on the little rise
        b.fill(2, 1, 15, 8, 1, 19, s(Blocks.GRASS_BLOCK));
        b.fill(3, 2, 16, 7, 2, 18, s(Blocks.GRASS_BLOCK));
        b.fill(5, 3, 17, 5, 5, 17, s(Blocks.OAK_FENCE));
        b.set(5, 4, 17, LandmarkKit.connect(Blocks.OAK_FENCE, false, true, false, true));
        b.set(4, 4, 17, LandmarkKit.connect(Blocks.OAK_FENCE, false, true, false, false));
        b.set(6, 4, 17, LandmarkKit.connect(Blocks.OAK_FENCE, false, false, false, true));
        b.set(5, 3, 16, LandmarkKit.candles(Blocks.ORANGE_CANDLE, 3));
        b.set(4, 3, 16, s(Blocks.POPPY));
        b.set(6, 3, 16, s(Blocks.OXEYE_DAISY));
        b.set(3, 3, 18, s(Blocks.CORNFLOWER));
        b.set(7, 3, 18, s(Blocks.AZURE_BLUET));
        b.set(4, 2, 15, s(Blocks.POPPY));
        b.set(7, 2, 15, s(Blocks.DANDELION));
        post(b, 2, 2, 19, s(Blocks.OAK_FENCE), 2, s(ArchBlocks.BRASS_LANTERN));
        npc(b, "child", 5, 2, 15);
        b.marker("legend", 5, 3, 17, tag("story", "tangerine_grove"));
    }

    // ============================================================== Flevance: the White City

    /** Materials of the white town: white plaster on pale masonry, slate roofs, frosted glazing, blue shutters. */
    private static Mats whiteMats(long seed) {
        Mats m = mats(BuildStyle.PALACE, seed);
        m.wall = s(ArchBlocks.WHITE_PLASTER);
        m.upperWall = m.wall;
        m.base = s(ArchBlocks.PALE_MASONRY);
        m.frame = s(ArchBlocks.PALE_MASONRY);
        m.trim = s(ArchBlocks.PALE_MASONRY);
        m.window = s(Blocks.WHITE_STAINED_GLASS_PANE);
        m.shutter = ArchBlocks.BLUE_SHUTTER.get();
        m.planters = false;
        m.sill = s(ArchBlocks.PALE_MASONRY_STAIRS);
        m.roof = s(ArchBlocks.SLATE_ROOF_TILES);
        m.roofStairs = s(ArchBlocks.SLATE_ROOF_TILE_STAIRS);
        m.roofSlab = s(ArchBlocks.SLATE_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.GABLE;
        m.eaveBrackets = false;
        return m;
    }

    /** Snow lying on the highest block of each column over a rectangle (roofs, ridges), from y down to yMin. */
    private static void snowCap(Builder b, int x1, int z1, int x2, int z2, int yTop, int yMin, int salt) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (b.rand(salt + x * 7 + z * 13, 4) == 0) continue;
                b.set(x, yTop + 1, z, s(Blocks.SNOW));
            }
    }

    /**
     * An abandoned white house (door north): white plaster on a pale masonry footing, a slate gable with snow along
     * its ridge, frosted windows with blue shutters; a burnt one has scorched patches, a broken roof and a gap in the
     * wall. Interior: bed, table, cobwebs.
     */
    private static void whiteHouse(Builder b, int x1, int z1, int x2, int z2, int salt, boolean burnt) {
        Mats m = whiteMats(salt);
        b.fill(x1, 0, z1, x2, 0, z2, m.base);
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, s(Blocks.BIRCH_PLANKS));
        box(b, x1, 1, z1, x2, 3, z2, m.wall);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 4, z2 - 1);
        ring(b, x1, z1, x2, z2, 1, m.base);
        ring(b, x1, z1, x2, z2, 4, m.base);
        int top = Arch.gable(b, m, x1, z1, x2, z2, 5, true, 1, m.wall);
        int xm = (x1 + x2) / 2;
        for (int x = x1 - 1; x <= x2 + 1; x++) if (b.rand(salt + x, 3) != 0) b.set(x, top + 1, (z1 + z2) / 2, s(Blocks.SNOW));
        b.bigDoor(xm, 1, z1, Blocks.BIRCH_DOOR, Direction.NORTH);
        Arch.window(b, m, xm - 2, 2, z1, Direction.NORTH, 1, true);
        Arch.window(b, m, xm + 2, 2, z1, Direction.NORTH, 1, true);
        Arch.window(b, m, x1, 2, (z1 + z2) / 2, Direction.WEST, 1, true);
        Arch.window(b, m, x2, 2, (z1 + z2) / 2, Direction.EAST, 1, true);
        Arch.window(b, m, x1, 6, (z1 + z2) / 2, Direction.WEST, 1, false);
        Arch.window(b, m, x2, 6, (z1 + z2) / 2, Direction.EAST, 1, false);
        b.bed(x1 + 1, 1, z2 - 2, Blocks.WHITE_BED, Direction.SOUTH);
        b.set(x2 - 1, 1, z2 - 1, s(Blocks.COBWEB));
        b.set(x2 - 1, 1, z1 + 1, s(Blocks.FLOWER_POT));
        b.set(xm + 1, 1, z2 - 2, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        if (burnt) {
            for (int x = x1; x <= x2; x++)
                for (int y = 1; y <= 3; y++)
                    for (int z : new int[]{z1, z2}) if (b.rand(salt + x * 31 + y * 7 + z, 4) == 0) b.set(x, y, z, s(Blocks.BLACKSTONE));
            for (int x = x2 - 3; x <= x2; x++) for (int y = 5; y <= top + 1; y++) for (int z = z1 - 1; z <= z2 + 1; z++) b.set(x, y, z, s(Blocks.AIR));
            b.clear(x2, 2, z2 - 2, x2, 3, z2 - 1);
            for (int x = x2 - 3; x <= x2 - 1; x++) b.set(x, 4, (z1 + z2) / 2, LandmarksB.beam(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
            b.set(x2 - 2, 1, z2 - 1, s(Blocks.SNOW));
        }
    }

    /**
     * The white town, abandoned behind a quarantine barricade: a square with a dry fountain and soul-lit lamps; the
     * two-storey hospital with its red cross, wards, the doctor's office and a roof partly fallen in; the church with
     * buttresses, lancet windows, a rose window and a snow-capped bell tower spire; white houses (two burnt) and the
     * town's graves; and the white-lead mine: a headframe over the adit, the gallery and a sealed side gallery holding
     * the town's last records.
     */
    static void whiteCity(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 47 x 47
        LandmarkKit.site(b, Blocks.SNOW_BLOCK, Blocks.STONE, 34);
        patchGround(b, 13, 0, Blocks.SNOW_BLOCK, Blocks.CALCITE, Blocks.WHITE_CONCRETE, Blocks.SMOOTH_QUARTZ);
        BlockState pale = s(ArchBlocks.PALE_MASONRY), paleSt = s(ArchBlocks.PALE_MASONRY_STAIRS), white = s(ArchBlocks.WHITE_PLASTER);
        // streets
        b.fill(21, 0, 0, 25, 0, d - 1, s(Blocks.QUARTZ_BRICKS));
        b.fill(0, 0, 21, w - 1, 0, 25, s(Blocks.QUARTZ_BRICKS));
        for (int i = 0; i < w; i++) {
            b.set(21, 0, i, pale);
            b.set(25, 0, i, pale);
            b.set(i, 0, 21, pale);
            b.set(i, 0, 25, pale);
        }
        // quarantine barricade along the north edge: planks, bars, a barred gate with a skull
        for (int x = 0; x < w; x++) {
            if (x >= 20 && x <= 26) continue;
            if (x % 4 == 0) b.fill(x, 1, 0, x, 3, 0, up(ArchBlocks.WEATHERED_BEAM));
            else {
                b.set(x, 1, 0, s(ArchBlocks.WEATHERED_PLANKS));
                b.set(x, 2, 0, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
            }
        }
        for (int x : new int[]{20, 26}) b.fill(x, 1, 0, x, 4, 0, up(ArchBlocks.WEATHERED_BEAM));
        b.fill(20, 5, 0, 26, 5, 0, LandmarksB.beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        for (int x = 21; x <= 25; x += 2) b.set(x, 1, 1, s(Blocks.SPRUCE_FENCE));
        b.set(23, 6, 0, s(Blocks.SKELETON_SKULL));
        b.set(22, 4, 0, s(Blocks.CHAIN));

        // ---- the square: a dry fountain with a white statue, soul-lit lamps
        int c = 23;
        LandmarkKit.disc(b, c, c, 0, 4.5, pale);
        LandmarkKit.ring(b, c, c, 1, 4, bot(ArchBlocks.PALE_MASONRY_SLAB));
        LandmarkKit.disc(b, c, c, 0, 3.5, s(Blocks.CALCITE));
        b.fill(c, 1, c, c, 3, c, up(ArchBlocks.MARBLE_COLUMN));
        b.set(c, 4, c, s(Blocks.CHISELED_QUARTZ_BLOCK));
        b.set(c, 5, c, Arch.stair(s(Blocks.QUARTZ_STAIRS), Direction.SOUTH, Half.BOTTOM));
        snowCap(b, c - 3, c - 3, c + 3, c + 3, 0, 0, 5);
        for (int[] o : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) post(b, c + o[0], 1, c + o[1], s(ArchBlocks.PALE_MASONRY_WALL), 3, s(Blocks.SOUL_LANTERN));
        npc(b, "bandit", c + 2, 1, c - 5);
        // white trees along the avenue
        for (int[] t : new int[][]{{19, 6}, {19, 13}, {27, 6}}) tree(b, t[0], 1, t[1], Blocks.BIRCH_LOG, Blocks.WHITE_WOOL, 5, 2, t[0] * 7 + t[1], null);

        // ---- the hospital (x 2..17, z 3..17): two storeys, hip roof partly fallen in, door and red cross east
        Mats hm = whiteMats(b.seed + 1);
        int hx1 = 2, hx2 = 17, hz1 = 3, hz2 = 17;
        b.fill(hx1, 0, hz1, hx2, 0, hz2, pale);
        b.fill(hx1 + 1, 0, hz1 + 1, hx2 - 1, 0, hz2 - 1, s(Blocks.SMOOTH_QUARTZ));
        box(b, hx1, 1, hz1, hx2, 7, hz2, white);
        b.clear(hx1 + 1, 1, hz1 + 1, hx2 - 1, 7, hz2 - 1);
        ring(b, hx1, hz1, hx2, hz2, 1, pale);
        plinth(b, hx1, hz1, hx2, hz2, 1, paleSt);
        ring(b, hx1, hz1, hx2, hz2, 4, pale);
        b.fill(hx1 + 1, 4, hz1 + 1, hx2 - 1, 4, hz2 - 1, s(Blocks.BIRCH_PLANKS));
        for (int[] q : new int[][]{{hx1, hz1}, {hx2, hz1}, {hx1, hz2}, {hx2, hz2}}) b.fill(q[0], 1, q[1], q[0], 7, q[1], pale);
        hm.roofKind = Arch.RoofKind.HIP;
        int htop = Arch.hip(b, hm, hx1, hz1, hx2, hz2, 8, 1);
        b.fill(hx1 + 1, 8, hz1 + 1, hx2 - 1, 8, hz2 - 1, s(Blocks.BIRCH_PLANKS));
        // the roof fallen in over the north-west ward, snow drifted onto the floor below
        for (int x = 4; x <= 8; x++)
            for (int z = 5; z <= 9; z++) {
                boolean edge = x == 4 || x == 8 || z == 5 || z == 9;
                if (edge && b.rand(x * 17 + z * 5, 3) == 0) continue;
                b.clear(x, 8, z, x, htop, z);
                b.set(x, 5, z, b.rand(x * 11 + z, 3) == 0 ? s(Blocks.SNOW_BLOCK) : s(Blocks.SNOW));
            }
        for (int z : new int[]{6, 8}) b.fill(4, 8, z, 8, 8, z, LandmarksB.beam(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
        b.set(6, 5, 7, LandmarksB.beam(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z));
        for (int z = hz1 + 2; z <= hz2 - 2; z += 3) {
            Arch.window(b, hm, hx1, 2, z, Direction.WEST, 2, true);
            Arch.window(b, hm, hx1, 5, z, Direction.WEST, 2, true);
        }
        for (int x = hx1 + 2; x <= hx2 - 2; x += 3) {
            Arch.window(b, hm, x, 2, hz1, Direction.NORTH, 2, true);
            Arch.window(b, hm, x, 5, hz1, Direction.NORTH, 2, true);
            Arch.window(b, hm, x, 5, hz2, Direction.SOUTH, 2, true);
        }
        Arch.window(b, hm, hx2, 5, 6, Direction.EAST, 2, true);
        Arch.window(b, hm, hx2, 5, 14, Direction.EAST, 2, true);
        b.bigDoor(hx2, 1, 10, Blocks.BIRCH_DOOR, Direction.EAST);
        for (int k = -1; k <= 1; k++) b.set(hx2 + 1, 4, 10 + k, hm.roofSlab);
        b.fill(hx2, 5, 10, hx2, 7, 10, s(Blocks.RED_CONCRETE));
        b.fill(hx2, 6, 9, hx2, 6, 11, s(Blocks.RED_CONCRETE));
        // ground floor: the ward, the doctor's office (south-east), the stair
        for (int z = hz1 + 2; z <= hz2 - 2; z += 2) {
            b.bed(hx1 + 2, 1, z, Blocks.WHITE_BED, Direction.WEST);
            if (b.rand(z * 3, 3) == 0) b.set(hx1 + 3, 1, z, s(Blocks.COBWEB));
        }
        for (int z = hz1 + 2; z <= 9; z += 2) b.bed(9, 1, z, Blocks.WHITE_BED, Direction.EAST);
        b.set(7, 1, 15, s(Blocks.BREWING_STAND));
        b.set(8, 1, 15, s(Blocks.CAULDRON));
        b.hanging(6, 3, 8, Blocks.LANTERN);
        b.fill(11, 1, 12, 16, 3, 12, white);
        b.door(13, 1, 12, Blocks.BIRCH_DOOR, Direction.NORTH);
        b.set(15, 1, 16, LandmarkKit.facing(Blocks.LECTERN, Direction.NORTH));
        b.fill(11, 1, 16, 12, 2, 16, s(Blocks.BOOKSHELF));
        b.chest(16, 1, 14, Direction.WEST, Buildings.LOOT_RUIN);
        b.set(14, 1, 14, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(14, 2, 14, LandmarkKit.candles(Blocks.WHITE_CANDLE, 2));
        b.hanging(13, 3, 14, Blocks.LANTERN);
        npc(b, "scholar", 13, 1, 15);
        flightZ(b, 4, 5, 1, 12, Direction.EAST, 4, s(Blocks.BIRCH_STAIRS), null);
        LandmarkKit.line(b, 12, 5, 6, 14, 6, Blocks.BIRCH_FENCE);
        // upper ward
        for (int x = 4; x <= 13; x += 3) b.bed(x, 5, 15, Blocks.WHITE_BED, Direction.SOUTH);
        b.set(15, 5, 12, s(Blocks.COBWEB));
        b.set(11, 5, 9, s(Blocks.COBWEB));
        b.hanging(12, 7, 12, Blocks.LANTERN);

        // ---- the church (x 29..43, z 3..18): buttressed nave, steep slate gable, bell tower with a spire
        Mats cm = whiteMats(b.seed + 2);
        int cx1 = 29, cx2 = 43, cz1 = 3, cz2 = 18;
        b.fill(cx1, 0, cz1, cx2, 0, cz2, pale);
        b.fill(cx1 + 1, 0, cz1 + 1, cx2 - 1, 0, cz2 - 1, s(Blocks.POLISHED_DIORITE));
        box(b, cx1, 1, cz1, cx2, 7, cz2, white);
        b.clear(cx1 + 1, 1, cz1 + 1, cx2 - 1, 7, cz2 - 1);
        ring(b, cx1, cz1, cx2, cz2, 1, pale);
        for (int z = cz1 + 3; z <= cz2; z += 3) {
            for (int x : new int[]{cx1 - 1, cx2 + 1}) {
                b.fill(x, 1, z, x, 5, z, pale);
                b.set(x, 6, z, Arch.stair(paleSt, x < cx1 ? Direction.EAST : Direction.WEST, Half.BOTTOM));
            }
        }
        int ctop = Arch.gable(b, cm, cx1, cz1, cx2, cz2, 8, false, 1, white);
        for (int z = cz1 - 1; z <= cz2 + 1; z++) if (b.rand(z * 5, 3) != 0) b.set(36, ctop + 1, z, s(Blocks.SNOW));
        for (int z = cz1 + 2; z <= cz2 - 2; z += 3) {
            for (int x : new int[]{cx1, cx2}) b.fill(x, 3, z, x, 5, z, Arch.along(cm.window, Direction.Axis.Z));
        }
        // the front: doors under a pointed arch, a rose window in the gable
        b.bigDoor(35, 1, cz1, Blocks.BIRCH_DOOR, Direction.NORTH);
        b.bigDoor(37, 1, cz1, Blocks.BIRCH_DOOR, Direction.NORTH);
        b.clear(36, 1, cz1, 36, 3, cz1);
        pointedArchX(b, 34, 38, 4, cz1, paleSt, pale, null);
        b.set(36, 4, cz1, s(Blocks.AIR));
        b.set(36, 5, cz1, pale);
        for (int[] o : new int[][]{{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}}) b.set(36 + o[0], 11 + o[1], cz1, Arch.along(s(Blocks.WHITE_STAINED_GLASS_PANE), Direction.Axis.X));
        // nave: pews, aisle, altar, candles
        for (int z = cz1 + 4; z <= cz2 - 5; z += 2) {
            for (int x = cx1 + 2; x <= 34; x++) b.set(x, 1, z, Arch.stair(s(Blocks.BIRCH_STAIRS), Direction.NORTH, Half.BOTTOM));
            for (int x = 38; x <= cx2 - 2; x++) b.set(x, 1, z, Arch.stair(s(Blocks.BIRCH_STAIRS), Direction.NORTH, Half.BOTTOM));
        }
        LandmarkKit.carpet(b, 35, 1, cz1 + 1, 37, cz2 - 4, Blocks.WHITE_CARPET);
        b.fill(34, 1, cz2 - 2, 38, 1, cz2 - 1, pale);
        b.fill(35, 2, cz2 - 1, 37, 2, cz2 - 1, s(Blocks.QUARTZ_BLOCK));
        b.set(36, 3, cz2 - 1, LandmarkKit.candles(Blocks.WHITE_CANDLE, 4));
        b.set(35, 2, cz2 - 2, LandmarkKit.candles(Blocks.WHITE_CANDLE, 2));
        b.set(37, 2, cz2 - 2, LandmarkKit.candles(Blocks.WHITE_CANDLE, 2));
        b.fill(cx1 + 1, 8, cz1 + 1, cx2 - 1, 8, cz2 - 1, s(Blocks.BIRCH_PLANKS));
        pendant(b, 36, 8, 10, s(Blocks.LANTERN), 2);
        npc(b, "bandit", 36, 1, 9);
        // bell tower at the front-west corner, rising through the roof to a slate spire
        int tx1 = cx1 - 2, tz1 = cz1 - 2;
        b.fill(tx1, 1, tz1, tx1 + 4, 18, tz1 + 4, pale);
        b.clear(tx1 + 1, 1, tz1 + 1, tx1 + 3, 17, tz1 + 3);
        b.clear(tx1, 15, tz1 + 1, tx1 + 4, 16, tz1 + 3);
        b.clear(tx1 + 1, 15, tz1, tx1 + 3, 16, tz1 + 4);
        b.fill(tx1 + 1, 17, tz1 + 1, tx1 + 3, 17, tz1 + 3, pale);
        b.set(tx1 + 2, 16, tz1 + 2, LandmarkKit.bellCeiling());
        cornice(b, tx1, tz1, tx1 + 4, tz1 + 4, 18, paleSt, pale);
        LandmarkKit.cone(b, tx1 + 2, tz1 + 2, 19, 8, 3.2, 0.3, s(ArchBlocks.SLATE_ROOF_TILES), false);
        b.set(tx1 + 2, 27, tz1 + 2, s(Blocks.LIGHTNING_ROD));
        for (int x = tx1 - 1; x <= tx1 + 5; x++) if (b.rand(x * 3, 2) == 0) b.set(x, 19, tz1 - 1, s(Blocks.SNOW));
        b.clear(tx1 + 4, 1, tz1 + 3, tx1 + 4, 3, tz1 + 3);
        b.fill(tx1 + 1, 14, tz1 + 1, tx1 + 3, 14, tz1 + 3, pale);
        b.ladderUp(tx1 + 2, 1, tz1 + 1, 14, Direction.SOUTH);
        Arch.window(b, cm, tx1 + 1, 8, tz1, Direction.NORTH, 2, false);
        Arch.window(b, cm, tx1, 8, tz1 + 2, Direction.WEST, 2, false);

        // ---- houses south of the square, two of them burnt
        whiteHouse(b, 3, 29, 10, 36, 301, true);
        whiteHouse(b, 12, 29, 19, 36, 302, false);
        whiteHouse(b, 3, 38, 10, 45, 303, false);
        whiteHouse(b, 12, 38, 19, 45, 304, true);
        b.barrel(17, 1, 34, Buildings.LOOT_HOUSE);
        b.barrel(5, 1, 44, Buildings.LOOT_HOUSE);
        // the town's graves
        for (int gx = 27; gx <= 31; gx += 2)
            for (int gz = 29; gz <= 37; gz += 4) {
                b.set(gx, 1, gz, s(Blocks.CALCITE));
                b.set(gx, 2, gz, s(ArchBlocks.PALE_MASONRY_WALL));
                b.set(gx, 1, gz + 1, s(Blocks.SNOW));
            }
        b.set(29, 1, 41, s(Blocks.LILY_OF_THE_VALLEY));

        // ---- the white lead mine (south-east): a white hill, a headframe over the adit, a gallery below
        heap(b, 401, 39, 38, 1, 7, 7, Blocks.CALCITE, Blocks.DIORITE, Blocks.CALCITE, Blocks.SNOW_BLOCK);
        b.fill(37, 1, 31, 41, 4, 33, s(Blocks.CALCITE));
        b.clear(38, 1, 31, 40, 3, 37);
        for (int z : new int[]{31, 34}) {
            b.fill(37, 1, z, 37, 3, z, up(ArchBlocks.WEATHERED_BEAM));
            b.fill(41, 1, z, 41, 3, z, up(ArchBlocks.WEATHERED_BEAM));
            b.fill(37, 4, z, 41, 4, z, LandmarksB.beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        }
        b.set(39, 3, 32, lantern(Blocks.LANTERN, true));
        b.set(39, 4, 32, s(Blocks.CALCITE));
        // the headframe: two legs, a crosshead, the winding wheel and its cable
        for (int x : new int[]{37, 41}) b.fill(x, 5, 32, x, 12, 32, up(ArchBlocks.WEATHERED_BEAM));
        b.fill(37, 13, 32, 41, 13, 32, LandmarksB.beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) b.set(39 + dx, 15 + dy, 32, LandmarksB.beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
        b.set(39, 14, 32, s(Blocks.CHAIN));
        for (int y = 5; y <= 12; y++) b.set(39, y, 32, chainY());
        // gallery
        shell(b, 30, -7, 42, 44, -2, 46, Blocks.CALCITE);
        for (int i = 0; i < 6; i++) {
            b.fill(38, -i, 37 + i, 40, -i, 37 + i, stair(Blocks.POLISHED_DIORITE_STAIRS, Direction.NORTH, Half.BOTTOM));
            b.clear(38, 1 - i, 37 + i, 40, 3 - i, 37 + i);
            b.fill(37, -i - 1, 37 + i, 41, -i - 1, 37 + i, Blocks.CALCITE);
            b.fill(37, -i, 37 + i, 37, 3 - i, 37 + i, Blocks.CALCITE);
            b.fill(41, -i, 37 + i, 41, 3 - i, 37 + i, Blocks.CALCITE);
            b.fill(38, 4 - i, 37 + i, 40, 4 - i, 37 + i, Blocks.CALCITE);
        }
        b.fill(31, -7, 44, 43, -7, 44, Blocks.SMOOTH_STONE);
        b.fill(31, -6, 44, 43, -6, 44, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST));
        for (int x = 32; x <= 42; x += 2) {
            b.set(x, -6, 43, Blocks.WHITE_GLAZED_TERRACOTTA);
            b.set(x, -4, 45, Blocks.DIORITE);
        }
        for (int x = 33; x <= 41; x += 4) {
            b.fill(x, -6, 42, x, -4, 42, up(ArchBlocks.WEATHERED_BEAM));
            b.fill(x, -6, 46, x, -4, 46, up(ArchBlocks.WEATHERED_BEAM));
            b.fill(x, -3, 42, x, -3, 46, LandmarksB.beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.Z));
        }
        lamp(b, 35, -4, 44);
        lamp(b, 39, -4, 44);
        b.set(35, -3, 44, s(ArchBlocks.WEATHERED_PLANKS));
        b.set(39, -3, 44, s(ArchBlocks.WEATHERED_PLANKS));
        b.chest(42, -6, 45, Direction.WEST, Buildings.LOOT_RUIN);
        npc(b, "ruin_guardian", 35, -6, 45);
        // a sealed side gallery behind a cracked wall: the last records of the town
        shell(b, 26, -7, 42, 30, -3, 46, Blocks.CALCITE);
        b.set(30, -5, 44, Blocks.CRACKED_STONE_BRICKS);
        b.set(30, -6, 44, Blocks.CRACKED_STONE_BRICKS);
        b.chest(27, -6, 44, Direction.EAST, Buildings.LOOT_TREASURE);
        b.set(27, -6, 43, facing(Blocks.LECTERN, Direction.EAST));
        b.set(28, -4, 45, lantern(Blocks.SOUL_LANTERN, true));
        b.set(28, -3, 45, Blocks.CALCITE);
        mark(b, "treasure", 27, -6, 44);
        b.marker("legend", c, 1, c - 3, tag("story", "white_city"));
    }

    // ============================================================== Lvneel: the Explorers' Monument

    /**
     * The kingdom's monument to its great explorer: a compass-rose plaza with the bronze explorer pointing out to sea
     * from a moulded masonry plinth (its plaque on a lectern, a vault hidden beneath), benches, lamps and flower beds;
     * and the Explorers' Hall behind it, a masonry museum under a slate gable with a columned, pedimented portico,
     * pilasters and tall windows, holding display cases, the logbook wall, a model ship and the chart table.
     */
    static void explorersMonument(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 27 x 27
        site(b, Blocks.POLISHED_ANDESITE, Blocks.STONE, 26);
        int c = 13;
        BlockState pale = s(ArchBlocks.PALE_MASONRY), paleSt = s(ArchBlocks.PALE_MASONRY_STAIRS), field = s(ArchBlocks.FIELDSTONE);
        BlockState col = up(ArchBlocks.MARBLE_COLUMN), brass = s(ArchBlocks.BRASS_LANTERN);
        // ---- the compass-rose plaza
        LandmarkKit.disc(b, c, 8, 0, 7.5, s(ArchBlocks.HARBOUR_STONE));
        LandmarkKit.ring(b, c, 8, 0, 6, s(Blocks.POLISHED_GRANITE));
        for (int i = -7; i <= 7; i++) {
            b.set(c + i, 0, 8, pale);
            b.set(c, 0, 8 + i, pale);
        }
        for (int i = -4; i <= 4; i++) {
            b.set(c + i, 0, 8 + i, s(Blocks.CHISELED_STONE_BRICKS));
            b.set(c + i, 0, 8 - i, s(Blocks.CHISELED_STONE_BRICKS));
        }
        b.set(c, 0, 1, s(Blocks.GOLD_BLOCK));
        // ---- the plinth: stepped base, moulded cornice, the plaque
        b.fill(c - 2, 1, 6, c + 2, 4, 10, pale);
        plinth(b, c - 2, 6, c + 2, 10, 1, paleSt);
        cornice(b, c - 2, 6, c + 2, 10, 4, paleSt, pale);
        b.fill(c - 2, 5, 6, c + 2, 5, 10, pale);
        b.fill(c - 1, 2, 6, c + 1, 3, 6, field);
        b.set(c, 2, 6, s(Blocks.CHISELED_STONE_BRICKS));
        b.set(c, 1, 4, LandmarkKit.facing(Blocks.LECTERN, Direction.NORTH));
        // ---- the bronze explorer, pointing out to sea with his spyglass
        BlockState bronze = s(Blocks.OXIDIZED_CUT_COPPER), coat = s(Blocks.OXIDIZED_COPPER);
        b.fill(c - 1, 6, 8, c - 1, 8, 8, coat);
        b.fill(c + 1, 6, 8, c + 1, 8, 8, coat);
        b.set(c - 1, 6, 7, Arch.stair(s(Blocks.OXIDIZED_CUT_COPPER_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.set(c + 1, 6, 7, Arch.stair(s(Blocks.OXIDIZED_CUT_COPPER_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.fill(c - 1, 9, 8, c + 1, 12, 8, bronze);
        b.fill(c - 1, 9, 9, c + 1, 11, 9, coat);
        b.set(c, 8, 9, coat);
        b.set(c, 13, 8, coat);
        b.set(c, 14, 8, bronze);
        b.fill(c - 1, 14, 8, c + 1, 14, 8, bot(Blocks.OXIDIZED_CUT_COPPER_SLAB));
        b.set(c, 15, 8, bot(Blocks.OXIDIZED_CUT_COPPER_SLAB));
        b.set(c, 13, 7, Arch.stair(s(Blocks.OXIDIZED_CUT_COPPER_STAIRS), Direction.SOUTH, Half.TOP));
        b.fill(c + 2, 12, 8, c + 3, 12, 8, bronze);
        b.set(c + 4, 12, 8, LandmarkKit.facing(Blocks.LIGHTNING_ROD, Direction.EAST));
        b.set(c - 2, 11, 8, bronze);
        b.set(c - 2, 10, 8, bronze);
        b.set(c - 2, 9, 8, s(Blocks.OXIDIZED_CUT_COPPER_SLAB));
        // lamps, benches, flower beds
        for (int[] o : new int[][]{{-6, 2}, {6, 2}, {-6, 14}, {6, 14}}) post(b, c + o[0], 1, o[1], s(ArchBlocks.PALE_MASONRY_WALL), 3, brass);
        for (int x : new int[]{3, 23}) for (int z = 4; z <= 12; z += 4) bench(b, x, 1, z, x < c ? Direction.EAST : Direction.WEST);
        for (int x = 1; x <= 4; x++) {
            b.set(x, 0, 1, s(Blocks.ROOTED_DIRT));
            b.set(x, 1, 1, s(Blocks.BLUE_ORCHID));
            b.set(x + 21, 0, 1, s(Blocks.ROOTED_DIRT));
            b.set(x + 21, 1, 1, s(Blocks.BLUE_ORCHID));
        }
        npc(b, "child", 9, 1, 3);
        npc(b, "resident", 18, 1, 12);
        // the hidden vault under the plinth: a loose chiseled block on the plinth's back hides the way down
        b.clear(c - 1, 1, 7, c + 1, 4, 9);
        b.set(c, 1, 10, s(Blocks.CHISELED_DEEPSLATE));
        b.set(c, 2, 10, s(Blocks.CHISELED_DEEPSLATE));
        shell(b, c - 3, -6, 5, c + 3, 0, 11, Blocks.DEEPSLATE_BRICKS);
        b.fill(c - 1, 0, 7, c + 1, 0, 9, Blocks.POLISHED_DEEPSLATE);
        b.clear(c, 0, 9, c, 0, 9);
        b.ladderUp(c, -5, 9, 6, Direction.NORTH);
        b.fill(c, -5, 10, c, 0, 10, Blocks.DEEPSLATE_BRICKS);
        b.set(c - 2, -5, 6, Blocks.GOLD_BLOCK);
        b.set(c - 2, -4, 6, facing(Blocks.BELL, Direction.SOUTH));
        b.chest(c + 2, -5, 6, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.set(c, -2, 7, lantern(Blocks.LANTERN, true));
        b.set(c, -1, 7, Blocks.DEEPSLATE_BRICKS);
        mark(b, "treasure", c + 2, -5, 6);

        // ---- the Explorers' Hall (x 3..23, z 16..25): masonry on a fieldstone plinth, slate gable, portico
        Mats m = mats(BuildStyle.STONE_TOWN, b.seed);
        m.wall = pale;
        m.window = s(ArchBlocks.MULLION_WINDOW);
        m.shutter = null;
        m.planters = false;
        m.sill = paleSt;
        m.eaveBrackets = true;
        int x1 = 3, x2 = 23, z1 = 16, z2 = 25;
        b.fill(x1, 0, z1, x2, 0, z2, field);
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, s(Blocks.SPRUCE_PLANKS));
        box(b, x1, 1, z1, x2, 6, z2, pale);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 6, z2 - 1);
        ring(b, x1, z1, x2, z2, 1, field);
        plinth(b, x1, z1, x2, z2, 1, s(ArchBlocks.FIELDSTONE_STAIRS));
        for (int x = x1; x <= x2; x += 4) {
            b.fill(x, 2, z1, x, 6, z1, col);
            b.fill(x, 2, z2, x, 6, z2, col);
        }
        int top = Arch.gable(b, m, x1, z1, x2, z2, 7, true, 1, pale);
        b.fill(x1 + 1, 7, z1 + 1, x2 - 1, 7, z2 - 1, s(Blocks.SPRUCE_PLANKS));
        for (int x : new int[]{5, 9, 17, 21}) {
            Arch.window(b, m, x, 2, z1, Direction.NORTH, 3, false);
            Arch.window(b, m, x, 2, z2, Direction.SOUTH, 3, false);
        }
        for (int z : new int[]{18, 21, 23}) {
            Arch.window(b, m, x1, 2, z, Direction.WEST, 3, false);
            Arch.window(b, m, x2, 2, z, Direction.EAST, 3, false);
        }
        Arch.window(b, m, x1, 9, 20, Direction.WEST, 2, false);
        Arch.window(b, m, x2, 9, 20, Direction.EAST, 2, false);
        // a little lantern cupola on the ridge
        b.fill(c - 1, top, 20, c + 1, top, 21, pale);
        for (int[] q : new int[][]{{c - 1, 20}, {c + 1, 20}, {c - 1, 21}, {c + 1, 21}}) b.fill(q[0], top + 1, q[1], q[0], top + 2, q[1], s(Blocks.SPRUCE_FENCE));
        b.set(c, top + 1, 20, Arch.lantern(brass, false));
        b.fill(c - 1, top + 3, 20, c + 1, top + 3, 21, bot(ArchBlocks.SLATE_ROOF_TILE_SLAB));
        // the portico with its pediment
        b.fill(9, 0, 12, 17, 0, 15, pale);
        b.clear(9, 1, 15, 17, 1, 15);
        for (int x : new int[]{9, 11, 15, 17}) b.fill(x, 1, 12, x, 5, 12, col);
        b.fill(9, 6, 12, 17, 6, 15, pale);
        Mats pm = mats(BuildStyle.STONE_TOWN, b.seed);
        pm.roofKind = Arch.RoofKind.GABLE;
        pm.eaveBrackets = false;
        Arch.gable(b, pm, 9, 12, 17, 15, 7, false, 1, pale);
        b.set(c, 9, 12, Arch.along(s(ArchBlocks.LATTICE_WINDOW), Direction.Axis.X));
        b.bigDoor(c, 1, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.set(c - 1, 4, z1 - 1, LandmarkKit.banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        b.set(c + 1, 4, z1 - 1, LandmarkKit.banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        b.hanging(c, 5, 14, ArchBlocks.BRASS_LANTERN.get());
        // the exhibits
        LandmarkKit.carpet(b, c - 1, 1, z1 + 1, c + 1, z2 - 2, Blocks.BLUE_CARPET);
        int[][] cases = {{5, 19}, {8, 19}, {18, 19}, {21, 19}, {5, 22}, {21, 22}};
        Block[] relics = {Blocks.GOLD_BLOCK, Blocks.SKELETON_SKULL, Blocks.BELL, Blocks.DECORATED_POT, Blocks.RAW_GOLD_BLOCK, Blocks.AMETHYST_CLUSTER};
        for (int i = 0; i < cases.length; i++) {
            int px = cases[i][0], pz = cases[i][1];
            b.set(px, 1, pz, s(ArchBlocks.DARK_BEAM));
            b.set(px, 2, pz, s(relics[i]));
            b.set(px, 3, pz, s(Blocks.GLASS));
        }
        b.fill(8, 1, 24, 18, 2, 24, s(Blocks.BOOKSHELF));
        b.set(c, 3, 24, s(Blocks.BOOKSHELF));
        b.set(9, 1, 22, Blocks.CARTOGRAPHY_TABLE);
        b.set(17, 1, 22, facing(Blocks.LECTERN, Direction.NORTH));
        // the model ship on its stand
        b.fill(11, 1, 22, 15, 1, 22, s(ArchBlocks.DARK_BEAM).setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
        b.set(11, 2, 22, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.EAST, Half.BOTTOM));
        b.set(15, 2, 22, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.WEST, Half.BOTTOM));
        b.fill(12, 2, 22, 14, 2, 22, bot(Blocks.SPRUCE_SLAB));
        b.set(c, 3, 22, s(Blocks.SPRUCE_FENCE));
        b.fill(c, 4, 22, c, 5, 22, s(Blocks.WHITE_WOOL));
        for (int x : new int[]{7, c, 19}) b.hanging(x, 6, 20, ArchBlocks.BRASS_LANTERN.get());
        b.chest(x2 - 1, 1, z2 - 1, Direction.WEST, Buildings.LOOT_SHOP);
        npc(b, "scholar", 16, 1, 21);
        npc(b, "guard", 11, 1, 18);
        b.marker("legend", c, 1, 4, tag("story", "explorers_monument"));
    }

    // ============================================================== Spider Miles: the junkyard fort

    private static final Block[] SCRAP = {Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_CUT_COPPER, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF,
            Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SMOOTH_BASALT, Blocks.IRON_BLOCK, Blocks.MOSSY_COBBLESTONE, Blocks.BARREL, Blocks.WAXED_EXPOSED_CUT_COPPER};

    /**
     * A pirate fort cobbled from junk, but built: a curtain wall of salvaged panels (one material per bay) between
     * weathered-beam posts with iron spikes along the ragged top; two timber watch-towers with tin roofs flanking the
     * gate, the gate itself under a beam lintel with the crew's flag; an overturned hull as the great hall (keel, ribs,
     * portholes, a boarded stern cabin for the captain); a salvaged mast with a yard and torn sail carrying the crow's
     * nest; a smithy shed with a smoking brick forge; junk heaps, a prisoner cage and a stash under a skull.
     */
    static void junkyardFort(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 37 x 37
        site(b, Blocks.GRAVEL, Blocks.COBBLESTONE, 24);
        patchGround(b, 17, 0, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.TUFF, Blocks.COBBLED_DEEPSLATE);
        BlockState post = up(ArchBlocks.WEATHERED_BEAM), dark = up(ArchBlocks.DARK_BEAM);
        Block[] panels = {ArchBlocks.WEATHERED_PLANKS.get(), Blocks.WEATHERED_CUT_COPPER, Blocks.COBBLED_DEEPSLATE, Blocks.EXPOSED_CUT_COPPER, Blocks.DARK_OAK_PLANKS};
        // ---- the curtain wall: two thick, bays of one salvaged material between beam posts, spikes on top
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean edge = x <= 1 || z <= 1 || x >= w - 2 || z >= d - 2;
                if (!edge) continue;
                int bay = (x <= 1 || x >= w - 2) ? z / 4 : x / 4;
                int h = 5 + b.rand(bay * 37 + (x <= 1 ? 1 : x >= w - 2 ? 2 : z <= 1 ? 3 : 4), 2);
                Block panel = panels[b.rand(bay * 13 + (x <= 1 ? 5 : x >= w - 2 ? 7 : z <= 1 ? 11 : 17), panels.length)];
                b.fill(x, 1, z, x, h, z, panel);
                b.set(x, 1, z, s(Blocks.MOSSY_COBBLESTONE));
                boolean outer = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (outer && ((x <= 1 || x >= w - 2) ? z % 4 == 0 : x % 4 == 0)) b.fill(x, 1, z, x, h + 1, z, post);
                if (!outer && (x + z) % 3 == 0) b.set(x, h + 1, z, Arch.along(s(Blocks.IRON_BARS), x <= 1 || x >= w - 2 ? Direction.Axis.Z : Direction.Axis.X));
            }
        // the gate between two watch-towers
        b.clear(16, 1, 0, 20, 5, 1);
        b.fill(15, 6, 0, 21, 6, 1, LandmarksB.beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
        b.fill(16, 5, 0, 16, 5, 1, s(Blocks.CHAIN));
        b.fill(20, 5, 0, 20, 5, 1, s(Blocks.CHAIN));
        b.set(18, 7, 0, s(Blocks.DAMAGED_ANVIL));
        b.set(18, 7, 1, s(Blocks.SKELETON_SKULL));
        for (int tx : new int[]{12, 22}) {
            for (int[] q : new int[][]{{tx, 0}, {tx + 2, 0}, {tx, 2}, {tx + 2, 2}}) b.fill(q[0], 1, q[1], q[0], 9, q[1], dark);
            b.fill(tx, 9, 0, tx + 2, 9, 2, s(ArchBlocks.WEATHERED_PLANKS));
            LandmarkKit.outline(b, tx, 10, 0, tx + 2, 2, Blocks.SPRUCE_FENCE);
            b.fill(tx - 1, 12, -0, tx + 3, 12, 3, bot(Blocks.WEATHERED_CUT_COPPER_SLAB));
            for (int[] q : new int[][]{{tx, 0}, {tx + 2, 0}, {tx, 2}, {tx + 2, 2}}) b.set(q[0], 11, q[1], dark);
            b.fill(tx + 1, 1, 2, tx + 1, 8, 2, s(ArchBlocks.WEATHERED_PLANKS));
            b.ladderUp(tx + 1, 1, 3, 9, Direction.SOUTH);
            b.set(tx + 1, 10, 2, s(Blocks.AIR));
        }
        npc(b, "pirate", 17, 1, 3);
        npc(b, "pirate", 21, 1, 4);

        // ---- the great hall: an overturned hull (keel along x), ribs, portholes, the bow door, the stern cabin
        int hx = 18, hz = 22;
        ellipsoid(b, hx, 0, hz, 10.5, 8.5, 6.5, Blocks.DARK_OAK_PLANKS.defaultBlockState(), true, 1);
        for (int x = hx - 9; x <= hx + 9; x += 3) {
            for (int z = hz - 6; z <= hz + 6; z++) {
                double v = 1 - ((x - hx) * (x - hx)) / (10.5 * 10.5) - ((z - hz) * (z - hz)) / (6.5 * 6.5);
                if (v <= 0) continue;
                int t = (int) Math.floor(8.5 * Math.sqrt(v));
                if (t >= 1) b.set(x, t, z, s(Blocks.STRIPPED_SPRUCE_LOG));
            }
        }
        for (int x = hx - 9; x <= hx + 9; x++) {
            int ky = (int) Math.floor(8.5 * Math.sqrt(Math.max(0, 1 - (x - hx) * (x - hx) / 110.25))) + 1;
            b.set(x, ky, hz, LandmarksB.beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
        }
        b.fill(hx - 10, 0, hz - 6, hx + 10, 0, hz + 6, s(ArchBlocks.DOCK_PLANKS));
        // the gunwale where the hull meets the ground, portholes along both sides
        for (int x = hx - 8; x <= hx + 8; x++) {
            double v = 1 - ((x - hx) * (x - hx)) / (10.5 * 10.5);
            int zz = (int) Math.floor(6.5 * Math.sqrt(v));
            b.set(x, 1, hz - zz, LandmarksB.beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
            b.set(x, 1, hz + zz, LandmarksB.beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
            if (Math.floorMod(x - hx, 3) == 1 && Math.abs(x - hx) < 7) {
                double v3 = 1 - ((x - hx) * (x - hx)) / 110.25 - 9 / 72.25;
                int z3 = (int) Math.floor(6.5 * Math.sqrt(v3));
                b.set(x, 3, hz - z3, s(Blocks.GLASS));
                b.set(x, 3, hz + z3, s(Blocks.GLASS));
            }
        }
        b.clear(hx - 11, 1, hz - 1, hx - 8, 3, hz + 1);
        b.fill(hx - 9, 1, hz - 1, hx - 9, 3, hz + 1, Blocks.DARK_OAK_PLANKS);
        b.fill(hx - 10, 1, hz - 2, hx - 10, 4, hz - 2, dark);
        b.fill(hx - 10, 1, hz + 2, hx - 10, 4, hz + 2, dark);
        b.fill(hx - 10, 4, hz - 1, hx - 10, 4, hz + 1, LandmarksB.beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
        b.door(hx - 9, 1, hz, Blocks.SPRUCE_DOOR, Direction.WEST);
        b.hanging(hx - 11, 3, hz, ArchBlocks.BRASS_LANTERN.get());
        b.set(hx - 11, 4, hz, LandmarksB.beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
        // mess hall
        longTableX(b, hx - 6, hx + 2, 1, hz, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        for (int x : new int[]{hx - 4, hx, hx + 3}) {
            b.set(x, 6, hz, Blocks.DARK_OAK_PLANKS);
            b.hanging(x, 5, hz, Blocks.LANTERN);
        }
        b.barrel(hx - 7, 1, hz - 3, Buildings.LOOT_TAVERN);
        b.barrel(hx - 6, 1, hz - 3, Buildings.LOOT_TAVERN);
        b.barrel(hx - 7, 1, hz + 3, Buildings.LOOT_WAREHOUSE);
        b.set(hx - 6, 1, hz + 3, s(ArchBlocks.CARGO_CRATE));
        b.set(hx - 3, 1, hz + 4, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, Direction.NORTH));
        b.set(hx - 2, 1, hz + 4, Blocks.CAMPFIRE);
        b.set(hx + 1, 1, hz - 4, decor(ArchBlocks.DECK_CANNON, Direction.NORTH));
        npc(b, "pirate", hx - 4, 1, hz - 2);
        npc(b, "pirate", hx, 1, hz + 2);
        // the captain's cabin at the stern (east), partitioned inside the hull only
        for (int y = 1; y <= 7; y++)
            for (int z = hz - 6; z <= hz + 6; z++) {
                double v = 25 / 88.36 + (y * y) / 54.76 + ((z - hz) * (z - hz)) / 29.16;
                if (v < 1.0) b.set(hx + 5, y, z, s(ArchBlocks.WEATHERED_PLANKS));
            }
        b.door(hx + 5, 1, hz, Blocks.SPRUCE_DOOR, Direction.WEST);
        LandmarkKit.carpet(b, hx + 6, 1, hz - 2, hx + 8, hz + 2, Blocks.RED_CARPET);
        b.bed(hx + 6, 1, hz + 2, Blocks.BLACK_BED, Direction.SOUTH);
        b.set(hx + 8, 1, hz - 1, facing(Blocks.LECTERN, Direction.WEST));
        b.chest(hx + 6, 1, hz - 3, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.set(hx + 7, 4, hz, Blocks.DARK_OAK_PLANKS);
        b.hanging(hx + 7, 3, hz, Blocks.LANTERN);
        // the stern's boarded windows
        for (int z = hz - 2; z <= hz + 2; z += 2) b.set(hx + 9, 3, z, Arch.along(s(ArchBlocks.LATTICE_WINDOW), Direction.Axis.Z));
        npc(b, "pirate_captain", hx + 7, 1, hz);

        // ---- the crow's nest on a salvaged mast, with a yard and a torn sail
        b.fill(5, 1, 6, 5, 19, 6, up(Blocks.SPRUCE_LOG));
        LandmarkKit.disc(b, 5, 6, 14, 2.3, s(ArchBlocks.DOCK_PLANKS));
        for (int x = 3; x <= 7; x++)
            for (int z = 4; z <= 8; z++) {
                double dd = Math.sqrt((x - 5) * (x - 5) + (z - 6) * (z - 6));
                if (dd > 1.6 && dd <= 2.8) b.set(x, 15, z, s(Blocks.SPRUCE_FENCE));
            }
        b.set(5, 15, 6, up(Blocks.SPRUCE_LOG));
        b.clear(5, 14, 7, 5, 14, 7);
        b.clear(5, 15, 8, 5, 15, 8);
        b.ladderUp(5, 1, 7, 14, Direction.SOUTH);
        b.fill(2, 11, 6, 8, 11, 6, LandmarksB.beam(Blocks.SPRUCE_LOG, Direction.Axis.X));
        for (int x = 3; x <= 7; x++) for (int y = 8; y <= 10; y++) if (x != 5 && b.rand(x * 7 + y, 5) != 0) b.set(x, y, 6, s(Blocks.WHITE_WOOL));
        b.set(6, 19, 6, LandmarkKit.banner(Blocks.BLACK_WALL_BANNER, Direction.EAST));
        npc(b, "lookout", 6, 15, 5);

        // ---- the smithy: an open shed under a tin roof, a brick forge with a smoking chimney
        for (int[] p : new int[][]{{26, 4}, {33, 4}, {26, 10}, {33, 10}}) b.fill(p[0], 1, p[1], p[0], 4, p[1], post);
        b.fill(26, 4, 4, 33, 4, 4, LandmarksB.beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        b.fill(26, 4, 10, 33, 4, 10, LandmarksB.beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        for (int x = 25; x <= 34; x++)
            for (int z = 3; z <= 11; z++) {
                int i = Math.abs(z - 7);
                b.set(x, 6 - i / 3, z, Arch.slab(s(Blocks.WEATHERED_CUT_COPPER_SLAB), i % 3 == 2 ? net.minecraft.world.level.block.state.properties.SlabType.BOTTOM : net.minecraft.world.level.block.state.properties.SlabType.TOP));
            }
        b.fill(27, 0, 5, 32, 0, 9, Blocks.SMOOTH_BASALT);
        b.fill(31, 1, 8, 33, 2, 10, s(Blocks.BRICKS));
        b.set(32, 1, 8, facing(Blocks.BLAST_FURNACE, Direction.NORTH));
        b.fill(33, 3, 10, 33, 8, 10, s(Blocks.BRICKS));
        b.set(33, 9, 10, campfire(true));
        b.set(33, 8, 10, s(Blocks.HAY_BLOCK));
        b.set(28, 1, 9, Blocks.ANVIL);
        b.set(29, 1, 9, Blocks.SMITHING_TABLE);
        b.set(30, 1, 9, Blocks.GRINDSTONE);
        b.set(32, 1, 6, Blocks.LAVA_CAULDRON);
        b.chest(27, 1, 6, Direction.EAST, Buildings.LOOT_SMITH);
        b.set(27, 1, 7, s(ArchBlocks.CARGO_CRATE));
        b.set(29, 3, 7, lantern(Blocks.LANTERN, true));
        b.set(29, 4, 7, s(ArchBlocks.WEATHERED_PLANKS));
        b.fill(29, 5, 7, 29, 5, 7, s(ArchBlocks.WEATHERED_PLANKS));
        npc(b, "pirate", 30, 1, 7);

        // ---- junk heaps and a prisoner cage
        heap(b, 501, 9, 30, 1, 3, 3, Blocks.GRAVEL, Blocks.EXPOSED_COPPER, Blocks.TUFF, Blocks.DARK_OAK_PLANKS, Blocks.CHAIN);
        heap(b, 502, 30, 30, 1, 3, 3, Blocks.GRAVEL, Blocks.WEATHERED_COPPER, Blocks.COBBLED_DEEPSLATE, Blocks.BARREL);
        heap(b, 503, 10, 10, 1, 2, 2, Blocks.GRAVEL, Blocks.IRON_BARS, Blocks.SPRUCE_PLANKS);
        outline(b, 25, 1, 14, 29, 18, Blocks.IRON_BARS);
        outline(b, 25, 2, 14, 29, 18, Blocks.IRON_BARS);
        b.fill(25, 3, 14, 29, 3, 18, Blocks.IRON_BLOCK);
        for (int[] q : new int[][]{{25, 14}, {29, 14}, {25, 18}, {29, 18}}) b.fill(q[0], 1, q[1], q[0], 2, q[1], dark);
        mark(b, "cell", 27, 1, 16);
        // buried stash beneath the south-east heap (a skull marks it)
        b.set(30, 4, 30, Blocks.SKELETON_SKULL);
        b.fill(29, -3, 29, 31, -1, 31, Blocks.GRAVEL);
        b.chest(30, -2, 30, Direction.NORTH, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 30, -2, 30);
        b.marker("arc_anchor", 18, 1, 8, tag("arc", "junkyard_family"));
    }

    // ============================================================== Ilisia: the royal castle

    /**
     * The flower kingdom's white castle: a pale masonry curtain wall with a string course, cornice and crenellated
     * walk; four round corner towers with white upper stages and flared teal cone roofs; a crenellated gatehouse with
     * a portcullis, the kingdom's banners and its guardroom; a courtyard of flower beds, hedges, planters and a
     * fountain; and the keep: white plaster on a masonry plinth with marble pilasters and floor bands, a columned
     * porch carrying a balcony, a teal hip roof and a central spire. Inside: throne hall, banquet hall, kitchen, grand
     * stair; royal chambers, library and chapel; the guard hall; the treasury below the throne.
     */
    static void royalCastle(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 51 x 51
        BlockState pale = s(ArchBlocks.PALE_MASONRY), paleSt = s(ArchBlocks.PALE_MASONRY_STAIRS), white = s(ArchBlocks.WHITE_PLASTER);
        BlockState col = up(ArchBlocks.MARBLE_COLUMN), teal = s(ArchBlocks.TEAL_ROOF_TILES), tealSt = s(ArchBlocks.TEAL_ROOF_TILE_STAIRS), gold = s(Blocks.GOLD_BLOCK);
        Block stone = ArchBlocks.PALE_MASONRY.get();
        Mats m = mats(BuildStyle.PALACE, b.seed);
        m.window = s(ArchBlocks.MULLION_WINDOW);
        m.shutter = ArchBlocks.BLUE_SHUTTER.get();
        m.planters = true;
        m.sill = paleSt;
        site(b, Blocks.GRASS_BLOCK, Blocks.STONE, 46);
        // ---- curtain wall (y 1..9), two thick, string course, cornice, crenellated walk
        for (int y = 1; y <= 9; y++) {
            b.walls(2, y, 2, w - 3, y, d - 3, stone);
            b.walls(3, y, 3, w - 4, y, d - 4, stone);
        }
        ring(b, 2, 2, w - 3, d - 3, 1, s(ArchBlocks.FIELDSTONE));
        ring(b, 2, 2, w - 3, d - 3, 6, s(Blocks.POLISHED_DIORITE));
        cornice(b, 2, 2, w - 3, d - 3, 9, paleSt, pale);
        crenelX(b, 2, w - 3, 10, 2, stone);
        crenelX(b, 2, w - 3, 10, d - 3, stone);
        crenelZ(b, 2, d - 3, 10, 2, stone);
        crenelZ(b, 2, d - 3, 10, w - 3, stone);
        // corner towers: masonry shaft, white upper stage, a flared teal cone with a gold finial
        for (int[] t : new int[][]{{5, 5}, {w - 6, 5}, {5, d - 6}, {w - 6, d - 6}}) {
            int tx = t[0], tz = t[1];
            int sx = tx < w / 2 ? 1 : -1, sz = tz < d / 2 ? 1 : -1;
            cyl(b, tx, tz, 1, 15, 4, pale, true);
            cyl(b, tx, tz, 11, 15, 4, white, true);
            clearCyl(b, tx, tz, 1, 15, 3);
            cyl(b, tx, tz, 0, 0, 3, s(Blocks.SMOOTH_QUARTZ), false);
            cyl(b, tx, tz, 9, 9, 3, s(Blocks.SPRUCE_PLANKS), false);
            cyl(b, tx, tz, 10, 10, 4, s(Blocks.POLISHED_DIORITE), true);
            cyl(b, tx, tz, 15, 15, 3, s(Blocks.SPRUCE_PLANKS), false);
            // flared eave ring, then the cone
            LandmarkKit.ring(b, tx, tz, 16, 5, teal);
            cone(b, tx, tz, 17, 8, 4.4, 0.5, teal, false);
            b.set(tx, 25, tz, gold);
            b.set(tx, 26, tz, s(Blocks.LIGHTNING_ROD));
            for (int y : new int[]{4, 12}) {
                b.set(tx - 4 * sx, y, tz, Arch.along(m.window, Direction.Axis.Z));
                b.set(tx - 4 * sx, y + 1, tz, Arch.along(m.window, Direction.Axis.Z));
                b.set(tx, y, tz - 4 * sz, Arch.along(m.window, Direction.Axis.X));
                b.set(tx, y + 1, tz - 4 * sz, Arch.along(m.window, Direction.Axis.X));
            }
            // doors from the courtyard, openings onto both wall walks, a ladder up the outer side
            b.clear(tx + 4 * sx, 1, tz, tx + 4 * sx, 3, tz);
            b.clear(tx, 1, tz + 4 * sz, tx, 3, tz + 4 * sz);
            for (int x = tx - 4; x <= tx + 4; x++)
                for (int z = tz - 4; z <= tz + 4; z++) {
                    boolean curtain = x == 2 || x == 3 || x == w - 4 || x == w - 3 || z == 2 || z == 3 || z == d - 4 || z == d - 3;
                    double dd = Math.sqrt((x - tx) * (x - tx) + (z - tz) * (z - tz));
                    if (curtain && dd > 3.4) b.clear(x, 10, z, x, 11, z);
                }
            b.ladderUp(tx, 1, tz - 3 * sz, 9, sz > 0 ? Direction.SOUTH : Direction.NORTH);
            b.hanging(tx, 8, tz + sz, Blocks.LANTERN);
            b.hanging(tx, 14, tz, Blocks.LANTERN);
            // a guard store below, a watch room above
            b.set(tx - 2 * sx, 1, tz + 2 * sz, s(Blocks.BARREL));
            b.set(tx - 2 * sx, 1, tz + sz, s(ArchBlocks.CARGO_CRATE));
            b.set(tx - 2 * sx, 2, tz + sz, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
            b.set(tx + sx, 1, tz - 2 * sz, s(Blocks.SMITHING_TABLE));
            b.set(tx - 2 * sx, 10, tz + 2 * sz, decor(ArchBlocks.PLANK_BENCH, Direction.NORTH));
            b.set(tx + 2 * sx, 10, tz - 2 * sz, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        }
        // ---- gatehouse (north, x 19..31): masonry, an arched passage with a portcullis, crenels, banners
        b.fill(19, 1, 0, 31, 13, 6, stone);
        ring(b, 19, 0, 31, 6, 1, s(ArchBlocks.FIELDSTONE));
        b.clear(20, 8, 1, 30, 12, 5);
        b.fill(20, 7, 1, 30, 7, 5, s(Blocks.SMOOTH_QUARTZ));
        archX(b, 25, 2, 1, 0, 6, 3);
        LandmarkKit.line(b, 24, 6, 0, 26, 0, Blocks.IRON_BARS);
        LandmarkKit.line(b, 23, 5, 0, 27, 0, Blocks.IRON_BARS);
        crenelX(b, 19, 31, 14, 0, stone);
        crenelX(b, 19, 31, 14, 6, stone);
        crenelZ(b, 1, 5, 14, 19, stone);
        crenelZ(b, 1, 5, 14, 31, stone);
        b.fill(23, 0, 0, 27, 0, 6, s(Blocks.POLISHED_ANDESITE));
        for (int x : new int[]{21, 29}) {
            b.set(x, 9, 7, LandmarkKit.banner(Blocks.LIGHT_BLUE_WALL_BANNER, Direction.SOUTH));
            b.fill(x, 9, 0, x, 10, 0, Arch.along(m.window, Direction.Axis.X));
        }
        npc(b, "guard", 23, 1, 7);
        npc(b, "guard", 27, 1, 7);
        // the guardroom over the gate (y 8..12), a ladder from the courtyard side
        b.ladderUp(21, 1, 7, 8, Direction.SOUTH);
        b.clear(21, 8, 6, 21, 9, 6);
        b.bed(29, 8, 2, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.chest(28, 8, 5, Direction.NORTH, Buildings.LOOT_MARINE);
        b.set(24, 8, 3, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(24, 9, 3, LandmarkKit.candles(Blocks.CANDLE, 2));
        b.set(23, 8, 3, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.WEST, Half.BOTTOM));
        lamp(b, 25, 12, 3);
        npc(b, "guard", 25, 8, 4);

        // ---- courtyard: flower beds in planters, hedges, a fountain, the processional way
        b.fill(24, 0, 7, 26, 0, 21, s(Blocks.SMOOTH_STONE));
        b.fill(23, 0, 7, 23, 0, 21, pale);
        b.fill(27, 0, 7, 27, 0, 21, pale);
        for (int x = 9; x <= 41; x++)
            for (int z = 10; z <= 19; z++) {
                if (x >= 21 && x <= 29) continue;
                boolean bed = (z == 11 || z == 12 || z == 17 || z == 18) && x % 8 != 1;
                if (bed) {
                    b.set(x, 0, z, s(Blocks.ROOTED_DIRT));
                    b.set(x, 1, z, (x + z) % 3 == 0 ? s(Blocks.PINK_TULIP) : (x + z) % 3 == 1 ? s(Blocks.WHITE_TULIP) : s(Blocks.ALLIUM));
                } else if (z == 10 || z == 19) b.set(x, 1, z, LandmarkKit.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
            }
        for (int x : new int[]{9, 17, 33, 41}) for (int z : new int[]{14, 15}) b.set(x, 1, z, decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        LandmarkKit.disc(b, 25, 14, 0, 3.6, pale);
        LandmarkKit.ring(b, 25, 14, 1, 3, bot(ArchBlocks.PALE_MASONRY_SLAB));
        LandmarkKit.disc(b, 25, 14, 0, 2.5, s(Blocks.WATER));
        b.set(25, 0, 14, pale);
        b.fill(25, 1, 14, 25, 3, 14, col);
        b.set(25, 4, 14, s(Blocks.CHISELED_QUARTZ_BLOCK));
        for (int x : new int[]{12, 38}) for (int z : new int[]{14}) post(b, x, 1, z, s(ArchBlocks.PALE_MASONRY_WALL), 3, s(ArchBlocks.BRASS_LANTERN));
        bench(b, 20, 1, 13, Direction.EAST);
        bench(b, 30, 1, 15, Direction.WEST);
        npc(b, "resident", 15, 1, 14);
        npc(b, "child", 34, 1, 13);

        // ---- the keep (x 9..41, z 22..45): three storeys (floors y 0, 7, 13), teal hip roof from 19, central spire
        int kx1 = 9, kx2 = 41, kz1 = 22, kz2 = 45;
        int[] fy = {0, 7, 13};
        for (int f = 0; f < 3; f++) {
            int y0 = fy[f], h = f == 0 ? 6 : 5;
            b.fill(kx1, y0, kz1, kx2, y0, kz2, f == 0 ? s(Blocks.POLISHED_DIORITE) : s(Blocks.SPRUCE_PLANKS));
            box(b, kx1, y0 + 1, kz1, kx2, y0 + h, kz2, f == 0 ? pale : white);
            b.clear(kx1 + 1, y0 + 1, kz1 + 1, kx2 - 1, y0 + h, kz2 - 1);
        }
        ring(b, kx1, kz1, kx2, kz2, 1, s(ArchBlocks.FIELDSTONE));
        plinth(b, kx1, kz1, kx2, kz2, 1, paleSt);
        for (int y : new int[]{7, 13}) {
            ring(b, kx1, kz1, kx2, kz2, y, pale);
            cornice(b, kx1, kz1, kx2, kz2, y, paleSt, null);
        }
        for (int[] q : new int[][]{{kx1, kz1}, {kx2, kz1}, {kx1, kz2}, {kx2, kz2}}) b.fill(q[0], 1, q[1], q[0], 18, q[1], pale);
        for (int x = kx1 + 4; x <= kx2 - 4; x += 4) {
            b.fill(x, 8, kz1, x, 12, kz1, col);
            b.fill(x, 14, kz1, x, 18, kz1, col);
            b.fill(x, 8, kz2, x, 12, kz2, col);
            b.fill(x, 14, kz2, x, 18, kz2, col);
        }
        for (int x = kx1 + 2; x <= kx2 - 2; x += 4) {
            if (Math.abs(x - 25) > 2) Arch.window(b, m, x, 2, kz1, Direction.NORTH, 3, true);
            Arch.window(b, m, x, 9, kz1, Direction.NORTH, 2, true);
            Arch.window(b, m, x, 15, kz1, Direction.NORTH, 2, true);
            Arch.window(b, m, x, 2, kz2, Direction.SOUTH, 3, true);
            Arch.window(b, m, x, 9, kz2, Direction.SOUTH, 2, true);
            Arch.window(b, m, x, 15, kz2, Direction.SOUTH, 2, true);
        }
        for (int z = kz1 + 2; z <= kz2 - 2; z += 4) {
            for (int y : new int[]{2, 9, 15}) {
                Arch.window(b, m, kx1, y, z, Direction.WEST, y == 2 ? 3 : 2, true);
                Arch.window(b, m, kx2, y, z, Direction.EAST, y == 2 ? 3 : 2, true);
            }
        }
        b.fill(kx1 + 1, 19, kz1 + 1, kx2 - 1, 19, kz2 - 1, s(Blocks.SPRUCE_PLANKS));
        Mats rm = mats(BuildStyle.PALACE, b.seed);
        rm.roofKind = Arch.RoofKind.HIP;
        rm.eaveBrackets = true;
        int rtop = Arch.hip(b, rm, kx1, kz1, kx2, kz2, 19, 1);
        // the central spire rising from the ridge
        roundTower(b, 25, 33, rtop - 2, rtop + 6, 3, white, pale, new int[]{rtop - 2}, null, new int[]{}, m.window, new int[]{rtop + 3}, null, null, 0, false);
        LandmarkKit.ring(b, 25, 33, rtop + 7, 4, teal);
        cone(b, 25, 33, rtop + 8, 9, 3.4, 0.4, teal, false);
        b.set(25, rtop + 17, 33, gold);
        b.fill(25, rtop + 18, 33, 25, rtop + 20, 33, s(Blocks.IRON_BARS));
        b.set(25, rtop + 20, 32, LandmarkKit.banner(Blocks.PINK_WALL_BANNER, Direction.NORTH));
        // the porch on four columns carrying a balcony, the great doors
        b.fill(21, 0, 18, 29, 0, 21, pale);
        for (int x : new int[]{21, 23, 27, 29}) b.fill(x, 1, 18, x, 6, 18, col);
        b.fill(21, 7, 18, 29, 7, 21, pale);
        for (int x = 21; x <= 29; x++) b.set(x, 7, 17, Arch.stair(paleSt, Direction.SOUTH, Half.TOP));
        for (int z = 18; z <= 21; z++) {
            b.set(20, 7, z, Arch.stair(paleSt, Direction.EAST, Half.TOP));
            b.set(30, 7, z, Arch.stair(paleSt, Direction.WEST, Half.TOP));
        }
        LandmarkKit.line(b, 21, 8, 18, 29, 18, ArchBlocks.PALE_MASONRY_WALL.get());
        LandmarkKit.line(b, 21, 8, 19, 21, 21, ArchBlocks.PALE_MASONRY_WALL.get());
        LandmarkKit.line(b, 29, 8, 19, 29, 21, ArchBlocks.PALE_MASONRY_WALL.get());
        b.door(25, 8, kz1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        for (int x = 24; x <= 26; x++) b.bigDoor(x, 1, kz1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        pointedArchX(b, 23, 27, 4, kz1, paleSt, gold, null);
        for (int x : new int[]{23, 27}) b.hanging(x, 6, 20, ArchBlocks.BRASS_LANTERN.get());
        // ground floor: throne hall in the middle (x 18..32), banquet hall west, kitchen east
        b.fill(17, 1, kz1 + 1, 17, 6, kz2 - 1, stone);
        b.fill(33, 1, kz1 + 1, 33, 6, kz2 - 1, stone);
        b.clear(17, 1, 26, 17, 3, 27);
        b.clear(33, 1, 26, 33, 3, 27);
        for (int z = kz1 + 3; z <= kz2 - 4; z += 4) {
            b.fill(19, 1, z, 19, 6, z, col);
            b.fill(31, 1, z, 31, 6, z, col);
        }
        LandmarkKit.carpet(b, 24, 1, kz1 + 1, 26, kz2 - 5, Blocks.RED_CARPET);
        b.fill(21, 1, kz2 - 4, 29, 1, kz2 - 1, Blocks.QUARTZ_BLOCK);
        b.fill(23, 2, kz2 - 3, 27, 2, kz2 - 1, Blocks.QUARTZ_BLOCK);
        b.set(25, 3, kz2 - 2, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.set(25, 4, kz2 - 1, Blocks.GOLD_BLOCK);
        b.set(24, 3, kz2 - 2, Blocks.GOLD_BLOCK);
        b.set(26, 3, kz2 - 2, Blocks.GOLD_BLOCK);
        for (int x : new int[]{21, 29}) b.set(x, 5, kz2 - 1, banner(Blocks.PINK_WALL_BANNER, Direction.NORTH));
        for (int z = kz1 + 4; z <= kz2 - 4; z += 6) lamp(b, 25, 6, z);
        npc(b, "noble", 25, 3, kz2 - 3);
        npc(b, "guard", 22, 2, kz2 - 3);
        npc(b, "guard", 28, 2, kz2 - 3);
        npc(b, "guard", 21, 1, 27);
        // banquet hall
        longTableZ(b, 25, 42, 1, 13, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        b.fill(kx1 + 1, 1, kz2 - 1, 16, 2, kz2 - 1, Blocks.BOOKSHELF);
        for (int z = 26; z <= 42; z += 8) lamp(b, 13, 6, z);
        npc(b, "waiter", 11, 1, 30);
        // kitchen & storerooms
        kitchenX(b, 35, 1, kz2 - 1, Direction.NORTH, Buildings.LOOT_TAVERN);
        b.barrel(40, 1, kz2 - 1, Buildings.LOOT_WAREHOUSE);
        b.barrel(40, 1, kz2 - 2, Buildings.LOOT_WAREHOUSE);
        counterRow(b, 37, 38, 1, 34, Direction.NORTH);
        b.set(40, 1, 36, decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        lamp(b, 37, 6, 34);
        npc(b, "cook", 36, 1, 38);
        // grand stair (east hall) from the ground floor to floor 2, and on to floor 3
        stairsUp(b, 35, 1, 30, Direction.NORTH, 7, Blocks.QUARTZ_STAIRS, 3, stone);
        stairsUp(b, 38, 8, 44, Direction.NORTH, 6, Blocks.SPRUCE_STAIRS, 3, Blocks.SPRUCE_PLANKS);
        // second floor: royal chambers west, library east of the stairwell, chapel middle
        b.fill(17, 8, kz1 + 1, 17, 12, kz2 - 1, ArchBlocks.WHITE_PLASTER.get());
        b.fill(33, 8, kz1 + 1, 33, 12, kz2 - 1, ArchBlocks.WHITE_PLASTER.get());
        b.clear(17, 8, 25, 17, 10, 25);
        b.clear(33, 8, 25, 33, 10, 25);
        b.bed(11, 8, 40, Blocks.PINK_BED, Direction.SOUTH);
        b.bed(13, 8, 40, Blocks.PINK_BED, Direction.SOUTH);
        carpet(b, 10, 8, 32, 16, 38, Blocks.PINK_CARPET);
        b.chest(16, 8, 44, Direction.WEST, Buildings.LOOT_HOUSE);
        b.set(10, 8, 44, facing(Blocks.LOOM, Direction.EAST));
        b.set(10, 8, 24, Blocks.POTTED_PINK_TULIP);
        b.set(16, 8, 24, decor(ArchBlocks.FLOWER_PLANTER, Direction.SOUTH));
        lamp(b, 13, 12, 36);
        npc(b, "noble", 14, 8, 35);
        b.fill(19, 8, kz2 - 1, 31, 10, kz2 - 1, Blocks.BOOKSHELF);
        b.fill(19, 8, 30, 19, 10, 40, Blocks.BOOKSHELF);
        b.set(25, 8, 36, Blocks.ENCHANTING_TABLE);
        b.set(24, 8, 32, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(26, 8, 32, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        lamp(b, 25, 12, 34);
        npc(b, "priest", 23, 8, 36);
        // third floor: open hall for the guard captain, and the way to the roof space
        lamp(b, 25, 18, 30);
        b.chest(kx1 + 1, 14, kz1 + 1, Direction.SOUTH, Buildings.LOOT_MARINE);
        b.bed(11, 14, 42, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.set(14, 14, 24, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(13, 14, 24, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.WEST, Half.BOTTOM));
        b.set(12, 14, 26, s(Blocks.SMITHING_TABLE));
        npc(b, "guard", 30, 14, 30);

        // ---- the secret treasury under the throne: a hatch hidden behind the throne, iron door with a lever
        shell(b, 20, -7, 34, 32, -1, kz2, Blocks.DEEPSLATE_BRICKS);
        b.fill(21, -6, 35, 31, -6, kz2 - 1, Blocks.POLISHED_DEEPSLATE);
        hatch(b, 28, 1, kz2 - 2, -5, Direction.NORTH, Blocks.SPRUCE_TRAPDOOR, Blocks.QUARTZ_BLOCK);
        b.fill(21, -5, 39, 31, -2, 39, Blocks.DEEPSLATE_BRICKS);
        b.door(25, -5, 39, Blocks.IRON_DOOR, Direction.SOUTH);
        b.set(26, -4, 40, lever(Direction.SOUTH));
        for (int x = 22; x <= 30; x += 2) b.set(x, -5, 35, Blocks.GOLD_BLOCK);
        b.chest(23, -5, 37, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(27, -5, 37, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(25, -3, 36, lantern(Blocks.LANTERN, true));
        b.set(25, -2, 36, Blocks.DEEPSLATE_BRICKS);
        b.set(25, -3, 42, lantern(Blocks.LANTERN, true));
        b.set(25, -2, 42, Blocks.DEEPSLATE_BRICKS);
        mark(b, "treasure", 25, -5, 36);
        b.marker("arc_anchor", 25, 1, 12, tag("arc", "royal_court"));
    }

    private BluesLandmarks() {}
}
