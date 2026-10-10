package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.util.RandomSource;

/**
 * The names children (and grown-ups) give their pets. Cats and dogs each have their own list of cosy, family-friendly
 * names; none is a person's name from the babies' or newcomers' lists, so "Biscuit" in a line is always a pet. A name
 * no living pet has is chosen first.
 */
final class PetNames {
	static final List<String> CATS = List.of(
		"Biscuit", "Pepper", "Muffin", "Smudge", "Mittens", "Whiskers", "Ginger", "Marmalade", "Pudding", "Toffee",
		"Crumpet", "Nutmeg", "Socks", "Tabitha", "Sooty", "Fudge", "Treacle", "Button", "Pumpkin", "Cinnamon",
		"Snowdrop", "Mouse", "Shadow", "Tiddles", "Pickle", "Custard", "Flapjack", "Bramblekin", "Teacake", "Dumpling");
	static final List<String> DOGS = List.of(
		"Rex", "Scamp", "Rascal", "Buster", "Conker", "Patch", "Barney", "Duke", "Captain", "Waffles",
		"Chester", "Doodle", "Bracken", "Digby", "Bisto", "Pebble", "Tango", "Nugget", "Bolt", "Scruffy",
		"Bruno", "Muddy", "Bandit", "Gizmo", "Sprout", "Wellie", "Biscotti", "Puddle", "Badger", "Copper");

	private PetNames() {
	}

	/** A name for a new pet of this kind that no living pet has ({@code taken}, lower case), or any if all are taken. */
	static String pick(PetKind kind, RandomSource random, Set<String> taken) {
		List<String> names = kind == PetKind.CAT ? CATS : DOGS;
		List<String> free = new ArrayList<>();
		for (String name : names) {
			if (!taken.contains(name.toLowerCase(Locale.ROOT))) {
				free.add(name);
			}
		}
		List<String> pool = free.isEmpty() ? names : free;
		return pool.get(random.nextInt(pool.size()));
	}
}
