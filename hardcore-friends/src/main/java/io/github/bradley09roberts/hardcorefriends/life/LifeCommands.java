package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * {@code /friends calendar} (today's date, what is on, and the next ten days) and {@code /friends chronicle [page]}
 * (the Village Chronicle in chat). Both only show; they work at permission level 0 with cheats off and change nothing.
 */
final class LifeCommands {
	/** How many days ahead the calendar looks. */
	private static final int AHEAD = 10;

	private LifeCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("calendar").executes(LifeCommands::calendar));
		root.then(Commands.literal("chronicle")
			.executes(ctx -> chronicle(ctx, 0))
			.then(Commands.argument("page", IntegerArgumentType.integer(1))
				.executes(ctx -> chronicle(ctx, IntegerArgumentType.getInteger(ctx, "page")))));
	}

	private static int chronicle(CommandContext<CommandSourceStack> ctx, int page) {
		for (Component line : Chronicle.show(ctx.getSource().getServer(), page)) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int calendar(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		for (Component line : calendarLines(server)) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	static List<Component> calendarLines(MinecraftServer server) {
		List<Component> out = new ArrayList<>();
		long day = Calendar.today(server);
		long time = Calendar.time(server);
		LifeData data = LifeData.get(server);
		out.add(Component.literal("Today is " + Calendar.longDate(day) + " (" + Calendar.partOfDay(time) + ").").withStyle(ChatFormatting.GOLD));
		List<String> today = occasions(server, data, day, true);
		if (today.isEmpty()) {
			out.add(Component.literal("Nothing special today.").withStyle(ChatFormatting.GRAY));
		} else {
			for (String s : today) {
				out.add(Component.literal("Today: " + s).withStyle(ChatFormatting.YELLOW));
			}
		}
		out.add(Component.literal("Coming up:").withStyle(ChatFormatting.GOLD));
		boolean any = false;
		for (long d = day + 1; d <= day + AHEAD; d++) {
			List<String> on = occasions(server, data, d, false);
			if (!on.isEmpty()) {
				any = true;
				out.add(Component.literal("  " + Calendar.weekdayName(d) + ", day " + (d + 1) + " (" + Calendar.seasonName(d) + "): "
					+ String.join("; ", on)).withStyle(ChatFormatting.WHITE));
			}
		}
		if (!any) {
			out.add(Component.literal("  Nothing in the next " + AHEAD + " days.").withStyle(ChatFormatting.GRAY));
		}
		out.add(Component.literal("A week has seven days; Saturday is market day. A year has four seasons of " + Calendar.seasonLength()
			+ " days: the midsummer feast, the harvest festival (when the farms do well) and the winter lights.").withStyle(ChatFormatting.GRAY));
		if (!FriendsConfig.get().villageLife) {
			out.add(Component.literal("Feasts, market days, music and birthdays are off (villageLife in the settings).")
				.withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	/** What is on a day: feasts, market day, funerals, birthdays and music. */
	private static List<String> occasions(MinecraftServer server, LifeData data, long day, boolean today) {
		List<String> list = new ArrayList<>();
		boolean life = FriendsConfig.get().villageLife;
		Calendar.Feast feast = Calendar.feastOn(day);
		if (feast != null && life) {
			if (today && data.isDone("feast:" + day) && Gatherings.active() == null && data.running.isEmpty()) {
				list.add(Calendar.capital(feast.phrase()) + (Calendar.time(server) >= Gatherings.FEAST_FROM ? " (over)" : " (not this year)"));
			} else {
				list.add(Calendar.capital(feast.phrase()) + " at the square, from the late afternoon"
					+ (feast == Calendar.Feast.HARVEST ? " (if the farms do well)" : ""));
			}
		}
		if (Calendar.marketDay(day) && life) {
			ServerLevel level = Places.campLevel(server);
			list.add(level != null && MarketDay.hasStalls(level) ? "Market day" : "Market day (once the village has stalls)");
		}
		for (LifeData.Funeral f : data.funerals) {
			if (f.day == day || today && f.day < day) {
				list.add("The funeral of " + Chronicle.and(f.names) + ", in the evening");
			}
		}
		if (life) {
			List<String> birthdays = new ArrayList<>();
			for (LifeData.Person p : data.people.values()) {
				if (!p.gone && Calendar.birthday(p.joined, day)) {
					birthdays.add(p.name);
				}
			}
			if (!birthdays.isEmpty()) {
				list.add("Birthday: " + Chronicle.and(birthdays));
			}
			if (today && Calendar.musicEvening(day) && feast == null) {
				list.add("Music in the evening");
			}
		}
		return list;
	}
}
