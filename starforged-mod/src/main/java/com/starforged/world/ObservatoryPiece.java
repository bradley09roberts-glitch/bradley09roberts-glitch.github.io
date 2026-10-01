package com.starforged.world;

import com.starforged.Starforged;
import com.starforged.entity.monster.AstralGolemEntity;
import com.starforged.entity.monster.MimicEntity;
import com.starforged.registry.ModBlocks;
import com.starforged.registry.ModEntities;
import com.starforged.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.Vec3;

/**
 * The whole Fallen Observatory, generated block by block. Coordinates in this class are local:
 * x/z in [0, 40] with the tower centred at (20, 20); y = 0 is the courtyard / ground-floor level.
 * <pre>
 *   y 28..39  starglass dome, great telescope, Celestial Altar (boss arena)
 *   y 18..26  armory      - loot, a Mimic, starfire runes
 *   y  9..17  library     - bookshelves, wraith spawner, gravity runes
 *   y  0..8   great hall  - pillars, star-lantern floor, a chest... and a Mimic
 *   y -11..-1 undercroft  - trapped corridor, the Vault Seal, and the sleeping Astral Golem
 * </pre>
 * A spiral staircase around the central pillar connects every level.
 */
public class ObservatoryPiece extends StructurePiece {
    public static final int CENTER = 20;
    private static final int SIZE = 41;
    private static final int TOWER_R = 11;
    private static final int[] FLOORS = {0, 9, 18};
    private static final int ROOF = 27;
    private static final int BASEMENT_FLOOR = -11;

    public static final ResourceKey<LootTable> LOOT_COMMON = loot("chests/observatory_common");
    public static final ResourceKey<LootTable> LOOT_LIBRARY = loot("chests/observatory_library");
    public static final ResourceKey<LootTable> LOOT_ARMORY = loot("chests/observatory_armory");
    public static final ResourceKey<LootTable> LOOT_VAULT = loot("chests/observatory_vault");

    private WorldGenLevel level;
    private BoundingBox chunkBox;
    private RandomSource random;

    public ObservatoryPiece(int minX, int floorY, int minZ) {
        super(ModStructures.OBSERVATORY_PIECE.get(), 0, new BoundingBox(minX, floorY - 13, minZ, minX + SIZE - 1, floorY + 42, minZ + SIZE - 1));
        this.setOrientation(null);
    }

    public ObservatoryPiece(CompoundTag tag) {
        super(ModStructures.OBSERVATORY_PIECE.get(), tag);
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
        return this.boundingBox.minY() + 13;
    }

    private int oz() {
        return this.boundingBox.minZ();
    }

