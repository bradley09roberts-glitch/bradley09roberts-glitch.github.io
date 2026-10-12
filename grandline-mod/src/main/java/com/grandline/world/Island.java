package com.grandline.world;

import com.grandline.player.Blue;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** One island instance in a world plan. Terrain is a pure function of (plan seed, island spec, x, z). */
public final class Island {
    public final String id;
    public final String name;          // display name (canon islands use translation key "island.grandline.<id>")
    public final boolean canon;
    public final int x, z, radius, peak;
    public final IslandShape shape;
    public final IslandTheme theme;
    public final SeaZone zone;
    public final int tier;
    public final SettlementType settlementType;
    public final List<LandmarkPlacement> landmarks;
    @Nullable public final Blue starterFor;
    public final long seed;
    public final String faction;       // controlling faction id (marine, pirate, civilian, revolutionary, wg, wild)
    public final com.grandline.world.build.BuildStyle style;
    @Nullable public final IslandTheme altTheme;
    private final SimplexNoise noise;
    private volatile Settlement settlement;
    private final WorldPlan plan;

    public record LandmarkPlacement(String id, int dx, int dz, int rotation) {}

    Island(WorldPlan plan, IslandSpec spec, long seed) {
        this.plan = plan;
        this.id = spec.id;
        this.name = spec.name;
        this.canon = spec.canon;
        this.x = spec.x;
        this.z = spec.z;
        this.radius = spec.radius;
        this.peak = spec.peak;
        this.shape = spec.shape;
        this.theme = spec.theme;
        this.zone = WorldLayout.zoneAt(spec.x, spec.z);
        this.tier = spec.tier > 0 ? spec.tier : zone.dangerTier;
        this.settlementType = spec.settlement;
        this.landmarks = List.copyOf(spec.landmarks);
        this.starterFor = spec.starterFor;
        this.seed = seed;
        this.faction = spec.faction;
        this.style = spec.style != null ? spec.style : spec.theme.style;
        this.altTheme = spec.altTheme;
        this.noise = new SimplexNoise(new net.minecraft.world.level.levelgen.LegacyRandomSource(seed));
    }

    /** Climate at a column (split islands use their alternate theme on the eastern half). */
    public IslandTheme themeAt(int px, int pz) {
        if (altTheme != null && px > x + n(px * 0.01, pz * 0.01) * 40) return altTheme;
        return theme;
    }

    public String translationKey() {
        return "island.grandline." + id;
    }

    public Settlement settlement() {
        Settlement s = settlement;
        if (s == null) {
            synchronized (this) {
                s = settlement;
                if (s == null) {
                    s = Settlement.layout(this);
                    settlement = s;
                }
            }
        }
        return s;
    }

    public int influenceRadius() {
        return (int) (radius * 1.75) + 8;
    }

