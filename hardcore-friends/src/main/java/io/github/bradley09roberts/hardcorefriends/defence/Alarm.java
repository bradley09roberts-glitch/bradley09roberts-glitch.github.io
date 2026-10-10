package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * The village alarm: when danger comes, the bell is rung, children and non-fighters take cover indoors, fighters go to
 * their posts, and when the danger has passed (or at dawn) somebody gives the all-clear.
 *
 * <p><b>What rings it</b> ({@link Cause}): hostiles closing in on the village at night in numbers (at least
 * {@code alarmHordeSize} seen in the village or just beyond its edge at once); a creeper inside the village, day or
 * night; a vanilla raid on or near the village ({@code ServerLevel.getRaidAt} the camp centre); a player in the village
 * with the Raid Omen (a raid is about to start); and a player ringing a bell in the village themselves.
 *
 * <p><b>Who sees what.</b> Twice a second ({@value #INTERVAL} ticks) the friends who are awake in the village look
 * about them with the existing {@link Threats} helpers: the friend on the night watch and the guards on duty every
 * time, and up to {@value #ROTATING_SENSORS} others in turn. A hostile counts when it is in the village (up at ground
 * level, {@link Area}) and a friend can see it, or it is within {@value #HEARD} blocks of one; the night watch's own
 * alarm list counts too. At most {@value #SIGHT_CHECKS} lines of sight are worked out a round, so a big fight stays
 * cheap. Each hostile raises the alarm once while it stays about: one still in sight after an all-clear does not ring
 * it again, though one that went away (unseen for {@value #FORGET} ticks) and came back does.
 *
 * <p><b>The all-clear</b> comes once nothing has been seen inside the village for {@value #ALL_CLEAR} ticks (and the alarm
 * has rung for at least {@value #MIN_ALARM}), or at dawn for an alarm raised at night; a raid holds it until the raid
 * is over. However things go, an alarm without a raid ends after {@value #MAX_ALARM} ticks, so a mob stuck in sight
 * somewhere can never keep everyone indoors for good. A raid won earns the camp Unity ({@value #RAID_UNITY}).
 *
 * <p>Nothing here changes a block; ringing a bell is the game's own bell ring ({@code BellBlock.attemptToRing}), so
 * players hear it and raiders near it glow as in the game itself.
 */
public final class Alarm {
	/** Why the alarm rang. */
	public enum Cause {
		MONSTERS("monsters", "monsters closing in"),
		CREEPER("a creeper", "a creeper in the village"),
		RAID("raiders", "a raid"),
		OMEN("raiders on the way", "a bad omen brought into the village"),
		PLAYER("trouble", "the bell rung by a player");

		/** For the alarm line: "monsters", "a creeper". */
		final String danger;
		/** For {@code /friends defence}. */
		final String description;

		Cause(String danger, String description) {
			this.danger = danger;
			this.description = description;
		}
	}

	/** An alarm in progress. */
	static final class State {
		Cause cause;
		final long since;
		final boolean atNight;
		final String detail;
		long lastThreat;
		int raidWaves;
		/** The bell to ring, and who is going to ring it. */
		@Nullable BlockPos bell;
		@Nullable UUID ringer;
		long ringerSince;
		boolean ringWanted;
		boolean rung;
		final Set<UUID> failedRingers = new HashSet<>();
		/** How many friends said each line this alarm, so a crowd taking cover does not fill the chat. */
		final Map<Line, Integer> spoken = new EnumMap<>(Line.class);

		State(Cause cause, long since, boolean atNight, String detail) {
			this.cause = cause;
			this.since = since;
			this.atNight = atNight;
			this.detail = detail;
			this.lastThreat = since;
		}
	}

	/** A bell a player was seen using (the ring is confirmed by the bell shaking a moment later). */
	private record PlayerRing(String player, long at) {
	}

	static final int INTERVAL = 10;
	/** How far round a friend they look for hostiles. */
	private static final double SENSE_RANGE = 20;
	private static final double SENSE_HEIGHT = 12;
	/** A hostile this close to a friend is noticed even out of sight (heard). */
	private static final double HEARD = 6;
	/** Besides the watch and the guards, this many friends look about each round, in turn. */
	private static final int ROTATING_SENSORS = 6;
	private static final int SIGHT_CHECKS = 48;
	/** Seen this recently, a hostile is still there. */
	static final int RECENT = 100;
	private static final int FORGET = 20 * 30;
	static final int ALL_CLEAR = 20 * 30;
	static final int MIN_ALARM = 20 * 30;
	static final int MAX_ALARM = 20 * 60 * 5;
	/** A player's ring sounds the alarm again at most this often. */
	private static final int PLAYER_RING_GAP = 20 * 30;
	/** Nobody is sent to a bell with a creeper this close to it. */
	static final double CREEPER_CLEARANCE = 10;
	/** Each line is said by at most this many friends an alarm. */
	private static final int SAY_LIMIT = 3;
	static final int RAID_UNITY = 40;
	private static final int RAID_UNITY_CAP = 80;
	static final String UNITY_RAID = "raid";
	/** What the camp remembers of the defence ({@code CampData.memory}): the last alarm, the friends' own bell. */
	static final String MEMORY = "defence";

	private static @Nullable State state;
	/** Hostiles seen in the village, with when they were last seen (game time). */
	private static final Map<LivingEntity, Long> SEEN = new WeakHashMap<>();
	/** Hostiles that have raised the alarm tonight: each does so once. */
	private static final Set<LivingEntity> REPORTED = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<BlockPos, PlayerRing> PLAYER_RINGS = new HashMap<>();
	/** Players already warned about for the Raid Omen they carry. */
	private static final Set<UUID> OMENS = new HashSet<>();
	private static @Nullable Raid raid;
	private static boolean raidSettled;
	private static int sensorCursor;
	private static long lastPlayerAlarm = Long.MIN_VALUE;

	private Alarm() {
	}

	// ------------------------------------------------------------------ queries

	/** True while the alarm is on. */
	public static boolean isActive() {
		return state != null;
	}

	static @Nullable State state() {
		return state;
	}

	/** The hostiles seen in the village (or just beyond its edge) within the last {@value #RECENT} ticks. */
	static List<LivingEntity> present(ServerLevel level) {
		long now = level.getGameTime();
		List<LivingEntity> list = new ArrayList<>();
		for (Map.Entry<LivingEntity, Long> e : SEEN.entrySet()) {
			LivingEntity t = e.getKey();
			if (now - e.getValue() <= RECENT && t.level() == level && t.isAlive() && !t.isRemoved()) {
				list.add(t);
			}
		}
		return list;
	}

	/** The creepers among {@link #present}: nobody is ever sent towards one. */
	static List<LivingEntity> creepers(ServerLevel level) {
		List<LivingEntity> list = new ArrayList<>();
		for (LivingEntity t : present(level)) {
			if (t instanceof Creeper) {
				list.add(t);
			}
		}
		return list;
	}

	/** Where the danger is: the middle of the hostiles seen lately, or null when none are (a player rang the bell). */
	static @Nullable Vec3 focus(ServerLevel level) {
		List<LivingEntity> seen = present(level);
		if (seen.isEmpty()) {
			return null;
		}
		double x = 0;
		double y = 0;
		double z = 0;
		for (LivingEntity t : seen) {
			x += t.getX();
			y += t.getY();
			z += t.getZ();
		}
		return new Vec3(x / seen.size(), y / seen.size(), z / seen.size());
	}

	/** True if this friend has been asked to ring the bell now. */
	static boolean isRinger(CompanionEntity c) {
		State s = state;
		return s != null && c.getUUID().equals(s.ringer);
	}

	/** The bell this alarm's ringer is to ring. */
	static @Nullable BlockPos bellToRing() {
		State s = state;
		return s == null ? null : s.bell;
	}

	/** Says a line for the alarm, unless {@value #SAY_LIMIT} friends have already said it this alarm. */
	static boolean say(CompanionEntity c, Line line, Object... args) {
		State s = state;
		if (s != null) {
			int said = s.spoken.getOrDefault(line, 0);
			if (said >= SAY_LIMIT) {
				return false;
			}
			if (Speech.say(c, line, args)) {
				s.spoken.put(line, said + 1);
				return true;
			}
			return false;
		}
		return Speech.say(c, line, args);
	}

	// --------------------------------------------------------------- ticking

	/** Every server tick; works every {@value #INTERVAL} ticks. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 3) {
			return;
		}
		CampData data = Camp.data(server);
		ServerLevel level = Area.campLevel(server, data);
		if (level == null || !FriendsConfig.get().villageDefence) {
			if (state != null) {
				state = null; // no camp any more, or the defence switched off: nothing to keep everyone in for
			}
			SEEN.clear();
			PLAYER_RINGS.clear();
			return;
		}
		long now = level.getGameTime();
		BlockPos centre = data.campPos().orElseThrow();
		int radius = Camp.radius(data);
		sense(level, centre, radius, now);
		Raid nearby = level.getRaidAt(centre);
		Raid ongoing = nearby != null && !nearby.isOver() && !nearby.isStopped() ? nearby : null;
		settleRaid(level, data, centre, ongoing);
		State s = state;
		if (s == null) {
			consider(level, data, centre, radius, now, ongoing);
		} else {
			keep(level, data, s, now, ongoing);
		}
		State after = state;
		if (after != null) {
			Ringing.tick(level, data, after, now);
		}
		PLAYER_RINGS.entrySet().removeIf(e -> now - e.getValue().at() > INTERVAL * 2 || e.getValue().at() > now);
	}

	/** The friends awake in the village look about them (see the class description). */
	private static void sense(ServerLevel level, BlockPos centre, int radius, long now) {
		List<CompanionEntity> sensors = new ArrayList<>();
		CompanionEntity watcher = NightWatch.watcher(level);
		if (watcher != null && !watcher.isAsleep()) {
			sensors.add(watcher);
		}
		for (CompanionEntity guard : GuardRota.onDuty(level)) {
			if (!guard.isAsleep() && !sensors.contains(guard)) {
				sensors.add(guard);
			}
		}
		List<CompanionEntity> others = new ArrayList<>();
		for (CompanionEntity c : Companions.in(level)) {
			if (!c.isAsleep() && !c.isChild() && !sensors.contains(c)
				&& Area.inside(level, c.blockPosition(), centre, radius + Area.EDGE)) {
				others.add(c);
			}
		}
		if (!others.isEmpty()) {
			int n = Math.min(ROTATING_SENSORS, others.size());
			for (int i = 0; i < n; i++) {
				sensors.add(others.get(Math.floorMod(sensorCursor + i, others.size())));
			}
			sensorCursor = Math.floorMod(sensorCursor + n, Math.max(1, others.size()));
		}
		int sightChecks = 0;
		for (CompanionEntity sensor : sensors) {
			for (LivingEntity threat : Threats.around(sensor, SENSE_RANGE)) {
				Long at = SEEN.get(threat);
				if (at != null && at == now) {
					continue;
				}
				if (Math.abs(threat.getY() - sensor.getY()) > SENSE_HEIGHT
					|| !Area.inside(level, threat.blockPosition(), centre, radius + Area.EDGE)) {
					continue;
				}
				boolean noticed = threat.distanceToSqr(sensor) <= HEARD * HEARD;
				if (!noticed && sightChecks < SIGHT_CHECKS) {
					sightChecks++;
					noticed = sensor.hasLineOfSight(threat);
				}
				if (noticed) {
					SEEN.put(threat, now);
				}
			}
		}
		for (LivingEntity threat : NightWatch.alarmed(level)) {
			SEEN.put(threat, now);
		}
		SEEN.entrySet().removeIf(e -> !e.getKey().isAlive() || e.getKey().isRemoved() || e.getKey().level() != level
			|| now - e.getValue() > FORGET);
		// One gone from sight for a while is forgotten: if it comes back, it is news again.
		REPORTED.removeIf(t -> !SEEN.containsKey(t));
	}

	/** No alarm on: does anything call for one? */
	private static void consider(ServerLevel level, CampData data, BlockPos centre, int radius, long now, @Nullable Raid ongoing) {
		if (ongoing != null) {
			raise(level, data, Cause.RAID, "", now, ongoing);
			return;
		}
		for (ServerPlayer player : level.players()) {
			boolean omen = player.hasEffect(MobEffects.RAID_OMEN);
			if (!omen) {
				OMENS.remove(player.getUUID());
			} else if (!player.isSpectator() && Area.inside(level, player.blockPosition(), centre, radius + 16)
				&& OMENS.add(player.getUUID())) {
				raise(level, data, Cause.OMEN, player.getName().getString(), now, null);
				return;
			}
		}
		String ringer = confirmedPlayerRing(level, now);
		if (ringer != null) {
			lastPlayerAlarm = now;
			raise(level, data, Cause.PLAYER, ringer, now, null);
			return;
		}
		List<LivingEntity> present = present(level);
		for (LivingEntity t : present) {
			if (t instanceof Creeper && Area.inside(level, t.blockPosition(), centre, radius) && REPORTED.add(t)) {
				raise(level, data, Cause.CREEPER, "", now, null);
				return;
			}
		}
		if (Camp.isNightTime(level) && present.size() >= FriendsConfig.get().alarmHordeSize) {
			boolean fresh = false;
			for (LivingEntity t : present) {
				fresh |= !REPORTED.contains(t);
			}
			if (fresh) {
				REPORTED.addAll(present);
				raise(level, data, Cause.MONSTERS, Integer.toString(present.size()), now, null);
			}
		}
	}

	private static void raise(ServerLevel level, CampData data, Cause cause, String detail, long now, @Nullable Raid ongoing) {
		State s = new State(cause, now, Camp.isNightTime(level), detail);
		s.ringWanted = cause != Cause.PLAYER; // a player who rang it has rung it already
		if (ongoing != null) {
			s.raidWaves = ongoing.getGroupsSpawned();
		}
		state = s;
		data.addStat("defence.alarms", 1);
		CompoundTag mem = data.memory(MEMORY);
		mem.putString("last_cause", cause.name());
		mem.putString("last_detail", detail);
		mem.putLong("last_day", Camp.day(level));
		mem.putLong("last_time", Camp.timeOfDay(level));
		mem.putLong("last_at", now);
		mem.putLong("last_length", -1);
		data.setDirty();
	}

	/** The alarm is on: keep it on while there is danger, or give the all-clear. */
	private static void keep(ServerLevel level, CampData data, State s, long now, @Nullable Raid ongoing) {
		BlockPos centre = data.campPos().orElseThrow();
		int radius = Camp.radius(data);
		for (LivingEntity t : present(level)) {
			if (Area.inside(level, t.blockPosition(), centre, radius)) {
				s.lastThreat = now; // still in the village (one only prowling past its edge does not keep everyone in)
				break;
			}
		}
		if (ongoing != null) {
			s.lastThreat = now;
			if (s.cause != Cause.RAID) {
				s.cause = Cause.RAID; // monsters, a bad omen or a player's ring turned out to be a raid
				s.ringWanted = true;
				s.rung = false;
			}
			if (ongoing.getGroupsSpawned() > s.raidWaves) {
				if (s.raidWaves > 0) {
					s.ringWanted = true; // a new wave: ring again
					s.rung = false;
				}
				s.raidWaves = ongoing.getGroupsSpawned();
			}
			return;
		}
		long ringing = now - s.since;
		boolean quiet = now - s.lastThreat >= ALL_CLEAR && ringing >= MIN_ALARM;
		boolean dawn = s.atNight && !Camp.isNightTime(level) && creepers(level).isEmpty();
		if (quiet || dawn || ringing >= MAX_ALARM) {
			allClear(level, data, s, now);
		}
	}

	private static void allClear(ServerLevel level, CampData data, State s, long now) {
		state = null;
		CompoundTag mem = data.memory(MEMORY);
		mem.putLong("last_length", now - s.since);
		data.setDirty();
		CompanionEntity speaker = speaker(level, s);
		if (speaker != null) {
			Speech.say(speaker, Line.ALL_CLEAR);
		}
	}

	/** Who gives the all-clear (or tells of a raid won): the ringer, else a fighter awake in the village, else anyone awake there. */
	private static @Nullable CompanionEntity speaker(ServerLevel level, @Nullable State s) {
		CompanionEntity best = null;
		int bestRank = Integer.MAX_VALUE;
		for (CompanionEntity c : Companions.in(level)) {
			if (c.isAsleep() || c.mode() == CompanionMode.STRANGER || !Area.atHome(c)) {
				continue;
			}
			int rank = s != null && c.getUUID().equals(s.ringer) ? 0 : Duty.fighter(c) ? 1 : c.isChild() ? 3 : 2;
			if (rank < bestRank) {
				bestRank = rank;
				best = c;
			}
		}
		return best;
	}

	/** Follows the raid on the village: a raid won earns the camp Unity, once. */
	private static void settleRaid(ServerLevel level, CampData data, BlockPos centre, @Nullable Raid ongoing) {
		if (ongoing != null && ongoing != raid) {
			raid = ongoing;
			raidSettled = false;
		}
		Raid watched = raid;
		if (watched == null || raidSettled) {
			return;
		}
		if (watched.isVictory()) {
			raidSettled = true;
			int gained = Unity.add(level, UNITY_RAID, RAID_UNITY, RAID_UNITY_CAP);
			data.addStat("defence.raids_won", 1);
			CompanionEntity speaker = speaker(level, state);
			if (speaker != null) {
				Speech.say(speaker, Line.RAID_WON);
			}
			if (gained > 0) {
				Speech.announce(level.getServer(), net.minecraft.network.chat.Component.literal(
					"The village beat off the raid. Unity +" + gained + ".").withStyle(net.minecraft.ChatFormatting.GOLD));
			}
		} else if (watched.isLoss() || watched.isStopped()) {
			raidSettled = true;
			data.addStat("defence.raids_lost", 1);
		}
	}

	// ----------------------------------------------------------- player rings

	/**
	 * A player used a bell: if it is in the village, remember it, and once the bell is seen shaking (the use rang it)
	 * the alarm sounds. Never stops the use itself.
	 */
	static InteractionResult bellUsed(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
		if (!(world instanceof ServerLevel level) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof BellBlock)) {
			return InteractionResult.PASS;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data) || !Area.inside(level, pos, data.campPos().orElseThrow(), Camp.radius(data) + Area.EDGE)) {
			return InteractionResult.PASS;
		}
		if (PLAYER_RINGS.size() < 16) {
			PLAYER_RINGS.put(pos.immutable(), new PlayerRing(player.getName().getString(), level.getGameTime()));
		}
		return InteractionResult.PASS;
	}

	/** The name of a player whose use of a bell in the village rang it just now, or null. */
	private static @Nullable String confirmedPlayerRing(ServerLevel level, long now) {
		if (now - lastPlayerAlarm < PLAYER_RING_GAP && now >= lastPlayerAlarm) {
			return null;
		}
		Iterator<Map.Entry<BlockPos, PlayerRing>> it = PLAYER_RINGS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, PlayerRing> e = it.next();
			if (level.isLoaded(e.getKey()) && level.getBlockEntity(e.getKey()) instanceof BellBlockEntity bell && bell.shaking) {
				it.remove();
				return e.getValue().player();
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- memory

	/** "day 12, at night: monsters closing in (4 seen); all clear after 3 minutes", or null if no alarm ever rang. */
	static @Nullable String lastAlarm(CampData data) {
		CompoundTag mem = data.memory(MEMORY);
		String cause = mem.getStringOr("last_cause", "");
		if (cause.isEmpty()) {
			return null;
		}
		Cause c;
		try {
			c = Cause.valueOf(cause);
		} catch (IllegalArgumentException e) {
			return null;
		}
		String detail = mem.getStringOr("last_detail", "");
		String what = switch (c) {
			case MONSTERS -> detail.isEmpty() ? c.description : c.description + " (" + detail + " seen)";
			case OMEN -> detail.isEmpty() ? c.description : detail + " brought a bad omen into the village";
			case PLAYER -> detail.isEmpty() ? c.description : "the bell rung by " + detail;
			default -> c.description;
		};
		long time = mem.getLongOr("last_time", 0);
		String when = time >= 12500 && time < 23500 ? "at night" : time < 6000 ? "in the morning" : "in the afternoon";
		long length = mem.getLongOr("last_length", -1);
		String end = length < 0 ? "still ringing" : "all clear after " + minutes(length);
		return String.format(Locale.ROOT, "day %d, %s: %s; %s", mem.getLongOr("last_day", 0), when, what, end);
	}

	static String minutes(long ticks) {
		long seconds = ticks / 20;
		if (seconds < 60) {
			return seconds + (seconds == 1 ? " second" : " seconds");
		}
		long m = Math.round(seconds / 60.0);
		return m + (m == 1 ? " minute" : " minutes");
	}

	/** Forgets everything: a server starting or stopping. */
	static void clear() {
		state = null;
		SEEN.clear();
		REPORTED.clear();
		PLAYER_RINGS.clear();
		OMENS.clear();
		raid = null;
		raidSettled = false;
		sensorCursor = 0;
		lastPlayerAlarm = Long.MIN_VALUE;
	}
}
