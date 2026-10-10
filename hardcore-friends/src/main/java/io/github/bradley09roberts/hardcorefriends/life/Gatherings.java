package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.civic.Professions;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * The village's gatherings, run once a second on the server: the three feasts of the year (at the square from the late
 * afternoon until nightfall) and the funerals (at the grave, the evening after a death). It announces the day's
 * occasions in the morning, starts a gathering at its hour, and steps it along: everyone gathers ({@link GatherTask}
 * brings them, children too), someone says a few words (Sage, or for a funeral the family, otherwise whoever has been
 * with the camp longest), and then a feast is a party (the cook hands out the food they brought from the stores, the
 * musician plays, everyone's fun and company fill, children play), while a funeral has its farewells and a moment of
 * quiet. A gathering ends at nightfall, or when the day it belongs to is over (the players slept through the night).
 * The Chronicle records each one. Only one gathering runs at a time; a funeral comes first.
 */
final class Gatherings {
	enum Kind {
		FEAST,
		FUNERAL
	}

	enum Phase {
		GATHERING,
		WORDS,
		PARTY,
		FAREWELLS
	}

	static final long FEAST_FROM = 9500;
	static final long FEAST_LATEST_START = 11500;
	static final long FUNERAL_FROM = 10000;
	static final long FUNERAL_LATEST_START = 11800;
	/** Every gathering is over by now (just after sunset) if night has not already ended it. */
	static final long ENDS_BY = 13000;
	/** How long the gathering waits for people before the words begin (or, with nobody there, gives up). */
	private static final int GATHER_TICKS_FEAST = 20 * 45;
	private static final int GATHER_TICKS_FUNERAL = 20 * 60;
	private static final int GATHER_MAX = 20 * 150;
	/** The share of those about who must be there before the words, if they come quickly. */
	private static final double QUORUM = 0.6;
	/** How near the centre an attendee must be for the feast's fun and food. */
	private static final double PARTY_RANGE = 12;
	private static final String FESTIVAL_UNITY = "festival";
	private static final String FUNERAL_UNITY = "funeral";
	/** A funeral that cannot be held after this many evenings is kept quietly instead. */
	private static final int FUNERAL_TRIES = 3;

	/** One gathering: what, where, whose, and how far it has got. */
	static final class Gathering {
		final Kind kind;
		final Calendar.@Nullable Feast feast;
		final LifeData.@Nullable Funeral funeral;
		final ResourceKey<Level> dimension;
		final BlockPos centre;
		/** For a funeral, the side the grave's sign faces (mourners stand there); null for a full ring. */
		final @Nullable Direction facing;
		final long day;
		final long startedAt;
		Phase phase = Phase.GATHERING;
		long phaseAt;
		int step;
		boolean spoke;
		@Nullable UUID speaker;
		final Set<UUID> arrived = new LinkedHashSet<>();
		final Set<UUID> attended = new LinkedHashSet<>();
		final List<String> attendedNames = new ArrayList<>();
		final Set<UUID> served = new HashSet<>();
		final Set<UUID> farewells = new HashSet<>();
		int nextSlot;

		Gathering(Kind kind, Calendar.@Nullable Feast feast, LifeData.@Nullable Funeral funeral, ServerLevel level, BlockPos centre,
				@Nullable Direction facing, long day) {
			this.kind = kind;
			this.feast = feast;
			this.funeral = funeral;
			this.dimension = level.dimension();
			this.centre = centre.immutable();
			this.facing = facing;
			this.day = day;
			this.startedAt = level.getGameTime();
			this.phaseAt = startedAt;
		}

		/** "the midsummer feast", or "Fern's funeral". */
		String title() {
			if (feast != null) {
				return feast.phrase();
			}
			return funeral == null ? "the funeral" : Chronicle.and(funeral.names) + "'s funeral";
		}
	}

	private static @Nullable Gathering active;
	/** Today's feast cook (chosen once a day) and the cooks who carry the feast's food (kept from putting it away). */
	private static long cookDay = -1;
	private static @Nullable UUID cook;
	private static final Set<UUID> FEAST_FOOD = new HashSet<>();

	private Gatherings() {
	}

	static void clear() {
		active = null;
		cookDay = -1;
		cook = null;
		FEAST_FOOD.clear();
	}

	/** The gathering under way, if any. */
	static @Nullable Gathering active() {
		return active;
	}

	/** The gathering under way in this friend's world, if they could take part. */
	static @Nullable Gathering activeFor(CompanionEntity c) {
		Gathering g = active;
		return g != null && c.level().dimension() == g.dimension ? g : null;
	}

	// ------------------------------------------------------------- the second

	/** Once a second: the morning's news, starting a gathering at its hour, and stepping one along. */
	static void second(MinecraftServer server) {
		ServerLevel level = Places.campLevel(server);
		CampData camp = Camp.data(server);
		if (level == null || camp.campPos().isEmpty()) {
			active = null;
			return;
		}
		LifeData data = LifeData.get(server);
		long day = Calendar.today(server);
		long time = Calendar.time(server);
		if (cookDay != day) {
			FEAST_FOOD.clear();
		}
		noteHarvest(server, data, day);
		morning(server, level, data, day, time);
		Gathering g = active;
		if (g != null) {
			if (g.day != day || g.dimension != level.dimension()) {
				finish(server, level, g, g.phase != Phase.GATHERING);
			} else {
				step(server, level, g, time);
			}
			return;
		}
		if (time >= FUNERAL_FROM && time < ENDS_BY) {
			startFuneral(server, level, data, day, time);
		}
		if (active == null && time >= FEAST_FROM && time < FEAST_LATEST_START) {
			startFeast(server, level, data, day);
		}
	}

	/** Remembers what the farms had harvested when autumn began, to judge the harvest festival by. */
	private static void noteHarvest(MinecraftServer server, LifeData data, long day) {
		long year = Calendar.year(day);
		if (Calendar.season(day) == 2 && data.harvestYear != year) {
			data.harvestYear = year;
			data.harvestBase = Camp.data(server).stat("crops_harvested");
			data.setDirty();
		}
	}

	/** True if the farms did well this autumn (or the stores are full anyway): a harvest festival is worth holding. */
	static boolean harvestGood(MinecraftServer server, LifeData data, long day) {
		if (CampNeeds.need(CampNeeds.Need.FOOD) <= 0.25) {
			return true;
		}
		if (data.harvestYear != Calendar.year(day) || data.harvestBase < 0) {
			return false;
		}
		long gathered = Camp.data(server).stat("crops_harvested") - data.harvestBase;
		return gathered >= Math.max(24, 3L * Companions.all().size());
	}

	/** The morning's news: today's feast or market, and any funeral this evening. Told once a day. */
	private static void morning(MinecraftServer server, ServerLevel level, LifeData data, long day, long time) {
		if (time < 500 || time >= 6000 || !data.markDone("announce:" + day)) {
			return;
		}
		FriendsConfig cfg = FriendsConfig.get();
		Calendar.Feast feast = Calendar.feastOn(day);
		if (feast != null && cfg.villageLife) {
			if (feast == Calendar.Feast.HARVEST && !harvestGood(server, data, day)) {
				data.markDone("feast:" + day);
				Speech.announce(server, Component.literal("No harvest festival this year: the harvest was too thin. Next year, perhaps!")
					.withStyle(ChatFormatting.GRAY));
			} else {
				Speech.announce(server, Component.literal("Today is " + feast.phrase() + "! Everyone gathers at the square from the late "
					+ "afternoon." + (feast == Calendar.Feast.WINTER_LIGHTS ? " Lights go up round it at dusk." : "")).withStyle(ChatFormatting.GOLD));
				CompanionEntity herald = herald(level);
				if (herald != null) {
					Speech.say(herald, Line.FESTIVAL_BEGINS, feast.phrase());
				}
			}
		}
		if (Calendar.marketDay(day) && cfg.villageLife && MarketDay.hasStalls(level)) {
			Speech.announce(server, Component.literal("It's market day! The village's stalls are open until the evening.")
				.withStyle(ChatFormatting.GOLD));
		}
		for (LifeData.Funeral f : data.funerals) {
			if (f.day <= day) {
				Speech.announce(server, Component.literal("This evening the village says goodbye to " + Chronicle.and(f.names)
					+ ", at the cemetery.").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** Someone awake about the camp to tell the news: Sage if she is there, otherwise anyone grown up. */
	private static @Nullable CompanionEntity herald(ServerLevel level) {
		CompanionEntity best = null;
		for (CompanionEntity c : Places.freePeople(level)) {
			if (c.isChild()) {
				continue;
			}
			if (c.friendId() == FriendId.SAGE && !c.isSettler()) {
				return c;
			}
			if (best == null) {
				best = c;
			}
		}
		return best;
	}

	// ------------------------------------------------------------- starting

	private static void startFuneral(MinecraftServer server, ServerLevel level, LifeData data, long day, long time) {
		LifeData.Funeral due = null;
		for (LifeData.Funeral f : data.funerals) {
			if (f.day <= day) {
				due = f;
				break;
			}
		}
		if (due == null) {
			return;
		}
		boolean canStart = time < FUNERAL_LATEST_START && !Camp.isNight(level) && !Places.freePeople(level).isEmpty();
		if (!canStart) {
			if (time >= FUNERAL_LATEST_START || Camp.isNightTime(level)) {
				putOff(server, data, due, day);
			}
			return;
		}
		LifeData.Grave grave = null;
		for (LifeData.Grave g : Graves.made(level, data)) {
			if (due.ids.contains(g.id)) {
				grave = g;
				break;
			}
		}
		BlockPos centre;
		Direction facing = null;
		if (grave != null && grave.pos != null) {
			centre = grave.pos;
			facing = Graves.facing(grave);
		} else if (data.cemetery != null && data.cemeteryDimension.equals(Camp.dimensionId(level)) && Places.inCamp(level, data.cemetery)) {
			centre = data.cemetery;
			facing = Direction.from2DDataValue(data.cemeteryFacing);
		} else {
			centre = Places.square(level);
		}
		if (centre == null || !level.isLoaded(centre)) {
			return;
		}
		active = new Gathering(Kind.FUNERAL, null, due, level, centre, facing, day);
	}

	/** A funeral that could not be held this evening moves to tomorrow; after a few evenings it is kept quietly. */
	private static void putOff(MinecraftServer server, LifeData data, LifeData.Funeral f, long day) {
		f.tries++;
		if (f.tries >= FUNERAL_TRIES) {
			data.funerals.remove(f);
			Chronicle.write(server, Chronicle.and(f.names) + (f.names.size() == 1 ? " was" : " were")
				+ " remembered quietly, as the village could not gather for a funeral.");
		} else {
			f.day = day + 1;
		}
		data.setDirty();
	}

	private static void startFeast(MinecraftServer server, ServerLevel level, LifeData data, long day) {
		Calendar.Feast feast = Calendar.feastOn(day);
		if (feast == null || !FriendsConfig.get().villageLife || data.isDone("feast:" + day) || Camp.isNight(level)) {
			return;
		}
		if (feast == Calendar.Feast.HARVEST && !harvestGood(server, data, day)) {
			data.markDone("feast:" + day);
			return;
		}
		BlockPos centre = Places.square(level);
		if (centre == null || !level.isLoaded(centre) || Places.freePeople(level).isEmpty()) {
			return;
		}
		data.markDone("feast:" + day);
		active = new Gathering(Kind.FEAST, feast, null, level, centre, null, day);
	}

	// ------------------------------------------------------------- stepping

	private static void step(MinecraftServer server, ServerLevel level, Gathering g, long time) {
		long now = level.getGameTime();
		if (Camp.isNightTime(level) || time >= ENDS_BY) {
			finish(server, level, g, g.phase != Phase.GATHERING);
			return;
		}
		g.arrived.removeIf(id -> Places.loaded(level, id) == null);
		boolean danger = Places.hostileNear(level, g.centre, Places.SPOIL_RANGE);
		switch (g.phase) {
			case GATHERING -> {
				int about = Places.freePeople(level).size();
				int present = g.arrived.size();
				long waited = now - g.startedAt;
				int wait = g.kind == Kind.FUNERAL ? GATHER_TICKS_FUNERAL : GATHER_TICKS_FEAST;
				boolean enough = present > 0 && (present >= Math.ceil(QUORUM * about) || waited >= wait);
				if (enough && !danger) {
					words(level, g);
				} else if (waited > GATHER_MAX && present == 0) {
					finish(server, level, g, false);
				}
			}
			case WORDS -> {
				if (now - g.phaseAt >= 100) {
					g.phase = g.kind == Kind.FEAST ? Phase.PARTY : Phase.FAREWELLS;
					g.phaseAt = now;
				}
			}
			case PARTY -> {
				if (!danger) {
					party(level, g, now);
				}
			}
			case FAREWELLS -> farewells(server, level, g, now);
		}
	}

	/** The few words: the speaker is the first suitable person there. */
	private static void words(ServerLevel level, Gathering g) {
		CompanionEntity speaker = chooseSpeaker(level, g);
		g.phase = Phase.WORDS;
		g.phaseAt = level.getGameTime();
		g.spoke = true;
		if (speaker == null) {
			return;
		}
		g.speaker = speaker.getUUID();
		if (g.kind == Kind.FEAST && g.feast != null) {
			Speech.say(speaker, Line.FESTIVAL_SPEECH, g.feast.phrase());
		} else if (g.funeral != null) {
			Speech.say(speaker, Line.FUNERAL_WORDS, Chronicle.and(g.funeral.names));
		}
	}

	/**
	 * Who speaks: Sage if she is there; at a funeral, then family of the one lost; then whoever of those there has been
	 * with the camp longest. Children listen.
	 */
	private static @Nullable CompanionEntity chooseSpeaker(ServerLevel level, Gathering g) {
		LifeData data = LifeData.get(level.getServer());
		List<CompanionEntity> there = new ArrayList<>();
		for (UUID id : g.arrived) {
			CompanionEntity c = Places.loaded(level, id);
			if (c != null && !c.isChild()) {
				there.add(c);
			}
		}
		for (CompanionEntity c : there) {
			if (c.friendId() == FriendId.SAGE && !c.isSettler()) {
				return c;
			}
		}
		if (g.funeral != null) {
			for (LifeData.Grave grave : data.graves) {
				if (g.funeral.ids.contains(grave.id)) {
					for (CompanionEntity c : there) {
						if (grave.family.contains(c.getUUID())) {
							return c;
						}
					}
				}
			}
		}
		there.sort(Comparator.comparingLong((CompanionEntity c) -> data.person(c.getUUID()).map(p -> p.joined).orElse(Long.MAX_VALUE))
			.thenComparingInt(CompanionEntity::rosterIndex));
		return there.isEmpty() ? null : there.getFirst();
	}

	/** A second of the feast: fun and company for everyone there, food handed round, a cheer, a few hearts. */
	private static void party(ServerLevel level, Gathering g, long now) {
		List<CompanionEntity> there = new ArrayList<>();
		for (UUID id : g.arrived) {
			CompanionEntity c = Places.loaded(level, id);
			if (c != null && c.distanceToSqr(g.centre.getX() + 0.5, g.centre.getY(), g.centre.getZ() + 0.5) <= PARTY_RANGE * PARTY_RANGE) {
				there.add(c);
			}
		}
		if (there.isEmpty()) {
			return;
		}
		for (CompanionEntity c : there) {
			c.needs().add(Needs.Need.FUN, 0.5);
			c.needs().add(Needs.Need.SOCIAL, 0.4);
		}
		serve(level, g, there);
		long since = now - g.phaseAt;
		CompanionEntity someone = there.get(level.getRandom().nextInt(there.size()));
		if (since % 100 < 20) {
			level.sendParticles(ParticleTypes.HEART, someone.getX(), someone.getY() + someone.getBbHeight() + 0.3, someone.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		}
		if (since % 160 < 20 && since > 60) {
			Speech.say(someone, Line.FESTIVAL_CHEER);
		}
	}

	/**
	 * Hands out one portion a second: the cook's food (brought from the stores for the feast) to someone there who is
	 * hungry and has not been served; with no cook's food, the hungry eat what they carry. Real food, eaten.
	 */
	private static void serve(ServerLevel level, Gathering g, List<CompanionEntity> there) {
		CompanionEntity host = Places.loaded(level, cook);
		boolean cookHere = host != null && there.contains(host) && host.hasFood();
		for (CompanionEntity c : there) {
			if (g.served.contains(c.getUUID()) || c.needs().get(Needs.Need.HUNGER) >= 85) {
				continue;
			}
			ItemStack food = ItemStack.EMPTY;
			if (cookHere) {
				food = host.backpack().take(CompanionEntity::isEdible, 1);
				if (!food.isEmpty() && c != host) {
					host.swingArm();
					Speech.say(host, Line.FEAST_SERVING);
				}
			}
			if (food.isEmpty() && c.needs().get(Needs.Need.HUNGER) < 75) {
				food = c.backpack().take(CompanionEntity::isEdible, 1);
			}
			if (food.isEmpty()) {
				continue;
			}
			g.served.add(c.getUUID());
			c.eat(food);
			return;
		}
	}

	/** A funeral's farewells, one every few seconds, a promise to the children left behind, then quiet. */
	private static void farewells(MinecraftServer server, ServerLevel level, Gathering g, long now) {
		if (now - g.phaseAt < 80 || g.funeral == null) {
			return;
		}
		g.phaseAt = now;
		g.step++;
		for (UUID id : g.arrived) {
			CompanionEntity c = Places.loaded(level, id);
			if (c != null) {
				c.needs().add(Needs.Need.SOCIAL, 1.5); // grief shared is lighter
			}
		}
		if (g.step <= 3) {
			for (UUID id : g.arrived) {
				CompanionEntity c = Places.loaded(level, id);
				if (c != null && !c.isChild() && !id.equals(g.speaker) && g.farewells.add(id)) {
					Speech.say(c, Line.FUNERAL_FAREWELL, g.funeral.names.get((g.step - 1) % g.funeral.names.size()));
					return;
				}
			}
			return;
		}
		if (g.step == 4) {
			care(level, g);
			return;
		}
		if (g.step >= 6) {
			finish(server, level, g, true);
		}
	}

	/** For a parent lost: the other parent (or the speaker) promises the child they will be looked after. */
	private static void care(ServerLevel level, Gathering g) {
		LifeData data = LifeData.get(level.getServer());
		if (g.funeral == null) {
			return;
		}
		for (UUID dead : g.funeral.ids) {
			LifeData.Person p = data.person(dead).orElse(null);
			if (p == null) {
				continue;
			}
			for (UUID childId : p.children) {
				CompanionEntity child = Places.loaded(level, childId);
				if (child == null || !child.isChild()) {
					continue;
				}
				CompanionEntity carer = Places.loaded(level, p.partner);
				if (carer == null || !g.arrived.contains(carer.getUUID())) {
					carer = Places.loaded(level, g.speaker);
				}
				if (carer != null) {
					Speech.say(carer, Line.FUNERAL_CARE, child.displayName());
				}
				return;
			}
		}
	}

	// ------------------------------------------------------------- ending

	/** The gathering is over: the last words, the news, the Chronicle, the bond. {@code held} if it got as far as words. */
	private static void finish(MinecraftServer server, ServerLevel level, Gathering g, boolean held) {
		active = null;
		LifeData data = LifeData.get(server);
		int count = g.attended.size();
		String who = who(g);
		if (g.kind == Kind.FEAST && g.feast != null) {
			FEAST_FOOD.clear();
			if (!held) {
				return;
			}
			CompanionEntity speaker = Places.loaded(level, g.speaker);
			if (speaker != null) {
				Speech.say(speaker, Line.FESTIVAL_END, g.feast.phrase());
			}
			Speech.announce(server, Component.literal(Calendar.capital(g.feast.phrase()) + " is over: " + count
				+ " came to the square.").withStyle(ChatFormatting.GOLD));
			Chronicle.write(server, Calendar.capital(g.feast.phrase()) + " was held at the square" + (who.isEmpty() ? "." : ": " + who + " came."));
			Unity.add(level, FESTIVAL_UNITY, 10, 10);
			return;
		}
		LifeData.Funeral f = g.funeral;
		if (f == null) {
			return;
		}
		if (!held) {
			putOff(server, data, f, g.day);
			return;
		}
		data.funerals.remove(f);
		data.setDirty();
		String names = Chronicle.and(f.names);
		Speech.announce(server, Component.literal("The village has said goodbye to " + names + ".").withStyle(ChatFormatting.GRAY));
		Chronicle.write(server, "The village said goodbye to " + names + (data.cemetery != null ? " at the cemetery" : "")
			+ (count > 0 ? "; " + count + " came to remember." : "."));
		Unity.add(level, FUNERAL_UNITY, 5, 10);
	}

	/** "Fern, Oak and 6 others", the first three who came and how many more. */
	private static String who(Gathering g) {
		List<String> names = g.attendedNames;
		if (names.isEmpty()) {
			return "";
		}
		if (names.size() <= 3) {
			return Chronicle.and(names);
		}
		return String.join(", ", names.subList(0, 3)) + " and " + (names.size() - 3) + " others";
	}

	// ------------------------------------------------------------- the people

	/** A friend has reached their place at the gathering. */
	static void arrive(Gathering g, CompanionEntity c) {
		g.arrived.add(c.getUUID());
		if (g.attended.add(c.getUUID())) {
			g.attendedNames.add(c.displayName());
		}
	}

	/** A friend has left the gathering (called away, or it ended). */
	static void leave(CompanionEntity c) {
		Gathering g = active;
		if (g != null) {
			g.arrived.remove(c.getUUID());
		}
	}

	/** The next place in the ring for someone joining. */
	static int slot(Gathering g) {
		return g.nextSlot++;
	}

	// ------------------------------------------------------------- the cook

	/**
	 * Today's feast cook: the innkeeper (or the camp's cook), else the farmer, else whoever has been with the camp
	 * longest. Chosen once a day among those about.
	 */
	static @Nullable UUID cookFor(ServerLevel level, long day) {
		if (cookDay == day && cook != null && Places.loaded(level, cook) != null) {
			return cook;
		}
		LifeData data = LifeData.get(level.getServer());
		CompanionEntity best = null;
		int bestRank = Integer.MAX_VALUE;
		for (CompanionEntity c : Places.freePeople(level)) {
			if (c.isChild()) {
				continue;
			}
			int rank;
			if (Professions.get().professionOf(c).map("innkeeper"::equals).orElse(false)) {
				rank = 0;
			} else if (c.friendId().role() == Role.FARMER) {
				rank = c.isSettler() ? 2 : 1;
			} else {
				rank = 3;
			}
			if (rank < bestRank || rank == bestRank && best != null && joined(data, c) < joined(data, best)) {
				best = c;
				bestRank = rank;
			}
		}
		cookDay = day;
		cook = best == null ? null : best.getUUID();
		return cook;
	}

	private static long joined(LifeData data, CompanionEntity c) {
		return data.person(c.getUUID()).map(p -> p.joined).orElse(Long.MAX_VALUE);
	}

	/** The cook now carries food for the feast: it stays with them (not put away) until the feast is over. */
	static void carryingFeastFood(CompanionEntity c) {
		FEAST_FOOD.add(c.getUUID());
	}

	/** The job filter: a cook carrying the feast's food does not put it back in the chest before the feast. */
	static boolean mayDo(CompanionEntity c, String jobId) {
		return !"common.deposit".equals(jobId) || FEAST_FOOD.isEmpty() || !FEAST_FOOD.contains(c.getUUID());
	}
}
