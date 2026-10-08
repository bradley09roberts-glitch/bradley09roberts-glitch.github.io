package io.github.bradley09roberts.hardcorefriends.camp.build;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Small, bounded lookups for camp furniture such as the crafting table or a furnace beside the supply chest. */
public final class CampFeatures {
	private CampFeatures() {
	}

	/**
	 * The nearest block matching {@code filter} within {@code radius} blocks horizontally and two vertically of
	 * {@code centre}, or null. At most (2r+1)² × 5 blocks are read.
	 */
	public static @Nullable BlockPos find(ServerLevel level, BlockPos centre, int radius, Predicate<BlockState> filter) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, 2, radius))) {
			if (level.isLoaded(p) && filter.test(level.getBlockState(p))) {
				double d = p.distSqr(centre);
				if (d < bestDist) {
					bestDist = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}
}
