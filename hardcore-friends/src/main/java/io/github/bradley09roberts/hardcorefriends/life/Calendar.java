package io.github.bradley09roberts.hardcorefriends.life;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;

import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * The village calendar, worked out from the overworld's day count (the clock that runs on through nights the players
 * sleep through), so it needs no saving and every part of the mod agrees on the date. Day 1 is the world's first day.
 * A week has seven days, Monday to Sunday, and Saturday is market day. A year has four seasons, spring, summer, autumn
 * and winter, of {@code daysPerSeason} days each (10 by default: a 40-day year). Three feasts mark the year: the
 * midsummer feast in the middle of summer, the harvest festival on the last day of autumn (if the farms did well), and
 * the winter lights in the middle of winter.
 */
public final class Calendar {
	public static final String[] WEEKDAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
	public static final String[] SEASONS = {"spring", "summer", "autumn", "winter"};
	/** Saturday. */
	public static final int MARKET_WEEKDAY = 5;
	/** A day is this many ticks of the overworld clock. */
	public static final long DAY_TICKS = 24000L;

	/** The year's three feasts. */
	public enum Feast {
		MIDSUMMER("the midsummer feast", "Midsummer feast"),
		HARVEST("the harvest festival", "Harvest festival"),
		WINTER_LIGHTS("the winter lights", "Winter lights");

		private final String phrase;
		private final String title;

		Feast(String phrase, String title) {
			this.phrase = phrase;
			this.title = title;
		}

		/** As it reads in a sentence: "the midsummer feast". */
		public String phrase() {
			return phrase;
		}

		/** As a heading: "Midsummer feast". */
		public String title() {
			return title;
		}

		/** Its day of the year (0 is the first day of spring). */
		public int dayOfYear() {
			int season = seasonLength();
			return switch (this) {
				case MIDSUMMER -> season + season / 2;
				case HARVEST -> 3 * season - 1;
				case WINTER_LIGHTS -> 3 * season + season / 2;
			};
		}
	}

	private Calendar() {
	}

	/** Days in a season, from the settings. */
	public static int seasonLength() {
		return Math.clamp(FriendsConfig.get().daysPerSeason, 3, 30);
	}

	public static int yearLength() {
		return 4 * seasonLength();
	}

	/** Today's number by the overworld clock, counting from 0. */
	public static long today(MinecraftServer server) {
		return Math.max(0, server.overworld().getOverworldClockTime()) / DAY_TICKS;
	}

	/** The time of day, 0 to 23999 (0 sunrise, 6000 noon, 12000 sunset, 18000 midnight). */
	public static long time(MinecraftServer server) {
		return Math.floorMod(server.overworld().getOverworldClockTime(), DAY_TICKS);
	}

	public static int dayOfYear(long day) {
		return (int) Math.floorMod(day, (long) yearLength());
	}

	/** 0 spring, 1 summer, 2 autumn, 3 winter. */
	public static int season(long day) {
		return dayOfYear(day) / seasonLength();
	}

	/** 1 for the first day of a season. */
	public static int dayOfSeason(long day) {
		return dayOfYear(day) % seasonLength() + 1;
	}

	/** 1 for the first year. */
	public static long year(long day) {
		return Math.max(0, day) / yearLength() + 1;
	}

	public static int weekday(long day) {
		return (int) Math.floorMod(day, 7L);
	}

	public static String weekdayName(long day) {
		return WEEKDAYS[weekday(day)];
	}

	public static String seasonName(long day) {
		return SEASONS[season(day)];
	}

	/** "Day 42, spring": how the Chronicle dates its lines. */
	public static String label(long day) {
		return "Day " + (day + 1) + ", " + seasonName(day);
	}

	/** "Wednesday, day 42: the 2nd day of summer, year 2". */
	public static String longDate(long day) {
		return weekdayName(day) + ", day " + (day + 1) + ": the " + ordinal(dayOfSeason(day)) + " day of " + seasonName(day)
			+ ", year " + year(day);
	}

	/** True on market day (every Saturday). */
	public static boolean marketDay(long day) {
		return weekday(day) == MARKET_WEEKDAY;
	}

	/** The feast falling on this day, if any. */
	public static @Nullable Feast feastOn(long day) {
		int d = dayOfYear(day);
		for (Feast f : Feast.values()) {
			if (f.dayOfYear() == d) {
				return f;
			}
		}
		return null;
	}

	/** True on the evenings someone plays music: every other day, and every feast and market day. */
	public static boolean musicEvening(long day) {
		return weekday(day) % 2 == 1 || weekday(day) == 6 || feastOn(day) != null;
	}

	/** True on someone's birthday: the same day of the year as the day they joined or were born, a year or more on. */
	public static boolean birthday(long joined, long day) {
		return day > joined && dayOfYear(day) == dayOfYear(joined);
	}

	/** How many whole years since a day. */
	public static long yearsSince(long from, long day) {
		return Math.max(0, day - from) / yearLength();
	}

	/** "1st", "2nd", "3rd", "4th"... */
	public static String ordinal(int n) {
		int mod100 = n % 100;
		String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
		return n + suffix;
	}

	/** "Morning", "afternoon", "evening" or "night", for the calendar's heading. */
	public static String partOfDay(long time) {
		if (time < 6000) {
			return "morning";
		}
		if (time < 11000) {
			return "afternoon";
		}
		if (time < 13000) {
			return "evening";
		}
		return time < 23000 ? "night" : "dawn";
	}

	/** Capitalised first letter. */
	static String capital(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}
}
