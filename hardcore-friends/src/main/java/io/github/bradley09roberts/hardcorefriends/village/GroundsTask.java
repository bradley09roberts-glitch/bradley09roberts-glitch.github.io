package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.Crops;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.EditSteps;
import io.github.bradley09roberts.hardcorefriends.ai.role.terra.ChestFetch;
import io.github.bradley09roberts.hardcorefriends.ai.role.terra.Landscape;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.progress.Water;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * The farmer looks after the village's grounds once they stand: water for the wells, the fountain and the wheat field's
 * channels, the wheat field's soil and crop, and the orchard's young trees.
 * <ul>
 * <li><b>Water.</b> Plans cannot place water, so their {@code water} spots wait for a bucket. Only into a basin that
 * holds it: every water spot must have something solid under it and, on every side, either a solid block or another
 * water spot, so not a drop can run out. One bucket at a time, into a water spot that is still dry, through the edit
 * guard (farm work may pour water inside the camp); the water then runs on along the basin by itself, and spots it
 * cannot reach get a bucket of their own later. The bucket is filled at water that tops itself up at once (a pool's
 * source with two others beside it), so the world never loses water; or the camp's water bucket is used.</li>
 * <li><b>The wheat field.</b> The soil between the field's two corner spots is tilled with a hoe where water is within
 * four blocks, sown with seeds (wheat first) and its ripe crop harvested; the next visit sows it again.</li>
 * <li><b>The orchard.</b> A sapling (from the backpack or the chest) on each {@code sapling} spot that is bare earth.</li>
 * </ul>
 * One friend at a time (an exclusive job), by day, up to {@value #PER_RUN} cells a run. A piece of work that came to
 * nothing (no water that tops itself up within reach of the basin, cells nobody can get to) is set aside for a few
 * minutes, so the grounds after it (the field, the orchard) are not kept waiting behind it.
 */
final class GroundsTask implements CompanionTask {
	static final String ID = "village.grounds";
	private static final int PER_RUN = 16;
	private static final int CHOOSE_INTERVAL = 200;
	private static final double REACH = 2.5;
	private static final int HYDRATION = 4;
	/** How long a piece of the grounds' work that came to nothing is left before it is tried again. */
	private static final int SET_ASIDE = 20 * 60 * 5;

	/** What a run does. */
	private enum Work {
		WATER,
		FIELD,
		SAPLINGS
	}

	private record Plan(Work work, String siteKey, List<BlockPos> cells) {
	}

	private @Nullable Plan chosen;
	private long chosenAt = Long.MIN_VALUE / 2;
	/** Work set aside after a run that came to nothing ({@code WATER@siteKey}), until this game time. */
	private final Map<String, Long> setAside = new HashMap<>();
	private long now;
	private @Nullable Plan plan;
	private final List<BlockPos> todo = new ArrayList<>();
	private @Nullable BlockPos current;
	private @Nullable BlockPos source;
	private int worked;
	private boolean fetched;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Plan p = plan;
		if (p == null) {
			return "tending the village grounds";
		}
		return switch (p.work()) {
			case WATER -> "fetching water for the village";
			case FIELD -> "tending the village's wheat field";
			case SAPLINGS -> "planting the orchard";
		};
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.isChild() || !(c.level() instanceof ServerLevel level) || Camp.isNight(level) || !Routine.inVillage(c)) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - chosenAt >= CHOOSE_INTERVAL || now < chosenAt) {
			chosenAt = now;
			chosen = choose(c, level);
		}
		return chosen == null ? 0 : chosen.work() == Work.WATER ? 46 : 42;
	}

	/**
	 * The first piece of the grounds that wants doing and can be done with what is carried or stored, passing over
	 * work set aside after a run that came to nothing.
	 */
	private @Nullable Plan choose(CompanionEntity c, ServerLevel level) {
		VillageData v = VillageData.get(level.getServer());
		CampData camp = Camp.data(level.getServer());
		long time = level.getGameTime();
		setAside.values().removeIf(until -> until <= time || until - time > SET_ASIDE);
		for (VillageData.Plot p : v.plots()) {
			if (!p.standing()) {
				continue;
			}
			Blueprint bp = Blueprints.forSite(camp, p.siteKey).orElse(null);
			if (bp == null) {
				continue;
			}
			if ("water".equals(bp.meta().get("needs")) && hasWater(c) && !setAside.containsKey(key(Work.WATER, p.siteKey))) {
				List<BlockPos> dry = dryWaterSpots(level, p.siteKey);
				if (!dry.isEmpty()) {
					return new Plan(Work.WATER, p.siteKey, dry);
				}
			}
			if (!bp.marker("field").isEmpty() && !setAside.containsKey(key(Work.FIELD, p.siteKey))) {
				List<BlockPos> cells = fieldWork(c, level, p.siteKey);
				if (!cells.isEmpty()) {
					return new Plan(Work.FIELD, p.siteKey, cells);
				}
			}
			if (!bp.marker("sapling").isEmpty() && has(c, s -> s.is(ItemTags.SAPLINGS))
				&& !setAside.containsKey(key(Work.SAPLINGS, p.siteKey))) {
				List<BlockPos> spots = new ArrayList<>();
				for (BlockPos s : Construction.markers(level, p.siteKey, "sapling")) {
					if (level.isLoaded(s) && level.getBlockState(s).isAir() && level.getBlockState(s.below()).is(BlockTags.DIRT)) {
						spots.add(s);
					}
				}
				if (!spots.isEmpty()) {
					return new Plan(Work.SAPLINGS, p.siteKey, spots);
				}
			}
		}
		return null;
	}

	private static String key(Work work, String siteKey) {
		return work + "@" + siteKey;
	}

	/** Carried, or in the supply chest. */
	private static boolean has(CompanionEntity c, Predicate<ItemStack> filter) {
		return c.backpack().has(filter) || ChestFetch.chestHas(c, filter);
	}

	/** A water bucket, or an empty one to fill, carried or in the chest. */
	private static boolean hasWater(CompanionEntity c) {
		return has(c, s -> s.is(Items.WATER_BUCKET) || s.is(Items.BUCKET));
	}

	// ------------------------------------------------------------------- water

	/**
	 * The water spots still dry, if the whole basin holds water: each spot solid underneath and, on every side, a solid
	 * block or another water spot. Empty if any spot could let water out (a rim block missing) or nothing is dry.
	 */
	static List<BlockPos> dryWaterSpots(ServerLevel level, String siteKey) {
		List<BlockPos> spots = Construction.markers(level, siteKey, "water");
		Set<BlockPos> set = new HashSet<>(spots);
		List<BlockPos> dry = new ArrayList<>();
		for (BlockPos w : spots) {
			if (!level.isLoaded(w)) {
				return List.of();
			}
			BlockState here = level.getBlockState(w);
			boolean wet = !here.getFluidState().isEmpty();
			if (!wet && !here.isAir()) {
				return List.of(); // something stands in the basin
			}
			// Every spot, wet or dry, must hold its water: a basin with a gap anywhere gets no more.
			BlockState below = level.getBlockState(w.below());
			if (!below.isFaceSturdy(level, w.below(), Direction.UP) && !below.getFluidState().is(FluidTags.WATER)) {
				return List.of();
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = w.relative(d);
				if (set.contains(n)) {
					continue;
				}
				BlockState side = level.getBlockState(n);
				if (side.isAir() || side.canBeReplaced() || !side.getFluidState().isEmpty()
					|| !side.isFaceSturdy(level, n, d.getOpposite())) {
					return List.of(); // the water could run out this way
				}
			}
			if (!wet) {
				dry.add(w);
			}
		}
		return dry;
	}

	// ------------------------------------------------------------------- field

	/** The field's soil between its two corner spots (the layer the corners are on). */
	private static List<BlockPos> soil(ServerLevel level, String siteKey) {
		List<BlockPos> corners = Construction.markers(level, siteKey, "field");
		List<BlockPos> cells = new ArrayList<>();
		if (corners.size() < 2) {
			return cells;
		}
		BlockPos a = corners.get(0);
		BlockPos b = corners.get(1);
		for (int x = Math.min(a.getX(), b.getX()); x <= Math.max(a.getX(), b.getX()); x++) {
			for (int z = Math.min(a.getZ(), b.getZ()); z <= Math.max(a.getZ(), b.getZ()); z++) {
				cells.add(new BlockPos(x, a.getY(), z));
			}
		}
		return cells;
	}

	/** Field cells with something to do now: earth to till (with a hoe, by water), farmland to sow, ripe crops to harvest. */
	private static List<BlockPos> fieldWork(CompanionEntity c, ServerLevel level, String siteKey) {
		boolean hoe = c.actions().hasTool(ItemTags.HOES);
		boolean seeds = has(c, Crops.IS_SEED);
		List<BlockPos> soil = soil(level, siteKey);
		List<BlockPos> list = new ArrayList<>();
		if (soil.isEmpty()) {
			return list;
		}
		// The water round the field, looked up once: a few hundred block reads, not one search per cell.
		List<BlockPos> water = new ArrayList<>();
		BlockPos first = soil.getFirst();
		BlockPos last = soil.getLast();
		for (BlockPos p : BlockPos.betweenClosed(first.offset(-HYDRATION, 0, -HYDRATION), last.offset(HYDRATION, 1, HYDRATION))) {
			if (level.isLoaded(p) && level.getFluidState(p).is(FluidTags.WATER)) {
				water.add(p.immutable());
			}
		}
		for (BlockPos cell : soil) {
			if (!level.isLoaded(cell)) {
				continue;
			}
			BlockState s = level.getBlockState(cell);
			BlockState above = level.getBlockState(cell.above());
			if (hoe && Landscape.isEarth(s) && above.isAir() && near(water, cell)
				|| seeds && s.is(Blocks.FARMLAND) && above.isAir()
				|| Crops.isRipe(level, cell.above(), above)) {
				list.add(cell);
			}
		}
		return list;
	}

	private static boolean near(List<BlockPos> water, BlockPos cell) {
		for (BlockPos w : water) {
			if (Math.abs(w.getX() - cell.getX()) <= HYDRATION && Math.abs(w.getZ() - cell.getZ()) <= HYDRATION
				&& w.getY() >= cell.getY() && w.getY() <= cell.getY() + 1) {
				return true;
			}
		}
		return false;
	}

	/** Water within {@value #HYDRATION} blocks across, on the soil's level or one above: farmland there stays moist. */
	private static boolean nearWater(ServerLevel level, BlockPos cell) {
		for (BlockPos p : BlockPos.betweenClosed(cell.offset(-HYDRATION, 0, -HYDRATION), cell.offset(HYDRATION, 1, HYDRATION))) {
			if (level.getFluidState(p).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	// -------------------------------------------------------------------- run

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Plan p = choose(c, level);
		chosenAt = Long.MIN_VALUE / 2;
		if (p == null) {
			return false;
		}
		plan = p;
		todo.clear();
		todo.addAll(p.cells());
		todo.sort(java.util.Comparator.comparingDouble(b -> b.distSqr(c.blockPosition())));
		current = null;
		source = null;
		worked = 0;
		fetched = false;
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		now = level.getGameTime();
		Plan p = plan;
		if (p == null || Camp.isNight(level)) {
			return finish();
		}
		return switch (p.work()) {
			case WATER -> water(c, level, p);
			case FIELD -> field(c, level);
			case SAPLINGS -> saplings(c, level);
		};
	}

	private TaskStatus water(CompanionEntity c, ServerLevel level, Plan p) {
		if (worked >= 3) {
			return finish(); // a few buckets a run; the water runs on by itself meanwhile
		}
		boolean full = c.backpack().has(s -> s.is(Items.WATER_BUCKET));
		if (!full && !c.backpack().has(s -> s.is(Items.BUCKET))) {
			if (fetched) {
				return finish();
			}
			ChestFetch.Result r = ChestFetch.step(c, s -> s.is(Items.WATER_BUCKET), 1);
			if (r == ChestFetch.Result.FAILED) {
				r = ChestFetch.step(c, s -> s.is(Items.BUCKET), 1);
			}
			if (r != ChestFetch.Result.RUNNING) {
				fetched = true;
			}
			return TaskStatus.RUNNING;
		}
		if (!full) {
			// An empty bucket: fill it at water that tops itself up.
			if (source == null) {
				source = Water.find(level, c.blockPosition(), true);
				if (source == null) {
					return finish();
				}
			}
			BlockPos src = source;
			if (!c.actions().canReach(src)) {
				c.actions().walkTo(src, 2.0);
				if (c.actions().isStuck()) {
					return finish();
				}
				return TaskStatus.RUNNING;
			}
			c.actions().stopWalking();
			if (!Water.fillBucket(c, src)) {
				return finish();
			}
			source = null;
			return TaskStatus.RUNNING;
		}
		if (current == null) {
			List<BlockPos> dry = dryWaterSpots(level, p.siteKey());
			if (dry.isEmpty()) {
				return finish();
			}
			dry.sort(java.util.Comparator.comparingDouble(b -> b.distSqr(c.blockPosition())));
			current = dry.getFirst();
		}
		BlockPos spot = current;
		if (!c.actions().canReach(spot)) {
			c.actions().walkTo(spot, REACH);
			if (c.actions().isStuck()) {
				return finish();
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (!level.getBlockState(spot).isAir() || !dryWaterSpots(level, p.siteKey()).contains(spot)) {
			current = null; // the water got there by itself, or the basin is no longer whole
			return TaskStatus.RUNNING;
		}
		return switch (EditSteps.place(c, spot, Blocks.WATER.defaultBlockState(), s -> s.is(Items.WATER_BUCKET), Reason.FARM)) {
			case DONE -> {
				give(c, new ItemStack(Items.BUCKET));
				worked++;
				current = null;
				yield TaskStatus.RUNNING;
			}
			case WAIT -> TaskStatus.RUNNING;
			case FAILED -> finish();
		};
	}

	private TaskStatus field(CompanionEntity c, ServerLevel level) {
		if (current == null) {
			if (worked >= PER_RUN || todo.isEmpty()) {
				return finish();
			}
			current = todo.removeFirst();
		}
		BlockPos cell = current;
		if (!fetched && !c.backpack().has(Crops.IS_SEED) && ChestFetch.chestHas(c, Crops.IS_SEED)
			&& level.getBlockState(cell).is(Blocks.FARMLAND)) {
			ChestFetch.Result r = ChestFetch.step(c, s -> s.is(Items.WHEAT_SEEDS), 16);
			if (r == ChestFetch.Result.FAILED) {
				r = ChestFetch.step(c, Crops.IS_SEED, 16);
			}
			if (r != ChestFetch.Result.RUNNING) {
				fetched = true;
			}
			return TaskStatus.RUNNING;
		}
		if (!near(c, cell)) {
			return TaskStatus.RUNNING;
		}
		BlockState s = level.getBlockState(cell);
		BlockState above = level.getBlockState(cell.above());
		if (Crops.isRipe(level, cell.above(), above)) {
			Actions.Result r = c.actions().mine(cell.above(), Reason.FARM);
			if (r != Actions.Result.RUNNING) {
				worked += r == Actions.Result.DONE ? 1 : 0;
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		if (Landscape.isEarth(s) && above.isAir() && nearWater(level, cell)) {
			EditSteps.Step step = EditSteps.transform(c, cell, Blocks.FARMLAND.defaultBlockState(), Reason.FARM, ItemTags.HOES);
			if (step != EditSteps.Step.WAIT) {
				worked += step == EditSteps.Step.DONE ? 1 : 0;
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		if (s.is(Blocks.FARMLAND) && above.isAir()) {
			Item seed = c.backpack().has(st -> st.is(Items.WHEAT_SEEDS)) ? Items.WHEAT_SEEDS : Crops.bestSeed(c.backpack());
			BlockState crop = seed == null ? null : Crops.cropFor(seed);
			if (crop == null) {
				current = null;
				return TaskStatus.RUNNING;
			}
			Item use = seed;
			EditSteps.Step step = EditSteps.place(c, cell.above(), crop, st -> st.is(use), Reason.FARM);
			if (step != EditSteps.Step.WAIT) {
				worked += step == EditSteps.Step.DONE ? 1 : 0;
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		current = null;
		return TaskStatus.RUNNING;
	}

	private TaskStatus saplings(CompanionEntity c, ServerLevel level) {
		if (!c.backpack().has(s -> s.is(ItemTags.SAPLINGS))) {
			if (fetched) {
				return finish();
			}
			ChestFetch.Result r = ChestFetch.step(c, s -> s.is(ItemTags.SAPLINGS), todo.size());
			if (r != ChestFetch.Result.RUNNING) {
				fetched = true;
			}
			return TaskStatus.RUNNING;
		}
		if (current == null) {
			if (todo.isEmpty()) {
				return finish();
			}
			current = todo.removeFirst();
		}
		BlockPos spot = current;
		if (!near(c, spot)) {
			return TaskStatus.RUNNING;
		}
		ItemStack sapling = c.backpack().find(s -> s.is(ItemTags.SAPLINGS) && s.getItem() instanceof BlockItem);
		if (sapling.isEmpty() || !level.getBlockState(spot).isAir()) {
			current = null;
			return TaskStatus.RUNNING;
		}
		Item item = sapling.getItem();
		BlockState state = ((BlockItem) item).getBlock().defaultBlockState();
		if (!state.canSurvive(level, spot)) {
			current = null;
			return TaskStatus.RUNNING;
		}
		EditSteps.Step step = EditSteps.place(c, spot, state, s -> s.is(item), Reason.FARM);
		if (step != EditSteps.Step.WAIT) {
			worked += step == EditSteps.Step.DONE ? 1 : 0;
			current = null;
		}
		return TaskStatus.RUNNING;
	}

	/** Walks within working reach of a cell; true once there. A cell they cannot get to is skipped. */
	private boolean near(CompanionEntity c, BlockPos cell) {
		if (c.actions().canReach(cell) && c.position().distanceTo(Vec3.atBottomCenterOf(cell.above())) <= REACH + 1) {
			c.actions().stopWalking();
			return true;
		}
		c.actions().walkTo(cell.above(), REACH);
		if (c.actions().isStuck()) {
			c.actions().stopWalking();
			current = null;
		}
		return false;
	}

	private static void give(CompanionEntity c, ItemStack stack) {
		ItemStack left = c.backpack().insert(stack);
		if (!left.isEmpty()) {
			c.spawnAtLocation((ServerLevel) c.level(), left);
		}
	}

	private TaskStatus finish() {
		current = null;
		Plan p = plan;
		if (worked == 0 && p != null) {
			setAside.put(key(p.work(), p.siteKey()), now + SET_ASIDE); // the next run tries the rest of the grounds
		}
		return worked > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().cancelMining();
		plan = null;
		current = null;
		source = null;
		todo.clear();
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int failureCooldown() {
		return 1200;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
