package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.util.RandomSource;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Persona;
import io.github.bradley09roberts.hardcorefriends.companion.Role;

/**
 * Who the newcomers are: their names, looks, trades and the short story each tells when they first meet a player.
 * Everything is picked at random so every world has its own people, but a living newcomer's name is never shared
 * with another (the caller passes the names already in use), so {@code /friends follow mabel} always means one person.
 *
 * <p>Names are single plain words (commands take one word) and never one of the nine friends' names. The trade is
 * weighted: plenty of farmers, foragers, builders and miners, fewer warriors and strategists, as in any village.
 */
public final class Personas {
	/** Where a stranger was met; picks the kind of story they tell. */
	public enum Origin {
		/** Living in a village. */
		VILLAGE("village"),
		/** At a survivor camp out in the wilds. */
		CAMP("camp"),
		/** A traveller who walked up to the team's camp. */
		ROAD("road");

		private final String key;

		Origin(String key) {
			this.key = key;
		}

		public String key() {
			return key;
		}

		public static Origin byKey(String key) {
			for (Origin o : values()) {
				if (o.key.equals(key)) {
					return o;
				}
			}
			return ROAD;
		}
	}

	/** About 120 first names, all different, none of them one of the nine friends' own. */
	static final List<String> NAMES = List.of(
		"Mabel", "Arthur", "Ivy", "Hugo", "Elsie", "Felix", "Maisie", "Otis", "Poppy", "Rupert",
		"Hazel", "Jasper", "Edith", "Alfie", "Winnie", "Barnaby", "Clara", "Toby", "Florence", "Rory",
		"Agnes", "Percy", "Daisy", "Stanley", "Iris", "Wilfred", "Beatrix", "Ned", "Lottie", "Cyril",
		"Martha", "Ezra", "Nell", "Gideon", "Rosa", "Milo", "Greta", "Silas", "Pearl", "Rufus",
		"Thea", "Bram", "Ada", "Corin", "Lena", "Dorian", "Mae", "Ellis", "Freya", "Ansel",
		"Juniper", "Basil", "Opal", "Cedric", "Willa", "Emrys", "Tess", "Finley", "Primrose", "Hector",
		"Odette", "Lionel", "Briar", "Ambrose", "Cora", "Tobias", "Marigold", "Edmund", "Isla", "Reuben",
		"Juno", "Ronan", "Esme", "Gus", "Sylvie", "Fergus", "Bonnie", "Lachlan", "Hattie", "Wren",
		"Bertie", "Nina", "Ossian", "Kit", "Leona", "Amos", "Fleur", "Duncan", "Mina", "Casper",
		"Lyra", "Teddy", "Ines", "Rowland", "Zara", "Pip", "Delia", "Murray", "Effie", "Nico",
		"Petra", "Ivor", "Tilly", "Dexter", "Kezia", "Morgan", "Aria", "Bennet", "Liesl", "Quinn",
		"Sunniva", "Theo", "Yara", "Elian", "Rosalind", "Dara", "Albie", "Orla", "Seren", "Huxley");

	/** Pleasant name colours, none of them exactly one of the nine friends'. */
	private static final int[] COLOURS = {
		0xE8A33D, 0x5AA9E6, 0xD86C9B, 0x7BC47F, 0xC9A0DC, 0xF2C14E, 0x4FB3A9, 0xE07A5F,
		0x9C89B8, 0x81B29A, 0xF4A261, 0x6D9DC5, 0xE5989B, 0xA3B18A, 0xB5838D, 0x8AB6D6};

	/** How common each trade is among newcomers: plenty of farmers and foragers, few warriors and strategists. */
	private static final int[] WEIGHTS = new int[FriendId.values().length];

	static {
		WEIGHTS[FriendId.FERN.ordinal()] = 14;
		WEIGHTS[FriendId.OAK.ordinal()] = 12;
		WEIGHTS[FriendId.FLINT.ordinal()] = 12;
		WEIGHTS[FriendId.SCOUT.ordinal()] = 10;
		WEIGHTS[FriendId.SPARK.ordinal()] = 8;
		WEIGHTS[FriendId.AEGIS.ordinal()] = 5;
		WEIGHTS[FriendId.SAGE.ordinal()] = 4;
		WEIGHTS[FriendId.TERRA.ordinal()] = 11;
		WEIGHTS[FriendId.ROWAN.ordinal()] = 14;
	}

