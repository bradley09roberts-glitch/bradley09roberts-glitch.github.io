package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * {@code /friends party}: each player's expedition party. {@code add <name>} and {@code remove <name>} choose who comes
 * (a friend is in one party at most), {@code list} (or no word at all) shows the party and the places expeditions have
 * found, {@code go} has everyone in the party who is near follow you (those near the camp stop at the chest to pack
 * first), and {@code home} sends everyone in the party back to work at the camp (from another dimension, through the
 * portal they came in by). Like every {@code /friends} command it works without cheats and hands out nothing.
 */
final class PartyCommand {
	/** Party members this close (in the player's dimension) hear "go". */
	private static final double HEARING = 128;
	/** A member this close to the supply chest packs before setting off. */
	private static final double PACKING = 48;

	private PartyCommand() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("party")
			.executes(PartyCommand::list)
			.then(Commands.literal("list").executes(PartyCommand::list))
			.then(Commands.literal("add").then(nameArg().executes(PartyCommand::add)))
			.then(Commands.literal("remove").then(nameArg().executes(PartyCommand::remove)))
			.then(Commands.literal("go").executes(PartyCommand::go))
			.then(Commands.literal("home").executes(PartyCommand::home)));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> nameArg() {
		return Commands.argument("name", StringArgumentType.word()).suggests((ctx, builder) -> {
			List<String> names = new ArrayList<>();
			for (FriendId id : FriendId.values()) {
				names.add(id.key());
			}
			for (Supplier<Collection<String>> extra : FriendsCommand.EXTRA_NAMES) {
				names.addAll(extra.get());
			}
			return SharedSuggestionProvider.suggest(names, builder);
		});
	}

	/** A loaded friend on the team, by name: one of the nine, or a newcomer. */
	private static Optional<CompanionEntity> find(String name) {
		Optional<FriendId> id = FriendId.byKey(name.toLowerCase(Locale.ROOT));
		if (id.isPresent()) {
			return Companions.find(id.get());
		}
		return FriendsCommand.findNewcomer(name);
	}

	private static @Nullable CompanionEntity loaded(UUID id) {
		for (CompanionEntity c : Companions.all()) {
			if (c.getUUID().equals(id)) {
				return c;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------- add

	private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "name");
		Optional<CompanionEntity> found = find(name);
		if (found.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("No friend called " + name + " is near enough to hear you."));
			return 0;
		}
		CompanionEntity c = found.get();
		ExpeditionData data = ExpeditionData.get(ctx.getSource().getServer());
		Optional<UUID> before = data.partyOf(c.getUUID());
		data.join(player.getUUID(), c.getUUID(), c.displayName());
		String moved = before.isPresent() && !before.get().equals(player.getUUID()) ? " (they left their other party)" : "";
		ctx.getSource().sendSuccess(() -> Component.literal(c.displayName() + " is in your party now" + moved
			+ ". /friends party go when you set off.").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "name");
		ExpeditionData data = ExpeditionData.get(ctx.getSource().getServer());
		for (ExpeditionData.Member m : data.party(player.getUUID())) {
			CompanionEntity c = loaded(m.id());
			String current = c != null ? c.displayName() : m.name();
			if (current.equalsIgnoreCase(name) || m.name().equalsIgnoreCase(name)) {
				data.leave(m.id());
				ctx.getSource().sendSuccess(() -> Component.literal(current + " has left your party.").withStyle(ChatFormatting.YELLOW), false);
				return 1;
			}
		}
		ctx.getSource().sendFailure(Component.literal(name + " is not in your party."));
		return 0;
	}

	// ------------------------------------------------------------------ list

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CommandSourceStack source = ctx.getSource();
		ExpeditionData data = ExpeditionData.get(source.getServer());
		List<ExpeditionData.Member> members = data.party(player.getUUID());
		if (members.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Your party is empty. Add friends with /friends party add <name>, "
				+ "then /friends party go to set off and /friends party home to send them back.").withStyle(ChatFormatting.GRAY), false);
		} else {
			source.sendSuccess(() -> Component.literal("Your party (/friends party go | home | add | remove):").withStyle(ChatFormatting.GOLD), false);
			for (ExpeditionData.Member m : members) {
				CompanionEntity c = loaded(m.id());
				MutableComponent line = c != null ? Speech.prefix(c) : Component.literal("[" + m.name() + "] ").withStyle(ChatFormatting.GRAY);
				String text;
				if (c == null) {
					text = "not nearby";
				} else {
					String where = c.level() == player.level()
						? String.format(Locale.ROOT, "%.0f blocks away", Math.sqrt(c.distanceToSqr(player)))
						: "in " + dimensionName(c.level());
					text = c.activity() + String.format(Locale.ROOT, " (%s, %.0f hp)", where, c.getHealth());
				}
				line.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
				source.sendSuccess(() -> line, false);
			}
		}
		List<ExpeditionData.Place> places = data.places();
		if (!places.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Places found on expeditions:").withStyle(ChatFormatting.AQUA), false);
			int shown = 0;
			for (int i = places.size() - 1; i >= 0 && shown < 8; i--, shown++) {
				ExpeditionData.Place p = places.get(i);
				String text = "  " + placeName(p.type()) + " at " + p.pos().getX() + " " + p.pos().getY() + " " + p.pos().getZ()
					+ " (" + dimensionName(p.dim()) + ")";
				source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
			}
		}
		return 1;
	}

	private static String placeName(String type) {
		return switch (type) {
			case ExpeditionData.NETHER_PORTAL -> "Nether portal";
			case ExpeditionData.FORTRESS -> "Nether fortress";
			case ExpeditionData.STRONGHOLD -> "Stronghold (near)";
			case ExpeditionData.END_PORTAL -> "End portal";
			case ExpeditionData.CAMP_PORTAL -> "Camp's Nether portal";
			default -> type.replace('_', ' ');
		};
	}

	private static String dimensionName(Level level) {
		return dimensionName(Travel.dimId(level));
	}

	private static String dimensionName(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "the overworld";
			case "minecraft:the_nether" -> "the Nether";
			case "minecraft:the_end" -> "the End";
			default -> dim;
		};
	}

	// ------------------------------------------------------------------ orders

	private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ExpeditionData data = ExpeditionData.get(ctx.getSource().getServer());
		List<ExpeditionData.Member> members = data.party(player.getUUID());
		if (members.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Your party is empty. Add friends with /friends party add <name>."));
			return 0;
		}
		ServerLevel level = player.level();
		var chest = Camp.data(level.getServer()).chestPos().filter(p -> Camp.isCampLevel(level, Camp.data(level.getServer())));
		int coming = 0;
		int packing = 0;
		List<String> away = new ArrayList<>();
		for (ExpeditionData.Member m : members) {
			CompanionEntity c = loaded(m.id());
			if (c == null || c.level() != level || c.distanceToSqr(player) > HEARING * HEARING) {
				away.add(c != null ? c.displayName() : m.name());
				continue;
			}
			c.setMode(CompanionMode.FOLLOW, player);
			Speech.say(c, Line.FOLLOW, player.getName().getString());
			coming++;
			if (chest.isPresent() && c.blockPosition().distSqr(chest.get()) <= PACKING * PACKING) {
				PackGoal.order(c);
				packing++;
			}
		}
		int n = coming;
		int p = packing;
		if (n > 0) {
			ctx.getSource().sendSuccess(() -> Component.literal(n + (n == 1 ? " friend follows" : " friends follow") + " you"
				+ (p > 0 ? " (" + p + " packing at the chest first)" : "") + ".").withStyle(ChatFormatting.GREEN), false);
		}
		if (!away.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("Too far away to hear you: " + String.join(", ", away) + ".")
				.withStyle(ChatFormatting.GRAY), false);
		}
		return n;
	}

	private static int home(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ExpeditionData data = ExpeditionData.get(ctx.getSource().getServer());
		int sent = 0;
		List<String> away = new ArrayList<>();
		for (ExpeditionData.Member m : data.party(player.getUUID())) {
			CompanionEntity c = loaded(m.id());
			if (c == null) {
				away.add(m.name());
				continue;
			}
			c.setMode(CompanionMode.WORK, player);
			Speech.say(c, Line.WORK);
			sent++;
		}
		int n = sent;
		if (n == 0 && away.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("Your party is empty."));
			return 0;
		}
		if (n > 0) {
			ctx.getSource().sendSuccess(() -> Component.literal(n + (n == 1 ? " friend heads" : " friends head")
				+ " back to work at the camp (through a portal first, if they are in another dimension).").withStyle(ChatFormatting.GREEN), false);
		}
		if (!away.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("Not nearby, so they did not hear: " + String.join(", ", away) + ".")
				.withStyle(ChatFormatting.GRAY), false);
		}
		return n;
	}
}
