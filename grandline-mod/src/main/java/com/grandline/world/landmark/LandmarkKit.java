package com.grandline.world.landmark;

import com.grandline.world.build.Builder;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/**
 * Shared construction helpers for the hand-designed landmark set pieces. All coordinates are local to the
 * landmark's {@link Builder}; every helper is deterministic (randomness only through {@link Builder#rand}).
 * Connected blocks (panes, bars, fences, walls) get their connection properties set explicitly because world
 * generation does not run neighbour shape updates.
 */
final class LandmarkKit {

    // ------------------------------------------------------------------ markers

    static CompoundTag role(String r) {
        CompoundTag t = new CompoundTag();
        t.putString("role", r);
        return t;
    }

    static CompoundTag tag(String k, String v) {
        CompoundTag t = new CompoundTag();
        t.putString(k, v);
        return t;
    }

    static void npc(Builder b, String role, int x, int y, int z) {
        b.marker("npc", x, y, z, role(role));
    }

    static void mark(Builder b, String kind, int x, int y, int z) {
        b.marker(kind, x, y, z, new CompoundTag());
    }

    // ------------------------------------------------------------------ block states

    static BlockState leaves(Block l) {
        BlockState s = l.defaultBlockState();
        return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true) : s;
    }

    static BlockState axis(Block b, Direction.Axis a) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, a) : s;
    }

    static BlockState stair(Block s, Direction facing, Half half) {
        BlockState st = s.defaultBlockState();
        if (!st.hasProperty(StairBlock.FACING)) return st;
        return st.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
    }

    static BlockState slabTop(Block s) {
        BlockState st = s.defaultBlockState();
        return st.hasProperty(SlabBlock.TYPE) ? st.setValue(SlabBlock.TYPE, SlabType.TOP) : st;
    }

    static BlockState facing(Block b, Direction f) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(HorizontalDirectionalBlock.FACING) && f.getAxis().isHorizontal()) return s.setValue(HorizontalDirectionalBlock.FACING, f);
        if (s.hasProperty(DirectionalBlock.FACING)) return s.setValue(DirectionalBlock.FACING, f);
        return s;
    }

    static BlockState candles(Block c, int n) {
        BlockState s = c.defaultBlockState();
        if (s.hasProperty(CandleBlock.CANDLES)) s = s.setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, n)));
        if (s.hasProperty(CandleBlock.LIT)) s = s.setValue(CandleBlock.LIT, true);
        return s;
    }

    static BlockState trapdoor(Block t, Direction f, Half half, boolean open) {
        BlockState s = t.defaultBlockState();
        if (!s.hasProperty(TrapDoorBlock.FACING)) return s;
        return s.setValue(TrapDoorBlock.FACING, f).setValue(TrapDoorBlock.HALF, half).setValue(TrapDoorBlock.OPEN, open);
    }

    static BlockState lever(Direction wallFacing) {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, wallFacing);
    }

    static BlockState button(Block btn, Direction wallFacing) {
        return btn.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, wallFacing);
    }

    static BlockState banner(Block wallBanner, Direction f) {
        BlockState s = wallBanner.defaultBlockState();
        return s.hasProperty(WallBannerBlock.FACING) ? s.setValue(WallBannerBlock.FACING, f) : s;
    }

    static BlockState lantern(Block l, boolean hanging) {
        BlockState s = l.defaultBlockState();
        return s.hasProperty(LanternBlock.HANGING) ? s.setValue(LanternBlock.HANGING, hanging) : s;
    }

    static BlockState bellCeiling() {
        return Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING);
    }

    /** A pane / bars / fence state connected along one horizontal axis. */
    static BlockState connected(Block b, Direction.Axis along) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(CrossCollisionBlock.EAST)) {
            boolean x = along == Direction.Axis.X;
            return s.setValue(CrossCollisionBlock.EAST, x).setValue(CrossCollisionBlock.WEST, x)
                    .setValue(CrossCollisionBlock.NORTH, !x).setValue(CrossCollisionBlock.SOUTH, !x);
        }
        if (s.hasProperty(WallBlock.EAST_WALL)) {
            boolean x = along == Direction.Axis.X;
            return s.setValue(WallBlock.UP, false)
                    .setValue(WallBlock.EAST_WALL, x ? WallSide.LOW : WallSide.NONE).setValue(WallBlock.WEST_WALL, x ? WallSide.LOW : WallSide.NONE)
                    .setValue(WallBlock.NORTH_WALL, x ? WallSide.NONE : WallSide.LOW).setValue(WallBlock.SOUTH_WALL, x ? WallSide.NONE : WallSide.LOW);
        }
        return s;
    }

    /** Explicit four-way connection state (n, e, s, w) for panes / bars / fences / walls. */
    static BlockState connect(Block b, boolean n, boolean e, boolean so, boolean w) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(CrossCollisionBlock.EAST)) {
            return s.setValue(CrossCollisionBlock.NORTH, n).setValue(CrossCollisionBlock.EAST, e).setValue(CrossCollisionBlock.SOUTH, so).setValue(CrossCollisionBlock.WEST, w);
        }
        if (s.hasProperty(WallBlock.EAST_WALL)) {
            boolean straight = (n && so && !e && !w) || (e && w && !n && !so);
            return s.setValue(WallBlock.UP, !straight)
                    .setValue(WallBlock.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE).setValue(WallBlock.EAST_WALL, e ? WallSide.LOW : WallSide.NONE)
                    .setValue(WallBlock.SOUTH_WALL, so ? WallSide.LOW : WallSide.NONE).setValue(WallBlock.WEST_WALL, w ? WallSide.LOW : WallSide.NONE);
        }
        return s;
    }

    // ------------------------------------------------------------------ sites

    /** Foundation under the whole footprint, a ground layer at y=0 and clear air from y=1 to clearH. */
    static void site(Builder b, Block ground, Block fill, int clearH) {
        b.foundation(0, 0, b.w - 1, b.d - 1, fill, 14);
        b.fill(0, 0, 0, b.w - 1, 0, b.d - 1, ground);
        b.clear(0, 1, 0, b.w - 1, clearH, b.d - 1);
    }

    /** Patchy ground: base block with a deterministic sprinkle of variants across the footprint at y. */
    static void patchGround(Builder b, int salt, int y, Block base, Block... variants) {
        for (int x = 0; x < b.w; x++)
            for (int z = 0; z < b.d; z++) {
                int r = b.rand(salt + x * 7919 + z * 104729, 10);
                b.set(x, y, z, r < variants.length ? variants[r] : base);
            }
    }

    // ------------------------------------------------------------------ rooms

    /** Room with a floor layer at y, walls from y+1 to y+h, an optional ceiling at y+h+1 and a cleared interior. */
    static void room(Builder b, int x1, int y, int z1, int x2, int z2, int h, Block wall, Block floor, Block ceiling) {
        b.fill(x1, y, z1, x2, y, z2, floor);
        b.walls(x1, y + 1, z1, x2, y + h, z2, wall);
        b.clear(x1 + 1, y + 1, z1 + 1, x2 - 1, y + h, z2 - 1);
        if (ceiling != null) b.fill(x1, y + h + 1, z1, x2, y + h + 1, z2, ceiling);
    }

    /** Solid shell box with a hollowed interior (walls, floor and roof all of {@code b}). */
    static void shell(Builder b, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        b.fill(x1, y1, z1, x2, y2, z2, block);
        b.clear(x1 + 1, y1 + 1, z1 + 1, x2 - 1, y2 - 1, z2 - 1);
    }

    /** Vertical corner/edge posts for a timber-framed look. */
    static void posts(Builder b, int x1, int y1, int z1, int x2, int y2, int z2, Block post) {
        b.fill(x1, y1, z1, x1, y2, z1, post);
        b.fill(x2, y1, z1, x2, y2, z1, post);
        b.fill(x1, y1, z2, x1, y2, z2, post);
        b.fill(x2, y1, z2, x2, y2, z2, post);
    }

    /** Two-tall windows along x on wall plane z, every {@code step} blocks, panes connected to the wall. */
    static void windowsX(Builder b, int x1, int x2, int y, int z, int step, Block pane) {
        for (int x = x1; x <= x2; x += step) {
            BlockState s = connected(pane, Direction.Axis.X);
            b.set(x, y, z, s);
            b.set(x, y + 1, z, s);
        }
    }

    static void windowsZ(Builder b, int z1, int z2, int y, int x, int step, Block pane) {
        for (int z = z1; z <= z2; z += step) {
            BlockState s = connected(pane, Direction.Axis.Z);
            b.set(x, y, z, s);
            b.set(x, y + 1, z, s);
        }
    }

    /** A straight run of a connected block (fence, wall, bars, pane) along x or z, ends joined to their neighbours. */
    static void line(Builder b, int x1, int y, int z1, int x2, int z2, Block block) {
        if (z1 == z2) {
            int a = Math.min(x1, x2), c = Math.max(x1, x2);
            for (int x = a; x <= c; x++) b.set(x, y, z1, connect(block, false, x < c, false, x > a));
        } else {
            int a = Math.min(z1, z2), c = Math.max(z1, z2);
            for (int z = a; z <= c; z++) b.set(x1, y, z, connect(block, z > a, false, z < c, false));
        }
    }

    /** Rectangle outline of a connected block with joined corners; gaps where {@code gapX}/{@code gapZ} ranges say. */
    static void outline(Builder b, int x1, int y, int z1, int x2, int z2, Block block) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edgeX = x == x1 || x == x2, edgeZ = z == z1 || z == z2;
                if (!edgeX && !edgeZ) continue;
                boolean n = edgeX && z > z1, s = edgeX && z < z2, e = edgeZ && x < x2, w = edgeZ && x > x1;
                b.set(x, y, z, connect(block, n, e, s, w));
            }
    }

    // ------------------------------------------------------------------ furniture

    /** Table surface: a top slab at y (reads as a table at waist height). */
    static void table(Builder b, int x, int y, int z, Block slab) {
        b.set(x, y, z, slabTop(slab));
    }

    /** Chair: a stair whose back faces {@code back}. */
    static void chair(Builder b, int x, int y, int z, Block stairs, Direction back) {
        b.set(x, y, z, stair(stairs, back, Half.BOTTOM));
    }

    /** Long table along x at row z with chairs on both sides (z-1 and z+1) and candles every few blocks. */
    static void longTableX(Builder b, int x1, int x2, int y, int z, Block slab, Block stairs, boolean candles) {
        for (int x = x1; x <= x2; x++) {
            table(b, x, y, z, slab);
            if ((x - x1) % 2 == 0) {
                chair(b, x, y, z - 1, stairs, Direction.NORTH);
                chair(b, x, y, z + 1, stairs, Direction.SOUTH);
            }
            if (candles && (x - x1) % 3 == 1) b.set(x, y + 1, z, candles(Blocks.WHITE_CANDLE, 3));
        }
    }

    /** Long table along z at column x with chairs at x-1 and x+1. */
    static void longTableZ(Builder b, int z1, int z2, int y, int x, Block slab, Block stairs, boolean candles) {
        for (int z = z1; z <= z2; z++) {
            table(b, x, y, z, slab);
            if ((z - z1) % 2 == 0) {
                chair(b, x - 1, y, z, stairs, Direction.WEST);
                chair(b, x + 1, y, z, stairs, Direction.EAST);
            }
            if (candles && (z - z1) % 3 == 1) b.set(x, y + 1, z, candles(Blocks.WHITE_CANDLE, 3));
        }
    }

    /** Hanging lantern (the block above must be solid). */
    static void lamp(Builder b, int x, int y, int z) {
        b.hanging(x, y, z, Blocks.LANTERN);
    }

    /** Lamp post: fence column with a lantern on top. */
    static void lampPost(Builder b, int x, int y, int z, Block fence, int h, Block light) {
        for (int i = 0; i < h; i++) b.set(x, y + i, z, fence);
        b.set(x, y + h, z, lantern(light, false));
    }

    static void carpet(Builder b, int x1, int y, int z1, int x2, int z2, Block carpet) {
        b.fill(x1, y, z1, x2, y, z2, carpet);
    }

    /** Bed with a nightstand (candle) and a chest at the foot. */
    static void bedroom(Builder b, int x, int y, int z, Block bed, Direction headTo, String loot) {
        b.bed(x, y, z, bed, headTo);
        Direction side = headTo.getClockWise();
        int hx = x + headTo.getStepX(), hz = z + headTo.getStepZ();
        b.set(hx + side.getStepX(), y, hz + side.getStepZ(), Blocks.SPRUCE_PLANKS);
        b.set(hx + side.getStepX(), y + 1, hz + side.getStepZ(), candles(Blocks.CANDLE, 1));
        if (loot != null) b.chest(x - headTo.getStepX(), y, z - headTo.getStepZ(), headTo.getOpposite(), loot);
    }

    /** A little kitchen corner along x: furnace, smoker, crafting, cauldron and a barrel. */
    static void kitchenX(Builder b, int x, int y, int z, Direction front, String loot) {
        b.set(x, y, z, facing(Blocks.SMOKER, front));
        b.set(x + 1, y, z, facing(Blocks.FURNACE, front));
        b.set(x + 2, y, z, Blocks.CRAFTING_TABLE);
        b.set(x + 3, y, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        if (loot != null) b.barrel(x + 4, y, z, loot);
    }

    // ------------------------------------------------------------------ circulation

    /**
     * Straight stair flight climbing toward {@code dir}: {@code n} steps from (x,y,z), {@code width} wide toward
     * dir's clockwise side, with three blocks of headroom cleared above every step and solid support below.
     */
    static void stairsUp(Builder b, int x, int y, int z, Direction dir, int n, Block stairs, int width, Block support) {
        Direction side = dir.getClockWise();
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < width; k++) {
                int px = x + dir.getStepX() * i + side.getStepX() * k, pz = z + dir.getStepZ() * i + side.getStepZ() * k;
                b.set(px, y + i, pz, stair(stairs, dir, Half.BOTTOM));
                b.clear(px, y + i + 1, pz, px, y + i + 3, pz);
                if (support != null && i > 0) b.fill(px, y, pz, px, y + i - 1, pz, support);
            }
        }
    }

    /** Ladder column with a backing column of {@code back} (so the ladder always has support). */
    static void ladder(Builder b, int x, int y1, int z, int h, Direction facing, Block back) {
        if (back != null) {
            int bx = x - facing.getStepX(), bz = z - facing.getStepZ();
            for (int y = y1; y < y1 + h; y++) {
                if (back != Blocks.AIR) b.set(bx, y, bz, back);
            }
        }
        b.ladderUp(x, y1, z, h, facing);
    }

    /** Hatch: a trapdoor in the floor at (x,y,z) with a ladder down to {@code bottomY}. */
    static void hatch(Builder b, int x, int y, int z, int bottomY, Direction ladderFacing, Block trapdoor, Block back) {
        ladder(b, x, bottomY, z, y - bottomY, ladderFacing, back);
        b.set(x, y, z, trapdoor(trapdoor, ladderFacing, Half.TOP, false));
    }

    // ------------------------------------------------------------------ roofs & shapes

    /** Hip roof: concentric rings of stairs stepping inward from the rectangle, capped with {@code cap}. */
    static void hipRoof(Builder b, int x1, int z1, int x2, int z2, int y, Block stairs, Block cap) {
        int i = 0;
        while (x1 + i <= x2 - i && z1 + i <= z2 - i) {
            int ax = x1 + i, bx = x2 - i, az = z1 + i, bz = z2 - i, yy = y + i;
            if (bx - ax < 2 || bz - az < 2) {
                b.fill(ax, yy, az, bx, yy, bz, cap);
                break;
            }
            for (int x = ax; x <= bx; x++) {
                b.set(x, yy, az, stair(stairs, Direction.SOUTH, Half.BOTTOM));
                b.set(x, yy, bz, stair(stairs, Direction.NORTH, Half.BOTTOM));
            }
            for (int z = az + 1; z < bz; z++) {
                b.set(ax, yy, z, stair(stairs, Direction.EAST, Half.BOTTOM));
                b.set(bx, yy, z, stair(stairs, Direction.WEST, Half.BOTTOM));
            }
            i++;
        }
    }

    /** Flared (East-Asian style) roof: an upturned slab eave ring outside the rectangle, then a hip roof. */
    static void flaredRoof(Builder b, int x1, int z1, int x2, int z2, int y, Block stairs, Block slab, Block cap) {
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            b.set(x, y, z1 - 1, slabTop(slab));
            b.set(x, y, z2 + 1, slabTop(slab));
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1 - 1, y, z, slabTop(slab));
            b.set(x2 + 1, y, z, slabTop(slab));
        }
        for (int[] c : new int[][]{{x1 - 1, z1 - 1}, {x2 + 1, z1 - 1}, {x1 - 1, z2 + 1}, {x2 + 1, z2 + 1}}) b.set(c[0], y + 1, c[1], slab);
        hipRoof(b, x1, z1, x2, z2, y + 1, stairs, cap);
    }

    static void disc(Builder b, double cx, double cz, int y, double r, BlockState s) {
        for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++)
            for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++) {
                double dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz <= r * r + 0.5) b.set(x, y, z, s);
            }
    }

    static void ring(Builder b, double cx, double cz, int y, double r, BlockState s) {
        for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r + 1); x++)
            for (int z = (int) Math.floor(cz - r - 1); z <= (int) Math.ceil(cz + r + 1); z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d <= r + 0.5 && d > r - 0.6) b.set(x, y, z, s);
            }
    }

    /** Ellipsoid (optionally hollow, shell thickness ~1) restricted to y offsets >= yMin. */
    static void ellipsoid(Builder b, int cx, int cy, int cz, double rx, double ry, double rz, BlockState s, boolean hollow, int yMin) {
        for (int x = (int) -Math.ceil(rx); x <= Math.ceil(rx); x++)
            for (int y = Math.max(yMin, (int) -Math.ceil(ry)); y <= Math.ceil(ry); y++)
                for (int z = (int) -Math.ceil(rz); z <= Math.ceil(rz); z++) {
                    double v = (x * x) / (rx * rx) + (y * y) / (ry * ry) + (z * z) / (rz * rz);
                    if (v > 1.0) continue;
                    if (hollow) {
                        double ix = Math.max(0.5, rx - 1.1), iy = Math.max(0.5, ry - 1.1), iz = Math.max(0.5, rz - 1.1);
                        double vi = (x * x) / (ix * ix) + (y * y) / (iy * iy) + (z * z) / (iz * iz);
                        if (vi < 1.0) continue;
                    }
                    b.set(cx + x, cy + y, cz + z, s);
                }
    }

    /** Interior of an ellipsoid set to air (for carving). */
    static void carveEllipsoid(Builder b, int cx, int cy, int cz, double rx, double ry, double rz, int yMin) {
        ellipsoid(b, cx, cy, cz, rx, ry, rz, Blocks.AIR.defaultBlockState(), false, yMin);
    }

    /** Cone / frustum from radius r0 at y1 to r1 at y1+h-1. */
    static void cone(Builder b, double cx, double cz, int y1, int h, double r0, double r1, BlockState s, boolean hollow) {
        for (int i = 0; i < h; i++) {
            double r = r0 + (r1 - r0) * (h <= 1 ? 0 : i / (double) (h - 1));
            if (hollow) ring(b, cx, cz, y1 + i, r, s);
            else disc(b, cx, cz, y1 + i, r, s);
        }
    }

    /** Wall slab [x1..x2]×[z1..z2] from y0 to yTop with a round-headed arch opening of the given width centred on x. */
    static void archX(Builder b, int cx, int halfW, int y0, int z1, int z2, int springH) {
        for (int x = cx - halfW; x <= cx + halfW; x++) {
            int dx = x - cx;
            int top = y0 + springH + (int) Math.floor(Math.sqrt(Math.max(0, (halfW + 0.5) * (halfW + 0.5) - dx * dx)));
            b.clear(x, y0, z1, x, top, z2);
        }
    }

    static void archZ(Builder b, int cz, int halfW, int y0, int x1, int x2, int springH) {
        for (int z = cz - halfW; z <= cz + halfW; z++) {
            int dz = z - cz;
            int top = y0 + springH + (int) Math.floor(Math.sqrt(Math.max(0, (halfW + 0.5) * (halfW + 0.5) - dz * dz)));
            b.clear(x1, y0, z, x2, top, z);
        }
    }

    /** Crenellations: alternating merlons on top of a wall run (y = level of the merlons). */
    static void crenelX(Builder b, int x1, int x2, int y, int z, Block block) {
        for (int x = x1; x <= x2; x++) if (((x - x1) & 1) == 0) b.set(x, y, z, block);
    }

    static void crenelZ(Builder b, int z1, int z2, int y, int x, Block block) {
        for (int z = z1; z <= z2; z++) if (((z - z1) & 1) == 0) b.set(x, y, z, block);
    }

    // ------------------------------------------------------------------ nature

    /** Round-canopy tree with persistent leaves; {@code fruit} (nullable) is sprinkled into the canopy. */
    static void tree(Builder b, int x, int y, int z, Block log, Block leaf, int h, int r, int salt, Block fruit) {
        BlockState lv = leaves(leaf);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = -r; dy <= r; dy++) {
                    double dd = dx * dx + dz * dz + dy * dy * 1.6;
                    if (dd > r * r + 0.8) continue;
                    if (dd > (r - 1) * (r - 1) && b.rand(salt + dx * 31 + dz * 17 + dy * 7, 5) == 0) continue;
                    int yy = y + h + dy;
                    if (yy <= y) continue;
                    BlockState s = (fruit != null && b.rand(salt + dx * 13 + dz * 11 + dy * 3, 9) == 0 && dd > 2) ? fruit.defaultBlockState() : lv;
                    b.set(x + dx, yy, z + dz, s);
                }
        b.fill(x, y, z, x, y + h, z, log);
    }

    /** Palm: leaning trunk with drooping fronds. */
    static void palm(Builder b, int x, int y, int z, int h, int salt) {
        int lean = b.rand(salt, 4);
        Direction d = Direction.from2DDataValue(lean);
        int tx = x, tz = z;
        for (int i = 0; i < h; i++) {
            if (i > h / 2 && i % 3 == 0) {
                tx += d.getStepX();
                tz += d.getStepZ();
            }
            b.set(tx, y + i, tz, Blocks.JUNGLE_LOG);
        }
        int top = y + h;
        BlockState lv = leaves(Blocks.JUNGLE_LEAVES);
        b.set(tx, top, tz, lv);
        for (Direction f : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 3; k++) b.set(tx + f.getStepX() * k, top - (k == 3 ? 1 : 0), tz + f.getStepZ() * k, lv);
            Direction s = f.getClockWise();
            b.set(tx + f.getStepX() + s.getStepX(), top, tz + f.getStepZ() + s.getStepZ(), lv);
        }
        b.set(tx + d.getStepX(), top - 1, tz + d.getStepZ(), Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, d.getOpposite()).setValue(CocoaBlock.AGE, 2));
    }

    /** Bare, twisted dead tree. */
    static void deadTree(Builder b, int x, int y, int z, int h, int salt, Block log) {
        b.fill(x, y, z, x, y + h, z, log);
        for (int i = 0; i < 3; i++) {
            Direction d = Direction.from2DDataValue(b.rand(salt + i * 11, 4));
            int by = y + h / 2 + b.rand(salt + i * 5, Math.max(1, h / 2));
            for (int k = 1; k <= 2 + b.rand(salt + i, 2); k++) b.set(x + d.getStepX() * k, by + k / 2, z + d.getStepZ() * k, axis(log, d.getAxis()));
        }
    }

    /** Deterministic scatter of a block on a layer (only into air at y, above solid ground is the caller's job). */
    static void scatter(Builder b, int salt, int n, int x1, int z1, int x2, int z2, int y, BlockState s) {
        for (int i = 0; i < n; i++) {
            int x = x1 + b.rand(salt + i * 2, x2 - x1 + 1), z = z1 + b.rand(salt + i * 2 + 1, z2 - z1 + 1);
            b.set(x, y, z, s);
        }
    }

    /** Lumpy heap (junk, rubble, sand) of random blocks from {@code mix}, radius r, height h. */
    static void heap(Builder b, int salt, int cx, int cz, int y, int r, int h, Block... mix) {
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z) / (r + 0.5);
                if (d > 1) continue;
                int top = (int) Math.round(h * (1 - d * d)) + b.rand(salt + x * 7 + z * 13, 2) - 1;
                for (int yy = 0; yy <= top; yy++) b.set(cx + x, y + yy, cz + z, mix[b.rand(salt + x * 31 + z * 37 + yy * 41, mix.length)]);
            }
    }

    // ------------------------------------------------------------------ composition

    /**
     * A child builder for a sub-footprint whose local (0,0) sits at this builder's local (x0, z0), floor at y0, of
     * size w×d and turned by {@code localRot} relative to this builder. Lets landmarks reuse settlement buildings.
     */
    static Builder sub(Builder b, int x0, int y0, int z0, int w, int d, Rotation localRot) {
        boolean swap = localRot == Rotation.CLOCKWISE_90 || localRot == Rotation.COUNTERCLOCKWISE_90;
        int sx = swap ? d : w, sz = swap ? w : d;
        int ax = b.wx(x0, z0), az = b.wz(x0, z0), cx = b.wx(x0 + sx - 1, z0 + sz - 1), cz = b.wz(x0 + sx - 1, z0 + sz - 1);
        return new Builder(b.sink, Math.min(ax, cx), b.oy + y0, Math.min(az, cz), w, d, b.rot.getRotated(localRot),
                b.seed * 31 + x0 * 7919L + z0 * 104729L + y0);
    }

    private LandmarkKit() {}
}
