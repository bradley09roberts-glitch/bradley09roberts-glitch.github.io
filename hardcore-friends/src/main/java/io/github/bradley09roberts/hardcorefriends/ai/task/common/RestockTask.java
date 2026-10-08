package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.List;
import java.util.Optional;

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
 *
 * <p>A tool for a speciality the friend only covers ({@link KeepList#isCoverTool}) is fetched in spare time and never
 * pushes anything out: it is held if the hand is empty, otherwise carried in the backpack, and with the backpack full
 * it stays in the chest. Their own tool is never put away to make room for it, so the two never swap back and forth.
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
		TagKey<Item> tool = KeepList.missingTool(c);
		if (tool != null && Upkeep.chestCount(level, s -> s.is(tool)) > 0) {
			if (!KeepList.isCoverTool(c, tool)) {
				return 70;
			}
			if (hasRoomForCoverTool(c)) {
				return 30; // a tool for a speciality they only cover waits until their own work allows
			}
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

	/** A cover tool is only taken where it fits without putting anything away: an empty hand or a free slot. */
	private static boolean hasRoomForCoverTool(CompanionEntity c) {
		return c.getMainHandItem().isEmpty() || c.backpack().freeSlots() > 0;
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

	/**
	 * Takes the strongest missing tool from the container. Their own speciality's tool is held at once, whatever was
	 * in hand going into the backpack (or the chest if the backpack is full). A cover tool goes into an empty hand or
	 * the backpack, and is left in the chest when neither has room.
	 */
	static boolean takeBestTool(CompanionEntity c, Container chest) {
		TagKey<Item> wanted = KeepList.missingTool(c);
		if (wanted == null) {
			return false;
		}
		boolean cover = KeepList.isCoverTool(c, wanted);
		if (cover && !hasRoomForCoverTool(c)) {
			return false;
		}
		List<Integer> slots = Upkeep.slotsBest(chest, s -> s.is(wanted), Upkeep::toolRank);
		if (slots.isEmpty()) {
			return false;
		}
		ItemStack tool = chest.removeItem(slots.getFirst(), 1);
		chest.setChanged();
		if (tool.isEmpty()) {
			return false;
		}
		if (!cover || c.getMainHandItem().isEmpty()) {
			Upkeep.holdInHand(c, tool, chest);
			return true;
		}
		ItemStack left = c.backpack().insert(tool); // whatever they hold stays in hand
		if (left.isEmpty()) {
			return true;
		}
		left = SupplyChest.insert(chest, left); // no room after all: back where it came from
		if (!left.isEmpty()) {
			c.spawnAtLocation((ServerLevel) c.level(), left);
		}
		return false;
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
