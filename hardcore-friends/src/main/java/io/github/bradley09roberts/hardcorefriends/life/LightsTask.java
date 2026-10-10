package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The winter lights: on the evening of the winter feast one friend puts up to {@value #LIGHTS} lights in a ring round
 * the square, lanterns from the supply chest (or torches, when the camp has plenty), each through the edit guard as
 * their own block. They glow through the night and are taken down again the next morning ({@link TidyUpTask}), going
 * back to the chest, so nothing is left in the square for good.
 */
final class LightsTask implements CompanionTask {
	static final String ID = "life.lights";
	private static final double SCORE = 62;
	/** Once the feast has begun, the lights come before joining in. */
	private static final double SCORE_LATE = 84;
	static final int LIGHTS = 8;
	private static final double RING = 7;
	private static final long FROM = 8500;
	private static final long UNTIL = 12300;
	/** Torches only from what the chest holds beyond this many (the camp's own lighting comes first). */
	private static final int TORCHES_SPARE = 24;

	private static long litDay = -1;
	private static @Nullable UUID lighter;

	private final List<BlockPos> spots = new ArrayList<>();
	private boolean fetched;
	private int index;
	private int tries;
	private int placed;

	static void clear() {
		litDay = -1;
		lighter = null;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "putting up the winter lights";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!FriendsConfig.get().villageLife || c.isChild() || !Places.free(c) || !(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		long day = Calendar.today(level.getServer());
		long time = Calendar.time(level.getServer());
		if (litDay == day || time < FROM || time >= UNTIL || Calendar.feastOn(day) != Calendar.Feast.WINTER_LIGHTS
			|| Camp.isNightTime(level)) {
			return 0;
		}
		if (lighter != null && !lighter.equals(c.getUUID()) && Places.loaded(level, lighter) != null) {
			return 0;
		}
		Gatherings.Gathering g = Gatherings.activeFor(c);
		boolean feastOn = g != null && g.kind == Gatherings.Kind.FEAST;
		if (!feastOn && LifeData.get(level.getServer()).isDone("feast:" + day)) {
			return 0;
		}
		return feastOn ? SCORE_LATE : SCORE;
	}

	private static boolean isLight(ItemStack s) {
		return s.is(Items.LANTERN) || s.is(Items.TORCH);
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos centre = Places.square(level);
		if (centre == null) {
			return false;
		}
		spots.clear();
		CampData camp = Camp.data(level.getServer());
		for (int i = 0; i < LIGHTS; i++) {
			double angle = Math.PI * 2 * i / LIGHTS + Math.PI / LIGHTS;
			BlockPos p = Spots.standable(level,
				centre.offset((int) Math.round(Math.cos(angle) * RING), 0, (int) Math.round(Math.sin(angle) * RING)));
			// Never against something a player built: the lights are the square's, for one night.
			if (p != null && Math.abs(p.getY() - centre.getY()) <= 3 && !WorldEditGuard.looksPlayerBuilt(level, p, 1, camp)) {
				spots.add(p);
			}
		}
		if (spots.isEmpty()) {
			return false;
		}
		lighter = c.getUUID();
		fetched = c.backpack().count(LightsTask::isLight) >= 4;
		index = 0;
		tries = 0;
		placed = 0;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (c.tickCount % 20 == 0 && Places.hostileNear(level, c.blockPosition(), Places.SPOIL_RANGE)) {
			return TaskStatus.FAILURE;
		}
		if (!fetched) {
			return fetch(c, level);
		}
		if (index >= spots.size() || !c.backpack().has(LightsTask::isLight)) {
			return done(c, level);
		}
		BlockPos spot = spots.get(index);
		if (!c.actions().canReach(spot)) {
			if (!c.actions().walkTo(spot, 2.0) && c.actions().isStuck()) {
				index++;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (c.tickCount % 10 != 0) {
			return TaskStatus.RUNNING;
		}
		ItemStack light = c.backpack().find(s -> s.is(Items.LANTERN));
		if (light.isEmpty()) {
			light = c.backpack().find(s -> s.is(Items.TORCH));
		}
		Item item = light.getItem();
		BlockState state = Block.byItem(item).defaultBlockState();
		if (level.getBlockState(spot).isAir() && state.canSurvive(level, spot)
			&& c.actions().place(spot, state, s -> s.is(item), WorldEditGuard.Reason.BUILD)) {
			// Recorded even when the camp's record of placed blocks is full: a light up for one night must come down again.
			Camp.data(level.getServer()).keepPlaced(level, spot, state);
			LifeData.get(level.getServer()).addTemp(Camp.dimensionId(level), spot, state.getBlock(), nextMorning(level));
			placed++;
			index++;
			tries = 0;
		} else if (++tries > 6) {
			index++;
			tries = 0;
		}
		return TaskStatus.RUNNING;
	}

	/** Lanterns from the chest, and torches to make up the number when the camp has plenty. */
	private TaskStatus fetch(CompanionEntity c, ServerLevel level) {
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			default -> {
			}
		}
		Container chest = SupplyChest.of(level).orElse(null);
		if (chest != null) {
			int want = spots.size() - c.backpack().count(LightsTask::isLight);
			want -= SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.LANTERN), Math.max(0, want));
			int torches = SupplyChest.count(chest, s -> s.is(Items.TORCH));
			if (want > 0 && torches > TORCHES_SPARE) {
				SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.TORCH), Math.min(want, torches - TORCHES_SPARE));
			}
		}
		if (!c.backpack().has(LightsTask::isLight)) {
			litDay = Calendar.today(level.getServer()); // nothing to light the square with this year
			return TaskStatus.FAILURE;
		}
		fetched = true;
		return TaskStatus.RUNNING;
	}

	private TaskStatus done(CompanionEntity c, ServerLevel level) {
		litDay = Calendar.today(level.getServer());
		if (placed > 0) {
			Speech.say(c, Line.LIGHTS_UP);
		}
		return placed > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	/** The overworld clock at the next morning, when the lights may come down. */
	private static long nextMorning(ServerLevel level) {
		long clock = level.getServer().overworld().getOverworldClockTime();
		return (clock / Calendar.DAY_TICKS + 1) * Calendar.DAY_TICKS + 1000;
	}

	@Override
	public void stop(CompanionEntity c) {
		if (c.getUUID().equals(lighter)) {
			lighter = null;
		}
	}

	@Override
	public int failureCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
