package com.grandline.world.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/**
 * Small construction DSL. Coordinates are local to a footprint of size w×d whose origin (0,0,0) maps to
 * world (ox, oy, oz) and which is rotated by {@code rot} about the footprint. Block states with facings are
 * rotated automatically, so a building written facing NORTH (door on local z=0) works in any orientation.
 */
public final class Builder {
    public final BlockSink sink;
    public final int ox, oy, oz, w, d;
    public final Rotation rot;
    public final long seed;

    public Builder(BlockSink sink, int ox, int oy, int oz, int w, int d, Rotation rot, long seed) {
        this.sink = sink;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.w = w;
        this.d = d;
        this.rot = rot;
        this.seed = seed;
    }

    public int wx(int lx, int lz) {
        return switch (rot) {
            case NONE -> ox + lx;
            case CLOCKWISE_90 -> ox + (d - 1 - lz);
            case CLOCKWISE_180 -> ox + (w - 1 - lx);
            case COUNTERCLOCKWISE_90 -> ox + lz;
        };
    }

    public int wz(int lx, int lz) {
        return switch (rot) {
            case NONE -> oz + lz;
            case CLOCKWISE_90 -> oz + lx;
            case CLOCKWISE_180 -> oz + (d - 1 - lz);
            case COUNTERCLOCKWISE_90 -> oz + (w - 1 - lx);
        };
    }

    public BlockPos world(int lx, int ly, int lz) {
        return new BlockPos(wx(lx, lz), oy + ly, wz(lx, lz));
    }

    /** World-space footprint width along x. */
    public int worldSizeX() {
        return (rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90) ? d : w;
    }

    public int worldSizeZ() {
        return (rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90) ? w : d;
    }

    public boolean touches() {
        return sink.accepts(ox - 1, oz - 1, ox + worldSizeX(), oz + worldSizeZ());
    }

    // ------------------------------------------------------------------ primitives

    public void set(int lx, int ly, int lz, BlockState s) {
        sink.set(wx(lx, lz), oy + ly, wz(lx, lz), s.rotate(rot));
    }

