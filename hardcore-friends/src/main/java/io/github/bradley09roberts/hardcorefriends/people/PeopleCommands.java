package io.github.bradley09roberts.hardcorefriends.people;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * {@code /friends family [name]}, {@code /friends couples} and {@code /friends relationships [name]}: who is married to
 * whom, parents and children, who is courting and what stands between a couple and a baby, and who each friend gets on
 * with best. Information only: they work at permission level 0 with cheats off and change nothing.
 */
final class PeopleCommands {
	private static final int SHOWN = 6;

	private PeopleCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("family")
				.executes(PeopleCommands::families)
				.then(nameArg().executes(ctx -> family(ctx, StringArgumentType.getString(ctx, "name")))))
			.then(Commands.literal("couples").executes(PeopleCommands::couples))
			.then(Commands.literal("relationships")
				.executes(PeopleCommands::closest)
				.then(nameArg().executes(ctx -> relationships(ctx, StringArgumentType.getString(ctx, "name")))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
		return Commands.argument("name", StringArgumentType.word()).suggests((ctx, builder) -> {
			List<String> names = new ArrayList<>();
			for (PeopleData.Person p : PeopleData.get(ctx.getSource().getServer()).people()) {
				if (p.alive()) {
					names.add(p.name.toLowerCase(Locale.ROOT));
				}
			}
			return SharedSuggestionProvider.suggest(names, builder);
		});
	}

	// ----------------------------------------------------------------- family

	/** Every family in the camp: the living grouped by family name. */
	private static int families(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		PeopleData data = PeopleData.get(source.getServer());
		Map<String, List<PeopleData.Person>> byName = new LinkedHashMap<>();
		for (PeopleData.Person p : data.people()) {
			if (p.alive()) {
				byName.computeIfAbsent(p.family.isEmpty() ? "?" : p.family, k -> new ArrayList<>()).add(p);
			}
		}
		if (byName.isEmpty()) {
			source.sendFailure(Component.literal("Nobody on the team yet. Friends are known here once they've been at the camp a moment."));
			return 0;
		}
		send(source, Component.literal("The families of the camp (/friends family <name> for one person)").withStyle(ChatFormatting.GOLD));
		List<String> names = new ArrayList<>(byName.keySet());
		names.sort(Comparator.comparing((String n) -> -byName.get(n).size()).thenComparing(n -> n));
		for (String family : names) {
			StringBuilder members = new StringBuilder();
			for (PeopleData.Person p : byName.get(family)) {
				if (!members.isEmpty()) {
					members.append(", ");
				}
				members.append(p.name).append(p.child ? " (child)" : "");
			}
			send(source, Component.literal("The " + family + " family: ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(members.toString()).withStyle(ChatFormatting.WHITE)));
		}
		return names.size();
	}

	/** One person's family: their partner, parents, children and who they live with. */
	private static int family(CommandContext<CommandSourceStack> ctx, String name) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		PeopleData data = PeopleData.get(server);
		Optional<PeopleData.Person> found = find(data, name);
		if (found.isEmpty()) {
			source.sendFailure(Component.literal("I don't know anyone called " + name + " in the camp."));
			return 0;
		}
		PeopleData.Person p = found.get();
		long day = PeopleEvents.day(server);
		send(source, heading(p, day));
		send(source, line("Partner", partnerText(data, p, day)));
		List<String> parents = new ArrayList<>();
		for (UUID id : p.parents) {
			data.person(id).ifPresent(parent -> parents.add(parent.name + (parent.alive() ? "" : " (fallen)")));
		}
		send(source, line("Parents", parents.isEmpty() ? (p.named ? "one of the nine friends" : "met in the world, grown up") : String.join(" and ", parents)));
		List<String> children = new ArrayList<>();
		for (PeopleData.Person child : data.childrenOf(p.id)) {
			children.add(child.name + " (" + ageText(child, day) + ")");
		}
		send(source, line("Children", children.isEmpty() ? "none" : String.join(", ", children)));
		if (p.alive()) {
			List<String> home = new ArrayList<>();
			for (UUID id : data.household(p.id)) {
				if (!id.equals(p.id)) {
					data.person(id).ifPresent(member -> home.add(member.name));
				}
			}
			send(source, line("Lives with", home.isEmpty() ? "nobody yet" : String.join(", ", home)));
		}
		return 1;
	}

	private static Component heading(PeopleData.Person p, long day) {
		int colour = p.colour;
		String trade = p.archetype.role().title();
		String age = p.child ? "child, " + ageText(p, day) : trade;
		return Component.literal("[" + p.fullName() + "] ").withStyle(s -> s.withColor(colour))
			.append(Component.literal(age + (p.alive() ? "" : p.state == PeopleData.State.DEAD ? " - fallen" : " - left the team"))
				.withStyle(ChatFormatting.GRAY));
	}

	private static String partnerText(PeopleData data, PeopleData.Person p, long day) {
		PeopleData.Bond bond = data.partnerBond(p.id);
		if (bond == null) {
			// A widow or widower: the last marriage, its partner now gone.
			for (PeopleData.Bond b : data.bondsOf(p.id)) {
				if (b.status == PeopleData.Status.MARRIED) {
					String other = data.person(b.other(p.id)).map(o -> o.name).orElse("someone");
					return "widowed (married to " + other + ")";
				}
			}
			return "single";
		}
		String other = data.person(bond.other(p.id)).map(o -> o.name).orElse("someone");
		return switch (bond.status) {
			case DATING -> "going out with " + other + " since day " + bond.since / 24000L;
			case ENGAGED -> "engaged to " + other + (bond.weddingDay >= 0 ? ", wedding " + whenText(bond.weddingDay, day) : "");
			case MARRIED -> "married to " + other + " since day " + bond.since / 24000L
				+ (bond.babyDue >= 0 ? ", a baby on the way" : "");
			default -> "single";
		};
	}

	private static String ageText(PeopleData.Person p, long day) {
		if (!p.alive()) {
			return p.state == PeopleData.State.DEAD ? "fallen" : "left the team";
		}
		if (p.child) {
			long days = Math.max(0, day - p.bornDay);
			int grownAt = FriendsConfig.get().childhoodDays;
			return (days == 1 ? "1 day old" : days + " days old") + ", grown up in " + Math.max(0, grownAt - days) + " days";
		}
		return p.bornDay >= 0 ? "grown up, " + p.archetype.role().title().toLowerCase(Locale.ROOT) : p.archetype.role().title().toLowerCase(Locale.ROOT);
	}

	private static String whenText(long weddingDay, long today) {
		if (weddingDay <= today) {
			return "today, in the late morning";
		}
		return weddingDay == today + 1 ? "tomorrow morning" : "on day " + weddingDay;
	}

	// ---------------------------------------------------------------- couples

	/** Every couple: going out, engaged or married, and for the married what stands between them and a baby. */
	private static int couples(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		PeopleData data = PeopleData.get(server);
		long day = PeopleEvents.day(server);
		List<PeopleData.Bond> couples = new ArrayList<>();
		for (PeopleData.Bond bond : data.bonds()) {
			boolean bothAlive = data.person(bond.a).map(PeopleData.Person::alive).orElse(false)
				&& data.person(bond.b).map(PeopleData.Person::alive).orElse(false);
			if (bond.status.together() && bothAlive) {
				couples.add(bond);
			}
		}
		if (couples.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No couples yet. Friends who get on well may start going out"
				+ (FriendsConfig.get().romance ? "; give them time together." : ", but romance is switched off in the settings."))
				.withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		send(source, Component.literal("Couples (" + couples.size() + ")").withStyle(ChatFormatting.GOLD));
		couples.sort(Comparator.comparing((PeopleData.Bond b) -> -b.status.ordinal()).thenComparingLong(b -> b.since));
		for (PeopleData.Bond bond : couples) {
			String a = data.person(bond.a).map(PeopleData.Person::fullName).orElse("?");
			String b = data.person(bond.b).map(PeopleData.Person::fullName).orElse("?");
			String state = switch (bond.status) {
				case DATING -> "going out since day " + bond.since / 24000L + " (" + bond.dates + (bond.dates == 1 ? " date)" : " dates)");
				case ENGAGED -> "engaged; the wedding is " + (bond.weddingDay >= 0 ? whenText(bond.weddingDay, day) : "being planned");
				case MARRIED -> "married since day " + bond.since / 24000L + babyText(server, data, bond);
				default -> "";
			};
			send(source, Component.literal(a + " and " + b + ": ").withStyle(s -> s.withColor(Relationships.ROMANCE_COLOUR))
				.append(Component.literal(state).withStyle(ChatFormatting.WHITE)));
		}
		return couples.size();
	}

	private static String babyText(MinecraftServer server, PeopleData data, PeopleData.Bond bond) {
		if (bond.babyDue >= 0) {
			long left = bond.babyDue - PeopleEvents.clock(server);
			return left <= 0 ? "; the baby is due now (it arrives beside a parent at the camp)"
				: String.format(Locale.ROOT, "; a baby is due in %.1f days", left / 24000.0);
		}
		CompanionEntity a = PeopleEvents.loaded(server, bond.a);
		CompanionEntity b = PeopleEvents.loaded(server, bond.b);
		if (a == null || b == null) {
			return "; no baby yet (they need to be home at the camp together)";
		}
		String why = Births.whyNot(server, data, bond, a, b);
		return why == null ? "; a baby could come along any time now" : "; no baby yet: " + why;
	}

	// ---------------------------------------------------------- relationships

	/** The closest friendships in the camp. */
	private static int closest(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		PeopleData data = PeopleData.get(source.getServer());
		List<PeopleData.Bond> bonds = new ArrayList<>();
		for (PeopleData.Bond bond : data.bonds()) {
			if (alive(data, bond.a) && alive(data, bond.b)) {
				bonds.add(bond);
			}
		}
		bonds.sort(Comparator.comparingDouble((PeopleData.Bond b) -> -(b.friendship + b.romance)));
		if (bonds.isEmpty()) {
			source.sendFailure(Component.literal("Nobody has got to know each other yet. Friends grow closer by working, eating and chatting together."));
			return 0;
		}
		send(source, Component.literal("Closest friends in the camp (/friends relationships <name> for one person)").withStyle(ChatFormatting.GOLD));
		for (PeopleData.Bond bond : bonds.subList(0, Math.min(8, bonds.size()))) {
			String a = data.person(bond.a).map(p -> p.name).orElse("?");
			String b = data.person(bond.b).map(p -> p.name).orElse("?");
			send(source, Component.literal(a + " and " + b + ": ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(describe(data, bond)).withStyle(ChatFormatting.WHITE)));
		}
		return bonds.size();
	}

	/** One person's partner and closest friends. */
	private static int relationships(CommandContext<CommandSourceStack> ctx, String name) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		PeopleData data = PeopleData.get(server);
		Optional<PeopleData.Person> found = find(data, name);
		if (found.isEmpty()) {
			source.sendFailure(Component.literal("I don't know anyone called " + name + " in the camp."));
			return 0;
		}
		PeopleData.Person p = found.get();
		send(source, heading(p, PeopleEvents.day(server)));
		send(source, line("Partner", partnerText(data, p, PeopleEvents.day(server))));
		int shown = 0;
		for (PeopleData.Bond bond : data.bondsOf(p.id)) {
			UUID other = bond.other(p.id);
			if (!alive(data, other)) {
				continue;
			}
			String otherName = data.person(other).map(o -> o.name).orElse("?");
			send(source, Component.literal("  " + otherName + ": ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(describe(data, bond)).withStyle(ChatFormatting.WHITE)));
			if (++shown >= SHOWN) {
				break;
			}
		}
		if (shown == 0) {
			send(source, Component.literal("  Nobody close yet.").withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	/** "best friends (92)", "family, good friends (70)", "going out (romance 54)". */
	private static String describe(PeopleData data, PeopleData.Bond bond) {
		StringBuilder text = new StringBuilder();
		if (data.related(bond.a, bond.b)) {
			text.append("family, ");
		}
		double f = bond.friendship;
		text.append(f >= 90 ? "best friends" : f >= Relationships.CLOSE_FRIENDS ? "close friends"
			: f >= Relationships.FRIENDS ? "good friends" : f >= 25 ? "friendly" : "acquaintances");
		text.append(String.format(Locale.ROOT, " (%.0f)", f));
		switch (bond.status) {
			case DATING -> text.append(", going out");
			case ENGAGED -> text.append(", engaged");
			case MARRIED -> text.append(", married");
			case PARTED -> text.append(", once a couple");
			default -> {
				if (bond.romance >= 10) {
					text.append(", a spark between them");
				}
			}
		}
		if (bond.status.together() || bond.romance >= 10) {
			text.append(String.format(Locale.ROOT, " (romance %.0f)", bond.romance));
		}
		return text.toString();
	}

	// ---------------------------------------------------------------- helpers

	/** The person of this name (or "name family"), the living first, then the most recently gone. */
	static Optional<PeopleData.Person> find(PeopleData data, String name) {
		PeopleData.Person best = null;
		for (PeopleData.Person p : data.people()) {
			if (!p.name.equalsIgnoreCase(name) && !p.fullName().equalsIgnoreCase(name)) {
				continue;
			}
			if (best == null || p.alive() && !best.alive() || p.alive() == best.alive() && p.endedAt > best.endedAt) {
				best = p;
			}
		}
		return Optional.ofNullable(best);
	}

	private static boolean alive(PeopleData data, UUID id) {
		return data.person(id).map(PeopleData.Person::alive).orElse(false);
	}

	private static MutableComponent line(String label, String text) {
		return Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
	}

	private static void send(CommandSourceStack source, @Nullable Component message) {
		if (message != null) {
			source.sendSuccess(() -> message, false);
		}
	}
}
