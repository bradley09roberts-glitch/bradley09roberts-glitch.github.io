package com.squidgame.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.squidgame.SquidGameMod;
import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BlockBuffer;
import com.squidgame.build.EntitySpec;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Copies a {@link BlockBuffer} into a {@link ServerLevel} in time slices so a multi-million block
 * arena never stalls the server: {@link #step} places blocks within a block and time budget, keeping
 * only the chunk being written force-loaded. Connection-dependent blocks (fences, panes, bars, walls)
 * get a neighbour-shape fix-up pass at the end, then the structure's entities are spawned.
 */
public final class BuildJob {
    private enum Stage {PLACE, SHAPES, ENTITIES, DONE}

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final ServerLevel level;
    private final ArenaId arena;
    private final ArenaBuilder builder;
    private final BlockBuffer buffer;
    private final long[] keys;
    private final BlockState[] stateCache;
    private final boolean[] shapeDependent;
    private final Set<String> badStates = new HashSet<>();
    private final LongArrayList shapePositions = new LongArrayList();
    private final LongOpenHashSet forced = new LongOpenHashSet();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private Stage stage = Stage.PLACE;
    private int sectionCursor;
    private int cellCursor;
    private int shapeCursor;
    private int entityCursor;
    private long placed;
    private final long totalCells;
    private long currentChunk = Long.MIN_VALUE;
    private long startNanos = System.nanoTime();

    public BuildJob(ServerLevel level, ArenaBuilder builder, BlockBuffer buffer) {
        this.level = level;
        this.arena = builder.id();
        this.builder = builder;
        this.buffer = buffer;
        this.keys = buffer.sortedSectionKeys();
        this.stateCache = new BlockState[buffer.paletteSize()];
        this.shapeDependent = new boolean[buffer.paletteSize()];
        this.totalCells = Math.max(1, buffer.writtenCount());
    }

    public ArenaId arena() {
        return arena;
    }

    public float progress() {
        return switch (stage) {
            case PLACE -> 0.9f * Math.min(1f, placed / (float) totalCells);
            case SHAPES -> 0.9f + 0.05f * (shapePositions.isEmpty() ? 1f : shapeCursor / (float) shapePositions.size());
            case ENTITIES -> 0.95f;
            case DONE -> 1f;
        };
    }

    public long placedBlocks() {
        return placed;
    }

    public boolean isDone() {
        return stage == Stage.DONE;
    }

    public long elapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private BlockState stateFor(int paletteIdx) {
        BlockState s = stateCache[paletteIdx];
        if (s != null) {
            return s;
        }
        String str = buffer.paletteState(paletteIdx);
        try {
            s = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), str, false).blockState();
        } catch (CommandSyntaxException | RuntimeException e) {
            if (badStates.add(str)) {
                SquidGameMod.LOGGER.error("[{}] unknown block state '{}': {}", arena, str, e.getMessage());
            }
            s = Blocks.MAGENTA_CONCRETE.defaultBlockState();
        }
        Block b = s.getBlock();
        shapeDependent[paletteIdx] = b instanceof CrossCollisionBlock || b instanceof WallBlock;
        stateCache[paletteIdx] = s;
        return s;
    }

    /**
     * Advances the job. Returns true once everything (blocks, shape fix-ups, entities) is finished.
     */
    public boolean step(int maxBlocks, long maxNanos) {
        long deadline = System.nanoTime() + maxNanos;
        int budget = maxBlocks;
        if (stage == Stage.PLACE) {
            while (sectionCursor < keys.length) {
                long key = keys[sectionCursor];
                int sx = buffer.sectionX(key), sy = buffer.sectionY(key), sz = buffer.sectionZ(key);
                loadChunk(sx, sz);
                short[] sec = buffer.section(key);
                int bx = sx << 4, by = sy << 4, bz = sz << 4;
                while (cellCursor < 4096) {
                    int idx = sec[cellCursor];
                    if (idx != 0) {
                        int c = cellCursor;
                        pos.set(bx + (c & 15), by + (c >> 8), bz + ((c >> 4) & 15));
                        BlockState state = stateFor(idx);
                        level.setBlock(pos, state, FLAGS);
                        if (shapeDependent[idx]) {
                            shapePositions.add(pos.asLong());
                        }
                        placed++;
                        budget--;
                    }
                    cellCursor++;
                    if (budget <= 0 || ((cellCursor & 255) == 0 && System.nanoTime() > deadline)) {
                        return false;
                    }
                }
                cellCursor = 0;
                sectionCursor++;
            }
            stage = Stage.SHAPES;
        }
        if (stage == Stage.SHAPES) {
            while (shapeCursor < shapePositions.size()) {
                BlockPos p = BlockPos.of(shapePositions.getLong(shapeCursor++));
                loadChunk(p.getX() >> 4, p.getZ() >> 4);
                BlockState cur = level.getBlockState(p);
                BlockState fixed = Block.updateFromNeighbourShapes(cur, level, p);
                if (fixed != cur) {
                    level.setBlock(p, fixed, FLAGS);
                }
                if ((shapeCursor & 255) == 0 && System.nanoTime() > deadline) {
                    return false;
                }
            }
            stage = Stage.ENTITIES;
        }
        if (stage == Stage.ENTITIES) {
            var specs = buffer.entities();
            while (entityCursor < specs.size()) {
                spawn(specs.get(entityCursor++));
                if (System.nanoTime() > deadline) {
                    return false;
                }
            }
            finish();
        }
        return stage == Stage.DONE;
    }

    private void loadChunk(int cx, int cz) {
        long k = ChunkPos.asLong(cx, cz);
        if (k == currentChunk) {
            return;
        }
        currentChunk = k;
        if (forced.add(k)) {
            level.setChunkForced(cx, cz, true);
        }
        level.getChunk(cx, cz); // force load / generate (void: instant)
    }

    private void spawn(EntitySpec spec) {
        loadChunk(((int) Math.floor(spec.x())) >> 4, ((int) Math.floor(spec.z())) >> 4);
        try {
            CompoundTag tag = TagParser.parseTag(spec.snbt());
            tag.putString("id", spec.type());
            Entity e = EntityType.loadEntityRecursive(tag, level, entity -> {
                entity.moveTo(spec.x(), spec.y(), spec.z(), spec.yaw(), spec.pitch());
                return entity;
            });
            if (e != null) {
                e.addTag("squidgame_static");
                level.addFreshEntity(e);
            }
        } catch (CommandSyntaxException | RuntimeException ex) {
            SquidGameMod.LOGGER.error("[{}] could not spawn {} at {},{},{}: {}", arena, spec.type(),
                    spec.x(), spec.y(), spec.z(), ex.getMessage());
        }
    }

    private void finish() {
        ArenaData data = ArenaData.get(level.getServer());
        data.store(arena, builder.version(), buffer);
        for (long k : forced) {
            level.setChunkForced(ChunkPos.getX(k), ChunkPos.getZ(k), false);
        }
        forced.clear();
        stage = Stage.DONE;
        SquidGameMod.LOGGER.info("[{}] built: {} blocks, {} entities, {} bad states, {} ms",
                arena, placed, buffer.entities().size(), badStates.size(), elapsedMillis());
    }

    /** Releases forced chunks if the job is abandoned. */
    public void abort() {
        for (long k : forced) {
            level.setChunkForced(ChunkPos.getX(k), ChunkPos.getZ(k), false);
        }
        forced.clear();
        stage = Stage.DONE;
    }
}
