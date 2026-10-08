package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;

import io.github.bradley09roberts.hardcorefriends.ai.role.sage.TeamPlan;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Sage's observations: contextual survival advice and team planning announcements. Throttles itself.
 *
 * <p>{@link #advice} picks the single most important piece of advice for a player's situation (badly hurt, hungry,
 * night without a bed or armour, darkness, a worn-out tool, phantoms, deep mining, full pockets, storms), then a camp
 * tip, then general Hardcore wisdom. {@link #tick} lets Sage speak up on their own: urgent survival advice to each
 * nearby player at most every three minutes, and the team focus whenever the camp's top need changes.
 */
public final class SageAdvisor {
	/** How urgent a piece of advice is. Sage only volunteers {@link #SURVIVAL} advice unprompted. */
	public enum Importance {
		SURVIVAL,
		CAMP,
		GENERAL
	}

	/** A piece of advice and how urgent it is. */
	public record Advice(String text, Importance importance) {
	}

	private static final int ADVICE_INTERVAL = 200;
	private static final int FOCUS_INTERVAL = 1200;
	private static final int PLAYER_COOLDOWN = 3600;
	private static final double LISTEN_RANGE = 32;
	private static final int PHANTOM_TICKS = 72000;

	private static final String[] WISDOM = {
		"Light up dark places, eat before you are hungry, and never dig straight down.",
		"Carry a water bucket. It puts out fire, breaks a long fall and blocks lava.",
		"Keep a shield in your off hand; it stops arrows and creeper blasts alike.",
		"Never mine the block under your feet. Lava and long drops hide below.",
		"A hissing creeper means run first and think later.",
		"Wear armour before you need it. In Hardcore there is no second try.",
		"Sleep when night falls. Fewer monsters, and no phantoms.",
		"Keep food in your hotbar so you can eat between blows.",
		"Wall off caves you are not exploring; monsters come up from the dark.",
		"Retreat at half health. There is no shame in living to see the morning.",
		"Put your spare valuables in a chest at camp. Lava and falls keep nothing.",
		"Bring a few blocks of cobblestone everywhere; a quick wall saves lives."
	};

	private static final Map<UUID, Long> LAST_ADVISED = new HashMap<>();
	private static boolean focusKnown;
	private static CampNeeds.@Nullable Need lastFocus;

	private SageAdvisor() {
	}

	// --------------------------------------------------------------- passive

	/** Called every tick for Sage; speaks at most every ten seconds. */
	public static void tick(CompanionEntity sage) {
		if (!(sage.level() instanceof ServerLevel level) || !sage.isAlive()) {
			return;
		}
		if (sage.tickCount % ADVICE_INTERVAL == 0) {
			adviseNearbyPlayers(sage, level);
		}
		if (sage.tickCount % FOCUS_INTERVAL == FOCUS_INTERVAL / 2) {
			announceFocusChange(sage, level);
		}
	}

	private static void adviseNearbyPlayers(CompanionEntity sage, ServerLevel level) {
		long now = level.getGameTime();
		LAST_ADVISED.values().removeIf(t -> now - t > PLAYER_COOLDOWN * 4L || now < t);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(sage) > LISTEN_RANGE * LISTEN_RANGE) {
				continue;
			}
			Long last = LAST_ADVISED.get(player.getUUID());
			if (last != null && now - last < PLAYER_COOLDOWN) {
				continue;
			}
			Advice advice = consult(player);
			if (advice.importance() != Importance.SURVIVAL) {
				continue;
			}
			if (Speech.say(sage, Line.ADVICE, addressed(player, advice.text()))) {
				LAST_ADVISED.put(player.getUUID(), now);
			}
			return; // one piece of advice per check; the line's own cooldown spaces out the rest
		}
	}

	private static void announceFocusChange(CompanionEntity sage, ServerLevel level) {
		CampNeeds.Need focus = CampNeeds.focus();
		if (focusKnown && focus == lastFocus) {
			return;
		}
		if (!focusKnown && focus == null) {
			// Nothing measured yet (or nothing needed): no news to share.
			focusKnown = true;
			lastFocus = null;
			return;
		}
		if (Speech.say(sage, Line.ADVICE, TeamPlan.announce(focus, level.getGameTime()))) {
			focusKnown = true;
			lastFocus = focus;
		}
	}

	/** Records that the team focus was just announced (by a store review), so it is not repeated. */
	public static void markFocusAnnounced(CampNeeds.@Nullable Need focus) {
		focusKnown = true;
		lastFocus = focus;
	}

	/** Forgets who was advised and which focus was announced (used when a new Sage arrives and by tests). */
	public static void reset() {
		LAST_ADVISED.clear();
		focusKnown = false;
		lastFocus = null;
	}

	private static String addressed(ServerPlayer player, String text) {
		if (text.isEmpty()) {
			return text;
		}
		return player.getName().getString() + ", " + text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
	}

	// ---------------------------------------------------------------- advice

	/** Sage's best piece of advice for this player right now, as plain sentences. Has no side effects. */
	public static String advice(ServerPlayer player) {
		return consult(player).text();
	}

	/** The most important advice for the player's situation, with its importance. Has no side effects. */
	public static Advice consult(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		boolean night = Camp.isNight(level);
		boolean dusk = Camp.isDusk(level);
		if (player.getHealth() <= 8) {
			return survival("You are badly hurt. Rest and eat before going on; in Hardcore there is no second chance.");
		}
		if (player.getFoodData().getFoodLevel() <= 6) {
			return survival("You are hungry. Eat something now: an empty stomach stops your healing and you cannot sprint away.");
		}
		if (dusk && player.getRespawnConfig() == null) {
			return survival("Night is coming and you have no bed. Craft one from three wool and three planks and sleep through the dark.");
		}
		if ((dusk || night) && player.getArmorValue() <= 0) {
			return survival("It is getting dark and you wear no armour. Stay inside the camp tonight; even leather helps.");
		}
		int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
		boolean enclosed = level.getBrightness(LightLayer.SKY, pos) < 7;
		if (blockLight < 7 && (night || enclosed)) {
			return survival("It is too dark where you stand. Monsters spawn in the dark; place torches as you go.");
		}
		ItemStack tool = player.getMainHandItem();
		if (tool.isDamageableItem() && tool.getMaxDamage() > 0 && tool.getMaxDamage() - tool.getDamageValue() < tool.getMaxDamage() * 0.1) {
			return survival("Your " + tool.getHoverName().getString() + " is nearly worn out. Replace it before it breaks at a bad moment.");
		}
		if (player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) > PHANTOM_TICKS) {
			return survival("You have not slept for three nights. Phantoms will hunt you after dark; sleep in a bed soon.");
		}
		if (pos.getY() < 0) {
			return survival("Diamonds are most common around y -58; watch for lava below y -54, and never dig straight down.");
		}
		if (freeInventorySlots(player) <= 2) {
			return survival("Your pockets are nearly full. Leave spare blocks in the supply chest so you do not walk past good loot.");
		}
		if (level.isThundering()) {
			return survival("A thunderstorm lets monsters roam even by day. Stay under a roof until it passes.");
		}
		Advice camp = campTip(level);
		if (camp != null) {
			return camp;
		}
		int index = (int) Math.floorMod(level.getGameTime() / 1200L + player.getUUID().hashCode(), (long) WISDOM.length);
		return new Advice(WISDOM[index], Importance.GENERAL);
	}

	private static Advice survival(String text) {
		return new Advice(text, Importance.SURVIVAL);
	}

	private static @Nullable Advice campTip(ServerLevel level) {
		if (Camp.data(level.getServer()).campPos().isEmpty()) {
			return new Advice("We have no camp yet. Stand where you want our home and use /friends camp set.", Importance.CAMP);
		}
		CampNeeds.Need focus = CampNeeds.focus();
		if (focus == null) {
			return null;
		}
		return new Advice("The camp is short of " + focus.label() + ". Bringing some to the supply chest helps everyone ("
			+ CampNeeds.summary() + ").", Importance.CAMP);
	}

	private static int freeInventorySlots(ServerPlayer player) {
		int free = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.isEmpty()) {
				free++;
			}
		}
		return free;
	}
}
