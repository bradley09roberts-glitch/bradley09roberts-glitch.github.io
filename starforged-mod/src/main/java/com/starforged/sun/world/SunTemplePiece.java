package com.starforged.sun.world;

import com.starforged.Starforged;
import com.starforged.registry.ModStructures;
import com.starforged.sun.SunBlocks;
import com.starforged.sun.SunEntities;
import com.starforged.sun.block.SunfireVentBlock;
import com.starforged.sun.entity.AshenKnightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Sun Temple: a stepped ziggurat of sunbaked brick rising from the Sunlands. Local coordinates: x/z in [0, 50]
 * with the centre at (25, 25); y = 0 is the plaza / hall floor.
 * <pre>
 *   y 13..   summit   - open-air arena, obelisks and the Sun Altar (the Sun Warden's battlefield)
 *   y  8..12 tier two - solid masonry around the stairwell, with a terrace walk on the tier below
 *   y  1..6  the Hall of Braziers - sunfire vent gauntlet, guardian knights, imp spawners, loot and four unlit braziers;
 *            lighting all four burns away the Sun Seal on the stairwell
 *   y -5..-1 a hidden vault under the cracked tiles in the middle of the hall
 * </pre>
 */
public class SunTemplePiece extends StructurePiece {
    public static final int CENTER = 25;
    private static final int SIZE = 51;
    private static final int HALL_R = 20;
    private static final int TIER0_R = 21;
    private static final int TIER1_R = 17;
    private static final int HALL_TOP = 6;
    private static final int SUMMIT = 13;
    private static final int SHAFT_X = CENTER;
    private static final int SHAFT_Z = CENTER - 12;

    public static final ResourceKey<LootTable> LOOT_COMMON = loot("chests/sun_temple_common");
    public static final ResourceKey<LootTable> LOOT_ARMORY = loot("chests/sun_temple_armory");
    public static final ResourceKey<LootTable> LOOT_VAULT = loot("chests/sun_temple_vault");

    private WorldGenLevel level;
    private BoundingBox chunkBox;
    private RandomSource random;

    public SunTemplePiece(int minX, int floorY, int minZ) {
        super(ModStructures.SUN_TEMPLE_PIECE.get(), 0, new BoundingBox(minX, floorY - 8, minZ, minX + SIZE - 1, floorY + 30, minZ + SIZE - 1));
        this.setOrientation(null);
    }

    public SunTemplePiece(CompoundTag tag) {
        super(ModStructures.SUN_TEMPLE_PIECE.get(), tag);
    }

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Starforged.id(path));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    private int ox() {
        return this.boundingBox.minX();
    }

    private int oy() {
        return this.boundingBox.minY() + 8;
    }

    private int oz() {
        return this.boundingBox.minZ();
    }

    private BlockPos at(int x, int y, int z) {
        return new BlockPos(this.ox() + x, this.oy() + y, this.oz() + z);
    }

    private void set(int x, int y, int z, BlockState state) {
        BlockPos pos = this.at(x, y, z);
        if (this.chunkBox.isInside(pos)) {
            this.level.setBlock(pos, state, 2);
        }
    }

    private BlockState get(int x, int y, int z) {
        BlockPos pos = this.at(x, y, z);
        return this.chunkBox.isInside(pos) ? this.level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    private static int ring(int x, int z) {
        return Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER));
    }

    private BlockState brick(int x, int y, int z) {
        long h = (x * 3129871L) ^ (z * 116129781L) ^ (y * 42317861L);
        h = h * h * 42317861L + h * 11L;
        int n = (int) ((h >> 16) & 15);
        if (n == 0) {
            return SunBlocks.CRACKED_SUNBAKED_BRICKS.get().defaultBlockState();
        }
        return SunBlocks.SUNBAKED_BRICKS.get().defaultBlockState();
    }

    private BlockState chiseled() {
        return SunBlocks.CHISELED_SUNBAKED_BRICKS.get().defaultBlockState();
    }

    // --- Generation ----------------------------------------------------------------------------------------------

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        this.level = level;
        this.chunkBox = chunkBB;
        this.random = random;

        this.groundwork();
        this.plaza();
        this.tierZero();
        this.hall();
        this.vault();
        this.tierOne();
        this.stairwell();
        this.summit();
    }

    private void groundwork() {
        BlockState foundation = SunBlocks.SCORCHSTONE.get().defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 1; y <= 28; y++) {
                    if (!this.get(x, y, z).isAir()) {
                        this.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
                BlockPos.MutableBlockPos pos = this.at(x, -1, z).mutable();
                if (!this.chunkBox.isInside(pos)) {
                    continue;
                }
                int depth = 0;
                while (depth < 24 && pos.getY() > this.level.getMinY() + 1) {
                    BlockState state = this.level.getBlockState(pos);
                    if (!state.isAir() && !state.liquid() && !state.canBeReplaced() && depth > 5) {
                        break;
                    }
                    this.level.setBlock(pos, depth < 6 && ring(x, z) <= HALL_R ? SunBlocks.SUNBAKED_BRICKS.get().defaultBlockState() : foundation, 2);
                    pos.move(Direction.DOWN);
                    depth++;
                }
            }
        }
    }

    private void plaza() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int r = ring(x, z);
                BlockState floor = r % 4 == 0 && (x + z) % 2 == 0 ? this.chiseled() : this.brick(x, 0, z);
                this.set(x, 0, z, floor);
                if (r == 25 && (x + z) % 4 == 0) {
                    this.set(x, 1, z, SunBlocks.SUNBAKED_BRICK_SLAB.get().defaultBlockState());
                }
            }
        }
        // Obelisks flanking the entrance, and at the plaza corners.
        int[][] obelisks = {{CENTER - 5, CENTER + 24}, {CENTER + 5, CENTER + 24}, {2, 2}, {48, 2}, {2, 48}, {48, 48}};
        for (int[] o : obelisks) {
            for (int y = 1; y <= 6; y++) {
                this.set(o[0], y, o[1], y == 6 ? SunBlocks.SUN_LANTERN.get().defaultBlockState() : y == 1 || y == 5 ? this.chiseled() : this.brick(o[0], y, o[1]));
            }
        }
        // Outer guards.
        this.knight(CENTER - 3, 1, CENTER + 23);
        this.knight(CENTER + 3, 1, CENTER + 23);
    }

    private void tierZero() {
        for (int x = CENTER - TIER0_R; x <= CENTER + TIER0_R; x++) {
            for (int z = CENTER - TIER0_R; z <= CENTER + TIER0_R; z++) {
                int r = ring(x, z);
                for (int y = 1; y <= HALL_TOP + 1; y++) {
                    if (r == TIER0_R || y == HALL_TOP + 1) {
                        BlockState state = this.brick(x, y, z);
                        if (r == TIER0_R && y == 3 && (x + z) % 6 == 0) {
                            state = SunBlocks.SOLAR_GLASS.get().defaultBlockState();
                        } else if (r == TIER0_R && (y == 1 || y == HALL_TOP + 1)) {
                            state = this.chiseled();
                        }
                        this.set(x, y, z, state);
                    }
                }
                // Terrace parapet on the tier below the masonry of tier two.
                if (r == TIER0_R && (x + z) % 2 == 0) {
                    this.set(x, HALL_TOP + 2, z, SunBlocks.SUNBAKED_BRICK_SLAB.get().defaultBlockState());
                }
            }
        }
        // Grand entrance in the south wall.
        for (int x = CENTER - 2; x <= CENTER + 2; x++) {
            for (int y = 1; y <= 4; y++) {
                this.set(x, y, CENTER + TIER0_R, Blocks.AIR.defaultBlockState());
            }
            this.set(x, 5, CENTER + TIER0_R, this.chiseled());
        }
    }

    private void hall() {
        // Floor pattern and light.
        for (int x = CENTER - HALL_R; x <= CENTER + HALL_R; x++) {
            for (int z = CENTER - HALL_R; z <= CENTER + HALL_R; z++) {
                int dx = x - CENTER;
                int dz = z - CENTER;
                if (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) {
                    this.set(x, 0, z, this.chiseled());
                }
                if ((dx % 6 == 3 || dx % 6 == -3) && (dz % 6 == 3 || dz % 6 == -3)) {
                    this.set(x, HALL_TOP, z, SunBlocks.SUN_LANTERN.get().defaultBlockState());
                }
            }
        }
        // Pillars.
        for (int px : new int[]{-14, -6, 6, 14}) {
            for (int pz : new int[]{-14, -6, 6, 14}) {
                if (pz == -14 && Math.abs(px) == 6) {
                    continue; // keep the stairwell approach open
                }
                for (int x = 0; x <= 1; x++) {
                    for (int z = 0; z <= 1; z++) {
                        for (int y = 1; y <= HALL_TOP; y++) {
                            this.set(CENTER + px + x, y, CENTER + pz + z, y == 1 || y == HALL_TOP ? this.chiseled() : this.brick(px + x, y, pz + z));
                        }
                    }
                }
            }
        }
        // Sunfire vent gauntlet across the entrance.
        for (int dz : new int[]{18, 15, 12}) {
            for (int dx = -3; dx <= 3; dx++) {
                this.vent(CENTER + dx, 0, CENTER + dz);
            }
        }
        // More vents ringing the centre.
        for (int i = -2; i <= 2; i++) {
            this.vent(CENTER + i, 0, CENTER + 4);
            this.vent(CENTER + i, 0, CENTER - 4);
            this.vent(CENTER + 4, 0, CENTER + i);
            this.vent(CENTER - 4, 0, CENTER + i);
        }
        // The four braziers on raised daises.
        int[][] braziers = {{-10, -4}, {10, -4}, {-10, 8}, {10, 8}};
        for (int[] b : braziers) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    this.set(CENTER + b[0] + x, 1, CENTER + b[1] + z, SunBlocks.SUNBAKED_BRICK_SLAB.get().defaultBlockState());
                }
            }
            this.set(CENTER + b[0], 1, CENTER + b[1], this.chiseled());
            this.set(CENTER + b[0], 2, CENTER + b[1], SunBlocks.SOLAR_BRAZIER.get().defaultBlockState());
        }
        // Spawners, chests and the guard.
        this.spawner(CENTER - 16, 1, CENTER, SunEntities.CINDER_IMP.get());
        this.spawner(CENTER + 16, 1, CENTER, SunEntities.CINDER_IMP.get());
        this.chest(CENTER - 18, 1, CENTER - 18, Direction.SOUTH, LOOT_COMMON);
        this.chest(CENTER + 18, 1, CENTER - 18, Direction.SOUTH, LOOT_COMMON);
        this.chest(CENTER - 18, 1, CENTER + 18, Direction.NORTH, LOOT_COMMON);
        this.chest(CENTER + 18, 1, CENTER + 18, Direction.NORTH, LOOT_COMMON);
        this.chest(CENTER - 5, 1, CENTER - 18, Direction.SOUTH, LOOT_ARMORY);
        this.knight(CENTER - 3, 1, CENTER - 6);
        this.knight(CENTER + 3, 1, CENTER - 6);
        this.knight(CENTER, 1, CENTER + 8);
    }

    /** Hidden vault under the cracked tiles at the centre of the hall. */
    private void vault() {
        for (int x = CENTER - 3; x <= CENTER + 3; x++) {
            for (int z = CENTER - 3; z <= CENTER + 3; z++) {
                boolean wall = Math.abs(x - CENTER) == 3 || Math.abs(z - CENTER) == 3;
                for (int y = -5; y <= -1; y++) {
                    this.set(x, y, z, wall || y == -5 ? this.chiseled() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int x = CENTER - 1; x <= CENTER + 1; x++) {
            for (int z = CENTER - 1; z <= CENTER + 1; z++) {
                this.set(x, 0, z, SunBlocks.CRACKED_SUNBAKED_BRICKS.get().defaultBlockState());
            }
        }
        this.set(CENTER, -4, CENTER - 2, SunBlocks.SUN_LANTERN.get().defaultBlockState());
        this.set(CENTER, -4, CENTER + 2, SunBlocks.SUN_LANTERN.get().defaultBlockState());
        this.chest(CENTER - 2, -4, CENTER, Direction.EAST, LOOT_VAULT);
        this.chest(CENTER + 2, -4, CENTER, Direction.WEST, LOOT_VAULT);
    }

    private void tierOne() {
        for (int x = CENTER - TIER1_R; x <= CENTER + TIER1_R; x++) {
            for (int z = CENTER - TIER1_R; z <= CENTER + TIER1_R; z++) {
                int r = ring(x, z);
                for (int y = HALL_TOP + 2; y <= SUMMIT; y++) {
                    BlockState state = this.brick(x, y, z);
                    if (r == TIER1_R && (y == HALL_TOP + 2 || y == SUMMIT)) {
                        state = this.chiseled();
                    } else if (r == TIER1_R && y == 10 && (x + z) % 4 == 0) {
                        state = SunBlocks.SUN_LANTERN.get().defaultBlockState();
                    }
                    this.set(x, y, z, state);
                }
            }
        }
    }

    /** Spiral stairs from the hall up to the summit, sealed with a Sun Seal until the braziers burn. */
    private void stairwell() {
        BlockState slab = SunBlocks.SUNBAKED_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState full = SunBlocks.SUNBAKED_BRICKS.get().defaultBlockState();
        for (int x = SHAFT_X - 4; x <= SHAFT_X + 4; x++) {
            for (int z = SHAFT_Z - 4; z <= SHAFT_Z + 4; z++) {
                int r = Math.max(Math.abs(x - SHAFT_X), Math.abs(z - SHAFT_Z));
                for (int y = 1; y <= SUMMIT; y++) {
                    if (r == 4) {
                        this.set(x, y, z, y <= HALL_TOP ? this.chiseled() : this.brick(x, y, z));
                    } else if (r <= 3) {
                        this.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        for (int y = 1; y <= SUMMIT; y++) {
            this.set(SHAFT_X, y, SHAFT_Z, this.chiseled());
        }
        int steps = (SUMMIT - 1) * 2;
        for (int x = SHAFT_X - 3; x <= SHAFT_X + 3; x++) {
            for (int z = SHAFT_Z - 3; z <= SHAFT_Z + 3; z++) {
                if (x == SHAFT_X && z == SHAFT_Z) {
                    continue;
                }
                double angle = Math.atan2(z - SHAFT_Z, x - SHAFT_X);
                // Start at the south side (by the door) and climb counter-clockwise.
                double a = (angle - Math.PI / 2 + Math.PI * 4) % (Math.PI * 2);
                int sector = (int) (a / (Math.PI * 2 / 16));
                for (int rev = 0; rev < 4; rev++) {
                    int i = sector + rev * 16;
                    if (i < 1 || i > steps) {
                        continue;
                    }
                    double h = 0.5 + i * 0.5;
                    int y = (int) Math.floor(h);
                    if (y >= 1) {
                        this.set(x, y, z, h == y ? slab : full);
                    }
                }
            }
        }
        // The sealed doorway on the hall side (south face of the shaft).
        for (int x = SHAFT_X - 1; x <= SHAFT_X + 1; x++) {
            for (int y = 1; y <= 3; y++) {
                this.set(x, y, SHAFT_Z + 4, SunBlocks.SUN_SEAL.get().defaultBlockState());
            }
        }
    }

    private void summit() {
        BlockState floorA = this.chiseled();
        for (int x = CENTER - TIER1_R; x <= CENTER + TIER1_R; x++) {
            for (int z = CENTER - TIER1_R; z <= CENTER + TIER1_R; z++) {
                int r = ring(x, z);
                int shaft = Math.max(Math.abs(x - SHAFT_X), Math.abs(z - SHAFT_Z));
                if (shaft <= 3) {
                    continue;
                }
                double d = Math.hypot(x - CENTER, z - CENTER);
                if ((int) d % 5 == 0) {
                    this.set(x, SUMMIT, z, floorA);
                }
                if (r == TIER1_R && (x + z) % 2 == 0) {
                    this.set(x, SUMMIT + 1, z, SunBlocks.SUNBAKED_BRICK_SLAB.get().defaultBlockState());
                }
            }
        }
        // Low wall around the stairwell opening (open on the south side).
        for (int x = SHAFT_X - 4; x <= SHAFT_X + 4; x++) {
            for (int z = SHAFT_Z - 4; z <= SHAFT_Z + 4; z++) {
                int r = Math.max(Math.abs(x - SHAFT_X), Math.abs(z - SHAFT_Z));
                if (r == 4 && !(z == SHAFT_Z + 4 && Math.abs(x - SHAFT_X) <= 1)) {
                    this.set(x, SUMMIT + 1, z, SunBlocks.SUNBAKED_BRICK_SLAB.get().defaultBlockState());
                }
            }
        }
        // Obelisks with sun lanterns.
        for (int ox : new int[]{-13, 13}) {
            for (int oz : new int[]{-13, 13}) {
                for (int y = SUMMIT + 1; y <= SUMMIT + 7; y++) {
                    this.set(CENTER + ox, y, CENTER + oz, y == SUMMIT + 7 ? SunBlocks.SUN_LANTERN.get().defaultBlockState()
                        : y == SUMMIT + 1 || y == SUMMIT + 6 ? this.chiseled() : this.brick(ox, y, oz));
                }
            }
        }
        // The altar on a three-step dais.
        for (int x = CENTER - 2; x <= CENTER + 2; x++) {
            for (int z = CENTER - 2; z <= CENTER + 2; z++) {
                this.set(x, SUMMIT + 1, z, Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER)) == 2
                    ? SunBlocks.SUNBAKED_BRICK_SLAB.get().defaultBlockState() : this.chiseled());
            }
        }
        this.set(CENTER, SUMMIT + 2, CENTER, SunBlocks.SUN_ALTAR.get().defaultBlockState());
        this.set(CENTER - 1, SUMMIT + 2, CENTER - 1, SunBlocks.SOLAR_BRAZIER.get().defaultBlockState().setValue(
            com.starforged.sun.block.SolarBrazierBlock.LIT, true));
        this.set(CENTER + 1, SUMMIT + 2, CENTER + 1, SunBlocks.SOLAR_BRAZIER.get().defaultBlockState().setValue(
            com.starforged.sun.block.SolarBrazierBlock.LIT, true));
    }

    // --- Contents ------------------------------------------------------------------------------------------------

    private void vent(int x, int y, int z) {
        BlockPos pos = this.at(x, y, z);
        if (!this.chunkBox.isInside(pos)) {
            return;
        }
        this.level.setBlock(pos, SunBlocks.SUNFIRE_VENT.get().defaultBlockState(), 2);
        this.level.scheduleTick(pos, SunBlocks.SUNFIRE_VENT.get(), SunfireVentBlock.initialDelay(pos));
    }

    private void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> table) {
        BlockPos pos = this.at(x, y, z);
        if (!this.chunkBox.isInside(pos)) {
            return;
        }
        this.level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
        if (this.level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(table, this.random.nextLong());
        }
    }

    private void spawner(int x, int y, int z, EntityType<?> type) {
        BlockPos pos = this.at(x, y, z);
        if (!this.chunkBox.isInside(pos)) {
            return;
        }
        this.level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 2);
        if (this.level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(type, this.random);
        }
    }

    private void knight(int x, int y, int z) {
        BlockPos pos = this.at(x, y, z);
        if (!this.chunkBox.isInside(pos)) {
            return;
        }
        AshenKnightEntity knight = SunEntities.ASHEN_KNIGHT.get().create(this.level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (knight != null) {
            knight.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0F, 0.0F);
            knight.setPersistenceRequired();
            this.level.addFreshEntityWithPassengers(knight);
        }
    }
}
