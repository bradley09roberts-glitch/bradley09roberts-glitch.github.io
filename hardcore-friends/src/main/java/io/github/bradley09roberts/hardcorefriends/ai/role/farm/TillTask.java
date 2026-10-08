package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Fern tills grass or dirt within four blocks of water into farmland with her hoe (it wears with each use), up to
 * eight blocks per run, then sows them. She only does this with at least four seeds to sow, fetching them from the
 * supply chest first when her backpack holds fewer, and the farmland in camp is capped by settlement stage (24, 48,
 * 80, 120, 160).
 */
public final class TillTask implements CompanionTask {
	private static final int MAX_PER_RUN = 8;
	private static final int MIN_SEEDS = 4;
	private static final int TARGET_TIMEOUT = 200;

	private final FarmContext farm;
	private final Deque<BlockPos> targets = new ArrayDeque<>();
	private final List<BlockPos> tilled = new ArrayList<>();
	private @Nullable BlockPos current;
	private int currentTicks;
	private int plantIndex;
	private boolean planting;
	private boolean reported;
	private boolean fetching;

	public TillTask(FarmContext farm) {
		this.farm = farm;
	}

	@Override
	public String id() {
		return "fern.till";
	}

	@Override
	public String describe() {
		return "tilling new farmland";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!c.actions().hasTool(ItemTags.HOES) || !hasSeedsToSow(c)) {
			return 0;
		}
		farm.stepCampSurvey(c);
		if (room(c) <= 0 || farm.tillCandidates(c, MAX_PER_RUN).isEmpty()) {
			return 0;
		}
		return 40 * CampNeeds.weight(CampNeeds.Need.FOOD);
	}

	/** At least {@value #MIN_SEEDS} seeds carried, or enough in the supply chest to make up the difference. */
	static boolean hasSeedsToSow(CompanionEntity c) {
		int carried = c.backpack().count(Crops.IS_SEED);
		return carried >= MIN_SEEDS || carried + ReplantTask.chestSeeds(c) >= MIN_SEEDS;
	}

	/** How much more farmland the camp may have at this stage, or 0 while the camp survey is still running. */
	private int room(CompanionEntity c) {
		if (!farm.campSurveyed()) {
			return 0;
		}
		CampData data = Camp.data(c.level().getServer());
		return Crops.farmlandCap(data.stage()) - farm.campFarmland();
	}

	@Override
	public boolean start(CompanionEntity c) {
		targets.clear();
		tilled.clear();
		current = null;
		plantIndex = 0;
		planting = false;
		reported = false;
		fetching = c.backpack().count(Crops.IS_SEED) < MIN_SEEDS;
		int limit = Math.min(MAX_PER_RUN, room(c));
		List<BlockPos> candidates = farm.tillCandidates(c, MAX_PER_RUN);
		for (int i = 0; i < candidates.size() && targets.size() < limit; i++) {
			targets.add(candidates.get(i));
		}
		return !targets.isEmpty();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (fetching) {
			TaskStatus fetched = ReplantTask.fetchSeedsStep(c, level);
			if (fetched != TaskStatus.SUCCESS) {
				return fetched;
			}
			fetching = false;
			return TaskStatus.RUNNING;
		}
		if (planting || (current == null && targets.isEmpty())) {
			if (!planting) {
				planting = true;
				currentTicks = 0;
			}
			return plantStep(c, level);
		}
		if (current == null) {
			current = targets.poll();
			currentTicks = 0;
			CampData data = Camp.data(level.getServer());
			if (current != null && !FarmContext.isTillSpot(c, level, data, c.homePos(), current, true)) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		if (++currentTicks > TARGET_TIMEOUT || !c.actions().hasTool(ItemTags.HOES)) {
			current = null;
			return TaskStatus.RUNNING;
		}
		Actions actions = c.actions();
		if (!actions.canReach(current)) {
			actions.walkTo(current.above(), Crops.FIELD_REACH);
			if (actions.isStuck()) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		BlockState above = level.getBlockState(current.above());
		if (!above.isAir()) {
			// Short grass or a fern in the way: clear it first (a plant, so no harm done).
			if (actions.mine(current.above(), Reason.LANDSCAPE) == Actions.Result.FAILED) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		switch (EditSteps.transform(c, current, Blocks.FARMLAND.defaultBlockState(), Reason.FARM, ItemTags.HOES)) {
			case DONE -> {
				tilled.add(current);
				Camp.data(level.getServer()).addStat("farmland_tilled", 1);
				current = null;
			}
			case FAILED -> current = null;
			case WAIT -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	/** Sows the freshly tilled farmland, one block per edit. */
	private TaskStatus plantStep(CompanionEntity c, ServerLevel level) {
		if (plantIndex >= tilled.size()) {
			return finish();
		}
		BlockPos soil = tilled.get(plantIndex);
		BlockPos cropPos = soil.above();
		Item seed = Crops.bestSeed(c.backpack());
		BlockState crop = seed != null ? Crops.cropFor(seed) : null;
		if (crop == null) {
			return finish();
		}
		if (!level.getBlockState(soil).is(Blocks.FARMLAND) || !level.getBlockState(cropPos).isAir() || ++currentTicks > TARGET_TIMEOUT) {
			nextPlant();
			return TaskStatus.RUNNING;
		}
		if (!c.actions().canReach(cropPos)) {
			c.actions().walkTo(cropPos, Crops.FIELD_REACH);
			if (c.actions().isStuck()) {
				nextPlant();
			}
			return TaskStatus.RUNNING;
		}
		if (EditSteps.place(c, cropPos, crop, s -> s.is(seed), Reason.FARM) != EditSteps.Step.WAIT) {
			nextPlant();
		}
		return TaskStatus.RUNNING;
	}

	private void nextPlant() {
		plantIndex++;
		currentTicks = 0;
	}

	private TaskStatus finish() {
		report();
		return tilled.isEmpty() ? TaskStatus.FAILURE : TaskStatus.SUCCESS;
	}

	/** Tells the farm survey about the new farmland once, however the run ends. */
	private void report() {
		if (!reported && !tilled.isEmpty()) {
			reported = true;
			farm.noteTilled(tilled.size());
			farm.invalidate();
		}
	}

	@Override
	public void stop(CompanionEntity c) {
		report();
		targets.clear();
		tilled.clear();
		current = null;
		fetching = false;
	}
}
