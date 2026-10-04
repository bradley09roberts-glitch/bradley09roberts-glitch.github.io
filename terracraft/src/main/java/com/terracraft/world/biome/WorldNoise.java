package com.terracraft.world.biome;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

/** Deterministic hashing and smooth value noise for TerraCraft's biome features (same result in every chunk). */
public final class WorldNoise {
    private WorldNoise() {}

    public static long hash(long seed, int a, int b, int c) {
        long h = seed ^ 0x2545F4914F6CDD1DL;
        h = (h ^ a) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 31) ^ b) * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 29) ^ c) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }

    /** 0..1 */
    public static double unit(long seed, int a, int b, int c) {
        return (hash(seed, a, b, c) % 1_000_000) / 1_000_000.0;
    }

    /** Smooth 3D value noise in 0..1 on a lattice of {@code cell} blocks. */
    public static double smooth(long seed, int x, int y, int z, int cell) {
        int x0 = Math.floorDiv(x, cell);
        int y0 = Math.floorDiv(y, cell);
        int z0 = Math.floorDiv(z, cell);
        double fx = fade((x - x0 * cell) / (double) cell);
        double fy = fade((y - y0 * cell) / (double) cell);
        double fz = fade((z - z0 * cell) / (double) cell);
        double c00 = Mth.lerp(fx, unit(seed, x0, y0, z0), unit(seed, x0 + 1, y0, z0));
        double c10 = Mth.lerp(fx, unit(seed, x0, y0 + 1, z0), unit(seed, x0 + 1, y0 + 1, z0));
        double c01 = Mth.lerp(fx, unit(seed, x0, y0, z0 + 1), unit(seed, x0 + 1, y0, z0 + 1));
        double c11 = Mth.lerp(fx, unit(seed, x0, y0 + 1, z0 + 1), unit(seed, x0 + 1, y0 + 1, z0 + 1));
        return Mth.lerp(fz, Mth.lerp(fy, c00, c10), Mth.lerp(fy, c01, c11));
    }

    private static double fade(double t) {
        return t * t * (3 - 2 * t);
    }

    /** The biome at a column's surface level (from the biome source, safe during world generation). */
    public static boolean biomeIs(ServerLevel level, int x, int z, TagKey<Biome> tag) {
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        Holder<Biome> biome = source.getNoiseBiome(x >> 2, 16, z >> 2, level.getChunkSource().randomState().sampler());
        return biome.is(tag);
    }
}
