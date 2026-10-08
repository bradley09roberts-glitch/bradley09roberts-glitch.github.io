package io.github.bradley09roberts.hardcorefriends.progress;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * The steps of Sage's plan to beat the game, in order. The camp works on one step at a time ({@link ProgressPlan#current}),
 * and each is checked against what the camp really has. The last three are finished by the expedition work (finding the
 * stronghold, opening the End portal, the dragon) through {@link ProgressPlan#complete}, so they never complete on
 * their own.
 */
public enum Milestone {
	SETTLED("Settled", "grow the camp into a Village"),
	IRON_AGE("Iron age", "iron pickaxes for the miners, iron swords for the fighters and a stock of iron"),
	DIAMONDS("Diamonds", "a diamond pickaxe in the camp"),
	ENCHANTING("Enchanting", "an enchanting table with 15 bookshelves round it"),
	NETHER_READY("Nether ready", "obsidian for a portal and a flint and steel"),
	BLAZE_RODS("Blaze rods", "at least 7 blaze rods"),
	ENDER_PEARLS("Ender pearls", "at least 12 ender pearls"),
	EYES_OF_ENDER("Eyes of ender", "at least 12 eyes of ender, and a few spare"),
	STRONGHOLD("Stronghold found", "find the stronghold"),
	END_PORTAL("End portal open", "open the End portal"),
	DRAGON("Dragon defeated", "defeat the ender dragon");

	private final String title;
	private final String goal;

	Milestone(String title, String goal) {
		this.title = title;
		this.goal = goal;
	}

	/** The step's name for players, e.g. "Iron age". */
	public String title() {
		return title;
	}

	/** What the step asks for, as a phrase that reads after "Our next goal: ". */
	public String goal() {
		return goal;
	}

	/** True for the steps only the expedition work can finish (they have no stock to count). */
	public boolean byExpedition() {
		return ordinal() >= STRONGHOLD.ordinal();
	}

	/** Lower-case key used in save data. */
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The step with this save key, or null. */
	public static @Nullable Milestone byKey(String key) {
		for (Milestone m : values()) {
			if (m.key().equals(key)) {
				return m;
			}
		}
		return null;
	}
}
