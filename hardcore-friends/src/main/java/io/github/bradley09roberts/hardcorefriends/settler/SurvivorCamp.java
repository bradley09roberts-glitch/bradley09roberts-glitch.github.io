package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * The survivor camp: a small camp of one or two tents round a campfire, out in the temperate overworld, where one to
 * three strangers live who may join the team. It is a world structure ({@code hardcorefriends:survivor_camp}), placed
 * by its structure set ({@code hardcorefriends:survivor_camps}) in the biomes of the tag
 * {@code #hardcorefriends:has_structure/survivor_camp}; the data files are under
 * {@code data/hardcorefriends/worldgen}.
 *
 * <p>The camp fits inside one chunk (13 by 13 blocks from the chunk's corner plus one), so it is built in one go. Its
 * floor height is worked out from the terrain before anything is built: the camp is only placed on dry ground that
 * varies by at most three blocks across it, so levelling it never digs into a hillside or builds out over water. The
 * strangers are added by the piece itself as it is built, like a swamp hut's witch.
 */
public final class SurvivorCamp extends Structure {
	public static final MapCodec<SurvivorCamp> CODEC = simpleCodec(SurvivorCamp::new);
	/** The camp's side, in blocks. */
	public static final int SIZE = 13;
	/** The most the ground may vary across the camp. */
	private static final int MAX_SLOPE = 3;

	public static final StructureType<SurvivorCamp> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "survivor_camp"), (StructureType<SurvivorCamp>) () -> CODEC);
	public static final StructurePieceType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "survivor_camp"),
		(StructurePieceType.ContextlessType) SurvivorCampPiece::new);
	/** The chest's loot: a little food, torches and odds and ends. */
	public static final ResourceKey<LootTable> CHEST_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "chests/survivor_camp"));

	public SurvivorCamp(StructureSettings settings) {
		super(settings);
	}

	/** Loads this class, so the structure and piece types are registered before the world's data is read. */
	public static void register() {
		HardcoreFriends.LOGGER.debug("Survivor camps registered: {} {}", TYPE, PIECE);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		if (!context.couldValidBiomeExistOnTopOfChunkCenter()) {
			return Optional.empty(); // cheap check first: no terrain sampling where the biome is wrong anyway
		}
		ChunkPos chunk = context.chunkPos();
		int minX = chunk.getMinBlockX() + 1;
		int minZ = chunk.getMinBlockZ() + 1;
		ChunkGenerator generator = context.chunkGenerator();
		LevelHeightAccessor heights = context.heightAccessor();
		RandomState randomState = context.randomState();
		int[][] samples = {{0, 0}, {SIZE - 1, 0}, {0, SIZE - 1}, {SIZE - 1, SIZE - 1}, {SIZE / 2, SIZE / 2}};
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		int sum = 0;
		for (int[] s : samples) {
			int x = minX + s[0];
			int z = minZ + s[1];
			int ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, randomState);
			int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState);
			if (ground != surface) {
				return Optional.empty(); // water over the ground: no camp in a lake, a river or the sea
			}
			low = Math.min(low, ground);
			high = Math.max(high, ground);
			sum += ground;
		}
		if (high - low > MAX_SLOPE) {
			return Optional.empty(); // too steep to level without scarring the hillside
		}
		int floor = Math.round(sum / (float) samples.length);
		if (floor <= heights.getMinY() + SurvivorCampPiece.FOUNDATION + 1 || floor + SurvivorCampPiece.HEADROOM >= heights.getMaxY()) {
			return Optional.empty();
		}
		BlockPos origin = new BlockPos(minX, floor, minZ);
		int rotation = context.random().nextInt(4);
		int tents = 1 + context.random().nextInt(2);
		int strangers = 1 + context.random().nextInt(3);
		return Optional.of(new GenerationStub(origin.offset(SIZE / 2, 0, SIZE / 2),
			builder -> builder.addPiece(new SurvivorCampPiece(origin, rotation, tents, strangers))));
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}
}
