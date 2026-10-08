package io.github.bradley09roberts.hardcorefriends.ai.role.mine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Flint empties the camp furnace's output into his backpack; the shared deposit routine then carries it to the
 * supply chest. From a furnace someone else placed he only takes iron, copper and gold ingots, never other
 * people's cooking.
 */
public final class CollectSmeltedTask implements CompanionTask {
	private final CampFurnace furnace;
	private @Nullable BlockPos target;

	public CollectSmeltedTask(CampFurnace furnace) {
		this.furnace = furnace;
	}

	@Override
	public String id() {
		return "flint.collect_smelted";
	}

	@Override
	public String describe() {
		return "collecting from the furnace";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		BlockPos pos = furnace.pos(c);
		if (pos == null) {
			return 0;
		}
		AbstractFurnaceBlockEntity f = CampFurnace.furnaceAt(level, pos);
		if (f == null) {
			return 0;
		}
		ItemStack out = f.getItem(CampFurnace.SLOT_RESULT);
		return !out.isEmpty() && mayTake(level, pos, out) && c.backpack().canFit(out.copyWithCount(1)) ? 35 : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		target = furnace.pos(c);
		return target != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (target == null) {
			return TaskStatus.FAILURE;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (!c.actions().canReach(target)) {
			c.actions().walkTo(target, 2.0);
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		AbstractFurnaceBlockEntity f = CampFurnace.furnaceAt(level, target);
		if (f == null) {
			furnace.forget();
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		int taken = collect(level, target, f, c);
		if (taken <= 0) {
			return TaskStatus.FAILURE;
		}
		c.swingArm();
		Camp.data(level.getServer()).addStat("ingots_collected", taken);
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		target = null;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}

	/** Moves as much of the output slot as fits into the backpack. Returns the number of items taken. */
	public static int collect(ServerLevel level, BlockPos pos, AbstractFurnaceBlockEntity f, CompanionEntity c) {
		ItemStack out = f.getItem(CampFurnace.SLOT_RESULT);
		if (out.isEmpty() || !mayTake(level, pos, out)) {
			return 0;
		}
		ItemStack left = c.backpack().insert(out.copy());
		int taken = out.getCount() - left.getCount();
		if (taken > 0) {
			f.removeItem(CampFurnace.SLOT_RESULT, taken);
			f.setChanged();
		}
		return taken;
	}

	private static boolean mayTake(ServerLevel level, BlockPos pos, ItemStack out) {
		if (Camp.data(level.getServer()).isPlacedByFriends(pos)) {
			return true;
		}
		return out.is(Items.IRON_INGOT) || out.is(Items.COPPER_INGOT) || out.is(Items.GOLD_INGOT);
	}
}
