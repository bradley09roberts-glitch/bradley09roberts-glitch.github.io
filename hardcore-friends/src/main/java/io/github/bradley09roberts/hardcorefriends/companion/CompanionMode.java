package io.github.bradley09roberts.hardcorefriends.companion;

/** What a friend does when nothing dangerous is happening. */
public enum CompanionMode {
	/** Autonomous routine: chooses useful jobs for the camp. */
	WORK,
	/** Stays with the player who gave the order, helps fight and carries loot. */
	FOLLOW,
	/** Holds the current spot and only defends themselves. */
	STAY;

	public static CompanionMode byOrdinal(int ordinal) {
		CompanionMode[] v = values();
		return ordinal >= 0 && ordinal < v.length ? v[ordinal] : WORK;
	}
}
