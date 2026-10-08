package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.progress.CampStock;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.progress.Tiers;

/**
 * The miner's pickaxe up the ladder Sage's plan needs: an iron pickaxe for diamonds, a diamond one for obsidian. A
 * better pickaxe already in the chest is fetched and held; otherwise, once the plan has reached that step, one is made
 * at the camp's crafting table from the chest's ingredients: an iron pickaxe from 3 iron ingots, the camp's single
 * diamond pickaxe from 3 diamonds (the first diamonds the camp keeps back go to it). Sticks come from the chest or are
 * cut from planks or logs.
 */
public final class PickaxeTask implements CompanionTask {
	private enum Phase {
		CHEST,
		TABLE
	}

	private Phase phase = Phase.CHEST;
	private int targetTier;
	private @Nullable BlockPos table;

	@Override
	public String id() {
		return "flint.better_pickaxe";
	}

	@Override
	public String describe() {
		return "getting a better pickaxe";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		return wantedTier(c, level.getServer()) > 0 ? 46 : 0;
	}

	/**
	 * The tier this friend should move up to now (0 for none): from the chest, or made from what the chest holds. Only
	 * the miner (or whoever covers mining while there is none) does this, so the camp's best pickaxe goes to the mine.
	 */
	private static int wantedTier(CompanionEntity c, MinecraftServer server) {
		if (c.friendId().role() != Role.MINER && Speciality.covering(c) != Role.MINER) {
			return 0;
		}
		int mine = Tiers.bestPickaxe(c);
		if (mine < 0) {
			return 0; // no pickaxe at all: the ordinary tool jobs see to that
		}
		int inChest = bestInChest(c);
		if (inChest > mine) {
			return inChest;
		}
		CampStock.Snapshot s = CampStock.get(server);
		boolean diamond = mine < Tiers.DIAMOND && ProgressPlan.reached(server, Milestone.DIAMONDS) && s.diamondPickaxes() == 0
			&& s.inChest(Items.DIAMOND) + c.backpack().count(Items.DIAMOND) >= 3;
		boolean iron = mine < Tiers.IRON && ProgressPlan.reached(server, Milestone.IRON_AGE)
			&& s.inChest(Items.IRON_INGOT) + c.backpack().count(Items.IRON_INGOT) >= 3;
		if (!diamond && !iron) {
			return 0;
		}
		boolean sticks = c.backpack().count(Items.STICK) >= 2 || c.backpack().has(st -> st.is(ItemTags.PLANKS) || st.is(ItemTags.LOGS))
			|| hasSticksInChest(c);
		return !sticks ? 0 : diamond ? Tiers.DIAMOND : Tiers.IRON;
	}

	private static boolean hasSticksInChest(CompanionEntity c) {
		return Trips.inChest(c, s -> s.is(Items.STICK)) >= 2 || Trips.inChest(c, s -> s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS)) >= 1;
	}

	private static int bestInChest(CompanionEntity c) {
		Optional<Container> chest = SupplyChest.of((ServerLevel) c.level());
		int best = -1;
		if (chest.isPresent()) {
			Container container = chest.get();
			for (int i = 0; i < container.getContainerSize(); i++) {
				best = Math.max(best, Tiers.pickaxe(container.getItem(i)));
			}
		}
		return best;
	}

	@Override
	public boolean start(CompanionEntity c) {
		targetTier = wantedTier(c, ((ServerLevel) c.level()).getServer());
		phase = Phase.CHEST;
		table = null;
		return targetTier > 0;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		return phase == Phase.CHEST ? chest(c) : table(c);
	}

	private TaskStatus chest(CompanionEntity c) {
		ChestWalk.State walk = ChestWalk.tick(c);
		if (walk == ChestWalk.State.FAILED) {
			return TaskStatus.FAILURE;
		}
		if (walk == ChestWalk.State.WALKING) {
			return TaskStatus.RUNNING;
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		int mine = Tiers.bestPickaxe(c);
		if (bestInChest(c) > mine) {
			int best = bestInChest(c);
			if (Trips.take(chest.get(), c, s -> Tiers.pickaxe(s) == best, 1) > 0) {
				hold(c);
				return TaskStatus.SUCCESS;
			}
			return TaskStatus.FAILURE;
		}
		// Make one: ingredients from the chest, then to the table.
		if (targetTier == Tiers.DIAMOND) {
			Trips.take(chest.get(), c, s -> s.is(Items.DIAMOND), 3 - c.backpack().count(Items.DIAMOND));
		} else {
			Trips.take(chest.get(), c, s -> s.is(Items.IRON_INGOT), 3 - c.backpack().count(Items.IRON_INGOT));
		}
		if (c.backpack().count(Items.STICK) < 2 && Trips.take(chest.get(), c, s -> s.is(Items.STICK), 2 - c.backpack().count(Items.STICK)) == 0
			&& !c.backpack().has(s -> s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS))) {
			Trips.take(chest.get(), c, s -> s.is(ItemTags.PLANKS), 2);
		}
		Crafting.ensureSticks(c.backpack(), 2);
		table = Trips.craftingTable(c);
		if (table == null) {
			return TaskStatus.FAILURE;
		}
		phase = Phase.TABLE;
		return TaskStatus.RUNNING;
	}

	private TaskStatus table(CompanionEntity c) {
		BlockPos at = table;
		if (at == null) {
			return TaskStatus.FAILURE;
		}
		if (!Crafting.nearCraftingTable(c) && !Trips.reach(c, at)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		boolean made = targetTier == Tiers.DIAMOND
			? Crafting.ensure(c, Items.DIAMOND_PICKAXE, c.backpack().count(Items.DIAMOND_PICKAXE) + 1)
			: Crafting.craftTool(c, ItemTags.PICKAXES);
		if (!made || Tiers.bestPickaxe(c) < targetTier) {
			return TaskStatus.FAILURE;
		}
		hold(c);
		Speech.say(c, Line.WORK_START, targetTier == Tiers.DIAMOND ? "making a diamond pickaxe" : "making an iron pickaxe");
		return TaskStatus.SUCCESS;
	}

	/** Holds the best pickaxe carried. */
	private static void hold(CompanionEntity c) {
		int best = Tiers.bestPickaxe(c);
		ItemStack hand = c.getMainHandItem();
		if (Tiers.pickaxe(hand) == best) {
			return;
		}
		c.actions().equip(s -> Tiers.pickaxe(s) == best);
	}

	@Override
	public void stop(CompanionEntity c) {
		table = null;
	}

	@Override
	public int failureCooldown() {
		return 1200;
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}
