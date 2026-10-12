package com.grandline.world.landmark;

import com.grandline.registry.ArchBlocks;
import com.grandline.world.build.Arch;
import com.grandline.world.build.Arch.Mats;
import com.grandline.world.build.Builder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Construction helpers shared by the landmark set pieces of Water 7, Enies Lobby, Thriller Bark, Kuraigana, Sabaody,
 * Amazon Lily, Punk Hazard, Dressrosa, Green Bit, Zou and Whole Cake Island: storeyed wall boxes with plinth, posts and
 * floor bands, rows of framed windows, entrances, straight and square-spiral staircases, conical spires, a pixel font
 * for painted numbers, flags and bubbles. Everything writes through the landmark's {@link Builder} in local
 * coordinates, so it rotates with the landmark; randomness only through {@link Builder#rand}.
 */
final class LandmarksC {
    private LandmarksC() {}

    static BlockState S(DeferredBlock<Block> d) {
        return d.get().defaultBlockState();
    }

    static BlockState S(Block b) {
        return b.defaultBlockState();
    }

    static BlockState stairS(BlockState s, Direction facing, Half half) {
        return Arch.stair(s, facing, half);
    }

    static BlockState slabS(BlockState s, SlabType t) {
        return Arch.slab(s, t);
    }

    static BlockState log(Block b, Direction.Axis a) {
        return Arch.axis(b.defaultBlockState(), a);
    }

    static BlockState log(DeferredBlock<Block> b, Direction.Axis a) {
        return Arch.axis(b.get().defaultBlockState(), a);
    }

    static void roofTiles(Mats m, DeferredBlock<Block> roof, DeferredBlock<Block> roofStairs, DeferredBlock<Block> roofSlab) {
        m.roof = S(roof);
        m.roofStairs = S(roofStairs);
        m.roofSlab = S(roofSlab);
    }

    static void roofTiles(Mats m, Block roof, Block roofStairs, Block roofSlab) {
        m.roof = S(roof);
        m.roofStairs = S(roofStairs);
        m.roofSlab = S(roofSlab);
    }

    // ------------------------------------------------------------------ walls

    static void wallsS(Builder b, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        b.fill(x1, y1, z1, x2, y2, z1, s);
        b.fill(x1, y1, z2, x2, y2, z2, s);
        b.fill(x1, y1, z1, x1, y2, z2, s);
        b.fill(x2, y1, z1, x2, y2, z2, s);
    }

    /** Horizontal band at y in the four wall planes of the box (beams if the frame is timber, otherwise trim). */
    static void band(Builder b, Mats m, int x1, int z1, int x2, int z2, int y) {
        BlockState bx = m.timbered() ? m.beam(Direction.Axis.X) : m.trim;
        BlockState bz = m.timbered() ? m.beam(Direction.Axis.Z) : m.trim;
        b.fill(x1, y, z1, x2, y, z1, bx);
        b.fill(x1, y, z2, x2, y, z2, bx);
        b.fill(x1, y, z1, x1, y, z2, bz);
        b.fill(x2, y, z1, x2, y, z2, bz);
    }

    /**
     * A storeyed wall box: the ground floor at y0 (floor layer at y0, a plinth course of m.base at y0+1), {@code n}
     * storeys of height {@code h} (walls from fy+1 to fy+h-1, a band at fy+h which is also the next floor), corner posts
     * of the frame material, upper floors filled with m.floor, the ground floor with {@code groundFloor}. The interior
     * is cleared. Returns the y of the top band (a roof starts at top+1).
     */
    static int storeys(Builder b, Mats m, int x1, int y0, int z1, int x2, int z2, int n, int h, BlockState groundFloor) {
        b.fill(x1, y0, z1, x2, y0, z2, m.base);
        if (groundFloor != null) b.fill(x1 + 1, y0, z1 + 1, x2 - 1, y0, z2 - 1, groundFloor);
        for (int f = 0; f < n; f++) {
            int fy = y0 + f * h;
            wallsS(b, x1, fy + 1, z1, x2, fy + h - 1, z2, f == 0 ? m.wall : m.upperWall);
            b.clear(x1 + 1, fy + 1, z1 + 1, x2 - 1, fy + h - 1, z2 - 1);
            band(b, m, x1, z1, x2, z2, fy + h);
            b.fill(x1 + 1, fy + h, z1 + 1, x2 - 1, fy + h, z2 - 1, m.floor);
        }
        wallsS(b, x1, y0 + 1, z1, x2, y0 + 1, z2, m.base);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(c[0], y0 + 1, c[1], c[0], y0 + n * h, c[1], m.frame);
        return y0 + n * h;
    }

    /** Windows along x in the wall plane z (outside toward {@code out}) every {@code step} from x1 to x2, skipping |x-skip|<=skipR. */
    static void winX(Builder b, Mats m, int x1, int x2, int y, int z, Direction out, int step, int h, boolean shutters, int skip, int skipR) {
        for (int x = x1; x <= x2; x += step) {
            if (Math.abs(x - skip) <= skipR) continue;
            Arch.window(b, m, x, y, z, out, h, shutters);
        }
    }

    static void winZ(Builder b, Mats m, int z1, int z2, int y, int x, Direction out, int step, int h, boolean shutters, int skip, int skipR) {
        for (int z = z1; z <= z2; z += step) {
            if (Math.abs(z - skip) <= skipR) continue;
            Arch.window(b, m, x, y, z, out, h, shutters);
        }
    }

    // ------------------------------------------------------------------ circulation

    /** Straight flight of n steps climbing toward dir from (x, y, z), width cells toward dir's clockwise side, headroom cleared. */
    static void flight(Builder b, BlockState stairs, int x, int y, int z, Direction dir, int n, int width, BlockState support) {
        Direction side = dir.getClockWise();
        for (int i = 0; i < n; i++)
            for (int k = 0; k < width; k++) {
                int px = x + dir.getStepX() * i + side.getStepX() * k, pz = z + dir.getStepZ() * i + side.getStepZ() * k;
                b.set(px, y + i, pz, stairS(stairs, dir, Half.BOTTOM));
                b.clear(px, y + i + 1, pz, px, y + i + 3, pz);
                if (support != null && i > 0) b.fill(px, y, pz, px, y + i - 1, pz, support);
            }
    }

    /**
     * Square spiral staircase along the inner ring [x1..x2]x[z1..z2] (one cell wide), climbing clockwise as seen from
     * above (east along z1, south along x2, west along z2, north along x1) from the corner (x1, z1) on a floor at
     * {@code floorY} until a step's top reaches {@code topY}. Corners are landings. Headroom is cleared above every
     * cell and each step rests on a support block. Returns the (x, z) of the last cell (where it lands on topY).
     */
    static int[] spiral(Builder b, int x1, int z1, int x2, int z2, int floorY, int topY, BlockState stairs, BlockState support) {
        int x = x1, z = z1, y = floorY; // y = height of the last placed tread block (floor at start)
        Direction dir = Direction.EAST;
        int guard = 0;
        while (y < topY - 1 && guard++ < 400) {
            int nx = x + dir.getStepX(), nz = z + dir.getStepZ();
            if (nx < x1 || nx > x2 || nz < z1 || nz > z2) {
                dir = dir.getClockWise();
                continue;
            }
            x = nx;
            z = nz;
            boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
            if (corner) {
                b.set(x, y, z, support);
            } else {
                y++;
                b.set(x, y, z, stairS(stairs, dir, Half.BOTTOM));
                if (y - 1 > floorY) b.set(x, y - 1, z, support);
            }
            b.clear(x, y + 1, z, x, y + 3, z);
        }
        return new int[]{x, z};
    }

    // ------------------------------------------------------------------ roofs and shapes

    /** Conical spire of roof blocks: radius r at y, narrowing by one every {@code rise} blocks, with a finial. */
    static int spire(Builder b, double cx, double cz, int y, double r, int rise, BlockState block, BlockState finial) {
        int yy = y;
        double rr = r;
        while (rr >= 0.5) {
            for (int k = 0; k < rise; k++) LandmarkKit.disc(b, cx, cz, yy++, rr, block);
            rr -= 1;
        }
        b.set((int) Math.round(cx), yy, (int) Math.round(cz), block);
        if (finial != null) b.set((int) Math.round(cx), yy + 1, (int) Math.round(cz), finial);
        return yy + 1;
    }

    /**
     * Steep square spire over [x1..x2]x[z1..z2] from y: each course is inset by one and {@code rise} blocks tall, its
     * lowest layer edged with inward-facing stairs; a finial on the point. Returns the y above the finial.
     */
    static int squareSpire(Builder b, int x1, int z1, int x2, int z2, int y, int rise, BlockState block, BlockState stairs, BlockState finial) {
        int k = 0, yy = y;
        while (x1 + k <= x2 - k && z1 + k <= z2 - k) {
            int ax = x1 + k, bx = x2 - k, az = z1 + k, bz = z2 - k;
            if (bx - ax <= 1 && bz - az <= 1) {
                b.fill(ax, yy, az, bx, yy + rise - 1, bz, block);
                yy += rise;
                break;
            }
            for (int j = 0; j < rise; j++, yy++) {
                b.fill(ax, yy, az, bx, yy, bz, block);
                if (j != 0) continue;
                for (int x = ax + 1; x < bx; x++) {
                    b.set(x, yy, az, stairS(stairs, Direction.SOUTH, Half.BOTTOM));
                    b.set(x, yy, bz, stairS(stairs, Direction.NORTH, Half.BOTTOM));
                }
                for (int z = az + 1; z < bz; z++) {
                    b.set(ax, yy, z, stairS(stairs, Direction.EAST, Half.BOTTOM));
                    b.set(bx, yy, z, stairS(stairs, Direction.WEST, Half.BOTTOM));
                }
                b.set(ax, yy, az, Arch.stair(stairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
                b.set(bx, yy, az, Arch.stair(stairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
                b.set(ax, yy, bz, Arch.stair(stairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
                b.set(bx, yy, bz, Arch.stair(stairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
            }
            k++;
        }
        if (finial != null) b.set((x1 + x2) / 2, yy, (z1 + z2) / 2, finial);
        return yy + 1;
    }

    /** Ring of outward-stepping stairs (an eave) round a disc of radius r at y: makes cones and domes read as roofs. */
    static void eaveRing(Builder b, double cx, double cz, int y, double r, BlockState stairs) {
        for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r + 1); x++)
            for (int z = (int) Math.floor(cz - r - 1); z <= (int) Math.ceil(cz + r + 1); z++) {
                double dx = x - cx, dz = z - cz, d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5 || d <= r - 0.6) continue;
                Direction in = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                b.set(x, y, z, stairS(stairs, in, Half.BOTTOM));
            }
    }

    /** Pyramid/hip cap over a square tower top [x1..x2]x[z1..z2] at y using stairs, with outer corner shapes. */
    static int pyramid(Builder b, int x1, int z1, int x2, int z2, int y, BlockState stairs, BlockState full, BlockState finial) {
        int i = 0;
        while (x1 + i <= x2 - i && z1 + i <= z2 - i) {
            int ax = x1 + i, bx = x2 - i, az = z1 + i, bz = z2 - i, yy = y + i;
            if (ax == bx || az == bz || bx - ax == 1 || bz - az == 1) {
                b.fill(ax, yy, az, bx, yy, bz, full);
                if (finial != null) b.set((ax + bx) / 2, yy + 1, (az + bz) / 2, finial);
                return yy + 1;
            }
            for (int x = ax + 1; x < bx; x++) {
                b.set(x, yy, az, stairS(stairs, Direction.SOUTH, Half.BOTTOM));
                b.set(x, yy, bz, stairS(stairs, Direction.NORTH, Half.BOTTOM));
            }
            for (int z = az + 1; z < bz; z++) {
                b.set(ax, yy, z, stairS(stairs, Direction.EAST, Half.BOTTOM));
                b.set(bx, yy, z, stairS(stairs, Direction.WEST, Half.BOTTOM));
            }
            b.set(ax, yy, az, Arch.stair(stairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
            b.set(bx, yy, az, Arch.stair(stairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
            b.set(ax, yy, bz, Arch.stair(stairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
            b.set(bx, yy, bz, Arch.stair(stairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
            i++;
        }
        return y + i;
    }

    // ------------------------------------------------------------------ signs, flags, bubbles

    private static final String[] DIGITS = {
            "###,#.#,#.#,#.#,###", ".#.,##.,.#.,.#.,###", "###,..#,###,#..,###", "###,..#,.##,..#,###", "#.#,#.#,###,..#,..#",
            "###,#..,###,..#,###", "###,#..,###,#.#,###", "###,..#,.#.,.#.,.#.", "###,#.#,###,#.#,###", "###,#.#,###,..#,###"};

    /**
     * Paints a number in a 3x5 pixel font on a wall plane: the glyph's top-left at (x, yTop, z), reading toward
     * {@code right} (the viewer's right hand), each digit 3 wide with a one-block gap.
     */
    static void number(Builder b, String n, int x, int yTop, int z, Direction right, BlockState ink) {
        int col = 0;
        for (char ch : n.toCharArray()) {
            String[] rows = DIGITS[ch - '0'].split(",");
            for (int r = 0; r < 5; r++)
                for (int c = 0; c < 3; c++)
                    if (rows[r].charAt(c) == '#') b.set(x + right.getStepX() * (col + c), yTop - r, z + right.getStepZ() * (col + c), ink);
            col += 4;
        }
    }

    /** Flag pole of height h at (x, y, z) with a cloth of {@code len} blocks streaming toward {@code toward}, 2 rows of two colours. */
    static void flag(Builder b, int x, int y, int z, int h, Direction toward, int len, BlockState c1, BlockState c2) {
        b.fill(x, y, z, x, y + h - 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        b.set(x, y + h, z, Blocks.LIGHTNING_ROD.defaultBlockState());
        for (int k = 1; k <= len; k++) {
            b.set(x + toward.getStepX() * k, y + h - 1, z + toward.getStepZ() * k, c1);
            b.set(x + toward.getStepX() * k, y + h - 2, z + toward.getStepZ() * k, c2);
        }
    }

    static void bubble(Builder b, int x, int y, int z, double r, BlockState glass) {
        LandmarkKit.ellipsoid(b, x, y, z, r, r, r, glass, true, -100);
    }

    /** A row of bollards every {@code step} along x at z on the quay surface y. */
    static void bollardsX(Builder b, int x1, int x2, int y, int z, int step) {
        for (int x = x1; x <= x2; x += step) b.set(x, y, z, Arch.decor(ArchBlocks.BOLLARD, Direction.NORTH));
    }

    /** Hanging lantern state of any lantern block (paper lanterns too). */
    static BlockState hang(BlockState l) {
        return Arch.lantern(l, true);
    }

    static BlockState stand(BlockState l) {
        return Arch.lantern(l, false);
    }

    static BlockState decor(DeferredBlock<Block> d, Direction f) {
        return Arch.decor(d, f);
    }
}
