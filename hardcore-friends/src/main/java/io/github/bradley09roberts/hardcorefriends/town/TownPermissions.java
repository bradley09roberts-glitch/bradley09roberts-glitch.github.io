package io.github.bradley09roberts.hardcorefriends.town;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * Who may tell the friends what to do. The camp has an <b>owner</b>: the first player to change anything (set the
 * camp, recruit someone, give an order...), or whoever the owner hands it to. The owner trusts other players with
 * {@code /friends trust}. While {@code requireTrust} is on, only the owner and trusted players may give orders
 * (follow, stay, work, dismiss), recruit named friends or newcomers, open a friend's backpack, hand a friend anything
 * but food, move the camp or the chest, change the chatter, or ask for deliveries. Anyone may look ({@code list},
 * {@code needs}, {@code where}...), and anyone may hand a friend food: kindness is always allowed. Untrusted players
 * get a polite refusal.
 *
 * <p>The host of a single-player world, and server operators, are always allowed, so a world played alone just works.
 *
 * <p>Other packages check their own changing sub-commands with {@link #mayCommand} (or {@link #require}, which also
 * tells the player why not).
 */
public final class TownPermissions {
	private TownPermissions() {
	}

	/**
	 * True when this player may change things: give the friends orders, recruit, open backpacks, move the camp. With no
	 * owner yet, the player becomes the camp's owner (the first player to change anything). Call it only when the
	 * player is about to change something; to merely ask, use {@link #isAllowed}.
	 */
	public static boolean mayCommand(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		TownData data = TownData.get(server);
		data.remember(player);
		if (data.owner().isEmpty()) {
			claim(data, player);
			return true;
		}
		return isAllowed(player);
	}

	/** True when this player may change things now, without claiming the camp (see {@link #mayCommand}). */
	public static boolean isAllowed(ServerPlayer player) {
		if (!FriendsConfig.get().requireTrust) {
			return true;
		}
		MinecraftServer server = player.level().getServer();
		TownData data = TownData.get(server);
		UUID id = player.getUUID();
		return data.owner().isEmpty() || data.isOwner(id) || data.isTrusted(id) || isHost(server, player) || isOperator(player);
	}

	/** True for the owner, or a trusted player (whatever the setting), by id. */
	public static boolean isOwnerOrTrusted(MinecraftServer server, UUID id) {
		TownData data = TownData.get(server);
		return data.isOwner(id) || data.isTrusted(id);
	}

	/** True for the camp's owner, the host of a single-player world, or an operator: they may hand over the camp. */
	public static boolean mayManage(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		TownData data = TownData.get(server);
		return data.owner().isEmpty() || data.isOwner(player.getUUID()) || isHost(server, player) || isOperator(player);
	}

