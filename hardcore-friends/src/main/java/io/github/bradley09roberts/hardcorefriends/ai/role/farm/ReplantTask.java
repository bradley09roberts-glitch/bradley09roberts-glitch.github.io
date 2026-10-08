package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Fern sows empty farmland in the camp with whichever seed she has most of (wheat seeds, carrots, potatoes or
 * beetroot seeds). With none in her backpack she first fetches some from the supply chest.
 */
public final class ReplantTask implements CompanionTask {
	private static final int MAX_PER_RUN = 16;
	private static final int TARGET_TIMEOUT = 200;

	private final FarmContext farm;
	private final Deque<BlockPos> targets = new ArrayDeque<>();
	private @Nullable BlockPos current;
	private int currentTicks;
	private boolean fetching;
	private int planted;

	public ReplantTask(FarmContext farm) {
		this.farm = farm;
	}

	@Override
	public String id() {
		return "fern.replant";
	}

	@Override
	public String describe() {
		return "replanting";
	}

	@Override
	public double score(CompanionEntity c) {
		farm.refresh(c);
		if (farm.emptyFarmland().isEmpty()) {
			return 0;
		}
		if (!c.backpack().has(Crops.IS_SEED) && chestSeeds(c) == 0) {
			return 0;
		}
		return 50;
	}

	private static int chestSeeds(CompanionEntity c) {
		return SupplyChest.of((ServerLevel) c.level()).map(chest -> SupplyChest.count(chest, Crops.IS_SEED)).orElse(0);
	}

	@Override
	public boolean start(CompanionEntity c) {
		targets.clear();
		current = null;
		planted = 0;
		targets.addAll(HarvestTask.route(c.blockPosition(), farm.emptyFarmland(), MAX_PER_RUN));
		fetching = !c.backpack().has(Crops.IS_SEED);
		return !targets.isEmpty();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (fetching) {
			return fetchSeeds(c, level);
		}
		if (current == null) {
			current = targets.poll();
			currentTicks = 0;
			if (current == null) {
				farm.invalidate();
				return planted > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
			}
		}
		Item seed = Crops.bestSeed(c.backpack());
		if (seed == null) {
			farm.invalidate();
			return planted > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
		}
		BlockPos cropPos = current.above();
		if (!level.getBlockState(current).is(Blocks.FARMLAND) || !level.getBlockState(cropPos).isAir() || ++currentTicks > TARGET_TIMEOUT) {
			current = null;
			return TaskStatus.RUNNING;
		}
		if (!c.actions().canReach(cropPos)) {
			c.actions().walkTo(cropPos, Crops.FIELD_REACH);
			if (c.actions().isStuck()) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		BlockState crop = Crops.cropFor(seed);
		if (crop == null) {
			current = null;
			return TaskStatus.RUNNING;
		}
		switch (EditSteps.place(c, cropPos, crop, s -> s.is(seed), Reason.FARM)) {
			case DONE -> {
				planted++;
				current = null;
			}
			case FAILED -> current = null;
			case WAIT -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	private TaskStatus fetchSeeds(CompanionEntity c, ServerLevel level) {
		Optional<BlockPos> chestPos = Camp.data(level.getServer()).chestPos();
		Optional<Container> chest = SupplyChest.of(level);
		if (chestPos.isEmpty() || chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(chestPos.get(), 2.5)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		if (SupplyChest.withdraw(chest.get(), c.backpack(), Crops.IS_SEED, 32) == 0) {
			return TaskStatus.FAILURE;
		}
		fetching = false;
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		targets.clear();
		current = null;
		fetching = false;
	}
}
