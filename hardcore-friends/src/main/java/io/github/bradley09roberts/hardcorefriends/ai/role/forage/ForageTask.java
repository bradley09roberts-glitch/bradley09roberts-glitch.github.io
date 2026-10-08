package io.github.bradley09roberts.hardcorefriends.ai.role.forage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.farm.EditSteps;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.ReturnHomeTask;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Rowan picks sweet berries from bushes that have them (like a player: the bush drops back to its first fruitless
 * stage and gives 1–2 berries, or 2–3 when fully grown), and gathers saplings, apples and sticks that fell from the
 * leaves of trees she felled. Out near the camp edge and beyond only by day.
 */
public final class ForageTask implements CompanionTask {
	private static final int MAX_BUSHES = 8;
	private static final int MAX_ITEMS = 8;
	private static final int TARGET_TIMEOUT = 200;

	private final ForageContext forage;
	private final Deque<BlockPos> bushes = new ArrayDeque<>();
	private final Deque<ItemEntity> items = new ArrayDeque<>();
	private @Nullable BlockPos bush;
	private @Nullable ItemEntity item;
	private int targetTicks;
	private int gathered;

	public ForageTask(ForageContext forage) {
		this.forage = forage;
	}

	@Override
	public String id() {
		return "rowan.forage";
	}

	@Override
	public String describe() {
		return "foraging";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.backpack().freeSlots() == 0 && !c.backpack().canFit(new ItemStack(Items.SWEET_BERRIES, 3))) {
			return 0;
		}
		if (forage.berries(c).stream().noneMatch(p -> allowedNow(c, p)) && forage.fallenGoods(c).stream()
			.noneMatch(e -> allowedNow(c, e.blockPosition()))) {
			return 0;
		}
		return 35 * CampNeeds.weight(CampNeeds.Need.FOOD);
	}

	/** At dusk and night Rowan only forages where the return home would not call her straight back. */
	private static boolean allowedNow(CompanionEntity c, BlockPos pos) {
		return !ReturnHomeTask.sendsHome(c, pos);
	}

	@Override
	public boolean start(CompanionEntity c) {
		bushes.clear();
		items.clear();
		bush = null;
		item = null;
		gathered = 0;
		for (BlockPos p : forage.berries(c)) {
			if (bushes.size() < MAX_BUSHES && allowedNow(c, p)) {
				bushes.add(p);
			}
		}
		List<ItemEntity> goods = forage.fallenGoods(c);
		for (ItemEntity e : goods) {
			if (items.size() < MAX_ITEMS && allowedNow(c, e.blockPosition())) {
				items.add(e);
			}
		}
		return !bushes.isEmpty() || !items.isEmpty();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (bush == null && item == null) {
			bush = bushes.poll();
			if (bush == null) {
				item = items.poll();
			}
			targetTicks = 0;
			if (bush == null && item == null) {
				forage.invalidateBerries(c);
				return gathered > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
			}
		}
		BlockPos target = bush != null ? bush : item != null ? item.blockPosition() : null;
		if (++targetTicks > TARGET_TIMEOUT || target == null || !allowedNow(c, target)) {
			bush = null;
			item = null;
			return TaskStatus.RUNNING;
		}
		if (bush != null) {
			pick(c, level, bush);
		} else if (item != null) {
			collect(c, level, item);
		}
		return TaskStatus.RUNNING;
	}

	private void pick(CompanionEntity c, ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!ForageContext.isRipeBush(state)) {
			bush = null;
			return;
		}
		if (!c.actions().canReach(pos)) {
			// Stop short of the bush: walking into it hurts.
			c.actions().walkTo(pos, 2.5);
			if (c.actions().isStuck()) {
				bush = null;
			}
			return;
		}
		c.actions().stopWalking();
		int age = state.getValue(SweetBerryBushBlock.AGE);
		switch (EditSteps.transform(c, pos, state.setValue(SweetBerryBushBlock.AGE, 1), Reason.FARM, null)) {
			case DONE -> {
				int berries = (age >= SweetBerryBushBlock.MAX_AGE ? 2 : 1) + c.getRandom().nextInt(2);
				ItemStack left = c.backpack().insert(new ItemStack(Items.SWEET_BERRIES, berries));
				if (!left.isEmpty()) {
					c.spawnAtLocation(level, left);
				}
				level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F,
					0.8F + c.getRandom().nextFloat() * 0.4F);
				Camp.data(level.getServer()).addStat("berries_picked", berries);
				gathered++;
				bush = null;
			}
			case FAILED -> bush = null;
			case WAIT -> {
			}
		}
	}

	private void collect(CompanionEntity c, ServerLevel level, ItemEntity entity) {
		if (!entity.isAlive() || entity.hasPickUpDelay()) {
			item = null;
			return;
		}
		if (!c.actions().walkToEntity(entity, 1.25)) {
			return;
		}
		ItemStack stack = entity.getItem();
		int before = stack.getCount();
		ItemStack left = c.backpack().insert(stack.copy());
		int taken = before - left.getCount();
		if (taken > 0) {
			c.take(entity, taken);
			if (left.isEmpty()) {
				entity.discard();
			} else {
				entity.setItem(left);
			}
			gathered++;
		}
		item = null;
	}

	@Override
	public void stop(CompanionEntity c) {
		bushes.clear();
		items.clear();
		bush = null;
		item = null;
	}
}
