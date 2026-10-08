package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * {@code /friends newcomers}: the newcomers who have joined (their trade, what they are doing or how their story
 * ended, and who asked them in), then the strangers within reach of the player and what each would like. Works at
 * permission level 0 with cheats off, and changes nothing. Also offers recruited newcomers' names when typing a
 * friend's name in the other {@code /friends} commands.
 */
final class SettlerCommands {
	/** Strangers this close to the player are listed. */
	private static final double NEARBY = 128;
	/** Past newcomers (fallen or gone) listed after the living ones. */
	private static final int PAST_SHOWN = 10;

	private SettlerCommands() {
	}

	static void register() {
		FriendsCommand.EXTENSIONS.add(root -> root.then(Commands.literal("newcomers").executes(SettlerCommands::newcomers)));
		FriendsCommand.EXTRA_NAMES.add(SettlerCommands::teamNames);
	}

	/** Recruited newcomers who are loaded, lower case, for name suggestions. */
	private static Collection<String> teamNames() {
		List<String> names = new ArrayList<>();
		for (CompanionEntity c : Companions.all()) {
			if (c.isSettler()) {
				names.add(c.displayName().toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

	private static int newcomers(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		SettlerData data = SettlerData.get(source.getServer());
		FriendsConfig cfg = FriendsConfig.get();
		List<SettlerData.Newcomer> all = data.newcomers();
		source.sendSuccess(() -> Component.literal("Newcomers on your team (" + data.aliveCount() + " of at most " + cfg.maxSettlers + ")")
			.withStyle(ChatFormatting.GOLD), false);
		if (all.isEmpty()) {
			source.sendSuccess(() -> Component.literal("Nobody has joined yet. People live in some villages and at survivor camps in "
				+ "the wilds, and now and then a traveller visits a growing camp. Right-click one to talk.")
				.withStyle(ChatFormatting.GRAY), false);
		}
		List<SettlerData.Newcomer> past = new ArrayList<>();
		for (SettlerData.Newcomer n : all) {
			if (n.state == SettlerData.State.ALIVE) {
				source.sendSuccess(() -> line(n), false);
			} else {
				past.add(n);
			}
		}
		past.sort(Comparator.comparingLong((SettlerData.Newcomer n) -> n.endedAt).reversed());
		for (SettlerData.Newcomer n : past.subList(0, Math.min(PAST_SHOWN, past.size()))) {
			source.sendSuccess(() -> line(n), false);
		}
		strangersNearby(source);
		return all.size();
	}

	/** "[Mabel] Farmer: harvesting crops (18 hp, mood good) - asked in by Steve". */
	private static Component line(SettlerData.Newcomer n) {
		int colour = n.colour;
		MutableComponent line = Component.literal("[" + n.name + "] ").withStyle(s -> s.withColor(colour))
			.append(Component.literal(n.archetype.role().title() + ": ").withStyle(ChatFormatting.GRAY));
		String text = switch (n.state) {
			case ALIVE -> loaded(n.id)
				.map(c -> c.activity() + String.format(Locale.ROOT, " (%.0f hp, mood %s)", c.getHealth(), c.needs().mood().word()))
				.orElse("away (last seen " + posText(n.lastPos) + ")");
			case DEAD -> "fallen for good - " + (n.cause.isEmpty() ? "lost" : n.cause);
			case DISMISSED -> "left the team";
		};
		line.append(Component.literal(text).withStyle(n.state == SettlerData.State.ALIVE ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
		if (!n.recruitedByName.isEmpty()) {
			line.append(Component.literal(" - asked in by " + n.recruitedByName).withStyle(ChatFormatting.DARK_GRAY));
		}
		return line;
	}

	private static void strangersNearby(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Vec3 here = source.getPosition();
		List<CompanionEntity> near = new ArrayList<>();
		for (CompanionEntity c : Companions.strangers()) {
			if (c.level() == level && c.isSettler() && c.position().distanceToSqr(here) <= NEARBY * NEARBY) {
				near.add(c);
			}
		}
		near.sort(Comparator.comparingDouble(c -> c.position().distanceToSqr(here)));
		if (near.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No strangers nearby.").withStyle(ChatFormatting.GRAY), false);
			return;
		}
		source.sendSuccess(() -> Component.literal("Strangers nearby").withStyle(ChatFormatting.GOLD), false);
		for (CompanionEntity c : near) {
			BlockPos p = c.blockPosition();
			double distance = Math.sqrt(c.position().distanceToSqr(here));
			CompoundTag tag = Strangers.state(c);
			int request = tag.getIntOr(Strangers.REQUEST, -1);
			String wants = request < 0 ? "not met yet" : "would like " + Requests.get(c.friendId(), request).describe();
			MutableComponent line = Component.literal("[" + c.displayName() + "] ").withStyle(s -> s.withColor(c.nameColour()))
				.append(Component.literal(c.friendId().role().title() + ": ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(posText(p) + String.format(Locale.ROOT, " (%.0f blocks away), ", distance) + wants)
					.withStyle(ChatFormatting.WHITE));
			source.sendSuccess(() -> line, false);
		}
	}

	private static Optional<CompanionEntity> loaded(UUID id) {
		for (CompanionEntity c : Companions.all()) {
			if (c.getUUID().equals(id)) {
				return Optional.of(c);
			}
		}
		return Optional.empty();
	}

	private static String posText(@Nullable BlockPos pos) {
		return pos == null ? "somewhere" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}
}
