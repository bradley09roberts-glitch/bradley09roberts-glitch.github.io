package io.github.bradley09roberts.hardcorefriends.ai.task.needs;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Finding places for everyday life: somewhere to stand, the camp's lit campfire, the inside of the cabin, a friend's
 * own bed in their village home, a block worth looking at. Every search is bounded and only runs when a job starts (or
 * is cached), never every tick. The needs jobs use all of it; the night watch uses the standing spots and the campfire.
 */
public final class Spots {
	private Spots() {
	}

	/** Solid ground below, room for the body, no fluid. */
	public static boolean isStandable(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p)) {
			return false;
		}
		BlockState below = level.getBlockState(p.below());
		BlockState feet = level.getBlockState(p);
		BlockState head = level.getBlockState(p.above());
		return below.isFaceSturdy(level, p.below(), Direction.UP) && !below.is(BlockTags.CAMPFIRES)
			&& feet.getCollisionShape(level, p).isEmpty() && head.getCollisionShape(level, p.above()).isEmpty()
			&& feet.getFluidState().isEmpty() && head.getFluidState().isEmpty();
	}

	/** A standable spot in the column at {@code pos}, looking up to three blocks up and down; null if none. */
	public static @Nullable BlockPos standable(ServerLevel level, BlockPos pos) {
		for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
			BlockPos p = pos.above(dy);
			if (isStandable(level, p)) {
				return p.immutable();
			}
		}
		return null;
	}

	/** True when this position is inside the camp (or near home, for a friend without a camp). */
	public static boolean inCamp(CompanionEntity c, BlockPos pos) {
		int r = WorldEditGuard.campRadius(c);
		return Camp.horizontalDistSqr(pos, c.homePos()) <= (double) r * r;
	}

	/** Under a roof: no sky above the head. */
	static boolean sheltered(ServerLevel level, BlockPos feet) {
		return !level.canSeeSky(feet.above());
	}

	/** A random standable spot within {@code range} blocks of a centre, or null after a few tries. */
	static @Nullable BlockPos randomNear(CompanionEntity c, BlockPos centre, int range) {
		ServerLevel level = (ServerLevel) c.level();
		for (int attempt = 0; attempt < 6; attempt++) {
			int dx = c.getRandom().nextInt(range * 2 + 1) - range;
			int dz = c.getRandom().nextInt(range * 2 + 1) - range;
			BlockPos spot = standable(level, centre.offset(dx, 0, dz));
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/**
	 * A spot to stand beside a block, {@code distance} blocks away (1 to 3), nearest to the friend first. Null when
	 * every side is blocked.
	 */
	static @Nullable BlockPos beside(CompanionEntity c, BlockPos target, int distance) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -distance; dx <= distance; dx++) {
			for (int dz = -distance; dz <= distance; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) {
					continue;
				}
				BlockPos spot = standable(level, target.offset(dx, 0, dz));
				if (spot != null && Math.abs(spot.getY() - target.getY()) <= 2) {
					double d = spot.distSqr(c.blockPosition());
					if (d < bestDist) {
						bestDist = d;
						best = spot;
					}
				}
			}
		}
		return best;
	}

	/**
	 * The nearest block matching a test within {@code radius} blocks horizontally and {@code height} up or down of a
	 * centre, restricted to the camp. At most (2r+1)² × (2h+1) block reads; call it when a job starts, not per tick.
	 */
	static @Nullable BlockPos nearestBlock(CompanionEntity c, BlockPos centre, int radius, int height, Predicate<BlockState> test) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-radius, -height, -radius), centre.offset(radius, height, radius))) {
			if (!level.isLoaded(p) || !test.test(level.getBlockState(p)) || !inCamp(c, p)) {
				continue;
			}
			double d = p.distSqr(centre);
			if (d < bestDist) {
				bestDist = d;
				best = p.immutable();
			}
		}
		return best;
	}

	static boolean isLitCampfire(BlockState s) {
		return s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT);
	}

	/** The camp's own campfire if it stands and burns, otherwise the nearest lit campfire within 10 blocks. */
	public static @Nullable BlockPos litCampfire(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (Camp.isCampLevel(level, data)) {
			var site = data.site(Structures.CAMPFIRE);
			if (site.isPresent()) {
				BlockPos fire = Blueprint.worldPos(site.get().origin, site.get().rotation, 0, 0, 0);
				if (level.isLoaded(fire) && isLitCampfire(level.getBlockState(fire))) {
					return fire;
				}
			}
		}
		BlockPos near = nearestBlock(c, c.blockPosition(), 10, 3, Spots::isLitCampfire);
		return near != null ? near : nearestBlock(c, c.homePos(), 10, 3, Spots::isLitCampfire);
	}

	/** True when the friend's rest spot is the inside of the built cabin rather than the open camp. */
	static boolean cabinBuilt(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos rest = c.restPos();
		return !rest.equals(c.homePos()) && sheltered(level, rest);
	}

	// ------------------------------------------------------------- real beds

	/**
	 * The head of this friend's own bed in their village home ({@code civic.Homes}), if they have one they can sleep in
	 * now: a whole bed the friends placed themselves (never a player's), inside the camp, and not taken by anyone else
	 * asleep in it (a player who lay down in it first keeps it). Null otherwise: they sleep in the cabin or round the camp
	 * as before.
	 */
	public static @Nullable BlockPos ownBed(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos foot = Homes.get().bedFor(c).orElse(null);
		if (foot == null || !level.isLoaded(foot)) {
			return null;
		}
		BlockState state = level.getBlockState(foot);
		if (!(state.getBlock() instanceof AbstractBedBlock)) {
			return null;
		}
		BlockPos head = state.getValue(AbstractBedBlock.PART) == BedPart.HEAD ? foot : foot.relative(state.getValue(AbstractBedBlock.FACING));
		BlockState headState = level.isLoaded(head) ? level.getBlockState(head) : null;
		if (headState == null || !(headState.getBlock() instanceof AbstractBedBlock) || headState.getValue(AbstractBedBlock.PART) != BedPart.HEAD) {
			return null;
		}
		if (!Camp.data(level.getServer()).isPlacedByFriends(level, head) || !inCamp(c, head)) {
			return null;
		}
		if (headState.getValue(AbstractBedBlock.OCCUPIED) && someoneElseAsleep(level, head, c)) {
			return null;
		}
		return head;
	}

	/** True if anyone but this friend (a player, another friend) lies asleep in the bed whose head is here. */
	static boolean someoneElseAsleep(ServerLevel level, BlockPos head, CompanionEntity c) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(head).inflate(1.0),
			e -> e != c && e.isSleeping() && head.equals(e.getSleepingPos().orElse(null))).isEmpty();
	}

	/** A spot to stand beside the bed (by its head or foot), nearest the friend; null if it is walled in. */
	static @Nullable BlockPos besideBed(CompanionEntity c, BlockPos head) {
		ServerLevel level = (ServerLevel) c.level();
		BlockState state = level.getBlockState(head);
		if (!(state.getBlock() instanceof AbstractBedBlock)) {
			return null;
		}
		BlockPos foot = head.relative(state.getValue(AbstractBedBlock.FACING).getOpposite());
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos part : new BlockPos[] {foot, head}) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos p = part.relative(d);
				if (p.equals(foot) || p.equals(head) || !isStandable(level, p)) {
					continue;
				}
				double dist = p.distSqr(c.blockPosition());
				if (dist < bestDist) {
					bestDist = dist;
					best = p;
				}
			}
		}
		return best;
	}

	/**
	 * Lies down in the bed whose head is here, as villagers do (the vanilla sleeping pose, the bed marked taken). False if
	 * it is no longer a bed or someone else got there first.
	 */
	static boolean lieInBed(CompanionEntity c, BlockPos head) {
		ServerLevel level = (ServerLevel) c.level();
		BlockState state = level.getBlockState(head);
		if (!(state.getBlock() instanceof AbstractBedBlock) || state.getValue(AbstractBedBlock.OCCUPIED) && someoneElseAsleep(level, head, c)) {
			return false;
		}
		return c.startSleeping(head);
	}
}
