package io.github.bradley09roberts.hardcorefriends.ai.task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Chooses which job a friend does next by scoring every task and running the most useful one. Re-scores every
 * second, switches when a clearly better job appears (by {@link #PREEMPT_MARGIN}), and fails tasks that run past
 * their time limit so a friend never stays stuck. Other people's work ({@link SpecialityTask}) has a known ceiling,
 * so it is not even scored when it could not win: with every friend able to do every job, that keeps choosing cheap.
 */
public final class TaskScheduler {
	public static final double PREEMPT_MARGIN = 25.0;
	private static final int EVALUATE_INTERVAL = 20;

	/** Time spent choosing jobs, all friends together, for performance checks. */
	private static long evaluateNanos;
	private static long evaluations;

	private final CompanionEntity companion;
	private final List<CompanionTask> tasks;
	private final Map<String, Long> cooldownUntil = new HashMap<>();
	private @Nullable CompanionTask current;
	private double currentScore;
	private long currentStarted;
	private int evaluateTimer;
	private String lastActivity = "settling in";

	public TaskScheduler(CompanionEntity companion, List<CompanionTask> tasks) {
		this.companion = companion;
		this.tasks = tasks;
	}

	public List<CompanionTask> tasks() {
		return tasks;
	}

	public @Nullable CompanionTask current() {
		return current;
	}

	/** Human-readable activity for status messages. */
	public String activity() {
		return current != null ? current.describe() : lastActivity;
	}

	/** Runs one tick. Called by the work goal while no reflex (fleeing, fighting) has taken over. */
	public void tick(long gameTime) {
		if (--evaluateTimer <= 0) {
			evaluateTimer = EVALUATE_INTERVAL;
			evaluate(gameTime);
		}
		if (current == null) {
			return;
		}
		TaskStatus status;
		try {
			status = gameTime - currentStarted > current.maxTicks() ? TaskStatus.FAILURE : current.tick(companion);
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.error("Task {} of {} crashed; skipping it for a while", current.id(),
				companion.friendId().displayName(), e);
			status = TaskStatus.FAILURE;
		}
		if (status != TaskStatus.RUNNING) {
			finish(gameTime, status == TaskStatus.SUCCESS);
			evaluateTimer = 0;
		}
	}

	private void evaluate(long gameTime) {
		long began = System.nanoTime();
		try {
			choose(gameTime);
		} finally {
			evaluateNanos += System.nanoTime() - began;
			evaluations++;
		}
	}

	private void choose(long gameTime) {
		CompanionTask best = null;
		double bestScore = 0;
		// Anything that cannot beat the running job by the margin could never take over, so it need not be scored.
		double mustBeat = current != null ? currentScore + PREEMPT_MARGIN : 0;
		for (CompanionTask task : tasks) {
			if (task == current) {
				continue;
			}
			Long until = cooldownUntil.get(task.id());
			if (until != null && gameTime < until) {
				continue;
			}
			// Other people's work has a known ceiling: skip scoring it (and its scans) when it could not win anyway.
			if (task instanceof SpecialityTask shared) {
				double ceiling = shared.ceiling(companion);
				if (ceiling <= bestScore || ceiling < mustBeat) {
					continue;
				}
			}
			double score;
			try {
				score = task.score(companion);
			} catch (RuntimeException e) {
				HardcoreFriends.LOGGER.error("Scoring {} crashed", task.id(), e);
				cooldownUntil.put(task.id(), gameTime + 1200);
				continue;
			}
			if (score > bestScore) {
				bestScore = score;
				best = task;
			}
		}
		if (best == null) {
			return;
		}
		if (current != null) {
			if (bestScore < currentScore + PREEMPT_MARGIN) {
				return;
			}
			stopCurrent();
		}
		boolean started;
		try {
			started = best.start(companion);
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.error("Starting {} crashed", best.id(), e);
			started = false;
		}
		if (started) {
			current = best;
			currentScore = bestScore;
			currentStarted = gameTime;
		} else {
			cooldownUntil.put(best.id(), gameTime + best.failureCooldown());
			safeStop(best);
		}
	}

	private void finish(long gameTime, boolean success) {
		CompanionTask task = current;
		if (task == null) {
			return;
		}
		lastActivity = task.describe();
		int cooldown = success ? task.successCooldown() : task.failureCooldown();
		if (cooldown > 0) {
			cooldownUntil.put(task.id(), gameTime + cooldown);
		}
		current = null;
		safeStop(task);
	}

	private void stopCurrent() {
		CompanionTask task = current;
		current = null;
		if (task != null) {
			lastActivity = task.describe();
			safeStop(task);
		}
	}

	private void safeStop(CompanionTask task) {
		try {
			task.stop(companion);
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.error("Stopping {} crashed", task.id(), e);
		}
		companion.actions().reset();
	}

	/** Stops the running task, e.g. when a reflex takes over or the friend changes mode. */
	public void interrupt() {
		stopCurrent();
		evaluateTimer = 0;
	}

	/** Nanoseconds all friends have spent choosing jobs since the last {@link #resetTiming}. */
	public static long evaluateNanos() {
		return evaluateNanos;
	}

	/** How many times a friend chose a job since the last {@link #resetTiming}. */
	public static long evaluations() {
		return evaluations;
	}

	public static void resetTiming() {
		evaluateNanos = 0;
		evaluations = 0;
	}

	/** Puts a task on cooldown, e.g. after another routine handled what it wanted to do. */
	public void cooldown(String taskId, long gameTime, int ticks) {
		cooldownUntil.put(taskId, gameTime + ticks);
	}
}
