package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Picking up things found on an expedition: a dropped item a friend may take is one no player threw (theirs are
 * the players' own), no friend threw (a gold ingot meant for a piglin), that a piglin threw only for the friends' own
 * gold (what it throws for a player's gold is the player's), that is not where a player died lately (their things, for
 * the town's keeping-safe job), not within {@value #PLAYER_GAP} blocks of a player (who may be about to take it), not
 * in lava or fire, and that fits in the backpack.
 */
final class Pickup {
	/** Items this close to a player are left for them. */
	private static final double PLAYER_GAP = 4;
	/** Things lying without a thrower this close to where a player died lately are theirs. */
	private static final double DEATH_REACH = 8;
	/** How long a player's death spot is remembered: as long as an item lasts on the ground (five minutes). */
	private static final long DEATH_MEMORY = 6000;

	/** Where a player died, and when (game time). */
	private record Death(ResourceKey<Level> dim, Vec3 pos, long at) {
	}

	private static final List<Death> DEATHS = new ArrayList<>();

	private Pickup() {
	}

	/** Remembers where a player died: what lies there without a thrower is never picked up (see the class description). */
	static void playerDied(ServerPlayer player) {
		long now = player.level().getGameTime();
		DEATHS.removeIf(d -> now - d.at() > DEATH_MEMORY || now < d.at());
		DEATHS.add(new Death(player.level().dimension(), player.position(), now));
	}

	/** Forgets the death spots (a server stopping). */
	static void clear() {
		DEATHS.clear();
	}

	private static boolean nearDeath(ServerLevel level, ItemEntity item) {
		long now = level.getGameTime();
		for (Death d : DEATHS) {
			if (d.dim() == level.dimension() && now - d.at() <= DEATH_MEMORY && now >= d.at()
				&& d.pos().distanceToSqr(item.position()) <= DEATH_REACH * DEATH_REACH) {
				return true;
			}
		}
		return false;
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
		if (!item.isAlive() || item.hasPickUpDelay() || item.isInLava() || item.isOnFire()) {
			return false;
		}
		Entity owner = item.getOwner();
		if (owner instanceof Player || owner instanceof CompanionEntity
			|| owner instanceof Piglin piglin && !NetherHelpGoal.friendsTrade(level, piglin, item)
			|| owner == null && nearDeath(level, item)) {
			return false;
		}
		BlockPos at = item.blockPosition();
		if (level.getBlockState(at).is(BlockTags.FIRE) || !level.getFluidState(at).isEmpty()
			|| !level.getFluidState(at.below()).isEmpty()) {
			return false;
		}
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(item) < PLAYER_GAP * PLAYER_GAP) {
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
