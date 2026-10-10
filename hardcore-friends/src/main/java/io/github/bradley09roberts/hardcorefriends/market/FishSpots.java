package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * Where to fish: a dry spot to stand on and the open water in front of it. At a fishing hut, its deck's
 * {@code fishing} spots; otherwise the banks near the camp, found by a light survey of the water's surface around the
 * camp (every other column, only in chunks already loaded, never loading one) at most every five minutes. A survey that
 * could not see the banks (their chunks unloaded) keeps the spots already known.
 */
final class FishSpots {
	/** A place to fish from. */
	record Spot(BlockPos stand, BlockPos water) {
	}

	private static final int RESCAN = 6000;
	private static final int MAX_SPOTS = 8;
	/** How far round the camp's edge the survey looks for water. */
	private static final int BEYOND_CAMP = 12;
	private static final int MAX_RADIUS = 56;

	private static List<Spot> campCache = List.of();
	private static long scannedAt = Long.MIN_VALUE / 2;
	private static String scannedFor = "";

	private FishSpots() {
	}

	/** The spots on the banks near the camp (none outside the camp's world). */
	static List<Spot> campSpots(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data) || data.campPos().isEmpty()) {
			return List.of();
		}
		BlockPos centre = data.campPos().get();
		String key = Camp.dimensionId(level) + "@" + centre.asLong();
		long now = level.getGameTime();
		if (key.equals(scannedFor) && now - scannedAt < RESCAN && now >= scannedAt) {
			return campCache;
		}
		int radius = Math.min(MAX_RADIUS, Camp.radius(data) + BEYOND_CAMP);
		boolean[] sawAll = {true};
		List<Spot> found = survey(level, centre, radius, sawAll);
		if (!found.isEmpty() || sawAll[0] || !key.equals(scannedFor)) {
			campCache = List.copyOf(found);
		}
		scannedFor = key;
		scannedAt = now;
		return campCache;
	}

	/** The fishing spots on a workplace's deck (its {@code fishing} markers with water in front). */
	static List<Spot> atWorkplace(ServerLevel level, Workplace w) {
		List<Spot> spots = new ArrayList<>();
		for (BlockPos stand : w.markers(level, "fishing")) {
			BlockPos water = waterNear(level, stand);
			if (water != null) {
				spots.add(new Spot(stand, water));
			}
		}
		return spots;
	}

	static void clear() {
		campCache = List.of();
		scannedAt = Long.MIN_VALUE / 2;
		scannedFor = "";
	}

	private static List<Spot> survey(ServerLevel level, BlockPos centre, int radius, boolean[] sawAll) {
		List<Spot> spots = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius && spots.size() < MAX_SPOTS; dx += 2) {
			for (int dz = -radius; dz <= radius && spots.size() < MAX_SPOTS; dz += 2) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					sawAll[0] = false;
					continue;
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				if (Math.abs(top - centre.getY()) > 16) {
					continue; // a lake in a ravine or up a mountain is not the camp's bank
				}
				m.set(x, top, z);
				if (!openWater(level, m)) {
					continue;
				}
				BlockPos stand = standBy(level, m);
				if (stand != null && !tooClose(spots, stand)) {
					spots.add(new Spot(stand, m.immutable()));
				}
			}
		}
		return spots;
	}

	private static boolean tooClose(List<Spot> spots, BlockPos stand) {
		for (Spot s : spots) {
			if (s.stand().distManhattan(stand) < 6) {
				return true;
			}
		}
		return false;
	}

	/** A still water source with air above it. */
	static boolean openWater(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		FluidState fluid = level.getFluidState(pos);
		return fluid.is(FluidTags.WATER) && fluid.isSource() && level.getBlockState(pos.above()).isAir();
	}

	/** The nearest open water in front of a deck spot: up to three blocks out and three down. */
	static @Nullable BlockPos waterNear(ServerLevel level, BlockPos stand) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(stand.offset(-3, -3, -3), stand.offset(3, 0, 3))) {
			if (openWater(level, p)) {
				double d = p.distSqr(stand);
				if (d < bestDist) {
					bestDist = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** Dry ground right beside the water to stand on (firm underfoot, two blocks of air), or null. */
	private static @Nullable BlockPos standBy(ServerLevel level, BlockPos water) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int up = 1; up <= 2; up++) {
				BlockPos feet = water.relative(d).above(up);
				if (standable(level, feet)) {
					return feet;
				}
			}
		}
		return null;
	}

	static boolean standable(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet)) {
			return false;
		}
		BlockState under = level.getBlockState(feet.below());
		BlockState at = level.getBlockState(feet);
		BlockState head = level.getBlockState(feet.above());
		return under.isFaceSturdy(level, feet.below(), Direction.UP) && under.getFluidState().isEmpty()
			&& at.getCollisionShape(level, feet).isEmpty() && at.getFluidState().isEmpty()
			&& head.getCollisionShape(level, feet.above()).isEmpty() && head.getFluidState().isEmpty()
			&& !under.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
	}

	/** True if a spot is within the friends' reach of the camp (the camp and its gathering ring). */
	static boolean inRange(ServerLevel level, BlockPos pos) {
		CampData data = Camp.data(level.getServer());
		if (data.campPos().isEmpty()) {
			return false;
		}
		int r = Camp.radius(data) + Math.min(FriendsConfig.get().resourceRadius, BEYOND_CAMP);
		return Camp.horizontalDistSqr(data.campPos().get(), pos) <= (double) r * r;
	}
}
