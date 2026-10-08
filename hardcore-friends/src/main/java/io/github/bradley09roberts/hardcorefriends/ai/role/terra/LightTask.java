package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Terra spawn-proofs the camp: finds dark ground (block light below 8) in camp, away from paths, farmland, buildings
 * and unfinished building sites, and places torches at least 5 blocks apart, up to 6 per run. Torches come from the
 * backpack, are crafted from carried coal and sticks, or are fetched from the supply chest. Lighting is a nicety: the
 * torches the camp's unfinished buildings still call for (and the coal to make them) stay in the chest for the
 * builder.
 */
public final class LightTask implements CompanionTask {
	private static final int PER_RUN = 6;
	private static final int DARK = 8;
	private static final double SPACING = 5;
	private static final int SCAN_INTERVAL = 200;
	private static final int LATTICE = 3;
	private static final int LOOK_ABOVE = 4;
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

	private final List<BlockPos> darkSpots = new ArrayList<>();
	private long scannedAt = -100_000;
	private long copiedAt = Long.MIN_VALUE;
	private int scanCount;

	/** The team's latest look for dark spots, which friends other than the landscaper reuse while it is fresh. */
	private static final class Shared {
		private long at = Long.MIN_VALUE / 2;
		private List<BlockPos> spots = List.of();
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
		if (Camp.isNight(level) && !WorldEditGuard.inCamp(c, c.blockPosition())) {
			return 0;
		}
		refreshSpots(c, level, data);
		if (darkSpots.isEmpty() || !torchesAvailable(c)) {
			return 0;
		}
		return 50;
	}

	/**
	 * Torches carried or spare in the chest, or coal (carried or spare) and something to make sticks from (carried or
	 * in the chest).
	 */
	private static boolean torchesAvailable(CompanionEntity c) {
		var bp = c.backpack();
		Spare spare = Spare.of(c);
		if (bp.has(TORCH) || spare.torches() > 0) {
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
			int reserved = torchesForBuildings(Camp.data(level.getServer()));
			int coalReserved = (Math.max(0, reserved - torches) + 3) / 4; // one coal makes four torches
			return new Spare(Math.max(0, torches - reserved), Math.max(0, coal - coalReserved));
		}
	}

	/** Torches that the camp's unfinished buildings (this stage and earlier) still call for, lanterns included. */
	static int torchesForBuildings(CampData data) {
		int total = 0;
		for (Structures.Entry e : Structures.ALL) {
			if (e.stage() > data.stage() || data.isCompleted(e.id())) {
				continue;
			}
			Optional<Blueprint> plan = Blueprints.forId(e.id());
			if (plan.isEmpty()) {
				continue;
			}
			int each = 0;
			for (Blueprint.Entry entry : plan.get().entries()) {
				Stock stock = entry.material().stock();
				if (stock == Stock.TORCH || stock == Stock.LANTERN) { // a lantern is made around a torch
					each++;
				}
			}
			total += each * plan.get().parts();
		}
		return total;
	}

	/**
	 * Looks for dark spots every 10 seconds. The landscaper looks on her own schedule, exactly as when she worked alone;
	 * anyone else reuses the team's latest look while it is fresh.
	 */
	private void refreshSpots(CompanionEntity c, ServerLevel level, CampData data) {
		long now = level.getGameTime();
		Shared shared = TeamCache.get(level, "terra.dark_spots", Shared::new);
		boolean own = c.friendId().role() == Role.LANDSCAPER;
		if (now - (own ? scannedAt : shared.at) >= SCAN_INTERVAL) {
			scannedAt = now;
			scan(c, level, data);
			shared.at = now;
			shared.spots = List.copyOf(darkSpots);
			copiedAt = now;
		} else if (!own && copiedAt != shared.at) {
			copiedAt = shared.at;
			darkSpots.clear();
			darkSpots.addAll(shared.spots);
		}
	}

	/** Looks for dark spots on a coarse lattice over the camp, shifting the lattice each scan to cover every block. */
	private void scan(CompanionEntity c, ServerLevel level, CampData data) {
		darkSpots.clear();
		BlockPos centre = data.campPos().orElseThrow();
		int r = WorldEditGuard.campRadius(c) - 1;
		int offX = scanCount % LATTICE;
		int offZ = (scanCount / LATTICE) % LATTICE;
		scanCount++;
		for (int dx = -r + offX; dx <= r; dx += LATTICE) {
			for (int dz = -r + offZ; dz <= r; dz += LATTICE) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				BlockPos probe = new BlockPos(x, centre.getY(), z);
				if (!level.isLoaded(probe)) {
					continue;
				}
				// Look down from just above camp level, so roofs, tree tops and overhangs are not mistaken for ground.
				BlockPos ground = Landscape.ground(level, x, z, centre.getY() + LOOK_ABOVE, centre.getY() - MAX_DROP);
				if (ground == null) {
					continue;
				}
				BlockPos spot = ground.above();
				if (level.getBrightness(LightLayer.BLOCK, spot) >= DARK) {
					continue;
				}
				if (isLightable(level, data, spot)) {
					darkSpots.add(spot);
				}
			}
		}
	}

	/** A natural, open, solid-floored spot off the paths, fields and building sites where a torch can stand. */
	private static boolean isLightable(ServerLevel level, CampData data, BlockPos spot) {
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
		if (PathPlan.onPath(data, spot) || Landscape.nearestSiteDistance(data, spot, true) < 5) {
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

	/** The best remaining dark spot: inner camp first, then close to Terra; skips spots that are no longer dark. */
	private @Nullable BlockPos pickNext(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		BlockPos centre = c.homePos();
		while (!darkSpots.isEmpty()) {
			BlockPos best = null;
			double bestRank = Double.MAX_VALUE;
			for (BlockPos p : darkSpots) {
				double rank = Math.sqrt(Camp.horizontalDistSqr(p, centre)) + 0.5 * Math.sqrt(p.distSqr(c.blockPosition()));
				if (rank < bestRank) {
					bestRank = rank;
					best = p;
				}
			}
			darkSpots.remove(best);
			if (best != null && level.getBrightness(LightLayer.BLOCK, best) < DARK && farFromPlaced(best)
				&& isLightable(level, data, best)) {
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
		scannedAt = -100_000; // light has changed; look again next time (everyone)
		TeamCache.get(level, "terra.dark_spots", Shared::new).at = Long.MIN_VALUE / 2;
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
