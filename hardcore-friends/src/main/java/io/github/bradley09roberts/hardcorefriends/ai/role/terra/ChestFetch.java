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
		if (!chestHas(c, filter)) {
			return Result.FAILED;
		}
		Result walk = reach(c);
		if (walk != Result.DONE) {
			return walk;
		}
		return take(c, filter, max) > 0 ? Result.DONE : Result.FAILED;
	}

	/** Walks towards the supply chest: {@link Result#DONE} once beside it, FAILED without a chest or when stuck. */
	public static Result reach(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<BlockPos> pos = Camp.data(level.getServer()).chestPos();
		if (pos.isEmpty() || SupplyChest.of(level).isEmpty()) {
			return Result.FAILED;
		}
		if (!c.actions().walkTo(pos.get(), REACH)) {
			return c.actions().isStuck() ? Result.FAILED : Result.RUNNING;
		}
		BlockPos p = pos.get();
		c.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
		return Result.DONE;
	}

	/** Moves up to {@code max} matching items from the chest into the backpack right now. Returns the amount. */
	public static int take(CompanionEntity c, Predicate<ItemStack> filter, int max) {
		if (max <= 0) {
			return 0;
		}
		Optional<Container> chest = SupplyChest.of((ServerLevel) c.level());
		return chest.map(box -> SupplyChest.withdraw(box, c.backpack(), filter, max)).orElse(0);
	}
}
