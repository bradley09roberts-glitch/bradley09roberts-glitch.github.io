package io.github.bradley09roberts.hardcorefriends.defence;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;

/**
 * Taking cover when the alarm rings: children and everyone who is not fit to fight ({@link Duty}) go indoors, to their
 * own home if it is safe to get to, else the nearest house, the town hall or another public building, or the cabin
 * ({@link Shelters}), never towards a creeper or (for a child) past any monster. Once in they shut the door behind them
 * (their own wooden doors only) and stay at a spot inside, away from the windows where the plan allows, until the
 * all-clear; one already indoors in a shelter simply stays put. At night, someone indoors whose bed is there too goes to
 * bed rather than standing about. It changes no block but the door.
 *
 * <p>It scores {@value #SCORE}: above all work and every ordinary need, below a desperate one (a starving friend eats
 * first) and below ringing the bell. The fighting reflexes keep running underneath (a child still runs from a monster
 * beside them to a grown-up). A friend who cannot get to any shelter after a few tries stays where they are.
 */
final class TakeCoverTask implements CompanionTask {
	static final String ID = "defence.take_cover";
	private static final double SCORE = 130;
	private static final double REACH = 1.2;
	private static final int RECHECK = 20;
	/** The door is shut this long after they are in (the vanilla door goal shuts the ones it opened first). */
	private static final int DOOR_DELAY = 20;

	private Shelters.@Nullable Shelter shelter;
	private boolean arrived;
	private boolean doorDone;
	private int ticks;
	private int inside;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return arrived ? "sheltering indoors until the all-clear" : "taking cover indoors";
	}

	@Override
	public double score(CompanionEntity c) {
		return Duty.needsCover(c) ? SCORE : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return false;
		}
		shelter = Shelters.choose(c, level);
		arrived = false;
		doorDone = false;
		ticks = 0;
		inside = 0;
		if (shelter == null) {
			Duty.coverFailed(c);
			return false;
		}
		if (Shelters.containing(level, c) == shelter) {
			arrived = true; // already in: stay put
		} else {
			Alarm.say(c, Line.TAKE_COVER);
		}
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Shelters.Shelter s = shelter;
		if (s == null || !(c.level() instanceof ServerLevel level) || !Alarm.isActive()) {
			return TaskStatus.SUCCESS; // the all-clear: out they come
		}
		ticks++;
		boolean night = Camp.isNightTime(level);
		if (!arrived) {
			if (ticks % RECHECK == 0 && !Shelters.safe(c, s, Alarm.present(level))) {
				Shelters.Shelter other = Shelters.choose(c, level);
				if (other == null) {
					c.actions().stopWalking();
					Duty.coverFailed(c);
					return TaskStatus.FAILURE; // nowhere safe to run to: the reflexes see to the danger
				}
				shelter = other;
				s = other;
			}
			if (c.actions().walkTo(s.spot(), REACH)) {
				arrived = true;
			} else if (c.actions().isStuck()) {
				if (Shelters.indoors(level, c)) {
					arrived = true; // in, if not quite at the spot
				} else {
					Duty.coverFailed(c);
					return TaskStatus.FAILURE;
				}
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (!doorDone && ++inside >= DOOR_DELAY) {
			doorDone = true;
			Shelters.shutDoor(c, s);
		}
		if (night && Duty.sleepsIndoors(c, level, level.getGameTime()) && Shelters.indoors(level, c)) {
			return TaskStatus.SUCCESS; // indoors with a bed indoors: off to bed (the sleep job)
		}
		if (ticks % 80 == 0) {
			c.getLookControl().setLookAt(c.getX() + c.getRandom().nextInt(7) - 3, c.getEyeY(), c.getZ() + c.getRandom().nextInt(7) - 3);
		}
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		shelter = null;
		arrived = false;
		doorDone = false;
		ticks = 0;
		inside = 0;
	}

	@Override
	public int failureCooldown() {
		return 60;
	}

	@Override
	public int successCooldown() {
		return 40;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 6;
	}
}
