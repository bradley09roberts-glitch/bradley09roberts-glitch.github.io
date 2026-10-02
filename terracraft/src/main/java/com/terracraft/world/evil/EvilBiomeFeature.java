package com.terracraft.world.evil;

import com.terracraft.progression.WorldVariants;
import com.terracraft.registry.content.EvilContent;
import com.terracraft.world.gen.WorldgenVariants;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the world's Corruption or Crimson, one chunk at a time (placed once per chunk, writes only inside
 * its own chunk). Inside {@link EvilZones}: grass and stone turn into the evil variants, Demonite/Crimtane Ore
 * is scattered underground, winding chasms drop from the surface into rooms holding Shadow Orbs or Crimson
 * Hearts, Demon/Crimson Altars stand on the surface and Vile/Vicious Mushrooms grow. Chasms and altars are
 * pure functions of the seed, so neighbouring chunks agree on their shapes.
 */
public class EvilBiomeFeature extends Feature<NoneFeatureConfiguration> {
    private static final int CHASM_CELL = 32;
    private static final int ALTAR_CELL = 40;
    private static final int TOP = 150;
    private static final int CONVERT_DEPTH = 45;

    record Chasm(double x, double z, int bottom, double phaseX, double phaseZ, double phaseR, boolean crimson,
                 List<double[]> rooms) {
        double centerX(int y) {
            return x + 3.0 * Math.sin(y * 0.13 + phaseX);
        }

        double centerZ(int y) {
            return z + 3.0 * Math.cos(y * 0.11 + phaseZ);
        }

        double radius(int y) {
            return crimson ? 3.2 + 1.6 * Math.sin(y * 0.21 + phaseR) : 2.2 + 0.9 * Math.sin(y * 0.3 + phaseR);
        }
    }

