package com.terracraft.world.biome;

import com.terracraft.TerraCraft;
import com.terracraft.registry.content.BiomeContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Floating Islands: high in the sky (Space), one in most 320-block cells. Each is a lens of Cloud capped with grass
 * and dirt, with a small Sunplate house holding a Skyware chest (Starfury, Shiny Red Balloon, Lucky Horseshoe,
 * Fledgling Wings...). Harpies live up here.
 */
public class SkyIslandFeature extends Feature<NoneFeatureConfiguration> {
    private static final int CELL = 320;
    private static final ResourceKey<LootTable> SKYWARE = ResourceKey.create(Registries.LOOT_TABLE, TerraCraft.id("chests/skyware"));

    public record Island(int x, int y, int z, int radius) {}

    public SkyIslandFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** The island of a cell, or null when that cell has none. */
    public static Island island(long seed, int cellX, int cellZ) {
        long h = WorldNoise.hash(seed + 4441, cellX, 77, cellZ);
        if (h % 10 >= 7) {
            return null;
        }
        int x = cellX * CELL + 40 + (int) ((h >>> 8) % (CELL - 80));
        int z = cellZ * CELL + 40 + (int) ((h >>> 20) % (CELL - 80));
        int y = 228 + (int) ((h >>> 32) % 16);
        int radius = 13 + (int) ((h >>> 40) % 7);
        return new Island(x, y, z, radius);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (level.getMaxY() < 260) {
            return false;
        }
        ChunkPos chunk = ChunkPos.containing(context.origin());
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        long seed = level.getSeed();
        boolean changed = false;
        for (int cx = Math.floorDiv(minX - 40, CELL); cx <= Math.floorDiv(minX + 56, CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - 40, CELL); cz <= Math.floorDiv(minZ + 56, CELL); cz++) {
                Island island = island(seed, cx, cz);
                if (island != null && island.x() + island.radius() + 2 >= minX && island.x() - island.radius() - 2 <= minX + 15
                    && island.z() + island.radius() + 2 >= minZ && island.z() - island.radius() - 2 <= minZ + 15) {
                    changed |= build(level, seed, island, minX, minZ);
                }
            }
        }
        return changed;
    }

    private boolean build(WorldGenLevel level, long seed, Island island, int minX, int minZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState cloud = BiomeContent.CLOUD.get().defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        int r = island.radius();
        boolean changed = false;
        for (int x = Math.max(minX, island.x() - r - 2); x <= Math.min(minX + 15, island.x() + r + 2); x++) {
            for (int z = Math.max(minZ, island.z() - r - 2); z <= Math.min(minZ + 15, island.z() + r + 2); z++) {
                double wobble = (WorldNoise.smooth(seed, x, 0, z, 6) - 0.5) * 4;
                double d = Math.sqrt(Math.pow(x - island.x(), 2) + Math.pow(z - island.z(), 2)) / (r + wobble);
                if (d >= 1.0) {
                    continue;
                }
                int top = island.y() + (int) Math.round(2 * (1 - d));
                int depth = (int) Math.round(Math.pow(1 - d * d, 0.7) * (r * 0.55) + WorldNoise.smooth(seed, x, 5, z, 4) * 3);
                for (int y = top - depth; y <= top; y++) {
                    BlockState state = d > 0.86 ? cloud : y == top ? grass : y >= top - 3 ? dirt : cloud;
                    level.setBlock(pos.set(x, y, z), state, Block.UPDATE_CLIENTS);
                    changed = true;
                }
            }
        }
        changed |= house(level, island, minX, minZ);
        return changed;
    }

    /** A 9x7 Sunplate hut with glass windows, a south door and the Skyware chest. */
    private boolean house(WorldGenLevel level, Island island, int minX, int minZ) {
        int floor = island.y() + 2;
        int x0 = island.x() - 4;
        int x1 = island.x() + 4;
        int z0 = island.z() - 3;
        int z1 = island.z() + 3;
        BlockState plate = BiomeContent.SUNPLATE_BLOCK.get().defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean changed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x < minX || x > minX + 15 || z < minZ || z > minZ + 15) {
                    continue;
                }
                for (int y = floor; y <= floor + 5; y++) {
                    boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                    BlockState state;
                    if (y == floor || y == floor + 5) {
                        state = plate;
                    } else if (wall) {
                        boolean door = z == z1 && x == island.x() && y <= floor + 2;
                        boolean window = y == floor + 2 && (x == x0 || x == x1) && Math.abs(z - island.z()) == 1;
                        state = door ? air : window ? glass : plate;
                    } else {
                        state = air;
                    }
                    level.setBlock(pos.set(x, y, z), state, Block.UPDATE_CLIENTS);
                    changed = true;
                }
            }
        }
        BlockPos chest = new BlockPos(island.x(), floor + 1, island.z() - 1);
        if (chest.getX() >= minX && chest.getX() <= minX + 15 && chest.getZ() >= minZ && chest.getZ() <= minZ + 15) {
            level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest, SKYWARE);
            level.setBlock(chest.above(2), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true),
                Block.UPDATE_CLIENTS);
        }
        return changed;
    }
}
