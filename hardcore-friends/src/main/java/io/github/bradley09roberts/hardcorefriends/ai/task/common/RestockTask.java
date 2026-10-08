package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Fetches a missing role tool or some food from the supply chest. The best tool in the chest is taken and held
 * straight away; food is topped up to {@value KeepList#FOOD_KEPT}, cooked food first.
 */
public final class RestockTask implements CompanionTask {
	private static final int LOW_FOOD = 2;

	private @Nullable BlockPos chestPos;
	private int lingerTicks;

	@Override
	public String id() {
		return "common.restock";
	}

	@Override
	public String describe() {
		return "restocking from the chest";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Upkeep.chestPos(level).isEmpty()) {
			return 0;
		}
		if (needsTool(c) && Upkeep.chestCount(level, toolFilter(c)) > 0) {
			return 70;
		}
		if (needsFood(c) && Upkeep.chestCount(level, KeepList::isFood) > 0) {
			return 55;
		}
		return 0;
	}

	static boolean needsTool(CompanionEntity c) {
		return !KeepList.hasRoleTool(c);
	}

	private static boolean needsFood(CompanionEntity c) {
		return KeepList.foodCount(c.backpack()) < LOW_FOOD;
	}

	private static Predicate<ItemStack> toolFilter(CompanionEntity c) {
		TagKey<Item> tool = KeepList.roleTool(c.friendId().role());
		return s -> tool != null && s.is(tool);
	}

	@Override
	public boolean start(CompanionEntity c) {
		chestPos = Upkeep.chestPos((ServerLevel) c.level()).orElse(null);
		lingerTicks = 0;
		return chestPos != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (chestPos == null) {
			return TaskStatus.FAILURE;
		}
		if (lingerTicks > 0) {
			c.getLookControl().setLookAt(Vec3.atCenterOf(chestPos));
			if (--lingerTicks == 0) {
				Upkeep.playSound(c, chestPos, SoundEvents.CHEST_CLOSE);
				return TaskStatus.SUCCESS;
			}
			return TaskStatus.RUNNING;
		}
		if (!c.actions().walkTo(chestPos, Upkeep.CHEST_REACH)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		Optional<Container> chest = SupplyChest.at((ServerLevel) c.level(), chestPos);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(chestPos));
		boolean tookTool = needsTool(c) && takeBestTool(c, chest.get());
		boolean tookFood = needsFood(c) && takeFood(c, chest.get(), KeepList.FOOD_KEPT) > 0;
		if (!tookTool && !tookFood) {
			return TaskStatus.FAILURE;
		}
		Upkeep.playSound(c, chestPos, SoundEvents.CHEST_OPEN);
		c.swingArm();
		lingerTicks = 10;
		return TaskStatus.RUNNING;
	}

	/** Takes the strongest role tool from the container and holds it. */
	static boolean takeBestTool(CompanionEntity c, Container chest) {
		List<Integer> slots = Upkeep.slotsBest(chest, toolFilter(c), Upkeep::toolRank);
		if (slots.isEmpty()) {
			return false;
		}
		ItemStack tool = chest.removeItem(slots.getFirst(), 1);
		chest.setChanged();
		if (tool.isEmpty()) {
			return false;
		}
		Upkeep.holdInHand(c, tool, chest);
		return true;
	}

	/** Tops the backpack up to {@code target} food items, best food first. Returns how many were taken. */
	static int takeFood(CompanionEntity c, Container chest, int target) {
		int want = target - KeepList.foodCount(c.backpack());
		int taken = 0;
		for (int slot : Upkeep.slotsBest(chest, KeepList::isFood, KeepList::foodValue)) {
			if (taken >= want) {
				break;
			}
			ItemStack stack = chest.getItem(slot);
			int n = Math.min(want - taken, stack.getCount());
			ItemStack left = c.backpack().insert(stack.copyWithCount(n));
			int moved = n - left.getCount();
			if (moved > 0) {
				stack.shrink(moved);
				if (stack.isEmpty()) {
					chest.setItem(slot, ItemStack.EMPTY);
				}
				taken += moved;
			}
			if (!left.isEmpty()) {
				break; // backpack full
			}
		}
		if (taken > 0) {
			chest.setChanged();
		}
		return taken;
	}

	@Override
	public void stop(CompanionEntity c) {
		chestPos = null;
		lingerTicks = 0;
	}

	@Override
	public int failureCooldown() {
		return 400;
	}

	@Override
	public int successCooldown() {
		return 60;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
