package io.github.bradley09roberts.hardcorefriends.people;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.util.RandomSource;

/**
 * Family names for everyone, and first names for babies born in the camp. Babies' names come from their own list,
 * which shares no name with the newcomers' list ({@code settler.Personas}) or the nine friends, so a stranger met
 * later can never turn up with the same name as a child of the camp and {@code /friends where pip} always means one
 * person. Every name is a single plain word, because commands take one word.
 */
final class Names {
	/** About a hundred family names. */
	static final List<String> FAMILY = List.of(
		"Ashby", "Barlow", "Bramble", "Brook", "Carter", "Cobb", "Dale", "Fairweather", "Fenwick", "Fletcher",
		"Fox", "Garland", "Goodwin", "Greenwood", "Hale", "Hart", "Hawthorne", "Heath", "Holloway", "Honeywell",
		"Kettle", "Linden", "Marsh", "Meadows", "Miller", "Moss", "Nightingale", "Oakley", "Orchard", "Pennywhistle",
		"Pike", "Platt", "Quill", "Reed", "Ridley", "Rook", "Sallow", "Shepherd", "Sparrow", "Stone",
		"Swift", "Tanner", "Thatcher", "Thorne", "Underwood", "Wainwright", "Warren", "Weaver", "Wells", "Whitlock",
		"Wilde", "Willoughby", "Yardley", "Abbott", "Bell", "Birch", "Bishop", "Blackwood", "Bloom", "Bramley",
		"Bright", "Burrows", "Chandler", "Clay", "Cooper", "Cotton", "Crane", "Croft", "Dawson", "Dove",
		"Everett", "Fairfax", "Field", "Finch", "Forrester", "Gardener", "Gray", "Harper", "Hayward", "Holt",
		"Hopkins", "Kemp", "Lambert", "Lowe", "Mason", "Merriweather", "Nash", "Penrose", "Pickering", "Rowe",
		"Sutton", "Tate", "Timms", "Tolley", "Vale", "Webb", "Whitaker", "Woodward", "Woolley", "Ashdown");

	/** About a hundred first names for babies, none on the newcomers' list or one of the nine friends. */
	static final List<String> BABY = List.of(
		"Archie", "Beau", "Bea", "Bluebell", "Clover", "Cosmo", "Dottie", "Elodie", "Ember", "Evie",
		"Fenn", "Flossie", "Frankie", "Georgie", "Hamish", "Hettie", "Indigo", "Jem", "Jory", "Kip",
		"Lark", "Lula", "Merry", "Minnie", "Monty", "Ottilie", "Ozzy", "Penny", "Peregrine", "Phoebe",
		"Piers", "Posy", "Rafferty", "Robin", "Romilly", "Rosie", "Sami", "Skye", "Sonny", "Sorrel",
		"Tansy", "Tamsin", "Tibby", "Tobin", "Wilbur", "Winona", "Wynn", "Zinnia", "Arlo", "Bramwell",
		"Cassia", "Dilys", "Eben", "Elowen", "Ferdie", "Gwen", "Hal", "Ianto", "Jojo", "Lettie",
		"Lorcan", "Mabon", "Nessa", "Noa", "Olly", "Peggy", "Pim", "Ralph", "Rhys", "Ruby",
		"Saffie", "Sid", "Sukie", "Tam", "Tegan", "Una", "Vita", "Wilf", "Ziggy", "Aled",
		"Bryn", "Cai", "Dewi", "Elsa", "Faye", "Gilly", "Ivo", "Jago", "Kaia", "Loveday",
		"Merlin", "Ottie", "Pippa", "Quentin", "Ren", "Sable", "Tavi", "Ula", "Vinny", "Wesley");

	private Names() {
	}

	/** A family name nobody living uses yet ({@code taken}, lower case), or any one if all are taken. */
	static String family(RandomSource random, Set<String> taken) {
		return pick(FAMILY, random, taken);
	}

	/** A baby's first name nobody living uses ({@code taken}, lower case), or any one if all are taken. */
	static String baby(RandomSource random, Set<String> taken) {
		return pick(BABY, random, taken);
	}

	private static String pick(List<String> names, RandomSource random, Set<String> taken) {
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
