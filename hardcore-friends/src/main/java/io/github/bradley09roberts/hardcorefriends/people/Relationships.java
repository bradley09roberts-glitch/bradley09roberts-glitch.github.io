package io.github.bradley09roberts.hardcorefriends.people;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * How friendships and romances grow and fade. Every pair of people on the team has a friendship and, for two adults
 * who are not family, a romance (see {@link PeopleData.Bond}); both grow from time spent together, more for a
 * compatible pair ({@link Compatibility}):
 * <ul>
 * <li>a chat (the needs package's chats call {@link #chatted}): friendship +3, romance +2;</li>
 * <li>sharing something (the common share job calls {@link #shared}): friendship +2;</li>
 * <li>fighting side by side: friendship +1.5 (at most every 30 seconds per pair);</li>
 * <li>each minute working within 12 blocks of each other: +0.5; eating, resting or playing within 6 blocks: +1 (and
 * romance +0.5); a parent and their child near each other: +1. A pair both in a good mood gains a quarter more;</li>
 * <li>a date: friendship +3, romance +8; children playing together: +2.</li>
 * </ul>
 * Romance only grows between two adults who are already friendly (friendship 45 or more), are not family, have a spark
 * at all, and are single or each other's sweetheart. Two people both in a low mood may quarrel now and then
 * (friendship -4, romance -3), which is how a romance goes sour.
 *
 * <p>Milestones: friendship 50 is said out loud (becoming good friends). Two single adults with friendship 60 and
 * romance 25 start going out (one asks, the other says yes; announced to everyone). A couple whose friendship falls
 * below 25 calls it off; a married couple only below 10 (rare). Proposals and dates are {@link DateTask}'s; weddings
 * {@link Weddings}'; babies {@link Births}'. Everything here runs on the server thread.
 */
final class Relationships {
	/** Friendship from which two people count as good friends (said out loud once). */
	static final double FRIENDS = 50;
	/** Friendship from which two people are close friends. */
	static final double CLOSE_FRIENDS = 75;
	/** Romance grows only from this much friendship. */
	static final double ROMANCE_NEEDS = 45;
	static final double DATING_FRIENDSHIP = 60;
	static final double DATING_ROMANCE = 25;
	/** A couple going out or engaged calls it off below this friendship; a married couple below {@link #SEPARATE_BELOW}. */
	static final double BREAK_UP_BELOW = 25;
	static final double SEPARATE_BELOW = 10;
	private static final double NEAR = 16;
	private static final double WORK_RANGE = 12;
	private static final double LEISURE_RANGE = 6;
	private static final int FIGHT_GAP = 600;
	/** The pink of romance news. */
	static final int ROMANCE_COLOUR = 0xF4A6C6;
	/** Unity for a new couple (category "romance"). */
	private static final String UNITY_ROMANCE = "romance";

	/** When each pair last gained from fighting side by side (game time). */
	private static final Map<String, Long> LAST_FIGHT = new HashMap<>();

	private Relationships() {
	}

	static void clear() {
		LAST_FIGHT.clear();
	}

	// -------------------------------------------------------------- the hooks

	/** Two friends had a chat (the needs package's chat job calls this when it ends well). */
	static void chatted(CompanionEntity starter, CompanionEntity partner) {
		if (starter.level() instanceof ServerLevel level) {
			change(level.getServer(), starter, partner, 3, 2);
		}
	}

	/** One friend handed another something they needed (the common share job). */
	static void shared(CompanionEntity giver, CompanionEntity receiver) {
		if (giver.level() instanceof ServerLevel level) {
			change(level.getServer(), giver, receiver, 2, 0);
		}
	}

	/** The {@code CompanionEvents.HIT} hook: a blow on a monster brings everyone fighting nearby closer. */
	static void hit(CompanionEntity c, ServerLevel level, Entity target, boolean killed) {
		if (!(target instanceof Enemy) || !c.isTeamMember()) {
			return;
		}
		long now = level.getGameTime();
		for (CompanionEntity other : Companions.near(level, new AABB(c.blockPosition()).inflate(WORK_RANGE))) {
			if (other == c || other.getTarget() == null) {
				continue;
			}
			String key = PeopleData.key(c.getUUID(), other.getUUID());
			Long last = LAST_FIGHT.get(key);
			if (last != null && now - last < FIGHT_GAP && now >= last) {
				continue;
			}
			if (LAST_FIGHT.size() > 2000) {
				LAST_FIGHT.clear(); // only rate limits: forgetting them is harmless
			}
			LAST_FIGHT.put(key, now);
			change(level.getServer(), c, other, 1.5, 0);
		}
	}

	// ------------------------------------------------------------ the change

	/**
	 * Changes how two people on the team get on: gains are scaled by their compatibility (romance by their attraction)
	 * and the speed setting, losses are not. Then the milestones: good friends said out loud, a romance gone sour.
	 */
	static void change(MinecraftServer server, CompanionEntity x, CompanionEntity y, double friendship, double romance) {
		if (x == y || !x.isTeamMember() || !y.isTeamMember() || !x.isAlive() || !y.isAlive()) {
			return;
		}
		PeopleData data = PeopleData.get(server);
		data.personFor(x, x.getRandom());
		data.personFor(y, y.getRandom());
		PeopleData.Bond bond = data.bond(x.getUUID(), y.getUUID());
		double speed = FriendsConfig.get().relationshipSpeed;
		if (friendship > 0) {
			bond.friendship += friendship * Compatibility.of(x.getUUID(), x.friendId(), y.getUUID(), y.friendId()) * speed;
		} else {
			bond.friendship += friendship;
		}
		if (romance > 0 && romanceCanGrow(data, bond, x, y)) {
			bond.romance += romance * Compatibility.attraction(x.getUUID(), x.friendId(), y.getUUID(), y.friendId()) * speed;
		} else if (romance < 0) {
			bond.romance += romance;
		}
		bond.friendship = PeopleData.clamp(bond.friendship);
		bond.romance = PeopleData.clamp(bond.romance);
		data.setDirty();
		if (!bond.friendsSaid && bond.friendship >= FRIENDS) {
			bond.friendsSaid = true;
			CompanionEntity speaker = x.getRandom().nextBoolean() ? x : y;
			Speech.say(speaker, Line.BECAME_FRIENDS, (speaker == x ? y : x).displayName());
		}
		if (bond.status.together() && bond.friendship < (bond.status == PeopleData.Status.MARRIED ? SEPARATE_BELOW : BREAK_UP_BELOW)) {
			breakUp(server, data, bond, x);
		}
	}

	/**
	 * True if romance may grow between these two: romance is on, both are adults, they are not family, they are
	 * friendly enough, and each is single or the other's sweetheart.
	 */
	static boolean romanceCanGrow(PeopleData data, PeopleData.Bond bond, CompanionEntity x, CompanionEntity y) {
		if (!FriendsConfig.get().romance || x.isChild() || y.isChild() || bond.friendship < ROMANCE_NEEDS
			|| data.related(x.getUUID(), y.getUUID())) {
			return false;
		}
		return free(data, x.getUUID(), y.getUUID()) && free(data, y.getUUID(), x.getUUID());
	}

	/** Single, or already with {@code with}. */
	private static boolean free(PeopleData data, UUID who, UUID with) {
		PeopleData.Bond own = data.partnerBond(who);
		return own == null || own.has(with);
	}

	// ----------------------------------------------------------- the minute

	/**
	 * Once a minute: time spent near each other, quarrels between two people both in a low mood, and new couples.
	 * At most a few hundred pairs; each costs a distance check unless the two are close.
	 */
	static void minute(MinecraftServer server) {
		List<CompanionEntity> team = new ArrayList<>();
		for (CompanionEntity c : Companions.all()) {
			if (c.mode() != CompanionMode.STRANGER) {
				team.add(c);
			}
		}
		PeopleData data = PeopleData.get(server);
		long clock = PeopleEvents.clock(server);
		for (int i = 0; i < team.size(); i++) {
			CompanionEntity x = team.get(i);
			for (int j = i + 1; j < team.size(); j++) {
				CompanionEntity y = team.get(j);
				if (x.level() != y.level() || x.distanceToSqr(y) > NEAR * NEAR) {
					continue;
				}
				together(server, data, x, y, clock);
			}
		}
	}

	private static void together(MinecraftServer server, PeopleData data, CompanionEntity x, CompanionEntity y, long clock) {
		double d2 = x.distanceToSqr(y);
		double friendship = 0;
		double romance = 0;
		if (d2 <= WORK_RANGE * WORK_RANGE && working(x) && working(y)) {
			friendship += 0.5;
		}
		if (d2 <= LEISURE_RANGE * LEISURE_RANGE && atLeisure(x) && atLeisure(y)) {
			friendship += 1;
			romance += 0.5;
		}
		if (data.related(x.getUUID(), y.getUUID()) && (x.isChild() || y.isChild())) {
			friendship += 1; // family time
		}
		Needs.Mood mx = x.needs().mood();
		Needs.Mood my = y.needs().mood();
		if (mx.ordinal() >= Needs.Mood.GOOD.ordinal() && my.ordinal() >= Needs.Mood.GOOD.ordinal()) {
			friendship *= 1.25;
			romance *= 1.25;
		}
		if (friendship > 0 || romance > 0) {
			change(server, x, y, friendship, romance);
		}
		PeopleData.Bond bond = data.bond(x.getUUID(), y.getUUID());
		boolean low = mx.ordinal() <= Needs.Mood.LOW.ordinal() && my.ordinal() <= Needs.Mood.LOW.ordinal();
		if (low && d2 <= 8 * 8 && awake(x) && awake(y) && clock - bond.lastQuarrel > 12000 && x.getRandom().nextFloat() < 0.1F) {
			bond.lastQuarrel = clock;
			CompanionEntity speaker = x.getRandom().nextBoolean() ? x : y;
			Speech.say(speaker, Line.QUARREL, (speaker == x ? y : x).displayName());
			change(server, x, y, -4, -3);
		}
		maybeAskOut(server, data, bond, x, y, clock);
	}

	/** Two single adults with feelings for each other, together and free: one asks the other out (a chance a minute). */
	private static void maybeAskOut(MinecraftServer server, PeopleData data, PeopleData.Bond bond, CompanionEntity x, CompanionEntity y,
			long clock) {
		if (bond.status.together() || bond.friendship < DATING_FRIENDSHIP || bond.romance < DATING_ROMANCE
			|| data.partnerBond(x.getUUID()) != null || data.partnerBond(y.getUUID()) != null
			|| !romanceCanGrow(data, bond, x, y) || !free(x) || !free(y)
			|| Camp.isNightTime((ServerLevel) x.level()) || x.getRandom().nextFloat() >= 0.25F) {
			return;
		}
		CompanionEntity asker = x.getRandom().nextBoolean() ? x : y;
		CompanionEntity other = asker == x ? y : x;
		Speech.say(asker, Line.ASK_OUT, other.displayName());
		PeopleEvents.later(server, 50, () -> {
			if (other.isAlive() && asker.isAlive()) {
				Speech.say(other, Line.ACCEPT, asker.displayName());
			}
		});
		bond.status = PeopleData.Status.DATING;
		bond.since = clock;
		bond.dates = 0;
		data.person(x.getUUID()).ifPresent(p -> p.partner = y.getUUID());
		data.person(y.getUUID()).ifPresent(p -> p.partner = x.getUUID());
		data.setDirty();
		announce(server, asker.displayName() + " and " + other.displayName() + " are going out together. How lovely!");
		Unity.add((ServerLevel) x.level(), UNITY_ROMANCE, 5, 20);
	}

	/**
	 * A romance gone sour: a couple going out or engaged call it off and stay friends; a married couple go their
	 * separate ways (their children stay at home with the parent they live with, and the other moves out).
	 */
	static void breakUp(MinecraftServer server, PeopleData data, PeopleData.Bond bond, @Nullable CompanionEntity speaker) {
		boolean married = bond.status == PeopleData.Status.MARRIED;
		Optional<PeopleData.Person> pa = data.person(bond.a);
		Optional<PeopleData.Person> pb = data.person(bond.b);
		String names = pa.map(p -> p.name).orElse("Someone") + " and " + pb.map(p -> p.name).orElse("someone");
		bond.status = PeopleData.Status.PARTED;
		bond.since = PeopleEvents.clock(server);
		bond.weddingDay = -1;
		bond.babyDue = -1;
		bond.romance = Math.min(bond.romance, 10);
		pa.ifPresent(p -> p.partner = null);
		pb.ifPresent(p -> p.partner = null);
		data.setDirty();
		if (speaker != null && speaker.isAlive()) {
			UUID other = bond.other(speaker.getUUID());
			data.person(other).ifPresent(p -> Speech.say(speaker, Line.BREAK_UP, p.name));
		}
		if (married) {
			// The one who moves out is still on the team: a home of their own (a household of one), not "gone for good".
			UUID leaver = leaver(data, bond);
			Homes.get().moveIn(server, leaver, Set.of(leaver));
			announce(server, names + " have gone their separate ways, but they'll always be family to their children.");
		} else {
			announce(server, names + " have decided to just be friends.");
		}
	}

	/** Of a married couple parting, the one who moves out: the one fewer of their children live with. */
	private static UUID leaver(PeopleData data, PeopleData.Bond bond) {
		int withA = 0;
		int withB = 0;
		for (PeopleData.Person child : data.livingChildren()) {
			if (bond.a.equals(child.homeParent)) {
				withA++;
			} else if (bond.b.equals(child.homeParent)) {
				withB++;
			}
		}
		return withA >= withB ? bond.b : bond.a;
	}

	// --------------------------------------------------------------- helpers

	/** At work: following a player, or on a job other than looking after themselves, idling or the people jobs. */
	static boolean working(CompanionEntity c) {
		if (c.mode() == CompanionMode.FOLLOW) {
			return true;
		}
		String job = job(c);
		return job != null && !job.startsWith("needs.") && !job.startsWith(People.JOB_PREFIX) && !job.equals("common.idle");
	}

	/** Eating, resting, chatting, at a pastime, playing, at a wedding or idling. */
	static boolean atLeisure(CompanionEntity c) {
		String job = job(c);
		return job != null && (job.startsWith("needs.") && !job.equals("needs.sleep") || job.startsWith(People.JOB_PREFIX)
			|| job.equals("common.idle"));
	}

	/** The id of the job in hand, or null. */
	static @Nullable String job(CompanionEntity c) {
		CompanionTask task = c.mode() == CompanionMode.WORK ? c.scheduler().current() : null;
		return task == null ? null : task.id();
	}

	/** Awake and not fighting or falling back. */
	static boolean awake(CompanionEntity c) {
		return c.isAlive() && !c.isAsleep() && c.getTarget() == null && !c.isRetreating();
	}

	/** Awake, at work in the camp's world, and not in a fight. */
	static boolean free(CompanionEntity c) {
		return awake(c) && c.mode() == CompanionMode.WORK;
	}

	/** A message about the camp's people for everyone, in a warm pink. */
	static void announce(MinecraftServer server, String text) {
		Speech.announce(server, Component.literal(text).withStyle(s -> s.withColor(ROMANCE_COLOUR)));
	}

	/** A plain gold message for everyone (births, growing up, weddings). */
	static void announceGold(MinecraftServer server, String text) {
		Speech.announce(server, Component.literal(text).withStyle(ChatFormatting.GOLD));
	}
}