    public void set(int lx, int ly, int lz, Block b) {
        set(lx, ly, lz, b.defaultBlockState());
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2);
        int ay = Math.min(y1, y2), by = Math.max(y1, y2);
        int az = Math.min(z1, z2), bz = Math.max(z1, z2);
        BlockState r = s.rotate(rot);
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++) {
                int X = wx(x, z), Z = wz(x, z);
                for (int y = ay; y <= by; y++) sink.set(X, oy + y, Z, r);
            }
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        fill(x1, y1, z1, x2, y2, z2, b.defaultBlockState());
    }

    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Blocks.AIR.defaultBlockState());
    }

    /** Four walls (no floor/ceiling) between corners. */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        fill(x1, y1, z1, x2, y2, z1, b);
        fill(x1, y1, z2, x2, y2, z2, b);
        fill(x1, y1, z1, x1, y2, z2, b);
        fill(x2, y1, z1, x2, y2, z2, b);
    }

    /** Fill downward from y=-1 until solid ground (max depth) so buildings never float. */
    public void foundation(int x1, int z1, int x2, int z2, Block b, int maxDepth) {
        BlockState s = b.defaultBlockState();
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                int X = wx(x, z), Z = wz(x, z);
                for (int y = -1; y >= -maxDepth; y--) {
                    BlockState cur = sink.get(X, oy + y, Z);
                    if (cur != null && !cur.isAir() && !cur.liquid() && !cur.canBeReplaced()) break;
                    sink.set(X, oy + y, Z, s);
                }
            }
    }

    public void stairs(int lx, int ly, int lz, Block stairs, Direction localFacing, Half half) {
        if (!(stairs instanceof StairBlock)) {
            set(lx, ly, lz, stairs);
            return;
        }
        set(lx, ly, lz, stairs.defaultBlockState().setValue(StairBlock.FACING, localFacing).setValue(StairBlock.HALF, half));
    }

    public void slab(int lx, int ly, int lz, Block slab, SlabType type) {
        if (!(slab instanceof SlabBlock)) {
            set(lx, ly, lz, slab);
            return;
        }
        set(lx, ly, lz, slab.defaultBlockState().setValue(SlabBlock.TYPE, type));
    }

    /** Two-tall door with its outside toward {@code localFacing}. */
    public void door(int lx, int ly, int lz, Block door, Direction localFacing) {
        if (!(door instanceof DoorBlock)) {
            clear(lx, ly, lz, lx, ly + 1, lz);
            return;
        }
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, localFacing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        set(lx, ly, lz, lower);
        set(lx, ly + 1, lz, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A 3-block-tall doorway (agreed: buildings use 3-block doors so large races fit): 2-tall door + open transom. */
    public void bigDoor(int lx, int ly, int lz, Block door, Direction localFacing) {
        door(lx, ly, lz, door, localFacing);
        set(lx, ly + 2, lz, Blocks.AIR);
    }

    public void hanging(int lx, int ly, int lz, Block light) {
        BlockState s = light.defaultBlockState();
        if (s.hasProperty(LanternBlock.HANGING)) s = s.setValue(LanternBlock.HANGING, true);
        set(lx, ly, lz, s);
    }

    public void torch(int lx, int ly, int lz, Direction localFacing) {
        set(lx, ly, lz, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, localFacing));
    }

    public void bed(int lx, int ly, int lz, Block bed, Direction localFacing) {
        if (!(bed instanceof BedBlock)) return;
        BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, localFacing).setValue(BedBlock.PART, BedPart.FOOT);
        set(lx, ly, lz, foot);
        int hx = lx + localFacing.getStepX(), hz = lz + localFacing.getStepZ();
        set(hx, ly, hz, foot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    public void chest(int lx, int ly, int lz, Direction localFacing, String lootTable) {
        BlockState s = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, localFacing).rotate(rot);
        sink.lootChest(wx(lx, lz), oy + ly, wz(lx, lz), s, lootTable);
    }

    public void barrel(int lx, int ly, int lz, String lootTable) {
        BlockState s = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
        sink.lootChest(wx(lx, lz), oy + ly, wz(lx, lz), s, lootTable);
    }

    public void marker(String kind, int lx, int ly, int lz, CompoundTag data) {
        sink.marker(kind, world(lx, ly, lz), data);
    }

    public void marker(String kind, int lx, int ly, int lz) {
        marker(kind, lx, ly, lz, new CompoundTag());
    }

    /** Gable roof covering local rectangle [x1..x2]×[z1..z2] starting at height y, ridge along local x. */
    public void gableRoof(int x1, int z1, int x2, int z2, int y, Block stairs, Block slab, Block gableFill) {
        int depth = z2 - z1 + 1;
        int half = depth / 2;
        for (int i = 0; i < half; i++) {
            for (int x = x1; x <= x2; x++) {
                stairs(x, y + i, z1 + i, stairs, Direction.SOUTH, Half.BOTTOM);
                stairs(x, y + i, z2 - i, stairs, Direction.NORTH, Half.BOTTOM);
            }
            // gable ends
            for (int z = z1 + i + 1; z <= z2 - i - 1; z++) {
                set(x1 + 1, y + i, z, gableFill);
                set(x2 - 1, y + i, z, gableFill);
            }
        }
        if (depth % 2 == 1) {
            for (int x = x1; x <= x2; x++) slab(x, y + half, z1 + half, slab, SlabType.BOTTOM);
        }
    }

    /** Hip-ish flat roof with parapet. */
    public void flatRoof(int x1, int z1, int x2, int z2, int y, Block floor, Block parapet) {
        fill(x1, y, z1, x2, y, z2, floor);
        walls(x1, y + 1, z1, x2, y + 1, z2, parapet);
    }

    public void cylinder(int cx, int cz, int y1, int y2, int r, Block b, boolean hollow) {
        BlockState s = b.defaultBlockState();
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double dd = Math.sqrt(x * x + z * z);
                if (dd > r + 0.5) continue;
                if (hollow && dd < r - 0.5) continue;
                for (int y = y1; y <= y2; y++) set(cx + x, y, cz + z, s);
            }
    }

    public void dome(int cx, int cy, int cz, int r, Block b) {
        BlockState s = b.defaultBlockState();
        for (int x = -r; x <= r; x++)
            for (int y = 0; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    double dd = Math.sqrt(x * x + y * y + z * z);
                    if (dd <= r + 0.5 && dd >= r - 0.6) set(cx + x, cy + y, cz + z, s);
                }
    }

    /** Ladder column from y1 up to y1+height-1 attached to the wall on the local {@code wallSide}. */
    public void ladderUp(int lx, int y1, int lz, int height, Direction facing) {
        BlockState l = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
        for (int y = y1; y < y1 + height; y++) set(lx, y, lz, l);
    }

    /** Deterministic per-building pseudo random. */
    public int rand(int salt, int bound) {
        long h = seed * 6364136223846793005L + salt * 1442695040888963407L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return (int) Math.floorMod(h, (long) bound);
    }
}
