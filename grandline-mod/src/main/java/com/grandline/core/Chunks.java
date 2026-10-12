package com.grandline.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Non-blocking chunk checks for server-thread code. {@code ServerLevel.hasChunk}/{@code isLoaded}/{@code hasChunkAt}
 * are already true while a chunk inside a player's view distance is still being generated, and the first block read
 * from it then stalls the whole server until generation finishes (seconds on a busy server). Code that only wants to
 * act where the world is ready should ask {@link #ready} instead and simply try again later.
 */
public final class Chunks {
    /** True when the chunk holding block column (x, z) is fully loaded right now (server thread; other levels: hasChunkAt). */
    public static boolean ready(Level level, int blockX, int blockZ) {
        if (level instanceof ServerLevel sl) return sl.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4) != null;
        return level.hasChunkAt(blockX, blockZ);
    }

    /** True when a chunk in the square of block columns around (x, z) is scheduled to load but not loaded yet. */
    public static boolean pendingAround(ServerLevel level, int blockX, int blockZ, int radius) {
        var cache = level.getChunkSource();
        for (int cx = (blockX - radius) >> 4; cx <= (blockX + radius) >> 4; cx++)
            for (int cz = (blockZ - radius) >> 4; cz <= (blockZ + radius) >> 4; cz++)
                if (cache.hasChunk(cx, cz) && cache.getChunkNow(cx, cz) == null) return true;
        return false;
    }

    private Chunks() {}
}
