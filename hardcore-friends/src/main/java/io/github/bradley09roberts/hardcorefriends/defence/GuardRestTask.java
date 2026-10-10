package io.github.bradley09roberts.hardcorefriends.defence;

import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.SleepTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;

/**
 * Guards sleep in the day: a friend who stood guard last night ({@link GuardRota#stoodGuardRecently}) and is below
 * {@value #NAP_BELOW} energy lies down for a nap at home by day, as the night watch's keepers do. The nap itself is the
 * sleep job's ({@link SleepTask}: their own bed if they have one, the same waking rules, up again when rested or after
 * a few minutes), so it is the same rest, only offered sooner. Not while the alarm is on.
 */
final class GuardRestTask implements CompanionTask {
	static final String ID = "defence.guard_rest";
	private static final double NAP_BELOW = 60;
	/** The sleep job's own nap score. */
	private static final double SCORE = 75;

	private final SleepTask nap = new SleepTask();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "sleeping after a night on guard";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.isChild() || !(c.level() instanceof ServerLevel level) || Camp.isNightTime(level) || Alarm.isActive()) {
			return 0;
		}
		if (c.needs().get(Need.ENERGY) >= NAP_BELOW || !Spots.inCamp(c, c.blockPosition()) || !GuardRota.stoodGuardRecently(c)) {
			return 0;
		}
		return SCORE;
	}

	@Override
	public boolean start(CompanionEntity c) {
		return nap.start(c);
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (Alarm.isActive()) {
			return TaskStatus.SUCCESS; // the bell: up they get
		}
		return nap.tick(c);
	}

	@Override
	public void stop(CompanionEntity c) {
		nap.stop(c);
	}

	@Override
	public int failureCooldown() {
		return 20 * 30;
	}

	@Override
	public int successCooldown() {
		return 20 * 60;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 4;
	}
}
