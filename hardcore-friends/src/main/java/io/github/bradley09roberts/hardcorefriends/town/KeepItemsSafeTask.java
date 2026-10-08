package io.github.bradley09roberts.hardcorefriends.town;

import java.util.HashSet;
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
 */
final class KeepItemsSafeTask implements CompanionTask {
	static final String ID = "town.keep_items";
	private static final double SCORE = 88;
	/** Taking a bag already carried to the chest. */
	private static final double DELIVER_SCORE = 45;
	private static final double PICK_REACH = 1.5;

	private Mourning.@Nullable Spot planned;
	private Mourning.@Nullable Spot spot;
	private @Nullable ItemEntity target;
	private final Set<UUID> ignored = new HashSet<>();
	private boolean depositing;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Mourning.Spot s = spot;
		return depositing || s == null ? "putting a fallen friend's things in the chest" : "keeping " + s.name + "'s things safe";
	}

	@Override
	public double score(CompanionEntity c) {
		planned = null;
		if (!(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.WORK) {
			return 0;
		}
		Mourning.Spot s = Mourning.spotFor(c, level);
		if (s != null && Mourning.hasRoom(c, s)) {
			planned = s;
			return SCORE;
		}
		// A bag already gathered goes to the chest when there is one (otherwise the everyday deposit takes it later).
		if (c.backpack().has(Mourning::isKeepsake) && !Camp.isNight(level) && SupplyChest.of(level).isPresent()) {
			return DELIVER_SCORE;
		}
		return 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		spot = planned;
		planned = null;
		target = null;
		ignored.clear();
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
		if (depositing) {
			return deposit(c, level);
		}
		ItemEntity item = target;
		if (item == null || !Mourning.belongs(s, item)) {
			item = Mourning.nextItem(c, level, s, ignored);
			target = item;
			if (item == null) {
				depositing = true;
				return TaskStatus.RUNNING;
			}
		}
		if (!c.actions().walkTo(item.blockPosition(), PICK_REACH)) {
			if (c.actions().isStuck()) {
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
		Camp.data(level.getServer()).addStat("belongings_kept", stack.getCount());
		return TaskStatus.RUNNING;
	}

	/** To the chest with every bag carried; without a chest, they stay in the backpack and everyone is told. */
	private TaskStatus deposit(CompanionEntity c, ServerLevel level) {
		if (!c.backpack().has(Mourning::isKeepsake)) {
			return TaskStatus.SUCCESS;
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
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		java.util.List<String> names = new java.util.ArrayList<>();
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
		return TaskStatus.FAILURE; // the chest is full: the bag stays safe in the backpack
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
	public int maxTicks() {
		return 20 * 120;
	}
}
