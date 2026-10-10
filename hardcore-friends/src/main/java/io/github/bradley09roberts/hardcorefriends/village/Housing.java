package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Who lives in which house, worked out again from the households every few seconds: everyone who has left the team
 * moves out; a household lives together in one house with a bed each; a household that has grown (a wedding, a baby)
 * moves to a house big enough as soon as there is one, and its old house goes to whoever needs one; a house built for
 * a household goes to that household first, and a family moves into the bigger house built for it even while the old
 * one still holds them all. A household that shares its house with another household's grown-ups (a grown-up child
 * still in their parents' house) only lodges there: it keeps its beds until a house of its own is free. What cannot
 * be met becomes a {@link Need}: a house to build. Children take beds in their parents' house. Only standing houses
 * are lived in.
 */
final class Housing {
	/**
	 * How pressing a household's need for a house is: none at all, too small, lodging in another household's house (a
	 * bed, but not a home of their own), or no spare bed for a baby.
	 */
	enum Urgency {
		NO_HOME,
		TOO_SMALL,
		LODGING,
		BABY_ROOM
	}

	/** A household that needs a (bigger) house, and how many beds it should have. */
	record Need(Households.Household household, int beds, Urgency urgency) {
	}

	/** Someone who moved into a house during a reconcile, for the friends to say so. */
	record Move(UUID who, VillageData.Plot home) {
	}

	private Housing() {
	}

	/** The number of beds a house plot has (its plan's bed spots), as recorded when it was planned. */
	static int capacity(VillageData.Plot plot) {
		return Math.max(0, Planner.bedCount(plot));
	}

	/**
	 * Brings the houses up to date with the households. Returns who moved in where. Children and grown-ups alike keep
	 * the beds they have unless the household moves.
	 */
	static List<Move> reconcile(VillageData v, Households.Roster roster) {
		List<Move> moves = new ArrayList<>();
		List<VillageData.Plot> houses = new ArrayList<>();
		for (VillageData.Plot p : v.plots()) {
			if (!p.isHouse()) {
				continue;
			}
			if (p.residents.keySet().removeIf(id -> !roster.contains(id))) {
				v.touch(); // left the team: died, dismissed, or gone
			}
			if (p.standing()) {
				houses.add(p);
			} else if (!p.residents.isEmpty()) {
				p.residents.clear(); // nobody lives in a house still being built
				v.touch();
			}
		}
		houses.sort(Comparator.comparingInt(Housing::capacity).thenComparingInt(p -> p.id));
		List<Households.Household> all = roster.households();
		// A household already together in a house with room for all stays (anyone of it not yet in moves in), unless a
		// bigger house built for it stands (the family that outgrew its house), or it only lodges with another household.
		List<Households.Household> unhoused = new ArrayList<>();
		Set<Households.Household> lodgers = new HashSet<>();
		for (Households.Household h : all) {
			VillageData.Plot together = together(houses, h);
			if (together != null) {
				VillageData.Plot bigger = biggerBuiltFor(houses, h, together, all);
				if (bigger != null) {
					moveAll(v, houses, bigger, h, moves);
					continue;
				}
				for (UUID m : h.members()) {
					if (!together.residents.containsKey(m)) {
						moveInto(v, houses, together, m);
						moves.add(new Move(m, together));
					}
				}
				if (lodging(together, h, all)) {
					unhoused.add(h); // keeps its beds here until a house of its own is free
					lodgers.add(h);
				}
				continue;
			}
			unhoused.add(h);
		}
		// Pass one: houses built for these very households.
		for (int i = 0; i < unhoused.size(); i++) {
			Households.Household h = unhoused.get(i);
			for (VillageData.Plot p : houses) {
				if (builtFor(p, h, all) && onlyTheirs(p, h) && capacity(p) >= h.size()) {
					moveAll(v, houses, p, h, moves);
					unhoused.remove(i--);
					break;
				}
			}
		}
		// Pass two: any other house with room that nobody it was built for still needs. A household that only lodges has a
		// bed each meanwhile, so it waits for a house with every bed it would like: a married couple at the parents' may
		// be expecting (the parents' spare bed let them), and a house with no bed for the baby would leave it without one.
		Set<UUID> stillWaiting = new HashSet<>();
		for (Households.Household h : unhoused) {
			stillWaiting.addAll(h.members());
		}
		for (int i = 0; i < unhoused.size(); i++) {
			Households.Household h = unhoused.get(i);
			int beds = lodgers.contains(h) ? h.wantBeds() : h.size();
			for (VillageData.Plot p : houses) {
				boolean reservedForOthers = false;
				for (UUID id : p.intended) {
					if (stillWaiting.contains(id) && !h.members().contains(id)) {
						reservedForOthers = true;
						break;
					}
				}
				if (!reservedForOthers && onlyTheirs(p, h) && capacity(p) >= beds) {
					moveAll(v, houses, p, h, moves);
					stillWaiting.removeAll(h.members());
					unhoused.remove(i--);
					break;
				}
			}
		}
		for (VillageData.Plot p : houses) {
			assignBeds(v, p);
		}
		return moves;
	}