	/**
	 * For a command that changes something: true when the player may (see {@link #mayCommand}), otherwise a polite
	 * refusal and false. A command run by the server console or a command block is always allowed.
	 */
	public static boolean require(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return true;
		}
		if (mayCommand(player)) {
			return true;
		}
		refuse(player, nearestFriend(player));
		return false;
	}

	/** Tells an untrusted player, kindly, why not (a friend nearby says so too) and who can change it. */
	public static void refuse(ServerPlayer player, @Nullable CompanionEntity speaker) {
		if (speaker != null) {
			Speech.say(speaker, Line.REFUSE_ORDER, player.getName().getString());
		}
		String owner = TownData.get(player.level().getServer()).ownerName();
		player.sendSystemMessage(Component.literal("Only the camp's owner (" + owner + ") and the players they trust can do that. "
			+ "Ask " + owner + " to use /friends trust " + player.getName().getString() + ".").withStyle(ChatFormatting.YELLOW));
	}

	/** True for a player whose being online keeps the camp running (survival's camp loader). */
	static boolean keepsCampRunning(ServerPlayer player) {
		if (!FriendsConfig.get().requireTrust) {
			return true;
		}
		MinecraftServer server = player.level().getServer();
		TownData data = TownData.get(server);
		UUID id = player.getUUID();
		return data.owner().isEmpty() || data.isOwner(id) || data.isTrusted(id) || isHost(server, player);
	}

	private static void claim(TownData data, ServerPlayer player) {
		data.setOwner(player.getUUID(), player.getName().getString());
		player.sendSystemMessage(Component.literal("You are now the camp's owner. On a shared world, only you and the players you "
			+ "trust (/friends trust <player>) can give the friends orders. Hand it over with /friends owner <player>.")
			.withStyle(ChatFormatting.GOLD));
		MinecraftServer server = player.level().getServer();
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != player && FriendsConfig.get().requireTrust) {
				other.sendSystemMessage(Component.literal(player.getName().getString() + " now owns the camp. Ask them to trust you "
					+ "(/friends trust " + other.getName().getString() + ") to give the friends orders.").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static boolean isHost(MinecraftServer server, ServerPlayer player) {
		return server.isSingleplayerOwner(player.nameAndId());
	}

	private static boolean isOperator(ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/** The nearest friend on the team within hearing, to say a refusal aloud. */
	static @Nullable CompanionEntity nearestFriend(ServerPlayer player) {
		CompanionEntity best = null;
		double bestDist = 16 * 16;
		for (CompanionEntity c : Companions.in(player.level())) {
			double d = c.distanceToSqr(player);
			if (d < bestDist) {
				bestDist = d;
				best = c;
			}
		}
		return best;
	}

	// ------------------------------------------------------------- right-click

	/**
	 * Runs before every other right-click handler on a friend (registered first): an untrusted player may not open a
	 * backpack, hand over anything but food, or ask a stranger to join once the stranger has said what they would
	 * like. Everything else goes on as usual.
	 */
	static InteractionResult interact(CompanionEntity c, ServerPlayer player, InteractionHand hand) {
		if (!c.isTeamMember()) {
			if (c.isSettler() && strangerHasAsked(c) && !mayCommand(player)) {
				Speech.tell(player, c, "I'd gladly join your camp, but I'd want its leader to agree. Ask "
					+ TownData.get(player.level().getServer()).ownerName() + " to trust you first.");
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			if (player.isShiftKeyDown() && !mayCommand(player)) {
				refuse(player, c);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		}
		// Real food is always welcome from anyone; anything else (rotten flesh included) goes into the backpack, which is
		// the camp's business.
		if (!CompanionEntity.isEdible(held) && !mayCommand(player)) {
			refuse(player, c);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	/**
	 * True once a stranger has said what they would like before joining: the next right-click may ask them in. (The
	 * settler package keeps the request under {@code "request"} in its state, {@code "settler"} in the friend's extra.)
	 */
	private static boolean strangerHasAsked(CompanionEntity c) {
		return c.extra().getCompoundOrEmpty("settler").getIntOr("request", -1) >= 0;
	}

	// ------------------------------------------------------------------ orders

	/**
	 * Which of the friends an order reaches: a friend who has come to distrust this player (a very low bond) will not
	 * follow them, and no player may have more than {@code maxFollowersPerPlayer} friends following them at once.
	 * Those left out say so; the player is told how many could not come.
	 */
	public static List<CompanionEntity> acceptOrder(ServerPlayer player, List<CompanionEntity> targets, CompanionMode mode) {
		if (mode != CompanionMode.FOLLOW) {
			return targets;
		}
		int following = 0;
		for (CompanionEntity c : Companions.all()) {
			ServerPlayer leader = c.mode() == CompanionMode.FOLLOW ? c.leader() : null;
			if (leader != null && leader.getUUID().equals(player.getUUID()) && !targets.contains(c)) {
				following++;
			}
		}
		int max = FriendsConfig.get().maxFollowersPerPlayer;
		List<CompanionEntity> accepted = new ArrayList<>();
		int refusedBond = 0;
		int overLimit = 0;
		for (CompanionEntity c : targets) {
			if (Bonds.refusesToFollow(c, player)) {
				Speech.say(c, Line.REFUSE_FOLLOW, player.getName().getString());
				refusedBond++;
			} else if (following + accepted.size() >= max) {
				overLimit++;
			} else {
				accepted.add(c);
			}
		}
		if (refusedBond > 0) {
			player.sendSystemMessage(Component.literal(refusedBond + (refusedBond == 1 ? " friend does" : " friends do")
				+ " not trust you enough to follow you. Gifts, food and help around the camp mend a bond (/friends bond).")
				.withStyle(ChatFormatting.YELLOW));
		}
		if (overLimit > 0) {
			player.sendSystemMessage(Component.literal("At most " + max + " friends may follow one player at a time, so "
				+ overLimit + (overLimit == 1 ? " stays" : " stay") + " behind. Send someone back with /friends work <name>.")
				.withStyle(ChatFormatting.YELLOW));
		}
		return accepted;
	}
}
