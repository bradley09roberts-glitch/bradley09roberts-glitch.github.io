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
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;

/**
 * Landmark batch A: shared construction helpers (switchback and spiral stairs, eave skirts, lettering and pixel
 * crests, window rows) and the East Blue / Paradise canon set pieces: Marine Headquarters, the Marine branch base,
 * the Baratie, Arlong Park and Drum Castle. Local frame as everywhere: entrance on the north side (z = 0), floor at
 * y = 0, storeys four blocks, doorways three blocks tall. Randomness only through {@link Builder#rand}.
 */
final class LandmarkBuildersA {
    private LandmarkBuildersA() {}

    // ================================================================== small state helpers

    static BlockState S(Block b) {
        return b.defaultBlockState();
    }

    static BlockState S(DeferredBlock<Block> b) {
        return b.get().defaultBlockState();
    }

    /** Upright pillar (beam, column, log). */
    static BlockState col(DeferredBlock<Block> b) {
        return Arch.axis(S(b), Direction.Axis.Y);
    }

    static BlockState col(Block b) {
        return Arch.axis(S(b), Direction.Axis.Y);
    }

    static BlockState beam(DeferredBlock<Block> b, Direction.Axis a) {
        return Arch.axis(S(b), a);
    }

    static BlockState up(BlockState stairs, Direction f) {
        return Arch.stair(stairs, f, Half.BOTTOM);
    }

    static BlockState top(BlockState stairs, Direction f) {
        return Arch.stair(stairs, f, Half.TOP);
    }

    static BlockState slabB(BlockState s) {
        return Arch.slab(s, SlabType.BOTTOM);
    }

    static BlockState slabT(BlockState s) {
        return Arch.slab(s, SlabType.TOP);
    }

    /** A free-standing post of a connected block (no arms). */
    static BlockState post(Block b) {
        return Arch.connect(b.defaultBlockState(), false, false, false, false);
    }

    static BlockState decor(DeferredBlock<Block> b, Direction f) {
        return Arch.decor(b, f);
    }

    static BlockState wallBanner(Block banner, Direction f) {
        BlockState s = banner.defaultBlockState();
        return s.hasProperty(WallBannerBlock.FACING) ? s.setValue(WallBannerBlock.FACING, f) : s;
    }

    static BlockState wallSign(Block sign, Direction f) {
        BlockState s = sign.defaultBlockState();
        return s.hasProperty(WallSignBlock.FACING) ? s.setValue(WallSignBlock.FACING, f) : s;
    }

