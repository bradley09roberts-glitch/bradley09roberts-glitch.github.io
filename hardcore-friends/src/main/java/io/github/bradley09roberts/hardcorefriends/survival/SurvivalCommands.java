package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * {@code /friends skills [name]}: each friend's levels as bars; and {@code /friends trips}: the places found on
 * trips, who is away and whether the camp keeps running while you are gone. Both only read; they work at permission
 * level 0 with cheats off and change nothing.
 */
final class SurvivalCommands {
	private SurvivalCommands() {
	}

	/** Adds the sub-commands to the {@code /friends} root (a {@code FriendsCommand.EXTENSIONS} entry). */
	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("skills")
			.executes(ctx -> skills(ctx, "all"))
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(names(), builder))
				.executes(ctx -> skills(ctx, StringArgumentType.getString(ctx, "name")))));
		root.then(Commands.literal("trips").executes(SurvivalCommands::trips));
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

	private static int skills(CommandContext<CommandSourceStack> ctx, String name) {
		CommandSourceStack source = ctx.getSource();
		if ("all".equalsIgnoreCase(name)) {
			List<CompanionEntity> friends = new ArrayList<>(Companions.all());
			friends.sort(Comparator.comparingInt(CompanionEntity::rosterIndex));
			if (friends.isEmpty()) {
				source.sendFailure(Component.literal("None of your friends are nearby."));
				return 0;
			}
			source.sendSuccess(() -> Component.literal("Skills (levels 0-" + Skills.MAX_LEVEL + "; /friends skills <name> for all of them)")
				.withStyle(ChatFormatting.GOLD), false);
			for (CompanionEntity c : friends) {
				source.sendSuccess(() -> summaryLine(c), false);
			}
			return friends.size();
		}
		Optional<CompanionEntity> found = find(name);
		if (found.isEmpty()) {
			source.sendFailure(Component.literal("No friend called " + name + " is nearby."));
			return 0;
		}
		CompanionEntity c = found.get();
		source.sendSuccess(() -> Speech.prefix(c).append(Component.literal("skills").withStyle(ChatFormatting.GOLD)), false);
		String own = Speciality.workName(c.friendId().role());
		for (String kind : Skills.kinds()) {
			int xp = Skills.xp(c, kind);
			int level = Skills.levelFor(xp);
			String next = level >= Skills.MAX_LEVEL ? "top level" : xp + "/" + Skills.threshold(level + 1) + " to the next";
			MutableComponent line = Component.literal("  ")
				.append(Component.literal(Skills.bar(level)).withStyle(level > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
				.append(Component.literal(" " + kind + " " + level).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" (" + next + (kind.equals(own) ? ", speciality" : "") + ")").withStyle(ChatFormatting.GRAY));
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	/** "[Fern] farming 4, foraging 2, fighting 1" (their three best skills, or "still learning"). */
	private static Component summaryLine(CompanionEntity c) {
		List<String> kinds = new ArrayList<>(Skills.kinds());
		kinds.sort(Comparator.comparingInt((String k) -> Skills.xp(c, k)).reversed());
		List<String> best = new ArrayList<>();
		for (String kind : kinds) {
			int level = Skills.level(c, kind);
			if (level > 0 && best.size() < 3) {
				best.add(kind + " " + level);
			}
		}
		String text = best.isEmpty() ? "still learning" : String.join(", ", best);
		return Speech.prefix(c).append(Component.literal(text).withStyle(ChatFormatting.WHITE));
	}

	private static Optional<CompanionEntity> find(String name) {
		Optional<FriendId> id = FriendId.byKey(name.toLowerCase(java.util.Locale.ROOT));
		if (id.isPresent()) {
			Optional<CompanionEntity> named = Companions.find(id.get());
			if (named.isPresent()) {
				return named;
			}
		}
		return FriendsCommand.findNewcomer(name);
	}

	private static int trips(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		CampData data = Camp.data(source.getServer());
		FriendsConfig cfg = FriendsConfig.get();
		List<String> lines = new ArrayList<>();
		lines.add("Trips are " + (cfg.allowTrips ? "on" : "off") + "; the camp " + (cfg.keepCampLoaded
			? "keeps running while a player is online" : "only runs while a player is near") + ".");
		lines.addAll(ChunkLoader.describe());
		Map<UUID, String> roaming = ChunkLoader.roaming();
		for (CompanionEntity c : Companions.all()) {
			if (roaming.containsKey(c.getUUID())) {
				lines.add(c.displayName() + " is away " + roaming.get(c.getUUID()) + ", around " + Compass.coords(c.blockPosition()) + ".");
			}
		}
		List<Places.Place> places = Places.all(data);
		if (places.isEmpty()) {
			lines.add("No places found on trips yet. Scout explores further afield on safe, well-fed days.");
		} else {
			lines.add("Places found (" + places.size() + "):");
			long now = source.getLevel().getGameTime();
			for (Places.Place p : places.reversed()) {
				if (lines.size() > 24) {
					lines.add("...and more.");
					break;
				}
				String avoid = p.avoidUntil() > now ? " (keeping away for now)" : "";
				String where = p.type().startsWith(Places.BIOME) ? " near " : " at ";
				lines.add("  " + p.name() + where + Compass.coords(p.pos()) + avoid);
			}
		}
		for (String line : lines) {
			source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}
}
