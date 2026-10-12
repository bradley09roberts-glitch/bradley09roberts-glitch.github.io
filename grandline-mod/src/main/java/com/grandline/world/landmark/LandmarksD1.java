package com.grandline.world.landmark;

import com.grandline.registry.ArchBlocks;
import com.grandline.world.build.Arch;
import com.grandline.world.build.Arch.Mats;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Shared architecture for the Wano, Onigashima, Baltigo, Egghead and Hachinosu set pieces and the later canon islands
 * (FinalSeaLandmarks, LandmarkBuildersExtra): Japanese castle tiers with half-pitch pent-roof skirts, hip-and-gable
 * (irimoya) top roofs, chidori gable dormers, battered ishigaki bases, dobei walls, torii and stone lanterns; plus a
 * few generic helpers (conical tower roofs, crenellated parapets, hull sections). Everything is written in the
 * builder's local frame, so it rotates with the landmark; randomness only through {@link Builder#rand}.
 */
final class LandmarksD1 {
    private LandmarksD1() {}

    static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    static BlockState k(DeferredBlock<Block> b) {
        return b.get().defaultBlockState();
    }

    static BlockState beam(DeferredBlock<Block> b, Direction.Axis a) {
        return Arch.axis(k(b), a);
    }

    static BlockState log(Block b, Direction.Axis a) {
        return Arch.axis(s(b), a);
    }

    /** Wano materials with no window sills (Japanese walls have flush lattice windows). */
    static Mats wano(Builder b) {
        Mats m = Mats.of(BuildStyle.WANO, b.seed);
        m.sill = null;
        return m;
    }

    // ================================================================== Japanese castle vocabulary

