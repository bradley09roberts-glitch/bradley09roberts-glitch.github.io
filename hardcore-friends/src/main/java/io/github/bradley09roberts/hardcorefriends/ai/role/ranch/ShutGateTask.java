package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Shuts the pen gate when it stands open with animals inside and nobody in the paddock (the pen is built with its gate
 * open; a player may leave it open too). Never while a player is in or right by the pen, or anything stands in the
 * gateway.
 */
public final class ShutGateTask implements CompanionTask {
	public static final String ID = "fern.shut_gate";
	private static final double SCORE = 45;

	private final Pen.GateWalk walk = new Pen.GateWalk();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "shutting the pen gate";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Pen pen = Pen.of(level).orElse(null);
		if (pen == null || !pen.gateOpen(level) || pen.holds(c) || Pen.otherAtWork(c)) {
			return 0;
		}
		if (pen.animals(level).isEmpty() || !pen.safeToShut(level, c)) {
			return 0;
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
			return TaskStatus.FAILURE;
		}
		return switch (pen.get().leave(c, walk, true)) {
			case DONE -> pen.get().gateOpen(level) ? TaskStatus.FAILURE : TaskStatus.SUCCESS;
			case FAILED -> TaskStatus.FAILURE;
			case RUNNING -> TaskStatus.RUNNING;
		};
	}

	@Override
	public void stop(CompanionEntity c) {
		walk.reset();
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int maxTicks() {
		return 20 * 40;
	}
}
