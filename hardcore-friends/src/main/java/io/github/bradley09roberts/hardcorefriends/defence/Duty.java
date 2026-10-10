package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
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
 * ({@link ToPostsTask}), except the friend on the night watch and the guards on duty, who keep their own.</li>
 * <li><b>Everyone else</b>, children above all, takes cover indoors ({@link TakeCoverTask}) when the village has a
 * shelter. At night a child or grown-up already indoors with a bed indoors (their home, the cabin) simply sleeps.</li>
 * </ul>
 *
 * <p>The filter ({@link #mayDo}) keeps three kinds of friend out of bed: the guards on duty, the fighters while the
 * alarm is on (the alarm gets them up, as the night watch's does), and those taking cover whose bed is out in the open.
 * Every other job choice is left to the scores.
 */
final class Duty {
	/** The sleep job's id: the only job the filter ever holds back. */
	static final String SLEEP = "needs.sleep";
	/** Below this energy nobody counts as a fighter: they would only be a danger to themselves. */
	private static final double TOO_TIRED = 20;
	/** A friend who cannot reach shelter this many times in one alarm stays where they are. */
	private static final int COVER_TRIES = 3;
	private static final int BED_CHECK = 20 * 10;

	private static final Set<UUID> FIGHTERS = new HashSet<>();
	private static final Set<UUID> COVER = new HashSet<>();
	private static final Set<UUID> NO_SLEEP = new HashSet<>();
	private static final Map<UUID, Integer> COVER_FAILS = new HashMap<>();
	/** Whether each friend sleeps indoors (a bed at home, or the cabin), and when that was looked at. */
	private static final Map<CompanionEntity, long[]> SLEEPS_INDOORS = new WeakHashMap<>();

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
			if (fighter(c)) {
				if (c != watcher && !GuardRota.isOnDuty(c)) {
					FIGHTERS.add(id);
				}
				NO_SLEEP.add(id);
				continue;
			}
			if (!shelters || COVER_FAILS.getOrDefault(id, 0) >= COVER_TRIES) {
				continue;
			}
			boolean bedIndoors = sleepsIndoors(c, level, now);
			if (night && bedIndoors && (c.isAsleep() || Shelters.indoors(level, c))) {
				continue; // tucked up indoors already: they sleep on
			}
			COVER.add(id);
			if (!bedIndoors) {
				NO_SLEEP.add(id); // their bed is out in the open: indoors they stay until the all-clear
			}
		}
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
		long[] known = SLEEPS_INDOORS.get(c);
		if (known != null && now - known[0] < BED_CHECK && now >= known[0]) {
			return known[1] != 0;
		}
		boolean indoors = Homes.get().bedFor(c).isPresent();
		if (!indoors) {
			BlockPos rest = c.restPos();
			indoors = level.isLoaded(rest) && !level.canSeeSky(rest.above()) && !rest.equals(c.homePos());
		}
		SLEEPS_INDOORS.put(c, new long[] {now, indoors ? 1 : 0});
		return indoors;
	}

	/** The job filter: only the sleep job is ever held back (see the class description). */
	static boolean mayDo(CompanionEntity c, String jobId) {
		return !SLEEP.equals(jobId) || !NO_SLEEP.contains(c.getUUID());
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
		SLEEPS_INDOORS.clear();
	}
}
