package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * The camp-wide farm survey, one for the whole team: it counts the farmland and finds the surface water inside the
 * camp, a few hundred columns at a time. Whoever does farm work steps it along, but it moves on at most once every
 * {@value #STEP_INTERVAL} ticks, so nine friends cost no more than one farmer. Newly tilled farmland and newly
 * poured water are noted straight away, so every tiller sees the same count (and the stage's farmland cap holds).
 *
 * <p>There is one survey per dimension, kept in the {@link TeamCache}, so a camp reset starts it afresh.
 */
public final class CampSurvey {
	private static final int COLUMNS_PER_STEP = 600;
	private static final int STEP_INTERVAL = 20;
	private static final int MAX_WATER = 64;

	// one pass covers every column of the camp disc
	private int cursor;
	private int radius = -1;
	private @Nullable BlockPos centre;
	private int passFarmland;
	private long passSumX;
	private long passSumZ;
	private final List<BlockPos> passWater = new ArrayList<>();
	private int farmland = -1;
	private @Nullable BlockPos farmlandCentre;
	private List<BlockPos> water = List.of();
	private long lastStep = Long.MIN_VALUE / 2;

	private CampSurvey() {
	}

	/** The team's survey of the camp in this level's dimension. */
	public static CampSurvey of(ServerLevel level) {
		return TeamCache.get(level, "farm.survey", CampSurvey::new);
	}

	/** Surveys the next few hundred surface columns, unless the survey moved on less than a second ago. */
	public void step(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		if (now >= lastStep && now - lastStep < STEP_INTERVAL) {
			return; // several routines, or several friends, may ask in the same second
		}
		lastStep = now;
		CampData data = Camp.data(level.getServer());
		BlockPos home = c.homePos();
		int r = Camp.radius(data);
		if (centre == null || !centre.equals(home) || radius != r) {
			centre = home;
			radius = r;
			restartPass();
			farmland = -1;
		}
		int side = 2 * r + 1;
		int total = side * side;
		int budget = COLUMNS_PER_STEP;
		while (budget > 0 && cursor < total) {
			int dx = cursor % side - r;
			int dz = cursor / side - r;
			cursor++;
			if (dx * dx + dz * dz > r * r) {
				continue;
			}
			budget--;
			int x = home.getX() + dx;
			int z = home.getZ() + dz;
			if (!level.hasChunkAt(x, z)) {
				continue;
			}
			int y = Ground.surfaceY(level, x, z, home.getY());
			if (y == Ground.NONE) {
				continue;
			}
			BlockPos top = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(top);
			if (state.is(Blocks.FARMLAND)) {
				passFarmland++;
				passSumX += x;
				passSumZ += z;
			} else if (Crops.isWaterSource(state) && passWater.size() < MAX_WATER) {
				passWater.add(top);
			}
		}
		if (cursor >= total) {
			farmland = passFarmland;
			farmlandCentre = passFarmland > 0
				? new BlockPos((int) Math.floorDiv(passSumX, passFarmland), home.getY(), (int) Math.floorDiv(passSumZ, passFarmland))
				: null;
			if (farmlandCentre != null) {
				int fy = Ground.surfaceY(level, farmlandCentre.getX(), farmlandCentre.getZ(), home.getY());
				farmlandCentre = new BlockPos(farmlandCentre.getX(), fy == Ground.NONE ? home.getY() : fy + 1, farmlandCentre.getZ());
			}
			water = List.copyOf(passWater);
			restartPass();
		}
	}

	private void restartPass() {
		cursor = 0;
		passFarmland = 0;
		passSumX = 0;
		passSumZ = 0;
		passWater.clear();
	}

	/** Farmland counted in the camp by the last complete pass, or -1 before the first pass finishes. */
	public int farmland() {
		return farmland;
	}

	/** The middle of the camp's farmland by the last complete pass, or null. */
	public @Nullable BlockPos farmlandCentre() {
		return farmlandCentre;
	}

	/** Surface water sources found in the camp by the last complete pass. */
	public List<BlockPos> water() {
		return water;
	}

	/** Counts newly tilled farmland straight away, so the stage cap holds between passes. */
	public void noteTilled(int n) {
		if (farmland >= 0) {
			farmland += n;
		}
	}

	/** Records water poured for the farm, so the farm plot is known before the next pass. */
	public void noteWater(BlockPos pos) {
		List<BlockPos> list = new ArrayList<>(water);
		list.add(pos.immutable());
		water = List.copyOf(list);
	}
}
