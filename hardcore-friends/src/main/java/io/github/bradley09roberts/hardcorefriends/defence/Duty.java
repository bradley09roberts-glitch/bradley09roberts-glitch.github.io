package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.civic.Professions;
import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;

/**
 * Who does what while the alarm is on, worked out twice a second for everyone at work in the village, so the jobs'
 * scores and the job filter are a set lookup.
 *
 * <ul>
 * <li><b>Fighters</b> ({@link #fighter}): grown-ups who are fit to fight (healthy, not too tired, not falling back) and
 * are warriors, hold the guard trade, or carry a sword, an axe or a bow with arrows. They go to their posts
 * ({@link ToPostsTask}), except the friend on the night watch and the guards on duty, who keep their own (the watch
 * does even when they hold only a pick or a hoe and so are no fighter: they are not sent indoors). A fighter who wears
 * out below {@value #TOO_TIRED} energy, or is asleep with less than {@value #WAKE} when the alarm finds them, sits the
 * rest of this alarm out ({@link #TIRED}), so a tired fighter is not got up again each time a short sleep lifts them
 * back over the line.</li>
 * <li><b>Everyone else</b>, children above all, takes cover indoors ({@link TakeCoverTask}) when the village has a
 * shelter. At night a child or grown-up already indoors with a bed indoors (their home, the cabin) simply sleeps;
 * one found asleep out in the open (their own bed taken or out of reach, so they lay down the old way) is treated as
 * sleeping out in the open until the all-clear: got up, and kept indoors.</li>
 * </ul>
 *
 * <p>The filter ({@link #mayDo}) keeps three kinds of friend out of bed: the guards on duty, the fighters while the
 * alarm is on (the alarm gets them up, as the night watch's does), and those taking cover whose bed is out in the open.
 * It also keeps the guards on duty from their evening at home and from a feast at the square, which would otherwise
 * hold them from their posts until nightfall. Every other job choice is left to the scores.
 */
final class Duty {
	/** The sleep job's id: held back from everyone who must stay up (see the class description). */
	static final String SLEEP = "needs.sleep";
	/**
	 * Time off a guard on duty puts down for their post: the village's evening at home and going to a feast at the
	 * square (the village and village life packages' jobs, which outscore standing guard).
	 */
	private static final Set<String> OFF_DUTY = Set.of("needs.evening", "needs.festival");
	/** Below this energy nobody counts as a fighter: they would only be a danger to themselves. */
	private static final double TOO_TIRED = 20;
	/** A fighter asleep with less energy than this is left asleep for the alarm: a few minutes up would wear them out. */
	private static final double WAKE = 40;
	/** A friend who cannot reach shelter this many times in one alarm stays where they are. */
	private static final int COVER_TRIES = 3;
	private static final int BED_CHECK = 20 * 10;

	private static final Set<UUID> FIGHTERS = new HashSet<>();
	private static final Set<UUID> COVER = new HashSet<>();
	private static final Set<UUID> NO_SLEEP = new HashSet<>();
	private static final Map<UUID, Integer> COVER_FAILS = new HashMap<>();
	/** Friends whose work the alarm has already put down this time (see {@link #callAway}). */
	private static final Set<UUID> CALLED = new HashSet<>();
	/** Too tired to fight this alarm: out of it until the all-clear, however a nap lifts their energy meanwhile. */
	private static final Set<UUID> TIRED = new HashSet<>();
	/** Found asleep out in the open this alarm (their own bed taken or out of reach): their bed counts as outdoors. */
	private static final Set<UUID> SLEPT_OUT = new HashSet<>();
	/** Where each friend's bed is (see {@link #bedShelter}), and when that was looked at. */
	private record BedRecord(long at, @Nullable String shelter) {
	}

	private static final Map<CompanionEntity, BedRecord> SLEEPS_INDOORS = new WeakHashMap<>();

	private Duty() {
	}

	/** Twice a second, after the alarm's own look round. */
	static void tick(ServerLevel level, CampData data) {
		FIGHTERS.clear();
		COVER.clear();
		NO_SLEEP.clear();
		NO_SLEEP.addAll(GuardRota.onDutyIds());
		if (!Alarm.isActive()) {
			COVER_FAILS.clear();
			CALLED.clear();
			TIRED.clear();
			SLEPT_OUT.clear();
			return;
		}
		boolean shelters = !Shelters.all(level).isEmpty();
		boolean night = Camp.isNightTime(level);
		CompanionEntity watcher = NightWatch.watcher(level);
		long now = level.getGameTime();
		for (CompanionEntity c : Companions.in(level)) {
			if (c.mode() != CompanionMode.WORK || !Area.atHome(c)) {
				continue;
			}
			UUID id = c.getUUID();
			if (c == watcher || GuardRota.isOnDuty(c)) {
				NO_SLEEP.add(id); // the watch (armed or not) and the guards keep their own posts, never sent indoors
				continue;
			}
			double energy = c.needs().get(Need.ENERGY);
			if (energy < TOO_TIRED || c.isAsleep() && energy < WAKE) {
				TIRED.add(id); // worn out, or asleep and not rested enough to be got up: out of it until the all-clear
			}
			if (!TIRED.contains(id) && fighter(c)) {
				FIGHTERS.add(id);
				callAway(c, ToPostsTask.ID);
				NO_SLEEP.add(id);
				continue;
			}
			if (!shelters || COVER_FAILS.getOrDefault(id, 0) >= COVER_TRIES) {
				continue;
			}
			boolean asleepIndoors = c.isAsleep() && !level.canSeeSky(c.blockPosition().above());
			if (c.isAsleep() && !asleepIndoors) {
				SLEPT_OUT.add(id); // their own bed is taken or out of reach: they lay down the old way, out in the open
			}
			boolean bedIndoors = sleepsIndoors(c, level, now);
			if (night && bedIndoors && (asleepIndoors || inOwnShelter(c, level, now))) {
				continue; // tucked up indoors already, where their bed is: they sleep on
			}
			COVER.add(id);
			callAway(c, TakeCoverTask.ID);
			if (!bedIndoors) {
				NO_SLEEP.add(id); // their bed is out in the open: indoors they stay until the all-clear
			}
		}
	}

