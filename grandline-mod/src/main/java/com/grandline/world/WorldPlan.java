package com.grandline.world;

import com.grandline.GrandLine;
import com.grandline.core.GLConfig;
import com.grandline.player.Blue;
import com.grandline.world.islands.CanonIslands;
import com.grandline.world.islands.IslandNames;
import com.grandline.world.settlement.SettlementType;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The deterministic map of the ocean world: canon islands at fixed positions plus generated original islands.
 * Everything here is a pure function of the plan seed so any chunk can be generated independently.
 */
public final class WorldPlan {
    public static final int SEA_LEVEL = 63;
    private static final int CELL = 2048;
    private static final Map<Long, WorldPlan> CACHE = new ConcurrentHashMap<>();

    /** Which world this plan describes: the main ocean, the White Sea in the sky, or the deep sea. */
    public enum Kind { OCEAN, SKY, DEEP }

    public final long seed;
    public final Kind kind;
    public final List<Island> islands;
    private final Map<String, Island> byId = new HashMap<>();
    private final Long2ObjectOpenHashMap<Island[]> grid = new Long2ObjectOpenHashMap<>();
    private final SimplexNoise floorNoise;
    private final SimplexNoise wallNoise;

    public static WorldPlan of(RandomState randomState) {
        long s = randomState.getOrCreateRandomFactory(GrandLine.id("world_plan")).at(0, 0, 0).nextLong();
        return of(s);
    }

    public static WorldPlan of(long planSeed) {
        int count = proceduralCount();
        return CACHE.computeIfAbsent(planSeed ^ ((long) count << 48), k -> new WorldPlan(planSeed, count));
    }

    /** A new, uncached ocean plan for a seed (determinism checks: two independent plans of one seed must agree). */
    public static WorldPlan fresh(long planSeed) {
        return new WorldPlan(planSeed, proceduralCount());
    }

    /** Plan of a non-ocean kind (sky or deep sea) for the same world seed. */
    public static WorldPlan of(RandomState randomState, Kind kind) {
        long s = randomState.getOrCreateRandomFactory(GrandLine.id("world_plan")).at(0, 0, 0).nextLong();
        if (kind == Kind.OCEAN) return of(s);
        return CACHE.computeIfAbsent(s * 31 + kind.ordinal() * 0x632BE59BD9B4E019L, k -> new WorldPlan(s, kind));
    }

    /** Plan of the level's generator (ocean, sky or deep sea), or null for other levels. */
    @Nullable
    public static WorldPlan forLevel(net.minecraft.world.level.Level level) {
        if (!(level instanceof ServerLevel sl)) return null;
        if (sl.getChunkSource().getGenerator() instanceof com.grandline.world.gen.ArchipelagoChunkGenerator g)
            return of(sl.getChunkSource().randomState(), g.kind());
        return null;
    }

    /** Plan for the overworld of a running server (null if the overworld is not our generator). */
    @Nullable
    public static WorldPlan of(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        if (!(ow.getChunkSource().getGenerator() instanceof com.grandline.world.gen.ArchipelagoChunkGenerator)) return null;
        return of(ow.getChunkSource().randomState());
    }

    private static int proceduralCount() {
        try {
            return GLConfig.PROCEDURAL_ISLANDS.get();
        } catch (Exception e) {
            return 170; // config not loaded yet (e.g. datagen)
        }
    }

    private WorldPlan(long seed, Kind kind) {
        this.seed = seed;
        this.kind = kind;
        this.floorNoise = new SimplexNoise(new LegacyRandomSource(seed ^ 0x51F1L ^ kind.ordinal()));
        this.wallNoise = new SimplexNoise(new LegacyRandomSource(seed ^ 0xBEEFL));
        List<IslandSpec> specs = kind == Kind.SKY ? SkyWorld.islands(seed) : DeepWorld.islands(seed);
        List<Island> list = new ArrayList<>();
        long i = 0;
        for (IslandSpec spec : specs) {
            Island island = new Island(this, spec, seed * 37 + (spec.id.hashCode() * 0x9E3779B97F4A7C15L) + i++);
            list.add(island);
            byId.put(island.id, island);
            index(island);
        }
        this.islands = Collections.unmodifiableList(list);
        GrandLine.LOGGER.info("{} plan {}: {} islands", kind, seed, list.size());
    }

