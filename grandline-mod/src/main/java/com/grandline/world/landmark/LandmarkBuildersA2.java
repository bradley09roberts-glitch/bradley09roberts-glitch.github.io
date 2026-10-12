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

import static com.grandline.world.landmark.LandmarkBuildersA.*;
import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;

/** Landmark batch A, East Blue and Paradise ships and strongholds: the Baratie, Arlong Park and Drum Castle. */
final class LandmarkBuildersA2 {
    private LandmarkBuildersA2() {}

    // ================================================================== Baratie

    /** Half-width of the Baratie's fish hull at row z: a round head, a long body, a tapering stern and the tail stalk. */
    static int baratieHalf(int z) {
        if (z < 0 || z > 44) return -1;
        if (z <= 9) return (int) Math.round(9.4 * Math.sqrt(Math.max(0, 1 - Math.pow((9.5 - z) / 10.5, 2))));
        if (z <= 30) return 9;
        if (z <= 38) return (int) Math.round(9 - (z - 30) * 0.75);
        return 2;
    }

    private static boolean inHull(int x, int z, int cx) {
        int h = baratieHalf(z);
        return h >= 0 && Math.abs(x - cx) <= h;
    }

    private static boolean isShell(int x, int z, int cx) {
        return inHull(x, z, cx) && (!inHull(x - 1, z, cx) || !inHull(x + 1, z, cx) || !inHull(x, z - 1, cx) || !inHull(x, z + 1, cx));
    }

