package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.civic.BlueprintLibrary;

/**
 * {@code /friends builds}: the building library, read-only, at permission level 0 with cheats off.
 * <ul>
 * <li>{@code /friends builds}: every kind of building with its plans;</li>
 * <li>{@code /friends builds <kind or id>}: the plans of one kind ({@code house}, {@code shop}, {@code shop:bakery}),
 * or one plan in detail (size, styles, beds, what it is built from);</li>
 * <li>{@code /friends builds check}: every plan file that was skipped or has warnings, and why, so a data pack's plans
 * are easy to put right.</li>
 * </ul>
 */
public final class BuildsCommand {
	private static final int MAX_LINES = 40;

	private BuildsCommand() {
	}

	/** Adds {@code builds} to {@code /friends}. */
	public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("builds")
			.executes(BuildsCommand::list)
			.then(Commands.literal("check").executes(BuildsCommand::check))
			.then(Commands.argument("kind", StringArgumentType.greedyString()).executes(BuildsCommand::kind)));
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Map<String, List<Blueprint>> byKind = PlanLibrary.byKindSorted();
		PlanLibrary.Snapshot snap = PlanLibrary.snapshot();
		send(source, Component.literal("Building plans: " + snap.byId().size() + (snap.skipped() > 0
			? " (" + snap.skipped() + " skipped, see /friends builds check)" : "")).withStyle(ChatFormatting.GOLD));
		if (byKind.isEmpty()) {
			send(source, Component.literal("No plans are loaded."));
			return 1;
		}
		int lines = 0;
		for (Map.Entry<String, List<Blueprint>> e : byKind.entrySet()) {
			if (lines++ >= MAX_LINES) {
				send(source, Component.literal("...and more kinds."));
				break;
			}
			StringBuilder names = new StringBuilder();
			for (Blueprint b : e.getValue()) {
				names.append(names.isEmpty() ? "" : ", ").append(b.name());
			}
			send(source, Component.literal(e.getKey()).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(" (" + e.getValue().size() + "): " + names).withStyle(ChatFormatting.WHITE)));
		}
		send(source, Component.literal("Type /friends builds <kind> for details, e.g. /friends builds house.").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int kind(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String query = StringArgumentType.getString(ctx, "kind").trim();
		var one = BlueprintLibrary.get().get(query);
		if (one.isEmpty() && !query.contains(":")) {
			one = BlueprintLibrary.get().get("hardcorefriends:" + query);
		}
		if (one.isPresent()) {
			describe(source, one.get());
			return 1;
		}
		List<Blueprint> plans = BlueprintLibrary.get().byKind(query);
		if (plans.isEmpty()) {
			send(source, Component.literal("There are no plans of the kind \"" + query + "\". Try /friends builds."));
			return 0;
		}
		send(source, Component.literal(query + ": " + plans.size() + " plans").withStyle(ChatFormatting.GOLD));
		int lines = 0;
		for (Blueprint b : plans.stream().sorted((a, b) -> a.id().compareTo(b.id())).toList()) {
			if (lines++ >= MAX_LINES) {
				send(source, Component.literal("...and more."));
				break;
			}
			send(source, Component.literal(summary(b)));
		}
		return 1;
	}

	private static void describe(CommandSourceStack source, Blueprint b) {
		send(source, Component.literal(b.name() + " (" + b.id() + ")").withStyle(ChatFormatting.GOLD));
		send(source, Component.literal(summary(b)));
		Map<Stock, Integer> materials = new EnumMap<>(Stock.class);
		for (Blueprint.Entry e : b.entries()) {
			Stock s = e.material().stock();
			if (s != null) {
				materials.merge(s, 1, Integer::sum);
			}
			Stock extra = e.material().extraStock();
			if (extra != null) {
				materials.merge(extra, 1, Integer::sum);
			}
		}
		StringBuilder sb = new StringBuilder("Built from: ");
		boolean first = true;
		for (Map.Entry<Stock, Integer> e : materials.entrySet()) {
			sb.append(first ? "" : ", ").append(e.getKey().describe(e.getValue()));
			first = false;
		}
		send(source, Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY));
		if (!b.markers().isEmpty()) {
			StringBuilder m = new StringBuilder("Marked spots: ");
			first = true;
			for (Map.Entry<String, List<int[]>> e : b.markers().entrySet()) {
				m.append(first ? "" : ", ").append(e.getKey()).append(e.getValue().size() > 1 ? " x" + e.getValue().size() : "");
				first = false;
			}
			send(source, Component.literal(m.toString()).withStyle(ChatFormatting.GRAY));
		}
	}

	private static String summary(Blueprint b) {
		StringBuilder sb = new StringBuilder();
		sb.append(b.name()).append(": ").append(b.width()).append(" x ").append(b.depth()).append(", ").append(b.height())
			.append(" high");
		int beds = b.metaInt("beds", -1);
		if (beds >= 0) {
			sb.append(", ").append(beds).append(beds == 1 ? " bed" : " beds");
		}
		if (!b.styles().isEmpty()) {
			sb.append(", ").append(String.join(", ", b.styles()));
		}
		return sb.toString();
	}

	private static int check(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		PlanLibrary.Snapshot snap = PlanLibrary.snapshot();
		if (snap.problems().isEmpty()) {
			send(source, Component.literal("All " + snap.read() + " plan files read cleanly.").withStyle(ChatFormatting.GREEN));
			return 1;
		}
		send(source, Component.literal(snap.read() + " plan files read, " + snap.skipped() + " skipped:").withStyle(ChatFormatting.GOLD));
		int lines = 0;
		for (String problem : snap.problems()) {
			if (lines++ >= MAX_LINES) {
				send(source, Component.literal("...and " + (snap.problems().size() - MAX_LINES) + " more in the server log."));
				break;
			}
			send(source, Component.literal(problem).withStyle(problem.contains("(warning)") ? ChatFormatting.YELLOW : ChatFormatting.RED));
		}
		return 1;
	}

	private static void send(CommandSourceStack source, Component line) {
		source.sendSuccess(() -> line, false);
	}
}