    static BlockState water(int level) {
        return Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, level);
    }

    /** One-block rectangle ring at height y. */
    static void ring(Builder b, int x1, int y, int z1, int x2, int z2, BlockState s) {
        b.fill(x1, y, z1, x2, y, z1, s);
        b.fill(x1, y, z2, x2, y, z2, s);
        b.fill(x1, y, z1, x1, y, z2, s);
        b.fill(x2, y, z1, x2, y, z2, s);
    }

    /** Four walls between corners (no floor or ceiling). */
    static void box(Builder b, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        b.fill(x1, y1, z1, x2, y2, z1, s);
        b.fill(x1, y1, z2, x2, y2, z2, s);
        b.fill(x1, y1, z1, x1, y2, z2, s);
        b.fill(x2, y1, z1, x2, y2, z2, s);
    }

    static void corners(Builder b, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        b.fill(x1, y1, z1, x1, y2, z1, s);
        b.fill(x2, y1, z1, x2, y2, z1, s);
        b.fill(x1, y1, z2, x1, y2, z2, s);
        b.fill(x2, y1, z2, x2, y2, z2, s);
    }

    static void lamp(Builder b, int x, int y, int z) {
        b.hanging(x, y, z, ArchBlocks.BRASS_LANTERN.get());
    }

    /** Fence/bars/wall railing along a straight run with joined ends. */
    static void rail(Builder b, int x1, int y, int z1, int x2, int z2, Block block) {
        LandmarkKit.line(b, x1, y, z1, x2, z2, block);
    }

    // ================================================================== circulation

    /**
     * Two-lane switchback stair. Flight k climbs one storey (four steps) from floor level y0 + 4k: even flights run
     * from cell (ax, az) along {@code dir}, odd flights run back in the lane offset by (lx, lz). Each flight's top
     * step is level with the next floor; even flights land one cell past their last step, odd flights one cell
     * before (ax, az) in their lane. Headroom (three blocks) is cleared above every step, which also cuts the floor
     * openings.
     */
    static void switchback(Builder b, BlockState stairs, int ax, int az, int lx, int lz, Direction dir, int y0, int flights) {
        for (int k = 0; k < flights; k++) {
            boolean even = (k & 1) == 0;
            for (int i = 0; i < 4; i++) {
                int t = even ? i : 3 - i;
                int x = ax + dir.getStepX() * t + (even ? 0 : lx), z = az + dir.getStepZ() * t + (even ? 0 : lz);
                int y = y0 + 4 * k + 1 + i;
                b.set(x, y, z, up(stairs, even ? dir : dir.getOpposite()));
                b.clear(x, y + 1, z, x, y + 3, z);
            }
        }
    }

    /** Cell {@code idx} of the square ring at Chebyshev distance h around (cx, cz), clockwise from the north-west corner. */
    static int[] ringCell(int cx, int cz, int h, int idx) {
        int n = 8 * h;
        int i = Math.floorMod(idx, n);
        if (i < 2 * h) return new int[]{cx - h + i, cz - h};
        if (i < 4 * h) return new int[]{cx + h, cz - h + (i - 2 * h)};
        if (i < 6 * h) return new int[]{cx + h - (i - 4 * h), cz + h};
        return new int[]{cx - h, cz + h - (i - 6 * h)};
    }

    /**
     * Square spiral stair on the ring at distance h around (cx, cz): n steps, the lowest at yBottom on ring cell
     * {@code start}, each next step one cell clockwise and one block higher; three blocks of headroom are cleared
     * above every step.
     */
    static void spiral(Builder b, BlockState stairs, int cx, int cz, int h, int yBottom, int n, int start) {
        for (int i = 0; i < n; i++) {
            int[] c = ringCell(cx, cz, h, start + i), nx = ringCell(cx, cz, h, start + i + 1);
            Direction f = Direction.fromDelta(Integer.signum(nx[0] - c[0]), 0, Integer.signum(nx[1] - c[1]));
            if (f == null) f = NORTH;
            int y = yBottom + i;
            b.set(c[0], y, c[1], up(stairs, f));
            b.clear(c[0], y + 1, c[1], c[0], y + 3, c[1]);
        }
    }

    // ================================================================== roofs and facades

    /**
     * Sloping eave skirt around a wall box [x1..x2]x[z1..z2]: course k (0 = lowest) is a ring of roof stairs facing
     * in, at distance (ov - k) outside the wall line and height y + k. With ov = 1 and two courses the upper course
     * sits on the wall top (a terrace parapet); with ov = 2 both courses lean against a continuing wall.
     */
    static void skirt(Builder b, BlockState stairs, int x1, int z1, int x2, int z2, int y, int ov, int courses) {
        for (int k = 0; k < courses; k++) {
            int o = ov - k, yy = y + k;
            int ax = x1 - o, bx = x2 + o, az = z1 - o, bz = z2 + o;
            for (int x = ax + 1; x < bx; x++) {
                b.set(x, yy, az, up(stairs, SOUTH));
                b.set(x, yy, bz, up(stairs, NORTH));
            }
            for (int z = az + 1; z < bz; z++) {
                b.set(ax, yy, z, up(stairs, EAST));
                b.set(bx, yy, z, up(stairs, WEST));
            }
            b.set(ax, yy, az, Arch.stair(stairs, SOUTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
            b.set(bx, yy, az, Arch.stair(stairs, SOUTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
            b.set(ax, yy, bz, Arch.stair(stairs, NORTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
            b.set(bx, yy, bz, Arch.stair(stairs, NORTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
        }
    }

    /** Upside-down stair cornice ring just outside a wall box at height y. */
    static void cornice(Builder b, BlockState stairs, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1; x <= x2; x++) {
            b.set(x, y, z1 - 1, top(stairs, SOUTH));
            b.set(x, y, z2 + 1, top(stairs, NORTH));
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1 - 1, y, z, top(stairs, EAST));
            b.set(x2 + 1, y, z, top(stairs, WEST));
        }
    }

    /** Windows every {@code step} along a wall running along x at plane z (outside toward {@code out}). */
    static void windowsX(Builder b, Mats m, int x1, int x2, int step, int y, int z, Direction out, int h, boolean shutters) {
        for (int x = x1; x <= x2; x += step) Arch.window(b, m, x, y, z, out, h, shutters);
    }

    static void windowsZ(Builder b, Mats m, int z1, int z2, int step, int y, int x, Direction out, int h, boolean shutters) {
        for (int z = z1; z <= z2; z += step) Arch.window(b, m, x, y, z, out, h, shutters);
    }

    // ================================================================== lettering and pixel crests

    private static final Map<Character, String[]> FONT = new HashMap<>();

    private static void glyph(char c, String... rows) {
        FONT.put(c, rows);
    }

    static {
        glyph('A', ".X.", "X.X", "XXX", "X.X", "X.X");
        glyph('B', "XX.", "X.X", "XX.", "X.X", "XX.");
        glyph('E', "XXX", "X..", "XX.", "X..", "XXX");
        glyph('G', ".XX", "X..", "X.X", "X.X", ".XX");
        glyph('I', "X", "X", "X", "X", "X");
        glyph('K', "X.X", "X.X", "XX.", "X.X", "X.X");
        glyph('L', "X..", "X..", "X..", "X..", "XXX");
        glyph('M', "X...X", "XX.XX", "X.X.X", "X...X", "X...X");
        glyph('N', "X..X", "XX.X", "X.XX", "X..X", "X..X");
        glyph('O', ".X.", "X.X", "X.X", "X.X", ".X.");
        glyph('P', "XX.", "X.X", "XX.", "X..", "X..");
        glyph('R', "XX.", "X.X", "XX.", "X.X", "X.X");
        glyph('T', "XXX", ".X.", ".X.", ".X.", ".X.");
        glyph(' ', "..", "..", "..", "..", "..");
    }

    /** The text as pixel rows (glyphs joined by one-column gaps). */
    static String[] textRows(String s) {
        StringBuilder[] r = new StringBuilder[5];
        for (int j = 0; j < 5; j++) r[j] = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String[] g = FONT.getOrDefault(s.charAt(i), FONT.get(' '));
            for (int j = 0; j < 5; j++) {
                if (i > 0) r[j].append('.');
                r[j].append(g[j]);
            }
        }
        String[] out = new String[5];
        for (int j = 0; j < 5; j++) out[j] = r[j].toString();
        return out;
    }

    static int textWidth(String s) {
        return textRows(s)[0].length();
    }

    /**
     * Pixel rows drawn into the world: character i of row j lands at origin + u*i + v*j; the palette maps a
     * character to a block (null = leave as is).
     */
    static void pixels(Builder b, String[] rows, int x, int y, int z, int ux, int uy, int uz, int vx, int vy, int vz, Function<Character, BlockState> pal) {
        for (int j = 0; j < rows.length; j++)
            for (int i = 0; i < rows[j].length(); i++) {
                BlockState s = pal.apply(rows[j].charAt(i));
                if (s != null) b.set(x + ux * i + vx * j, y + uy * i + vy * j, z + uz * i + vz * j, s);
            }
    }

    /**
     * Text on a north-facing wall plane z, read by someone standing north of it: starts at column xLeft (the
     * reader's left, i.e. the high-x end) and runs toward -x; the top row is at yTop.
     */
    static void textNorthWall(Builder b, String s, int xLeft, int yTop, int z, BlockState ink) {
        pixels(b, textRows(s), xLeft, yTop, z, -1, 0, 0, 0, -1, 0, ch -> ch == 'X' ? ink : null);
    }

    /** Text laid in a floor at height y, read by someone standing north of it facing south (top row farthest away). */
    static void textFloor(Builder b, String s, int xLeft, int y, int zTop, BlockState ink) {
        pixels(b, textRows(s), xLeft, y, zTop, -1, 0, 0, 0, 0, -1, ch -> ch == 'X' ? ink : null);
    }

    // ================================================================== furniture

    static void bunks(Builder b, int x1, int x2, int step, int y, int z, Direction head, Block bed) {
        for (int x = x1; x <= x2; x += step) b.bed(x, y, z, bed, head);
    }

    /** A writing desk (plank table) with a chair on the {@code sit} side and a lamp. */
    static void desk(Builder b, int x, int y, int z, Direction sit, BlockState chair) {
        b.set(x, y, z, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(x + sit.getStepX(), y, z + sit.getStepZ(), up(chair, sit));
        b.set(x, y + 1, z, LandmarkKit.candles(Blocks.CANDLE, 2));
    }

    static void shelves(Builder b, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        b.fill(x1, y1, z1, x2, y2, z2, s);
    }

    static Mats marineMats(long seed, boolean tealRoof) {
        Mats m = Mats.of(BuildStyle.MARINE, seed);
        if (tealRoof) {
            m.roof = S(ArchBlocks.TEAL_ROOF_TILES);
            m.roofStairs = S(ArchBlocks.TEAL_ROOF_TILE_STAIRS);
            m.roofSlab = S(ArchBlocks.TEAL_ROOF_TILE_SLAB);
        }
        return m;
    }

    // ================================================================== Marine Headquarters (Marineford)

    /**
     * Marineford HQ on 41x41: a harbour quay on the bay side (north) with bollards and the bay batteries, the
     * encircling bay walls on the other three sides, the great paved plaza with "MARINE" laid in blue, a marble
     * portico, and the tiered white fortress: four set-back tiers with blue bands and teal eave skirts, crowned by
     * an open lookout pavilion with the bell and the Marine flag. "MARINE" also runs across the second tier. A
     * switchback stair climbs all eight floors (barracks, bounty office, mess, armoury, war room, records,
     * officers' quarters, salon, the Fleet Admiral's office).
     */
    static void marineHeadquarters(Builder b, Island island, BuildStyle st) {
        Mats m = marineMats(b.seed, true);
        int w = b.w, d = b.d, c = w / 2;
        BlockState mas = S(ArchBlocks.PALE_MASONRY), blue = S(Blocks.BLUE_CONCRETE), harbour = S(ArchBlocks.HARBOUR_STONE);
        BlockState marble = col(ArchBlocks.MARBLE_COLUMN), chair = S(Blocks.SPRUCE_STAIRS);
        b.foundation(0, 0, w - 1, d - 1, ArchBlocks.HARBOUR_STONE.get(), 20);
        b.clear(0, 1, 0, w - 1, 52, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, S(Blocks.POLISHED_ANDESITE));
        // ---- bay front: quay with bollards
        b.fill(0, 0, 0, w - 1, 0, 1, harbour);
        for (int x = 6; x <= w - 7; x += 7) b.set(x, 1, 0, decor(ArchBlocks.BOLLARD, NORTH));
        // ---- encircling bay walls (west, east, south) with a wall walk, merlons and batteries
        for (int side = 0; side < 2; side++) {
            int xo = side == 0 ? 0 : w - 2, xi = side == 0 ? 1 : w - 2, xout = side == 0 ? 0 : w - 1;
            Direction outDir = side == 0 ? WEST : EAST;
            b.fill(xo, 1, 2, xo + 1, 6, d - 1, mas);
            b.fill(xi, 5, 4, xi, 5, d - 3, blue);
            for (int z = 2; z < d; z += 2) b.set(xout, 7, z, mas);
            for (int z = 8; z < d - 3; z += 7) b.set(xi, 7, z, decor(ArchBlocks.DECK_CANNON, outDir));
            // corner bastion on the bay front
            int bx1 = side == 0 ? 0 : w - 4;
            b.fill(bx1, 1, 0, bx1 + 3, 8, 3, mas);
            b.fill(bx1, 7, 0, bx1 + 3, 7, 3, blue);
            for (int i = 0; i < 4; i++) {
                if ((i & 1) == 0) {
                    b.set(bx1 + i, 9, 0, mas);
                    b.set(side == 0 ? bx1 : bx1 + 3, 9, i, mas);
                }
            }
            b.set(bx1 + 1, 9, 1, decor(ArchBlocks.DECK_CANNON, NORTH));
            b.set(bx1 + 2, 9, 2, decor(ArchBlocks.DECK_CANNON, outDir));
            LandmarkKit.lampPost(b, side == 0 ? bx1 + 3 : bx1, 9, 3, Blocks.IRON_BARS, 2, ArchBlocks.BRASS_LANTERN.get());
        }
        b.fill(0, 1, d - 2, w - 1, 6, d - 1, mas);
        b.fill(2, 5, d - 2, w - 3, 5, d - 2, blue);
        for (int x = 0; x < w; x += 2) b.set(x, 7, d - 1, mas);
        for (int x = 8; x < w - 6; x += 8) b.set(x, 7, d - 2, decor(ArchBlocks.DECK_CANNON, SOUTH));
        // ---- plaza: framed white field with MARINE in blue, flag masts
        b.fill(5, 0, 2, w - 6, 0, 10, mas);
        b.fill(6, 0, 3, w - 7, 0, 9, S(Blocks.SMOOTH_QUARTZ));
        textFloor(b, "MARINE", c + 12, 0, 8, blue);
        for (int x = 2; x <= w - 3; x++) b.set(x, 0, 11, mas);
        for (int mx : new int[]{6, w - 7}) {
            b.fill(mx, 0, 13, mx, 0, 13, mas);
            b.fill(mx, 1, 13, mx, 12, 13, post(Blocks.IRON_BARS));
            b.set(mx, 12, 12, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
            b.set(mx, 11, 12, wallBanner(Blocks.WHITE_WALL_BANNER, NORTH));
        }
        for (int x : new int[]{3, w - 4})
            for (int z = 6; z <= 10; z += 4) LandmarkKit.lampPost(b, x, 1, z, Blocks.IRON_BARS, 3, ArchBlocks.BRASS_LANTERN.get());

        // ---- the tiered fortress
        int[][] T = {{4, 16, 36, 37, 0}, {7, 19, 33, 35, 8}, {11, 21, 29, 33, 16}, {15, 23, 25, 31, 24}};
        for (int i = 0; i < T.length; i++) hqTier(b, m, T[i][0], T[i][1], T[i][2], T[i][3], T[i][4], i == 0);
        // sloping plinth course around the base (not in front of the portico)
        for (int x = 3; x <= 37; x++) {
            if (x < 13 || x > 27) b.set(x, 1, 15, up(S(ArchBlocks.PALE_MASONRY_STAIRS), SOUTH));
            b.set(x, 1, 38, up(S(ArchBlocks.PALE_MASONRY_STAIRS), NORTH));
        }
        for (int z = 16; z <= 37; z++) {
            b.set(3, 1, z, up(S(ArchBlocks.PALE_MASONRY_STAIRS), EAST));
            b.set(37, 1, z, up(S(ArchBlocks.PALE_MASONRY_STAIRS), WEST));
        }
        // windows: every tier, both storeys, all four sides
        for (int i = 0; i < T.length; i++) {
            int x1 = T[i][0], z1 = T[i][1], x2 = T[i][2], z2 = T[i][3], y0 = T[i][4];
            for (int s = 0; s < 2; s++) {
                int y = y0 + 4 * s + 1;
                for (int x = x1 + 2; x <= x2 - 2; x += 3) {
                    boolean front = !(i == 0 && s == 0 && x >= 12 && x <= 28) && i != 1;
                    if (front) Arch.window(b, m, x, y, z1, NORTH, 2, true);
                    Arch.window(b, m, x, y, z2, SOUTH, 2, true);
                }
                for (int z = z1 + 2; z <= z2 - 2; z += 3) {
                    if (i == 1 && s == 0 && z == 27) continue; // terrace doors
                    Arch.window(b, m, x1, y, z, WEST, 2, true);
                    Arch.window(b, m, x2, y, z, EAST, 2, true);
                }
            }
        }
        // ---- portico: seven-high marble colonnade carrying the first terrace, three doors
        b.fill(13, 0, 11, 27, 0, 15, S(Blocks.SMOOTH_QUARTZ));
        for (int x = 13; x <= 27; x++) b.set(x, 0, 11, mas);
        for (int x : new int[]{13, 15, 17, 23, 25, 27}) b.fill(x, 1, 11, x, 7, 11, marble);
        for (int z : new int[]{13, 15}) {
            b.fill(13, 1, z, 13, 7, z, marble);
            b.fill(27, 1, z, 27, 7, z, marble);
        }
        b.clear(13, 8, 15, 27, 9, 16); // the base tier's skirt gives way to the portico deck
        b.fill(13, 8, 11, 27, 8, 16, S(Blocks.SMOOTH_STONE));
        for (int x = 13; x <= 27; x++) b.set(x, 8, 11, blue);
        cornice(b, S(ArchBlocks.PALE_MASONRY_STAIRS), 13, 11, 27, 11, 7);
        b.fill(13, 7, 12, 27, 7, 15, mas);
        b.fill(12, 7, 12, 12, 7, 15, top(S(ArchBlocks.PALE_MASONRY_STAIRS), EAST));
        b.fill(28, 7, 12, 28, 7, 15, top(S(ArchBlocks.PALE_MASONRY_STAIRS), WEST));
        rail(b, 13, 9, 11, 27, 11, ArchBlocks.PALE_MASONRY_WALL.get());
        rail(b, 13, 9, 12, 13, 15, ArchBlocks.PALE_MASONRY_WALL.get());
        rail(b, 27, 9, 12, 27, 15, ArchBlocks.PALE_MASONRY_WALL.get());
        for (int x = 19; x <= 21; x++) b.bigDoor(x, 1, 16, Blocks.DARK_OAK_DOOR, NORTH);
        for (int x = 17; x <= 23; x += 6) lamp(b, x, 6, 13);
        b.set(c, 7, 13, mas);
        lamp(b, c, 6, 13);
        // bounty board beside the doors
        b.set(17, 2, 15, wallSign(Blocks.OAK_WALL_SIGN, NORTH));
        b.set(16, 2, 15, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
        b.marker("bounty_board", 17, 2, 15, new net.minecraft.nbt.CompoundTag());
        // second tier: MARINE across the front over the terrace door
        textNorthWall(b, "MARINE", c + 12, 15, 19, blue);
        b.door(c, 9, 19, Blocks.DARK_OAK_DOOR, NORTH);
        Arch.wallLantern(b, m, c - 2, 10, 19, NORTH);
        Arch.wallLantern(b, m, c + 2, 10, 19, NORTH);
        // terrace doors on the second tier's flanks and cannons on the terraces
        b.door(7, 9, 27, Blocks.DARK_OAK_DOOR, WEST);
        b.door(33, 9, 27, Blocks.DARK_OAK_DOOR, EAST);
        for (int[] g : new int[][]{{5, 18, 0}, {35, 18, 1}, {5, 36, 0}, {35, 36, 1}}) b.set(g[0], 9, g[1], decor(ArchBlocks.DECK_CANNON, g[2] == 0 ? WEST : EAST));
        for (int[] g : new int[][]{{9, 21}, {31, 21}, {9, 34}, {31, 34}}) b.set(g[0], 17, g[1], decor(ArchBlocks.DECK_CANNON, g[0] < c ? WEST : EAST));

        // ---- crown: open lookout pavilion with the bell and the flag
        for (int[] p : new int[][]{{17, 24}, {23, 24}, {17, 30}, {23, 30}, {17, 27}, {23, 27}}) b.fill(p[0], 33, p[1], p[0], 36, p[1], marble);
        b.fill(16, 37, 23, 24, 37, 31, mas);
        ring(b, 16, 37, 23, 24, 31, blue);
        b.clear(17, 37, 24, 23, 37, 30);
        b.fill(17, 37, 24, 23, 37, 24, mas);
        b.fill(17, 37, 30, 23, 37, 30, mas);
        b.fill(17, 37, 25, 17, 37, 29, mas);
        b.fill(23, 37, 25, 23, 37, 29, mas);
        int roofTop = Arch.hip(b, m, 17, 24, 23, 30, 38, 1);
        b.fill(c, 37, 27, c, 37, 27, mas);
        b.set(c, 36, 27, LandmarkKit.bellCeiling());
        b.fill(c, roofTop + 1, 27, c, roofTop + 7, 27, post(Blocks.IRON_BARS));
        b.set(c, roofTop + 7, 26, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
        b.set(c, roofTop + 6, 26, wallBanner(Blocks.WHITE_WALL_BANNER, NORTH));
        b.set(c, roofTop + 8, 27, Blocks.LIGHTNING_ROD.defaultBlockState());
        lamp(b, 18, 36, 25);
        lamp(b, 22, 36, 29);

        // ---- interiors
        BlockState plaster = S(ArchBlocks.WHITE_PLASTER);
        // base tier, both storeys: front rooms | hall | front rooms, the stair hall and back rooms
        for (int s = 0; s < 2; s++) {
            int y = 4 * s + 1;
            b.fill(5, y, 24, 35, y + 2, 24, plaster);
            b.clear(18, y, 24, 21, y + 2, 24);
            b.fill(13, y, 17, 13, y + 2, 23, plaster);
            b.fill(27, y, 17, 27, y + 2, 23, plaster);
            b.clear(13, y, 20, 13, y + 2, 20);
            b.clear(27, y, 20, 27, y + 2, 20);
            b.fill(17, y, 25, 17, y + 2, 36, plaster);
            b.fill(22, y, 25, 22, y + 2, 36, plaster);
            b.clear(17, y, 31, 17, y + 2, 32);
            b.clear(22, y, 31, 22, y + 2, 32);
            for (int[] l : new int[][]{{9, 20}, {31, 20}, {11, 30}, {29, 30}, {20, 33}}) lamp(b, l[0], y + 2, l[1]);
        }
        // double-height entrance hall with marble columns and a gallery
        b.clear(14, 4, 17, 26, 4, 21);
        b.fill(14, 0, 17, 26, 0, 23, S(Blocks.SMOOTH_QUARTZ));
        b.fill(c, 0, 17, c, 0, 23, blue);
        for (int z : new int[]{18, 21}) {
            b.fill(15, 1, z, 15, 7, z, marble);
            b.fill(25, 1, z, 25, 7, z, marble);
        }
        rail(b, 14, 5, 22, 26, 22, Blocks.IRON_BARS);
        lamp(b, 18, 7, 20);
        lamp(b, 22, 7, 20);
        b.set(c, 7, 17, wallBanner(Blocks.BLUE_WALL_BANNER, SOUTH));
        b.set(16, 1, 23, decor(ArchBlocks.SHOP_COUNTER, NORTH));
        b.set(17, 1, 23, decor(ArchBlocks.SHOP_COUNTER, NORTH));
        b.set(23, 1, 23, decor(ArchBlocks.SHOP_COUNTER, NORTH));
        b.set(24, 1, 23, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, NORTH));
        // ground: barracks (west front), bounty office (east front), mess (west back), armoury (east back)
        bunks(b, 5, 11, 2, 1, 17, SOUTH, Blocks.BLUE_BED);
        bunks(b, 5, 11, 2, 1, 22, NORTH, Blocks.BLUE_BED);
        for (int x = 28; x <= 34; x += 3) b.set(x, 1, 17, decor(ArchBlocks.SHOP_COUNTER, SOUTH));
        b.set(35, 1, 20, Blocks.CARTOGRAPHY_TABLE);
        b.set(35, 1, 22, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, WEST));
        b.fill(28, 1, 23, 33, 2, 23, S(Blocks.BOOKSHELF));
        LandmarkKit.longTableX(b, 6, 15, 1, 28, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        LandmarkKit.longTableX(b, 6, 15, 1, 33, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        LandmarkKit.kitchenX(b, 6, 1, 36, NORTH, null);
        for (int x = 24; x <= 34; x += 2) b.set(x, 1, 36, decor(ArchBlocks.DECK_CANNON, NORTH));
        for (int z = 26; z <= 34; z += 2) Arch.cargo(b, 35, 1, z, z);
        b.set(24, 1, 27, Blocks.ANVIL);
        b.set(24, 1, 28, Blocks.GRINDSTONE);
        b.set(28, 1, 31, Blocks.SMITHING_TABLE);
        // base tier upper: officers' office (W), records (E), dormitory (W back), chart room (E back)
        desk(b, 8, 5, 19, SOUTH, chair);
        desk(b, 11, 5, 19, SOUTH, chair);
        b.fill(5, 5, 17, 5, 6, 23, S(Blocks.BOOKSHELF));
        b.fill(35, 5, 17, 35, 6, 23, S(Blocks.BOOKSHELF));
        b.fill(28, 5, 17, 33, 6, 17, S(Blocks.BOOKSHELF));
        desk(b, 31, 5, 21, NORTH, chair);
        bunks(b, 6, 16, 2, 5, 25, SOUTH, Blocks.WHITE_BED);
        bunks(b, 6, 16, 2, 5, 35, NORTH, Blocks.WHITE_BED);
        b.set(28, 5, 30, Blocks.CARTOGRAPHY_TABLE);
        b.set(29, 5, 30, Blocks.CARTOGRAPHY_TABLE);
        b.set(30, 5, 30, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, NORTH));
        LandmarkKit.longTableZ(b, 26, 35, 5, 32, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, false);
        // second tier: war room (front), quartermaster (W back), communications (E back); records hall upstairs
        b.fill(8, 9, 24, 32, 11, 24, plaster);
        b.clear(18, 9, 24, 21, 11, 24);
        b.fill(17, 9, 25, 17, 11, 34, plaster);
        b.fill(22, 9, 25, 22, 11, 34, plaster);
        b.clear(17, 9, 29, 17, 11, 30);
        b.clear(22, 9, 29, 22, 11, 30);
        for (int x = 12; x <= 28; x++) {
            if (x >= 18 && x <= 22) continue;
            b.set(x, 9, 22, decor(ArchBlocks.PLANK_TABLE, NORTH));
            if ((x & 1) == 0) {
                b.set(x, 9, 21, up(chair, NORTH));
                b.set(x, 9, 23, up(chair, SOUTH));
            }
        }
        b.set(14, 10, 22, Blocks.POTTED_FERN);
        b.set(26, 10, 22, LandmarkKit.candles(Blocks.CANDLE, 3));
        b.set(8, 9, 21, Blocks.CARTOGRAPHY_TABLE);
        b.set(32, 9, 21, Blocks.CARTOGRAPHY_TABLE);
        for (int x : new int[]{11, 15, 25, 29}) lamp(b, x, 11, 22);
        for (int z = 26; z <= 33; z += 2) Arch.cargo(b, 9, 9, z, z + 1);
        b.set(15, 9, 26, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        for (int x = 24; x <= 31; x += 2) {
            b.set(x, 9, 34, decor(ArchBlocks.SHOP_COUNTER, NORTH));
            b.set(x, 10, 34, Blocks.NOTE_BLOCK.defaultBlockState());
        }
        lamp(b, 12, 11, 30);
        lamp(b, 28, 11, 30);
        b.fill(8, 13, 20, 8, 14, 34, S(Blocks.BOOKSHELF));
        b.fill(32, 13, 20, 32, 14, 34, S(Blocks.BOOKSHELF));
        for (int x = 10; x <= 16; x += 2) b.fill(x, 13, 27, x, 14, 33, S(Blocks.BOOKSHELF));
        for (int x = 24; x <= 30; x += 2) b.fill(x, 13, 27, x, 14, 33, S(Blocks.BOOKSHELF));
        desk(b, 12, 13, 22, SOUTH, chair);
        desk(b, 28, 13, 22, SOUTH, chair);
        for (int x : new int[]{11, 20, 29}) lamp(b, x, 15, 24);
        // third tier: officers' quarters, lounge
        bunks(b, 12, 17, 2, 17, 22, SOUTH, Blocks.BLUE_BED);
        bunks(b, 23, 28, 2, 17, 22, SOUTH, Blocks.BLUE_BED);
        bunks(b, 12, 17, 2, 17, 31, NORTH, Blocks.BLUE_BED);
        bunks(b, 23, 28, 2, 17, 31, NORTH, Blocks.BLUE_BED);
        b.set(12, 17, 27, Blocks.BOOKSHELF);
        b.set(28, 17, 27, Blocks.BOOKSHELF);
        lamp(b, 14, 19, 27);
        lamp(b, 26, 19, 27);
        Arch.tableSet(b, 14, 21, 26, chair);
        Arch.tableSet(b, 26, 21, 26, chair);
        Arch.tableSet(b, 14, 21, 30, chair);
        Arch.tableSet(b, 26, 21, 30, chair);
        b.fill(12, 21, 32, 16, 21, 32, decor(ArchBlocks.SHOP_COUNTER, NORTH));
        b.fill(24, 21, 32, 28, 21, 32, decor(ArchBlocks.GOODS_SHELF, NORTH));
        lamp(b, 14, 23, 28);
        lamp(b, 26, 23, 28);
        // fourth tier: strategy salon and the Fleet Admiral's office
        b.set(16, 25, 27, Blocks.CARTOGRAPHY_TABLE);
        b.set(23, 25, 31 - 2, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(22, 25, 29, up(chair, WEST));
        b.set(24, 25, 29, up(chair, EAST));
        b.fill(16, 25, 30, 18, 26, 30, S(Blocks.BOOKSHELF));
        lamp(b, 17, 27, 26);
        lamp(b, 23, 27, 26);
        b.fill(22, 29, 25, 24, 29, 25, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(23, 29, 24, up(chair, NORTH));
        b.set(22, 30, 25, LandmarkKit.candles(Blocks.CANDLE, 3));
        b.set(16, 29, 24, wallBanner(Blocks.BLUE_WALL_BANNER, SOUTH));
        b.fill(16, 29, 28, 16, 30, 30, S(Blocks.BOOKSHELF));
        b.set(17, 29, 30, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, NORTH));
        b.chest(24, 29, 30, WEST, Buildings.LOOT_MARINE);
        lamp(b, 17, 31, 27);
        lamp(b, 23, 31, 27);
        // the stair core through every floor
        switchback(b, S(Blocks.POLISHED_ANDESITE_STAIRS), 19, 25, 1, 0, SOUTH, 0, 8);

        // ---- people and markers
        LandmarkKit.npc(b, "marine", c, 1, 13);
        LandmarkKit.npc(b, "marine", 10, 1, 12);
        LandmarkKit.npc(b, "marine", 30, 1, 12);
        LandmarkKit.npc(b, "vice_admiral", c, 9, 21);
        LandmarkKit.npc(b, "admiral", 22, 29, 27);
    }

    /** One tier of the HQ: two storeys with floors, walls (pale masonry base on the bottom tier), blue bands, quoins, deck and teal skirt. */
    private static void hqTier(Builder b, Mats m, int x1, int z1, int x2, int z2, int y0, boolean base) {
        BlockState mas = S(ArchBlocks.PALE_MASONRY), pl = S(ArchBlocks.WHITE_PLASTER), blue = S(Blocks.BLUE_CONCRETE);
        for (int s = 0; s < 2; s++) {
            int ys = y0 + 4 * s;
            b.fill(x1, ys, z1, x2, ys, z2, m.floor);
            box(b, x1, ys + 1, z1, x2, ys + 3, z2, base && s == 0 ? mas : pl);
            b.clear(x1 + 1, ys + 1, z1 + 1, x2 - 1, ys + 3, z2 - 1);
        }
        b.fill(x1, y0 + 8, z1, x2, y0 + 8, z2, S(Blocks.SMOOTH_STONE));
        ring(b, x1, y0 + 4, z1, x2, z2, blue);
        ring(b, x1, y0 + 8, z1, x2, z2, blue);
        corners(b, x1, y0 + 1, z1, x2, y0 + 3, z2, mas);
        corners(b, x1, y0 + 5, z1, x2, y0 + 7, z2, mas);
        skirt(b, m.roofStairs, x1, z1, x2, z2, y0 + 8, 1, 2);
    }

    // ================================================================== Marine branch base (Shells Town, Loguetown)

    /**
     * East Blue Marine branch on 27x27: a walled compound with a gatehouse and flag masts, a training yard, a
     * two-storey barracks with a slate gable, an armoury shed with a cannon battery and the five-storey HQ tower
     * (pale masonry, blue bands, slate hip roof and flag) holding the front desk with the bounty board, the holding
     * cell, the officer's floor, the mess, the records room and the captain's office at the top.
     */
    static void marineBase(Builder b, Island island, BuildStyle st) {
        Mats m = marineMats(b.seed, false);
        int w = b.w, d = b.d;
        BlockState mas = S(ArchBlocks.PALE_MASONRY), pl = S(ArchBlocks.WHITE_PLASTER), blue = S(Blocks.BLUE_CONCRETE), chair = S(Blocks.SPRUCE_STAIRS);
        BlockState masStairs = S(ArchBlocks.PALE_MASONRY_STAIRS);
        b.foundation(0, 0, w - 1, d - 1, ArchBlocks.HARBOUR_STONE.get(), 16);
        b.clear(0, 1, 0, w - 1, 34, d - 1);
        LandmarkKit.patchGround(b, 11, 0, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.GRAVEL);
        // paved parade ground and paths
        b.fill(11, 0, 0, 17, 0, 13, S(ArchBlocks.HARBOUR_STONE));
        b.fill(2, 0, 3, 24, 0, 4, S(ArchBlocks.HARBOUR_STONE));
        b.fill(8, 0, 5, 9, 0, 24, S(ArchBlocks.HARBOUR_STONE));
        // ---- compound wall with merlons and a gatehouse
        box(b, 0, 1, 0, w - 1, 4, d - 1, mas);
        ring(b, 0, 3, 0, w - 1, d - 1, blue);
        for (int x = 0; x < w; x += 2) {
            b.set(x, 5, 0, mas);
            b.set(x, 5, d - 1, mas);
        }
        for (int z = 0; z < d; z += 2) {
            b.set(0, 5, z, mas);
            b.set(w - 1, 5, z, mas);
        }
        b.fill(10, 1, 0, 16, 7, 1, mas);
        b.fill(10, 6, 0, 16, 6, 1, blue);
        b.clear(11, 1, 0, 15, 4, 1);
        b.fill(11, 5, 0, 15, 5, 0, top(masStairs, NORTH));
        for (int x = 10; x <= 16; x += 2) b.set(x, 8, 0, mas);
        for (int x : new int[]{10, 16}) {
            b.fill(x, 8, 1, x, 13, 1, post(Blocks.IRON_BARS));
            b.set(x, 13, 0, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
            b.set(x, 12, 0, wallBanner(Blocks.WHITE_WALL_BANNER, NORTH));
            LandmarkKit.lampPost(b, x, 1, 2, Blocks.IRON_BARS, 2, ArchBlocks.BRASS_LANTERN.get());
        }
        // ---- training yard (west front): straw dummies, targets and a bench
        for (int x = 2; x <= 6; x += 2) {
            b.set(x, 1, 7, Blocks.HAY_BLOCK);
            b.set(x, 2, 7, Blocks.CARVED_PUMPKIN.defaultBlockState());
        }
        for (int x = 2; x <= 6; x += 2) b.set(x, 1, 10, Blocks.TARGET);
        b.set(7, 1, 9, decor(ArchBlocks.PLANK_BENCH, WEST));
        // flagpole on the parade ground
        b.set(19, 0, 9, mas);
        b.fill(19, 1, 9, 19, 11, 9, post(Blocks.IRON_BARS));
        b.set(19, 11, 8, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
        b.set(19, 10, 8, wallBanner(Blocks.WHITE_WALL_BANNER, NORTH));
        // battery facing the gate
        for (int x = 20; x <= 24; x += 2) b.set(x, 1, 5, decor(ArchBlocks.DECK_CANNON, NORTH));

        // ---- barracks (west): two storeys, slate gable along z
        int bx1 = 1, bx2 = 7, bz1 = 13, bz2 = 25;
        b.fill(bx1, 0, bz1, bx2, 0, bz2, m.floor);
        box(b, bx1, 1, bz1, bx2, 3, bz2, mas);
        b.fill(bx1, 4, bz1, bx2, 4, bz2, m.floor);
        ring(b, bx1, 4, bz1, bx2, bz2, blue);
        box(b, bx1, 5, bz1, bx2, 7, bz2, pl);
        b.clear(bx1 + 1, 1, bz1 + 1, bx2 - 1, 3, bz2 - 1);
        b.clear(bx1 + 1, 5, bz1 + 1, bx2 - 1, 7, bz2 - 1);
        corners(b, bx1, 5, bz1, bx2, 7, bz2, mas);
        Arch.gable(b, m, bx1, bz1, bx2, bz2, 8, false, 1, pl);
        b.bigDoor(bx2, 1, 16, Blocks.SPRUCE_DOOR, EAST);
        Arch.wallLantern(b, m, bx2, 3, 18, EAST);
        windowsZ(b, m, 20, 23, 3, 1, bx2, EAST, 2, true);
        windowsZ(b, m, 15, 23, 4, 5, bx2, EAST, 2, true);
        windowsX(b, m, 3, 5, 2, 1, bz1, NORTH, 2, true);
        windowsX(b, m, 3, 5, 2, 5, bz1, NORTH, 2, true);
        for (int z = 15; z <= 23; z += 2) b.bed(3, 1, z, Blocks.BLUE_BED, WEST);
        for (int z = 15; z <= 23; z += 2) b.bed(3, 5, z, Blocks.BLUE_BED, WEST);
        b.set(6, 1, 24, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(6, 1, 14, Blocks.CRAFTING_TABLE);
        Arch.staircase(b, S(Blocks.SPRUCE_STAIRS), 5, 1, 19, SOUTH, 4);
        lamp(b, 4, 3, 17);
        lamp(b, 4, 7, 17);

        // ---- armoury (east) with a flat parapet roof and the powder store
        int ax1 = 20, ax2 = 24, az1 = 8, az2 = 13;
        b.fill(ax1, 0, az1, ax2, 0, az2, m.floor);
        box(b, ax1, 1, az1, ax2, 3, az2, mas);
        b.clear(ax1 + 1, 1, az1 + 1, ax2 - 1, 3, az2 - 1);
        ring(b, ax1, 3, az1, ax2, az2, blue);
        Arch.flat(b, m, ax1, az1, ax2, az2, 4, false, true);
        b.bigDoor(22, 1, az1, Blocks.SPRUCE_DOOR, NORTH);
        for (int x = 21; x <= 23; x++) b.set(x, 1, 12, decor(ArchBlocks.DECK_CANNON, NORTH));
        Arch.cargo(b, 21, 1, 9, 0);
        Arch.cargo(b, 23, 1, 9, 2);
        lamp(b, 22, 3, 11);
        // supply yard behind it
        for (int z = 16; z <= 24; z += 2) Arch.cargo(b, 24, 1, z, z);
        b.set(22, 1, 23, decor(ArchBlocks.ROPE_COIL, NORTH));

        // ---- the HQ tower: five storeys, blue bands, slate hip roof with the flag
        int tx1 = 10, tx2 = 18, tz1 = 14, tz2 = 24;
        for (int f = 0; f < 5; f++) {
            int y = 4 * f;
            b.fill(tx1, y, tz1, tx2, y, tz2, m.floor);
            box(b, tx1, y + 1, tz1, tx2, y + 3, tz2, f == 0 ? mas : pl);
            b.clear(tx1 + 1, y + 1, tz1 + 1, tx2 - 1, y + 3, tz2 - 1);
            if (f > 0) ring(b, tx1, y, tz1, tx2, tz2, blue);
            corners(b, tx1, y + 1, tz1, tx2, y + 3, tz2, mas);
            int wy = y + 1;
            if (f > 0) windowsX(b, m, tx1 + 2, tx2 - 2, 2, wy, tz1, NORTH, 2, false);
            windowsX(b, m, tx1 + 2, tx2 - 2, 4, wy, tz2, SOUTH, 2, true);
            windowsZ(b, m, tz1 + 2, tz2 - 2, 3, wy, tx1, WEST, 2, true);
            windowsZ(b, m, tz1 + 2, tz2 - 2, 3, wy, tx2, EAST, 2, true);
        }
        b.fill(tx1, 20, tz1, tx2, 20, tz2, m.floor);
        ring(b, tx1, 20, tz1, tx2, tz2, blue);
        cornice(b, masStairs, tx1, tz1, tx2, tz2, 20);
        int top = Arch.hip(b, m, tx1, tz1, tx2, tz2, 21, 1);
        b.fill(14, top + 1, 19, 14, top + 6, 19, post(Blocks.IRON_BARS));
        b.set(14, top + 6, 18, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
        b.set(14, top + 5, 18, wallBanner(Blocks.WHITE_WALL_BANNER, NORTH));
        // entrance porch with marble columns and a blue awning
        b.fill(12, 0, 12, 16, 0, 13, mas);
        b.fill(12, 1, 12, 12, 3, 12, col(ArchBlocks.MARBLE_COLUMN));
        b.fill(16, 1, 12, 16, 3, 12, col(ArchBlocks.MARBLE_COLUMN));
        b.fill(12, 4, 12, 16, 4, 13, S(ArchBlocks.PALE_MASONRY_SLAB));
        for (int x = 13; x <= 15; x++) b.set(x, 3, 11, decor(ArchBlocks.BLUE_AWNING, NORTH));
        b.bigDoor(14, 1, tz1, Blocks.SPRUCE_DOOR, NORTH);
        b.set(12, 2, tz1 - 1, wallSign(Blocks.OAK_WALL_SIGN, NORTH));
        b.marker("bounty_board", 12, 2, tz1 - 1, new net.minecraft.nbt.CompoundTag());
        // ground floor: front desk, the holding cell (back west)
        b.set(11, 1, 16, decor(ArchBlocks.SHOP_COUNTER, EAST));
        b.set(11, 1, 17, decor(ArchBlocks.SHOP_COUNTER, EAST));
        b.set(11, 1, 18, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, EAST));
        b.fill(11, 1, 20, 14, 3, 20, Arch.along(S(Blocks.IRON_BARS), Direction.Axis.X));
        b.set(14, 1, 20, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, NORTH));
        b.set(14, 2, 20, Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        b.fill(15, 1, 21, 15, 3, 23, Arch.along(S(Blocks.IRON_BARS), Direction.Axis.Z));
        b.set(15, 1, 20, S(Blocks.IRON_BARS));
        b.bed(11, 1, 22, Blocks.WHITE_BED, SOUTH);
        b.marker("cell", 13, 1, 22, new net.minecraft.nbt.CompoundTag());
        lamp(b, 13, 3, 17);
        // stair: lanes x 16/17 from z 16 (landings at z 15 and z 20)
        switchback(b, S(Blocks.POLISHED_ANDESITE_STAIRS), 16, 16, 1, 0, SOUTH, 0, 4);
        // floor 1: officers; floor 2: mess; floor 3: records; floor 4: captain's office
        desk(b, 12, 5, 16, SOUTH, chair);
        b.fill(11, 5, 23, 14, 6, 23, S(Blocks.BOOKSHELF));
        b.bed(11, 5, 19, Blocks.BLUE_BED, SOUTH);
        b.bed(13, 5, 20, Blocks.BLUE_BED, SOUTH);
        lamp(b, 13, 7, 18);
        LandmarkKit.longTableZ(b, 15, 22, 9, 12, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        b.set(14, 9, 23, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, NORTH));
        b.set(15, 9, 23, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        lamp(b, 13, 11, 19);
        b.fill(11, 13, 15, 11, 14, 23, S(Blocks.BOOKSHELF));
        b.set(13, 13, 18, Blocks.CARTOGRAPHY_TABLE);
        b.set(13, 13, 21, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, NORTH));
        lamp(b, 13, 15, 19);
        desk(b, 13, 17, 17, SOUTH, chair);
        b.set(12, 17, 17, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(11, 17, 15, wallBanner(Blocks.BLUE_WALL_BANNER, EAST));
        b.chest(11, 17, 23, EAST, Buildings.LOOT_MARINE);
        b.fill(12, 17, 23, 14, 18, 23, S(Blocks.BOOKSHELF));
        lamp(b, 13, 19, 19);
        // people
        LandmarkKit.npc(b, "marine", 13, 1, 5);
        LandmarkKit.npc(b, "marine", 4, 1, 9);
        LandmarkKit.npc(b, "marine", 21, 1, 7);
        LandmarkKit.npc(b, "marine_officer", 13, 5, 18);
        LandmarkKit.npc(b, "marine_captain", 13, 17, 19);
    }
}
