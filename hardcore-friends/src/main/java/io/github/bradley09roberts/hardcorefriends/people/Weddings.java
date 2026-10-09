package io.github.bradley09roberts.hardcorefriends.people;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Weddings. An engaged couple marry on their wedding day, in the late morning (time of day 5000 to 10000), at the town
 * hall once the village has built one, otherwise at the camp centre: when both are at the camp and nothing hostile is
 * about, the wedding begins ({@link #second}), everyone free at the camp gathers round in a ring
 * ({@link WeddingTask}), the couple say their vows, they are married and take one family name, a guest raises a toast,
 * the bell rings and Unity rises. A wedding the couple cannot get to in two minutes, or that a monster or nightfall
 * interrupts, is put off until the next day.
 *
 * <p>One wedding at a time. The ceremony lives in memory only: a server stopping mid-wedding simply starts it again
 * the next morning, since the couple are still engaged in the saved records.
 */
final class Weddings {
	/** The wedding morning: from this time of day... */
	static final long WINDOW_START = 5000;
	/** ...to this one. */
	static final long WINDOW_END = 10000;
	/** Guests stand in a ring this far from the couple. */
	static final double RING = 4;
	/** After this long, the vows begin with whoever is there if the couple are near the spot. */
	private static final int GATHER_TICKS = 20 * 50;
	/** The couple must reach the spot within this long, or the wedding is put off. */
	private static final int GATHER_MAX = 20 * 120;
	/** Unity for a wedding (category "wedding"). */
	private static final String UNITY_WEDDING = "wedding";

	/** The wedding under way: the couple, where, and how far along it is. */
	static final class Ceremony {
		final UUID a;
		final UUID b;
		final ResourceKey<Level> dimension;
		final BlockPos venue;
		final long started;
		long vowsAt = -1;
		int step;
		boolean married;

		Ceremony(UUID a, UUID b, ResourceKey<Level> dimension, BlockPos venue, long started) {
			this.a = a;
			this.b = b;
			this.dimension = dimension;
			this.venue = venue;
			this.started = started;
		}

		boolean isCouple(UUID id) {
			return a.equals(id) || b.equals(id);
		}

		/** Where one of the couple stands: either side of the venue, facing each other. */
		BlockPos spotFor(UUID id) {
			return venue.offset(a.equals(id) ? -1 : 1, 0, 0);
		}
	}

	private static @Nullable Ceremony active;

	private Weddings() {
	}

	/** The wedding going on now, if any. */
	static @Nullable Ceremony active() {
		return active;
	}

	static boolean involves(UUID id) {
		Ceremony w = active;
		return w != null && w.isCouple(id);
	}

	static void clear() {
		active = null;
	}

	/** Every second: start a wedding that is due, or move the one under way along. */
	static void second(MinecraftServer server) {
		Ceremony w = active;
		if (w == null) {
			if (server.getTickCount() % 100 == 3) {
				maybeStart(server);
			}
			return;
		}
		run(server, w);
	}

	private static void maybeStart(MinecraftServer server) {
		PeopleData data = PeopleData.get(server);
		long day = PeopleEvents.day(server);
		for (PeopleData.Bond bond : data.bonds()) {
			if (bond.status != PeopleData.Status.ENGAGED || bond.weddingDay < 0 || bond.weddingDay > day) {
				continue;
			}
			CompanionEntity a = PeopleEvents.loaded(server, bond.a);
			CompanionEntity b = PeopleEvents.loaded(server, bond.b);
			if (a == null || b == null || a.level() != b.level() || !(a.level() instanceof ServerLevel level)) {
				continue;
			}
			long time = Camp.timeOfDay(level);
			CampData camp = Camp.data(server);
			if (time < WINDOW_START || time >= WINDOW_END || Camp.isNight(level) || !Camp.isCampLevel(level, camp)
				|| camp.campPos().isEmpty()) {
				continue;
			}
			BlockPos centre = camp.campPos().get();
			int reach = Camp.radius(camp) + 16;
			if (Camp.horizontalDistSqr(a.blockPosition(), centre) > (double) reach * reach
				|| Camp.horizontalDistSqr(b.blockPosition(), centre) > (double) reach * reach
				|| !Relationships.free(a) || !Relationships.free(b)) {
				continue;
			}
			BlockPos venue = venue(level, camp);
			if (venue == null || hostileNear(level, venue)) {
				continue;
			}
			active = new Ceremony(bond.a, bond.b, level.dimension(), venue, level.getGameTime());
			Relationships.announce(server, "The wedding of " + a.displayName() + " and " + b.displayName()
				+ " is starting at the camp! Everyone is gathering round.");
			return;
		}
	}

	private static void run(MinecraftServer server, Ceremony w) {
		ServerLevel level = server.getLevel(w.dimension);
		CompanionEntity a = PeopleEvents.loaded(server, w.a);
		CompanionEntity b = PeopleEvents.loaded(server, w.b);
		if (level == null || a == null || b == null || a.level() != level || b.level() != level) {
			postpone(server, w, "the couple are not both here");
			return;
		}
		long now = level.getGameTime();
		if (!w.married && (Camp.isNight(level) || hostileNear(level, w.venue))) {
			postpone(server, w, Camp.isNight(level) ? "it got too late" : "a monster came too close");
			return;
		}
		if (w.vowsAt < 0) {
			long waited = now - w.started;
			boolean there = a.blockPosition().closerThan(w.spotFor(w.a), 2.5) && b.blockPosition().closerThan(w.spotFor(w.b), 2.5);
			boolean near = a.blockPosition().closerThan(w.venue, 8) && b.blockPosition().closerThan(w.venue, 8);
			if (there || waited > GATHER_TICKS && near) {
				w.vowsAt = now;
			} else if (waited > GATHER_MAX) {
				postpone(server, w, "the couple could not get there in time");
			}
			return;
		}
		long t = now - w.vowsAt;
		if (w.step == 0) {
			w.step = 1;
			Speech.say(a, Line.WEDDING_VOWS, b.displayName());
		} else if (w.step == 1 && t >= 60) {
			w.step = 2;
			Speech.say(b, Line.WEDDING_VOWS, a.displayName());
		} else if (w.step == 2 && t >= 120) {
			w.step = 3;
			marry(server, level, w, a, b);
		} else if (w.step == 3 && t >= 170) {
			w.step = 4;
			CompanionEntity guest = guest(level, w);
			if (guest != null) {
				Speech.say(guest, Line.WEDDING_TOAST, a.displayName() + " and " + b.displayName());
			}
		} else if (w.step == 4 && t >= 300) {
			active = null; // the guests go back to their day
		}
		if (w.married && t % 20 == 0) {
			hearts(level, a);
			hearts(level, b);
		}
	}

	/** They are married: one family name for both, a home together, the bell, Unity and the news for everyone. */
	private static void marry(MinecraftServer server, ServerLevel level, Ceremony w, CompanionEntity a, CompanionEntity b) {
		PeopleData data = PeopleData.get(server);
		PeopleData.Bond bond = data.bond(w.a, w.b);
		PeopleData.Person pa = data.personFor(a, a.getRandom());
		PeopleData.Person pb = data.personFor(b, b.getRandom());
		bond.status = PeopleData.Status.MARRIED;
		bond.since = PeopleEvents.clock(server);
		bond.weddingDay = -1;
		pa.partner = pb.id;
		pb.partner = pa.id;
		String family = a.getRandom().nextBoolean() ? pa.family : pb.family;
		if (family.isEmpty()) {
			family = pa.family.isEmpty() ? pb.family : pa.family;
		}
		pa.family = family;
		pb.family = family;
		data.setDirty();
		w.married = true;
		// They live together now: the village gives the household one home (the people of b's household move in with a).
		Set<UUID> household = data.household(pa.id);
		for (UUID member : household) {
			if (!member.equals(pa.id)) {
				Homes.get().moveIn(server, member, household);
			}
		}
		level.playSound(null, w.venue, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 1.0F);
		Unity.add(level, UNITY_WEDDING, 30, 60);
		Camp.data(server).addStat("weddings", 1);
		Relationships.announce(server, a.displayName() + " and " + b.displayName() + " are married!"
			+ (family.isEmpty() ? "" : " From today they are the " + family + " family."));
	}

	private static void postpone(MinecraftServer server, Ceremony w, String why) {
		active = null;
		if (w.married) {
			return; // already married: only the party ends early
		}
		PeopleData data = PeopleData.get(server);
		PeopleData.Bond bond = data.bondIf(w.a, w.b);
		if (bond == null || bond.status != PeopleData.Status.ENGAGED) {
			return;
		}
		bond.weddingDay = PeopleEvents.day(server) + 1;
		data.setDirty();
		String names = data.person(w.a).map(p -> p.name).orElse("Someone") + " and " + data.person(w.b).map(p -> p.name).orElse("someone");
		Relationships.announce(server, "The wedding of " + names + " is put off until tomorrow: " + why + ".");
	}

	/** A guest near the couple to raise the toast: anyone at the wedding but the couple. */
	private static @Nullable CompanionEntity guest(ServerLevel level, Ceremony w) {
		List<CompanionEntity> guests = new ArrayList<>();
		for (CompanionEntity c : Companions.in(level)) {
			if (!w.isCouple(c.getUUID()) && c.blockPosition().closerThan(w.venue, RING + 4) && !c.isAsleep()) {
				guests.add(c);
			}
		}
		return guests.isEmpty() ? null : guests.get(level.getRandom().nextInt(guests.size()));
	}

	private static boolean hostileNear(ServerLevel level, BlockPos venue) {
		return Threats.archerNear(level, venue, 12) || !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
			new net.minecraft.world.phys.AABB(venue).inflate(12, 6, 12), Threats::isThreat).isEmpty();
	}

	private static void hearts(ServerLevel level, CompanionEntity c) {
		level.sendParticles(ParticleTypes.HEART, c.getX(), c.getY() + c.getBbHeight() + 0.3, c.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
	}

	/**
	 * Where weddings happen: the village's town hall once one is finished (a camp site whose id names a town hall),
	 * otherwise a standable spot near the camp centre, clear of the campfire.
	 */
	static @Nullable BlockPos venue(ServerLevel level, CampData camp) {
		for (Map.Entry<String, CampData.Site> site : camp.sites().entrySet()) {
			if (site.getKey().contains("town_hall") && camp.isCompleted(site.getKey())) {
				BlockPos spot = clearSpot(level, site.getValue().origin);
				if (spot != null) {
					return spot;
				}
			}
		}
		return camp.campPos().map(centre -> clearSpot(level, centre)).orElse(null);
	}

	/** A standable spot two to five blocks from {@code around} with room either side for the couple. */
	private static @Nullable BlockPos clearSpot(ServerLevel level, BlockPos around) {
		for (int r = 2; r <= 5; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos spot = Spots.standable(level, around.offset(dx, 0, dz));
					if (spot != null && Spots.isStandable(level, spot.west()) && Spots.isStandable(level, spot.east())) {
						return spot;
					}
				}
			}
		}
		return null;
	}
}
