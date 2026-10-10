package io.github.bradley09roberts.hardcorefriends.life;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Visiting a grave: someone in mourning goes once a day to the grave of the one they lost, and family keep going now
 * and then afterwards (about one day in five). They stand before it a while, say a few words, and a grown-up leaves a
 * flower beside the headstone if a place there is empty and a flower can be had (carried, or from the supply chest).
 * A visit eases the grief a little. A needs job ({@code needs.remember}), so children visit too, without the flowers
 * (children change no blocks). By day only, never with a monster near.
 */
final class VisitGraveTask implements CompanionTask {
	static final String ID = "needs.remember";
	private static final double MOURNING = 30;
	private static final double FAMILY = 22;
	private static final long FROM = 1500;
	private static final long UNTIL = 10500;
	private static final int STAY = 20 * 12;

	private LifeData.@Nullable Grave grave;
	private @Nullable BlockPos stand;
	private boolean fetching;
	private boolean arrived;
	private int ticks;
	private boolean spoke;
	private boolean flowerDone;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		LifeData.Grave g = grave;
		return g == null ? "visiting a grave" : "visiting " + g.name + "'s grave";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!Places.free(c) || !(c.level() instanceof ServerLevel level) || Camp.isNight(level) || c.getTarget() != null) {
			return 0;
		}
		long time = Calendar.time(level.getServer());
		if (time < FROM || time >= UNTIL) {
			return 0;
		}
		LifeData data = LifeData.get(level.getServer());
		if (data.graves.isEmpty()) {
			return 0;
		}
		LifeData.Mourn m = data.mourning.get(c.getUUID());
		LifeData.Grave g = graveFor(c, level, data, m);
		if (g == null) {
			return 0;
		}
		return m != null && m.name().equals(g.name) ? MOURNING : FAMILY;
	}

	/**
	 * The grave this friend would visit today: that of the one they mourn, else one of their family's on its day (about
	 * one day in five, the day set by who they are, so family do not all go at once).
	 */
	private static LifeData.@Nullable Grave graveFor(CompanionEntity c, ServerLevel level, LifeData data, LifeData.@Nullable Mourn m) {
		long day = Calendar.today(level.getServer());
		boolean familyDay = Math.floorMod(day + c.getUUID().hashCode(), 5) == 0;
		if (m == null && !familyDay) {
			return null;
		}
		String dim = Camp.dimensionId(level);
		for (LifeData.Grave g : data.graves) {
			if (!g.made || g.pos == null || !g.dimension.equals(dim)) {
				continue;
			}
			if (m != null && m.name().equals(g.name) || familyDay && g.family.contains(c.getUUID())) {
				return g;
			}
		}
		return null;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		LifeData data = LifeData.get(level.getServer());
		grave = graveFor(c, level, data, data.mourning.get(c.getUUID()));
		if (grave == null) {
			return false;
		}
		stand = Graves.visitorSpot(level, grave);
		if (stand == null) {
			return false;
		}
		arrived = false;
		spoke = false;
		ticks = 0;
		flowerDone = c.isChild() || !emptyFlowerSpot(level, grave);
		fetching = !flowerDone && !c.backpack().has(Graves::isFlower) && SupplyChest.of(level)
			.map(chest -> SupplyChest.count(chest, Graves::isFlower) > 0).orElse(false);
		if (!fetching && !c.backpack().has(Graves::isFlower)) {
			flowerDone = true;
		}
		return true;
	}

	private static boolean emptyFlowerSpot(ServerLevel level, LifeData.Grave g) {
		for (BlockPos p : Graves.flowerSpots(g)) {
			if (level.isLoaded(p) && level.getBlockState(p).isAir()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		LifeData.Grave g = grave;
		BlockPos to = stand;
		if (g == null || to == null || g.pos == null) {
			return TaskStatus.FAILURE;
		}
		ticks++;
		if (ticks % 20 == 0 && Places.hostileNear(level, c.blockPosition(), Places.SPOIL_RANGE)) {
			return TaskStatus.FAILURE;
		}
		if (fetching) {
			switch (ChestWalk.tick(c)) {
				case WALKING -> {
					return TaskStatus.RUNNING;
				}
				case FAILED -> fetching = false;
				default -> {
					SupplyChest.of(level).ifPresent((Container chest) -> SupplyChest.withdraw(chest, c.backpack(), Graves::isFlower, 1));
					fetching = false;
				}
			}
			if (!c.backpack().has(Graves::isFlower)) {
				flowerDone = true;
			}
			return TaskStatus.RUNNING;
		}
		if (!arrived) {
			if (c.actions().walkTo(to, 1.0)) {
				arrived = true;
				ticks = 0;
			} else if (c.actions().isStuck()) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		c.getLookControl().setLookAt(g.pos.getX() + 0.5, g.pos.getY() + 0.5, g.pos.getZ() + 0.5);
		if (!spoke && ticks >= 40) {
			spoke = true;
			Speech.say(c, Line.GRAVE_VISIT, g.name);
		}
		if (!flowerDone && ticks >= 80 && ticks % 10 == 0) {
			flowerDone = leaveFlower(c, level, g) || ticks > 160;
		}
		if (ticks >= STAY) {
			c.needs().add(Needs.Need.SOCIAL, 4);
			c.needs().add(Needs.Need.FUN, 3);
			return TaskStatus.SUCCESS;
		}
		return TaskStatus.RUNNING;
	}

	/** A flower on an empty spot beside the headstone, through the edit guard. True once done (or nothing to do). */
	private static boolean leaveFlower(CompanionEntity c, ServerLevel level, LifeData.Grave g) {
		ItemStack flower = c.backpack().find(Graves::isFlower);
		if (flower.isEmpty()) {
			return true;
		}
		Item item = flower.getItem();
		BlockState state = Block.byItem(item).defaultBlockState();
		for (BlockPos p : Graves.flowerSpots(g)) {
			if (level.isLoaded(p) && level.getBlockState(p).isAir() && state.canSurvive(level, p)) {
				return c.actions().place(p, state, s -> s.is(item), WorldEditGuard.Reason.BUILD);
			}
		}
		return true;
	}

	@Override
	public void stop(CompanionEntity c) {
		grave = null;
		stand = null;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60 * 8;
	}

	@Override
	public int successCooldown() {
		return 20 * 60 * 20;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
