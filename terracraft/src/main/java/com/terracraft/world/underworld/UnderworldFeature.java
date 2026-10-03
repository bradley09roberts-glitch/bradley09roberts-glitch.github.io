package com.terracraft.world.underworld;

import com.terracraft.block.LockedChestBlock;
import com.terracraft.registry.content.UnderworldContent;
import com.terracraft.world.TerrariaLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Terraria's Underworld at the bottom of the overworld (below {@link TerrariaLayer#UNDERWORLD_START}): one huge
 * open cavern between an ash floor and an ash ceiling, a lava sea in the low spots, Hellstone veins in the ash
 * and ruined obsidian houses holding Hellforges and Shadow Chests. Heights come from smooth seeded noise, so
 * every chunk is generated on its own and still lines up with its neighbours.
 */
public class UnderworldFeature extends Feature<NoneFeatureConfiguration> {
    public static final int TOP = TerrariaLayer.UNDERWORLD_START + 4;
    public static final int LAVA_LEVEL = -57;
    private static final int HOUSE_CELL = 48;

    public UnderworldFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** Top of the ash floor at a column (somewhere between y -62 and -53). */
    public static int floor(long seed, int x, int z) {
        return -58 + (int) Math.round(noise(seed, x, z, 24, 11) * 4.0 + noise(seed, x, z, 7, 12) * 1.2);
    }

