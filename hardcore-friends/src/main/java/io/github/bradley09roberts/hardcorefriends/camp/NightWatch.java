package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Reach;
import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * The camp's night watch: one friend stays awake while the others sleep, and raises the alarm when a hostile comes
 * into the camp.
 *
 * <p><b>The rota.</b> The night has two watches: the first from dusk until midnight ({@value #MIDNIGHT}), the second
 * from midnight until dawn. Aegis keeps the first watch whenever he is alive and working at the camp. Every other
 * watch goes to a friend who has something to fight back with, the best available ranked in this order: healthy; at
 * the camp rather than away from it; did not keep a watch last night (so the duty rotates night by night and nobody
 * misses sleep every night); the best
 * weapon (any sword before any axe before any other tool, then the sturdier); the most health; and finally the
 * friend's place in the roster, so the choice is always the same for the same camp. Tonight's first watcher never
 * keeps the second watch as well, and a friend alone at the camp (other than Aegis on his first watch) has nobody to
 * watch over and simply sleeps. A watcher stays on watch until it ends, unless they stop being fit for it (dead, gone, sent
 * to follow or stay, too weak to work, too tired, below {@value #TOO_TIRED} energy, or away from the camp, more than
 * {@value #LOOKOUT_RANGE} blocks beyond its edge, for over {@value #AWAY_TICKS} ticks): then the watch passes to the
 * next friend, so the camp keeps someone on watch even with Aegis dead or away. Friends at the camp are chosen before
 * friends away from it. Who kept watch is remembered in {@link CampData#memory} ({@value #MEMORY}), so it survives a
 * reload, and watchers may nap sooner the next day ({@link #keptWatchRecently}).
 *
 * <p>The watches follow the clock ({@link Camp#isNightTime}): a thunderstorm by day darkens the sky but keeps no watch.
 *
 * <p><b>The alarm.</b> Every half second the friend on watch, if awake, looks out over the camp, all of it however far
 * the village has grown it (at least {@value #LOOKOUT_RANGE} blocks round them, at most {@value #MAX_LOOKOUT}): a
 * hostile inside the camp that they can see, or one on the camp's own ground (within
 * {@value #GROUND_HEIGHT} blocks of the watcher's level or the camp's, not in a cave beneath it) that is close by or
 * already going for someone in the camp, and that a path leads to, sets off the alarm (they shout {@link Line#ALARM}).
 * Sleepers in the camp within {@value #ALARM_RANGE} blocks of a hostile it was raised about wake for a new alarm,
 * and armed friends in the camp that close go for those hostiles (see
 * {@link io.github.bradley09roberts.hardcorefriends.ai.goal.MutualDefenceTargetGoal}): the alarm is shouted across the
 * camp's heart, not to every house of a village that has grown to sixty blocks and more. A hostile stays on the
 * alarm list while it lives and stays within {@value #EDGE_MARGIN} blocks of the camp's edge, and the alarm is raised
 * about each hostile once a night: one that wanders out and back in is gone for again without waking the camp.
 */
public final class NightWatch {
	/** The two watches of a night. */
	public enum Watch {
		/** Dusk until midnight: Aegis's, while he can keep it. */
		FIRST,
		/** Midnight until dawn. */
		SECOND
	}

	/** Who kept which watch, in {@link CampData#memory}. */
	public static final String MEMORY = "night_watch";
	/** Midnight, as a time of day: the first watch hands over to the second. */
	public static final long MIDNIGHT = 18000;
	/** Below this energy a friend is too tired to keep watch, and the watch passes on. */
	public static final double TOO_TIRED = 15;
	/** How far from the watcher a hostile inside the camp is spotted at least (further, if the camp reaches further). */
	public static final double LOOKOUT_RANGE = 24;
	/**
	 * Sleepers wake, and armed friends rally, for an alarm about a hostile this close to them: as far as a sleeper in the
	 * old camp's cabin ever was from one the watcher by the campfire spotted.
	 */
	public static final double ALARM_RANGE = 40;

	/**
	 * The furthest the watcher looks out, for a camp grown by its village (whose homes stand up to {@code villageRadius}
	 * blocks out, 64 at most): its whole ground from the watch post by the campfire, with room to spare.
	 */
	private static final double MAX_LOOKOUT = 96;
	/** A hostile this close to the watcher is noticed even out of sight (heard, if not seen), on the camp's own ground. */
	private static final double HEARD_RANGE = 12;
	/** Seen, a hostile further above or below the watcher than this is on a cliff or down a ravine, not in the camp. */
	private static final double LOOKOUT_HEIGHT = 12;
	/** Unseen, a hostile counts as on the camp's own ground within this height of the watcher or the camp centre. */
	private static final double GROUND_HEIGHT = 4;
	/** A hostile stays on the alarm list until it is this far beyond the camp's edge (so the edge does not re-alarm). */
	private static final int EDGE_MARGIN = 8;
	/** A watcher away from the camp this long hands the watch on. */
	private static final int AWAY_TICKS = 20 * 30;
	private static final int LOOKOUT_INTERVAL = 10;
	private static final long NOON = 6000;
	private static final long DAY = 24000;

	/** Hostiles the alarm was raised about, while they live and stay in or near the camp. */
	private static final Set<LivingEntity> ALARMED = Collections.newSetFromMap(new WeakHashMap<>());
	/** Every hostile the alarm was raised about tonight: the alarm is raised about each once a night. */
	private static final Set<LivingEntity> REPORTED = Collections.newSetFromMap(new WeakHashMap<>());
	/** Since when (game time) each friend has been away from the camp while on watch. */
	private static final Map<UUID, Long> AWAY_SINCE = new HashMap<>();
	private static long alarmAt = Long.MIN_VALUE;
	private static @Nullable ServerLevel alarmLevel;

	/** The watcher worked out this tick, so the many callers each tick share one answer. */
	private static long cachedAt = Long.MIN_VALUE;
	private static @Nullable ServerLevel cachedLevel;
	private static @Nullable CompanionEntity cachedWatcher;

	private NightWatch() {
	}

	// ------------------------------------------------------------------ rota

	/**
	 * The watch being kept now: the first from dusk until midnight, the second from midnight until dawn, null by day
	 * (a thunderstorm by day included: the watches follow the clock, see {@link Camp#isNightTime}).
	 */
	public static @Nullable Watch watch(ServerLevel level) {
		if (!Camp.isNightTime(level) && !Camp.isDusk(level)) {
			return null;
		}
		long t = Camp.timeOfDay(level);
		return t >= MIDNIGHT || t < NOON ? Watch.SECOND : Watch.FIRST;
	}

	/** Which night it is: a night runs from one noon to the next, so the morning after still belongs to it. */
	public static long nightIndex(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime() - NOON, DAY);
	}

	/** The friend on watch now, choosing one when the watch starts or its watcher can no longer keep it; null by day. */
	public static @Nullable CompanionEntity watcher(ServerLevel level) {
		long now = level.getGameTime();
		if (cachedAt != now || cachedLevel != level) {
			cachedAt = now;
			cachedLevel = level;
			cachedWatcher = findWatcher(level);
		}
		return cachedWatcher;
	}

	/** True while this friend is the one keeping watch. */
	public static boolean isOnWatch(CompanionEntity c) {
		return c.level() instanceof ServerLevel level && watcher(level) == c;
	}

	/**
	 * True when this friend kept a watch last night (or is keeping one tonight): they may nap sooner the next day to
	 * make up the sleep.
	 */
	public static boolean keptWatchRecently(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return false;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return false;
		}
		CompoundTag mem = data.memory(MEMORY);
		if (mem.getLongOr("night", Long.MIN_VALUE) < nightIndex(level) - 1) {
			return false;
		}
		return keys(mem.getStringOr("kept", "")).contains(watchKey(c));
	}

	private static @Nullable CompanionEntity findWatcher(ServerLevel level) {
		Watch watch = watch(level);
		if (watch == null) {
			return null;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return null;
		}
		CompoundTag mem = data.memory(MEMORY);
		long night = nightIndex(level);
		long recorded = mem.getLongOr("night", Long.MIN_VALUE);
		if (recorded != night) {
			// A new night: last night's watchers are remembered (for the rota), tonight's watches are still open.
			mem.putString("prev", recorded == night - 1 ? mem.getStringOr("kept", "") : "");
			mem.putString("kept", "");
			mem.putString(Watch.FIRST.name(), "");
			mem.putString(Watch.SECOND.name(), "");
			mem.putLong("night", night);
			data.setDirty();
		}
		CompanionEntity current = byKey(level, mem.getStringOr(watch.name(), ""));
		if (current != null && fit(current, level) && !awayTooLong(current, level, data)) {
			return current;
		}
		CompanionEntity chosen = choose(level, watch, mem);
		String key = chosen == null ? "" : watchKey(chosen);
		if (!key.equals(mem.getStringOr(watch.name(), ""))) {
			mem.putString(watch.name(), key);
			if (chosen != null) {
				Set<String> kept = keys(mem.getStringOr("kept", ""));
				kept.add(key);
				mem.putString("kept", String.join(",", kept));
			}
			data.setDirty();
		}
		return chosen;
	}

	/** The friend best placed to keep this watch, by the rota's order (see the class description); null if nobody can. */
	private static @Nullable CompanionEntity choose(ServerLevel level, Watch watch, CompoundTag mem) {
		int atCamp = 0;
		List<CompanionEntity> pool = new ArrayList<>();
		for (CompanionEntity c : Companions.in(level)) {
			if (c.mode() == CompanionMode.WORK && nearCamp(c, level)) {
				atCamp++;
				if (fit(c, level)) {
					pool.add(c);
				}
			}
		}
		if (watch == Watch.FIRST) {
			CampData data = Camp.data(level.getServer());
			for (CompanionEntity c : pool) {
				if (c.friendId() == FriendId.AEGIS && !awayTooLong(c, level, data)) {
					return c; // the first watch is his, unless he is away from the camp
				}
			}
		} else {
			String first = mem.getStringOr(Watch.FIRST.name(), "");
			pool.removeIf(c -> watchKey(c).equals(first)); // they kept the first watch: they sleep now
		}
		if (atCamp < 2) {
			return null; // a friend on their own has nobody to watch over: they sleep, and wake if danger comes close
		}
		pool.removeIf(c -> !c.hasMeleeTool()); // only someone who can fight back keeps watch
		Set<String> lastNight = keys(mem.getStringOr("prev", ""));
		CampData data = Camp.data(level.getServer());
		Comparator<CompanionEntity> order = Comparator
			.comparing((CompanionEntity c) -> !c.isHealthy())
			.thenComparing(c -> awayFromCamp(c, data))
			.thenComparing(c -> lastNight.contains(watchKey(c)))
			.thenComparing(Comparator.comparingInt(CompanionEntity::bestWeaponRank).reversed())
			.thenComparing(Comparator.comparingDouble(CompanionEntity::getHealth).reversed())
			.thenComparingInt(CompanionEntity::rosterIndex);
		return pool.stream().min(order).orElse(null);
	}

	/** Able to keep watch: here at the camp, working on their own, and neither too weak nor too tired. */
	static boolean fit(CompanionEntity c, ServerLevel level) {
		if (!c.isAlive() || c.isRemoved() || c.level() != level || c.mode() != CompanionMode.WORK || c.isChild()) {
			return false;
		}
		return !c.tooWeakToWork() && c.needs().get(Need.ENERGY) >= TOO_TIRED && nearCamp(c, level);
	}

	/** At the camp or out in its gathering ring, not off exploring far away. */
	private static boolean nearCamp(CompanionEntity c, ServerLevel level) {
		int reach = Camp.radius(Camp.data(level.getServer())) + FriendsConfig.get().resourceRadius;
		return Camp.horizontalDistSqr(c.blockPosition(), c.homePos()) <= (double) reach * reach;
	}

	/** More than {@value #LOOKOUT_RANGE} blocks beyond the camp's edge: too far to look out over it. */
	private static boolean awayFromCamp(CompanionEntity c, CampData data) {
		Optional<BlockPos> centre = data.campPos();
		if (centre.isEmpty()) {
			return false;
		}
		double reach = Camp.radius(data) + LOOKOUT_RANGE;
		return Camp.horizontalDistSqr(c.blockPosition(), centre.get()) > reach * reach;
	}

	/**
	 * True when the friend on watch has been away from the camp for over {@value #AWAY_TICKS} ticks (escorting a
	 * player out late, say): the watch passes on, as no alarm can be raised for the camp from out there.
	 */
	private static boolean awayTooLong(CompanionEntity c, ServerLevel level, CampData data) {
		if (!awayFromCamp(c, data)) {
			AWAY_SINCE.remove(c.getUUID());
			return false;
		}
		long now = level.getGameTime();
		long since = AWAY_SINCE.computeIfAbsent(c.getUUID(), id -> now);
		return now - since > AWAY_TICKS;
	}

	private static @Nullable CompanionEntity byKey(ServerLevel level, String key) {
		if (key.isEmpty()) {
			return null;
		}
		for (CompanionEntity c : Companions.in(level)) {
			if (watchKey(c).equals(key)) {
				return c;
			}
		}
		return null;
	}

	/**
	 * Who a watcher is in the rota's memory: a named friend's key ("aegis"), or a newcomer's own id, since newcomers
	 * share their archetype's key and must never be taken for that friend (or for each other).
	 */
	private static String watchKey(CompanionEntity c) {
		return c.isSettler() ? c.getUUID().toString() : c.friendId().key();
	}

	private static Set<String> keys(String list) {
		Set<String> keys = new LinkedHashSet<>();
		if (!list.isEmpty()) {
			keys.addAll(Arrays.asList(list.split(",")));
		}
		return keys;
	}

	// ----------------------------------------------------------------- alarm

	/** The friend on watch looks out over the camp; called every server tick, works every half second. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOKOUT_INTERVAL != 0) {
			return;
		}
		ALARMED.removeIf(threat -> !threat.isAlive() || threat.isRemoved() || !insideCamp(threat, EDGE_MARGIN));
		REPORTED.removeIf(threat -> !threat.isAlive() || threat.isRemoved());
		CampData data = Camp.data(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (!Camp.isCampLevel(level, data)) {
				continue;
			}
			if (watch(level) == null) {
				REPORTED.clear(); // a new day: tonight's hostiles are tomorrow night's news
				AWAY_SINCE.clear();
				continue;
			}
			CompanionEntity watcher = watcher(level);
			if (watcher != null && !watcher.isAsleep()) {
				lookout(watcher, level, data);
			}
		}
	}

	private static void lookout(CompanionEntity watcher, ServerLevel level, CampData data) {
		BlockPos centre = data.campPos().orElse(watcher.homePos());
		// The whole camp, as far as the village has grown it, not just the old camp round the campfire: homes stand out
		// there with friends asleep in them.
		double range = Math.clamp(Camp.radius(data) + Math.sqrt(Camp.horizontalDistSqr(watcher.blockPosition(), centre)),
			LOOKOUT_RANGE, MAX_LOOKOUT);
		LivingEntity spotted = null;
		for (LivingEntity threat : Threats.around(watcher, range)) {
			if (ALARMED.contains(threat) || !insideCamp(threat) || Math.abs(threat.getY() - watcher.getY()) > LOOKOUT_HEIGHT
				|| !noticed(watcher, threat, centre)) {
				continue;
			}
			ALARMED.add(threat);
			if (REPORTED.add(threat)) {
				spotted = threat; // new tonight: raise the alarm (one back from the edge is simply gone for again)
			}
		}
		if (spotted != null) {
			alarmAt = level.getGameTime();
			alarmLevel = level;
			Speech.say(watcher, Line.ALARM, spotted.getName().getString());
		}
	}

	/**
	 * Whether the watcher notices this hostile inside the camp: one they can see; otherwise only one on the camp's own
	 * ground (within {@value #GROUND_HEIGHT} blocks of the watcher's level or the camp centre's, so not in a cave
	 * beneath it) that is close by or going for someone in the camp, and that a whole path leads to.
	 */
	private static boolean noticed(CompanionEntity watcher, LivingEntity threat, BlockPos centre) {
		if (watcher.hasLineOfSight(threat)) {
			return true;
		}
		double dy = Math.min(Math.abs(threat.getY() - watcher.getY()), Math.abs(threat.getY() - centre.getY()));
		if (dy > GROUND_HEIGHT) {
			return false;
		}
		boolean heard = threat.distanceTo(watcher) <= HEARD_RANGE
			|| threat instanceof Mob mob && mob.getTarget() != null && insideCamp(mob.getTarget());
		return heard && Reach.check(watcher, threat) != Reach.Answer.NO;
	}

	/** True if the camp's alarm was raised in this level after the given game time. */
	public static boolean alarmRaisedSince(ServerLevel level, long since) {
		return alarmLevel == level && alarmAt > since;
	}

	/** The hostiles the alarm was raised about that are still alive and inside the camp of this level. */
	public static List<LivingEntity> alarmed(ServerLevel level) {
		List<LivingEntity> list = new ArrayList<>();
		for (LivingEntity threat : ALARMED) {
			if (threat.level() == level && threat.isAlive() && !threat.isRemoved() && insideCamp(threat)) {
				list.add(threat);
			}
		}
		return list;
	}

	/** True if a hostile the alarm was raised about is still within {@value #ALARM_RANGE} blocks of this friend. */
	public static boolean alarmedNear(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return false;
		}
		for (LivingEntity threat : alarmed(level)) {
			if (threat.distanceToSqr(c) <= ALARM_RANGE * ALARM_RANGE) {
				return true;
			}
		}
		return false;
	}

	/** True if this entity stands inside the camp (horizontally) in the camp's own dimension. */
	public static boolean insideCamp(LivingEntity e) {
		return insideCamp(e, 0);
	}

	/** True if this entity stands inside the camp, or within {@code margin} blocks beyond its edge. */
	private static boolean insideCamp(LivingEntity e, int margin) {
		if (!(e.level() instanceof ServerLevel level)) {
			return false;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return false;
		}
		BlockPos centre = data.campPos().orElseThrow();
		int r = Camp.radius(data) + margin;
		return Camp.horizontalDistSqr(e.blockPosition(), centre) <= (double) r * r;
	}

	/** Forgets the alarm and the cached watcher: a server starting or stopping, or a test resetting the camp. */
	public static void clear() {
		ALARMED.clear();
		REPORTED.clear();
		AWAY_SINCE.clear();
		alarmAt = Long.MIN_VALUE;
		alarmLevel = null;
		cachedAt = Long.MIN_VALUE;
		cachedLevel = null;
		cachedWatcher = null;
	}
}
