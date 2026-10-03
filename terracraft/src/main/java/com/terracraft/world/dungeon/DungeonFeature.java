package com.terracraft.world.dungeon;

import com.terracraft.TerraCraft;
import com.terracraft.block.LockedChestBlock;
import com.terracraft.registry.content.DungeonContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Builds the part of the world's Dungeon ({@link DungeonLayout}) that lies in the current chunk: a two-block
 * brick shell around every hall, then the air inside, the entrance tower with its doorway, porch and
 * battlements, and finally ladders, chests, spikes, bookshelves, lanterns and cobwebs. Writes only inside its
 * own chunk, so chunks can be generated in any order.
 */
public class DungeonFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceKey<LootTable> CHEST_LOOT = ResourceKey.create(Registries.LOOT_TABLE, TerraCraft.id("chests/dungeon"));

    public DungeonFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        DungeonLayout layout = DungeonManager.layout(level.getLevel());
        ChunkPos chunk = ChunkPos.containing(context.origin());
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int[] bounds = layout.bounds();
        if (maxX < bounds[0] || minX > bounds[2] || maxZ < bounds[1] || minZ > bounds[3]) {
            return false;
        }
        BlockState brick = DungeonContent.brick(layout.brick());
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        int shell = DungeonLayout.SHELL;

        // 1. shells
        for (DungeonLayout.Box box : layout.boxes()) {
            if (!box.touchesColumn(minX, minZ, maxX, maxZ, shell)) {
                continue;
            }
            changed |= fill(level, pos, box.x0() - shell, box.y0() - shell, box.z0() - shell, box.x1() + shell, box.y1() + shell, box.z1() + shell,
                minX, minZ, maxX, maxZ, brick);
        }
        // tower foundation down to solid ground, so the entrance never floats over a slope
        DungeonLayout.Box tower = layout.boxes().get(0);
        for (int x = Math.max(minX, tower.x0() - shell); x <= Math.min(maxX, tower.x1() + shell); x++) {
            for (int z = Math.max(minZ, tower.z0() - shell); z <= Math.min(maxZ, tower.z1() + shell); z++) {
                for (int y = tower.y0() - shell - 1; y > tower.y0() - 24; y--) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isSolid() && state.getFluidState().isEmpty()) {
                        break;
                    }
                    level.setBlock(pos, brick, Block.UPDATE_CLIENTS);
                    changed = true;
                }
            }
        }
        // 2. interiors
        for (DungeonLayout.Box box : layout.boxes()) {
            if (box.touchesColumn(minX, minZ, maxX, maxZ, 0)) {
                changed |= fill(level, pos, box.x0(), box.y0(), box.z0(), box.x1(), box.y1(), box.z1(), minX, minZ, maxX, maxZ, air);
            }
        }
        // 3. entrance: doorway, porch toward spawn and battlements on the roof
        changed |= entrance(level, layout, tower, brick, pos, minX, minZ, maxX, maxZ);
        // 4. furniture and traps
        for (DungeonLayout.Placement placement : layout.placements()) {
            BlockPos at = placement.pos();
            if (at.getX() < minX || at.getX() > maxX || at.getZ() < minZ || at.getZ() > maxZ) {
                continue;
            }
            changed |= decorate(level, context, placement, brick);
        }
        return changed;
    }

    private static boolean fill(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x0, int y0, int z0, int x1, int y1, int z1,
                                int minX, int minZ, int maxX, int maxZ, BlockState state) {
        boolean changed = false;
        int bottom = Math.max(level.getMinY() + 1, y0);
        int top = Math.min(level.getMaxY() - 1, y1);
        for (int x = Math.max(minX, x0); x <= Math.min(maxX, x1); x++) {
            for (int z = Math.max(minZ, z0); z <= Math.min(maxZ, z1); z++) {
                for (int y = bottom; y <= top; y++) {
                    pos.set(x, y, z);
                    BlockState current = level.getBlockState(pos);
                    if (current.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean entrance(WorldGenLevel level, DungeonLayout layout, DungeonLayout.Box tower, BlockState brick, BlockPos.MutableBlockPos pos,
                                    int minX, int minZ, int maxX, int maxZ) {
        boolean changed = false;
        Direction door = layout.doorFacing();
        Direction across = door.getClockWise();
        int cx = layout.centerX();
        int cz = layout.centerZ();
        int floor = tower.y0();
        BlockState air = Blocks.AIR.defaultBlockState();
        // doorway through the wall (3 wide, 4 tall) and a porch 8 blocks out, cleared and floored
        for (int out = 5; out <= 14; out++) {
            for (int side = -2; side <= 2; side++) {
                int x = cx + door.getStepX() * out + across.getStepX() * side;
                int z = cz + door.getStepZ() * out + across.getStepZ() * side;
                if (x < minX || x > maxX || z < minZ || z > maxZ) {
                    continue;
                }
                boolean wall = out <= 6;
                if (wall && Math.abs(side) == 2) {
                    continue;
                }
                for (int y = floor; y <= floor + (wall ? 3 : 5); y++) {
                    level.setBlock(pos.set(x, y, z), air, Block.UPDATE_CLIENTS);
                }
                level.setBlock(pos.set(x, floor - 1, z), brick, Block.UPDATE_CLIENTS);
                for (int y = floor - 2; y > floor - 10; y--) {
                    BlockState below = level.getBlockState(pos.set(x, y, z));
                    if (below.isSolid() && below.getFluidState().isEmpty()) {
                        break;
                    }
                    level.setBlock(pos, brick, Block.UPDATE_CLIENTS);
                }
                changed = true;
            }
        }
        // clear trees crowding the entrance (they were placed before the Dungeon)
        for (int x = Math.max(minX, cx - 10); x <= Math.min(maxX, cx + 10); x++) {
            for (int z = Math.max(minZ, cz - 10); z <= Math.min(maxZ, cz + 10); z++) {
                for (int y = floor - 3; y <= floor + 24; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.VINE)) {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                        changed = true;
                    }
                }
            }
        }
        // battlements: every other block around the roof edge
        int roof = tower.y1() + DungeonLayout.SHELL + 1;
        for (int x = Math.max(minX, tower.x0() - 2); x <= Math.min(maxX, tower.x1() + 2); x++) {
            for (int z = Math.max(minZ, tower.z0() - 2); z <= Math.min(maxZ, tower.z1() + 2); z++) {
                boolean edge = x == tower.x0() - 2 || x == tower.x1() + 2 || z == tower.z0() - 2 || z == tower.z1() + 2;
                if (edge && (x + z) % 2 == 0) {
                    level.setBlock(pos.set(x, roof, z), brick, Block.UPDATE_CLIENTS);
                    changed = true;
                }
            }
        }
        // windows on the side walls
        for (Direction side : new Direction[]{across, across.getOpposite()}) {
            for (int along = -2; along <= 2; along += 4) {
                int x = cx + side.getStepX() * 5 + door.getStepX() * along;
                int z = cz + side.getStepZ() * 5 + door.getStepZ() * along;
                for (int t = 0; t < 2; t++) {
                    int wx = x + side.getStepX() * t;
                    int wz = z + side.getStepZ() * t;
                    if (wx >= minX && wx <= maxX && wz >= minZ && wz <= maxZ) {
                        level.setBlock(pos.set(wx, floor + 4, wz), Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_CLIENTS);
                        level.setBlock(pos.set(wx, floor + 5, wz), Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_CLIENTS);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static boolean decorate(WorldGenLevel level, FeaturePlaceContext<NoneFeatureConfiguration> context, DungeonLayout.Placement placement,
                                    BlockState brick) {
        BlockPos pos = placement.pos();
        switch (placement.decor()) {
            case LADDER -> level.setBlock(pos, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, placement.facing()), Block.UPDATE_CLIENTS);
            case CHEST -> {
                level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, placement.facing()), Block.UPDATE_CLIENTS);
                RandomizableContainer.setBlockEntityLootTable(level, context.random(), pos, CHEST_LOOT);
            }
            case LOCKED_CHEST -> level.setBlock(pos, DungeonContent.LOCKED_GOLD_CHEST.get().defaultBlockState()
                .setValue(LockedChestBlock.FACING, placement.facing()), Block.UPDATE_CLIENTS);
            case SPIKES -> {
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, DungeonContent.SPIKES.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            case BOOKSHELF -> level.setBlock(pos, Blocks.BOOKSHELF.defaultBlockState(), Block.UPDATE_CLIENTS);
            case LANTERN -> level.setBlock(pos, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), Block.UPDATE_CLIENTS);
            case COBWEB -> {
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return true;
    }
}
