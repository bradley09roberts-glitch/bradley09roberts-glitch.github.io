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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import static com.grandline.world.landmark.LandmarkBuildersA.*;
import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;

/**
 * Landmark batch A, Grand Line civic and government set pieces: the Water 7 fountain, the Tower of Justice, Impel
 * Down and Mary Geoise.
 */
final class LandmarkBuildersA3 {
    private LandmarkBuildersA3() {}

    /** World Government emblem (7x7): a ring, a cross and the globe's bands. W = white, B = blue. */
    static final String[] WG_EMBLEM = {
            "..WWW..",
            ".W.B.W.",
            "W..B..W",
            "WBBBBBW",
            "W..B..W",
            ".W.B.W.",
            "..WWW.."};

    // ================================================================== Water 7 fountain

    /**
     * Water 7's great fountain (21x21): a round basin with a harbour-stone rim in the middle of a paved plaza, four
     * canals leaving it along the axes under little hump bridges, and the three-tier fountain tower in white plaster:
     * a pedestal, two bowls that spill through notches in their rims into the bowl below, and a slim spire with a
     * lantern on top. Lamp posts, benches and planters in the plaza corners.
     */
    static void water7Fountain(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        BlockState harbour = S(ArchBlocks.HARBOUR_STONE), plaster = S(ArchBlocks.WHITE_PLASTER), sand = S(ArchBlocks.DRESSED_SANDSTONE);
        BlockState water = S(Blocks.WATER), fall = water(8), slabH = slabB(S(ArchBlocks.HARBOUR_STONE_SLAB));
        b.foundation(0, 0, w - 1, w - 1, ArchBlocks.HARBOUR_STONE.get(), 12);
        b.clear(0, 1, 0, w - 1, 30, w - 1);
        // ---- plaza paving: harbour stone with a sandstone ring and radial joints
        for (int x = 0; x < w; x++)
            for (int z = 0; z < w; z++) {
                double dd = Math.hypot(x - c, z - c);
                boolean ringBand = Math.abs(dd - 9.2) < 0.5, joint = (x == z || x + z == w - 1) && dd > 7.5;
                b.set(x, 0, z, ringBand || joint ? sand : harbour);
            }
        // ---- canals along the axes, out from the basin to the plaza edge, with hump bridges
        for (int k = 0; k < 4; k++) {
            Direction dir = Direction.from2DDataValue(k);
            for (int t = 0; t <= 2; t++)
                for (int s = -2; s <= 2; s++) {
                    int x = c + dir.getStepX() * (c - t) + (dir.getAxis() == Direction.Axis.Z ? s : 0);
                    int z = c + dir.getStepZ() * (c - t) + (dir.getAxis() == Direction.Axis.X ? s : 0);
                    if (Math.abs(s) == 2) {
                        b.fill(x, -3, z, x, -1, z, harbour);
                        b.set(x, 0, z, harbour);
                    } else {
                        b.set(x, -3, z, S(Blocks.PRISMARINE_BRICKS));
                        b.fill(x, -2, z, x, -1, z, water);
                        b.set(x, 0, z, Blocks.AIR.defaultBlockState());
                    }
                }
            // hump bridge across the canal two cells out from the rim
            int t = 1;
            Direction along = dir.getClockWise();
            for (int s = -2; s <= 2; s++) {
                int x = c + dir.getStepX() * (c - t) + along.getStepX() * s;
                int z = c + dir.getStepZ() * (c - t) + along.getStepZ() * s;
                BlockState bs = Math.abs(s) == 2 ? up(S(ArchBlocks.HARBOUR_STONE_STAIRS), s < 0 ? along : along.getOpposite()) : slabH;
                b.set(x, 1, z, bs);
                if (Math.abs(s) < 2) b.set(x, 0, z, top(S(ArchBlocks.HARBOUR_STONE_STAIRS), dir));
            }
            for (int s = -1; s <= 1; s++) {
                int x = c + dir.getStepX() * (c - t) + along.getStepX() * s;
                int z = c + dir.getStepZ() * (c - t) + along.getStepZ() * s;
                b.set(x + dir.getStepX(), 2, z + dir.getStepZ(), Arch.along(S(ArchBlocks.HARBOUR_STONE_WALL), along.getAxis()));
            }
            // spout: the basin spills through a notch in its rim into the canal head
            int nx = c + dir.getStepX() * 7, nz = c + dir.getStepZ() * 7;
            b.set(nx, 0, nz, water);
            b.set(nx + dir.getStepX(), 0, nz + dir.getStepZ(), fall);
            b.set(nx, 1, nz, up(S(ArchBlocks.DRESSED_SANDSTONE_STAIRS), dir.getOpposite()));
        }
        // ---- the basin: rim at r = 7, water to r = 6
        for (int x = 0; x < w; x++)
            for (int z = 0; z < w; z++) {
                double dd = Math.hypot(x - c, z - c);
                if (dd <= 6.5) {
                    b.set(x, -3, z, S(Blocks.PRISMARINE_BRICKS));
                    b.fill(x, -2, z, x, 0, z, water);
                } else if (dd <= 7.5) {
                    boolean notch = (x == c || z == c);
                    b.fill(x, -3, z, x, -1, z, harbour);
                    if (!notch) {
                        b.set(x, 0, z, harbour);
                        b.set(x, 1, z, slabH);
                    }
                }
            }
        // ---- the fountain tower
        for (int y = -2; y <= 5; y++) LandmarkKit.disc(b, c, c, y, 3, y == 5 ? sand : harbour);
        LandmarkKit.ring(b, c, c, 2, 3, plaster);
        LandmarkKit.ring(b, c, c, 3, 3, plaster);
        LandmarkKit.disc(b, c, c, 6, 5, S(Blocks.SMOOTH_QUARTZ));
        LandmarkKit.ring(b, c, c, 6, 5, plaster);
        LandmarkKit.disc(b, c, c, 7, 4.4, water);
        LandmarkKit.ring(b, c, c, 7, 5, plaster);
        for (int y = 7; y <= 16; y++) LandmarkKit.disc(b, c, c, y, 2, y == 10 || y == 16 ? harbour : plaster);
        LandmarkKit.disc(b, c, c, 17, 3, S(Blocks.SMOOTH_QUARTZ));
        LandmarkKit.disc(b, c, c, 18, 2.4, water);
        LandmarkKit.ring(b, c, c, 18, 3, plaster);
        for (int y = 18; y <= 25; y++) LandmarkKit.disc(b, c, c, y, 1, y == 22 ? harbour : plaster);
        b.fill(c - 1, 26, c - 1, c + 1, 26, c + 1, slabB(S(ArchBlocks.HARBOUR_STONE_SLAB)));
        for (int k = 0; k < 4; k++) {
            Direction dir = Direction.from2DDataValue(k);
            b.set(c + dir.getStepX(), 26, c + dir.getStepZ(), top(S(ArchBlocks.WHITE_PLASTER_STAIRS), dir.getOpposite()));
        }
        b.set(c, 26, c, harbour);
        b.fill(c, 27, c, c, 29, c, col(ArchBlocks.MARBLE_COLUMN));
        b.set(c, 30, c, LandmarkKit.lantern(ArchBlocks.BRASS_LANTERN.get(), false));
        // spills: notches in each bowl rim with a falling sheet to the water below
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int rx = c + (int) Math.round(Math.cos(a) * 5), rz = c + (int) Math.round(Math.sin(a) * 5);
            int ox = c + (int) Math.round(Math.cos(a) * 6), oz = c + (int) Math.round(Math.sin(a) * 6);
            b.set(rx, 7, rz, water);
            b.fill(ox, 1, oz, ox, 7, oz, fall);
        }
        for (int k = 0; k < 4; k++) {
            Direction dir = Direction.from2DDataValue(k);
            int rx = c + dir.getStepX() * 3, rz = c + dir.getStepZ() * 3;
            b.set(rx, 18, rz, water);
            b.fill(rx + dir.getStepX(), 8, rz + dir.getStepZ(), rx + dir.getStepX(), 18, rz + dir.getStepZ(), fall);
            // lion-head spouts on the column
            b.set(c + dir.getStepX() * 3, 12, c + dir.getStepZ() * 3, up(S(ArchBlocks.DRESSED_SANDSTONE_STAIRS), dir.getOpposite()));
            b.set(c + dir.getStepX() * 2, 21, c + dir.getStepZ() * 2, up(S(ArchBlocks.DRESSED_SANDSTONE_STAIRS), dir.getOpposite()));
        }
        // ---- plaza corners: lamps, benches, planters
        for (int[] q : new int[][]{{2, 2}, {w - 3, 2}, {2, w - 3}, {w - 3, w - 3}}) {
            LandmarkKit.lampPost(b, q[0], 1, q[1], Blocks.IRON_BARS, 3, ArchBlocks.BRASS_LANTERN.get());
            int sx = q[0] < c ? 1 : -1, sz = q[1] < c ? 1 : -1;
            b.set(q[0] + sx * 2, 1, q[1], decor(ArchBlocks.PLANK_BENCH, sz > 0 ? SOUTH : NORTH));
            b.set(q[0], 1, q[1] + sz * 2, decor(ArchBlocks.PLANK_BENCH, sx > 0 ? EAST : WEST));
            b.set(q[0] - sx, 1, q[1] - sz, decor(ArchBlocks.FLOWER_PLANTER, NORTH));
        }
        LandmarkKit.npc(b, "canal_guide", 3, 1, 4);
        b.marker("town_center", w - 4, 1, 3, new CompoundTag());
    }

    // ================================================================== Tower of Justice (Enies Lobby)

    /**
     * The Tower of Justice (17x17): a tall white tower in three set-back stages (five, three and two storeys) with
     * marble pilasters, pale masonry bands and cornices, gilded pinnacles, the World Government emblem on the second
     * stage and the flag mast on the roof terrace; a marble portico with a teal pediment and three doors, and the
     * balcony high on the top stage. A switchback climbs all ten floors: lobby, guard room, archive, interrogation
     * room, the Rokushiki training hall, the agents' bar, offices, dormitory, the chief's office and his quarters.
     */
    static void towerOfJustice(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        Mats m = Mats.of(BuildStyle.PALACE, b.seed);
        m.planters = false;
        BlockState mas = S(ArchBlocks.PALE_MASONRY), pl = S(ArchBlocks.WHITE_PLASTER), marble = col(ArchBlocks.MARBLE_COLUMN), gold = S(Blocks.GOLD_BLOCK);
        BlockState masStairs = S(ArchBlocks.PALE_MASONRY_STAIRS), chair = S(Blocks.DARK_OAK_STAIRS), blue = S(Blocks.BLUE_CONCRETE);
        b.foundation(0, 0, w - 1, w - 1, ArchBlocks.PALE_MASONRY.get(), 14);
        b.clear(0, 1, 0, w - 1, 52, w - 1);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < w; z++) b.set(x, 0, z, ((x + z) & 1) == 0 ? mas : S(Blocks.POLISHED_DIORITE));
        // ---- three stages: {x1, z1, x2, z2, yBase, storeys}
        int[][] S3 = {{2, 3, 14, 15, 0, 5}, {3, 4, 13, 14, 20, 3}, {4, 5, 12, 13, 32, 2}};
        for (int i = 0; i < 3; i++) {
            int x1 = S3[i][0], z1 = S3[i][1], x2 = S3[i][2], z2 = S3[i][3], y0 = S3[i][4], n = S3[i][5];
            for (int f = 0; f < n; f++) {
                int y = y0 + 4 * f;
                b.fill(x1, y, z1, x2, y, z2, f == 0 && i == 0 ? S(Blocks.POLISHED_DIORITE) : S(Blocks.DARK_OAK_PLANKS));
                box(b, x1, y + 1, z1, x2, y + 3, z2, i == 0 && f == 0 ? mas : pl);
                b.clear(x1 + 1, y + 1, z1 + 1, x2 - 1, y + 3, z2 - 1);
                if (y > 0) ring(b, x1, y, z1, x2, z2, mas);
                int wy = y + 1;
                boolean frontOk = !(i == 1) && !(i == 0 && f == 0);
                int[] xs = i == 2 ? new int[]{6, 8, 10} : new int[]{5, 8, 11};
                int[] zs = i == 2 ? new int[]{7, 9, 11} : new int[]{z1 + 2, z1 + 5, z1 + 8};
                for (int x : xs) {
                    if (frontOk || (i == 0 && f == 0 && x != 8)) Arch.window(b, m, x, wy, z1, NORTH, 2, i < 2);
                    Arch.window(b, m, x, wy, z2, SOUTH, 2, i < 2);
                }
                for (int z : zs) {
                    Arch.window(b, m, x1, wy, z, WEST, 2, i < 2);
                    Arch.window(b, m, x2, wy, z, EAST, 2, i < 2);
                }
            }
            int top = y0 + 4 * n;
            b.fill(x1, top, z1, x2, top, z2, mas);
            corners(b, x1, y0 + 1, z1, x2, top - 1, z2, marble);
            cornice(b, masStairs, x1, z1, x2, z2, top);
        }
        // pinnacles on the setbacks and the roof
        for (int[] p : new int[][]{{2, 3}, {14, 3}, {2, 15}, {14, 15}}) {
            b.fill(p[0], 21, p[1], p[0], 22, p[1], marble);
            b.set(p[0], 23, p[1], gold);
        }
        for (int[] p : new int[][]{{3, 4}, {13, 4}, {3, 14}, {13, 14}}) {
            b.fill(p[0], 33, p[1], p[0], 34, p[1], marble);
            b.set(p[0], 35, p[1], gold);
        }
        LandmarkKit.outline(b, 4, 41, 5, 12, 13, ArchBlocks.PALE_MASONRY_WALL.get());
        for (int[] p : new int[][]{{4, 5}, {12, 5}, {4, 13}, {12, 13}}) {
            b.fill(p[0], 41, p[1], p[0], 42, p[1], marble);
            b.set(p[0], 43, p[1], gold);
        }
        b.fill(c, 41, 9, c, 49, 9, post(Blocks.IRON_BARS));
        b.set(c, 49, 8, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
        b.set(c, 48, 8, wallBanner(Blocks.WHITE_WALL_BANNER, NORTH));
        b.set(c, 50, 9, Blocks.LIGHTNING_ROD.defaultBlockState());
        // World Government emblem on the second stage
        b.fill(c - 4, 22, 4, c + 4, 30, 4, blue);
        pixels(b, WG_EMBLEM, c + 3, 29, 4, -1, 0, 0, 0, -1, 0, ch -> ch == 'W' ? S(Blocks.WHITE_CONCRETE) : ch == 'B' ? gold : null);
        // ---- portico: marble columns, entablature, teal pediment, three doors
        b.fill(4, 0, 0, 12, 0, 2, mas);
        for (int x : new int[]{4, 6, 10, 12}) b.fill(x, 1, 0, x, 6, 0, marble);
        b.fill(4, 1, 2, 4, 6, 2, marble);
        b.fill(12, 1, 2, 12, 6, 2, marble);
        b.fill(4, 7, 0, 12, 7, 2, mas);
        b.set(4, 7, 0, gold);
        b.set(12, 7, 0, gold);
        Arch.gable(b, m, 4, 0, 12, 2, 8, false, 0, mas);
        for (int x = 7; x <= 9; x++) b.bigDoor(x, 1, 3, Blocks.DARK_OAK_DOOR, NORTH);
        lamp(b, 8, 6, 1);
        Arch.wallLantern(b, m, 5, 3, 3, NORTH);
        Arch.wallLantern(b, m, 11, 3, 3, NORTH);
        // the balcony on the top stage
        b.fill(6, 32, 2, 10, 32, 3, mas);
        for (int x = 6; x <= 10; x++) b.set(x, 31, 2, top(masStairs, SOUTH));
        rail(b, 6, 33, 2, 10, 2, ArchBlocks.PALE_MASONRY_WALL.get());
        rail(b, 6, 33, 3, 6, 3, ArchBlocks.PALE_MASONRY_WALL.get());
        rail(b, 10, 33, 3, 10, 3, ArchBlocks.PALE_MASONRY_WALL.get());
        b.door(c, 33, 5, Blocks.DARK_OAK_DOOR, NORTH);

        // ---- interiors (the stair well: lanes x 10/11, z 6..11)
        switchback(b, S(Blocks.POLISHED_DIORITE_STAIRS), 10, 7, 1, 0, SOUTH, 0, 10);
        // lobby
        for (int x = 4; x <= 6; x++) b.set(x, 1, 10, decor(ArchBlocks.SHOP_COUNTER, NORTH));
        b.set(4, 1, 11, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, NORTH));
        b.set(3, 1, 6, decor(ArchBlocks.PLANK_BENCH, EAST));
        b.set(13, 1, 5, decor(ArchBlocks.PLANK_BENCH, WEST));
        for (int x = 4; x <= 8; x += 2) b.set(x, 3, 14, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
        b.fill(c, 0, 4, c, 0, 9, blue);
        lamp(b, 6, 3, 7);
        // guard room
        for (int x = 3; x <= 7; x += 2) b.bed(x, 5, 14, Blocks.BLUE_BED, NORTH);
        Arch.tableSet(b, 6, 5, 8, chair);
        b.set(3, 5, 4, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        lamp(b, 6, 7, 9);
        // archive
        b.fill(3, 9, 4, 3, 10, 14, S(Blocks.BOOKSHELF));
        for (int z : new int[]{6, 9, 12}) b.fill(5, 9, z, 8, 10, z, S(Blocks.BOOKSHELF));
        b.set(12, 9, 13, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, WEST));
        lamp(b, 6, 11, 8);
        // interrogation room
        b.fill(3, 13, 7, 5, 15, 7, Arch.along(S(Blocks.IRON_BARS), Direction.Axis.X));
        b.fill(6, 13, 4, 6, 15, 6, Arch.along(S(Blocks.IRON_BARS), Direction.Axis.Z));
        b.set(6, 13, 7, S(Blocks.IRON_BARS));
        b.set(4, 13, 5, up(chair, NORTH));
        b.set(8, 13, 10, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(8, 13, 11, up(chair, SOUTH));
        b.fill(4, 14, 5, 4, 15, 5, S(Blocks.CHAIN));
        lamp(b, 8, 15, 8);
        // Rokushiki training hall
        for (int z = 5; z <= 13; z += 2) b.set(3, 18, z, Blocks.TARGET);
        b.set(6, 17, 13, Blocks.HAY_BLOCK);
        b.set(7, 17, 13, Blocks.HAY_BLOCK);
        b.set(6, 18, 13, Blocks.HAY_BLOCK);
        b.set(8, 17, 5, Blocks.ANVIL);
        lamp(b, 6, 19, 9);
        // the agents' bar
        for (int x = 4; x <= 8; x++) b.set(x, 21, 11, decor(ArchBlocks.SHOP_COUNTER, NORTH));
        for (int x = 4; x <= 8; x += 2) b.set(x, 21, 13, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        for (int x = 4; x <= 8; x += 2) b.set(x, 21, 10, up(chair, SOUTH));
        Arch.tableSet(b, 6, 21, 6, chair);
        b.set(5, 22, 11, Blocks.BREWING_STAND.defaultBlockState());
        lamp(b, 6, 23, 8);
        // offices
        desk(b, 5, 25, 6, SOUTH, chair);
        desk(b, 8, 25, 6, SOUTH, chair);
        desk(b, 5, 25, 11, NORTH, chair);
        b.fill(4, 25, 13, 8, 26, 13, S(Blocks.BOOKSHELF));
        lamp(b, 6, 27, 9);
        // dormitory
        for (int x = 4; x <= 8; x += 2) b.bed(x, 29, 13, Blocks.BLACK_BED, NORTH);
        for (int x = 4; x <= 8; x += 2) b.bed(x, 29, 5, Blocks.BLACK_BED, SOUTH);
        lamp(b, 6, 31, 9);
        // the chief's office (with the balcony door) and his quarters
        b.set(7, 33, 10, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(6, 33, 10, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(7, 33, 11, up(chair, SOUTH));
        b.set(6, 34, 10, LandmarkKit.candles(Blocks.CANDLE, 3));
        b.set(5, 33, 12, S(Blocks.BOOKSHELF));
        b.set(5, 34, 12, S(Blocks.BOOKSHELF));
        b.chest(5, 33, 11, EAST, Buildings.LOOT_TREASURE);
        b.set(9, 35, 12, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
        lamp(b, 7, 35, 8);
        b.bed(6, 37, 11, Blocks.WHITE_BED, SOUTH);
        b.set(5, 37, 12, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(8, 37, 7, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(8, 38, 7, Blocks.POTTED_WITHER_ROSE);
        lamp(b, 7, 39, 9);
        // people
        LandmarkKit.npc(b, "cp_agent", 5, 1, 7);
        LandmarkKit.npc(b, "cp_agent", 6, 9, 8);
        LandmarkKit.npc(b, "cp_agent", 6, 21, 8);
        LandmarkKit.npc(b, "cp_leader", 7, 33, 8);
    }

    // ================================================================== Impel Down

    /**
     * Impel Down (31x31): the great prison. Above the sea, a dark stone drum with seastone bands, buttresses and a
     * spiked parapet, a watch drum with a dome on the roof, and the gatehouse with the World Government emblem over
     * the gate. Inside, the reception hall (the warden's desk, the boiling bath, confiscated goods) opens onto the
     * central spiral stair that winds down past five underground levels: Crimson Hell, Wild Beast Hell, Starvation
     * Hell, Blazing Hell and Freezing Hell, each a ring of barred cells around a guard corridor with its own floor
     * and decor, a jailer on watch and a marked cell.
     */
    static void impelDown(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        BlockState brick = S(Blocks.DEEPSLATE_BRICKS), tile = S(Blocks.DEEPSLATE_TILES), sea = S(ModBlocks.SEASTONE_BRICKS);
        BlockState chain = S(Blocks.CHAIN), soul = LandmarkKit.lantern(Blocks.SOUL_LANTERN, true);
        b.foundation(0, 0, w - 1, w - 1, ModBlocks.SEASTONE_BRICKS.get(), 30);
        b.clear(0, 1, 0, w - 1, 40, w - 1);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < w; z++) b.set(x, 0, z, ((x * 7 + z * 3) % 5 == 0) ? S(Blocks.POLISHED_BLACKSTONE_BRICKS) : tile);
        // ---- the drum: batter course, body, seastone bands, roof, spiked parapet
        for (int y = 1; y <= 24; y++) {
            BlockState s = (y % 6 == 0) ? sea : brick;
            if (y <= 3) ringT(b, c, c, y, 12.5, 2.0, s);
            else ringT(b, c, c, y, 12, 1.0, s);
        }
        for (int y = 1; y <= 7; y++) discAir(b, c, c, y, 11.4);
        LandmarkKit.disc(b, c, c, 8, 11.5, tile);
        LandmarkKit.disc(b, c, c, 24, 11.5, tile);
        for (int y = 9; y <= 23; y++) discAir(b, c, c, y, 11.4);
        for (int k = 0; k < 64; k++) {
            double a = k * Math.PI * 2 / 64;
            int x = c + (int) Math.round(Math.cos(a) * 12), z = c + (int) Math.round(Math.sin(a) * 12);
            b.set(x, 25, z, brick);
            if ((k & 1) == 0) {
                b.set(x, 26, z, brick);
                b.set(x, 27, z, Blocks.POINTED_DRIPSTONE.defaultBlockState());
            }
        }
        // buttresses
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 8 + k * Math.PI / 4;
            for (double rr = 12.5; rr <= 14.6; rr += 0.5) {
                int x = c + (int) Math.round(Math.cos(a) * rr), z = c + (int) Math.round(Math.sin(a) * rr);
                int topY = (int) Math.round(20 - (rr - 12.5) * 6);
                b.fill(x, 1, z, x, topY, z, brick);
                b.set(x, topY + 1, z, up(S(Blocks.DEEPSLATE_BRICK_STAIRS), nearestDir(c - x, c - z)));
            }
        }
        // slit windows high on the drum
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int x = c + (int) Math.round(Math.cos(a) * 12), z = c + (int) Math.round(Math.sin(a) * 12);
            if (z < c - 8) continue;
            for (int y = 11; y <= 20; y += 9) b.fill(x, y, z, x, y + 2, z, S(Blocks.IRON_BARS));
        }
        // watch drum and dome on the roof
        for (int y = 25; y <= 31; y++) ringT(b, c, c, y, 5, 1.0, y == 28 ? sea : brick);
        for (int y = 25; y <= 31; y++) discAir(b, c, c, y, 4.4);
        for (int k = 0; k < 4; k++) {
            Direction dd = Direction.from2DDataValue(k);
            b.set(c + dd.getStepX() * 5, 29, c + dd.getStepZ() * 5, S(Blocks.RED_STAINED_GLASS));
        }
        Arch.dome(b, c, 32, c, 5, tile, Blocks.LIGHTNING_ROD.defaultBlockState(), false);
        // ---- the gatehouse with the emblem
        b.fill(9, 1, 0, 21, 16, 4, brick);
        b.fill(9, 6, 0, 21, 6, 0, sea);
        b.fill(9, 16, 0, 21, 16, 0, sea);
        for (int x = 9; x <= 21; x += 2) b.set(x, 17, 0, brick);
        b.set(9, 17, 4, brick);
        b.set(21, 17, 4, brick);
        b.clear(12, 1, 0, 18, 7, 6);
        b.set(12, 7, 0, top(S(Blocks.DEEPSLATE_BRICK_STAIRS), EAST));
        b.set(18, 7, 0, top(S(Blocks.DEEPSLATE_BRICK_STAIRS), WEST));
        b.fill(13, 7, 1, 17, 7, 1, Arch.along(S(Blocks.IRON_BARS), Direction.Axis.X));
        b.fill(12, 1, 2, 12, 6, 2, S(Blocks.IRON_BLOCK));
        b.fill(18, 1, 2, 18, 6, 2, S(Blocks.IRON_BLOCK));
        b.fill(12, 0, 0, 18, 0, 6, S(Blocks.POLISHED_BLACKSTONE_BRICKS));
        pixels(b, WG_EMBLEM, c + 3, 15, 0, -1, 0, 0, 0, -1, 0, ch -> ch == 'W' ? S(Blocks.WHITE_CONCRETE) : ch == 'B' ? S(Blocks.BLUE_CONCRETE) : null);
        b.set(13, 7, 3, soul);
        b.set(17, 7, 3, soul);

        // ---- reception hall (ground): warden's desk, the boiling bath, confiscated goods, banners
        b.fill(13, 1, 9, 17, 1, 9, decor(ArchBlocks.SHOP_COUNTER, NORTH));
        b.set(15, 1, 10, up(S(Blocks.DARK_OAK_STAIRS), SOUTH));
        b.set(14, 2, 9, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, NORTH));
        b.set(6, 1, 13, Blocks.LAVA_CAULDRON.defaultBlockState());
        b.set(6, 1, 14, Blocks.LAVA_CAULDRON.defaultBlockState());
        b.set(6, 1, 15, Blocks.LAVA_CAULDRON.defaultBlockState());
        for (int z = 12; z <= 18; z += 2) Arch.cargo(b, 24, 1, z, z);
        b.set(23, 1, 19, decor(ArchBlocks.DECK_CANNON, WEST));
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int x = c + (int) Math.round(Math.cos(a) * 11), z = c + (int) Math.round(Math.sin(a) * 11);
            if (z < 8) continue;
            b.set(x, 6, z, chain);
            b.set(x, 5, z, chain);
        }
        for (int[] l : new int[][]{{9, 9}, {21, 9}, {9, 21}, {21, 21}, {15, 22}}) b.set(l[0], 7, l[1], soul);
        b.set(15, 5, 26, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
        // railing round the stair opening and the central column
        rail(b, 12, 1, 12, 12, 15, Blocks.IRON_BARS);
        rail(b, 12, 1, 12, 14, 12, Blocks.IRON_BARS);
        b.fill(14, -30, 14, 16, 7, 16, sea);
        b.marker("prison", c, 1, 11, new CompoundTag());
        LandmarkKit.npc(b, "warden", 18, 1, 11);

        // ---- the five levels below
        for (int lvl = 0; lvl < 5; lvl++) {
            int y0 = -6 - lvl * 6;
            BlockState floor = switch (lvl) {
                case 0 -> S(Blocks.RED_NETHER_BRICKS);
                case 1 -> S(Blocks.COARSE_DIRT);
                case 2 -> S(Blocks.SMOOTH_SANDSTONE);
                case 3 -> S(Blocks.BLACKSTONE);
                default -> S(Blocks.PACKED_ICE);
            };
            for (int y = y0; y <= y0 + 5; y++) ringT(b, c, c, y, 12, 1.0, sea);
            LandmarkKit.disc(b, c, c, y0, 11.5, floor);
            for (int y = y0 + 1; y <= y0 + 5; y++) discAir(b, c, c, y, 11.4);
            // inner ring wall with barred cell fronts, radial dividers
            for (int x = c - 9; x <= c + 9; x++)
                for (int z = c - 9; z <= c + 9; z++) {
                    double dd = Math.hypot(x - c, z - c);
                    if (dd <= 7.5 || dd > 8.5) continue;
                    double ang = Math.atan2(z - c, x - c), sec = ang / (Math.PI / 4);
                    double off = Math.abs(sec - Math.round(sec));
                    boolean bars = off < 0.16;
                    BlockState s = bars ? (Math.abs(x - c) > Math.abs(z - c) ? Arch.along(S(Blocks.IRON_BARS), Direction.Axis.Z) : Arch.along(S(Blocks.IRON_BARS), Direction.Axis.X)) : sea;
                    b.fill(x, y0 + 1, z, x, y0 + (bars ? 3 : 5), z, s);
                    if (bars) b.fill(x, y0 + 4, z, x, y0 + 5, z, sea);
                }
            for (int k = 0; k < 8; k++) {
                double a = Math.PI / 8 + k * Math.PI / 4;
                for (double rr = 8; rr <= 11.6; rr += 0.5) {
                    int x = c + (int) Math.round(Math.cos(a) * rr), z = c + (int) Math.round(Math.sin(a) * rr);
                    b.fill(x, y0 + 1, z, x, y0 + 5, z, sea);
                }
            }
            // themed cells and corridor
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                int x = c + (int) Math.round(Math.cos(a) * 11), z = c + (int) Math.round(Math.sin(a) * 11);
                switch (lvl) {
                    case 0 -> {
                        b.set(x, y0 + 1, z, Blocks.POINTED_DRIPSTONE.defaultBlockState());
                        b.set(x, y0 + 5, z, S(Blocks.RED_STAINED_GLASS));
                    }
                    case 1 -> {
                        b.set(x, y0 + 1, z, Blocks.HAY_BLOCK.defaultBlockState());
                        b.set(x, y0 + 4, z, Blocks.COBWEB.defaultBlockState());
                    }
                    case 2 -> b.set(x, y0 + 1, z, S(Blocks.DEAD_BUSH));
                    case 3 -> {
                        b.set(x, y0, z, S(Blocks.MAGMA_BLOCK));
                        b.set(x, y0 + 1, z, Blocks.LAVA_CAULDRON.defaultBlockState());
                    }
                    default -> {
                        b.set(x, y0 + 1, z, S(Blocks.BLUE_ICE));
                        b.set(x, y0 + 2, z, Blocks.SNOW.defaultBlockState());
                    }
                }
            }
            if (lvl == 2) {
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    int x = c + (int) Math.round(Math.cos(a) * 11), z = c + (int) Math.round(Math.sin(a) * 11);
                    b.set(x, y0, z, S(Blocks.SAND));
                }
            }
            if (lvl == 1) for (int k = 0; k < 6; k++) b.set(c - 5 + 2 * k, y0 + 1, c + 5, S(Blocks.BONE_BLOCK));
            for (int k = 0; k < 4; k++) {
                double a = Math.PI / 4 + k * Math.PI / 2;
                int x = c + (int) Math.round(Math.cos(a) * 5), z = c + (int) Math.round(Math.sin(a) * 5);
                b.set(x, y0 + 5, z, soul);
            }
            b.marker("cell", c + 10, y0 + 1, c, intTag("level", lvl + 1));
            LandmarkKit.npc(b, lvl < 2 ? "jailer" : "jailer_beast", c + 4, y0 + 1, c);
        }
        // the spiral stair from the hall floor down to Freezing Hell (top step level with the hall floor, north side)
        spiral(b, S(Blocks.DEEPSLATE_BRICK_STAIRS), c, c, 2, -30, 31, 4);
    }

    private static Direction nearestDir(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? EAST : WEST;
        return dz >= 0 ? SOUTH : NORTH;
    }

    private static CompoundTag intTag(String k, int v) {
        CompoundTag t = new CompoundTag();
        t.putInt(k, v);
        return t;
    }

    /** Ring of thickness t (cells with r - t/2 < d <= r + t/2) at height y. */
    static void ringT(Builder b, double cx, double cz, int y, double r, double t, BlockState s) {
        int R = (int) Math.ceil(r + t);
        for (int x = (int) cx - R; x <= (int) cx + R; x++)
            for (int z = (int) cz - R; z <= (int) cz + R; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > r - t / 2 - 0.0001 && d <= r + t / 2) b.set(x, y, z, s);
            }
    }

    static void discAir(Builder b, double cx, double cz, int y, double r) {
        int R = (int) Math.ceil(r);
        for (int x = (int) cx - R; x <= (int) cx + R; x++)
            for (int z = (int) cz - R; z <= (int) cz + R; z++)
                if (Math.hypot(x - cx, z - cz) <= r) b.set(x, y, z, Blocks.AIR.defaultBlockState());
    }

    // ================================================================== Mary Geoise

    /**
     * Pangaea Castle, Mary Geoise (41x41 on top of the Red Line, which caps everything at 18 blocks above the floor
     * because the world ends at y 319): a white palace on a quartz court, pale masonry below and white plaster above
     * with marble pilasters, blue shutters and gilded bands; the great gold dome over the throne hall, four round
     * corner towers with gold domes, and a marble portico under a low teal pediment. The front court has the
     * avenue, a fountain, clipped hedges and lamps; the back terrace looks over the edge of the Red Line. Inside: the
     * entrance hall with twin stairs to the gallery floor, the banquet hall and the salon in the wings, the Empty
     * Throne on its dais under the dome ringed by twenty swords planted in the floor, and bedrooms and the library
     * upstairs.
     */
    static void maryGeoise(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        Mats m = Mats.of(BuildStyle.PALACE, b.seed);
        BlockState mas = S(ArchBlocks.PALE_MASONRY), pl = S(ArchBlocks.WHITE_PLASTER), marble = col(ArchBlocks.MARBLE_COLUMN), gold = S(Blocks.GOLD_BLOCK);
        BlockState quartz = S(Blocks.QUARTZ_BRICKS), chair = S(Blocks.QUARTZ_STAIRS), hedge = LandmarkKit.leaves(Blocks.AZALEA_LEAVES);
        b.foundation(0, 0, w - 1, w - 1, ArchBlocks.PALE_MASONRY.get(), 20);
        b.fill(0, 0, 0, w - 1, 0, w - 1, quartz);
        b.clear(0, 1, 0, w - 1, 18, w - 1);
        // ---- main block x 8..32, z 14..34: storeys of five (floors y 0 and 5), roof deck y 10
        int x1 = 8, x2 = 32, z1 = 14, z2 = 34;
        b.fill(x1, 0, z1, x2, 0, z2, S(Blocks.POLISHED_DIORITE));
        box(b, x1, 1, z1, x2, 4, z2, mas);
        b.fill(x1, 5, z1, x2, 5, z2, S(Blocks.DARK_OAK_PLANKS));
        ring(b, x1, 5, z1, x2, z2, gold);
        box(b, x1, 6, z1, x2, 9, z2, pl);
        b.fill(x1, 10, z1, x2, 10, z2, mas);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 4, z2 - 1);
        b.clear(x1 + 1, 6, z1 + 1, x2 - 1, 9, z2 - 1);
        for (int x = x1; x <= x2; x += 4) {
            b.fill(x, 1, z1, x, 9, z1, marble);
            b.fill(x, 1, z2, x, 9, z2, marble);
        }
        for (int z = z1; z <= z2; z += 4) {
            b.fill(x1, 1, z, x1, 9, z, marble);
            b.fill(x2, 1, z, x2, 9, z, marble);
        }
        cornice(b, S(ArchBlocks.PALE_MASONRY_STAIRS), x1, z1, x2, z2, 10);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                if ((x == x1 || x == x2 || z == z1 || z == z2)) b.set(x, 11, z, (x + z) % 4 == 0 ? mas : S(Blocks.QUARTZ_SLAB));
        // windows between the pilasters
        for (int x = x1 + 2; x <= x2 - 2; x += 4) {
            for (int y : new int[]{2, 7}) {
                if (!(y == 2 && x >= 18 && x <= 22)) Arch.window(b, m, x, y, z1, NORTH, 2, false);
                Arch.window(b, m, x, y, z2, SOUTH, 2, false);
            }
        }
        for (int z = z1 + 2; z <= z2 - 2; z += 4)
            for (int y : new int[]{2, 7}) {
                Arch.window(b, m, x1, y, z, WEST, 2, false);
                Arch.window(b, m, x2, y, z, EAST, 2, false);
            }
        // ---- the great gold dome over the throne hall (x 15..25, z 19..29)
        int dz = 26;
        LandmarkKit.ring(b, c, dz, 10, 6, mas);
        LandmarkKit.disc(b, c, dz, 10, 5.4, Blocks.AIR.defaultBlockState());
        Arch.dome(b, c, 11, dz, 5, gold, Blocks.LIGHTNING_ROD.defaultBlockState(), false);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            b.set(c + (int) Math.round(Math.cos(a) * 6), 11, dz + (int) Math.round(Math.sin(a) * 6), marble);
        }
        // ---- corner towers with gold domes
        for (int[] t : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            b.cylinder(t[0], t[1], 1, 11, 3, ArchBlocks.WHITE_PLASTER.get(), true);
            b.cylinder(t[0], t[1], 1, 4, 3, ArchBlocks.PALE_MASONRY.get(), true);
            LandmarkKit.ring(b, t[0], t[1], 5, 3, gold);
            for (int y = 1; y <= 11; y++) b.cylinder(t[0], t[1], y, y, 2, Blocks.AIR, false);
            b.cylinder(t[0], t[1], 5, 5, 2, Blocks.DARK_OAK_PLANKS, false);
            b.cylinder(t[0], t[1], 0, 0, 2, Blocks.POLISHED_DIORITE, false);
            Arch.dome(b, t[0], 12, t[1], 3, gold, Blocks.LIGHTNING_ROD.defaultBlockState(), false);
            b.cylinder(t[0], t[1], 12, 12, 2, Blocks.DARK_OAK_PLANKS, false);
            int ox = t[0] < c ? -1 : 1, oz = t[1] < c ? -1 : 1;
            for (int y : new int[]{2, 7}) {
                b.fill(t[0] + 3 * ox, y, t[1], t[0] + 3 * ox, y + 1, t[1], Arch.along(S(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), Direction.Axis.Z));
                b.fill(t[0], y, t[1] + 3 * oz, t[0], y + 1, t[1] + 3 * oz, Arch.along(S(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE), Direction.Axis.X));
            }
            // open the towers into the rooms they stand in
            for (int x = x1 + 1; x < x2; x++)
                for (int z = z1 + 1; z < z2; z++) {
                    double dd = Math.hypot(x - t[0], z - t[1]);
                    if (dd > 2.5 && dd <= 3.5) for (int y0 : new int[]{1, 6}) b.clear(x, y0, z, x, y0 + 2, z);
                }
            lamp(b, t[0], 4, t[1]);
        }
        // ---- portico with the low teal pediment
        b.fill(14, 0, 9, 26, 0, 13, mas);
        for (int x : new int[]{14, 16, 18, 22, 24, 26}) b.fill(x, 1, 9, x, 7, 9, marble);
        b.fill(14, 1, 13, 14, 7, 13, marble);
        b.fill(26, 1, 13, 26, 7, 13, marble);
        b.fill(14, 8, 9, 26, 8, 13, mas);
        for (int x = 14; x <= 26; x++) b.set(x, 8, 9, gold);
        Mats low = Mats.of(BuildStyle.PALACE, b.seed);
        low.roofKind = Arch.RoofKind.LOW_GABLE;
        Arch.gable(b, low, 14, 9, 26, 13, 9, false, 0, mas);
        for (int x = 19; x <= 21; x++) b.bigDoor(x, 1, z1, Blocks.DARK_OAK_DOOR, NORTH);
        lamp(b, 17, 7, 11);
        lamp(b, 23, 7, 11);
        b.set(c, 7, 11, mas);
        lamp(b, c, 6, 11);
        b.fill(14, 7, 10, 26, 7, 13, mas);
        // ---- front court: avenue, fountain, hedges, lamps
        b.fill(18, 0, 0, 22, 0, 8, S(Blocks.SMOOTH_QUARTZ));
        LandmarkKit.disc(b, c, 4, 0, 3.4, S(Blocks.SMOOTH_QUARTZ));
        LandmarkKit.ring(b, c, 4, 1, 3, S(Blocks.QUARTZ_SLAB));
        LandmarkKit.disc(b, c, 4, 0, 2.4, S(Blocks.WATER));
        b.fill(c, 0, 4, c, 2, 4, col(ArchBlocks.MARBLE_COLUMN));
        b.set(c, 3, 4, gold);
        for (int x : new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38})
            if (x != 16 && x != 24) b.set(x, 1, 1, hedge);
        for (int z = 1; z <= 12; z++) {
            b.set(2, 1, z, hedge);
            b.set(w - 3, 1, z, hedge);
        }
        for (int[] bed : new int[][]{{5, 4, 12, 8}, {28, 4, 35, 8}}) {
            b.fill(bed[0], 0, bed[1], bed[2], 0, bed[3], S(Blocks.GRASS_BLOCK));
            for (int x = bed[0]; x <= bed[2]; x++)
                for (int z = bed[1]; z <= bed[3]; z++)
                    b.set(x, 1, z, switch (b.rand(x * 13 + z * 7, 5)) {
                        case 0 -> S(Blocks.WHITE_TULIP);
                        case 1 -> S(Blocks.LILY_OF_THE_VALLEY);
                        case 2 -> S(Blocks.CORNFLOWER);
                        default -> Blocks.AIR.defaultBlockState();
                    });
        }
        for (int z = 2; z <= 8; z += 3) {
            LandmarkKit.lampPost(b, 16, 1, z, Blocks.IRON_BARS, 2, ArchBlocks.BRASS_LANTERN.get());
            LandmarkKit.lampPost(b, 24, 1, z, Blocks.IRON_BARS, 2, ArchBlocks.BRASS_LANTERN.get());
        }
        // side gardens and the back terrace over the edge
        for (int[] tr : new int[][]{{3, 20}, {3, 28}, {w - 4, 20}, {w - 4, 28}}) LandmarkKit.tree(b, tr[0], 1, tr[1], Blocks.BIRCH_LOG, Blocks.CHERRY_LEAVES, 4, 2, tr[0] * 7 + tr[1], null);
        for (int z = 16; z <= 33; z++) {
            b.set(1, 1, z, hedge);
            b.set(w - 2, 1, z, hedge);
        }
        LandmarkKit.outline(b, 1, 1, 37, w - 2, w - 2, ArchBlocks.PALE_MASONRY_WALL.get());
        b.clear(2, 1, 37, w - 3, 1, 37);
        for (int x = 6; x <= w - 7; x += 7) b.set(x, 1, 38, decor(ArchBlocks.PLANK_BENCH, SOUTH));

        // ---- interiors
        BlockState plw = S(ArchBlocks.WHITE_PLASTER);
        // partitions: entrance hall z 15..18 | throne hall x 15..25, z 19..33 | wings
        for (int y0 : new int[]{1, 6}) {
            int yt = y0 == 1 ? 4 : 9;
            b.fill(x1 + 1, y0, 18, x2 - 1, yt, 18, plw);
            b.fill(14, y0, 19, 14, yt, z2 - 1, plw);
            b.fill(26, y0, 19, 26, yt, z2 - 1, plw);
        }
        b.clear(14, 1, 25, 14, 3, 25);
        b.clear(26, 1, 25, 26, 3, 25);
        for (int z = 21; z <= 31; z += 5) {
            b.fill(14, 7, z, 14, 8, z, Arch.along(S(Blocks.IRON_BARS), Direction.Axis.Z));
            b.fill(26, 7, z, 26, 8, z, Arch.along(S(Blocks.IRON_BARS), Direction.Axis.Z));
        }
        b.fill(16, 7, 18, 24, 8, 18, Arch.along(S(Blocks.IRON_BARS), Direction.Axis.X));
        b.clear(18, 1, 18, 22, 3, 18);
        b.clear(15, 5, 19, 25, 5, 33);
        b.clear(15, 6, 19, 25, 9, 33);
        // twin stairs from the entrance hall up to the gallery over it
        Arch.staircase(b, chair, 16, 1, 16, WEST, 5);
        Arch.staircase(b, chair, 24, 1, 16, EAST, 5);
        b.fill(c - 1, 0, 15, c + 1, 0, 33, S(Blocks.POLISHED_DIORITE));
        b.fill(c - 1, 1, 15, c + 1, 1, 23, S(Blocks.RED_CARPET));
        // the Empty Throne on its dais under the dome, ringed by twenty swords
        b.fill(c - 2, 1, 25, c + 2, 1, 28, quartz);
        b.fill(c - 1, 2, 26, c + 1, 2, 28, gold);
        b.set(c, 3, 27, up(chair, SOUTH));
        b.set(c - 1, 3, 27, top(chair, EAST));
        b.set(c + 1, 3, 27, top(chair, WEST));
        b.fill(c, 3, 28, c, 6, 28, gold);
        b.set(c, 7, 28, Blocks.LIGHTNING_ROD.defaultBlockState());
        for (int x = c - 2; x <= c + 2; x++) b.set(x, 1, 24, up(chair, SOUTH));
        java.util.Set<Long> swords = new java.util.HashSet<>();
        for (int k = 0; k < 20; k++) {
            double a = k * Math.PI * 2 / 20;
            int x = c + (int) Math.round(Math.cos(a) * 4.8), z = 26 + (int) Math.round(Math.sin(a) * 3.8);
            if (!swords.add((long) x * 4096 + z)) continue;
            if (z < 25 && Math.abs(x - c) <= 1) continue;
            int yb = (x >= c - 1 && x <= c + 1 && z >= 26 && z <= 28) ? 3 : (x >= c - 2 && x <= c + 2 && z >= 25 && z <= 28) ? 2 : 1;
            b.fill(x, yb, z, x, yb + 1, z, post(Blocks.IRON_BARS));
        }
        b.fill(c, 10, dz, c, 15, dz, S(Blocks.CHAIN));
        b.set(c, 9, dz, LandmarkKit.lantern(ArchBlocks.BRASS_LANTERN.get(), true));
        for (int z = 20; z <= 32; z += 4) {
            b.set(15, 3, z, wallBanner(Blocks.WHITE_WALL_BANNER, EAST));
            b.set(25, 3, z, wallBanner(Blocks.WHITE_WALL_BANNER, WEST));
        }
        // west wing: banquet hall; east wing: the salon
        LandmarkKit.longTableZ(b, 21, 31, 1, 11, Blocks.QUARTZ_SLAB, Blocks.QUARTZ_STAIRS, true);
        lamp(b, 11, 4, 23);
        lamp(b, 11, 4, 29);
        for (int z = 21; z <= 31; z += 5) {
            Arch.tableSet(b, 29, 1, z, chair);
            b.set(29, 2, z, Blocks.POTTED_WHITE_TULIP);
        }
        b.set(31, 1, 33, Blocks.NOTE_BLOCK.defaultBlockState());
        b.set(30, 1, 33, decor(ArchBlocks.PLANK_BENCH, NORTH));
        lamp(b, 29, 4, 26);
        // entrance hall
        b.set(10, 1, 16, decor(ArchBlocks.FLOWER_PLANTER, EAST));
        b.set(30, 1, 16, decor(ArchBlocks.FLOWER_PLANTER, WEST));
        lamp(b, c, 4, 16);
        // upstairs: bedrooms (west), library (east), gallery over the entrance
        for (int z = 21; z <= 31; z += 5) b.bed(10, 6, z, Blocks.WHITE_BED, WEST);
        for (int z = 21; z <= 31; z += 5) b.set(10, 6, z + 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.fill(31, 6, 20, 31, 7, 32, S(Blocks.BOOKSHELF));
        b.fill(28, 6, 21, 28, 7, 24, S(Blocks.BOOKSHELF));
        b.fill(28, 6, 28, 28, 7, 31, S(Blocks.BOOKSHELF));
        desk(b, 29, 6, 26, WEST, chair);
        lamp(b, 11, 9, 26);
        lamp(b, 29, 9, 26);
        lamp(b, c, 9, 16);
        // people
        LandmarkKit.npc(b, "celestial_guard", c, 1, 17);
        b.marker("arc_anchor", c, 1, 8, LandmarkKit.tag("arc", "holy_land"));
    }
}
