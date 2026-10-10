package io.github.bradley09roberts.hardcorefriends.life;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * The feast's cook (the innkeeper, else the farmer, else whoever has been with the camp longest: see
 * {@link Gatherings#cookFor}) gets the food ready on a feast afternoon: at the supply chest they bake bread from spare
 * wheat at the crafting table, then take food for everyone, the best first, which they hand out at the feast. Only
 * from real stock and never more than half of what is there, and less (or none) when the camp is short of food; the
 * rest stays for everyday meals. Food not eaten goes back to the chest after the feast (a job filter keeps the cook
 * from putting it away before). Once a feast day.
 */
final class FeastCookTask implements CompanionTask {
	static final String ID = "life.feast_cook";
	private static final double SCORE = 55;
	/** Once the feast has begun the food comes first, before the cook joins in. */
	private static final double SCORE_LATE = 82;
	private static final long FROM = 8000;
	private static final long UNTIL = 11000;
	/** Ready food, the most filling first. */
	private static final List<Predicate<ItemStack>> MENU = List.of(
		s -> s.is(Items.COOKED_BEEF) || s.is(Items.COOKED_PORKCHOP) || s.is(Items.COOKED_MUTTON),
		s -> s.is(Items.PUMPKIN_PIE) || s.is(Items.RABBIT_STEW),
		s -> s.is(Items.COOKED_CHICKEN) || s.is(Items.COOKED_SALMON) || s.is(Items.COOKED_COD) || s.is(Items.COOKED_RABBIT),
		s -> s.is(Items.BREAD) || s.is(Items.BAKED_POTATO),
		CompanionEntity::isEdible);

	/** The day the feast's food was fetched (once a feast day). */
	private static long fetchedDay = -1;

	static void clear() {
		fetchedDay = -1;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "getting the feast ready";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!FriendsConfig.get().villageLife || c.isChild() || !(c.level() instanceof ServerLevel level) || !Places.free(c)) {
			return 0;
		}
		long day = Calendar.today(level.getServer());
		long time = Calendar.time(level.getServer());
		if (fetchedDay == day || time < FROM || time >= UNTIL || Calendar.feastOn(day) == null) {
			return 0;
		}
		Gatherings.Gathering g = Gatherings.active();
		boolean feastOn = g != null && g.kind == Gatherings.Kind.FEAST;
		if (!feastOn && LifeData.get(level.getServer()).isDone("feast:" + day)) {
			return 0; // over already, or no feast this year
		}
		if (!c.getUUID().equals(Gatherings.cookFor(level, day)) || share(c) <= 0) {
			return 0;
		}
		return feastOn ? SCORE_LATE : SCORE;
	}

	/** How many portions the camp can spare for the feast: all for everyone when food is plentiful, fewer when not. */
	private static int share(CompanionEntity c) {
		double need = CampNeeds.need(CampNeeds.Need.FOOD);
		if (need > 0.6) {
			return 0;
		}
		int people = Math.max(2, Places.freePeople((ServerLevel) c.level()).size());
		int wanted = Math.min(16, people + 2);
		return need <= 0.35 ? wanted : wanted / 2;
	}

	@Override
	public boolean start(CompanionEntity c) {
		return SupplyChest.of((ServerLevel) c.level()).isPresent();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			default -> {
			}
		}
		ServerLevel level = (ServerLevel) c.level();
		Container chest = SupplyChest.of(level).orElse(null);
		if (chest == null) {
			return TaskStatus.FAILURE;
		}
		fetchedDay = Calendar.today(level.getServer());
		bake(c, chest);
		int wanted = share(c);
		int stored = SupplyChest.count(chest, CompanionEntity::isEdible);
		int portions = Math.min(wanted, stored / 2);
		int taken = 0;
		for (Predicate<ItemStack> dish : MENU) {
			if (taken >= portions) {
				break;
			}
			taken += SupplyChest.withdraw(chest, c.backpack(), dish, portions - taken);
		}
		if (taken > 0) {
			Gatherings.carryingFeastFood(c);
		}
		c.swingArm();
		return TaskStatus.SUCCESS;
	}

	/**
	 * Cooking extra: spare wheat (the camp keeps none back for planting, its seeds are separate) is baked into bread at
	 * the crafting table by the chest, up to three loaves, which go back in the chest for the feast and after.
	 */
	private static void bake(CompanionEntity c, Container chest) {
		if (!Crafting.nearCraftingTable(c) || SupplyChest.count(chest, s -> s.is(Items.WHEAT)) < 9) {
			return;
		}
		int wheat = SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.WHEAT), 9);
		int loaves = wheat / 3;
		if (loaves > 0) {
			Crafting.ensure(c, Items.BREAD, c.backpack().count(Items.BREAD) + loaves);
		}
		SupplyChest.deposit(c.backpack(), chest, s -> s.is(Items.BREAD) || s.is(Items.WHEAT), 64);
	}

	@Override
	public void stop(CompanionEntity c) {
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
