package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.build.CampFeatures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;

/**
 * Small shared steps for the plan's jobs: walking up to a block, taking things from and putting them back in the
 * supply chest, and the checks that send a friend home from deep work (hurt, hungry, no food, no light, a full pack).
 */
final class Trips {
	/** Below this share of their health a friend stops deep work and heads home. */
	static final double MIN_HEALTH = 0.6;
	/** Below this hunger a friend stops deep work and heads home. */
	static final double MIN_HUNGER = 30;

	private Trips() {
	}

	/** Walks within reach of a block (standing no further than 3 blocks off). True once there. */
	static boolean reach(CompanionEntity c, BlockPos pos) {
		if (c.actions().canReach(pos) && c.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) < 9) {
			c.actions().stopWalking();
			c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
			return true;
		}
		c.actions().walkTo(pos, 2.0);
		return false;
	}

	/** A crafting table near the friend, the supply chest or the camp centre (the camp's own, by the chest), or null. */
	static @Nullable BlockPos craftingTable(CompanionEntity c) {
		Optional<BlockPos> near = Crafting.findNearby(c, 8);
		if (near.isPresent()) {
			return near.get();
		}
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		for (BlockPos centre : new BlockPos[] {data.chestPos().orElse(null), data.campPos().orElse(null)}) {
			if (centre != null && Camp.isCampLevel(level, data)) {
				BlockPos found = CampFeatures.find(level, centre, 6, s -> s.is(Blocks.CRAFTING_TABLE));
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** True when every chunk within {@code r} blocks (sideways) of the block is loaded, so looking round it loads none. */
	static boolean loaded(ServerLevel level, BlockPos pos, int r) {
		return level.hasChunkAt(pos.getX() - r, pos.getZ() - r) && level.hasChunkAt(pos.getX() + r, pos.getZ() - r)
			&& level.hasChunkAt(pos.getX() - r, pos.getZ() + r) && level.hasChunkAt(pos.getX() + r, pos.getZ() + r);
	}

	static int take(Container chest, CompanionEntity c, Predicate<ItemStack> filter, int max) {
		return max <= 0 ? 0 : SupplyChest.withdraw(chest, c.backpack(), filter, max);
	}

	/** Puts every matching item the friend carries into the chest. Returns how many went in. */
	static int putBack(Container chest, CompanionEntity c, Predicate<ItemStack> filter) {
		return SupplyChest.deposit(c.backpack(), chest, filter, 64 * 27);
	}

	/** The chest's count of matching items, 0 without a chest. */
	static int inChest(CompanionEntity c, Predicate<ItemStack> filter) {
		return SupplyChest.of((ServerLevel) c.level()).map(chest -> SupplyChest.count(chest, filter)).orElse(0);
	}

	/**
	 * Fit for deep work: well enough (at least {@value #MIN_HEALTH} of their health), fed (hunger at least
	 * {@value #MIN_HUNGER}), with food in the pack, a way to light the way and room for what they dig.
	 */
	static boolean fitForDeepWork(CompanionEntity c, int freeSlots) {
		return c.getHealth() >= c.getMaxHealth() * MIN_HEALTH
			&& c.needs().get(Needs.Need.HUNGER) >= MIN_HUNGER
			&& KeepList.foodCount(c.backpack()) > 0
			&& hasLight(c)
			&& c.backpack().freeSlots() >= freeSlots;
	}

	/** Two torches carried, or coal and something to make sticks from. */
	static boolean hasLight(CompanionEntity c) {
		if (c.backpack().count(Items.TORCH) >= 2) {
			return true;
		}
		return c.backpack().has(s -> s.is(ItemTags.COALS))
			&& c.backpack().has(s -> s.is(Items.STICK) || s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS));
	}

	/** Blocks carried for sealing holes and gaps: cobblestone and cobbled deepslate. */
	static int sealBlocks(CompanionEntity c) {
		return c.backpack().count(s -> s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE));
	}
}
