package io.github.bradley09roberts.hardcorefriends.ai.role.build;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteClearing;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Clears a building site in a forest camp: fells exactly the natural trees the site search recorded for it (never
 * any other camp tree), bottom-up, standing in the stump's place to reach the top of the trunk, then trims the
 * leaves left in the building's space. Nothing is replanted there, and the logs go to the build. Rowan does this
 * eagerly; the builder lends a hand while the build waits on it.
 */
public final class ClearSiteTask implements CompanionTask {
	private static final int BLOCK_TIMEOUT = 200;

	private final double baseScore;
	private final Set<BlockPos> givenUp = new HashSet<>();
	private SiteClearing.@Nullable Job job;
	private @Nullable BlockPos current;
	private boolean currentIsLog;
	private int currentTicks;
	private int cleared;

	public ClearSiteTask(double baseScore) {
		this.baseScore = baseScore;
	}

	@Override
	public String id() {
		return "clear_site";
	}

	@Override
	public String describe() {
		SiteClearing.Job j = job;
		return "clearing the " + SiteClearing.siteName(j == null ? null : j.planId()) + " site";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !FriendsConfig.get().allowTreeFelling) {
			return 0;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return 0;
		}
		if (c.backpack().freeSlots() == 0 && !c.backpack().canFit(new ItemStack(Items.OAK_LOG, 6))) {
			return 0; // a deposit first
		}
		return SiteClearing.active(level, data).isPresent() ? baseScore : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<SiteClearing.Job> active = SiteClearing.active(level, Camp.data(level.getServer()));
		if (active.isEmpty()) {
			return false;
		}
		job = active.get();
		givenUp.clear();
		current = null;
		cleared = 0;
		// The recorded trunks may be felled even with their base already gone (a restart part-way through).
		c.approveLogs(SiteClearing.standingLogs(level, active.get()));
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		SiteClearing.Job j = job;
		if (j == null) {
			return TaskStatus.FAILURE;
		}
		ServerLevel level = (ServerLevel) c.level();
		Actions actions = c.actions();
		if (current == null && !pickNext(c, level, j)) {
			if (cleared > 0) {
				Camp.data(level.getServer()).addStat("site_blocks_cleared", cleared);
			}
			return givenUp.isEmpty() || cleared > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
		}
		BlockPos target = current;
		boolean stillThere = currentIsLog ? level.getBlockState(target).is(BlockTags.LOGS)
			: SiteClearing.isNaturalLeaves(level.getBlockState(target));
		if (!stillThere) {
			current = null;
			return TaskStatus.RUNNING;
		}
		if (++currentTicks > BLOCK_TIMEOUT) {
			giveUp(c);
			return TaskStatus.RUNNING;
		}
		if (!actions.canReach(target)) {
			// Underneath it: the stump's place for a trunk, the ground below for leaves.
			actions.walkTo(new BlockPos(target.getX(), c.blockPosition().getY(), target.getZ()), 0.5);
			if (actions.isStuck()) {
				giveUp(c);
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		switch (actions.mine(target, currentIsLog ? Reason.GATHER_WOOD : Reason.BUILD)) {
			case DONE -> {
				cleared++;
				current = null;
			}
			case FAILED -> giveUp(c);
			case RUNNING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	/** The next block to take: the lowest log of the nearest trunk first, then leaves, nearest first. */
	private boolean pickNext(CompanionEntity c, ServerLevel level, SiteClearing.Job j) {
		BlockPos here = c.blockPosition();
		List<BlockPos> logs = SiteClearing.standingLogs(level, j).stream().filter(p -> !givenUp.contains(p)).toList();
		Optional<BlockPos> log = logs.stream().min(Comparator.<BlockPos>comparingDouble(p -> horizontalDistSqr(p, here))
			.thenComparingInt(BlockPos::getY));
		if (log.isPresent()) {
			// Bottom-up within that column, so no log is ever left hanging under one still to cut.
			BlockPos column = log.get();
			current = logs.stream().filter(p -> p.getX() == column.getX() && p.getZ() == column.getZ())
				.min(Comparator.comparingInt(BlockPos::getY)).orElse(column);
			currentIsLog = true;
			currentTicks = 0;
			return true;
		}
		Optional<BlockPos> leaf = SiteClearing.leaves(level, j).stream().filter(p -> !givenUp.contains(p))
			.min(Comparator.comparingDouble(p -> p.distSqr(here)));
		if (leaf.isPresent()) {
			current = leaf.get();
			currentIsLog = false;
			currentTicks = 0;
			return true;
		}
		return false;
	}

	private static double horizontalDistSqr(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	private void giveUp(CompanionEntity c) {
		SiteClearing.Job j = job;
		if (current != null) {
			givenUp.add(current);
			if (j != null && c.level() instanceof ServerLevel level) {
				SiteClearing.skip(Camp.data(level.getServer()), j, current);
			}
		}
		current = null;
		c.actions().reset();
	}

	@Override
	public void stop(CompanionEntity c) {
		job = null;
		current = null;
		c.actions().reset();
	}
}
