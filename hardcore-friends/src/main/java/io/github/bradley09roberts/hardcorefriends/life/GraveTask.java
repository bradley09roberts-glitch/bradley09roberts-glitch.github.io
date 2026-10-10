package io.github.bradley09roberts.hardcorefriends.life;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Making a grave for someone who died: a grown-up fetches a headstone (a stone wall, or a stone block), a sign (or six
 * planks and a stick to make one at the crafting table) and a flower or two from the supply chest, walks to the next
 * free place in the cemetery ({@link Graves}), and puts up the headstone, the sign with the name written on it, and the
 * flowers, each through the edit guard (BUILD inside the camp). Then the grave's ground is reserved as a camp site so
 * nothing is ever built over it. One friend at a time, by day, in the camp. A grave whose cemetery has no room left, or
 * whose spot nobody can walk to, is tried again later, up to {@value #MAX_TRIES} times (a spot that could not be reached
 * twice is let go for another); the name is in the Chronicle either way. A grave that failed waits behind the others for
 * the rest of the day, so one that cannot be made does not hold up the graves after it.
 */
final class GraveTask implements CompanionTask {
	static final String ID = "life.grave";
	private static final double SCORE = 52;
	static final int MAX_TRIES = 6;
	private static final int PLACE_EVERY = 10;
	private static final int GIVE_UP_PLACING = 20 * 8;
	/** A walk to the grave longer than this has gone wrong (round and round below a ledge, most likely). */
	private static final int WALK_LIMIT = 20 * 60;

	/** The friend making a grave just now: one at a time. */
	private static @Nullable UUID digger;
	/** The day the camp had no stone for a headstone (tried again the next day). */
	private static long noStoneDay = -1;
	/** Whether any grave waits to be made, looked at every few seconds rather than by every friend every second. */
	private static boolean pending;
	private static long pendingAt = Long.MIN_VALUE;

	private enum Step {
		FETCH,
		WALK,
		BUILD
	}

	private LifeData.@Nullable Grave grave;
	private Step step = Step.FETCH;
	private int ticks;
	private int stuckPlacing;
	private int part;
	private @Nullable BlockPos stand;
	private int walkFrom;

	static void clear() {
		digger = null;
		noStoneDay = -1;
		pending = false;
		pendingAt = Long.MIN_VALUE;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		LifeData.Grave g = grave;
		return g == null ? "making a grave" : "making a grave for " + g.name;
	}

