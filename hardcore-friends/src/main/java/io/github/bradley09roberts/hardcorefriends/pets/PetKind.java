package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.Locale;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The pets the friends keep: a stray cat, tamed with raw fish as a player tames one, or a wild wolf, tamed with bones
 * (a "dog" once it is someone's). Parrots are left out: they live in jungles far from most camps and fly off on their
 * own. Each kind knows what tames it and what it eats as a treat from the camp's stock.
 */
public enum PetKind {
	CAT("cat", "cats", s -> s.is(ItemTags.CAT_FOOD), s -> s.is(ItemTags.CAT_FOOD)),
	/** A tamed wolf: "dog" in the friends' words, as players say. */
	WOLF("dog", "dogs", s -> s.is(Items.BONE), PetKind::dogTreat);

	private final String word;
	private final String plural;
	private final Predicate<ItemStack> tames;
	private final Predicate<ItemStack> treat;

	PetKind(String word, String plural, Predicate<ItemStack> tames, Predicate<ItemStack> treat) {
		this.word = word;
		this.plural = plural;
		this.tames = tames;
		this.treat = treat;
	}

	/** "cat" or "dog", as the friends say it. */
	public String word() {
		return word;
	}

	public String plural() {
		return plural;
	}

	/** What a friend feeds this kind to tame it: raw cod or salmon for a cat, a bone for a wolf. */
	public Predicate<ItemStack> tamingFood() {
		return tames;
	}

	/** What the camp feeds this kind now and then: raw fish for a cat; rotten flesh or raw meat for a dog. */
	public Predicate<ItemStack> treat() {
		return treat;
	}

	/** Saved name. */
	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static PetKind byKey(String key) {
		for (PetKind kind : values()) {
			if (kind.key().equals(key)) {
				return kind;
			}
		}
		return CAT;
	}

	/** The kind of this animal, or null if the friends do not keep it as a pet. */
	public static @Nullable PetKind of(TamableAnimal animal) {
		if (animal instanceof Cat) {
			return CAT;
		}
		if (animal instanceof Wolf) {
			return WOLF;
		}
		return null;
	}

	/**
	 * A dog's treat: rotten flesh first (nobody else wants it), then raw meat; never cooked meat, which is the friends'
	 * own food. Wolves eat any meat, as in the game.
	 */
	private static boolean dogTreat(ItemStack s) {
		return s.is(Items.ROTTEN_FLESH) || s.is(Items.BEEF) || s.is(Items.PORKCHOP) || s.is(Items.MUTTON)
			|| s.is(Items.CHICKEN) || s.is(Items.RABBIT);
	}
}
