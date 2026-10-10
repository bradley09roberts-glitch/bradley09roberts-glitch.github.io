package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.pets.PetsData.MapRecord;
import io.github.bradley09roberts.hardcorefriends.pets.PetsData.MapState;
import io.github.bradley09roberts.hardcorefriends.survival.Trips;

/**
 * The map maker brings a finished map home: into an item frame on the town hall's wall (or the camp library's, before
 * there is a town hall), or, with nowhere to hang it or no frame to be had, into the camp's chest. A frame is taken
 * from the chest or made at the crafting table from the camp's leather and sticks. Maps already put away in the chest
 * are fetched and hung once a hall stands with room on its walls (by the map maker). An explorer who drew a map while
 * they were the map maker brings it home all the same.
 */
final class HangMapTask implements CompanionTask {
	static final String ID = "pets.hang_map";
	private static final double CARRIED_SCORE = 40;
	private static final double STORED_SCORE = 18;
	private static final int PLAN_INTERVAL = 200;
	private static final double HANG_REACH = 2.5;
	/** After a map could not be hung (no room, or the edit rules said no), stored maps wait this long for another go. */
	private static final int BLOCKED_TICKS = 20 * 600;

	private enum Phase {
		CHEST,
		TABLE,
		HALL,
		STORE
	}

