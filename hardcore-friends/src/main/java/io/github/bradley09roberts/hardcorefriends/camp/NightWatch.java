package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

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
 * watch goes to a friend who has something to fight back with, the best available ranked in this order: healthy;
 * did not keep a watch last night (so the duty rotates night by night and nobody misses sleep every night); the best
 * weapon (any sword before any axe before any other tool, then the sturdier); the most health; and finally the
 * friend's place in the roster, so the choice is always the same for the same camp. Tonight's first watcher never
 * keeps the second watch as well, and a friend alone at the camp (other than Aegis on his first watch) has nobody to
 * watch over and simply sleeps. A watcher stays on watch until it ends, unless they stop being fit for it (dead, gone, sent
 * to follow or stay, too weak to work, or too tired, below {@value #TOO_TIRED} energy): then the watch passes to the
 * next friend, so the camp keeps someone on watch even with Aegis dead or away. Who kept watch is remembered in
 * {@link CampData#memory} ({@value #MEMORY}), so it survives a reload, and watchers may nap sooner the next day
 * ({@link #keptWatchRecently}).
 *
 * <p><b>The alarm.</b> Every half second the friend on watch, if awake, looks out over the camp: a hostile within
 * {@value #LOOKOUT_RANGE} blocks of them and inside the camp, that they can see, that is close by, or that is
 * already going for someone, sets off the alarm (they shout {@link Line#ALARM}). Sleepers in the camp wake for a new
 * alarm, and armed friends in the camp go for the hostiles it was raised about (see
 * {@link io.github.bradley09roberts.hardcorefriends.ai.goal.MutualDefenceTargetGoal}). A hostile stays on the alarm
 * list while it lives and stays inside the camp.
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
	/** How far from the watcher a hostile inside the camp is spotted. */
	public static final double LOOKOUT_RANGE = 24;
	/** A hostile this close to the watcher is noticed even out of sight (heard, if not seen). */
	private static final double HEARD_RANGE = 12;
	/** A hostile further above or below the watcher than this is in a cave or on a cliff, not in the camp. */
	private static final double LOOKOUT_HEIGHT = 12;
	private static final int LOOKOUT_INTERVAL = 10;
	private static final long NOON = 6000;
	private static final long DAY = 24000;

	/** Hostiles the alarm was raised about, while they live and stay in the camp. */
	private static final Set<LivingEntity> ALARMED = Collections.newSetFromMap(new WeakHashMap<>());
	private static long alarmAt = Long.MIN_VALUE;
	private static @Nullable ServerLevel alarmLevel;

	/** The watcher worked out this tick, so the many callers each tick share one answer. */
	private static long cachedAt = Long.MIN_VALUE;
	private static @Nullable ServerLevel cachedLevel;
	private static @Nullable CompanionEntity cachedWatcher;

	private NightWatch() {
	}

	// ------------------------------------------------------------------ rota

	/** The watch being kept now: the first from dusk until midnight, the second from midnight until dawn, null by day. */
	public static @Nullable Watch watch(ServerLevel level) {
		if (!Camp.isNight(level) && !Camp.isDusk(level)) {
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
		return keys(mem.getStringOr("kept", "")).contains(c.friendId().key());
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
		if (current != null && fit(current, level)) {
			return current;
		}
		CompanionEntity chosen = choose(level, watch, mem);
		String key = chosen == null ? "" : chosen.friendId().key();
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
			for (CompanionEntity c : pool) {
				if (c.friendId() == FriendId.AEGIS) {
					return c; // the first watch is his
				}
			}
		} else {
			String first = mem.getStringOr(Watch.FIRST.name(), "");
			pool.removeIf(c -> c.friendId().key().equals(first)); // they kept the first watch: they sleep now
		}
		if (atCamp < 2) {
			return null; // a friend on their own has nobody to watch over: they sleep, and wake if danger comes close
		}
		pool.removeIf(c -> !c.hasMeleeTool()); // only someone who can fight back keeps watch
		Set<String> lastNight = keys(mem.getStringOr("prev", ""));
		Comparator<CompanionEntity> order = Comparator
			.comparing((CompanionEntity c) -> !c.isHealthy())
			.thenComparing(c -> lastNight.contains(c.friendId().key()))
			.thenComparing(Comparator.comparingInt(CompanionEntity::bestWeaponRank).reversed())
			.thenComparing(Comparator.comparingDouble(CompanionEntity::getHealth).reversed())
			.thenComparingInt(c -> c.friendId().ordinal());
		return pool.stream().min(order).orElse(null);
	}

	/** Able to keep watch: here at the camp, working on their own, and neither too weak nor too tired. */
	static boolean fit(CompanionEntity c, ServerLevel level) {
		if (!c.isAlive() || c.isRemoved() || c.level() != level || c.mode() != CompanionMode.WORK) {
			return false;
		}
		return !c.tooWeakToWork() && c.needs().get(Need.ENERGY) >= TOO_TIRED && nearCamp(c, level);
	}

	/** At the camp or out in its gathering ring, not off exploring far away. */
	private static boolean nearCamp(CompanionEntity c, ServerLevel level) {
		int reach = Camp.radius(Camp.data(level.getServer())) + FriendsConfig.get().resourceRadius;
		return Camp.horizontalDistSqr(c.blockPosition(), c.homePos()) <= (double) reach * reach;
	}

	private static @Nullable CompanionEntity byKey(ServerLevel level, String key) {
		if (key.isEmpty()) {
			return null;
		}
		for (CompanionEntity c : Companions.in(level)) {
			if (c.friendId().key().equals(key)) {
				return c;
			}
		}
		return null;
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
		ALARMED.removeIf(threat -> !threat.isAlive() || threat.isRemoved() || !insideCamp(threat));
		CampData data = Camp.data(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (!Camp.isCampLevel(level, data)) {
				continue;
			}
			CompanionEntity watcher = watcher(level);
			if (watcher != null && !watcher.isAsleep()) {
				lookout(watcher, level);
			}
		}
	}

	private static void lookout(CompanionEntity watcher, ServerLevel level) {
		LivingEntity spotted = null;
		for (LivingEntity threat : Threats.around(watcher, LOOKOUT_RANGE)) {
			if (ALARMED.contains(threat) || !insideCamp(threat) || Math.abs(threat.getY() - watcher.getY()) > LOOKOUT_HEIGHT) {
				continue;
			}
			boolean noticed = threat.distanceTo(watcher) <= HEARD_RANGE || watcher.hasLineOfSight(threat)
				|| threat instanceof Mob mob && mob.getTarget() != null;
			if (noticed) {
				ALARMED.add(threat);
				spotted = threat;
			}
		}
		if (spotted != null) {
			alarmAt = level.getGameTime();
			alarmLevel = level;
			Speech.say(watcher, Line.ALARM, spotted.getName().getString());
		}
	}

	/** True if the camp's alarm was raised in this level after the given game time. */
	public static boolean alarmRaisedSince(ServerLevel level, long since) {
		return alarmLevel == level && alarmAt > since;
	}

	/** The hostiles the alarm was raised about that are still alive and inside the camp of this level. */
	public static List<LivingEntity> alarmed(ServerLevel level) {
		List<LivingEntity> list = new ArrayList<>();
		for (LivingEntity threat : ALARMED) {
			if (threat.level() == level && threat.isAlive() && !threat.isRemoved()) {
				list.add(threat);
			}
		}
		return list;
	}

	/** True if this entity stands inside the camp (horizontally) in the camp's own dimension. */
	public static boolean insideCamp(LivingEntity e) {
		if (!(e.level() instanceof ServerLevel level)) {
			return false;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return false;
		}
		BlockPos centre = data.campPos().orElseThrow();
		int r = Camp.radius(data);
		return Camp.horizontalDistSqr(e.blockPosition(), centre) <= (double) r * r;
	}

	/** Forgets the alarm and the cached watcher: a server starting or stopping, or a test resetting the camp. */
	public static void clear() {
		ALARMED.clear();
		alarmAt = Long.MIN_VALUE;
		alarmLevel = null;
		cachedAt = Long.MIN_VALUE;
		cachedLevel = null;
		cachedWatcher = null;
	}
}
