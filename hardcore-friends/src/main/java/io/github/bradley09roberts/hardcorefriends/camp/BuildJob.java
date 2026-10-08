package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
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
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * One run of building a blueprint, shared by Oak (structures) and Spark (contraptions). A run reserves a site if
 * needed, picks the next batch of up to {@value #BATCH} unbuilt entries, fetches their materials from the supply
 * chest (crafting planks, slabs, doors, torches and the rest from real ingredients), then walks round placing them
 * in order. Progress is stored on the site, so an interrupted build carries on where it stopped.
 *
 * <p>Nothing that is not a plant or snow is ever broken. If something foreign has appeared in the plan's space since
 * the site was reserved, that entry is simply left out.
 */
public final class BuildJob {
	/** How many entries one run places at most. */
	public static final int BATCH = 24;
	private static final int ENTRY_TIMEOUT = 200;
	/** Footprint columns the site search may test per tick: a big plan tries fewer candidate spots per tick. */
	private static final int SEARCH_AREA_BUDGET = 96;

	private enum Phase {
		SITE,
		PLAN,
		TO_CHEST,
		TO_TABLE,
		BUILD
	}

	/** Why a run ended without success, so tasks can pick their cooldown. */
	public enum Failure {
		NONE,
		NO_SITE,
		NO_TABLE,
		SHORT,
		UNREACHABLE,
		/** The site still has trees on it to fell first. */
		CLEARING
	}

	private final CompanionEntity c;
	private final Blueprint bp;
	private final WorldEditGuard.Reason reason;
	private final boolean repair;
	private Phase phase = Phase.SITE;
	private SiteFinder.@Nullable Search search;
	private List<Placement> all = List.of();
	private List<Placement> batch = new ArrayList<>();
	private Map<Stock, Integer> wanted = Map.of();
	private @Nullable BlockPos tablePos;
	private int index;
	private int entryTicks;
	private int placed;
	private int skipped;
	private @Nullable BlockPos stepAside;
	private Failure failure = Failure.NONE;
	private boolean finished;

	public BuildJob(CompanionEntity companion, Blueprint blueprint, WorldEditGuard.Reason reason, boolean repair) {
		this.c = companion;
		this.bp = blueprint;
		this.reason = reason;
		this.repair = repair;
	}

	public Blueprint blueprint() {
		return bp;
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

	public TaskStatus tick() {
		return switch (phase) {
			case SITE -> tickSite();
			case PLAN -> plan();
			case TO_CHEST -> tickToChest();
			case TO_TABLE -> tickToTable();
			case BUILD -> tickBuild();
		};
	}

	public void stop() {
		c.actions().reset();
		stepAside = null;
	}

	// ------------------------------------------------------------------ site

	private TaskStatus tickSite() {
		CampData data = data();
		List<Part> parts = SiteFinder.parts(data, bp);
		if (!parts.isEmpty() && !repair && !data.isCompleted(bp.id()) && !insideCamp(parts)) {
			data.removeSite(bp.id()); // the camp has moved since this was planned: start again at the new camp
			parts = List.of();
		}
		if (!parts.isEmpty()) {
			TaskStatus waiting = waitForClearing(data);
			return waiting != null ? waiting : toPlan(parts);
		}
		List<Part> fixed = SiteFinder.fixedParts(level(), data, bp);
		if (fixed != null) {
			if (fixed.isEmpty()) {
				return fail(Failure.NO_SITE);
			}
			SiteFinder.reserve(data, bp, fixed);
			return toPlan(fixed);
		}
		if (repair) {
			return fail(Failure.NO_SITE);
		}
		if (search == null) {
			search = SiteFinder.search(level(), data, bp);
		}
		List<Part> found = search.step(Math.clamp(SEARCH_AREA_BUDGET / (bp.width() * bp.depth()), 2, 16));
		if (found != null) {
			SiteFinder.reserve(data, bp, found);
			CampNeeds.clearSiteProblem();
			if (search.clearBox().length == 6) {
				SiteClearing.reserve(data, bp.id(), search.logsToFell(), search.clearBox());
				HardcoreFriends.LOGGER.info("{} chose a site for the {} with {} logs to fell", c.displayName(),
					Structures.get(bp.id()).displayName(), search.logsToFell().size());
			}
			TaskStatus waiting = waitForClearing(data);
			return waiting != null ? waiting : toPlan(found);
		}
		if (search.failed()) {
			String name = Structures.get(bp.id()).displayName();
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
		Optional<SiteClearing.Job> job = SiteClearing.job(data, bp.id());
		if (job.isEmpty() || !SiteClearing.pending(level(), job.get())) {
			return null;
		}
		String name = Structures.get(bp.id()).displayName();
		CampNeeds.reportSiteProblem(level(), c.displayName() + " is waiting for the trees to be cleared off the "
			+ name + " site.");
		Speech.say(c, Line.NEED_MATERIALS, "the trees cleared off the " + name + " site");
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
		MaterialSpec m = p.entry().material();
		if (m.isBuilt(s)) {
			return Slot.BUILT;
		}
		if (m == MaterialSpec.DOOR_TOP) {
			BlockState below = level.getBlockState(p.pos().below());
			boolean pending = below.isAir() || WorldEditGuard.isClearablePlant(below);
			if (!isLowerDoor(below) && !pending) {
				return Slot.FOREIGN; // the door was left out, so its top is too
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

	private static boolean isLowerDoor(BlockState state) {
		return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
	}

	private boolean needsWork(Placement p) {
		Slot s = slot(p);
		return s == Slot.EMPTY || s == Slot.PLANT;
	}

	/**
	 * Blocks of a finished structure that have gone missing: places where the plan has a block but there is only
	 * air or a plant now. Foundations are not counted. Returns at most {@code limit} placements.
	 */
	public static List<Placement> missing(ServerLevel level, CampData data, Blueprint bp, int limit) {
		List<Placement> list = new ArrayList<>();
		List<Part> parts = SiteFinder.parts(data, bp);
		if (parts.isEmpty()) {
			return list;
		}
		for (Placement p : Blueprints.placements(bp, parts)) {
			if (list.size() >= limit) {
				break;
			}
			if (!p.isFoundation()) {
				Slot s = slot(level, p);
				if (s == Slot.EMPTY || s == Slot.PLANT) {
					list.add(p);
				}
			}
		}
		return list;
	}

	private TaskStatus plan() {
		CampData data = data();
		Optional<CampData.Site> site = data.site(bp.id());
		if (site.isEmpty()) {
			return fail(Failure.NO_SITE);
		}
		batch = new ArrayList<>();
		if (repair) {
			for (Placement p : all) {
				if (!p.isFoundation() && batch.size() < BATCH && needsWork(p)) {
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
				if (needsWork(all.get(i))) {
					batch.add(all.get(i));
				}
			}
		}
		if (batch.isEmpty()) {
			return complete();
		}
		Optional<Container> chest = SupplyChest.of(level());
		if (batch.stream().anyMatch(p -> p.entry().material() == MaterialSpec.GLASS_PANE)
			&& !Supplies.canMake(c, chest.orElse(null), Stock.GLASS_PANE, 1)) {
			batch.replaceAll(BuildJob::withoutGlass); // nobody can make glass: close the windows with planks
		}
		wanted = materials(batch);
		if (carries(wanted)) {
			return startBuilding(Map.of());
		}
		if (chest.isPresent() && data.chestPos().isPresent()) {
			phase = Phase.TO_CHEST;
			return TaskStatus.RUNNING;
		}
		// No supply chest yet (Oak building the first one): use what is carried.
		Supplies supplies = new Supplies(c, null, Crafting.nearCraftingTable(c));
		Map<Stock, Integer> missing = supplies.gather(wanted);
		if (supplies.needsTable()) {
			return goToTable(missing);
		}
		return startBuilding(missing);
	}

	/**
	 * A window without glass: when neither the backpack nor the chest holds glass panes or glass to make them, the
	 * window is filled with planks instead, so the building can still be finished. The plan still asks for glass, so
	 * the plank counts as something else standing there and is left alone afterwards.
	 */
	private static Placement withoutGlass(Placement p) {
		Blueprint.Entry e = p.entry();
		if (e.material() != MaterialSpec.GLASS_PANE) {
			return p;
		}
		Blueprint.Entry planks = new Blueprint.Entry(e.dx(), e.dy(), e.dz(), MaterialSpec.PLANKS, UnaryOperator.identity(), e.attachment());
		return new Placement(p.index(), p.part(), p.pos(), planks, p.rotation());
	}

	private static Map<Stock, Integer> materials(List<Placement> list) {
		Map<Stock, Integer> map = new EnumMap<>(Stock.class);
		for (Placement p : list) {
			Stock s = p.entry().material().stock();
			if (s != null) {
				map.merge(s, 1, Integer::sum);
			}
		}
		return map;
	}

	private boolean carries(Map<Stock, Integer> map) {
		for (Map.Entry<Stock, Integer> e : map.entrySet()) {
			if (c.backpack().count(e.getKey().item()) < e.getValue()) {
				return false;
			}
		}
		return true;
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
		Supplies supplies = new Supplies(c, chest.get(), Crafting.nearCraftingTable(c));
		Map<Stock, Integer> missing = supplies.gather(wanted);
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
		Supplies supplies = new Supplies(c, null, true);
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
		for (Stock s : Stock.values()) {
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
		// Build everything carried for, but never start a higher layer while the one below still has gaps.
		Map<Stock, Integer> carried = new EnumMap<>(Stock.class);
		List<Placement> buildable = new ArrayList<>();
		Placement gap = null;
		for (Placement p : batch) {
			if (gap != null && (gap.entry().attachment() != p.entry().attachment() || gap.entry().dy() != p.entry().dy())) {
				break;
			}
			Stock s = p.entry().material().stock();
			if (s != null) {
				int used = carried.getOrDefault(s, 0) + 1;
				if (c.backpack().count(s.item()) < used) {
					gap = p;
					continue;
				}
				carried.put(s, used);
			}
			buildable.add(p);
		}
		if (!missing.isEmpty()) {
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
				CampNeeds.reportBuildShortage(level(), Supplies.needs(stillMissing), text);
				Speech.say(c, Line.NEED_MATERIALS, text);
			}
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

	// ----------------------------------------------------------------- build

	private TaskStatus tickBuild() {
		if (index >= batch.size()) {
			return endBatch();
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
		if (!actions.canReach(pos)) {
			actions.walkTo(pos, 2.0);
			if (actions.isStuck()) {
				actions.stopWalking();
				return skip();
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		if (slot == Slot.PLANT) {
			Actions.Result r = actions.mine(pos, reason);
			if (r == Actions.Result.FAILED) {
				return skip();
			}
			return TaskStatus.RUNNING;
		}
		ServerLevel level = level();
		if (level.getGameTime() - c.lastEditTick() < 4) {
			return TaskStatus.RUNNING; // the guard paces edits
		}
		MaterialSpec material = p.entry().material();
		BlockState state;
		ItemStack chosen = ItemStack.EMPTY;
		if (material == MaterialSpec.DOOR_TOP) {
			BlockState below = level.getBlockState(pos.below());
			if (!isLowerDoor(below)) {
				return skip(); // the lower half could not be placed
			}
			state = below.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
		} else {
			ItemStack found = c.backpack().find(material::accepts);
			if (found.isEmpty()) {
				return endBatch(); // something took our materials; plan again next time
			}
			chosen = found.copyWithCount(1);
			state = p.entry().stateFor(chosen, Blueprint.rotation(p.rotation()));
			if (state.getBlock() instanceof CrossCollisionBlock) {
				state = Block.updateFromNeighbourShapes(state, level, pos);
			}
		}
		WorldEditGuard.Verdict verdict = WorldEditGuard.canPlace(c, pos, state, reason);
		if (!verdict.allowed()) {
			if ("someone is standing there".equals(verdict.why())) {
				if (c.getBoundingBox().intersects(new AABB(pos))) {
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
		if (material == MaterialSpec.DOOR_TOP) {
			ok = WorldEditGuard.placeBlock(c, pos, state, reason);
			if (ok) {
				c.swingArm();
			}
		} else {
			ItemStack template = chosen;
			ok = actions.place(pos, state, s -> ItemStack.isSameItemSameComponents(s, template), reason);
		}
		if (!ok) {
			return skip();
		}
		placed++;
		onPlaced(p);
		return next(true);
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
			data().site(bp.id()).ifPresent(site -> {
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

	private TaskStatus endBatch() {
		c.actions().reset();
		if (placed == 0 && skipped > 0) {
			return fail(Failure.UNREACHABLE);
		}
		for (Placement p : all) {
			if ((!repair || !p.isFoundation()) && needsWork(p)) {
				return TaskStatus.SUCCESS; // more to do next run
			}
		}
		return complete();
	}

	private TaskStatus complete() {
		CampData data = data();
		data.site(bp.id()).ifPresent(site -> setProgress(site, all.size()));
		finished = true;
		if (!repair) {
			if (bp == Blueprints.SUPPLY_CHEST && data.chestPos().isEmpty()) {
				SiteFinder.parts(data, bp).stream().findFirst().map(Part::origin)
					.filter(origin -> SupplyChest.isValidStorage(level(), origin)).ifPresent(data::setChestPos);
			}
			CampProgress.complete(c, bp.id());
			CampNeeds.reportBuildShortage(level(), Map.of(), "");
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
