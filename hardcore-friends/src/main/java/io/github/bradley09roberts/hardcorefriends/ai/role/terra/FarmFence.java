package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.ArrayList;
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
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The layout of the farm fence: a rectangle one block outside the bounding box of the farmland around Fern's farm
 * plot (or around the camp centre when there is no plot yet), with one gate on the side facing the camp centre, or
 * where a planned path crosses the line. Read-only: it only looks at the world.
 */
public final class FarmFence {
	/** How far from the farm plot (or the camp centre) farmland is looked for. Bounds the fence to 23×23. */
	public static final int SEARCH_RADIUS = 10;
	private static final int SEARCH_DY = 3;
	private static final int MIN_FARMLAND = 4;

	/** State of one fence cell. */
	public enum Status {
		/** A fence or gate already stands there. */
		DONE,
		/** Open ground where a fence can go. */
		TODO,
		/** Water, a wall, a tree or anything else that is left alone. */
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

	/** Plans the fence, or returns null when there is no farm to fence (fewer than 4 farmland blocks). */
	public static @Nullable Plan plan(ServerLevel level, CampData data) {
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
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
				m.set(anchor.getX() + dx, anchor.getY(), anchor.getZ() + dz);
				if (!level.isLoaded(m)) {
					continue;
				}
				for (int dy = SEARCH_DY; dy >= -SEARCH_DY; dy--) {
					m.setY(anchor.getY() + dy);
					if (level.getBlockState(m).is(Blocks.FARMLAND)) {
						minX = Math.min(minX, m.getX());
						maxX = Math.max(maxX, m.getX());
						minZ = Math.min(minZ, m.getZ());
						maxZ = Math.max(maxZ, m.getZ());
						sumY += m.getY();
						count++;
						break;
					}
				}
			}
		}
		if (count < MIN_FARMLAND) {
			return null;
		}
		int farmY = (int) Math.round(sumY / (double) count);
		List<Cell> cells = new ArrayList<>();
		int x0 = minX - 1;
		int x1 = maxX + 1;
		int z0 = minZ - 1;
		int z1 = maxZ + 1;
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
		int gateIndex = gateIndex(data, cells, centre, x0, x1, z0, z1);
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

	/** The gate goes where a planned path crosses the line, otherwise on the side nearest the camp centre. */
	private static int gateIndex(CampData data, List<Cell> cells, BlockPos centre, int x0, int x1, int z0, int z1) {
		int best = -1;
		double bestRank = Double.MAX_VALUE;
		for (int i = 0; i < cells.size(); i++) {
			BlockPos p = cells.get(i).pos();
			boolean corner = (p.getX() == x0 || p.getX() == x1) && (p.getZ() == z0 || p.getZ() == z1);
			if (corner) {
				continue;
			}
			double rank = Camp.horizontalDistSqr(p, centre) - (PathPlan.onPath(data, p) ? 1_000_000 : 0);
			if (rank < bestRank) {
				bestRank = rank;
				best = i;
			}
		}
		return best;
	}

	public static boolean isFence(BlockState state) {
		return state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES);
	}

	/** Whether a cell already has its fence, still needs one, or is left alone. */
	public static Status status(ServerLevel level, Cell cell) {
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
		return Status.TODO;
	}
}
