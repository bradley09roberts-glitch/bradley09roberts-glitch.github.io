package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/** Static access to the shared camp: where it is, how big it is and what stage it has reached. */
public final class Camp {
	public static final String[] STAGE_NAMES = {"Campsite", "Camp", "Hamlet", "Village", "Settlement"};
	public static final int MAX_STAGE = STAGE_NAMES.length - 1;
	/** Unity needed before each stage's buildings may begin. */
	public static final int[] STAGE_UNITY = {0, 0, 100, 250, 500};

	private Camp() {
	}

	public static CampData data(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(CampData.TYPE);
	}

	public static String dimensionId(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	public static boolean isCampLevel(ServerLevel level, CampData data) {
		return data.campPos().isPresent() && data.campDimension().equals(dimensionId(level));
	}

	public static Optional<BlockPos> center(ServerLevel level) {
		CampData data = data(level.getServer());
		return isCampLevel(level, data) ? data.campPos() : Optional.empty();
	}

	/**
	 * Current camp radius: the configured base, growing 4 blocks per stage, plus any room the camp has grown to fit
	 * a building ({@link #radiusBonus}), up to the configured maximum.
	 */
	public static int radius(CampData data) {
		FriendsConfig cfg = FriendsConfig.get();
		return Math.min(cfg.maxCampRadius, cfg.campRadius + 4 * data.stage() + radiusBonus(data));
	}

	/** Camp memory of the extra room the camp grew to make space for buildings. */
	private static final String ROOM_MEMORY = "survival.camp_room";
	/** How far the camp grows at a time when a building does not fit. */
	public static final int ROOM_STEP = 4;

	/**
	 * Extra blocks of radius the camp grew because a building would not fit anywhere inside it, even on levelled
	 * ground. It belongs to this camp centre only: moving the camp starts again without it.
	 */
	public static int radiusBonus(CampData data) {
		if (data.campPos().isEmpty()) {
			return 0;
		}
		CompoundTag tag = data.memory(ROOM_MEMORY);
		if (tag.getLongOr("centre", Long.MIN_VALUE) != data.campPos().get().asLong()) {
			return 0;
		}
		return Math.max(0, tag.getIntOr("bonus", 0));
	}

	/**
	 * Lets the camp grow by {@value #ROOM_STEP} blocks to make room for a building that fits nowhere inside it.
	 * Returns false when the camp is already as big as the settings allow.
	 */
	public static boolean growForRoom(CampData data) {
		if (data.campPos().isEmpty() || radius(data) >= FriendsConfig.get().maxCampRadius) {
			return false;
		}
		CompoundTag tag = data.memory(ROOM_MEMORY);
		int bonus = radiusBonus(data) + ROOM_STEP;
		tag.putLong("centre", data.campPos().get().asLong());
		tag.putInt("bonus", bonus);
		data.setDirty();
		return true;
	}

	public static String stageName(int stage) {
		return STAGE_NAMES[Math.clamp(stage, 0, MAX_STAGE)];
	}

	/** Horizontal distance squared between two positions. */
	public static double horizontalDistSqr(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** In-game day number, used for daily Unity caps and the return-after-death timer. */
	public static long day(ServerLevel level) {
		return level.getOverworldClockTime() / 24000L;
	}

	/** Time of day in ticks, 0–23999 (0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight). */
	public static long timeOfDay(ServerLevel level) {
		return Math.floorMod(level.getOverworldClockTime(), 24000L);
	}

	/** True from dusk until dawn in dimensions with a day cycle. */
	public static boolean isNight(ServerLevel level) {
		return level.isDarkOutside();
	}

	/** True in the last minute of daylight, when friends start heading home. */
	public static boolean isDusk(ServerLevel level) {
		long t = timeOfDay(level);
		return !level.dimensionType().hasFixedTime() && t >= 11000 && t < 13000;
	}
}
