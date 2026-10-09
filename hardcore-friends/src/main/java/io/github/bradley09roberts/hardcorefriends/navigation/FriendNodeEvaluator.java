package io.github.bradley09roberts.hardcorefriends.navigation;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Vanilla walking, with a friend's sense of where not to put their feet. Every step a path could take gets an extra
 * cost on top of the vanilla one (or is ruled out), worked out once per spot per path:
 *
 * <ul>
 * <li><b>Water:</b> running water costs much more than still water, deep water more than shallow, and falling water
 * (a waterfall, a spring running down a cliff) or the lip of one is never entered: it carries a friend off.</li>
 * <li><b>Caves:</b> on a walk from the surface to the surface ({@link #plan}), a sky-less spot well under the top of
 * its column costs a lot, so a path to a tree goes round a hill rather than through the cave under it. A walk that
 * starts or ends underground (the mine) is not affected.</li>
 * <li><b>Edges:</b> standing beside a drop of more than three blocks costs a little; the drop itself is charged by
 * {@link FriendNavigation} (a step down two or more blocks cannot be walked back up).</li>
 * <li><b>Lava and fire:</b> vanilla charges a spot next to lava only when lava is the first unusual block it finds
 * round it (water or a thorn bush found first hides it); here every spot with lava or fire round it is charged.</li>
 * <li><b>Heard danger:</b> spots close to a creeper the friend can hear (and, for friends who are not fighters, to
 * other monsters) cost more, so a path does not lead round a corner into one ({@link Senses}).</li>
 * <li><b>Blocked spots:</b> where a friend just got stuck costs more for a while, so the next path goes another way
 * ({@link Wayfinder}).</li>
 * </ul>
 *
 * <p>Costs only ever go up, so a path that vanilla could find is still found when there is no better one: nothing here
 * strands a friend who has only one way to go, apart from falling water.
 */
public class FriendNodeEvaluator extends WalkNodeEvaluator {
	/** Extra cost of a step in running water (still water costs the friend's own water malus, 4). */
	static final float RUNNING_WATER = 10.0F;
	/** Extra cost of a step in water with more water under it (out of their depth). */
	static final float DEEP_WATER = 3.0F;
	/** Extra cost of a sky-less step on a walk between two places on the surface. */
	static final float UNDERGROUND = 7.0F;
	/** Extra cost of standing beside a drop of more than {@value #EDGE_DROP} blocks. */
	static final float CLIFF_EDGE = 3.0F;
	/** A drop of more than this many blocks beside a spot makes it an edge. */
	static final int EDGE_DROP = 3;
	/** Extra cost of a spot with lava or fire round it that vanilla did not charge for. */
	static final float NEAR_LAVA = 16.0F;
	/** Extra cost of a spot within {@value #CREEPER_RANGE} blocks of a creeper the friend can hear. */
	static final float NEAR_CREEPER = 12.0F;
	static final int CREEPER_RANGE = 4;
	/** Extra cost, for a friend who is not a fighter, of a spot within {@value #MONSTER_RANGE} blocks of a monster heard. */
	static final float NEAR_MONSTER = 4.0F;
	static final int MONSTER_RANGE = 3;
	/** Extra cost of a spot where the friend recently got stuck. */
	static final float BLOCKED_SPOT = 16.0F;
	/** Marks a spot that is never stepped on. */
	private static final float NEVER = -1.0F;

	private final Long2FloatOpenHashMap extraBySpot = new Long2FloatOpenHashMap();
	private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
	private boolean surfaceWalk;
	private long[] creepers = new long[0];
	private long[] monsters = new long[0];
	private @Nullable LongSet blocked;

	/**
	 * Sets up the next path search: whether it is a walk from the surface to the surface (sky-less spots then cost a
	 * lot), where the creepers and other monsters the friend can hear are (packed positions; other monsters only
	 * matter to a friend who is not a fighter, so pass an empty array for a fighter), and the spots where they got stuck
	 * lately (or null).
	 */
	void plan(boolean surface, long[] heardCreepers, long[] heardMonsters, @Nullable LongSet stuckSpots) {
		this.surfaceWalk = surface;
		this.creepers = heardCreepers;
		this.monsters = heardMonsters;
		this.blocked = stuckSpots;
	}

	@Override
	public void prepare(PathNavigationRegion level, Mob entity) {
		super.prepare(level, entity);
		extraBySpot.clear();
	}

	@Override
	public void done() {
		extraBySpot.clear();
		super.done();
	}

	@Override
	protected @Nullable Node findAcceptedNode(int x, int y, int z, int jumpSize, double nodeHeight, Direction travelDirection,
		PathType blockPathTypeCurrent) {
		Node node = super.findAcceptedNode(x, y, z, jumpSize, nodeHeight, travelDirection, blockPathTypeCurrent);
		if (node == null || node.costMalus < 0.0F) {
			return node;
		}
		float extra = extra(node);
		if (extra < 0.0F) {
			node.costMalus = NEVER;
		} else if (extra > 0.0F) {
			// The same spot can be handed back several times in one search: max keeps the charge from piling up.
			node.costMalus = Math.max(node.costMalus, this.mob.getPathfindingMalus(node.type) + extra);
		}
		return node;
	}

	private float extra(Node node) {
		long key = BlockPos.asLong(node.x, node.y, node.z);
		if (extraBySpot.containsKey(key)) {
			return extraBySpot.get(key);
		}
		float extra = compute(node.x, node.y, node.z, node.type);
		extraBySpot.put(key, extra);
		return extra;
	}

	private float compute(int x, int y, int z, PathType type) {
		BlockGetter region = this.currentContext.level();
		float extra = 0.0F;
		FluidState fluid = region.getFluidState(scratch.set(x, y, z));
		boolean swimming = fluid.is(FluidTags.WATER);
		if (swimming) {
			if (Terrain.fallingWater(fluid)) {
				return NEVER;
			}
			if (Terrain.runningWater(fluid)) {
				if (besideFallingWater(region, x, y, z)) {
					return NEVER; // the lip of a waterfall: the current would take them over
				}
				extra += RUNNING_WATER;
			}
			if (region.getFluidState(scratch.set(x, y - 1, z)).is(FluidTags.WATER)) {
				extra += DEEP_WATER;
			}
		}
		// Vanilla looks for lava round a walkable spot itself, and stops at the first unusual neighbour it finds.
		if (type != PathType.WALKABLE && type != PathType.FIRE_IN_NEIGHBOR && fireOrLavaAround(x, y, z)) {
			extra += NEAR_LAVA;
		}
		if (!swimming && edge(x, y, z)) {
			extra += CLIFF_EDGE;
		}
		if (surfaceWalk && Terrain.underground(level(), scratch.set(x, y, z))) {
			extra += UNDERGROUND;
		}
		extra += heardDanger(x, y, z);
		LongSet stuck = blocked;
		if (stuck != null && stuck.contains(BlockPos.asLong(x, y, z))) {
			extra += BLOCKED_SPOT;
		}
		return extra;
	}

	private Level level() {
		return this.mob.level();
	}

	private boolean besideFallingWater(BlockGetter region, int x, int y, int z) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (Terrain.fallingWater(region.getFluidState(scratch.set(x + d.getStepX(), y, z + d.getStepZ())))) {
				return true;
			}
		}
		return false;
	}

	private boolean fireOrLavaAround(int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					PathType around = this.currentContext.getPathTypeFromState(x + dx, y + dy, z + dz);
					if (around == PathType.LAVA || around == PathType.FIRE) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** True when, beside this spot, the ground falls away by more than {@value #EDGE_DROP} blocks (into air, not water). */
	private boolean edge(int x, int y, int z) {
		if (this.currentContext.getPathTypeFromState(x, y - 1, z) == PathType.OPEN) {
			return false; // not standing on anything here: a fall in progress is charged as a drop
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int nx = x + d.getStepX();
			int nz = z + d.getStepZ();
			boolean open = true;
			for (int dy = 0; dy <= EDGE_DROP + 1 && open; dy++) {
				open = this.currentContext.getPathTypeFromState(nx, y - dy, nz) == PathType.OPEN;
			}
			if (open) {
				return true;
			}
		}
		return false;
	}

	private float heardDanger(int x, int y, int z) {
		float extra = 0.0F;
		for (long packed : creepers) {
			if (near(packed, x, y, z, CREEPER_RANGE)) {
				extra += NEAR_CREEPER;
				break;
			}
		}
		for (long packed : monsters) {
			if (near(packed, x, y, z, MONSTER_RANGE)) {
				extra += NEAR_MONSTER;
				break;
			}
		}
		return extra;
	}

	private static boolean near(long packed, int x, int y, int z, int range) {
		int dx = BlockPos.getX(packed) - x;
		int dy = BlockPos.getY(packed) - y;
		int dz = BlockPos.getZ(packed) - z;
		return dx * dx + dz * dz <= range * range && Math.abs(dy) <= 3;
	}
}
