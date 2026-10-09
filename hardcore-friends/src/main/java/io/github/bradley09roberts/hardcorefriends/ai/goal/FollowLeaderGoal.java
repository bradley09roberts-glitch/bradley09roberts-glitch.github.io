package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.navigation.Sprint;
import io.github.bradley09roberts.hardcorefriends.navigation.Terrain;
import io.github.bradley09roberts.hardcorefriends.navigation.Wayfinder;

/**
 * FOLLOW mode: stay a few blocks from the leader, catching up if left far behind in the same dimension (sooner in the
 * End, where the way to the leader is often a narrow bridge over the void). Following a leader into another dimension
 * is the expedition package's {@code TravelGoal}.
 *
 * <p>In the End a friend is never put down within {@value #CRYSTAL_CLEARANCE} blocks of a living end crystal (a blast
 * from one the player sets off throws people about that far), and never up a tower or a pillar beside a leader who has
 * climbed one: they walk after them instead, and wait at the bottom.
 *
 * <p>Friends run when the leader is far ahead (on any path longer than {@code Sprint.START_AT} blocks) and keep up with
 * a sprinting leader. A friend who is stuck (the {@code Wayfinder} has seen them get nowhere for
 * {@value #STUCK_CATCH_UP} ticks) and more than {@value #STUCK_GAP} blocks behind catches up at once rather than wait
 * for the leader to get {@code followTeleportDistance} away, in any dimension, when catching up is on.
 */
public class FollowLeaderGoal extends Goal {
	/** In the End, a friend further than this from their leader catches up rather than walks (when catching up is on). */
	private static final int END_CATCH_UP = 16;
	/** In the End, nobody is put down this close to a living end crystal (twice its blast power of 6). */
	private static final double CRYSTAL_CLEARANCE = 12;
	/** In the End, a leader standing this far above the ground round about is up a tower or a pillar. */
	private static final int UP_HIGH = 6;
	/** How far round the leader the ground is looked at, in blocks (outside the widest tower's top). */
	private static final int GROUND_LOOK = 8;
	/** A friend stuck this long (ticks) catches up with their leader at once... */
	public static final int STUCK_CATCH_UP = 100;
	/** ...when the leader is more than this many blocks away. */
	private static final int STUCK_GAP = 8;
	private final CompanionEntity companion;
	private @Nullable ServerPlayer leader;
	private int recalc;

	public FollowLeaderGoal(CompanionEntity companion) {
		this.companion = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (companion.mode() != CompanionMode.FOLLOW) {
			return false;
		}
		leader = companion.leader();
		return canFollow(companion, leader) && companion.distanceToSqr(leader) > 25;
	}

	@Override
	public boolean canContinueToUse() {
		return companion.mode() == CompanionMode.FOLLOW && canFollow(companion, leader) && companion.distanceToSqr(leader) > 9;
	}

	/**
	 * True if the leader can be followed: alive, not a spectator and in the friend's own dimension. Distances
	 * between dimensions compare raw coordinates, so a leader elsewhere is never chased or caught up with.
	 */
	public static boolean canFollow(CompanionEntity companion, @Nullable ServerPlayer leader) {
		return leader != null && leader.isAlive() && !leader.isSpectator() && leader.level() == companion.level();
	}

	@Override
	public void start() {
		recalc = 0;
	}

	@Override
	public void stop() {
		leader = null;
		companion.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (!canFollow(companion, leader)) {
			return; // canContinueToUse is only checked every other tick
		}
		companion.getLookControl().setLookAt(leader, 10.0F, companion.getMaxHeadXRot());
		if (companion.distanceToSqr(leader) > 25) {
			// Following is what moves them just now: the stuck watcher counts the leader as where they are going (and
			// only while this runs, so a friend another goal keeps in place on purpose is not "stuck").
			companion.actions().heading(leader.blockPosition());
		}
		if (--recalc > 0) {
			return;
		}
		recalc = 10;
		int teleport = FriendsConfig.get().followTeleportDistance;
		if (teleport > 0 && companion.level().dimension() == Level.END) {
			// The End is islands over the void: a long walk after the player is a walk along their narrow bridge.
			teleport = Math.min(teleport, END_CATCH_UP);
		}
		boolean stuck = teleport > 0 && Wayfinder.troubleTicks(companion) >= STUCK_CATCH_UP
			&& companion.distanceToSqr(leader) > STUCK_GAP * STUCK_GAP;
		if (teleport > 0 && (stuck || companion.distanceToSqr(leader) > (double) teleport * teleport)
			&& !upHighInEnd(companion.level(), leader.blockPosition())) {
			tryCatchUp();
			return;
		}
		if (leader.isSprinting()) {
			Sprint.hurry(companion, 20, false); // keep up: they run too (the sprint itself gives the extra speed)
		}
		companion.getNavigation().moveTo(leader, 1.1);
	}

