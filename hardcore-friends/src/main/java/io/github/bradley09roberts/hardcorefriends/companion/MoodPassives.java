package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.Collection;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.EatTask;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Mood;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;

/**
 * Lets a friend's mood show in what they say. Every {@value #CHECK_INTERVAL} ticks a friend who is awake and not
 * fighting may ask for food (hungry, with none in the backpack or the supply chest) or say that they are starving
 * (hunger at 0), or, when not falling back either, that they feel low and which need is worst (mood low or
 * miserable), or that they feel great. The lines' own cooldowns and the friend's chattiness keep this to now and then,
 * and a friend already seeing to the need in question does not complain.
 *
 * <p>Also holds the mood helpers the status displays share: the job that sees to each need, a friend's mood in words,
 * and the team's mood (a team in high spirits feeds the Unity bond, see
 * {@link io.github.bradley09roberts.hardcorefriends.unity.Unity#teamSpirit}).
 */
public final class MoodPassives {
	/** How often a friend considers saying how they feel, in ticks (staggered between friends). */
	public static final int CHECK_INTERVAL = 20 * 15;
	/**
	 * The team's average mood value at which the team is in high spirits (with nobody feeling low): the upper half of
	 * a good mood, where a well-fed, rested camp spends much of the day.
	 */
	public static final double HIGH_SPIRITS = 75;

	private MoodPassives() {
	}

	/** Called every server tick from the companion. */
	public static void tick(CompanionEntity c) {
		if ((c.tickCount + c.getId()) % CHECK_INTERVAL == 0) {
			voice(c);
		}
	}

	/**
	 * Says how this friend feels if there is something worth saying: hunger first (asking for food when there is
	 * none in the backpack or the supply chest, or saying they are starving), then a low mood (naming the worst need),
	 * then a great one. Returns the line they reached for, or null when there is nothing to say; {@link Speech#say}
	 * still applies the cooldowns, so the line is not always heard.
	 */
	public static @Nullable Line voice(CompanionEntity c) {
		if (!c.isAlive() || c.isAsleep() || c.getTarget() != null) {
			return null; // sleeping or fighting: nothing to say about feelings
		}
		Needs needs = c.needs();
		double hunger = needs.get(Need.HUNGER);
		// Starving hurts, so a starving friend is often falling back or resting too: saying why matters more then.
		if (hunger < EatTask.HUNGRY && !seeingTo(c, Need.HUNGER)) { // the eating job speaks for itself
			// One line at a time: with nothing to eat anywhere, ask for food first; a starving friend who already
			// asked says how bad it is.
			if (!saidRecently(c, Line.NO_FOOD) && !EatTask.foodAvailable(c)) {
				Speech.say(c, Line.NO_FOOD);
				return Line.NO_FOOD;
			}
			if (hunger <= 0) {
				Speech.say(c, Line.STARVING);
				return Line.STARVING;
			}
		}
		if (c.isRetreating()) {
			return null;
		}
		Mood mood = needs.mood();
		if (mood == Mood.LOW || mood == Mood.MISERABLE) {
			Need worst = needs.lowest();
			if (seeingTo(c, worst)) {
				return null;
			}
			Speech.say(c, Line.MOOD_LOW, word(worst));
			return Line.MOOD_LOW;
		}
		if (mood == Mood.GREAT) {
			Speech.say(c, Line.MOOD_GREAT);
			return Line.MOOD_GREAT;
		}
		return null;
	}

	/** True when the friend said this line within its own cooldown, so saying it again now would be skipped. */
	private static boolean saidRecently(CompanionEntity c, Line line) {
		Long last = c.speechMemory().get(line);
		return last != null && c.level().getGameTime() - last < line.cooldownTicks();
	}

	/** "hunger", "energy", "social", "fun" or "comfort": how lines and messages name a need. */
	public static String word(Need need) {
		return need.title().toLowerCase(Locale.ROOT);
	}

	/** The id of the needs job that looks after a need (the jobs in {@code ai/task/needs}). */
	public static String jobFor(Need need) {
		return switch (need) {
			case HUNGER -> "needs.eat";
			case ENERGY -> "needs.sleep";
			case SOCIAL -> "needs.socialize";
			case FUN -> "needs.leisure";
			case COMFORT -> "needs.cosy";
		};
	}

	/** The job a friend is on right now, or null when idle or not working (following or staying). */
	public static @Nullable CompanionTask currentJob(CompanionEntity c) {
		return c.mode() == CompanionMode.WORK ? c.scheduler().current() : null;
	}

	/** True while the friend's current job is the one that looks after this need. */
	public static boolean seeingTo(CompanionEntity c, Need need) {
		CompanionTask job = currentJob(c);
		return job != null && job.id().equals(jobFor(need));
	}

	/** The mood word, plus the worst need when the mood is low: "good", or "low (worst: hunger 12)". */
	public static String moodText(CompanionEntity c) {
		Needs needs = c.needs();
		Mood mood = needs.mood();
		if (mood != Mood.LOW && mood != Mood.MISERABLE) {
			return mood.word();
		}
		Need worst = needs.lowest();
		return mood.word() + " (worst: " + word(worst) + " " + Math.round(needs.get(worst)) + ")";
	}

	/**
	 * The team's mood: the mood of a friend whose every need sits at the team's average. Null when there are no
	 * friends.
	 */
	public static @Nullable Mood teamMood(Collection<CompanionEntity> friends) {
		return friends.isEmpty() ? null : averageNeeds(friends).mood();
	}

	/** The team's mood value, 0 to 100: that of a friend whose every need sits at the team's average (0 for nobody). */
	public static double teamMoodValue(Collection<CompanionEntity> friends) {
		return friends.isEmpty() ? 0 : averageNeeds(friends).moodValue();
	}

	/**
	 * True when the team is in high spirits: their average mood is at least {@value #HIGH_SPIRITS} and nobody feels low
	 * or miserable. A well-fed, rested camp sits there for much of the day (a great mood, 85, is a peak that a whole
	 * team only reaches together for a moment).
	 */
	public static boolean highSpirits(Collection<CompanionEntity> friends) {
		if (friends.isEmpty() || teamMoodValue(friends) < HIGH_SPIRITS) {
			return false;
		}
		for (CompanionEntity c : friends) {
			Mood mood = c.needs().mood();
			if (mood == Mood.LOW || mood == Mood.MISERABLE) {
				return false;
			}
		}
		return true;
	}

	private static Needs averageNeeds(Collection<CompanionEntity> friends) {
		Needs average = new Needs();
		for (Need need : Need.values()) {
			double total = 0;
			for (CompanionEntity c : friends) {
				total += c.needs().get(need);
			}
			average.set(need, total / friends.size());
		}
		return average;
	}
}
