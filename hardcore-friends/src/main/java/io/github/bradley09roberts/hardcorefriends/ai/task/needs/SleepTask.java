package io.github.bradley09roberts.hardcorefriends.ai.task.needs;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Pose;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Sleeps through the night at home: each friend has their own place to lie down, inside the cabin once it is built,
 * otherwise in a ring around the camp centre. Sleep restores energy by the in-game time slept (faster under a roof: a
 * full night in the cabin is about +100, see {@link CompanionEntity#settleSleep}), so a night the players sleep
 * through counts in full, and ends at dawn with a good morning. A friend exhausted in daytime takes a short nap at
 * camp.
 *
 * <p>The tireder the friend, the sooner they turn in: a friend still full of energy finishes useful work in camp
 * first, and a hungry one eats before bed; one exhausted at night ({@value #EXHAUSTED}) goes to bed whatever the
 * work. Work never wakes a sleeper (see {@link io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler}).
 * Sleepers wake whenever they are hurt or a monster comes close; the danger reflexes also interrupt the job, and
 * {@link #stop} always gets them back on their feet.
 *
 * <p>Aegis keeps the first watch: he guards until midnight and sleeps the small hours when no monster is about, and
 * naps by day sooner than the others to make up for it.
 */
public final class SleepTask implements CompanionTask {
	/** Below this energy at night a friend goes to bed before any work (an urgent score). */
	static final double EXHAUSTED = 30;
	/** The score of going to bed exhausted: above {@link TaskScheduler#URGENT}, so no work keeps them up. */
	static final double EXHAUSTED_SCORE = TaskScheduler.URGENT;
	/** Below this energy in daytime, a friend lies down for a nap at camp. */
	static final double NAP_BELOW = 15;
	/** Aegis spends half the night on watch, so he naps by day once below this. */
	static final double GUARD_NAP_BELOW = 40;
	/** Aegis keeps watch until midnight. */
	static final long GUARD_SLEEPS_FROM = 18000;
	/** The longest daytime nap. */
	private static final int NAP_TICKS = 20 * 180;
	private static final double BED_REACH = 0.8;
	/** Sleepers wake when a monster comes this close. */
	private static final double WAKE_DISTANCE = 8;
	private static final int RING = 3;

	private @Nullable BlockPos bed;
	private boolean lyingDown;
	private boolean nap;
	private int asleepTicks;

	@Override
	public String id() {
		return "needs.sleep";
	}

	@Override
	public String describe() {
		if (lyingDown) {
			return nap ? "taking a nap" : "sleeping";
		}
		return "going to bed";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		double energy = c.needs().get(Need.ENERGY);
		boolean guard = c.friendId() == FriendId.AEGIS;
		if (!Camp.isNight(level)) {
			return energy < (guard ? GUARD_NAP_BELOW : NAP_BELOW) ? 75 : 0;
		}
		if (!Spots.inCamp(c, c.blockPosition())) {
			return 0; // the trip home comes first
		}
		if (guard) {
			if (energy < NAP_BELOW) {
				return 80; // too tired to keep watch
			}
			// The first watch is his; after midnight he turns in, unless something is prowling about.
			return Camp.timeOfDay(level) >= GUARD_SLEEPS_FROM && Threats.nearest(c, 24) == null ? 75 : 0;
		}
		if (energy < EXHAUSTED) {
			return EXHAUSTED_SCORE; // only a starving friend's meal comes first
		}
		double score = 25 + (100 - energy) * 0.6; // 31 when fresh, 67 when worn out at the end of a day
		if (c.needs().get(Need.HUNGER) < 50 && EatTask.foodAvailable(c)) {
			score = Math.min(score, 40); // supper first
		}
		return score;
	}

	@Override
	public boolean start(CompanionEntity c) {
		nap = !Camp.isNight((ServerLevel) c.level());
		lyingDown = false;
		asleepTicks = 0;
		bed = bedFor(c);
		return bed != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (bed == null) {
			return TaskStatus.FAILURE;
		}
		if (!lyingDown) {
			boolean there = c.actions().walkTo(bed, BED_REACH);
			if (!there && c.actions().isStuck()) {
				if (c.blockPosition().distSqr(bed) > 9) {
					return TaskStatus.FAILURE;
				}
				there = true; // close enough: lie down here
			}
			if (there) {
				lieDown(c);
			}
			return TaskStatus.RUNNING;
		}
		if (disturbed(c)) {
			return TaskStatus.FAILURE; // stop() gets them up
		}
		c.getNavigation().stop();
		asleepTicks++;
		c.settleSleep(); // energy for the time slept, a whole night if the players slept through it
		double energy = c.needs().get(Need.ENERGY);
		boolean night = Camp.isNight(level);
		if (nap && night) {
			nap = false; // dozed off into the night: sleep till morning
		}
		if (!nap && !night) {
			Speech.say(c, energy >= 80 ? Line.RESTED : Line.MORNING);
			return TaskStatus.SUCCESS;
		}
		if (nap && (energy >= 99.5 || asleepTicks >= NAP_TICKS)) {
			if (energy >= 80) {
				Speech.say(c, Line.RESTED);
			}
			return TaskStatus.SUCCESS;
		}
		return TaskStatus.RUNNING;
	}

	private void lieDown(CompanionEntity c) {
		c.actions().stopWalking();
		BlockPos centre = c.restPos();
		float yaw = centre.equals(c.blockPosition()) || Spots.sheltered((ServerLevel) c.level(), c.blockPosition())
			? 0.0F
			: (float) (Mth.atan2(centre.getZ() - c.getZ() + 0.5, centre.getX() - c.getX() + 0.5) * Mth.RAD_TO_DEG) - 90.0F;
		c.setYRot(yaw);
		c.setYBodyRot(yaw);
		c.setYHeadRot(yaw);
		c.setXRot(0.0F);
		c.setPose(Pose.SLEEPING);
		c.setAsleep(true);
		lyingDown = true;
		Speech.say(c, Line.SLEEPY);
	}

	/** Hurt by anything but hunger pangs, or a monster close by. */
	private boolean disturbed(CompanionEntity c) {
		if (c.ticksSinceDamaged() < 5) {
			DamageSource source = c.getLastDamageSource();
			if (source == null || !source.is(DamageTypes.STARVE)) {
				return true;
			}
		}
		return asleepTicks % 10 == 0 && (Threats.nearest(c, WAKE_DISTANCE) != null || friendFighting(c));
	}

	/** A friend within 16 blocks is fighting a monster: sleepers get up and stand with them. */
	private static boolean friendFighting(CompanionEntity c) {
		for (CompanionEntity other : Companions.all()) {
			if (other != c && other.level() == c.level() && other.distanceToSqr(c) <= 16 * 16
				&& other.getTarget() != null && Threats.isThreat(other.getTarget())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * This friend's own place to sleep: side by side inside the cabin, or a ring around the camp centre (each friend
	 * in their own spot, so nobody lies on top of anyone else). Falls back to any free spot near home.
	 */
	static @Nullable BlockPos bedFor(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos rest = c.restPos();
		int index = c.friendId().ordinal();
		if (Spots.cabinBuilt(c)) {
			int[][] slots = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {1, 1}, {-1, 1}, {0, -1}, {1, -1}, {-1, -1}};
			for (int k = 0; k < slots.length; k++) {
				int[] s = slots[(index + k) % slots.length];
				BlockPos p = rest.offset(s[0], 0, s[1]);
				if (Spots.isStandable(level, p) && Spots.sheltered(level, p)) {
					return p;
				}
			}
			return rest;
		}
		int count = FriendId.values().length;
		for (int k = 0; k < count; k++) {
			double angle = (index + k) * 2 * Math.PI / count;
			BlockPos p = Spots.standable(level, rest.offset((int) Math.round(Math.cos(angle) * RING), 0,
				(int) Math.round(Math.sin(angle) * RING)));
			if (p != null && Spots.inCamp(c, p)) {
				return p;
			}
		}
		BlockPos near = Spots.randomNear(c, rest, RING + 1);
		return near != null ? near : rest;
	}

	@Override
	public void stop(CompanionEntity c) {
		wake(c);
		bed = null;
		lyingDown = false;
		asleepTicks = 0;
	}

	/** Gets a friend back on their feet. Safe to call when they are already awake. */
	static void wake(CompanionEntity c) {
		if (c.getPose() == Pose.SLEEPING) {
			c.setPose(Pose.STANDING);
		}
		c.setAsleep(false);
	}

	@Override
	public int failureCooldown() {
		return 200;
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 10; // a whole night, with time to walk to bed
	}
}
