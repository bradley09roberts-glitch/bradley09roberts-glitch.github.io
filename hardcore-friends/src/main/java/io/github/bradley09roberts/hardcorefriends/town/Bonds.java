package io.github.bradley09roberts.hardcorefriends.town;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Each friend's bond with each player, from -100 to 100. It grows with kindness: food and gifts handed over (more
 * when the friend was hurt and the food heals them), standing up to a monster that hurt them, delivering what the
 * camp needs while they are near, and time spent near each other (a little each day). It falls when a player hurts
 * them on purpose (a sneaking hit) or dismisses them. Every gain is capped per player and friend each in-game day, so a
 * bond is built over time rather than bought.
 *
 * <p>What a bond does: the hello on a right-click is warm ({@value #WARM} and up) or cool ({@value #COOL} and down);
 * at {@value #CLOSE} and up the friend defends that player first and feeds them first when they are hungry; at
 * {@value #DISTRUST} and down they will not follow that player (they say so politely), and stop following if the bond
 * falls that far on the way. Nothing here ever makes a friend hurt a player.
 */
public final class Bonds {
	public static final int MIN = -100;
	public static final int MAX = 100;
	/** Close: defended first and fed first. */
	public static final int CLOSE = 60;
	/** Warm: a warm hello. */
	public static final int WARM = 40;
	/** Cool: a cool hello. */
	public static final int COOL = -30;
	/** At or below this, the friend will not follow the player. */
	public static final int DISTRUST = -50;

	private static final String TIME = "time";
	private static final String GIFT = "gift";
	private static final String DEFEND = "defend";
	static final String JOBS = "jobs";
	/** A player near a friend for a minute: +1, at most 4 a day per friend. */
	private static final int TIME_RANGE = 16;
	/** A deliberate (sneaking) hit costs this much, at most once every half second per friend and player. */
	private static final int HIT_COST = 8;
	private static final int DISMISS_COST = 15;

	/** Today's gains per friend, player and kind ({@code key|player|kind}), against the daily caps. Not saved. */
	private static final Map<String, Integer> EARNED = new HashMap<>();
	private static long earnedDay = Long.MIN_VALUE;
	/** When each friend was last hit on purpose by each player (game time), so one swing counts once. Not saved. */
	private static final Map<String, Long> LAST_HIT = new HashMap<>();

	private Bonds() {
	}

	/**
	 * The key a friend's bonds are kept under: one of the nine friends by their own name, a newcomer by their entity
	 * id (a newcomer's name may be given to someone else once they are gone).
	 */
	public static String key(CompanionEntity c) {
		return c.isSettler() ? c.getUUID().toString() : c.friendId().key();
	}

	/** This friend's bond with a player (0 when they have never met). */
	public static int get(CompanionEntity c, UUID player) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		return TownData.get(level.getServer()).bond(key(c), player);
	}

	/**
	 * Changes a bond by {@code amount} (negative to lower it). A gain counts against the daily cap for its kind
	 * ({@code dailyCap} for this friend and player, 0 for none). Returns the change actually made.
	 */
	public static int add(CompanionEntity c, ServerPlayer player, int amount, String kind, int dailyCap) {
		if (!(c.level() instanceof ServerLevel level) || !c.isTeamMember() || amount == 0) {
			return 0;
		}
		TownData data = TownData.get(level.getServer());
		data.remember(player);
		String key = key(c);
		if (amount > 0 && dailyCap > 0) {
			long day = Camp.day(level);
			if (day != earnedDay) {
				EARNED.clear();
				earnedDay = day;
			}
			String capKey = key + "|" + player.getUUID() + "|" + kind;
			int earned = EARNED.getOrDefault(capKey, 0);
			amount = Math.min(amount, dailyCap - earned);
			if (amount <= 0) {
				return 0;
			}
			EARNED.put(capKey, earned + amount);
		}
		int before = data.bond(key, player.getUUID());
		data.setBond(key, c.displayName(), player.getUUID(), before + amount);
		return data.bond(key, player.getUUID()) - before;
	}

	/** "close", "warm", "friendly", "neutral", "cool" or "distant". */
	public static String word(int bond) {
		if (bond >= CLOSE) {
			return "close";
		}
		if (bond >= WARM) {
			return "warm";
		}
		if (bond >= 10) {
			return "friendly";
		}
		if (bond > COOL) {
			return "neutral";
		}
		if (bond > DISTRUST) {
			return "cool";
		}
		return "distant";
	}

	public static ChatFormatting colour(int bond) {
		if (bond >= WARM) {
			return ChatFormatting.GREEN;
		}
		if (bond > COOL) {
			return ChatFormatting.WHITE;
		}
		return bond > DISTRUST ? ChatFormatting.GOLD : ChatFormatting.RED;
	}

	/** True when the friend has come to distrust this player too much to follow them. */
	public static boolean refusesToFollow(CompanionEntity c, ServerPlayer player) {
		return get(c, player.getUUID()) <= DISTRUST;
	}

	/** For the food-sharing job: 1 for a player this friend is close to (fed first), otherwise 0. */
	public static int feedPriority(CompanionEntity c, ServerPlayer player) {
		return get(c, player.getUUID()) >= CLOSE ? 1 : 0;
	}

	// ------------------------------------------------------------------ events

	/** A player handed something over: food eaten at once (more if it heals a hurt friend), or a gift for the backpack. */
	static void gift(CompanionEntity c, ServerPlayer player, ItemStack given, boolean ate) {
		int amount = ate ? 3 : 2;
		if (ate && c.getHealth() < c.getMaxHealth()) {
			amount += 2; // feeding a hurt friend heals them
		}
		add(c, player, amount, GIFT, 12);
	}

	/** A sneaking player hit a friend on purpose (players who are not sneaking cannot hurt friends at all). */
	static float hurt(CompanionEntity c, ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer player) {
			String hitKey = key(c) + "|" + player.getUUID();
			long now = level.getGameTime();
			Long last = LAST_HIT.get(hitKey);
			if (last == null || now - last >= 10 || now < last) {
				LAST_HIT.put(hitKey, now);
				add(c, player, -HIT_COST, "", 0);
				if (LAST_HIT.size() > 512) {
					LAST_HIT.clear();
				}
			}
		}
		return amount;
	}

	/** A friend fell: whoever comes back in their name later is somebody new, so their bonds go with them. */
	static void died(CompanionEntity c, ServerLevel level, DamageSource source) {
		if (c.isTeamMember()) {
			TownData.get(level.getServer()).forgetFriend(key(c));
		}
	}

	/** A newcomer who is dismissed leaves for good; one of the nine may come back and remembers. */
	static void dismissed(CompanionEntity c, ServerLevel level) {
		if (c.isSettler()) {
			TownData.get(level.getServer()).forgetFriend(key(c));
		}
	}

	/** {@code /friends dismiss}: the friend remembers who sent them away. */
	public static void dismissedBy(CommandSourceStack source, CompanionEntity c) {
		ServerPlayer player = source.getPlayer();
		if (player != null && !c.isSettler()) {
			add(c, player, -DISMISS_COST, "", 0);
		}
	}

	/**
	 * A player hurt a monster: every friend nearby whom that monster hurt in the last 15 seconds is grateful (fighting
	 * off what was attacking them).
	 */
	static void afterDamage(LivingEntity victim, DamageSource source, float taken) {
		if (taken <= 0 || !(source.getEntity() instanceof ServerPlayer player) || victim instanceof Player
			|| victim instanceof CompanionEntity || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		for (CompanionEntity c : Companions.near(level, victim.getBoundingBox().inflate(16))) {
			if (c.getLastHurtByMob() == victim && c.tickCount - c.getLastHurtByMobTimestamp() < 300) {
				add(c, player, 1, DEFEND, 6);
			}
		}
	}

	/** Every minute: a little closer to each player who spends time near them. */
	static void timeTogether(MinecraftServer server) {
		for (CompanionEntity c : Companions.all()) {
			if (!(c.level() instanceof ServerLevel level)) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(c) <= TIME_RANGE * TIME_RANGE) {
					add(c, player, 1, TIME, 4);
				}
			}
		}
	}

	/** Every friend's tick: defend a close player first, and stop following a player they have come to distrust. */
	static void tick(CompanionEntity c, ServerLevel level) {
		if (!c.isTeamMember() || !c.isAlive()) {
			return;
		}
		int phase = Math.floorMod(c.tickCount + c.getId(), 100);
		if (phase % 10 == 0 && (c.mode() == CompanionMode.WORK || c.mode() == CompanionMode.FOLLOW) && !c.isRetreating()
			&& !c.isAsleep()) {
			defendClosePlayer(c, level);
		}
		if (phase == 50 && c.mode() == CompanionMode.FOLLOW) {
			ServerPlayer leader = c.leader();
			if (leader != null && refusesToFollow(c, leader)) {
				c.setMode(CompanionMode.WORK, null);
				Speech.say(c, Line.REFUSE_FOLLOW, leader.getName().getString());
				leader.sendSystemMessage(Component.literal(c.displayName() + " no longer trusts you enough to follow you, "
					+ "and has gone back to work.").withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	/**
	 * A close player (bond {@value #CLOSE} or more) within 16 blocks who was hurt by a monster in the last three
	 * seconds comes first: the friend goes for that monster, if they could stand and fight it.
	 */
	private static void defendClosePlayer(CompanionEntity c, ServerLevel level) {
		TownData data = TownData.get(level.getServer());
		String key = key(c);
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator() || player.isCreative() || player.distanceToSqr(c) > 16 * 16
				|| data.bond(key, player.getUUID()) < CLOSE) {
				continue;
			}
			LivingEntity attacker = player.getLastHurtByMob();
			if (attacker == null || player.tickCount - player.getLastHurtByMobTimestamp() > 60 || attacker == c.getTarget()
				|| attacker instanceof Player || attacker instanceof CompanionEntity || !Threats.isThreat(attacker)
				|| attacker.level() != level || attacker.distanceToSqr(c) > 16 * 16) {
				continue;
			}
			if (c.canStandAndFight(attacker) && !c.hasGivenUpOn(attacker)) {
				c.setTarget(attacker);
				return;
			}
		}
	}

	/** Right-click with an empty hand: a warm or cool hello by the bond (the usual one in between). */
	static InteractionResult greet(CompanionEntity c, ServerPlayer player, InteractionHand hand) {
		if (!c.isTeamMember() || player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) {
			return InteractionResult.PASS;
		}
		int bond = get(c, player.getUUID());
		Line line = bond >= WARM ? Line.GREET_WARM : bond <= COOL ? Line.GREET_COOL : null;
		if (line == null) {
			return InteractionResult.PASS;
		}
		Speech.say(c, line, player.getName().getString());
		player.sendSystemMessage(c.statusLine());
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Forgets the daily caps and hit times (a server stopping). */
	static void clear() {
		EARNED.clear();
		earnedDay = Long.MIN_VALUE;
		LAST_HIT.clear();
	}
}
