package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * {@code /friends trades} (who holds which trade, where, and what they are doing; workplaces still waiting for someone;
 * what the village has been asked to build) and {@code /friends shops} (each shop, its keeper, whether it is open, and
 * its trade so far). Both only look: they work for anyone, at permission level 0 with cheats off.
 */
final class MarketCommands {
	private MarketCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("trades").executes(MarketCommands::trades));
		root.then(Commands.literal("shops").executes(MarketCommands::shops));
	}

	private static void say(CommandSourceStack source, String text, ChatFormatting colour) {
		source.sendSuccess(() -> Component.literal(text).withStyle(colour), false);
	}

	private static int trades(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		ServerLevel level = Planner.campLevel(server);
		if (level == null) {
			source.sendFailure(Component.literal("No camp yet, so no village trades. Set one with /friends camp set."));
			return 0;
		}
		MarketData data = MarketData.get(server);
		say(source, "Village trades", ChatFormatting.GOLD);
		if (!FriendsConfig.get().villageTrades) {
			say(source, "Village trades are switched off (villageTrades in the settings).", ChatFormatting.YELLOW);
		}
		List<MarketData.Holding> holdings = data.sortedHoldings();
		if (holdings.isEmpty()) {
			say(source, "Nobody holds a trade yet. Trades come with the village's shops and workplaces, or plainly at the camp "
				+ "once it has six or more grown-ups.", ChatFormatting.GRAY);
		}
		for (MarketData.Holding h : holdings) {
			String where = h.atCamp() ? "at the camp" : Workplaces.byKey(level, h.site).map(w -> "at " + w.described()).orElse("(workplace gone)");
			CompanionEntity c = loaded(h);
			String doing = c == null ? "away" : activity(c);
			say(source, " " + h.name + " - " + h.title() + " " + where + ", since day " + h.since + " - " + doing, ChatFormatting.WHITE);
		}
		List<String> empty = new ArrayList<>();
		for (Workplace w : Workplaces.all(level)) {
			if (data.holderOf(w.key()).isEmpty()) {
				empty.add(w.described() + " (" + w.trade().title().toLowerCase(Locale.ROOT) + ")");
			}
		}
		if (!empty.isEmpty()) {
			say(source, "Waiting for someone: " + String.join(", ", empty) + ". At least " + FriendsConfig.get().friendsWithoutTrade
				+ " grown-ups always keep to the camp's own work.", ChatFormatting.GRAY);
		}
		if (!data.requested().isEmpty()) {
			List<String> asked = new ArrayList<>();
			for (Map.Entry<String, Long> e : data.requested().entrySet()) {
				asked.add(e.getKey() + " (day " + e.getValue() + ")");
			}
			say(source, "Asked of the village: " + String.join(", ", asked) + ".", ChatFormatting.DARK_GRAY);
		} else if (!VillageLink.available()) {
			say(source, "There is no town plan to ask for workplaces, so trades work plainly at the camp.", ChatFormatting.DARK_GRAY);
		}
		return Math.max(1, holdings.size());
	}

	private static int shops(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		ServerLevel level = Planner.campLevel(server);
		if (level == null) {
			source.sendFailure(Component.literal("No camp yet, so no shops. Set one with /friends camp set."));
			return 0;
		}
		MarketData data = MarketData.get(server);
		say(source, "Shops", ChatFormatting.GOLD);
		int shops = 0;
		for (MarketData.Holding h : data.sortedHoldings()) {
			CompanionEntity c = loaded(h);
			Optional<Shop> shop = c == null ? Optional.empty() : Shop.of(level, c, h);
			if (shop.isEmpty()) {
				continue;
			}
			shops++;
			Shop s = shop.get();
			BlockPos at = s.counter();
			String state = !FriendsConfig.get().playerShops ? "shops are switched off"
				: Shops.shut(level) != null ? "shut " + Shops.shut(level)
				: Shops.isTrading(c) ? "serving a customer"
				: c.position().distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) <= Shops.AT_COUNTER * Shops.AT_COUNTER
				? "open: the keeper is at the counter" : "the keeper is busy elsewhere";
			say(source, " " + s.title() + " - " + h.name + " (" + h.title().toLowerCase(Locale.ROOT) + ") at " + at.getX() + " " + at.getY()
				+ " " + at.getZ() + " - " + state + " - " + data.sales(s.key()) + " trades so far", ChatFormatting.WHITE);
		}
		if (shops == 0) {
			say(source, "No shop is open yet. A shopkeeper keeps the general store, or a stall at the supply chest once the camp is a "
				+ "Hamlet with six or more grown-ups; the baker, butcher, fishmonger, tailor and smith keep their own shops.", ChatFormatting.GRAY);
		} else {
			say(source, "Right-click a keeper at their counter by day to trade (anyone may). Prices are fixed and paid in emeralds; "
				+ "shops sell what the village really has, and buy what the camp is short of.", ChatFormatting.GRAY);
		}
		say(source, "All shops: " + data.totalSales() + " trades, " + data.emeraldsTaken() + " emeralds taken, " + data.emeraldsPaid()
			+ " paid out.", ChatFormatting.DARK_GRAY);
		return Math.max(1, shops);
	}

	private static @Nullable CompanionEntity loaded(MarketData.Holding h) {
		for (CompanionEntity c : Companions.all()) {
			if (c.getUUID().equals(h.who)) {
				return c;
			}
		}
		return null;
	}

	private static String activity(CompanionEntity c) {
		if (c.mode() != CompanionMode.WORK) {
			return c.activity();
		}
		CompanionTask job = c.scheduler().current();
		return job == null ? "between jobs" : job.describe();
	}
}
