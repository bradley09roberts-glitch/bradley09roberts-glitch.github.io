package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.ArrayDeque;
import java.util.Deque;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Fern spends carried bone meal on young crops in the camp. Each use grows the crop the way vanilla bone meal does
 * (two to five stages, a third of that for beetroots) through the edit guard, and costs one bone meal.
 */
public final class BoneMealTask implements CompanionTask {
	private static final int MAX_PER_RUN = 8;
	private static final int TARGET_TIMEOUT = 200;

	private final FarmContext farm;
	private final Deque<BlockPos> targets = new ArrayDeque<>();
	private @Nullable BlockPos current;
	private int currentTicks;
	private int used;

	public BoneMealTask(FarmContext farm) {
		this.farm = farm;
	}

	@Override
	public String id() {
		return "fern.bone_meal";
	}

	@Override
	public String describe() {
		return "tending young crops";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!c.backpack().has(s -> s.is(Items.BONE_MEAL))) {
			return 0;
		}
		farm.refresh(c);
		return farm.growing().isEmpty() ? 0 : 25;
	}

	@Override
	public boolean start(CompanionEntity c) {
		targets.clear();
		current = null;
		used = 0;
		int limit = Math.min(MAX_PER_RUN, c.backpack().count(Items.BONE_MEAL));
		targets.addAll(HarvestTask.route(c.blockPosition(), farm.growing(), limit));
		return !targets.isEmpty();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (current == null) {
			current = targets.poll();
			currentTicks = 0;
			if (current == null || !c.backpack().has(s -> s.is(Items.BONE_MEAL))) {
				farm.invalidate(c);
				return used > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
			}
		}
		BlockState state = level.getBlockState(current);
		if (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state) || ++currentTicks > TARGET_TIMEOUT) {
			current = null;
			return TaskStatus.RUNNING;
		}
		if (!c.actions().canReach(current)) {
			c.actions().walkTo(current, Crops.FIELD_REACH);
			if (c.actions().isStuck()) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		BlockState grown = crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(state) + growth(level, crop)));
		switch (EditSteps.transform(c, current, grown, Reason.FARM, null)) {
			case DONE -> {
				c.backpack().remove(s -> s.is(Items.BONE_MEAL), 1);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, current.getX() + 0.5, current.getY() + 0.5, current.getZ() + 0.5,
					12, 0.3, 0.3, 0.3, 0.0);
				Camp.data(level.getServer()).addStat("bone_meal_used", 1);
				used++;
				current = null;
			}
			case FAILED -> current = null;
			case WAIT -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	/** Vanilla bone meal growth: 2–5 stages (a third for beetroots), at least one so the meal is never wasted. */
	private static int growth(ServerLevel level, CropBlock crop) {
		int stages = Mth.nextInt(level.getRandom(), 2, 5);
		if (crop instanceof BeetrootBlock) {
			stages /= 3;
		}
		return Math.max(1, stages);
	}

	@Override
	public void stop(CompanionEntity c) {
		targets.clear();
		current = null;
	}
}
