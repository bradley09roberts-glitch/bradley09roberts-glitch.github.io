package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds.Need;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Hands spare items to a nearby friend whose job needs them: building materials to Oak while a build is short,
 * seeds and bone meal to Fern, coal and raw ore to Flint or Spark, saplings and flowers to Terra, and food to a
 * hurt friend who has none. Only surplus beyond the giver's keep-list is shared, and never the giver's role tool.
 */
public final class ShareTask implements CompanionTask {
	private static final double RANGE = 24.0;
	private static final double GIVE_REACH = 2.0;
	private static final double HURT_FRACTION = 0.6;

	/** Something a role wants, up to {@code wantCap} carried. {@code need} gates building materials. */
	private record ShareRule(Set<Role> roles, Predicate<ItemStack> item, int wantCap, @Nullable Need need) {
		boolean wantedBy(CompanionEntity friend) {
			return roles.contains(friend.friendId().role()) && (need == null || buildShortage(need));
		}
	}

	private static final Predicate<ItemStack> SEEDS = s -> s.is(Items.WHEAT_SEEDS) || s.is(Items.BEETROOT_SEEDS)
		|| s.is(Items.MELON_SEEDS) || s.is(Items.PUMPKIN_SEEDS);
	private static final Predicate<ItemStack> RAW_ORE = s -> s.is(Items.RAW_IRON) || s.is(Items.RAW_COPPER)
		|| s.is(Items.RAW_GOLD);

	private static final List<ShareRule> RULES = List.of(
		new ShareRule(Set.of(Role.BUILDER), s -> s.is(ItemTags.LOGS), 32, Need.WOOD),
		new ShareRule(Set.of(Role.BUILDER), s -> s.is(ItemTags.PLANKS), 64, Need.WOOD),
		new ShareRule(Set.of(Role.BUILDER), s -> s.is(Items.COBBLESTONE), 64, Need.STONE),
		new ShareRule(Set.of(Role.BUILDER), s -> s.is(Items.DIRT), 32, Need.DIRT),
		new ShareRule(Set.of(Role.BUILDER), s -> s.is(Items.GLASS), 16, Need.BUILD),
		new ShareRule(Set.of(Role.FARMER), SEEDS, 64, null),
		new ShareRule(Set.of(Role.FARMER), s -> s.is(Items.BONE_MEAL), 64, null),
		new ShareRule(Set.of(Role.INVENTOR, Role.MINER), s -> s.is(ItemTags.COALS), 16, null),
		new ShareRule(Set.of(Role.MINER, Role.INVENTOR), RAW_ORE, 64, null),
		new ShareRule(Set.of(Role.LANDSCAPER), s -> s.is(ItemTags.SAPLINGS), 16, null),
		new ShareRule(Set.of(Role.LANDSCAPER), s -> s.is(BlockItemTags.SMALL_FLOWERS.item()), 16, null));

	/** A planned hand-over: what to give and to whom. */
	private record Plan(CompanionEntity recipient, ItemStack sample, Predicate<ItemStack> category, int wantCap, boolean food) {
	}

	private @Nullable Plan planned;
	private @Nullable Plan current;
	private final EntityApproach approach = new EntityApproach();

	@Override
	public String id() {
		return "common.share";
	}

	@Override
	public String describe() {
		return "sharing supplies";
	}

	@Override
	public double score(CompanionEntity c) {
		planned = findPlan(c);
		if (planned == null) {
			return 0;
		}
		// Feeding a hurt friend and unblocking the builder come before routine gathering.
		if (planned.food()) {
			return 70;
		}
		if (planned.recipient().friendId().role() == io.github.bradley09roberts.hardcorefriends.companion.Role.BUILDER
			&& !CampNeeds.buildShortage().isEmpty()) {
			return 55 + 10 * c.friendId().generosity();
		}
		return 25 + 25 * c.friendId().generosity();
	}

	/** True when the builder has reported a shortage that this kind of material helps with. */
	static boolean buildShortage(Need need) {
		Map<Need, Integer> shortage = CampNeeds.buildShortage();
		if (shortage.isEmpty() || CampNeeds.need(Need.BUILD) <= 0) {
			return false;
		}
		return shortage.containsKey(need) || shortage.containsKey(Need.BUILD);
	}

	private static @Nullable Plan findPlan(CompanionEntity c) {
		int[] surplus = KeepList.surplusBySlot(c);
		boolean anySurplus = false;
		for (int n : surplus) {
			anySurplus |= n > 0;
		}
		boolean canFeed = KeepList.foodCount(c.backpack()) >= 2;
		if (!anySurplus && !canFeed) {
			return null;
		}
		ServerLevel level = (ServerLevel) c.level();
		for (CompanionEntity friend : Companions.near(level, c.getBoundingBox().inflate(RANGE))) {
			if (friend == c || !friend.isAlive() || friend.isRetreating() || friend.distanceToSqr(c) > RANGE * RANGE) {
				continue;
			}
			Plan plan = planFor(c, friend, surplus, canFeed);
			if (plan != null) {
				return plan;
			}
		}
		return null;
	}

