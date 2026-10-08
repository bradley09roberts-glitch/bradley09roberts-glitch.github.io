package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/** FOLLOW mode: stay a few blocks from the leader, catching up if left far behind in the same dimension. */
public class FollowLeaderGoal extends Goal {
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
		if (--recalc > 0) {
			return;
		}
		recalc = 10;
		int teleport = FriendsConfig.get().followTeleportDistance;
		if (teleport > 0 && companion.distanceToSqr(leader) > (double) teleport * teleport) {
			tryCatchUp();
			return;
		}
		companion.getNavigation().moveTo(leader, leader.isSprinting() ? 1.35 : 1.1);
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
		for (int i = 0; i < 12; i++) {
			BlockPos p = base.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
			if (Math.abs(p.getX() - base.getX()) < 2 && Math.abs(p.getZ() - base.getZ()) < 2) {
				continue;
			}
			if (!level.isLoaded(p) || !level.isLoaded(p.below()) || !level.isLoaded(p.above())) {
				continue;
			}
			BlockState below = level.getBlockState(p.below());
			if (below.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
				&& level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
				&& below.getFluidState().isEmpty()) {
				return p;
			}
		}
		return null;
	}
}