	@Override
	public double score(CompanionEntity c) {
		if (!FriendsConfig.get().graves || c.isChild() || !Places.free(c) || !(c.level() instanceof ServerLevel level)
			|| Camp.isNight(level) || Calendar.time(level.getServer()) > 11500) {
			return 0;
		}
		if (digger != null && !digger.equals(c.getUUID()) && Places.loaded(level, digger) != null
			|| noStoneDay == Calendar.today(level.getServer())) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - pendingAt >= 100 || now < pendingAt) {
			pendingAt = now;
			pending = next(level) != null;
		}
		return pending ? SCORE : 0;
	}

	/**
	 * The oldest grave still to be made in this world, not yet given up. One that already failed today comes after the
	 * others, so a grave that cannot be made just now does not hold up the rest.
	 */
	private static LifeData.@Nullable Grave next(ServerLevel level) {
		LifeData data = LifeData.get(level.getServer());
		String dim = Camp.dimensionId(level);
		long today = Calendar.today(level.getServer());
		LifeData.Grave later = null;
		for (LifeData.Grave g : data.graves) {
			if (!g.made && g.tries < MAX_TRIES && (g.dimension.isEmpty() || g.dimension.equals(dim))) {
				if (g.failedDay != today) {
					return g;
				}
				if (later == null) {
					later = g;
				}
			}
		}
		return later;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		grave = next(level);
		if (grave == null) {
			return false;
		}
		digger = c.getUUID();
		ticks = 0;
		stuckPlacing = 0;
		part = 0;
		stand = null;
		step = hasMaterials(c) ? Step.WALK : Step.FETCH;
		return true;
	}

	private static boolean hasMaterials(CompanionEntity c) {
		return c.backpack().has(Graves::isHeadstone) && c.backpack().has(Graves::isSign);
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		LifeData.Grave g = grave;
		if (g == null || g.made || Camp.isNight(level) || ++ticks > maxTicks()) {
			return TaskStatus.FAILURE;
		}
		if (c.tickCount % 20 == 0 && Places.hostileNear(level, c.blockPosition(), Places.SPOIL_RANGE)) {
			return TaskStatus.FAILURE; // the grave can wait; the reflexes see to the danger
		}
		return switch (step) {
			case FETCH -> fetch(c, level);
			case WALK -> walk(c, level, g);
			case BUILD -> build(c, level, g);
		};
	}

	/** At the supply chest: a headstone, a sign (or what makes one) and up to two flowers. */
	private TaskStatus fetch(CompanionEntity c, ServerLevel level) {
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return hasMaterials(c) ? goTo(Step.WALK) : TaskStatus.FAILURE;
			}
			default -> {
			}
		}
		Container chest = SupplyChest.of(level).orElse(null);
		if (chest == null) {
			return TaskStatus.FAILURE;
		}
		if (!c.backpack().has(Graves::isHeadstone)) {
			for (Item stone : Graves.HEADSTONES) {
				// Cobblestone, the plainest, only when the camp has plenty for its building.
				int keep = stone == Items.COBBLESTONE ? 64 : 0;
				if (SupplyChest.count(chest, s -> s.is(stone)) > keep && SupplyChest.withdraw(chest, c.backpack(), s -> s.is(stone), 1) > 0) {
					break;
				}
			}
		}
		if (!c.backpack().has(Graves::isSign) && SupplyChest.withdraw(chest, c.backpack(), Graves::isSign, 1) == 0
			&& SupplyChest.count(chest, s -> s.is(ItemTags.PLANKS)) >= 32) {
			// Make one at the crafting table by the chest: six planks and a stick for three signs (the rest go back).
			SupplyChest.withdraw(chest, c.backpack(), s -> s.is(ItemTags.PLANKS), 6);
			if (!c.backpack().has(s -> s.is(Items.STICK))) {
				SupplyChest.withdraw(chest, c.backpack(), s -> s.is(Items.STICK), 1);
			}
			Crafting.ensure(c, Items.OAK_SIGN, 1);
		}
		if (c.backpack().count(Graves::isFlower) < 2) {
			SupplyChest.withdraw(chest, c.backpack(), Graves::isFlower, 2 - c.backpack().count(Graves::isFlower));
		}
		if (!c.backpack().has(Graves::isHeadstone)) {
			noStoneDay = Calendar.today(level.getServer()); // nothing to make a headstone of: tomorrow, when the camp has stone
			return TaskStatus.FAILURE;
		}
		return goTo(Step.WALK);
	}

	private TaskStatus goTo(Step s) {
		step = s;
		stuckPlacing = 0;
		return TaskStatus.RUNNING;
	}

	private TaskStatus walk(CompanionEntity c, ServerLevel level, LifeData.Grave g) {
		if (stand == null) {
			LifeData data = LifeData.get(level.getServer());
			if (Graves.place(level, data, g) == null) {
				return failed(level, g);
			}
			stand = Graves.visitorSpot(level, g);
			if (stand == null) {
				if (!g.made && !Camp.data(level.getServer()).isPlacedByFriends(level, g.pos)) {
					g.pos = null; // the spot in front was blocked since: look for another place next time
				}
				return failed(level, g);
			}
			walkFrom = ticks;
		}
		if (c.actions().walkTo(stand, 1.2)) {
			step = Step.BUILD;
			return TaskStatus.RUNNING;
		}
		return c.actions().isStuck() || ticks - walkFrom > WALK_LIMIT ? unreachable(level, g) : TaskStatus.RUNNING;
	}

	/**
	 * Nobody could walk to the grave's spot: a failed try. The second time the spot is let go (unless its headstone is
	 * already up), so the next try takes another one.
	 */
	private static TaskStatus unreachable(ServerLevel level, LifeData.Grave g) {
		BlockPos h = g.pos;
		if (++g.walkFails >= 2 && h != null && !Camp.data(level.getServer()).isPlacedByFriends(level, h)) {
			g.walkFails = 0;
			Graves.letGo(level, LifeData.get(level.getServer()), g);
		}
		return failed(level, g);
	}

	/** A failed try at a grave: counted, so a hopeless one is given up, and the grave waits behind the others today. */
	private static TaskStatus failed(ServerLevel level, LifeData.Grave g) {
		g.tries++;
		g.failedDay = Calendar.today(level.getServer());
		LifeData.get(level.getServer()).setDirty();
		pendingAt = Long.MIN_VALUE;
		return TaskStatus.FAILURE;
	}

	/** The headstone, then the sign (written on), then the flowers, one every half second. */
	private TaskStatus build(CompanionEntity c, ServerLevel level, LifeData.Grave g) {
		BlockPos h = g.pos;
		if (h == null) {
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(h.getX() + 0.5, h.getY() + 0.5, h.getZ() + 0.5);
		if (ticks % PLACE_EVERY != 0) {
			return TaskStatus.RUNNING;
		}
		if (++stuckPlacing > GIVE_UP_PLACING / PLACE_EVERY) {
			return failed(level, g); // someone standing in the way, most likely: try again later, but not for ever
		}
		Direction toward = Graves.facing(g);
		switch (part) {
			case 0 -> {
				ItemStack stone = c.backpack().find(Graves::isHeadstone);
				if (stone.isEmpty()) {
					return TaskStatus.FAILURE;
				}
				Item item = stone.getItem();
				BlockState state = Block.byItem(item).defaultBlockState();
				if (level.getBlockState(h).is(state.getBlock()) && Camp.data(level.getServer()).isPlacedByFriends(level, h)
					|| c.actions().place(h, state, s -> s.is(item), WorldEditGuard.Reason.BUILD)) {
					reserve(level, g); // from the first stone on, nothing else is put on this ground
					part = 1;
					stuckPlacing = 0;
				}
			}
			case 1 -> {
				BlockPos signPos = Graves.signPos(g);
				ItemStack sign = c.backpack().find(Graves::isSign);
				if (sign.isEmpty() && !(level.getBlockState(signPos).getBlock() instanceof StandingSignBlock)) {
					part = 2; // no sign to be had: a plain headstone, the name kept in the Chronicle
					return TaskStatus.RUNNING;
				}
				Item item = sign.getItem();
				BlockState state = Graves.signState(sign, toward);
				if (level.getBlockState(signPos).getBlock() instanceof StandingSignBlock
					&& Camp.data(level.getServer()).isPlacedByFriends(level, signPos)) {
					Graves.inscribe(level, signPos, g); // put up on an earlier try
					part = 2;
					stuckPlacing = 0;
				} else if (state.canSurvive(level, signPos) && c.actions().place(signPos, state, s -> s.is(item), WorldEditGuard.Reason.BUILD)) {
					Graves.inscribe(level, signPos, g);
					part = 2;
					stuckPlacing = 0;
				} else if (!state.canSurvive(level, signPos)) {
					part = 2;
				}
			}
			default -> {
				List<BlockPos> spots = Graves.flowerSpots(g);
				int index = part - 2;
				if (index >= spots.size() || !c.backpack().has(Graves::isFlower)) {
					return finish(c, level, g);
				}
				BlockPos spot = spots.get(index);
				ItemStack flower = c.backpack().find(Graves::isFlower);
				Item item = flower.getItem();
				BlockState state = Block.byItem(item).defaultBlockState();
				if (!level.getBlockState(spot).isAir() || !state.canSurvive(level, spot)
					|| c.actions().place(spot, state, s -> s.is(item), WorldEditGuard.Reason.BUILD)) {
					part++;
					stuckPlacing = 0;
				}
			}
		}
		return TaskStatus.RUNNING;
	}

	/** The grave stands: its ground is reserved as a camp site, and the maker says so. */
	private TaskStatus finish(CompanionEntity c, ServerLevel level, LifeData.Grave g) {
		LifeData data = LifeData.get(level.getServer());
		g.made = true;
		reserve(level, g);
		data.setDirty();
		pendingAt = Long.MIN_VALUE;
		Speech.say(c, Line.GRAVE_MADE, g.name);
		return TaskStatus.SUCCESS;
	}

	/** Reserves the grave's ground as a camp site of its own (a 3 by 3 box round the headstone). */
	private static void reserve(ServerLevel level, LifeData.Grave g) {
		CampData camp = Camp.data(level.getServer());
		if (g.pos != null && camp.site(g.siteKey()).isEmpty()) {
			camp.putSite(g.siteKey(), new CampData.Site(g.pos, 0, 0));
		}
	}

	@Override
	public void stop(CompanionEntity c) {
		if (c.getUUID().equals(digger)) {
			digger = null;
		}
		grave = null;
		stand = null;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60 * 3;
	}

	@Override
	public int maxTicks() {
		return 20 * 150;
	}
}
