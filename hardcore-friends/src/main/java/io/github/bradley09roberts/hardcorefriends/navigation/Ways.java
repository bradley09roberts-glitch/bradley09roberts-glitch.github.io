package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Small, bounded searches over the blocks round a friend, walking the way a friend can (a step at a time, up one
 * block with room to jump, down at most three, swimming in still or running water but never falling water): can they
 * get away from here at all, the way to the nearest spot under open sky, and the best way out of the water. Each
 * search looks at a fixed number of spots at most and only at loaded blocks, so it is cheap enough to run now and then
 * for a friend in trouble (never every tick, and never for every friend at once).
 */
final class Ways {
	private static final byte UNKNOWN = 0;
	private static final byte NONE = 1;
	private static final byte STAND = 2;
	private static final byte SWIM = 3;
	private static final int MAX_DROP = 3;

	private Ways() {
	}

	/** What a search remembers about each spot it looked at (each spot is looked at once). */
	private static final class Spots {
		private final ServerLevel level;
		private final Long2ByteOpenHashMap kinds = new Long2ByteOpenHashMap();
		private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

		Spots(ServerLevel level) {
			this.level = level;
		}

		/** STAND (firm and dry), SWIM (water to be in) or NONE. */
		byte kind(BlockPos p) {
			long key = p.asLong();
			byte k = kinds.get(key);
			if (k == UNKNOWN) {
				k = work(p);
				kinds.put(key, k);
			}
			return k;
		}

		private byte work(BlockPos p) {
			if (!level.isLoaded(p) || !level.isInWorldBounds(p)) {
				return NONE;
			}
			BlockState at = level.getBlockState(p);
			BlockState head = level.getBlockState(m.setWithOffset(p, 0, 1, 0));
			if (!at.getCollisionShape(level, p).isEmpty() || !head.getCollisionShape(level, m).isEmpty()
				|| Terrain.hazard(at) || Terrain.hazard(head)) {
				return NONE;
			}
			FluidState fluid = at.getFluidState();
			if (fluid.is(FluidTags.WATER)) {
				return Terrain.fallingWater(fluid) ? NONE : SWIM;
			}
			if (!fluid.isEmpty() || !head.getFluidState().isEmpty() && !head.getFluidState().is(FluidTags.WATER)) {
				return NONE;
			}
			m.setWithOffset(p, 0, -1, 0);
			BlockState below = level.getBlockState(m);
			if (below.getCollisionShape(level, m).isEmpty() || Terrain.hazard(below) || below.is(BlockTags.LEAVES)) {
				return NONE;
			}
			return STAND;
		}

		boolean passable(BlockPos p) {
			return level.isLoaded(p) && Terrain.passable(level, p) && !level.getFluidState(p).is(FluidTags.LAVA);
		}
	}

