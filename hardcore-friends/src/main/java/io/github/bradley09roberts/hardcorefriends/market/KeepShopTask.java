package io.github.bradley09roberts.hardcorefriends.market;

import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * Minding the shop: by day the keeper stands behind their counter (or by the supply chest, for the camp stall) and
 * serves whoever comes. They go there when a player comes within {@value #CUSTOMERS} blocks of it (58, main work) and
 * stay while one is about; with nothing else at all to do they wait there anyway (12, just above idling). While serving
 * a player the job is all they do (95; the market's job filter keeps everything but their needs away), so the screen is
 * never closed by the keeper wandering off. Opening the shop is said once a day. The shop shuts at night.
 */
final class KeepShopTask extends TradeJob {
	static final String ID = "market.keep_shop";
	/** How near a player must come for the keeper to go and open up. */
	static final int CUSTOMERS = 24;
	private static final double SERVING = 95;
	private static final double CUSTOMER_COMING = 58;
	private static final double MINDING = 12;
	/** A visit with nobody about ends after this long. */
	private static final int QUIET_TICKS = 20 * 30;
	private static final String KEY = "market";

	private @Nullable Shop shop;
	private int quiet;
	private boolean opened;

	KeepShopTask() {
		super(ID, Set.of(), Trade.SHOPKEEPER, Trade.BAKER, Trade.BUTCHER, Trade.FISHMONGER, Trade.TAILOR, Trade.BLACKSMITH);
	}

	@Override
	public String describe() {
		Shop s = shop;
		return s == null ? "keeping shop" : "keeping " + s.described();
	}

	@Override
	double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		if (!FriendsConfig.get().playerShops) {
			return 0;
		}
		if (Shops.isTrading(c)) {
			return SERVING;
		}
		if (!workingHours(level)) {
			return 0;
		}
		Optional<Shop> s = Shop.of(level, c, h);
		if (s.isEmpty()) {
			return 0;
		}
		return playerNear(level, s.get().counter(), CUSTOMERS) != null ? CUSTOMER_COMING : MINDING;
	}

	/** The nearest player (not watching as a spectator) within {@code range} of a spot, or null. */
	static @Nullable ServerPlayer playerNear(ServerLevel level, BlockPos spot, double range) {
		ServerPlayer best = null;
		double bestDist = range * range;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || !p.isAlive()) {
				continue;
			}
			double d = p.distanceToSqr(Vec3.atCenterOf(spot));
			if (d <= bestDist) {
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		shop = Shop.of(level, c, holding(c)).orElse(null);
		quiet = 0;
		opened = false;
		return shop != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Shop s = shop;
		boolean serving = Shops.isTrading(c);
		if (s == null || !serving && !workingHours(level)) {
			return TaskStatus.SUCCESS;
		}
		switch (Stores.stand(c, s.counter(), 0.8)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return serving ? TaskStatus.RUNNING : TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		if (!opened) {
			opened = true;
			sayOpen(c, level, s);
		}
		Optional<ServerPlayer> customer = Shops.customerOf(c);
		ServerPlayer near = customer.orElseGet(() -> playerNear(level, s.counter(), 8));
		if (near != null) {
			c.getLookControl().setLookAt(near);
		} else if (s.customer() != null) {
			c.getLookControl().setLookAt(Vec3.atCenterOf(s.customer()).add(0, 1, 0));
		}
		if (serving || playerNear(level, s.counter(), CUSTOMERS) != null) {
			quiet = 0;
		} else if (++quiet > QUIET_TICKS) {
			return TaskStatus.SUCCESS;
		}
		return TaskStatus.RUNNING;
	}

	/** "Open for trade at the bakery!", once an in-game day. */
	private static void sayOpen(CompanionEntity c, ServerLevel level, Shop s) {
		CompoundTag tag = c.extra().getCompoundOrEmpty(KEY).copy();
		long day = Camp.day(level);
		if (tag.getLongOr("opened", -1L) == day) {
			return;
		}
		tag.putLong("opened", day);
		c.extra().put(KEY, tag);
		Speech.say(c, Line.SHOP_OPEN, s.described());
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		shop = null;
	}

	@Override
	public int failureCooldown() {
		// A counter that cannot be reached is not tried again at once. (While serving, the job never fails: a keeper
		// whose run times out stays put at the counter, as nothing else is allowed meanwhile.)
		return 20 * 30;
	}

	@Override
	public int successCooldown() {
		return 20 * 20;
	}

	@Override
	public int maxTicks() {
		return 20 * 600;
	}
}
