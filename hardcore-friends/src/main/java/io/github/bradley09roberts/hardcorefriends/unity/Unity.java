package io.github.bradley09roberts.hardcorefriends.unity;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.MoodPassives;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The team-wide Unity bond (0–1000). It grows through time spent together and teamwork, shrinks when a friend is
 * lost, and unlocks modest teamwork bonuses. None of the bonuses can prevent a player's death.
 */
public final class Unity {
	public static final int[] THRESHOLDS = {0, 100, 250, 500, 800};
	public static final String[] NAMES = {"Strangers", "Acquaintances", "Companions", "Close Friends", "Family"};
	public static final String[] BONUSES = {
		"No bonuses yet: spend time together and work as a team.",
		"Backpacks hold 18 stacks; friends share food with hungry players.",
		"Work rhythm (+15% work speed beside a friend); careful hands (20% less tool wear).",
		"Backpacks hold 27 stacks; friends heal slowly at camp; Scout's warnings make threats glow.",
		"Rally: below 3 hearts with 2+ friends near, you get 5 s of Regeneration I and they target your attacker (10 min cooldown)."
	};

	// Categories with daily caps
	public static final String TIME = "time";
	public static final String DELIVERY = "delivery";
	public static final String GIFT = "gift";
	public static final String HANDOFF = "handoff";
	public static final String DEFENCE = "defence";
	public static final String BUILD = "build";
	/** Two friends chatting (see {@link #chat}). */
	public static final String CHAT = "chat";
	/** Team spirit: the friends' average mood is great (see {@link #teamSpirit}). */
	public static final String SPIRIT = "spirit";
	/** Most Unity a great team mood adds in one in-game day, at +1 an in-game hour. */
	public static final int SPIRIT_DAILY_CAP = 12;

	private static final long RALLY_COOLDOWN = 20L * 60 * 10;
	/** One in-game hour, in ticks. */
	private static final long HOUR = 1000;

	private Unity() {
	}

	public static int level(int score) {
		int level = 0;
		for (int i = 0; i < THRESHOLDS.length; i++) {
			if (score >= THRESHOLDS[i]) {
				level = i;
			}
		}
		return level;
	}

	public static int level(MinecraftServer server) {
		return level(Camp.data(server).unity());
	}

	public static String levelName(int level) {
		return NAMES[Math.clamp(level, 0, NAMES.length - 1)];
	}

	/** Points still needed for the next level, or 0 at the top. */
	public static int toNextLevel(int score) {
		int level = level(score);
		return level >= THRESHOLDS.length - 1 ? 0 : THRESHOLDS[level + 1] - score;
	}

	/**
	 * Adds Unity for a teamwork event. {@code dailyCap} limits how much this category can earn per in-game day
	 * (0 or less means uncapped). Returns the amount actually added.
	 */
	public static int add(ServerLevel level, String category, int amount, int dailyCap) {
		if (amount <= 0) {
			return 0;
		}
		MinecraftServer server = level.getServer();
		CampData data = Camp.data(server);
		long day = Camp.day(level);
		if (dailyCap > 0) {
			int earned = data.unityEarnedToday(category, day);
			amount = Math.min(amount, dailyCap - earned);
			if (amount <= 0) {
				return 0;
			}
		}
		int before = data.unity();
		data.setUnity(before + amount);
		if (dailyCap > 0) {
			data.addUnityEarnedToday(category, day, amount);
		}
		int oldLevel = level(before);
		int newLevel = level(data.unity());
		if (newLevel > oldLevel) {
			onLevelUp(server, newLevel);
		}
		return data.unity() - before;
	}

	/** Two friends had a chat: +1, at most 30 a day. */
	public static void chat(ServerLevel level) {
		add(level, CHAT, 1, 30);
	}

	/**
	 * One in-game hour of team spirit: while the friends' average mood is great, the bond grows by 1, at most
	 * {@value #SPIRIT_DAILY_CAP} a day. A low mood costs nothing; it only slows the friends' work. Called hourly from
	 * {@link #tick}. Returns the amount added.
	 */
	public static int teamSpirit(MinecraftServer server) {
		if (MoodPassives.teamMood(Companions.all()) != Needs.Mood.GREAT) {
			return 0;
		}
		return add(server.overworld(), SPIRIT, 1, SPIRIT_DAILY_CAP);
	}