	/** Calls {@code out} with each spot a friend at {@code p} can move to in one step. */
	private static void neighbours(Spots spots, BlockPos p, byte here, List<BlockPos> out) {
		out.clear();
		boolean headroom = spots.passable(p.above(2)) || here == SWIM;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = p.relative(d);
			byte k = spots.kind(side);
			if (k != NONE) {
				out.add(side);
				continue;
			}
			if (!spots.passable(side)) {
				BlockPos up = side.above();
				if (headroom && spots.kind(up) != NONE) {
					out.add(up);
				}
				continue;
			}
			if (!spots.passable(side.above())) {
				continue;
			}
			for (int drop = 1; drop <= MAX_DROP; drop++) {
				BlockPos down = side.below(drop);
				if (spots.kind(down) != NONE) {
					out.add(down);
					break;
				}
				if (!spots.passable(down)) {
					break;
				}
			}
		}
		if (here == SWIM) {
			// Swimming: up to the surface (or out onto a ledge above) and down through the water.
			if (spots.kind(p.above()) != NONE) {
				out.add(p.above());
			}
			if (spots.kind(p.below()) == SWIM) {
				out.add(p.below());
			}
		}
	}

	/** The result of a search: the way to the spot found (from the start, inclusive), or nothing. */
	private static @Nullable List<BlockPos> search(ServerLevel level, BlockPos start, int radius, int maxSpots,
		Predicate<BlockPos> goal, Predicate<BlockPos> allowed) {
		Spots spots = new Spots(level);
		Long2LongOpenHashMap cameFrom = new Long2LongOpenHashMap();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(start);
		cameFrom.put(start.asLong(), start.asLong());
		List<BlockPos> next = new ArrayList<>(8);
		int looked = 0;
		while (!open.isEmpty() && looked++ < maxSpots) {
			BlockPos p = open.poll();
			byte here = spots.kind(p);
			if (!p.equals(start) && goal.test(p)) {
				return walkBack(cameFrom, start, p);
			}
			neighbours(spots, p, here == NONE ? STAND : here, next);
			for (BlockPos n : next) {
				long key = n.asLong();
				if (cameFrom.containsKey(key) || Math.abs(n.getX() - start.getX()) > radius || Math.abs(n.getZ() - start.getZ()) > radius
					|| Math.abs(n.getY() - start.getY()) > radius || !allowed.test(n)) {
					continue;
				}
				cameFrom.put(key, p.asLong());
				open.add(n);
			}
		}
		return null;
	}

	private static List<BlockPos> walkBack(Long2LongOpenHashMap cameFrom, BlockPos start, BlockPos end) {
		List<BlockPos> way = new ArrayList<>();
		long at = end.asLong();
		long first = start.asLong();
		way.add(end);
		for (int guard = 0; at != first && guard < 100_000; guard++) {
			at = cameFrom.get(at);
			way.add(BlockPos.of(at));
		}
		Collections.reverse(way);
		return way;
	}

	/**
	 * True when a friend at {@code start} can get at least {@code radius} blocks away (across) on foot or swimming,
	 * looking at no more than {@code maxSpots} spots. False means they are shut in: a pit, a hole, a pocket of cave.
	 */
	static boolean canLeave(ServerLevel level, BlockPos start, int radius, int maxSpots) {
		return search(level, start, radius + 1, maxSpots,
			p -> Math.max(Math.abs(p.getX() - start.getX()), Math.abs(p.getZ() - start.getZ())) >= radius, p -> true) != null;
	}

	/**
	 * The way, spot by spot, from {@code start} to the nearest dry spot under open sky that a friend can walk to, within
	 * {@code radius} blocks and {@code maxSpots} spots looked at; null when there is none that near.
	 */
	static @Nullable List<BlockPos> toOpenSky(ServerLevel level, BlockPos start, int radius, int maxSpots) {
		Spots check = new Spots(level);
		return search(level, start, radius, maxSpots,
			p -> check.kind(p) == STAND && !Terrain.underground(level, p) && level.getBrightness(LightLayer.SKY, p) >= 10,
			p -> !level.getFluidState(p).is(FluidTags.LAVA));
	}

	/**
	 * Where to climb out of the water: the dry spot next to the water that is quickest to swim to, preferring one
	 * towards {@code towards} (where the friend wants to go; may be null), one that does not mean swimming with the
	 * current (it could carry them somewhere worse) or straight against it, and one that leads somewhere (a ledge
	 * with nowhere to go from it is a last choice). Null when no shore is within reach: a hole with steep sides.
	 */
	static @Nullable BlockPos shore(ServerLevel level, BlockPos start, @Nullable BlockPos towards, Vec3 current, int radius, int maxSpots) {
		Spots spots = new Spots(level);
		Long2LongOpenHashMap cameFrom = new Long2LongOpenHashMap();
		Long2ByteOpenHashMap steps = new Long2ByteOpenHashMap();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(start);
		cameFrom.put(start.asLong(), start.asLong());
		steps.put(start.asLong(), (byte) 0);
		List<BlockPos> next = new ArrayList<>(8);
		List<BlockPos> landings = new ArrayList<>();
		List<Integer> landingSteps = new ArrayList<>();
		int looked = 0;
		while (!open.isEmpty() && looked++ < maxSpots && landings.size() < 24) {
			BlockPos p = open.poll();
			byte here = spots.kind(p);
			int stepsHere = steps.get(p.asLong());
			neighbours(spots, p, here == NONE ? SWIM : here, next);
			for (BlockPos n : next) {
				long key = n.asLong();
				if (cameFrom.containsKey(key) || Math.abs(n.getX() - start.getX()) > radius || Math.abs(n.getZ() - start.getZ()) > radius) {
					continue;
				}
				cameFrom.put(key, p.asLong());
				steps.put(key, (byte) Math.min(120, stepsHere + 1));
				if (spots.kind(n) == STAND) {
					landings.add(n); // out of the water: a landing; the search does not go on over land
					landingSteps.add(stepsHere + 1);
				} else {
					open.add(n);
				}
			}
		}
		Vec3 flow = current.lengthSqr() > 1.0E-6 ? current.normalize() : Vec3.ZERO;
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < landings.size(); i++) {
			BlockPos l = landings.get(i);
			double score = landingSteps.get(i);
			if (towards != null) {
				score += 0.3 * Math.sqrt(l.distSqr(towards));
			}
			if (flow != Vec3.ZERO) {
				Vec3 way = new Vec3(l.getX() - start.getX(), 0, l.getZ() - start.getZ());
				if (way.lengthSqr() > 1.0E-6) {
					double along = way.normalize().dot(flow);
					score += along > 0.4 ? 8 : along < -0.4 ? 4 : 0; // across the current is best
				}
			}
			if (score < bestScore && !canLeave(level, l, 4, 60)) {
				score += 40; // a ledge going nowhere
			}
			if (score < bestScore) {
				bestScore = score;
				best = l;
			}
		}
		return best;
	}
}
