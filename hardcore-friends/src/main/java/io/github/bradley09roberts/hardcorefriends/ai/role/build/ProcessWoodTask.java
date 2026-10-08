package io.github.bradley09roberts.hardcorefriends.ai.role.build;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Oak's workshop chore: when the supply chest holds plenty of logs but few planks, he takes up to eight logs, saws
 * them into planks, makes a bundle of sticks (and torches if there is coal), and puts everything back.
 */
public final class ProcessWoodTask implements CompanionTask {
	private static final Predicate<ItemStack> LOGS = s -> s.is(ItemTags.LOGS);
	private static final Predicate<ItemStack> PLANKS = s -> s.is(ItemTags.PLANKS);
	private static final Predicate<ItemStack> STICKS = s -> s.is(Items.STICK);
	private static final Predicate<ItemStack> TORCHES = s -> s.is(Items.TORCH);
	private static final Predicate<ItemStack> COAL = s -> s.is(ItemTags.COALS);
	private static final Predicate<ItemStack> PRODUCTS = PLANKS.or(STICKS).or(TORCHES).or(COAL).or(LOGS);
	private static final int WORK_TICKS = 30;

	private boolean crafted;
	private int workTicks;

	@Override
	public String id() {
		return "oak.process_wood";
	}

	@Override
	public String describe() {
		return "sawing planks";
	}

	@Override
	public double score(CompanionEntity c) {
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty()) {
			return 0;
		}
		return SupplyChest.count(chest.get(), LOGS) >= 8 && SupplyChest.count(chest.get(), PLANKS) < 32 ? 35 : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		crafted = false;
		workTicks = 0;
		return ChestWalk.chest(c).isPresent();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (!crafted) {
			ChestWalk.State walk = ChestWalk.tick(c);
			if (walk == ChestWalk.State.FAILED) {
				return TaskStatus.FAILURE;
			}
			if (walk == ChestWalk.State.WALKING) {
				return TaskStatus.RUNNING;
			}
			Optional<Container> chest = ChestWalk.chest(c);
			if (chest.isEmpty() || !saw(c, chest.get())) {
				return TaskStatus.FAILURE;
			}
			crafted = true;
		}
		if (workTicks++ % 8 == 0) {
			c.swingArm();
		}
		if (workTicks < WORK_TICKS) {
			return TaskStatus.RUNNING;
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		SupplyChest.deposit(c.backpack(), chest.get(), PRODUCTS, 64 * 9);
		return TaskStatus.SUCCESS;
	}

	/** Turns up to eight logs from the chest into planks, sticks and torches. */
	private static boolean saw(CompanionEntity c, Container chest) {
		Backpack bp = c.backpack();
		int logs = SupplyChest.withdraw(chest, bp, LOGS, 8);
		if (logs == 0) {
			return false;
		}
		Crafting.ensurePlanks(bp, bp.count(PLANKS) + 4 * logs);
		if (SupplyChest.count(chest, STICKS) < 16) {
			Crafting.ensureSticks(bp, bp.count(STICKS) + 8);
		}
		if (SupplyChest.count(chest, TORCHES) < 32) {
			int coal = SupplyChest.withdraw(chest, bp, COAL, 2);
			if (coal > 0) {
				Crafting.ensureTorches(bp, bp.count(TORCHES) + 4 * coal);
			}
		}
		return true;
	}

	@Override
	public void stop(CompanionEntity c) {
		crafted = false;
	}

	@Override
	public int successCooldown() {
		return 400;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}
