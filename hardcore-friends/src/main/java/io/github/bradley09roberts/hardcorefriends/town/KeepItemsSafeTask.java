package io.github.bradley09roberts.hardcorefriends.town;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Keeping a fallen player's things safe (see {@link Mourning}): a friend within {@value Mourning#HELP_RANGE} blocks of
 * where a player died in the last five minutes drops what they are doing (urgent upkeep, 88), by day and with no
 * monster about, picks up the items lying within {@value Mourning#RANGE} blocks of the spot one by one into a bag named
 * after the player, then takes the bag to the supply chest. Several friends may help, each to different items. Not at
 * night, and given up as soon as a monster comes near; the bag stays in the backpack until the chest can take it.
 *
 * <p>It only starts when there is something for this friend to do: an item safe to fetch that no other friend is on
 * the way to and that this friend has not given up on (one they could not reach stays given up on while the death is
 * fresh), or a bag to put away in a chest with room for it. So it never starts over and over with nothing to do.
 */
final class KeepItemsSafeTask implements CompanionTask {
	static final String ID = "town.keep_items";
	private static final double SCORE = 88;
	/** Taking a bag already carried to the chest. */
	private static final double DELIVER_SCORE = 45;
	private static final double PICK_REACH = 1.5;
	/** An item not reached in this long (20 seconds) is given up on, like one the friend got stuck on. */
	private static final int MAX_WALK_TICKS = 400;

	private Mourning.@Nullable Spot planned;
	private Mourning.@Nullable Spot spot;
	private @Nullable ItemEntity target;
	private int walkTicks;
	/** Items this friend could not reach, kept while any death is fresh so they are not tried again and again. */
	private final Set<UUID> ignored = new HashSet<>();
	private boolean depositing;
	/** Items put in the bag this run: a run that gathered nothing and has no bag to put away has failed. */
	private int gathered;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Mourning.Spot s = spot;
		return depositing || s == null ? "putting a fallen player's things in the chest" : "keeping " + s.name + "'s things safe";
	}

	@Override
	public double score(CompanionEntity c) {
		planned = null;
		if (!(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.WORK) {
			return 0;
		}
		if (!Mourning.active()) {
			ignored.clear();
		}
		Mourning.Spot s = Mourning.spotFor(c, level, sp -> Mourning.hasRoom(c, sp) && Mourning.hasFreeItem(c, level, sp, ignored));
		if (s != null) {
			planned = s;
			return SCORE;
		}
		// A bag already gathered goes to the chest when it has room (otherwise the everyday deposit takes it later).
		if (c.backpack().has(Mourning::isKeepsake) && !Camp.isNight(level)
			&& SupplyChest.of(level).filter(KeepItemsSafeTask::hasEmptySlot).isPresent()) {
			return DELIVER_SCORE;
		}
		return 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		spot = planned;
		planned = null;
		target = null;
		walkTicks = 0;
		gathered = 0;
		depositing = spot == null;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Mourning.Spot s = spot;
		if (!depositing && (s == null || !Mourning.stillSafe(level, s))) {
			depositing = true;
		}
		if (depositing || s == null) {
			return deposit(c, level);
		}
		ItemEntity item = target;
		if (item == null || !Mourning.belongs(s, item) || !Mourning.fetchable(level, item)) {
			item = Mourning.nextItem(c, level, s, ignored);
			target = item;
			walkTicks = 0;
			if (item == null) {
				depositing = true;
				return TaskStatus.RUNNING;
			}
		}
		if (!c.actions().walkTo(item.blockPosition(), PICK_REACH)) {
			if (c.actions().isStuck() || ++walkTicks > MAX_WALK_TICKS) {
				ignored.add(item.getUUID());
				target = null;
				c.actions().stopWalking();
			}
			return TaskStatus.RUNNING;
		}
		ItemStack stack = item.getItem().copy();
		if (!Mourning.addToBag(c, s, stack)) {
			depositing = true; // no room for more: what is gathered goes to the chest, the rest waits for another trip
			return TaskStatus.RUNNING;
		}
		Mourning.announceKeeping(c, s);
		c.take(item, stack.getCount());
		item.discard();
		target = null;
		gathered += stack.getCount();
		Camp.data(level.getServer()).addStat("belongings_kept", stack.getCount());
		return TaskStatus.RUNNING;
	}

	/**
	 * To the chest with every bag carried. Without a chest, or with no room in it, the bags stay safe in the backpack
	 * (no walk to a full chest); the job is not offered again until the chest has room.
	 */
	private TaskStatus deposit(CompanionEntity c, ServerLevel level) {
		if (!c.backpack().has(Mourning::isKeepsake)) {
			return gathered > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE; // nothing gathered: wait before looking again
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty() || !hasEmptySlot(chest.get())) {
			return TaskStatus.FAILURE; // kept in the backpack until the chest has room
		}
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE; // kept in the backpack; tried again later
			}
			case ARRIVED -> {
			}
		}
		List<String> names = new ArrayList<>();
		for (ItemStack bag : c.backpack().stacks()) {
			if (Mourning.isKeepsake(bag)) {
				names.add(bag.getHoverName().getString());
			}
		}
		int moved = SupplyChest.deposit(c.backpack(), chest.get(), Mourning::isKeepsake, 64);
		if (moved > 0) {
			String where = Camp.data(level.getServer()).chestPos().map(Compass::coords).orElse("camp");
			Speech.announce(level.getServer(), Speech.prefix(c).append(Component.literal("put " + String.join(", ", names)
				+ " in the supply chest at " + where + ". Use the bag to unpack it.").withStyle(ChatFormatting.GRAY)));
			return TaskStatus.SUCCESS;
		}
		return TaskStatus.FAILURE; // the chest filled up: the bag stays safe in the backpack
	}

	/** A bag of belongings does not stack, so the chest needs a free slot for it. */
	private static boolean hasEmptySlot(Container chest) {
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void stop(CompanionEntity c) {
		Mourning.release(c);
		target = null;
		spot = null;
		depositing = false;
	}

	@Override
	public int failureCooldown() {
		return 200;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
