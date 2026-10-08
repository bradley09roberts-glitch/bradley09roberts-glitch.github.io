package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Scout's far exploring: once a day, when the camp is safe and fed, Scout sets off on a trip of up to
 * {@value #FAR} blocks into the least explored of eight directions, to find what is worth knowing out there: villages
 * to trade with, survivor camps, ruined portals (obsidian), pillager outposts (danger, so she turns back), desert and
 * jungle temples, and new biomes. She only looks at land that is loaded around her as she walks (a roaming chunk
 * ticket keeps it running), never changes a block, reports each find to everyone as she makes it, and comes home
 * before dusk with a summary. Hurt, hungry, tired, a storm or the evening drawing in turn her back early; caught out
 * at night, the night shelter takes over.
 */
public final class FarTripTask implements CompanionTask {
	public static final String ID = "survival.far_trip";
	/** The farthest a trip goes from camp, in blocks. */
	public static final int FAR = 300;
	/** The shortest trip worth setting off on, in blocks. */
	private static final int NEAREST = 96;
	/** How much further each trip in a direction goes than the last. */
	private static final int STEP_OUT = 120;
	private static final int SECTORS = 8;
	private static final String MEMORY = "survival.far_trips";
	private static final String PACK = "pack";
	private static final String OUT = "out";
	private static final String HOME = "home";
	/** How often the land around is looked over, in ticks. */
	private static final int SENSE_INTERVAL = 40;
	private static final int SENSE_CHUNKS = 2;

	private final Trips.Walker walker = new Trips.Walker();
	private Trips.@Nullable State trip;
	private int ticks;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Trips.State t = trip;
		if (t == null) {
			return "exploring far afield";
		}
		if (PACK.equals(t.phase)) {
			return "packing for a trip";
		}
		return HOME.equals(t.phase) ? "heading home from a trip" : "on a trip to explore around " + Compass.coords(t.destination);
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return 0;
		}
		Trips.State saved = Trips.state(c);
		if (saved != null) {
			// A trip in progress (a fight on the road broke it off): back to it by day; at night the shelter takes over.
			return saved.job.equals(ID) && !Trips.expired(c, saved) && !Camp.isNight(level) ? 60 : 0;
		}
		if (!Trips.allowed() || Camp.isNight(level) || Camp.isDusk(level) || data.memory(MEMORY).getLongOr("day", -1) == Camp.day(level)) {
			return 0; // one far trip a day
		}
		if (Trips.reachToday(level, 0) < NEAREST || CampNeeds.need(CampNeeds.Need.FOOD) > 0.6) {
			return 0;
		}
		if (!ChunkLoader.canRoam(level.getServer()) || !Trips.campSafe(level, data) || !Trips.fitToGo(c)) {
			return 0;
		}
		return 52; // a little above her ring scouting near camp
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		ticks = 0;
		walker.reset();
		Trips.State saved = Trips.state(c);
		if (saved != null && saved.job.equals(ID)) {
			if (!ChunkLoader.startRoaming(c, "exploring far afield") && !Trips.home(c)) {
				return false;
			}
			if (!Trips.allowed()) {
				saved.phase = HOME; // trips were switched off meanwhile: just come home
			}
			trip = saved;
			return true;
		}
		BlockPos home = c.homePos();
		int reach = Math.min(FAR, Trips.reachToday(level, 0));
		BlockPos destination = chooseDestination(level, data, home, reach);
		if (destination == null || !ChunkLoader.startRoaming(c, "exploring far afield")) {
			return false;
		}
		boolean pack = Trips.needsKit(c) && SupplyChest.of(level).isPresent();
		trip = new Trips.State(ID, pack ? PACK : OUT, destination, level.getGameTime());
		Trips.save(c, trip);
		data.memory(MEMORY).putLong("day", Camp.day(level));
		data.setDirty();
		if (!pack) {
			setOff(c, trip);
		}
		return true;
	}

	/**
	 * Where to go: the least explored of eight directions, a little further than last time (up to {@value #FAR}
	 * blocks and what daylight allows), never near where a friend died lately or a known pillager outpost.
	 */
	private @Nullable BlockPos chooseDestination(ServerLevel level, CampData data, BlockPos home, int reach) {
		CompoundTag mem = data.memory(MEMORY);
		int[] explored = mem.getIntArray("explored").orElse(new int[SECTORS]);
		if (explored.length != SECTORS) {
			explored = new int[SECTORS];
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < SECTORS; i++) {
			order.add(i);
		}
		int[] known = explored;
		java.util.Collections.shuffle(order, new java.util.Random(level.getRandom().nextLong()));
		order.sort((a, b) -> Integer.compare(known[a], known[b]));
		for (int sector : order) {
			int distance = Math.min(reach, Math.max(NEAREST, known[sector] >= FAR ? FAR : known[sector] + STEP_OUT));
			if (distance < NEAREST) {
				return null;
			}
			double angle = sector * 2 * Math.PI / SECTORS;
			BlockPos target = home.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
			if (!Trips.dangerous(level, data, target)) {
				return target;
			}
		}
		return null;
	}

	/** Off they go: everyone hears where. */
	private static void setOff(CompanionEntity c, Trips.State t) {
		t.phase = OUT;
		Trips.save(c, t);
		Trips.announce(c, Line.TRIP_START, "the land to the " + Trips.direction(c.homePos(), t.destination));
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Trips.State t = trip;
		if (t == null) {
			return TaskStatus.FAILURE;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (PACK.equals(t.phase)) {
			// A few blocks and torches for a night out, from the chest, first.
			ChestWalk.State walk = ChestWalk.tick(c);
			if (walk == ChestWalk.State.WALKING) {
				return TaskStatus.RUNNING;
			}
			if (walk == ChestWalk.State.ARRIVED) {
				ChestWalk.chest(c).ifPresent(chest -> Trips.packKit(c, chest));
			}
			c.actions().stopWalking();
			setOff(c, t);
			return TaskStatus.RUNNING;
		}
		ticks++;
		if (ticks % SENSE_INTERVAL == 0) {
			sense(c, level, t);
		}
		if (ticks % 100 == 0) {
			Trips.snack(c);
		}
		if (OUT.equals(t.phase)) {
			String why = Trips.turnBackReason(c);
			if (why != null) {
				Speech.say(c, Line.TRIP_TURN_BACK);
				turnHome(c, t);
				return TaskStatus.RUNNING;
			}
			switch (walker.walk(c, t.destination, 6)) {
				case ARRIVED, BLOCKED -> turnHome(c, t);
				case WALKING -> {
				}
			}
			return TaskStatus.RUNNING;
		}
		if (Trips.home(c)) {
			finish(c, level, t);
			return TaskStatus.SUCCESS;
		}
		return switch (walker.walk(c, c.homePos(), 6)) {
			case ARRIVED -> {
				finish(c, level, t);
				yield TaskStatus.SUCCESS;
			}
			case BLOCKED -> TaskStatus.FAILURE; // tried again in a while; the trip (and the way home) is remembered
			case WALKING -> TaskStatus.RUNNING;
		};
	}

	private void turnHome(CompanionEntity c, Trips.State t) {
		recordReach(c, t);
		t.phase = HOME;
		walker.reset();
		Trips.save(c, t);
	}

	/** Remembers how far out in this direction the trip got. */
	private static void recordReach(CompanionEntity c, Trips.State t) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		BlockPos home = c.homePos();
		double dx = t.destination.getX() - home.getX();
		double dz = t.destination.getZ() - home.getZ();
		int sector = Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (2 * Math.PI / SECTORS)), SECTORS);
		int reached = (int) Math.sqrt(Camp.horizontalDistSqr(c.blockPosition(), home));
		CompoundTag mem = data.memory(MEMORY);
		int[] explored = mem.getIntArray("explored").orElse(new int[SECTORS]);
		if (explored.length != SECTORS) {
			explored = new int[SECTORS];
		}
		explored = explored.clone();
		explored[sector] = Math.max(explored[sector], reached);
		mem.putIntArray("explored", explored);
		data.setDirty();
	}

	/** Looks over the loaded land around: structures worth knowing, and the biome underfoot. */
	private void sense(CompanionEntity c, ServerLevel level, Trips.State t) {
		boolean dirty = false;
		for (StructureSense.Found found : StructureSense.scan(level, c.blockPosition(), SENSE_CHUNKS)) {
			if (!Places.record(level, found.type(), found.pos())) {
				continue;
			}
			String what = article(Places.name(found.type())) + " around " + Compass.coords(found.pos());
			t.notes.add(Places.name(found.type()));
			dirty = true;
			Trips.announce(c, Line.TRIP_FIND, what);
			Camp.data(level.getServer()).addStat("trip_finds", 1);
			if (Places.OUTPOST.equals(found.type()) && !HOME.equals(t.phase)) {
				turnHomeQuietly(c, t); // pillagers: not a place to linger
			}
		}
		String biome = StructureSense.biomeType(level, c.blockPosition());
		if (biome != null && Places.record(level, biome, c.blockPosition())) {
			t.notes.add(Places.name(biome));
			dirty = true;
		}
		if (dirty) {
			Trips.save(c, t);
		}
	}

	private void turnHomeQuietly(CompanionEntity c, Trips.State t) {
		recordReach(c, t);
		t.phase = HOME;
		walker.reset();
	}

	/** Home again: tells everyone what came of the trip and lets go of the land out there. */
	private void finish(CompanionEntity c, ServerLevel level, Trips.State t) {
		Trips.announce(c, Line.TRIP_BACK, summary(t.notes));
		Trips.clear(c);
		ChunkLoader.stopRoaming(c);
		Camp.data(level.getServer()).addStat("far_trips", 1);
		trip = null;
	}

	/** "found a village, a ruined portal and the dark forest", or "found nothing new this time" (follows "I"). */
	static String summary(List<String> notes) {
		if (notes.isEmpty()) {
			return "found nothing new this time";
		}
		List<String> parts = new ArrayList<>();
		for (String note : notes) {
			parts.add(isPlaceType(note) ? article(note) : "the " + note);
		}
		return "found " + Trips.joinList(parts);
	}

	private static boolean isPlaceType(String name) {
		return name.equals("village") || name.equals("survivor camp") || name.equals("ruined portal")
			|| name.equals("pillager outpost") || name.equals("temple");
	}

	private static String article(String noun) {
		return (noun.isEmpty() || "aeiou".indexOf(noun.charAt(0)) < 0 ? "a " : "an ") + noun;
	}

	@Override
	public void stop(CompanionEntity c) {
		walker.reset();
		c.actions().reset();
		Trips.State t = trip;
		if (t != null && Trips.state(c) != null) {
			Trips.save(c, t);
		}
		if (Trips.home(c)) {
			ChunkLoader.stopRoaming(c); // otherwise the land stays loaded until they are back (see ChunkLoader)
		}
		trip = null;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 10;
	}

	@Override
	public int failureCooldown() {
		return 300;
	}

	@Override
	public int successCooldown() {
		return 1200;
	}
}