	private Phase phase = Phase.CHEST;
	private @Nullable MapRecord map;
	private MapFrames.@Nullable Spot spot;
	private long plannedAt = Long.MIN_VALUE / 2;
	private double planned;
	private long blockedUntil = Long.MIN_VALUE / 2;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return phase == Phase.STORE ? "putting a map away" : "hanging up a map";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.WORK || !Maps.isExplorer(c)
			|| !FriendsConfig.get().scoutMaps || c.getTarget() != null || c.isAsleep() || Camp.isNight(level)
			|| Camp.center(level).isEmpty() || Trips.state(c) != null || !Trips.home(c)) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - plannedAt < PLAN_INTERVAL && now >= plannedAt) {
			return planned;
		}
		plannedAt = now;
		PetsData data = PetsData.get(level.getServer());
		if (finishedCarried(c, data) != null) {
			planned = CARRIED_SCORE;
		} else if (Maps.isMaker(c) && now >= blockedUntil && FriendsConfig.get().allowWorldEditing
			&& !data.maps(MapState.STORED).isEmpty() && MapFrames.building(level).isPresent()
			&& (Workbench.carriesFrameMakings(c.backpack()) || Workbench.chestHasFrameMakings(level))
			&& Workbench.craftingTable(level) != null) {
			planned = STORED_SCORE;
		} else {
			planned = 0;
		}
		return planned;
	}

	private static @Nullable MapRecord finishedCarried(CompanionEntity c, PetsData data) {
		for (MapRecord m : data.carriedBy(c.getUUID())) {
			if (m.state == MapState.FINISHED && !Maps.carried(c, m.id).isEmpty()) {
				return m;
			}
		}
		return null;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		PetsData data = PetsData.get(level.getServer());
		map = finishedCarried(c, data);
		spot = null;
		Optional<String> hall = FriendsConfig.get().allowWorldEditing ? MapFrames.building(level) : Optional.empty();
		if (hall.isPresent()) {
			spot = MapFrames.freeSpot(level, hall.get());
		}
		if (map == null) {
			if (!Maps.isMaker(c)) {
				return false; // maps put away are the map maker's to hang
			}
			List<MapRecord> stored = data.maps(MapState.STORED);
			if (stored.isEmpty() || spot == null) {
				blockedUntil = level.getGameTime() + BLOCKED_TICKS; // no room on the walls just now
				return false;
			}
			map = stored.getFirst();
			phase = Phase.CHEST; // fetch the map (and a frame) from the chest
			return true;
		}
		if (spot == null) {
			phase = Phase.STORE;
		} else if (c.backpack().count(Workbench.FRAME) >= 1) {
			phase = Phase.HALL;
		} else {
			phase = Workbench.carriesFrameMakings(c.backpack()) ? Phase.TABLE : Phase.CHEST;
		}
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		MapRecord m = map;
		if (m == null) {
			return TaskStatus.FAILURE;
		}
		switch (phase) {
			case CHEST -> {
				ChestWalk.State walk = ChestWalk.tick(c);
				if (walk == ChestWalk.State.FAILED) {
					return TaskStatus.FAILURE;
				}
				if (walk != ChestWalk.State.ARRIVED) {
					return TaskStatus.RUNNING;
				}
				Optional<Container> chest = ChestWalk.chest(c);
				if (chest.isEmpty()) {
					return TaskStatus.FAILURE;
				}
				if (m.state == MapState.STORED && Maps.carried(c, m.id).isEmpty()) {
					if (!takeStored(c, chest.get(), m)) {
						m.state = MapState.GONE; // someone took it out of the chest
						PetsData.get(level.getServer()).setDirty();
						return TaskStatus.FAILURE;
					}
				}
				if (!Workbench.takeFrameMakings(level, chest.get(), c.backpack())) {
					return putAway(c, chest.get(), m);
				}
				phase = c.backpack().count(Workbench.FRAME) >= 1 ? Phase.HALL : Phase.TABLE;
				return TaskStatus.RUNNING;
			}
			case TABLE -> {
				BlockPos table = Workbench.craftingTable(level);
				if (table == null) {
					phase = Phase.STORE;
					return TaskStatus.RUNNING;
				}
				if (!Workbench.atTable(c, table)) {
					c.actions().walkTo(table, Workbench.TABLE_REACH);
					return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
				}
				c.actions().stopWalking();
				c.getLookControl().setLookAt(Vec3.atCenterOf(table));
				phase = Workbench.craftFrame(c) ? Phase.HALL : Phase.STORE;
				return TaskStatus.RUNNING;
			}
			case HALL -> {
				MapFrames.Spot at = spot;
				if (at == null) {
					phase = Phase.STORE;
					return TaskStatus.RUNNING;
				}
				// Within arm's reach of the spot (frames may hang a little above head height), standing near it.
				boolean close = c.actions().canReach(at.pos())
					&& c.position().distanceToSqr(Vec3.atBottomCenterOf(at.pos())) <= (HANG_REACH + 1) * (HANG_REACH + 1);
				if (!close) {
					c.actions().walkTo(at.pos(), HANG_REACH - 0.5);
					if (c.actions().isStuck()) {
						phase = Phase.STORE;
					}
					return TaskStatus.RUNNING;
				}
				c.actions().stopWalking();
				c.getLookControl().setLookAt(Vec3.atCenterOf(at.wall()));
				ItemStack held = Maps.carried(c, m.id);
				if (held.isEmpty()) {
					return TaskStatus.FAILURE;
				}
				Maps.decorate(level, held, m);
				if (c.backpack().count(Workbench.FRAME) < 1 || !MapFrames.hang(c, at, held)) {
					// The spot was taken or the guard said no (a player standing there, say): into the chest for now,
					// to be hung another time.
					blockedUntil = level.getGameTime() + BLOCKED_TICKS;
					phase = Phase.STORE;
					return TaskStatus.RUNNING;
				}
				c.backpack().remove(s -> Maps.isMap(s, m.id), 1);
				c.backpack().remove(Workbench.FRAME, 1);
				m.state = MapState.HUNG;
				m.holder = null;
				m.frame = at.pos();
				PetsData.get(level.getServer()).setDirty();
				return TaskStatus.SUCCESS;
			}
			case STORE -> {
				ChestWalk.State walk = ChestWalk.tick(c);
				if (walk == ChestWalk.State.FAILED) {
					return TaskStatus.FAILURE;
				}
				if (walk != ChestWalk.State.ARRIVED) {
					return TaskStatus.RUNNING;
				}
				Optional<Container> chest = ChestWalk.chest(c);
				return chest.isEmpty() ? TaskStatus.FAILURE : putAway(c, chest.get(), m);
			}
		}
		return TaskStatus.FAILURE;
	}

	/** Takes a stored map back out of the chest into the backpack. */
	private static boolean takeStored(CompanionEntity c, Container chest, MapRecord m) {
		if (SupplyChest.withdraw(chest, c.backpack(), s -> Maps.isMap(s, m.id), 1) < 1) {
			return false;
		}
		m.state = MapState.FINISHED;
		m.holder = c.getUUID();
		if (c.level() instanceof ServerLevel level) {
			PetsData.get(level.getServer()).setDirty();
			Maps.refreshCarried(level.getServer()); // kept in the backpack now, not tidied back into the chest
		}
		return true;
	}

	/** Puts the carried map in the chest (it stays the camp's, listed as put away). */
	private static TaskStatus putAway(CompanionEntity c, Container chest, MapRecord m) {
		ItemStack held = Maps.carried(c, m.id);
		if (held.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		if (c.level() instanceof ServerLevel level) {
			Maps.decorate(level, held, m);
		}
		if (SupplyChest.deposit(c.backpack(), chest, s -> Maps.isMap(s, m.id), 1) < 1) {
			return TaskStatus.FAILURE; // the chest is full
		}
		m.state = MapState.STORED;
		m.holder = null;
		if (c.level() instanceof ServerLevel level) {
			PetsData.get(level.getServer()).setDirty();
		}
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		map = null;
		spot = null;
		phase = Phase.CHEST;
		plannedAt = Long.MIN_VALUE / 2;
	}

	@Override
	public int successCooldown() {
		return 20 * 30;
	}

	@Override
	public int failureCooldown() {
		return 20 * 120;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