    /**
     * White-plaster storey walls over [x1..x2]x[z1..z2] from y1 to y2: dark beam posts at the corners and every
     * {@code bay} blocks measured from the centre of each face, a dark head beam at {@code y2} if {@code headBeam},
     * and flush lattice windows (two tall, from y1+1) centred between the posts when {@code windows}.
     */
    static void wanoWalls(Builder b, Mats m, int x1, int z1, int x2, int z2, int y1, int y2, int bay, boolean windows, boolean headBeam) {
        BlockState wall = m.wall;
        b.fill(x1, y1, z1, x2, y2, z1, wall);
        b.fill(x1, y1, z2, x2, y2, z2, wall);
        b.fill(x1, y1, z1, x1, y2, z2, wall);
        b.fill(x2, y1, z1, x2, y2, z2, wall);
        int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
        BlockState post = beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y);
        for (int x = x1; x <= x2; x++) {
            boolean p = x == x1 || x == x2 || Math.floorMod(x - cx, bay) == 0;
            boolean w = !p && Math.floorMod(x - cx, bay) == bay / 2;
            if (p) {
                b.fill(x, y1, z1, x, y2, z1, post);
                b.fill(x, y1, z2, x, y2, z2, post);
            } else if (w && windows) {
                for (int y = y1 + 1; y <= Math.min(y1 + 2, y2 - 1); y++) {
                    b.set(x, y, z1, Arch.along(m.window, Direction.Axis.X));
                    b.set(x, y, z2, Arch.along(m.window, Direction.Axis.X));
                }
            }
        }
        for (int z = z1; z <= z2; z++) {
            boolean p = z == z1 || z == z2 || Math.floorMod(z - cz, bay) == 0;
            boolean w = !p && Math.floorMod(z - cz, bay) == bay / 2;
            if (p) {
                b.fill(x1, y1, z, x1, y2, z, post);
                b.fill(x2, y1, z, x2, y2, z, post);
            } else if (w && windows) {
                for (int y = y1 + 1; y <= Math.min(y1 + 2, y2 - 1); y++) {
                    b.set(x1, y, z, Arch.along(m.window, Direction.Axis.Z));
                    b.set(x2, y, z, Arch.along(m.window, Direction.Axis.Z));
                }
            }
        }
        if (headBeam) {
            b.fill(x1, y2, z1, x2, y2, z1, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
            b.fill(x1, y2, z2, x2, y2, z2, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
            b.fill(x1, y2, z1, x1, y2, z2, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
            b.fill(x2, y2, z1, x2, y2, z2, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
        }
    }

    /**
     * Half-pitch pent-roof skirt (the roof between two castle tiers) around the upper tier's wall rectangle
     * [x1..x2]x[z1..z2]. The lower tier's walls stand {@code inset} blocks further out and top out at y-1 (y is the
     * upper tier's floor level); the skirt overhangs them by {@code ov}. Rings rise half a block each from the eave
     * (a bottom slab at y-1) to the upper wall; the outermost corners kick up into horn tips, and dark brackets sit
     * under the eave at every post.
     */
    static void skirt(Builder b, Mats m, int x1, int z1, int x2, int z2, int y, int inset, int ov, int bracketBay) {
        int n = inset + ov;
        for (int d = n; d >= 1; d--) {
            int kHalf = n - d;
            int yy = y - 1 + kHalf / 2;
            BlockState sl = Arch.slab(m.roofSlab, kHalf % 2 == 0 ? SlabType.BOTTOM : SlabType.TOP);
            int ax = x1 - d, bx = x2 + d, az = z1 - d, bz = z2 + d;
            for (int x = ax; x <= bx; x++) {
                b.set(x, yy, az, sl);
                b.set(x, yy, bz, sl);
            }
            for (int z = az; z <= bz; z++) {
                b.set(ax, yy, z, sl);
                b.set(bx, yy, z, sl);
            }
        }
        // horn tips at the outer corners
        int ax = x1 - n, bx = x2 + n, az = z1 - n, bz = z2 + n;
        hornCorners(b, m, ax, az, bx, bz, y - 1);
        // brackets under the eave, outside the lower walls
        if (bracketBay > 0) {
            int lx1 = x1 - inset, lx2 = x2 + inset, lz1 = z1 - inset, lz2 = z2 + inset;
            int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
            BlockState br = s(Blocks.DARK_OAK_STAIRS);
            for (int x = lx1; x <= lx2; x++) {
                if (Math.floorMod(x - cx, bracketBay) != 0 && x != lx1 && x != lx2) continue;
                b.set(x, y - 2, lz1 - 1, Arch.stair(br, Direction.SOUTH, Half.TOP));
                b.set(x, y - 2, lz2 + 1, Arch.stair(br, Direction.NORTH, Half.TOP));
            }
            for (int z = lz1; z <= lz2; z++) {
                if (Math.floorMod(z - cz, bracketBay) != 0 && z != lz1 && z != lz2) continue;
                b.set(lx1 - 1, y - 2, z, Arch.stair(br, Direction.EAST, Half.TOP));
                b.set(lx2 + 1, y - 2, z, Arch.stair(br, Direction.WEST, Half.TOP));
            }
        }
    }

    /** Upturned eave corners: a top slab at y and an outer-corner horn stair above it at each corner of the rectangle. */
    static void hornCorners(Builder b, Mats m, int ax, int az, int bx, int bz, int y) {
        BlockState top = Arch.slab(m.roofSlab, SlabType.TOP);
        b.set(ax, y, az, top);
        b.set(bx, y, az, top);
        b.set(ax, y, bz, top);
        b.set(bx, y, bz, top);
        b.set(ax, y + 1, az, Arch.stair(m.roofStairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
        b.set(bx, y + 1, az, Arch.stair(m.roofStairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
        b.set(ax, y + 1, bz, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
        b.set(bx, y + 1, bz, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
    }

    /**
     * Hip-and-gable (irimoya) roof over the wall box [x1..x2]x[z1..z2] whose walls top out at y-1, with overhang ov:
     * {@code hipRings} rings of stairs on all four sides (the lowest with upturned corners), then a gable roof along
     * the long axis whose triangles are filled with {@code gable} and framed by a dark beam; a ridge cap and golden
     * ridge-end ornaments (shachihoko) when {@code gold}. Returns the ridge height.
     */
    static int irimoya(Builder b, Mats m, int x1, int z1, int x2, int z2, int y, int ov, int hipRings, BlockState gable, boolean gold) {
        boolean alongX = (x2 - x1) >= (z2 - z1);
        int ax = x1 - ov, bx = x2 + ov, az = z1 - ov, bz = z2 + ov;
        int top = y;
        for (int i = 0; ; i++) {
            int rx1 = ax + i, rx2 = bx - i, rz1 = az + i, rz2 = bz - i, yy = y + i;
            if (rx1 > rx2 || rz1 > rz2) break;
            boolean hip = i < hipRings;
            // the short (gable) ends stop moving in once the hip rings are done
            // (the gable roof overhangs its triangle by one block: a bargeboard over the last hip ring)
            int gx1 = alongX ? (hip ? rx1 : ax + hipRings - 1) : rx1, gx2 = alongX ? (hip ? rx2 : bx - hipRings + 1) : rx2;
            int gz1 = alongX ? rz1 : (hip ? rz1 : az + hipRings - 1), gz2 = alongX ? rz2 : (hip ? rz2 : bz - hipRings + 1);
            top = yy;
            if (alongX ? rz1 == rz2 : rx1 == rx2) { // ridge line
                for (int x = gx1; x <= gx2; x++)
                    for (int z = gz1; z <= gz2; z++) {
                        b.set(x, yy - 1, z, m.roof);
                        b.set(x, yy, z, Arch.slab(m.roofSlab, SlabType.BOTTOM));
                    }
                if (gold) {
                    if (alongX) {
                        b.set(gx1, yy + 1, rz1, s(Blocks.GOLD_BLOCK));
                        b.set(gx2, yy + 1, rz1, s(Blocks.GOLD_BLOCK));
                    } else {
                        b.set(rx1, yy + 1, gz1, s(Blocks.GOLD_BLOCK));
                        b.set(rx1, yy + 1, gz2, s(Blocks.GOLD_BLOCK));
                    }
                }
                break;
            }
            if (alongX ? rz2 - rz1 == 1 : rx2 - rx1 == 1) { // two-wide ridge: stairs meeting
                for (int x = gx1; x <= gx2; x++)
                    for (int z = gz1; z <= gz2; z++) {
                        Direction f = alongX ? (z == rz1 ? Direction.SOUTH : Direction.NORTH) : (x == rx1 ? Direction.EAST : Direction.WEST);
                        b.set(x, yy, z, Arch.stair(m.roofStairs, f, Half.BOTTOM));
                    }
                break;
            }
            if (alongX) {
                for (int x = gx1; x <= gx2; x++) {
                    b.set(x, yy, rz1, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
                    b.set(x, yy, rz2, Arch.stair(m.roofStairs, Direction.NORTH, Half.BOTTOM));
                }
                if (hip) for (int z = rz1 + 1; z < rz2; z++) {
                    b.set(rx1, yy, z, Arch.stair(m.roofStairs, Direction.EAST, Half.BOTTOM));
                    b.set(rx2, yy, z, Arch.stair(m.roofStairs, Direction.WEST, Half.BOTTOM));
                }
            } else {
                for (int z = gz1; z <= gz2; z++) {
                    b.set(rx1, yy, z, Arch.stair(m.roofStairs, Direction.EAST, Half.BOTTOM));
                    b.set(rx2, yy, z, Arch.stair(m.roofStairs, Direction.WEST, Half.BOTTOM));
                }
                if (hip) for (int x = rx1 + 1; x < rx2; x++) {
                    b.set(x, yy, rz1, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
                    b.set(x, yy, rz2, Arch.stair(m.roofStairs, Direction.NORTH, Half.BOTTOM));
                }
            }
            if (hip) {
                b.set(rx1, yy, rz1, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
                b.set(rx2, yy, rz1, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
                b.set(rx1, yy, rz2, Arch.stair(m.roofStairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
                b.set(rx2, yy, rz2, Arch.stair(m.roofStairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
                if (i == 0) hornCorners(b, m, rx1, rz1, rx2, rz2, yy);
            }
        }
        // gable triangles in the planes where the hip rings stop, under the gable roof, framed by a dark beam
        if (gable != null) {
            int g1 = (alongX ? ax : az) + hipRings, g2 = (alongX ? bx : bz) - hipRings;
            int lo = alongX ? az : ax, hi = alongX ? bz : bx;
            for (int a = lo + hipRings; a <= hi - hipRings; a++) {
                int r = Math.min(a - lo, hi - a); // ring index of the roof surface above this cell
                for (int gy = y + hipRings - 1; gy <= y + r - 1; gy++) {
                    BlockState fill = gy == y + hipRings - 1 ? beam(ArchBlocks.DARK_BEAM, alongX ? Direction.Axis.Z : Direction.Axis.X) : gable;
                    if (alongX) {
                        b.set(g1, gy, a, fill);
                        b.set(g2, gy, a, fill);
                    } else {
                        b.set(a, gy, g1, fill);
                        b.set(a, gy, g2, fill);
                    }
                }
            }
        }
        return top;
    }

    /**
     * A chidori-hafu: a triangular gable dormer standing on a skirt, facing {@code out}. The skirt rings around the
     * upper wall plane run from v=1 to v=n (n = inset+ov) at half-pitch from the eave at y-1; the dormer's ridge runs
     * perpendicular to the wall at (along-face) centre {@code cu}, half-width hw, its face (white plaster with a gold
     * pendant) one block in from the eave. {@code wallX1..wallZ2} is the upper tier's wall rectangle.
     */
    static void chidori(Builder b, Mats m, Direction out, int wallX1, int wallZ1, int wallX2, int wallZ2, int cu, int y, int inset, int ov, int hw) {
        int n = inset + ov;
        Direction cw = out.getClockWise();
        int bx, bz;
        switch (out) {
            case NORTH -> { bx = cu; bz = wallZ1; }
            case SOUTH -> { bx = cu; bz = wallZ2; }
            case WEST -> { bx = wallX1; bz = cu; }
            default -> { bx = wallX2; bz = cu; }
        }
        for (int v = 1; v <= n; v++) {
            int bottom = v > inset ? y - 1 : y;
            for (int u = -hw; u <= hw; u++) {
                int h = y - 1 + (hw - Math.abs(u));
                int x = bx + out.getStepX() * v + cw.getStepX() * u, z = bz + out.getStepZ() * v + cw.getStepZ() * u;
                if ((hw - Math.abs(u)) * 2 < (n - v)) continue; // the skirt is higher here
                if (u == 0) {
                    b.set(x, h, z, Arch.slab(m.roofSlab, SlabType.BOTTOM));
                    if (v == n) b.set(x, h - 1, z, m.roof); // ridge-end tile over the face
                    else for (int yy = bottom; yy < h; yy++) b.set(x, yy, z, m.roof);
                } else {
                    Direction f = u < 0 ? cw : cw.getOpposite();
                    b.set(x, h, z, Arch.stair(m.roofStairs, f, Half.BOTTOM));
                    if (v < n) for (int yy = bottom; yy < h; yy++) b.set(x, yy, z, m.roof);
                }
            }
        }
        // the gable face one block in from the eave
        int v = n - 1;
        for (int u = -hw + 1; u <= hw - 1; u++) {
            int h = y - 1 + (hw - Math.abs(u));
            int x = bx + out.getStepX() * v + cw.getStepX() * u, z = bz + out.getStepZ() * v + cw.getStepZ() * u;
            for (int yy = y - 1; yy < h; yy++) b.set(x, yy, z, yy == y - 1 ? beam(ArchBlocks.DARK_BEAM, cw.getAxis()) : m.wall);
            if (u == 0 && h - 1 > y - 1) b.set(x, h - 1, z, s(Blocks.GOLD_BLOCK));
        }
    }

    /**
     * Battered castle base (ishigaki) over [x1..x2]x[z1..z2] from y=1 to y=h, solid: each course steps in along a
     * concave curve ({@code batter} blocks in total), with stair blocks dressing every ledge, rubble fieldstone faces
     * and dressed corner stones alternating by course. Returns nothing; the top surface is at y=h.
     */
    static void ishigaki(Builder b, int x1, int z1, int x2, int z2, int h, int batter) {
        BlockState face = k(ArchBlocks.FIELDSTONE), dressed = s(Blocks.STONE_BRICKS), dressed2 = s(Blocks.POLISHED_ANDESITE);
        BlockState mossy = s(Blocks.MOSSY_STONE_BRICKS);
        for (int y = 1; y <= h; y++) {
            int in = inset(y, h, batter), inUp = y < h ? inset(y + 1, h, batter) : in;
            int ax = x1 + in, bx = x2 - in, az = z1 + in, bz = z2 - in;
            for (int x = ax; x <= bx; x++)
                for (int z = az; z <= bz; z++) {
                    boolean edgeX = x == ax || x == bx, edgeZ = z == az || z == bz;
                    BlockState s = face;
                    if (edgeX && edgeZ) s = (y & 1) == 0 ? dressed : dressed2;
                    else if ((edgeX && (z == az + 1 || z == bz - 1) && (y & 1) == 1) || (edgeZ && (x == ax + 1 || x == bx - 1) && (y & 1) == 0)) s = dressed;
                    else if ((edgeX || edgeZ) && y <= 2 && b.rand(x * 31 + z * 17 + y, 7) == 0) s = mossy;
                    b.set(x, y, z, s);
                }
            if (inUp > in) { // dress the ledge this course leaves with stairs rising inward
                BlockState st = k(ArchBlocks.FIELDSTONE_STAIRS);
                for (int x = ax + 1; x < bx; x++) {
                    b.set(x, y, az, Arch.stair(st, Direction.SOUTH, Half.BOTTOM));
                    b.set(x, y, bz, Arch.stair(st, Direction.NORTH, Half.BOTTOM));
                }
                for (int z = az + 1; z < bz; z++) {
                    b.set(ax, y, z, Arch.stair(st, Direction.EAST, Half.BOTTOM));
                    b.set(bx, y, z, Arch.stair(st, Direction.WEST, Half.BOTTOM));
                }
                b.set(ax, y, az, Arch.stair(st, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
                b.set(bx, y, az, Arch.stair(st, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
                b.set(ax, y, bz, Arch.stair(st, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
                b.set(bx, y, bz, Arch.stair(st, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
            }
        }
    }

    /** Inset of course y (1..h) of a concave batter: quick at the foot, vertical at the top. */
    static int inset(int y, int h, int batter) {
        double t = (y - 1) / (double) Math.max(1, h - 1);
        return (int) Math.round(batter * (1 - (1 - t) * (1 - t)));
    }

    /** A dobei: plastered compound wall on a fieldstone footing with a kawara cap, along a straight local line. */
    static void dobei(Builder b, Mats m, int x1, int z1, int x2, int z2, int y0, int h) {
        boolean alongX = z1 == z2;
        BlockState cap = Arch.slab(m.roofSlab, SlabType.BOTTOM);
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                b.set(x, y0, z, k(ArchBlocks.FIELDSTONE));
                for (int y = y0 + 1; y < y0 + h; y++) b.set(x, y, z, m.wall);
                b.set(x, y0 + h, z, m.roof);
                b.set(x, y0 + h + 1, z, cap);
                int along = alongX ? x : z;
                if (Math.floorMod(along, 6) == 0) for (int y = y0 + 1; y < y0 + h; y++) b.set(x, y, z, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
            }
        // eaves either side of the cap
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                if (alongX) {
                    b.set(x, y0 + h, z - 1, Arch.stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
                    b.set(x, y0 + h, z + 1, Arch.stair(m.roofStairs, Direction.NORTH, Half.BOTTOM));
                } else {
                    b.set(x - 1, y0 + h, z, Arch.stair(m.roofStairs, Direction.EAST, Half.BOTTOM));
                    b.set(x + 1, y0 + h, z, Arch.stair(m.roofStairs, Direction.WEST, Half.BOTTOM));
                }
            }
    }

    /** Stone lantern (toro) standing on the surface at y (its post starts at y). */
    static void toro(Builder b, int x, int y, int z) {
        b.set(x, y, z, Arch.connect(s(Blocks.STONE_BRICK_WALL), false, false, false, false));
        b.set(x, y + 1, z, s(Blocks.CHISELED_STONE_BRICKS));
        b.set(x, y + 2, z, Arch.lantern(s(Blocks.LANTERN), false));
        b.set(x, y + 3, z, Arch.slab(s(Blocks.STONE_BRICK_SLAB), SlabType.BOTTOM));
    }

    /**
     * A torii across local x at row z: red pillars at px1 and px2 rising from y0, a red tie beam (nuki) and a black
     * upswept top lintel (kasagi) at y0+h, with a gold name plaque in the middle.
     */
    static void torii(Builder b, int px1, int px2, int z, int y0, int h) {
        torii(b, px1, px2, z, y0, h, true);
    }

    /** A torii spanning a1..a2 along local x (alongX) or z at the fixed other coordinate {@code f}. */
    static void torii(Builder b, int a1, int a2, int f, int y0, int h, boolean alongX) {
        BlockState red = s(Blocks.RED_CONCRETE), black = s(Blocks.POLISHED_BLACKSTONE);
        BlockState lo = Arch.stair(s(Blocks.POLISHED_BLACKSTONE_STAIRS), alongX ? Direction.WEST : Direction.NORTH, Half.BOTTOM);
        BlockState hi = Arch.stair(s(Blocks.POLISHED_BLACKSTONE_STAIRS), alongX ? Direction.EAST : Direction.SOUTH, Half.BOTTOM);
        java.util.function.BiConsumer<int[], BlockState> put = (p, st) -> {
            if (alongX) b.set(p[0], p[1], f, st);
            else b.set(f, p[1], p[0], st);
        };
        for (int a : new int[]{a1, a2}) {
            put.accept(new int[]{a, y0}, s(Blocks.POLISHED_BLACKSTONE));
            for (int y = y0 + 1; y <= y0 + h - 1; y++) put.accept(new int[]{a, y}, red);
        }
        for (int a = a1 - 1; a <= a2 + 1; a++) {
            put.accept(new int[]{a, y0 + h - 3}, red);
            put.accept(new int[]{a, y0 + h - 1}, red);
            put.accept(new int[]{a, y0 + h}, black);
        }
        put.accept(new int[]{a1 - 2, y0 + h}, lo);
        put.accept(new int[]{a2 + 2, y0 + h}, hi);
        put.accept(new int[]{(a1 + a2) / 2, y0 + h - 2}, s(Blocks.GOLD_BLOCK));
    }

    // ================================================================== generic

    /** Conical (or polygonal) spire roof of radius r starting at y over (cx, cz), rising {@code rise} per ring, with a finial. */
    static int spire(Builder b, int cx, int cz, int y, double r, double step, BlockState roof, BlockState finial) {
        int yy = y;
        for (double rr = r; rr > 0.4; rr -= step, yy++) LandmarkKit.disc(b, cx, cz, yy, rr, roof);
        b.set(cx, yy, cz, roof);
        if (finial != null) b.set(cx, yy + 1, cz, finial);
        return yy;
    }

    /** Alternating merlons of {@code block} on the rectangle outline [x1..x2]x[z1..z2] at height y. */
    static void merlons(Builder b, int x1, int z1, int x2, int z2, int y, BlockState block) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (!(x == x1 || x == x2 || z == z1 || z == z2)) continue;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                if (corner || ((x + z) & 1) == 0) b.set(x, y, z, block);
            }
    }

    /** A rectangle outline of a connected block (fence, bars, wall) with its connections set. */
    static void rail(Builder b, int x1, int y, int z1, int x2, int z2, BlockState block) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edgeX = x == x1 || x == x2, edgeZ = z == z1 || z == z2;
                if (!edgeX && !edgeZ) continue;
                boolean n = edgeX && z > z1, so = edgeX && z < z2, e = edgeZ && x < x2, w = edgeZ && x > x1;
                b.set(x, y, z, Arch.connect(block, n, e, so, w));
            }
    }

    /** A straight run of a connected block from (x1,z1) to (x2,z2) along one axis at height y. */
    static void run(Builder b, int x1, int y, int z1, int x2, int z2, BlockState block) {
        if (z1 == z2) {
            int a = Math.min(x1, x2), c = Math.max(x1, x2);
            for (int x = a; x <= c; x++) b.set(x, y, z1, Arch.connect(block, false, x < c, false, x > a));
        } else {
            int a = Math.min(z1, z2), c = Math.max(z1, z2);
            for (int z = a; z <= c; z++) b.set(x1, y, z, Arch.connect(block, z > a, false, z < c, false));
        }
    }

    /**
     * A straight flight of {@code n} stairs climbing toward {@code dir} from (x, y, z), {@code width} wide toward the
     * clockwise side, with headroom cleared above and solid {@code support} under each step down to {@code baseY}.
     */
    static void flight(Builder b, BlockState stairs, int x, int y, int z, Direction dir, int n, int width, BlockState support, int baseY) {
        Direction side = dir.getClockWise();
        for (int i = 0; i < n; i++)
            for (int w = 0; w < width; w++) {
                int px = x + dir.getStepX() * i + side.getStepX() * w, pz = z + dir.getStepZ() * i + side.getStepZ() * w;
                b.set(px, y + i, pz, Arch.stair(stairs, dir, Half.BOTTOM));
                b.clear(px, y + i + 1, pz, px, y + i + 3, pz);
                if (support != null) for (int yy = baseY; yy < y + i; yy++) b.set(px, yy, pz, support);
            }
    }

    /** Open door (both halves) so passages read as open gates. */
    static void openDoor(Builder b, int x, int y, int z, Block door, Direction facing, DoorHingeSide hinge) {
        BlockState lo = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.OPEN, true).setValue(DoorBlock.HINGE, hinge);
        b.set(x, y, z, lo);
        b.set(x, y + 1, z, lo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Hanging light under a ceiling block. */
    static void hang(Builder b, int x, int y, int z, BlockState light) {
        b.set(x, y, z, Arch.lantern(light, true));
    }
}
