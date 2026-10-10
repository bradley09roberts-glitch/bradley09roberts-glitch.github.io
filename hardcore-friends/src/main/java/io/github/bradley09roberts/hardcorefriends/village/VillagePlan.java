package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.civic.BlueprintLibrary;
import io.github.bradley09roberts.hardcorefriends.civic.Families;

/**
 * The village's public face for the other packages (the market above all): ask for a building of a kind
 * ({@link #requestBuilding}), and find the ones that stand ({@link #buildingsOfKind}, {@link #isBuilt}) with the
 * world positions of their marked spots (a shop's {@code counter}, a workplace's {@code job}, a tavern's {@code sit}
 * spots). The village decides where a requested building goes (a plot along a street, the waterside for a plan whose
 * meta says {@code faces: water}) and when (after the houses for households with none, and the well), and the
 * builders build it like any other. Kinds are the library's: {@code shop:bakery}, {@code workplace:fisher},
 * {@code civic:tavern}; a kind without a colon ({@code shop}) means any of its sub-kinds.
 *
 * <p>All calls run on the server thread and are cheap (no world scans).
 */
public final class VillagePlan {
	/**
	 * A village building: its site key, kind, plan id, dimension, origin and rotation, whether it stands (finished, or
	 * as good as), and its marked spots in the world by name ({@code door}, {@code counter}, {@code job}, {@code chest}...).
	 */
	public record Building(String siteKey, String kind, String planId, String name, String dimension, BlockPos origin, int rotation,
		boolean built, Map<String, List<BlockPos>> markers) {
		/** The world positions of one marker (empty if the plan has none). */
		public List<BlockPos> marker(String name) {
			return markers.getOrDefault(name, List.of());
		}

		/** The first position of a marker, if the plan has one. */
		public Optional<BlockPos> first(String name) {
			List<BlockPos> list = marker(name);
			return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
		}
	}

	private VillagePlan() {
	}

	/**
	 * Asks the village for one building of this kind (for {@code reason}, shown in {@code /friends village}). Asking again
	 * for a kind already asked for, planned or built changes nothing. Returns false only when the building library has
	 * no plan of this kind, so it can never be built.
	 */
	public static boolean requestBuilding(MinecraftServer server, String kind, String reason) {
		return requestBuilding(server, kind, reason, 1);
	}

	/** Asks for {@code count} buildings of this kind in all (two fishing huts, say); see {@link #requestBuilding}. */
	public static boolean requestBuilding(MinecraftServer server, String kind, String reason, int count) {
		if (kind.isBlank() || BlueprintLibrary.get().byKind(kind).isEmpty()) {
			return false;
		}
		VillageData v = VillageData.get(server);
		VillageData.Request old = v.requests().get(kind);
		int wanted = Math.clamp(count, 1, 8);
		if (old == null || old.count() < wanted) {
			v.putRequest(new VillageData.Request(kind, reason, wanted, server.overworld().getGameTime()));
		}
		return true;
	}

	/** Everyone on the team, grown-ups and children (named friends, newcomers, people born in the camp). */
	public static int population(MinecraftServer server) {
		return Households.population(server);
	}

	/**
	 * True when the team has reached the population cap ({@code maxPopulation}): no newcomer joins beyond it (as no baby
	 * is born beyond it), so the village never grows past what the server can bear. Babies on the way count, as they do
	 * for the births, so newcomers never take the places of babies already expected.
	 */
	public static boolean populationFull(MinecraftServer server) {
		return population(server) + Families.get().babiesOnTheWay(server)
			>= io.github.bradley09roberts.hardcorefriends.config.FriendsConfig.get().maxPopulation;
	}

	/** True once the village has asked its builders for this kind (planned, being built or standing) or it is requested. */
	public static boolean isRequested(MinecraftServer server, String kind) {
		VillageData v = VillageData.get(server);
		return v.requests().containsKey(kind) || Planner.countOf(v, kind) > 0;
	}

	/** Every standing building of this kind, oldest first. */
	public static List<Building> buildingsOfKind(MinecraftServer server, String kind) {
		return buildings(server, kind, false);
	}

	/** Every building of this kind, standing or still being built, oldest first. */
	public static List<Building> allOfKind(MinecraftServer server, String kind) {
		return buildings(server, kind, true);
	}

	/** True if at least one building of this kind stands. */
	public static boolean isBuilt(MinecraftServer server, String kind) {
		VillageData v = VillageData.get(server);
		for (VillageData.Plot p : v.plots()) {
			if (p.standing() && p.isKind(kind)) {
				return true;
			}
		}
		return false;
	}

	/** True if the village building with this site key stands. */
	public static boolean isSiteBuilt(MinecraftServer server, String siteKey) {
		return VillageData.get(server).plotBySite(siteKey).map(VillageData.Plot::standing).orElse(false);
	}

	/** The village building with this site key, if there is one. */
	public static Optional<Building> building(MinecraftServer server, String siteKey) {
		VillageData v = VillageData.get(server);
		Optional<VillageData.Plot> p = v.plotBySite(siteKey);
		ServerLevel level = Planner.campLevel(server, Camp.data(server));
		return p.isEmpty() || level == null ? Optional.empty() : Optional.ofNullable(describe(level, v, p.get()));
	}

	private static List<Building> buildings(MinecraftServer server, String kind, boolean unfinishedToo) {
		VillageData v = VillageData.get(server);
		ServerLevel level = Planner.campLevel(server, Camp.data(server));
		List<Building> list = new ArrayList<>();
		if (level == null) {
			return list;
		}
		for (VillageData.Plot p : v.plots()) {
			if (p.isKind(kind) && (p.standing() || unfinishedToo)) {
				Building b = describe(level, v, p);
				if (b != null) {
					list.add(b);
				}
			}
		}
		return list;
	}

	private static @Nullable Building describe(ServerLevel level, VillageData v, VillageData.Plot p) {
		CampData camp = Camp.data(level.getServer());
		Optional<CampData.Site> site = camp.site(p.siteKey);
		Optional<Blueprint> plan = Blueprints.forSite(camp, p.siteKey);
		if (site.isEmpty() || plan.isEmpty()) {
			return null;
		}
		Map<String, List<BlockPos>> markers = new LinkedHashMap<>();
		for (String name : plan.get().markers().keySet()) {
			markers.put(name, List.copyOf(Construction.markers(level, p.siteKey, name)));
		}
		return new Building(p.siteKey, p.kind, p.planId, plan.get().name(), v.dimension(), site.get().origin, site.get().rotation,
			p.standing(), Map.copyOf(markers));
	}
}
