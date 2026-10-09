package io.github.bradley09roberts.hardcorefriends.people;

import net.minecraft.core.BlockPos;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;

/**
 * Children keep near home: a child who has strayed outside the camp (running from a monster, or wandering after a
 * game) comes straight back, before anything but a pressing need. Changes no block.
 */
final class StayCloseTask implements CompanionTask {
	static final String ID = People.JOB_PREFIX + "stay_close";
	private static final double SCORE = 75;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "heading back home";
	}

	@Override
	public double score(CompanionEntity c) {
		return c.isChild() && c.mode() == CompanionMode.WORK && !Spots.inCamp(c, c.blockPosition()) ? SCORE : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		BlockPos home = c.restPos();
		if (Spots.inCamp(c, c.blockPosition()) && c.blockPosition().closerThan(home, 12)) {
			return TaskStatus.SUCCESS;
		}
		if (c.actions().walkTo(home, 3)) {
			return TaskStatus.SUCCESS;
		}
		return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
	}

	@Override
	public int failureCooldown() {
		return 20 * 10;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
