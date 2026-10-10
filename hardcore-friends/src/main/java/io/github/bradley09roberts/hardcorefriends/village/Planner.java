package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.architecture.Styles;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.camp.SiteGrading;
import io.github.bradley09roberts.hardcorefriends.camp.build.MaterialSpec;
import io.github.bradley09roberts.hardcorefriends.camp.build.Part;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;
import io.github.bradley09roberts.hardcorefriends.civic.BlueprintLibrary;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * The village's planner, run from the server tick: it lays out the town plan when the camp becomes a Village, keeps the
 * houses matched to the households ({@link Housing}), decides what to build next and finds it a plot
 * ({@link PlotSearch}), reserves the site for the builders ({@link Construction}), opens the streets the plots front,
 * notices when a building stands (finished, or 95% built with its beds and chests in, so one ridge slab out of reach
 * never holds the village up), and keeps the camp's radius as wide as the village.
 *
 * <p>What it builds, in order: a house for each household that has none (a married couple's with a spare bed for a
 * baby), a bigger house for a household that has outgrown its own, the well in the square, the buildings other
 * packages asked for ({@link VillagePlan#requestBuilding}), the civic buildings of the camp's stage, and the
 * decoration (lamp posts along the streets, benches, a garden, a fountain). At most {@code villageBuildsAtOnce}
 * buildings are under way at once, plus two pieces of decoration; one plot search runs at a time.
 *
 * <p>Costs: a few cheap checks and one to three ground surveys a tick (by the plan's size) while a search runs;
 * otherwise a little work once a second and a reconcile every ten seconds.
 */
public final class Planner {
	/** The town plan is laid out once the camp reaches this stage (the Village). */
	public static final int VILLAGE_STAGE = 3;
	/** Site keys of the village's buildings start with this. */
	public static final String KEY_PREFIX = "village.";
	/** A plot search surveys about this many footprint columns of ground a tick (one to three plots). */
	private static final int SURVEY_AREA_PER_TICK = 240;
	/** How long a kind (or a household's house) waits after no plot could be found, in ticks. */
	private static final int NO_ROOM_WAIT = 20 * 60 * 5;
	/** Decoration under way at once, besides the buildings. */
	private static final int DECOR_AT_ONCE = 2;
	/** Lamp posts only go up near something standing, within this many blocks. */
	private static final int LAMP_NEAR = 20;

	/** The civic buildings each stage adds (from the Village on), in the order they are built. */
	private static final List<List<String>> CIVIC = List.of(
		List.of("civic:well"),
		List.of("civic:town_hall", "civic:market", "civic:tavern"),
		List.of("civic:school", "civic:chapel", "farm:wheat", "farm:orchard", "civic:watchtower"),
		List.of("civic:gate", "farm:barn"));
	/** The decoration each stage adds (from the Village on). */
	private static final List<List<String>> DECOR = List.of(
		List.of("decor:bench", "decor:bench"),
		List.of("decor:garden", "decor:signpost"),
		List.of("decor:fountain"),
		List.of());

	private static @Nullable PlotSearch search;
	private static boolean fallbackTried;
	private static final Map<String, Long> WAIT = new HashMap<>();
	private static boolean reconcileSoon;
	private static int standingCursor;
	private static String lastProblem = "";
	private static final Map<String, Integer> BEDS = new ConcurrentHashMap<>();

	private Planner() {
	}

	/** Forgets everything worked out while a server ran (a world closing, or another opening). */
	static void clear() {
		search = null;
		fallbackTried = false;
		WAIT.clear();
		reconcileSoon = false;
		standingCursor = 0;
		lastProblem = "";
		BEDS.clear();
	}

	/** What the planner could not find room for lately, in plain words, or empty. */
	static String problem() {
		return lastProblem;
	}

	/** The building being looked for a plot for just now, or null. */
	static @Nullable String searching() {
		PlotSearch s = search;
		return s == null ? null : s.plan.name();
	}

	/** Asks for the houses to be matched to the households again soon (someone moved in or out). */
	static void reconcileSoon() {
		reconcileSoon = true;
	}

	// ------------------------------------------------------------------- tick

	static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (search == null && tick % 20 != 7) {
			return; // nothing to do between the once-a-second checks unless a plot search is running
		}
		CampData camp = Camp.data(server);
		VillageData v = VillageData.get(server);
		ServerLevel level = campLevel(server, camp);
		if (level == null || camp.campPos().isEmpty()) {
			return;
		}
		if (v.centre().isPresent() && (!v.centre().get().equals(camp.campPos().get()) || !v.dimension().equals(camp.campDimension()))) {
			reset(level, camp, v); // the camp moved: the old plan belongs to the old place
			return;
		}
		PlotSearch s = search;
		if (s != null) {
			stepSearch(level, camp, v, s);
		}
		if (tick % 20 != 7) {
			return;
		}
		if (v.centre().isEmpty()) {
			if (FriendsConfig.get().villageHomes && camp.stage() >= VILLAGE_STAGE) {
				layOut(level, camp, v);
			}
			return;
		}
		if (tick % 200 == 7 || reconcileSoon) {
			reconcileSoon = false;
			checkSites(level, camp, v);
			reconcile(level, v);
			v.setStreetLevel(camp.stage() >= VillageGrowth.TOWN ? 2 : camp.stage() > VILLAGE_STAGE ? 1 : 0);
			syncReach(camp, v);
		}
		if (search == null && tick % 100 == 7 && FriendsConfig.get().villageHomes) {
			planNext(level, camp, v);
		}
	}

	/** The camp's level, if the camp's dimension is loaded. */
	static @Nullable ServerLevel campLevel(MinecraftServer server, CampData camp) {
		Identifier id = Identifier.tryParse(camp.campDimension());
		if (id == null) {
			return null;
		}
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	// ----------------------------------------------------------------- layout

	private static void layOut(ServerLevel level, CampData camp, VillageData v) {
		BlockPos centre = camp.campPos().orElseThrow();
		v.startPlan(centre, camp.campDimension(), Camp.day(level));
		v.openStreet(0, -12, 12);
		v.openStreet(1, -12, 12);
		syncReach(camp, v);
		Speech.announce(level.getServer(), Component.literal("The friends have drawn up a town plan: the "
			+ TownPlan.STREETS.get(0).name() + " and " + TownPlan.STREETS.get(1).name()
			+ " cross at the camp's heart, with plots along them for houses of their own. (/friends village)")
			.withStyle(ChatFormatting.GOLD));
		CompanionEntity speaker = speakerNear(level, centre);
		if (speaker != null) {
			Speech.say(speaker, Line.NEW_STREET, TownPlan.STREETS.get(0).name());
		}
		HardcoreFriends.LOGGER.info("The village's town plan is laid out round {}", centre);
	}

	/** Lets the whole plan go: every site not yet standing is released, and the homes are forgotten. */
	static void reset(ServerLevel level, CampData camp, VillageData v) {
		for (VillageData.Plot p : List.copyOf(v.plots())) {
			Construction.release(level, p.siteKey);
			if (camp.completed().remove(p.siteKey)) {
				camp.setDirty();
			}
		}
		v.clearPlan();
		search = null;
		WAIT.clear();
		CompoundTag reach = camp.memory(Camp.VILLAGE_MEMORY);
		reach.putInt("reach", 0);
		camp.setDirty();
	}

	/** Records how far the village reaches, so the camp (and the edit rules) grow with it. */
	static void syncReach(CampData camp, VillageData v) {
		BlockPos centre = v.centre().orElse(null);
		if (centre == null) {
			return;
		}
		double far = 0;
		for (VillageData.Plot p : v.plots()) {
			far = Math.max(far, Math.sqrt(TownPlan.farthestSqr(centre, p.box)));
		}
		for (Map.Entry<Integer, int[]> e : v.openStreets().entrySet()) {
			TownPlan.Street s = TownPlan.STREETS.get(Math.clamp(e.getKey(), 0, TownPlan.STREETS.size() - 1));
			for (int t : e.getValue()) {
				BlockPos end = TownPlan.column(s, centre, t, 0);
				far = Math.max(far, Math.sqrt(Camp.horizontalDistSqr(end, centre)));
			}
		}
		int reach = Math.min(TownPlan.maxRadius(), (int) Math.ceil(far) + 4);
		CompoundTag tag = camp.memory(Camp.VILLAGE_MEMORY);
		if (tag.getIntOr("reach", -1) != reach || tag.getLongOr("centre", 0L) != centre.asLong()) {
			tag.putLong("centre", centre.asLong());
			tag.putInt("reach", reach);
			camp.setDirty();
		}
	}

	// ------------------------------------------------------------------ sites

	/** Notices buildings that now stand (a few a time), and drops plots whose site has been released. */
	private static void checkSites(ServerLevel level, CampData camp, VillageData v) {
		List<VillageData.Plot> plots = List.copyOf(v.plots());
		for (VillageData.Plot p : plots) {
			// A site released by someone else is gone for good. (A plan missing from the library, say while a data pack
			// reloads, is not: the plot waits for it to come back.)
			if (camp.site(p.siteKey).isEmpty()) {
				v.removePlot(p);
				reconcileSoon = true;
			}
		}
		int checked = 0;
		List<VillageData.Plot> building = new ArrayList<>();
		for (VillageData.Plot p : v.plots()) {
			if (!p.standing()) {
				building.add(p);
			}
		}
		for (int k = 0; k < building.size() && checked < 4; k++) {
			VillageData.Plot p = building.get(Math.floorMod(standingCursor + k, building.size()));
			Optional<Blueprint> plan = Blueprints.forSite(camp, p.siteKey);
			if (plan.isEmpty()) {
				continue;
			}
			if (Blueprints.isFinished(camp, p.siteKey)) {
				standing(level, camp, v, p);
				continue;
			}
			checked++;
			if (mostlyBuilt(level, camp, p.siteKey, plan.get())) {
				// Close enough: the rest (a slab out of reach, decoration) is left to the builders' repair job.
				Blueprints.markFinished(camp, p.siteKey, true);
				Construction.fireFinished(level, p.siteKey, plan.get());
				standing(level, camp, v, p);
			}
		}
		standingCursor += 4;
	}

	/**
	 * True when a building is as good as finished: 95% of its structure (decoration aside) is in, every bed stands on
	 * its bed spot and every chest spot holds its chest or barrel.
	 */
	static boolean mostlyBuilt(ServerLevel level, CampData camp, String key, Blueprint plan) {
		List<Part> parts = SiteFinder.parts(camp, key, plan);
		if (parts.isEmpty()) {
			return false;
		}
		int total = 0;
		int built = 0;
		for (Placement p : Blueprints.placements(plan, parts)) {
			Blueprint.Entry e = p.entry();
			if (p.isFoundation() || e.material() == MaterialSpec.AIR || e.optional()) {
				continue;
			}
			if (!level.isLoaded(p.pos())) {
				return false;
			}
			total++;
			if (e.isBuilt(level.getBlockState(p.pos()))) {
				built++;
			}
		}
		if (total == 0 || built < total * 0.95) {
			return false;
		}
		for (BlockPos bed : Construction.markers(level, key, "bed")) {
			if (!(level.getBlockState(bed).getBlock() instanceof AbstractBedBlock)) {
				return false;
			}
		}
		for (BlockPos chest : Construction.markers(level, key, "chest")) {
			BlockState s = level.getBlockState(chest);
			if (s.isAir() || !s.hasBlockEntity()) {
				return false;
			}
		}
		return true;
	}

	/** Called when the builders finish a village building (the architecture package's listener). */
	static void finished(ServerLevel level, String siteKey) {
		if (!siteKey.startsWith(KEY_PREFIX)) {
			return;
		}
		MinecraftServer server = level.getServer();
		VillageData v = VillageData.get(server);
		v.plotBySite(siteKey).ifPresent(p -> standing(level, Camp.data(server), v, p));
	}

	/** A building stands: houses take their people in, civic buildings are celebrated. */
	private static void standing(ServerLevel level, CampData camp, VillageData v, VillageData.Plot p) {
		if (p.standing()) {
			return;
		}
		p.state = VillageData.PlotState.STANDING;
		v.touch();
		reconcileSoon = true;
		String name = Blueprints.displayName(camp, p.siteKey);
		if (p.kind.equals("civic:town_hall")) {
			camp.markCompleted(p.siteKey); // weddings move to the town hall (the people package looks for it)
			CompanionEntity speaker = speakerNear(level, p.middle());
			if (speaker != null) {
				Speech.say(speaker, Line.TOWN_HALL_DONE);
			}
			Speech.announce(level.getServer(), Component.literal("The town hall is finished: the village has a heart. Weddings "
				+ "will be held there from now on.").withStyle(ChatFormatting.GOLD));
			Unity.add(level, "village", 30, 0);
		} else if (p.kind.startsWith("civic:") || p.kind.startsWith("farm:") || p.kind.startsWith("shop:")
			|| p.kind.startsWith("workplace:")) {
			Speech.announce(level.getServer(), Component.literal("The village's " + name + " is finished, on "
				+ TownPlan.sideName(p.street, p.side) + ".").withStyle(ChatFormatting.GOLD));
			Unity.add(level, "village", 15, 0);
		} else if (p.isHouse()) {
			Unity.add(level, "village", 10, 40);
		}
		camp.addStat("village_buildings", 1);
	}

	/** Beds in a house plot's plan (its {@code bed} spots); remembered per plan. */
	static int bedCount(VillageData.Plot p) {
		Integer known = BEDS.get(p.planId);
		if (known != null) {
			return known;
		}
		int beds = Blueprints.byPlanId(p.planId).map(b -> b.marker("bed").size()).orElse(0);
		BEDS.put(p.planId, beds);
		return beds;
	}

	// ---------------------------------------------------------------- housing

	private static void reconcile(ServerLevel level, VillageData v) {
		MinecraftServer server = level.getServer();
		Set<UUID> residents = new HashSet<>();
		for (VillageData.Plot p : v.plots()) {
			residents.addAll(p.residents.keySet());
		}
		Households.Roster roster = Households.roster(server, residents);
		for (CompanionEntity c : Companions.all()) {
			v.rememberName(c.getUUID(), c.displayName());
		}
		// A house planned for a household that is gone (everyone in it died or left) and hardly begun is given up, so no
		// materials go into a home nobody needs. One already well on is finished, for whoever needs a home next.
		for (VillageData.Plot p : List.copyOf(v.plots())) {
			if (p.isHouse() && !p.standing() && !p.intended.isEmpty() && p.intended.stream().noneMatch(roster::contains)
				&& Construction.progress(level, p.siteKey) < 0.1) {
				Construction.release(level, p.siteKey);
				v.removePlot(p);
			}
		}
		List<Housing.Move> moves = Housing.reconcile(v, roster);
		Map<VillageData.Plot, List<String>> movedIn = new HashMap<>();
		for (Housing.Move m : moves) {
			movedIn.computeIfAbsent(m.home(), k -> new ArrayList<>()).add(Households.firstName(server, v, m.who()));
			for (CompanionEntity c : Companions.all()) {
				if (c.getUUID().equals(m.who()) && !c.isChild()) {
					Speech.say(c, Line.MOVED_IN, Blueprints.displayName(Camp.data(server), m.home().siteKey));
				}
			}
		}
		movedIn.forEach((home, names) -> Speech.announce(server, Component.literal(join(names) + (names.size() == 1 ? " has" : " have")
			+ " moved into the " + Blueprints.displayName(Camp.data(server), home.siteKey) + " on " + TownPlan.sideName(home.street, home.side)
			+ ".").withStyle(ChatFormatting.GREEN)));
	}

	static String join(List<String> names) {
		if (names.size() <= 1) {
			return names.isEmpty() ? "" : names.getFirst();
		}
		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
	}

	// --------------------------------------------------------------- planning

	/** Decides what to build next and starts looking for its plot. */
	private static void planNext(ServerLevel level, CampData camp, VillageData v) {
		MinecraftServer server = level.getServer();
		BlockPos centre = v.centre().orElseThrow();
		long now = level.getGameTime();
		int stage = camp.stage();
		int underway = 0;
		int decorUnderway = 0;
		for (VillageData.Plot p : v.plots()) {
			if (!p.standing()) {
				if (p.kind.startsWith("decor:")) {
					decorUnderway++;
				} else {
					underway++;
				}
			}
		}
		int cap = FriendsConfig.get().villageBuildsAtOnce;
		Set<UUID> residents = new HashSet<>();
		for (VillageData.Plot p : v.plots()) {
			residents.addAll(p.residents.keySet());
		}
		Households.Roster roster = Households.roster(server, residents);
		List<Housing.Need> needs = Housing.needs(v, roster);
		if (underway < cap) {
			// Homes first: a household with none, then one that has outgrown its own.
			for (Housing.Need need : needs) {
				if (need.urgency() == Housing.Urgency.BABY_ROOM) {
					continue;
				}
				if (startHouse(level, v, centre, need, now)) {
					return;
				}
			}
			// The well in the square first, then what other packages asked for (shops, workplaces), then the stage's
			// civic buildings, so the market's trades are not kept waiting behind every hall and chapel.
			List<String> civic = due(CIVIC, stage);
			if (civic.contains("civic:well") && countOf(v, "civic:well") == 0
				&& startKind(level, camp, v, centre, "civic:well", "civic", now)) {
				return;
			}
			for (VillageData.Request r : List.copyOf(v.requests().values())) {
				if (countOf(v, r.kind()) >= r.count()) {
					continue;
				}
				if (startKind(level, camp, v, centre, r.kind(), "request", now)) {
					return;
				}
			}
			for (String kind : civic) {
				if (countOf(v, kind) == 0 && startKind(level, camp, v, centre, kind, "civic", now)) {
					return;
				}
			}
			for (Housing.Need need : needs) {
				if (need.urgency() == Housing.Urgency.BABY_ROOM && startHouse(level, v, centre, need, now)) {
					return;
				}
			}
			if (stage >= VillageGrowth.CITY && planWalls(level, camp, v, now)) {
				return;
			}
		}
		if (decorUnderway < DECOR_AT_ONCE) {
			Map<String, Integer> wanted = new HashMap<>();
			for (String kind : due(DECOR, stage)) {
				wanted.merge(kind, 1, Integer::sum);
			}
			for (Map.Entry<String, Integer> e : wanted.entrySet()) {
				if (countOf(v, e.getKey()) < e.getValue() && startKind(level, camp, v, centre, e.getKey(), "decor", now)) {
					return;
				}
			}
			planLamp(level, camp, v, centre, now);
		}
	}

	/** The kinds due by this stage, from the Village on, in order. */
	private static List<String> due(List<List<String>> table, int stage) {
		List<String> kinds = new ArrayList<>();
		for (int s = VILLAGE_STAGE; s <= stage && s - VILLAGE_STAGE < table.size(); s++) {
			kinds.addAll(table.get(s - VILLAGE_STAGE));
		}
		return kinds;
	}

	/** Plots of this kind, standing or under way ("shop" counts every shop). */
	static int countOf(VillageData v, String kind) {
		int n = 0;
		for (VillageData.Plot p : v.plots()) {
			if (p.isKind(kind)) {
				n++;
			}
		}
		return n;
	}

	private static boolean waiting(String key, long now) {
		Long until = WAIT.get(key);
		return until != null && now < until;
	}

	private static boolean startHouse(ServerLevel level, VillageData v, BlockPos centre, Housing.Need need, long now) {
		String waitKey = "home:" + need.household().anchor();
		if (waiting(waitKey, now)) {
			return false;
		}
		Blueprint plan = housePlan(level, centre, need.beds(), need.household().size());
		if (plan == null) {
			WAIT.put(waitKey, now + NO_ROOM_WAIT);
			return false;
		}
		begin(level, v, centre, PlotSearch.Mode.STREET, plan, "house", "home", need.household().members());
		return true;
	}

	/**
	 * The house plan for a household: the fewest beds that still give everyone one (and the baby's, when wanted), in the
	 * style that best suits the camp's ground and its wood. Null if the library has no house with room enough.
	 */
	static @Nullable Blueprint housePlan(ServerLevel level, BlockPos centre, int beds, int minimum) {
		List<Blueprint> houses = BlueprintLibrary.get().byKind("house");
		int chosen = Integer.MAX_VALUE;
		int most = 0;
		for (Blueprint b : houses) {
			int n = b.marker("bed").size();
			most = Math.max(most, n);
			if (n >= beds && n < chosen) {
				chosen = n;
			}
		}
		if (chosen == Integer.MAX_VALUE) {
			if (most < minimum || most == 0) {
				return null;
			}
			chosen = most;
		}
		int size = chosen;
		return Styles.pick(level, centre, houses, b -> b.marker("bed").size() == size).orElse(null);
	}

	private static boolean startKind(ServerLevel level, CampData camp, VillageData v, BlockPos centre, String kind, String purpose,
		long now) {
		if (waiting(kind, now) || BlueprintLibrary.get().byKind(kind).isEmpty()) {
			return false; // missing kinds are skipped quietly
		}
		Optional<Blueprint> plan = Styles.pick(level, centre, kind, b -> b.kind().equals(kind) || !kind.contains(":"));
		if (plan.isEmpty()) {
			return false;
		}
		Blueprint b = plan.get();
		begin(level, v, centre, modeFor(b), b, b.kind(), purpose, Set.of());
		return true;
	}

	/** Where a kind of building goes: round the square, on the waterside, at the end of a street, or along a street. */
	static PlotSearch.Mode modeFor(Blueprint plan) {
		String kind = plan.kind();
		if ("water".equals(plan.meta().get("faces"))) {
			return PlotSearch.Mode.WATERFRONT;
		}
		if (kind.equals("civic:gate")) {
			return PlotSearch.Mode.GATE;
		}
		if (kind.equals("civic:well") || kind.equals("decor:bench") || kind.equals("decor:fountain") || kind.equals("decor:garden")
			|| kind.equals("decor:signpost")) {
			return PlotSearch.Mode.SQUARE;
		}
		return PlotSearch.Mode.STREET;
	}

	private static void begin(ServerLevel level, VillageData v, BlockPos centre, PlotSearch.Mode mode, Blueprint plan, String kind,
		String purpose, Set<UUID> intended) {
		List<TownPlan.Candidate> candidates = switch (mode) {
			case SQUARE -> TownPlan.squareCandidates(centre, plan);
			case WATERFRONT -> PlotSearch.waterfrontCandidates(level, centre, plan);
			case GATE -> TownPlan.gateCandidates(centre, plan, v);
			case WALL, STREET -> TownPlan.streetCandidates(centre, plan);
		};
		search = new PlotSearch(mode, plan, kind, purpose, intended, candidates, centre);
		fallbackTried = false;
	}

	private static void stepSearch(ServerLevel level, CampData camp, VillageData v, PlotSearch s) {
		// About 240 footprint columns of ground a tick: three cottages, or one town hall.
		int surveys = Math.clamp(SURVEY_AREA_PER_TICK / Math.max(1, s.plan.width() * s.plan.depth()), 1, 3);
		PlotSearch.Found found = s.step(level, camp, v, surveys);
		if (found != null) {
			search = null;
			reserve(level, camp, v, s, found);
			return;
		}
		if (!s.done()) {
			return;
		}
		search = null;
		if (s.mode == PlotSearch.Mode.SQUARE && !fallbackTried) {
			// No room left round the square: along a street will do.
			BlockPos centre = v.centre().orElseThrow();
			search = new PlotSearch(PlotSearch.Mode.STREET, s.plan, s.kind, s.purpose, s.intended,
				TownPlan.streetCandidates(centre, s.plan), centre);
			fallbackTried = true;
			return;
		}
		// A length of wall waits on its own, so the other side of the gate still gets its walls.
		String waitKey = s.mode == PlotSearch.Mode.WALL ? s.purpose : s.kind;
		if (s.kind.equals("house")) {
			// A household's anchor is its first grown-up; waiting on every member keeps the household waiting whoever leads it.
			for (UUID id : s.intended) {
				WAIT.put("home:" + id, level.getGameTime() + NO_ROOM_WAIT);
			}
		} else {
			WAIT.put(waitKey, level.getGameTime() + NO_ROOM_WAIT);
		}
		lastProblem = "No plot found for a " + s.plan.name() + ": " + s.problem() + ". Clearing or levelling ground along the streets "
			+ "helps (or a larger villageRadius).";
		HardcoreFriends.LOGGER.info("The village found no plot for a {} ({})", s.plan.name(), s.problem());
	}

	/** Reserves the site found for a building, with any levelling, opens its street and records the plot. */
	private static void reserve(ServerLevel level, CampData camp, VillageData v, PlotSearch s, PlotSearch.Found found) {
		TownPlan.Candidate c = found.candidate();
		PlotSurvey.Result r = found.result();
		int id = v.nextPlotId();
		String key = siteKey(s.kind, id);
		BlockPos origin = new BlockPos(c.origin().getX(), r.floorY(), c.origin().getZ());
		if (!Construction.reserve(level, key, s.plan, origin, c.rotation(), null)) {
			WAIT.put(s.kind, level.getGameTime() + 200);
			return;
		}
		if (!r.cut().isEmpty() || !r.fill().isEmpty()) {
			SiteGrading.reserve(camp, key, r.cut(), r.fill());
		}
		VillageData.Plot plot = new VillageData.Plot(id, s.kind, s.plan.planId(), key, c.box(), c.street(), c.side(), c.rotation(),
			r.floorY(), s.purpose, level.getGameTime());
		plot.intended.addAll(s.intended);
		v.addPlot(plot);
		lastProblem = "";
		openStreetFor(level, v, plot);
		syncReach(camp, v);
		HardcoreFriends.LOGGER.info("The village planned a {} at {} ({})", s.plan.name(), origin, TownPlan.sideName(c.street(), c.side()));
	}

	/** A site key for a new village plot: {@code village.house.4}, {@code village.civic.town_hall.7}. */
	static String siteKey(String kind, int id) {
		return KEY_PREFIX + kind.replace(':', '.').replace('/', '.') + "." + id;
	}

	/**
	 * Opens the stretch of street a plot fronts (and the main street leading to a lane), telling everyone when a whole
	 * new street opens.
	 */
	private static void openStreetFor(ServerLevel level, VillageData v, VillageData.Plot plot) {
		if (plot.street < 0 || plot.street >= TownPlan.STREETS.size()) {
			return;
		}
		BlockPos centre = v.centre().orElseThrow();
		TownPlan.Street s = TownPlan.STREETS.get(plot.street);
		int a = TownPlan.along(s, centre, plot.box[0], plot.box[1]);
		int b = TownPlan.along(s, centre, plot.box[2], plot.box[3]);
		boolean fresh = v.openStreet(plot.street, Math.min(a, b) - 2, Math.max(a, b) + 2);
		if (plot.street >= 2) {
			// A lane is reached along the main street that crosses it.
			v.openStreet(s.alongX() ? 1 : 0, Math.min(0, s.offset()), Math.max(0, s.offset()));
		}
		if (fresh && v.announceOnce("street." + plot.street)) {
			CompanionEntity speaker = speakerNear(level, plot.middle());
			if (speaker != null) {
				Speech.say(speaker, Line.NEW_STREET, s.name());
			}
			Speech.announce(level.getServer(), Component.literal("The village has a new street: " + s.name() + ".")
				.withStyle(ChatFormatting.GOLD));
		}
	}

	// ------------------------------------------------------------- the edges

	/**
	 * At the City stage, two lengths of town wall on each side of the town gate, joined end to end at their join spots
	 * (each planned once the piece it joins is planned).
	 */
	private static boolean planWalls(ServerLevel level, CampData camp, VillageData v, long now) {
		if (waiting("civic:wall", now) || BlueprintLibrary.get().byKind("civic:wall").isEmpty()) {
			return false;
		}
		VillageData.Plot gate = null;
		for (VillageData.Plot p : v.plots()) {
			if (p.kind.equals("civic:gate")) {
				gate = p;
				break;
			}
		}
		if (gate == null) {
			return false;
		}
		BlockPos centre = v.centre().orElseThrow();
		Blueprint wall = Styles.pick(level, centre, "civic:wall", b -> true).orElse(null);
		if (wall == null) {
			return false;
		}
		for (String side : new String[] {"L", "R"}) {
			VillageData.Plot last = gate;
			for (int n = 1; n <= 2; n++) {
				VillageData.Plot existing = null;
				for (VillageData.Plot p : v.plots()) {
					if (p.kind.equals("civic:wall") && p.purpose.equals("wall:" + side + n)) {
						existing = p;
					}
				}
				if (existing != null) {
					last = existing;
					continue;
				}
				if (waiting("wall:" + side + n, now)) {
					break; // no room for this length just now: try the other side
				}
				Optional<CampData.Site> site = camp.site(last.siteKey);
				Optional<Blueprint> piece = Blueprints.forSite(camp, last.siteKey);
				if (site.isEmpty() || piece.isEmpty()) {
					break;
				}
				TownPlan.Candidate c = TownPlan.joinedTo(piece.get(), site.get().origin, site.get().rotation, wall, side.equals("R"));
				if (c == null) {
					WAIT.put("civic:wall", now + NO_ROOM_WAIT);
					return false;
				}
				search = new PlotSearch(PlotSearch.Mode.WALL, wall, "civic:wall", "wall:" + side + n, Set.of(), List.of(c), centre);
				fallbackTried = true;
				return true;
			}
		}
		return false;
	}

	/** One lamp post on a street's verge, beside a stretch with something standing, where the ground allows. */
	private static boolean planLamp(ServerLevel level, CampData camp, VillageData v, BlockPos centre, long now) {
		if (waiting("decor:lamp", now)) {
			return false;
		}
		Blueprint lamp = Styles.pick(level, centre, "decor:lamp", b -> b.width() == 1 && b.depth() == 1).orElse(null);
		if (lamp == null) {
			WAIT.put("decor:lamp", now + NO_ROOM_WAIT);
			return false;
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (TownPlan.Street s : TownPlan.STREETS) {
			int[] open = v.open(s.index());
			if (open == null) {
				continue;
			}
			for (BlockPos slot : TownPlan.lampSlots(s, centre)) {
				int t = TownPlan.along(s, centre, slot.getX(), slot.getZ());
				if (t < open[0] || t > open[1] || waiting("lamp@" + slot.asLong(), now) || taken(v, slot)
					|| !nearStanding(v, slot) || !level.hasChunkAt(slot.getX(), slot.getZ())) {
					continue;
				}
				int g = PlotSurvey.groundY(level, camp, slot.getX(), slot.getZ(), centre.getY(), m);
				BlockPos origin = new BlockPos(slot.getX(), g + 1, slot.getZ());
				if (g < Integer.MIN_VALUE + 16 || Construction.check(level, lamp, origin, 0).isPresent()) {
					WAIT.put("lamp@" + slot.asLong(), now + NO_ROOM_WAIT * 4);
					continue;
				}
				int id = v.nextPlotId();
				String key = siteKey("decor:lamp", id);
				if (!Construction.reserve(level, key, lamp, origin, 0, null)) {
					return false;
				}
				int side = Integer.signum(TownPlan.across(s, centre, slot.getX(), slot.getZ()));
				v.addPlot(new VillageData.Plot(id, "decor:lamp", lamp.planId(), key, lamp.footprint(origin, 0), s.index(), side, 0,
					origin.getY(), "lamp", now));
				return true;
			}
		}
		WAIT.put("decor:lamp", now + 1200);
		return false;
	}

	private static boolean taken(VillageData v, BlockPos slot) {
		for (VillageData.Plot p : v.plots()) {
			if (slot.getX() >= p.box[0] && slot.getX() <= p.box[2] && slot.getZ() >= p.box[1] && slot.getZ() <= p.box[3]) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearStanding(VillageData v, BlockPos pos) {
		for (VillageData.Plot p : v.plots()) {
			if (p.standing() && !p.kind.equals("decor:lamp")
				&& TownPlan.distSqr(pos, p.box) <= (double) LAMP_NEAR * LAMP_NEAR) {
				return true;
			}
		}
		return false;
	}

	// ----------------------------------------------------------------- speech

	/** A grown-up friend at work in the camp's world nearest this spot, to say something about it, or null. */
	static @Nullable CompanionEntity speakerNear(Level level, BlockPos pos) {
		CompanionEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (CompanionEntity c : Companions.all()) {
			if (c.level() != level || c.isChild()) {
				continue;
			}
			double d = c.blockPosition().distSqr(pos);
			if (d < bestDist) {
				bestDist = d;
				best = c;
			}
		}
		return best;
	}
}
