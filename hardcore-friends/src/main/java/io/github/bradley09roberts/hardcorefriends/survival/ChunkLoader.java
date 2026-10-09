package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * Keeps the camp alive while its players are away. Minecraft only runs the world near players, so without this the
 * camp freezes the moment you walk off: crops stop, nobody eats, sleeps or builds. While a player is online anywhere
 * (and {@link #campLoadAllowed} lets them count), the camp and its gathering ring stay loaded and running, in the
 * camp's own dimension, through a chunk ticket of our own. Mobs only spawn near players, so an empty camp gets no
 * new monsters; the ones already there keep moving.
 *
 * <p>A friend on a trip far from camp may also hold a <b>roaming</b> ticket: the 3×3 chunks around them keep running
 * and follow them as they walk, at most {@code maxRoamingFriends} at once. It is let go as soon as they are back in the
 * camp area, after {@value #MAX_ROAM_TICKS} ticks away at most (they then wait, frozen, until someone comes by), or
 * when they die, leave, change dimension or stop working on their own. Any feature may ask for one with
 * {@link #startRoaming}.
 *
 * <p>The tickets themselves are never saved: they are worked out afresh every second and dropped when the server
 * stops, so removing the mod leaves nothing behind. Only where roaming friends were is remembered (in camp memory),
 * so that after the world is closed and opened again a friend out on a trip is woken up where they were and can carry
 * on home, instead of staying frozen far away until someone walks over to them.
 */
public final class ChunkLoader {
	/** Keeps the camp area loaded and its entities running (and its dimension awake). Not saved: re-added each second. */
	public static final TicketType CAMP_TICKET = register("camp");
	/** Keeps the 3×3 chunks around a friend on a trip running. Not saved either. */
	public static final TicketType ROAMING_TICKET = register("roaming");

	/**
	 * Which online players keep the camp running. Every player counts by default; other features may tighten it (for
	 * example to the camp's own trusted players) with {@link #restrictCampLoad}.
	 */
	public static volatile Predicate<ServerPlayer> campLoadAllowed = player -> true;

	/**
	 * Friends who keep a roaming grant even when not working on their own: the expedition package's friends making
	 * their way back to a portal in another dimension (following their leader through it, or going home), who would
	 * otherwise freeze as soon as the player has gone.
	 */
	public static volatile Predicate<CompanionEntity> roamsInAnyMode = c -> false;

	/** How often the tickets are brought up to date, in ticks. */
	private static final int INTERVAL = 20;
	/** Entity-ticking radius around a roaming friend, in chunks: their own chunk and the ring around it. */
	private static final int ROAMING_RADIUS = 1;
	/** The camp area never keeps more than this many chunks out from its centre running. */
	private static final int MAX_CAMP_CHUNKS = 10;
	/** The longest a friend may hold a roaming ticket, in ticks (a day and a half). */
	public static final long MAX_ROAM_TICKS = 36000L;
	/** A roaming friend this far inside the camp area's edge counts as home again. */
	private static final int HOME_MARGIN = 16;
	/** Where roaming friends were, kept in camp memory so their land can be woken again after a restart. */
	private static final String MEMORY = "survival.roaming";
	/** How long a friend woken after a restart has to turn up before their grant lapses, in ticks. */
	private static final int RESTORE_GRACE = 400;

	/** A ticket we hold: of which kind, in which dimension, where and how far. */
	private record Held(TicketType type, ResourceKey<Level> dimension, ChunkPos pos, int radius) {
	}

	/** A friend's roaming grant. */
	private static final class Roamer {
		final UUID friend;
		final long since;
		String why;
		@Nullable Held ticket;
		/** Set once they have been outside the camp area, so the grant only lapses when they come back to it. */
		boolean beenAway;
		/** Woken after a restart and not seen yet: kept for a short while so the friend can load. */
		boolean restoring;

		Roamer(UUID friend, long since, String why) {
			this.friend = friend;
			this.since = since;
			this.why = why;
		}
	}

	private static @Nullable Held camp;
	private static final Map<UUID, Roamer> ROAMING = new LinkedHashMap<>();
	/** Whether this server's remembered roaming friends have been woken yet, and when. */
	private static boolean restored;
	private static int restoredAt;
	/** Set when the roaming grants changed since they were last written to camp memory. */
	private static boolean roamingChanged;

	private ChunkLoader() {
	}

	private static TicketType register(String name) {
		// Loads, simulates (entities and blocks tick) and keeps the dimension awake; no timeout, never saved.
		int flags = TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE;
		return Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, name),
			new TicketType(TicketType.NO_TIMEOUT, flags));
	}

	/** Registers the ticket types and the server hooks. Called once from {@link Survival#init()}. */
	static void init() {
		Objects.requireNonNull(CAMP_TICKET);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % INTERVAL == 11) {
				update(server);
			}
		});
		ServerLifecycleEvents.SERVER_STARTING.register(server -> forget());
		ServerLifecycleEvents.SERVER_STOPPING.register(ChunkLoader::releaseAll);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> forget());
	}

	/** Lets fewer players keep the camp running: only those the extra rule accepts as well. */
	public static void restrictCampLoad(Predicate<ServerPlayer> extra) {
		campLoadAllowed = campLoadAllowed.and(extra);
	}

	// ----------------------------------------------------------------- roaming

	/**
	 * Asks for the land around a friend to keep running while they are away from camp ({@code why} is shown by
	 * {@code /friends trips}). Returns true when granted, or already held. Refused when keeping land loaded is
	 * switched off, nobody who counts is online, or {@code maxRoamingFriends} friends already hold one.
	 */
	public static boolean startRoaming(CompanionEntity c, String why) {
		if (!(c.level() instanceof ServerLevel level) || !c.isAlive() || c.isRemoved()) {
			return false;
		}
		Roamer held = ROAMING.get(c.getUUID());
		if (held != null) {
			held.why = why;
			return true;
		}
		if (!canRoam(level.getServer())) {
			return false;
		}
		Roamer roamer = new Roamer(c.getUUID(), level.getGameTime(), why);
		ROAMING.put(c.getUUID(), roamer);
		follow(level, roamer, c);
		remember(level.getServer());
		return true;
	}

	/** True when another friend could be granted a roaming ticket right now. */
	public static boolean canRoam(MinecraftServer server) {
		int max = FriendsConfig.get().maxRoamingFriends;
		return max > 0 && ROAMING.size() < max && anyoneCounts(server);
	}

	/** True when this friend holds a roaming ticket. */
	public static boolean isRoaming(CompanionEntity c) {
		return ROAMING.containsKey(c.getUUID());
	}

	/** Why each roaming friend holds a ticket, by friend, for the trips report. */
	public static Map<UUID, String> roaming() {
		Map<UUID, String> map = new LinkedHashMap<>();
		ROAMING.forEach((id, r) -> map.put(id, r.why));
		return map;
	}

	/** Lets go of a friend's roaming ticket at once (safe to call when they hold none). */
	public static void stopRoaming(CompanionEntity c) {
		Roamer roamer = ROAMING.remove(c.getUUID());
		if (roamer != null && c.level() instanceof ServerLevel level) {
			release(level.getServer(), roamer.ticket);
			remember(level.getServer());
		}
	}

	/** True when this position is inside the part of the camp area the camp ticket keeps running. */
	public static boolean inCampArea(ServerLevel level, BlockPos pos) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return false;
		}
		int r = Math.max(8, campBlocks(data) - HOME_MARGIN);
		return Camp.horizontalDistSqr(pos, data.campPos().orElseThrow()) <= (double) r * r;
	}

	// ------------------------------------------------------------------ ticking

	private static void update(MinecraftServer server) {
		boolean anyone = anyoneCounts(server);
		updateCamp(server, anyone);
		if (anyone && !restored) {
			restore(server);
		}
		updateRoaming(server, anyone);
		if (roamingChanged) {
			remember(server);
		}
	}

	/** True when a player who keeps the camp running is online. */
	private static boolean anyoneCounts(MinecraftServer server) {
		Predicate<ServerPlayer> allowed = campLoadAllowed;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (allowed.test(player)) {
				return true;
			}
		}
		return false;
	}

	private static void updateCamp(MinecraftServer server, boolean anyone) {
		Held wanted = anyone && FriendsConfig.get().keepCampLoaded ? campTicket(server) : null;
		if (Objects.equals(wanted, camp)) {
			return;
		}
		Held old = camp;
		if (wanted != null && add(server, wanted)) {
			camp = wanted;
		} else {
			camp = null;
		}
		release(server, old);
		if (wanted != null && camp != null) {
			HardcoreFriends.LOGGER.info("Keeping the camp running while you are away: {} chunks out from {} {}", wanted.radius() - 2,
				wanted.pos().getMiddleBlockX(), wanted.pos().getMiddleBlockZ());
		}
	}

	/** The camp ticket the camp should have now: the camp radius plus the gathering ring, in chunks. */
	private static @Nullable Held campTicket(MinecraftServer server) {
		CampData data = Camp.data(server);
		if (data.campPos().isEmpty()) {
			return null;
		}
		Identifier dimensionId = Identifier.tryParse(data.campDimension());
		if (dimensionId == null) {
			return null;
		}
		ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
		if (server.getLevel(dimension) == null) {
			return null;
		}
		int chunks = Math.clamp((campBlocks(data) + 15) / 16, 1, MAX_CAMP_CHUNKS);
		// A ticket of radius r keeps chunks up to r - 2 away entity-ticking.
		return new Held(CAMP_TICKET, dimension, ChunkPos.containing(data.campPos().get()), chunks + 2);
	}

	/** The camp radius plus the gathering ring, in blocks. */
	private static int campBlocks(CampData data) {
		return Camp.radius(data) + FriendsConfig.get().resourceRadius;
	}

	private static void updateRoaming(MinecraftServer server, boolean anyone) {
		Map<UUID, CompanionEntity> loaded = new java.util.HashMap<>();
		for (CompanionEntity c : Companions.everyone()) {
			loaded.put(c.getUUID(), c);
		}
		int max = FriendsConfig.get().maxRoamingFriends;
		int kept = 0;
		Iterator<Roamer> it = ROAMING.values().iterator();
		while (it.hasNext()) {
			Roamer roamer = it.next();
			CompanionEntity c = loaded.get(roamer.friend);
			if (c == null && roamer.restoring && anyone && server.getTickCount() - restoredAt < RESTORE_GRACE) {
				kept++;
				continue; // woken after a restart: give them a moment to load
			}
			String drop = dropReason(roamer, c, anyone, ++kept > max);
			if (drop != null) {
				release(server, roamer.ticket);
				it.remove();
				kept--;
				roamingChanged = true;
				continue;
			}
			roamer.restoring = false;
			follow((ServerLevel) c.level(), roamer, c);
		}
	}

	/** Why a roaming grant should lapse now, or null to keep it. */
	private static @Nullable String dropReason(Roamer roamer, @Nullable CompanionEntity c, boolean anyone, boolean overLimit) {
		if (c == null || !c.isAlive() || c.isRemoved() || !(c.level() instanceof ServerLevel level)) {
			return "gone";
		}
		if (!anyone || overLimit) {
			return "not allowed now";
		}
		if (c.mode() != CompanionMode.WORK && !roamsInAnyMode.test(c)) {
			return "not on their own";
		}
		if (roamer.ticket != null && !roamer.ticket.dimension().equals(level.dimension())) {
			return "changed dimension";
		}
		if (level.getGameTime() - roamer.since > MAX_ROAM_TICKS) {
			return "away too long";
		}
		boolean home = inCampArea(level, c.blockPosition());
		if (!home) {
			roamer.beenAway = true;
		} else if (roamer.beenAway) {
			return "home";
		}
		return null;
	}

	/** Moves a roaming ticket to the friend's current chunk (adding the new one before letting the old one go). */
	private static void follow(ServerLevel level, Roamer roamer, CompanionEntity c) {
		Held wanted = new Held(ROAMING_TICKET, level.dimension(), c.chunkPosition(), ROAMING_RADIUS + 2);
		if (wanted.equals(roamer.ticket)) {
			return;
		}
		Held old = roamer.ticket;
		roamer.ticket = add(level.getServer(), wanted) ? wanted : null;
		release(level.getServer(), old);
		roamingChanged = true;
	}

	/**
	 * Wakes the land of the friends who were roaming when the world last closed, so they load and can carry on (their
	 * trip is remembered with them). Done once, when a player who counts is first online.
	 */
	private static void restore(MinecraftServer server) {
		restored = true;
		restoredAt = server.getTickCount();
		CompoundTag mem = Camp.data(server).memory(MEMORY);
		int max = FriendsConfig.get().maxRoamingFriends;
		for (String key : List.copyOf(mem.keySet())) {
			if (ROAMING.size() >= max) {
				break;
			}
			CompoundTag entry = mem.getCompoundOrEmpty(key);
			Identifier dimensionId = Identifier.tryParse(entry.getStringOr("dim", ""));
			UUID friend;
			try {
				friend = UUID.fromString(key);
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (dimensionId == null || ROAMING.containsKey(friend)) {
				continue;
			}
			ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
			Held held = new Held(ROAMING_TICKET, dimension, ChunkPos.unpack(entry.getLongOr("chunk", 0L)), ROAMING_RADIUS + 2);
			Roamer roamer = new Roamer(friend, entry.getLongOr("since", 0L), entry.getStringOr("why", "on a trip"));
			roamer.beenAway = true;
			roamer.restoring = true;
			if (add(server, held)) {
				roamer.ticket = held;
				ROAMING.put(friend, roamer);
			}
		}
		roamingChanged = true;
	}

	/** Writes where the roaming friends are to camp memory (not their tickets: those are never saved). */
	private static void remember(MinecraftServer server) {
		roamingChanged = false;
		CampData data = Camp.data(server);
		CompoundTag mem = new CompoundTag();
		for (Roamer roamer : ROAMING.values()) {
			Held held = roamer.ticket;
			if (held == null) {
				continue;
			}
			CompoundTag entry = new CompoundTag();
			entry.putString("dim", held.dimension().identifier().toString());
			entry.putLong("chunk", held.pos().pack());
			entry.putLong("since", roamer.since);
			entry.putString("why", roamer.why);
			mem.put(roamer.friend.toString(), entry);
		}
		CompoundTag old = data.memory(MEMORY);
		if (!old.equals(mem)) {
			for (String key : List.copyOf(old.keySet())) {
				old.remove(key);
			}
			for (String key : mem.keySet()) {
				old.put(key, mem.getCompoundOrEmpty(key));
			}
			data.setDirty();
		}
	}

	// ----------------------------------------------------------------- tickets

	private static boolean add(MinecraftServer server, Held held) {
		ServerLevel level = server.getLevel(held.dimension());
		if (level == null) {
			return false;
		}
		level.getChunkSource().addTicketWithRadius(held.type(), held.pos(), held.radius());
		return true;
	}

	private static void release(MinecraftServer server, @Nullable Held held) {
		if (held == null) {
			return;
		}
		ServerLevel level = server.getLevel(held.dimension());
		if (level != null) {
			level.getChunkSource().removeTicketWithRadius(held.type(), held.pos(), held.radius());
		}
	}

	private static void releaseAll(MinecraftServer server) {
		release(server, camp);
		camp = null;
		for (Roamer roamer : new ArrayList<>(ROAMING.values())) {
			release(server, roamer.ticket);
		}
		ROAMING.clear();
	}

	private static void forget() {
		camp = null;
		ROAMING.clear();
		restored = false;
		roamingChanged = false;
	}

	/** One line per held ticket, for the trips report. */
	static List<String> describe() {
		List<String> lines = new ArrayList<>();
		Held c = camp;
		if (c != null) {
			lines.add("The camp keeps running " + (c.radius() - 2) + " chunks out from its centre.");
		}
		return lines;
	}
}