	public static void lose(MinecraftServer server, int amount) {
		CampData data = Camp.data(server);
		data.setUnity(data.unity() - amount);
		applyBackpackSizes();
	}

	private static void onLevelUp(MinecraftServer server, int newLevel) {
		applyBackpackSizes();
		List<CompanionEntity> friends = Companions.all();
		if (!friends.isEmpty()) {
			CompanionEntity speaker = friends.get(server.overworld().getRandom().nextInt(friends.size()));
			Speech.say(speaker, Line.UNITY_UP, levelName(newLevel));
		}
		Speech.announce(server, Component.literal("Unity bond: " + levelName(newLevel) + "! " + BONUSES[newLevel])
			.withStyle(ChatFormatting.GOLD));
	}

	public static int backpackSlots(MinecraftServer server) {
		int level = level(server);
		return level >= 3 ? 27 : level >= 1 ? 18 : 9;
	}

	public static void applyBackpackSizes() {
		for (CompanionEntity c : Companions.all()) {
			c.backpack().setCapacity(backpackSlots(c.level().getServer()));
		}
	}

	/** 1.15 when the bond is at least Companions and another friend is within 12 blocks, otherwise 1.0. */
	public static double workSpeed(CompanionEntity companion) {
		if (level(companion.level().getServer()) < 2) {
			return 1.0;
		}
		for (CompanionEntity other : Companions.all()) {
			if (other != companion && other.level() == companion.level() && other.distanceToSqr(companion) <= 144) {
				return 1.15;
			}
		}
		return 1.0;
	}

	/** 20% chance that a tool use costs no durability once the bond reaches Companions. */
	public static boolean carefulHands(CompanionEntity companion) {
		return level(companion.level().getServer()) >= 2 && companion.getRandom().nextFloat() < 0.2F;
	}

	public static boolean scoutMarksThreats(MinecraftServer server) {
		return level(server) >= 3;
	}

	/** Called every server tick. Handles time-together and team-spirit gains, camp healing and the Family rally. */
	public static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (tick % 1200 == 0) {
			timeTogether(server);
		}
		if (tick % HOUR == 0) {
			teamSpirit(server);
		}
		if (tick % 80 == 0 && level(server) >= 3) {
			campRegen(server);
		}
		if (tick % 10 == 0 && level(server) >= 4) {
			rally(server);
		}
	}

	private static void timeTogether(MinecraftServer server) {
		for (CompanionEntity c : Companions.all()) {
			ServerLevel level = (ServerLevel) c.level();
			if (level.getNearestPlayer(c, 24) != null) {
				add(level, TIME, 1, 120);
			}
		}
	}

	private static void campRegen(MinecraftServer server) {
		for (CompanionEntity c : Companions.all()) {
			ServerLevel level = (ServerLevel) c.level();
			Camp.center(level).ifPresent(center -> {
				if (Camp.horizontalDistSqr(c.blockPosition(), center) <= Math.pow(Camp.radius(Camp.data(server)), 2)
					&& c.getHealth() < c.getMaxHealth() && c.getTarget() == null) {
					c.heal(1.0F);
				}
			});
		}
	}

	private static void rally(MinecraftServer server) {
		CampData data = Camp.data(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || player.getHealth() >= 6.0F) {
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			if (now - data.lastRallyTime() < RALLY_COOLDOWN) {
				return;
			}
			List<CompanionEntity> near = Companions.near(level, player.getBoundingBox().inflate(16));
			if (near.size() < 2) {
				continue;
			}
			data.setLastRallyTime(now);
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
			LivingEntity attacker = player.getLastHurtByMob();
			for (CompanionEntity c : near) {
				if (attacker != null && attacker.isAlive() && !(attacker instanceof CompanionEntity) && attacker != player) {
					c.setTarget(attacker);
				}
				Speech.say(c, Line.PLAYER_HURT, player.getName().getString());
			}
			player.sendSystemMessage(Component.literal("Your friends rally around you!").withStyle(ChatFormatting.GOLD));
		}
	}

}
