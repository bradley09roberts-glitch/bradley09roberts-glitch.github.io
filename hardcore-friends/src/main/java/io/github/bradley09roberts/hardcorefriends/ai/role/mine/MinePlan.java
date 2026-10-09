package io.github.bradley09roberts.hardcorefriends.ai.role.mine;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;

/**
 * Flint's staircase mine, kept in {@code CampData.memory("flint.mine")} so it survives restarts.
 *
 * <p>Layout: a 1-wide staircase (3 blocks of head room per step, one step down each time) starts at the entrance
 * heading away from the camp. It turns clockwise whenever it would leave the inner part of the mine box, so it
 * spirals down without ever leaving the box. At the bottom a 1×2 corridor runs across the box, with 1×2 branch
 * tunnels of up to {@value #BRANCH_LENGTH} blocks every {@value #BRANCH_SPACING} blocks, alternating sides. Every
 * dug position lies inside the recorded {@value #BOX_SIZE}×{@value #BOX_SIZE} box centred on the entrance.
 *
 * <p>The staircase is the way back up, so it is never undermined: a step never heads back the way the last step came
 * (that would dig out the floor of the step above and leave a climb of two or three blocks), and neither does the
 * corridor. Every block a friend walks on (the entrance, each step, corridor and branch block) is recorded, and
 * {@link #isWalkwayFloor} tells the miners never to dig out the floor under one.
 */
