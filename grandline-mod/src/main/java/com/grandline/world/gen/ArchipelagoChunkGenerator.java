package com.grandline.world.gen;

import com.grandline.world.Island;
import com.grandline.world.IslandTheme;
import com.grandline.world.WorldPlan;
import com.grandline.world.build.Builder;
import com.grandline.world.build.ChunkSink;
import com.grandline.world.landmark.Landmarks;
import com.grandline.world.settlement.Buildings;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.core.RegistryAccess;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The ocean-world generator. Terrain comes from {@link WorldPlan#sample}; settlements and landmarks are stamped in
 * during decoration, each chunk writing only its own blocks so any chunk generates independently.
 */
public final class ArchipelagoChunkGenerator extends ChunkGenerator {
    public static final MapCodec<ArchipelagoChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            com.mojang.serialization.Codec.STRING.optionalFieldOf("plan", "ocean").forGetter(g -> g.kind.name().toLowerCase(java.util.Locale.ROOT))
    ).apply(i, ArchipelagoChunkGenerator::new));

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    private final WorldPlan.Kind kind;

    public ArchipelagoChunkGenerator(BiomeSource biomeSource) {
        this(biomeSource, "ocean");
    }

    public ArchipelagoChunkGenerator(BiomeSource biomeSource, String plan) {
        super(biomeSource);
        this.kind = WorldPlan.Kind.valueOf(plan.toUpperCase(java.util.Locale.ROOT));
    }

    /** Ocean world, White Sea (sky) or deep sea. */
    public WorldPlan.Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    private WorldPlan plan(RandomState rs) {
        WorldPlan p = WorldPlan.of(rs, kind);
        if (biomeSource instanceof ArchipelagoBiomeSource src) src.plan = p;
        if (kind == WorldPlan.Kind.OCEAN) ArchipelagoBiomeSource.activePlan = p;
        return p;
    }

    // ------------------------------------------------------------------ biomes

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        WorldPlan plan = plan(randomState);
        if (biomeSource instanceof ArchipelagoBiomeSource src) {
            WorldPlan.Sample s = new WorldPlan.Sample();
            @SuppressWarnings("unchecked") Holder<Biome>[] cache = new Holder[4 * 4];
            int qx0 = chunk.getPos().x << 2, qz0 = chunk.getPos().z << 2;
            BiomeResolver resolver = (qx, qy, qz, sampler) -> {
                int ix = qx - qx0, iz = qz - qz0;
                if (ix >= 0 && ix < 4 && iz >= 0 && iz < 4) {
                    Holder<Biome> h = cache[ix * 4 + iz];
                    if (h == null) cache[ix * 4 + iz] = h = src.biomeAt(plan, (qx << 2) + 2, (qz << 2) + 2, s);
                    return h;
                }
                return src.biomeAt(plan, qx << 2, qz << 2, s);
            };
            chunk.fillBiomesFromNoise(resolver, randomState.sampler());
            return CompletableFuture.completedFuture(chunk);
        }
        return super.createBiomes(randomState, blender, structureManager, chunk);
    }

    // ------------------------------------------------------------------ terrain

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        WorldPlan plan = plan(randomState);
        LevelChunkSection[] sections = chunk.getSections();
        for (LevelChunkSection sec : sections) sec.acquire();
        try {
            switch (kind) {
                case OCEAN -> fill(plan, chunk);
                case SKY -> fillSky(plan, chunk);
                case DEEP -> fillDeep(plan, chunk);
            }
        } finally {
            for (LevelChunkSection sec : sections) sec.release();
        }
        return CompletableFuture.completedFuture(chunk);
    }

    private static SimplexNoise caveNoise(long seed) {
        return new SimplexNoise(new LegacyRandomSource(seed ^ 0xCA7EL));
    }

    private void fill(WorldPlan plan, ChunkAccess chunk) {
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = chunk.getMinBuildHeight();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        WorldPlan.Sample s = new WorldPlan.Sample();
        SimplexNoise cave = caveNoise(plan.seed);
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                plan.sample(x, z, s);
                int h = Math.max(minY + 2, Math.min(chunk.getMaxBuildHeight() - 2, s.height));
                Island isl = s.island;
                IslandTheme theme = isl != null ? isl.themeAt(x, z) : null;
                boolean land = h >= WorldPlan.SEA_LEVEL;
                BlockState top, sub, mid;
                if (s.redLine || (isl == null && land && !s.canal)) {
                    top = h > 250 ? Blocks.RED_SANDSTONE.defaultBlockState() : Blocks.RED_TERRACOTTA.defaultBlockState();
                    sub = Blocks.TERRACOTTA.defaultBlockState();
                    mid = ((h + x / 7) & 3) == 0 ? Blocks.ORANGE_TERRACOTTA.defaultBlockState() : Blocks.RED_TERRACOTTA.defaultBlockState();
                } else if (theme != null && land) {
                    boolean beach = h <= WorldPlan.SEA_LEVEL + 2 && isl.t(x, z) > 0.84;
                    boolean steep = isSteep(plan, s, x, z, h);
                    top = (beach ? theme.beach : steep && theme != IslandTheme.DESERT ? theme.stone : theme.surface).defaultBlockState();
                    sub = (beach ? theme.beach : theme.subsurface).defaultBlockState();
                    mid = theme.stone.defaultBlockState();
                } else {
                    // sea floor
                    int v = Math.floorMod((x * 31 + z * 17) ^ (x >> 3) ^ (z >> 2), 23);
                    top = (s.canal ? Blocks.SMOOTH_STONE : v < 2 ? Blocks.CLAY : v < 6 ? Blocks.GRAVEL : Blocks.SAND).defaultBlockState();
                    sub = Blocks.SAND.defaultBlockState();
                    mid = STONE;
                }
                int waterTop = Math.max(s.waterTop, WorldPlan.SEA_LEVEL);
                int lastSolid = minY;
                for (int y = minY; y <= Math.max(h, waterTop); y++) {
                    BlockState st;
                    if (y == minY || (y < minY + 4 && ((x ^ z ^ y) & 3) == 0)) st = BEDROCK;
                    else if (y <= h) {
                        int depth = h - y;
                        if (depth == 0) st = top;
                        else if (depth < 4) st = sub;
                        else if (depth < 8) st = mid;
                        else st = y < 0 ? DEEPSLATE : STONE;
                        // caves inside islands and the Red Line
                        if (depth > 6 && y > minY + 6 && (isl != null || s.redLine) && y < 200) {
                            double n = cave.getValue(x * 0.035, y * 0.06, z * 0.035);
                            if (n > 0.72) st = y < -40 ? Blocks.LAVA.defaultBlockState() : AIR;
                        }
                    } else if (y <= waterTop) {
                        st = WATER;
                    } else st = AIR;
                    if (st.isAir()) continue;
                    LevelChunkSection sec = chunk.getSection(chunk.getSectionIndex(y));
                    sec.setBlockState(lx, y & 15, lz, st, false);
                    if (!st.is(Blocks.WATER) && !st.is(Blocks.LAVA)) lastSolid = y;
                }
                ocean.update(lx, lastSolid, lz, top);
                surface.update(lx, Math.max(lastSolid, waterTop), lz, waterTop > lastSolid ? WATER : top);
            }
    }

    // ------------------------------------------------------------------ the White Sea (sky)

    private void fillSky(WorldPlan plan, ChunkAccess chunk) {
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BlockState cloud = com.grandline.registry.ModBlocks.ISLAND_CLOUD.get().defaultBlockState();
        WorldPlan.Sample s = new WorldPlan.Sample();
        int floorBottom = com.grandline.world.SkyWorld.FLOOR_Y - 1;
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                plan.sample(x, z, s);
                int h = Math.max(com.grandline.world.SkyWorld.FLOOR_Y, Math.min(chunk.getMaxBuildHeight() - 2, s.height));
                Island isl = s.island;
                IslandTheme theme = isl != null ? isl.themeAt(x, z) : null;
                boolean cloudCol = theme == null || theme == IslandTheme.CLOUD || h < WorldPlan.SEA_LEVEL;
                BlockState top, sub, mid;
                if (cloudCol) top = sub = mid = cloud;
                else {
                    boolean steep = isSteep(plan, s, x, z, h);
                    top = (steep ? theme.stone : theme.surface).defaultBlockState();
                    sub = theme.subsurface.defaultBlockState();
                    mid = theme.stone.defaultBlockState();
                }
                for (int y = floorBottom; y <= Math.max(h, WorldPlan.SEA_LEVEL); y++) {
                    BlockState st;
                    if (y <= h) {
                        int depth = h - y;
                        st = depth == 0 ? top : depth < 4 ? sub : y <= floorBottom + 1 ? cloud : mid;
                    } else st = WATER; // Sea Cloud (tinted by the white_sea biome)
                    chunk.getSection(chunk.getSectionIndex(y)).setBlockState(lx, y & 15, lz, st, false);
                }
                ocean.update(lx, h, lz, top);
                surface.update(lx, Math.max(h, WorldPlan.SEA_LEVEL), lz, h >= WorldPlan.SEA_LEVEL ? top : WATER);
            }
    }

    // ------------------------------------------------------------------ the deep sea

    private void fillDeep(WorldPlan plan, ChunkAccess chunk) {
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = chunk.getMinBuildHeight();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BlockState membrane = com.grandline.registry.ModBlocks.BUBBLE_MEMBRANE.get().defaultBlockState();
        WorldPlan.Sample s = new WorldPlan.Sample();
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                plan.sample(x, z, s);
                int h = Math.max(minY + 2, s.height);
                Island isl = s.island;
                IslandTheme theme = isl != null ? isl.themeAt(x, z) : null;
                BlockState top, sub, mid;
                if (theme != null) {
                    boolean steep = isSteep(plan, s, x, z, h);
                    top = (steep ? theme.stone : theme.surface).defaultBlockState();
                    sub = theme.subsurface.defaultBlockState();
                    mid = theme.stone.defaultBlockState();
                } else {
                    int v = Math.floorMod((x * 31 + z * 17) ^ (x >> 3) ^ (z >> 2), 19);
                    top = (v < 3 ? Blocks.CLAY : v < 7 ? Blocks.GRAVEL : v < 9 ? Blocks.MAGMA_BLOCK : Blocks.SAND).defaultBlockState();
                    sub = Blocks.SAND.defaultBlockState();
                    mid = Blocks.DEEPSLATE.defaultBlockState();
                }
                com.grandline.world.DeepWorld.Dome dome = com.grandline.world.DeepWorld.domeAt(x, z);
                int highestAir = -1;
                for (int y = minY; y <= com.grandline.world.DeepWorld.WATER_TOP; y++) {
                    BlockState st;
                    if (y == minY || (y < minY + 4 && ((x ^ z ^ y) & 3) == 0)) st = BEDROCK;
                    else if (y <= h) {
                        int depth = h - y;
                        st = depth == 0 ? top : depth < 4 ? sub : depth < 8 ? mid : y < 0 ? DEEPSLATE : STONE;
                    } else if (dome != null && y > WorldPlan.SEA_LEVEL && com.grandline.world.DeepWorld.inAnyDome(x, y, z)) {
                        highestAir = y;
                        continue;
                    } else if (dome != null && y > WorldPlan.SEA_LEVEL && com.grandline.world.DeepWorld.isMembrane(x, y, z)) st = membrane;
                    else st = WATER;
                    chunk.getSection(chunk.getSectionIndex(y)).setBlockState(lx, y & 15, lz, st, false);
                }
                ocean.update(lx, h, lz, top);
                surface.update(lx, com.grandline.world.DeepWorld.WATER_TOP, lz, WATER);
            }
    }

    private static boolean isSteep(WorldPlan plan, WorldPlan.Sample s, int x, int z, int h) {
        Island isl = s.island;
        if (isl == null) return false;
        double a = isl.height(x + 2, z), b = isl.height(x, z + 2);
        if (Double.isNaN(a) || Double.isNaN(b)) return false;
        return Math.abs(a - h) > 5 || Math.abs(b - h) > 5;
    }

    // ------------------------------------------------------------------ decoration (vanilla features + settlements)

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        ServerLevel server = level.getLevel();
        WorldPlan plan = plan(server.getChunkSource().randomState());
        ChunkSink chunkSink = new ChunkSink(level, chunk);
        com.grandline.world.build.BlockSink sink = kind == WorldPlan.Kind.DEEP ? new com.grandline.world.build.FloodedSink(chunkSink) : chunkSink;
        int cx = chunk.getPos().getMiddleBlockX(), cz = chunk.getPos().getMiddleBlockZ();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        int maxX = minX + 15, maxZ = minZ + 15;
        for (Island isl : plan.candidates(cx, cz)) {
            Settlement st = isl.settlement();
            for (Plot p : st.secrets) {
                if (p.x() - 2 > maxX || p.x() + p.sizeX() + 2 < minX || p.z() - 2 > maxZ || p.z() + p.sizeZ() + 2 < minZ) continue;
                Buildings.build(p, isl.style, new Builder(sink, p.x(), p.y(), p.z(), p.type().w, p.type().d, p.rotation(), p.seed()));
            }
            if (st.plots.isEmpty() && !st.hasDock) continue;
            if (st.maxX < minX || st.minX > maxX || st.maxZ < minZ || st.minZ > maxZ) continue;
            stampRoads(isl, st, chunkSink, minX, minZ, maxX, maxZ);
            stampDock(isl, st, chunkSink, minX, minZ, maxX, maxZ);
            for (Plot p : st.plots) {
                if (p.x() - 2 > maxX || p.x() + p.sizeX() + 2 < minX || p.z() - 2 > maxZ || p.z() + p.sizeZ() + 2 < minZ) continue;
                if (p.type() == com.grandline.world.settlement.BuildingType.LANDMARK) {
                    Landmarks.Spec spec = Landmarks.spec(p.landmark());
                    Builder b = new Builder(sink, p.x(), p.y(), p.z(), spec.width(), spec.depth(), p.rotation(), p.seed());
                    Landmarks.build(p, isl, b);
                } else {
                    Builder b = new Builder(sink, p.x(), p.y(), p.z(), p.type().w, p.type().d, p.rotation(), p.seed());
                    Buildings.build(p, isl.style, b);
                }
            }
        }
    }

    private static void stampRoads(Island isl, Settlement st, ChunkSink sink, int minX, int minZ, int maxX, int maxZ) {
        BlockState road = switch (isl.style) {
            case DESERT -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case SNOW -> Blocks.PACKED_ICE.defaultBlockState();
            case VENETIAN, PALACE, MARINE, FUTURE -> Blocks.STONE_BRICKS.defaultBlockState();
            case CANDY -> Blocks.PINK_TERRACOTTA.defaultBlockState();
            case WANO -> Blocks.GRAVEL.defaultBlockState();
            case STONE_TOWN -> Blocks.COBBLESTONE.defaultBlockState();
            default -> Blocks.DIRT_PATH.defaultBlockState();
        };
        for (int[] r : st.roads) {
            int ax = r[0], az = r[1], bx = r[2], bz = r[3];
            if (Math.max(ax, bx) + 2 < minX || Math.min(ax, bx) - 2 > maxX || Math.max(az, bz) + 2 < minZ || Math.min(az, bz) - 2 > maxZ) continue;
            int steps = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
            for (int i = 0; i <= steps; i++) {
                int x = ax + (bx - ax) * i / Math.max(1, steps);
                int z = az + (bz - az) * i / Math.max(1, steps);
                for (int ox = 0; ox <= 1; ox++)
                    for (int oz = 0; oz <= 1; oz++) {
                        int px = x + ox, pz = z + oz;
                        if (px < minX || px > maxX || pz < minZ || pz > maxZ) continue;
                        double h = isl.height(px, pz);
                        if (Double.isNaN(h) || h < WorldPlan.SEA_LEVEL) continue;
                        int y = (int) Math.floor(h);
                        BlockState cur = sink.get(px, y, pz);
                        if (cur == null || cur.isAir() || cur.liquid()) continue;
                        if (!(cur.is(Blocks.GRASS_BLOCK) || cur.is(Blocks.DIRT) || cur.is(Blocks.SAND) || cur.is(Blocks.SNOW_BLOCK) || cur.is(Blocks.PODZOL)
                                || cur.is(Blocks.COARSE_DIRT) || cur.is(Blocks.RED_SAND) || cur.is(Blocks.MUD) || cur.is(Blocks.MYCELIUM) || cur.is(Blocks.PINK_CONCRETE_POWDER)
                                || cur.is(Blocks.STONE) || cur.is(Blocks.CALCITE) || cur.is(Blocks.BLACKSTONE) || cur.is(Blocks.GRAVEL))) continue;
                        sink.set(px, y, pz, road);
                        for (int k = 1; k <= 3; k++) {
                            BlockState above = sink.get(px, y + k, pz);
                            if (above != null && !above.isAir() && (above.canBeReplaced() || above.is(net.minecraft.tags.BlockTags.FLOWERS) || above.is(net.minecraft.tags.BlockTags.LEAVES) || above.is(net.minecraft.tags.BlockTags.LOGS))) sink.set(px, y + k, pz, AIR);
                        }
                    }
            }
        }
    }

    private static void stampDock(Island isl, Settlement st, ChunkSink sink, int minX, int minZ, int maxX, int maxZ) {
        if (!st.hasDock) return;
        int len = Settlement.DOCK_LENGTH;
        double ux = st.dockDirX / 1000.0, uz = st.dockDirZ / 1000.0;
        double px = -uz, pz = ux; // perpendicular
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
        int y = WorldPlan.SEA_LEVEL + 1;
        for (int i = -4; i <= len; i++) {
            for (int w = -2; w <= 2; w++) {
                int x = (int) Math.round(st.dockX + ux * i + px * w);
                int z = (int) Math.round(st.dockZ + uz * i + pz * w);
                if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                sink.set(x, y, z, plank);
                for (int k = 1; k <= 3; k++) sink.set(x, y + k, z, AIR);
                if ((w == -2 || w == 2) && i % 4 == 0) {
                    for (int d = y - 1; d > y - 14; d--) {
                        BlockState cur = sink.get(x, d, z);
                        if (cur != null && !cur.isAir() && !cur.liquid()) break;
                        sink.set(x, d, z, post);
                    }
                    sink.set(x, y + 1, z, fence);
                    if (i % 8 == 0) sink.set(x, y + 2, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }

    // ------------------------------------------------------------------ misc

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {}

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        net.minecraft.world.level.ChunkPos pos = level.getCenter();
        Holder<Biome> biome = level.getBiome(pos.getWorldPosition().atY(level.getMaxBuildHeight() - 1));
        WorldgenRandom r = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        r.setDecorationSeed(level.getSeed(), pos.getMinBlockX(), pos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(level, biome, pos, r);
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager) {
        // vanilla structures are disabled in the ocean world; settlements are generated by the world plan
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return WorldPlan.SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        WorldPlan.Sample s = new WorldPlan.Sample();
        plan(random).sample(x, z, s);
        int h = s.height;
        if (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG) return h + 1;
        if (kind == WorldPlan.Kind.DEEP && com.grandline.world.DeepWorld.domeAt(x, z) == null) return com.grandline.world.DeepWorld.WATER_TOP + 1;
        return Math.max(h, Math.max(s.waterTop, WorldPlan.SEA_LEVEL)) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        WorldPlan.Sample s = new WorldPlan.Sample();
        plan(random).sample(x, z, s);
        int min = level.getMinBuildHeight();
        BlockState[] states = new BlockState[level.getHeight()];
        int waterTop = Math.max(s.waterTop, WorldPlan.SEA_LEVEL);
        for (int i = 0; i < states.length; i++) {
            int y = min + i;
            if (kind == WorldPlan.Kind.DEEP)
                states[i] = y <= s.height ? STONE : com.grandline.world.DeepWorld.inAnyDome(x, y, z) ? AIR : y <= com.grandline.world.DeepWorld.WATER_TOP ? WATER : AIR;
            else if (kind == WorldPlan.Kind.SKY && y < com.grandline.world.SkyWorld.FLOOR_Y - 1) states[i] = AIR;
            else states[i] = y <= s.height ? STONE : y <= waterTop ? WATER : AIR;
        }
        return new NoiseColumn(min, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        WorldPlan plan = plan(random);
        WorldPlan.Sample s = new WorldPlan.Sample();
        plan.sample(pos.getX(), pos.getZ(), s);
        info.add("Grand Line: zone=" + s.zone.id() + (s.island != null ? " island=" + s.island.id : ""));
    }
}
