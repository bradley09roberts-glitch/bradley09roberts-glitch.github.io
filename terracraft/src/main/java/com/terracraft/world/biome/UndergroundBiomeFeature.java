package com.terracraft.world.biome;

import com.terracraft.registry.content.BiomeContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
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
import net.neoforged.neoforge.common.Tags;

/**
 * Terraria's underground biomes under Minecraft's surface ones:
 * <ul>
 *     <li><b>Underground Desert</b> under deserts: the rock becomes sandstone and hardened sand, with wide caverns
 *     carved through it and Desert Fossil veins (Sturdy Fossils).</li>
 *     <li><b>Ice caverns</b> under the Snow biome: the rock becomes packed ice, ice and snow, with caverns.</li>
 * </ul>
 * Everything is decided per block from the seed, so neighbouring chunks agree.
 */
public class UndergroundBiomeFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { DESERT, SNOW }

    private final Kind kind;

    public UndergroundBiomeFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    private TagKey<Biome> biomeTag() {
        return kind == Kind.DESERT ? Tags.Biomes.IS_DESERT : Tags.Biomes.IS_SNOWY;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ServerLevel server = level.getLevel();
        ChunkPos chunk = ChunkPos.containing(context.origin());
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        long seed = level.getSeed() + (kind == Kind.DESERT ? 7151 : 9283);
        boolean[][] inside = new boolean[16][16];
        boolean any = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                inside[dx][dz] = WorldNoise.biomeIs(server, minX + dx, minZ + dz, biomeTag());
                any |= inside[dx][dz];
            }
        }
        if (!any) {
            return false;
        }
        int bottom = Math.max(level.getMinY() + 4, kind == Kind.DESERT ? -16 : -24);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean changed = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                if (!inside[dx][dz]) {
                    continue;
                }
                int x = minX + dx;
                int z = minZ + dz;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                for (int y = surface - 5; y >= bottom; y--) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!convertible(state)) {
                        continue;
                    }
                    // wide winding caverns from about 14 blocks down
                    double cave = WorldNoise.smooth(seed, x, y * 2, z, 14);
                    if (y < surface - 14 && y > bottom + 3 && cave > 0.68) {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    } else {
                        level.setBlock(pos, rock(seed, x, y, z), Block.UPDATE_CLIENTS);
                    }
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean convertible(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.COARSE_DIRT)
            || state.is(Blocks.CLAY) || state.is(Blocks.SAND);
    }

    private BlockState rock(long seed, int x, int y, int z) {
        long h = WorldNoise.hash(seed, x >> 2, y >> 2, z >> 2) % 100;
        if (kind == Kind.DESERT) {
            if (WorldNoise.hash(seed + 1, x >> 1, y >> 1, z >> 1) % 90 == 0) {
                return BiomeContent.DESERT_FOSSIL.get().defaultBlockState();
            }
            return h < 30 ? BiomeContent.HARDENED_SAND.get().defaultBlockState()
                : h < 38 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
        }
        return h < 45 ? Blocks.PACKED_ICE.defaultBlockState() : h < 55 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
    }
}
