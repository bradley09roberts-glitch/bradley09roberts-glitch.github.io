package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.guard.Gear;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;

/**
 * Packing for an expedition. When a player sends their party off with {@code /friends party go} while a member is near
 * the camp, that member first stops at the supply chest: food for the trip (up to {@value #FOOD} pieces) and a potion
 * of fire resistance if the chest has one, and, carried by one member for the party, what the step of Sage's plan in
 * hand calls for: a few gold ingots to barter with piglins (while the plan is in the Nether), the eyes of ender for
 * the End portal (once the stronghold is found), and blocks to pillar up to a caged crystal (once the portal is open).
 * They take only what the chest has; back at work, the deposit job puts whatever is left back in the chest.
 */
public class PackGoal extends Goal {
	/** Food each member takes along. */
	static final int FOOD = 6;
	private static final int GOLD = 8;
	private static final int EYES = 12;
	private static final int PILLAR_BLOCKS = 32;
	/** How long after the order the packing may still happen, in ticks. */
	static final int PACK_WINDOW = 20 * 60;
	/** A member further than this from the player follows at once instead of packing. */
	private static final double LEAVE_BEHIND = 48;
	private static final int TIME = 20 * 30;

	private final CompanionEntity c;
	private int recheck;
	private int ticks;

	public PackGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/** Marks a party member to pack before following (see the class description). */
	static void order(CompanionEntity c) {
		Travel.state(c).putLong("pack", c.level().getGameTime() + PACK_WINDOW);
	}

	private static boolean ordered(CompanionEntity c) {
		return c.extra().getCompoundOrEmpty(Travel.KEY).getLongOr("pack", 0L) > c.level().getGameTime();
	}

	private static void done(CompanionEntity c) {
		var t = c.extra().getCompoundOrEmpty(Travel.KEY);
		if (t.contains("pack")) {
			t.remove("pack");
		}
	}

	@Override
	public boolean canUse() {
		if (--recheck > 0) {
			return false;
		}
		recheck = 20;
		if (c.mode() != CompanionMode.FOLLOW || !ordered(c) || c.getTarget() != null || !(c.level() instanceof ServerLevel level)) {
			return false;
		}
		ServerPlayer leader = c.leader();
		if (leader == null || leader.level() != level || leader.distanceToSqr(c) > LEAVE_BEHIND * LEAVE_BEHIND
			|| ChestWalk.chest(c).isEmpty()) {
			done(c);
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return ticks < TIME && ordered(c) && c.getTarget() == null && c.mode() == CompanionMode.FOLLOW;
	}

	@Override
	public void start() {
		ticks = 0;
	}

	@Override
	public void stop() {
		c.actions().stopWalking();
		done(c);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ticks++;
		switch (ChestWalk.tick(c)) {
			case ARRIVED -> {
				ChestWalk.chest(c).ifPresent(chest -> pack(c, chest));
				done(c);
			}
			case FAILED -> done(c);
			case WALKING -> {
			}
		}
	}

	/** Takes the personal kit, and the party's share if no other member already carries it. */
	private static void pack(CompanionEntity c, Container chest) {
		var bp = c.backpack();
		int food = KeepList.foodCount(bp);
		if (food < FOOD) {
			SupplyChest.withdraw(chest, bp, KeepList::isFood, FOOD - food);
		}
		if (!bp.has(Gear::isFireResistance)) {
			SupplyChest.withdraw(chest, bp, Gear::isFireResistance, 1);
		}
		if (!ProgressPlan.enabled()) {
			return;
		}
		MinecraftServer server = c.level().getServer();
		boolean nether = ProgressPlan.isDone(server, Milestone.NETHER_READY) && !ProgressPlan.isDone(server, Milestone.EYES_OF_ENDER);
		boolean portal = ProgressPlan.isDone(server, Milestone.STRONGHOLD) && !ProgressPlan.isDone(server, Milestone.END_PORTAL);
		boolean dragon = ProgressPlan.isDone(server, Milestone.END_PORTAL) && !ProgressPlan.isDone(server, Milestone.DRAGON);
		if (nether) {
			share(c, chest, s -> s.is(Items.GOLD_INGOT), GOLD);
		}
		if (portal) {
			share(c, chest, s -> s.is(Items.ENDER_EYE), EYES);
		}
		if (dragon) {
			share(c, chest, DragonFight::isPillarItem, PILLAR_BLOCKS);
		}
	}

	/** Takes up to {@code amount} for the party, unless another member already carries half that or more. */
	private static void share(CompanionEntity c, Container chest, Predicate<ItemStack> what, int amount) {
		UUID leader = ExpeditionData.get(c.level().getServer()).partyOf(c.getUUID()).orElse(null);
		for (CompanionEntity other : Companions.all()) {
			if (other == c || other.mode() != CompanionMode.FOLLOW) {
				continue;
			}
			boolean sameParty = leader != null && leader.equals(ExpeditionData.get(c.level().getServer()).partyOf(other.getUUID()).orElse(null));
			if (sameParty && other.backpack().count(what) >= amount / 2) {
				return;
			}
		}
		int have = c.backpack().count(what);
		if (have < amount) {
			SupplyChest.withdraw(chest, c.backpack(), what, amount - have);
		}
	}
}
