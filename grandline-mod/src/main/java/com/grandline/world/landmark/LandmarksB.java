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
 * Construction helpers shared by the Blues and early-Paradise set pieces (BluesLandmarks, ParadiseLandmarks): block
 * state shortcuts for the architectural kit, wall rings, cornices, round towers with onion domes, desert houses with
 * flat parapet roofs, awning rows and furniture runs. Everything is written in the builder's local frame and is
 * deterministic (randomness only through {@link Builder#rand}).
 */
final class LandmarksB {
    private LandmarksB() {}

    // ================================================================== block states

    static BlockState s(DeferredBlock<Block> d) {
        return d.get().defaultBlockState();
    }

    static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    /** Upright pillar (beams, columns, logs). */
    static BlockState up(DeferredBlock<Block> d) {
        return Arch.axis(s(d), Direction.Axis.Y);
    }

    static BlockState up(Block d) {
        return Arch.axis(s(d), Direction.Axis.Y);
    }

    static BlockState beam(DeferredBlock<Block> d, Direction.Axis a) {
        return Arch.axis(s(d), a);
    }

    static BlockState beam(Block d, Direction.Axis a) {
        return Arch.axis(s(d), a);
    }

    static BlockState st(DeferredBlock<Block> d, Direction f, Half h) {
        return Arch.stair(s(d), f, h);
    }

    static BlockState st(Block d, Direction f, Half h) {
        return Arch.stair(s(d), f, h);
    }

    static BlockState top(DeferredBlock<Block> d) {
        return Arch.slab(s(d), SlabType.TOP);
    }

    static BlockState top(Block d) {
        return Arch.slab(s(d), SlabType.TOP);
    }

    static BlockState bot(DeferredBlock<Block> d) {
        return Arch.slab(s(d), SlabType.BOTTOM);
    }

    static BlockState bot(Block d) {
        return Arch.slab(s(d), SlabType.BOTTOM);
    }

    static BlockState decor(DeferredBlock<Block> d, Direction f) {
        return Arch.decor(d, f);
    }

    static BlockState lantern(DeferredBlock<Block> d, boolean hanging) {
        return Arch.lantern(s(d), hanging);
    }

    static BlockState chainY() {
        return Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
    }

    static BlockState campfire(boolean lit) {
        return Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, lit).setValue(CampfireBlock.SIGNAL_FIRE, false);
    }

    static BlockState gate(Block g, Direction f, boolean open) {
        return g.defaultBlockState().setValue(FenceGateBlock.FACING, f).setValue(FenceGateBlock.OPEN, open);
    }

    /** A material set for a style, with the overrides a set piece wants applied by the caller. */
    static Mats mats(BuildStyle st, long seed) {
        return Mats.of(st, seed);
    }

    /** Desert materials: cream (or the given) plaster on dressed sandstone, lattice windows, sandstone sills. */
    static Mats desert(long seed, BlockState wall) {
        Mats m = Mats.of(BuildStyle.DESERT, seed);
        if (wall != null) {
            m.wall = wall;
            m.upperWall = wall;
        }
        m.planters = false;
        m.shutter = null;
        return m;
    }

    // ================================================================== walls, bands, cornices

    /** Four walls of a box (no floor or ceiling). */
    static void box(Builder b, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        b.fill(x1, y1, z1, x2, y2, z1, s);
        b.fill(x1, y1, z2, x2, y2, z2, s);
        b.fill(x1, y1, z1, x1, y2, z2, s);
        b.fill(x2, y1, z1, x2, y2, z2, s);
    }

    /** A one-block ring at height y round the rectangle. */
    static void ring(Builder b, int x1, int z1, int x2, int z2, int y, BlockState s) {
        box(b, x1, y, z1, x2, y, z2, s);
    }

    /** Ring of beams: x-axis beams on the long north/south runs, z-axis beams on the east/west runs. */
    static void beamRing(Builder b, int x1, int z1, int x2, int z2, int y, DeferredBlock<Block> beam) {
        b.fill(x1, y, z1, x2, y, z1, beam(beam, Direction.Axis.X));
        b.fill(x1, y, z2, x2, y, z2, beam(beam, Direction.Axis.X));
        b.fill(x1, y, z1 + 1, x1, y, z2 - 1, beam(beam, Direction.Axis.Z));
        b.fill(x2, y, z1 + 1, x2, y, z2 - 1, beam(beam, Direction.Axis.Z));
    }

    /** Upside-down stairs one block outside the rectangle at height y, backs against the wall: a moulded cornice. */
    static void cornice(Builder b, int x1, int z1, int x2, int z2, int y, BlockState stairs, BlockState corner) {
        for (int x = x1; x <= x2; x++) {
            b.set(x, y, z1 - 1, Arch.stair(stairs, Direction.SOUTH, Half.TOP));
            b.set(x, y, z2 + 1, Arch.stair(stairs, Direction.NORTH, Half.TOP));
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1 - 1, y, z, Arch.stair(stairs, Direction.EAST, Half.TOP));
            b.set(x2 + 1, y, z, Arch.stair(stairs, Direction.WEST, Half.TOP));
        }
        if (corner != null) {
            b.set(x1 - 1, y, z1 - 1, corner);
            b.set(x2 + 1, y, z1 - 1, corner);
            b.set(x1 - 1, y, z2 + 1, corner);
            b.set(x2 + 1, y, z2 + 1, corner);
        }
    }

    /** Plinth course: stairs (right way up) hugging the base of the walls, one block outside, at y. */
    static void plinth(Builder b, int x1, int z1, int x2, int z2, int y, BlockState stairs) {
        for (int x = x1; x <= x2; x++) {
            b.set(x, y, z1 - 1, Arch.stair(stairs, Direction.SOUTH, Half.BOTTOM));
            b.set(x, y, z2 + 1, Arch.stair(stairs, Direction.NORTH, Half.BOTTOM));
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1 - 1, y, z, Arch.stair(stairs, Direction.EAST, Half.BOTTOM));
            b.set(x2 + 1, y, z, Arch.stair(stairs, Direction.WEST, Half.BOTTOM));
        }
    }

    /** Parapet of a connected wall block round a rectangle at y with joined corners (skips cells already non-air? no: writes all). */
    static void balustrade(Builder b, int x1, int z1, int x2, int z2, int y, Block wall) {
        LandmarkKit.outline(b, x1, y, z1, x2, z2, wall);
    }

    // ================================================================== round forms

    static void cyl(Builder b, double cx, double cz, int y1, int y2, double r, BlockState s, boolean hollow) {
        for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r + 1); x++)
            for (int z = (int) Math.floor(cz - r - 1); z <= (int) Math.ceil(cz + r + 1); z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > r + 0.5) continue;
                if (hollow && d < r - 0.5) continue;
                b.fill(x, y1, z, x, y2, z, s);
            }
    }

    static void clearCyl(Builder b, double cx, double cz, int y1, int y2, double r) {
        cyl(b, cx, cz, y1, y2, r, Blocks.AIR.defaultBlockState(), false);
    }

    /**
     * Round tower: a shaft of radius r (wall {@code wall}) from y1 to y2 with its interior cleared, bands of
     * {@code band} at the given heights, floors of {@code floor} at the given heights inside, a cornice ring at the top
     * and an onion (or round) dome of radius domeR with a finial. Lattice windows at the four cardinal points at
     * {@code winY}.
     */
    static void roundTower(Builder b, int cx, int cz, int y1, int y2, int r, BlockState wall, BlockState band, int[] bands,
                           BlockState floor, int[] floors, BlockState pane, int[] winY, BlockState dome, BlockState tip, int domeR, boolean onion) {
        cyl(b, cx, cz, y1, y2, r, wall, true);
        clearCyl(b, cx, cz, y1, y2 + 1, r - 1);
        if (band != null) for (int y : bands) cyl(b, cx, cz, y, y, r, band, true);
        if (floor != null) for (int y : floors) cyl(b, cx, cz, y, y, r - 1, floor, false);
        if (pane != null)
            for (int y : winY) {
                b.set(cx + r, y, cz, Arch.along(pane, Direction.Axis.Z));
                b.set(cx - r, y, cz, Arch.along(pane, Direction.Axis.Z));
                b.set(cx, y, cz + r, Arch.along(pane, Direction.Axis.X));
                b.set(cx, y, cz - r, Arch.along(pane, Direction.Axis.X));
            }
        cyl(b, cx, cz, y2, y2, r + 1, band != null ? band : wall, true);
        if (dome != null) Arch.dome(b, cx, y2 + 1, cz, domeR, dome, tip, onion);
    }

    /** A connected rail (wall or fence) on the ring of cells at distance r around (cx, cz) at height y. */
    static void ringRail(Builder b, int cx, int cz, int y, double r, Block rail) {
        int R = (int) Math.ceil(r) + 1;
        for (int x = cx - R; x <= cx + R; x++)
            for (int z = cz - R; z <= cz + R; z++) {
                if (!onRing(x - cx, z - cz, r)) continue;
                b.set(x, y, z, LandmarkKit.connect(rail, onRing(x - cx, z - 1 - cz, r), onRing(x + 1 - cx, z - cz, r), onRing(x - cx, z + 1 - cz, r), onRing(x - 1 - cx, z - cz, r)));
            }
    }

    private static boolean onRing(int dx, int dz, double r) {
        double d = Math.sqrt(dx * dx + dz * dz);
        return d > r - 0.5 && d <= r + 0.5;
    }

    // ================================================================== openings

    /**
     * Pointed arch head over an opening x1..x2 in the wall plane z, springing at y: a stepped outline of upside-down
     * stairs meeting at a keystone; the cells inside the outline are set to {@code inside} (air when null, for an
     * opening; a panel block for a blind arch). Returns the keystone height.
     */
    static int pointedArchX(Builder b, int x1, int x2, int y, int z, BlockState stairs, BlockState key, BlockState inside) {
        BlockState in = inside != null ? inside : Blocks.AIR.defaultBlockState();
        int half = (x2 - x1) / 2;
        for (int i = 0; i < half; i++) {
            b.set(x1 + i, y + i, z, Arch.stair(stairs, Direction.EAST, Half.TOP));
            b.set(x2 - i, y + i, z, Arch.stair(stairs, Direction.WEST, Half.TOP));
            for (int x = x1 + i + 1; x <= x2 - i - 1; x++) b.set(x, y + i, z, in);
        }
        b.set(x1 + half, y + half, z, key);
        if ((x2 - x1) % 2 != 0) b.set(x1 + half + 1, y + half, z, key);
        return y + half;
    }

    static int pointedArchZ(Builder b, int z1, int z2, int y, int x, BlockState stairs, BlockState key, BlockState inside) {
        BlockState in = inside != null ? inside : Blocks.AIR.defaultBlockState();
        int half = (z2 - z1) / 2;
        for (int i = 0; i < half; i++) {
            b.set(x, y + i, z1 + i, Arch.stair(stairs, Direction.SOUTH, Half.TOP));
            b.set(x, y + i, z2 - i, Arch.stair(stairs, Direction.NORTH, Half.TOP));
            for (int z = z1 + i + 1; z <= z2 - i - 1; z++) b.set(x, y + i, z, in);
        }
        b.set(x, y + half, z1 + half, key);
        if ((z2 - z1) % 2 != 0) b.set(x, y + half, z1 + half + 1, key);
        return y + half;
    }

    /** A row of awning blocks along x at height y in front of a wall plane (awnings stand at z, facing {@code out}). */
    static void awningsX(Builder b, int x1, int x2, int y, int z, DeferredBlock<Block> aw, Direction out) {
        for (int x = x1; x <= x2; x++) b.set(x, y, z, decor(aw, out));
    }

    static void awningsZ(Builder b, int z1, int z2, int y, int x, DeferredBlock<Block> aw, Direction out) {
        for (int z = z1; z <= z2; z++) b.set(x, y, z, decor(aw, out));
    }

    /** Windows every {@code step} along a wall plane parallel to x (skipping the listed x's), using Arch.window. */
    static void windowRowX(Builder b, Mats m, int x1, int x2, int step, int y, int z, Direction out, int h, boolean shutters, int... skip) {
        outer:
        for (int x = x1; x <= x2; x += step) {
            for (int sx : skip) if (Math.abs(sx - x) <= 0) continue outer;
            Arch.window(b, m, x, y, z, out, h, shutters);
        }
    }

    static void windowRowZ(Builder b, Mats m, int z1, int z2, int step, int y, int x, Direction out, int h, boolean shutters, int... skip) {
        outer:
        for (int z = z1; z <= z2; z += step) {
            for (int sz : skip) if (sz == z) continue outer;
            Arch.window(b, m, x, y, z, out, h, shutters);
        }
    }

    // ================================================================== stairs

    /** A stair flight along +z or -z ({@code dir}) n steps from (x1..x2, y, z), headroom cleared, solid {@code fill} beneath. */
    static void flightX(Builder b, int x1, int x2, int y, int z, Direction dir, int n, BlockState stairs, BlockState fill) {
        for (int i = 0; i < n; i++) {
            int zz = z + dir.getStepZ() * i, xx = dir.getStepX() * i;
            for (int x = x1; x <= x2; x++) {
                b.set(x + xx, y + i, zz, Arch.stair(stairs, dir, Half.BOTTOM));
                b.clear(x + xx, y + i + 1, zz, x + xx, y + i + 3, zz);
                if (fill != null && i > 0) b.fill(x + xx, y, zz, x + xx, y + i - 1, zz, fill);
            }
        }
    }

    /** A stair flight along +x or -x, (x, y, z1..z2) start, n steps. */
    static void flightZ(Builder b, int z1, int z2, int y, int x, Direction dir, int n, BlockState stairs, BlockState fill) {
        for (int i = 0; i < n; i++) {
            int xx = x + dir.getStepX() * i;
            for (int z = z1; z <= z2; z++) {
                b.set(xx, y + i, z, Arch.stair(stairs, dir, Half.BOTTOM));
                b.clear(xx, y + i + 1, z, xx, y + i + 3, z);
                if (fill != null && i > 0) b.fill(xx, y, z, xx, y + i - 1, z, fill);
            }
        }
    }

    // ================================================================== desert building

    /**
     * A desert building on [x1..x2]x[z1..z2] whose ground floor stands at y0 (floor block at y0): plaster walls of
     * {@code floors} storeys of {@code h} (h-1 clear + floor), a dressed-sandstone plinth course and corner quoins,
     * a string course at each floor line, a flat roof with a (crenellated) parapet and cornice. Upper floors are laid
     * inside. Returns the roof deck height.
     */
    static int desertBlock(Builder b, Mats m, int x1, int z1, int x2, int z2, int y0, int floors, int h, boolean crenels) {
        BlockState dressed = s(ArchBlocks.DRESSED_SANDSTONE);
        int roofY = y0 + floors * h;
        b.fill(x1, y0, z1, x2, y0, z2, dressed);
        b.fill(x1 + 1, y0, z1 + 1, x2 - 1, y0, z2 - 1, m.floor);
        box(b, x1, y0 + 1, z1, x2, roofY - 1, z2, m.wall);
        b.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, roofY - 1, z2 - 1);
        ring(b, x1, z1, x2, z2, y0 + 1, dressed);
        for (int f = 1; f < floors; f++) {
            int fy = y0 + f * h;
            ring(b, x1, z1, x2, z2, fy, dressed);
            b.fill(x1 + 1, fy, z1 + 1, x2 - 1, fy, z2 - 1, m.floor);
        }
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(c[0], y0 + 1, c[1], c[0], roofY - 1, c[1], dressed);
        BlockState wall = m.wall;
        m.wall = dressed;
        Arch.flat(b, m, x1, z1, x2, z2, roofY, crenels, true);
        m.wall = wall;
        return roofY;
    }

    /** Door with a pointed sandstone arch head in a north/south wall plane; doorway 1 wide, 3 tall. */
    static void desertDoor(Builder b, Mats m, int x, int y, int z, Direction out, boolean awning) {
        b.bigDoor(x, y, z, m.door.getBlock(), out);
        b.set(x - 1, y + 3, z, Arch.stair(s(ArchBlocks.DRESSED_SANDSTONE_STAIRS), Direction.EAST, Half.TOP));
        b.set(x + 1, y + 3, z, Arch.stair(s(ArchBlocks.DRESSED_SANDSTONE_STAIRS), Direction.WEST, Half.TOP));
        b.set(x, y + 3, z, s(ArchBlocks.DRESSED_SANDSTONE));
        if (awning && m.awning != null) {
            int oz = z + out.getStepZ();
            for (int k = -1; k <= 1; k++) b.set(x + k, y + 3, oz, Arch.facing(m.awning.defaultBlockState(), out));
        }
    }

    // ================================================================== props

    static void counterRow(Builder b, int x1, int x2, int y, int z, Direction face) {
        for (int x = x1; x <= x2; x++) b.set(x, y, z, decor(ArchBlocks.SHOP_COUNTER, face));
    }

    static void shelfRow(Builder b, int x1, int x2, int y, int z, Direction face, int h) {
        for (int x = x1; x <= x2; x++) for (int k = 0; k < h; k++) b.set(x, y + k, z, decor(ArchBlocks.GOODS_SHELF, face));
    }

    static void bench(Builder b, int x, int y, int z, Direction f) {
        b.set(x, y, z, decor(ArchBlocks.PLANK_BENCH, f));
    }

    static void tableSet(Builder b, int x, int y, int z, BlockState chair) {
        Arch.tableSet(b, x, y, z, chair);
    }

    /** A hanging lantern on a short chain from the ceiling at yCeil-1 (needs a solid block at yCeil). */
    static void pendant(Builder b, int x, int yCeil, int z, BlockState light, int drop) {
        for (int k = 1; k < drop; k++) b.set(x, yCeil - k, z, chainY());
        b.set(x, yCeil - drop, z, Arch.lantern(light, true));
    }

    /** Lamp post: a wall-block pillar with a lantern on top. */
    static void post(Builder b, int x, int y, int z, BlockState pillar, int h, BlockState light) {
        for (int i = 0; i < h; i++) b.set(x, y + i, z, pillar);
        b.set(x, y + h, z, Arch.lantern(light, false));
    }

    /** Round-canopy fruit tree on a short trunk (mikan, orchard trees). */
    static void fruitTree(Builder b, int x, int y, int z, int salt, BlockState fruit) {
        BlockState lv = LandmarkKit.leaves(Blocks.OAK_LEAVES);
        BlockState log = up(Blocks.OAK_LOG);
        b.fill(x, y, z, x, y + 1, z, log);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 2; dy <= 4; dy++) {
                    int d2 = dx * dx + dz * dz + (dy - 3) * (dy - 3) * 2;
                    if (d2 > 6) continue;
                    if (d2 >= 5 && b.rand(salt + dx * 31 + dz * 17 + dy * 7, 3) == 0) continue;
                    BlockState v = (fruit != null && d2 >= 3 && b.rand(salt + dx * 13 + dz * 11 + dy * 5, 4) == 0) ? fruit : lv;
                    b.set(x + dx, y + dy, z + dz, v);
                }
        b.set(x, y + 2, z, log);
    }
}
