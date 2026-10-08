package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/** One step of a trip to the supply chest to take materials for a job. Call every tick until it stops running. */
public final class ChestFetch {
	/** Progress of the trip. */
	public enum Result {
		RUNNING,
		DONE,
		FAILED
	}

	private static final double REACH = 2.0;

	private ChestFetch() {
	}

	/** True if the supply chest holds anything matching. */
	public static boolean chestHas(CompanionEntity c, Predicate<ItemStack> filter) {
		return SupplyChest.of((ServerLevel) c.level()).map(chest -> SupplyChest.count(chest, filter) > 0).orElse(false);
	}

	/** Walks to the chest, then moves up to {@code max} matching items into the backpack. */
	public static Result step(CompanionEntity c, Predicate<ItemStack> filter, int max) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<BlockPos> pos = Camp.data(level.getServer()).chestPos();
		Optional<Container> chest = SupplyChest.of(level);
		if (pos.isEmpty() || chest.isEmpty() || SupplyChest.count(chest.get(), filter) == 0) {
			return Result.FAILED;
		}
		if (!c.actions().walkTo(pos.get(), REACH)) {
			return c.actions().isStuck() ? Result.FAILED : Result.RUNNING;
		}
		return SupplyChest.withdraw(chest.get(), c.backpack(), filter, max) > 0 ? Result.DONE : Result.FAILED;
	}
}
