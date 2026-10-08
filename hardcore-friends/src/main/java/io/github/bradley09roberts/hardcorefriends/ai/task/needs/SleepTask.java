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
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
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
 * <p><b>Bedtime.</b> At night everyone not on watch goes to bed ({@value #BEDTIME} and up, a little more the tireder
 * they are): above every pastime, chat and cosy warm-up, and, since the scheduler keeps ordinary work for the
 * morning ({@link TaskScheduler#NIGHT_JOBS}), nothing else keeps them up. Supper comes first for a hungry friend
 * with food at hand, warming up by the campfire first for a chilly one without a cabin to sleep in, and the trip home
 * first for one still out. Exhausted at night ({@value #EXHAUSTED}) they go to bed before anything but a starving
 * friend's meal. Work never wakes a sleeper (see {@link TaskScheduler}).
 *
 * <p><b>Waking.</b> Sleepers wake at dawn; when hurt; when a monster comes close or a friend nearby is fighting one;
 * when the night watch raises the alarm ({@link NightWatch}); and when their own watch begins. The danger reflexes
 * also interrupt the job, and {@link #stop} always gets them back on their feet.
 *
 * <p><b>The watch.</b> Whoever is on watch ({@link NightWatch}) stays up: Aegis keeps the first watch and sleeps the
 * small hours when no monster is about; the second watcher sleeps until midnight and is then woken for their watch.
 * Anyone who kept a watch naps by day sooner than the others ({@value #WATCHER_NAP_BELOW}) to make up for it.
 */
public final class SleepTask implements CompanionTask {
	/** Below this energy at night a friend goes to bed before any work (an urgent score). */
	static final double EXHAUSTED = 30;
	/** The score of going to bed exhausted: above {@link TaskScheduler#URGENT}, so no work keeps them up. */
	static final double EXHAUSTED_SCORE = TaskScheduler.URGENT;
	/**
	 * Bedtime at night, for a friend fully rested: above every pastime, chat and warm-up (at most 66), so those wait
	 * for the morning, and below a hungry friend's meal (80) once they are tired.
	 */
	static final double BEDTIME = 75;
	/** Supper first: a hungry friend with food at hand eats before bed. */
	static final double SUPPER_FIRST = 40;
	/** Below this hunger, supper comes before bed. */
	static final double SUPPER_BELOW = 50;
	/** Warm up first: below the cosy job's lowest score, so a chilly friend huddles by the fire before bed. */
	static final double WARM_UP_FIRST = 30;
	/** Below this energy in daytime, a friend lies down for a nap at camp. */
	static final double NAP_BELOW = 15;
	/** A friend who kept a night watch (Aegis keeps one every night) naps by day once below this. */
	static final double WATCHER_NAP_BELOW = 40;
	/** The longest daytime nap. */
	private static final int NAP_TICKS = 20 * 180;
	private static final double BED_REACH = 0.8;
	/** Sleepers wake when a monster comes this close. */
	private static final double WAKE_DISTANCE = 8;
	/** Aegis does not turn in with a monster this close. */
	private static final double PROWLER_DISTANCE = 24;
	private static final int RING = 3;

	private @Nullable BlockPos bed;
	private boolean lyingDown;
	private boolean nap;
	private int asleepTicks;
	/** When they lay down (game time), to tell an alarm raised since. */
	private long layDownAt;

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
		if (!Camp.isNight(level)) {
			boolean watcher = c.friendId() == FriendId.AEGIS || NightWatch.keptWatchRecently(c);
			return energy < (watcher ? WATCHER_NAP_BELOW : NAP_BELOW) ? 75 : 0;
		}
		if (!Spots.inCamp(c, c.blockPosition())) {
			return 0; // the trip home comes first
		}
		if (NightWatch.isOnWatch(c)) {
			return 0; // keeping watch (a watcher too tired to keep it is relieved, see NightWatch)
		}
		if (energy < EXHAUSTED) {
			return EXHAUSTED_SCORE; // only a starving friend's meal comes first
		}
		if (c.isFighter() && Threats.nearest(c, PROWLER_DISTANCE) != null) {
			return 0; // Aegis does not turn in while something is prowling about
		}
		double score = BEDTIME + (100 - energy) * 0.1; // 75 when fresh, 82 when worn out
		if (c.needs().get(Need.HUNGER) < SUPPER_BELOW && EatTask.foodAvailable(c)) {
			score = SUPPER_FIRST;
		} else if (c.needs().get(Need.COMFORT) < ComfortTask.CHILLY && !Spots.cabinBuilt(c) && Spots.litCampfire(c) != null) {
			score = WARM_UP_FIRST; // a few minutes by the fire, then bed
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
		if (NightWatch.isOnWatch(c)) {
			return TaskStatus.SUCCESS; // their watch: up they get (the watch job takes over)
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
		layDownAt = c.level().getGameTime();
		Speech.say(c, Line.SLEEPY);
	}

	/** Hurt by anything but hunger pangs, a monster close by, a friend fighting nearby, or the alarm raised in camp. */
	private boolean disturbed(CompanionEntity c) {
		if (c.ticksSinceDamaged() < 5) {
			DamageSource source = c.getLastDamageSource();
			if (source == null || !source.is(DamageTypes.STARVE)) {
				return true;
			}
		}
		if (asleepTicks % 10 != 0) {
			return false;
		}
		if (NightWatch.alarmRaisedSince((ServerLevel) c.level(), layDownAt) && NightWatch.insideCamp(c)) {
			return true; // the watch raised the alarm: everyone up
		}
		return Threats.nearest(c, WAKE_DISTANCE) != null || friendFighting(c);
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
		int index = c.rosterIndex();
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
