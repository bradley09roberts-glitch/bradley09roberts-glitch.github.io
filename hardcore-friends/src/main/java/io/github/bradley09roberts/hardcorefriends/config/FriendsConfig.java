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

	// ---- Independence: living on while you are away, trips, making room to build ----
	/** Keeps the camp's chunks running while a player of the camp is online anywhere, so friends live on while you are away. */
	public boolean keepCampLoaded = true;
	/** How many friends at once may keep the land around them loaded while away on a trip (exploring, trading, deliveries). */
	public int maxRoamingFriends = 3;
	/** Lets friends go on trips past the gathering ring on their own (far exploring, foraging trips, trading at villages). */
	public boolean allowTrips = true;
	/** Lets friends level uneven ground (dig down bumps, fill dips) to make room for a building. */
	public boolean allowTerraforming = true;
	/** The most a building site may be dug down or built up to level it, in blocks. */
	public int maxGradeDepth = 3;

	// ---- Newcomers: people met in villages, at survivor camps and on the road ----
	public boolean allowSettlers = true;
	/** Chance a village has newcomers living in it, met the first time a player comes by. */
	public double villageSettlerChance = 0.75;
	/** Most newcomers on the team at once (the nine named friends are not counted). */
	public int maxSettlers = 12;
	/** Most newcomers one player may recruit. */
	public int maxSettlersPerPlayer = 6;
	/** A wanderer may turn up at the camp now and then, hoping to join. */
	public boolean wanderingVisitors = true;

	// ---- Combat ----
	/** Friends with a bow and arrows shoot at hostiles. */
	public boolean friendsUseBows = true;

	// ---- Beating the game: Sage's plan, the Nether, the stronghold and the End ----
	/** Sage's long-term plan: iron, diamonds, enchanting, the Nether, the stronghold and the End. */
	public boolean progressionGoals = true;
	/** Friends following a player go through portals with them (the Nether, the End and back). */
	public boolean friendsFollowThroughPortals = true;

	// ---- Several players ----
	/** Only the camp's owner and the players they trust may give the friends orders, open backpacks or move the camp. */
	public boolean requireTrust = true;
	/** Opt-in group event: every few nights a bigger wave of monsters tests the camp. */
	public boolean siegeNights = false;
	/** Most friends who may follow one player at once, so nobody takes the whole team away from the camp. */
	public int maxFollowersPerPlayer = 4;
	/** How far from the camp a player's mailbox may be for friends to take deliveries there, in blocks (0 = no deliveries, at most 600). */
	public int maxDeliveryDistance = 400;

	// ---- Better builds (package architecture) ----

	// ---- Finding the way (package navigation) ----
	/**
	 * A friend on the team stuck or lost for a long time (two in-game minutes), trapped underground hurt or starving, or
	 * about to drown, is brought home (or back to their leader) and everyone is told. Off for Hardcore purists.
	 */
	public boolean rescueStuckFriends = true;

	// ---- Living together (package people) ----
	/** Friends may fall for each other, go on dates, get engaged and marry. */
	public boolean romance = true;
	/** Married couples may have children (they need room at home, food in the camp and the population under the cap). */
	public boolean children = true;
	/** In-game days a child takes to grow up (1 to 100). */
	public int childhoodDays = 10;
	/** Most people on the team at once, named friends, newcomers and children together; no baby is born beyond it (0 to 200). */
	public int maxPopulation = 30;
	/** In-game days a couple waits after one baby before another (1 to 60). */
	public int daysBetweenChildren = 5;
	/** How fast friendships and romances grow: 1 is normal, 2 twice as fast, 0.5 half as fast (0.25 to 4). */
	public double relationshipSpeed = 1.0;

	// ---- A proper village (package village) ----

	// ---- Shops and trades (package market) ----

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
		maxRoamingFriends = Math.clamp(maxRoamingFriends, 0, 16);
		maxGradeDepth = Math.clamp(maxGradeDepth, 0, 6);
		villageSettlerChance = Math.clamp(villageSettlerChance, 0.0, 1.0);
		maxSettlers = Math.clamp(maxSettlers, 0, 64);
		maxSettlersPerPlayer = Math.clamp(maxSettlersPerPlayer, 0, maxSettlers);
		maxFollowersPerPlayer = Math.clamp(maxFollowersPerPlayer, 1, 64);
		// A delivery is a day trip there and back: past about 600 blocks there is never daylight enough for one.
		maxDeliveryDistance = Math.clamp(maxDeliveryDistance, 0, 600);
		// Living together (package people)
		childhoodDays = Math.clamp(childhoodDays, 1, 100);
		maxPopulation = Math.clamp(maxPopulation, 0, 200);
		daysBetweenChildren = Math.clamp(daysBetweenChildren, 1, 60);
		relationshipSpeed = Double.isFinite(relationshipSpeed) ? Math.clamp(relationshipSpeed, 0.25, 4.0) : 1.0;
	}
}
