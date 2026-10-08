package io.github.bradley09roberts.hardcorefriends.ai.role.mine;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import io.github.bradley09roberts.hardcorefriends.camp.CampData;

/**
 * Flint's staircase mine, kept in {@code CampData.memory("flint.mine")} so it survives restarts.
 *
 * <p>Layout: a 1-wide staircase (3 blocks of head room per step, one step down each time) starts at the entrance
 * heading away from the camp. It turns clockwise whenever it would leave the inner part of the mine box, so it
 * spirals down without ever leaving the box. At the bottom a 1×2 corridor runs across the box, with 1×2 branch
 * tunnels of up to {@value #BRANCH_LENGTH} blocks every {@value #BRANCH_SPACING} blocks, alternating sides. Every
 * dug position lies inside the recorded {@value #BOX_SIZE}×{@value #BOX_SIZE} box centred on the entrance.
 */
public final class MinePlan {
	public static final String KEY = "flint.mine";
	public static final int BOX_SIZE = 24;
	/** The box spans entrance − 12 to entrance + 11 on both horizontal axes. */
	public static final int HALF = BOX_SIZE / 2;
	/** Stairs turn before going further than this from the entrance, keeping clear of the box walls. */
	public static final int STAIR_LIMIT = 9;
	public static final int BRANCH_LENGTH = 16;
	public static final int BRANCH_SPACING = 3;
	public static final int CORRIDOR_LENGTH = 22;
	public static final int TORCH_SPACING = 8;
	/** Normal bottom of the mine. */
	public static final int BOTTOM_Y = 16;
	/** Minimum depth below the entrance when the surface is low. */
	public static final int MIN_DEPTH = 12;

	public enum Phase {
		NONE,
		STAIRS,
		BRANCHES,
		DONE
	}

	public enum Kind {
		STAIR,
		CORRIDOR,
		BRANCH
	}

	/**
	 * One unit of digging: stand at {@code stand}, clear {@code clear} (top-down), make sure {@code cell} has a
	 * floor, then record progress.
	 */
	public record Job(Kind kind, BlockPos stand, BlockPos cell, List<BlockPos> clear, Direction dir) {
	}

	private final CampData data;
	private final CompoundTag tag;

	private MinePlan(CampData data, CompoundTag tag) {
		this.data = data;
		this.tag = tag;
	}

	public static MinePlan of(CampData data) {
		return new MinePlan(data, data.memory(KEY));
	}

	// ------------------------------------------------------------------ state

	public boolean exists() {
		return tag.contains("entrance");
	}

	public Phase phase() {
		if (!exists()) {
			return Phase.NONE;
		}
		int p = tag.getIntOr("phase", 0);
		return p == 1 ? Phase.BRANCHES : p == 2 ? Phase.DONE : Phase.STAIRS;
	}

	public BlockPos entrance() {
		return BlockPos.of(tag.getLongOr("entrance", 0L));
	}

	public int bottomY() {
		return tag.getIntOr("bottom", BOTTOM_Y);
	}

	/** The recorded mine box: every block Flint digs or places for this mine lies inside it. */
	public BoundingBox box() {
		int[] b = tag.getIntArray("box").filter(a -> a.length == 6).orElse(new int[6]);
		return new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
	}

	public boolean inBox(BlockPos pos) {
		return exists() && box().isInside(pos);
	}

	public int stepsDug() {
		return tag.getIntOr("steps", 0);
	}

	public long finishedAt() {
		return tag.getLongOr("finishedAt", 0L);
	}

	/** Earlier mine entrances, so a new mine is started somewhere else. */
	public long[] oldEntrances() {
		return tag.getLongArray("old").orElse(new long[0]);
	}

	/** A block in the planned path that needs a better pickaxe than Flint has, or null. */
	public @Nullable BlockPos toolBlocked() {
		return tag.contains("toolBlocked") ? BlockPos.of(tag.getLongOr("toolBlocked", 0L)) : null;
	}

	public void setToolBlocked(@Nullable BlockPos pos) {
		if (pos == null) {
			tag.remove("toolBlocked");
		} else {
			tag.putLong("toolBlocked", pos.asLong());
		}
		data.setDirty();
	}

	public int stuckCount() {
		return tag.getIntOr("stuck", 0);
	}

	public void setStuckCount(int n) {
		tag.putInt("stuck", n);
		data.setDirty();
	}

	/** Starts a new mine at the entrance (feet position), first heading in {@code dir}. */
	public void begin(BlockPos entrance, Direction dir, int bottomY) {
		long[] old = oldEntrances();
		for (String key : List.copyOf(tag.keySet())) {
			tag.remove(key);
		}
		tag.putLongArray("old", old);
		tag.putLong("entrance", entrance.asLong());
		tag.putInt("bottom", bottomY);
		tag.putIntArray("box", new int[] {
			entrance.getX() - HALF, bottomY - 2, entrance.getZ() - HALF,
			entrance.getX() + HALF - 1, entrance.getY() + 3, entrance.getZ() + HALF - 1});
		tag.putInt("phase", 0);
		tag.putLong("stair", entrance.asLong());
		tag.putInt("sdir", dir.get2DDataValue());
		data.setDirty();
	}

	/** Forgets the current mine (remembering its entrance) so a new one can be chosen. */
	public void abandon() {
		long[] old = oldEntrances();
		long[] more = new long[Math.min(8, old.length + (exists() ? 1 : 0))];
		int i = 0;
		if (exists()) {
			more[i++] = entrance().asLong();
		}
		for (long l : old) {
			if (i < more.length) {
				more[i++] = l;
			}
		}
		for (String key : List.copyOf(tag.keySet())) {
			tag.remove(key);
		}
		tag.putLongArray("old", more);
		data.setDirty();
	}

