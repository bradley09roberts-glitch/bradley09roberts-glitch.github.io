package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Runs the {@link Wayfinder}'s plan for getting a friend out of trouble (swimming out, walking out of a cave, digging
 * out). It takes the friend's legs from their job or from following, so the job's own walking does not fight it, but
 * gives way to falling back, dodging danger and fighting: a monster coming at them ends the plan, and it is thought out
 * again afterwards if they are still stuck.
 */
public final class WayOutGoal extends Goal {
	/** Below dodging danger (1) and above fighting (3), following (4) and work (5). */
	public static final int PRIORITY = 2;

	private final CompanionEntity c;

	public WayOutGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return c.isAlive() && c.getTarget() == null && !c.isRetreating() && Wayfinder.hasPlan(c);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (c.level() instanceof ServerLevel level) {
			Wayfinder.tickPlan(c, level);
		}
	}

	@Override
	public void stop() {
		Wayfinder.interrupt(c);
	}
}
