package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * Trading with players at the shops. A player right-clicks a shopkeeper standing at their counter by day and the
 * trading screen opens ({@link ShopTrade}). Anyone may trade, trusted with the camp or not: buying and selling at a
 * shop changes nothing of the camp's but its stock and takings, at fair prices, so this runs before the camp's trust
 * check (which would otherwise turn away an untrusted player holding their emeralds). Sneaking, or handing food to a
 * hungry or hurt keeper, goes on to the usual handling. A keeper serves one player at a time; from dusk, in a dark
 * storm, away from the counter, fighting or asleep, there is no shop.
 */
final class Shops {
	/** How close to their counter a keeper must be to open the shop. */
	static final double AT_COUNTER = 3;
	private static final String FOR_THE_NIGHT = "for the night";
	private static final String FOR_THE_STORM = "till the storm passes";
	private static final Map<UUID, ShopTrade> OPEN = new HashMap<>();

	private Shops() {
	}

	/** A right-click on a friend (registered first among the right-click handlers). */
	static InteractionResult interact(CompanionEntity c, ServerPlayer player, InteractionHand hand) {
		if (player.isShiftKeyDown() || !FriendsConfig.get().playerShops || !FriendsConfig.get().villageTrades
			|| !(c.level() instanceof ServerLevel level) || c.isChild() || !c.isTeamMember() || c.mode() != CompanionMode.WORK) {
			return InteractionResult.PASS;
		}
		MarketData.Holding h = Market.holding(level.getServer(), c);
		Optional<Shop> found = Shop.of(level, c, h);
		if (found.isEmpty()) {
			return InteractionResult.PASS;
		}
		Shop shop = found.get();
		if (c.position().distanceToSqr(Vec3.atBottomCenterOf(shop.counter())) > AT_COUNTER * AT_COUNTER) {
			return InteractionResult.PASS; // not at the shop: the usual hello and status
		}
		ItemStack held = player.getItemInHand(hand);
		if (wantsToEat(c, held)) {
			return InteractionResult.PASS; // a hungry or hurt keeper takes the food first
		}
		String shut = shut(level);
		if (shut != null) {
			Speech.tell(player, c, "Sorry, " + player.getName().getString() + ", the shop's shut " + shut + ". "
				+ (shut.equals(FOR_THE_STORM) ? "Come back when it clears!" : "Come back in the morning!"));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (c.getTarget() != null || c.isAsleep() || c.isRetreating()) {
			return InteractionResult.PASS;
		}
		ShopTrade busy = OPEN.get(c.getUUID());
		if (busy != null && busy.isOpen()) {
			if (busy.customer() == player) {
				return InteractionResult.SUCCESS_SERVER;
			}
			Speech.tell(player, c, "Just a moment, " + player.getName().getString() + ". I'm serving "
				+ busy.customer().getName().getString() + ".");
			return InteractionResult.SUCCESS_SERVER;
		}
		ShopTrade trade = ShopTrade.open(c, player, shop);
		if (trade == null) {
			Speech.tell(player, c, "Sorry, " + player.getName().getString() + ", the shelves are bare and there's nothing we need today. "
				+ "Come back later!");
			return InteractionResult.SUCCESS_SERVER;
		}
		OPEN.put(c.getUUID(), trade);
		c.getNavigation().stop();
		c.getLookControl().setLookAt(player);
		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * Why the shops are shut just now ("for the night", "till the storm passes"), or null while they are open. They keep
	 * the trades' working hours, so no customer holds a keeper at the counter past dusk, when the friends head home, or
	 * through a storm dark enough for monsters (the market's job filter keeps a serving keeper from going home).
	 */
	static @Nullable String shut(ServerLevel level) {
		if (TradeJob.workingHours(level)) {
			return null;
		}
		return Camp.isNightTime(level) || Camp.isDusk(level) ? FOR_THE_NIGHT : FOR_THE_STORM;
	}

	/** True while this friend is serving a player at their counter. */
	static boolean isTrading(CompanionEntity c) {
		ShopTrade t = OPEN.get(c.getUUID());
		return t != null && t.isOpen();
	}

	/** The player this keeper is serving, if any. */
	static Optional<ServerPlayer> customerOf(CompanionEntity c) {
		ShopTrade t = OPEN.get(c.getUUID());
		return t != null && t.isOpen() ? Optional.of(t.customer()) : Optional.empty();
	}

	/** A session has closed. */
	static void forget(ShopTrade trade) {
		OPEN.remove(trade.keeper().getUUID(), trade);
	}

	/**
	 * Closes every open trading screen, putting everything held back where it belongs: the server is stopping, so the
	 * chests are filled before the world is saved.
	 */
	static void closeAll() {
		for (ShopTrade t : new ArrayList<>(OPEN.values())) {
			ServerPlayer p = t.customer();
			if (p.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu) {
				p.closeContainer();
			}
			t.close();
		}
		OPEN.clear();
	}

	/** Food a keeper would rather eat now than trade over (the friend's own rule for food handed to them). */
	private static boolean wantsToEat(CompanionEntity c, ItemStack held) {
		return CompanionEntity.isEdible(held)
			&& (c.getHealth() < c.getMaxHealth() || c.needs().get(Needs.Need.HUNGER) < CompanionEntity.EATS_HANDED_FOOD_BELOW);
	}
}
