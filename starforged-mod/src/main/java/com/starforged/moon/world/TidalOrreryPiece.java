package com.starforged.moon.world;

import com.starforged.Starforged;
import com.starforged.moon.MoonBlocks;
import com.starforged.moon.MoonEntities;
import com.starforged.moon.block.OrreryRingBlock;
import com.starforged.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
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
 * The Tidal Orrery: a round clockwork observatory of lunar brick. Local x/z in [0, 40], centre (20, 20); y = 0 is
 * the hall floor.
 * <pre>
 *   y 9..    the roof - an open-air arena with the Moon Altar (the Pale Matriarch's battlefield)
 *   y 1..8   the Orrery Hall - gravity plates across the entrance, a ring channel of tide water with Tidal Clams,
 *            sentinel and moth spawners, and in the middle the Great Orrery: four phase rings around the alignment
 *            console. Murals on the walls (one in the direction of each ring) show the phase each ring must show.
 *            Aligning them melts the Moon Seal on the stair tower.
 *   y -5..-1 a hidden vault under cracked tiles
 * </pre>
 */
public class TidalOrreryPiece extends StructurePiece {
    public static final int CENTER = 20;
    private static final int SIZE = 41;
    private static final double WALL_R = 18.0;
    public static final int ROOF = 9;
    private static final int SHAFT_X = CENTER + 11;
    private static final int SHAFT_Z = CENTER - 11;
    private static final int VAULT_X = CENTER - 5;
    private static final int VAULT_Z = CENTER + 5;

    public static final ResourceKey<LootTable> LOOT_COMMON = loot("chests/tidal_orrery_common");
    public static final ResourceKey<LootTable> LOOT_VAULT = loot("chests/tidal_orrery_vault");

    private WorldGenLevel level;
    private BoundingBox chunkBox;
    private RandomSource random;

    public TidalOrreryPiece(int minX, int floorY, int minZ) {
        super(ModStructures.TIDAL_ORRERY_PIECE.get(), 0, new BoundingBox(minX, floorY - 8, minZ, minX + SIZE - 1, floorY + 24, minZ + SIZE - 1));
        this.setOrientation(null);
    }

    public TidalOrreryPiece(CompoundTag tag) {
        super(ModStructures.TIDAL_ORRERY_PIECE.get(), tag);
    }

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Starforged.id(path));
    }

    /** The phase (0-3) the ring in direction {@code dir} (0=N, 1=E, 2=S, 3=W) must show for an orrery at this corner. */
    public static int targetPhase(int minX, int minZ, int dir) {
        long h = minX * 341873128712L + minZ * 132897987541L + dir * 1999L;
        h ^= h >>> 17;
        h *= 0x9E3779B97F4A7C15L;
        return (int) ((h >>> 40) & 3);
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

    private static double dist(int x, int z) {
        return Math.hypot(x - CENTER, z - CENTER);
    }

    private BlockState brick(int x, int y, int z) {
        long h = (x * 3129871L) ^ (z * 116129781L) ^ (y * 42317861L);
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 15) == 0 ? MoonBlocks.CRACKED_LUNAR_BRICKS.get().defaultBlockState() : MoonBlocks.LUNAR_BRICKS.get().defaultBlockState();
    }

    private BlockState chiseled() {
        return MoonBlocks.CHISELED_LUNAR_BRICKS.get().defaultBlockState();
    }

    private BlockState slab() {
        return MoonBlocks.LUNAR_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        this.level = level;
        this.chunkBox = chunkBB;
        this.random = random;
        this.groundwork();
        this.shell();
        this.hall();
        this.orrery();
        this.vault();
        this.stairwell();
        this.roof();
    }

    private void groundwork() {
        BlockState foundation = MoonBlocks.MOONSTONE.get().defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double d = dist(x, z);
                if (d > WALL_R + 1.5) {
                    continue;
                }
                for (int y = 1; y <= 24; y++) {
                    if (!this.get(x, y, z).isAir()) {
                        this.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
                BlockPos.MutableBlockPos pos = this.at(x, 0, z).mutable();
                if (!this.chunkBox.isInside(pos)) {
                    continue;
                }
                int depth = 0;
                while (depth < 40 && pos.getY() > this.level.getMinY() + 1) {
                    BlockState state = this.level.getBlockState(pos);
                    if (!state.isAir() && !state.liquid() && !state.canBeReplaced() && depth > 5) {
                        break;
                    }
                    this.level.setBlock(pos, depth < 6 ? this.brick(x, -depth, z) : foundation, 2);
                    pos.move(Direction.DOWN);
                    depth++;
                }
            }
        }
    }

    /** Round outer wall with moon-glass windows, and the ceiling that is also the roof arena's floor. */
    private void shell() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double d = dist(x, z);
                if (d > WALL_R + 0.5) {
                    continue;
                }
                boolean wall = d > WALL_R - 0.5;
                if (wall) {
                    double angle = Math.atan2(z - CENTER, x - CENTER);
                    boolean window = ((int) Math.floor((angle + Math.PI) / (Math.PI / 12))) % 2 == 0;
                    for (int y = 1; y < ROOF; y++) {
                        BlockState state = y == 1 || y == ROOF - 1 ? this.chiseled() : this.brick(x, y, z);
                        if (window && (y == 3 || y == 4)) {
                            state = MoonBlocks.MOON_GLASS.get().defaultBlockState();
                        }
                        this.set(x, y, z, state);
                    }
                }
                this.set(x, ROOF, z, wall ? this.chiseled() : this.brick(x, ROOF, z));
                // Floor.
                int ring = (int) d;
                this.set(x, 0, z, ring % 4 == 0 && ring > 4 ? this.chiseled() : this.brick(x, 0, z));
            }
        }
        // Grand entrance in the south.
        for (int x = CENTER - 2; x <= CENTER + 2; x++) {
            for (int z = CENTER + 16; z <= CENTER + 19; z++) {
                for (int y = 1; y <= 4; y++) {
                    this.set(x, y, z, Blocks.AIR.defaultBlockState());
                }
                this.set(x, 0, z, this.chiseled());
            }
            this.set(x, 5, CENTER + 18, this.chiseled());
        }
    }

    private void hall() {
        // Tide channel ring with clams, bridged at the four cardinal points.
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double d = dist(x, z);
                if (d < 12.0 || d >= 13.5) {
                    continue;
                }
                boolean bridge = Math.abs(x - CENTER) <= 1 || Math.abs(z - CENTER) <= 1;
                if (bridge) {
                    continue;
                }
                this.set(x, 0, z, Blocks.WATER.defaultBlockState());
                this.set(x, -1, z, Blocks.WATER.defaultBlockState());
                this.set(x, -2, z, MoonBlocks.SILVER_SAND.get().defaultBlockState());
                if ((x * 7 + z * 13) % 11 == 0) {
                    this.set(x, -1, z, MoonBlocks.TIDAL_CLAM.get().defaultBlockState());
                }
            }
        }
        // Pillars.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 8 + i * Math.PI / 4;
            int px = CENTER + (int) Math.round(Math.cos(a) * 8.5);
            int pz = CENTER + (int) Math.round(Math.sin(a) * 8.5);
            for (int y = 1; y < ROOF; y++) {
                this.set(px, y, pz, y == 1 || y == ROOF - 1 ? this.chiseled() : this.brick(px, y, pz));
            }
            this.set(px, ROOF - 2, pz, MoonBlocks.MOON_LANTERN.get().defaultBlockState());
        }
        // Lanterns set into the ceiling.
        for (int x = CENTER - 15; x <= CENTER + 15; x += 5) {
            for (int z = CENTER - 15; z <= CENTER + 15; z += 5) {
                if (dist(x, z) < 16.5 && dist(x, z) > 5) {
                    this.set(x, ROOF, z, MoonBlocks.MOON_LANTERN.get().defaultBlockState());
                }
            }
        }
        // Inversion corridor: gravity plates across the entrance.
        for (int x = CENTER - 2; x <= CENTER + 2; x++) {
            for (int z = CENTER + 13; z <= CENTER + 16; z++) {
                if ((x + z) % 2 == 0) {
                    this.set(x, 1, z, MoonBlocks.GRAVITY_PLATE.get().defaultBlockState());
                }
            }
        }
        this.spawner(CENTER - 9, 1, CENTER + 4, MoonEntities.SELENITE_SENTINEL.get());
        this.spawner(CENTER + 9, 1, CENTER + 4, MoonEntities.SELENITE_SENTINEL.get());
        this.spawner(CENTER - 6, 1, CENTER - 8, MoonEntities.LUNAR_MOTH.get());
        this.chest(CENTER - 11, 1, CENTER - 11, Direction.SOUTH, LOOT_COMMON);
        this.chest(CENTER - 11, 1, CENTER + 11, Direction.NORTH, LOOT_COMMON);
        this.chest(CENTER + 11, 1, CENTER + 11, Direction.NORTH, LOOT_COMMON);
        this.chest(CENTER + 15, 1, CENTER + 3, Direction.WEST, LOOT_COMMON);
    }

    /** The Great Orrery: four phase rings around the console, and a mural on the wall behind each ring. */
    private void orrery() {
        for (int x = CENTER - 4; x <= CENTER + 4; x++) {
            for (int z = CENTER - 4; z <= CENTER + 4; z++) {
                if (dist(x, z) <= 4.5) {
                    this.set(x, 0, z, this.chiseled());
                }
            }
        }
        this.set(CENTER, 1, CENTER, MoonBlocks.ORRERY_CONSOLE.get().defaultBlockState());
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            int target = targetPhase(this.ox(), this.oz(), i);
            int start = (target + 1 + i % 3) & 3;
            this.set(CENTER + dirs[i][0] * 3, 1, CENTER + dirs[i][1] * 3,
                MoonBlocks.ORRERY_RING.get().defaultBlockState().setValue(OrreryRingBlock.PHASE, start));
            int mx = CENTER + dirs[i][0] * 18;
            int mz = CENTER + dirs[i][1] * 18;
            this.set(mx, 6, mz, MoonBlocks.LUNAR_MURAL.get().defaultBlockState().setValue(OrreryRingBlock.PHASE, target));
            this.set(mx - dirs[i][1], 6, mz + dirs[i][0], this.chiseled());
            this.set(mx + dirs[i][1], 6, mz - dirs[i][0], this.chiseled());
        }
        // A floating armillary of moon glass above the console.
        for (int y = 5; y <= 7; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) + Math.abs(y - 6) <= 1) {
                        this.set(CENTER + dx, y, CENTER + dz, MoonBlocks.MOON_GLASS.get().defaultBlockState());
                    }
                }
            }
        }
        this.set(CENTER, 6, CENTER, MoonBlocks.MOON_LANTERN.get().defaultBlockState());
    }

    private void vault() {
        for (int x = VAULT_X - 3; x <= VAULT_X + 3; x++) {
            for (int z = VAULT_Z - 3; z <= VAULT_Z + 3; z++) {
                boolean wall = Math.abs(x - VAULT_X) == 3 || Math.abs(z - VAULT_Z) == 3;
                for (int y = -5; y <= -1; y++) {
                    this.set(x, y, z, wall || y == -5 ? this.chiseled() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int x = VAULT_X - 1; x <= VAULT_X + 1; x++) {
            for (int z = VAULT_Z - 1; z <= VAULT_Z + 1; z++) {
                this.set(x, 0, z, MoonBlocks.CRACKED_LUNAR_BRICKS.get().defaultBlockState());
            }
        }
        this.set(VAULT_X, -4, VAULT_Z - 2, MoonBlocks.MOON_LANTERN.get().defaultBlockState());
        this.chest(VAULT_X - 2, -4, VAULT_Z, Direction.EAST, LOOT_VAULT);
        this.chest(VAULT_X + 2, -4, VAULT_Z, Direction.WEST, LOOT_VAULT);
    }

    /** Spiral stair tower up to the roof, sealed with a Moon Seal until the orrery is aligned. */
    private void stairwell() {
        BlockState slab = this.slab();
        BlockState full = MoonBlocks.LUNAR_BRICKS.get().defaultBlockState();
        for (int x = SHAFT_X - 4; x <= SHAFT_X + 4; x++) {
            for (int z = SHAFT_Z - 4; z <= SHAFT_Z + 4; z++) {
                int r = Math.max(Math.abs(x - SHAFT_X), Math.abs(z - SHAFT_Z));
                for (int y = 0; y <= ROOF; y++) {
                    if (r == 4 && y >= 1) {
                        this.set(x, y, z, y == 1 ? this.chiseled() : this.brick(x, y, z));
                    } else if (r <= 3) {
                        this.set(x, y, z, y == 0 ? this.brick(x, 0, z) : Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        for (int y = 1; y < ROOF; y++) {
            this.set(SHAFT_X, y, SHAFT_Z, this.chiseled());
        }
        int steps = (ROOF - 1) * 2;
        for (int x = SHAFT_X - 3; x <= SHAFT_X + 3; x++) {
            for (int z = SHAFT_Z - 3; z <= SHAFT_Z + 3; z++) {
                if (x == SHAFT_X && z == SHAFT_Z) {
                    continue;
                }
                double angle = Math.atan2(z - SHAFT_Z, x - SHAFT_X);
                double a = (angle - Math.PI / 2 + Math.PI * 4) % (Math.PI * 2);
                int sector = (int) (a / (Math.PI * 2 / 16));
                for (int rev = 0; rev < 3; rev++) {
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
        for (int x = SHAFT_X - 1; x <= SHAFT_X + 1; x++) {
            for (int y = 1; y <= 3; y++) {
                this.set(x, y, SHAFT_Z + 4, MoonBlocks.MOON_SEAL.get().defaultBlockState());
            }
        }
    }

    private void roof() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double d = dist(x, z);
                int shaft = Math.max(Math.abs(x - SHAFT_X), Math.abs(z - SHAFT_Z));
                if (d > WALL_R + 0.5 || shaft <= 3) {
                    continue;
                }
                if ((int) d % 5 == 0) {
                    this.set(x, ROOF, z, this.chiseled());
                }
                if (d > WALL_R - 0.5 && (x + z) % 2 == 0) {
                    this.set(x, ROOF + 1, z, this.slab());
                }
            }
        }
        // Low wall around the stair opening (open to the south-west, towards the altar).
        for (int x = SHAFT_X - 4; x <= SHAFT_X + 4; x++) {
            for (int z = SHAFT_Z - 4; z <= SHAFT_Z + 4; z++) {
                int r = Math.max(Math.abs(x - SHAFT_X), Math.abs(z - SHAFT_Z));
                if (r == 4 && !(z == SHAFT_Z + 4 && Math.abs(x - SHAFT_X) <= 1)) {
                    this.set(x, ROOF + 1, z, this.slab());
                }
            }
        }
        // Lantern posts around the arena.
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int px = CENTER + (int) Math.round(Math.cos(a) * 15);
            int pz = CENTER + (int) Math.round(Math.sin(a) * 15);
            if (Math.max(Math.abs(px - SHAFT_X), Math.abs(pz - SHAFT_Z)) <= 5) {
                continue;
            }
            for (int y = ROOF + 1; y <= ROOF + 4; y++) {
                this.set(px, y, pz, y == ROOF + 4 ? MoonBlocks.MOON_LANTERN.get().defaultBlockState() : y == ROOF + 1 ? this.chiseled()
                    : this.brick(px, y, pz));
            }
        }
        // The altar on its dais.
        for (int x = CENTER - 2; x <= CENTER + 2; x++) {
            for (int z = CENTER - 2; z <= CENTER + 2; z++) {
                this.set(x, ROOF + 1, z, Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER)) == 2 ? this.slab() : this.chiseled());
            }
        }
        this.set(CENTER, ROOF + 2, CENTER, MoonBlocks.MOON_ALTAR.get().defaultBlockState());
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
}
