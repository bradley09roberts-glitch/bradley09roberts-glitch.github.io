package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The layout of the farm fence: a rectangle one block outside the bounding box of the friends' own farmland around
 * Fern's farm plot (or around the camp centre when there is no plot yet), with one gate on the side facing the camp
 * centre, or where a planned path crosses the line. A player's fields are never fenced, and no post goes on a
 * player's path or within two blocks of anything a player built. Read-only: it only looks at the world.
 */
public final class FarmFence {
	/** How far from the farm plot (or the camp centre) farmland is looked for. Bounds the fence to 23×23. */
	public static final int SEARCH_RADIUS = 10;
	private static final int SEARCH_DY = 3;
	private static final int MIN_FARMLAND = 4;
	/** How close to a player's build a fence post may go: never within two blocks. */
	private static final int MARKER_GAP = 2;

	/** State of one fence cell. */
	public enum Status {
		/** A fence or gate already stands there. */
		DONE,
		/** Open ground where a fence can go. */
		TODO,
		/** Water, a wall, a tree, a player's path or build nearby, or anything else that is left alone. */
		BLOCKED
	}

	/** One place on the fence line: where the fence goes, which way is outside, and whether it is the gate. */
	public record Cell(BlockPos pos, Direction outward, boolean gate) {
	}

	/** The whole fence line, in walking order around the farm. */
	public record Plan(List<Cell> cells, int farmland) {
		public @Nullable Cell gate() {
			for (Cell cell : cells) {
				if (cell.gate()) {
					return cell;
				}
			}
			return null;
		}
	}

	private FarmFence() {
	}

	/** The friends' own farmland found around the farm: how much, its bounding box, and whether all of it was loaded. */
	private record Farmland(int count, int minX, int maxX, int minZ, int maxZ, long sumY, boolean complete) {
	}

