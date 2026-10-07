package io.github.bradley09roberts.hardcorefriends.ai.task;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * One job a friend can choose to do. Instances are per-companion, so they may keep state in fields.
 *
 * <p>Score bands: 70–89 urgent upkeep, 40–69 main role work, 20–39 secondary help, 1–19 idle and social.
 * Return 0 when the task does not apply right now. {@link #score} runs at most once every 20 ticks, so it may do
 * small bounded scans, but anything larger should be cached.
 */
public interface CompanionTask {
	/** Stable id such as {@code "fern.harvest"}. */
	String id();

	/** Short phrase for status displays, such as {@code "harvesting crops"}. */
	String describe();

	double score(CompanionEntity companion);

	/** Prepare to run. Return false if the task cannot start after all; it then goes on failure cooldown. */
	boolean start(CompanionEntity companion);

	TaskStatus tick(CompanionEntity companion);

	/** Always called once after the task ends for any reason (success, failure, preemption, timeout, death). */
	void stop(CompanionEntity companion);

	/** Ticks before this task may be chosen again after it fails or cannot start. */
	default int failureCooldown() {
		return 200;
	}

	/** Ticks before this task may be chosen again after it succeeds. */
	default int successCooldown() {
		return 0;
	}

	/** Hard limit on how long one run may take before it is treated as stuck and failed. */
	default int maxTicks() {
		return 20 * 120;
	}
}
