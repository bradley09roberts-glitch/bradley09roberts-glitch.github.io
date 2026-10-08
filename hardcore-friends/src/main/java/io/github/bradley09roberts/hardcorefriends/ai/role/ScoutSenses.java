package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Scout's constant watchfulness, checked every {@value #INTERVAL} ticks whatever Scout is doing. For every player
 * within {@value #WATCH_RANGE} blocks Scout warns about a creeper within {@value #CREEPER_RANGE} blocks of them
 * (with a compass direction) and about two or more hostiles within {@value #HOSTILE_RANGE} blocks. Players near camp
 * hear when night is coming (time of day passing {@value #NIGHT_WARNING_TIME}), and nearby players when a
 * thunderstorm starts. Each player hears each kind of warning at most once per {@value #COOLDOWN} ticks. From the
 * Close Friends bond upwards, warned mobs glow for 8 seconds.
 */
public final class ScoutSenses {
	public static final int INTERVAL = 40;
	public static final int WATCH_RANGE = 48;
	public static final int CREEPER_RANGE = 16;
	public static final int HOSTILE_RANGE = 20;
	public static final int NIGHT_WARNING_TIME = 11500;
	public static final int COOLDOWN = 600;
	public static final int GLOW_TICKS = 160;
	/** Players within this distance of Scout hear a warning as speech; further ones get a direct message. */
	private static final double SPEECH_RANGE = 60;
	/** How long a night or storm warning keeps being retried after it becomes due. */
	private static final int PENDING_TICKS = 400;

	/** Kinds of warning, each with its own per-player cooldown. */
	public enum Kind {
		CREEPER,
		HOSTILES,
		NIGHT,
		STORM,
		LAVA
	}

	/** Per-scout memory: when each player was last warned about each kind, and the last weather and time seen. */
	private static final class State {
		final Map<UUID, EnumMap<Kind, Long>> warned = new HashMap<>();
		long lastTimeOfDay = -1;
		boolean wasThundering;
		boolean seenWeather;
		long nightPendingUntil;
		long stormPendingUntil;
	}

	private static final Map<CompanionEntity, State> STATES = new WeakHashMap<>();

	private ScoutSenses() {
	}

	public static void tick(CompanionEntity scout) {
		if (!(scout.level() instanceof ServerLevel level) || !scout.isAlive() || scout.tickCount % INTERVAL != 0) {
			return;
		}
		State state = STATES.computeIfAbsent(scout, s -> new State());
		long now = level.getGameTime();
		state.warned.values().forEach(m -> m.values().removeIf(t -> now - t > COOLDOWN * 4L));
		boolean glow = Unity.scoutMarksThreats(level.getServer());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(scout) > WATCH_RANGE * WATCH_RANGE) {
				continue;
			}
			watchPlayer(scout, state, player, now, glow);
		}
		watchSky(scout, level, state, now);
	}

	private static void watchPlayer(CompanionEntity scout, State state, ServerPlayer player, long now, boolean glow) {
		Creeper creeper = Threats.nearestCreeper(player, CREEPER_RANGE);
		if (creeper != null && creeper.distanceTo(player) <= CREEPER_RANGE && ready(state, player, Kind.CREEPER, now)) {
			Vec3 at = creeper.position();
			int dist = (int) Math.round(creeper.distanceTo(player));
			String where = Compass.direction(player, at);
			String hazard = Compass.isBehind(player, at)
				? "creeper behind you, to the " + where + " (" + dist + " blocks)"
				: "creeper to the " + where + " of you (" + dist + " blocks)";
			if (warn(scout, player, hazard)) {
				mark(state, player, Kind.CREEPER, now);
				if (glow) {
					markMob(creeper);
				}
			}
			return;
		}
		List<LivingEntity> hostiles = new ArrayList<>();
		for (LivingEntity e : Threats.around(player, HOSTILE_RANGE)) {
			if (e.distanceTo(player) <= HOSTILE_RANGE) {
				hostiles.add(e);
			}
		}
		if (hostiles.size() >= 2 && ready(state, player, Kind.HOSTILES, now)) {
			double x = 0;
			double z = 0;
			for (LivingEntity e : hostiles) {
				x += e.getX();
				z += e.getZ();
			}
			Vec3 middle = new Vec3(x / hostiles.size(), player.getY(), z / hostiles.size());
			String hazard = hostiles.size() + " hostiles closing in from the " + Compass.direction(player, middle);
			if (warn(scout, player, hazard)) {
				mark(state, player, Kind.HOSTILES, now);
				if (glow) {
					hostiles.forEach(ScoutSenses::markMob);
				}
			}
		}
	}

	private static void watchSky(CompanionEntity scout, ServerLevel level, State state, long now) {
		boolean dayCycle = !level.dimensionType().hasFixedTime();
		long tod = Camp.timeOfDay(level);
		if (dayCycle && state.lastTimeOfDay >= 0 && state.lastTimeOfDay < NIGHT_WARNING_TIME && tod >= NIGHT_WARNING_TIME && tod < 13000) {
			state.nightPendingUntil = now + PENDING_TICKS;
		}
		state.lastTimeOfDay = tod;
		if (now < state.nightPendingUntil) {
			// Retried for a while, because Scout's warning line may be on cooldown at the exact moment.
			warnEach(scout, state, playersNearCampOrScout(level, scout), Kind.NIGHT, now, "night is coming - time to head back to camp");
		}
		boolean thundering = level.isThundering();
		if (state.seenWeather && thundering && !state.wasThundering) {
			state.stormPendingUntil = now + PENDING_TICKS;
		}
		state.wasThundering = thundering;
		state.seenWeather = true;
		if (now < state.stormPendingUntil) {
			List<ServerPlayer> targets = new ArrayList<>();
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(scout) <= WATCH_RANGE * WATCH_RANGE) {
					targets.add(p);
				}
			}
			warnEach(scout, state, targets, Kind.STORM, now, "a thunderstorm is starting - get under a roof and watch for mobs in the dark");
		}
	}

	private static void warnEach(CompanionEntity scout, State state, List<ServerPlayer> targets, Kind kind, long now, String hazard) {
		for (ServerPlayer p : targets) {
			if (ready(state, p, kind, now) && warn(scout, p, hazard)) {
				mark(state, p, kind, now);
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Warns one player: as Scout's WARNING line when the player is close enough to hear, otherwise as a direct
	 * message. Returns false if the line is still on cooldown (the caller retries later).
	 */
	public static boolean warn(CompanionEntity scout, ServerPlayer player, String hazard) {
		if (player.distanceToSqr(scout) <= SPEECH_RANGE * SPEECH_RANGE) {
			return Speech.say(scout, Line.WARNING, player.getName().getString(), hazard);
		}
		Speech.tell(player, FriendId.SCOUT, player.getName().getString() + ", " + hazard + "!");
		return true;
	}

	/** Warns several players about the same hazard (lava near camp), with the per-player cooldown. */
	public static void warnAll(CompanionEntity scout, List<ServerPlayer> players, String hazard) {
		State state = STATES.computeIfAbsent(scout, s -> new State());
		warnEach(scout, state, players, Kind.LAVA, scout.level().getGameTime(), hazard);
	}

	/** Non-spectator players in the camp's dimension within the camp radius (+8) of its centre. */
	public static List<ServerPlayer> playersNearCamp(ServerLevel level) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.isAlive() && isNearCamp(level, p)) {
				list.add(p);
			}
		}
		return list;
	}

	public static boolean isNearCamp(ServerLevel level, ServerPlayer player) {
		CampData data = Camp.data(level.getServer());
		BlockPos centre = Camp.center(level).orElse(null);
		if (centre == null) {
			return false;
		}
		int r = Camp.radius(data) + 8;
		return Camp.horizontalDistSqr(centre, player.blockPosition()) <= (double) r * r;
	}

	private static List<ServerPlayer> playersNearCampOrScout(ServerLevel level, CompanionEntity scout) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.isAlive() && (isNearCamp(level, p) || p.distanceToSqr(scout) <= WATCH_RANGE * WATCH_RANGE)) {
				list.add(p);
			}
		}
		return list;
	}

	private static boolean ready(State state, ServerPlayer player, Kind kind, long now) {
		Long last = state.warned.getOrDefault(player.getUUID(), new EnumMap<>(Kind.class)).get(kind);
		return last == null || now - last >= COOLDOWN;
	}

	private static void mark(State state, ServerPlayer player, Kind kind, long now) {
		state.warned.computeIfAbsent(player.getUUID(), u -> new EnumMap<>(Kind.class)).put(kind, now);
	}

	private static void markMob(LivingEntity mob) {
		mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS), null);
	}

	/**
	 * Game time when this scout last warned the player about a kind of hazard, or -1 if never (within the memory
	 * window). Used by tests and status displays.
	 */
	public static long lastWarned(CompanionEntity scout, UUID player, Kind kind) {
		State state = STATES.get(scout);
		if (state == null) {
			return -1;
		}
		Map<Kind, Long> kinds = state.warned.get(player);
		Long t = kinds == null ? null : kinds.get(kind);
		return t == null ? -1 : t;
	}
}
