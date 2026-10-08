package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A friend's everyday needs, Sims-style: Hunger, Energy, Social, Fun and Comfort, each from 0 (desperate) to 100
 * (fully satisfied). They drift down over time on their own; friends look after them with their own jobs (eating real
 * food, sleeping at night, chatting, relaxing, warming up by the fire), which score higher the lower the need is.
 * Together the needs make a mood, which speeds up or slows down their work and colours what they say.
 *
 * <p>Rates are per second of game time and tuned to the 20-minute Minecraft day: a friend gets through about half a
 * loaf of bread's worth of food a day (a loaf every two days, a little more when at work all day), is tired by
 * nightfall, and lonely or bored after half a day without company or fun. Sleep restores energy by the in-game time
 * slept, so a night the players sleep through counts as a whole night.
 */
public final class Needs {
	public enum Need {
		HUNGER("Hunger"),
		ENERGY("Energy"),
		SOCIAL("Social"),
		FUN("Fun"),
		COMFORT("Comfort");

		private final String title;

		Need(String title) {
			this.title = title;
		}

		public String title() {
			return title;
		}
	}

	public enum Mood {
		MISERABLE("miserable"),
		LOW("low"),
		OKAY("okay"),
		GOOD("good"),
		GREAT("great");

		private final String word;

		Mood(String word) {
			this.word = word;
		}

		public String word() {
			return word;
		}
	}

	private static final Need[] NEEDS = Need.values();
	/** How much each need weighs in the mood (sums to 1). */
	private static final double[] MOOD_WEIGHT = {0.30, 0.25, 0.15, 0.15, 0.15};

	// Per second. A Minecraft day is 1200 seconds.
	/** About 15 hunger a day, half a loaf of bread (30); see {@link #typicalDailyHunger}. */
	static final double HUNGER_DECAY = 100.0 / 9000.0;
	static final double HUNGER_WORK_FACTOR = 1.3;
	static final double ENERGY_DECAY = 70.0 / 840.0; // from rested at dawn to about 45 at nightfall, 30 by late evening
	/** Energy restored per in-game second asleep under a roof: about +100 over a whole night. */
	public static final double SLEEP_INDOOR_RATE = 100.0 / 500.0;
	/** Energy restored per in-game second asleep in the open: about +65 over a whole night. */
	public static final double SLEEP_OUTDOOR_RATE = 65.0 / 500.0;
	/** At or below this hunger a friend no longer heals, neither on their own nor at camp. */
	public static final double TOO_HUNGRY_TO_HEAL = 10;
	/** Seconds of daylight in a Minecraft day (the sky is bright from about tick 0 to 13000); the rest is night. */
	public static final double DAYLIGHT_SECONDS = 650;
	/** Seconds in a Minecraft day. */
	public static final double DAY_SECONDS = 1200;
	static final double SOCIAL_DECAY = 100.0 / 1000.0;
	static final double SOCIAL_COMPANY_GAIN = 0.15; // simply being near friends or a player
	static final double FUN_DECAY = 100.0 / 1100.0;
	static final double COMFORT_APPROACH = 0.5; // comfort drifts towards how comfortable the surroundings are

	private final double[] values = {80, 90, 70, 70, 70};

	public double get(Need need) {
		return values[need.ordinal()];
	}

	public void set(Need need, double value) {
		values[need.ordinal()] = Math.clamp(value, 0.0, 100.0);
	}

	public void add(Need need, double amount) {
		set(need, get(need) + amount);
	}

	/** The need furthest from satisfied. */
	public Need lowest() {
		Need low = Need.HUNGER;
		for (Need n : NEEDS) {
			if (get(n) < get(low)) {
				low = n;
			}
		}
		return low;
	}

	/** Weighted average of all needs, 0 to 100. */
	public double moodValue() {
		double total = 0;
		for (Need n : NEEDS) {
			total += get(n) * MOOD_WEIGHT[n.ordinal()];
		}
		return total;
	}

	public Mood mood() {
		double m = moodValue();
		if (m < 25) {
			return Mood.MISERABLE;
		}
		if (m < 45) {
			return Mood.LOW;
		}
		if (m < 65) {
			return Mood.OKAY;
		}
		if (m < 85) {
			return Mood.GOOD;
		}
		return Mood.GREAT;
	}

	/** Work speed from mood: 0.8 when miserable, 1.0 at an okay mood of 66, up to 1.1 when everything is met. */
	public double workSpeed() {
		return 0.8 + 0.3 * moodValue() / 100.0;
	}