	private void finish(long gameTime) {
		tag.putInt("phase", 2);
		tag.putLong("finishedAt", gameTime);
		data.setDirty();
	}

	// ------------------------------------------------------------------- jobs

	/** The next piece of digging, or null when the mine is finished. May advance the phase. */
	public @Nullable Job nextJob(long gameTime) {
		for (int guard = 0; guard < 6; guard++) {
			switch (phase()) {
				case NONE, DONE -> {
					return null;
				}
				case STAIRS -> {
					Job job = stairJob();
					if (job != null) {
						return job;
					}
				}
				case BRANCHES -> {
					Job job = tunnelJob(gameTime);
					if (job != null) {
						return job;
					}
				}
			}
		}
		return null;
	}

	private @Nullable Job stairJob() {
		BlockPos stair = BlockPos.of(tag.getLongOr("stair", 0L));
		Direction dir = Direction.from2DDataValue(tag.getIntOr("sdir", 0));
		if (stair.getY() <= bottomY()) {
			beginBranches(stair, dir);
			return null;
		}
		BlockPos entrance = entrance();
		for (int turn = 0; turn < 4; turn++) {
			BlockPos next = stair.relative(dir).below();
			if (Math.abs(next.getX() - entrance.getX()) <= STAIR_LIMIT && Math.abs(next.getZ() - entrance.getZ()) <= STAIR_LIMIT) {
				return new Job(Kind.STAIR, stair, next, List.of(next.above(2), next.above(), next), dir);
			}
			dir = dir.getClockWise();
		}
		beginBranches(stair, dir);
		return null;
	}

	private void beginBranches(BlockPos origin, Direction stairDir) {
		Direction best = stairDir;
		int bestRoom = room(origin, stairDir);
		for (Direction d : new Direction[] {stairDir.getClockWise(), stairDir.getCounterClockWise()}) {
			int r = room(origin, d);
			if (r > bestRoom) {
				bestRoom = r;
				best = d;
			}
		}
		tag.putInt("phase", 1);
		tag.putLong("corridor", origin.asLong());
		tag.putInt("cdir", best.get2DDataValue());
		tag.putInt("ck", 0);
		tag.putInt("bk", 0);
		tag.putInt("bj", 0);
		data.setDirty();
	}

	private int room(BlockPos origin, Direction dir) {
		BoundingBox box = box();
		int n = 0;
		while (n < CORRIDOR_LENGTH && box.isInside(origin.relative(dir, n + 1))) {
			n++;
		}
		return n;
	}

	private @Nullable Job tunnelJob(long gameTime) {
		BlockPos origin = BlockPos.of(tag.getLongOr("corridor", 0L));
		Direction dir = Direction.from2DDataValue(tag.getIntOr("cdir", 0));
		int ck = tag.getIntOr("ck", 0);
		int bk = tag.getIntOr("bk", 0);
		if (ck > 0 && ck % BRANCH_SPACING == 0 && bk < ck) {
			Direction side = (ck / BRANCH_SPACING) % 2 == 1 ? dir.getCounterClockWise() : dir.getClockWise();
			BlockPos junction = origin.relative(dir, ck);
			int j = tag.getIntOr("bj", 0) + 1;
			BlockPos cell = junction.relative(side, j);
			if (j > BRANCH_LENGTH || !inBox(cell) || !inBox(cell.above())) {
				endBranch();
				return null;
			}
			return new Job(Kind.BRANCH, junction.relative(side, j - 1), cell, List.of(cell.above(), cell), side);
		}
		int k = ck + 1;
		BlockPos cell = origin.relative(dir, k);
		if (k > CORRIDOR_LENGTH || !inBox(cell) || !inBox(cell.above())) {
			finish(gameTime);
			return null;
		}
		return new Job(Kind.CORRIDOR, origin.relative(dir, k - 1), cell, List.of(cell.above(), cell), dir);
	}

	private void endBranch() {
		tag.putInt("bk", tag.getIntOr("ck", 0));
		tag.putInt("bj", 0);
		data.setDirty();
	}

	/** Records that a job's blocks are cleared and its cell has a floor. */
	public void complete(Job job) {
		switch (job.kind()) {
			case STAIR -> {
				tag.putLong("stair", job.cell().asLong());
				tag.putInt("sdir", job.dir().get2DDataValue());
				tag.putInt("steps", stepsDug() + 1);
				if (job.cell().getY() <= bottomY()) {
					beginBranches(job.cell(), job.dir());
				}
			}
			case CORRIDOR -> tag.putInt("ck", tag.getIntOr("ck", 0) + 1);
			case BRANCH -> tag.putInt("bj", tag.getIntOr("bj", 0) + 1);
		}
		tag.putInt("torch", tag.getIntOr("torch", 0) + 1);
		tag.putInt("stuck", 0);
		data.setDirty();
	}

	/**
	 * A job could not be done safely (the guard refused a block, or there is no floor and nothing to seal it with):
	 * the staircase ends where it is, a branch ends, or the corridor (and the mine) is finished.
	 */
	public void refuse(Job job, long gameTime) {
		switch (job.kind()) {
			case STAIR -> beginBranches(job.stand(), job.dir());
			case BRANCH -> endBranch();
			case CORRIDOR -> finish(gameTime);
		}
	}

	public boolean torchDue() {
		return tag.getIntOr("torch", 0) >= TORCH_SPACING;
	}

	public void torchPlaced() {
		tag.putInt("torch", 0);
		data.setDirty();
	}
}