    // --- Helpers -------------------------------------------------------------------------------------------------

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
        // Only read inside the chunk being generated: neighbouring chunks may not exist yet.
        return this.chunkBox.isInside(pos) ? this.level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    private static double radius(int x, int z) {
        double dx = x - CENTER;
        double dz = z - CENTER;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double angle(int x, int z) {
        double a = Math.atan2(z - CENTER, x - CENTER);
        return a < 0 ? a + Math.PI * 2 : a;
    }

    /** Deterministic per-block noise in [0,1): identical no matter which chunk is being generated. */
    private float noise(int x, int y, int z) {
        long seed = Mth.getSeed(this.ox() + x, this.oy() + y, this.oz() + z);
        return (float) ((seed >>> 16) & 0xFFFF) / 65536.0F;
    }

    private BlockState masonry(int x, int y, int z) {
        float n = this.noise(x, y, z);
        if (n < 0.16F) {
            return ModBlocks.CRACKED_ASTRAL_BRICKS.get().defaultBlockState();
        }
        if (n < 0.2F) {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        return ModBlocks.ASTRAL_BRICKS.get().defaultBlockState();
    }

    // --- Generation ----------------------------------------------------------------------------------------------

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        this.level = level;
        this.chunkBox = chunkBB;
        this.random = random;

        this.groundwork();
        this.courtyard();
        this.basement();
        this.tower();
        this.staircase();
        this.greatHall();
        this.library();
        this.armory();
        this.roof();
        this.dome();
        this.telescope();
    }

    private void groundwork() {
        BlockState foundation = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (radius(x, z) > 20.5) {
                    continue;
                }
                for (int y = 1; y <= 40; y++) {
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
                    if (!state.isAir() && !state.liquid() && !state.canBeReplaced() && depth > 1) {
                        break;
                    }
                    this.level.setBlock(pos, foundation, 2);
                    pos.move(Direction.DOWN);
                    depth++;
                }
            }
        }
    }

    private void courtyard() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double r = radius(x, z);
                if (r > 20.5) {
                    continue;
                }
                float n = this.noise(x, 0, z);
                BlockState floor;
                if (r > 15.5 && r < 16.5) {
                    floor = ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState();
                } else if (n < 0.06F && r > 12) {
                    floor = Blocks.COARSE_DIRT.defaultBlockState();
                } else if (n < 0.14F && r > 12) {
                    floor = Blocks.MOSS_BLOCK.defaultBlockState();
                } else {
                    floor = this.masonry(x, 0, z);
                }
                this.set(x, 0, z, floor);
                if (n > 0.93F && r > 12.5 && r < 15) {
                    this.set(x, 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
        // Broken pillars around the courtyard.
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0 + Math.PI / 12.0;
            int px = CENTER + (int) Math.round(Math.cos(a) * 18.0);
            int pz = CENTER + (int) Math.round(Math.sin(a) * 18.0);
            int height = 2 + (int) (this.noise(px, 7, pz) * 5);
            for (int y = 1; y <= height; y++) {
                this.set(px, y, pz, y == 1 ? ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState() : this.masonry(px, y, pz));
            }
            if (this.noise(px, 9, pz) < 0.55F) {
                this.set(px, height + 1, pz, ModBlocks.STAR_LANTERN.get().defaultBlockState());
            } else {
                this.set(px, height + 1, pz, ModBlocks.ASTRAL_BRICK_SLAB.get().defaultBlockState());
            }
        }
        // Approach steps to the south door.
        for (int x = CENTER - 2; x <= CENTER + 2; x++) {
            this.set(x, 0, CENTER + TOWER_R + 1, ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState());
            this.set(x, 0, CENTER + TOWER_R + 2, ModBlocks.ASTRAL_BRICKS.get().defaultBlockState());
        }
    }

    private void tower() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double r = radius(x, z);
                if (r > TOWER_R + 0.6) {
                    continue;
                }
                double a = angle(x, z);
                boolean wall = r > TOWER_R - 0.5;
                for (int y = 1; y <= ROOF; y++) {
                    int level = y < 9 ? 0 : y < 18 ? 9 : 18;
                    if (wall) {
                        BlockState state = (y % 9 == 0) ? ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState() : this.masonry(x, y, z);
                        int local = y - level;
                        double sector = (a + Math.PI / 8.0) % (Math.PI / 4.0);
                        boolean window = local >= 3 && local <= 5 && Math.abs(sector - Math.PI / 8.0) < 0.12;
                        if (window) {
                            state = ModBlocks.STARGLASS.get().defaultBlockState();
                        }
                        if (local == 6 && Math.abs(sector - Math.PI / 8.0) < 0.09 && (int) Math.round(a / (Math.PI / 4.0)) % 2 == 0) {
                            state = ModBlocks.STAR_LANTERN.get().defaultBlockState();
                        }
                        if (y > 21 && this.noise(x, y, z) < 0.06F) {
                            state = air;
                        }
                        this.set(x, y, z, state);
                    } else {
                        this.set(x, y, z, air);
                    }
                }
                // Floors between levels.
                if (!wall && r > 3.7) {
                    for (int f : new int[]{9, 18, ROOF}) {
                        BlockState floor = (r > 5.5 && r < 6.5) ? ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState() : this.masonry(x, f, z);
                        this.set(x, f, z, floor);
                    }
                }
                if (r <= 3.7) {
                    this.set(x, 0, z, air);
                }
            }
        }
        // Doorway on the south side.
        for (int x = CENTER - 1; x <= CENTER + 1; x++) {
            for (int z = CENTER + TOWER_R - 1; z <= CENTER + TOWER_R + 1; z++) {
                for (int y = 1; y <= 4; y++) {
                    this.set(x, y, z, air);
                }
                this.set(x, 5, z, ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState());
            }
        }
        // Central pillar.
        for (int y = BASEMENT_FLOOR; y <= ROOF; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean chiseled = y % 3 == 0 && (dx == 0 || dz == 0);
                    this.set(CENTER + dx, y, CENTER + dz, chiseled ? ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState()
                        : ModBlocks.ASTRAL_BRICKS.get().defaultBlockState());
                }
            }
        }
    }

    /** Spiral staircase of alternating slabs and full blocks around the central pillar - walkable without jumping. */
    private void staircase() {
        BlockState slab = ModBlocks.ASTRAL_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState full = ModBlocks.ASTRAL_BRICKS.get().defaultBlockState();
        int steps = (ROOF - BASEMENT_FLOOR) * 2 - 2;
        for (int x = CENTER - 4; x <= CENTER + 4; x++) {
            for (int z = CENTER - 4; z <= CENTER + 4; z++) {
                double r = radius(x, z);
                if (r <= 1.6 || r > 3.65) {
                    continue;
                }
                int sector = (int) (angle(x, z) / (Math.PI * 2 / 16));
                for (int rev = 0; rev < 8; rev++) {
                    int i = sector + rev * 16;
                    if (i > steps) {
                        continue;
                    }
                    double h = BASEMENT_FLOOR + 1 + i * 0.5;
                    int y = (int) Math.floor(h);
                    this.set(x, y, z, h == y ? slab : full);
                    for (int clear = 1; clear <= 3; clear++) {
                        int cy = y + clear;
                        if (cy <= ROOF + 1) {
                            this.set(x, cy, z, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    private void greatHall() {
        // Glowing constellation set into the floor.
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            this.set(CENTER + (int) Math.round(Math.cos(a) * 8.0), 0, CENTER + (int) Math.round(Math.sin(a) * 8.0), ModBlocks.STAR_LANTERN.get().defaultBlockState());
        }
        // Four great pillars.
        for (int[] p : new int[][]{{14, 14}, {26, 14}, {14, 26}, {26, 26}}) {
            for (int y = 1; y <= 8; y++) {
                this.set(p[0], y, p[1], y == 8 || y == 1 ? ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState() : ModBlocks.ASTRAL_BRICKS.get().defaultBlockState());
            }
        }
        this.chest(11, 1, CENTER, Direction.EAST, LOOT_COMMON);
        this.chest(CENTER, 1, 11, Direction.SOUTH, LOOT_COMMON);
        this.mimic(29, 1, CENTER, Direction.WEST);
        this.spawner(CENTER - 6, 1, CENTER + 6, ModEntities.ASTRAL_WRAITH.get());
    }

    private void library() {
        BlockState shelf = Blocks.BOOKSHELF.defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double r = radius(x, z);
                if (r > 9.4 && r <= 10.5) {
                    double sector = (angle(x, z) + Math.PI / 8.0) % (Math.PI / 4.0);
                    boolean gap = Math.abs(sector - Math.PI / 8.0) < 0.2;
                    if (!gap) {
                        for (int y = 10; y <= 12; y++) {
                            this.set(x, y, z, this.noise(x, y, z) < 0.12F ? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : shelf);
                        }
                    }
                }
            }
        }
        this.chest(CENTER, 10, 11, Direction.SOUTH, LOOT_LIBRARY);
        this.chest(CENTER + 9, 10, CENTER, Direction.WEST, LOOT_LIBRARY);
        for (int x = CENTER - 1; x <= CENTER + 1; x++) {
            this.set(x, 9, 13, ModBlocks.GRAVITY_RUNE.get().defaultBlockState());
        }
        this.set(CENTER + 7, 9, CENTER, ModBlocks.GRAVITY_RUNE.get().defaultBlockState());
        this.spawner(CENTER - 7, 10, CENTER, ModEntities.ASTRAL_WRAITH.get());
        this.set(CENTER, 10, CENTER + 8, Blocks.ENCHANTING_TABLE.defaultBlockState());
        this.set(CENTER - 6, 10, CENTER - 6, Blocks.LECTERN.defaultBlockState());
    }

    private void armory() {
        this.chest(11, 19, CENTER, Direction.EAST, LOOT_ARMORY);
        this.mimic(29, 19, CENTER, Direction.WEST);
        this.chest(CENTER, 19, 29, Direction.NORTH, LOOT_ARMORY);
        for (int x = CENTER - 1; x <= CENTER + 1; x++) {
            for (int z = 26; z <= 27; z++) {
                this.set(x, 18, z, ModBlocks.STARFIRE_RUNE.get().defaultBlockState());
            }
        }
        this.set(CENTER - 7, 19, CENTER - 5, Blocks.SMITHING_TABLE.defaultBlockState());
        this.set(CENTER - 7, 19, CENTER - 4, Blocks.ANVIL.defaultBlockState());
        this.set(CENTER + 6, 19, CENTER - 6, Blocks.BLAST_FURNACE.defaultBlockState());
    }

    private void roof() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                double r = radius(x, z);
                if (r > TOWER_R - 0.5 && r <= TOWER_R + 0.6) {
                    this.set(x, ROOF + 1, z, ModBlocks.ASTRAL_BRICKS.get().defaultBlockState());
                    this.set(x, ROOF + 2, z, ModBlocks.ASTRAL_BRICK_SLAB.get().defaultBlockState());
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0 + Math.PI / 8.0;
            this.set(CENTER + (int) Math.round(Math.cos(a) * 8.0), ROOF + 1, CENTER + (int) Math.round(Math.sin(a) * 8.0), ModBlocks.STAR_LANTERN.get().defaultBlockState());
        }
        this.set(CENTER, ROOF + 1, CENTER, ModBlocks.CELESTIAL_ALTAR.get().defaultBlockState());
    }

    private void dome() {
        BlockState glass = ModBlocks.STARGLASS.get().defaultBlockState();
        BlockState rib = ModBlocks.ASTRAL_BRICKS.get().defaultBlockState();
        double radius = TOWER_R;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = ROOF + 3; y <= ROOF + 13; y++) {
                    double dx = x - CENTER;
                    double dy = y - (ROOF + 1);
                    double dz = z - CENTER;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < radius - 0.6 || d > radius + 0.5) {
                        continue;
                    }
                    double a = angle(x, z);
                    double sector = a % (Math.PI / 4.0);
                    boolean isRib = sector < 0.13 || sector > Math.PI / 4.0 - 0.13;
                    this.set(x, y, z, isRib ? rib : glass);
                }
            }
        }
        this.set(CENTER, ROOF + 12, CENTER, ModBlocks.STAR_LANTERN.get().defaultBlockState());
    }

    /** A great brass-and-slate telescope aimed at the sky, poking through the dome. */
    private void telescope() {
        Vec3 base = new Vec3(CENTER + 5.5, ROOF + 2.5, CENTER + 5.5);
        Vec3 dir = new Vec3(0.55, 0.75, 0.55).normalize();
        double length = 13.0;
        // Mount.
        for (int y = ROOF + 1; y <= ROOF + 2; y++) {
            this.set(CENTER + 5, y, CENTER + 5, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        }
        for (int x = CENTER + 2; x <= CENTER + 18; x++) {
            for (int z = CENTER + 2; z <= CENTER + 18; z++) {
                for (int y = ROOF + 1; y <= ROOF + 14; y++) {
                    Vec3 p = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    double t = Mth.clamp(p.subtract(base).dot(dir), 0.0, length);
                    double dist = base.add(dir.scale(t)).distanceTo(p);
                    double thickness = 0.9 + t * 0.04;
                    if (dist > thickness) {
                        continue;
                    }
                    BlockState state;
                    if (t > length - 0.8) {
                        state = Blocks.TINTED_GLASS.defaultBlockState();
                    } else if (((int) t) % 4 == 0) {
                        state = Blocks.GOLD_BLOCK.defaultBlockState();
                    } else {
                        state = Blocks.DEEPSLATE_TILES.defaultBlockState();
                    }
                    this.set(x, y, z, state);
                }
            }
        }
    }

    private void basement() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        // Antechamber under the tower.
        for (int x = CENTER - 7; x <= CENTER + 7; x++) {
            for (int z = CENTER - 7; z <= CENTER + 7; z++) {
                double r = radius(x, z);
                if (r > 6.6) {
                    continue;
                }
                for (int y = BASEMENT_FLOOR; y <= -1; y++) {
                    BlockState state;
                    if (y == BASEMENT_FLOOR) {
                        state = r > 4.5 && r < 5.5 ? ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState() : this.masonry(x, y, z);
                    } else if (y == -1) {
                        state = r <= 3.7 ? air : stone;
                    } else if (r > 5.5) {
                        state = this.masonry(x, y, z);
                    } else {
                        state = air;
                    }
                    this.set(x, y, z, state);
                }
            }
        }
        // Trapped corridor north to the vault.
        for (int x = CENTER - 2; x <= CENTER + 2; x++) {
            for (int z = 9; z <= 15; z++) {
                for (int y = BASEMENT_FLOOR; y <= -4; y++) {
                    boolean edge = x == CENTER - 2 || x == CENTER + 2 || y == BASEMENT_FLOOR || y == -4;
                    this.set(x, y, z, edge ? this.masonry(x, y, z) : air);
                }
            }
        }
        for (int x = CENTER - 1; x <= CENTER + 1; x++) {
            this.set(x, BASEMENT_FLOOR, 14, ModBlocks.GRAVITY_RUNE.get().defaultBlockState());
            this.set(x, BASEMENT_FLOOR, 12, ModBlocks.STARFIRE_RUNE.get().defaultBlockState());
            this.set(x, BASEMENT_FLOOR, 11, ModBlocks.GRAVITY_RUNE.get().defaultBlockState());
            for (int y = BASEMENT_FLOOR + 1; y <= BASEMENT_FLOOR + 4; y++) {
                this.set(x, y, 9, ModBlocks.VAULT_SEAL.get().defaultBlockState());
            }
        }
        this.set(CENTER - 2, -7, 13, ModBlocks.STAR_LANTERN.get().defaultBlockState());
        this.set(CENTER + 2, -7, 13, ModBlocks.STAR_LANTERN.get().defaultBlockState());
        // The vault.
        for (int x = 11; x <= 29; x++) {
            for (int z = 0; z <= 8; z++) {
                for (int y = BASEMENT_FLOOR; y <= -3; y++) {
                    boolean shell = x == 11 || x == 29 || z == 0 || y == BASEMENT_FLOOR || y == -3;
                    BlockState state = shell ? this.masonry(x, y, z) : air;
                    if (y == BASEMENT_FLOOR && !shell && (x + z) % 4 == 0) {
                        state = ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState();
                    }
                    if (y == -3 && (x == 14 || x == 26) && (z == 2 || z == 6)) {
                        state = ModBlocks.STAR_LANTERN.get().defaultBlockState();
                    }
                    this.set(x, y, z, state);
                }
                if (z == 8) {
                    for (int y = BASEMENT_FLOOR; y <= -3; y++) {
                        if (x < CENTER - 1 || x > CENTER + 1 || y > BASEMENT_FLOOR + 4 || y == BASEMENT_FLOOR) {
                            this.set(x, y, 8, this.masonry(x, y, 8));
                        }
                    }
                }
            }
        }
        for (int x = CENTER - 1; x <= CENTER + 1; x++) {
            for (int y = BASEMENT_FLOOR + 1; y <= BASEMENT_FLOOR + 4; y++) {
                this.set(x, y, 8, ModBlocks.VAULT_SEAL.get().defaultBlockState());
            }
        }
        for (int[] p : new int[][]{{12, 1}, {28, 1}, {12, 7}, {28, 7}}) {
            for (int y = BASEMENT_FLOOR + 1; y <= -4; y++) {
                this.set(p[0], y, p[1], ModBlocks.CHISELED_ASTRAL_BRICKS.get().defaultBlockState());
            }
        }
        this.chest(13, BASEMENT_FLOOR + 1, 4, Direction.EAST, LOOT_VAULT);
        this.chest(27, BASEMENT_FLOOR + 1, 4, Direction.WEST, LOOT_VAULT);
        this.chest(CENTER, BASEMENT_FLOOR + 1, 1, Direction.SOUTH, LOOT_VAULT);
        this.set(CENTER - 2, BASEMENT_FLOOR + 1, 1, ModBlocks.ASTRAL_CRYSTAL_CLUSTER.get().defaultBlockState());
        this.set(CENTER + 2, BASEMENT_FLOOR + 1, 1, ModBlocks.ASTRAL_CRYSTAL_CLUSTER.get().defaultBlockState());
        this.golem(CENTER, BASEMENT_FLOOR + 1, 4);
    }

    // --- Contents ------------------------------------------------------------------------------------------------

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

    private void mimic(int x, int y, int z, Direction facing) {
        BlockPos pos = this.at(x, y, z);
        if (!this.chunkBox.isInside(pos)) {
            return;
        }
        MimicEntity mimic = ModEntities.MIMIC.get().create(this.level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (mimic != null) {
            mimic.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, facing.toYRot(), 0.0F);
            mimic.disguise(facing.toYRot());
            mimic.setPersistenceRequired();
            this.level.addFreshEntityWithPassengers(mimic);
        }
    }

    private void golem(int x, int y, int z) {
        BlockPos pos = this.at(x, y, z);
        if (!this.chunkBox.isInside(pos)) {
            return;
        }
        AstralGolemEntity golem = ModEntities.ASTRAL_GOLEM.get().create(this.level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (golem != null) {
            golem.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            golem.setDormant(true);
            golem.setPersistenceRequired();
            this.level.addFreshEntityWithPassengers(golem);
        }
    }
}
