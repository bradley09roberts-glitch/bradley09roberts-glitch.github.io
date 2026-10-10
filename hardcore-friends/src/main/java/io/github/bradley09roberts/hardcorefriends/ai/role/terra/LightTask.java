package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Terra spawn-proofs the camp: finds dark ground (block light below 8) in camp, away from paths, farmland, buildings
 * and unfinished building sites, and places torches at least 5 blocks apart, up to 6 per run. Torches come from the
 * backpack, are crafted from carried coal and sticks, or are fetched from the supply chest. Lighting is a nicety: the
 * torches the camp's unfinished buildings still call for, the village's houses and shops under way included (and the
 * coal to make them), stay in the chest for the builders.
 *
 * <p>The look for dark ground is the team's, one at a time, a few hundred columns a step ({@link Survey}), so a camp
 * grown to the size of its village costs no more in any one tick than a small one, however many friends light it.
 * After dark only the heart of the camp is lit ({@link Camp#coreRadius}, the camp without its village): the village's
 * outer streets and plots wait for the day (and have their lamp posts), so lighting never keeps a friend out late.
 */
public final class LightTask implements CompanionTask {
	/** Lighting a dark camp at night: above bedtime for a rested landscaper (75), so the camp is lit before she sleeps. */
	private static final double NIGHT_SCORE = 80;
	private static final int PER_RUN = 6;
	private static final int DARK = 8;
	private static final double SPACING = 5;
	private static final int SCAN_INTERVAL = 200;
	private static final int LATTICE = 3;
	/** Columns of the lattice looked at per step of the team's look, at most one step every {@value #STEP_INTERVAL} ticks. */
	private static final int COLUMNS_PER_STEP = 250;
	private static final int STEP_INTERVAL = 20;
	private static final int LOOK_ABOVE = 4;
	/**
	 * Where the ground {@value #LOOK_ABOVE} blocks above the camp's is solid, the look starts again from this high: the
	 * village builds on ground up to eight blocks above the camp's, which would otherwise never be lit.
	 */
	private static final int HILL_ABOVE = 9;
	private static final int MAX_DROP = 8;
	private static final double WORK_REACH = 2.5;

	private static final Predicate<ItemStack> TORCH = s -> s.is(Items.TORCH);
	private static final Predicate<ItemStack> COAL = s -> s.is(ItemTags.COALS);
	private static final Predicate<ItemStack> STICK = s -> s.is(Items.STICK);
	private static final Predicate<ItemStack> STICK_MAKINGS = s -> s.is(Items.STICK) || s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS);

	private enum Phase {
		FETCH,
		LIGHT
	}

	private static final String SURVEY = "terra.dark_spots";

	private final List<BlockPos> darkSpots = new ArrayList<>();
	/** Which of the team's looks {@link #darkSpots} was copied from. */
	private int copiedPass = -1;

	/** What a look for dark spots needs to know about the camp: worked out once a look, not once a column. */
	private record Layout(BlockPos centre, List<BlockPos> pathTargets, List<BlockPos> unfinishedSites) {
		static Layout of(CampData data) {
			BlockPos centre = data.campPos().orElse(BlockPos.ZERO);
			return new Layout(centre, PathPlan.targets(data), Landscape.unfinishedSiteOrigins(data));
		}
	}

	/**
	 * The team's look for dark spots in the camp, one for everyone (whatever their role) in this dimension, kept in the
	 * {@link TeamCache}. A look goes over a coarse lattice of the camp a step at a time, shifting the lattice each look
	 * to cover every block in the end; a new one starts {@value #SCAN_INTERVAL} ticks after the last finished, or
	 * straight away once torches have been placed.
	 */
	private static final class Survey {
		/** When the last look finished (game time); long ago means a new look is wanted now. */
		private long at = Long.MIN_VALUE / 2;
		/** How many looks have finished, so each friend knows when theirs is out of date. */
		private int pass;
		private List<BlockPos> spots = List.of();
		private long lastStep = Long.MIN_VALUE / 2;
		// The look under way.
		private boolean looking;
		private int cursor;
		private int lookCount;
		private int radius;
		private int offX;
		private int offZ;
		private BlockPos centre = BlockPos.ZERO;
		private @Nullable Layout layout;
		private final List<BlockPos> found = new ArrayList<>();

		/** Looks at the next stretch of the camp, at most once every {@value #STEP_INTERVAL} ticks. */
		void step(ServerLevel level, CampData data, long now) {
			if (now >= lastStep && now - lastStep < STEP_INTERVAL) {
				return; // several friends may ask in the same second
			}
			lastStep = now;
			BlockPos home = data.campPos().orElseThrow();
			int r = Math.max(0, Camp.radius(data) - 1);
			Layout plan = layout;
			if (!looking || plan == null || r != radius || !home.equals(centre)) {
				looking = true;
				cursor = 0;
				radius = r;
				centre = home;
				offX = lookCount % LATTICE;
				offZ = (lookCount / LATTICE) % LATTICE;
				lookCount++;
				plan = Layout.of(data);
				layout = plan;
				found.clear();
			}
			int across = (2 * r - offX) / LATTICE + 1;
			int down = (2 * r - offZ) / LATTICE + 1;
			int total = across * down;
			int budget = COLUMNS_PER_STEP;
			while (budget > 0 && cursor < total) {
				int dx = -r + offX + (cursor % across) * LATTICE;
				int dz = -r + offZ + (cursor / across) * LATTICE;
				cursor++;
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				budget--;
				BlockPos spot = darkSpot(level, data, plan, centre.getX() + dx, centre.getZ() + dz);
				if (spot != null) {
					found.add(spot);
				}
			}
			if (cursor >= total) {
				looking = false;
				spots = List.copyOf(found);
				found.clear();
				layout = null;
				at = now;
				pass++;
			}
		}

		/** Torches went up: light has changed, so a new look is wanted (one under way carries on). */
		void lightChanged() {
			at = Long.MIN_VALUE / 2;
		}
	}

	private final List<BlockPos> placedThisRun = new ArrayList<>();
	private Phase phase = Phase.LIGHT;
	private @Nullable BlockPos current;

	@Override
	public String id() {
		return "terra.light";
	}

	@Override
	public String describe() {
		return "lighting the camp";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (data.campPos().isEmpty() || !Camp.isCampLevel(level, data)) {
			return 0;
		}
		boolean night = Camp.isNight(level);
		if (night && !WorldEditGuard.inCamp(c, c.blockPosition())) {
			return 0;
		}
		refreshSpots(level, data);
		if (darkSpots.isEmpty() || night && !anyInHeart(data) || !torchesAvailable(c)) {
			return 0;
		}
		// A dark camp at night is where monsters spawn: lighting it comes before bed while there is energy for it.
		return night ? NIGHT_SCORE : 50;
	}

	/** True if a dark spot lies in the heart of the camp ({@link Camp#coreRadius}), the part lit after dark. */
	private boolean anyInHeart(CampData data) {
		BlockPos centre = data.campPos().orElseThrow();
		for (BlockPos p : darkSpots) {
			if (inHeart(data, centre, p)) {
				return true;
			}
		}
		return false;
	}

	private static boolean inHeart(CampData data, BlockPos centre, BlockPos p) {
		int core = Camp.coreRadius(data);
		return Camp.horizontalDistSqr(p, centre) <= (double) core * core;
	}

	/**
	 * Torches carried or spare in the chest, or coal (carried or spare) and something to make sticks from (carried or
	 * in the chest).
	 */
	private static boolean torchesAvailable(CompanionEntity c) {
		var bp = c.backpack();
		if (bp.has(TORCH)) {
			return true;
		}
		Spare spare = Spare.of(c);
		if (spare.torches() > 0) {
			return true;
		}
		boolean coal = bp.has(COAL) || spare.coal() > 0;
		return coal && (bp.has(STICK_MAKINGS) || ChestFetch.chestHas(c, STICK_MAKINGS));
	}

	/** What the chest can give for lighting: torches and coal beyond what unfinished buildings need. */
	private record Spare(int torches, int coal) {
		static Spare of(CompanionEntity c) {
			ServerLevel level = (ServerLevel) c.level();
			Optional<Container> chest = SupplyChest.of(level);
			if (chest.isEmpty()) {
				return new Spare(0, 0);
			}
			int torches = SupplyChest.count(chest.get(), TORCH);
			int coal = SupplyChest.count(chest.get(), COAL);
			int reserved = TeamCache.get(level, "terra.torch_reserve", Reserve::new).of(level);
			int coalReserved = (Math.max(0, reserved - torches) + 3) / 4; // one coal makes four torches
			return new Spare(Math.max(0, torches - reserved), Math.max(0, coal - coalReserved));
		}
	}

	/**
	 * Torches that the camp's unfinished buildings (this stage and earlier) still call for, lanterns included, and those
	 * of the village's houses and other library buildings under way (their lights, decoration aside: a house is not
	 * lived in until its lights are in).
	 */
	static int torchesForBuildings(CampData data) {
		int total = 0;
		for (Structures.Entry e : Structures.ALL) {
			if (e.stage() > data.stage() || data.isCompleted(e.id())) {
				continue;
			}
			total += Blueprints.forSite(data, e.id()).map(plan -> torchesIn(plan, false)).orElse(0);
		}
		// The village's houses, shops and lamp posts under way want theirs too: wall torches, and lanterns.
		for (String key : Blueprints.librarySites(data)) {
			if (!Blueprints.isFinished(data, key)) {
				total += Blueprints.forSite(data, key).map(plan -> torchesIn(plan, true)).orElse(0);
			}
		}
		return total;
	}

	/**
	 * Torches per plan, all of them and the ones it requires, counted once: plans last the whole game (a data pack reload
	 * makes new ones, and these go).
	 */
	private static final Map<Blueprint, int[]> TORCHES_IN = new WeakHashMap<>();

	/**
	 * How many torches a plan calls for, lanterns included (a lantern is made around a torch); {@code requiredOnly} leaves
	 * out the optional ones (decoration).
	 */
	private static int torchesIn(Blueprint plan, boolean requiredOnly) {
		int[] counts = TORCHES_IN.computeIfAbsent(plan, p -> {
			int all = 0;
			int required = 0;
			for (Blueprint.Entry entry : p.entries()) {
				Stock stock = entry.material().stock();
				if (stock == Stock.TORCH || stock == Stock.LANTERN) {
					all++;
					if (!entry.optional()) {
						required++;
					}
				}
			}
			return new int[] {all * p.parts(), required * p.parts()};
		});
		return counts[requiredOnly ? 1 : 0];
	}

	/** The torches kept back for buildings, worked out every few seconds rather than at every look at the chest. */
	private static final class Reserve {
		private long at = Long.MIN_VALUE / 2;
		private int torches;

		int of(ServerLevel level) {
			long now = level.getGameTime();
			if (now < at || now - at >= SCAN_INTERVAL / 2) {
				at = now;
				torches = torchesForBuildings(Camp.data(level.getServer()));
			}
			return torches;
		}
	}

	/**
	 * Steps the team's look for dark spots along when one is wanted, and takes its latest finished look if this friend
	 * has not yet.
	 */
	private void refreshSpots(ServerLevel level, CampData data) {
		long now = level.getGameTime();
		Survey survey = TeamCache.get(level, SURVEY, Survey::new);
		if (survey.looking || now - survey.at >= SCAN_INTERVAL || now < survey.at) {
			survey.step(level, data, now);
		}
		if (copiedPass != survey.pass) {
			copiedPass = survey.pass;
			darkSpots.clear();
			darkSpots.addAll(survey.spots);
		}
	}

	/** The dark spot on this column of the camp where a torch could stand, or null. */
	private static @Nullable BlockPos darkSpot(ServerLevel level, CampData data, Layout layout, int x, int z) {
		BlockPos centre = layout.centre();
		if (!level.hasChunkAt(x, z)) {
			return null;
		}
		// Look down from just above camp level, so roofs, tree tops and overhangs are not mistaken for ground. Where the
		// ground there is solid it rises (a hillside the village has spread onto): look again from higher up.
		int top = centre.getY() + LOOK_ABOVE;
		BlockPos ground = Landscape.ground(level, x, z, top, centre.getY() - MAX_DROP);
		if (ground != null && ground.getY() == top) {
			BlockPos higher = Landscape.ground(level, x, z, centre.getY() + HILL_ABOVE, top + 1);
			if (higher != null) {
				ground = higher;
			}
		}
		if (ground == null) {
			return null;
		}
		BlockPos spot = ground.above();
		if (level.getBrightness(LightLayer.BLOCK, spot) >= DARK || !isLightable(level, data, layout, spot)) {
			return null;
		}
		return spot;
	}

	/** A natural, open, solid-floored spot off the paths, fields and building sites where a torch can stand. */
	private static boolean isLightable(ServerLevel level, CampData data, Layout layout, BlockPos spot) {
		BlockState here = level.getBlockState(spot);
		if (!(here.isAir() || Landscape.isWeed(here)) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockPos below = spot.below();
		BlockState ground = level.getBlockState(below);
		if (!ground.isFaceSturdy(level, below, Direction.UP) || ground.is(Blocks.DIRT_PATH) || ground.is(Blocks.FARMLAND)
			|| ground.is(BlockTags.LEAVES) || ground.is(ModTags.NEVER_TOUCH)
			|| data.isPlacedByFriends(level, below) || Landscape.isPlayerMade(level, ground, below, data)) {
			return false;
		}
		if (!Blocks.TORCH.defaultBlockState().canSurvive(level, spot) || WorldEditGuard.touchesFluid(level, spot)) {
			return false;
		}
		if (PathPlan.onPath(layout.centre(), layout.pathTargets(), spot)
			|| Landscape.nearestDistance(layout.unfinishedSites(), spot) < 5) {
			return false;
		}
		return !Landscape.anyNear(level, below, 2, 0, 0, s -> s.is(Blocks.FARMLAND));
	}

	@Override
	public boolean start(CompanionEntity c) {
		placedThisRun.clear();
		current = null;
		if (darkSpots.isEmpty()) {
			return false;
		}
		phase = readyTorches(c) ? Phase.LIGHT : Phase.FETCH;
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	/** At the chest: takes spare torches, or else a little spare coal and the sticks (or planks) to go with it. */
	private static void takeTorchMakings(CompanionEntity c) {
		var bp = c.backpack();
		ChestFetch.take(c, TORCH, Math.min(16 - bp.count(TORCH), Spare.of(c).torches()));
		if (bp.count(TORCH) >= PER_RUN) {
			return;
		}
		ChestFetch.take(c, COAL, Math.min(2 - bp.count(COAL), Spare.of(c).coal()));
		if (!bp.has(STICK) && !bp.has(s -> s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS))) {
			if (ChestFetch.take(c, STICK, 2) == 0 && ChestFetch.take(c, s -> s.is(ItemTags.PLANKS), 2) == 0) {
				ChestFetch.take(c, s -> s.is(ItemTags.LOGS), 1);
			}
		}
	}

	/** Makes sure at least one torch is carried, crafting from coal and sticks if needed. */
	private static boolean readyTorches(CompanionEntity c) {
		if (c.backpack().has(TORCH)) {
			return true;
		}
		Crafting.ensureTorches(c.backpack(), Math.min(PER_RUN, 4));
		return c.backpack().has(TORCH);
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (phase == Phase.FETCH) {
			ChestFetch.Result r = ChestFetch.reach(c);
			if (r == ChestFetch.Result.RUNNING) {
				return TaskStatus.RUNNING;
			}
			if (r == ChestFetch.Result.DONE) {
				takeTorchMakings(c);
			}
			if (!readyTorches(c)) {
				Speech.say(c, Line.NEED_MATERIALS, "torches (coal and sticks)");
				return TaskStatus.FAILURE;
			}
			phase = Phase.LIGHT;
		}
		if (current == null) {
			if (placedThisRun.size() >= PER_RUN || darkSpots.isEmpty()) {
				return finish(level);
			}
			if (!readyTorches(c)) {
				return finish(level);
			}
			current = pickNext(c, level);
			if (current == null) {
				return finish(level);
			}
		}
		BlockPos spot = current;
		if (!c.actions().canReach(spot) || c.position().distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 9) {
			c.actions().walkTo(spot, WORK_REACH);
			if (c.actions().isStuck()) {
				c.actions().stopWalking();
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		BlockState torch = Blocks.TORCH.defaultBlockState();
		WorldEditGuard.Verdict verdict = WorldEditGuard.canPlace(c, spot, torch, Reason.LANDSCAPE);
		if (!verdict.allowed()) {
			if (!"pacing".equals(verdict.why())) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		if (c.actions().place(spot, torch, TORCH, Reason.LANDSCAPE)) {
			placedThisRun.add(spot);
		}
		current = null;
		return TaskStatus.RUNNING;
	}

	/**
	 * The best remaining dark spot: inner camp first, then close to Terra; skips spots that are no longer dark. After
	 * dark only the heart of the camp is lit.
	 */
	private @Nullable BlockPos pickNext(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		BlockPos centre = c.homePos();
		boolean night = Camp.isNight(level);
		Layout layout = Layout.of(data);
		while (!darkSpots.isEmpty()) {
			BlockPos best = null;
			double bestRank = Double.MAX_VALUE;
			for (BlockPos p : darkSpots) {
				if (night && !inHeart(data, layout.centre(), p)) {
					continue; // the village's outer streets wait for the day
				}
				double rank = Math.sqrt(Camp.horizontalDistSqr(p, centre)) + 0.5 * Math.sqrt(p.distSqr(c.blockPosition()));
				if (rank < bestRank) {
					bestRank = rank;
					best = p;
				}
			}
			if (best == null) {
				return null;
			}
			darkSpots.remove(best);
			if (level.getBrightness(LightLayer.BLOCK, best) < DARK && farFromPlaced(best)
				&& isLightable(level, data, layout, best)) {
				return best;
			}
		}
		return null;
	}

	private boolean farFromPlaced(BlockPos p) {
		for (BlockPos t : placedThisRun) {
			if (t.distSqr(p) < SPACING * SPACING) {
				return false;
			}
		}
		return true;
	}

	private TaskStatus finish(ServerLevel level) {
		TeamCache.get(level, SURVEY, Survey::new).lightChanged(); // light has changed: the team looks again
		if (placedThisRun.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		Camp.data(level.getServer()).addStat("torches_placed", placedThisRun.size());
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		current = null;
		placedThisRun.clear();
		phase = Phase.LIGHT;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
