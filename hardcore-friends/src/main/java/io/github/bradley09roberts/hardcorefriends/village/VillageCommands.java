package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.civic.Families;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * {@code /friends village} (the town plan: its people, streets and plots, what is being built and what the next stage
 * needs), {@code /friends village plots} (every plot) and {@code /friends home [name]} (where everyone lives, or one
 * person). Information only: they work at permission level 0 with cheats off and change nothing.
 */
final class VillageCommands {
	private static final int SHOWN = 12;

	private VillageCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("village")
				.executes(ctx -> village(ctx, false))
				.then(Commands.literal("plots").executes(ctx -> village(ctx, true))))
			.then(Commands.literal("home")
				.executes(VillageCommands::homes)
				.then(nameArg().executes(ctx -> home(ctx, StringArgumentType.getString(ctx, "name")))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
		return Commands.argument("name", StringArgumentType.word()).suggests((ctx, builder) -> {
			MinecraftServer server = ctx.getSource().getServer();
			VillageData v = VillageData.get(server);
			Households.Roster roster = roster(server, v);
			List<String> names = new ArrayList<>();
			for (UUID id : roster.adults()) {
				names.add(Households.firstName(server, v, id).toLowerCase(Locale.ROOT));
			}
			for (UUID id : roster.children()) {
				names.add(Households.firstName(server, v, id).toLowerCase(Locale.ROOT));
			}
			return SharedSuggestionProvider.suggest(names, builder);
		});
	}

	private static Households.Roster roster(MinecraftServer server, VillageData v) {
		Set<UUID> residents = new HashSet<>();
		for (VillageData.Plot p : v.plots()) {
			residents.addAll(p.residents.keySet());
		}
		return Households.roster(server, residents);
	}

	// ---------------------------------------------------------------- village

	private static int village(CommandContext<CommandSourceStack> ctx, boolean allPlots) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		CampData camp = Camp.data(server);
		VillageData v = VillageData.get(server);
		if (v.centre().isEmpty()) {
			String why = !FriendsConfig.get().villageHomes ? "The village is switched off (villageHomes in the settings)."
				: camp.campPos().isEmpty() ? "There is no camp yet: /friends camp set."
				: "No town plan yet: the friends lay one out when the camp becomes a " + Camp.stageName(Planner.VILLAGE_STAGE)
					+ " (it is a " + Camp.stageName(camp.stage()) + " now).";
			source.sendSuccess(() -> Component.literal(why).withStyle(ChatFormatting.YELLOW), false);
			return 0;
		}
		Households.Roster roster = roster(server, v);
		int standing = 0;
		int homes = 0;
		int underway = 0;
		for (VillageData.Plot p : v.plots()) {
			if (p.standing()) {
				standing++;
				homes += p.isHouse() ? 1 : 0;
			} else {
				underway++;
			}
		}
		int cap = FriendsConfig.get().maxPopulation;
		int expected = Families.get().babiesOnTheWay(server); // counted towards the cap, as for the births
		send(source, Component.literal("The " + Camp.stageName(camp.stage()).toLowerCase(Locale.ROOT) + ": " + roster.population()
			+ " people" + (expected > 0 ? " and " + expected + (expected == 1 ? " baby" : " babies") + " on the way" : "")
			+ " (at most " + cap + "), " + homes + " homes and " + (standing - homes) + " other buildings standing, "
			+ underway + " being built.").withStyle(ChatFormatting.GOLD));
		List<String> streets = new ArrayList<>();
		for (Map.Entry<Integer, int[]> e : v.openStreets().entrySet()) {
			TownPlan.Street s = TownPlan.STREETS.get(Math.clamp(e.getKey(), 0, TownPlan.STREETS.size() - 1));
			streets.add(s.name() + " (" + (e.getValue()[1] - e.getValue()[0] + 1) + " blocks)");
		}
		String surface = switch (v.streetLevel()) {
			case 1 -> "gravel";
			case 2 -> "cobblestone";
			default -> "trodden paths";
		};
		send(source, line("Streets", (streets.isEmpty() ? "none yet" : String.join(", ", streets)) + "; " + surface + "."));
		Map<String, Integer> paving = StreetsTask.progress();
		if (paving.get("paths") > 0 || paving.get("surfaces") > 0) {
			send(source, line("Still to lay", paving.get("paths") + " path blocks, " + paving.get("surfaces") + " to surface"));
		}
		List<VillageData.Plot> plots = new ArrayList<>(v.plots());
		if (!allPlots) {
			plots.removeIf(p -> p.kind.equals("decor:lamp"));
		}
		int lamps = 0;
		for (VillageData.Plot p : v.plots()) {
			lamps += p.kind.equals("decor:lamp") ? 1 : 0;
		}
		ServerLevel level = Planner.campLevel(server, camp);
		int shown = 0;
		for (VillageData.Plot p : plots) {
			if (!allPlots && shown >= SHOWN) {
				send(source, Component.literal("... and " + (plots.size() - shown) + " more (/friends village plots).")
					.withStyle(ChatFormatting.GRAY));
				break;
			}
			send(source, plotLine(server, level, camp, v, p));
			shown++;
		}
		if (!allPlots && lamps > 0) {
			send(source, line("Lamp posts", Integer.toString(lamps)));
		}
		String searching = Planner.searching();
		if (searching != null) {
			send(source, line("Looking for a plot", "for a " + searching));
		}
		for (VillageData.Request r : v.requests().values()) {
			if (Planner.countOf(v, r.kind()) < r.count()) {
				send(source, line("Asked for", VillageGrowth.pretty(r.kind()) + (r.reason().isEmpty() ? "" : " (" + r.reason() + ")")));
			}
		}
		String problem = Planner.problem();
		if (!problem.isEmpty()) {
			send(source, Component.literal(problem).withStyle(ChatFormatting.YELLOW));
		}
		int stage = camp.stage();
		if (stage < Camp.MAX_STAGE && stage + 1 >= VillageGrowth.TOWN) {
			List<String> missing = VillageGrowth.missing(server, stage + 1);
			String needs = "Unity " + camp.unity() + "/" + Camp.STAGE_UNITY[stage + 1]
				+ (missing.isEmpty() ? "" : "; " + String.join("; ", missing));
			send(source, line("To become a " + Camp.stageName(stage + 1), needs));
		}
		return 1;
	}

	private static Component plotLine(MinecraftServer server, ServerLevel level, CampData camp, VillageData v, VillageData.Plot p) {
		String name = Blueprints.displayName(camp, p.siteKey);
		BlockPos at = p.middle();
		StringBuilder sb = new StringBuilder();
		sb.append(name).append(", ").append(TownPlan.sideName(p.street, p.side)).append(" (").append(at.getX()).append(' ')
			.append(at.getY()).append(' ').append(at.getZ()).append("): ");
		if (p.standing()) {
			if (p.isHouse()) {
				List<String> people = new ArrayList<>();
				for (UUID id : p.residents.keySet()) {
					people.add(Households.firstName(server, v, id));
				}
				sb.append(people.isEmpty() ? "empty, waiting for someone" : "home of " + Planner.join(people)).append(" (")
					.append(p.residents.size()).append(" of ").append(Housing.capacity(p)).append(" beds)");
			} else {
				sb.append("standing");
			}
		} else {
			// The blocks actually placed (the builders' own progress counts firm ground under the plot as done).
			double share = level == null ? 0 : Planner.builtShare(level, camp, p.siteKey);
			int percent = (int) Math.round(100 * (share >= 0 ? share : Construction.progress(level, p.siteKey)));
			sb.append("being built, ").append(percent).append("%");
			if (p.isHouse() && !p.intended.isEmpty()) {
				List<String> people = new ArrayList<>();
				for (UUID id : p.intended) {
					people.add(Households.firstName(server, v, id));
				}
				sb.append(", for ").append(Planner.join(people));
			}
		}
		return Component.literal(sb.toString()).withStyle(p.standing() ? ChatFormatting.WHITE : ChatFormatting.GRAY);
	}

	// ------------------------------------------------------------------- home

	/** Everyone's home, household by household. */
	private static int homes(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		VillageData v = VillageData.get(server);
		Households.Roster roster = roster(server, v);
		if (roster.households().isEmpty()) {
			source.sendFailure(Component.literal("Nobody on the team yet."));
			return 0;
		}
		send(source, Component.literal("Where everyone lives (/friends home <name> for one person)").withStyle(ChatFormatting.GOLD));
		for (Households.Household h : roster.households()) {
			List<String> names = new ArrayList<>();
			for (UUID id : h.members()) {
				names.add(Households.firstName(server, v, id));
			}
			send(source, line(Planner.join(names), whereText(server, v, h.members())));
		}
		return roster.households().size();
	}

	/** One person's home: the house, the street, their bed and who they live with. */
	private static int home(CommandContext<CommandSourceStack> ctx, String name) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		VillageData v = VillageData.get(server);
		Households.Roster roster = roster(server, v);
		UUID found = null;
		List<UUID> everyone = new ArrayList<>(roster.adults());
		everyone.addAll(roster.children());
		for (UUID id : everyone) {
			String first = Households.firstName(server, v, id);
			String full = Households.fullName(server, v, id);
			if (first.equalsIgnoreCase(name) || full.equalsIgnoreCase(name) || full.replace(' ', '_').equalsIgnoreCase(name)) {
				found = id;
				break;
			}
		}
		if (found == null) {
			source.sendFailure(Component.literal("I don't know anyone called " + name + " in the village."));
			return 0;
		}
		UUID who = found;
		String full = Households.fullName(server, v, who);
		Optional<VillageData.Plot> home = v.homeOf(who);
		if (home.isEmpty() || !home.get().standing()) {
			send(source, line(full, whereText(server, v, Set.of(who))));
			return 1;
		}
		VillageData.Plot p = home.get();
		CampData camp = Camp.data(server);
		BlockPos at = p.middle();
		send(source, line(full, "the " + Blueprints.displayName(camp, p.siteKey) + " on " + TownPlan.sideName(p.street, p.side)
			+ " (" + at.getX() + " " + at.getY() + " " + at.getZ() + ")"));
		int bed = p.residents.getOrDefault(who, -1);
		send(source, line("Bed", bed >= 0 ? "bed " + (bed + 1) + " of " + Housing.capacity(p) : "none free yet: a bigger house is planned"));
		List<String> others = new ArrayList<>();
		for (UUID id : p.residents.keySet()) {
			if (!id.equals(who)) {
				others.add(Households.firstName(server, v, id));
			}
		}
		send(source, line("Lives with", others.isEmpty() ? "nobody: a home of their own" : Planner.join(others)));
		return 1;
	}

	/** Where these people live, or why they have no home yet. */
	private static String whereText(MinecraftServer server, VillageData v, Set<UUID> people) {
		CampData camp = Camp.data(server);
		Map<VillageData.Plot, Integer> homes = new LinkedHashMap<>();
		for (UUID id : people) {
			v.homeOf(id).filter(VillageData.Plot::standing).ifPresent(p -> homes.merge(p, 1, Integer::sum));
		}
		if (!homes.isEmpty()) {
			List<String> parts = new ArrayList<>();
			for (VillageData.Plot p : homes.keySet()) {
				parts.add("the " + Blueprints.displayName(camp, p.siteKey) + " on " + TownPlan.sideName(p.street, p.side));
			}
			return String.join(" and ", parts);
		}
		for (VillageData.Plot p : v.plots()) {
			if (p.isHouse() && !p.standing() && p.intended.stream().anyMatch(people::contains)) {
				return "no home yet: their " + Blueprints.displayName(camp, p.siteKey) + " is being built on " + TownPlan.sideName(p.street, p.side);
			}
		}
		return v.centre().isEmpty() ? "the camp, until the village has homes" : "the camp, until a house can be built for them";
	}

	private static Component line(String label, String text) {
		return Component.literal(label + ": ").withStyle(ChatFormatting.YELLOW).append(Component.literal(text).withStyle(ChatFormatting.WHITE));
	}

	private static void send(CommandSourceStack source, Component message) {
		source.sendSuccess(() -> message, false);
	}
}
