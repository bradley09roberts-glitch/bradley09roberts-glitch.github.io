package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.IllegalFormatException;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/** Sends a friend's lines to nearby players, with per-line cooldowns and personality-based chattiness. */
public final class Speech {
	private static final double HEARING_RANGE_SQR = 40 * 40;
	private static final double DANGER_RANGE_SQR = 64 * 64;

	private Speech() {
	}

	/** "[Fern] " in the friend's colour. */
	public static MutableComponent prefix(FriendId id) {
		return Component.literal("[" + id.displayName() + "] ").withStyle(s -> s.withColor(id.colour()));
	}

	/**
	 * Says a line if its cooldowns allow. Returns true when something was said.
	 */
	public static boolean say(CompanionEntity companion, Line line, Object... args) {
		if (!(companion.level() instanceof ServerLevel level) || !companion.isAlive()) {
			return false;
		}
		long now = level.getGameTime();
		String chatter = FriendsConfig.get().chatter;
		Long last = companion.speechMemory().get(line);
		int cooldown = line.cooldownTicks();
		if ("chatty".equals(chatter)) {
			cooldown /= 2;
		}
		if (last != null && now - last < cooldown) {
			return false;
		}
		if (line.priority() == Line.Priority.CASUAL) {
			if ("quiet".equals(chatter)) {
				return false;
			}
			long gap = (long) (20 * (25 + (1.0 - companion.friendId().chattiness()) * 65));
			if ("chatty".equals(chatter)) {
				gap /= 2;
			}
			if (now - companion.lastCasualSpeech() < gap) {
				return false;
			}
			companion.setLastCasualSpeech(now);
		}
		companion.speechMemory().put(line, now);
		String[] variants = Lines.get(companion.friendId(), line);
		String template = variants[companion.getRandom().nextInt(variants.length)];
		String text;
		try {
			text = String.format(template, args);
		} catch (IllegalFormatException e) {
			text = template;
		}
		Component message = prefix(companion.friendId()).append(Component.literal(text).withStyle(ChatFormatting.WHITE));
		double range = line.priority() == Line.Priority.DANGER ? DANGER_RANGE_SQR : HEARING_RANGE_SQR;
		boolean heard = false;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(companion) <= range) {
				player.sendSystemMessage(message);
				heard = true;
			}
		}
		return heard;
	}

	/** A message from the friend to everyone on the server (deaths, milestones). */
	public static void announce(MinecraftServer server, Component message) {
		server.getPlayerList().broadcastSystemMessage(message, false);
	}

	/** A plain system message to one player, in the friend's voice but ignoring cooldowns. */
	public static void tell(ServerPlayer player, FriendId id, String text) {
		player.sendSystemMessage(prefix(id).append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
	}
}
