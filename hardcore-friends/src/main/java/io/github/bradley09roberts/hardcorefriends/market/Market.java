package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Pen;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.civic.Professions;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;
import io.github.bradley09roberts.hardcorefriends.survival.Skills;

/**
 * Shops and trades: workplaces with their job blocks, village professions (baker, fisher, shepherd, beekeeper,
 * mason, carpenter, tailor, cook, teacher, doctor, shopkeeper...), and shops where players trade with the friends.
 * Provides {@code civic.Professions}.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, and wording through {@code Lines.define}.
 * The market changes no block of its own kind: the beekeeper's hives and the farmer's composter go through the edit
 * guard's ordinary BUILD and FARM rules.
 *
 * <p>The parts: {@link Trade} (the trades), {@link MarketData} (who holds which, saved), {@link Workplaces} (the
 * buildings they work in, found through {@code architecture.Construction}), {@link Planner} (Sage's assigning of trades
 * and asking the village for buildings, through {@link VillageLink}), the trades' jobs ({@link TradeJob} and its kinds),
 * the shops ({@link Shop}, {@link Shops}, {@link ShopTrade}, {@link Catalogue}), {@link MarketLines} and
 * {@link MarketCommands}.
 */
public final class Market {
	/** Every job of this package has an id starting with this. */
	public static final String JOB_PREFIX = "market.";
	private static final String SMITH_JOB = "combat.smith";
	private static final int SMITH_REFRESH = 100;

	private static @Nullable UUID blacksmith;
	private static long blacksmithAt = Long.MIN_VALUE / 2;

	private Market() {
	}

