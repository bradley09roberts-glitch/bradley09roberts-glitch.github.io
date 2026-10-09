package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Temporary scaffolding: a pillar of dirt or cobblestone a builder puts up to stand on (jumping and placing a block
 * underfoot, the way players do) when a wall or roof is out of reach from the ground, and takes down again by digging
 * the blocks back out from the top. Every scaffold block goes through the edit guard ({@code BUILD}, inside the camp)
 * and is remembered in camp memory under {@value #MEMORY}, so a pillar left behind by an interrupted job (the friend
 * called away, the world closed) is found and taken down later: by the friend standing on it at once
 * ({@link ScaffoldDescentGoal}), or from the ground by the clean-up job ({@link ScaffoldCleanupTask}).
 *
 * <p>Pillars are at most {@code maxScaffoldHeight} (6) blocks, so the top block can always be reached from the ground
 * beside it; they never stand in a cell the building plan uses, next to water or lava, or by anything player-built.
 */
public final class Scaffold {
	/** Camp memory key of the scaffold blocks still standing. */
	public static final String MEMORY = "architecture.scaffold";
	/** How long after its last building tick a friend still counts as using their scaffold, in ticks. */
	private static final int BUSY_TICKS = 10;
	/** How far a builder's eyes may be from a block to place it, a little under the friends' reach. */
	private static final double PLACE_REACH = 4.3;
	private static final int TICKS_PER_TRY = 12;
	private static final int TRIES_BEFORE_LIFT = 3;
	/** Columns tried around the block to reach (a square of this half-width). */
	private static final int SEARCH = 3;
	/** The cheapest candidate spots, each checked with a real path of its own, per search. */
	private static final int PATH_CHECKS = 3;
	/** The other candidate spots are checked in groups of this many, one path search per group. */
	private static final int GROUP_CHECKS = 16;

	/** Where to put a pillar: the feet position at its bottom, and how many blocks to stack (0 = just stand there). */
	public record Spot(BlockPos base, int height) {
		/** Where the friend's feet are once on top. */
		public BlockPos top() {
			return base.above(height);
		}
	}

	private static final Map<CompanionEntity, Long> BUSY = new WeakHashMap<>();
	private static final Map<CampData, LongOpenHashSet> CACHE = new WeakHashMap<>();

	private Scaffold() {
	}

	// ----------------------------------------------------------------- records

	private static LongOpenHashSet blocks(CampData data) {
		return CACHE.computeIfAbsent(data, d -> {
			LongOpenHashSet set = new LongOpenHashSet();
			for (long l : d.memory(MEMORY).getLongArray("blocks").orElse(new long[0])) {
				set.add(l);
			}
			return set;
		});
	}

	private static void save(CampData data, LongOpenHashSet set) {
		CompoundTag mem = data.memory(MEMORY);
		mem.putLongArray("blocks", set.toLongArray());
		data.setDirty();
	}

	/** Remembers a scaffold block the friends just placed (in the camp's dimension). */
	static void add(ServerLevel level, BlockPos pos) {
		CampData data = Camp.data(level.getServer());
		LongOpenHashSet set = blocks(data);
		if (set.add(pos.asLong())) {
			save(data, set);
		}
	}

	/** Forgets a scaffold block (taken down, or gone some other way). */
	static void remove(ServerLevel level, BlockPos pos) {
		CampData data = Camp.data(level.getServer());
		LongOpenHashSet set = blocks(data);
		if (set.remove(pos.asLong())) {
			save(data, set);
		}
	}

	/** True if any scaffold block is remembered (cheap). */
	public static boolean any(ServerLevel level) {
		return !blocks(Camp.data(level.getServer())).isEmpty();
	}

