package io.github.bradley09roberts.hardcorefriends.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * Server-side settings, stored in {@code config/hardcorefriends.json}. Missing or invalid values fall back to
 * defaults, and the file is rewritten so every option is visible to the player.
 */
public final class FriendsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static FriendsConfig instance = new FriendsConfig();

	/** quiet, normal or chatty. Danger warnings and deaths are always shown. */
	public String chatter = "normal";
	/** Base camp radius in blocks. Grows by 4 per settlement stage, up to {@link #maxCampRadius}. */
	public int campRadius = 24;
	public int maxCampRadius = 40;
	/** Extra ring beyond the camp radius where gathering, quarrying and mining are allowed. */
	public int resourceRadius = 48;
	/** Master switch for every block change made by companions. */
	public boolean allowWorldEditing = true;
	public boolean allowTreeFelling = true;
	public boolean allowQuarrying = true;
	public boolean allowMining = true;
	/** In-game days before a fallen friend can be recruited again. -1 means death is permanent. */
	public int deadFriendsReturnAfterDays = 3;
	/** In FOLLOW mode, friends further than this many blocks catch up to their leader. 0 disables. */
	public int followTeleportDistance = 48;
	/** Lets zombies, skeletons, spiders, illagers and witches hunt companions as they hunt villagers. */
	public boolean monstersTargetCompanions = true;

	public static FriendsConfig get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("hardcorefriends.json");
	}

	public static void load() {
		Path path = path();
		FriendsConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, FriendsConfig.class);
			} catch (IOException | JsonParseException e) {
				HardcoreFriends.LOGGER.warn("Could not read {}, using defaults: {}", path, e.getMessage());
			}
		}
		instance = loaded == null ? new FriendsConfig() : loaded;
		instance.sanitise();
		save();
	}

	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			HardcoreFriends.LOGGER.warn("Could not write {}: {}", path, e.getMessage());
		}
	}

	private void sanitise() {
		if (!"quiet".equals(chatter) && !"chatty".equals(chatter)) {
			chatter = "normal";
		}
		campRadius = Math.clamp(campRadius, 8, 64);
		maxCampRadius = Math.clamp(maxCampRadius, campRadius, 96);
		resourceRadius = Math.clamp(resourceRadius, 0, 96);
		deadFriendsReturnAfterDays = Math.max(-1, deadFriendsReturnAfterDays);
		followTeleportDistance = Math.max(0, followTeleportDistance);
	}
}
