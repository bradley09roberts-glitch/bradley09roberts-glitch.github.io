package com.starforged.tempest.world;

import com.starforged.Starforged;
import com.starforged.registry.ModStructures;
import com.starforged.tempest.TempestBlocks;
import com.starforged.tempest.TempestEntities;
import com.starforged.tempest.block.ConductorBlock;
import com.starforged.tempest.block.StormDynamoBlock;
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
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Tempest Citadel. Local x/z in [0, 54], centre (27, 27); y = 0 is the floor of the keep.
 * <pre>
 *   y 10..   the summit - a round open-air arena with the Tempest Altar (Veyr's battlefield)
 *   y 1..8   the Keep, and four Wings off it. Each wing holds a storm circuit: a Storm Dynamo (fired with a lever),
 *            three Rotating Conductors and a Citadel Core. Turned the right way the pulse reaches the core; turned
 *            the wrong way it hits an Overload Relay, which shocks everyone nearby and knocks the wing's core out.
 *            When all four cores are lit the Tempest Seals around the wind lift in the keep shatter.
 *   y < 0    the island itself, hanging over nothing
 * </pre>
 * Wing coordinates are (u, v): u runs outward from the keep, v across the wing.
 */
public class TempestCitadelPiece extends StructurePiece {
    public static final int CENTER = 27;
    private static final int SIZE = 55;
    private static final int KEEP = 8;
    private static final int WING_END = 22;
    private static final int WING_HALF = 6;
    private static final int WING_ROOF = 6;
    public static final int SUMMIT = 9;
    private static final double ARENA_R = 16.0;
    private static final double ISLAND_R = 27.0;
    private static final int LIFT_X = CENTER + 4;
    private static final int LIFT_Z = CENTER + 4;

    public static final ResourceKey<LootTable> LOOT_COMMON = loot("chests/tempest_citadel_common");
    public static final ResourceKey<LootTable> LOOT_VAULT = loot("chests/tempest_citadel_vault");

    private WorldGenLevel level;
    private BoundingBox chunkBox;
    private RandomSource random;

    public TempestCitadelPiece(int minX, int floorY, int minZ) {
        super(ModStructures.TEMPEST_CITADEL_PIECE.get(), 0, new BoundingBox(minX, floorY - 26, minZ, minX + SIZE - 1, floorY + 20, minZ + SIZE - 1));
        this.setOrientation(null);
    }

    public TempestCitadelPiece(CompoundTag tag) {
        super(ModStructures.TEMPEST_CITADEL_PIECE.get(), tag);
    }

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Starforged.id(path));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    private BlockPos at(int x, int y, int z) {
        return new BlockPos(this.boundingBox.minX() + x, this.boundingBox.minY() + 26 + y, this.boundingBox.minZ() + z);
    }

    private void set(int x, int y, int z, BlockState state) {
        BlockPos pos = this.at(x, y, z);
        if (this.chunkBox.isInside(pos)) {
            this.level.setBlock(pos, state, 2);
        }
    }

    private static double dist(int x, int z) {
        return Math.hypot(x - CENTER, z - CENTER);
    }

    private static long hash(int x, int y, int z) {
        long h = (x * 3129871L) ^ (z * 116129781L) ^ (y * 42317861L);
        return h * h * 42317861L + h * 11L;
    }

    private BlockState brick() {
        return TempestBlocks.TEMPEST_BRICKS.get().defaultBlockState();
    }

    private BlockState chiseled() {
        return TempestBlocks.CHISELED_TEMPEST_BRICKS.get().defaultBlockState();
    }

    private BlockState slab() {
        return TempestBlocks.TEMPEST_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    private BlockState lantern(boolean hanging) {
        return TempestBlocks.STORM_LANTERN.get().defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        this.level = level;
        this.chunkBox = chunkBB;
        this.random = random;
        this.island();
        this.keep();
        for (int d = 0; d < 4; d++) {
            this.wing(d);
        }
        this.lift();
        this.summit();
    }

    // --- Island ----------------------------------------------------------------------------------------------------

    private void island() {
        BlockState rock = TempestBlocks.SKYROCK.get().defaultBlockState();
        BlockState stone = TempestBlocks.STORMSTONE.get().defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double d = dist(x, z);
                double edge = ISLAND_R + ((hash(x, 0, z) >> 20) & 3) * 0.5 - 1.0;
                if (d > edge) {
                    continue;
                }
                for (int y = 1; y <= 20; y++) {
                    this.set(x, y, z, this.air());
                }
                int depth = (int) ((edge - d) * 0.9) + 2 + (int) ((hash(x, 1, z) >> 24) & 3);
                for (int y = 0; y > -Math.min(depth, 25); y--) {
                    BlockState state = y == 0 ? TempestBlocks.STORMGRASS.get().defaultBlockState() : y > -4 ? TempestBlocks.SKYSOIL.get().defaultBlockState()
                        : ((hash(x, y, z) >> 18) & 7) == 0 ? stone : rock;
                    if (y < -6 && ((hash(x, y, z) >> 12) & 63) == 0) {
                        state = TempestBlocks.AETHERIUM_ORE.get().defaultBlockState();
                    }
                    this.set(x, y, z, state);
                }
                if (d > 22 && ((hash(x, 2, z) >> 16) & 31) == 0) {
                    this.set(x, 1, z, TempestBlocks.THUNDER_CRYSTAL_CLUSTER.get().defaultBlockState());
                }
            }
        }
    }

    // --- Keep ------------------------------------------------------------------------------------------------------

    private void keep() {
        for (int x = CENTER - KEEP; x <= CENTER + KEEP; x++) {
            for (int z = CENTER - KEEP; z <= CENTER + KEEP; z++) {
                boolean wall = Math.abs(x - CENTER) == KEEP || Math.abs(z - CENTER) == KEEP;
                boolean corner = Math.abs(x - CENTER) == KEEP && Math.abs(z - CENTER) == KEEP;
                this.set(x, 0, z, (x + z) % 4 == 0 ? this.chiseled() : this.brick());
                for (int y = 1; y < SUMMIT; y++) {
                    this.set(x, y, z, !wall ? this.air() : corner || y == 1 || y == SUMMIT - 1 ? this.chiseled()
                        : y == 4 && (x + z) % 3 == 0 ? TempestBlocks.AETHERGLASS.get().defaultBlockState() : this.brick());
                }
            }
        }
        // Doorways into the four wings.
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] d : dirs) {
            for (int v = -1; v <= 1; v++) {
                int x = CENTER + d[0] * KEEP - d[1] * v;
                int z = CENTER + d[1] * KEEP + d[0] * v;
                for (int y = 1; y <= 3; y++) {
                    this.set(x, y, z, this.air());
                }
                this.set(x, 1, z, TempestBlocks.SHOCK_PLATE.get().defaultBlockState());
            }
        }
        // Hanging lanterns and pillars.
        for (int[] p : new int[][]{{-5, -5}, {5, -5}, {-5, 5}}) {
            for (int y = 1; y < SUMMIT; y++) {
                this.set(CENTER + p[0], y, CENTER + p[1], y == 1 || y == SUMMIT - 1 ? this.chiseled() : this.brick());
            }
            this.set(CENTER + p[0] + 1, SUMMIT - 1, CENTER + p[1], this.lantern(true));
        }
        this.set(CENTER, SUMMIT - 1, CENTER, this.lantern(true));
        this.set(CENTER - 3, SUMMIT - 1, CENTER + 3, TempestBlocks.WIND_CHIME.get().defaultBlockState());
        this.set(CENTER + 3, SUMMIT - 1, CENTER - 3, TempestBlocks.WIND_CHIME.get().defaultBlockState());
        this.set(CENTER - 4, 0, CENTER, TempestBlocks.CYCLONE_EMITTER.get().defaultBlockState());
        this.spawner(CENTER - 6, 1, CENTER + 6, TempestEntities.STATIC_WISP.get());
        this.spawner(CENTER + 6, 1, CENTER - 6, TempestEntities.STATIC_WISP.get());
    }

    /** The wind lift to the summit, ringed by Tempest Seals; the vault chests sit inside the ring. */
    private void lift() {
        for (int x = LIFT_X - 2; x <= LIFT_X + 2; x++) {
            for (int z = LIFT_Z - 2; z <= LIFT_Z + 2; z++) {
                boolean ring = Math.abs(x - LIFT_X) == 2 || Math.abs(z - LIFT_Z) == 2;
                for (int y = 1; y < SUMMIT; y++) {
                    this.set(x, y, z, ring && y <= 3 ? TempestBlocks.TEMPEST_SEAL.get().defaultBlockState()
                        : ring ? (y == 4 || y == SUMMIT - 1 ? this.chiseled() : this.brick()) : this.air());
                }
            }
        }
        for (int x = LIFT_X - 1; x <= LIFT_X + 1; x++) {
            for (int z = LIFT_Z - 1; z <= LIFT_Z + 1; z++) {
                if (x == LIFT_X || z == LIFT_Z) {
                    this.set(x, 0, z, TempestBlocks.WIND_VENT.get().defaultBlockState());
                }
            }
        }
        this.chest(LIFT_X - 1, 1, LIFT_Z - 1, Direction.SOUTH, LOOT_VAULT);
        this.chest(LIFT_X + 1, 1, LIFT_Z + 1, Direction.NORTH, LOOT_VAULT);
    }

    // --- Wings -----------------------------------------------------------------------------------------------------

    /** Wing {@code d}: 0 north, 1 east, 2 south, 3 west. */
    private void wing(int d) {
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int dx = dirs[d][0];
        int dz = dirs[d][1];
        Direction out = Direction.getApproximateNearest((float) (dx), 0.0F, (float) (dz));
        Direction in = out.getOpposite();
        // Shell.
        for (int u = KEEP; u <= WING_END; u++) {
            for (int v = -WING_HALF; v <= WING_HALF; v++) {
                int x = this.wx(dx, dz, u, v);
                int z = this.wz(dx, dz, u, v);
                if (u == KEEP && Math.abs(v) <= 1) {
                    continue;
                }
                boolean wall = u == WING_END || Math.abs(v) == WING_HALF || u == KEEP;
                if (u == KEEP && !wall) {
                    continue;
                }
                if (u > KEEP) {
                    this.set(x, 0, z, Math.abs(v) == 4 && u % 4 == 0 ? this.chiseled() : this.brick());
                }
                if (u == KEEP) {
                    continue;
                }
                for (int y = 1; y < WING_ROOF; y++) {
                    this.set(x, y, z, !wall ? this.air() : y == 1 ? this.chiseled()
                        : y == 3 && u % 3 == 0 ? TempestBlocks.AETHERGLASS.get().defaultBlockState() : this.brick());
                }
                this.set(x, WING_ROOF, z, wall ? this.chiseled() : this.brick());
                if (wall && (u + v) % 2 == 0) {
                    this.set(x, WING_ROOF + 1, z, this.slab());
                }
            }
        }
        // Outer door in the side wall, guarded by runes.
        for (int u = 18; u <= 20; u++) {
            int x = this.wx(dx, dz, u, -WING_HALF);
            int z = this.wz(dx, dz, u, -WING_HALF);
            for (int y = 1; y <= 3; y++) {
                this.set(x, y, z, this.air());
            }
            int ox = this.wx(dx, dz, u, -WING_HALF - 2);
            int oz = this.wz(dx, dz, u, -WING_HALF - 2);
            this.set(ox, 0, oz, (u + d) % 2 == 0 ? TempestBlocks.THUNDER_RUNE.get().defaultBlockState()
                : TempestBlocks.GALE_RUNE.get().defaultBlockState());
            this.set(this.wx(dx, dz, u, -WING_HALF + 1), 1, this.wz(dx, dz, u, -WING_HALF + 1), TempestBlocks.SHOCK_PLATE.get().defaultBlockState());
        }
        // The storm circuit.
        Direction vPlus = Direction.getApproximateNearest((float) (-dz), 0.0F, (float) (dx));
        this.place(dx, dz, 20, 0, TempestBlocks.STORM_DYNAMO.get().defaultBlockState().setValue(StormDynamoBlock.FACING, in));
        this.place(dx, dz, 21, 0, TempestBlocks.STORM_LANTERN.get().defaultBlockState());
        this.place(dx, dz, 20, 1, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
            .setValue(LeverBlock.FACING, vPlus));
        // Starting orientations are wrong (and vary by wing); the solution is A -> +v, B -> in, C -> in.
        Direction[] startA = {out, vPlus.getOpposite(), out, in};
        Direction[] startB = {vPlus, out, vPlus.getOpposite(), vPlus};
        Direction[] startC = {vPlus.getOpposite(), vPlus, out, vPlus.getOpposite()};
        BlockState rotating = TempestBlocks.ROTATING_CONDUCTOR.get().defaultBlockState();
        this.place(dx, dz, 17, 0, rotating.setValue(ConductorBlock.FACING, startA[d]));
        this.place(dx, dz, 17, 4, rotating.setValue(ConductorBlock.FACING, startB[d]));
        this.place(dx, dz, 13, 4, rotating.setValue(ConductorBlock.FACING, startC[d]));
        this.place(dx, dz, 10, 4, TempestBlocks.CITADEL_CORE.get().defaultBlockState());
        this.place(dx, dz, 13, 0, TempestBlocks.OVERLOAD_RELAY.get().defaultBlockState());
        this.place(dx, dz, 17, -4, TempestBlocks.OVERLOAD_RELAY.get().defaultBlockState());
        // Furnishing: a spawner, a chest, wind vents up onto the roof, lanterns.
        this.spawnerAt(dx, dz, 11, -4, d % 2 == 0 ? TempestEntities.STORMBOUND.get() : TempestEntities.SHARDWING.get());
        int cx = this.wx(dx, dz, 21, 4);
        int cz = this.wz(dx, dz, 21, 4);
        this.chest(cx, 1, cz, in, LOOT_COMMON);
        for (int u : new int[]{11, 15, 19}) {
            this.place(dx, dz, u, 5, WING_ROOF - 1, this.lantern(true));
            this.place(dx, dz, u, -5, WING_ROOF - 1, this.lantern(true));
        }
        // A gale vent by the inner doorway, blowing people back out toward the circuit.
        this.place(dx, dz, 9, -3, 0, TempestBlocks.GALE_VENT.get().defaultBlockState()
            .setValue(com.starforged.tempest.block.GaleVentBlock.FACING, out));
        // A pillar between the wings props up the summit.
        int px = CENTER + (dx - dz) * 11;
        int pz = CENTER + (dz + dx) * 11;
        for (int y = 1; y < SUMMIT; y++) {
            this.set(px, y, pz, y == 1 || y == SUMMIT - 1 ? this.chiseled() : this.brick());
            this.set(px + Integer.signum(dx - dz), y, pz, this.brick());
            this.set(px, y, pz + Integer.signum(dz + dx), this.brick());
        }
        this.set(px, 1, pz + 2 * Integer.signum(dz + dx), TempestBlocks.LIGHTNING_BEACON.get().defaultBlockState());
    }

    private int wx(int dx, int dz, int u, int v) {
        return CENTER + dx * u - dz * v;
    }

    private int wz(int dx, int dz, int u, int v) {
        return CENTER + dz * u + dx * v;
    }

    private void place(int dx, int dz, int u, int v, BlockState state) {
        this.place(dx, dz, u, v, 1, state);
    }

    private void place(int dx, int dz, int u, int v, int y, BlockState state) {
        this.set(this.wx(dx, dz, u, v), y, this.wz(dx, dz, u, v), state);
    }

    private void spawnerAt(int dx, int dz, int u, int v, EntityType<?> type) {
        this.spawner(this.wx(dx, dz, u, v), 1, this.wz(dx, dz, u, v), type);
    }

    // --- Summit ----------------------------------------------------------------------------------------------------

    private void summit() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double d = dist(x, z);
                if (d > ARENA_R + 0.5) {
                    continue;
                }
                boolean lift = Math.abs(x - LIFT_X) <= 1 && Math.abs(z - LIFT_Z) <= 1;
                if (lift) {
                    this.set(x, SUMMIT, z, this.air());
                    continue;
                }
                int ring = (int) d;
                this.set(x, SUMMIT, z, ring % 5 == 0 || d > ARENA_R - 0.5 ? this.chiseled() : this.brick());
                if (d > ARENA_R - 0.5 && (x + z) % 2 == 0) {
                    this.set(x, SUMMIT + 1, z, this.slab());
                }
            }
        }
        // Lightning rods on lantern posts around the rim.
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int px = CENTER + (int) Math.round(Math.cos(a) * 14.5);
            int pz = CENTER + (int) Math.round(Math.sin(a) * 14.5);
            for (int y = SUMMIT + 1; y <= SUMMIT + 3; y++) {
                this.set(px, y, pz, y == SUMMIT + 1 ? this.chiseled() : this.brick());
            }
            this.set(px, SUMMIT + 4, pz, this.lantern(false));
            this.set(px, SUMMIT + 5, pz, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
        }
        // The altar on its dais.
        for (int x = CENTER - 2; x <= CENTER + 2; x++) {
            for (int z = CENTER - 2; z <= CENTER + 2; z++) {
                this.set(x, SUMMIT + 1, z, Math.max(Math.abs(x - CENTER), Math.abs(z - CENTER)) == 2 ? this.slab() : this.chiseled());
            }
        }
        this.set(CENTER, SUMMIT + 2, CENTER, TempestBlocks.TEMPEST_ALTAR.get().defaultBlockState());
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
