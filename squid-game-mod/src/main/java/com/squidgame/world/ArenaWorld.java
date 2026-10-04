package com.squidgame.world;

import com.squidgame.SquidGameMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Access to the dedicated tournament dimension ({@code squidgame:arena}, a void world defined by datapack JSON). */
public final class ArenaWorld {
    public static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, SquidGameMod.id("arena"));

    private ArenaWorld() {
    }

    /** The tournament level, or null if the dimension failed to load. */
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DIMENSION);
    }

    public static boolean isArena(Level level) {
        return level.dimension().equals(DIMENSION);
    }
}
