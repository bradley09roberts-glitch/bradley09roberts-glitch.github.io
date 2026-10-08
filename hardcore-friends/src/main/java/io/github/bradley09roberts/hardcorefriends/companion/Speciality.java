package io.github.bradley09roberts.hardcorefriends.companion;

import org.jspecify.annotations.Nullable;

import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Every friend can do every kind of work, but each has a speciality (their role) and one other interest. They choose
 * their speciality first and are fastest at it; their interest comes next; anything else they do when it needs doing
 * and nobody better is on it, a little slower.
 */
public final class Speciality {
	/** How keen a friend is on their speciality, their interest, and other work (multiplies a job's score). */
	public static final double OWN = 1.0;
	public static final double INTEREST = 0.8;
	public static final double OTHER = 0.6;

	private Speciality() {
	}

	/** Each friend's second love besides their role. */
	public static Role interest(FriendId id) {
		return switch (id) {
			case FERN -> Role.FORAGER; // berries and apples for the larder
			case OAK -> Role.LANDSCAPER;
			case FLINT -> Role.INVENTOR; // smelting and furnaces
			case SCOUT -> Role.FORAGER;
			case SPARK -> Role.MINER; // redstone comes from below
			case AEGIS -> Role.EXPLORER;
			case SAGE -> Role.FARMER;
			case TERRA -> Role.FARMER;
			case ROWAN -> Role.BUILDER;
		};
	}

	/** How keen this friend is on work of this kind. */
	public static double affinity(FriendId id, Role work) {
		if (id.role() == work) {
			return OWN;
		}
		return interest(id) == work ? INTEREST : OTHER;
	}

	/** Work speed for work of this kind: specialists are quicker, everyone else a little slower. */
	public static double skill(FriendId id, @Nullable Role work) {
		if (work == null || id.role() == work) {
			return work == null ? 1.0 : 1.2;
		}
		return interest(id) == work ? 1.0 : 0.85;
	}

	/** The kind of work a world edit belongs to, or null for none in particular. */
	public static @Nullable Role roleFor(WorldEditGuard.Reason reason) {
		return switch (reason) {
			case FARM -> Role.FARMER;
			case BUILD -> Role.BUILDER;
			case INVENT -> Role.INVENTOR;
			case LANDSCAPE -> Role.LANDSCAPER;
			case MINE -> Role.MINER;
			case GATHER_WOOD, GATHER_EARTH -> Role.FORAGER;
		};
	}

	/** "farming", "building" and so on, for status lines. */
	public static String workName(Role role) {
		return switch (role) {
			case FARMER -> "farming";
			case BUILDER -> "building";
			case MINER -> "mining";
			case EXPLORER -> "exploring";
			case INVENTOR -> "redstone";
			case WARRIOR -> "guarding";
			case STRATEGIST -> "planning";
			case LANDSCAPER -> "landscaping";
			case FORAGER -> "foraging";
		};
	}
}
