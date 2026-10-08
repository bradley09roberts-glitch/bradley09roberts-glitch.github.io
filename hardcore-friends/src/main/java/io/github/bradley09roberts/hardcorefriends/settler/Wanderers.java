package io.github.bradley09roberts.hardcorefriends.settler;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * Travellers who visit the camp. Once the camp is at least a Camp (stage 1), every two to four in-game days, in the
 * morning, while a player is at or near the camp, a stranger may walk in from the wilds to the camp's edge and stay
 * for a day, hoping to be asked to join. If nobody asks, they set off again the next day and are gone once out of
 * sight (see {@link SettlerEvents}). Only one traveller visits at a time, and none come while the team is full.
 */
final class Wanderers {
	private static final int INTERVAL = 200;
	private static final long DAY = 24000L;
	/** How long a traveller stays. */
	private static final long VISIT = DAY;
	/** Where they first appear, beyond the camp's edge. */
	private static final int APPROACH = 24;
	/** A player must be within this distance of the camp's edge for a traveller to come. */
	private static final int WATCHED = 64;

	private Wanderers() {
	}

	/** Called every server tick; works every {@value #INTERVAL} ticks. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 71) {
			return;
		}
		FriendsConfig cfg = FriendsConfig.get();
		if (!cfg.wanderingVisitors || !cfg.allowSettlers) {
			return;
		}
		CampData camp = Camp.data(server);
		if (camp.campPos().isEmpty() || camp.stage() < 1) {
			return;
		}
		ServerLevel level = campLevel(server, camp);
		if (level == null || level.dimensionType().hasCeiling() || !level.dimensionType().hasSkyLight()) {
			return; // travellers walk in under the open sky, not onto the Nether's roof
		}
		SettlerData data = SettlerData.get(server);
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		if (data.nextWandererAt() < 0) {
			data.setNextWandererAt(now + nextGap(random)); // the first traveller comes a few days after the camp grows
			return;
		}
		if (now < data.nextWandererAt() || now < data.wandererUntil()) {
			return;
		}
		long time = Camp.timeOfDay(level);
		if (Camp.isNight(level) || time < 500 || time > 9000) {
			return; // they arrive in the morning, with the day ahead of them
		}
		if (data.aliveCount() >= cfg.maxSettlers) {
			data.setNextWandererAt(now + nextGap(random)); // the team is full: nobody comes this time
			return;
		}
		BlockPos centre = camp.campPos().get();
		int radius = Camp.radius(camp);
		if (!watched(level, centre, radius)) {
			return; // try again shortly: someone should be there to meet them
		}
		for (int attempt = 0; attempt < 4; attempt++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			double dx = Mth.cos(angle);
			double dz = Mth.sin(angle);
			BlockPos edge = centre.offset((int) Math.round(dx * (radius + 3)), 0, (int) Math.round(dz * (radius + 3)));
			BlockPos from = centre.offset((int) Math.round(dx * (radius + APPROACH)), 0, (int) Math.round(dz * (radius + APPROACH)));
			BlockPos home = Strangers.surfaceSpot(level, edge, 4);
			BlockPos spot = Strangers.surfaceSpot(level, from, 6);
			if (home == null || spot == null || !level.isPositionEntityTicking(spot) || !level.isPositionEntityTicking(home)) {
				continue;
			}
			CompanionEntity c = Strangers.spawn(level, spot, Personas.Origin.ROAD, random, null);
			if (c == null) {
				return;
			}
			c.setHomePos(home);
			Strangers.state(c).putLong(Strangers.LEAVE_AT, now + VISIT);
			data.setWandererUntil(now + VISIT + DAY / 4);
			data.setNextWandererAt(now + nextGap(random));
			return;
		}
	}

	/** Two to four days. */
	private static long nextGap(RandomSource random) {
		return 2 * DAY + random.nextInt((int) (2 * DAY) + 1);
	}

	private static @Nullable ServerLevel campLevel(MinecraftServer server, CampData camp) {
		for (ServerLevel level : server.getAllLevels()) {
			if (Camp.isCampLevel(level, camp)) {
				return level;
			}
		}
		return null;
	}

	private static boolean watched(ServerLevel level, BlockPos centre, int radius) {
		double reach = radius + WATCHED;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && Camp.horizontalDistSqr(player.blockPosition(), centre) <= reach * reach) {
				return true;
			}
		}
		return false;
	}
}
