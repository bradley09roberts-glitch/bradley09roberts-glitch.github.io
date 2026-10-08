package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Fern bakes bread (three wheat each, a 3×3 recipe) at a crafting table in the camp, using wheat she carries or
 * takes from the supply chest. Up to nine loaves per run.
 */
public final class BakeBreadTask implements CompanionTask {
	private static final int WHEAT_PER_BREAD = 3;
	private static final int MAX_LOAVES = 9;

	private final FarmContext farm;
	private @Nullable BlockPos table;
	private boolean fetching;

	public BakeBreadTask(FarmContext farm) {
		this.farm = farm;
	}

	@Override
	public String id() {
		return "fern.bake";
	}

	@Override
	public String describe() {
		return "baking bread";
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.backpack().count(Items.WHEAT) < WHEAT_PER_BREAD && chestWheat(c) < WHEAT_PER_BREAD) {
			return 0;
		}
		if (!c.backpack().canFit(new ItemStack(Items.BREAD)) && c.backpack().freeSlots() == 0) {
			return 0;
		}
		farm.refresh(c);
		Optional<BlockPos> found = farm.craftingTable(c);
		return found.isPresent() && WorldEditGuard.inCamp(c, found.get()) ? 35 : 0;
	}

	private static int chestWheat(CompanionEntity c) {
		return SupplyChest.of((ServerLevel) c.level()).map(chest -> SupplyChest.count(chest, s -> s.is(Items.WHEAT))).orElse(0);
	}

	@Override
	public boolean start(CompanionEntity c) {
		table = farm.craftingTable(c).orElse(null);
		fetching = c.backpack().count(Items.WHEAT) < WHEAT_PER_BREAD;
		return table != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (fetching) {
			Optional<BlockPos> chestPos = Camp.data(level.getServer()).chestPos();
			Optional<Container> chest = SupplyChest.of(level);
			if (chestPos.isEmpty() || chest.isEmpty()) {
				return TaskStatus.FAILURE;
			}
			if (!c.actions().walkTo(chestPos.get(), 2.5)) {
				return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
			}
			int want = Math.min(SupplyChest.count(chest.get(), s -> s.is(Items.WHEAT)), MAX_LOAVES * WHEAT_PER_BREAD);
			want -= want % WHEAT_PER_BREAD;
			SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(Items.WHEAT), want);
			fetching = false;
			return TaskStatus.RUNNING;
		}
		BlockPos at = table;
		if (at == null || !level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(at, 2.0)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		int loaves = Math.min(MAX_LOAVES, c.backpack().count(Items.WHEAT) / WHEAT_PER_BREAD);
		if (loaves <= 0) {
			return TaskStatus.FAILURE;
		}
		int before = c.backpack().count(Items.BREAD);
		Crafting.ensure(c, Items.BREAD, before + loaves);
		int made = c.backpack().count(Items.BREAD) - before;
		if (made <= 0) {
			return TaskStatus.FAILURE;
		}
		c.swingArm();
		Camp.data(level.getServer()).addStat("bread_baked", made);
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		table = null;
		fetching = false;
	}
}
