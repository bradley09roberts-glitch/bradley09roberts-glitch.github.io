package io.github.bradley09roberts.hardcorefriends.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;

/**
 * Recognises naturally grown trees. A log counts as natural when its connected trunk stands on soil and touches at
 * least three naturally grown (non-persistent) leaves, and no player-placed (persistent) leaves. Log cabins, log
 * walls and decorative logs have no natural leaves, so they are never felled.
 */
public final class TreeFinder {
	private static final int MAX_LOGS = 48;
	/** Largest radius {@link #nearest} scans. */
	public static final int MAX_RADIUS = 24;
	/** Most trunks {@link #nearest} flood-fills in one call; the rest wait for the next search. */
	private static final int MAX_ANALYSES = 16;
	/** Column offsets within {@link #MAX_RADIUS}, nearest first ({dx, dz, dx² + dz²}). */
	private static final int[][] OFFSETS = columnOffsets();

	/** A tree that may be felled. Logs are sorted bottom-up. */
	public record Tree(BlockPos base, List<BlockPos> logs, int height) {
	}

	private TreeFinder() {
	}

	/** The tallest trunk a friend can fell completely, standing in the stump's place (reach 4.5 from the eyes). */
	public static final int FELLABLE_HEIGHT = 6;

	public static boolean isNaturalTreeLog(ServerLevel level, BlockPos pos) {
		return analyse(level, pos).isPresent();
	}

	/**
	 * Flood-fills the trunk and crown from a log. Returns empty when it is not a natural tree, is too large, or has
	 * player-placed leaves attached.
	 */
	public static Optional<Tree> analyse(ServerLevel level, BlockPos start) {
		return analyse(level, start, Integer.MAX_VALUE, null);
	}

	/** Like {@link #analyse(ServerLevel, BlockPos)}, but empty as soon as the trunk proves taller than {@code maxHeight}. */
	public static Optional<Tree> analyse(ServerLevel level, BlockPos start, int maxHeight) {
		return analyse(level, start, maxHeight, null);
	}

	/**
	 * Flood-fills like {@link #analyse(ServerLevel, BlockPos)}, but gives up as soon as the trunk proves taller than
	 * {@code maxHeight}, and adds every log it visits to {@code visited} (whatever the outcome), so a search can skip
	 * the other columns of a tree it has already judged.
	 */
	private static Optional<Tree> analyse(ServerLevel level, BlockPos start, int maxHeight, @Nullable Set<BlockPos> visited) {
		if (!level.getBlockState(start).is(BlockTags.LOGS)) {
			return Optional.empty();
		}
		Set<BlockPos> logs = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.immutable());
		logs.add(start.immutable());
		if (visited != null) {
			visited.add(start.immutable());
		}
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
								if (visited != null) {
									visited.add(n.immutable());
								}
								// The base is at or below the start, so a log this high makes the tree too tall.
								if (logs.size() > MAX_LOGS || n.getY() - start.getY() + 1 > maxHeight) {
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
		if (!naturalTrunk(level, sorted)) {
			return Optional.empty();
		}
		BlockState below = level.getBlockState(base.below());
		if (!below.is(BlockTags.DIRT) && !below.is(BlockTags.GRASS_BLOCKS) && !below.is(BlockTags.SUPPORTS_VEGETATION)
			&& !below.is(BlockTags.SAND)) {
			return Optional.empty();
		}
		int height = sorted.getLast().getY() - base.getY() + 1;
		return height > maxHeight ? Optional.empty() : Optional.of(new Tree(base, sorted, height));
	}

	/**
	 * Finds the nearest natural tree base (no taller than {@code maxHeight}) around a centre, scanning columns within
	 * a horizontal radius of at most {@value #MAX_RADIUS}, nearest first. Each tree is flood-filled at most once, a
	 * too-tall one is given up on as soon as that shows, and at most {@value #MAX_ANALYSES} trunks are judged per call.
	 */
	public static Optional<Tree> nearest(ServerLevel level, BlockPos centre, int radius, int maxHeight, Predicate<BlockPos> allowed) {
		int r = Math.min(radius, MAX_RADIUS);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		Tree best = null;
		double bestDist = Double.MAX_VALUE;
		Set<BlockPos> seenLogs = new HashSet<>();
		int analysed = 0;
		for (int[] o : OFFSETS) {
			double d = o[2];
			if (d > (double) r * r || d >= bestDist) {
				break; // every later column is further away
			}
			for (int dy = -6; dy <= 8; dy++) {
				m.set(centre.getX() + o[0], centre.getY() + dy, centre.getZ() + o[1]);
				if (!level.isLoaded(m)) {
					break;
				}
				BlockState s = level.getBlockState(m);
				if (s.is(BlockTags.LOGS) && !level.getBlockState(m.below()).is(BlockTags.LOGS)) {
					BlockPos base = m.immutable();
					if (seenLogs.contains(base) || !allowed.test(base)) {
						continue;
					}
					if (analysed++ >= MAX_ANALYSES) {
						return Optional.ofNullable(best);
					}
					Optional<Tree> tree = analyse(level, base, maxHeight, seenLogs);
					if (tree.isPresent()) {
						// From a branch log the flood fill finds the real base, which must pass the same tests.
						Tree t = tree.get();
						double td = Camp.horizontalDistSqr(t.base(), centre);
						if (td < bestDist && (t.base().equals(base) || allowed.test(t.base()))) {
							best = t;
							bestDist = td;
						}
					}
					break;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	private static int[][] columnOffsets() {
		List<int[]> list = new ArrayList<>();
		for (int dx = -MAX_RADIUS; dx <= MAX_RADIUS; dx++) {
			for (int dz = -MAX_RADIUS; dz <= MAX_RADIUS; dz++) {
				int d = dx * dx + dz * dz;
				if (d <= MAX_RADIUS * MAX_RADIUS) {
					list.add(new int[] {dx, dz, d});
				}
			}
		}
		list.sort(Comparator.comparingInt(o -> o[2])); // stable: equal distances keep the west-to-east order
		return list.toArray(new int[0][]);
	}
	/**
	 * Natural trees rise from one trunk, or a 2×2 trunk (dark oak), of upright logs. A row of logs standing side by
	 * side on the ground (a log wall or fence), or logs lying on their side at ground level, is something a player
	 * built, even if a neighbouring tree's leaves touch it.
	 */
	private static boolean naturalTrunk(ServerLevel level, List<BlockPos> sortedLogs) {
		int minY = sortedLogs.getFirst().getY();
		List<BlockPos> bottom = new ArrayList<>();
		for (BlockPos p : sortedLogs) {
			if (p.getY() > minY + 1) {
				break;
			}
			BlockState s = level.getBlockState(p);
			if (s.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
				&& s.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) != net.minecraft.core.Direction.Axis.Y) {
				return false;
			}
			if (p.getY() == minY) {
				bottom.add(p);
			}
		}
		if (bottom.size() == 1) {
			return true;
		}
		if (bottom.size() != 4) {
			return false;
		}
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : bottom) {
			minX = Math.min(minX, p.getX());
			maxX = Math.max(maxX, p.getX());
			minZ = Math.min(minZ, p.getZ());
			maxZ = Math.max(maxZ, p.getZ());
		}
		return maxX - minX == 1 && maxZ - minZ == 1;
	}

}
