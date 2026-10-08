package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Gives a little food to a hungry player nearby. Generous friends (Fern, Rowan) do this from the start; everyone
 * else once the team bond reaches Acquaintances. Each player is fed at most once every two minutes by each friend,
 * and only from food the friend actually carries.
 */
public final class FeedPlayerTask implements CompanionTask {
	private static final double RANGE = 16.0;
	private static final int HUNGRY_AT = 10;
	private static final int MIN_FOOD = 2;
	private static final int PLAYER_COOLDOWN = 2400;

	private final Map<UUID, Long> fedUntil = new HashMap<>();
	private @Nullable ServerPlayer planned;
	private @Nullable ServerPlayer target;

	@Override
	public String id() {
		return "common.feed_player";
	}

	@Override
	public String describe() {
		return "sharing food";
	}

	@Override
	public double score(CompanionEntity c) {
		planned = null;
		ServerLevel level = (ServerLevel) c.level();
		if (Unity.level(level.getServer()) < 1 && c.friendId().generosity() < 0.9) {
			return 0;
		}
		if (KeepList.foodCount(c.backpack()) < MIN_FOOD) {
			return 0;
		}
		long now = level.getGameTime();
		fedUntil.values().removeIf(until -> until <= now);
		double best = RANGE * RANGE;
		for (ServerPlayer player : level.players()) {
			if (!isHungry(player) || fedUntil.containsKey(player.getUUID())) {
				continue;
			}
			double d = player.distanceToSqr(c);
			if (d <= best) {
				best = d;
				planned = player;
			}
		}
		if (planned == null) {
			return 0;
		}
		return c.friendId() == FriendId.FERN ? 60 : 50;
	}

	private static boolean isHungry(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative()
			&& player.getFoodData().getFoodLevel() <= HUNGRY_AT;
	}

	@Override
	public boolean start(CompanionEntity c) {
		target = planned;
		planned = null;
		return target != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerPlayer player = target;
		if (player == null || !player.isAlive() || player.level() != c.level() || player.distanceToSqr(c) > 24 * 24) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkToEntity(player, 2.0)) {
			return TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(player);
		int carried = KeepList.foodCount(c.backpack());
		if (carried < MIN_FOOD || !isHungry(player)) {
			return TaskStatus.FAILURE;
		}
		int missing = 20 - player.getFoodData().getFoodLevel();
		int amount = Math.clamp(missing / 5, 1, 3);
		amount = Math.min(amount, carried - 1);
		ItemStack food = takeBestFood(c, amount);
		if (food.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		ItemStack given = food.copy();
		player.getInventory().add(food); // shrinks the stack by what fitted
		dropAtFeet(player, food);
		ServerLevel level = (ServerLevel) c.level();
		c.swingArm();
		Speech.say(c, Line.SHARE, player.getName().getString(), Upkeep.describe(given, given.getCount()));
		Camp.data(level.getServer()).addStat("players_fed", 1);
		fedUntil.put(player.getUUID(), level.getGameTime() + PLAYER_COOLDOWN);
		return TaskStatus.SUCCESS;
	}

	private static ItemStack takeBestFood(CompanionEntity c, int amount) {
		ItemStack best = ItemStack.EMPTY;
		for (ItemStack s : c.backpack().stacks()) {
			if (KeepList.isFood(s) && (best.isEmpty() || KeepList.foodValue(s) > KeepList.foodValue(best))) {
				best = s;
			}
		}
		if (best.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack sample = best.copyWithCount(1);
		return c.backpack().take(s -> ItemStack.isSameItemSameComponents(s, sample), amount);
	}

	/** Leaves food the player had no room for at their feet, marked as theirs so friends do not tidy it away. */
	private static void dropAtFeet(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY() + 0.25, player.getZ(), stack.copy());
		entity.setThrower(player);
		entity.setNoPickUpDelay();
		player.level().addFreshEntity(entity);
		stack.setCount(0);
	}

	@Override
	public void stop(CompanionEntity c) {
		target = null;
	}

	@Override
	public int failureCooldown() {
		return 400;
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public int maxTicks() {
		return 20 * 30;
	}
}
