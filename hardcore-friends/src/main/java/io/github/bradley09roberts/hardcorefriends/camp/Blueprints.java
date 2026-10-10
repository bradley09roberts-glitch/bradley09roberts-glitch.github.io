package io.github.bradley09roberts.hardcorefriends.camp;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.architecture.MaterialDemand;
import io.github.bradley09roberts.hardcorefriends.architecture.PlanLibrary;
import io.github.bradley09roberts.hardcorefriends.architecture.PlanParser;
import io.github.bradley09roberts.hardcorefriends.camp.build.MaterialSpec;
import io.github.bradley09roberts.hardcorefriends.camp.build.Part;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;
import io.github.bradley09roberts.hardcorefriends.civic.BlueprintLibrary;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * The camp's building plans: everything Oak builds and every contraption Spark makes. Plans use ordinary survival
 * materials. Coordinates are local: x to the east, z to the south, the front on the north side.
 *
 * <p>The cabin, the storehouse and the watchtower are proper buildings since 3.0 (a cottage with a gable roof, a
 * timber-framed store, a tower with a roofed lookout), drawn in the building-plan file format in
 * {@code /hardcorefriends/camp_plans/} inside the mod. Their old box-shaped plans are kept as "legacy" plans: a site
 * reserved before 3.0, or one already finished, keeps the plan it was started with ({@link #forSite}), so old saves
 * never end up with half of one plan and half of another. The config option {@code fancyCampBuildings} chooses the
 * old plans for new sites too.
 *
 * <p>Every reserved site remembers its plan (and the wood chosen for it) in camp memory under
 * {@value #SITES_MEMORY}, which is also how library buildings (houses, shops...) on the camp's sites are found again.
 * A library site also keeps the version of its plan it was reserved with (the plan file's content, shared by every site
 * of that version, under {@value #PLAN_SOURCES_MEMORY}), so a data pack or an update of the mod that changes or removes
 * the plan under that id leaves buildings already begun or standing as they are: only new sites use the new version.
 */
public final class Blueprints {
	/** Camp memory key: for each site key, the plan it was reserved with, its wood and whether it is finished. */
	public static final String SITES_MEMORY = "architecture.sites";
	/** Camp memory key: the content of each library plan version a site was reserved with, by its fingerprint. */
	public static final String PLAN_SOURCES_MEMORY = "architecture.plan_sources";
	/** A chest just east of the camp centre, linked as the supply chest once built. */
	public static final Blueprint SUPPLY_CHEST = Blueprint.builder(Structures.SUPPLY_CHEST, 1, 1).at(2, 0)
		.put(0, 0, 0, MaterialSpec.CHEST, facing(Direction.NORTH)).build();

	/** A lit campfire just south of the centre. */
	public static final Blueprint CAMPFIRE = Blueprint.builder(Structures.CAMPFIRE, 1, 1).at(0, 3)
		.put(0, 0, 0, MaterialSpec.CAMPFIRE, s -> s.setValue(BlockStateProperties.LIT, true)).build();

	public static final Blueprint CRAFTING_TABLE = Blueprint.builder(Structures.CRAFTING_TABLE, 1, 1)
		.anchor(Blueprint.Anchor.CHEST).at(0, -1).put(0, 0, 0, MaterialSpec.CRAFTING_TABLE).build();

	public static final Blueprint FURNACE = Blueprint.builder(Structures.FURNACE, 1, 1)
		.anchor(Blueprint.Anchor.CHEST).at(0, 1).put(0, 0, 0, MaterialSpec.FURNACE, facing(Direction.NORTH)).build();

	/** Four fence posts with a torch on top, around the centre. */
	public static final Blueprint TORCH_POSTS = Blueprint.builder(Structures.TORCH_POSTS, 1, 1)
		.at(6, 6).at(-6, 6).at(6, -6).at(-6, -6).fixedFacing()
		.put(0, 0, 0, MaterialSpec.FENCE).attach(0, 1, 0, MaterialSpec.TORCH).build();

	/** The 2.x cabin: a 7×7 plank box with a flat slab roof. Kept for sites started before 3.0. */
	public static final Blueprint LEGACY_CABIN = cabin(Structures.CABIN, -10, -1);

	/** The 2.x second cabin (same plan as {@link #LEGACY_CABIN}). */
	public static final Blueprint LEGACY_CABIN_2 = cabin(Structures.CABIN_2, 0, -11);

	/** The 2.x storehouse: a 5×5 plank shed with a door and two chests against the back wall. */
	public static final Blueprint LEGACY_STOREHOUSE = storehouse();

	/** The 2.x watchtower: a 3×3 cobblestone tower with a ladder inside and a slab lookout ringed by torches. */
	public static final Blueprint LEGACY_WATCHTOWER = watchtower();

	/**
	 * The cabin: a cottage with a log frame on a cobblestone plinth, plank walls, a stair gable roof whose eaves overhang
	 * the doorstep, open windows that get glass once the camp can make it, a porch lantern and a vaulted room inside.
	 * Keeps the 2.x contract: the door's lower half at local {@link #CABIN_DOOR}, a solid doorstep in front of it and
	 * floor behind it (the automatic door's pressure plates), and a clear, roofed 3×3 around {@link #CABIN_INSIDE}
	 * where friends sleep.
	 */
	public static final Blueprint CABIN = campPlan("cabin", Structures.CABIN, LEGACY_CABIN);

	/** The second cabin: the same cottage, elsewhere in the camp. */
	public static final Blueprint CABIN_2 = CABIN.copyAs(Structures.CABIN_2, "camp/cabin_2", List.of(new int[] {0, -11}));

	/** The storehouse: a timber-framed store with a gable roof, chests and barrels along the walls. */
	public static final Blueprint STOREHOUSE = campPlan("storehouse", Structures.STOREHOUSE, LEGACY_STOREHOUSE);

	/**
	 * The watchtower: a stone-based tower with a one-block ladder shaft (climbable the way {@code GuardTask} climbs it),
	 * a railed lookout platform and a roof over it. The lookout is the only sturdy floor up there with headroom, so
	 * {@code ai.role.guard.Watchtower} finds it.
	 */
	public static final Blueprint WATCHTOWER = campPlan("watchtower", Structures.WATCHTOWER, LEGACY_WATCHTOWER);

	/** Tall fence posts carrying lanterns near the camp's main features. */
	public static final Blueprint LANTERN_POSTS = Blueprint.builder(Structures.LANTERN_POSTS, 1, 1)
		.at(0, -6).at(6, 0).at(0, 6).at(-5, 0).fixedFacing()
		.put(0, 0, 0, MaterialSpec.FENCE).put(0, 1, 0, MaterialSpec.FENCE)
		.attach(0, 2, 0, MaterialSpec.LANTERN, s -> s.setValue(BlockStateProperties.HANGING, false)).build();

	/**
	 * Wooden pressure plates on the doorstep and just inside the cabin door, so the door opens for anyone walking
	 * through. Uses the cabin's own site and coordinates (door at local 3, 1, 1).
	 */
	public static final Blueprint AUTO_DOOR = Blueprint.builder(Structures.AUTO_DOOR, 7, 8).anchor(Blueprint.Anchor.CABIN)
		.attach(3, 1, 0, MaterialSpec.PRESSURE_PLATE).attach(3, 1, 2, MaterialSpec.PRESSURE_PLATE).build();

	/** A hopper pointing down into the supply chest: drop items on it and they go into the chest. */
	public static final Blueprint HOPPER_DROPOFF = Blueprint.builder(Structures.HOPPER_DROPOFF, 1, 1)
		.anchor(Blueprint.Anchor.CHEST_TOP).put(0, 1, 0, MaterialSpec.HOPPER, hopper(Direction.DOWN)).build();

	/**
	 * A vanilla hopper-fed furnace: input chest on top, a hopper into the furnace's top, a fuel chest and hopper
	 * feeding its side, and a hopper under it emptying into the output chest on the ground.
	 */
	public static final Blueprint AUTO_SMELTER = Blueprint.builder(Structures.AUTO_SMELTER, 2, 1).at(7, 7)
		.put(0, 0, 0, MaterialSpec.CHEST, facing(Direction.NORTH))
		.put(1, 0, 0, MaterialSpec.COBBLESTONE)
		.put(0, 1, 0, MaterialSpec.HOPPER, hopper(Direction.DOWN))
		.put(1, 1, 0, MaterialSpec.COBBLESTONE)
		.put(0, 2, 0, MaterialSpec.FURNACE, facing(Direction.NORTH))
		.put(1, 2, 0, MaterialSpec.HOPPER, hopper(Direction.WEST))
		.put(0, 3, 0, MaterialSpec.HOPPER, hopper(Direction.DOWN))
		.put(1, 3, 0, MaterialSpec.CHEST, facing(Direction.NORTH))
		.put(0, 4, 0, MaterialSpec.CHEST, facing(Direction.NORTH)).build();

	/** Fence posts topped by a redstone lamp and an inverted daylight detector: they light up at night. */
	public static final Blueprint LAMP_POSTS = Blueprint.builder(Structures.LAMP_POSTS, 1, 1)
		.at(4, -9).at(-4, 9).fixedFacing()
		.put(0, 0, 0, MaterialSpec.FENCE).put(0, 1, 0, MaterialSpec.REDSTONE_LAMP)
		.put(0, 2, 0, MaterialSpec.DAYLIGHT_DETECTOR, s -> s.setValue(DaylightDetectorBlock.INVERTED, true)).build();

	/**
	 * A 9×9 ring of wooden fences round a 7×7 paddock, with a fence gate in the middle of the front (the side facing
	 * the camp centre). The gate is built open so nobody is shut in while building; the farmer shuts it once animals
	 * are inside. {@code ai.role.ranch.Pen} reads the paddock's layout from this plan's site.
	 */
	public static final Blueprint ANIMAL_PEN = animalPen();

	/** Local position of the library's enchanting table (declared before the plan, which reads it). */
	public static final int[] LIBRARY_TABLE = {2, 0, 2};

	/**
	 * Sage's library: an enchanting table in the middle of a 5×5 square, ringed by 15 bookshelves on the ground (every
	 * edge block but the doorway in the middle of the front), with the ring of air between table and shelves that the
	 * table needs, and a torch on each corner shelf. Local position of the table: {@link #LIBRARY_TABLE}.
	 */
	public static final Blueprint LIBRARY = library();

	/** An anvil a few blocks from the supply chest. */
	public static final Blueprint ANVIL = Blueprint.builder(Structures.ANVIL, 1, 1).anchor(Blueprint.Anchor.CHEST).at(0, 3)
		.put(0, 0, 0, MaterialSpec.ANVIL).build();

	/** A brewing stand a few blocks from the supply chest, on the other side. */
	public static final Blueprint BREWING_STAND = Blueprint.builder(Structures.BREWING_STAND, 1, 1).anchor(Blueprint.Anchor.CHEST)
		.at(0, -3).put(0, 0, 0, MaterialSpec.BREWING_STAND).build();

	/** Local position of the bottom of the Nether portal's opening, where it is lit (declared before the plan). */
	public static final int[] PORTAL_FIRE = {1, 1, 0};

	/**
	 * A Nether portal for expeditions: a frame of 10 obsidian, 4 wide and 5 high, standing on the ground, with its four
	 * corners in cobblestone (a portal does not need them) round a 2×3 opening. The expedition package's own job builds
	 * it once Sage's plan says the camp is ready, then lights it at {@link #PORTAL_FIRE}.
	 */
	public static final Blueprint NETHER_PORTAL = netherPortal();

	/** Local positions of the smelter's chests, for players and tests. */
	public static final int[] SMELTER_INPUT = {0, 4, 0};
	public static final int[] SMELTER_FUEL = {1, 3, 0};
	public static final int[] SMELTER_OUTPUT = {0, 0, 0};
	/** Local position of the cabin's door (lower half), the same in the 2.x cabin and the 3.0 cottage. */
	public static final int[] CABIN_DOOR = {3, 1, 1};
	/** Local position of the middle of the cabin's floor, where friends sleep (the same in both cabins). */
	public static final int[] CABIN_INSIDE = {3, 1, 4};

	private static final Map<String, Blueprint> BY_ID = new LinkedHashMap<>();
	/** Plans replaced in 3.0, by structure id: used for sites started (or finished) before 3.0. */
	private static final Map<String, Blueprint> LEGACY = new LinkedHashMap<>();
	/** Every camp plan, current and legacy, by plan id. */
	private static final Map<String, Blueprint> BY_PLAN_ID = new LinkedHashMap<>();

	static {
		for (Blueprint b : List.of(SUPPLY_CHEST, CAMPFIRE, CRAFTING_TABLE, FURNACE, TORCH_POSTS, CABIN, STOREHOUSE, WATCHTOWER,
			LANTERN_POSTS, CABIN_2, AUTO_DOOR, HOPPER_DROPOFF, AUTO_SMELTER, LAMP_POSTS, ANIMAL_PEN, LIBRARY, ANVIL, BREWING_STAND,
			NETHER_PORTAL)) {
			BY_ID.put(b.id(), b);
			BY_PLAN_ID.put(b.planId(), b);
		}
		for (Blueprint b : List.of(LEGACY_CABIN, LEGACY_CABIN_2, LEGACY_STOREHOUSE, LEGACY_WATCHTOWER)) {
			if (BY_ID.get(b.id()) != b) {
				LEGACY.put(b.id(), b);
			}
			BY_PLAN_ID.put(b.planId(), b);
		}
	}

	private Blueprints() {
	}

	/**
	 * The plan a new site for this structure is built from: the 3.0 plan, or the 2.x one when the config option
	 * {@code fancyCampBuildings} is off. For a structure that already has a site, use {@link #forSite}.
	 */
	public static Optional<Blueprint> forId(String structureId) {
		if (!FriendsConfig.get().fancyCampBuildings) {
			Blueprint legacy = LEGACY.get(structureId);
			if (legacy != null) {
				return Optional.of(legacy);
			}
		}
		return Optional.ofNullable(BY_ID.get(structureId));
	}

	/**
	 * The plan a site is built from. A site remembers the plan it was reserved with, so it keeps it even if the plans
	 * change: a camp site with no record was reserved before 3.0 and keeps the 2.x plan; a library site (houses, shops)
	 * keeps the version of its library plan it was reserved with, even after a data pack or an update of the mod has
	 * changed or removed the plan under that id ({@link #libraryPlan}). A structure without a site gets
	 * {@link #forId}.
	 */
	public static Optional<Blueprint> forSite(CampData data, String siteKey) {
		CompoundTag rec = record(data, siteKey);
		String ref = rec.getStringOr("plan", "");
		boolean camp = BY_ID.containsKey(siteKey);
		boolean inUse = data.site(siteKey).isPresent() || data.isCompleted(siteKey);
		if (camp && !inUse) {
			return forId(siteKey);
		}
		if (!ref.isEmpty()) {
			Optional<Blueprint> recorded = camp || BY_PLAN_ID.containsKey(ref) ? byPlanId(ref) : libraryPlan(data, rec, ref);
			if (recorded.isPresent()) {
				return recorded;
			}
		}
		if (camp) {
			Blueprint legacy = LEGACY.get(siteKey);
			return legacy != null && ref.isEmpty() ? Optional.of(legacy) : forId(siteKey);
		}
		return Optional.empty();
	}

	/**
	 * A library site's plan: the library's while it is still the version the site was reserved with, else that version
	 * read again from what the site kept. A site reserved before versions were kept takes the library's version as it
	 * is now as its own. With nothing kept (or what was kept no longer reads), the library's plan, if it has one.
	 */
	private static Optional<Blueprint> libraryPlan(CampData data, CompoundTag rec, String ref) {
		Optional<Blueprint> current = BlueprintLibrary.get().get(ref);
		String print = rec.getStringOr("src", "");
		if (print.isEmpty()) {
			current.ifPresent(plan -> keepVersion(data, rec, plan));
			return current;
		}
		PlanLibrary.Source now = current.map(PlanLibrary::sourceOf).orElse(null);
		if (now != null && now.print().equals(print)) {
			return current;
		}
		String json = data.memory(PLAN_SOURCES_MEMORY).getStringOr(print, "");
		Optional<Blueprint> kept = json.isEmpty() ? Optional.empty() : PlanLibrary.kept(ref, print, json);
		return kept.isPresent() ? kept : current;
	}

	/** Notes in a site's record which version of a library plan it is built from, keeping that version's content. */
	private static void keepVersion(CampData data, CompoundTag rec, Blueprint plan) {
		PlanLibrary.Source source = PlanLibrary.sourceOf(plan);
		if (source == null || !source.keepable()) {
			return; // not a library plan, or too big to keep in the save: the site follows the library
		}
		rec.putString("src", source.print());
		data.memory(PLAN_SOURCES_MEMORY).putString(source.print(), source.json());
		data.setDirty();
	}

	/** Forgets a kept plan version once no site is built from it any more. */
	private static void dropVersionIfUnused(CampData data, String print) {
		if (print.isEmpty()) {
			return;
		}
		CompoundTag sites = data.memory(SITES_MEMORY);
		for (String key : sites.keySet()) {
			if (print.equals(sites.getCompoundOrEmpty(key).getStringOr("src", ""))) {
				return;
			}
		}
		if (data.memory(PLAN_SOURCES_MEMORY).remove(print) != null) {
			data.setDirty();
		}
	}

	/** A camp plan (current or legacy) or a library plan by its plan id. */
	public static Optional<Blueprint> byPlanId(String planId) {
		Blueprint camp = BY_PLAN_ID.get(planId);
		return camp != null ? Optional.of(camp) : BlueprintLibrary.get().get(planId);
	}

	/** A readable name for a site in speech: the camp structure's name, or the plan's ("oak cottage"). */
	public static String displayName(CampData data, String siteKey) {
		for (Structures.Entry e : Structures.ALL) {
			if (e.id().equals(siteKey)) {
				return e.displayName();
			}
		}
		return forSite(data, siteKey).map(Blueprint::name).orElse("building");
	}

	/** True if this site key is one of the camp's own improvements (a {@link Structures} id). */
	public static boolean isCampStructure(String siteKey) {
		for (Structures.Entry e : Structures.ALL) {
			if (e.id().equals(siteKey)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------- site records

	private static CompoundTag record(CampData data, String siteKey) {
		return data.memory(SITES_MEMORY).getCompoundOrEmpty(siteKey);
	}

	/**
	 * Remembers which plan (and wood) a site was reserved with, and for a library plan which version of it; a fresh
	 * reservation is not finished.
	 */
	public static void recordPlan(CampData data, String siteKey, Blueprint plan, @Nullable String wood) {
		String before = record(data, siteKey).getStringOr("src", "");
		CompoundTag rec = new CompoundTag();
		rec.putString("plan", plan.planId());
		if (wood != null) {
			rec.putString("wood", wood);
		}
		data.memory(SITES_MEMORY).put(siteKey, rec);
		if (!BY_PLAN_ID.containsKey(plan.planId())) {
			keepVersion(data, rec, plan);
		}
		dropVersionIfUnused(data, before);
		data.setDirty();
	}

	/** The wood chosen for a site when it was reserved (its wooden parts prefer it), or null. */
	public static @Nullable String siteWood(CampData data, String siteKey) {
		String wood = record(data, siteKey).getStringOr("wood", "");
		return wood.isEmpty() ? null : wood;
	}

	/**
	 * Marks a site's building finished (or not), for library sites, whose completion is not a camp stage. A finished
	 * building asks for no more materials, however it was finished (the last batch, or the village calling it as good
	 * as finished): its forecast and any shortage it reported are dropped.
	 */
	public static void markFinished(CampData data, String siteKey, boolean finished) {
		CompoundTag sites = data.memory(SITES_MEMORY);
		CompoundTag rec = sites.getCompoundOrEmpty(siteKey);
		rec.putBoolean("done", finished);
		sites.put(siteKey, rec);
		data.setDirty();
		if (finished) {
			MaterialDemand.clear(siteKey);
			CampNeeds.clearBuildShortage(siteKey);
		}
	}

	/** True once a site's building was finished: a completed camp structure, or a library site marked finished. */
	public static boolean isFinished(CampData data, String siteKey) {
		return data.isCompleted(siteKey) || record(data, siteKey).getBooleanOr("done", false);
	}

	/** Forgets a site's record, and the plan version it kept if no other site uses it (the caller removes the site). */
	public static void forgetRecord(CampData data, String siteKey) {
		String print = record(data, siteKey).getStringOr("src", "");
		if (data.memory(SITES_MEMORY).remove(siteKey) != null) {
			data.setDirty();
		}
		dropVersionIfUnused(data, print);
	}

	/** Site keys of every reserved site built from a library plan (houses, shops...), in no particular order. */
	public static List<String> librarySites(CampData data) {
		List<String> keys = new ArrayList<>();
		for (String key : data.memory(SITES_MEMORY).keySet()) {
			if (!isCampStructure(key) && data.site(key).isPresent()) {
				keys.add(key);
			}
		}
		return keys;
	}

	/**
	 * Reads one of the mod's own camp plans from {@code /hardcorefriends/camp_plans/<file>.json} inside the mod, in the
	 * building-plan format. If the file is missing or wrong (which would be a bug in the mod), the legacy plan is used
	 * and the problem logged, so the camp still builds.
	 */
	private static Blueprint campPlan(String file, String structureId, Blueprint fallback) {
		String path = "/hardcorefriends/camp_plans/" + file + ".json";
		try (InputStream in = Blueprints.class.getResourceAsStream(path)) {
			if (in == null) {
				HardcoreFriends.LOGGER.error("Camp plan {} is missing; using the old plan", path);
				return fallback;
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				PlanParser.Result result = PlanParser.parse(structureId, "camp/" + file, JsonParser.parseReader(reader));
				if (result.plan() == null) {
					HardcoreFriends.LOGGER.error("Camp plan {} is wrong ({}); using the old plan", path, String.join("; ", result.errors()));
					return fallback;
				}
				for (String w : result.warnings()) {
					HardcoreFriends.LOGGER.warn("Camp plan {}: {}", path, w);
				}
				return result.plan();
			}
		} catch (Exception e) {
			HardcoreFriends.LOGGER.error("Camp plan {} could not be read; using the old plan", path, e);
			return fallback;
		}
	}

	/** World position of a local block on a single-part site. */
	public static BlockPos at(CampData.Site site, int[] local) {
		return Blueprint.worldPos(site.origin, site.rotation, local[0], local[1], local[2]);
	}

	/**
	 * Every entry of a plan resolved onto its parts, in build order: foundations first (one per footprint column,
	 * counted as done where the ground is already solid), then each layer across all parts, then attachments.
	 */
	public static List<Placement> placements(Blueprint bp, List<Part> parts) {
		List<Placement> list = new ArrayList<>();
		if (bp.hasFoundations()) {
			Blueprint.Entry fill = new Blueprint.Entry(0, -1, 0, MaterialSpec.FOUNDATION, UnaryOperator.identity(), false);
			for (int p = 0; p < parts.size(); p++) {
				Part part = parts.get(p);
				for (int dx = 0; dx < bp.width(); dx++) {
					for (int dz = 0; dz < bp.depth(); dz++) {
						BlockPos pos = Blueprint.worldPos(part.origin(), part.rotation(), dx, -1, dz);
						list.add(new Placement(list.size(), p, pos, fill, part.rotation()));
					}
				}
			}
		}
		List<Blueprint.Entry> entries = bp.entries();
		int i = 0;
		while (i < entries.size()) {
			int j = i;
			Blueprint.Entry first = entries.get(i);
			while (j < entries.size() && entries.get(j).attachment() == first.attachment() && entries.get(j).dy() == first.dy()) {
				j++;
			}
			for (int p = 0; p < parts.size(); p++) {
				Part part = parts.get(p);
				for (int k = i; k < j; k++) {
					Blueprint.Entry e = entries.get(k);
					BlockPos pos = Blueprint.worldPos(part.origin(), part.rotation(), e.dx(), e.dy(), e.dz());
					list.add(new Placement(list.size(), p, pos, e, part.rotation()));
				}
			}
			i = j;
		}
		return list;
	}

	// ---------------------------------------------------------------- plans

	/**
	 * The 2.x cabin: a 7×7 box with a doorstep in front: plank floor on the ground, log corners, plank walls three high
	 * with a door and two glass-pane windows, a flat wooden-slab roof and a wall torch inside. When the camp has no
	 * glass the builder closes the windows with planks instead (the windows' fallback).
	 */
	private static Blueprint cabin(String id, int x, int z) {
		Blueprint.Builder b = Blueprint.builder(id, 7, 8).at(x, z).planId("legacy/" + id).kind("camp:" + id);
		b.put(3, 0, 0, MaterialSpec.PLANKS); // doorstep
		for (int dx = 0; dx < 7; dx++) {
			for (int dz = 1; dz <= 7; dz++) {
				boolean corner = (dx == 0 || dx == 6) && (dz == 1 || dz == 7);
				b.put(dx, 0, dz, corner ? MaterialSpec.LOG : MaterialSpec.PLANKS);
			}
		}
		for (int dy = 1; dy <= 3; dy++) {
			for (int dx = 0; dx < 7; dx++) {
				for (int dz = 1; dz <= 7; dz++) {
					boolean edgeX = dx == 0 || dx == 6;
					boolean edgeZ = dz == 1 || dz == 7;
					if (!edgeX && !edgeZ) {
						continue;
					}
					if (dx == 3 && dz == 1 && dy <= 2) {
						continue; // doorway
					}
					if (dy == 2 && dz == 4 && edgeX) {
						b.entry(new Blueprint.Entry(dx, dy, dz, MaterialSpec.GLASS_PANE, UnaryOperator.identity(), false, null, false,
							new Blueprint.Alternative(MaterialSpec.PLANKS, UnaryOperator.identity(), null)));
						continue;
					}
					b.put(dx, dy, dz, edgeX && edgeZ ? MaterialSpec.LOG : MaterialSpec.PLANKS);
				}
			}
		}
		for (int dx = 0; dx < 7; dx++) {
			for (int dz = 1; dz <= 7; dz++) {
				b.put(dx, 4, dz, MaterialSpec.SLAB, Blueprints::bottomSlab);
			}
		}
		b.attach(3, 1, 1, MaterialSpec.DOOR, facing(Direction.SOUTH));
		b.attach(3, 2, 1, MaterialSpec.DOOR_TOP);
		b.attach(3, 2, 6, MaterialSpec.WALL_TORCH, facing(Direction.NORTH));
		return b.build();
	}

	private static Blueprint storehouse() {
		Blueprint.Builder b = Blueprint.builder(Structures.STOREHOUSE, 5, 6).at(9, -8).planId("legacy/storehouse")
			.kind("camp:storehouse");
		b.put(2, 0, 0, MaterialSpec.PLANKS); // doorstep
		for (int dy = 0; dy <= 2; dy++) {
			for (int dx = 0; dx < 5; dx++) {
				for (int dz = 1; dz <= 5; dz++) {
					boolean edgeX = dx == 0 || dx == 4;
					boolean edgeZ = dz == 1 || dz == 5;
					if (dy > 0 && !edgeX && !edgeZ) {
						continue;
					}
					if (dy > 0 && dx == 2 && dz == 1) {
						continue; // doorway
					}
					b.put(dx, dy, dz, edgeX && edgeZ ? MaterialSpec.LOG : MaterialSpec.PLANKS);
				}
			}
		}
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 1; dz <= 5; dz++) {
				b.put(dx, 3, dz, MaterialSpec.SLAB, Blueprints::bottomSlab);
			}
		}
		b.attach(2, 1, 1, MaterialSpec.DOOR, facing(Direction.SOUTH));
		b.attach(2, 2, 1, MaterialSpec.DOOR_TOP);
		b.attach(1, 1, 4, MaterialSpec.CHEST, facing(Direction.NORTH));
		b.attach(3, 1, 4, MaterialSpec.CHEST, facing(Direction.NORTH));
		b.attach(2, 2, 4, MaterialSpec.WALL_TORCH, facing(Direction.NORTH));
		return b.build();
	}

	private static Blueprint watchtower() {
		Blueprint.Builder b = Blueprint.builder(Structures.WATCHTOWER, 3, 3).at(-8, 9).planId("legacy/watchtower")
			.kind("camp:watchtower");
		for (int dy = 0; dy <= 3; dy++) {
			for (int dx = 0; dx < 3; dx++) {
				for (int dz = 0; dz < 3; dz++) {
					if (dx == 1 && dz == 1 || dx == 1 && dz == 0 && dy <= 1) {
						continue; // shaft and doorway
					}
					b.put(dx, dy, dz, MaterialSpec.COBBLESTONE);
				}
			}
		}
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				boolean corner = dx != 1 && dz != 1;
				if (corner) {
					b.put(dx, 4, dz, MaterialSpec.COBBLESTONE);
				} else if (!(dx == 1 && dz == 1)) {
					b.put(dx, 4, dz, MaterialSpec.SLAB, Blueprints::bottomSlab);
				}
			}
		}
		for (int dy = 0; dy <= 3; dy++) {
			b.attach(1, dy, 1, MaterialSpec.LADDER, facing(Direction.NORTH));
		}
		b.attach(0, 5, 0, MaterialSpec.TORCH).attach(2, 5, 0, MaterialSpec.TORCH)
			.attach(0, 5, 2, MaterialSpec.TORCH).attach(2, 5, 2, MaterialSpec.TORCH);
		return b.build();
	}

	/** Local position of the animal pen's gate: the middle of the front row. */
	public static final int[] PEN_GATE = {4, 0, 0};

	private static Blueprint animalPen() {
		Blueprint.Builder b = Blueprint.builder(Structures.ANIMAL_PEN, 9, 9).at(11, 11);
		for (int dx = 0; dx < 9; dx++) {
			for (int dz = 0; dz < 9; dz++) {
				if (dx != 0 && dx != 8 && dz != 0 && dz != 8) {
					continue; // the paddock itself stays open ground
				}
				if (dx == 4 && dz == 0) {
					b.put(dx, 0, dz, MaterialSpec.FENCE_GATE, Blueprints::openGate);
				} else {
					b.put(dx, 0, dz, MaterialSpec.FENCE);
				}
			}
		}
		return b.build();
	}

	private static Blueprint library() {
		Blueprint.Builder b = Blueprint.builder(Structures.LIBRARY, 5, 5).at(12, -1);
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				boolean edge = dx == 0 || dx == 4 || dz == 0 || dz == 4;
				if (edge && !(dx == 2 && dz == 0)) {
					b.put(dx, 0, dz, MaterialSpec.BOOKSHELF);
				}
			}
		}
		b.put(LIBRARY_TABLE[0], LIBRARY_TABLE[1], LIBRARY_TABLE[2], MaterialSpec.ENCHANTING_TABLE);
		b.attach(0, 1, 0, MaterialSpec.TORCH).attach(4, 1, 0, MaterialSpec.TORCH)
			.attach(0, 1, 4, MaterialSpec.TORCH).attach(4, 1, 4, MaterialSpec.TORCH);
		return b.build();
	}

	private static Blueprint netherPortal() {
		Blueprint.Builder b = Blueprint.builder(Structures.NETHER_PORTAL, 4, 1).at(-3, 15);
		for (int dx = 0; dx < 4; dx++) {
			for (int dy = 0; dy <= 4; dy++) {
				boolean side = dx == 0 || dx == 3;
				boolean end = dy == 0 || dy == 4;
				if (side || end) {
					b.put(dx, dy, 0, side && end ? MaterialSpec.COBBLESTONE : MaterialSpec.OBSIDIAN); // the rest is the opening
				}
			}
		}
		return b.build();
	}

	// --------------------------------------------------------------- tweaks

	/** A gate across the front row (east to west in the plan), standing open. */
	private static BlockState openGate(BlockState s) {
		return s.hasProperty(FenceGateBlock.OPEN)
			? s.setValue(FenceGateBlock.FACING, Direction.NORTH).setValue(FenceGateBlock.OPEN, true) : s;
	}

	private static UnaryOperator<BlockState> facing(Direction direction) {
		return s -> s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(BlockStateProperties.HORIZONTAL_FACING, direction) : s;
	}

	private static UnaryOperator<BlockState> hopper(Direction direction) {
		return s -> s.hasProperty(HopperBlock.FACING) ? s.setValue(HopperBlock.FACING, direction) : s;
	}

	private static BlockState bottomSlab(BlockState s) {
		return s.hasProperty(SlabBlock.TYPE) ? s.setValue(SlabBlock.TYPE, SlabType.BOTTOM) : s;
	}
}
