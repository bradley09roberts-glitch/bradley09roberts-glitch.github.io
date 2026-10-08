package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * Newcomers living in villages. Every few seconds each player's position is checked against the villages around
 * them; the first time a player walks into a village, it is decided once and for all (by {@code villageSettlerChance})
 * whether newcomers live there, and if so one or two appear near the meeting place (the bell), each with a bed nearby
 * to stand by at night. Each village is remembered by where it started, so it is never decided twice.
 *
 * <p>Only chunks that are already loaded are looked at: the player's own chunk, which lists the villages that reach
 * into it, and the village's starting chunk. Nothing here ever loads a chunk.
 */
final class VillageSettlers {
	private static final int INTERVAL = 100;
	/** A player this close to any part of a village counts as having come to it. */
	private static final int NEAR_PIECE = 4;
	/** How far round the village centre the bell and beds are looked for. */
	private static final int SEARCH = 48;

	private VillageSettlers() {
	}

	/** Called every server tick; works every {@value #INTERVAL} ticks. */
	static void tick(MinecraftServer server) {
		FriendsConfig cfg = FriendsConfig.get();
		if (server.getTickCount() % INTERVAL != 37 || !cfg.allowSettlers || cfg.villageSettlerChance <= 0) {
			return;
		}
		SettlerData data = SettlerData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isSpectator() && player.isAlive()) {
				check(player.level(), player.blockPosition(), data);
			}
		}
	}

	private static void check(ServerLevel level, BlockPos pos, SettlerData data) {
		LevelChunk here = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		if (here == null) {
			return;
		}
		Map<Structure, LongSet> references = here.getAllReferences();
		if (references.isEmpty()) {
			return;
		}
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
			Structure structure = entry.getKey();
			if (!structures.wrapAsHolder(structure).is(StructureTags.VILLAGE)) {
				continue;
			}
			Identifier id = structures.getKey(structure);
			for (long startChunk : entry.getValue()) {
				String key = Camp.dimensionId(level) + "|" + id + "|" + startChunk;
				if (data.villageLookedAt(key)) {
					continue;
				}
				LevelChunk origin = level.getChunkSource().getChunkNow(ChunkPos.getX(startChunk), ChunkPos.getZ(startChunk));
				StructureStart start = origin == null ? null : origin.getStartForStructure(structure);
				if (start == null || !start.isValid() || !inside(start, pos)) {
					continue;
				}
				data.markVillage(key);
				RandomSource random = level.getRandom();
				if (random.nextDouble() < FriendsConfig.get().villageSettlerChance) {
					settle(level, start, random);
				}
			}
		}
	}

	/** True when the player stands in (or right next to) one of the village's pieces. */
	private static boolean inside(StructureStart start, BlockPos pos) {
		for (StructurePiece piece : start.getPieces()) {
			if (piece.getBoundingBox().inflatedBy(NEAR_PIECE).isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	/** One or two strangers by the village's bell (or its centre), each with a bed to stand by at night. */
	private static void settle(ServerLevel level, StructureStart start, RandomSource random) {
		List<StructurePiece> pieces = start.getPieces();
		BlockPos centre = pieces.isEmpty() ? start.getBoundingBox().getCenter() : pieces.get(0).getBoundingBox().getCenter();
		PoiManager pois = level.getPoiManager();
		BlockPos meeting = pois.findClosest(h -> h.is(PoiTypes.MEETING), centre, SEARCH, PoiManager.Occupancy.ANY).orElse(null);
		BlockPos around = meeting != null ? meeting : centre;
		List<BlockPos> beds = pois.getInRange(h -> h.is(PoiTypes.HOME), around, SEARCH, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.sorted(Comparator.comparingDouble(p -> p.distSqr(around)))
			.limit(4)
			.toList();
		int count = random.nextFloat() < 0.4F ? 2 : 1;
		for (int i = 0; i < count; i++) {
			// A little apart from each other, rather than both on the same block.
			BlockPos near = i == 0 ? around : around.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
			BlockPos spot = meeting != null ? Strangers.standingSpot(level, near, 2, 8, 3)
				: Strangers.surfaceSpot(level, near, 8);
			if (spot == null) {
				continue;
			}
			BlockPos night = i < beds.size() ? besideBed(level, beds.get(i)) : null;
			Strangers.spawn(level, spot, Personas.Origin.VILLAGE, random, night);
		}
	}

	/** A spot to stand beside a bed (inside the house), or null if there is no room there. */
	private static @Nullable BlockPos besideBed(ServerLevel level, BlockPos bed) {
		return Strangers.standingSpot(level, bed, 1, 2, 1);
	}
}
