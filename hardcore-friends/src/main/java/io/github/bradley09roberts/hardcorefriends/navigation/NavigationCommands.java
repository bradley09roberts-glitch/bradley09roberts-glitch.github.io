package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * {@code /friends senses [name]}: what each friend senses (underground, in the dark, in the water, monsters heard and
 * how far) and whether they are stuck or getting themselves out of trouble. Only reads: it works at permission level 0
 * with cheats off and changes nothing.
 */
final class NavigationCommands {
	private NavigationCommands() {
	}

	/** Adds the sub-command to the {@code /friends} root (a {@code FriendsCommand.EXTENSIONS} entry). */
	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("senses")
			.executes(ctx -> senses(ctx, "all"))
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(names(), builder))
				.executes(ctx -> senses(ctx, StringArgumentType.getString(ctx, "name")))));
	}

	private static List<String> names() {
		List<String> names = new ArrayList<>();
		for (FriendId id : FriendId.values()) {
			names.add(id.key());
		}
		for (Supplier<Collection<String>> extra : FriendsCommand.EXTRA_NAMES) {
			names.addAll(extra.get());
		}
		names.add("all");
		return names;
	}

	private static int senses(CommandContext<CommandSourceStack> ctx, String name) {
		CommandSourceStack source = ctx.getSource();
		List<CompanionEntity> friends = new ArrayList<>();
		if ("all".equalsIgnoreCase(name)) {
			friends.addAll(Companions.all());
			friends.sort(Comparator.comparingInt(CompanionEntity::rosterIndex));
		} else {
			find(name).ifPresent(friends::add);
		}
		if (friends.isEmpty()) {
			source.sendFailure(Component.literal("all".equalsIgnoreCase(name) ? "None of your friends are nearby."
				: "No friend called " + name + " is nearby."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("What your friends sense (rescues are "
			+ (FriendsConfig.get().rescueStuckFriends ? "on" : "off") + ")").withStyle(ChatFormatting.GOLD), false);
		for (CompanionEntity c : friends) {
			String trouble = Wayfinder.describe(c);
			String text = Senses.describe(c) + (trouble != null ? "; " + trouble : "") + (c.isSprinting() ? "; running" : "");
			source.sendSuccess(() -> Speech.prefix(c).append(Component.literal(text).withStyle(ChatFormatting.GRAY)), false);
		}
		return friends.size();
	}

	private static Optional<CompanionEntity> find(String name) {
		Optional<FriendId> id = FriendId.byKey(name.toLowerCase(Locale.ROOT));
		if (id.isPresent()) {
			Optional<CompanionEntity> named = Companions.find(id.get());
			if (named.isPresent()) {
				return named;
			}
		}
		return FriendsCommand.findNewcomer(name);
	}
}
