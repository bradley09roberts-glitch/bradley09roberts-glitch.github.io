package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * The guard rota: once the village has a watchtower, a gate or walls ({@link Posts}), guards stand the posts at night,
 * besides the camp's night watch (which goes on as before; before there are posts, it is all there is).
 *
 * <p>The night is split as the watch's is ({@link NightWatch#watch}): a first shift from dusk to midnight and a second
 * from midnight to dawn, each with up to {@code guardsPerShift} guards (no more than there are posts). A guard is a
 * grown-up at work in the village, fit (healthy, not too tired, not too weak) and armed with a sword, an axe or a bow;
 * chosen in this order: the guard trade (should the market give it), warriors, those who did not stand guard last
 * night, the best weapon, the most health, and the roster. Nobody keeps both shifts (everyone who stood any part of
 * the first is let off the second, and {@link #stoodFirstShift} keeps them off the night watch's second half too), and
 * the friend on the watch is never a guard as well. A bow carrier takes the watchtower lookout when it is free; the
 * others the gate, then the walls. A guard stays on until their shift ends unless they stop being fit, or could not
 * reach their post {@value #TRIES} times; then someone else takes the post, and the one relieved is not picked again
 * that night. Taking a post needs more energy ({@value #FRESH}) than keeping one ({@value #TOO_TIRED}), so a guard
 * relieved worn out is not woken for the post again as soon as a short sleep lifts them over the line.
 *
 * <p>Guards are paid in respect: in the morning the camp gains {@value #PAY} Unity for each who stood guard (at most
 * {@value #PAY_CAP} a day), and they may nap during the day ({@link GuardRestTask}). Who stood which shift is
 * remembered in {@code CampData.memory} ({@value #MEMORY}), so it survives a reload.
 */
final class GuardRota {
	static final String MEMORY = "defence.guards";
	/** Everyone who stood any part of tonight's first shift, in {@link #MEMORY}. */
	private static final String FIRST_SERVED = "first";
	/** Guards relieved tonight (hurt, worn out, stuck, gone off): not picked again until tomorrow night. */
	private static final String RELIEVED = "relieved";
	static final String UNITY_GUARD = "guard";
	private static final int PAY = 2;
	private static final int PAY_CAP = 8;
	private static final int TRIES = 3;
	/** Below this energy a guard is relieved. */
	private static final double TOO_TIRED = 20;
	/** A friend needs this much energy to be picked for a post. */
	private static final double FRESH = 35;
	/** A guard further than this beyond the village's edge is not at the village. */
	private static final int AWAY = 16;
	/** A guard within this distance of a high post's stand is up there (and shoots from it). */
	private static final double ON_POST = 2.5;

	private static final Map<UUID, Posts.Post> ON_DUTY = new HashMap<>();
	private static final Map<UUID, Integer> FAILS = new HashMap<>();
	private static String shift = "";

	private GuardRota() {
	}

	// ---------------------------------------------------------------- queries

	/** This guard's post now, or null when they are not on duty. */
	static Posts.@Nullable Post postOf(CompanionEntity c) {
		return ON_DUTY.get(c.getUUID());
	}

	static boolean isOnDuty(CompanionEntity c) {
		return ON_DUTY.containsKey(c.getUUID());
	}

	static Set<UUID> onDutyIds() {
		return ON_DUTY.keySet();
	}

	/** The guards on duty now who are loaded in this world. */
	static List<CompanionEntity> onDuty(ServerLevel level) {
		List<CompanionEntity> list = new ArrayList<>();
		for (UUID id : ON_DUTY.keySet()) {
			if (level.getEntity(id) instanceof CompanionEntity c && c.isAlive()) {
				list.add(c);
			}
		}
		return list;
	}

	/**
	 * For the combat package's archery: a guard up at the watchtower lookout shoots from there rather than climbing
	 * down to close in with a blade.
	 */
	static boolean holdsPost(CompanionEntity c) {
		Posts.Post post = ON_DUTY.get(c.getUUID());
		return post != null && post.high() && c.position().distanceToSqr(Vec3.atBottomCenterOf(post.stand())) <= ON_POST * ON_POST;
	}

	/** True when this friend stood guard last night (or is standing it tonight): they may nap by day. */
	static boolean stoodGuardRecently(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return false;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return false;
		}
		CompoundTag mem = data.memory(MEMORY);
		if (mem.getLongOr("night", Long.MIN_VALUE) < NightWatch.nightIndex(level) - 1) {
			return false;
		}
		return keys(mem.getStringOr("served", "")).contains(key(c));
	}

	/**
	 * True when this friend stood guard in tonight's first shift, any part of it: they sleep the second half of the
	 * night, so the night watch does not pick them for its second watch either (a {@link NightWatch#EXCUSED} rule).
	 */
	static boolean stoodFirstShift(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return false;
		}
		CompoundTag mem = data.memory(MEMORY);
		if (mem.getLongOr("night", Long.MIN_VALUE) != NightWatch.nightIndex(level)) {
			return false;
		}
		String k = key(c);
		return keys(mem.getStringOr(FIRST_SERVED, "")).contains(k)
			|| parse(mem.getStringOr(NightWatch.Watch.FIRST.name(), "")).containsKey(k);
	}

	/** The guard could not get to their post: after {@value #TRIES} tries someone else takes it. */
	static void failed(CompanionEntity c) {
		FAILS.merge(c.getUUID(), 1, Integer::sum);
	}

	// ------------------------------------------------------------------- rota

	/** Once a second: who is on duty, and in the morning, the guards' pay. */
	static void tick(ServerLevel level, CampData data) {
		FriendsConfig cfg = FriendsConfig.get();
		CompoundTag mem = data.memory(MEMORY);
		long night = NightWatch.nightIndex(level);
		NightWatch.Watch watch = NightWatch.watch(level);
		if (watch == null) {
			ON_DUTY.clear();
			FAILS.clear();
			shift = "";
			pay(level, data, mem, night);
			return;
		}
		List<Posts.Post> posts = cfg.villageDefence && cfg.guardsPerShift > 0 ? Posts.guardPosts(level) : List.of();
		if (posts.isEmpty()) {
			ON_DUTY.clear();
			return;
		}
		if (mem.getLongOr("night", Long.MIN_VALUE) != night) {
			boolean lastNight = mem.getLongOr("night", Long.MIN_VALUE) == night - 1;
			mem.putString("prev", lastNight ? mem.getStringOr("served", "") : "");
			mem.putString("served", "");
			mem.putString(FIRST_SERVED, "");
			mem.putString(RELIEVED, "");
			mem.putString(NightWatch.Watch.FIRST.name(), "");
			mem.putString(NightWatch.Watch.SECOND.name(), "");
			mem.putBoolean("paid", false);
			mem.putLong("night", night);
			data.setDirty();
		}
		if (!shift.equals(watch.name())) {
			shift = watch.name();
			FAILS.clear();
		}
		List<CompanionEntity> team = Companions.in(level);
		BlockPos centre = data.campPos().orElseThrow();
		int reach = Camp.radius(data) + AWAY;
		Map<String, Integer> kept = new LinkedHashMap<>();
		Map<UUID, Posts.Post> duty = new HashMap<>();
		Set<Integer> taken = new HashSet<>();
		Set<String> relieved = keys(mem.getStringOr(RELIEVED, ""));
		boolean newlyRelieved = false;
		for (Map.Entry<String, Integer> e : parse(mem.getStringOr(watch.name(), "")).entrySet()) {
			CompanionEntity g = byKey(team, e.getKey());
			int post = e.getValue();
			if (g == null) {
				continue;
			}
			if (!fit(g, level, centre, reach, TOO_TIRED) || FAILS.getOrDefault(g.getUUID(), 0) >= TRIES) {
				newlyRelieved |= relieved.add(e.getKey()); // hurt, worn out, stuck or gone off: done for the night
				continue;
			}
			if (post >= 0 && post < posts.size() && !NightWatch.isOnWatch(g) && taken.add(post)) {
				kept.put(e.getKey(), post);
				duty.put(g.getUUID(), posts.get(post));
			}
		}
		if (newlyRelieved) {
			mem.putString(RELIEVED, String.join(",", relieved));
			data.setDirty();
		}
		int wanted = Math.min(posts.size(), cfg.guardsPerShift);
		if (kept.size() < wanted) {
			Set<String> resting = new HashSet<>(relieved);
			if (watch == NightWatch.Watch.SECOND) {
				// Everyone who stood any part of the first shift sleeps the second.
				resting.addAll(keys(mem.getStringOr(FIRST_SERVED, "")));
				resting.addAll(parse(mem.getStringOr(NightWatch.Watch.FIRST.name(), "")).keySet());
				// The first watch's keeper sleeps the second half of the night too (the rota's memory, same keys).
				resting.add(data.memory(NightWatch.MEMORY).getStringOr(NightWatch.Watch.FIRST.name(), ""));
			}
			Set<String> lastNight = keys(mem.getStringOr("prev", ""));
			List<CompanionEntity> pool = new ArrayList<>();
			for (CompanionEntity g : team) {
				String k = key(g);
				if (!kept.containsKey(k) && !resting.contains(k) && !NightWatch.isOnWatch(g)
					&& FAILS.getOrDefault(g.getUUID(), 0) < TRIES && fit(g, level, centre, reach, FRESH)) {
					pool.add(g);
				}
			}
			pool.sort(Comparator
				.comparing((CompanionEntity g) -> !Duty.isGuardByTrade(g))
				.thenComparing(g -> !g.isFighter())
				.thenComparing(g -> lastNight.contains(key(g)))
				.thenComparing(Comparator.comparingInt(CompanionEntity::bestWeaponRank).reversed())
				.thenComparing(Comparator.comparingDouble(CompanionEntity::getHealth).reversed())
				.thenComparingInt(CompanionEntity::rosterIndex));
			for (CompanionEntity g : pool) {
				if (kept.size() >= wanted) {
					break;
				}
				int post = choosePost(g, posts, taken);
				if (post < 0) {
					break;
				}
				taken.add(post);
				kept.put(key(g), post);
				duty.put(g.getUUID(), posts.get(post));
			}
		}
		String joined = join(kept);
		if (!joined.equals(mem.getStringOr(watch.name(), ""))) {
			mem.putString(watch.name(), joined);
			Set<String> served = keys(mem.getStringOr("served", ""));
			served.addAll(kept.keySet());
			mem.putString("served", String.join(",", served));
			if (watch == NightWatch.Watch.FIRST) {
				Set<String> first = keys(mem.getStringOr(FIRST_SERVED, ""));
				first.addAll(kept.keySet());
				mem.putString(FIRST_SERVED, String.join(",", first));
			}
			data.setDirty();
		}
		ON_DUTY.clear();
		ON_DUTY.putAll(duty);
	}

	/**
	 * Fit to stand guard: grown up, at work at the village, armed, healthy, not too weak, and with at least
	 * {@code energy} (more to take a post than to keep one).
	 */
	private static boolean fit(CompanionEntity c, ServerLevel level, BlockPos centre, int reach, double energy) {
		if (!c.isAlive() || c.isRemoved() || c.level() != level || c.mode() != CompanionMode.WORK || c.isChild() || !c.isTeamMember()) {
			return false;
		}
		if (c.tooWeakToWork() || !c.isHealthy() || c.needs().get(Need.ENERGY) < energy || c.isRetreating()) {
			return false;
		}
		if (!c.isArmed() && !Archery.canShoot(c)) {
			return false;
		}
		return Camp.horizontalDistSqr(c.blockPosition(), centre) <= (double) reach * reach;
	}

	/** A bow carrier takes a free lookout first; anyone else the gate, then the walls, then whatever is left. */
	private static int choosePost(CompanionEntity g, List<Posts.Post> posts, Set<Integer> taken) {
		boolean bow = Archery.canShoot(g);
		int fallback = -1;
		for (int i = 0; i < posts.size(); i++) {
			if (taken.contains(i)) {
				continue;
			}
			if (posts.get(i).high() == bow) {
				return i;
			}
			if (fallback < 0) {
				fallback = i;
			}
		}
		return fallback;
	}

	/** In the morning, the guards of the night just ended are paid in Unity, once. */
	private static void pay(ServerLevel level, CampData data, CompoundTag mem, long night) {
		if (mem.getLongOr("night", Long.MIN_VALUE) != night || mem.getBooleanOr("paid", true)) {
			return;
		}
		Set<String> served = keys(mem.getStringOr("served", ""));
		mem.putBoolean("paid", true);
		data.setDirty();
		if (!served.isEmpty()) {
			Unity.add(level, UNITY_GUARD, PAY * served.size(), PAY_CAP);
			data.addStat("defence.guard_shifts", served.size());
		}
	}

	// ------------------------------------------------------------------ keys

	/**
	 * Who a guard is in the rota's memory: a named friend's key ("scout"), or a newcomer's own id, the same way the
	 * night watch remembers its watchers.
	 */
	static String key(CompanionEntity c) {
		return c.isSettler() ? c.getUUID().toString() : c.friendId().key();
	}

	private static @Nullable CompanionEntity byKey(List<CompanionEntity> team, String key) {
		for (CompanionEntity c : team) {
			if (key(c).equals(key)) {
				return c;
			}
		}
		return null;
	}

	/** "scout=0,sage=1" into a map of guard key to post index. */
	private static Map<String, Integer> parse(String list) {
		Map<String, Integer> map = new LinkedHashMap<>();
		if (list.isEmpty()) {
			return map;
		}
		for (String entry : list.split(",")) {
			int eq = entry.lastIndexOf('=');
			if (eq <= 0) {
				continue;
			}
			try {
				map.put(entry.substring(0, eq), Integer.parseInt(entry.substring(eq + 1)));
			} catch (NumberFormatException e) {
				// a damaged entry is dropped; the post is filled again
			}
		}
		return map;
	}

	private static String join(Map<String, Integer> map) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, Integer> e : map.entrySet()) {
			parts.add(e.getKey() + "=" + e.getValue());
		}
		return String.join(",", parts);
	}

	private static Set<String> keys(String list) {
		Set<String> keys = new LinkedHashSet<>();
		if (!list.isEmpty()) {
			keys.addAll(Arrays.asList(list.split(",")));
		}
		return keys;
	}

	static void clear() {
		ON_DUTY.clear();
		FAILS.clear();
		shift = "";
	}
}
