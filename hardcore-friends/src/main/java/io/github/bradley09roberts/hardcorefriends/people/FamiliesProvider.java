package io.github.bradley09roberts.hardcorefriends.people;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import io.github.bradley09roberts.hardcorefriends.civic.Families;

/**
 * The people package's answers to {@code civic.Families}, read from {@link PeopleData}: the partner is the living
 * husband or wife (sweethearts who are only going out live apart), parents and children are as recorded (children
 * only while alive), a household is a married couple and their children still young, and the family name is the one
 * the person carries.
 */
final class FamiliesProvider implements Families.Provider {
	@Override
	public Optional<UUID> partnerOf(MinecraftServer server, UUID person) {
		return PeopleData.get(server).spouse(person);
	}

	@Override
	public List<UUID> parentsOf(MinecraftServer server, UUID person) {
		return PeopleData.get(server).person(person).map(p -> List.copyOf(p.parents)).orElse(List.of());
	}

	@Override
	public List<UUID> childrenOf(MinecraftServer server, UUID person) {
		List<UUID> children = new ArrayList<>();
		for (PeopleData.Person child : PeopleData.get(server).childrenOf(person)) {
			if (child.alive()) {
				children.add(child.id);
			}
		}
		return children;
	}

	@Override
	public Set<UUID> householdOf(MinecraftServer server, UUID person) {
		return Set.copyOf(PeopleData.get(server).household(person));
	}

	@Override
	public Optional<String> familyName(MinecraftServer server, UUID person) {
		return PeopleData.get(server).person(person).map(p -> p.family).filter(name -> !name.isEmpty());
	}

	@Override
	public int babiesOnTheWay(MinecraftServer server) {
		int n = 0;
		for (PeopleData.Bond bond : PeopleData.get(server).bonds()) {
			if (bond.babyDue >= 0) {
				n++; // as Births.population counts them
			}
		}
		return n;
	}
}