	/** Looks for farmland the friends tilled themselves; a player's farmland is theirs and is not counted. */
	private static @Nullable Farmland scan(ServerLevel level, CampData data) {
		BlockPos centre = data.campPos().orElse(null);
		if (centre == null) {
			return null;
		}
		BlockPos anchor = data.site(Structures.FARM_PLOT).map(site -> site.origin).orElse(centre);
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		int count = 0;
		long sumY = 0;
		boolean complete = true;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
				m.set(anchor.getX() + dx, anchor.getY(), anchor.getZ() + dz);
				if (!level.isLoaded(m)) {
					complete = false;
					continue;
				}
				for (int dy = SEARCH_DY; dy >= -SEARCH_DY; dy--) {
					m.setY(anchor.getY() + dy);
					if (level.getBlockState(m).is(Blocks.FARMLAND)) {
						if (data.isPlacedByFriends(m)) {
							minX = Math.min(minX, m.getX());
							maxX = Math.max(maxX, m.getX());
							minZ = Math.min(minZ, m.getZ());
							maxZ = Math.max(maxZ, m.getZ());
							sumY += m.getY();
							count++;
						}
						break;
					}
				}
			}
		}
		return new Farmland(count, minX, maxX, minZ, maxZ, sumY, complete);
	}

	/**
	 * True when the whole area around the farm is loaded and holds too little farmland of the friends' own to fence:
	 * the camp's fields are the player's, and fencing them is the player's business.
	 */
	public static boolean nothingToFence(ServerLevel level, CampData data) {
		Farmland farm = scan(level, data);
		return farm != null && farm.complete() && farm.count() < MIN_FARMLAND;
	}

	/** Plans the fence, or returns null when there is no farm to fence (fewer than 4 farmland blocks of the friends). */
	public static @Nullable Plan plan(ServerLevel level, CampData data) {
		Farmland farm = scan(level, data);
		BlockPos centre = data.campPos().orElse(null);
		if (farm == null || centre == null || farm.count() < MIN_FARMLAND) {
			return null;
		}
		int count = farm.count();
		int farmY = (int) Math.round(farm.sumY() / (double) count);
		List<Cell> cells = new ArrayList<>();
		int x0 = farm.minX() - 1;
		int x1 = farm.maxX() + 1;
		int z0 = farm.minZ() - 1;
		int z1 = farm.maxZ() + 1;
		// Clockwise seen from above: north edge west to east, east edge, south edge east to west, west edge.
		for (int x = x0; x <= x1; x++) {
			addCell(level, cells, x, z0, farmY, Direction.NORTH);
		}
		for (int z = z0 + 1; z <= z1; z++) {
			addCell(level, cells, x1, z, farmY, Direction.EAST);
		}
		for (int x = x1 - 1; x >= x0; x--) {
			addCell(level, cells, x, z1, farmY, Direction.SOUTH);
		}
		for (int z = z1 - 1; z > z0; z--) {
			addCell(level, cells, x0, z, farmY, Direction.WEST);
		}
		int gateIndex = gateIndex(level, data, cells, centre, x0, x1, z0, z1);
		if (gateIndex >= 0) {
			Cell g = cells.get(gateIndex);
			cells.set(gateIndex, new Cell(g.pos(), g.outward(), true));
		}
		return new Plan(List.copyOf(cells), count);
	}

	/** Finds the fence height in one column: on the ground, or the fence block already standing there. */
	private static void addCell(ServerLevel level, List<Cell> cells, int x, int z, int farmY, Direction outward) {
		BlockPos ground = Landscape.ground(level, x, z, farmY + 2, farmY - 3);
		if (ground == null) {
			return;
		}
		BlockState state = level.getBlockState(ground);
		BlockPos pos = isFence(state) ? ground : ground.above();
		cells.add(new Cell(pos, outward, false));
	}

	/**
	 * The gate goes where a planned path crosses the line, otherwise on the side nearest the camp centre. A cell that
	 * is left alone (water, a tree, a wall) cannot hold the gate, so the gate goes to the next best open cell.
	 */
	private static int gateIndex(ServerLevel level, CampData data, List<Cell> cells, BlockPos centre, int x0, int x1, int z0,
		int z1) {
		double[] rank = new double[cells.size()];
		List<Integer> candidates = new ArrayList<>();
		for (int i = 0; i < cells.size(); i++) {
			BlockPos p = cells.get(i).pos();
			boolean corner = (p.getX() == x0 || p.getX() == x1) && (p.getZ() == z0 || p.getZ() == z1);
			if (corner) {
				continue;
			}
			rank[i] = Camp.horizontalDistSqr(p, centre) - (PathPlan.onPath(data, p) ? 1_000_000 : 0);
			candidates.add(i);
		}
		candidates.sort(Comparator.comparingDouble(i -> rank[i]));
		for (int i : candidates) {
			if (status(level, data, cells.get(i)) != Status.BLOCKED) {
				return i;
			}
		}
		return candidates.isEmpty() ? -1 : candidates.getFirst();
	}

	public static boolean isFence(BlockState state) {
		return state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES);
	}

	/**
	 * Whether a cell already has its fence, still needs one, or is left alone. A cell on a player's path, or within
	 * two blocks of anything a player built (their farmland and paths included), is left alone.
	 */
	public static Status status(ServerLevel level, CampData data, Cell cell) {
		BlockPos pos = cell.pos();
		if (!level.isLoaded(pos)) {
			return Status.BLOCKED;
		}
		BlockState state = level.getBlockState(pos);
		if (isFence(state)) {
			return Status.DONE;
		}
		if (!(state.isAir() || WorldEditGuard.isClearablePlant(state)) || !state.getFluidState().isEmpty()) {
			return Status.BLOCKED;
		}
		BlockPos below = pos.below();
		BlockState ground = level.getBlockState(below);
		boolean firm = ground.isFaceSturdy(level, below, Direction.UP) || ground.is(Blocks.DIRT_PATH);
		if (!ground.getFluidState().isEmpty() || !firm) {
			return Status.BLOCKED;
		}
		if (nearPlayerBuild(level, data, pos)) {
			return Status.BLOCKED;
		}
		return Status.TODO;
	}

	/**
	 * True if a build marker or block entity the friends did not place is within {@value #MARKER_GAP} blocks. The
	 * linked supply chest is camp property and does not count.
	 */
	private static boolean nearPlayerBuild(ServerLevel level, CampData data, BlockPos pos) {
		List<BlockPos> chest = SiteFinder.chestHalves(level, data);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -MARKER_GAP; dx <= MARKER_GAP; dx++) {
			for (int dy = -MARKER_GAP; dy <= MARKER_GAP; dy++) {
				for (int dz = -MARKER_GAP; dz <= MARKER_GAP; dz++) {
					m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (level.isLoaded(m) && Landscape.isPlayerMade(level.getBlockState(m), m, data) && !chest.contains(m)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
