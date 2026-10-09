package io.github.bradley09roberts.hardcorefriends.people;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * A date: once a day, in the late afternoon or evening (time of day 9000 to 12500), a couple going out, engaged or
 * married spend a little time together inside the camp: watching the sunset from the highest ground about, sitting by
 * the lit campfire, or an evening walk side by side. One of them asks; the other, invited, stops what they are doing
 * unless it is pressing. Hearts float up, their fun and company needs fill, and romance grows. Changes no block.
 *
 * <p>At the end of a date a couple who have been going out for two days or more, been on a few dates and are deeply
 * in love (friendship and romance 70 or more) may get engaged: one proposes, the other says yes, and the wedding is
 * set for the next day ({@link Weddings}).
 */
final class DateTask implements CompanionTask {
	static final String ID = People.JOB_PREFIX + "date";
	/** The asker: high among pastimes, below urgent upkeep and most real work in hand. */
	private static final double ASK = 66;
	/** The one asked stops for it unless they are busy with something pressing. */
	private static final double ASKED = 90;
	private static final long FROM = 9000;
	private static final long UNTIL = 12500;
	private static final double PARTNER_RANGE = 32;
	private static final int ENJOY_TICKS = 20 * 20;
	private static final int GATHER_TICKS = 20 * 30;

	enum Kind { SUNSET, FIRESIDE, WALK }

	/** A date in hand: who asked, who was asked, what and where. */
	private static final class Outing {
		final UUID asker;
		final UUID asked;
		final Kind kind;
		final BlockPos spot;
		final long until;
		long enjoyFrom = -1;
		boolean over;

		Outing(UUID asker, UUID asked, Kind kind, BlockPos spot, long until) {
			this.asker = asker;
			this.asked = asked;
			this.kind = kind;
			this.spot = spot;
			this.until = until;
		}
	}

	/** Dates under way, by both people on them. */
	private static final Map<UUID, Outing> OUTINGS = new HashMap<>();

	private @Nullable Outing outing;
	private boolean arrived;
	private int ticks;

