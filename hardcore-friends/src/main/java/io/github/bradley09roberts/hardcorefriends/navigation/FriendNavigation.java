package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * How friends find their way: vanilla ground navigation with {@link FriendNodeEvaluator}'s sense of danger, a longer
 * reach ({@value #PATH_LENGTH} blocks of path, so the survival package's 20-block trip legs fit with room for detours,
 * still with a bounded number of spots looked at), and two rules for a way that cannot reach its goal:
 *
 * <ul>
 * <li>On a walk between two places on the surface, a way that would go into a cave stops at the cave mouth. The
 * friend does not follow a dead end underground; {@link #cutAtCave} tells the job the goal cannot be reached that way
 * ({@code Actions.walkTo} then gives up at once, so the job picks another target).</li>
 * <li>A way that cannot reach its goal does not end out in the water: it stops at the last dry step.</li>
 * </ul>
 *
 * <p>Steps down of two or more blocks cost extra (a friend cannot walk back up them), and friends never drop more than
 * three blocks on a path, even in a fight.
 */
public class FriendNavigation extends GroundPathNavigation {
	/** The longest path worked out, in blocks walked (and so the spots looked at: sixteen per block). */
	public static final float PATH_LENGTH = 48.0F;
	/** Extra cost for each block of a step down beyond the first. */
	static final float DROP_COST = 1.5F;

	/** The last new path that was cut short at a cave mouth (no initial value: the parent constructor runs first). */
	private @Nullable Path caveCut;

	public FriendNavigation(CompanionEntity companion, Level level) {
		super(companion, level);
		this.setRequiredPathLength(PATH_LENGTH);
	}

	@Override
	protected PathFinder createPathFinder(int maxVisitedNodes) {
		// Called from the parent constructor: only the parent's fields may be touched here.
		this.nodeEvaluator = new FriendNodeEvaluator();
		return new Finder(this.nodeEvaluator, maxVisitedNodes);
	}

	/**
	 * True when this path is a new one that was cut short at a cave mouth because the only way on led underground: the
	 * goal cannot be reached over the surface from here.
	 */
	public boolean cutAtCave(@Nullable Path path) {
		return path != null && path == caveCut;
	}

	@Override
	protected @Nullable Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange, float maxPathLength) {
		if (targets.isEmpty() || !(this.level instanceof ServerLevel serverLevel) || !(this.mob instanceof CompanionEntity c)) {
			return super.createPath(targets, radiusOffset, above, reachRange, maxPathLength);
		}
		Path current = this.path;
		if (current != null && !current.isDone() && targets.contains(this.getTargetPos())) {
			return current; // the parent hands the path in progress back unchanged: nothing to work out
		}
		boolean surface = Terrain.caveAware(serverLevel) && !Terrain.underground(serverLevel, c.blockPosition());
		if (surface) {
			for (BlockPos target : targets) {
				if (Terrain.undergroundTarget(serverLevel, target)) {
					surface = false;
					break;
				}
			}
		}
		Senses.Reading heard = Senses.read(c);
		((FriendNodeEvaluator) this.nodeEvaluator).plan(surface, heard.creeperSpots(), c.isFighter() ? new long[0] : heard.monsterSpots(),
			Wayfinder.blockedSpots(c));
		Path made = super.createPath(targets, radiusOffset, above, reachRange, maxPathLength);
		if (made != null && made != current && !made.canReach()) {
			trimDeadEnd(serverLevel, made, surface);
		}
		return made;
	}

	/**
	 * Shortens a way that cannot reach its goal: on a surface walk it stops before the first underground step, and it
	 * never ends in water (unless the friend is in the water already, when getting anywhere is better than nowhere).
	 */
	private void trimDeadEnd(ServerLevel level, Path path, boolean surface) {
		int keep = path.getNodeCount();
		if (surface) {
			for (int i = 1; i < keep; i++) {
				if (Terrain.underground(level, path.getNodePos(i))) {
					keep = i;
					caveCut = path;
					break;
				}
			}
		}
		if (!this.mob.isInWater()) {
			while (keep > 1 && level.isLoaded(path.getNodePos(keep - 1))
				&& level.getFluidState(path.getNodePos(keep - 1)).is(FluidTags.WATER)) {
				keep--;
			}
		}
		if (keep < path.getNodeCount()) {
			path.truncateNodes(Math.max(1, keep));
		}
	}

	/** The search itself, charging for steps down that cannot be walked back up. */
	private static final class Finder extends PathFinder {
		Finder(NodeEvaluator evaluator, int maxVisitedNodes) {
			super(evaluator, maxVisitedNodes);
		}

		@Override
		protected float distance(Node from, Node to) {
			float d = super.distance(from, to);
			int drop = from.y - to.y;
			return drop >= 2 ? d + (drop - 1) * DROP_COST : d;
		}
	}
}
