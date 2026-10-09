package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * A reflex: a friend found standing on scaffolding while not building from it (the build job was put down for the
 * night, for a meal, a fight, or the world was reloaded) digs their way straight back down, block by block, before
 * doing anything else, so nobody is left stranded up a pillar or wanders off it onto a roof. High priority, holding
 * movement, so no job walks them off the edge first; it gives way only to the survival package's own pillar reflex.
 * If the way down stays barred for half a minute (a player standing right by the pillar, water that has flowed in
 * beside it), the friend is left to their other goals for a minute before trying again, so the reflex never holds
 * them for good.
 */
public final class ScaffoldDescentGoal extends Goal {
	/** Give up after this long without getting a block lower. */
	private static final int STUCK_LIMIT = 20 * 30;
	/** How long to leave it after giving up. */
	private static final int RETRY_AFTER = 20 * 60;

	private final CompanionEntity c;
	private int stuckTicks;
	private double lastY;
	private boolean done;
	private long retryAt = Long.MIN_VALUE;

	public ScaffoldDescentGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!(c.level() instanceof ServerLevel level) || c.isPassenger() || Scaffold.busy(c)) {
			return false;
		}
		if (level.getGameTime() < retryAt) {
			return false;
		}
		return Scaffold.onScaffold(c);
	}

	@Override
	public void start() {
		done = false;
		stuckTicks = 0;
		lastY = c.getY();
		c.getNavigation().stop();
	}

	@Override
	public boolean canContinueToUse() {
		return !done && c.isAlive() && !Scaffold.busy(c) && stuckTicks < STUCK_LIMIT;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		switch (Scaffold.descend(c)) {
			case DONE -> done = true;
			case FAILED, WORKING -> {
				// A refusal (a player right beside the pillar) is waited out; so is a slow dig.
				if (c.getY() < lastY - 0.5) {
					lastY = c.getY();
					stuckTicks = 0;
				} else {
					stuckTicks++;
				}
			}
		}
	}

	@Override
	public void stop() {
		c.actions().cancelMining();
		if (stuckTicks >= STUCK_LIMIT) {
			retryAt = c.level().getGameTime() + RETRY_AFTER;
		}
	}
}
