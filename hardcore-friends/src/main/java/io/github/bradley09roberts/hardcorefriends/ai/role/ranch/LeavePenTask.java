package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Nobody stays shut in the animal pen: a friend who finds themself in the paddock without a pen job in hand (called
 * away in the middle of one, or wandered in through an open gate) walks out through the gate, opening it if it is
 * shut, and shuts it behind them when animals are inside. Urgent upkeep, so it comes before anything but fleeing and
 * the most desperate needs; every friend has it.
 */
public final class LeavePenTask implements CompanionTask {
	public static final String ID = "common.leave_pen";
	private static final double SCORE = 85;

	private final Pen.GateWalk walk = new Pen.GateWalk();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "leaving the animal pen";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		Pen pen = Pen.of(level).orElse(null);
		if (pen == null || !pen.holds(c)) {
			return 0;
		}
		CompanionTask doing = c.scheduler().current();
		if (doing != null && (Pen.JOBS.contains(doing.id()) || doing.id().equals("terra.pen"))) {
			return 0; // in there on purpose
		}
		return SCORE;
	}

	@Override
	public boolean start(CompanionEntity c) {
		walk.reset();
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<Pen> pen = Pen.of(level);
		if (pen.isEmpty()) {
			return TaskStatus.SUCCESS;
		}
		boolean shut = !pen.get().animals(level).isEmpty();
		return switch (pen.get().leave(c, walk, shut)) {
			case DONE -> TaskStatus.SUCCESS;
			case FAILED -> TaskStatus.FAILURE;
			case RUNNING -> TaskStatus.RUNNING;
		};
	}

	@Override
	public void stop(CompanionEntity c) {
		walk.reset();
	}

	@Override
	public int failureCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return 20 * 40;
	}
}
