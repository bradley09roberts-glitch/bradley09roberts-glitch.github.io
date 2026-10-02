package com.terracraft.world.gen;

import com.terracraft.progression.WorldVariants;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Thread-safe view of the world's variants (ore pair choices, evil) for world generation threads, which must
 * not touch SavedData. Before the server has published the stored variants, they are derived from the seed
 * exactly like {@code WorldProgression} initialises them.
 */
public final class WorldgenVariants {
    private static volatile WorldVariants published;
    private static volatile long seededFor = Long.MIN_VALUE;
    private static volatile WorldVariants seeded = WorldVariants.DEFAULT;

    private WorldgenVariants() {}

    /** Called by the progression system whenever stored variants are loaded or changed. */
    public static void publish(WorldVariants variants) {
        published = variants;
    }

    public static void clear() {
        published = null;
    }

    public static WorldVariants get(WorldGenLevel level) {
        WorldVariants current = published;
        if (current != null) {
            return current;
        }
        long seed = level.getSeed();
        if (seededFor != seed) {
            seeded = WorldVariants.fromSeed(seed);
            seededFor = seed;
        }
        return seeded;
    }
}
