package com.squidgame.game.tug;

import com.squidgame.SquidGameMod;
import com.squidgame.build.Marker;
import com.squidgame.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The walkway of one team's platform: the strip of deck the team stands on along the rope. It can
 * <ul>
 *   <li>be fenced at its tip with invisible walls, so nobody walks (or is pushed) over the edge before the match is decided;</li>
 *   <li>collapse, column by column from the tip towards the rear: the deck, the plate and the ribs under the strip disappear
 *       with dust and the sound of the material, which drops everybody standing there into the pit;</li>
 *   <li>be repaired from the snapshot taken while it collapsed (between heats and in {@code cleanup}).</li>
 * </ul>
 * The geometry comes from the arena markers (slot 0 of the team, the rope centre), never from coordinates; the strip is 5 blocks
 * wide around the slot line and three layers deep below the standing level. What was changed is recorded here and can be saved
 * ({@link #save}) so that a server restart in the middle of a collapse repairs the deck on the next start.
 */
final class TugPlatform {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int HALF_WIDTH = 2;
    private static final int LAYERS = 3;

    private final ServerLevel level;
    /** +1 / -1: the direction along X from the rear of the deck towards the gap. */
    final int dir;
    /** Block y of the walking surface (the standing level is one block higher). */
    final int deckY;
    /** Block x of the tip column: the column of the strip nearest the gap. */
    final int tipX;
    final int zMin, zMax;
    /** Standing level of the team (world y). */
    final double standY;
    private final Map<BlockPos, BlockState> removed = new LinkedHashMap<>();
    private final List<BlockPos> fence = new ArrayList<>();
    private int columns;

    private TugPlatform(ServerLevel level, int dir, int deckY, int tipX, int zMin, int zMax, double standY) {
        this.level = level;
        this.dir = dir;
        this.deckY = deckY;
        this.tipX = tipX;
        this.zMin = zMin;
        this.zMax = zMax;
        this.standY = standY;
    }

    /**
     * @param slot0     the team's slot nearest the gap
     * @param tipColumn block x of the column of the walking surface nearest the gap (the end of the {@code tug.edge_*} region)
     * @param gapX      x of the middle of the gap
     */
    static TugPlatform create(ServerLevel level, Marker slot0, int tipColumn, double gapX) {
        int dir = gapX >= slot0.x() ? 1 : -1;
        int deckY = (int) Math.ceil(slot0.y()) - 1;
        int zc = (int) Math.floor(slot0.z());
        return new TugPlatform(level, dir, deckY, tipColumn, zc - HALF_WIDTH, zc + HALF_WIDTH, slot0.y());
    }

    int columnX(int i) {
        return tipX - dir * i;
    }

    /** Centre of the walking surface of column {@code i}, at standing height. */
    Vec3 columnCenter(int i) {
        return new Vec3(columnX(i) + 0.5, standY, (zMin + zMax + 1) / 2.0);
    }

    boolean isCollapsed() {
        return columns > 0;
    }

    int collapsedColumns() {
        return columns;
    }

    boolean hasChanges() {
        return !removed.isEmpty() || !fence.isEmpty();
    }

    // ------------------------------------------------------------------ fence

    /** Invisible walls across the tip, three blocks high (only into free cells). */
    void placeFence() {
        if (!fence.isEmpty()) {
            return;
        }
        int x = tipX;
        BlockState wall = ModBlocks.INVISIBLE_WALL.defaultBlockState();
        for (int z = zMin; z <= zMax; z++) {
            for (int y = deckY + 1; y <= deckY + 3; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, wall, FLAGS);
                    fence.add(pos);
                }
            }
        }
    }

    void removeFence() {
        for (BlockPos pos : fence) {
            if (level.getBlockState(pos).is(ModBlocks.INVISIBLE_WALL)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        fence.clear();
    }

    // ------------------------------------------------------------------ collapse and repair

    /** Collapses the columns up to (excluding) {@code upTo}, counted from the tip; returns how many were taken now. */
    int collapseTo(int upTo, boolean loud) {
        int n = 0;
        while (columns < upTo) {
            dropColumn(columns++, loud);
            n++;
        }
        return n;
    }

    private void dropColumn(int i, boolean loud) {
        int x = columnX(i);
        BlockState surface = null;
        for (int z = zMin; z <= zMax; z++) {
            for (int y = deckY; y >= deckY - (LAYERS - 1); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState st = level.getBlockState(pos);
                if (st.isAir() || st.is(ModBlocks.INVISIBLE_WALL) || removed.containsKey(pos)) {
                    continue;
                }
                removed.put(pos, st);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                if (y == deckY) {
                    if (surface == null || z == (zMin + zMax) / 2) {
                        surface = st;
                    }
                    if ((z - zMin) % 2 == 0) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), x + 0.5, y + 0.6, z + 0.5,
                                loud ? 8 : 4, 0.35, 0.15, 0.35, 0.12);
                    }
                }
            }
        }
        if (surface != null && (loud || i % 3 == 0)) {
            Vec3 c = columnCenter(i);
            level.playSound(null, c.x, c.y, c.z, surface.getSoundType().getBreakSound(), SoundSource.BLOCKS, loud ? 2.0f : 1.3f,
                    0.7f + 0.1f * (i % 4));
        }
    }

    /** Puts every block that was taken or placed back, in the order they were changed. Idempotent. */
    void restore() {
        removeFence();
        if (removed.isEmpty()) {
            columns = 0;
            return;
        }
        for (Map.Entry<BlockPos, BlockState> e : removed.entrySet()) {
            level.setBlock(e.getKey(), e.getValue(), FLAGS);
        }
        removed.clear();
        columns = 0;
    }

    // ------------------------------------------------------------------ persistence

    /** Writes what must be put back after a restart: the removed blocks (with their states) and the fence cells. */
    void save(CompoundTag out, String key) {
        CompoundTag t = new CompoundTag();
        ListTag palette = new ListTag();
        Map<BlockState, Integer> ids = new HashMap<>();
        long[] positions = new long[removed.size()];
        int[] indices = new int[removed.size()];
        int k = 0;
        for (Map.Entry<BlockPos, BlockState> e : removed.entrySet()) {
            Integer id = ids.get(e.getValue());
            if (id == null) {
                id = palette.size();
                ids.put(e.getValue(), id);
                palette.add(NbtUtils.writeBlockState(e.getValue()));
            }
            positions[k] = e.getKey().asLong();
            indices[k++] = id;
        }
        t.put("palette", palette);
        t.putLongArray("pos", positions);
        t.putIntArray("idx", indices);
        long[] fenceCells = new long[fence.size()];
        for (int i = 0; i < fenceCells.length; i++) {
            fenceCells[i] = fence.get(i).asLong();
        }
        t.putLongArray("fence", fenceCells);
        out.put(key, t);
    }

    /**
     * Repairs a platform that was left broken by an interrupted game: puts the saved blocks back and takes the fence cells
     * out. Safe to call with anything (unknown blocks are skipped); returns the number of blocks restored.
     */
    static int repair(ServerLevel level, CompoundTag in, String key) {
        if (!in.contains(key, Tag.TAG_COMPOUND)) {
            return 0;
        }
        CompoundTag t = in.getCompound(key);
        int n = 0;
        try {
            ListTag palette = t.getList("palette", Tag.TAG_COMPOUND);
            List<BlockState> states = new ArrayList<>();
            for (int i = 0; i < palette.size(); i++) {
                states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
            }
            long[] positions = t.getLongArray("pos");
            int[] indices = t.getIntArray("idx");
            for (int i = 0; i < positions.length && i < indices.length; i++) {
                if (indices[i] >= 0 && indices[i] < states.size()) {
                    level.setBlock(BlockPos.of(positions[i]), states.get(indices[i]), FLAGS);
                    n++;
                }
            }
            for (long cell : t.getLongArray("fence")) {
                BlockPos pos = BlockPos.of(cell);
                if (level.getBlockState(pos).is(ModBlocks.INVISIBLE_WALL)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        } catch (RuntimeException e) {
            SquidGameMod.LOGGER.error("Could not repair the tug of war platform from the saved state", e);
        }
        return n;
    }
}
