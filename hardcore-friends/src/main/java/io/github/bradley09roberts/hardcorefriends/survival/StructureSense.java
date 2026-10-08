package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * What a friend on a trip can tell about the land around them: which structures stand nearby and which biome they
 * are in. Structures are read from the chunks that are already loaded around the friend (every chunk remembers which
 * structures reach into it), so nothing is generated or loaded just to look: the same knowledge a player gets by
 * walking past.
 */
public final class StructureSense {
	/** The survivor camps the newcomers' feature adds to world generation (absent without it). */
	public static final ResourceKey<Structure> SURVIVOR_CAMP = ResourceKey.create(Registries.STRUCTURE,
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "survivor_camp"));

	/** A structure found near a friend: the kind of place (a {@link Places} type) and roughly where it is. */
	public record Found(String type, BlockPos pos) {
	}

	private StructureSense() {
	}

	/**
	 * The places worth knowing whose structures reach into the loaded chunks within {@code chunkRadius} chunks of
	 * {@code around}. Each structure is reported at the middle of its bounds when its starting chunk is loaded,
	 * otherwise at the middle of that chunk, at ground level when known.
	 */
	public static List<Found> scan(ServerLevel level, BlockPos around, int chunkRadius) {
		List<Found> found = new ArrayList<>();
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		ChunkPos centre = ChunkPos.containing(around);
		for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
			for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
					if (e.getValue().isEmpty()) {
						continue;
					}
					String type = classify(registry, e.getKey());
					if (type == null) {
						continue;
					}
					LongIterator starts = e.getValue().iterator();
					while (starts.hasNext()) {
						BlockPos pos = position(level, e.getKey(), ChunkPos.unpack(starts.nextLong()));
						if (pos != null && found.stream().noneMatch(f -> f.type().equals(type) && f.pos().distSqr(pos) < 32 * 32)) {
							found.add(new Found(type, pos));
						}
					}
				}
			}
		}
		return found;
	}

	/** The kind of place a structure is, or null when it is not one friends report. */
	private static @Nullable String classify(Registry<Structure> registry, Structure structure) {
		Optional<ResourceKey<Structure>> key = registry.getResourceKey(structure);
		if (key.isEmpty()) {
			return null;
		}
		ResourceKey<Structure> k = key.get();
		if (k.equals(SURVIVOR_CAMP)) {
			return Places.SURVIVOR_CAMP;
		}
		if (k.equals(BuiltinStructures.PILLAGER_OUTPOST)) {
			return Places.OUTPOST;
		}
		if (k.equals(BuiltinStructures.DESERT_PYRAMID) || k.equals(BuiltinStructures.JUNGLE_TEMPLE)) {
			return Places.TEMPLE;
		}
		Holder<Structure> holder = registry.wrapAsHolder(structure);
		if (holder.is(StructureTags.VILLAGE)) {
			return Places.VILLAGE;
		}
		if (holder.is(StructureTags.RUINED_PORTAL)) {
			return Places.RUINED_PORTAL;
		}
		return null;
	}

	/** Where to put a structure on the map: the middle of its bounds, or of its starting chunk. */
	private static @Nullable BlockPos position(ServerLevel level, Structure structure, ChunkPos start) {
		LevelChunk startChunk = level.getChunkSource().getChunkNow(start.x(), start.z());
		if (startChunk != null) {
			StructureStart st = startChunk.getStartForStructure(structure);
			if (st != null && st.isValid()) {
				BoundingBox box = st.getBoundingBox();
				int x = (box.minX() + box.maxX()) / 2;
				int z = (box.minZ() + box.maxZ()) / 2;
				return new BlockPos(x, groundY(level, x, z, box.minY()), z);
			}
			return null; // the chunk is loaded and holds no such structure after all
		}
		int x = start.getMiddleBlockX();
		int z = start.getMiddleBlockZ();
		return new BlockPos(x, groundY(level, x, z, level.getSeaLevel()), z);
	}

	private static int groundY(ServerLevel level, int x, int z, int fallback) {
		return level.hasChunkAt(x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : fallback;
	}

	/** The biome at a position as a {@link Places} type ("biome:minecraft:plains"), or null when unknown. */
	public static @Nullable String biomeType(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return null;
		}
		Holder<Biome> biome = level.getBiome(pos);
		return biome.unwrapKey().map(k -> Places.BIOME + k.identifier()).orElse(null);
	}
}