public final class MinePlan {
	public static final String KEY = "flint.mine";
	/** Memory key of the deep branch mine at diamond level (package progress), laid out the same way. */
	public static final String DEEP_KEY = "progress.deep_mine";
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
	/** At most this many walkway blocks are remembered (far more than a mine has). */
	private static final int MAX_WALK = 1024;

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
		return of(data, KEY);
	}

	/** A mine kept under another memory key: the deep branch mine at diamond level (package progress) uses the same layout. */
	public static MinePlan of(CampData data, String key) {
		return new MinePlan(data, data.memory(key));
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

	/** The dimension the mine was dug in ({@code ""} for mines planned before this was recorded). */
	public String dimension() {
		return tag.getStringOr("dim", "");
	}

	/**
	 * True when the mine lies in this level. A mine from another dimension is left alone there: its coordinates mean
	 * nothing in this level, so it is neither worked nor abandoned.
	 */
	public boolean isIn(ServerLevel level) {
		String dim = dimension();
		return dim.isEmpty() || dim.equals(Camp.dimensionId(level));
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

	/** The last step of the staircase dug so far (the top of the corridor once branching has begun). */
	public BlockPos stairEnd() {
		return BlockPos.of(tag.getLongOr("stair", tag.getLongOr("entrance", 0L)));
	}

	/**
	 * The way the staircase was heading at its last step dug (its first heading before any). The next step may try
	 * another way round, but never the opposite of this one.
	 */
	public Direction stairDir() {
		return Direction.from2DDataValue(tag.getIntOr("ldir", tag.getIntOr("sdir", 0)));
	}

	/** True when a friend walks here in this mine: the entrance, a stair step, or a corridor or branch block. */
	public boolean isWalkway(BlockPos feet) {
		if (!exists()) {
			return false;
		}
		long l = feet.asLong();
		for (long w : walkway()) {
			if (w == l) {
				return true;
			}
		}
		return false;
	}

	private long[] walkway() {
		return tag.getLongArray("walk").orElse(new long[0]);
	}

	private void addWalk(BlockPos feet) {
		if (isWalkway(feet)) {
			return;
		}
		long[] old = walkway();
		int keep = Math.min(old.length, MAX_WALK - 1);
		long[] walk = new long[keep + 1];
		System.arraycopy(old, old.length - keep, walk, 0, keep);
		walk[keep] = feet.asLong();
		tag.putLongArray("walk", walk);
	}

	/**
	 * True when digging this block out would take the floor from under a walkway of one of the camp's mines in this
	 * level (the entrance, a stair step, a corridor or branch block). Such a block is never dug: a step whose floor is
	 * gone is two or three blocks high, and whoever is below could not climb back up.
	 */
	public static boolean isWalkwayFloor(ServerLevel level, CampData data, BlockPos pos) {
		BlockPos feet = pos.above();
		for (String key : new String[] {KEY, DEEP_KEY}) {
			MinePlan plan = of(data, key);
			if (plan.isIn(level) && plan.isWalkway(feet)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True when this block is the open space of a walkway of one of the camp's mines in this level (the block a friend
	 * stands in, or one of the two above it): never filled in, or the way would be blocked.
	 */
	public static boolean isWalkwaySpace(ServerLevel level, CampData data, BlockPos pos) {
		for (String key : new String[] {KEY, DEEP_KEY}) {
			MinePlan plan = of(data, key);
			if (plan.isIn(level) && (plan.isWalkway(pos) || plan.isWalkway(pos.below()) || plan.isWalkway(pos.below(2)))) {
				return true;
			}
		}
		return false;
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

	/** Starts a new mine at the entrance (feet position) in the given dimension, first heading in {@code dir}. */
	public void begin(BlockPos entrance, Direction dir, int bottomY, String dimension) {
		long[] old = oldEntrances();
		for (String key : List.copyOf(tag.keySet())) {
			tag.remove(key);
		}
		tag.putLongArray("old", old);
		tag.putLong("entrance", entrance.asLong());
		tag.putString("dim", dimension);
		tag.putInt("bottom", bottomY);
		tag.putIntArray("box", new int[] {
			entrance.getX() - HALF, bottomY - 2, entrance.getZ() - HALF,
			entrance.getX() + HALF - 1, entrance.getY() + 3, entrance.getZ() + HALF - 1});
		tag.putInt("phase", 0);
		tag.putLong("stair", entrance.asLong());
		tag.putInt("sdir", dir.get2DDataValue());
		tag.putInt("ldir", dir.get2DDataValue());
		addWalk(entrance);
		data.setDirty();
	}

	/**
	 * Starts this plan as the continuation of another mine's staircase: the same entrance and box (reaching down to
	 * {@code bottomY}), carrying on down from that mine's last step. That mine's stairs are the way back up, so its
	 * walkways are remembered here too.
	 */
	public void beginBelow(MinePlan above, int bottomY) {
		// Down at right angles to the corridor above (and never back under the stairs above), so the new steps cut
		// through none of that mine's corridor or branches on their way below them.
		Direction last = above.stairDir();
		Direction corridor = Direction.from2DDataValue(above.tag.getIntOr("cdir", last.get2DDataValue()));
		Direction dir = corridor.getClockWise();
		if (dir == last.getOpposite()) {
			dir = corridor.getCounterClockWise();
		}
		begin(above.entrance(), dir, bottomY, above.dimension());
		tag.putLong("stair", above.stairEnd().asLong());
		tag.putInt("steps", above.stepsDug());
		// The first step never heads back under the last step above, nor along the corridor above, whichever way a
		// refused step turns.
		tag.putInt("ldir", last.get2DDataValue());
		tag.putInt("fdir", corridor.get2DDataValue());
		for (long w : above.walkway()) {
			addWalk(BlockPos.of(w));
		}
		addWalk(above.stairEnd());
		data.setDirty();
	}

	/**
	 * True when the staircase may head this way from its last step: never back the way that step came (it would dig
	 * out the floor of the step above), and for the first step below another mine never along that mine's corridor.
	 */
	private boolean mayHead(Direction dir) {
		if (dir == stairDir().getOpposite()) {
			return false;
		}
		return !tag.contains("fdir") || dir.get2DDataValue() != tag.getIntOr("fdir", 0);
	}

	/**
	 * A stair step could not be dug safely (water or lava behind it, loose gravel above): the staircase tries the next
	 * way round instead (never back the way it came), and only after three refusals without a step dug does it stop
	 * and branch where it is.
	 */
	public void turnStair(Job job) {
		int turns = tag.getIntOr("turns", 0) + 1;
		if (turns >= 3) {
			tag.remove("turns");
			beginBranches(job.stand());
			return;
		}
		Direction next = job.dir().getClockWise();
		for (int i = 0; i < 3 && !mayHead(next); i++) {
			next = next.getClockWise();
		}
		tag.putInt("turns", turns);
		tag.putInt("sdir", next.get2DDataValue());
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
			beginBranches(stair);
			return null;
		}
		BlockPos entrance = entrance();
		for (int turn = 0; turn < 4; turn++) {
			BlockPos next = stair.relative(dir).below();
			if (mayHead(dir) && Math.abs(next.getX() - entrance.getX()) <= STAIR_LIMIT
				&& Math.abs(next.getZ() - entrance.getZ()) <= STAIR_LIMIT) {
				return new Job(Kind.STAIR, stair, next, List.of(next.above(2), next.above(), next), dir);
			}
			dir = dir.getClockWise();
		}
		beginBranches(stair);
		return null;
	}

	/**
	 * The corridor starts from the last step, straight on or to either side, whichever has most room in the box, but
	 * never back under the stairs.
	 */
	private void beginBranches(BlockPos origin) {
		Direction last = stairDir();
		Direction best = null;
		int bestRoom = -1;
		for (Direction d : new Direction[] {last, last.getClockWise(), last.getCounterClockWise()}) {
			if (!mayHead(d)) {
				continue;
			}
			int r = room(origin, d);
			if (r > bestRoom) {
				bestRoom = r;
				best = d;
			}
		}
		if (best == null) {
			best = last.getClockWise(); // never happens: at most one of the three is barred
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
				tag.putInt("ldir", job.dir().get2DDataValue());
				tag.putInt("steps", stepsDug() + 1);
				tag.remove("turns");
				tag.remove("fdir");
				if (job.cell().getY() <= bottomY()) {
					beginBranches(job.cell());
				}
			}
			case CORRIDOR -> tag.putInt("ck", tag.getIntOr("ck", 0) + 1);
			case BRANCH -> tag.putInt("bj", tag.getIntOr("bj", 0) + 1);
		}
		addWalk(job.cell());
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
			case STAIR -> beginBranches(job.stand());
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
