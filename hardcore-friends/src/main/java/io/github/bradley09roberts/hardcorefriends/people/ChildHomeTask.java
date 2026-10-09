package io.github.bradley09roberts.hardcorefriends.people;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Children go home early. From the end of the afternoon (time of day {@value #FROM}) until nightfall a child goes
 * home, to their bed in the family's village home or the camp's resting place (the cabin), and stays in quietly,
 * so they are indoors before the monsters come out; at nightfall the sleep job takes over and puts them to bed.
 * Changes no block.
 */
final class ChildHomeTask implements CompanionTask {
	static final String ID = People.JOB_PREFIX + "child_home";
	/** Home time. */
	static final long FROM = 11000;
	private static final double SCORE = 70;

	private @Nullable BlockPos home;
	private boolean arrived;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return arrived ? "staying in for the evening" : "going home for the evening";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!c.isChild() || !(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		long time = Camp.timeOfDay(level);
		return time >= FROM && time < 13000 && !Camp.isNightTime(level) ? SCORE : 0;
	}

	/** Their own bed at home if the village gave them one, otherwise the camp's resting place. */
	static BlockPos homeSpot(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos bed = Homes.get().bedFor(c).orElse(null);
		if (bed != null && level.isLoaded(bed)) {
			BlockPos beside = Spots.standable(level, bed.above());
			if (beside == null) {
				beside = Spots.standable(level, bed.north());
			}
			if (beside != null) {
				return beside;
			}
		}
		return c.restPos();
	}

	@Override
	public boolean start(CompanionEntity c) {
		home = homeSpot(c);
		arrived = c.blockPosition().closerThan(home, 3);
		if (!arrived) {
			Speech.say(c, Line.CHILD_BEDTIME);
		}
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		BlockPos to = home;
		if (to == null || !(c.level() instanceof ServerLevel level) || Camp.isNightTime(level) || Camp.timeOfDay(level) >= 13000) {
			return TaskStatus.SUCCESS; // bedtime: the sleep job takes over
		}
		if (!arrived) {
			if (c.actions().walkTo(to, 1.5)) {
				arrived = true;
			} else if (c.actions().isStuck()) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (c.tickCount % 60 == 0) {
			c.getLookControl().setLookAt(c.getX() + c.getRandom().nextInt(9) - 4, c.getEyeY(), c.getZ() + c.getRandom().nextInt(9) - 4);
		}
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		home = null;
		arrived = false;
	}

	@Override
	public int failureCooldown() {
		return 20 * 15;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
