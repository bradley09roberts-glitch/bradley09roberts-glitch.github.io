package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * The containers the trades work with, and walking to them. A workplace's chests count only while they are the
 * friends' own (placed by them when the building went up): a chest a player put there instead is never opened. The
 * supply chest is the one the players linked. Nothing is ever lost: whatever does not fit where it should goes to the
 * supply chest, then the friend's backpack, and only then onto the ground at their feet.
 */
final class Stores {
	/** How a walk to a block is going. */
	enum Walk {
		WALKING,
		ARRIVED,
		FAILED
	}

	private Stores() {
	}

	/** The friends' own chest or barrel at {@code pos}, if it is loaded and still theirs. */
	static Optional<Container> own(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !Camp.data(level.getServer()).isPlacedByFriends(level, pos)) {
			return Optional.empty();
		}
		return SupplyChest.at(level, pos);
	}

	/** The workplace's own chests that are still there. */
	static List<BlockPos> chests(ServerLevel level, @Nullable Workplace w) {
		List<BlockPos> list = new ArrayList<>();
		if (w == null) {
			return list;
		}
		for (BlockPos p : w.chests()) {
			if (own(level, p).isPresent()) {
				list.add(p);
			}
		}
		return list;
	}

	static Optional<BlockPos> supplyPos(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		return Camp.isCampLevel(level, data) && SupplyChest.of(level).isPresent() ? data.chestPos() : Optional.empty();
	}

	/** The container at a position: the supply chest, or one of the friends' own. */
	static Optional<Container> at(ServerLevel level, BlockPos pos) {
		Optional<BlockPos> supply = supplyPos(level);
		if (supply.isPresent() && supply.get().equals(pos)) {
			return SupplyChest.of(level);
		}
		return own(level, pos);
	}

	/** How many matching items the containers at these positions hold together. */
	static int count(ServerLevel level, List<BlockPos> where, Predicate<ItemStack> match) {
		int n = 0;
		for (BlockPos p : where) {
			Optional<Container> c = at(level, p);
			if (c.isPresent()) {
				n += SupplyChest.count(c.get(), match);
			}
		}
		return n;
	}

	static int supplyCount(ServerLevel level, Predicate<ItemStack> match) {
		Optional<Container> chest = SupplyChest.of(level);
		return chest.map(c -> SupplyChest.count(c, match)).orElse(0);
	}

	/**
	 * Puts a stack away: into the first of {@code where} with room, then the supply chest, then the backpack, then on
	 * the ground at the friend's feet. Never loses an item.
	 */
	static void putAway(CompanionEntity c, List<BlockPos> where, ItemStack stack) {
		ServerLevel level = (ServerLevel) c.level();
		ItemStack left = stack;
		for (BlockPos p : where) {
			if (left.isEmpty()) {
				return;
			}
			Optional<Container> box = at(level, p);
			if (box.isPresent()) {
				left = SupplyChest.insert(box.get(), left);
			}
		}
		if (!left.isEmpty()) {
			Optional<Container> supply = SupplyChest.of(level);
			if (supply.isPresent()) {
				left = SupplyChest.insert(supply.get(), left);
			}
		}
		if (!left.isEmpty()) {
			left = c.backpack().insert(left);
		}
		if (!left.isEmpty()) {
			c.spawnAtLocation(level, left);
		}
	}

	/** Moves up to {@code max} matching items from the backpack into the container at {@code pos}. */
	static int deposit(CompanionEntity c, BlockPos pos, Predicate<ItemStack> match, int max) {
		Optional<Container> box = at((ServerLevel) c.level(), pos);
		return box.map(container -> SupplyChest.deposit(c.backpack(), container, match, max)).orElse(0);
	}

	/** Moves up to {@code max} matching items from the container at {@code pos} into the backpack. */
	static int withdraw(CompanionEntity c, BlockPos pos, Predicate<ItemStack> match, int max) {
		Optional<Container> box = at((ServerLevel) c.level(), pos);
		return box.map(container -> SupplyChest.withdraw(container, c.backpack(), match, max)).orElse(0);
	}

	/**
	 * One tick of walking to within reach of a block (a chest, a work station, a counter). {@link Walk#ARRIVED} once
	 * the friend can reach it and is within {@code reach} of it, {@link Walk#FAILED} if the way is lost.
	 */
	static Walk walk(CompanionEntity c, BlockPos to, double reach) {
		Actions actions = c.actions();
		double d2 = c.position().distanceToSqr(Vec3.atBottomCenterOf(to));
		if (d2 <= reach * reach && actions.canReach(to)) {
			actions.stopWalking();
			c.getLookControl().setLookAt(Vec3.atCenterOf(to));
			return Walk.ARRIVED;
		}
		if (actions.walkTo(to, Math.max(0.5, reach - 0.5))) {
			return actions.canReach(to) || d2 <= reach * reach + 4 ? Walk.ARRIVED : Walk.FAILED;
		}
		return actions.isStuck() ? Walk.FAILED : Walk.WALKING;
	}

	/** One tick of walking to stand on a spot (a counter, a fishing deck): within {@code slack} of it. */
	static Walk stand(CompanionEntity c, BlockPos spot, double slack) {
		Actions actions = c.actions();
		if (c.position().distanceToSqr(Vec3.atBottomCenterOf(spot)) <= slack * slack) {
			actions.stopWalking();
			return Walk.ARRIVED;
		}
		if (actions.walkTo(spot, slack)) {
			return Walk.ARRIVED;
		}
		return actions.isStuck() ? Walk.FAILED : Walk.WALKING;
	}

	/** True if the backpack can take one more of each of these without spilling. */
	static boolean room(Backpack bp, int slots) {
		return bp.freeSlots() >= slots;
	}
}
