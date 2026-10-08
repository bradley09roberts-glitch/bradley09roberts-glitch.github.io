package io.github.bradley09roberts.hardcorefriends.companion;

/** What a friend does when nothing dangerous is happening. */
public enum CompanionMode {
	/** Autonomous routine: chooses useful jobs for the camp. */
	WORK,
	/** Stays with the player who gave the order, helps fight and carries loot. */
	FOLLOW,
	/** Holds the current spot and only defends themselves. */
	STAY,
	/**
	 * Not on the team yet: a newcomer living in a village, at a survivor camp or passing by, who can be recruited.
	 * They mind their own home, eat and sleep there, and defend themselves, but take no orders and do no camp work.
	 */
	STRANGER;

	public static CompanionMode byOrdinal(int ordinal) {
		CompanionMode[] v = values();
		return ordinal >= 0 && ordinal < v.length ? v[ordinal] : WORK;
	}
}
