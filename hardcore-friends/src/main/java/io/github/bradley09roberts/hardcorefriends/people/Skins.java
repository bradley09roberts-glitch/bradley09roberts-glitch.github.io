package io.github.bradley09roberts.hardcorefriends.people;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Persona;
import io.github.bradley09roberts.hardcorefriends.companion.Role;

/**
 * Every skin a person can wear, read once from {@code assets/hardcorefriends/skins.json} in the mod's own jar, so the
 * server (choosing skins for newcomers and babies) and the client (drawing them) always agree. Each skin has a number
 * that is saved with the person and synced to players, its texture, its arm model (wide or slim, drawn with the
 * matching player model) and who may wear it: grown-ups, children, anyone, or only the named friend it belongs to.
 *
 * <p>The numbers never change: 0-8 are the nine friends' own skins, 100-108 the game's default skins with wide arms
 * and 109-117 the same nine with slim arms (Steve, Alex, Ari, Efe, Kai, Makena, Noor, Sunny, Zuri). New skins take
 * numbers from 1000 up; adding one is a PNG and a line of JSON (see {@code docs/v3/people.md}). A missing or broken
 * file falls back to the built-in list, and a broken entry is skipped with a warning, so a typo never stops the game.
 *
 * <p>A skin may carry tags: a trade ({@code "baker"}, {@code "guard"}...) or a place ({@code "desert"},
 * {@code "snowy"}...). When a skin is picked for someone, one that suits their trade or the land they come from is
 * more likely, one nobody nearby wears is preferred, and once enough skins have been added the game's own default
 * skins are only used when nothing else suits.
 */
public final class Skins {
	/** Who may be given a skin when one is picked at random. */
	public enum Wear {
		/** Grown-ups only. */
		ADULT,
		/** Children only. */
		CHILD,
		/** Anyone. */
		ANY,
		/** Never picked at random: the named friend's own. */
		NAMED;

		boolean suits(boolean child) {
			return this == ANY || (child ? this == CHILD : this == ADULT);
		}
	}

	/** One skin: its number, its texture, whether it uses the slim arms, who may wear it, and its tags (lower case). */
	public record Skin(int id, Identifier texture, boolean slim, Wear wear, Set<String> tags) {
		public Skin {
			tags = Set.copyOf(tags);
		}

		Skin(int id, Identifier texture, boolean slim, Wear wear) {
			this(id, texture, slim, wear, Set.of());
		}

		boolean hasAny(Collection<String> wanted) {
			for (String tag : wanted) {
				if (tags.contains(tag)) {
					return true;
				}
			}
			return false;
		}
	}

	/** First number for skins added after the built-in ones. */
	public static final int FIRST_EXTRA_ID = 1000;
	/** With at least this many added skins suiting someone, the game's default skins are left for when nothing else suits. */
	private static final int ENOUGH_ADDED = 6;
	/** Chance of a skin from the land someone comes from, when there is one nobody nearby wears. */
	private static final float PLACE_CHANCE = 0.4F;
	/** Chance of a skin of someone's own trade, when there is one nobody nearby wears. */
	private static final float TRADE_CHANCE = 0.6F;

	/** The skin tags that go with each kind of work (a newcomer's trade, or a grown-up child's). */
	private static final Map<Role, List<String>> TRADE_TAGS = new EnumMap<>(Role.class);

	static {
		TRADE_TAGS.put(Role.FARMER, List.of("farmer", "farmhand", "shepherd", "beekeeper"));
		TRADE_TAGS.put(Role.BUILDER, List.of("carpenter", "mason"));
		TRADE_TAGS.put(Role.MINER, List.of("miner", "blacksmith", "mason"));
		TRADE_TAGS.put(Role.EXPLORER, List.of("traveller", "wanderer", "hunter", "fisher"));
		TRADE_TAGS.put(Role.INVENTOR, List.of("scholar", "tailor", "weaver", "blacksmith"));
		TRADE_TAGS.put(Role.WARRIOR, List.of("guard", "hunter"));
		TRADE_TAGS.put(Role.STRATEGIST, List.of("scholar", "teacher", "doctor", "shopkeeper"));
		TRADE_TAGS.put(Role.LANDSCAPER, List.of("farmhand", "mason", "carpenter"));
		TRADE_TAGS.put(Role.FORAGER, List.of("hunter", "fisher", "baker", "butcher", "innkeeper", "beekeeper"));
	}
	/** The slim-armed copies of the game's default skins start here (the wide ones at {@link Persona#DEFAULT_SKIN_BASE}). */
	public static final int SLIM_DEFAULT_BASE = Persona.DEFAULT_SKIN_BASE + 9;
	private static final String FILE = "assets/" + HardcoreFriends.MOD_ID + "/skins.json";

	private static volatile @Nullable Map<Integer, Skin> loaded;

	private Skins() {
	}

