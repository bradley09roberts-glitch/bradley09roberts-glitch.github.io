package com.terracraft.world.evil;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a world's Corruption or Crimson lies. Like a Terraria world, each world has a few large evil patches
 * placed away from spawn; they are pure functions of the seed so every chunk can be generated on its own.
 */
public final class EvilZones {
    public record Zone(double x, double z, double radius, double wobblePhase) {
        /** Effective radius in a direction (wavy edges). */
        double radiusAt(double angle) {
            return radius * (1.0 + 0.18 * Math.sin(angle * 3 + wobblePhase) + 0.08 * Math.sin(angle * 7 + wobblePhase * 2));
        }

        public boolean contains(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            return dx * dx + dz * dz <= Mth.square(radiusAt(Math.atan2(dz, dx)));
        }
    }

    /** Tells whether a world position is dry land (decided from the seed's biome layout). */
    public interface LandTest {
        boolean isLand(int x, int z);
    }

    private static long cachedSeed = Long.MIN_VALUE;
    private static List<Zone> cached = List.of();

    private EvilZones() {}

    /** Zones as computed for the running world (must have been initialised with {@link #zones(long, LandTest)}). */
    public static synchronized List<Zone> zones(long seed) {
        return seed == cachedSeed ? cached : zones(seed, (x, z) -> true);
    }

    /**
     * Places three zones (near, far, very far from spawn). Each placement retries its direction until the
     * centre and most of its rim are land, so evil biomes do not end up in the ocean.
     */
    public static synchronized List<Zone> zones(long seed, LandTest land) {
        if (seed != cachedSeed) {
            RandomSource random = RandomSource.create(seed ^ 0x5EED_E71L);
            List<Zone> zones = new ArrayList<>();
            double angle = random.nextDouble() * Math.PI * 2;
            zones.add(place(random, land, angle, 380, 220, 80 + random.nextInt(30)));
            zones.add(place(random, land, angle + Math.PI, 650, 300, 70 + random.nextInt(30)));
            zones.add(place(random, land, random.nextDouble() * Math.PI * 2, 1300, 500, 90 + random.nextInt(40)));
            cached = List.copyOf(zones);
            cachedSeed = seed;
        }
        return cached;
    }

    private static Zone place(RandomSource random, LandTest land, double baseAngle, int minDistance, int spread, double radius) {
        Zone fallback = null;
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = baseAngle + (attempt == 0 ? 0 : (random.nextDouble() - 0.5) * Math.min(Math.PI * 2, 0.5 + attempt * 0.25));
            double distance = minDistance + random.nextInt(spread) + attempt * 20;
            Zone zone = new Zone(Math.cos(angle) * distance, Math.sin(angle) * distance, radius, random.nextDouble() * Math.PI * 2);
            if (fallback == null) {
                fallback = zone;
            }
            int dry = 0;
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                if (land.isLand((int) (zone.x() + Math.cos(a) * radius * 0.7), (int) (zone.z() + Math.sin(a) * radius * 0.7))) {
                    dry++;
                }
            }
            if (land.isLand((int) zone.x(), (int) zone.z()) && dry >= 6) {
                return zone;
            }
        }
        return fallback;
    }

    public static boolean isEvil(long seed, double x, double z) {
        for (Zone zone : zones(seed)) {
            if (zone.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    /** Cheap test whether a 16x16 chunk could touch any zone. */
    public static boolean chunkNearZone(long seed, int chunkX, int chunkZ) {
        double cx = chunkX * 16 + 8;
        double cz = chunkZ * 16 + 8;
        for (Zone zone : zones(seed)) {
            double reach = zone.radius() * 1.3 + 12;
            if (Mth.square(cx - zone.x()) + Mth.square(cz - zone.z()) <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    public static Zone nearest(long seed, double x, double z) {
        Zone best = null;
        double bestDist = Double.MAX_VALUE;
        for (Zone zone : zones(seed)) {
            double d = Mth.square(x - zone.x()) + Mth.square(z - zone.z());
            if (d < bestDist) {
                bestDist = d;
                best = zone;
            }
        }
        return best;
    }
}
