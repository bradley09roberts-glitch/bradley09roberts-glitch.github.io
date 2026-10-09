package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Picking up things found on an expedition: a dropped item a friend may take is one no player threw (theirs are
 * the players' own), no friend threw (a gold ingot meant for a piglin), that is not right by a player (who may be
 * about to take it), not in lava or fire, and that fits in the backpack.
 */
final class Pickup {
	private Pickup() {
	}

	/** The nearest item within {@code radius} blocks matching {@code wanted} that this friend may take, or null. */
	static @Nullable ItemEntity nearest(CompanionEntity c, double radius, Predicate<ItemStack> wanted) {
		ServerLevel level = (ServerLevel) c.level();
		AABB box = c.getBoundingBox().inflate(radius, 3, radius);
		ItemEntity best = null;
		double bestDist = radius * radius;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && wanted.test(e.getItem()))) {
			double d = item.distanceToSqr(c);
			if (d < bestDist && mayTake(c, level, item)) {
				best = item;
				bestDist = d;
			}
		}
		return best;
	}

	/** True when this friend may take this item (see the class description). */
	static boolean mayTake(CompanionEntity c, ServerLevel level, ItemEntity item) {
		if (!item.isAlive() || item.hasPickUpDelay() || item.isInLava() || item.isOnFire()
			|| item.getOwner() instanceof Player || item.getOwner() instanceof CompanionEntity) {
			return false;
		}
		BlockPos at = item.blockPosition();
		if (level.getBlockState(at).is(BlockTags.FIRE) || !level.getFluidState(at).isEmpty()
			|| !level.getFluidState(at.below()).isEmpty()) {
			return false;
		}
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(item) < 2.5 * 2.5) {
				return false;
			}
		}
		return c.backpack().canFit(item.getItem());
	}

	/** Takes the item into the backpack (what does not fit stays on the ground). True if anything was taken. */
	static boolean take(CompanionEntity c, ItemEntity item) {
		if (!item.isAlive()) {
			return false;
		}
		ItemStack stack = item.getItem();
		int before = stack.getCount();
		ItemStack left = c.backpack().insert(stack.copy());
		int taken = before - left.getCount();
		if (taken <= 0) {
			return false;
		}
		c.take(item, taken);
		if (left.isEmpty()) {
			item.discard();
		} else {
			item.setItem(left);
		}
		return true;
	}
}
