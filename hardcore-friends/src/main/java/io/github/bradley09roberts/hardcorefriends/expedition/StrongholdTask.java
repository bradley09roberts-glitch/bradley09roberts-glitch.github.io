package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.survival.ChunkLoader;
import io.github.bradley09roberts.hardcorefriends.survival.Trips;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Scout finds the stronghold, the way a player does: when Sage's plan has eyes of ender and wants the stronghold,
 * she takes a few eyes from the chest, walks out past the camp's edge and throws one. It is a real eye of ender (the
 * players can watch it fly); she notes the way it flew and picks it up again if it did not break (vanilla: four times
 * in five). Then she walks {@value #SIDEWAYS} blocks across that line and throws again; where the two lines cross is
 * the stronghold. (Lines nearly parallel mean it is far away: she walks across once more and throws a third.) She
 * walks there, sets up a small marker (three cobblestone and a torch: only there, only into air, never near anything
 * player-built), comes home and tells everyone where it is. She never digs down. A trip of a day or more: she shelters
 * at night like anyone far from camp, and carries on in the morning; hurt or hungry, she turns back and reports what
 * she knows.
 */
public final class StrongholdTask implements CompanionTask {
	static final String ID = "scout.stronghold";
	private static final String STATE = "expedition.stronghold";
	/** How far across the first line she walks before the second throw. */
	private static final int SIDEWAYS = 200;
	/** How far out of camp she throws first. */
	private static final int OUT = 24;
	private static final int EYES_TAKEN = 3;
	private static final int MAX_THROWS = 3;
	/** Two lines this close to parallel (radians) cannot be trusted: throw again further across. */
	private static final double MIN_ANGLE = Math.toRadians(2.0);
	/** Further than this from the throws, the crossing is not believed. */
	private static final int MAX_DISTANCE = 6000;
	/** The longest the trip is remembered (three days): after that it is given up. */
	private static final long TRIP_MEMORY = 72000L;
	/** A roaming ticket lapses after a day and a half: it is asked for afresh this often on a long trip. */
	private static final long REFRESH_ROAMING = 12000L;
	private static final String MEMORY = "expedition.stronghold_search";

	private static final String PACK = "pack";
	private static final String OUT_PHASE = "out";
	private static final String THROW = "throw";
	private static final String COLLECT = "collect";
	private static final String ACROSS = "across";
	private static final String SPOT = "spot";
	private static final String MARK = "mark";
	private static final String HOME = "home";

	/** The column each friend may build a stronghold marker in, while marking. */
	private static final Map<UUID, BlockPos> MARKERS = new ConcurrentHashMap<>();

	private final Trips.Walker walker = new Trips.Walker();
	private @Nullable EyeOfEnder eye;
	private @Nullable Vec3 eyeLast;
	private int eyeGoneTicks;
	private @Nullable ItemEntity dropped;
	private int phaseTicks;
	private long roamingSince;
	private int markStep;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "following the eyes of ender to the stronghold";
	}

	// ------------------------------------------------------------------ state

	private static CompoundTag state(CompanionEntity c) {
		return c.extra().getCompoundOrEmpty(STATE);
	}

	private static boolean onTrip(CompanionEntity c) {
		return c.extra().contains(STATE);
	}

	private static void save(CompanionEntity c, CompoundTag tag) {
		c.extra().put(STATE, tag);
	}

	private static String phase(CompoundTag t) {
		return t.getStringOr("phase", PACK);
	}

	private static void setPhase(CompanionEntity c, CompoundTag t, String phase) {
		t.putString("phase", phase);
		save(c, t);
	}

	/** True when this friend may place a marker block here (the column they were given, while marking). */
	static boolean inMarkerColumn(CompanionEntity c, BlockPos pos) {
		BlockPos base = MARKERS.get(c.getUUID());
		return base != null && pos.getX() == base.getX() && pos.getZ() == base.getZ() && pos.getY() >= base.getY()
			&& pos.getY() <= base.getY() + 3;
	}

	static void clear() {
		MARKERS.clear();
	}

	// ------------------------------------------------------------------ score

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return 0;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return 0;
		}
		if (onTrip(c)) {
			long started = state(c).getLongOr("started", 0L);
			if (level.getGameTime() - started > TRIP_MEMORY || level.getGameTime() < started) {
				giveUp(c);
				return 0;
			}
			if (Camp.isNight(level)) {
				return 0; // the night shelter takes over; on in the morning
			}
			// At dusk far out she carries on (and shelters when night falls) rather than walking all the way back.
			return Camp.isDusk(level) && !Trips.home(c) ? 90 : 60;
		}
		MinecraftServer server = level.getServer();
		if (!ProgressPlan.enabled() || ProgressPlan.current(server) != Milestone.STRONGHOLD || !Trips.allowed()) {
			return 0;
		}
		if (Camp.isNight(level) || Camp.isDusk(level) || Camp.timeOfDay(level) > 5000) {
			return 0; // she sets off in the morning: it is a long way
		}
		if (data.memory(MEMORY).getLongOr("noneDay", -1) == Camp.day(level)
			|| ExpeditionData.get(server).latest(ExpeditionData.STRONGHOLD).isPresent()) {
			return 0;
		}
		if (!eyesAvailable(c, level) || !ChunkLoader.canRoam(server) || !Trips.fitToGo(c) || !Trips.campSafe(level, data)) {
			return 0;
		}
		return 58;
	}

	private static boolean eyesAvailable(CompanionEntity c, ServerLevel level) {
		if (c.backpack().has(s -> s.is(Items.ENDER_EYE))) {
			return true;
		}
		return SupplyChest.of(level).map(chest -> SupplyChest.count(chest, s -> s.is(Items.ENDER_EYE)) > 0).orElse(false);
	}

	// ------------------------------------------------------------------ start

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		walker.reset();
		eye = null;
		eyeLast = null;
		dropped = null;
		phaseTicks = 0;
		markStep = 0;
		if (!ChunkLoader.startRoaming(c, "finding the stronghold") && !Trips.home(c)) {
			return false;
		}
		roamingSince = level.getGameTime();
		if (onTrip(c)) {
			return true;
		}
		CompoundTag t = new CompoundTag();
		t.putString("phase", PACK);
		t.putLong("started", level.getGameTime());
		save(c, t);
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CompoundTag t = state(c);
		if (!onTrip(c)) {
			return TaskStatus.FAILURE;
		}
		phaseTicks++;
		if (phaseTicks % 100 == 0) {
			Trips.snack(c);
		}
		if (level.getGameTime() - roamingSince > REFRESH_ROAMING) {
			// A long trip outlasts one roaming ticket: let it go and ask again at once (nothing unloads in between).
			roamingSince = level.getGameTime();
			ChunkLoader.stopRoaming(c);
			ChunkLoader.startRoaming(c, "finding the stronghold");
		}
		String phase = phase(t);
		if (!HOME.equals(phase) && !PACK.equals(phase) && turnBack(c)) {
			setPhase(c, t, HOME);
			walker.reset();
			phase = HOME;
		}
		return switch (phase) {
			case PACK -> pack(c, level, t);
			case OUT_PHASE, ACROSS -> walkOut(c, level, t);
			case THROW -> throwEye(c, level, t);
			case COLLECT -> collect(c, level, t);
			case SPOT -> toSpot(c, level, t);
			case MARK -> mark(c, level, t);
			default -> home(c, level, t);
		};
	}

	/** Hurt or hungry with nothing to eat: time to go home with what she knows. */
	private static boolean turnBack(CompanionEntity c) {
		String why = Trips.turnBackReason(c);
		return "hurt".equals(why) || "hungry".equals(why);
	}

	// ------------------------------------------------------------------- pack

	private TaskStatus pack(CompanionEntity c, ServerLevel level, CompoundTag t) {
		switch (ChestWalk.tick(c)) {
			case ARRIVED -> ChestWalk.chest(c).ifPresent(chest -> {
				int eyes = c.backpack().count(Items.ENDER_EYE);
				if (eyes < EYES_TAKEN) {
					SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.ENDER_EYE), EYES_TAKEN - eyes);
				}
				Trips.packKit(c, chest);
				if (c.backpack().count(Items.COBBLESTONE) < 3) {
					SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.COBBLESTONE), 3 - c.backpack().count(Items.COBBLESTONE));
				}
				if (c.backpack().count(Items.TORCH) < 3) {
					SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.TORCH), 3 - c.backpack().count(Items.TORCH));
				}
				int food = KeepList.foodCount(c.backpack());
				if (food < 6) {
					SupplyChest.withdraw(chest, c.backpack(), KeepList::isFood, 6 - food);
				}
			});
			case FAILED -> {
			}
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
		}
		c.actions().stopWalking();
		if (!c.backpack().has(s -> s.is(Items.ENDER_EYE))) {
			giveUp(c);
			return TaskStatus.FAILURE;
		}
		// Out past the camp's edge, away from the chest, in any direction: the eyes will say which way.
		BlockPos home = c.homePos();
		double angle = level.getRandom().nextDouble() * Math.PI * 2;
		int distance = Camp.radius(Camp.data(level.getServer())) + OUT;
		BlockPos dest = home.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
		t.putLong("dest", dest.asLong());
		setPhase(c, t, OUT_PHASE);
		Trips.announce(c, Line.TRIP_START, "the land beyond the camp, eyes of ender in hand");
		phaseTicks = 0;
		return TaskStatus.RUNNING;
	}

	// ------------------------------------------------------------------- walk

	/** Out of camp (or across the first line): to the next place to throw from. */
	private TaskStatus walkOut(CompanionEntity c, ServerLevel level, CompoundTag t) {
		BlockPos dest = BlockPos.of(t.getLongOr("dest", c.blockPosition().asLong()));
		switch (walker.walk(c, dest, 4)) {
			case ARRIVED, BLOCKED -> {
				// Blocked on the way (water, a cliff): throw from here, as long as it is out of camp.
				if (Trips.home(c) && OUT_PHASE.equals(phase(t))) {
					giveUp(c);
					return TaskStatus.FAILURE;
				}
				setPhase(c, t, THROW);
				phaseTicks = 0;
			}
			case WALKING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	// ------------------------------------------------------------------ throw

	/** Throws an eye towards the stronghold and watches where it flies. */
	private TaskStatus throwEye(CompanionEntity c, ServerLevel level, CompoundTag t) {
		c.actions().stopWalking();
		EyeOfEnder flying = eye;
		if (flying == null) {
			BlockPos target = level.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, c.blockPosition(), 100, false);
			if (target == null) {
				// No stronghold in this world (a flat world, say): nothing to find.
				Camp.data(level.getServer()).memory(MEMORY).putLong("noneDay", Camp.day(level));
				Camp.data(level.getServer()).setDirty();
				giveUp(c);
				return TaskStatus.FAILURE;
			}
			ItemStack one = c.backpack().take(s -> s.is(Items.ENDER_EYE), 1);
			if (one.isEmpty()) {
				return afterThrows(c, level, t); // out of eyes: make do with the throws so far
			}
			EyeOfEnder thrown = new EyeOfEnder(level, c.getX(), c.getY(0.5), c.getZ());
			thrown.setItem(one);
			thrown.signalTo(Vec3.atLowerCornerOf(target));
			level.addFreshEntity(thrown);
			level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.NEUTRAL, 1.0F,
				0.33F + level.getRandom().nextFloat() * 0.17F);
			c.swingArm();
			eye = thrown;
			eyeLast = thrown.position();
			t.putLong("from", c.blockPosition().asLong());
			save(c, t);
			return TaskStatus.RUNNING;
		}
		if (flying.isAlive() && !flying.isRemoved()) {
			eyeLast = flying.position();
			c.getLookControl().setLookAt(flying.position());
			return TaskStatus.RUNNING;
		}
		// It has come down: the way it flew is the bearing.
		Vec3 last = eyeLast != null ? eyeLast : flying.position();
		BlockPos from = BlockPos.of(t.getLongOr("from", c.blockPosition().asLong()));
		double dx = last.x - (from.getX() + 0.5);
		double dz = last.z - (from.getZ() + 0.5);
		eye = null;
		if (dx * dx + dz * dz < 4) {
			return TaskStatus.RUNNING; // hardly moved: throw again
		}
		ListTag throwsTag = t.getListOrEmpty("throws");
		CompoundTag one = new CompoundTag();
		one.putDouble("x", from.getX() + 0.5);
		one.putDouble("z", from.getZ() + 0.5);
		one.putDouble("bearing", Math.atan2(dz, dx));
		throwsTag.add(one);
		t.put("throws", throwsTag);
		Trips.announce(c, Line.EYE_THROWN, Compass.direction(dx, dz));
		eyeGoneTicks = 0;
		dropped = null;
		setPhase(c, t, COLLECT);
		phaseTicks = 0;
		return TaskStatus.RUNNING;
	}

	/** Picks the eye up again if it did not break, then throws again further across, or works out the spot. */
	private TaskStatus collect(CompanionEntity c, ServerLevel level, CompoundTag t) {
		Vec3 last = eyeLast;
		if (dropped == null && last != null && ++eyeGoneTicks < 20) {
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(BlockPos.containing(last)).inflate(4, 10, 4),
				e -> e.isAlive() && e.getItem().is(Items.ENDER_EYE) && !(e.getOwner() instanceof net.minecraft.world.entity.player.Player))) {
				dropped = item;
				break;
			}
			return TaskStatus.RUNNING;
		}
		ItemEntity item = dropped;
		if (item != null && item.isAlive() && phaseTicks < 20 * 30) {
			if (c.distanceToSqr(item) <= 1.6 * 1.6) {
				Pickup.take(c, item);
				dropped = null;
			} else {
				c.actions().walkToEntity(item, 1.2);
				if (!c.actions().isStuck()) {
					return TaskStatus.RUNNING;
				}
			}
		}
		dropped = null;
		eyeLast = null;
		c.actions().stopWalking();
		return afterThrows(c, level, t);
	}

	/** With two throws or more, works out where the lines cross; otherwise walks across for the next throw. */
	private TaskStatus afterThrows(CompanionEntity c, ServerLevel level, CompoundTag t) {
		List<double[]> throwsMade = throwsOf(t);
		if (throwsMade.size() >= 2) {
			BlockPos spot = crossing(throwsMade);
			if (spot != null) {
				t.putLong("estimate", spot.asLong());
				t.putLong("dest", spot.asLong());
				setPhase(c, t, SPOT);
				walker.reset();
				phaseTicks = 0;
				return TaskStatus.RUNNING;
			}
		}
		if (throwsMade.size() >= MAX_THROWS || !c.backpack().has(s -> s.is(Items.ENDER_EYE)) || throwsMade.isEmpty()) {
			setPhase(c, t, HOME); // the eyes could not settle it this time
			walker.reset();
			return TaskStatus.RUNNING;
		}
		double[] lastThrow = throwsMade.getLast();
		double across = lastThrow[2] + Math.PI / 2;
		int x = (int) Math.round(lastThrow[0] + Math.cos(across) * SIDEWAYS);
		int z = (int) Math.round(lastThrow[1] + Math.sin(across) * SIDEWAYS);
		t.putLong("dest", new BlockPos(x, c.getBlockY(), z).asLong());
		setPhase(c, t, ACROSS);
		walker.reset();
		phaseTicks = 0;
		return TaskStatus.RUNNING;
	}

	private static List<double[]> throwsOf(CompoundTag t) {
		List<double[]> list = new ArrayList<>();
		for (Tag tag : t.getListOrEmpty("throws")) {
			if (tag instanceof CompoundTag one) {
				list.add(new double[] {one.getDoubleOr("x", 0), one.getDoubleOr("z", 0), one.getDoubleOr("bearing", 0)});
			}
		}
		return list;
	}

	/**
	 * Where the lines of two throws cross (the pair at the widest angle), in front of both and not absurdly far, at
	 * ground level when known. Null when they are too near parallel to trust.
	 */
	static @Nullable BlockPos crossing(List<double[]> throwsMade) {
		double[] a = null;
		double[] b = null;
		double best = 0;
		for (int i = 0; i < throwsMade.size(); i++) {
			for (int j = i + 1; j < throwsMade.size(); j++) {
				double angle = Math.abs(Math.sin(throwsMade.get(i)[2] - throwsMade.get(j)[2]));
				if (angle > best) {
					best = angle;
					a = throwsMade.get(i);
					b = throwsMade.get(j);
				}
			}
		}
		if (a == null || best < Math.sin(MIN_ANGLE)) {
			return null;
		}
		double ax = Math.cos(a[2]);
		double az = Math.sin(a[2]);
		double bx = Math.cos(b[2]);
		double bz = Math.sin(b[2]);
		double denom = ax * bz - az * bx;
		if (Math.abs(denom) < 1.0E-9) {
			return null;
		}
		double ta = ((b[0] - a[0]) * bz - (b[1] - a[1]) * bx) / denom;
		double tb = ((b[0] - a[0]) * az - (b[1] - a[1]) * ax) / denom;
		if (ta <= 0 || tb <= 0 || ta > MAX_DISTANCE || tb > MAX_DISTANCE) {
			return null;
		}
		return BlockPos.containing(a[0] + ax * ta, 64, a[1] + az * ta);
	}

	// ------------------------------------------------------------------ there

	private TaskStatus toSpot(CompanionEntity c, ServerLevel level, CompoundTag t) {
		BlockPos dest = BlockPos.of(t.getLongOr("dest", c.blockPosition().asLong()));
		switch (walker.walk(c, dest, 6)) {
			case ARRIVED -> {
				setPhase(c, t, MARK);
				markStep = 0;
				phaseTicks = 0;
			}
			case BLOCKED -> {
				setPhase(c, t, HOME); // no way across (the sea, a ravine): home with the spot reported unmarked
				walker.reset();
			}
			case WALKING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	/** Three blocks and a torch on the ground over the spot, so the players can find it. */
	private TaskStatus mark(CompanionEntity c, ServerLevel level, CompoundTag t) {
		BlockPos base = MARKERS.get(c.getUUID());
		if (base == null) {
			base = markerBase(c, level, BlockPos.of(t.getLongOr("estimate", c.blockPosition().asLong())));
			if (base == null || phaseTicks > 20 * 60) {
				return markDone(c, t);
			}
			MARKERS.put(c.getUUID(), base);
		}
		if (markStep >= 4 || phaseTicks > 20 * 60) {
			return markDone(c, t);
		}
		BlockPos pos = base.above(markStep);
		BlockState want = markStep < 3 ? (c.backpack().has(s -> s.is(Items.COBBLESTONE)) ? Blocks.COBBLESTONE.defaultBlockState()
			: Blocks.DIRT.defaultBlockState()) : Blocks.TORCH.defaultBlockState();
		if (level.getBlockState(pos).is(want.getBlock())) {
			markStep++;
			return TaskStatus.RUNNING;
		}
		if (!c.actions().canReach(pos)) {
			c.actions().walkTo(base.east(), 1.5);
			if (c.actions().isStuck()) {
				return markDone(c, t);
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (level.getGameTime() - c.lastEditTick() < 4) {
			return TaskStatus.RUNNING;
		}
		if (c.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos))) {
			c.actions().walkTo(base.east(2), 0.5); // standing in the way of their own marker
			return TaskStatus.RUNNING;
		}
		boolean placed = c.actions().place(pos, want, s -> s.is(want.getBlock().asItem()), WorldEditGuard.Reason.EXPEDITION);
		if (!placed) {
			return markDone(c, t); // nothing to build it with, or not allowed here: the coordinates will do
		}
		markStep++;
		return TaskStatus.RUNNING;
	}

	/**
	 * Where the marker goes: open ground at the spot or within four blocks of it, with three blocks of air above (and
	 * one for the torch), not in water. Null if there is none.
	 */
	private static @Nullable BlockPos markerBase(CompanionEntity c, ServerLevel level, BlockPos spot) {
		for (int r = 0; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = spot.getX() + dx;
					int z = spot.getZ() + dz;
					if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
						continue;
					}
					BlockPos base = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
					BlockState ground = level.getBlockState(base.below());
					if (!ground.isFaceSturdy(level, base.below(), net.minecraft.core.Direction.UP) || !ground.getFluidState().isEmpty()) {
						continue;
					}
					boolean air = true;
					for (int dy = 0; dy < 4 && air; dy++) {
						air = level.getBlockState(base.above(dy)).isAir();
					}
					if (air) {
						return base;
					}
				}
			}
		}
		return null;
	}

	private TaskStatus markDone(CompanionEntity c, CompoundTag t) {
		MARKERS.remove(c.getUUID());
		t.putBoolean("marked", markStep >= 3);
		setPhase(c, t, HOME);
		walker.reset();
		phaseTicks = 0;
		return TaskStatus.RUNNING;
	}

	// ------------------------------------------------------------------- home

	private TaskStatus home(CompanionEntity c, ServerLevel level, CompoundTag t) {
		if (Trips.home(c)) {
			finish(c, level, t);
			return TaskStatus.SUCCESS;
		}
		return switch (walker.walk(c, c.homePos(), 6)) {
			case ARRIVED -> {
				finish(c, level, t);
				yield TaskStatus.SUCCESS;
			}
			case BLOCKED -> TaskStatus.FAILURE; // tried again in a while; the trip is remembered
			case WALKING -> TaskStatus.RUNNING;
		};
	}

	/** Home: tells everyone where the stronghold is, and the plan moves on. */
	private void finish(CompanionEntity c, ServerLevel level, CompoundTag t) {
		MinecraftServer server = level.getServer();
		if (t.contains("estimate")) {
			BlockPos spot = BlockPos.of(t.getLongOr("estimate", 0L));
			String where = "x " + spot.getX() + ", z " + spot.getZ();
			Trips.announce(c, Line.STRONGHOLD_FOUND, where);
			Camp.data(server).addPoi(ExpeditionData.STRONGHOLD, spot, level.getGameTime());
			ExpeditionData.get(server).addPlace(ExpeditionData.STRONGHOLD, Travel.dimId(level), spot, level.getGameTime());
			Camp.data(server).addStat("strongholds_found", 1);
			if (ProgressPlan.enabled()) {
				ProgressPlan.complete(server, Milestone.STRONGHOLD);
			}
		} else {
			Trips.announce(c, Line.TRIP_BACK, "could not settle where the stronghold is this time");
		}
		giveUp(c);
	}

	/** Forgets the trip and lets go of the land out there. */
	private void giveUp(CompanionEntity c) {
		c.extra().remove(STATE);
		MARKERS.remove(c.getUUID());
		ChunkLoader.stopRoaming(c);
		eye = null;
		dropped = null;
	}

	@Override
	public void stop(CompanionEntity c) {
		walker.reset();
		c.actions().reset();
		MARKERS.remove(c.getUUID());
		eye = null; // an eye in the air when called away is lost: another is thrown on the way back
		dropped = null;
		if (Trips.home(c) && !onTrip(c)) {
			ChunkLoader.stopRoaming(c);
		}
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 10;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int successCooldown() {
		return 2400;
	}
}