    public double distance(int px, int pz) {
        double dx = px - x, dz = pz - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double n(double a, double b) {
        return noise.getValue(a, b);
    }

    /** Effective normalised radius coordinate (0 at centre, 1 at the coastline). */
    public double t(int px, int pz) {
        double dx = px - x, dz = pz - z;
        double d = Math.sqrt(dx * dx + dz * dz);
        double wobble = n(px * 0.0035, pz * 0.0035) * 0.22 + n(px * 0.013 + 31.7, pz * 0.013 - 11.3) * 0.06;
        double rEff = radius * (1 + wobble);
        return d / rEff;
    }

    /**
     * Natural terrain height (ignoring settlement pads), or NaN if outside this island's influence.
     * Values below the sea level describe the underwater shelf.
     */
    public double naturalHeight(int px, int pz) {
        double dx = px - x, dz = pz - z;
        double dd = dx * dx + dz * dz;
        double inf = influenceRadius();
        if (dd > inf * inf) return Double.NaN;
        double t = t(px, pz);
        int sea = WorldPlan.SEA_LEVEL;
        if (t >= 1.0) {
            double k = Mth.clamp((t - 1.0) / 0.7, 0, 1);
            return (sea - 2) - k * 34;
        }
        double detail = n(px * 0.02, pz * 0.02) * 3 + n(px * 0.06 + 5, pz * 0.06 - 5) * 1.2;
        double inv = 1 - t;
        double h;
        switch (shape) {
            case MOUNTAIN -> h = sea + 1 + peak * Math.pow(inv, 1.7) + Math.abs(n(px * 0.01, pz * 0.01)) * peak * 0.18 * inv + detail;
            case PLATEAU -> h = sea + 1 + 3 * inv + peak * smooth(1.0, 0.8, t) + detail * 0.6;
            case FLAT -> h = sea + 2 + 4 * inv + detail * 0.6;
            case DUNES -> h = sea + 2 + 6 * inv + (Math.sin(px * 0.045 + n(px * 0.004, pz * 0.004) * 4) + 1) * 4 * Math.min(1, inv * 3) + detail * 0.5;
            case SPIRES -> {
                double base = sea + 2 + 7 * inv + detail;
                h = base + spire(px, pz) * peak * Math.min(1, inv * 2.5);
            }
            case CRATER -> {
                double rim = Math.exp(-Math.pow((t - 0.55) / 0.17, 2));
                h = sea - 1 + peak * rim + (t < 0.42 ? -7 : 2 * inv) + detail;
            }
            case RING -> h = t < 0.55 ? sea - 6 - (0.55 - t) * 10 : sea + 1 + 7 * Math.sin(Math.PI * (t - 0.55) / 0.45) + detail * 0.7;
            case ATOLL -> h = t < 0.7 ? sea - 4 - (0.7 - t) * 8 : sea + 1 + 3 * Math.sin(Math.PI * (t - 0.7) / 0.3) + detail * 0.4;
            case TERRACES -> {
                double raw = peak * Math.pow(inv, 1.1) * (0.6 + 0.4 * (n(px * 0.006, pz * 0.006) + 1) / 2);
                h = sea + 1 + Math.floor(raw / 9.0) * 9.0 + Math.min(3, (raw % 9.0)) + detail * 0.3;
            }
            case DRUM -> {
                double base = sea + 2 + 12 * inv + detail;
                double drum = 0;
                drum = Math.max(drum, cyl(px, pz, x, z, radius * 0.28, peak));
                drum = Math.max(drum, cyl(px, pz, x + radius * 0.45, z - radius * 0.2, radius * 0.16, peak * 0.6));
                drum = Math.max(drum, cyl(px, pz, x - radius * 0.4, z + radius * 0.3, radius * 0.14, peak * 0.5));
                h = Math.max(base, sea + drum);
            }
            case ARCHIPELAGO -> {
                double v = (n(px * 0.012, pz * 0.012) + 1) / 2 - t * 0.55;
                h = v > 0.2 ? sea + 1 + (v - 0.2) * peak * 2.5 + detail * 0.5 : sea - 3 - (0.2 - v) * 20;
            }
            case CLIFF_WALL -> {
                double wall = smooth(1.0, 0.9, t) * (t > 0.72 ? 1 : 0);
                double inner = t <= 0.72 ? peak * 0.22 + 6 * (1 - t / 0.72) : peak * 0.22;
                h = sea + 1 + Math.max(inner, peak * wall * smooth(0.66, 0.78, t)) + detail * 0.7;
            }
            case GIANT_TREE, HILLS -> h = sea + 1 + peak * Math.pow(inv, 1.2) * (0.55 + 0.45 * (n(px * 0.005, pz * 0.005) + 1) / 2) + detail;
            default -> h = sea + 1 + peak * inv + detail;
        }
        // soft beach band near the coast
        if (t > 0.9 && h > sea + 3) h = Mth.lerp((t - 0.9) / 0.1, h, sea + 2);
        return Math.min(h, 300);
    }

    /** Terrain height including settlement pads (flattened plots). */
    public double height(int px, int pz) {
        double h = naturalHeight(px, pz);
        if (Double.isNaN(h) || settlementType == SettlementType.NONE && landmarks.isEmpty()) return h;
        Settlement s = settlement();
        if (!s.bounds(px, pz)) return h;
        for (Plot p : s.plots) {
            if (!p.flatten()) continue;
            double out = p.outsideDistance(px, pz);
            if (out > 4) continue;
            double w = out <= 1 ? 1 : 1 - (out - 1) / 3.0;
            h = Mth.lerp(w, h, p.y() - 1);
        }
        return h;
    }

    private static double smooth(double edge0, double edge1, double v) {
        double k = Mth.clamp((v - edge0) / (edge1 - edge0), 0, 1);
        return k * k * (3 - 2 * k);
    }

    private static double cyl(int px, int pz, double cx, double cz, double r, double height) {
        double d = Math.sqrt((px - cx) * (px - cx) + (pz - cz) * (pz - cz));
        if (d > r + 6) return 0;
        if (d <= r) return height;
        return height * (1 - (d - r) / 6.0) * 0.3;
    }

    /** Sparse rock pillars: value 0..1 near a pillar centre. */
    private double spire(int px, int pz) {
        int cell = 22;
        int cx = Math.floorDiv(px, cell), cz = Math.floorDiv(pz, cell);
        double best = 0;
        for (int ix = -1; ix <= 1; ix++)
            for (int iz = -1; iz <= 1; iz++) {
                long h = hash(cx + ix, cz + iz);
                if ((h & 3) != 0) continue;
                double sx = (cx + ix) * cell + ((h >>> 8) & 15) + 3;
                double sz = (cz + iz) * cell + ((h >>> 16) & 15) + 3;
                double r = 3 + ((h >>> 24) & 3);
                double d = Math.sqrt((px - sx) * (px - sx) + (pz - sz) * (pz - sz));
                if (d < r) best = Math.max(best, (0.45 + ((h >>> 28) & 7) / 14.0) * (1 - Math.pow(d / r, 4)));
            }
        return best;
    }

    private long hash(int a, int b) {
        long h = seed ^ (a * 341873128712L) ^ (b * 132897987541L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    public WorldPlan plan() {
        return plan;
    }
}
