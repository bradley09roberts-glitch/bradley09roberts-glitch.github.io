package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.ai.goal.FollowLeaderGoal;
import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Keeps an eye on every friend (every mode, team members and strangers alike) for getting stuck, and helps them out,
 * a step at a time:
 *
 * <ol>
 * <li><b>Stuck on the spot</b> while trying to get somewhere: a hop and a step aside, and the spot they were stuck on
 * costs more for a while so the next path goes round it (up to three times).</li>
 * <li><b>Under water and short of air:</b> straight up for air, towards the nearest open surface (a reflex, every
 * tick). <b>Stuck in the water, or in a current:</b> swim to the best shore ({@link ShorePlan}); in a hole with steep
 * sides, cut a step out just above the water ({@link StairPlan}).</li>
 * <li><b>Shut in</b> (a pit, a hole, a pocket they cannot walk out of): a carried block underfoot to get up a step and
 * taken back after ({@link StepPlan}), or a staircase dug through natural ground ({@link StairPlan}).</li>
 * <li><b>Lost in a cave</b> (underground, heading for somewhere that is not, and getting no nearer): walk the way found
 * to the nearest open sky ({@link CaveExitPlan}), or dig up to it.</li>
 * <li><b>Last resort:</b> a friend on the team still in trouble after {@value #RESCUE_AFTER} ticks (two in-game
 * minutes), trapped underground hurt or starving, or about to drown, is brought home (or to their leader) and
 * everyone is told. Never out of a fight, never out of a mine job that is going fine, never one told to stay put, and
 * not at all when {@code rescueStuckFriends} is off.</li>
 * </ol>
 *
 * <p>Watching costs a few comparisons every {@value #CHECK} ticks per friend; the searches for a way out run only for a
 * friend in trouble, at most every {@value #PROBE_GAP} ticks.
 */
public final class Wayfinder {
	static final int CHECK = 10;
	private static final double STILL_RADIUS = 1.5;
	private static final double PATCH_RADIUS = 6;
	/** Closer than this to where they are going counts as there. */
	private static final double ARRIVED = 2.5;
	static final int NUDGE_AFTER = 60;
	private static final int NUDGE_GAP = 60;
	private static final int MAX_NUDGES = 3;
	/** Still this long (the nudges did not help): look for a way out. */
	private static final int LOOK_AFTER = 240;
	/** Wanting to go further, but not leaving a patch {@value #PATCH_RADIUS} blocks across, this long: look too. */
	private static final int CONFINED_AFTER = 400;
	/** Underground, heading for somewhere that is not, getting no nearer, this long: lost. */
	private static final int LOST_AFTER = 300;
	/** Stuck in the water this long: swim for the shore. */
	private static final int WATER_AFTER = 40;
	/** Not wanting to go anywhere for this long ends any trouble (a short gap between two jobs only pauses the count). */
	private static final int IDLE_CALMS = 200;
	/** Carried this much further from where they want to go by a current, between two looks: swim out of it. */
	private static final double CARRIED_OFF = 3;
	/** Two in-game minutes in trouble: brought home. */
	public static final int RESCUE_AFTER = 2400;
	/** Trapped underground, hurt or starving, this long: brought home. */
	private static final int WEAK_RESCUE_AFTER = 600;
	/** Gasping for air this long, and almost out: brought home. */
	private static final int DROWN_RESCUE_AFTER = 100;
	private static final int RESCUE_GAP = 2400;
	private static final int BLOCKED_FOR = 600;
	private static final int PLAN_COOLDOWN = 300;
	static final int PROBE_GAP = 200;
	/** A plan that has not been run this long (something else has the friend's legs) is dropped. */
	private static final int PLAN_STALE = 200;
	/** This many walks given up lately, outside the camp, make the friend look whether they are hemmed in. */
	private static final int GIVEN_UP_WALKS = 3;
	private static final int GIVEN_UP_WINDOW = 2400;
	private static final int HEMMED_CHECK_GAP = 600;

	/** What kind of trouble a friend was in, for the news when they are brought home. */
	enum Trouble {
		STUCK("got stuck"),
		WATER("got stuck in the water"),
		CAVE("got lost in a cave"),
		TRAPPED("was trapped underground"),
		DROWNING("nearly drowned");

		final String words;

		Trouble(String words) {
			this.words = words;
		}
	}

	/** Everything watched for one friend. */
	static final class Watch {
		@Nullable BlockPos dest;
		boolean destUnderground;
		@Nullable Vec3 stillAt;
		int still;
		int nudges;
		long nextNudge;
		boolean saidStuck;
		@Nullable Vec3 patchAt;
		int confined;
		@Nullable BlockPos windowDest;
		double windowDistance;
		long windowAt;
		int lost;
		long troubleSince = -1;
		int idle;
		int weakUnderground;
		int gasping;
		@Nullable BlockPos airAt;
		@Nullable Plan plan;
		long planTicked;
		final Map<Plan.Kind, Long> cooldown = new EnumMap<>(Plan.Kind.class);
		final LongOpenHashSet blocked = new LongOpenHashSet();
		long blockedUntil;
		final long[] givenUp = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
		int givenUpNext;
		long nextProbe;
		long nextHemmedCheck;
		long lastRescue = Long.MIN_VALUE / 2;
		@Nullable BlockPos unreachable;
		long unreachableUntil;

		void calm(Vec3 pos) {
			still = 0;
			confined = 0;
			lost = 0;
			nudges = 0;
			stillAt = pos;
			patchAt = pos;
			troubleSince = -1;
			weakUnderground = 0;
		}
	}

	private static final Map<CompanionEntity, Watch> WATCH = new WeakHashMap<>();

	private Wayfinder() {
	}

	private static Watch watch(CompanionEntity c) {
		return WATCH.computeIfAbsent(c, k -> new Watch());
	}

	// ------------------------------------------------------------------ for others

	/** Ticks this friend has been in trouble (stuck, lost, in a plan to get out), or 0 when they are fine. */
	public static int troubleTicks(CompanionEntity c) {
		Watch w = WATCH.get(c);
		if (w == null || w.troubleSince < 0) {
			return 0;
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0, c.level().getGameTime() - w.troubleSince));
	}

	/** True while the friend is busy getting themselves out of trouble (swimming out, walking out, digging out). */
	public static boolean gettingOut(CompanionEntity c) {
		Watch w = WATCH.get(c);
		return w != null && w.plan != null;
	}

	/** The spots this friend got stuck on lately (paths go round them), or null. */
	static @Nullable LongSet blockedSpots(CompanionEntity c) {
		Watch w = WATCH.get(c);
		if (w == null || w.blocked.isEmpty() || c.level().getGameTime() >= w.blockedUntil) {
			return null;
		}
		return w.blocked;
	}

	/**
	 * Told by {@code Actions} when a job's walk is given up as stuck: several of those outside the camp make the friend
	 * look whether they are hemmed in (the bottom of a ravine, a pit too wide to be "shut in").
	 */
	public static void walkGivenUp(CompanionEntity c) {
		Watch w = watch(c);
		w.givenUp[w.givenUpNext] = c.level().getGameTime();
		w.givenUpNext = (w.givenUpNext + 1) % w.givenUp.length;
	}

	/** A plan got somewhere (a step dug, a stretch of the way walked): the clock towards being brought home starts again. */
	static void progress(CompanionEntity c) {
		Watch w = WATCH.get(c);
		if (w != null && w.troubleSince >= 0) {
			w.troubleSince = c.level().getGameTime();
		}
	}

	/** What the friend is doing about being stuck, for {@code /friends senses}; null when they are not in trouble. */
	static @Nullable String describe(CompanionEntity c) {
		Watch w = WATCH.get(c);
		if (w == null) {
			return null;
		}
		if (w.plan != null) {
			String doing = switch (w.plan.kind()) {
				case SHORE -> "swimming for the shore";
				case CAVE_EXIT -> "finding the way out of a cave";
				case STEP -> "stepping up out of a hole";
				case STAIR -> "digging a way out";
			};
			return doing + " (" + troubleTicks(c) / 20 + " s)";
		}
		if (w.gasping > 0) {
			return "coming up for air";
		}
		if (w.troubleSince < 0) {
			return null;
		}
		String what = w.lost >= LOST_AFTER ? "lost underground" : w.confined >= CONFINED_AFTER ? "hemmed in" : "stuck";
		return what + " (" + troubleTicks(c) / 20 + " s)";
	}

	/** Forgets everything (a server starting or stopping). */
	static void clear() {
		WATCH.clear();
	}

	// ---------------------------------------------------------------------- tick

	/** A {@code CompanionEvents.TICK} listener: the air reflex every tick, the rest every {@value #CHECK} ticks. */
	static void tick(CompanionEntity c, ServerLevel level) {
		if (!c.isAlive() || c.isRemoved()) {
			return;
		}
		Watch w = watch(c);
		if (w.gasping > 0) {
			breathe(c, level, w);
		}
		if (c.tickCount % CHECK != 0) {
			return;
		}
		long now = level.getGameTime();
		Senses.Reading r = Senses.read(c);
		air(c, level, w, r);
		if (w.plan != null) {
			if (now - w.planTicked > PLAN_STALE) {
				endPlan(c, w, false, false);
			} else {
				countWeakness(c, w, r);
				rescueIfDue(c, level, w, r, now);
				return;
			}
		}
		if (busy(c)) {
			return; // fighting, falling back, running away, carried or asleep: neither stuck nor free
		}
		BlockPos dest = destination(c);
		track(c, level, w, r, dest, now);
		countWeakness(c, w, r);
		if (dest != null) {
			escalate(c, level, w, r, now);
		}
		rescueIfDue(c, level, w, r, now);
	}

	/** Busy with something that moves them on purpose (or keeps them still on purpose). */
	private static boolean busy(CompanionEntity c) {
		return c.getTarget() != null || c.isRetreating() || Sprint.fleeing(c) || c.isPassenger() || c.isAsleep()
			|| c.isSleeping() || c.isLeashed();
	}

	/**
	 * Where the friend is trying to get to just now: a job's walk, their leader (following, and more than five blocks
	 * behind), or wherever their path leads; null when they are not trying to go anywhere.
	 */
	static @Nullable BlockPos destination(CompanionEntity c) {
		BlockPos walk = c.actions().walkIntent();
		if (walk != null) {
			return walk;
		}
		if (c.mode() == CompanionMode.FOLLOW) {
			ServerPlayer leader = c.leader();
			return FollowLeaderGoal.canFollow(c, leader) && c.distanceToSqr(leader) > 25 ? leader.blockPosition() : null;
		}
		PathNavigation nav = c.getNavigation();
		return nav.isInProgress() ? nav.getTargetPos() : null;
	}

	private static void track(CompanionEntity c, ServerLevel level, Watch w, Senses.Reading r, @Nullable BlockPos dest, long now) {
		Vec3 pos = c.position();
		if (dest == null) {
			// Between two jobs, or done walking: the counts wait. Wanting to go nowhere for a while ends the trouble.
			w.idle += CHECK;
			if (w.idle >= IDLE_CALMS && w.gasping == 0 && w.plan == null) {
				w.saidStuck = false;
				w.calm(pos);
				w.dest = null;
			}
			return;
		}
		w.idle = 0;
		if (w.dest == null || w.dest.distSqr(dest) > 16) {
			w.dest = dest.immutable();
			w.destUnderground = Terrain.undergroundTarget(level, dest);
		}
		double toDest = pos.distanceTo(Vec3.atBottomCenterOf(dest));
		boolean there = toDest <= ARRIVED;
		boolean givenUp = w.unreachable != null && now < w.unreachableUntil && w.unreachable.distSqr(dest) <= 16;
		if (w.stillAt == null || pos.distanceToSqr(w.stillAt) > STILL_RADIUS * STILL_RADIUS) {
			if (w.saidStuck) {
				Speech.say(c, Line.UNSTUCK);
				w.saidStuck = false;
			}
			w.stillAt = pos;
			w.still = 0;
			w.nudges = 0;
		} else if (!there && !givenUp) {
			w.still += CHECK;
		}
		if (w.patchAt == null || pos.distanceToSqr(w.patchAt) > PATCH_RADIUS * PATCH_RADIUS) {
			w.patchAt = pos;
			w.confined = 0;
		} else if (!there && !givenUp && dest.distToCenterSqr(w.patchAt) > (PATCH_RADIUS + 2) * (PATCH_RADIUS + 2)) {
			w.confined += CHECK;
		}
		if (now - w.windowAt >= 100) {
			boolean same = w.windowDest != null && w.windowDest.distSqr(dest) <= 16;
			boolean nearer = same && toDest < w.windowDistance - 2;
			if (r.underground() && !w.destUnderground && !there && !nearer) {
				w.lost += (int) Math.min(100, now - w.windowAt);
			} else {
				w.lost = 0;
			}
			w.windowAt = now;
			w.windowDistance = toDest;
			w.windowDest = dest.immutable();
		}
		boolean trouble = w.still >= NUDGE_AFTER || w.confined >= CONFINED_AFTER || w.lost >= LOST_AFTER
			|| r.inWater() && w.still >= WATER_AFTER;
		if (trouble && w.troubleSince < 0) {
			w.troubleSince = now;
		} else if (!trouble && w.still == 0 && w.confined == 0 && w.lost == 0 && w.gasping == 0) {
			w.troubleSince = -1;
			w.weakUnderground = 0;
		}
	}

	private static void escalate(CompanionEntity c, ServerLevel level, Watch w, Senses.Reading r, long now) {
		if (w.dest == null) {
			return;
		}
		// In the water: no hopping about, straight for the shore (or a step cut out of a hole with steep sides).
		if (r.inWater()) {
			BlockPos dest = w.dest;
			boolean carriedOff = r.inCurrent() && w.windowDest != null && w.windowDest.distSqr(dest) <= 16
				&& c.position().distanceTo(Vec3.atBottomCenterOf(dest)) > w.windowDistance + CARRIED_OFF;
			if (r.waterfall() || carriedOff || w.still >= WATER_AFTER || w.confined >= CONFINED_AFTER / 2 || r.inCurrent() && w.still >= 20) {
				startTrouble(w, now);
				if (!tryPlan(c, level, w, new ShorePlan(w.dest), now) && w.still >= WATER_AFTER * 3 && mayDig(c)) {
					tryPlan(c, level, w, new StairPlan(w.dest, false, 8), now);
				}
			}
			return;
		}
		// Lost in a cave: walk out to the sky, or dig up to it.
		if (w.lost >= LOST_AFTER && mayWalkOut(c)) {
			if (tryPlan(c, level, w, new CaveExitPlan(), now)) {
				return;
			}
			if (mayDig(c) && tryPlan(c, level, w, new StairPlan(home(c), true, 6), now)) {
				return;
			}
		}
		if (w.still >= NUDGE_AFTER && w.nudges < MAX_NUDGES && now >= w.nextNudge) {
			nudge(c, level, w, now);
			return;
		}
		if ((w.still >= LOOK_AFTER || w.confined >= CONFINED_AFTER) && now >= w.nextProbe) {
			w.nextProbe = now + PROBE_GAP;
			lookForWayOut(c, level, w, r, now);
			return;
		}
		if (givenUpLately(w, now) >= GIVEN_UP_WALKS && now >= w.nextHemmedCheck && mayDig(c) && !inCamp(c, level)) {
			w.nextHemmedCheck = now + HEMMED_CHECK_GAP;
			if (!Ways.canLeave(level, c.blockPosition(), 24, 2500)) {
				startTrouble(w, now);
				tryPlan(c, level, w, new StairPlan(home(c), false, 24), now);
			}
		}
	}

	/** Stuck a while, the nudges no help: shut in, lost, or only the place they want is out of reach? */
	private static void lookForWayOut(CompanionEntity c, ServerLevel level, Watch w, Senses.Reading r, long now) {
		BlockPos feet = c.blockPosition();
		if (!Ways.canLeave(level, feet, 8, 400)) {
			if (mayDig(c) && (tryPlan(c, level, w, new StepPlan(), now) || tryPlan(c, level, w, new StairPlan(w.dest, r.underground(), 8), now))) {
				return;
			}
			return; // nothing they can do from here: the rescue comes in time
		}
		if (r.underground() && !w.destUnderground && mayWalkOut(c) && tryPlan(c, level, w, new CaveExitPlan(), now)) {
			return;
		}
		// They can get about; only that one place is out of reach. Their job gives it up on its own.
		w.unreachable = w.dest;
		w.unreachableUntil = now + 600;
		w.calm(c.position());
	}

	private static void nudge(CompanionEntity c, ServerLevel level, Watch w, long now) {
		w.nudges++;
		w.nextNudge = now + NUDGE_GAP;
		if (w.nudges == 2 && !w.saidStuck) {
			Speech.say(c, Line.STUCK);
			w.saidStuck = true;
		}
		BlockPos feet = c.blockPosition();
		BlockPos dest = w.dest;
		Path path = c.getNavigation().getPath();
		BlockPos ahead;
		if (path != null && !path.isDone()) {
			ahead = path.getNextNodePos();
		} else if (dest != null && !dest.equals(feet)) {
			ahead = feet.relative(Direction.getApproximateNearest(dest.getX() - feet.getX(), 0, dest.getZ() - feet.getZ()));
		} else {
			ahead = feet;
		}
		block(w, ahead, now);
		if (c.onGround()) {
			c.getJumpControl().jump();
		}
		Direction side = sideStep(level, c, dest);
		if (side != null) {
			c.addDeltaMovement(new Vec3(side.getStepX() * 0.12, 0, side.getStepZ() * 0.12));
		}
		c.getNavigation().stop(); // the next walk works out a new path, round the blocked spot
	}

	private static void block(Watch w, BlockPos pos, long now) {
		if (now >= w.blockedUntil) {
			w.blocked.clear();
		}
		if (w.blocked.size() < 24) {
			w.blocked.add(pos.asLong());
		}
		w.blockedUntil = now + BLOCKED_FOR;
	}

	/** A side to step to (across the way they were going, where there is room), or null. */
	private static @Nullable Direction sideStep(ServerLevel level, CompanionEntity c, @Nullable BlockPos dest) {
		BlockPos feet = c.blockPosition();
		Direction facing = dest != null && !dest.equals(feet)
			? Direction.getApproximateNearest(dest.getX() - feet.getX(), 0, dest.getZ() - feet.getZ()) : c.getDirection();
		if (!facing.getAxis().isHorizontal()) {
			facing = c.getDirection();
		}
		Direction[] sides = c.getRandom().nextBoolean()
			? new Direction[] {facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()}
			: new Direction[] {facing.getCounterClockWise(), facing.getClockWise(), facing.getOpposite()};
		for (Direction d : sides) {
			if (Terrain.standable(level, feet.relative(d))) {
				return d;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------- air

	private static void air(CompanionEntity c, ServerLevel level, Watch w, Senses.Reading r) {
		if (!r.lowOnAir() || !c.isUnderWater()) {
			w.gasping = 0;
			w.airAt = null;
			return;
		}
		if (w.gasping == 0) {
			Speech.say(c, Line.GASPING);
			startTrouble(w, level.getGameTime());
		}
		w.gasping += CHECK;
		w.airAt = nearestAir(level, c.blockPosition());
	}

	/** Every tick while gasping: up, and over to the nearest open water surface if right above is roofed over. */
	private static void breathe(CompanionEntity c, ServerLevel level, Watch w) {
		if (!c.isUnderWater()) {
			return;
		}
		Vec3 motion = c.getDeltaMovement();
		double up = Math.min(0.12, Math.max(motion.y, 0.0) + 0.04);
		Vec3 sideways = Vec3.ZERO;
		BlockPos air = w.airAt;
		if (air != null) {
			Vec3 to = new Vec3(air.getX() + 0.5 - c.getX(), 0, air.getZ() + 0.5 - c.getZ());
			if (to.lengthSqr() > 0.09) {
				sideways = to.normalize().scale(0.03);
			}
		}
		c.setDeltaMovement(motion.x * 0.9 + sideways.x, up, motion.z * 0.9 + sideways.z);
		c.getJumpControl().jump();
	}

	/** The nearest spot (within four blocks across, six up) where the water ends in open air, or null. */
	private static @Nullable BlockPos nearestAir(ServerLevel level, BlockPos feet) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int r = 0; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = 0; dy <= 6; dy++) {
						m.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
						if (!level.isLoaded(m)) {
							break;
						}
						if (level.getFluidState(m).is(FluidTags.WATER)) {
							continue;
						}
						if (Terrain.passable(level, m) && level.getFluidState(m).isEmpty()) {
							return m.immutable();
						}
						break; // roofed over here
					}
				}
			}
		}
		return null;
	}

	// --------------------------------------------------------------------- plans

	private static boolean tryPlan(CompanionEntity c, ServerLevel level, Watch w, Plan plan, long now) {
		if (w.plan != null || w.cooldown.getOrDefault(plan.kind(), Long.MIN_VALUE) > now) {
			return false;
		}
		boolean started;
		try {
			started = plan.start(c, level);
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.error("Finding a way out for {} crashed", c.displayName(), e);
			started = false;
		}
		if (!started) {
			w.cooldown.put(plan.kind(), now + PLAN_COOLDOWN);
			return false;
		}
		w.plan = plan;
		w.planTicked = now;
		startTrouble(w, now);
		return true;
	}

	/** True when a plan is waiting for {@link WayOutGoal} to run it. */
	static boolean hasPlan(CompanionEntity c) {
		Watch w = WATCH.get(c);
		return w != null && w.plan != null;
	}

	/** Runs the friend's plan for one tick (from {@link WayOutGoal}). */
	static void tickPlan(CompanionEntity c, ServerLevel level) {
		Watch w = WATCH.get(c);
		Plan plan = w == null ? null : w.plan;
		if (plan == null) {
			return;
		}
		w.planTicked = level.getGameTime();
		Plan.Status status;
		try {
			status = plan.tick(c, level);
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.error("Finding a way out for {} crashed", c.displayName(), e);
			status = Plan.Status.FAILED;
		}
		if (status != Plan.Status.RUNNING) {
			endPlan(c, w, status == Plan.Status.DONE, true);
		}
	}

	/** The plan was cut short (a fight, falling back): it ends, and is thought out again later if still needed. */
	static void interrupt(CompanionEntity c) {
		Watch w = WATCH.get(c);
		if (w != null && w.plan != null) {
			endPlan(c, w, false, false);
		}
	}

	private static void endPlan(CompanionEntity c, Watch w, boolean done, boolean coolDown) {
		Plan plan = w.plan;
		w.plan = null;
		if (plan == null) {
			return;
		}
		try {
			plan.stop(c);
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.error("Tidying up {}'s way out crashed", c.displayName(), e);
		}
		long now = c.level().getGameTime();
		if (done) {
			if (plan.kind() == Plan.Kind.STEP || plan.kind() == Plan.Kind.STAIR && !Senses.underground(c)) {
				Speech.say(c, Line.UNSTUCK);
			}
			w.saidStuck = false;
			w.calm(c.position());
			w.blocked.clear();
		} else if (coolDown) {
			w.cooldown.put(plan.kind(), now + PLAN_COOLDOWN);
		}
	}

	private static void startTrouble(Watch w, long now) {
		if (w.troubleSince < 0) {
			w.troubleSince = now;
		}
	}

	// -------------------------------------------------------------------- rescue

	private static void countWeakness(CompanionEntity c, Watch w, Senses.Reading r) {
		boolean underground = r.underground() || w.plan != null && w.plan.kind() != Plan.Kind.SHORE && w.plan.kind() != Plan.Kind.STEP;
		boolean weak = c.badlyHurt() || c.isTeamMember() && c.needs().get(Needs.Need.HUNGER) <= Needs.TOO_HUNGRY_TO_HEAL && !c.hasFood();
		if (w.troubleSince >= 0 && underground && weak) {
			w.weakUnderground += CHECK;
		} else {
			w.weakUnderground = 0;
		}
	}

	private static void rescueIfDue(CompanionEntity c, ServerLevel level, Watch w, Senses.Reading r, long now) {
		if (!FriendsConfig.get().rescueStuckFriends || !c.isTeamMember() || c.mode() == CompanionMode.STAY || c.isPassenger()
			|| now - w.lastRescue < RESCUE_GAP || inFight(c, r)) {
			return;
		}
		boolean drowning = w.gasping >= DROWN_RESCUE_AFTER && c.getAirSupply() <= c.getMaxAirSupply() / 10;
		if (drowning) {
			rescue(c, level, w, Trouble.DROWNING, now);
			return;
		}
		if (w.troubleSince < 0 || workingFine(c, w, now)) {
			return;
		}
		long trouble = now - w.troubleSince;
		boolean weak = w.weakUnderground >= WEAK_RESCUE_AFTER;
		if (trouble < RESCUE_AFTER && !weak) {
			return;
		}
		Plan plan = w.plan;
		boolean caveTrouble = r.underground() || plan != null && plan.kind() == Plan.Kind.CAVE_EXIT;
		Trouble why = weak ? Trouble.TRAPPED : caveTrouble ? Trouble.CAVE : r.inWater() ? Trouble.WATER : Trouble.STUCK;
		rescue(c, level, w, why, now);
	}

	/** In a fight, or a monster heard close by is after them: never rescued out of that. */
	private static boolean inFight(CompanionEntity c, Senses.Reading r) {
		if (c.getTarget() != null || c.getLastHurtByMob() != null && c.tickCount - c.getLastHurtByMobTimestamp() < 100) {
			return true;
		}
		for (Senses.Heard h : r.heard()) {
			if (h.distance() <= 8 && h.mob().isAlive() && Threats.isTargeting(h.mob(), c)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A job that is still getting on with it (a block broken or placed by the job in the last half minute: the mine, the
	 * quarry, a building) is going fine, underground or not, and nobody is brought home out of it.
	 */
	private static boolean workingFine(CompanionEntity c, Watch w, long now) {
		if (c.mode() != CompanionMode.WORK || w.plan != null) {
			return false;
		}
		CompanionTask job = c.scheduler().current();
		return job != null && now - c.lastEditTick() < 600;
	}

	/**
	 * Brings the friend home (or to their leader, following), onto a safe, loaded spot nearby, and tells everyone.
	 * Nothing happens (it is tried again shortly) when there is no such spot just now.
	 */
	private static void rescue(CompanionEntity c, ServerLevel level, Watch w, Trouble why, long now) {
		BlockPos spot = null;
		String where = "home";
		ServerPlayer leader = c.leader();
		if (c.mode() == CompanionMode.FOLLOW && FollowLeaderGoal.canFollow(c, leader)) {
			spot = FollowLeaderGoal.catchUpSpot(level, leader.blockPosition(), c.getRandom());
			if (spot == null) {
				spot = Terrain.safeSpotNear(level, leader.blockPosition(), 4);
			}
			where = "back to " + leader.getName().getString();
		} else if (c.mode() == CompanionMode.WORK) {
			BlockPos here = c.blockPosition();
			// Somewhere that leads somewhere, and not back into the hole they are in (a pit inside the camp).
			spot = Terrain.safeSpotNear(level, c.homePos(), 8,
				p -> p.distSqr(here) > 9 && Ways.canLeave(level, p, 4, 120));
		}
		if (spot == null || !level.isPositionEntityTicking(spot)) {
			w.lastRescue = now - RESCUE_GAP + 200; // nowhere safe just now (not loaded?): try again in a while
			return;
		}
		if (w.plan != null) {
			endPlan(c, w, false, false);
		}
		c.getNavigation().stop();
		c.actions().reset();
		if (c.mode() == CompanionMode.WORK) {
			c.scheduler().interrupt(); // whatever job led them astray starts afresh
		}
		c.setDeltaMovement(Vec3.ZERO);
		c.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, c.getYRot(), c.getXRot());
		c.resetFallDistance();
		w.calm(c.position());
		w.gasping = 0;
		w.lastRescue = now;
		w.blocked.clear();
		w.saidStuck = false;
		Speech.announce(level.getServer(), Component.literal(c.displayName() + " " + why.words + " and found the way " + where + ".")
			.withStyle(ChatFormatting.YELLOW));
		Speech.say(c, Line.RESCUED);
		Camp.data(level.getServer()).addStat("friends_rescued", 1);
	}

	// ------------------------------------------------------------------- helpers

	/** May dig or place blocks to get out: a grown-up on the team, with world editing on. */
	private static boolean mayDig(CompanionEntity c) {
		return c.isTeamMember() && !c.isChild() && c.mode() != CompanionMode.STAY && FriendsConfig.get().allowWorldEditing;
	}

	/** May walk off to find the way out of a cave: anyone not told to stay put. */
	private static boolean mayWalkOut(CompanionEntity c) {
		return c.mode() != CompanionMode.STAY && Terrain.caveAware(c.level());
	}

	private static @Nullable BlockPos home(CompanionEntity c) {
		ServerPlayer leader = c.leader();
		if (c.mode() == CompanionMode.FOLLOW && FollowLeaderGoal.canFollow(c, leader)) {
			return leader.blockPosition();
		}
		return c.isTeamMember() ? c.homePos() : null;
	}

	private static boolean inCamp(CompanionEntity c, ServerLevel level) {
		return Camp.isCampLevel(level, Camp.data(level.getServer())) && WorldEditGuard.inCampHorizontally(c, c.blockPosition());
	}

	private static int givenUpLately(Watch w, long now) {
		int n = 0;
		for (long at : w.givenUp) {
			if (now - at <= GIVEN_UP_WINDOW) {
				n++;
			}
		}
		return n;
	}
}
