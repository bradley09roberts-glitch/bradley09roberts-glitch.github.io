package io.github.bradley09roberts.hardcorefriends.people;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Persona;

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

	/** One skin: its number, its texture, whether it uses the slim arms, and who may wear it. */
	public record Skin(int id, Identifier texture, boolean slim, Wear wear) {
	}

	/** First number for skins added after the built-in ones. */
	public static final int FIRST_EXTRA_ID = 1000;
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
		List<Skin> choice = new ArrayList<>();
		for (Skin skin : all().values()) {
			if (skin.wear().suits(child)) {
				choice.add(skin);
			}
		}
		if (choice.isEmpty()) {
			return Persona.DEFAULT_SKIN_BASE + random.nextInt(Persona.DEFAULT_SKINS.size());
		}
		return choice.get(random.nextInt(choice.size())).id();
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
			if (id < 0 || texture == null || !texture.getPath().endsWith(".png") || !(model.equals("wide") || model.equals("slim"))) {
				HardcoreFriends.LOGGER.warn("Skipping a skin in {}: {}", FILE, element);
				return null;
			}
			return new Skin(id, texture, model.equals("slim"), Wear.valueOf(wear));
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
