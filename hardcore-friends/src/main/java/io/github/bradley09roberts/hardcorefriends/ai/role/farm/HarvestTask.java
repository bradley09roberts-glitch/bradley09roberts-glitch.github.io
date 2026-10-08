package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Fern harvests ripe crops in the camp (wheat, carrots, potatoes, beetroots, and melons or pumpkins grown from a
 * stem) and replants each field crop straight away with a seed from the harvest. Up to 12 crops per run.
 */
public final class HarvestTask implements CompanionTask {
	private static final int MAX_PER_RUN = 12;
	private static final int TARGET_TIMEOUT = 200;

	private final FarmContext farm;
	private final Deque<BlockPos> targets = new ArrayDeque<>();
	private @Nullable BlockPos current;
	private int currentTicks;
	private @Nullable BlockPos replantAt;
	private @Nullable Item replantSeed;
	private int replantTicks;
	private int harvested;

	public HarvestTask(FarmContext farm) {
		this.farm = farm;
	}

	@Override
	public String id() {
		return "fern.harvest";
	}

	@Override
	public String describe() {
		return "harvesting crops";
	}

	@Override
	public double score(CompanionEntity c) {
		farm.refresh(c);
		if (farm.ripe().isEmpty() || !hasRoom(c)) {
			return 0;
		}
		return 55 * CampNeeds.weight(CampNeeds.Need.FOOD);
	}

	private static boolean hasRoom(CompanionEntity c) {
		return c.backpack().freeSlots() > 0 || c.backpack().canFit(new ItemStack(Items.WHEAT));
	}

	@Override
	public boolean start(CompanionEntity c) {
		targets.clear();
		current = null;
		replantAt = null;
		harvested = 0;
		targets.addAll(route(c.blockPosition(), farm.ripe(), MAX_PER_RUN));
		if (targets.isEmpty()) {
			return false;
		}
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	/** Orders targets by always walking to the nearest remaining one. */
	static List<BlockPos> route(BlockPos from, List<BlockPos> candidates, int max) {
		List<BlockPos> left = new ArrayList<>(candidates);
		List<BlockPos> out = new ArrayList<>();
		BlockPos at = from;
		while (!left.isEmpty() && out.size() < max) {
			BlockPos best = left.getFirst();
			for (BlockPos p : left) {
				if (p.distSqr(at) < best.distSqr(at)) {
					best = p;
				}
			}
			left.remove(best);
			out.add(best);
			at = best;
		}
		return out;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (replantAt != null) {
			replant(c, level);
			return TaskStatus.RUNNING;
		}
		if (current == null) {
			current = targets.poll();
			currentTicks = 0;
			if (current == null) {
				farm.invalidate();
				return harvested > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
			}
		}
		BlockState state = level.getBlockState(current);
		if (!Crops.isRipe(level, current, state) || ++currentTicks > TARGET_TIMEOUT || !hasRoom(c)) {
			current = null;
			return TaskStatus.RUNNING;
		}
		Actions actions = c.actions();
		if (!actions.canReach(current)) {
			actions.walkTo(current, Crops.FIELD_REACH);
			if (actions.isStuck()) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		switch (actions.mine(current, Reason.FARM)) {
			case DONE -> {
				harvested++;
				Camp.data(level.getServer()).addStat("crops_harvested", 1);
				Item seed = Crops.seedFor(state);
				if (seed != null && level.getBlockState(current.below()).is(Blocks.FARMLAND)) {
					replantAt = current;
					replantSeed = seed;
					replantTicks = 0;
				}
				current = null;
			}
			case FAILED -> current = null;
			case RUNNING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	private void replant(CompanionEntity c, ServerLevel level) {
		BlockPos pos = replantAt;
		Item seed = replantSeed;
		BlockState crop = seed != null ? Crops.cropFor(seed) : null;
		if (pos == null || crop == null || !c.backpack().has(s -> s.is(seed)) || ++replantTicks > 60
			|| !level.getBlockState(pos.below()).is(Blocks.FARMLAND) || !level.getBlockState(pos).isAir()) {
			replantAt = null;
			return;
		}
		if (!c.actions().canReach(pos)) {
			c.actions().walkTo(pos, Crops.FIELD_REACH);
			return;
		}
		EditSteps.Step step = EditSteps.place(c, pos, crop, s -> s.is(seed), Reason.FARM);
		if (step != EditSteps.Step.WAIT) {
			replantAt = null;
		}
	}

	@Override
	public void stop(CompanionEntity c) {
		targets.clear();
		current = null;
		replantAt = null;
	}
}
