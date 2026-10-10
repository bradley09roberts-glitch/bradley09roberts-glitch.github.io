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
 * a household goes to that household first. What cannot be met becomes a {@link Need}: a house to build. Children take
 * beds in their parents' house. Only standing houses are lived in.
 */
final class Housing {
	/** How pressing a household's need for a house is: none at all, too small, or no spare bed for a baby. */
	enum Urgency {
		NO_HOME,
		TOO_SMALL,
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
		// A household already together in a house with room for all stays (anyone of it not yet in moves in).
		List<Households.Household> unhoused = new ArrayList<>();
		for (Households.Household h : roster.households()) {
			VillageData.Plot together = together(houses, h);
			if (together != null) {
				for (UUID m : h.members()) {
					if (!together.residents.containsKey(m)) {
						moveInto(v, houses, together, m);
						moves.add(new Move(m, together));
					}
				}
				continue;
			}
			unhoused.add(h);
		}
		// Pass one: houses built for these very households.
		for (int i = 0; i < unhoused.size(); i++) {
			Households.Household h = unhoused.get(i);
			for (VillageData.Plot p : houses) {
				if (!Collections.disjoint(p.intended, h.members()) && onlyTheirs(p, h) && capacity(p) >= h.size()) {
					moveAll(v, houses, p, h, moves);
					unhoused.remove(i--);
					break;
				}
			}
		}
		// Pass two: any other house with room that nobody it was built for still needs.
		Set<UUID> stillWaiting = new HashSet<>();
		for (Households.Household h : unhoused) {
			stillWaiting.addAll(h.members());
		}
		for (int i = 0; i < unhoused.size(); i++) {
			Households.Household h = unhoused.get(i);
			for (VillageData.Plot p : houses) {
				boolean reservedForOthers = false;
				for (UUID id : p.intended) {
					if (stillWaiting.contains(id) && !h.members().contains(id)) {
						reservedForOthers = true;
						break;
					}
				}
				if (!reservedForOthers && onlyTheirs(p, h) && capacity(p) >= h.size()) {
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

	/** The one house the whole household lives in with room for them all, or null. */
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
		if (found == null || free(found, h) < h.size()) {
			return null;
		}
		return found;
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
	 * between houses or without a bed each, then married couples with no spare bed for a baby. A household that already
	 * has a house being built for it needs nothing more for now.
	 */
	static List<Need> needs(VillageData v, Households.Roster roster) {
		List<Need> list = new ArrayList<>();
		for (Households.Household h : roster.households()) {
			if (planned(v, h)) {
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
			} else if (capacity(home) < h.wantBeds()) {
				list.add(new Need(h, h.wantBeds(), Urgency.BABY_ROOM));
			}
		}
		list.sort(Comparator.comparing(Need::urgency).thenComparing(n -> -n.household().size()));
		return list;
	}

	/** True if a house is being built for (anyone of) this household, or one stands empty that was built for them. */
	static boolean planned(VillageData v, Households.Household h) {
		for (VillageData.Plot p : v.plots()) {
			if (p.isHouse() && !p.standing() && !Collections.disjoint(p.intended, h.members())) {
				return true;
			}
		}
		return false;
	}
}
