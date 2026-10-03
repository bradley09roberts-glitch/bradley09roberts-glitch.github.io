package com.terracraft.world.jungle;

import com.terracraft.registry.content.JungleContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Turns Minecraft jungles into Terraria's Jungle, one chunk at a time:
 * <ul>
 *     <li>under every jungle column the dirt becomes mud and most stone down to the caverns becomes mud
 *     (Terraria's Underground Jungle), with stone left in patches;</li>
 *     <li>mud that faces open air grows Jungle Grass, and cave grass sprouts glowing Jungle Spores;</li>
 *     <li>Bee Hives (shells of hive blocks with honey and a Larva inside) are buried in the underground jungle.</li>
 * </ul>
 * Columns and hives are decided from the biome layout (not the chunk), so neighbouring chunks agree.
 */
public class JungleFeature extends Feature<NoneFeatureConfiguration> {
    private static final int HIVE_CELL = 56;
    private static final int MUD_BOTTOM = -24;

    public JungleFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ServerLevel server = level.getLevel();
        ChunkPos chunk = ChunkPos.containing(context.origin());
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        long seed = level.getSeed();
        BlockState mud = Blocks.MUD.defaultBlockState();
        BlockState grass = JungleContent.JUNGLE_GRASS.get().defaultBlockState();
        BlockState spores = JungleContent.JUNGLE_SPORES.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        boolean[][] jungle = new boolean[16][16];
        boolean any = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                jungle[dx][dz] = isJungle(server, minX + dx, minZ + dz);
                any |= jungle[dx][dz];
            }
        }
        if (!any) {
            return false;
        }
        int bottom = Math.max(level.getMinY() + 2, MUD_BOTTOM);
        // 1. mud
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                if (!jungle[dx][dz]) {
                    continue;
                }
                int x = minX + dx;
                int z = minZ + dz;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                for (int y = surface; y >= bottom; y--) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    boolean nearSurface = y > surface - 6;
                    if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                        || !nearSurface && (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL)) && mudCell(seed, x, y, z)) {
                        level.setBlock(pos, mud, Block.UPDATE_CLIENTS);
                        changed = true;
                    } else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)) {
                        level.setBlock(pos, grass, Block.UPDATE_CLIENTS);
                        changed = true;
                    }
                }
            }
        }
        // 2. jungle grass on exposed mud (cave floors, walls and ceilings) and spores on cave floors
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                if (!jungle[dx][dz]) {
                    continue;
                }
                int x = minX + dx;
                int z = minZ + dz;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                for (int y = surface - 4; y >= bottom; y--) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).is(Blocks.MUD) || !exposed(level, pos, minX, minZ)) {
                        continue;
                    }
                    level.setBlock(pos, grass, Block.UPDATE_CLIENTS);
                    changed = true;
                    BlockPos above = pos.above();
                    if (level.getBlockState(above).isAir() && hash(seed, x, y, z) % 14 == 0) {
                        level.setBlock(above, spores, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        // 3. bee hives
        int cellMinX = Math.floorDiv(minX - 12, HIVE_CELL);
        int cellMaxX = Math.floorDiv(minX + 27, HIVE_CELL);
        int cellMinZ = Math.floorDiv(minZ - 12, HIVE_CELL);
        int cellMaxZ = Math.floorDiv(minZ + 27, HIVE_CELL);
        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
            for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
                changed |= hive(level, server, seed, cx, cz, minX, minZ, pos);
            }
        }
        return changed;
    }

    /** Whether a column belongs to the Jungle (decided from the biome source, so it is chunk independent). */
    public static boolean isJungle(ServerLevel level, int x, int z) {
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        Holder<Biome> biome = source.getNoiseBiome(x >> 2, 16, z >> 2, sampler);
        return biome.is(BiomeTags.IS_JUNGLE);
    }

    private static boolean mudCell(long seed, int x, int y, int z) {
        // mostly mud with stone left in 4x4x4 patches
        return hash(seed, x >> 2, (y >> 2) + 4000, z >> 2) % 10 < 7;
    }

    private static boolean exposed(WorldGenLevel level, BlockPos pos, int minX, int minZ) {
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.relative(direction);
            if (side.getX() < minX || side.getX() > minX + 15 || side.getZ() < minZ || side.getZ() > minZ + 15) {
                continue;
            }
            if (level.getBlockState(side).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hive(WorldGenLevel level, ServerLevel server, long seed, int cellX, int cellZ, int minX, int minZ,
                                BlockPos.MutableBlockPos pos) {
        long h = hash(seed, cellX, 9191, cellZ);
        if (h % 3 != 0) {
            return false;
        }
        int hx = cellX * HIVE_CELL + 12 + (int) ((h >>> 4) % (HIVE_CELL - 24));
        int hz = cellZ * HIVE_CELL + 12 + (int) ((h >>> 12) % (HIVE_CELL - 24));
        int hy = 4 + (int) ((h >>> 20) % 30);
        if (!isJungle(server, hx, hz)) {
            return false;
        }
        double radius = 6.0 + (h >>> 28) % 3;
        if (hx + radius + 3 < minX || hx - radius - 3 > minX + 15 || hz + radius + 3 < minZ || hz - radius - 3 > minZ + 15) {
            return false;
        }
        BlockState hive = JungleContent.HIVE.get().defaultBlockState();
        BlockState honey = Blocks.HONEY_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean changed = false;
        for (int x = Math.max(minX, hx - 11); x <= Math.min(minX + 15, hx + 11); x++) {
            for (int z = Math.max(minZ, hz - 11); z <= Math.min(minZ + 15, hz + 11); z++) {
                for (int y = hy - 9; y <= hy + 9; y++) {
                    // slightly flattened sphere with a wobbly shell
                    double d = Math.sqrt(Mth.square(x - hx) + Mth.square((y - hy) * 1.3) + Mth.square(z - hz));
                    double wobble = (hash(seed, x, y, z) % 100) / 100.0 * 0.8;
                    if (d > radius + 1.2 + wobble) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
                        continue;
                    }
                    BlockState state;
                    if (d > radius - 1.0) {
                        state = hive;
                    } else if (y <= hy - 3) {
                        state = honey;               // honey pool at the bottom
                    } else {
                        state = air;
                    }
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    changed = true;
                }
            }
        }
        BlockPos larva = new BlockPos(hx, hy - 2, hz);
        if (larva.getX() >= minX && larva.getX() <= minX + 15 && larva.getZ() >= minZ && larva.getZ() <= minZ + 15) {
            level.setBlock(larva, JungleContent.LARVA.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return changed;
    }

    static long hash(long seed, int a, int b, int c) {
        long h = seed ^ 0x2545F4914F6CDD1DL;
        h = (h ^ a) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 31) ^ b) * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 29) ^ c) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }
}
