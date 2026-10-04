package com.squidgame.tournament;

import com.squidgame.build.ArenaId;
import com.squidgame.world.ArenaData;
import com.squidgame.world.ArenaWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.EnumSet;
import java.util.Set;

/**
 * Keeps the chunks of active arenas loaded (so NPCs keep thinking, doors animate and nothing freezes while the
 * only human looks elsewhere) and releases them when the arena is no longer in use. Forced chunks are persistent in
 * the world, so {@link #releaseEverything} also runs on reset and start-up to clear leftovers of a crash.
 */
public final class ChunkLoader {
    private final MinecraftServer server;
    private final Set<ArenaId> active = EnumSet.noneOf(ArenaId.class);

    public ChunkLoader(MinecraftServer server) {
        this.server = server;
    }

    public void force(ArenaId id) {
        if (active.contains(id)) {
            return;
        }
        ServerLevel level = ArenaWorld.level(server);
        ArenaData.Record r = ArenaData.get(server).record(id);
        if (level == null || r == null) {
            return;
        }
        active.add(id);
        int count = 0;
        for (int cx = (r.minX - 8) >> 4; cx <= (r.maxX + 8) >> 4; cx++) {
            for (int cz = (r.minZ - 8) >> 4; cz <= (r.maxZ + 8) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                count++;
            }
        }
        com.squidgame.SquidGameMod.LOGGER.info("Forced {} chunks for {}", count, id);
    }

    public void release(ArenaId id) {
        if (!active.remove(id)) {
            return;
        }
        ServerLevel level = ArenaWorld.level(server);
        ArenaData.Record r = ArenaData.get(server).record(id);
        if (level == null || r == null) {
            return;
        }
        for (int cx = (r.minX - 8) >> 4; cx <= (r.maxX + 8) >> 4; cx++) {
            for (int cz = (r.minZ - 8) >> 4; cz <= (r.maxZ + 8) >> 4; cz++) {
                level.setChunkForced(cx, cz, false);
            }
        }
    }

    public void releaseAllActive() {
        for (ArenaId id : EnumSet.copyOf(active.isEmpty() ? EnumSet.noneOf(ArenaId.class) : active)) {
            release(id);
        }
    }

    /** Unforces every chunk of the arena dimension (including forced chunks left behind by an earlier run). */
    public void releaseEverything() {
        ServerLevel level = ArenaWorld.level(server);
        if (level == null) {
            return;
        }
        for (long l : level.getForcedChunks().toLongArray()) {
            level.setChunkForced(ChunkPos.getX(l), ChunkPos.getZ(l), false);
        }
        active.clear();
    }
}
