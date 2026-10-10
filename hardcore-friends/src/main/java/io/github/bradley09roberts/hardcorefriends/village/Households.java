package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.civic.Families;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.settler.SettlerData;

/**
 * Who lives in the village and with whom. The grown-ups are the named friends who are alive on the team and the
 * newcomers who joined (people born in the camp count once grown up); the children are those of the team's people
 * (from {@code civic.Families}) who are alive. A household is a grown-up with whoever lives with them by
 * {@code Families.householdOf}: a husband or wife and their young children. Worked out from the saved records, so
 * friends away or unloaded keep their place.
 */
final class Households {
	/** Most beds any house plan has. */
	static final int MOST_BEDS = 6;
	/** A family has at most this many children (the people package's own limit), so no room is kept beyond it. */
	private static final int MOST_CHILDREN = 4;

	/** One household: everyone in it, its grown-ups, and whether it is a married couple. */
	record Household(Set<UUID> members, Set<UUID> adults, boolean married) {
		/** A stable identity for the household: the first of its grown-ups by id. */
		UUID anchor() {
			return adults.stream().min(Comparator.naturalOrder()).orElseGet(() -> members.iterator().next());
		}

		int size() {
			return members.size();
		}

		int children() {
			return members.size() - adults.size();
		}

		/** Beds the household would like: one each, and a spare for a baby while a married couple may have one. */
		int wantBeds() {
			boolean babyRoom = married && FriendsConfig.get().children && children() < MOST_CHILDREN && size() < MOST_BEDS;
			return Math.min(MOST_BEDS, size() + (babyRoom ? 1 : 0));
		}
	}

	/** The village's people at one moment: the grown-ups, the children and the households. */
	record Roster(Set<UUID> adults, Set<UUID> children, List<Household> households) {
		boolean contains(UUID id) {
			return adults.contains(id) || children.contains(id);
		}

		int population() {
			return adults.size() + children.size();
		}
	}

	private Households() {
	}

	/** The grown-ups on the team: named friends alive, and newcomers (and people grown up in the camp) alive. */
	static Set<UUID> adults(MinecraftServer server) {
		Set<UUID> adults = new LinkedHashSet<>();
		CampData camp = Camp.data(server);
		for (FriendId id : FriendId.values()) {
			CampData.Ledger ledger = camp.ledger(id);
			if (ledger.state == CampData.LifeState.ALIVE && ledger.entityId != null) {
				adults.add(ledger.entityId);
			}
		}
		for (SettlerData.Newcomer n : SettlerData.get(server).newcomers()) {
			if (n.state == SettlerData.State.ALIVE) {
				adults.add(n.id);
			}
		}
		// A loaded grown-up on the team that the records have not caught up with yet.
		for (CompanionEntity c : Companions.all()) {
			if (!c.isChild()) {
				adults.add(c.getUUID());
			}
		}
		return adults;
	}

	/** True if this person is a living child of the camp: one of their parents' children (which lists only the living). */
	static boolean isLivingChild(MinecraftServer server, UUID id) {
		Families.Provider families = Families.get();
		for (UUID parent : families.parentsOf(server, id)) {
			if (families.childrenOf(server, parent).contains(id)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Everyone in the village now, with households. {@code residents} are people the village already houses: a child
	 * whose parents have both died is still found through them.
	 */
	static Roster roster(MinecraftServer server, Set<UUID> residents) {
		Families.Provider families = Families.get();
		Set<UUID> adults = adults(server);
		Set<UUID> children = new LinkedHashSet<>();
		for (UUID a : adults) {
			for (UUID child : families.childrenOf(server, a)) {
				if (!adults.contains(child)) {
					children.add(child);
				}
			}
		}
		for (CompanionEntity c : Companions.all()) {
			if (c.isChild()) {
				children.add(c.getUUID());
			}
		}
		for (UUID r : residents) {
			if (!adults.contains(r) && !children.contains(r) && isLivingChild(server, r)) {
				children.add(r);
			}
		}
		List<Set<UUID>> groups = new ArrayList<>();
		for (UUID a : adults) {
			Set<UUID> group = new LinkedHashSet<>();
			group.add(a);
			for (UUID m : families.householdOf(server, a)) {
				if (adults.contains(m) || children.contains(m)) {
					group.add(m);
				}
			}
			// Merge with any group sharing someone (a couple's two views of the same household).
			for (int i = groups.size() - 1; i >= 0; i--) {
				Set<UUID> other = groups.get(i);
				if (!java.util.Collections.disjoint(other, group)) {
					group.addAll(other);
					groups.remove(i);
				}
			}
			groups.add(group);
		}
		List<Household> households = new ArrayList<>();
		for (Set<UUID> group : groups) {
			Set<UUID> grown = new LinkedHashSet<>();
			boolean married = false;
			for (UUID m : group) {
				if (adults.contains(m)) {
					grown.add(m);
					Optional<UUID> partner = families.partnerOf(server, m);
					married |= partner.isPresent() && group.contains(partner.get());
				}
			}
			if (!grown.isEmpty()) {
				households.add(new Household(Set.copyOf(group), Set.copyOf(grown), married));
			}
		}
		households.sort(Comparator.comparing(Household::anchor));
		return new Roster(adults, children, households);
	}

	/** How many people live in the village: grown-ups and children (not babies still on the way). */
	static int population(MinecraftServer server) {
		VillageData v = VillageData.get(server);
		Set<UUID> residents = new HashSet<>();
		for (VillageData.Plot p : v.plots()) {
			residents.addAll(p.residents.keySet());
		}
		return roster(server, residents).population();
	}

	/** The best name for this person: first name and family name if they have one ("Fern Hart"). */
	static String fullName(MinecraftServer server, VillageData v, UUID id) {
		String first = firstName(server, v, id);
		return Families.get().familyName(server, id).map(f -> first + " " + f).orElse(first);
	}

	/** This person's first name: from the loaded friend, the newcomer records, the named friends, or what was last seen. */
	static String firstName(MinecraftServer server, VillageData v, UUID id) {
		for (CompanionEntity c : Companions.everyone()) {
			if (c.getUUID().equals(id)) {
				return c.displayName();
			}
		}
		Optional<SettlerData.Newcomer> n = SettlerData.get(server).newcomer(id);
		if (n.isPresent()) {
			return n.get().name;
		}
		CampData camp = Camp.data(server);
		for (FriendId fid : FriendId.values()) {
			if (id.equals(camp.ledger(fid).entityId)) {
				return fid.displayName();
			}
		}
		return v.name(id);
	}
}
