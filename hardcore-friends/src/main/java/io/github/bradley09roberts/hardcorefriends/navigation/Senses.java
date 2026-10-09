package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * A friend's senses, for any package to ask about: what they can hear, whether they are underground or in the dark,
 * short of air, in a current, near lava or a long drop, and how much danger that adds up to. Hearing is not sight:
 * a monster within {@value #HEARING} blocks is noticed through a wall, and a creeper's hiss is heard, so friends do
 * not walk round a corner into one (their paths keep away from what they hear, see {@link FriendNodeEvaluator}).
 *
 * <p>Sensing is cheap: each friend's reading is worked out at most once every {@value #REFRESH} ticks, from a handful of
 * blocks round them and one search for monsters, and kept until then. Every answer is about the friend's own spot;
 * nothing here loads a chunk. Off the server (or for a friend who has not been read yet) the answers are the calm ones.
 *
 * <p>Typical use: {@code Senses.underground(c)} and {@code Senses.dark(c)} before choosing a job underground,
 * {@code Senses.danger(c) == Senses.Danger.SERIOUS} to drop everything, {@code Senses.heard(c)} for what is about.
 */
public final class Senses {
	/** How far a friend hears monsters, through walls, in blocks (half that up or down). */
	public static final double HEARING = 16;
	/** A creeper hissing this close is serious danger. */
	public static final double HISS_DANGER = 8;
	/** How often a reading is worked out again, in ticks. */
	static final int REFRESH = 10;
	/** At or below this light (sky and block together, the sky dimmed at night) a friend is in the dark. */
	public static final int DARK = 3;
	/** Below this share of their air, a friend under water must come up. */
	static final double LOW_AIR = 0.45;
	/** The most monsters a reading keeps (nearest first). */
	private static final int MAX_HEARD = 6;
	/** Paths keep away from monsters heard within this distance. */
	private static final double PATH_WARY = 12;

	private static final Map<CompanionEntity, Reading> READINGS = new WeakHashMap<>();
	/** The calm answers off the server (never kept: a reading kept is always one taken at a real game time). */
	private static final Reading CALM = calm(Long.MIN_VALUE);

	/** How much danger a friend senses at once. */
	public enum Danger {
		/** Nothing to worry about. */
		NONE,
		/** Something to be careful of: underground in the dark, a long drop beside them, a current, lava near, monsters heard. */
		CAUTION,
		/** Get out now: on fire or in lava, out of air, in a waterfall, a creeper hissing close by. */
		SERIOUS
	}

	/** A monster the friend can hear, how far away it was, and whether it is a creeper and hissing. */
	public record Heard(LivingEntity mob, double distance, boolean creeper, boolean hissing) {
	}

	/**
	 * One reading of a friend's senses. {@code light} is 0-15 at their head; {@code current} is which way the water
	 * pushes (flat), zero when still; {@code heard} is nearest first.
	 */
	public record Reading(long at, boolean underground, int light, boolean dark, boolean inWater, boolean underWater,
		boolean lowOnAir, Vec3 current, boolean waterfall, boolean nearLava, boolean burning, boolean nearDrop, List<Heard> heard,
		long[] creeperSpots, long[] monsterSpots) {

		/** True while in water that is running. */
		public boolean inCurrent() {
			return inWater && current.lengthSqr() > 1.0E-6;
		}

		/** True when a creeper within {@value Senses#HISS_DANGER} blocks is hissing. */
		public boolean hissingClose() {
			for (Heard h : heard) {
				if (h.hissing() && h.distance() <= HISS_DANGER && h.mob().isAlive()) {
					return true;
				}
			}
			return false;
		}

		/** How much danger all this adds up to. */
		public Danger danger() {
			if (burning || lowOnAir || waterfall && inWater || hissingClose()) {
				return Danger.SERIOUS;
			}
			if (underground && dark || nearDrop || inCurrent() || nearLava || underWater || !heard.isEmpty()) {
				return Danger.CAUTION;
			}
			return Danger.NONE;
		}
	}

	private Senses() {
	}

	/** The friend's current reading (worked out again when older than {@value #REFRESH} ticks). */
	public static Reading read(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return CALM;
		}
		long now = level.getGameTime();
		Reading r = READINGS.get(c);
		if (r == null || now - r.at() >= REFRESH || now < r.at()) {
			r = sense(c, level, now);
			READINGS.put(c, r);
		}
		return r;
	}

	/** True when the friend is underground: in a cave, a mine or a sealed cellar, out of the sky's reach. */
	public static boolean underground(CompanionEntity c) {
		return read(c).underground();
	}

	/** True when it is too dark round the friend's head to see well (monsters can spawn about them). */
	public static boolean dark(CompanionEntity c) {
		return read(c).dark();
	}

	/** How much danger the friend senses. */
	public static Danger danger(CompanionEntity c) {
		return read(c).danger();
	}

	/** The monsters the friend can hear, nearest first (seen or not; through walls too), still alive. */
	public static List<LivingEntity> heard(CompanionEntity c) {
		List<LivingEntity> out = new ArrayList<>();
		for (Heard h : read(c).heard()) {
			if (h.mob().isAlive() && !h.mob().isRemoved()) {
				out.add(h.mob());
			}
		}
		return out;
	}

	/** True when the friend is under water and running short of air. */
	public static boolean lowOnAir(CompanionEntity c) {
		return read(c).lowOnAir();
	}

	/** True while the friend is in running water. */
	public static boolean inCurrent(CompanionEntity c) {
		return read(c).inCurrent();
	}

	/** Forgets every reading (a server starting or stopping). */
	static void clear() {
		READINGS.clear();
	}

	/** Nothing sensed at all, taken at {@code at}. */
	private static Reading calm(long at) {
		return new Reading(at, false, 15, false, false, false, false, Vec3.ZERO, false, false, false, false, List.of(), new long[0],
			new long[0]);
	}

	private static Reading sense(CompanionEntity c, ServerLevel level, long now) {
		BlockPos feet = c.blockPosition();
		if (!level.isLoaded(feet)) {
			// Out of the world's height or in land not loaded: calm for now, and sensed again at the next refresh (a
			// reading dated long ago would never be refreshed: the age sum would overflow).
			return calm(now);
		}
		BlockPos head = feet.above();
		boolean underground = Terrain.underground(level, feet) && Terrain.underground(level, head);
		int light = level.getMaxLocalRawBrightness(head);
		boolean inWater = c.isInWater();
		boolean underWater = c.isUnderWater();
		boolean lowOnAir = underWater && c.getAirSupply() < c.getMaxAirSupply() * LOW_AIR;
		Vec3 current = Terrain.current(level, feet);
		if (current.lengthSqr() < 1.0E-6) {
			current = Terrain.current(level, head);
		}
		boolean waterfall = Terrain.fallingWater(level.getFluidState(feet)) || Terrain.fallingWater(level.getFluidState(head));
		boolean nearLava = Terrain.nearLava(level, feet);
		boolean burning = c.isOnFire() || c.isInLava();
		boolean nearDrop = c.onGround() && nearDrop(level, feet);

		List<Heard> heard = new ArrayList<>();
		for (LivingEntity e : Threats.around(c, HEARING)) {
			double d = e.distanceTo(c);
			if (d > HEARING) {
				continue;
			}
			boolean creeper = e instanceof Creeper;
			boolean hissing = e instanceof Creeper cr && (cr.getSwellDir() > 0 || cr.isIgnited());
			heard.add(new Heard(e, d, creeper, hissing));
		}
		heard.sort(Comparator.comparingDouble(Heard::distance));
		if (heard.size() > MAX_HEARD) {
			heard = new ArrayList<>(heard.subList(0, MAX_HEARD));
		}
		List<Long> creepers = new ArrayList<>();
		List<Long> monsters = new ArrayList<>();
		for (Heard h : heard) {
			if (h.distance() <= PATH_WARY) {
				(h.creeper() ? creepers : monsters).add(h.mob().blockPosition().asLong());
			}
		}
		return new Reading(now, underground, light, light <= DARK, inWater, underWater, lowOnAir, current, waterfall, nearLava,
			burning, nearDrop, List.copyOf(heard), unbox(creepers), unbox(monsters));
	}

	private static long[] unbox(List<Long> list) {
		long[] out = new long[list.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = list.get(i);
		}
		return out;
	}

	/** True when, beside a friend standing at {@code feet}, the ground falls away by more than three blocks. */
	static boolean nearDrop(ServerLevel level, BlockPos feet) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = feet.relative(d);
			if (!level.isLoaded(side)) {
				continue;
			}
			boolean open = true;
			for (int dy = 0; dy <= FriendNodeEvaluator.EDGE_DROP + 1 && open; dy++) {
				BlockPos p = side.below(dy);
				open = Terrain.passable(level, p) && level.getFluidState(p).isEmpty();
			}
			if (open) {
				return true;
			}
		}
		return false;
	}

	/** One line about what a friend senses, for {@code /friends senses}. */
	static String describe(CompanionEntity c) {
		Reading r = read(c);
		List<String> parts = new ArrayList<>();
		parts.add(r.underground() ? "underground" : "above ground");
		parts.add((r.dark() ? "in the dark" : "can see") + " (light " + r.light() + ")");
		if (r.underWater()) {
			parts.add(r.lowOnAir() ? "under water and short of air" : "under water");
		} else if (r.inWater()) {
			parts.add("in the water");
		}
		if (r.inCurrent()) {
			parts.add("in a current");
		}
		if (r.waterfall()) {
			parts.add("in falling water");
		}
		if (r.nearLava()) {
			parts.add("lava close by");
		}
		if (r.nearDrop()) {
			parts.add("beside a long drop");
		}
		if (r.burning()) {
			parts.add("burning");
		}
		LivingEntity nearest = null;
		double nearestAt = 0;
		boolean hiss = false;
		for (Heard h : r.heard()) {
			if (h.mob().isAlive()) {
				if (nearest == null) {
					nearest = h.mob();
					nearestAt = h.distance();
				}
				hiss |= h.hissing();
			}
		}
		if (nearest != null) {
			parts.add("hears " + r.heard().size() + " monster" + (r.heard().size() == 1 ? "" : "s") + " (nearest "
				+ nearest.getName().getString() + ", " + Math.round(nearestAt) + " blocks)" + (hiss ? ", a creeper hissing" : ""));
		} else {
			parts.add("hears no monsters");
		}
		parts.add("danger: " + r.danger().name().toLowerCase(java.util.Locale.ROOT));
		return String.join(", ", parts);
	}

	/** The nearest monster heard, if any (alive). */
	static @Nullable LivingEntity nearestHeard(CompanionEntity c) {
		for (Heard h : read(c).heard()) {
			if (h.mob().isAlive()) {
				return h.mob();
			}
		}
		return null;
	}
}
