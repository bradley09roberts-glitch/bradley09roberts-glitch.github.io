package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

	/** Which speciality each working friend covers, worked out once per game tick. */
	private static final Map<UUID, Role> COVER = new HashMap<>();
	private static long coverAt = Long.MIN_VALUE;

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

	/**
	 * Work speed of this friend at work of this kind: their {@link #skill(FriendId, Role) skill}, plus up to 20% for
	 * the experience they have gathered doing it ({@code survival.Skills}). Practice never lifts anyone above a
	 * specialist at the specialist's own work: outside their speciality a friend stays just under the specialist's
	 * starting speed.
	 */
	public static double skill(CompanionEntity c, @Nullable Role work) {
		double base = skill(c.friendId(), work);
		if (work == null) {
			return base;
		}
		double practised = base * (1.0 + io.github.bradley09roberts.hardcorefriends.survival.Skills.workBonus(c, work));
		return c.friendId().role() == work ? practised : Math.min(practised, SPECIALIST_SKILL - 0.01);
	}

	/** How fast a specialist starts out at their own work. */
	private static final double SPECIALIST_SKILL = 1.2;

	/** The kind of work a world edit belongs to, or null for none in particular. */
	public static @Nullable Role roleFor(WorldEditGuard.Reason reason) {
		return switch (reason) {
			case FARM -> Role.FARMER;
			case BUILD -> Role.BUILDER;
			case INVENT -> Role.INVENTOR;
			case LANDSCAPE -> Role.LANDSCAPER;
			case MINE -> Role.MINER;
			case GATHER_WOOD, GATHER_EARTH -> Role.FORAGER;
			case GRADE -> Role.LANDSCAPER;
			case CAST -> Role.MINER;
			case SURVIVAL, EXPEDITION -> null;
		};
	}

	/**
	 * The speciality this friend covers because nobody of it is working (its specialist is dead, not recruited,
	 * following a player or told to stay), or null. Exactly one working friend covers each such speciality, the one
	 * whose interest it is if possible: they keep, fetch and craft its tool, so a farm still gets tilled after the
	 * farmer is gone. Guarding and planning are never covered. Worked out once per game tick.
	 */
	public static @Nullable Role covering(CompanionEntity c) {
		long now = c.level().getGameTime();
		if (now != coverAt) {
			coverAt = now;
			assignCover();
		}
		return COVER.get(c.getUUID());
	}

	private static void assignCover() {
		COVER.clear();
		List<CompanionEntity> working = new ArrayList<>();
		Set<Role> staffed = EnumSet.noneOf(Role.class);
		for (CompanionEntity f : Companions.all()) {
			if (f.isAlive() && f.mode() == CompanionMode.WORK && !f.isChild()) { // children do no work
				working.add(f);
				staffed.add(f.friendId().role());
			}
		}
		working.sort(Comparator.comparingInt(CompanionEntity::rosterIndex));
		for (Role role : Role.values()) {
			if (staffed.contains(role) || role == Role.WARRIOR || role == Role.STRATEGIST) {
				continue;
			}
			CompanionEntity best = null;
			for (CompanionEntity f : working) {
				if (COVER.containsKey(f.getUUID())) {
					continue;
				}
				if (interest(f.friendId()) == role) {
					best = f;
					break;
				}
				if (best == null) {
					best = f;
				}
			}
			if (best != null) {
				COVER.put(best.getUUID(), role);
			}
		}
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
