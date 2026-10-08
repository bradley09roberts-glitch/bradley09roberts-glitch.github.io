package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * What every trip away from camp shares: who is fit to go, whether there is daylight enough for the way there and
 * back, when to turn for home, walking a long way leg by leg, news everyone hears however far away the friend is,
 * one friend at a time on each kind of trip, and the trip in progress kept with the friend (so a fight on the road, or
 * the world closing, does not make them forget where they were going).
 */
public final class Trips {
	/** How fast friends cover ground on a long walk, in blocks a tick, allowing for detours (about 2.4 a second). */
	private static final double WALK_SPEED = 0.12;
	/** Spare daylight kept back on every trip, in ticks. */
	private static final int MARGIN_TICKS = 1200;
	/** The time of day sunset starts (friends want to be home before it). */
	private static final long SUNSET = 12000;
	/** Where a friend's trip in progress is kept in {@link CompanionEntity#extra()}. */
	private static final String STATE = "survival.trip";
	/** Who is on each kind of trip that only one friend does at a time. */
	private static final Map<String, UUID> CLAIMS = new HashMap<>();

	private Trips() {
	}

	// -------------------------------------------------------------- conditions

	/** True when trips are switched on in the settings. */
	public static boolean allowed() {
		return FriendsConfig.get().allowTrips;
	}

	/**
	 * Fit to set off: working on their own, healthy (three quarters of their health or more), fed, rested enough, and
	 * carrying some food for the road.
	 */
	public static boolean fitToGo(CompanionEntity c) {
		return c.mode() == CompanionMode.WORK && c.isTeamMember() && !c.tooWeakToWork() && !c.badlyHurt()
			&& c.getHealth() >= c.getMaxHealth() * 0.75F && c.needs().get(Need.HUNGER) >= 55
			&& c.needs().get(Need.ENERGY) >= 40 && c.backpack().count(CompanionEntity::isEdible) >= 2;
	}

	/** True while the camp is safe to leave: no friend died near it lately and no storm is raging. */
	public static boolean campSafe(ServerLevel level, CampData data) {
		return !level.isThundering() && data.campPos().map(p -> !data.nearDanger(p, level.getGameTime())).orElse(false);
	}

	/** Ticks a walk of this many blocks takes. */
	public static long travelTicks(double blocks) {
		return (long) (blocks / WALK_SPEED);
	}

	/** True by day with time enough left for this much walking (and some to spare) before sunset. */
	public static boolean daylightFor(ServerLevel level, double blocks, long extraTicks) {
		if (Camp.isNight(level) || Camp.isDusk(level)) {
			return false;
		}
		return Camp.timeOfDay(level) + travelTicks(blocks) + extraTicks + MARGIN_TICKS < SUNSET;
	}

	/** The farthest a friend could walk out and back again before sunset, starting now (0 at dusk or night). */
	public static int reachToday(ServerLevel level, long extraTicks) {
		if (Camp.isNight(level) || Camp.isDusk(level)) {
			return 0;
		}
		long spare = SUNSET - Camp.timeOfDay(level) - MARGIN_TICKS - extraTicks;
		return spare <= 0 ? 0 : (int) (spare * WALK_SPEED / 2);
	}

	/**
	 * Why a friend out on a trip should turn for home now, or null to carry on: hurt, hungry, tired, a storm, or not
	 * enough daylight left to get home.
	 */
	public static @Nullable String turnBackReason(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (c.badlyHurt() || c.getHealth() < c.getMaxHealth() * 0.5F) {
			return "hurt";
		}
		if (c.needs().get(Need.HUNGER) < 30 && !c.hasFood()) {
			return "hungry";
		}
		if (c.needs().get(Need.ENERGY) < 20) {
			return "tired";
		}
		if (level.isThundering()) {
			return "storm";
		}
		double home = Math.sqrt(Camp.horizontalDistSqr(c.blockPosition(), c.homePos()));
		if (!daylightFor(level, home, 0)) {
			return "late";
		}
		return null;
	}

