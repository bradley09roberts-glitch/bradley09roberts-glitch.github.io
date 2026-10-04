package com.squidgame.world;

import com.squidgame.SquidConfig;
import com.squidgame.SquidGameMod;
import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaBuilders;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BlockBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Generates and places arenas without stalling the server. Pipeline per arena:
 * <ol>
 *   <li>builder runs on a background thread into a {@link BlockBuffer} (pure Java);</li>
 *   <li>(rebuilds only) the previous footprint is cleared in time slices;</li>
 *   <li>{@link BuildJob} copies the buffer into the world in time slices;</li>
 *   <li>markers/regions are stored in {@link ArenaData}.</li>
 * </ol>
 * One build runs at a time; further requests are queued.
 */
public final class BuildManager {
    private static final ExecutorService GENERATOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SquidGame-Builder");
        t.setDaemon(true);
        return t;
    });

    private final MinecraftServer server;
    private final ArrayDeque<ArenaId> queue = new ArrayDeque<>();
    private final List<Consumer<Component>> feedback = new ArrayList<>();
    private final List<Runnable> onIdle = new ArrayList<>();
    private boolean force;
    private ArenaId generatingId;
    private ArenaBuilder generatingBuilder;
    private CompletableFuture<BlockBuffer> generating;
    private List<String> problems = new ArrayList<>();
    private BuildJob job;
    private ClearJob clear;
    private BlockBuffer pending;
    private int totalArenas;
    private int doneArenas;

    public BuildManager(MinecraftServer server) {
        this.server = server;
    }

    public boolean isBusy() {
        return generating != null || job != null || clear != null || !queue.isEmpty();
    }

    public String status() {
        if (!isBusy()) {
            return "idle";
        }
        if (generating != null) {
            return "generating " + generatingId;
        }
        if (clear != null) {
            return "clearing " + clear.arena;
        }
        if (job != null) {
            return String.format("placing %s %.0f%%", job.arena(), job.progress() * 100f);
        }
        return "queued " + queue.size();
    }

    /** Queues arenas for building. Already up-to-date arenas are skipped unless {@code force}. */
    public void request(List<ArenaId> arenas, boolean force, Consumer<Component> sink, Runnable whenIdle) {
        ArenaData data = ArenaData.get(server);
        for (ArenaId id : arenas) {
            ArenaBuilder b = ArenaBuilders.get(id);
            if (force || !data.isUpToDate(id, b.version()) || ArenaBuilders.isPlaceholder(id) && !data.isBuilt(id)) {
                if (!queue.contains(id) && id != generatingId) {
                    queue.add(id);
                }
            }
        }
        this.force |= force;
        if (sink != null) {
            feedback.add(sink);
        }
        if (whenIdle != null) {
            onIdle.add(whenIdle);
        }
        int inFlight = generating != null || clear != null || job != null ? 1 : 0;
        totalArenas = Math.max(totalArenas, doneArenas + queue.size() + inFlight);
        if (!isBusy()) {
            finishIdle();
        }
    }

    private void say(String msg) {
        SquidGameMod.LOGGER.info("[build] {}", msg);
        Component c = Component.literal("[Squid Game] " + msg);
        for (Consumer<Component> f : feedback) {
            try {
                f.accept(c);
            } catch (RuntimeException ignored) {
                // sink may be a disconnected player
            }
        }
    }

    private void finishIdle() {
        List<Runnable> r = new ArrayList<>(onIdle);
        onIdle.clear();
        feedback.clear();
        force = false;
        totalArenas = 0;
        doneArenas = 0;
        r.forEach(Runnable::run);
    }

    public void tick() {
        ServerLevel level = ArenaWorld.level(server);
        if (level == null) {
            if (!queue.isEmpty()) {
                say("The tournament dimension is not loaded; cannot build.");
                queue.clear();
                finishIdle();
            }
            return;
        }
        SquidConfig cfg = SquidConfig.get();
        long nanos = cfg.buildMillisPerTick * 1_000_000L;

        if (generating == null && clear == null && job == null && !queue.isEmpty()) {
            generatingId = queue.poll();
            generatingBuilder = ArenaBuilders.get(generatingId);
            problems = new ArrayList<>();
            final ArenaId id = generatingId;
            final List<String> probs = problems;
            say("Building " + id.id + " (" + (doneArenas + 1) + "/" + Math.max(totalArenas, doneArenas + 1) + ")...");
            generating = CompletableFuture.supplyAsync(() -> ArenaBuilders.buildAndValidate(id, 1L, probs), GENERATOR);
        }
        if (generating != null && generating.isDone()) {
            try {
                pending = generating.join();
            } catch (RuntimeException e) {
                SquidGameMod.LOGGER.error("Builder for {} crashed", generatingId, e);
                say("Builder for " + generatingId.id + " failed: " + e.getMessage());
                generating = null;
                generatingId = null;
                if (queue.isEmpty()) {
                    finishIdle();
                }
                return;
            }
            generating = null;
            for (String p : problems) {
                say("WARNING: " + p);
            }
            ArenaData.Record old = ArenaData.get(server).record(generatingId);
            if (old != null) {
                clear = new ClearJob(level, generatingId, old);
            } else {
                job = new BuildJob(level, generatingBuilder, pending);
            }
        }
        if (clear != null) {
            if (clear.step(200_000, nanos)) {
                ArenaData.get(server).forget(clear.arena);
                clear = null;
                job = new BuildJob(level, generatingBuilder, pending);
            }
            return;
        }
        if (job != null) {
            if (job.step(cfg.buildBlocksPerTick, nanos)) {
                say("Finished " + job.arena().id + " (" + job.placedBlocks() + " blocks, " + job.elapsedMillis() / 1000 + " s)");
                job = null;
                pending = null;
                generatingId = null;
                doneArenas++;
                if (queue.isEmpty()) {
                    server.saveEverything(true, false, false);
                    finishIdle();
                }
            }
        }
    }

    /** Clears the old footprint of an arena before a rebuild (time-sliced). */
    private static final class ClearJob {
        final ServerLevel level;
        final ArenaId arena;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        int x, y, z;
        boolean started;
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        final BlockState air = Blocks.AIR.defaultBlockState();

        ClearJob(ServerLevel level, ArenaId arena, ArenaData.Record r) {
            this.level = level;
            this.arena = arena;
            // entities tagged static in the footprint are removed as well
            this.minX = r.minX;
            this.minY = r.minY;
            this.minZ = r.minZ;
            this.maxX = r.maxX;
            this.maxY = r.maxY;
            this.maxZ = r.maxZ;
            this.x = minX;
            this.y = minY;
            this.z = minZ;
        }

        boolean step(int maxCells, long maxNanos) {
            if (!started) {
                started = true;
                for (var e : level.getAllEntities()) {
                    if (e.getTags().contains("squidgame_static") && e.getX() >= minX - 2 && e.getX() <= maxX + 3
                            && e.getZ() >= minZ - 2 && e.getZ() <= maxZ + 3) {
                        e.discard();
                    }
                }
            }
            long deadline = System.nanoTime() + maxNanos;
            int n = 0;
            while (y <= maxY) {
                while (z <= maxZ) {
                    while (x <= maxX) {
                        p.set(x, y, z);
                        if ((x & 15) == 0 || n == 0) {
                            level.getChunk(x >> 4, z >> 4);
                        }
                        if (!level.getBlockState(p).isAir()) {
                            level.setBlock(p, air, Block_FLAGS);
                        }
                        x++;
                        if (++n >= maxCells || ((n & 1023) == 0 && System.nanoTime() > deadline)) {
                            return false;
                        }
                    }
                    x = minX;
                    z++;
                }
                z = minZ;
                y++;
            }
            return true;
        }

        private static final int Block_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
    }
}