	private static @Nullable Plan planFor(CompanionEntity giver, CompanionEntity friend, int[] surplus, boolean canFeed) {
		if (canFeed && needsFood(friend)) {
			ItemStack food = bestFood(giver.backpack());
			if (!food.isEmpty() && friend.backpack().canFit(food.copyWithCount(1))) {
				return new Plan(friend, food.copyWithCount(1), KeepList::isFood, 0, true);
			}
		}
		for (ShareRule rule : RULES) {
			// Two friends who both use an item never pass it back and forth.
			if (!rule.wantedBy(friend) || rule.roles().contains(giver.friendId().role())) {
				continue;
			}
			ItemStack sample = spareSample(giver, surplus, rule.item());
			if (sample.isEmpty() || friend.backpack().count(rule.item()) >= rule.wantCap()) {
				continue;
			}
			if (friend.backpack().canFit(sample.copyWithCount(1))) {
				return new Plan(friend, sample.copyWithCount(1), rule.item(), rule.wantCap(), false);
			}
		}
		return null;
	}

	private static boolean needsFood(CompanionEntity friend) {
		return friend.getHealth() < friend.getMaxHealth() * HURT_FRACTION && !friend.hasFood();
	}

	private static ItemStack bestFood(Backpack bp) {
		ItemStack best = ItemStack.EMPTY;
		for (ItemStack s : bp.stacks()) {
			if (KeepList.isFood(s) && (best.isEmpty() || KeepList.foodValue(s) > KeepList.foodValue(best))) {
				best = s;
			}
		}
		return best;
	}

	/** A backpack stack with surplus matching the filter, never the giver's role tool. */
	private static ItemStack spareSample(CompanionEntity giver, int[] surplus, Predicate<ItemStack> filter) {
		Role role = giver.friendId().role();
		for (int slot = 0; slot < surplus.length; slot++) {
			ItemStack stack = giver.backpack().get(slot);
			if (surplus[slot] > 0 && filter.test(stack) && !KeepList.isRoleTool(role, stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Surplus of exactly this item (same item and components). */
	private static int spareOf(CompanionEntity giver, ItemStack sample) {
		int[] surplus = KeepList.surplusBySlot(giver);
		int total = 0;
		for (int slot = 0; slot < surplus.length; slot++) {
			if (surplus[slot] > 0 && ItemStack.isSameItemSameComponents(giver.backpack().get(slot), sample)) {
				total += surplus[slot];
			}
		}
		return total;
	}

	@Override
	public boolean start(CompanionEntity c) {
		current = planned;
		planned = null;
		approach.reset();
		return current != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Plan plan = current;
		if (plan == null) {
			return TaskStatus.FAILURE;
		}
		CompanionEntity friend = plan.recipient();
		if (!friend.isAlive() || friend.isRemoved() || friend.level() != c.level() || friend.distanceToSqr(c) > 32 * 32) {
			return TaskStatus.FAILURE;
		}
		if (!approach.walk(c, friend, GIVE_REACH)) {
			return approach.isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(friend);
		int amount;
		if (plan.food()) {
			int food = KeepList.foodCount(c.backpack());
			amount = needsFood(friend) && food >= 2 ? Math.min(2, food / 2) : 0;
		} else {
			int has = friend.backpack().count(plan.category());
			amount = Math.min(spareOf(c, plan.sample()), plan.wantCap() - has);
		}
		amount = Math.min(amount, plan.sample().getMaxStackSize());
		if (amount <= 0) {
			return TaskStatus.FAILURE;
		}
		ItemStack given = c.backpack().take(s -> ItemStack.isSameItemSameComponents(s, plan.sample()), amount);
		if (given.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		ItemStack left = friend.backpack().insert(given);
		int moved = given.getCount() - left.getCount();
		if (!left.isEmpty()) {
			ItemStack back = c.backpack().insert(left);
			if (!back.isEmpty()) {
				c.spawnAtLocation((ServerLevel) c.level(), back);
			}
		}
		if (moved <= 0) {
			return TaskStatus.FAILURE; // it did not fit after all
		}
		ServerLevel level = (ServerLevel) c.level();
		c.swingArm();
		Speech.say(c, Line.SHARE, friend.friendId().displayName(), Upkeep.describe(given, moved));
		Unity.add(level, Unity.HANDOFF, 2, 40);
		Camp.data(level.getServer()).addStat("shares", 1);
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		current = null;
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
		return 20 * 40;
	}
}