	/**
	 * Once an alarm, puts down whatever work this friend has in hand so the alarm's job ({@code job}) is chosen next:
	 * heavy work at the camp's full need scores more than the margin a job needs to take over from it. Their needs
	 * (a meal, a nap), getting unstuck and the defence's own jobs are left running (the scores sort those out), as is
	 * anything another package's filter would not let them leave for this job (a shopkeeper serving a player).
	 */
	private static void callAway(CompanionEntity c, String job) {
		if (!CALLED.add(c.getUUID())) {
			return;
		}
		CompanionTask current = c.scheduler().current();
		if (current == null) {
			return;
		}
		String id = current.id();
		if (id.startsWith("needs.") || id.startsWith("navigation.") || id.startsWith(Defence.JOB_PREFIX)) {
			return;
		}
		for (BiPredicate<CompanionEntity, String> filter : TaskScheduler.JOB_FILTERS) {
			if (!filter.test(c, job)) {
				return;
			}
		}
		c.scheduler().interrupt();
	}

	/**
	 * Fit to stand and fight at the alarm: grown up, not falling back, healthy, not too tired or weak, and a warrior, a
	 * guard by trade, or armed (a sword or an axe, or a bow with arrows).
	 */
	static boolean fighter(CompanionEntity c) {
		if (c.isChild() || c.isRetreating() || c.tooWeakToWork() || !c.isHealthy() || c.needs().get(Need.ENERGY) < TOO_TIRED) {
			return false;
		}
		if (c.isFighter() || isGuardByTrade(c)) {
			return true;
		}
		return c.isArmed() || Archery.canShoot(c);
	}

	/** True if the market package gave this friend the guard trade (a trade it may add; nothing breaks without it). */
	static boolean isGuardByTrade(CompanionEntity c) {
		return Professions.get().professionOf(c).map("guard"::equals).orElse(false);
	}

	/** Their bed is indoors: their own bed in their village home, or the cabin's resting place under its roof. */
	static boolean sleepsIndoors(CompanionEntity c, ServerLevel level, long now) {
		return bedShelter(c, level, now) != null;
	}

	/** True if this friend is indoors in the shelter where their own bed is (their home, or the cabin). */
	static boolean inOwnShelter(CompanionEntity c, ServerLevel level, long now) {
		String bed = bedShelter(c, level, now);
		Shelters.Shelter here = bed == null ? null : Shelters.containing(level, c);
		return here != null && here.key().equals(bed);
	}

	/**
	 * Which shelter their bed is in: their village home's key ({@code civic.Homes}), the cabin's, or null when they sleep
	 * out in the open (round the camp centre), or were found asleep out there this alarm ({@link #SLEPT_OUT}). Looked at
	 * every {@value #BED_CHECK} ticks at most.
	 */
	static @Nullable String bedShelter(CompanionEntity c, ServerLevel level, long now) {
		if (SLEPT_OUT.contains(c.getUUID())) {
			return null; // their own bed is taken or out of reach: indoors they stay, awake, until the all-clear
		}
		BedRecord known = SLEEPS_INDOORS.get(c);
		if (known != null && now - known.at() < BED_CHECK && now >= known.at()) {
			return known.shelter();
		}
		String shelter = null;
		if (Homes.get().bedFor(c).isPresent()) {
			shelter = Homes.get().homeOf(level.getServer(), c.getUUID()).map(Homes.Home::id).orElse(null);
		}
		if (shelter == null) {
			BlockPos rest = c.restPos();
			if (level.isLoaded(rest) && !level.canSeeSky(rest.above()) && !rest.equals(c.homePos())) {
				shelter = Structures.CABIN; // the cabin's resting place (CompanionEntity.restPos)
			}
		}
		SLEEPS_INDOORS.put(c, new BedRecord(now, shelter));
		return shelter;
	}

	/** The job filter: bed for those who must stay up, and time off for the guards on duty (see the class description). */
	static boolean mayDo(CompanionEntity c, String jobId) {
		if (SLEEP.equals(jobId)) {
			return !NO_SLEEP.contains(c.getUUID());
		}
		return !OFF_DUTY.contains(jobId) || !GuardRota.isOnDuty(c);
	}

	/** True while this friend should be at a post for the alarm. */
	static boolean atAlarm(CompanionEntity c) {
		return FIGHTERS.contains(c.getUUID());
	}

	/** True while this friend should be taking cover. */
	static boolean needsCover(CompanionEntity c) {
		return COVER.contains(c.getUUID());
	}

	/** This friend could not get to shelter (stuck): after a few tries they stay where they are this alarm. */
	static void coverFailed(CompanionEntity c) {
		COVER_FAILS.merge(c.getUUID(), 1, Integer::sum);
	}

	static int fighters() {
		return FIGHTERS.size();
	}

	static int takingCover() {
		return COVER.size();
	}

	static void clear() {
		FIGHTERS.clear();
		COVER.clear();
		NO_SLEEP.clear();
		COVER_FAILS.clear();
		CALLED.clear();
		TIRED.clear();
		SLEPT_OUT.clear();
		SLEEPS_INDOORS.clear();
	}
}
