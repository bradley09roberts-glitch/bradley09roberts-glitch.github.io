package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.pets.PetsData.MapState;
import io.github.bradley09roberts.hardcorefriends.survival.Trips;

/**
 * The map maker's own job: making an empty map from the camp's stock, at the crafting table (or the cartography table,
 * once the camp has one). The first becomes the map of the camp at once; after that the maker keeps one spare in their
 * backpack for their trips, where it becomes the map of wherever they go ({@link Maps}). Ingredients are real: eight
 * paper and a compass, the compass made from four iron the camp can spare and a redstone if there is none. When the
 * camp has never had a map and the chest is short, the maker says so once a day.
 */
final class MakeMapTask implements CompanionTask {
	static final String ID = "pets.make_map";
	private static final double CAMP_MAP_SCORE = 44;
	private static final double SPARE_SCORE = 24;
	private static final int PLAN_INTERVAL = 200;
	private static final String SHORT_SAID = "pets.maps_short_day";
	/** Days between reminders that the camp has nothing to make its first map from. */
	private static final int SHORT_EVERY_DAYS = 5;

	private enum Phase {
		CHEST,
		TABLE,
		MAKE
	}

	private Phase phase = Phase.CHEST;
	private @Nullable BlockPos table;
	private long plannedAt = Long.MIN_VALUE / 2;
	private double planned;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "making a map";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.WORK || !Maps.isMaker(c)
			|| c.getTarget() != null || c.isAsleep() || !c.isHealthy() || Camp.isNight(level) || Camp.isDusk(level)
			|| Camp.center(level).isEmpty() || Trips.state(c) != null) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - plannedAt < PLAN_INTERVAL && now >= plannedAt) {
			return planned;
		}
		plannedAt = now;
		planned = plan(c, level);
		return planned;
	}

	private static double plan(CompanionEntity c, ServerLevel level) {
		PetsData data = PetsData.get(level.getServer());
		boolean campMap = Maps.campMapHere(level).isEmpty();
		boolean spare = !campMap && Trips.allowed() && c.backpack().count(Workbench.EMPTY_MAP) < 1
			&& data.carriedBy(c.getUUID()).stream().noneMatch(m -> m.state == MapState.DRAWING && !m.camp);
		if (!campMap && !spare) {
			return 0;
		}
		if (c.backpack().count(Workbench.EMPTY_MAP) >= 1) {
			return CAMP_MAP_SCORE; // the camp's map, from the empty one already carried
		}
		if (!Workbench.carriesMapMakings(c.backpack()) && !Workbench.chestHasMapMakings(level)) {
			if (campMap) {
				sayShort(c, level);
			}
			return 0;
		}
		boolean readyMade = Workbench.stock(level, "map", Workbench.EMPTY_MAP) >= 1;
		if (!readyMade && Workbench.mapTable(level) == null) {
			return 0; // nowhere to make one
		}
		return campMap ? CAMP_MAP_SCORE : SPARE_SCORE;
	}

	/** The camp has no map yet and nothing to make one with: the maker says so, at most every few days. */
	private static void sayShort(CompanionEntity c, ServerLevel level) {
		long day = Camp.day(level);
		long said = c.extra().getLongOr(SHORT_SAID, Long.MIN_VALUE / 2);
		if (day - said >= SHORT_EVERY_DAYS || day < said) {
			c.extra().putLong(SHORT_SAID, day);
			Speech.say(c, Line.MAP_NO_PAPER);
		}
	}

	@Override
	public boolean start(CompanionEntity c) {
		table = null;
		if (c.backpack().count(Workbench.EMPTY_MAP) >= 1) {
			phase = Phase.MAKE;
		} else if (Workbench.carriesMapMakings(c.backpack())) {
			phase = Phase.TABLE;
		} else {
			phase = Phase.CHEST;
		}
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		switch (phase) {
			case CHEST -> {
				ChestWalk.State walk = ChestWalk.tick(c);
				if (walk == ChestWalk.State.FAILED) {
					return TaskStatus.FAILURE;
				}
				if (walk == ChestWalk.State.ARRIVED) {
					Optional<Container> chest = ChestWalk.chest(c);
					if (chest.isEmpty() || !Workbench.takeMapMakings(level, chest.get(), c.backpack())) {
						return TaskStatus.FAILURE;
					}
					phase = c.backpack().count(Workbench.EMPTY_MAP) >= 1 ? Phase.MAKE : Phase.TABLE;
				}
				return TaskStatus.RUNNING;
			}
			case TABLE -> {
				if (table == null) {
					table = Workbench.mapTable(level);
					if (table == null) {
						return TaskStatus.FAILURE;
					}
				}
				if (!Workbench.atTable(c, table)) {
					c.actions().walkTo(table, Workbench.TABLE_REACH);
					return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
				}
				c.actions().stopWalking();
				c.getLookControl().setLookAt(table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5);
				boolean crafting = level.getBlockState(table).is(Blocks.CRAFTING_TABLE);
				if (!Workbench.craftEmptyMap(c, crafting)) {
					// A compass needs a crafting table: go there if the cartography table could not make one.
					BlockPos crafter = Workbench.craftingTable(level);
					if (!crafting && crafter != null && !crafter.equals(table)) {
						table = crafter;
						return TaskStatus.RUNNING;
					}
					return TaskStatus.FAILURE;
				}
				phase = Phase.MAKE;
				return TaskStatus.RUNNING;
			}
			case MAKE -> {
				if (Maps.campMapHere(level).isEmpty() && !Maps.startCampMap(c)) {
					return TaskStatus.FAILURE;
				}
				return TaskStatus.SUCCESS;
			}
		}
		return TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		table = null;
		phase = Phase.CHEST;
		plannedAt = Long.MIN_VALUE / 2;
	}

	@Override
	public int successCooldown() {
		return 20 * 60;
	}

	@Override
	public int failureCooldown() {
		return 20 * 120;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
