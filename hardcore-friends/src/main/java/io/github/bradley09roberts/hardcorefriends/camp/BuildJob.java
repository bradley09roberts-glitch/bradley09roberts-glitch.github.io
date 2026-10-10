package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.architecture.MaterialDemand;
import io.github.bradley09roberts.hardcorefriends.architecture.Pots;
import io.github.bradley09roberts.hardcorefriends.architecture.Scaffold;
import io.github.bradley09roberts.hardcorefriends.architecture.Styles;
import io.github.bradley09roberts.hardcorefriends.camp.build.CampFeatures;
import io.github.bradley09roberts.hardcorefriends.camp.build.MaterialSpec;
import io.github.bradley09roberts.hardcorefriends.camp.build.Part;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.camp.build.Supplies;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * One run of building a blueprint, shared by Oak (structures), Spark (contraptions) and the village's builders
 * (library plans on their own sites). A run reserves a site if needed (camp structures only: library sites are
 * reserved by whoever plans them), picks the next batch of up to {@value #BATCH} unbuilt entries, fetches their
 * materials from the supply chest (crafting planks, stairs, doors, beds, torches and the rest from real ingredients,
 * the plan's wood and colours first), then walks round placing them in order. Progress is stored on the site, so an
 * interrupted build carries on where it stopped; a run hands back after a couple of minutes so big buildings never
 * stall a friend.
 *
 * <p>Order: foundations, then each layer of the structure bottom-up, then the attachments bottom-up (windows, doors,
 * furniture, lights). Within a layer the builder takes whatever is in reach first. Doors, beds and tall flowers go
 * down as two halves. Decoration ({@code optional}) is left out while its material cannot be had and added later by
 * the repair job; an entry with a {@code fallback} uses that instead (a torch for a lantern) when its own material
 * cannot be had.
 *
 * <p>Reach: a block a friend cannot reach from the ground is reached from a spot nearby, on a temporary scaffold
 * pillar if need be ({@link Scaffold}); the pillar is taken down as soon as the friend is done up there.
 *
 * <p>Nothing that is not a plant, snow or the friends' own scaffolding is ever broken. If something foreign has
 * appeared in the plan's space since the site was reserved, that entry is simply left out.
 */
public final class BuildJob {
	/** How many entries one run places at most. */
	public static final int BATCH = 24;
	private static final int ENTRY_TIMEOUT = 200;
	/** Footprint columns the site search may test per tick: a big plan tries fewer candidate spots per tick. */
	private static final int SEARCH_AREA_BUDGET = 96;
	/** A run that has gone on this long finishes its batch early and hands back, so it never hits the task's time limit. */
	private static final int RUN_BUDGET = 20 * 150;
	/** The longest a friend spends putting up one pillar before giving up on it. */
	private static final int CLIMB_LIMIT = 20 * 40;
	/**
	 * The longest a friend spends coming down a pillar without getting a block lower before leaving it to the descent
	 * reflex and the clean-up job. Per block, not for the whole pillar: cobblestone dug without a pickaxe takes the
	 * best part of ten seconds a block.
	 */
	private static final int DESCEND_LIMIT = 20 * 30;
	/** How many of a plan's remaining entries are looked through for its material forecast. */
	private static final int FORECAST_SCAN = 4096;
	/**
	 * Ticks a friend stands at the end of the way towards an entry, getting no closer, before looking for a spot to
	 * reach it from: well before the stuck watcher would start nudging them about.
	 */
	private static final int APPROACH_PATIENCE = 30;

	private enum Phase {
		SITE,
		PLAN,
		TO_CHEST,
		TO_TABLE,
		BUILD,
		DESCEND
	}

	/** Why a run ended without success, so tasks can pick their cooldown. */
	public enum Failure {
		NONE,
		NO_SITE,
		NO_TABLE,
		SHORT,
		UNREACHABLE,
		/** The site still has trees on it to fell, or ground to level, first. */
		CLEARING
	}

	private final CompanionEntity c;
	private final Blueprint bp;
	private final String siteKey;
	private final WorldEditGuard.Reason reason;
	private final boolean repair;
	private final boolean campStructure;
	private Phase phase = Phase.SITE;
	private SiteFinder.@Nullable Search search;
	private List<Placement> all = List.of();
	private List<Placement> batch = new ArrayList<>();
	private Map<Stock, Integer> wanted = Map.of();
	private Map<Stock, String> preferences = Map.of();
	private final Map<Stock, Boolean> makeable = new EnumMap<>(Stock.class);
	private @Nullable String siteWood;
	/** The site record this run was planned on; a different one (or none) means it was given up or planned afresh. */
	private CampData.@Nullable Site plannedSite;
	/** Blocks to carry for a scaffold pillar this batch, if it reaches up high (0 if not). */
	private int scaffoldBlocks;
	private @Nullable Set<BlockPos> planCells;
	private @Nullable BlockPos tablePos;
	private int index;
	private int entryTicks;
	private int placed;
	private int skipped;
	private int runTicks;
	private @Nullable BlockPos stepAside;
	private Scaffold.@Nullable Climb climb;
	private int climbTicks;
	private int descendTicks;
	private double descendY;
	private boolean endAfterDescent;
	private final Set<Integer> scaffoldTried = new HashSet<>();
	/** The entry being walked towards, the closest the friend has got to it, and how long they have got no closer. */
	private int approachFor = -1;
	private double approachBest;
	private int approachStill;
	private Failure failure = Failure.NONE;
	private boolean finished;

	public BuildJob(CompanionEntity companion, Blueprint blueprint, WorldEditGuard.Reason reason, boolean repair) {
		this(companion, blueprint, blueprint.id(), reason, repair);
	}

	/**
	 * A run on the site with this key (a camp structure id, or a library site's own key) built from this plan.
	 */
	public BuildJob(CompanionEntity companion, Blueprint blueprint, String siteKey, WorldEditGuard.Reason reason, boolean repair) {
		this.c = companion;
		this.bp = blueprint;
		this.siteKey = siteKey;
		this.reason = reason;
		this.repair = repair;
		this.campStructure = Blueprints.isCampStructure(siteKey);
	}

	public Blueprint blueprint() {
		return bp;
	}

	/** The key of the site this run builds on. */
	public String siteKey() {
		return siteKey;
	}

	public Failure failure() {
		return failure;
	}

	/** True once the whole blueprint stands (or, for a repair, nothing more is missing). */
	public boolean finished() {
		return finished;
	}

	public int placed() {
		return placed;
	}

	private ServerLevel level() {
		return (ServerLevel) c.level();
	}

	private CampData data() {
		return Camp.data(level().getServer());
	}

	/** The building's name in speech ("cabin", "oak cottage"). */
	private String name() {
		return Blueprints.displayName(data(), siteKey);
	}

	public TaskStatus tick() {
		runTicks++;
		return switch (phase) {
			case SITE -> tickSite();
			case PLAN -> plan();
			case TO_CHEST -> tickToChest();
			case TO_TABLE -> tickToTable();
			case BUILD -> tickBuild();
			case DESCEND -> tickDescend();
		};
	}

	public void stop() {
		c.actions().reset();
		stepAside = null;
		climb = null;
		// Up a pillar? The descent reflex takes the friend straight back down.
		Scaffold.release(c);
	}

	// ------------------------------------------------------------------ site

	private TaskStatus tickSite() {
		CampData data = data();
		if (!Camp.isCampLevel(level(), data)) {
			return fail(Failure.NO_SITE); // sites (and the camp's own coordinates) only mean anything in the camp's dimension
		}
		if (Scaffold.onScaffold(c)) {
			// Left up a pillar by an earlier run: come down first.
			startDescent(false);
			return TaskStatus.RUNNING;
		}
		List<Part> parts = SiteFinder.parts(data, siteKey, bp);
		if (!parts.isEmpty() && !repair && !Blueprints.isFinished(data, siteKey) && !insideCamp(parts)) {
			if (!campStructure) {
				return fail(Failure.NO_SITE); // a library site outside the camp: whoever planned it decides what to do
			}
			data.removeSite(siteKey); // the camp has moved since this was planned: start again at the new camp
			parts = List.of();
		}
		if (!parts.isEmpty()) {
			TaskStatus waiting = waitForClearing(data);
			return waiting != null ? waiting : toPlan(parts);
		}
		if (!campStructure) {
			return fail(Failure.NO_SITE); // library sites are reserved by whoever plans them
		}
		List<Part> fixed = SiteFinder.fixedParts(level(), data, bp);
		if (fixed != null) {
			if (fixed.isEmpty()) {
				return fail(Failure.NO_SITE);
			}
			SiteFinder.reserve(data, siteKey, bp, fixed, Styles.woodFor(level(), fixed.getFirst().origin(), bp));
			return toPlan(fixed);
		}
		if (repair) {
			return fail(Failure.NO_SITE);
		}
		if (search == null) {
			search = SiteFinder.search(level(), data, bp);
		}
		List<Part> found = search.step(Math.clamp(SEARCH_AREA_BUDGET / (bp.width() * bp.depth()), 2, 16));
		if (found != null && !SiteFinder.isFree(data, siteKey, bp, found)) {
			// Someone reserved a site there (a village plot) while the search ran: look again.
			search = null;
			return TaskStatus.RUNNING;
		}
		if (found != null) {
			SiteFinder.reserve(data, siteKey, bp, found, Styles.woodFor(level(), found.getFirst().origin(), bp));
			CampNeeds.clearSiteProblem();
			if (search.clearBox().length == 6) {
				SiteClearing.reserve(data, siteKey, search.logsToFell(), search.clearBox());
				HardcoreFriends.LOGGER.info("{} chose a site for the {} with {} logs to fell", c.displayName(), name(),
					search.logsToFell().size());
			}
			if (!search.gradeCut().isEmpty() || !search.gradeFill().isEmpty()) {
				SiteGrading.reserve(data, siteKey, search.gradeCut(), search.gradeFill());
				HardcoreFriends.LOGGER.info("{} chose a site for the {} to level: {} blocks to dig, {} to fill",
					c.displayName(), name(), search.gradeCut().size(), search.gradeFill().size());
			}
			TaskStatus waiting = waitForClearing(data);
			return waiting != null ? waiting : toPlan(found);
		}
		if (search.failed() && Camp.growForRoom(data)) {
			// Nothing fits inside the camp, even levelled: let the camp grow a little and look again, further out.
			String name = name();
			HardcoreFriends.LOGGER.info("{} found no site for the {} ({}); the camp grows to radius {}", c.displayName(), name,
				search.breakdown(), Camp.radius(data));
			Speech.say(c, Line.LOOKING_FURTHER, name);
			search = null;
			return TaskStatus.RUNNING;
		}
		if (search.failed()) {
			String name = name();
			String problem = "a clear, fairly level spot for the " + name + " (" + search.problem() + ")";
			CampNeeds.reportSiteProblem(level(), c.displayName() + " needs " + problem
				+ ". Clearing or levelling a patch inside the camp helps.");
			HardcoreFriends.LOGGER.info("{} found no site for the {} ({})", c.displayName(), name, search.breakdown());
			Speech.say(c, Line.NEED_MATERIALS, problem);
			return fail(Failure.NO_SITE);
		}
		return TaskStatus.RUNNING;
	}

	/**
	 * In a forest camp the site may still have natural trees or overhanging leaves on it: the clearing job (Rowan
	 * first, the builder too) takes those away, and building waits until then.
	 */
	private @Nullable TaskStatus waitForClearing(CampData data) {
		Optional<SiteClearing.Job> job = SiteClearing.job(data, siteKey);
		if (job.isEmpty() || !SiteClearing.pending(level(), job.get())) {
			return waitForGrading(data);
		}
		String name = name();
		CampNeeds.reportSiteProblem(level(), c.displayName() + " is waiting for the trees to be cleared off the "
			+ name + " site.");
		Speech.say(c, Line.NEED_MATERIALS, "the trees cleared off the " + name + " site");
		return fail(Failure.CLEARING);
	}

	/**
	 * On uneven ground the site may still need levelling: the levelling job (Terra first, the builder too) digs the
	 * bumps away and fills the dips, and building waits until then. Once it is done the levelling plan is retired
	 * ({@link SiteGrading#stillToDo}) before the first block goes down, so nobody digs the new floor back out.
	 */
	private @Nullable TaskStatus waitForGrading(CampData data) {
		Optional<SiteGrading.Job> job = SiteGrading.job(data, siteKey);
		if (job.isEmpty() || !SiteGrading.stillToDo(level(), data, job.get())) {
			return null;
		}
		String name = name();
		CampNeeds.reportSiteProblem(level(), c.displayName() + " is waiting for the " + name + " site to be levelled.");
		Speech.say(c, Line.NEED_MATERIALS, "the " + name + " site levelled first");
		return fail(Failure.CLEARING);
	}

	private boolean insideCamp(List<Part> parts) {
		for (Part part : parts) {
			if (!WorldEditGuard.inCamp(c, part.origin())) {
				return false;
			}
		}
		return true;
	}

	private TaskStatus toPlan(List<Part> parts) {
		all = Blueprints.placements(bp, parts);
		String recorded = Blueprints.siteWood(data(), siteKey);
		siteWood = recorded != null ? recorded : bp.wood();
		phase = Phase.PLAN;
		return TaskStatus.RUNNING;
	}

	// ------------------------------------------------------------------ plan

	/** What is at a placement's position right now. */
	private enum Slot {
		BUILT,
		FOREIGN,
		PLANT,
		EMPTY
	}

	private Slot slot(Placement p) {
		return slot(level(), p);
	}

	private static Slot slot(ServerLevel level, Placement p) {
		if (!level.isLoaded(p.pos())) {
			return Slot.FOREIGN;
		}
		BlockState s = level.getBlockState(p.pos());
		Blueprint.Entry e = p.entry();
		MaterialSpec m = e.material();
		if (e.isBuilt(s)) {
			return Slot.BUILT;
		}
		if (m.isSecondHalf()) {
			BlockPos first = firstHalfPos(p);
			BlockState firstState = level.getBlockState(first);
			boolean pending = firstState.isAir() || WorldEditGuard.isClearablePlant(firstState);
			if (!isFirstHalf(m, firstState) && !pending) {
				return Slot.FOREIGN; // the first half was left out, so its other half is too
			}
		}
		if (s.isAir()) {
			return Slot.EMPTY;
		}
		if (WorldEditGuard.isClearablePlant(s) || SiteClearing.isNaturalLeaves(s)) {
			return Slot.PLANT;
		}
		return Slot.FOREIGN;
	}

	/** Where the first half of a second-half entry stands: below a door's top or a tall flower's top, behind a bed's head. */
	private static BlockPos firstHalfPos(Placement p) {
		if (p.entry().material() == MaterialSpec.BED_HEAD) {
			Direction facing = facingOf(p);
			return p.pos().relative(facing.getOpposite());
		}
		return p.pos().below();
	}

	/** The horizontal facing the entry's own tweak gives it on this site. */
	private static Direction facingOf(Placement p) {
		BlockState sample = p.entry().tweak().apply(p.entry().material().sample().defaultBlockState())
			.rotate(Blueprint.rotation(p.rotation()));
		return sample.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? sample.getValue(BlockStateProperties.HORIZONTAL_FACING)
			: Direction.NORTH;
	}

	private static boolean isFirstHalf(MaterialSpec second, BlockState state) {
		return switch (second) {
			case DOOR_TOP -> state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
			case BED_HEAD -> state.getBlock() instanceof AbstractBedBlock && state.getValue(AbstractBedBlock.PART) == BedPart.FOOT;
			case TALL_FLOWER_TOP -> state.getBlock() instanceof TallFlowerBlock && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
			default -> false;
		};
	}

	private boolean needsWork(Placement p) {
		Slot s = slot(p);
		return s == Slot.EMPTY || s == Slot.PLANT;
	}

	/** Needs work and is not decoration whose material (and fallback) cannot be had right now. */
	private boolean needsWorkNow(Placement p) {
		Blueprint.Entry e = p.entry();
		return needsWork(p) && (!e.optional() || available(e) || e.fallback() != null && available(e.withFallback()));
	}

	/**
	 * Blocks of a finished structure that have gone missing: places where the plan has a block but there is only
	 * air or a plant now. Foundations are not counted. Returns at most {@code limit} placements.
	 */
	public static List<Placement> missing(ServerLevel level, CampData data, Blueprint bp, int limit) {
		return missing(level, data, bp.id(), bp, limit);
	}

	/** {@link #missing(ServerLevel, CampData, Blueprint, int)} for the site with this key. */
	public static List<Placement> missing(ServerLevel level, CampData data, String siteKey, Blueprint bp, int limit) {
		List<Placement> list = new ArrayList<>();
		List<Part> parts = SiteFinder.parts(data, siteKey, bp);
		if (parts.isEmpty()) {
			return list;
		}
		for (Placement p : Blueprints.placements(bp, parts)) {
			if (list.size() >= limit) {
				break;
			}
			if (!p.isFoundation() && p.entry().material() != MaterialSpec.AIR) {
				Slot s = slot(level, p);
				if (s == Slot.EMPTY || s == Slot.PLANT) {
					list.add(p);
				}
			}
		}
		return list;
	}

	/** True if what this entry is made of can be had now: carried, in the chest, or craftable from what is (cached per run). */
	private boolean available(Blueprint.Entry e) {
		Stock s = e.material().stock();
		Stock extra = e.material().extraStock();
		return (s == null || canMake(s)) && (extra == null || canMake(extra));
	}

	private boolean canMake(Stock s) {
		return makeable.computeIfAbsent(s, k -> Supplies.canMake(c, SupplyChest.of(level()).orElse(null), k, 1));
	}

	private TaskStatus plan() {
		CampData data = data();
		Optional<CampData.Site> site = data.site(siteKey);
		if (site.isEmpty()) {
			return fail(Failure.NO_SITE);
		}
		plannedSite = site.get();
		makeable.clear();
		batch = new ArrayList<>();
		if (repair) {
			for (Placement p : all) {
				if (!p.isFoundation() && batch.size() < BATCH && needsWorkNow(p)) {
					batch.add(p);
				}
			}
		} else {
			int progress = Math.clamp(site.get().progress, 0, all.size());
			while (progress < all.size() && !needsWork(all.get(progress))) {
				progress++;
			}
			setProgress(site.get(), progress);
			for (int i = progress; i < all.size() && batch.size() < BATCH; i++) {
				if (needsWorkNow(all.get(i))) {
					batch.add(all.get(i));
				}
			}
			forecast(progress);
		}
		if (batch.isEmpty()) {
			return complete();
		}
		// Anything whose own material cannot be had is built from its fallback, if that can be had.
		batch.replaceAll(p -> {
			Blueprint.Entry e = p.entry();
			if (e.fallback() == null || available(e)) {
				return p;
			}
			Blueprint.Entry alt = e.withFallback();
			return available(alt) ? new Placement(p.index(), p.part(), p.pos(), alt, p.rotation()) : p;
		});
		wanted = materials(batch);
		preferences = preferences(batch);
		// Something up high may need a pillar: blocks for one are taken along if the chest has them (they come back when
		// it is taken down), but they are not part of what the batch needs, so a camp without any is not held up or
		// reported short until a pillar is actually wanted (see approach).
		scaffoldBlocks = FriendsConfig.get().allowScaffolding && highestAboveGround(batch) >= 4
			? FriendsConfig.get().maxScaffoldHeight : 0;
		Optional<Container> chest = SupplyChest.of(level());
		if (carries(wanted) && !(data.chestPos().isPresent() && wantsScaffoldBlocks(chest.orElse(null)))) {
			return startBuilding(Map.of());
		}
		if (chest.isPresent() && data.chestPos().isPresent()) {
			phase = Phase.TO_CHEST;
			return TaskStatus.RUNNING;
		}
		// No supply chest yet (Oak building the first one): use what is carried.
		Supplies supplies = new Supplies(c, null, Crafting.nearCraftingTable(c)).prefer(preferences);
		Map<Stock, Integer> missing = supplies.gather(wanted);
		if (supplies.needsTable()) {
			return goToTable(missing);
		}
		return startBuilding(missing);
	}

	/** How many blocks above the site's ground floor the highest entry of the batch is. */
	private int highestAboveGround(List<Placement> list) {
		int base = data().site(siteKey).map(s -> s.origin.getY()).orElse(c.getBlockY());
		int top = Integer.MIN_VALUE;
		for (Placement p : list) {
			top = Math.max(top, p.pos().getY() - base);
		}
		return top;
	}

	/** Tells the camp what the rest of this building calls for, so the materials can be gathered and made ahead. */
	private void forecast(int from) {
		Map<Stock, Integer> remaining = new EnumMap<>(Stock.class);
		int end = Math.min(all.size(), from + FORECAST_SCAN);
		for (int i = from; i < end; i++) {
			Blueprint.Entry e = all.get(i).entry();
			Stock s = e.material().stock();
			if (s == null) {
				continue; // decoration counts too: asking for its glass is how the windows get glass later
			}
			if (!needsWork(all.get(i))) {
				continue;
			}
			remaining.merge(s, 1, Integer::sum);
			Stock extra = e.material().extraStock();
			if (extra != null) {
				remaining.merge(extra, 1, Integer::sum);
			}
		}
		MaterialDemand.report(level(), siteKey, remaining);
	}

	private static Map<Stock, Integer> materials(List<Placement> list) {
		Map<Stock, Integer> map = new EnumMap<>(Stock.class);
		for (Placement p : list) {
			Stock s = p.entry().material().stock();
			if (s != null) {
				map.merge(s, 1, Integer::sum);
			}
			Stock extra = p.entry().material().extraStock();
			if (extra != null) {
				map.merge(extra, 1, Integer::sum);
			}
		}
		return map;
	}

	/** The wood or colour each kind of the batch should be, by majority (the entry's own wish, else the site's wood). */
	private Map<Stock, String> preferences(List<Placement> list) {
		Map<Stock, Map<String, Integer>> votes = new EnumMap<>(Stock.class);
		for (Placement p : list) {
			String wish = wish(p.entry());
			Stock s = p.entry().material().stock();
			if (wish != null && s != null) {
				votes.computeIfAbsent(s, k -> new HashMap<>()).merge(wish, 1, Integer::sum);
			}
		}
		Map<Stock, String> chosen = new EnumMap<>(Stock.class);
		votes.forEach((s, v) -> v.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(w -> chosen.put(s, w.getKey())));
		return chosen;
	}

	/** The wood or colour an entry would like: its own, or for wooden parts the site's wood. */
	private @Nullable String wish(Blueprint.Entry e) {
		if (e.variant() != null) {
			return e.variant();
		}
		return e.material().variant() == MaterialSpec.Variant.WOOD ? siteWood : null;
	}

	private boolean carries(Map<Stock, Integer> map) {
		for (Map.Entry<Stock, Integer> e : map.entrySet()) {
			if (c.backpack().count(e.getKey().item()) < e.getValue()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * True if this batch may need a pillar and the chest can top up the blocks carried for one: dirt above all, which
	 * digs out in a moment by hand where cobblestone takes the best part of ten seconds a block without a pickaxe (and
	 * the builder carries an axe), else cobblestone.
	 */
	private boolean wantsScaffoldBlocks(@Nullable Container chest) {
		if (scaffoldBlocks <= 0 || chest == null) {
			return false;
		}
		Backpack bp = c.backpack();
		if (bp.count(Items.DIRT) < scaffoldBlocks && SupplyChest.count(chest, s -> s.is(Items.DIRT)) > 0) {
			return true;
		}
		return bp.count(Stock.FILL.item()) < scaffoldBlocks && SupplyChest.count(chest, Stock.FILL.item()) > 0;
	}

	/** Tops up the blocks carried for a pillar from the chest, dirt first ({@link #wantsScaffoldBlocks}). */
	private void takeScaffoldBlocks(Container chest) {
		if (scaffoldBlocks <= 0) {
			return;
		}
		Backpack bp = c.backpack();
		int dirt = bp.count(Items.DIRT);
		if (dirt < scaffoldBlocks) {
			SupplyChest.withdraw(chest, bp, s -> s.is(Items.DIRT), scaffoldBlocks - dirt);
		}
		int fill = bp.count(Stock.FILL.item());
		if (fill < scaffoldBlocks) {
			SupplyChest.withdraw(chest, bp, Stock.FILL.item(), scaffoldBlocks - fill);
		}
	}

	// ----------------------------------------------------------------- fetch

	private TaskStatus tickToChest() {
		Optional<BlockPos> chestPos = data().chestPos();
		Optional<Container> chest = SupplyChest.of(level());
		if (chestPos.isEmpty() || chest.isEmpty()) {
			phase = Phase.PLAN;
			return TaskStatus.RUNNING;
		}
		Actions actions = c.actions();
		if (!(actions.canReach(chestPos.get()) && c.position().distanceToSqr(Vec3.atBottomCenterOf(chestPos.get())) < 9) && !actions.walkTo(chestPos.get(), 2.0)) {
			return actions.isStuck() ? fail(Failure.UNREACHABLE) : TaskStatus.RUNNING;
		}
		actions.stopWalking();
		c.getLookControl().setLookAt(Vec3.atCenterOf(chestPos.get()));
		makeRoom(chest.get());
		Supplies supplies = new Supplies(c, chest.get(), Crafting.nearCraftingTable(c)).prefer(preferences);
		Map<Stock, Integer> missing = supplies.gather(wanted);
		takeScaffoldBlocks(chest.get());
		if (supplies.needsTable()) {
			return goToTable(missing);
		}
		return startBuilding(missing);
	}

	private TaskStatus goToTable(Map<Stock, Integer> missing) {
		tablePos = findTable();
		if (tablePos == null) {
			return fail(Failure.NO_TABLE);
		}
		phase = Phase.TO_TABLE;
		return TaskStatus.RUNNING;
	}

	private TaskStatus tickToTable() {
		if (tablePos == null || !level().getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
			return fail(Failure.NO_TABLE);
		}
		Actions actions = c.actions();
		if (!Crafting.nearCraftingTable(c) || c.position().distanceToSqr(Vec3.atBottomCenterOf(tablePos)) > 16) {
			if (!actions.walkTo(tablePos, 2.0)) {
				return actions.isStuck() ? fail(Failure.NO_TABLE) : TaskStatus.RUNNING;
			}
		}
		actions.stopWalking();
		c.getLookControl().setLookAt(Vec3.atCenterOf(tablePos));
		Supplies supplies = new Supplies(c, null, true).prefer(preferences);
		return startBuilding(supplies.gather(wanted));
	}

	/** A crafting table near the friend, the supply chest or the camp centre. */
	private @Nullable BlockPos findTable() {
		Optional<BlockPos> near = Crafting.findNearby(c, 8);
		if (near.isPresent()) {
			return near.get();
		}
		CampData data = data();
		for (BlockPos centre : new BlockPos[] {data.chestPos().orElse(null), data.campPos().orElse(null)}) {
			if (centre != null) {
				BlockPos found = CampFeatures.find(level(), centre, 6, s -> s.is(Blocks.CRAFTING_TABLE));
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** Puts things this job does not need into the chest when the backpack is nearly full. */
	private void makeRoom(Container chest) {
		Backpack backpack = c.backpack();
		if (backpack.freeSlots() >= 3) {
			return;
		}
		Predicate<ItemStack> keep = s -> s.isDamageableItem() || CompanionEntity.isEdible(s);
		for (Stock s : wanted.keySet()) {
			keep = keep.or(s.item());
		}
		for (Stock s : List.of(Stock.LOG, Stock.PLANKS, Stock.STICK, Stock.COBBLESTONE, Stock.FILL)) {
			keep = keep.or(s.item());
		}
		Predicate<ItemStack> keepFinal = keep;
		SupplyChest.deposit(backpack, chest, s -> !keepFinal.test(s), 64 * 27);
	}

	/**
	 * Decides how much of the batch can be built from what is now carried. Reports any shortage to the camp so
	 * gatherers prioritise it. Fails when not even the first entry can be built.
	 */
	private TaskStatus startBuilding(Map<Stock, Integer> missing) {
		// Build everything carried for, but never start a higher layer while the one below still has gaps. Decoration
		// that could not be had is simply left out (the repair job adds it later): it never holds up the layers above,
		// such as a carpet row holding back the door tops and lights.
		Map<Stock, Integer> carried = new EnumMap<>(Stock.class);
		List<Placement> buildable = new ArrayList<>();
		Set<BlockPos> leftOut = new HashSet<>();
		Placement gap = null;
		for (Placement p : batch) {
			if (gap != null && (gap.entry().attachment() != p.entry().attachment() || gap.entry().dy() != p.entry().dy())) {
				break;
			}
			if (p.entry().material().isSecondHalf() && leftOut.contains(firstHalfPos(p))) {
				continue; // its first half was left out
			}
			if (!carriedFor(p, carried)) {
				if (p.entry().optional()) {
					leftOut.add(p.pos());
					continue;
				}
				gap = p;
				continue;
			}
			buildable.add(p);
		}
		Map<Stock, Integer> stillMissing = new EnumMap<>(Stock.class);
		for (Map.Entry<Stock, Integer> e : missing.entrySet()) {
			int have = c.backpack().count(e.getKey().item());
			int need = wanted.getOrDefault(e.getKey(), 0);
			if (have < need) {
				stillMissing.put(e.getKey(), need - have);
			}
		}
		if (!stillMissing.isEmpty()) {
			String text = Supplies.describe(stillMissing);
			CampNeeds.reportBuildShortage(level(), siteKey, c, Supplies.needs(stillMissing), text);
			Speech.say(c, Line.NEED_MATERIALS, text);
		} else {
			CampNeeds.clearBuildShortage(siteKey); // supplied: this building is short of nothing now
		}
		if (buildable.isEmpty()) {
			return fail(Failure.SHORT);
		}
		batch = buildable;
		index = 0;
		entryTicks = 0;
		phase = Phase.BUILD;
		return TaskStatus.RUNNING;
	}

	/** True if what this placement needs is carried, beyond what earlier placements of the batch already use. */
	private boolean carriedFor(Placement p, Map<Stock, Integer> carried) {
		Stock s = p.entry().material().stock();
		Stock extra = p.entry().material().extraStock();
		for (Stock k : new Stock[] {s, extra}) {
			if (k != null && c.backpack().count(k.item()) < carried.getOrDefault(k, 0) + 1) {
				return false;
			}
		}
		for (Stock k : new Stock[] {s, extra}) {
			if (k != null) {
				carried.merge(k, 1, Integer::sum);
			}
		}
		return true;
	}

	// ----------------------------------------------------------------- build

	/** True while the site this run was planned on is still reserved: not given up, or planned afresh, meanwhile. */
	private boolean siteHeld() {
		CampData.Site held = plannedSite;
		return held == null || data().site(siteKey).orElse(null) == held;
	}

	private TaskStatus tickBuild() {
		if (runTicks % 20 == 0 && !siteHeld()) {
			// The site was given up (the village replanned): place nothing more there, but come down from any pillar first.
			climb = null;
			return endBatch(Scaffold.onScaffold(c));
		}
		if (climb != null) {
			return tickClimb();
		}
		boolean onScaffold = Scaffold.onScaffold(c);
		if (onScaffold) {
			Scaffold.markBusy(c);
		}
		if (index >= batch.size() || runTicks > RUN_BUDGET) {
			return endBatch(onScaffold);
		}
		Placement p = batch.get(index);
		if (++entryTicks > ENTRY_TIMEOUT) {
			return skip();
		}
		Slot slot = slot(p);
		if (slot == Slot.BUILT || slot == Slot.FOREIGN) {
			return next(false);
		}
		Actions actions = c.actions();
		BlockPos pos = p.pos();
		if (stepAside != null) {
			if (actions.walkTo(stepAside, 0.5) || actions.isStuck()) {
				stepAside = null;
				actions.stopWalking();
			}
			return TaskStatus.RUNNING;
		}
		boolean inMyWay = c.getBoundingBox().intersects(new AABB(pos));
		if (!actions.canReach(pos) || onScaffold && inMyWay) {
			if (pullReachableForward()) {
				return TaskStatus.RUNNING;
			}
			if (onScaffold) {
				startDescent(false);
				return TaskStatus.RUNNING;
			}
			return approach(p);
		}
		actions.stopWalking();
		if (slot == Slot.PLANT) {
			Actions.Result r = actions.mine(pos, reason);
			if (r == Actions.Result.FAILED) {
				return skip();
			}
			return TaskStatus.RUNNING;
		}
		MaterialSpec material = p.entry().material();
		if (material == MaterialSpec.AIR) {
			return next(false); // an empty cell that should be empty
		}
		ServerLevel level = level();
		if (level.getGameTime() - c.lastEditTick() < 4) {
			return TaskStatus.RUNNING; // the guard paces edits
		}
		BlockState state;
		ItemStack chosen = ItemStack.EMPTY;
		ItemStack flower = ItemStack.EMPTY;
		if (material.isSecondHalf()) {
			state = secondHalf(level, p);
			if (state == null) {
				return skip(); // the first half could not be placed
			}
		} else {
			ItemStack found = choose(p.entry());
			if (found.isEmpty()) {
				return endBatch(onScaffold); // something took our materials; plan again next time
			}
			chosen = found.copyWithCount(1);
			state = p.entry().stateFor(chosen, Blueprint.rotation(p.rotation()));
			if (material == MaterialSpec.POTTED_FLOWER) {
				flower = c.backpack().find(Stock.FLOWER::matches);
				BlockState potted = flower.isEmpty() ? null : Pots.pottedFor(flower);
				if (potted == null) {
					return skip();
				}
				state = potted;
				flower = flower.copyWithCount(1);
			}
			if (state.getBlock() instanceof CrossCollisionBlock || state.getBlock() instanceof WallBlock
				|| state.getBlock() instanceof StairBlock) {
				state = Block.updateFromNeighbourShapes(state, level, pos);
			}
		}
		WorldEditGuard.Verdict verdict = WorldEditGuard.canPlace(c, pos, state, reason);
		if (!verdict.allowed()) {
			if ("someone is standing there".equals(verdict.why())) {
				if (inMyWay && !onScaffold) {
					stepAside = freeSpotNear(pos);
					if (stepAside == null) {
						return skip();
					}
				}
				return TaskStatus.RUNNING; // wait for the other to move (or time out)
			}
			if ("pacing".equals(verdict.why())) {
				return TaskStatus.RUNNING;
			}
			return skip();
		}
		boolean ok;
		if (material.isSecondHalf()) {
			ok = WorldEditGuard.placeBlock(c, pos, state, reason);
			if (ok) {
				c.swingArm();
			}
		} else {
			ItemStack template = chosen;
			ok = actions.place(pos, state, s -> ItemStack.isSameItemSameComponents(s, template), reason);
			if (ok && !flower.isEmpty()) {
				ItemStack flowerTemplate = flower;
				c.backpack().remove(s -> ItemStack.isSameItemSameComponents(s, flowerTemplate), 1);
			}
		}
		if (!ok) {
			return skip();
		}
		placed++;
		onPlaced(p);
		return next(true);
	}

	/** The item to build an entry from: one of the wood or colour it would like if carried, otherwise any that fits. */
	private ItemStack choose(Blueprint.Entry e) {
		MaterialSpec m = e.material();
		String wish = wish(e);
		if (wish != null) {
			ItemStack preferred = c.backpack().find(s -> m.accepts(s) && m.prefers(s, wish));
			if (!preferred.isEmpty()) {
				return preferred;
			}
		}
		if (m == MaterialSpec.FOUNDATION || m == MaterialSpec.DIRT) {
			ItemStack dirt = c.backpack().find(s -> s.is(Items.DIRT));
			if (!dirt.isEmpty()) {
				return dirt; // dirt before cobblestone: cobblestone is worth more
			}
		}
		return c.backpack().find(m::accepts);
	}

	/** The second half of a two-part block, made from its first half; null if the first half is not there. */
	private static @Nullable BlockState secondHalf(ServerLevel level, Placement p) {
		BlockPos firstPos = firstHalfPos(p);
		BlockState first = level.getBlockState(firstPos);
		MaterialSpec m = p.entry().material();
		if (!isFirstHalf(m, first)) {
			return null;
		}
		return switch (m) {
			case DOOR_TOP -> first.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
			case TALL_FLOWER_TOP -> first.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
			case BED_HEAD -> first.getValue(BlockStateProperties.HORIZONTAL_FACING) == facingOf(p)
				? first.setValue(AbstractBedBlock.PART, BedPart.HEAD) : null;
			default -> null;
		};
	}

	/**
	 * Moves the next entry of the same layer that is in reach right now (and is not the second half of something) to
	 * the front, so the builder works round what is close instead of walking or climbing for each block in drawing
	 * order. True if one was found.
	 */
	private boolean pullReachableForward() {
		Placement current = batch.get(index);
		for (int i = index + 1; i < batch.size(); i++) {
			Placement other = batch.get(i);
			if (other.entry().attachment() != current.entry().attachment() || other.entry().dy() != current.entry().dy()) {
				break;
			}
			if (other.entry().material().isSecondHalf() || !c.actions().canReach(other.pos())
				|| c.getBoundingBox().intersects(new AABB(other.pos())) || !needsWork(other)) {
				continue;
			}
			batch.set(i, current);
			batch.set(index, other);
			entryTicks = 0;
			return true;
		}
		return false;
	}

	/**
	 * Gets within reach of an entry: walks there if it can be reached from the ground; if it is too high, or there is
	 * no way to it (the walk stops short of reach, as it does below a wall or roof block), stands somewhere nearby that
	 * reaches it, on a scaffold pillar if need be. Each entry gets one try at a scaffold; after that it is skipped (and
	 * picked up by a later run).
	 */
	private TaskStatus approach(Placement p) {
		Actions actions = c.actions();
		BlockPos pos = p.pos();
		double dx = c.getX() - (pos.getX() + 0.5);
		double dz = c.getZ() - (pos.getZ() + 0.5);
		boolean tooHigh = dx * dx + dz * dz <= 2.5 * 2.5 && pos.getY() > c.getBlockY() + 3;
		if (!tooHigh) {
			actions.walkTo(pos, 2.0);
			if (!actions.isStuck() && !stoppedShort(p)) {
				return TaskStatus.RUNNING;
			}
			actions.stopWalking();
		}
		if (!scaffoldTried.add(p.index())) {
			return skip();
		}
		List<BlockPos> others = new ArrayList<>();
		for (int i = index + 1; i < batch.size() && others.size() < 16; i++) {
			others.add(batch.get(i).pos());
		}
		Scaffold.Spot spot = Scaffold.plan(c, pos, planCells(), others);
		if (spot == null) {
			return skip();
		}
		if (spot.height() > 0 && !Scaffold.canScaffold(c)) {
			if (FriendsConfig.get().allowScaffolding) {
				reportScaffoldShortage(spot.height());
				Speech.say(c, Line.NEED_MATERIALS, "some dirt or cobblestone for scaffolding");
			}
			return skip();
		}
		if (spot.height() > 0) {
			Speech.say(c, Line.SCAFFOLDING, name());
		}
		climb = new Scaffold.Climb(spot);
		climbTicks = 0;
		return TaskStatus.RUNNING;
	}

	/**
	 * True once the walk towards this entry has ended short of reach: the friend has stood at the end of the way there
	 * for {@value #APPROACH_PATIENCE} ticks without getting any closer. Walking on the spot until the walk counts as
	 * stuck would only have the stuck watcher hop and push them about beside the wall.
	 */
	private boolean stoppedShort(Placement p) {
		double dist = c.position().distanceTo(Vec3.atCenterOf(p.pos()));
		if (approachFor != p.index() || dist < approachBest - 0.25) {
			approachFor = p.index();
			approachBest = dist;
			approachStill = 0;
			return false;
		}
		if (c.getNavigation().isDone()) {
			approachStill++; // standing at the end of the way there (walking round a corner does not count)
		}
		return approachStill > APPROACH_PATIENCE;
	}

	/**
	 * A pillar is wanted and there is nothing to build it from: adds dirt or cobblestone to what the camp is told this
	 * building is short of (keeping anything already reported), so the gatherers bring some.
	 */
	private void reportScaffoldShortage(int blocks) {
		CampNeeds.addBuildShortage(level(), siteKey, c, Supplies.needs(Map.of(Stock.FILL, blocks)),
			Stock.FILL.describe(blocks));
	}

	/** Every cell the plan uses on this site (no scaffold may stand in one), worked out once per run. */
	private Set<BlockPos> planCells() {
		Set<BlockPos> cells = planCells;
		if (cells == null) {
			cells = new HashSet<>();
			for (Placement p : all) {
				if (!p.isFoundation()) {
					cells.add(p.pos());
				}
			}
			planCells = cells;
		}
		return cells;
	}

	private TaskStatus tickClimb() {
		Scaffold.Climb current = climb;
		if (current == null) {
			return TaskStatus.RUNNING;
		}
		if (++climbTicks > CLIMB_LIMIT) {
			climb = null;
			if (Scaffold.onScaffold(c)) {
				startDescent(false);
				return TaskStatus.RUNNING;
			}
			return skip();
		}
		switch (current.tick(c)) {
			case DONE -> {
				climb = null;
				entryTicks = 0; // now in reach: the entry gets its full time
			}
			case FAILED -> {
				climb = null;
				c.actions().stopWalking();
				if (Scaffold.onScaffold(c)) {
					startDescent(false);
					return TaskStatus.RUNNING;
				}
				return skip();
			}
			case WORKING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	private void startDescent(boolean thenEnd) {
		c.actions().stopWalking();
		phase = Phase.DESCEND;
		descendTicks = 0;
		descendY = c.getY();
		endAfterDescent = thenEnd;
	}

	private TaskStatus tickDescend() {
		Scaffold.markBusy(c);
		Scaffold.Step step = Scaffold.descend(c);
		if (c.getY() < descendY - 0.5) {
			descendY = c.getY(); // a block lower: the next block gets its own time
			descendTicks = 0;
		}
		if (step == Scaffold.Step.DONE || ++descendTicks > DESCEND_LIMIT) {
			c.actions().cancelMining();
			if (step != Scaffold.Step.DONE) {
				Scaffold.release(c); // could not get down (a player right by the pillar): the reflex keeps trying
				return fail(Failure.UNREACHABLE);
			}
			if (endAfterDescent || all.isEmpty()) {
				phase = all.isEmpty() ? Phase.SITE : Phase.BUILD;
				return all.isEmpty() ? TaskStatus.RUNNING : finishBatch();
			}
			phase = Phase.BUILD;
			entryTicks = 0;
		}
		return TaskStatus.RUNNING;
	}

	private void onPlaced(Placement p) {
		if (bp == Blueprints.SUPPLY_CHEST && data().chestPos().isEmpty()) {
			data().setChestPos(p.pos()); // usable straight away
		}
	}

	private TaskStatus next(boolean progressMade) {
		index++;
		entryTicks = 0;
		if (progressMade && !repair) {
			data().site(siteKey).ifPresent(site -> {
				int progress = site.progress;
				while (progress < all.size() && !needsWork(all.get(progress))) {
					progress++;
				}
				setProgress(site, progress);
			});
		}
		return TaskStatus.RUNNING;
	}

	private TaskStatus skip() {
		skipped++;
		c.actions().reset();
		stepAside = null;
		return next(false);
	}

	/** Ends the batch: first down from any scaffold, then {@link #finishBatch}. */
	private TaskStatus endBatch(boolean onScaffold) {
		if (onScaffold) {
			startDescent(true);
			return TaskStatus.RUNNING;
		}
		return finishBatch();
	}

	private TaskStatus finishBatch() {
		c.actions().reset();
		if (!siteHeld()) {
			return fail(Failure.NO_SITE);
		}
		if (placed == 0 && skipped > 0) {
			return fail(Failure.UNREACHABLE);
		}
		for (Placement p : all) {
			if ((!repair || !p.isFoundation()) && needsWorkNow(p)) {
				return TaskStatus.SUCCESS; // more to do next run
			}
		}
		return complete();
	}

	private TaskStatus complete() {
		CampData data = data();
		data.site(siteKey).ifPresent(site -> setProgress(site, all.size()));
		finished = true;
		if (!repair) {
			MaterialDemand.clear(siteKey);
			if (campStructure) {
				if (bp == Blueprints.SUPPLY_CHEST && data.chestPos().isEmpty()) {
					SiteFinder.parts(data, siteKey, bp).stream().findFirst().map(Part::origin)
						.filter(origin -> SupplyChest.isValidStorage(level(), origin)).ifPresent(data::setChestPos);
				}
				CampProgress.complete(c, siteKey);
			} else if (!Blueprints.isFinished(data, siteKey)) {
				Blueprints.markFinished(data, siteKey, true);
				Speech.say(c, Line.BUILDING_FINISHED, name());
				Construction.fireFinished(level(), siteKey, bp);
			}
			CampNeeds.clearBuildShortage(siteKey);
		}
		return TaskStatus.SUCCESS;
	}

	private TaskStatus fail(Failure why) {
		failure = why;
		c.actions().reset();
		return TaskStatus.FAILURE;
	}

	private void setProgress(CampData.Site site, int progress) {
		if (site.progress != progress) {
			site.progress = progress;
			data().setDirty();
		}
	}

	/** A spot two or three blocks away where the friend can stand clear of the block being placed. */
	private @Nullable BlockPos freeSpotNear(BlockPos pos) {
		ServerLevel level = level();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int d = Math.max(Math.abs(dx), Math.abs(dz));
				if (d < 2) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					m.set(pos.getX() + dx, c.blockPosition().getY() + dy, pos.getZ() + dz);
					if (standable(level, m)) {
						double dist = m.distSqr(c.blockPosition());
						if (dist < bestDist) {
							bestDist = dist;
							best = m.immutable();
						}
					}
				}
			}
		}
		return best;
	}

	private boolean standable(ServerLevel level, BlockPos feet) {
		BlockState below = level.getBlockState(feet.below());
		return below.isFaceSturdy(level, feet.below(), Direction.UP)
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
			&& level.getFluidState(feet).isEmpty();
	}
}