	/**
	 * True if this is a scaffold block the friends put up and is still standing there. A record whose block has gone
	 * (or was replaced) is forgotten.
	 */
	public static boolean isScaffold(ServerLevel level, BlockPos pos) {
		CampData data = Camp.data(level.getServer());
		LongOpenHashSet set = blocks(data);
		if (set.isEmpty() || !set.contains(pos.asLong()) || !Camp.isCampLevel(level, data)) {
			return false;
		}
		if (!level.isLoaded(pos)) {
			return true; // cannot look; trust the record
		}
		BlockState s = level.getBlockState(pos);
		if ((s.is(Blocks.DIRT) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.GRASS_BLOCK)) && data.isPlacedByFriends(level, pos)) {
			return true;
		}
		set.remove(pos.asLong());
		save(data, set);
		return false;
	}

	/** Every scaffold block still remembered, for the clean-up job (a copy). */
	static List<BlockPos> all(ServerLevel level) {
		List<BlockPos> list = new ArrayList<>();
		LongArrayList copy = new LongArrayList(blocks(Camp.data(level.getServer())));
		for (long l : copy) {
			list.add(BlockPos.of(l));
		}
		return list;
	}

	// ------------------------------------------------------------------ busy

	/** Called every tick a builder is working from scaffolding, so the descent goal leaves them to it. */
	public static void markBusy(CompanionEntity c) {
		BUSY.put(c, c.level().getGameTime());
	}

	/** The builder has stopped: if they are up a pillar, the descent goal takes them down at once. */
	public static void release(CompanionEntity c) {
		BUSY.remove(c);
	}

	/** True while a builder is working from their scaffolding. */
	static boolean busy(CompanionEntity c) {
		Long at = BUSY.get(c);
		long now = c.level().getGameTime();
		return at != null && now - at <= BUSY_TICKS && now >= at;
	}

	/** True if the friend stands on top of a scaffold block. */
	public static boolean onScaffold(CompanionEntity c) {
		return c.level() instanceof ServerLevel level && c.onGround() && isScaffold(level, c.blockPosition().below());
	}

	// ---------------------------------------------------------------- planning

	/** True if scaffolding is allowed and the friend carries a block to build it from. */
	public static boolean canScaffold(CompanionEntity c) {
		return FriendsConfig.get().allowScaffolding && c.backpack().has(Stock.FILL.item());
	}

	/** True if a block is within the builder's reach from these feet. */
	public static boolean reachableFrom(BlockPos feet, BlockPos target) {
		Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
		return eye.distanceToSqr(Vec3.atCenterOf(target)) <= PLACE_REACH * PLACE_REACH;
	}

	/**
	 * The best place to stand (on a pillar if need be) to reach {@code target}: a spot the friend can walk to, a few
	 * blocks to the side, with the pillar's cells and the standing room above it all empty, inside the camp, outside the
	 * plan's own cells ({@code planCells}), away from water, lava and anything player-built. Lower pillars and spots
	 * that also reach more of {@code alsoWanted} win. Null if there is none.
	 *
	 * <p>The cheapest spots get a path check each. The cheapest can all be out of walking reach (a ledge inside the
	 * building, the floor of a basin walled in from outside) while a dearer spot is a short walk away, so the rest are
	 * then checked in groups, cheapest first, with one path search per group that finds the nearest spot of the group
	 * the friend can walk to.
	 */
	public static @Nullable Spot plan(CompanionEntity c, BlockPos target, Set<BlockPos> planCells, List<BlockPos> alsoWanted) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		int maxHeight = FriendsConfig.get().allowScaffolding ? FriendsConfig.get().maxScaffoldHeight : 0;
		// The lowest floor worth a look: from the top of the tallest pillar there, a block five higher one column to the
		// side is still in reach (so a full-height pillar on the ground reaches the ridge of a 12-high plan).
		int lowest = target.getY() - maxHeight - 5;
		record Candidate(Spot spot, double cost) {
		}
		List<Candidate> candidates = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -SEARCH; dx <= SEARCH; dx++) {
			for (int dz = -SEARCH; dz <= SEARCH; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int x = target.getX() + dx;
				int z = target.getZ() + dz;
				for (int y = target.getY() - 1; y >= lowest; y--) {
					m.set(x, y, z);
					if (!level.isLoaded(m)) {
						break;
					}
					if (!standable(level, m)) {
						continue;
					}
					BlockPos base = m.immutable();
					int height = heightNeeded(base, target, maxHeight);
					if (height < 0) {
						break; // even the tallest pillar here is not enough; lower floors are worse
					}
					if (!clearForPillar(c, level, base, height, planCells)) {
						break;
					}
					Spot spot = new Spot(base, height);
					int served = 0;
					for (BlockPos other : alsoWanted) {
						if (served < 8 && reachableFrom(spot.top(), other)) {
							served++;
						}
					}
					double cost = height * 4.0 + Math.sqrt(dx * dx + dz * dz) + Math.sqrt(c.blockPosition().distSqr(base)) / 8.0
						- served * 1.5;
					if (planCells.contains(spot.top()) || planCells.contains(spot.top().above())) {
						cost += 3; // standing where a block is still to go: it has to wait until the friend comes down
					}
					candidates.add(new Candidate(spot, cost));
					break; // the first floor down this column is the one to use
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(Candidate::cost));
		int checked = 0;
		Map<BlockPos, Spot> group = new HashMap<>();
		for (Candidate cand : candidates) {
			Spot spot = cand.spot();
			if (spot.height() > 0 && nearPlayerBuild(level, data, spot)) {
				continue;
			}
			if (checked < PATH_CHECKS) {
				checked++;
				if (c.blockPosition().equals(spot.base()) || canWalkTo(c, spot.base())) {
					return spot;
				}
				continue;
			}
			group.put(spot.base(), spot);
			if (group.size() >= GROUP_CHECKS) {
				Spot found = nearestWalkable(c, group);
				if (found != null) {
					return found;
				}
				group.clear();
			}
		}
		return group.isEmpty() ? null : nearestWalkable(c, group);
	}

	/** The spot of the group the friend can walk to soonest (one path search for all of them), or null if none. */
	private static @Nullable Spot nearestWalkable(CompanionEntity c, Map<BlockPos, Spot> group) {
		Path path = c.getNavigation().createPath(group.keySet(), 0);
		return path != null && path.canReach() ? group.get(path.getTarget()) : null;
	}

	/** The fewest blocks to stack on {@code base} so {@code target} is in reach, or -1 if more than {@code max}. */
	private static int heightNeeded(BlockPos base, BlockPos target, int max) {
		for (int h = 0; h <= max; h++) {
			if (reachableFrom(base.above(h), target)) {
				return h;
			}
		}
		return -1;
	}

	/** Room for the pillar and for standing on it: empty cells, not the plan's, inside the camp, nowhere near fluid. */
	private static boolean clearForPillar(CompanionEntity c, ServerLevel level, BlockPos base, int height, Set<BlockPos> planCells) {
		for (int i = 0; i < height + 2; i++) {
			BlockPos p = base.above(i);
			BlockState s = level.getBlockState(p);
			if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty()) {
				return false;
			}
			if (i < height) {
				if (!s.isAir() && !WorldEditGuard.isClearablePlant(s) || planCells.contains(p) || !WorldEditGuard.inCamp(c, p)
					|| WorldEditGuard.touchesFluid(level, p)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean nearPlayerBuild(ServerLevel level, CampData data, Spot spot) {
		for (int i = 0; i < spot.height(); i++) {
			if (WorldEditGuard.looksPlayerBuilt(level, spot.base().above(i), 1, data)) {
				return true;
			}
		}
		return false;
	}

	private static boolean canWalkTo(CompanionEntity c, BlockPos feet) {
		Path path = c.getNavigation().createPath(feet, 0);
		return path != null && path.canReach();
	}

	/** Solid ground under the feet and room for a friend's body, with no fluid. */
	static boolean standable(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		BlockState floor = level.getBlockState(below);
		return floor.isFaceSturdy(level, below, Direction.UP) && floor.getFluidState().isEmpty()
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
			&& level.getFluidState(feet.above()).isEmpty();
	}

	// ----------------------------------------------------------------- climbing

	/** Progress of putting up or taking down a pillar. */
	public enum Step {
		WORKING,
		DONE,
		FAILED
	}

	/**
	 * Putting up one pillar: walk to its base, then jump and place a block underfoot until on top. Call
	 * {@link #tick} every tick.
	 */
	public static final class Climb {
		private final Spot spot;
		private int placed;
		private int ticks;
		private int tries;
		private int walkTicks;

		public Climb(Spot spot) {
			this.spot = spot;
		}

		public Spot spot() {
			return spot;
		}

		public Step tick(CompanionEntity c) {
			ServerLevel level = (ServerLevel) c.level();
			markBusy(c);
			BlockPos feet = spot.base().above(placed);
			Actions actions = c.actions();
			if (placed == 0) {
				double dx = c.getX() - (feet.getX() + 0.5);
				double dz = c.getZ() - (feet.getZ() + 0.5);
				boolean there = dx * dx + dz * dz < 0.3 * 0.3 && Math.abs(c.getY() - feet.getY()) < 0.6;
				if (!there && spot.height() == 0 && reachableFrom(c.blockPosition(), spot.top())) {
					there = c.blockPosition().equals(feet); // just standing there is all it takes
				}
				if (!there) {
					if (++walkTicks > 20 * 20) {
						return Step.FAILED;
					}
					if (actions.walkTo(feet, 0.6)) {
						// Close enough: step onto the exact spot.
						c.getMoveControl().setWantedPosition(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.6);
						if (dx * dx + dz * dz < 0.8 * 0.8 && Math.abs(c.getY() - feet.getY()) < 0.6) {
							c.setPos(feet.getX() + 0.5, c.getY(), feet.getZ() + 0.5);
						}
					} else if (actions.isStuck()) {
						return Step.FAILED;
					}
					return Step.WORKING;
				}
			}
			if (placed >= spot.height()) {
				actions.stopWalking();
				return Step.DONE;
			}
			actions.stopWalking();
			BlockPos aboveHead = feet.above(2);
			if (!level.getBlockState(aboveHead).getCollisionShape(level, aboveHead).isEmpty()) {
				return Step.FAILED;
			}
			ItemStack block = c.backpack().find(s -> s.is(Items.DIRT));
			if (block.isEmpty()) {
				block = c.backpack().find(s -> s.is(Items.COBBLESTONE));
			}
			if (block.isEmpty()) {
				return Step.FAILED;
			}
			Vec3 motion = c.getDeltaMovement();
			c.setDeltaMovement(0, motion.y, 0);
			if (ticks == 0) {
				if (!c.onGround()) {
					return Step.WORKING; // land first
				}
				c.setPos(feet.getX() + 0.5, c.getY(), feet.getZ() + 0.5);
				c.getJumpControl().jump();
			}
			ticks++;
			if (c.getY() >= feet.getY() + 1.0 && place(c, level, feet, block)) {
				return Step.WORKING;
			}
			if (ticks > TICKS_PER_TRY) {
				ticks = 0;
				if (++tries >= TRIES_BEFORE_LIFT) {
					// The jumps fall short (a slab underfoot, a low branch): lift them the last bit, as there is room above.
					c.setPos(feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5);
					c.setDeltaMovement(Vec3.ZERO);
					if (!place(c, level, feet, block)) {
						return Step.FAILED;
					}
				}
			}
			return Step.WORKING;
		}

		private boolean place(CompanionEntity c, ServerLevel level, BlockPos feet, ItemStack block) {
			if (level.getGameTime() - c.lastEditTick() < 4) {
				return false;
			}
			BlockState state = Block.byItem(block.getItem()).defaultBlockState();
			ItemStack template = block.copyWithCount(1);
			if (!c.actions().place(feet, state, s -> ItemStack.isSameItemSameComponents(s, template), WorldEditGuard.Reason.BUILD)) {
				return false;
			}
			add(level, feet);
			placed++;
			ticks = 0;
			tries = 0;
			return true;
		}
	}

	/**
	 * One tick of coming down a pillar: digs out the scaffold block underfoot (the drop goes back in the backpack) until
	 * the friend stands on something else. Returns {@link Step#DONE} once down.
	 */
	public static Step descend(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (!c.onGround()) {
			return Step.WORKING; // still dropping onto the next block
		}
		BlockPos below = c.blockPosition().below();
		if (!isScaffold(level, below)) {
			c.actions().cancelMining();
			return Step.DONE;
		}
		c.getNavigation().stop();
		c.setDeltaMovement(0, c.getDeltaMovement().y, 0);
		return switch (c.actions().mine(below, WorldEditGuard.Reason.BUILD)) {
			case DONE -> {
				remove(level, below);
				yield Step.WORKING;
			}
			case FAILED -> Step.FAILED;
			case RUNNING -> Step.WORKING;
		};
	}
}
