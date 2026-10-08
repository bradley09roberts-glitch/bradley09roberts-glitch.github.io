package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Pen;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.registry.ModItems;

/**
 * Picks up stray items lying near the friend so nothing useful is lost. Items stay untouched while they are fresh
 * (under ten seconds old), when a player threw them, and always when they are a fallen friend's backpack.
 */
public final class CollectItemsTask implements CompanionTask {
	private static final double RANGE = 8.0;
	/** Items must have lain on the ground this long before a friend tidies them. */
	public static final int MIN_AGE = 200;
	private static final double PICK_REACH = 1.5;
	private static final int IGNORE_TICKS = 1200;

	private final Map<UUID, Long> ignoredUntil = new HashMap<>();
	private @Nullable ItemEntity planned;
	private @Nullable ItemEntity target;

	@Override
	public String id() {
		return "common.collect_items";
	}

	@Override
	public String describe() {
		return "tidying up";
	}

	@Override
	public double score(CompanionEntity c) {
		planned = null;
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		ignoredUntil.values().removeIf(until -> until <= now);
		double best = RANGE * RANGE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, c.getBoundingBox().inflate(RANGE, 4, RANGE),
			e -> mayCollect(c, e) && !ignored(e))) {
			double d = item.distanceToSqr(c);
			if (d <= best) {
				best = d;
				planned = item;
			}
		}
		if (planned == null) {
			return 0;
		}
		return KeepList.isUseful(c.friendId().role(), planned.getItem()) ? 35 : 25;
	}

	/**
	 * Whether a friend may tidy this item away: old enough, not a backpack, not thrown by a player, not shut in the
	 * animal pen (an egg a hen laid: out of reach behind the fence, unless the friend is in the paddock too), and it
	 * fits.
	 */
	public static boolean mayCollect(CompanionEntity c, ItemEntity item) {
		if (!item.isAlive() || item.isRemoved() || item.isInLava()) {
			return false;
		}
		ItemStack stack = item.getItem();
		if (stack.isEmpty() || stack.is(ModItems.BACKPACK) || item.getAge() < MIN_AGE) {
			return false;
		}
		Entity thrower = item.getOwner();
		if (thrower instanceof Player) {
			return false;
		}
		if (c.level() instanceof ServerLevel level
			&& Pen.site(level).filter(p -> p.covers(item.blockPosition()) && !p.holds(c)).isPresent()) {
			return false;
		}
		return c.backpack().canFit(stack.copyWithCount(1));
	}

	private boolean ignored(ItemEntity item) {
		return ignoredUntil.containsKey(item.getUUID());
	}

	@Override
	public boolean start(CompanionEntity c) {
		target = planned;
		planned = null;
		return target != null && !ignored(target);
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ItemEntity item = target;
		if (item == null || !item.isAlive() || item.isRemoved()) {
			return TaskStatus.SUCCESS; // someone else picked it up
		}
		if (!mayCollect(c, item)) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(item.blockPosition(), PICK_REACH)) {
			if (c.actions().isStuck()) {
				ignoredUntil.put(item.getUUID(), c.level().getGameTime() + IGNORE_TICKS);
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		ItemStack stack = item.getItem();
		int before = stack.getCount();
		ItemStack left = c.backpack().insert(stack.copy());
		int picked = before - left.getCount();
		if (picked <= 0) {
			ignoredUntil.put(item.getUUID(), c.level().getGameTime() + IGNORE_TICKS);
			return TaskStatus.FAILURE;
		}
		c.take(item, picked);
		if (left.isEmpty()) {
			item.discard();
		} else {
			item.setItem(left);
		}
		Camp.data(((ServerLevel) c.level()).getServer()).addStat("items_tidied", picked);
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		target = null;
	}

	@Override
	public int failureCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return 20 * 30;
	}
}