	/** Every skin by number, in file order. Loaded on first use. */
	public static Map<Integer, Skin> all() {
		Map<Integer, Skin> skins = loaded;
		if (skins == null) {
			synchronized (Skins.class) {
				skins = loaded;
				if (skins == null) {
					skins = Collections.unmodifiableMap(load());
					loaded = skins;
				}
			}
		}
		return skins;
	}

	/** The skin with this number, if there is one. */
	public static Optional<Skin> get(int id) {
		return Optional.ofNullable(all().get(id));
	}

	/**
	 * The skin to draw for a number: that skin, or for a number nobody knows (a skin taken out of the file) a default
	 * skin chosen by the number, so a person never turns invisible or purple.
	 */
	public static Skin forDrawing(int id) {
		Skin skin = all().get(id);
		if (skin != null) {
			return skin;
		}
		// The built-in default skins are always in the list (see load), whatever the file says.
		Skin fallback = all().get(Persona.DEFAULT_SKIN_BASE + Math.floorMod(id, Persona.DEFAULT_SKINS.size()));
		return fallback != null ? fallback : builtIn().get(FriendId.values().length);
	}

	/**
	 * A random skin for a new person: grown-up and "any" skins for an adult, child and "any" skins for a child. Never a
	 * named friend's own skin.
	 */
	public static int randomFor(RandomSource random, boolean child) {
		return randomFor(random, child, null, List.of(), Set.of());
	}

	/**
	 * A skin for a new person that suits them where it can: grown-up and "any" skins for an adult, child and "any" skins
	 * for a child, never a named friend's own. Skins nobody in {@code worn} wears come first. Then, by chance, one tagged
	 * with {@code place} (the land they come from, see {@link #placeOf}), else one tagged with any of {@code trades} (see
	 * {@link #tradeTags}), else any that suits. Once enough skins have been added, the game's default skins are only
	 * picked when no added skin suits.
	 */
	public static int randomFor(RandomSource random, boolean child, @Nullable String place, Collection<String> trades,
			Collection<Integer> worn) {
		List<Skin> suitable = new ArrayList<>();
		int added = 0;
		for (Skin skin : all().values()) {
			if (skin.wear().suits(child)) {
				suitable.add(skin);
				if (skin.id() >= FIRST_EXTRA_ID) {
					added++;
				}
			}
		}
		if (suitable.isEmpty()) {
			return Persona.DEFAULT_SKIN_BASE + random.nextInt(Persona.DEFAULT_SKINS.size());
		}
		if (added >= ENOUGH_ADDED) {
			suitable.removeIf(skin -> skin.id() < FIRST_EXTRA_ID);
		}
		List<Skin> fresh = new ArrayList<>(suitable);
		fresh.removeIf(skin -> worn.contains(skin.id()));
		List<Skin> pool = fresh.isEmpty() ? suitable : fresh;
		if (place != null) {
			List<Skin> local = pool.stream().filter(skin -> skin.tags().contains(place)).toList();
			if (!local.isEmpty() && random.nextFloat() < PLACE_CHANCE) {
				return local.get(random.nextInt(local.size())).id();
			}
		}
		if (!trades.isEmpty()) {
			List<Skin> trade = pool.stream().filter(skin -> skin.hasAny(trades)).toList();
			if (!trade.isEmpty() && random.nextFloat() < TRADE_CHANCE) {
				return trade.get(random.nextInt(trade.size())).id();
			}
		}
		return pool.get(random.nextInt(pool.size())).id();
	}

	/** The skins worn by everyone in the loaded world now (friends, newcomers, strangers, children). Server thread only. */
	public static Set<Integer> wornNow() {
		Set<Integer> worn = new java.util.HashSet<>();
		for (CompanionEntity c : Companions.everyone()) {
			worn.add(c.getSkinId());
		}
		return worn;
	}

	/** The skin tags that go with a kind of work, e.g. carpenter and mason for a builder. */
	public static List<String> tradeTags(Role role) {
		return TRADE_TAGS.getOrDefault(role, List.of());
	}