	private static final List<String> VILLAGE_STORIES = List.of(
		"I came here looking for work, but the villagers mostly just nod at me.",
		"I lost my village to pillagers last spring. This one took me in.",
		"I've been helping out here for a while, but I'd like to build something of my own.",
		"The villagers are kind, but they don't say much. I miss proper conversation.",
		"I arrived during a storm and never quite got round to leaving.",
		"I trade a bit with the villagers. Mostly I trade wheat for funny looks.",
		"My family farmed near here once. The fields are long gone now.",
		"I keep this village's paths clear, but nobody ever says thank you.",
		"I was a travelling tinker until my cart lost a wheel. That was two years ago.",
		"The zombies came close last week. I'd feel safer with a proper team.");

	private static final List<String> CAMP_STORIES = List.of(
		"Our little camp has been through a hard winter. We're tired, but we're still here.",
		"We set up here after a creeper took our old shelter. It wasn't a good day.",
		"We're the last of a bigger group. The others went looking for a village and never came back.",
		"We hunt, we gather, we keep the fire going. It's not much of a life, but it's ours.",
		"We've been camping out here since the spring floods washed our homes away.",
		"Every night we take turns keeping watch. It would be nice to sleep through for once.",
		"We found this spot by following the river. It's quiet, mostly.",
		"Our tents are patched more than they're whole. We could do with a real roof.",
		"We left the mountains when the caves got too dangerous.",
		"I've kept this fire burning for forty nights. I'd love to hand the job over.");

	private static final List<String> ROAD_STORIES = List.of(
		"I've been walking north for weeks. Your fire was the first friendly light I've seen.",
		"I heard there was a camp out this way that looks after its own.",
		"I lost my way in a forest and somehow ended up here.",
		"My old camp broke up last month. Everyone went their own way.",
		"I've slept under hedges for a fortnight. A bed would be lovely.",
		"I've walked from the coast with nothing but a stick and a bit of bread.",
		"I saw your smoke from the hills and thought I'd say hello.",
		"I'm looking for somewhere to belong. Is there room for one more?",
		"Travelling alone at night is no fun. I'd rather travel with friends.",
		"Somebody told me you were building something good here. I wanted to see it.");

	private Personas() {
	}

	/**
	 * A new person: a name nobody living is using (from {@code taken}, compared without case), a trade, a colour and a
	 * default skin. With every name in use, a used one is allowed rather than failing.
	 */
	public static Persona create(RandomSource random, Set<String> taken) {
		return new Persona(freeName(random, taken), COLOURS[random.nextInt(COLOURS.length)],
			Persona.DEFAULT_SKIN_BASE + random.nextInt(Persona.DEFAULT_SKINS.size()), randomArchetype(random));
	}

	/** A random name not in {@code taken} (lower-case names), or any name if all are taken. */
	public static String freeName(RandomSource random, Set<String> taken) {
		List<String> free = new ArrayList<>();
		for (String name : NAMES) {
			if (!taken.contains(name.toLowerCase(Locale.ROOT))) {
				free.add(name);
			}
		}
		List<String> pool = free.isEmpty() ? NAMES : free;
		return pool.get(random.nextInt(pool.size()));
	}

	/** A trade by the weights above. */
	public static FriendId randomArchetype(RandomSource random) {
		int total = 0;
		for (int w : WEIGHTS) {
			total += w;
		}
		int pick = random.nextInt(total);
		for (FriendId id : FriendId.values()) {
			pick -= WEIGHTS[id.ordinal()];
			if (pick < 0) {
				return id;
			}
		}
		return FriendId.ROWAN;
	}

	/** A short story about themselves, suited to where they were met. */
	public static String story(RandomSource random, Origin origin) {
		List<String> stories = switch (origin) {
			case VILLAGE -> VILLAGE_STORIES;
			case CAMP -> CAMP_STORIES;
			case ROAD -> ROAD_STORIES;
		};
		return stories.get(random.nextInt(stories.size()));
	}

	/** Their trade with an article, for introductions: "a farmer", "an explorer". */
	public static String tradeWithArticle(Role role) {
		String title = role.title().toLowerCase(Locale.ROOT);
		return ("aeiou".indexOf(title.charAt(0)) >= 0 ? "an " : "a ") + title;
	}
}
