package io.github.bradley09roberts.hardcorefriends.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Recognises naturally grown trees. A log counts as natural when its connected trunk stands on soil and touches at
 * least three naturally grown (non-persistent) leaves, and no player-placed (persistent) leaves. Log cabins, log
 * walls and decorative logs have no natural leaves, so they are never felled.
 */
public final class TreeFinder {
	private static final int MAX_LOGS = 48;

	/** A tree that may be felled. Logs are sorted bottom-up. */
	public record Tree(BlockPos base, List<BlockPos> logs, int height) {
	}

	private TreeFinder() {
	}

	public static boolean isNaturalTreeLog(ServerLevel level, BlockPos pos) {
		return analyse(level, pos).isPresent();
	}

	/**
	 * Flood-fills the trunk and crown from a log. Returns empty when it is not a natural tree, is too large, or has
	 * player-placed leaves attached.
	 */
	public static Optional<Tree> analyse(ServerLevel level, BlockPos start) {
		if (!level.getBlockState(start).is(BlockTags.LOGS)) {
			return Optional.empty();
		}
		Set<BlockPos> logs = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.immutable());
		logs.add(start.immutable());
		int naturalLeaves = 0;
		Set<BlockPos> seenLeaves = new HashSet<>();
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos n = p.offset(dx, dy, dz);
						if (!level.isLoaded(n)) {
							return Optional.empty();
						}
						BlockState s = level.getBlockState(n);
						if (s.is(BlockTags.LOGS)) {
							if (logs.add(n.immutable())) {
								if (logs.size() > MAX_LOGS) {
									return Optional.empty();
								}
								queue.add(n.immutable());
							}
						} else if (s.getBlock() instanceof LeavesBlock && seenLeaves.add(n.immutable())) {
							if (s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT)) {
								return Optional.empty();
							}
							naturalLeaves++;
						}
					}
				}
			}
		}
		if (naturalLeaves < 3) {
			return Optional.empty();
		}
		List<BlockPos> sorted = new ArrayList<>(logs);
		sorted.sort(Comparator.comparingInt(BlockPos::getY));
		BlockPos base = sorted.getFirst();
		BlockState below = level.getBlockState(base.below());
		if (!below.is(BlockTags.DIRT) && !below.is(BlockTags.SAND)) {
			return Optional.empty();
		}
		int height = sorted.getLast().getY() - base.getY() + 1;
		return Optional.of(new Tree(base, sorted, height));
	}

	/** Finds the nearest natural tree base around a centre, scanning a bounded horizontal radius. */
	public static Optional<Tree> nearest(ServerLevel level, BlockPos centre, int radius, int maxHeight,
			java.util.function.Predicate<BlockPos> allowed) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		Tree best = null;
		double bestDist = Double.MAX_VALUE;
		Set<BlockPos> checkedBases = new HashSet<>();
		for (int dx = -radius; dx <= radius; dx += 1) {
			for (int dz = -radius; dz <= radius; dz += 1) {
				double d = dx * dx + dz * dz;
				if (d > (double) radius * radius || d >= bestDist) {
					continue;
				}
				for (int dy = -6; dy <= 8; dy++) {
					m.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (!level.isLoaded(m)) {
						break;
					}
					BlockState s = level.getBlockState(m);
					if (s.is(BlockTags.LOGS) && !level.getBlockState(m.below()).is(BlockTags.LOGS)) {
						BlockPos base = m.immutable();
						if (!checkedBases.add(base) || !allowed.test(base)) {
							continue;
						}
						Optional<Tree> tree = analyse(level, base);
						if (tree.isPresent() && tree.get().height() <= maxHeight && tree.get().base().equals(base)) {
							best = tree.get();
							bestDist = d;
						}
						break;
					}
				}
			}
		}
		return Optional.ofNullable(best);
	}
}