	/**
	 * The place tag for skins from this biome ({@code desert}, {@code snowy}, {@code jungle}, {@code swamp},
	 * {@code savanna} or {@code dark_forest}), read from the game's biome tags so modded biomes that use them count
	 * too, or null for anywhere else.
	 */
	public static @Nullable String placeOf(Holder<Biome> biome) {
		if (biome.is(BiomeTags.HAS_VILLAGE_DESERT) || biome.is(BiomeTags.HAS_DESERT_PYRAMID) || biome.is(BiomeTags.IS_BADLANDS)) {
			return "desert";
		}
		if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY) || biome.is(BiomeTags.HAS_IGLOO) || biome.is(BiomeTags.SPAWNS_SNOW_FOXES)) {
			return "snowy";
		}
		if (biome.is(BiomeTags.IS_JUNGLE)) {
			return "jungle";
		}
		if (biome.is(BiomeTags.HAS_SWAMP_HUT) || biome.is(BiomeTags.HAS_RUINED_PORTAL_SWAMP)) {
			return "swamp";
		}
		if (biome.is(BiomeTags.IS_SAVANNA)) {
			return "savanna";
		}
		if (biome.is(BiomeTags.HAS_WOODLAND_MANSION)) {
			return "dark_forest";
		}
		return null;
	}

	/** True if a person of this age may wear this skin (unknown numbers count as fine: they draw as a default). */
	public static boolean suits(int id, boolean child) {
		Skin skin = all().get(id);
		return skin == null || skin.wear() == Wear.NAMED || skin.wear().suits(child);
	}

	// ------------------------------------------------------------- loading

	private static Map<Integer, Skin> load() {
		Map<Integer, Skin> skins = new LinkedHashMap<>();
		Path path = null;
		try {
			path = FabricLoader.getInstance().getModContainer(HardcoreFriends.MOD_ID)
				.flatMap(mod -> mod.findPath(FILE)).orElse(null);
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.warn("Could not look for {}: {}", FILE, e.getMessage());
		}
		if (path != null) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				read(JsonParser.parseReader(reader), skins);
			} catch (IOException | RuntimeException e) {
				// Any mistake in the file (bad JSON, a list that is not a list...) falls back to the built-in skins, once:
				// the result is kept, so a broken file never throws again.
				HardcoreFriends.LOGGER.warn("Could not read {}, using the built-in skins: {}", FILE, e.getMessage());
				skins.clear();
			}
		}
		// The built-in skins are always there, so old saves and the nine friends keep their looks whatever the file says.
		for (Skin skin : builtIn()) {
			skins.putIfAbsent(skin.id(), skin);
		}
		return skins;
	}

	private static void read(JsonElement root, Map<Integer, Skin> skins) {
		JsonElement skinsList = root.isJsonObject() ? root.getAsJsonObject().get("skins") : null;
		if (skinsList == null || !skinsList.isJsonArray()) {
			throw new JsonParseException("no \"skins\" list");
		}
		JsonArray list = skinsList.getAsJsonArray();
		for (JsonElement element : list) {
			Skin skin = entry(element);
			if (skin == null) {
				continue;
			}
			if (skins.containsKey(skin.id())) {
				HardcoreFriends.LOGGER.warn("Skin number {} is listed twice in {}; keeping the first", skin.id(), FILE);
				continue;
			}
			skins.put(skin.id(), skin);
		}
	}

	private static @Nullable Skin entry(JsonElement element) {
		try {
			JsonObject o = element.getAsJsonObject();
			int id = o.get("id").getAsInt();
			Identifier texture = Identifier.tryParse(o.get("texture").getAsString());
			String model = o.has("model") ? o.get("model").getAsString().toLowerCase(Locale.ROOT) : "wide";
			String wear = o.has("for") ? o.get("for").getAsString().toUpperCase(Locale.ROOT) : "ANY";
			List<String> tags = new ArrayList<>();
			if (o.has("tags")) {
				for (JsonElement tag : o.get("tags").getAsJsonArray()) {
					tags.add(tag.getAsString().toLowerCase(Locale.ROOT));
				}
			}
			if (id < 0 || texture == null || !texture.getPath().endsWith(".png") || !(model.equals("wide") || model.equals("slim"))) {
				HardcoreFriends.LOGGER.warn("Skipping a skin in {}: {}", FILE, element);
				return null;
			}
			return new Skin(id, texture, model.equals("slim"), Wear.valueOf(wear), Set.copyOf(tags));
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.warn("Skipping a skin in {} ({}): {}", FILE, e.getMessage(), element);
			return null;
		}
	}

	/** The nine friends' own skins, then the game's nine default skins wide and slim. */
	private static List<Skin> builtIn() {
		List<Skin> skins = new ArrayList<>();
		for (FriendId id : FriendId.values()) {
			skins.add(new Skin(id.ordinal(), Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID,
				"textures/entity/companion/" + id.key() + ".png"), false, Wear.NAMED));
		}
		for (int i = 0; i < Persona.DEFAULT_SKINS.size(); i++) {
			skins.add(new Skin(Persona.DEFAULT_SKIN_BASE + i,
				Identifier.withDefaultNamespace("textures/entity/player/wide/" + Persona.DEFAULT_SKINS.get(i) + ".png"), false, Wear.ANY));
		}
		for (int i = 0; i < Persona.DEFAULT_SKINS.size(); i++) {
			skins.add(new Skin(SLIM_DEFAULT_BASE + i,
				Identifier.withDefaultNamespace("textures/entity/player/slim/" + Persona.DEFAULT_SKINS.get(i) + ".png"), true, Wear.ANY));
		}
		return skins;
	}
}