	static void clear() {
		OUTINGS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Outing o = outing;
		if (o == null) {
			return "on a date";
		}
		return switch (o.kind) {
			case SUNSET -> "watching the sunset with someone special";
			case FIRESIDE -> "sitting by the fire with someone special";
			case WALK -> "on an evening walk with someone special";
		};
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.isChild() || !(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		Outing mine = OUTINGS.get(c.getUUID());
		long now = level.getGameTime();
		if (mine != null) {
			if (mine.over || now > mine.until) {
				OUTINGS.remove(c.getUUID(), mine);
				return 0;
			}
			return mine.asked.equals(c.getUUID()) ? ASKED : 0;
		}
		if (!FriendsConfig.get().romance) {
			return 0;
		}
		long time = Camp.timeOfDay(level);
		if (time < FROM || time >= UNTIL || Camp.isNightTime(level) || !Spots.inCamp(c, c.blockPosition())) {
			return 0;
		}
		return partnerFor(c, level) != null ? ASK : 0;
	}

	/** Their sweetheart, if both are free for a date today: loaded nearby, at the camp, not dated today, not busy. */
	private static @Nullable CompanionEntity partnerFor(CompanionEntity c, ServerLevel level) {
		MinecraftServer server = level.getServer();
		PeopleData data = PeopleData.get(server);
		PeopleData.Bond bond = data.partnerBond(c.getUUID());
		if (bond == null || bond.lastDateDay == PeopleEvents.day(server)) {
			return null;
		}
		CompanionEntity partner = PeopleEvents.loaded(server, bond.other(c.getUUID()));
		if (partner == null || partner.level() != level || partner.distanceToSqr(c) > PARTNER_RANGE * PARTNER_RANGE
			|| OUTINGS.containsKey(partner.getUUID()) || !Relationships.free(partner) || partner.isChild()
			|| !Spots.inCamp(partner, partner.blockPosition())) {
			return null;
		}
		return partner;
	}

	@Override
	public boolean start(CompanionEntity c) {
		arrived = false;
		ticks = 0;
		outing = null;
		ServerLevel level = (ServerLevel) c.level();
		Outing invited = OUTINGS.get(c.getUUID());
		if (invited != null) {
			outing = invited.over ? null : invited;
			return outing != null;
		}
		CompanionEntity partner = partnerFor(c, level);
		if (partner == null) {
			return false;
		}
		Outing o = plan(c, level, partner);
		if (o == null) {
			return false;
		}
		OUTINGS.put(c.getUUID(), o);
		OUTINGS.put(partner.getUUID(), o);
		outing = o;
		Speech.say(c, Line.DATE, partner.displayName());
		return true;
	}

	/** Where to go: the sunset late on, the fire if it burns, otherwise a walk. */
	private static @Nullable Outing plan(CompanionEntity c, ServerLevel level, CompanionEntity partner) {
		long until = level.getGameTime() + (long) 20 * 90;
		if (Camp.timeOfDay(level) >= 11000) {
			BlockPos high = highGround(c, level);
			if (high != null) {
				return new Outing(c.getUUID(), partner.getUUID(), Kind.SUNSET, high, until);
			}
		}
		BlockPos fire = Spots.litCampfire(c);
		if (fire != null) {
			BlockPos seat = Spots.standable(level, fire.offset(2, 0, 0));
			if (seat != null) {
				return new Outing(c.getUUID(), partner.getUUID(), Kind.FIRESIDE, seat, until);
			}
		}
		for (int attempt = 0; attempt < 6; attempt++) {
			BlockPos p = Spots.standable(level, c.homePos().offset(c.getRandom().nextInt(17) - 8, 0, c.getRandom().nextInt(17) - 8));
			if (p != null && Spots.inCamp(c, p)) {
				return new Outing(c.getUUID(), partner.getUUID(), Kind.WALK, p, until);
			}
		}
		return null;
	}

	/** The highest place to stand in the camp near the friend, sampled every third column: the best view west. */
	private static @Nullable BlockPos highGround(CompanionEntity c, ServerLevel level) {
		BlockPos here = c.blockPosition();
		BlockPos best = null;
		for (int dx = -12; dx <= 12; dx += 3) {
			for (int dz = -12; dz <= 12; dz += 3) {
				int x = here.getX() + dx;
				int z = here.getZ() + dz;
				BlockPos column = new BlockPos(x, here.getY(), z);
				if (!level.isLoaded(column) || !Spots.inCamp(c, column)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos p = new BlockPos(x, top, z);
				if (Math.abs(top - here.getY()) <= 10 && Spots.isStandable(level, p) && (best == null || p.getY() > best.getY())) {
					best = p;
				}
			}
		}
		return best;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Outing o = outing;
		if (o == null || o.over || !(c.level() instanceof ServerLevel level)) {
			return TaskStatus.SUCCESS;
		}
		boolean asker = o.asker.equals(c.getUUID());
		CompanionEntity partner = PeopleEvents.loaded(level.getServer(), asker ? o.asked : o.asker);
		long now = level.getGameTime();
		if (partner == null || partner.level() != level || now > o.until || Camp.isNightTime(level)) {
			o.over = true;
			return TaskStatus.SUCCESS;
		}
		ticks++;
		// The one asked walks beside the other; the asker heads for the spot.
		BlockPos target = asker ? o.spot : (o.kind == Kind.WALK ? partner.blockPosition() : o.spot.east());
		if (!arrived) {
			if (c.actions().walkTo(target, asker ? 0.8 : 1.6)) {
				arrived = true;
			} else if (c.actions().isStuck() || ticks > GATHER_TICKS) {
				if (c.distanceToSqr(partner) > 6 * 6) {
					o.over = true;
					return TaskStatus.FAILURE;
				}
				arrived = true;
			}
			return TaskStatus.RUNNING;
		}
		if (o.kind == Kind.WALK && !asker && c.distanceToSqr(partner) > 2.5 * 2.5) {
			c.actions().walkToEntity(partner, 1.8); // keep alongside
		} else {
			c.actions().stopWalking();
		}
		lookAt(c, partner, o.kind);
		if (asker && o.enjoyFrom < 0 && c.distanceToSqr(partner) <= 4 * 4) {
			o.enjoyFrom = now;
		}
		if (o.enjoyFrom >= 0 && (now - o.enjoyFrom) % 40 == 0) {
			level.sendParticles(ParticleTypes.HEART, c.getX(), c.getY() + c.getBbHeight() + 0.3, c.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
		}
		if (asker && o.enjoyFrom >= 0 && now - o.enjoyFrom >= ENJOY_TICKS) {
			finish(level, c, partner);
			o.over = true;
			return TaskStatus.SUCCESS;
		}
		return TaskStatus.RUNNING;
	}

	private static void lookAt(CompanionEntity c, CompanionEntity partner, Kind kind) {
		if (kind == Kind.SUNSET) {
			// The sun sets in the west.
			c.getLookControl().setLookAt(c.getX() - 30, c.getEyeY() + 3, c.getZ());
		} else {
			c.getLookControl().setLookAt(partner);
		}
	}

	/** A lovely time had by both; and perhaps, a question. */
	private static void finish(ServerLevel level, CompanionEntity c, CompanionEntity partner) {
		MinecraftServer server = level.getServer();
		PeopleData data = PeopleData.get(server);
		PeopleData.Bond bond = data.bondIf(c.getUUID(), partner.getUUID());
		if (bond == null || !bond.status.together()) {
			return;
		}
		for (CompanionEntity one : new CompanionEntity[] {c, partner}) {
			one.needs().add(Needs.Need.FUN, 30);
			one.needs().add(Needs.Need.SOCIAL, 30);
		}
		Relationships.change(server, c, partner, 3, 8);
		bond.dates++;
		bond.lastDateDay = PeopleEvents.day(server);
		data.setDirty();
		long clock = PeopleEvents.clock(server);
		if (bond.status == PeopleData.Status.DATING && clock - bond.since >= 2 * 24000L && bond.dates >= 3
			&& bond.friendship >= 70 && bond.romance >= 70 && FriendsConfig.get().romance) {
			propose(server, data, bond, c, partner, clock);
		}
	}

	/** One asks, the other says yes, and the wedding is the next day. */
	private static void propose(MinecraftServer server, PeopleData data, PeopleData.Bond bond, CompanionEntity asker, CompanionEntity other,
			long clock) {
		Speech.say(asker, Line.PROPOSE, other.displayName());
		PeopleEvents.later(server, 50, () -> {
			if (other.isAlive() && asker.isAlive()) {
				Speech.say(other, Line.ACCEPT, asker.displayName());
			}
		});
		bond.status = PeopleData.Status.ENGAGED;
		bond.since = clock;
		bond.weddingDay = clock / 24000L + 1;
		data.setDirty();
		Relationships.announce(server, asker.displayName() + " and " + other.displayName()
			+ " are engaged! The wedding is tomorrow morning at the camp, and everyone is invited.");
	}

	@Override
	public void stop(CompanionEntity c) {
		Outing o = outing;
		if (o != null) {
			o.over = true; // either of them leaving ends the date for both
			OUTINGS.remove(c.getUUID(), o);
		}
		outing = null;
		arrived = false;
		ticks = 0;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int successCooldown() {
		return 20 * 60;
	}

	@Override
	public int maxTicks() {
		return 20 * 100;
	}
}