    public EvilBiomeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        long seed = level.getSeed();
        EvilZones.zones(seed, landTest(level.getLevel()));
        ChunkPos chunk = ChunkPos.containing(context.origin());
        if (!EvilZones.chunkNearZone(seed, chunk.x(), chunk.z())) {
            return false;
        }
        boolean crimson = WorldgenVariants.get(level).evil() == WorldVariants.WorldEvil.CRIMSON;
        BlockState stone = (crimson ? EvilContent.CRIMSTONE : EvilContent.EBONSTONE).get().defaultBlockState();
        BlockState grass = (crimson ? EvilContent.CRIMSON_GRASS : EvilContent.CORRUPT_GRASS).get().defaultBlockState();
        BlockState ore = (crimson ? EvilContent.CRIMTANE_ORE : EvilContent.DEMONITE_ORE).get().defaultBlockState();
        BlockState mushroom = (crimson ? EvilContent.VICIOUS_MUSHROOM : EvilContent.VILE_MUSHROOM).get().defaultBlockState();
        Block log = (crimson ? EvilContent.SHADEWOOD : EvilContent.EBONWOOD).get();
        BlockState leaves = (crimson ? EvilContent.SHADEWOOD_LEAVES : EvilContent.EBONWOOD_LEAVES).get().defaultBlockState();
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        boolean changed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 1. convert the surface and the rock below it
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                if (!EvilZones.isEvil(seed, x, z)) {
                    continue;
                }
                changed = true;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int bottom = Math.max(level.getMinY() + 1, Math.min(surface - CONVERT_DEPTH, 40));
                for (int y = surface; y >= bottom; y--) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    boolean top = y == surface || level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.above()).isSolid();
                    if (state.is(Blocks.GRASS_BLOCK) || top && (state.is(Blocks.PODZOL) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.DIRT)
                        || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MYCELIUM))) {
                        level.setBlock(pos, grass, Block.UPDATE_CLIENTS);
                        BlockState above = level.getBlockState(pos.above());
                        if (above.is(BlockTags.REPLACEABLE) || above.is(BlockTags.FLOWERS)) {
                            level.setBlock(pos.above(), hash(seed, x, y, z) % 24 == 0 ? mushroom : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    } else if (state.is(BlockTags.LOGS)) {
                        BlockState converted = log.defaultBlockState();
                        if (state.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)) {
                            converted = converted.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                                state.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS));
                        }
                        level.setBlock(pos, converted, Block.UPDATE_CLIENTS);
                    } else if (state.is(BlockTags.LEAVES)) {
                        level.setBlock(pos, leaves, Block.UPDATE_CLIENTS);
                    } else if (isRock(state)) {
                        // ore in small 2x2x2 clusters (~15 blocks per chunk)
                        boolean oreHere = y < surface - 6 && hash(seed, x >> 1, (y >> 1) + 9000, z >> 1) % 10000 < 15;
                        level.setBlock(pos, oreHere ? ore : stone, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        // 2. chasms (from cells whose shafts may reach into this chunk)
        int cellMinX = Math.floorDiv(minX - 24, CHASM_CELL);
        int cellMaxX = Math.floorDiv(minX + 16 + 24, CHASM_CELL);
        int cellMinZ = Math.floorDiv(minZ - 24, CHASM_CELL);
        int cellMaxZ = Math.floorDiv(minZ + 16 + 24, CHASM_CELL);
        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
            for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
                Chasm chasm = chasm(seed, cx, cz, crimson);
                if (chasm != null && carve(level, chunk, chasm, stone, pos)) {
                    changed = true;
                }
            }
        }

        // 3. altars on the surface
        int altarMinX = Math.floorDiv(minX, ALTAR_CELL);
        int altarMinZ = Math.floorDiv(minZ, ALTAR_CELL);
        for (int ax = altarMinX; ax <= Math.floorDiv(minX + 15, ALTAR_CELL); ax++) {
            for (int az = altarMinZ; az <= Math.floorDiv(minZ + 15, ALTAR_CELL); az++) {
                long h = hash(seed, ax, 777, az);
                int x = ax * ALTAR_CELL + 6 + (int) (h % 28);
                int z = az * ALTAR_CELL + 6 + (int) ((h >>> 8) % 28);
                if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16 || (h >>> 16) % 3 == 0 || !EvilZones.isEvil(seed, x, z)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                pos.set(x, y - 1, z);
                if (level.getBlockState(pos).isFaceSturdy(level, pos, net.minecraft.core.Direction.UP) && level.getBlockState(pos.above()).isAir()) {
                    level.setBlock(pos.above(), (crimson ? EvilContent.CRIMSON_ALTAR : EvilContent.DEMON_ALTAR).get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Land test from the world's biome source (no chunk access, safe on generation threads). */
    public static EvilZones.LandTest landTest(net.minecraft.server.level.ServerLevel level) {
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        return (x, z) -> {
            var biome = source.getNoiseBiome(x >> 2, 16, z >> 2, sampler);
            return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH)
                && !biome.is(BiomeTags.IS_MOUNTAIN);
        };
    }

    private static boolean isRock(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE);
    }

    /** The chasm of a grid cell, or null when the cell has none. */
    static Chasm chasm(long seed, int cellX, int cellZ, boolean crimson) {
        long h = hash(seed, cellX, 31337, cellZ);
        if (h % 10 < 3) {
            return null;
        }
        double x = cellX * CHASM_CELL + 8 + (h >>> 4) % 16;
        double z = cellZ * CHASM_CELL + 8 + (h >>> 12) % 16;
        if (!EvilZones.isEvil(seed, x, z)) {
            return null;
        }
        int bottom = 18 + (int) ((h >>> 20) % 26);
        double px = ((h >>> 28) % 628) / 100.0;
        double pz = ((h >>> 36) % 628) / 100.0;
        double pr = ((h >>> 44) % 628) / 100.0;
        Chasm base = new Chasm(x, z, bottom, px, pz, pr, crimson, new ArrayList<>());
        // orb room at the bottom, plus a side pocket off the shaft
        base.rooms().add(new double[]{base.centerX(bottom), bottom + 3, base.centerZ(bottom), 4.2, 1});
        int branchY = bottom + 12 + (int) ((h >>> 52) % 18);
        double angle = ((h >>> 40) % 628) / 100.0;
        double bx = base.centerX(branchY) + Math.cos(angle) * 11;
        double bz = base.centerZ(branchY) + Math.sin(angle) * 11;
        base.rooms().add(new double[]{bx, branchY, bz, 3.2, (h >>> 58) % 2, base.centerX(branchY), base.centerZ(branchY)});
        return base;
    }

    private static boolean carve(WorldGenLevel level, ChunkPos chunk, Chasm chasm, BlockState stone, BlockPos.MutableBlockPos pos) {
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        if (chasm.x() + 14 < minX || chasm.x() - 14 > minX + 15 || chasm.z() + 14 < minZ || chasm.z() - 14 > minZ + 15) {
            // branch pockets reach at most ~16 blocks out
            if (chasm.x() + 20 < minX || chasm.x() - 20 > minX + 15 || chasm.z() + 20 < minZ || chasm.z() - 20 > minZ + 15) {
                return false;
            }
        }
        boolean changed = false;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState orb = (chasm.crimson() ? EvilContent.CRIMSON_HEART : EvilContent.SHADOW_ORB).get().defaultBlockState();
        int top = Math.min(TOP, level.getMaxY() - 1);
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                for (int y = chasm.bottom() - 6; y <= top; y++) {
                    double d2 = Mth.square(x - chasm.centerX(y)) + Mth.square(z - chasm.centerZ(y));
                    double r = chasm.radius(y);
                    boolean inside = y >= chasm.bottom() && d2 <= r * r;
                    boolean shell = !inside && y >= chasm.bottom() - 2 && d2 <= (r + 1.6) * (r + 1.6);
                    for (double[] room : chasm.rooms()) {
                        double rd2 = Mth.square(x - room[0]) + Mth.square(y - room[1]) + Mth.square(z - room[2]);
                        if (rd2 <= room[3] * room[3]) {
                            inside = true;
                        } else if (rd2 <= (room[3] + 1.6) * (room[3] + 1.6)) {
                            shell = true;
                        }
                        if (room.length > 5) {
                            // tunnel from the shaft to the pocket
                            double t = Mth.clamp(((x - room[5]) * (room[0] - room[5]) + (z - room[6]) * (room[2] - room[6]))
                                / (Mth.square(room[0] - room[5]) + Mth.square(room[2] - room[6]) + 1e-6), 0.0, 1.0);
                            double tx = room[5] + (room[0] - room[5]) * t;
                            double tz = room[6] + (room[2] - room[6]) * t;
                            double td2 = Mth.square(x - tx) + Mth.square(y - room[1]) + Mth.square(z - tz);
                            if (td2 <= 2.0 * 2.0) {
                                inside = true;
                            } else if (td2 <= 3.4 * 3.4) {
                                shell = true;
                            }
                        }
                    }
                    if (!inside && !shell) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    if (inside) {
                        if (!state.getFluidState().isEmpty()) {
                            continue;
                        }
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    } else if (isRock(state) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)) {
                        level.setBlock(pos, stone, Block.UPDATE_CLIENTS);
                    }
                    changed = true;
                }
            }
        }
        for (double[] room : chasm.rooms()) {
            int ox = Mth.floor(room[0]);
            int oz = Mth.floor(room[2]);
            if (room[4] > 0 && ox >= minX && ox < minX + 16 && oz >= minZ && oz < minZ + 16) {
                level.setBlock(pos.set(ox, Mth.floor(room[1]), oz), orb, Block.UPDATE_CLIENTS);
                changed = true;
            }
        }
        return changed;
    }

    /** Deterministic non-negative hash of a seed and three ints. */
    static long hash(long seed, int a, int b, int c) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        h = (h ^ a) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 31) ^ b) * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 29) ^ c) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }
}