	public static void init() {
		MarketLines.register();
		Professions.provide(new ProfessionsProvider());

		// Each job scores nothing for anyone without its trade, so every friend can carry the whole list.
		TaskRegistry.PACKS.add(id -> jobs());
		TaskScheduler.JOB_FILTERS.add(Market::mayDo);
		TaskScheduler.JOB_DONE.add(Market::jobDone);

		// Trading at a shop is open to anyone, trusted or not: it runs before the camp's trust check.
		CompanionEvents.INTERACT.add(0, Shops::interact);
		CompanionEvents.DEATH.add((c, level, source) -> left(c, level));
		CompanionEvents.DISMISSED.add(Market::left);
		Construction.FINISHED.add((level, key, plan) -> Workplaces.invalidate());

		// A fishing rod and shears are kept like any tool, not tidied away to the chest after every trip.
		KeepList.addCommonRule(new KeepList.Rule("fishing rod", s -> s.is(Items.FISHING_ROD), 1));
		KeepList.addCommonRule(new KeepList.Rule("shears", s -> s.is(Items.SHEARS), 1));
		recipe(Items.FISHING_ROD, 1, true, Crafting.of(Items.STICK, 3), Crafting.of(Items.STRING, 2));
		recipe(Items.BEEHIVE, 1, true, Crafting.of(ItemTags.PLANKS, 6), Crafting.of(Items.HONEYCOMB, 3));
		recipe(Items.BOWL, 4, true, Crafting.of(ItemTags.PLANKS, 3));
		recipe(Items.GLASS_BOTTLE, 3, true, Crafting.of(Items.GLASS, 3));
		recipe(Items.SHEARS, 1, false, Crafting.of(Items.IRON_INGOT, 2));

		FriendsCommand.EXTENSIONS.add(MarketCommands::register);
		ServerTickEvents.END_SERVER_TICK.register(Planner::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Shops.closeAll());
		ServerLifecycleEvents.SERVER_STARTING.register(server -> clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	/** Teaches the friends a vanilla recipe, unless another package already has. */
	private static void recipe(Item output, int count, boolean needsTable, Crafting.Ingredient... inputs) {
		if (!Crafting.hasAddedRecipe(output)) {
			Crafting.addRecipe(output, count, needsTable, inputs);
		}
	}

	/** One friend's fresh set of trade jobs. */
	private static List<CompanionTask> jobs() {
		List<CompanionTask> list = new ArrayList<>();
		list.add(new KeepShopTask());
		list.add(new StockShopTask());
		return list;
	}

	private static void clear() {
		Workplaces.invalidate();
		FishSpots.clear();
		blacksmith = null;
		blacksmithAt = Long.MIN_VALUE / 2;
	}

	/** This friend's trade, if they hold one. */
	static MarketData.@Nullable Holding holding(MinecraftServer server, CompanionEntity c) {
		return MarketData.get(server).holding(c.getUUID());
	}

	/**
	 * The trades' job filter. A keeper serving a player does nothing but mind the counter and their own needs; and once
	 * the village has a blacksmith at work, the camp's smith work is theirs.
	 */
	static boolean mayDo(CompanionEntity c, String jobId) {
		if (Shops.isTrading(c)) {
			return jobId.startsWith("needs.") || jobId.equals(KeepShopTask.ID);
		}
		if (jobId.equals(SMITH_JOB) && c.level() instanceof ServerLevel level) {
			UUID smith = blacksmithAtWork(level);
			return smith == null || smith.equals(c.getUUID());
		}
		return true;
	}

	/** The village's blacksmith, if they are at work in the camp's world (looked up every few seconds). */
	private static @Nullable UUID blacksmithAtWork(ServerLevel level) {
		long now = level.getGameTime();
		if (now - blacksmithAt < SMITH_REFRESH && now >= blacksmithAt) {
			return blacksmith;
		}
		blacksmithAt = now;
		blacksmith = null;
		if (!Camp.isCampLevel(level, Camp.data(level.getServer()))) {
			return null;
		}
		MarketData data = MarketData.get(level.getServer());
		for (CompanionEntity c : Companions.in(level)) {
			MarketData.Holding h = data.holding(c.getUUID());
			if (h != null && h.trade == Trade.BLACKSMITH && !h.atCamp() && c.mode() == CompanionMode.WORK && !c.isChild()) {
				blacksmith = c.getUUID();
				break;
			}
		}
		return blacksmith;
	}

	/** Finished trade work trains the trade's kind of work, as any job does its speciality's. */
	private static void jobDone(CompanionEntity c, CompanionTask task, boolean success, long ticks) {
		if (!success || !(task instanceof TradeJob) || !(c.level() instanceof ServerLevel level)) {
			return;
		}
		MarketData.Holding h = holding(level.getServer(), c);
		if (h != null) {
			Skills.add(c, Speciality.workName(h.trade.skillRole()), (int) Math.min(5, 1 + ticks / 600));
		}
	}

	/** Someone died or left the team: their trade is free at once. */
	private static void left(CompanionEntity c, ServerLevel level) {
		Planner.release(level.getServer(), c.getUUID());
	}

	/**
	 * Where a trade held at the camp works from: the supply chest for the stall, the campfire (or furnace) for the
	 * cook, the nearest bank for the fisher, the pen's gate for the shepherd.
	 */
	static @Nullable BlockPos campStation(ServerLevel level, Trade trade) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return null;
		}
		return switch (trade) {
			case SHOPKEEPER -> Stores.supplyPos(level).orElse(null);
			case INNKEEPER -> {
				Optional<CampData.Site> fire = data.isCompleted(Structures.CAMPFIRE) ? data.site(Structures.CAMPFIRE) : Optional.empty();
				Optional<CampData.Site> furnace = data.isCompleted(Structures.FURNACE) ? data.site(Structures.FURNACE) : Optional.empty();
				yield fire.map(s -> s.origin).orElse(furnace.map(s -> s.origin).orElse(null));
			}
			case FISHER -> {
				List<FishSpots.Spot> spots = FishSpots.campSpots(level);
				yield spots.isEmpty() ? null : spots.getFirst().stand();
			}
			case SHEPHERD -> Pen.of(level).map(Pen::outside).orElse(null);
			default -> null;
		};
	}
}
