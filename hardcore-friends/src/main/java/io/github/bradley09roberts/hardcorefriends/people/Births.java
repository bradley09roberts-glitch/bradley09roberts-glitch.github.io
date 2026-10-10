package io.github.bradley09roberts.hardcorefriends.people;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;

import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Persona;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModEntities;
import io.github.bradley09roberts.hardcorefriends.settler.SettlerData;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Babies. Once a minute each married couple who could start a family has a small chance (about once in fifteen
 * minutes of being able to) of finding they are expecting; the baby arrives about an in-game day later, beside a
 * parent at the camp and up at ground level there (never down the mine), as a child of the camp ({@link Children}).
 *
 * <p>A couple can have a baby when ({@link #whyNot} says which is missing): children are switched on; both are
 * alive, grown up and at the camp together; they married at least a day ago; their youngest is at least
 * {@code daysBetweenChildren} days old and they have fewer than {@value #MOST_CHILDREN} children; neither is mourning a
 * child; the team is under {@code maxPopulation}; the camp has food in store (its food need at most
 * {@value #FOOD_NEED_MAX}); both are at least in an okay mood; and there is room at home: a free bed in their village
 * home ({@code Homes.roomForOneMore}), or, while the village has no homes at all, a spare place in the camp's cabins (nine
 * to a cabin) for everyone on the team.
 */
final class Births {
	/** The most children one couple has. */
	static final int MOST_CHILDREN = 4;
	/** The camp's food need (0 plenty to 1 none) above which no baby is planned. */
	static final double FOOD_NEED_MAX = 0.4;
	/** A parent at most this many blocks above or below the camp centre is about level with it, for a baby to arrive. */
	private static final int GROUND_LEVEL = 6;
	/** Sleeping places in a cabin (the sleep job's slots). */
	static final int CABIN_BEDS = 9;
	/** How long a baby is expected before arriving (overworld clock ticks). */
	static final long EXPECTING = 24000L;
	/** The chance a minute that a couple who can have a baby find they are expecting. */
	private static final double CHANCE = 1.0 / 15.0;
	/** Soft name colours for children. */
	private static final int[] COLOURS = {
		0xF7C59F, 0xA0E7E5, 0xFFD6E0, 0xB4F8C8, 0xFBE7C6, 0xC3B1E1, 0xFFDAC1, 0xB5EAD7, 0xE2F0CB, 0xFFB7B2};

	private Births() {
	}

	/** Once a minute: babies on the way arrive when due; couples who can may find they are expecting. */
	static void minute(MinecraftServer server) {
		PeopleData data = PeopleData.get(server);
		long clock = PeopleEvents.clock(server);
		for (PeopleData.Bond bond : new ArrayList<>(data.bonds())) {
			if (bond.status != PeopleData.Status.MARRIED) {
				continue;
			}
			PeopleData.Person pa = data.person(bond.a).orElse(null);
			PeopleData.Person pb = data.person(bond.b).orElse(null);
			if (pa == null || pb == null || !pa.alive() || !pb.alive()) {
				if (bond.babyDue >= 0) {
					bond.babyDue = -1;
					data.setDirty();
				}
				continue;
			}
			if (bond.babyDue >= 0) {
				if (clock >= bond.babyDue) {
					deliver(server, data, bond, pa, pb);
				}
				continue;
			}
			CompanionEntity a = PeopleEvents.loaded(server, bond.a);
			CompanionEntity b = PeopleEvents.loaded(server, bond.b);
			if (a != null && b != null && whyNot(server, data, bond, a, b) == null && a.getRandom().nextDouble() < CHANCE) {
				expect(server, data, bond, a, b, clock);
			}
		}
	}

	/**
	 * Null if this married couple could find they are expecting now; otherwise what stands in the way, in a few plain
	 * words for {@code /friends couples}.
	 */
	static @Nullable String whyNot(MinecraftServer server, PeopleData data, PeopleData.Bond bond, CompanionEntity a, CompanionEntity b) {
		FriendsConfig cfg = FriendsConfig.get();
		if (!cfg.children) {
			return "children are switched off in the settings";
		}
		if (bond.babyDue >= 0) {
			return "a baby is already on the way";
		}
		if (a.isChild() || b.isChild() || !a.isAlive() || !b.isAlive()) {
			return "not now";
		}
		if (!atCamp(a) || !atCamp(b)) {
			return "they need to be home at the camp together";
		}
		long clock = PeopleEvents.clock(server);
		long day = clock / 24000L;
		if (clock - bond.since < 24000L) {
			return "they have only just married";
		}
		if (bond.lastBabyDay >= 0 && day - bond.lastBabyDay < cfg.daysBetweenChildren) {
			return "their youngest is still very little";
		}
		if (childrenOf(data, bond).size() >= MOST_CHILDREN) {
			return "they have a big family already";
		}
		if (mourning(data, bond.a, clock) || mourning(data, bond.b, clock)) {
			return "they are mourning";
		}
		int population = population(server, data);
		if (population >= cfg.maxPopulation) {
			return "the team is full (" + population + " of " + cfg.maxPopulation + " people)";
		}
		if (CampNeeds.need(CampNeeds.Need.FOOD) > FOOD_NEED_MAX) {
			return "the camp needs more food in store";
		}
		if (a.needs().mood().ordinal() < Needs.Mood.OKAY.ordinal() || b.needs().mood().ordinal() < Needs.Mood.OKAY.ordinal()) {
			return "they are not happy enough right now";
		}
		Homes.Provider homes = Homes.get();
		if (!homes.homes(server).isEmpty()) {
			return homes.roomForOneMore(server, List.of(bond.a, bond.b)) ? null : "there is no free bed in their home";
		}
		return population < cabinBeds(server) ? null : "the camp needs a home with a spare bed (the cabin sleeps nine)";
	}

	private static boolean mourning(PeopleData data, UUID id, long clock) {
		return data.person(id).map(p -> p.mournUntil > clock).orElse(false);
	}

	/** In the camp's world, inside the camp, at work there (not following anyone off). */
	private static boolean atCamp(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.WORK) {
			return false;
		}
		CampData camp = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, camp) || camp.campPos().isEmpty()) {
			return false;
		}
		int r = Camp.radius(camp) + 8;
		return Camp.horizontalDistSqr(c.blockPosition(), camp.campPos().get()) <= (double) r * r;
	}

	/**
	 * At the camp and up at ground level there, about level with the camp centre and near the surface: where a baby
	 * may arrive beside them. Never down the camp mine or in a cave under the camp, which lie inside the camp's circle
	 * by distance alone ({@link #atCamp} says nothing of height).
	 */
	private static boolean atCampAboveGround(CompanionEntity c) {
		if (!atCamp(c) || !(c.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos here = c.blockPosition();
		Optional<BlockPos> centre = Camp.data(level.getServer()).campPos();
		return centre.isPresent() && Math.abs(here.getY() - centre.get().getY()) <= GROUND_LEVEL && Children.nearSurface(level, here);
	}

	/** The couple's living children together. */
	private static List<PeopleData.Person> childrenOf(PeopleData data, PeopleData.Bond bond) {
		List<PeopleData.Person> list = new ArrayList<>();
		for (PeopleData.Person p : data.childrenOf(bond.a)) {
			if (p.alive() && p.parents.contains(bond.b)) {
				list.add(p);
			}
		}
		return list;
	}

	/**
	 * Everyone on the team, loaded or not: the named friends alive, the newcomers alive (those born here and grown up
	 * among them), the children, and the babies on the way.
	 */
	static int population(MinecraftServer server, PeopleData data) {
		int n = 0;
		CampData camp = Camp.data(server);
		for (FriendId id : FriendId.values()) {
			if (camp.ledger(id).state == CampData.LifeState.ALIVE) {
				n++;
			}
		}
		for (SettlerData.Newcomer newcomer : SettlerData.get(server).newcomers()) {
			if (newcomer.state == SettlerData.State.ALIVE) {
				n++;
			}
		}
		for (PeopleData.Person p : data.people()) {
			if (p.alive() && p.child) {
				n++;
			}
		}
		for (PeopleData.Bond bond : data.bonds()) {
			if (bond.babyDue >= 0) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Places to sleep in the camp's finished cabin, for the reckoning before the village has homes. Only the first cabin
	 * counts: it is the one the sleep job lays everyone down in (its nine places round the resting spot); nobody sleeps
	 * in the second cabin, so it makes no room for a baby.
	 */
	private static int cabinBeds(MinecraftServer server) {
		return Camp.data(server).isCompleted(Structures.CABIN) ? CABIN_BEDS : 0;
	}

	private static void expect(MinecraftServer server, PeopleData data, PeopleData.Bond bond, CompanionEntity a, CompanionEntity b,
			long clock) {
		bond.babyDue = clock + EXPECTING;
		data.setDirty();
		CompanionEntity speaker = a.getRandom().nextBoolean() ? a : b;
		Speech.say(speaker, Line.BABY_NEWS, (speaker == a ? b : a).displayName());
		Relationships.announceGold(server, a.displayName() + " and " + b.displayName()
			+ " are expecting a baby! The little one should arrive in about a day.");
	}

	/**
	 * The baby arrives beside a parent who is at the camp (if neither is, it waits for one to come home): a child of the
	 * camp with a name of their own, the family name, a child's skin and a trade from a parent's work or interest.
	 */
	private static void deliver(MinecraftServer server, PeopleData data, PeopleData.Bond bond, PeopleData.Person pa, PeopleData.Person pb) {
		CompanionEntity parent = PeopleEvents.loaded(server, bond.a);
		if (parent == null || !atCampAboveGround(parent)) {
			parent = PeopleEvents.loaded(server, bond.b);
		}
		if (parent == null || !atCampAboveGround(parent) || !(parent.level() instanceof ServerLevel level)) {
			return; // nobody home yet (or only down the mine): the baby arrives when a parent is back up at the camp
		}
		BlockPos spot = besideParent(level, parent);
		CompanionEntity child = spot == null ? null : ModEntities.COMPANION.create(level, EntitySpawnReason.BREEDING);
		if (child == null) {
			return; // try again next minute
		}
		RandomSource random = parent.getRandom();
		Set<String> taken = new HashSet<>(data.firstNamesInUse());
		taken.addAll(SettlerData.get(server).namesInUse(null));
		String name = Names.baby(random, taken);
		String family = !pa.family.isEmpty() ? pa.family : pb.family;
		FriendId archetype = archetype(random, pa.archetype, pb.archetype);
		int colour = COLOURS[random.nextInt(COLOURS.length)];
		// A baby looks different from the other children about, where the skin list allows.
		child.setPersona(new Persona(name, colour, Skins.randomFor(random, true, null, List.of(), Skins.wornNow()), archetype));
		child.setChild(true);
		child.setMode(CompanionMode.WORK, null);
		child.setHomePos(Camp.center(level).orElse(parent.blockPosition()));
		child.backpack().setCapacity(Unity.backpackSlots(server));
		child.setHealth(child.getMaxHealth());
		child.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, parent.getYRot() + 180.0F, 0.0F);
		if (!level.addFreshEntity(child)) {
			return;
		}
		Companions.track(child);
		long day = PeopleEvents.day(server);
		PeopleData.Person kid = data.addBaby(child.getUUID(), name, family, archetype, colour, List.of(parent.getUUID(), bond.other(parent.getUUID())), day);
		bond.babyDue = -1;
		bond.lastBabyDay = day;
		// Family start out close: parents and child, brothers and sisters. Born friends, so nobody announces it later.
		for (UUID p : kid.parents) {
			PeopleData.Bond tie = data.bond(kid.id, p);
			tie.friendship = 70;
			tie.friendsSaid = true;
		}
		for (PeopleData.Person sibling : childrenOf(data, bond)) {
			if (!sibling.id.equals(kid.id)) {
				PeopleData.Bond tie = data.bond(kid.id, sibling.id);
				tie.friendship = 50;
				tie.friendsSaid = true;
			}
		}
		data.setDirty();
		Homes.get().moveIn(server, kid.id, data.household(parent.getUUID()));
		Speech.say(parent, Line.BABY_ARRIVED, name);
		PeopleEvents.later(server, 60, () -> {
			if (child.isAlive()) {
				Speech.say(child, Line.CHILD_FIRST_WORDS);
			}
		});
		Relationships.announceGold(server, "Welcome, little " + kid.fullName() + "! " + pa.name + " and " + pb.name
			+ "'s baby has arrived at the camp.");
		Unity.add(level, Children.UNITY_FAMILY, 10, 30);
		Camp.data(server).addStat("babies_born", 1);
	}

	/** A place to stand right beside the parent; null if there is none. */
	private static @Nullable BlockPos besideParent(ServerLevel level, CompanionEntity parent) {
		BlockPos here = parent.blockPosition();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos spot = Spots.standable(level, here.relative(d));
			if (spot != null && Math.abs(spot.getY() - here.getY()) <= 1) {
				return spot;
			}
		}
		return Spots.isStandable(level, here) ? here : null;
	}

	/** A trade from a parent: their own work, or the work they are keen on besides. */
	private static FriendId archetype(RandomSource random, FriendId a, FriendId b) {
		FriendId[] choices = {a, b, byRole(Speciality.interest(a)), byRole(Speciality.interest(b))};
		return choices[random.nextInt(choices.length)];
	}

	private static FriendId byRole(Role role) {
		for (FriendId id : FriendId.values()) {
			if (id.role() == role) {
				return id;
			}
		}
		return FriendId.ROWAN;
	}
}
