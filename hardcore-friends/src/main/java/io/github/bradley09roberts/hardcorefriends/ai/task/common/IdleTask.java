package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/** When there is nothing better to do: a short stroll near the camp centre and the odd bit of small talk. */
public final class IdleTask implements CompanionTask {
	private static final int WANDER = 8;

	private @Nullable BlockPos spot;
	private int linger;

	@Override
	public String id() {
		return "common.idle";
	}

	@Override
	public String describe() {
		return "taking a break";
	}

	@Override
	public double score(CompanionEntity c) {
		return 3;
	}

	@Override
	public boolean start(CompanionEntity c) {
		spot = Upkeep.randomSpotNear(c, c.homePos(), WANDER);
		linger = 0;
		return spot != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (spot == null) {
			return TaskStatus.FAILURE;
		}
		if (linger > 0) {
			return --linger == 0 ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
		}
		if (c.actions().walkTo(spot, 1.5) || c.actions().isStuck()) {
			c.actions().stopWalking();
			linger = 40 + c.getRandom().nextInt(80);
			if (c.getRandom().nextInt(4) == 0) {
				Speech.say(c, Line.IDLE);
			}
		}
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		spot = null;
		linger = 0;
	}

	@Override
	public int successCooldown() {
		return 40;
	}

	@Override
	public int maxTicks() {
		return 20 * 30;
	}
}