	/** Below this hunger a friend on the road eats something from their backpack as they walk. */
	private static final double SNACK_BELOW = 40;

	/**
	 * A bite to eat on the road: a trip keeps the everyday meal job waiting, so a hungry traveller eats from their
	 * backpack as they go (one item every few seconds at most; call it now and then).
	 */
	public static void snack(CompanionEntity c) {
		if (c.needs().get(Need.HUNGER) >= SNACK_BELOW) {
			return;
		}
		ItemStack food = c.backpack().take(CompanionEntity::isEdible, 1);
		if (!food.isEmpty()) {
			String name = food.getHoverName().getString();
			c.eat(food);
			Speech.say(c, Line.ATE, name);
		}
	}

	/** True when this friend is back inside the camp (a few blocks in from its edge). */
	public static boolean home(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return false;
		}
		int r = Math.max(6, Camp.radius(data) - 4);
		return Camp.horizontalDistSqr(c.blockPosition(), c.homePos()) <= (double) r * r;
	}

	/** True near where a friend died lately, or near a known pillager outpost. */
	public static boolean dangerous(ServerLevel level, CampData data, BlockPos pos) {
		if (data.nearDanger(pos, level.getGameTime())) {
			return true;
		}
		return Places.nearest(data, Places.OUTPOST, pos, 64, Long.MAX_VALUE, p -> true) != null;
	}

	/** Shelter blocks a friend likes to carry on a trip (enough for a pillar or most of a pillbox). */
	public static final int KIT_BLOCKS = 8;
	/** Torches a friend likes to carry on a trip (one for a shelter, one spare). */
	public static final int KIT_TORCHES = 2;

	/** True when the friend carries less than a trip kit and could use a stop at the chest first. */
	public static boolean needsKit(CompanionEntity c) {
		return Shelters.blocksCarried(c) < KIT_BLOCKS / 2 || !c.backpack().has(s -> s.is(Items.TORCH));
	}

	/**
	 * Packs a trip kit from the chest: dirt (or cobblestone) up to {@value #KIT_BLOCKS} blocks and a couple of torches,
	 * for a night shelter or a pillar out of reach. Takes only what the chest has, never more than will fit.
	 */
	public static void packKit(CompanionEntity c, Container chest) {
		int blocks = KIT_BLOCKS - Shelters.blocksCarried(c);
		if (blocks > 0) {
			int got = SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.DIRT), blocks);
			if (got < blocks) {
				SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.COBBLESTONE), blocks - got);
			}
		}
		int torches = KIT_TORCHES - c.backpack().count(Items.TORCH);
		if (torches > 0) {
			SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.TORCH), torches);
		}
	}

	/** "a, b and c" without repeats; past four things, the rest are counted ("a, b, c and 3 more"). */
	public static String joinList(List<String> items) {
		List<String> parts = new ArrayList<>();
		for (String item : items) {
			if (!parts.contains(item)) {
				parts.add(item);
			}
		}
		if (parts.isEmpty()) {
			return "nothing";
		}
		if (parts.size() > 4) {
			int more = parts.size() - 3;
			parts = new ArrayList<>(parts.subList(0, 3));
			parts.add(more + " more");
		}
		if (parts.size() == 1) {
			return parts.getFirst();
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
	}

	/** "the north-east", for where a trip is heading. */
	public static String direction(BlockPos from, BlockPos to) {
		return Compass.direction(to.getX() - from.getX(), to.getZ() - from.getZ());
	}

	// ------------------------------------------------------------------ claims

	/** True while another friend is on this kind of trip right now (one at a time). */
	public static boolean heldByOther(String jobId, CompanionEntity c) {
		UUID holder = CLAIMS.get(jobId);
		if (holder == null || holder.equals(c.getUUID())) {
			return false;
		}
		for (CompanionEntity other : Companions.all()) {
			if (other.getUUID().equals(holder) && other.mode() == CompanionMode.WORK) {
				CompanionTask doing = other.scheduler().current();
				State trip = state(other);
				if (doing != null && doing.id().equals(jobId) || trip != null && trip.job.equals(jobId)) {
					return true;
				}
			}
		}
		CLAIMS.remove(jobId, holder); // they died, left or gave it up: the trip is free again
		return false;
	}

	public static void claim(String jobId, CompanionEntity c) {
		CLAIMS.put(jobId, c.getUUID());
	}

	public static void release(String jobId, CompanionEntity c) {
		CLAIMS.remove(jobId, c.getUUID());
	}

	/** Forgets every claim (a server starting or stopping). */
	static void clearClaims() {
		CLAIMS.clear();
	}

	// ------------------------------------------------------------------- state

	/** A trip in progress: which job, what stage, where to, when it began, and what was found or done so far. */
	public static final class State {
		public final String job;
		public String phase;
		public BlockPos destination;
		public final long startedAt;
		public final List<String> notes = new ArrayList<>();

		public State(String job, String phase, BlockPos destination, long startedAt) {
			this.job = job;
			this.phase = phase;
			this.destination = destination.immutable();
			this.startedAt = startedAt;
		}
	}

	/** The friend's trip in progress, if any. */
	public static @Nullable State state(CompanionEntity c) {
		CompoundTag tag = c.extra().getCompoundOrEmpty(STATE);
		if (tag.isEmpty()) {
			return null;
		}
		State s = new State(tag.getStringOr("job", ""), tag.getStringOr("phase", ""), BlockPos.of(tag.getLongOr("dest", 0L)),
			tag.getLongOr("started", 0L));
		for (Tag t : tag.getListOrEmpty("notes")) {
			t.asString().ifPresent(s.notes::add);
		}
		return s;
	}

	public static void save(CompanionEntity c, State s) {
		CompoundTag tag = new CompoundTag();
		tag.putString("job", s.job);
		tag.putString("phase", s.phase);
		tag.putLong("dest", s.destination.asLong());
		tag.putLong("started", s.startedAt);
		ListTag notes = new ListTag();
		for (String note : s.notes) {
			notes.add(StringTag.valueOf(note));
		}
		tag.put("notes", notes);
		c.extra().put(STATE, tag);
	}

	public static void clear(CompanionEntity c) {
		c.extra().remove(STATE);
	}

	/** The longest a trip is remembered, in ticks (two days): after that it is given up and forgotten. */
	private static final long TRIP_MEMORY = 48000L;

	/**
	 * True (and the trip forgotten) when a trip has dragged on for two days: the friend could not get there or back,
	 * so their everyday jobs take over again (they head home at dusk like anyone).
	 */
	public static boolean expired(CompanionEntity c, State s) {
		long now = c.level().getGameTime();
		if (now - s.startedAt <= TRIP_MEMORY && now >= s.startedAt) {
			return false;
		}
		clear(c);
		ChunkLoader.stopRoaming(c);
		return true;
	}

	// ------------------------------------------------------------------- words

	/**
	 * Says a line to every player on the server, however far away: news from a trip. Follows the line's own cooldown
	 * like ordinary speech.
	 */
	public static void announce(CompanionEntity c, Line line, Object... args) {
		if (!(c.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Long last = c.speechMemory().get(line);
		if (last != null && now - last < line.cooldownTicks()) {
			return;
		}
		c.speechMemory().put(line, now);
		String[] variants = Lines.get(c.friendId(), line);
		String template = variants[c.getRandom().nextInt(variants.length)];
		String text;
		try {
			text = String.format(template, args);
		} catch (java.util.IllegalFormatException e) {
			text = template;
		}
		if (c.isSettler()) {
			text = text.replaceAll("\\b" + Pattern.quote(c.friendId().displayName()) + "\\b", Matcher.quoteReplacement(c.displayName()));
		}
		var hook = Speech.listener;
		if (hook != null) {
			hook.accept(c, text);
		}
		Speech.announce(level.getServer(), Speech.prefix(c).append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
	}

	// ------------------------------------------------------------------ walking

	/**
	 * Walks a friend a long way, one leg of up to {@value #LEG} blocks at a time towards the destination (so each path
	 * stays inside the land that is loaded around them). A leg that cannot be walked is tried again at an angle, up to
	 * five times, before the way counts as blocked. Legs never end in water or near danger.
	 */
	public static final class Walker {
		/** The longest leg, in blocks. */
		public static final int LEG = 20;
		private static final double LEG_REACH = 2.5;
		private static final int[] DETOURS = {0, 45, -45, 90, -90, 135, -135};
		/** A walk with no leg finished for this long counts as blocked. */
		private static final int NO_PROGRESS_LIMIT = 20 * 45;

		public enum Result {
			WALKING,
			ARRIVED,
			BLOCKED
		}

		private @Nullable BlockPos leg;
		private int detour;
		private int sinceProgress;

		public void reset() {
			leg = null;
			detour = 0;
			sinceProgress = 0;
		}

		/** Call every tick. Arrives within {@code arrive} blocks of the destination (horizontally). */
		public Result walk(CompanionEntity c, BlockPos destination, double arrive) {
			ServerLevel level = (ServerLevel) c.level();
			double toGoal = Math.sqrt(Camp.horizontalDistSqr(c.blockPosition(), destination));
			if (toGoal <= arrive) {
				c.actions().stopWalking();
				reset();
				return Result.ARRIVED;
			}
			if (++sinceProgress > NO_PROGRESS_LIMIT) {
				c.actions().stopWalking();
				reset();
				return Result.BLOCKED;
			}
			if (leg == null) {
				leg = nextLeg(level, c, destination, toGoal);
				if (leg == null) {
					reset();
					return Result.BLOCKED;
				}
			}
			if (c.actions().walkTo(leg, LEG_REACH) || Camp.horizontalDistSqr(c.blockPosition(), leg) <= LEG_REACH * LEG_REACH) {
				leg = null;
				detour = 0;
				sinceProgress = 0;
				return Result.WALKING;
			}
			if (c.actions().isStuck()) {
				c.actions().stopWalking();
				leg = null;
				if (++detour >= DETOURS.length) {
					reset();
					return Result.BLOCKED;
				}
			}
			return Result.WALKING;
		}

		/** The next leg: straight on if possible, otherwise at the current detour's angle; never into water or danger. */
		private @Nullable BlockPos nextLeg(ServerLevel level, CompanionEntity c, BlockPos destination, double toGoal) {
			CampData data = Camp.data(level.getServer());
			Vec3 here = c.position();
			double dx = destination.getX() + 0.5 - here.x;
			double dz = destination.getZ() + 0.5 - here.z;
			double length = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
			for (; detour < DETOURS.length; detour++) {
				double angle = Math.toRadians(DETOURS[detour]);
				double ux = (dx * Math.cos(angle) - dz * Math.sin(angle)) / length;
				double uz = (dx * Math.sin(angle) + dz * Math.cos(angle)) / length;
				double step = detour == 0 ? Math.min(LEG, toGoal) : LEG * 0.75;
				int x = (int) Math.floor(here.x + ux * step);
				int z = (int) Math.floor(here.z + uz * step);
				BlockPos column = new BlockPos(x, c.getBlockY(), z);
				if (!level.hasChunkAt(column)) {
					continue;
				}
				BlockPos target = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				boolean wet = !level.getFluidState(target).isEmpty() || !level.getFluidState(target.below()).isEmpty();
				boolean destinationLeg = detour == 0 && step >= toGoal - 0.5;
				if ((wet && !destinationLeg) || dangerous(level, data, target) && !destinationLeg) {
					continue;
				}
				return target;
			}
			return null;
		}
	}
}
