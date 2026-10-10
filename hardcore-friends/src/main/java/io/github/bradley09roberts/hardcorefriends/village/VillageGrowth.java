package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.civic.BlueprintLibrary;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * The stages after the Settlement: the Town and the City. Besides the Unity each stage needs ({@code Camp.STAGE_UNITY})
 * and the Settlement's own improvements, a village becomes a Town with {@value #TOWN_PEOPLE} people, {@value #TOWN_HOUSES}
 * houses standing, the town hall and the well, and one more of the tavern, the market, the school or the chapel; and a
 * City with {@value #CITY_PEOPLE} people, {@value #CITY_HOUSES} houses, the town hall, the well, the tavern and the
 * market, and two more of the school, the chapel, the watchtower and the town gate. People are everyone on the team,
 * children included. Kinds the building library has no plan for are not asked for. With {@code villageHomes} off the
 * camp stays a Settlement.
 */
public final class VillageGrowth {
	/** The first of the village's own stages (the Town). */
	public static final int TOWN = 5;
	static final int CITY = 6;
	static final int TOWN_PEOPLE = 14;
	static final int TOWN_HOUSES = 5;
	static final int CITY_PEOPLE = 22;
	static final int CITY_HOUSES = 9;

	private record Requirement(int people, int houses, List<String> needed, int moreOf, List<String> pool) {
	}

	private static final Requirement TOWN_NEEDS = new Requirement(TOWN_PEOPLE, TOWN_HOUSES, List.of("civic:town_hall", "civic:well"), 1,
		List.of("civic:tavern", "civic:market", "civic:school", "civic:chapel"));
	private static final Requirement CITY_NEEDS = new Requirement(CITY_PEOPLE, CITY_HOUSES,
		List.of("civic:town_hall", "civic:well", "civic:tavern", "civic:market"), 2,
		List.of("civic:school", "civic:chapel", "civic:watchtower", "civic:gate"));

	private VillageGrowth() {
	}

	/** True if the village is ready for this stage (always for the stages up to the Settlement, which are the camp's own). */
	public static boolean ready(MinecraftServer server, int stage) {
		return stage < TOWN || missing(server, stage).isEmpty();
	}

	/** What the village still lacks for this stage, in plain words; empty when it is ready. */
	public static List<String> missing(MinecraftServer server, int stage) {
		List<String> list = new ArrayList<>();
		if (stage < TOWN) {
			return list;
		}
		if (!FriendsConfig.get().villageHomes) {
			list.add("the village (villageHomes is off in the settings)");
			return list;
		}
		Requirement r = stage >= CITY ? CITY_NEEDS : TOWN_NEEDS;
		VillageData v = VillageData.get(server);
		int people = Households.population(server);
		if (people < r.people()) {
			list.add(r.people() + " people (" + people + " now)");
		}
		int houses = 0;
		for (VillageData.Plot p : v.plots()) {
			if (p.isHouse() && p.standing()) {
				houses++;
			}
		}
		if (houses < r.houses()) {
			list.add(r.houses() + " houses (" + houses + " standing)");
		}
		for (String kind : r.needed()) {
			if (exists(kind) && !VillagePlan.isBuilt(server, kind)) {
				list.add("a " + pretty(kind));
			}
		}
		int more = 0;
		int possible = 0;
		List<String> names = new ArrayList<>();
		for (String kind : r.pool()) {
			if (!exists(kind)) {
				continue;
			}
			possible++;
			names.add(pretty(kind));
			if (VillagePlan.isBuilt(server, kind)) {
				more++;
			}
		}
		int wanted = Math.min(r.moreOf(), possible);
		if (more < wanted) {
			list.add((wanted - more) + " more of: " + String.join(", ", names));
		}
		return list;
	}

	/** What a new stage brings, for the announcement. */
	public static String projects(int stage) {
		return stage >= CITY ? "the town gate and walls, a barn, more houses, and grander streets"
			: "a school, a chapel, a wheat field and an orchard, a fountain, more houses, and cobbled streets";
	}

	/** A friend says the village has grown. */
	public static void celebrate(MinecraftServer server, int stage) {
		CampData camp = Camp.data(server);
		ServerLevel level = Planner.campLevel(server, camp);
		if (level == null || camp.campPos().isEmpty()) {
			return;
		}
		CompanionEntity speaker = Planner.speakerNear(level, camp.campPos().get());
		if (speaker != null) {
			Speech.say(speaker, Line.VILLAGE_GROWS, Camp.stageName(stage));
		}
	}

	private static boolean exists(String kind) {
		return !BlueprintLibrary.get().byKind(kind).isEmpty();
	}

	/** "civic:town_hall" as "town hall". */
	static String pretty(String kind) {
		int colon = kind.indexOf(':');
		return (colon >= 0 ? kind.substring(colon + 1) : kind).replace('_', ' ').replace('/', ' ');
	}
}
