package io.github.bradley09roberts.hardcorefriends.ai.role.spark;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Spark keeps the torch supply up: with coal and some wood in the supply chest, Spark takes a little of each,
 * makes torches (one coal and one stick make four) and puts them back for everyone.
 */
public final class TorchTask implements CompanionTask {
	private static final Predicate<ItemStack> COAL = s -> s.is(ItemTags.COALS);
	private static final Predicate<ItemStack> STICKS = s -> s.is(Items.STICK);
	private static final Predicate<ItemStack> PLANKS = s -> s.is(ItemTags.PLANKS);
	private static final Predicate<ItemStack> LOGS = s -> s.is(ItemTags.LOGS);
	private static final Predicate<ItemStack> TORCHES = s -> s.is(Items.TORCH);
	private static final Predicate<ItemStack> RETURNED = TORCHES.or(COAL).or(STICKS).or(PLANKS).or(LOGS);
	/** Torches kept in the chest before Spark stops making more. */
	private static final int STOCK_TARGET = 32;
	private static final int WORK_TICKS = 20;

	private boolean crafted;
	private int workTicks;

	@Override
	public String id() {
		return "spark.torches";
	}

	@Override
	public String describe() {
		return "making torches";
	}

	@Override
	public double score(CompanionEntity c) {
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty()) {
			return 0;
		}
		Container ch = chest.get();
		boolean wood = SupplyChest.count(ch, STICKS) > 0 || SupplyChest.count(ch, PLANKS) > 0 || SupplyChest.count(ch, LOGS) > 0
			|| c.backpack().has(STICKS.or(PLANKS));
		boolean coal = SupplyChest.count(ch, COAL) > 0 || c.backpack().has(COAL);
		if (!wood || !coal || SupplyChest.count(ch, TORCHES) >= STOCK_TARGET) {
			return 0;
		}
		return 30 * CampNeeds.weight(CampNeeds.Need.TORCHES);
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
			if (chest.isEmpty() || !makeTorches(c, chest.get())) {
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
		SupplyChest.deposit(c.backpack(), chest.get(), RETURNED, 64 * 9);
		return TaskStatus.SUCCESS;
	}

	/** Takes up to four coal plus the wood for as many sticks and crafts torches. */
	private static boolean makeTorches(CompanionEntity c, Container chest) {
		Backpack bp = c.backpack();
		SupplyChest.withdraw(chest, bp, COAL, Math.max(0, 4 - bp.count(COAL)));
		int coal = Math.min(4, bp.count(COAL));
		if (coal == 0) {
			return false;
		}
		int sticksShort = coal - bp.count(STICKS);
		if (sticksShort > 0) {
			SupplyChest.withdraw(chest, bp, STICKS, sticksShort);
			sticksShort = coal - bp.count(STICKS);
		}
		if (sticksShort > 0 && bp.count(PLANKS) < 2) {
			SupplyChest.withdraw(chest, bp, PLANKS, 2 - bp.count(PLANKS));
			if (bp.count(PLANKS) < 2) {
				SupplyChest.withdraw(chest, bp, LOGS, 1);
			}
		}
		int before = bp.count(TORCHES);
		Crafting.ensureTorches(bp, before + 4 * coal);
		return bp.count(TORCHES) > before;
	}

	@Override
	public void stop(CompanionEntity c) {
		crafted = false;
	}

	@Override
	public int successCooldown() {
		return 600;
	}

	@Override
	public int failureCooldown() {
		return 1200;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}