	/**
	 * The one house the whole household lives in with room for them all, or null. A house where only its children
	 * live is not the household's: a parent who moved out when a marriage ended keeps a child who lives with them
	 * (Families' household), but is not moved straight back into the old home for it; the child keeps their bed there
	 * until the household has a house of its own.
	 */
	private static VillageData.@Nullable Plot together(List<VillageData.Plot> houses, Households.Household h) {
		VillageData.Plot found = null;
		for (VillageData.Plot p : houses) {
			if (!Collections.disjoint(p.residents.keySet(), h.members())) {
				if (found != null) {
					return null; // split between two houses
				}
				found = p;
			}
		}
		if (found == null || free(found, h) < h.size() || Collections.disjoint(found.residents.keySet(), h.adults())) {
			return null;
		}
		return found;
	}

	/**
	 * A standing house built for this household with more beds than the one it lives in, holding nobody else: the
	 * bigger house built when the family outgrew its own. Null if there is none.
	 */
	private static VillageData.@Nullable Plot biggerBuiltFor(List<VillageData.Plot> houses, Households.Household h,
		VillageData.Plot current, List<Households.Household> all) {
		VillageData.Plot best = null;
		for (VillageData.Plot p : houses) {
			if (p != current && capacity(p) > capacity(current) && capacity(p) >= h.size() && builtFor(p, h, all)
				&& onlyTheirs(p, h) && (best == null || capacity(p) > capacity(best))) {
				best = p;
			}
		}
		return best;
	}

	/**
	 * True if the house was built for this household: it holds some of the people the house was planned for, and no
	 * other household holds more of them (a family's house stays theirs when one of the children grows up).
	 */
	private static boolean builtFor(VillageData.Plot p, Households.Household h, List<Households.Household> all) {
		int mine = overlap(p.intended, h.members());
		if (mine == 0) {
			return false;
		}
		for (Households.Household other : all) {
			if (other != h && overlap(p.intended, other.members()) > mine) {
				return false;
			}
		}
		return true;
	}

	/**
	 * True if the household only lodges in this house: grown-ups of another household live there too, and that
	 * household has the better claim to it (more of its people living there, then more of those it was built for, then
	 * the first in the roster). The usual lodger is a grown-up child still in their parents' house.
	 */
	private static boolean lodging(VillageData.Plot p, Households.Household h, List<Households.Household> all) {
		int mine = overlap(p.residents.keySet(), h.members());
		int mineIntended = overlap(p.intended, h.members());
		for (Households.Household other : all) {
			if (other == h || Collections.disjoint(p.residents.keySet(), other.adults())) {
				continue;
			}
			int theirs = overlap(p.residents.keySet(), other.members());
			int theirsIntended = overlap(p.intended, other.members());
			if (theirs != mine ? theirs > mine
				: theirsIntended != mineIntended ? theirsIntended > mineIntended : all.indexOf(other) < all.indexOf(h)) {
				return true;
			}
		}
		return false;
	}

	private static int overlap(Set<UUID> a, Set<UUID> b) {
		int n = 0;
		for (UUID id : a) {
			if (b.contains(id)) {
				n++;
			}
		}
		return n;
	}

	/** True if nobody outside the household lives in the house: a household never moves in with strangers. */
	private static boolean onlyTheirs(VillageData.Plot p, Households.Household h) {
		return h.members().containsAll(p.residents.keySet());
	}

	/** Beds in the house not taken by people outside the household. */
	private static int free(VillageData.Plot p, Households.Household h) {
		int others = 0;
		for (UUID id : p.residents.keySet()) {
			if (!h.members().contains(id)) {
				others++;
			}
		}
		return capacity(p) - others;
	}