    /** Bottom of the ash ceiling at a column (somewhere between y -48 and -40). */
    public static int ceiling(long seed, int x, int z) {
        return -44 + (int) Math.round(noise(seed, x, z, 20, 13) * 3.5 + noise(seed, x, z, 6, 14) * 1.0);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        long seed = level.getSeed();
        ChunkPos chunk = ChunkPos.containing(context.origin());
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        int bottom = level.getMinY();
        BlockState ash = UnderworldContent.ASH.get().defaultBlockState();
        BlockState hellstone = UnderworldContent.HELLSTONE.get().defaultBlockState();
        BlockState lava = Blocks.LAVA.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;
                int floor = floor(seed, x, z);
                int ceiling = ceiling(seed, x, z);
                // the ash ceiling blends into the rock above it
                int top = TOP + (int) (hash(seed, x, 0, z) % 3);
                for (int y = bottom; y <= top; y++) {
                    pos.set(x, y, z);
                    BlockState current = level.getBlockState(pos);
                    if (current.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    BlockState state;
                    if (y <= floor || y >= ceiling) {
                        boolean vein = hash(seed, x >> 1, (y >> 1) + 777, z >> 1) % 100 < 6 && y < floor - 1;
                        state = vein ? hellstone : ash;
                        if (y >= ceiling && current.isAir() && y > ceiling + 2) {
                            continue;   // keep open caves above the ceiling crust
                        }
                    } else if (y <= LAVA_LEVEL) {
                        state = lava;
                    } else {
                        state = air;
                    }
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                }
                // occasional lavafalls from the ceiling
                if (hash(seed, x, 31, z) % 900 == 0) {
                    level.setBlock(pos.set(x, ceiling, z), lava, Block.UPDATE_CLIENTS);
                }
            }
        }
        // ruined houses
        for (int cx = Math.floorDiv(minX - 12, HOUSE_CELL); cx <= Math.floorDiv(minX + 27, HOUSE_CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - 12, HOUSE_CELL); cz <= Math.floorDiv(minZ + 27, HOUSE_CELL); cz++) {
                house(level, seed, cx, cz, minX, minZ, pos, context);
            }
        }
        return true;
    }

    private static void house(WorldGenLevel level, long seed, int cellX, int cellZ, int minX, int minZ, BlockPos.MutableBlockPos pos,
                              FeaturePlaceContext<NoneFeatureConfiguration> context) {
        long h = hash(seed, cellX, 4242, cellZ);
        if (h % 5 >= 2) {
            return;
        }
        int hx = cellX * HOUSE_CELL + 12 + (int) ((h >>> 8) % (HOUSE_CELL - 24));
        int hz = cellZ * HOUSE_CELL + 12 + (int) ((h >>> 16) % (HOUSE_CELL - 24));
        int floorY = Math.max(LAVA_LEVEL + 1, floor(seed, hx, hz) + 1);
        int halfX = 4 + (int) ((h >>> 24) % 3);
        int halfZ = 3 + (int) ((h >>> 28) % 2);
        int height = 5;
        if (hx + halfX + 1 < minX || hx - halfX - 1 > minX + 15 || hz + halfZ + 1 < minZ || hz - halfZ - 1 > minZ + 15) {
            return;
        }
        BlockState brick = UnderworldContent.OBSIDIAN_BRICK.get().defaultBlockState();
        BlockState floorBrick = UnderworldContent.HELLSTONE_BRICK.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = Math.max(minX, hx - halfX - 1); x <= Math.min(minX + 15, hx + halfX + 1); x++) {
            for (int z = Math.max(minZ, hz - halfZ - 1); z <= Math.min(minZ + 15, hz + halfZ + 1); z++) {
                for (int y = floorY - 1; y <= floorY + height; y++) {
                    boolean wall = Math.abs(x - hx) == halfX + 1 || Math.abs(z - hz) == halfZ + 1;
                    boolean floorOrRoof = y == floorY - 1 || y == floorY + height;
                    pos.set(x, y, z);
                    if (wall || floorOrRoof) {
                        // ruined: some bricks are missing, more of them higher up
                        boolean missing = !floorOrRoof && y > floorY + 1 && hash(seed, x, y, z) % 10 < 2
                            || y == floorY + height && hash(seed, x, y + 99, z) % 10 < 3;
                        boolean door = wall && Math.abs(z - hz) <= 0 && y <= floorY + 2 && y >= floorY && Math.abs(x - hx) == halfX + 1;
                        level.setBlock(pos, missing || door ? air : y == floorY - 1 ? floorBrick : brick, Block.UPDATE_CLIENTS);
                    } else {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        // a Hellforge in some houses, a Shadow Chest in most
        BlockPos forge = new BlockPos(hx + halfX - 1, floorY, hz - halfZ + 1);
        if ((h >>> 32) % 3 == 0 && inChunk(forge, minX, minZ)) {
            level.setBlock(forge, UnderworldContent.HELLFORGE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockPos chest = new BlockPos(hx - halfX + 1, floorY, hz + halfZ - 1);
        if ((h >>> 36) % 4 != 0 && inChunk(chest, minX, minZ)) {
            level.setBlock(chest, UnderworldContent.LOCKED_SHADOW_CHEST.get().defaultBlockState().setValue(LockedChestBlock.FACING, Direction.NORTH),
                Block.UPDATE_CLIENTS);
        }
    }

    private static boolean inChunk(BlockPos pos, int minX, int minZ) {
        return pos.getX() >= minX && pos.getX() <= minX + 15 && pos.getZ() >= minZ && pos.getZ() <= minZ + 15;
    }

    /** Smooth value noise in [-1, 1] on a grid of {@code scale} blocks. */
    static double noise(long seed, int x, int z, int scale, int salt) {
        int gx = Math.floorDiv(x, scale);
        int gz = Math.floorDiv(z, scale);
        double fx = (x - gx * (double) scale) / scale;
        double fz = (z - gz * (double) scale) / scale;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = corner(seed, gx, gz, salt);
        double b = corner(seed, gx + 1, gz, salt);
        double c = corner(seed, gx, gz + 1, salt);
        double d = corner(seed, gx + 1, gz + 1, salt);
        return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static double corner(long seed, int x, int z, int salt) {
        return (hash(seed, x, salt, z) % 2001) / 1000.0 - 1.0;
    }

    static long hash(long seed, int a, int b, int c) {
        long h = seed ^ 0x6A09E667F3BCC908L;
        h = (h ^ a) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 31) ^ b) * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 29) ^ c) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }
}