	/**
	 * True when this friend, following and stuck for {@value #STUCK_CATCH_UP} ticks, would be caught up with their
	 * leader: catching up is on, the leader can be followed, is more than {@value #STUCK_GAP} blocks away and is not up a
	 * pillar in the End. The stuck watcher lets that happen before starting anything slower (a walk out of a cave,
	 * digging), which would keep this goal from running.
	 */
	public static boolean willCatchUp(CompanionEntity companion) {
		ServerPlayer leader = companion.leader();
		return companion.mode() == CompanionMode.FOLLOW && FriendsConfig.get().followTeleportDistance > 0
			&& canFollow(companion, leader) && companion.distanceToSqr(leader) > STUCK_GAP * STUCK_GAP
			&& !upHighInEnd(companion.level(), leader.blockPosition());
	}

	private void tryCatchUp() {
		BlockPos p = catchUpSpot(companion.level(), leader.blockPosition(), companion.getRandom());
		if (p != null) {
			companion.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, companion.getYRot(), companion.getXRot());
			companion.getNavigation().stop();
		}
	}

	/**
	 * A safe standing spot 2–3 blocks from {@code base}, or null. Only looks at loaded chunks, so it never loads or
	 * generates terrain and never puts a friend somewhere that will not tick.
	 */
	public static @Nullable BlockPos catchUpSpot(Level level, BlockPos base, RandomSource random) {
		List<EndCrystal> crystals = level.dimension() == Level.END
			? level.getEntitiesOfClass(EndCrystal.class, new AABB(base).inflate(CRYSTAL_CLEARANCE + 4), Entity::isAlive)
			: List.of();
		for (int i = 0; i < 12; i++) {
			BlockPos p = base.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
			if (Math.abs(p.getX() - base.getX()) < 2 && Math.abs(p.getZ() - base.getZ()) < 2) {
				continue;
			}
			if (!level.isLoaded(p) || !level.isLoaded(p.below()) || !level.isLoaded(p.above())) {
				continue;
			}
			if (nearCrystal(crystals, p)) {
				continue;
			}
			BlockState below = level.getBlockState(p.below());
			if (below.isFaceSturdy(level, p.below(), Direction.UP)
				&& level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
				&& below.getFluidState().isEmpty() && !Terrain.hazard(below) && !Terrain.nearLava(level, p)) {
				return p;
			}
		}
		return null;
	}

	private static boolean nearCrystal(List<EndCrystal> crystals, BlockPos p) {
		for (EndCrystal crystal : crystals) {
			if (crystal.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < CRYSTAL_CLEARANCE * CRYSTAL_CLEARANCE) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True in the End while the leader stands more than {@value #UP_HIGH} blocks above the lowest ground a few blocks
	 * round them (the void does not count): up a tower or a pillar. A friend is not put up there beside them, neither by
	 * catching up nor by the stuck watcher's rescue.
	 */
	public static boolean upHighInEnd(Level level, BlockPos at) {
		if (level.dimension() != Level.END) {
			return false;
		}
		int lowest = Integer.MAX_VALUE;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = at.relative(d, GROUND_LOOK);
			if (!level.isLoaded(p)) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
			if (ground > level.getMinY() + 1) {
				lowest = Math.min(lowest, ground);
			}
		}
		return lowest != Integer.MAX_VALUE && at.getY() - lowest > UP_HIGH;
	}
}