	private static void moveAll(VillageData v, List<VillageData.Plot> houses, VillageData.Plot target, Households.Household h,
		List<Move> moves) {
		for (UUID m : h.members()) {
			if (!target.residents.containsKey(m)) {
				moveInto(v, houses, target, m);
				moves.add(new Move(m, target));
			}
		}
	}

	/** Moves one person into a house, out of any other. Their bed is given out by {@link #assignBeds}. */
	static void moveInto(VillageData v, List<VillageData.Plot> houses, VillageData.Plot target, UUID who) {
		for (VillageData.Plot p : houses) {
			if (p != target && p.residents.remove(who) != null) {
				v.touch();
			}
		}
		for (VillageData.Plot p : v.plots()) {
			if (p != target && p.isHouse() && p.residents.remove(who) != null) {
				v.touch();
			}
		}
		target.residents.put(who, -1);
		v.touch();
	}

	/** Gives everyone in the house a bed of their own: those who have one keep it, the rest take the free ones in order. */
	static void assignBeds(VillageData v, VillageData.Plot p) {
		int capacity = capacity(p);
		Set<Integer> taken = new HashSet<>();
		Map<UUID, Integer> fixed = new LinkedHashMap<>();
		for (Map.Entry<UUID, Integer> e : p.residents.entrySet()) {
			int bed = e.getValue();
			if (bed >= 0 && bed < capacity && taken.add(bed)) {
				fixed.put(e.getKey(), bed);
			} else {
				fixed.put(e.getKey(), -1);
			}
		}
		int next = 0;
		for (Map.Entry<UUID, Integer> e : fixed.entrySet()) {
			if (e.getValue() < 0) {
				while (next < capacity && taken.contains(next)) {
					next++;
				}
				if (next < capacity) {
					e.setValue(next);
					taken.add(next);
				}
			}
		}
		if (!fixed.equals(p.residents)) {
			p.residents.clear();
			p.residents.putAll(fixed);
			v.touch();
		}
	}

	/**
	 * What houses the households need, most pressing first: households with no house at all, then households split
	 * between houses or without a bed each, then households lodging in another household's house (a grown-up child at
	 * their parents'), then married couples with no spare bed for a baby. A household that already has a house being
	 * built for it, or a bigger one standing ready for it, needs nothing more for now.
	 */
	static List<Need> needs(VillageData v, Households.Roster roster) {
		List<Need> list = new ArrayList<>();
		List<Households.Household> all = roster.households();
		for (Households.Household h : all) {
			if (planned(v, h, all)) {
				continue;
			}
			VillageData.Plot home = null;
			boolean split = false;
			boolean bedless = false;
			for (VillageData.Plot p : v.plots()) {
				if (!p.isHouse() || !p.standing()) {
					continue;
				}
				for (UUID m : h.members()) {
					Integer bed = p.residents.get(m);
					if (bed != null) {
						if (home != null && home != p) {
							split = true;
						}
						home = p;
						bedless |= bed < 0;
					}
				}
			}
			int housed = 0;
			for (UUID m : h.members()) {
				if (v.homeOf(m).isPresent()) {
					housed++;
				}
			}
			if (home == null) {
				list.add(new Need(h, h.wantBeds(), Urgency.NO_HOME));
			} else if (split || bedless || housed < h.size()) {
				list.add(new Need(h, h.wantBeds(), Urgency.TOO_SMALL));
			} else if (lodging(home, h, all)) {
				list.add(new Need(h, h.wantBeds(), Urgency.LODGING));
			} else if (capacity(home) < h.wantBeds()) {
				list.add(new Need(h, h.wantBeds(), Urgency.BABY_ROOM));
			}
		}
		list.sort(Comparator.comparing(Need::urgency).thenComparing(n -> -n.household().size()));
		return list;
	}

	/**
	 * True if a house is being built for (anyone of) this household, or one stands empty that was built for them with a
	 * bed for everyone they would like: {@link #reconcile} moves them into it, so no other is planned meanwhile.
	 */
	static boolean planned(VillageData v, Households.Household h, List<Households.Household> all) {
		for (VillageData.Plot p : v.plots()) {
			if (!p.isHouse() || Collections.disjoint(p.intended, h.members())) {
				continue;
			}
			if (!p.standing() || p.residents.isEmpty() && capacity(p) >= h.wantBeds() && builtFor(p, h, all)) {
				return true;
			}
		}
		return false;
	}
}
