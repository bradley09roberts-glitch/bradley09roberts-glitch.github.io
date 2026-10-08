package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;

/** Small read-only helpers Terra uses to judge the ground. None of them change the world. */
public final class Landscape {
	private Landscape() {
	}

	/** Grass and ferns Terra clears away. Flowers are never weeds. */
	public static boolean isWeed(BlockState s) {
		return s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
			|| s.is(Blocks.SHORT_DRY_GRASS) || s.is(Blocks.TALL_DRY_GRASS);
	}

	/** Weeds or a thin snow layer: things that may sit on top of a path or torch spot and be cleared first. */
	public static boolean isClutter(BlockState s) {
		return isWeed(s) || s.is(Blocks.SNOW);
	}

	/** Grass or plain dirt that a shovel can turn into a dirt path. */
	public static boolean isEarth(BlockState s) {
		return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT);
	}

	/** Empty air or clutter: room to stand or place something. */
	public static boolean isOpen(BlockState s) {
		return s.isAir() || isClutter(s);
	}

	/**
	 * The top block of a column that something could stand on (anything with a collision shape, or fluid), scanning
	 * from {@code topY} down to {@code bottomY}. Plants and air are skipped. Null if nothing was found.
	 */
	public static @Nullable BlockPos ground(ServerLevel level, int x, int z, int topY, int bottomY) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, topY, z);
		if (!level.isLoaded(m)) {
			return null;
		}
		for (int y = topY; y >= bottomY; y--) {
			m.setY(y);
			BlockState s = level.getBlockState(m);
			if (!s.getFluidState().isEmpty() || !s.getCollisionShape(level, m).isEmpty()) {
				return m.immutable();
			}
		}
		return null;
	}

	/** True if a block looks player-made: a build marker the friends did not place themselves. */
	public static boolean isPlayerMade(BlockState s, BlockPos pos, CampData data) {
		return (s.is(ModTags.BUILD_MARKERS) || s.hasBlockEntity()) && !data.isPlacedByFriends(pos);
	}

	/** Horizontal distance to the nearest site origin, optionally only for sites not yet completed. */
	public static double nearestSiteDistance(CampData data, BlockPos pos, boolean unfinishedOnly) {
		double best = Double.MAX_VALUE;
		for (Map.Entry<String, CampData.Site> e : data.sites().entrySet()) {
			if (unfinishedOnly && data.isCompleted(e.getKey())) {
				continue;
			}
			best = Math.min(best, Math.sqrt(Camp.horizontalDistSqr(e.getValue().origin, pos)));
		}
		return best;
	}

	/** True if any block in the square of the given radius around {@code pos} (same y) matches. */
	public static boolean anyNear(ServerLevel level, BlockPos pos, int radius, int dyMin, int dyMax,
			java.util.function.Predicate<BlockState> test) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				for (int dy = dyMin; dy <= dyMax; dy++) {
					m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (level.isLoaded(m) && test.test(level.getBlockState(m))) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
