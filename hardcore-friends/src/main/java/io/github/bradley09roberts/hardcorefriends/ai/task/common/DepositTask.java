package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Carries surplus to the shared supply chest: urgently when the backpack is nearly full, otherwise once a good
 * amount has piled up. Tools, the role's keep-list and a little food stay in the backpack.
 */
public final class DepositTask implements CompanionTask {
	/** Surplus items worth a trip even when the backpack still has room. */
	public static final int SURPLUS_TRIP = 16;

	private @Nullable BlockPos chestPos;
	private int lingerTicks;

	@Override
	public String id() {
		return "common.deposit";
	}

	@Override
	public String describe() {
		return "depositing supplies";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Upkeep.chestPos(level).isEmpty()) {
			return 0;
		}
		Backpack bp = c.backpack();
		int surplus = KeepList.surplusTotal(c);
		if (surplus <= 0) {
			return 0;
		}
		if (bp.fullness() >= 0.8 || bp.freeSlots() == 0) {
			return 75;
		}
		return surplus >= SURPLUS_TRIP ? 45 : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		Optional<BlockPos> pos = Upkeep.chestPos((ServerLevel) c.level());
		chestPos = pos.orElse(null);
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
		ServerLevel level = (ServerLevel) c.level();
		Optional<Container> chest = SupplyChest.at(level, chestPos);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(chestPos));
		int moved = depositSurplus(c, chest.get());
		if (moved <= 0) {
			return TaskStatus.FAILURE; // chest full or nothing left to give
		}
		Upkeep.playSound(c, chestPos, SoundEvents.CHEST_OPEN);
		c.swingArm();
		Speech.say(c, Line.DEPOSIT);
		Unity.add(level, Unity.DELIVERY, 1, 60);
		Camp.data(level.getServer()).addStat("deposits", 1);
		lingerTicks = 10;
		return TaskStatus.RUNNING;
	}

	/** Moves every surplus item into the container. Returns how many items were moved. */
	public static int depositSurplus(CompanionEntity c, Container container) {
		Backpack bp = c.backpack();
		int[] surplus = KeepList.surplusBySlot(c);
		int moved = 0;
		for (int slot = 0; slot < surplus.length; slot++) {
			if (surplus[slot] <= 0) {
				continue;
			}
			ItemStack stack = bp.get(slot);
			ItemStack left = SupplyChest.insert(container, stack.copyWithCount(surplus[slot]));
			int put = surplus[slot] - left.getCount();
			if (put > 0) {
				stack.shrink(put);
				moved += put;
				if (stack.isEmpty()) {
					bp.container().setItem(slot, ItemStack.EMPTY);
				}
			}
		}
		if (moved > 0) {
			bp.container().setChanged();
		}
		return moved;
	}

	@Override
	public void stop(CompanionEntity c) {
		chestPos = null;
		lingerTicks = 0;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
