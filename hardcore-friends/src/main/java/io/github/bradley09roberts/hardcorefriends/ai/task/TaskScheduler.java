package io.github.bradley09roberts.hardcorefriends.ai.task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Chooses which job a friend does next by scoring every task and running the most useful one. Re-scores every
 * second, switches when a clearly better job appears (by {@link #PREEMPT_MARGIN}), and fails tasks that run past
 * their time limit so a friend never stays stuck. Other people's work ({@link SpecialityTask}) has a known ceiling,
 * so it is not even scored when it could not win: with every friend able to do every job, that keeps choosing cheap.
 *
 * <p>Three rules keep the friend's own needs safe from work: a desperate need ({@link #URGENT}) takes over from any
 * job at once; work never wakes a sleeper; and a friend too weak to work
 * ({@link CompanionEntity#tooWeakToWork}) only takes on the jobs in {@link #FIT_WHEN_WEAK}.
 *
 * <p>A fourth keeps the night for sleep: at night ({@link Camp#isNight}) a friend only takes on their needs jobs and
 * the jobs that belong to the night ({@link #NIGHT_JOBS}: coming home, keeping watch, lighting a dark camp, feeding a
 * hurt friend or a hungry player, fetching a lost weapon or food), and puts down any other job at nightfall. So
 * ordinary work never keeps a friend from bed or pulls them out of it: they go to bed and stay asleep, and the friend
 * on watch keeps watch.
 */
public final class TaskScheduler {
	public static final double PREEMPT_MARGIN = 25.0;
	/**
	 * Desperate needs (starving with food at hand, exhausted at night) score this or more: above anything work can
	 * reach, which is at most 132 (building, 60, at the camp's full need for materials, weight 2.0, with Sage's 10%
	 * planning bonus). Such a job comes first, and takes over from a running job with a lower score at once.
	 */
	public static final double URGENT = 140;
	/**
	 * What a friend too weak to work (badly hurt and too hungry to heal) may still do besides their needs jobs: go
	 * home, use the camp chest, and grow and bake food in the camp, so a starving camp can still feed itself. Every
	 * other job, above all work outside the camp (the mine, felling, exploring), waits until they have eaten.
	 */
	public static final Set<String> FIT_WHEN_WEAK = Set.of("common.idle", "common.return_home", "common.restock",
		"common.deposit", "fern.harvest", "fern.replant", "fern.bake", "fern.bone_meal");
	/**
	 * The jobs that belong to the night, besides the needs jobs: coming home, the watch (anyone's, and Aegis's guard
	 * and gear), lighting a dark camp (spawn-proofing matters most in the dark), feeding a hurt friend or a hungry
	 * player, fetching a lost weapon or food from the chest, and idling by the fire. Everything else waits for
	 * morning.
	 */
	public static final Set<String> NIGHT_JOBS = Set.of("common.idle", "common.return_home", "common.watch",
		"common.share", "common.feed_player", "common.restock", "aegis.guard", "aegis.equip_gear", "terra.light");
	private static final String NEEDS = "needs.";
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
		boolean weak = companion.tooWeakToWork();
		if (weak && current != null && !fitWhenWeak(current.id())) {
			stopCurrent(); // too weak for this now: home to eat or rest
		}
		boolean night = Camp.isNight((ServerLevel) companion.level());
		if (night && current != null && !fitAtNight(current.id())) {
			stopCurrent(); // nightfall: the job waits for morning
		}
		// Work never wakes a sleeper: while they lie asleep, only another need can take over (a starving friend gets up
		// to eat). Danger wakes them through the sleep job and the reflexes.
		boolean sleeping = current != null && companion.isAsleep();
		CompanionTask best = null;
		double bestScore = 0;
		// Anything that cannot beat the running job by the margin could never take over, so it need not be scored.
		// (Only needs jobs reach URGENT, and other people's work never does.)
		double mustBeat = current != null ? currentScore + PREEMPT_MARGIN : 0;
		for (CompanionTask task : tasks) {
			if (task == current) {
				continue;
			}
			Long until = cooldownUntil.get(task.id());
			if (until != null && gameTime < until) {
				continue;
			}
			if ((weak && !fitWhenWeak(task.id())) || (night && !fitAtNight(task.id()))
				|| (sleeping && !task.id().startsWith(NEEDS))) {
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
			boolean urgent = bestScore >= URGENT && bestScore > currentScore;
			if (!urgent && bestScore < currentScore + PREEMPT_MARGIN) {
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

	/** True for the jobs a friend may take on at night: their needs and {@link #NIGHT_JOBS}. */
	public static boolean fitAtNight(String taskId) {
		return taskId.startsWith(NEEDS) || NIGHT_JOBS.contains(taskId);
	}

	/** True for the jobs a friend too weak to work may still take on: their needs and {@link #FIT_WHEN_WEAK}. */
	public static boolean fitWhenWeak(String taskId) {
		return taskId.startsWith(NEEDS) || FIT_WHEN_WEAK.contains(taskId);
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