    /**
     * The Baratie (23x45, on the water): a fish-shaped restaurant ship. The hull is a fish in plan, teal-scaled above
     * the waterline, with the raised fish head at the bow (eyes, gills, a lipped mouth) carrying the forecastle deck,
     * the mast and two bow cannons; the pectoral fins open out as dining decks, the tail fin rises at the stern. On
     * the oak main deck stands the two-storey restaurant: cream plaster between dark posts, red shutters and planters,
     * a terracotta gable whose big front gable faces the bow, a lookout turret with the flag. Inside: the dining hall
     * with a bar, the kitchen behind a serving pass, the staff dorm and the owner-chef's cabin upstairs, a front
     * balcony and a stern terrace. A floating landing stage on the port quarter is the boarding point.
     */
    static void baratie(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, cx = w / 2;
        Mats m = Mats.of(BuildStyle.VILLAGE, b.seed);
        m.wall = S(ArchBlocks.CREAM_PLASTER);
        m.shutter = ArchBlocks.RED_SHUTTER.get();
        m.planters = true;
        m.window = S(ArchBlocks.MULLION_WINDOW);
        m.roof = S(ArchBlocks.TERRACOTTA_ROOF_TILES);
        m.roofStairs = S(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS);
        m.roofSlab = S(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.GABLE;
        m.eaveBrackets = true;
        m.lantern = S(ArchBlocks.BRASS_LANTERN);
        m.fence = S(Blocks.DARK_OAK_FENCE);
        BlockState teal = S(ArchBlocks.TEAL_ROOF_TILES), dark = S(Blocks.DARK_OAK_PLANKS), spruce = S(Blocks.SPRUCE_PLANKS);
        BlockState deck = S(Blocks.OAK_PLANKS), cream = S(ArchBlocks.CREAM_PLASTER), post = col(ArchBlocks.DARK_BEAM), chair = S(Blocks.SPRUCE_STAIRS);
        b.clear(0, 1, 0, w - 1, 28, d - 1);
        // ---- hull: fish plan, dark strakes below, teal scales at the waterline, oak deck, dark rail
        for (int z = 0; z < d; z++) {
            int h = baratieHalf(z);
            for (int x = cx - h; x <= cx + h; x++) {
                boolean shell = isShell(x, z, cx);
                if (Math.abs(x - cx) <= h - 2) b.set(x, -3, z, dark);
                b.set(x, -2, z, shell ? dark : spruce);
                b.set(x, -1, z, shell ? dark : spruce);
                b.set(x, 0, z, shell ? teal : spruce);
                b.set(x, 1, z, shell && z >= 9 ? teal : deck);
                if (shell && z >= 9 && z <= 38) {
                    boolean n = isShell(x, z - 1, cx), so = isShell(x, z + 1, cx), e = isShell(x + 1, z, cx), wv = isShell(x - 1, z, cx);
                    b.set(x, 2, z, LandmarkKit.connect(Blocks.DARK_OAK_FENCE, n, e, so, wv));
                }
            }
        }
        // ---- the fish head (bow): solid, rising to the forecastle deck at y = 5
        for (int z = 0; z <= 8; z++) {
            int h = baratieHalf(z), topY = z == 0 ? 3 : z == 1 ? 4 : 5;
            for (int x = cx - h; x <= cx + h; x++) {
                boolean edge = Math.abs(x - cx) == h || z == 0 || Math.abs(x - cx) > baratieHalf(z - 1);
                for (int y = 1; y < topY; y++) b.set(x, y, z, edge ? teal : z == 8 ? dark : spruce);
                Direction in = (z == 0 || Math.abs(x - cx) > baratieHalf(z - 1)) ? SOUTH : x < cx ? EAST : WEST;
                b.set(x, topY, z, edge ? up(S(ArchBlocks.TEAL_ROOF_TILE_STAIRS), in) : S(ArchBlocks.DOCK_PLANKS));
            }
        }
        // mouth with lips, eyes and gills
        b.fill(cx - 3, 0, 0, cx + 3, 0, 0, S(Blocks.RED_TERRACOTTA));
        b.fill(cx - 3, 2, 0, cx + 3, 2, 0, S(Blocks.RED_TERRACOTTA));
        b.fill(cx - 3, 1, 0, cx + 3, 1, 0, S(Blocks.BLACK_CONCRETE));
        for (int side = -1; side <= 1; side += 2) {
            for (int z = 3; z <= 5; z++)
                for (int y = 2; y <= 4; y++) b.set(cx + side * baratieHalf(z), y, z, S(z == 4 && y == 3 ? Blocks.BLACK_CONCRETE : Blocks.WHITE_CONCRETE));
            for (int y = 0; y <= 4; y++) b.set(cx + side * baratieHalf(7), y, 7, dark);
        }
        // forecastle rail, mast with yards, furled sail, crow's nest and flag, bow cannons
        for (int z = 2; z <= 8; z++) {
            int h = baratieHalf(z) - 1;
            b.set(cx - h, 6, z, LandmarkKit.connect(Blocks.DARK_OAK_FENCE, z > 2, false, z < 8, false));
            b.set(cx + h, 6, z, LandmarkKit.connect(Blocks.DARK_OAK_FENCE, z > 2, false, z < 8, false));
        }
        int hf = baratieHalf(2) - 1;
        for (int x = cx - hf; x <= cx + hf; x++) b.set(x, 6, 2, LandmarkKit.connect(Blocks.DARK_OAK_FENCE, false, x < cx + hf, x == cx - hf || x == cx + hf, x > cx - hf));
        b.fill(cx, 6, 5, cx, 21, 5, col(Blocks.SPRUCE_LOG));
        b.fill(cx - 5, 17, 5, cx + 5, 17, 5, Arch.axis(S(Blocks.SPRUCE_LOG), Direction.Axis.X));
        b.fill(cx - 4, 16, 5, cx + 4, 16, 5, S(Blocks.WHITE_WOOL));
        b.fill(cx - 3, 12, 5, cx + 3, 12, 5, Arch.axis(S(Blocks.SPRUCE_LOG), Direction.Axis.X));
        b.fill(cx - 2, 11, 5, cx + 2, 11, 5, S(Blocks.WHITE_WOOL));
        b.fill(cx - 1, 19, 4, cx + 1, 19, 6, slabB(S(Blocks.SPRUCE_SLAB)));
        b.set(cx, 19, 5, col(Blocks.SPRUCE_LOG));
        LandmarkKit.outline(b, cx - 1, 20, 4, cx + 1, 6, Blocks.SPRUCE_FENCE);
        b.set(cx, 21, 4, wallBanner(Blocks.BLACK_WALL_BANNER, NORTH));
        b.set(cx - 4, 6, 3, decor(ArchBlocks.DECK_CANNON, NORTH));
        b.set(cx + 4, 6, 3, decor(ArchBlocks.DECK_CANNON, NORTH));
        b.set(cx - 2, 6, 7, decor(ArchBlocks.ROPE_COIL, NORTH));
        // stairs from the bow deck up to the forecastle, both sides
        for (int x : new int[]{cx - 4, cx + 4})
            for (int i = 0; i < 4; i++) {
                b.set(x, 2 + i, 12 - i, up(S(Blocks.DARK_OAK_STAIRS), NORTH));
                if (i > 0) b.fill(x, 2, 12 - i, x, 1 + i, 12 - i, dark);
            }
        // ---- pectoral fins: dining decks either side (the rail opens onto them)
        for (int side = -1; side <= 1; side += 2) {
            for (int z = 18; z <= 25; z++) {
                int reach = z == 18 || z == 25 ? 1 : 2;
                for (int k = 1; k <= reach; k++) {
                    int x = cx + side * (9 + k);
                    b.set(x, 0, z, teal);
                    b.set(x, 1, z, S(ArchBlocks.DOCK_PLANKS));
                }
                int xo = cx + side * (9 + reach);
                b.set(xo, 1, z, teal);
                b.set(xo, 2, z, LandmarkKit.connect(Blocks.DARK_OAK_FENCE, z > 18, false, z < 25, false));
                if (z >= 19 && z <= 24) b.set(cx + side * 9, 2, z, Blocks.AIR.defaultBlockState());
            }
            int tx = cx + side * 9;
            b.set(tx, 2, 20, decor(ArchBlocks.PLANK_TABLE, NORTH));
            b.set(tx, 2, 19, up(chair, NORTH));
            b.set(tx, 2, 21, up(chair, SOUTH));
            b.set(tx, 2, 23, decor(ArchBlocks.PLANK_TABLE, NORTH));
            b.set(tx, 2, 22, up(chair, NORTH));
            b.set(tx, 2, 24, up(chair, SOUTH));
        }
        // ---- tail fin at the stern
        for (int z = 38; z <= 44; z++) {
            int topY = 2 + (int) ((z - 37) * 1.6);
            for (int y = 1; y <= topY; y++) {
                boolean thin = y > 6;
                for (int x = cx - 1; x <= cx + 1; x++) if (!thin || x == cx) b.set(x, y, z, (y + z) % 4 == 0 ? dark : teal);
            }
            b.set(cx, topY + 1, z, slabB(S(ArchBlocks.TEAL_ROOF_TILE_SLAB)));
        }
        // ---- landing stage on the port quarter
        b.fill(0, 0, 26, 1, 0, 30, S(ArchBlocks.DOCK_PLANKS));
        b.fill(0, -3, 26, 0, -1, 26, col(ArchBlocks.WEATHERED_BEAM));
        b.fill(0, -3, 30, 0, -1, 30, col(ArchBlocks.WEATHERED_BEAM));
        b.set(0, 1, 26, decor(ArchBlocks.BOLLARD, WEST));
        b.set(0, 1, 30, decor(ArchBlocks.BOLLARD, WEST));
        b.set(1, 1, 30, decor(ArchBlocks.ROPE_COIL, NORTH));
        b.clear(cx - 9, 2, 27, cx - 9, 2, 29);
        b.marker("dock", 1, 1, 28, new CompoundTag());

        // ---- the restaurant: x 5..17; ground floor z 13..31, upper floor z 15..29
        int x1 = 5, x2 = 17, z1 = 13, z2 = 31, u1 = 15, u2 = 29;
        box(b, x1, 2, z1, x2, 4, z2, cream);
        b.clear(x1 + 1, 2, z1 + 1, x2 - 1, 4, z2 - 1);
        b.fill(x1, 5, z1, x2, 5, z2, deck);
        b.fill(x1, 5, z1, x2, 5, z1, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
        b.fill(x1, 5, z2, x2, 5, z2, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
        b.fill(x1, 5, z1 + 1, x1, 5, z2 - 1, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
        b.fill(x2, 5, z1 + 1, x2, 5, z2 - 1, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
        box(b, x1, 6, u1, x2, 8, u2, cream);
        b.clear(x1 + 1, 6, u1 + 1, x2 - 1, 8, u2 - 1);
        for (int z = z1; z <= z2; z += 6) {
            b.fill(x1, 2, z, x1, 4, z, post);
            b.fill(x2, 2, z, x2, 4, z, post);
        }
        for (int x : new int[]{x1, x2}) {
            b.fill(x, 2, z2, x, 4, z2, post);
            for (int z = u1; z <= u2; z += 7) b.fill(x, 6, z, x, 8, z, post);
        }
        b.fill(x1 + 4, 2, z1, x1 + 4, 4, z1, post);
        b.fill(x2 - 4, 2, z1, x2 - 4, 4, z1, post);
        b.fill(x1 + 1, 9, u1, x2 - 1, 9, u2, deck);
        Arch.gable(b, m, x1, u1, x2, u2, 9, false, 1, cream);
        // front gable: a round window
        b.set(cx, 13, u1, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
        b.set(cx - 1, 12, u1, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
        b.set(cx + 1, 12, u1, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
        b.set(cx, 11, u1, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
        b.set(cx, 12, u1, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
        // lookout turret through the ridge
        box(b, cx - 1, 9, 21, cx + 1, 18, 23, cream);
        corners(b, cx - 1, 9, 21, cx + 1, 18, 23, post);
        b.set(cx, 16, 21, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
        b.set(cx, 16, 23, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
        b.set(cx - 1, 16, 22, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.Z));
        b.set(cx + 1, 16, 22, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.Z));
        b.clear(cx, 9, 22, cx, 17, 22);
        int tTop = Arch.hip(b, m, cx - 1, 21, cx + 1, 23, 19, 1);
        b.fill(cx, tTop + 1, 22, cx, tTop + 4, 22, post(Blocks.IRON_BARS));
        b.set(cx, tTop + 4, 21, wallBanner(Blocks.BLACK_WALL_BANNER, NORTH));
        // entrance, balcony over it, stern terrace
        b.bigDoor(cx, 2, z1, Blocks.SPRUCE_DOOR, NORTH);
        b.bigDoor(cx, 2, z2, Blocks.SPRUCE_DOOR, SOUTH);
        for (int x = cx - 1; x <= cx + 1; x++) b.set(x, 4, z1 - 1, decor(ArchBlocks.RED_AWNING, NORTH));
        b.door(cx, 6, u1, Blocks.SPRUCE_DOOR, NORTH);
        b.door(cx, 6, u2, Blocks.SPRUCE_DOOR, SOUTH);
        rail(b, x1, 6, z1, x2, z1, Blocks.DARK_OAK_FENCE);
        rail(b, x1, 6, z2, x2, z2, Blocks.DARK_OAK_FENCE);
        rail(b, x1, 6, z1, x1, u1 - 1, Blocks.DARK_OAK_FENCE);
        rail(b, x2, 6, z1, x2, u1 - 1, Blocks.DARK_OAK_FENCE);
        rail(b, x1, 6, u2 + 1, x1, z2, Blocks.DARK_OAK_FENCE);
        rail(b, x2, 6, u2 + 1, x2, z2, Blocks.DARK_OAK_FENCE);
        b.set(x1 + 2, 6, z1 + 1, decor(ArchBlocks.FLOWER_PLANTER, NORTH));
        b.set(x2 - 2, 6, z1 + 1, decor(ArchBlocks.FLOWER_PLANTER, NORTH));
        b.set(x1 + 2, 6, z2 - 1, decor(ArchBlocks.PLANK_BENCH, NORTH));
        // windows: ground floor (dining, kitchen), upper floor, front
        for (int x : new int[]{x1, x2}) {
            Direction out = x == x1 ? WEST : EAST;
            for (int z : new int[]{16, 21, 27}) Arch.window(b, m, x, 2, z, out, 2, true);
            for (int z : new int[]{18, 25}) Arch.window(b, m, x, 6, z, out, 2, true);
        }
        Arch.window(b, m, 7, 2, z1, NORTH, 2, true);
        Arch.window(b, m, 15, 2, z1, NORTH, 2, true);
        Arch.window(b, m, 8, 6, u1, NORTH, 2, true);
        Arch.window(b, m, 14, 6, u1, NORTH, 2, true);
        Arch.window(b, m, 8, 6, u2, SOUTH, 2, true);
        Arch.window(b, m, 14, 6, u2, SOUTH, 2, true);
        for (int x : new int[]{x1, x2}) {
            Direction out = x == x1 ? WEST : EAST;
            Arch.wallLantern(b, m, x, 4, 19, out);
            Arch.wallLantern(b, m, x, 4, 25, out);
        }

        // ---- dining hall (z 14..24): tables, red carpet aisle, bar at the pass, stair up
        b.fill(10, 2, 14, 12, 2, 22, S(Blocks.RED_CARPET));
        for (int z : new int[]{15, 19}) {
            Arch.tableSet(b, 7, 2, z, chair);
            Arch.tableSet(b, 15, 2, z, chair);
            b.set(7, 3, z, LandmarkKit.candles(Blocks.WHITE_CANDLE, 2));
            b.set(15, 3, z, LandmarkKit.candles(Blocks.WHITE_CANDLE, 2));
            lamp(b, 7, 4, z);
            lamp(b, 15, 4, z);
        }
        lamp(b, cx, 4, 18);
        // kitchen wall with the serving pass and door
        b.fill(x1 + 1, 2, 25, x2 - 1, 4, 25, cream);
        b.clear(cx, 2, 25, cx, 4, 25);
        for (int x : new int[]{8, 9, 13, 14}) {
            b.set(x, 2, 25, decor(ArchBlocks.SHOP_COUNTER, NORTH));
            b.clear(x, 3, 25, x, 3, 25);
        }
        b.fill(14, 2, 23, 16, 2, 23, decor(ArchBlocks.SHOP_COUNTER, NORTH));
        b.set(16, 2, 24, decor(ArchBlocks.GOODS_SHELF, WEST));
        b.set(14, 3, 23, Blocks.BREWING_STAND.defaultBlockState());
        Arch.staircase(b, S(Blocks.SPRUCE_STAIRS), 6, 2, 24, NORTH, 4);
        // kitchen (z 26..30)
        b.set(7, 2, 30, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, NORTH));
        b.set(8, 2, 30, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, NORTH));
        b.set(9, 2, 30, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, NORTH));
        b.set(13, 2, 30, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        b.set(14, 2, 30, Blocks.CRAFTING_TABLE);
        b.set(15, 2, 30, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        b.barrel(16, 2, 29, Buildings.LOOT_TAVERN);
        b.set(16, 2, 27, decor(ArchBlocks.GOODS_SHELF, WEST));
        b.set(6, 2, 27, decor(ArchBlocks.GOODS_SHELF, EAST));
        b.set(6, 2, 28, decor(ArchBlocks.CARGO_SACKS, EAST));
        for (int x = 9; x <= 13; x++) if (x != cx) b.set(x, 2, 29, decor(ArchBlocks.SHOP_COUNTER, NORTH));
        b.set(9, 3, 29, Blocks.POTTED_RED_TULIP);
        lamp(b, 8, 4, 28);
        lamp(b, 14, 4, 28);
        // upper floor: staff dorm (west), owner-chef's cabin (east), split by a partition
        b.fill(cx, 6, u1 + 1, cx, 8, u2 - 1, cream);
        b.clear(cx, 6, 20, cx, 7, 20);
        b.clear(cx, 6, u1 + 1, cx, 7, u1 + 1);
        b.clear(cx, 6, u2 - 1, cx, 7, u2 - 1);
        for (int z = 22; z <= 27; z += 2) b.bed(8, 6, z, Blocks.WHITE_BED, EAST);
        b.set(7, 6, 16, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.bed(15, 6, 26, Blocks.RED_BED, SOUTH);
        desk(b, 14, 6, 18, WEST, chair);
        b.fill(16, 6, 17, 16, 7, 20, S(Blocks.BOOKSHELF));
        b.set(13, 6, 27, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(13, 7, 27, LandmarkKit.candles(Blocks.CANDLE, 1));
        b.set(16, 6, 23, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        lamp(b, 8, 8, 20);
        lamp(b, 14, 8, 22);
        // stern deck: supplies
        for (int x = 8; x <= 14; x += 3) Arch.cargo(b, x, 2, 34, x);
        b.set(cx, 2, 36, decor(ArchBlocks.ROPE_COIL, NORTH));
        // people
        LandmarkKit.npc(b, "head_chef", cx, 2, 27);
        LandmarkKit.npc(b, "cook", 8, 2, 27);
        LandmarkKit.npc(b, "cook", 14, 2, 27);
        LandmarkKit.npc(b, "waiter", 10, 2, 17);
    }

    // ================================================================== Arlong Park

    private static final String[] SHARK_CREST = {
            "..BBBBB..",
            ".BBBBBBB.",
            "BBWBBBWBB",
            "BBBBBBBBB",
            "BRWRWRWRB",
            ".BWRWRWB.",
            "..BBBBB.."};

    /**
     * Arlong Park (31x31, Cocoyasi): the Fish-Man compound. A cream-and-sandstone curtain wall with teal-capped
     * corner turrets and a gate under the big "ARLONG" signboard; inside, the sea-water pool split by a plank bridge
     * (its east half runs out under the wall to the sea), loungers and palms; the throne terrace where Arlong holds
     * court; and the four-storey central tower with a teal eave skirt, the shark crest on its face and a flared hip
     * roof crowned by a belvedere. Flanking wings hold the crew's barracks and the storehouse; the tower holds the
     * crew hall, the bunk room, Nami's map room (the hoard chest) and Arlong's quarters.
     */
    static void arlongPark(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = w / 2;
        Mats m = Mats.of(BuildStyle.VILLAGE, b.seed);
        m.wall = S(ArchBlocks.CREAM_PLASTER);
        m.upperWall = m.wall;
        m.base = S(ArchBlocks.DRESSED_SANDSTONE);
        m.roof = S(ArchBlocks.TEAL_ROOF_TILES);
        m.roofStairs = S(ArchBlocks.TEAL_ROOF_TILE_STAIRS);
        m.roofSlab = S(ArchBlocks.TEAL_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.FLARED;
        m.window = S(ArchBlocks.LATTICE_WINDOW);
        m.shutter = null;
        m.planters = false;
        m.sill = S(ArchBlocks.DRESSED_SANDSTONE_STAIRS);
        m.eaveBrackets = false;
        m.lantern = S(ArchBlocks.WHITE_PAPER_LANTERN);
        m.fence = S(Blocks.DARK_OAK_FENCE);
        BlockState cream = S(ArchBlocks.CREAM_PLASTER), sand = S(ArchBlocks.DRESSED_SANDSTONE), sandStairs = S(ArchBlocks.DRESSED_SANDSTONE_STAIRS);
        BlockState harbour = S(ArchBlocks.HARBOUR_STONE), tealSlab = slabB(S(ArchBlocks.TEAL_ROOF_TILE_SLAB)), chair = S(Blocks.DARK_OAK_STAIRS);
        b.foundation(0, 0, w - 1, d - 1, ArchBlocks.HARBOUR_STONE.get(), 14);
        b.clear(0, 1, 0, w - 1, 30, d - 1);
        LandmarkKit.patchGround(b, 7, 0, ArchBlocks.HARBOUR_STONE.get(), ArchBlocks.FIELDSTONE.get(), Blocks.SMOOTH_SANDSTONE);
        // ---- curtain wall: sandstone base, cream body, teal coping
        box(b, 0, 1, 0, w - 1, 2, d - 1, sand);
        box(b, 0, 3, 0, w - 1, 6, d - 1, cream);
        ring(b, 0, 7, 0, w - 1, d - 1, tealSlab);
        for (int i = 3; i < w - 3; i += 4) {
            b.fill(i, 1, 1, i, 6, 1, sand);
            b.fill(i, 1, d - 2, i, 6, d - 2, sand);
            b.fill(1, 1, i, 1, 6, i, sand);
            b.fill(w - 2, 1, i, w - 2, 6, i, sand);
        }
        // corner turrets with flared teal caps
        for (int[] t : new int[][]{{0, 0}, {w - 3, 0}, {0, d - 3}, {w - 3, d - 3}}) {
            b.fill(t[0], 1, t[1], t[0] + 2, 9, t[1] + 2, sand);
            b.fill(t[0], 3, t[1], t[0] + 2, 8, t[1] + 2, cream);
            corners(b, t[0], 3, t[1], t[0] + 2, 8, t[1] + 2, sand);
            Arch.hip(b, m, t[0], t[1], t[0] + 2, t[1] + 2, 10, 0);
            b.set(t[0] + 1, 12, t[1] + 1, LandmarkKit.lantern(ArchBlocks.RED_PAPER_LANTERN.get(), false));
        }
        // ---- gate under the ARLONG signboard
        b.fill(11, 1, 0, 13, 15, 1, sand);
        b.fill(17, 1, 0, 19, 15, 1, sand);
        b.clear(14, 1, 0, 16, 6, 1);
        b.set(14, 6, 0, top(sandStairs, EAST));
        b.set(16, 6, 0, top(sandStairs, WEST));
        b.set(14, 6, 1, top(sandStairs, EAST));
        b.set(16, 6, 1, top(sandStairs, WEST));
        b.fill(2, 8, 0, 28, 14, 0, cream);
        b.fill(2, 8, 0, 2, 14, 0, sand);
        b.fill(28, 8, 0, 28, 14, 0, sand);
        b.fill(2, 14, 0, 28, 14, 0, sand);
        b.fill(2, 8, 0, 28, 8, 0, sand);
        textNorthWall(b, "ARLONG", c + 12, 13, 0, S(Blocks.BLUE_CONCRETE));
        b.fill(2, 15, 0, 28, 15, 0, up(S(ArchBlocks.TEAL_ROOF_TILE_STAIRS), SOUTH));
        b.fill(2, 15, 1, 28, 15, 1, S(ArchBlocks.TEAL_ROOF_TILES));
        for (int x = 4; x <= 26; x += 5) b.fill(x, 8, 1, x, 14, 1, col(ArchBlocks.DARK_BEAM));
        LandmarkKit.lampPost(b, 10, 1, 2, Blocks.DARK_OAK_FENCE, 2, ArchBlocks.WHITE_PAPER_LANTERN.get());
        LandmarkKit.lampPost(b, 20, 1, 2, Blocks.DARK_OAK_FENCE, 2, ArchBlocks.WHITE_PAPER_LANTERN.get());
        b.fill(14, 0, 0, 16, 0, 5, sand);

        // ---- the sea-water pool, bridge, channel out to the sea
        b.fill(4, -3, 5, 26, 0, 15, harbour);
        b.fill(5, -3, 6, 25, -3, 14, S(Blocks.DARK_PRISMARINE));
        b.fill(5, -2, 6, 25, 0, 14, S(Blocks.WATER));
        b.fill(13, -2, 6, 17, 0, 14, harbour);
        b.fill(13, 0, 6, 17, 0, 14, S(ArchBlocks.DOCK_PLANKS));
        rail(b, 13, 1, 6, 13, 14, Blocks.DARK_OAK_FENCE);
        rail(b, 17, 1, 6, 17, 14, Blocks.DARK_OAK_FENCE);
        b.fill(26, -3, 9, w - 1, -3, 11, S(Blocks.DARK_PRISMARINE));
        b.fill(26, -2, 9, w - 1, 0, 11, S(Blocks.WATER));
        b.fill(26, -2, 8, w - 1, 0, 8, harbour);
        b.fill(26, -2, 12, w - 1, 0, 12, harbour);
        b.fill(w - 1, 1, 9, w - 1, 2, 11, Arch.along(S(Blocks.IRON_BARS), Direction.Axis.Z));
        // loungers and parasols by the pool, palms
        for (int x : new int[]{7, 10, 20, 23}) {
            b.set(x, 1, 16, up(chair, SOUTH));
            b.set(x, 1, 17, slabB(S(Blocks.DARK_OAK_SLAB)));
        }
        for (int x : new int[]{8, 22}) {
            b.fill(x, 1, 17, x, 2, 17, post(Blocks.DARK_OAK_FENCE));
            b.set(x, 3, 17, decor(ArchBlocks.RED_AWNING, NORTH));
        }
        LandmarkKit.palm(b, 5, 1, 18, 6, 31);
        LandmarkKit.palm(b, 25, 1, 18, 6, 47);
        LandmarkKit.palm(b, 5, 1, 4, 6, 59);
        LandmarkKit.palm(b, 25, 1, 4, 6, 61);

        // ---- throne terrace in front of the tower
        b.fill(11, 1, 15, 19, 1, 17, sand);
        for (int x = 11; x <= 19; x++) b.set(x, 1, 15, up(sandStairs, SOUTH));
        b.set(c, 2, 16, up(S(Blocks.DARK_OAK_STAIRS), SOUTH));
        b.set(c - 1, 2, 16, top(S(Blocks.DARK_OAK_STAIRS), EAST));
        b.set(c + 1, 2, 16, top(S(Blocks.DARK_OAK_STAIRS), WEST));
        b.set(c - 1, 3, 16, post(Blocks.DARK_OAK_FENCE));
        b.set(c + 1, 3, 16, post(Blocks.DARK_OAK_FENCE));
        for (int x : new int[]{11, 19}) {
            b.fill(x, 2, 17, x, 4, 17, post(Blocks.DARK_OAK_FENCE));
            b.set(x, 5, 17, LandmarkKit.lantern(ArchBlocks.RED_PAPER_LANTERN.get(), false));
        }

        // ---- the central tower: x 10..20, z 18..28, five storeys
        int tx1 = 10, tx2 = 20, tz1 = 18, tz2 = 28;
        for (int f = 0; f < 5; f++) {
            int y = 4 * f;
            b.fill(tx1, y, tz1, tx2, y, tz2, S(Blocks.SPRUCE_PLANKS));
            box(b, tx1, y + 1, tz1, tx2, y + 3, tz2, f == 0 ? sand : cream);
            b.clear(tx1 + 1, y + 1, tz1 + 1, tx2 - 1, y + 3, tz2 - 1);
            if (f > 0) ring(b, tx1, y, tz1, tx2, tz2, sand);
        }
        b.fill(tx1, 0, tz1, tx2, 0, tz2, harbour);
        b.fill(tx1, 20, tz1, tx2, 20, tz2, sand);
        corners(b, tx1, 1, tz1, tx2, 19, tz2, sand);
        skirt(b, S(ArchBlocks.TEAL_ROOF_TILE_STAIRS), tx1, tz1, tx2, tz2, 11, 2, 2);
        int roofTop = Arch.hip(b, m, tx1, tz1, tx2, tz2, 21, 1);
        // belvedere crowning the roof
        b.fill(14, roofTop - 1, 22, 16, roofTop - 1, 24, S(ArchBlocks.TEAL_ROOF_TILES));
        corners(b, 14, roofTop, 22, 16, roofTop + 1, 24, col(ArchBlocks.DARK_BEAM));
        b.clear(15, roofTop, 23, 15, roofTop + 1, 23);
        b.set(15, roofTop + 2, 23, LandmarkKit.lantern(ArchBlocks.RED_PAPER_LANTERN.get(), true));
        int bTop = Arch.hip(b, m, 14, 22, 16, 24, roofTop + 2, 1);
        b.set(15, bTop + 1, 23, Blocks.LIGHTNING_ROD.defaultBlockState());
        // the shark crest on the tower face
        pixels(b, SHARK_CREST, c + 4, 19, tz1, -1, 0, 0, 0, -1, 0, ch -> switch (ch) {
            case 'B' -> S(Blocks.CYAN_TERRACOTTA);
            case 'W' -> S(Blocks.WHITE_CONCRETE);
            case 'R' -> S(Blocks.RED_CONCRETE);
            default -> null;
        });
        // windows and the door
        for (int f = 0; f < 5; f++) {
            int y = 4 * f + 1;
            for (int z = 21; z <= 25; z += 4) {
                Arch.window(b, m, tx1, y, z, WEST, 2, false);
                Arch.window(b, m, tx2, y, z, EAST, 2, false);
            }
            for (int x = 12; x <= 18; x += 3) {
                Arch.window(b, m, x, y, tz2, SOUTH, 2, false);
                if (f < 3 && x != c) Arch.window(b, m, x, y, tz1, NORTH, 2, false);
            }
        }
        b.bigDoor(c, 1, tz1, Blocks.DARK_OAK_DOOR, NORTH);
        // ---- wings: storehouse (west) and crew barracks (east)
        for (int side = 0; side < 2; side++) {
            int wx1 = side == 0 ? 3 : 22, wx2 = wx1 + 5, wz1 = 20, wz2 = 28;
            b.fill(wx1, 0, wz1, wx2, 0, wz2, S(Blocks.SPRUCE_PLANKS));
            box(b, wx1, 1, wz1, wx2, 1, wz2, sand);
            box(b, wx1, 2, wz1, wx2, 3, wz2, cream);
            corners(b, wx1, 1, wz1, wx2, 3, wz2, sand);
            b.clear(wx1 + 1, 1, wz1 + 1, wx2 - 1, 3, wz2 - 1);
            b.fill(wx1, 4, wz1, wx2, 4, wz2, S(Blocks.SPRUCE_PLANKS));
            Arch.hip(b, m, wx1, wz1, wx2, wz2, 4, 1);
            int dx = side == 0 ? wx1 + 2 : wx2 - 2;
            b.bigDoor(dx, 1, wz1, Blocks.DARK_OAK_DOOR, NORTH);
            Arch.window(b, m, side == 0 ? wx1 + 4 : wx1 + 1, 2, wz1, NORTH, 1, false);
            Arch.window(b, m, side == 0 ? wx1 : wx2, 2, 24, side == 0 ? WEST : EAST, 1, false);
            b.hanging(dx, 3, 24, ArchBlocks.WHITE_PAPER_LANTERN.get());
        }
        for (int z = 22; z <= 27; z += 2) Arch.cargo(b, 4, 1, z, z);
        b.set(7, 1, 27, decor(ArchBlocks.DECK_CANNON, NORTH));
        b.set(7, 1, 22, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(6, 1, 27, decor(ArchBlocks.FISHING_NET, NORTH));
        for (int z = 22; z <= 26; z += 2) b.bed(25, 1, z, Blocks.CYAN_BED, EAST);
        b.set(23, 1, 27, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));

        // ---- tower interiors: crew hall, bunk room, Nami's map room, Arlong's quarters
        switchback(b, S(Blocks.SPRUCE_STAIRS), 18, 21, 1, 0, SOUTH, 0, 4);
        LandmarkKit.longTableZ(b, 21, 26, 1, 13, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
        for (int z = 20; z <= 26; z += 3) b.set(11, 1, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(11, 2, 23, S(Blocks.BONE_BLOCK));
        b.set(16, 1, 27, decor(ArchBlocks.GOODS_SHELF, NORTH));
        b.hanging(14, 3, 23, ArchBlocks.RED_PAPER_LANTERN.get());
        for (int z = 19; z <= 25; z += 3) b.bed(11, 5, z, Blocks.CYAN_BED, EAST);
        b.set(14, 5, 27, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.hanging(14, 7, 23, ArchBlocks.WHITE_PAPER_LANTERN.get());
        // floor 8: the armoury and trophy room
        for (int z = 19; z <= 25; z += 3) b.set(11, 9, z, decor(ArchBlocks.DECK_CANNON, WEST));
        b.fill(12, 9, 27, 16, 9, 27, decor(ArchBlocks.GOODS_SHELF, NORTH));
        b.set(14, 9, 23, S(Blocks.BONE_BLOCK));
        b.set(14, 10, 23, Blocks.SKELETON_SKULL.defaultBlockState());
        b.set(c, 11, 19, wallBanner(Blocks.CYAN_WALL_BANNER, SOUTH));
        b.hanging(14, 11, 21, ArchBlocks.WHITE_PAPER_LANTERN.get());
        b.set(11, 13, 19, Blocks.CARTOGRAPHY_TABLE);
        b.set(12, 13, 19, Blocks.CARTOGRAPHY_TABLE);
        b.set(14, 13, 21, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(14, 13, 22, up(S(Blocks.SPRUCE_STAIRS), SOUTH));
        b.set(14, 14, 21, LandmarkKit.candles(Blocks.CANDLE, 3));
        b.set(11, 13, 22, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, EAST));
        b.fill(11, 13, 24, 11, 14, 26, S(Blocks.BOOKSHELF));
        b.fill(12, 13, 27, 15, 14, 27, S(Blocks.BOOKSHELF));
        b.chest(16, 13, 27, NORTH, Buildings.LOOT_TREASURE);
        b.hanging(14, 15, 24, ArchBlocks.WHITE_PAPER_LANTERN.get());
        b.bed(12, 17, 26, Blocks.BLUE_BED, SOUTH);
        b.bed(13, 17, 26, Blocks.BLUE_BED, SOUTH);
        b.set(c, 17, 19, up(S(Blocks.DARK_OAK_STAIRS), NORTH));
        b.set(11, 17, 19, wallBanner(Blocks.CYAN_WALL_BANNER, SOUTH));
        b.set(16, 17, 21, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(16, 18, 21, Blocks.POTTED_DEAD_BUSH);
        b.set(11, 17, 22, S(Blocks.BONE_BLOCK));
        b.hanging(14, 19, 23, ArchBlocks.RED_PAPER_LANTERN.get());

        // ---- people and markers
        LandmarkKit.npc(b, "fishman_boss", c - 2, 2, 16);
        LandmarkKit.npc(b, "fishman_brute", 3, 1, 10);
        LandmarkKit.npc(b, "fishman_brute", 27, 1, 14);
        LandmarkKit.npc(b, "fishman_guard", 12, 1, 3);
        LandmarkKit.npc(b, "fishman_guard", 18, 1, 3);
        b.marker("arc_anchor", c, 1, 4, LandmarkKit.tag("arc", "fishman_occupation"));
    }

    // ================================================================== Drum Castle

    /**
     * Drum Castle (25x25) on the flat top of the drum rock: a square keep of grey stone with pale bands, four round
     * corner towers capped with ice-blue domes, and the tall central tower with its own dome and lookout, reached
     * by a spiral stair from the crenellated roof. A gabled entrance porch faces the approach; snow lies on the
     * court and the roofs, and pink petals by the door remember the doctor's cherry blossoms. Inside: the infirmary
     * ward and apothecary on the ground floor, the old doctor's study and library above, the store and the young
     * doctor's room on the second floor, linked by a switchback stair.
     */
    static void drumCastle(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = w / 2;
        Mats m = Mats.of(BuildStyle.SNOW, b.seed);
        m.shutter = ArchBlocks.RED_SHUTTER.get();
        BlockState stone = S(Blocks.STONE_BRICKS), mas = S(ArchBlocks.PALE_MASONRY), field = S(ArchBlocks.FIELDSTONE);
        BlockState dome = S(ArchBlocks.SKY_ROOF_TILES), chair = S(Blocks.SPRUCE_STAIRS), snow = Blocks.SNOW.defaultBlockState();
        b.foundation(0, 0, w - 1, d - 1, ArchBlocks.FIELDSTONE.get(), 10);
        b.clear(0, 1, 0, w - 1, 36, d - 1);
        LandmarkKit.patchGround(b, 5, 0, Blocks.SNOW_BLOCK, ArchBlocks.FIELDSTONE.get(), Blocks.SNOW_BLOCK, Blocks.PACKED_ICE);
        b.fill(10, 0, 0, 14, 0, 6, field);
        // ---- keep: x 6..18, z 7..19, three storeys, crenellated roof deck at y = 12
        int k1 = 6, k2 = 18, kz1 = 7, kz2 = 19;
        for (int f = 0; f < 3; f++) {
            int y = 4 * f;
            b.fill(k1, y, kz1, k2, y, kz2, f == 0 ? S(Blocks.POLISHED_ANDESITE) : S(Blocks.SPRUCE_PLANKS));
            box(b, k1, y + 1, kz1, k2, y + 3, kz2, stone);
            b.clear(k1 + 1, y + 1, kz1 + 1, k2 - 1, y + 3, kz2 - 1);
            if (f > 0) ring(b, k1, y, kz1, k2, kz2, mas);
        }
        box(b, k1, 1, kz1, k2, 1, kz2, field);
        b.fill(k1, 12, kz1, k2, 12, kz2, mas);
        for (int x = k1; x <= k2; x++)
            for (int z = kz1; z <= kz2; z++)
                if ((x == k1 || x == k2 || z == kz1 || z == kz2) && ((x + z) & 1) == 0) b.set(x, 13, z, stone);
        cornice(b, S(Blocks.STONE_BRICK_STAIRS), k1, kz1, k2, kz2, 12);
        // keep windows (red shutters), the door and the porch
        for (int f = 0; f < 3; f++) {
            int y = 4 * f + 1;
            for (int x = 10; x <= 14; x += 4) {
                if (f > 0) Arch.window(b, m, x, y, kz1, NORTH, 2, true);
                Arch.window(b, m, x, y, kz2, SOUTH, 2, true);
            }
            for (int z = 11; z <= 15; z += 4) {
                Arch.window(b, m, k1, y, z, WEST, 2, true);
                Arch.window(b, m, k2, y, z, EAST, 2, true);
            }
        }
        b.fill(10, 0, 3, 14, 0, 6, S(Blocks.POLISHED_ANDESITE));
        box(b, 10, 1, 3, 14, 5, 6, stone);
        b.clear(11, 1, 3, 13, 5, 6);
        b.clear(11, 1, 3, 13, 4, 3);
        b.set(11, 4, 3, top(S(Blocks.STONE_BRICK_STAIRS), EAST));
        b.set(13, 4, 3, top(S(Blocks.STONE_BRICK_STAIRS), WEST));
        Arch.gable(b, m, 10, 3, 14, 6, 6, false, 1, stone);
        b.bigDoor(c, 1, kz1, Blocks.SPRUCE_DOOR, NORTH);
        b.set(c, 5, kz1 - 1, wallBanner(Blocks.RED_WALL_BANNER, NORTH));
        lamp(b, c, 5, 5);
        for (int x : new int[]{9, 15}) LandmarkKit.lampPost(b, x, 1, 1, Blocks.SPRUCE_FENCE, 2, ArchBlocks.BRASS_LANTERN.get());
        for (int[] p : new int[][]{{9, 2}, {15, 3}, {10, 1}, {14, 1}, {8, 3}}) b.set(p[0], 1, p[1], Blocks.PINK_PETALS.defaultBlockState());

        // ---- four slim round corner towers with ice-blue domes
        int[][] T = {{k1, kz1}, {k2, kz1}, {k1, kz2}, {k2, kz2}};
        for (int[] t : T) {
            b.cylinder(t[0], t[1], 1, 17, 2, Blocks.STONE_BRICKS, true);
            b.cylinder(t[0], t[1], 1, 1, 2, ArchBlocks.FIELDSTONE.get(), true);
            for (int y = 1; y <= 17; y++) b.cylinder(t[0], t[1], y, y, 1, Blocks.AIR, false);
            for (int y : new int[]{0, 4, 8, 12}) b.cylinder(t[0], t[1], y, y, 1, y == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SPRUCE_PLANKS, false);
            LandmarkKit.ring(b, t[0], t[1], 12, 2, mas);
            LandmarkKit.ring(b, t[0], t[1], 17, 2, mas);
            int ox = t[0] < c ? -1 : 1, oz = t[1] < c ? -1 : 1;
            for (int y : new int[]{2, 6, 10, 14}) {
                b.fill(t[0] + 2 * ox, y, t[1], t[0] + 2 * ox, y + 1, t[1], Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.Z));
                b.fill(t[0], y, t[1] + 2 * oz, t[0], y + 1, t[1] + 2 * oz, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
            }
            Arch.dome(b, t[0], 18, t[1], 2, dome, Blocks.LIGHTNING_ROD.defaultBlockState(), false);
            b.cylinder(t[0], t[1], 18, 18, 1, Blocks.SPRUCE_PLANKS, false);
            // the round bay opens into the keep's rooms; a door from the roof deck into the top room
            for (int x = k1 + 1; x < k2; x++)
                for (int z = kz1 + 1; z < kz2; z++) {
                    double dd = Math.hypot(x - t[0], z - t[1]);
                    if (dd > 1.5 && dd <= 2.5) for (int y0 : new int[]{1, 5, 9}) b.clear(x, y0, z, x, y0 + 2, z);
                }
            b.clear(t[0] - 2 * ox, 13, t[1] - oz, t[0] - 2 * ox, 14, t[1] - oz);
            lamp(b, t[0], 17, t[1]);
        }
        // ---- the central tower: r = 4 from the roof deck to y 29, lookout floor, ice-blue dome
        int tc = 12;
        b.cylinder(c, tc, 13, 29, 4, Blocks.STONE_BRICKS, true);
        LandmarkKit.ring(b, c, tc, 20, 4, mas);
        LandmarkKit.ring(b, c, tc, 29, 4, mas);
        b.cylinder(c, tc, 27, 27, 3, Blocks.SPRUCE_PLANKS, false);
        b.fill(c, 13, tc, c, 29, tc, col(ArchBlocks.DARK_BEAM));
        LandmarkBuildersA.spiral(b, chair, c, tc, 1, 13, 15, 5);
        for (int y : new int[]{16, 22}) {
            b.fill(c, y, tc - 4, c, y + 1, tc - 4, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
            b.fill(c, y, tc + 4, c, y + 1, tc + 4, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
            b.fill(c - 4, y, tc, c - 4, y + 1, tc, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.Z));
            b.fill(c + 4, y, tc, c + 4, y + 1, tc, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.Z));
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int x = c + (int) Math.round(Math.cos(a) * 4), z = tc + (int) Math.round(Math.sin(a) * 4);
            b.set(x, 28, z, Arch.connect(S(Blocks.GLASS_PANE), false, false, false, false));
        }
        b.door(c, 13, tc + 4, Blocks.SPRUCE_DOOR, SOUTH);
        Arch.dome(b, c, 30, tc, 4, dome, Blocks.LIGHTNING_ROD.defaultBlockState(), false);
        b.cylinder(c, tc, 30, 30, 3, Blocks.SPRUCE_PLANKS, false);
        b.set(c + 2, 28, tc + 1, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, SOUTH));
        b.set(c - 2, 28, tc - 1, decor(ArchBlocks.PLANK_BENCH, EAST));
        lamp(b, c, 26, tc + 2);
        // snow on the roof deck
        for (int x = k1 + 1; x < k2; x++)
            for (int z = kz1 + 1; z < kz2; z++)
                if (b.rand(x * 31 + z * 7, 3) == 0 && Math.hypot(x - c, z - tc) > 4.6) b.set(x, 13, z, snow);

        // ---- interiors
        // ground: hall, infirmary ward (west), apothecary (east), stair along the back wall
        b.fill(k1 + 1, 1, 11, k2 - 1, 3, 11, stone);
        b.clear(c - 1, 1, 11, c + 1, 3, 11);
        b.fill(c, 1, 12, c, 3, 15, stone);
        b.clear(c, 1, 13, c, 3, 13);
        for (int z = 12; z <= 14; z += 2) b.bed(8, 1, z, Blocks.WHITE_BED, WEST);
        b.bed(10, 1, 13, Blocks.WHITE_BED, NORTH);
        b.set(10, 1, 15, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        b.set(14, 1, 12, decor(ArchBlocks.SHOP_COUNTER, SOUTH));
        b.set(15, 1, 12, decor(ArchBlocks.SHOP_COUNTER, SOUTH));
        b.set(14, 2, 12, Blocks.BREWING_STAND.defaultBlockState());
        b.set(15, 2, 12, Blocks.BREWING_STAND.defaultBlockState());
        b.fill(17, 1, 12, 17, 2, 15, S(Blocks.BOOKSHELF));
        b.chest(16, 1, 15, NORTH, Buildings.LOOT_TREASURE);
        b.set(9, 1, 8, decor(ArchBlocks.PLANK_BENCH, EAST));
        b.set(15, 1, 8, decor(ArchBlocks.PLANK_BENCH, WEST));
        b.set(c, 3, 10, LandmarkKit.lantern(ArchBlocks.BRASS_LANTERN.get(), true));
        lamp(b, 9, 3, 13);
        lamp(b, 15, 3, 13);
        switchback(b, S(Blocks.SPRUCE_STAIRS), 8, 17, 0, -1, EAST, 0, 3);
        // first floor: study and library
        b.fill(k1 + 1, 5, 9, k1 + 1, 6, 15, S(Blocks.BOOKSHELF));
        b.fill(k2 - 1, 5, 9, k2 - 1, 6, 15, S(Blocks.BOOKSHELF));
        desk(b, 13, 5, 10, SOUTH, chair);
        b.set(10, 5, 10, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, SOUTH));
        Arch.tableSet(b, c, 5, 13, chair);
        b.set(c, 6, 13, Blocks.POTTED_RED_TULIP);
        lamp(b, 10, 7, 12);
        lamp(b, 14, 7, 12);
        // second floor: stores and the young doctor's room
        b.bed(9, 9, 9, Blocks.PINK_BED, SOUTH);
        b.set(10, 9, 9, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(10, 10, 9, Blocks.POTTED_FERN);
        for (int x = 14; x <= 17; x++) Arch.cargo(b, x, 9, 9, x);
        b.set(17, 9, 12, Blocks.BREWING_STAND.defaultBlockState());
        b.set(17, 9, 13, Blocks.CAULDRON.defaultBlockState());
        lamp(b, c, 11, 12);
        // people
        LandmarkKit.npc(b, "doctor", 11, 1, 14);
        LandmarkKit.npc(b, "apprentice_doctor", 14, 1, 14);
        b.marker("arc_anchor", c, 1, 9, LandmarkKit.tag("arc", "winter_kingdom"));
    }
}