    private WorldPlan(long seed, int proceduralCount) {
        this.seed = seed;
        this.kind = Kind.OCEAN;
        this.floorNoise = new SimplexNoise(new LegacyRandomSource(seed ^ 0x51F1L));
        this.wallNoise = new SimplexNoise(new LegacyRandomSource(seed ^ 0xBEEFL));
        // Existing worlds must not change: the core canon roster and the originals keep their order, and so their
        // island seeds (noise, coastline, town layout). Originals are laid out around the core roster only; canon
        // islands added later come after them, and an original that would crowd one is dropped (its index stays used).
        List<IslandSpec> core = CanonIslands.core();
        List<IslandSpec> originals = generateOriginals(seed, core, proceduralCount);
        List<IslandSpec> added = CanonIslands.additions();
        Set<IslandSpec> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IslandSpec o : originals)
            if (added.stream().anyMatch(a -> Math.hypot(a.x - o.x, a.z - o.z) < a.radius + o.radius + 800)) dropped.add(o);
        List<IslandSpec> specs = new ArrayList<>(core);
        specs.addAll(originals);
        specs.addAll(added);
        List<Island> list = new ArrayList<>();
        long i = 0;
        for (IslandSpec spec : specs) {
            long index = i++;
            if (dropped.contains(spec)) continue;
            Island island = new Island(this, spec, seed * 31 + (spec.id.hashCode() * 0x9E3779B97F4A7C15L) + index);
            list.add(island);
            byId.put(island.id, island);
            index(island);
        }
        this.islands = Collections.unmodifiableList(list);
        GrandLine.LOGGER.info("World plan {}: {} islands ({} canon/starter, {} original)", seed, list.size(), CanonIslands.all().size(), list.size() - CanonIslands.all().size());
    }

    /** Point-sized places (Mary Geoise on the Red Line): no terrain of their own, but a set piece and people. */
    private final List<Island> points = new ArrayList<>();

    private void index(Island island) {
        if (island.radius < 10) {
            points.add(island);
            return;
        }
        int r = island.influenceRadius();
        for (int cx = Math.floorDiv(island.x - r, CELL); cx <= Math.floorDiv(island.x + r, CELL); cx++)
            for (int cz = Math.floorDiv(island.z - r, CELL); cz <= Math.floorDiv(island.z + r, CELL); cz++) {
                long key = key(cx, cz);
                Island[] arr = grid.get(key);
                if (arr == null) arr = new Island[]{island};
                else {
                    arr = Arrays.copyOf(arr, arr.length + 1);
                    arr[arr.length - 1] = island;
                }
                grid.put(key, arr);
            }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static final Island[] NONE = new Island[0];

    public Island[] candidates(int x, int z) {
        Island[] a = grid.get(key(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
        return a == null ? NONE : a;
    }

    /**
     * Islands whose settlements, set pieces and people may reach (x, z): the terrain index plus point-sized places
     * within {@code POINT_REACH} blocks. Terrain sampling keeps using {@link #candidates} so points shape no land.
     */
    public Island[] around(int x, int z) {
        Island[] a = candidates(x, z);
        for (Island p : points) {
            if (Math.abs(p.x - x) > POINT_REACH || Math.abs(p.z - z) > POINT_REACH) continue;
            a = Arrays.copyOf(a, a.length + 1);
            a[a.length - 1] = p;
        }
        return a;
    }

    public static final int POINT_REACH = 160;

    /** A point-sized place within {@code r} blocks (for discovery), or null. */
    @Nullable
    public Island pointAt(int x, int z, int r) {
        for (Island p : points) if (p.distance(x, z) <= r) return p;
        return null;
    }

    @Nullable
    public Island byId(String id) {
        return byId.get(id);
    }

    @Nullable
    public Island starterIsland(Blue blue) {
        for (Island i : islands) if (i.starterFor == blue) return i;
        return null;
    }

    /** Island whose land covers (x,z), or null. */
    @Nullable
    public Island islandAt(int x, int z) {
        Island best = null;
        double bestT = 1.15;
        for (Island i : candidates(x, z)) {
            double t = i.t(x, z);
            if (t < bestT) {
                bestT = t;
                best = i;
            }
        }
        return best;
    }

    @Nullable
    public Island nearest(int x, int z, double maxDist) {
        Island best = null;
        double bd = maxDist;
        for (Island i : islands) {
            double d = i.distance(x, z) - i.radius;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ terrain sampling

    /** Mutable result of {@link #sample}. */
    public static final class Sample {
        public int height;          // top solid block y
        public double exact;        // unrounded terrain height (slopes, surface variety)
        public int waterTop = SEA_LEVEL;
        @Nullable public Island island;
        public SeaZone zone;
        public boolean redLine;
        public boolean canal;
    }

    public void sample(int x, int z, Sample out) {
        out.island = null;
        out.redLine = false;
        out.canal = false;
        out.waterTop = SEA_LEVEL;
        if (kind != Kind.OCEAN) {
            out.zone = kind == Kind.SKY ? SeaZone.SKY_SEA : SeaZone.DEEP_SEA;
            double best = kind == Kind.SKY ? SkyWorld.FLOOR_Y : DeepWorld.floor(floorNoise, x, z);
            Island owner = null;
            for (Island i : candidates(x, z)) {
                double h = i.height(x, z);
                if (!Double.isNaN(h) && h > best) {
                    best = h;
                    owner = i;
                }
            }
            out.height = (int) Math.floor(best);
            out.exact = best;
            out.island = owner;
            return;
        }
        SeaZone zone = WorldLayout.zoneAt(x, z);
        out.zone = zone;
        double floor = oceanFloor(x, z, zone);
        double best = floor;
        Island owner = null;
        for (Island i : candidates(x, z)) {
            double h = i.height(x, z);
            if (!Double.isNaN(h) && h > best) {
                best = h;
                owner = i;
            }
        }
        // Red Line walls
        int rd = WorldLayout.redLineDistance(x);
        if (rd < WorldLayout.RED_LINE_HALF_WIDTH + 90) {
            double top = WorldLayout.RED_LINE_TOP + wallNoise.getValue(x * 0.01, z * 0.01) * 6 + wallNoise.getValue(x * 0.05, z * 0.05) * 2;
            double k = smooth(WorldLayout.RED_LINE_HALF_WIDTH + 90, WorldLayout.RED_LINE_HALF_WIDTH - 40, rd);
            double wall = Mth.lerp(k, floor, top);
            // cliff striations
            wall -= (1 - k) * Math.abs(wallNoise.getValue(x * 0.08, z * 0.02)) * 18;
            if (wall > best) {
                best = wall;
                owner = null;
                out.redLine = k > 0.5;
            }
        }
        // Reverse Mountain canals at (0,0)
        if (Math.abs(x) < 1100 && Math.abs(z) < 1100) {
            double canal = ReverseMountain.canalSurface(x, z);
            if (!Double.isNaN(canal)) {
                best = Math.min(best, canal - 3);
                out.waterTop = (int) Math.floor(canal);
                out.canal = true;
                owner = null;
            } else {
                double m = ReverseMountain.mountainHeight(x, z);
                if (m > best) {
                    best = m;
                    owner = null;
                }
            }
        }
        out.height = (int) Math.floor(best);
        out.exact = best;
        out.island = owner;
    }

    public double oceanFloor(int x, int z, SeaZone zone) {
        double f = zone.floorY + floorNoise.getValue(x * 0.0015, z * 0.0015) * 8 + floorNoise.getValue(x * 0.011, z * 0.011) * 2.5;
        if (zone == SeaZone.WORLD_EDGE) f -= 10;
        return Math.max(-50, f);
    }

    private static double smooth(double edge0, double edge1, double v) {
        double k = Mth.clamp((v - edge0) / (edge1 - edge0), 0, 1);
        return k * k * (3 - 2 * k);
    }

    // ------------------------------------------------------------------ original islands

    private static List<IslandSpec> generateOriginals(long seed, List<IslandSpec> fixed, int total) {
        RandomSource rnd = new LegacyRandomSource(seed ^ 0x15A9D5L);
        List<IslandSpec> out = new ArrayList<>();
        // zone weights roughly matching sea areas
        record ZoneBox(SeaZone zone, int minX, int maxX, int minZ, int maxZ, double weight) {}
        List<ZoneBox> boxes = List.of(
                new ZoneBox(SeaZone.EAST_BLUE, 2500, 40000, 8000, 29000, 0.105),
                new ZoneBox(SeaZone.NORTH_BLUE, 2500, 40000, -29000, -8000, 0.105),
                new ZoneBox(SeaZone.WEST_BLUE, -40000, -2500, -29000, -8000, 0.105),
                new ZoneBox(SeaZone.SOUTH_BLUE, -40000, -2500, 8000, 29000, 0.105),
                new ZoneBox(SeaZone.PARADISE, 2500, 57000, -3500, 3500, 0.24),
                new ZoneBox(SeaZone.CALM_BELT, -57000, 57000, 4300, 6200, 0.035),
                new ZoneBox(SeaZone.CALM_BELT, -57000, 57000, -6200, -4300, 0.035),
                new ZoneBox(SeaZone.NEW_WORLD, -57500, -2500, -3500, 3500, 0.27));
        List<int[]> placed = new ArrayList<>();
        for (IslandSpec s : fixed) placed.add(new int[]{s.x, s.z, Math.max(s.radius, 200)});
        int n = 0;
        for (ZoneBox box : boxes) {
            int want = (int) Math.round(total * box.weight);
            int attempts = 0;
            int made = 0;
            while (made < want && attempts < want * 60) {
                attempts++;
                int x = Mth.nextInt(rnd, box.minX, box.maxX);
                int z = Mth.nextInt(rnd, box.minZ, box.maxZ);
                if (WorldLayout.zoneAt(x, z) != box.zone) continue;
                if (WorldLayout.redLineDistance(x) < 1300) continue;
                if (Math.abs(x) < 1600 && Math.abs(z) < 1600) continue;
                int size = rnd.nextInt(10);
                int radius = size < 5 ? Mth.nextInt(rnd, 110, 260) : size < 9 ? Mth.nextInt(rnd, 260, 520) : Mth.nextInt(rnd, 520, 900);
                int minGap = box.zone.isGrandLine() ? 1700 : 2000;
                boolean ok = true;
                for (int[] p : placed) {
                    double dx = p[0] - x, dz = p[1] - z;
                    double need = p[2] + radius + minGap;
                    if (dx * dx + dz * dz < need * need) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;
                placed.add(new int[]{x, z, radius});
                out.add(original(rnd, box.zone, x, z, radius, n++));
                made++;
            }
        }
        return out;
    }

    private static IslandSpec original(RandomSource rnd, SeaZone zone, int x, int z, int radius, int index) {
        IslandSpec s = new IslandSpec();
        s.canon = false;
        s.x = x;
        s.z = z;
        s.radius = radius;
        s.name = IslandNames.generate(rnd);
        s.id = "isle_" + index + "_" + s.name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        IslandTheme[] themes = switch (zone) {
            case EAST_BLUE, SOUTH_BLUE -> new IslandTheme[]{IslandTheme.TEMPERATE, IslandTheme.MEADOW, IslandTheme.FLOWER, IslandTheme.TROPICAL, IslandTheme.SAVANNA, IslandTheme.TEMPERATE};
            case NORTH_BLUE, WEST_BLUE -> new IslandTheme[]{IslandTheme.TAIGA, IslandTheme.SNOW, IslandTheme.TEMPERATE, IslandTheme.ROCKY, IslandTheme.MEADOW, IslandTheme.DARK};
            case CALM_BELT -> new IslandTheme[]{IslandTheme.TROPICAL, IslandTheme.SWAMP, IslandTheme.ROCKY, IslandTheme.MUSHROOM};
            case PARADISE -> IslandTheme.values();
            case NEW_WORLD -> new IslandTheme[]{IslandTheme.VOLCANIC, IslandTheme.ICE, IslandTheme.DARK, IslandTheme.BADLANDS, IslandTheme.CHERRY, IslandTheme.GIANT_FOREST, IslandTheme.CANDY, IslandTheme.FUTURE, IslandTheme.SWAMP, IslandTheme.DESERT, IslandTheme.MUSHROOM};
            default -> new IslandTheme[]{IslandTheme.TEMPERATE};
        };
        s.theme = themes[rnd.nextInt(themes.length)];
        IslandShape[] shapes = {IslandShape.HILLS, IslandShape.HILLS, IslandShape.MOUNTAIN, IslandShape.PLATEAU, IslandShape.FLAT, IslandShape.SPIRES,
                IslandShape.CRATER, IslandShape.RING, IslandShape.TERRACES, IslandShape.ARCHIPELAGO, IslandShape.CLIFF_WALL, IslandShape.ATOLL};
        s.shape = shapes[rnd.nextInt(shapes.length)];
        if (s.theme == IslandTheme.DESERT || s.theme == IslandTheme.BADLANDS) s.shape = rnd.nextBoolean() ? IslandShape.DUNES : s.shape;
        s.peak = switch (s.shape) {
            case MOUNTAIN -> Mth.nextInt(rnd, 60, 140);
            case SPIRES -> Mth.nextInt(rnd, 40, 110);
            case PLATEAU, CLIFF_WALL -> Mth.nextInt(rnd, 25, 80);
            case CRATER -> Mth.nextInt(rnd, 30, 70);
            case TERRACES -> Mth.nextInt(rnd, 30, 70);
            default -> Mth.nextInt(rnd, 12, 45);
        };
        if (radius < 200) s.peak = Math.min(s.peak, 40);
        int roll = rnd.nextInt(100);
        boolean big = radius > 380;
        s.settlement = roll < 22 ? SettlementType.NONE
                : roll < 40 ? SettlementType.HAMLET
                : roll < 62 ? SettlementType.VILLAGE
                : roll < 74 ? (big ? SettlementType.TOWN : SettlementType.VILLAGE)
                : roll < 80 ? (big ? SettlementType.CITY : SettlementType.PORT)
                : roll < 87 ? SettlementType.MARINE_BASE
                : roll < 93 ? SettlementType.FORTRESS
                : SettlementType.RUINS;
        if (s.shape == IslandShape.RING || s.shape == IslandShape.ATOLL) s.settlement = rnd.nextBoolean() ? SettlementType.HAMLET : SettlementType.NONE;
        s.faction = switch (s.settlement) {
            case MARINE_BASE -> "marine";
            case FORTRESS -> rnd.nextInt(4) == 0 ? "revolutionary" : "pirate";
            default -> zone == SeaZone.NEW_WORLD && rnd.nextInt(3) == 0 ? "pirate" : "civilian";
        };
        if (s.settlement == SettlementType.MARINE_BASE) s.style = com.grandline.world.build.BuildStyle.MARINE;
        s.tier = Mth.clamp(zone.dangerTier + rnd.nextInt(3) - 1 + (zone == SeaZone.NEW_WORLD ? rnd.nextInt(3) : 0), 1, 10);
        if (rnd.nextInt(4) == 0) s.landmarks.add(new Island.LandmarkPlacement(rnd.nextBoolean() ? "treasure_cache" : "ancient_ruin", Mth.nextInt(rnd, -radius / 3, radius / 3), Mth.nextInt(rnd, -radius / 3, radius / 3), rnd.nextInt(4)));
        if (rnd.nextInt(5) == 0) s.landmarks.add(new Island.LandmarkPlacement("fruit_tree_grove", Mth.nextInt(rnd, -radius / 4, radius / 4), Mth.nextInt(rnd, -radius / 4, radius / 4), 0));
        return s;
    }
}