	/**
	 * One second of everyday life. {@code working} is true while the friend is on a job (they get hungry and bored
	 * faster), {@code asleep} while sleeping (energy then does not drain: {@link #rest} restores it by the clock).
	 */
	public void tickSecond(CompanionEntity c, boolean working, boolean asleep) {
		add(Need.HUNGER, -hungerDrain(working));
		if (!asleep) {
			add(Need.ENERGY, -ENERGY_DECAY);
		}
		add(Need.SOCIAL, hasCompany(c) ? SOCIAL_COMPANY_GAIN : -SOCIAL_DECAY);
		if (working) {
			add(Need.FUN, -FUN_DECAY);
		}
		double target = comfortOfSurroundings(c);
		double comfort = get(Need.COMFORT);
		add(Need.COMFORT, Math.clamp(target - comfort, -COMFORT_APPROACH, COMFORT_APPROACH));
	}

	/** Hunger lost per second: a little faster while working. */
	public static double hungerDrain(boolean working) {
		return HUNGER_DECAY * (working ? HUNGER_WORK_FACTOR : 1.0);
	}

	/**
	 * Hunger a friend gets through in an ordinary day: at work through the daylight and asleep through the night.
	 * That is about 15.5, half a loaf of bread (30), so nine friends eat four or five loaves a day.
	 */
	public static double typicalDailyHunger() {
		return hungerDrain(true) * DAYLIGHT_SECONDS + hungerDrain(false) * (DAY_SECONDS - DAYLIGHT_SECONDS);
	}

	/** True while hunger is high enough for wounds to heal (above {@value #TOO_HUNGRY_TO_HEAL}). */
	public boolean canHeal() {
		return get(Need.HUNGER) > TOO_HUNGRY_TO_HEAL;
	}

	/**
	 * Restores energy for {@code ticks} of in-game time asleep, faster under a roof. Sleep counts by the clock, so a
	 * night the players sleep through restores as much as a night slept tick by tick.
	 */
	public void rest(CompanionEntity c, long ticks) {
		boolean indoors = !c.level().canSeeSky(c.blockPosition().above());
		add(Need.ENERGY, (indoors ? SLEEP_INDOOR_RATE : SLEEP_OUTDOOR_RATE) * ticks / 20.0);
	}

	/** Another friend or a player within 6 blocks. */
	public static boolean hasCompany(CompanionEntity c) {
		for (CompanionEntity other : Companions.all()) {
			if (other != c && other.level() == c.level() && other.distanceToSqr(c) <= 36) {
				return true;
			}
		}
		Player p = c.level().getNearestPlayer(c, 6);
		return p != null && !p.isSpectator();
	}

	/**
	 * How comfortable the surroundings are, 0 to 100: a roof overhead and a lit campfire nearby are cosy; rain,
	 * darkness in the open and being hurt are not.
	 */
	public static double comfortOfSurroundings(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 50;
		}
		BlockPos head = c.blockPosition().above();
		boolean open = level.canSeeSky(head);
		double comfort = 55;
		if (!open) {
			comfort += 20; // under a roof
		}
		if (nearLitCampfire(level, c.blockPosition(), 5)) {
			comfort += 25;
		}
		if (open && level.isRainingAt(head)) {
			comfort -= 30;
		}
		if (open && level.isDarkOutside()) {
			comfort -= 15;
		}
		if (c.getHealth() < c.getMaxHealth() * 0.5F) {
			comfort -= 20;
		}
		return Math.clamp(comfort, 0, 100);
	}

	public static boolean nearLitCampfire(ServerLevel level, BlockPos pos, int radius) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -2, -radius), pos.offset(radius, 2, radius))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT)) {
				return true;
			}
		}
		return false;
	}

	/** "Hunger 80, Energy 90, ..." for status lines. */
	public String summary() {
		StringBuilder b = new StringBuilder();
		for (Need n : NEEDS) {
			b.append(b.isEmpty() ? "" : ", ").append(n.title()).append(' ').append(Math.round(get(n)));
		}
		return b.toString();
	}

	/** A ten-block bar such as {@code ■■■■■■■□□□} for chat. */
	public static String bar(double value) {
		int filled = (int) Math.round(Math.clamp(value, 0, 100) / 10.0);
		return "■".repeat(filled) + "□".repeat(10 - filled);
	}

	public void save(ValueOutput output) {
		for (Need n : NEEDS) {
			output.putFloat("Need" + n.title(), (float) get(n));
		}
	}

	public void load(ValueInput input) {
		for (Need n : NEEDS) {
			set(n, input.getFloatOr("Need" + n.title(), (float) get(n)));
		}
	}

	@Override
	public String toString() {
		return summary() + " (mood " + mood().name().toLowerCase(Locale.ROOT) + ")";
	}
}
