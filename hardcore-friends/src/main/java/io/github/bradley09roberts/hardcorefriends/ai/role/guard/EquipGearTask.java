package io.github.bradley09roberts.hardcorefriends.ai.role.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Aegis checks the supply chest for a better sword or axe, better armour pieces and a shield for an empty off hand,
 * puts them on and returns the replaced gear to the chest.
 */
public final class EquipGearTask implements CompanionTask {
	private static final int CHECK_INTERVAL = 100;
	private static final double CHEST_REACH = 2.0;

	/** One upgrade: take the item in {@code chestSlot} and wear it in {@code slot}. */
	private record Swap(EquipmentSlot slot, int chestSlot) {
	}

	private long nextCheck;
	private boolean upgradeWaiting;

	@Override
	public String id() {
		return "aegis.equip_gear";
	}

	@Override
	public String describe() {
		return "checking gear";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		if (now >= nextCheck) {
			nextCheck = now + CHECK_INTERVAL;
			upgradeWaiting = SupplyChest.of(level).map(chest -> !plan(c, chest).isEmpty()).orElse(false);
		}
		return upgradeWaiting ? 65 : 0;
	}

	/** Upgrades the chest offers right now, at most one per equipment slot. */
	private static List<Swap> plan(CompanionEntity c, Container chest) {
		List<Swap> swaps = new ArrayList<>();
		int heldRank = Gear.weaponRank(c.getMainHandItem());
		for (ItemStack s : c.backpack().stacks()) {
			heldRank = Math.max(heldRank, Gear.weaponRank(s));
		}
		int bestWeapon = -1;
		int bestRank = heldRank;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			int rank = Gear.weaponRank(chest.getItem(i));
			if (rank > bestRank) {
				bestRank = rank;
				bestWeapon = i;
			}
		}
		if (bestWeapon >= 0) {
			swaps.add(new Swap(EquipmentSlot.MAINHAND, bestWeapon));
		}
		for (EquipmentSlot slot : Gear.ARMOUR_SLOTS) {
			ItemStack worn = c.getItemBySlot(slot);
			int best = -1;
			double bestScore = Gear.armourScore(worn, slot);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack s = chest.getItem(i);
				if (Gear.betterArmour(s, worn, slot) && Gear.armourScore(s, slot) > bestScore) {
					bestScore = Gear.armourScore(s, slot);
					best = i;
				}
			}
			if (best >= 0) {
				swaps.add(new Swap(slot, best));
			}
		}
		if (c.getOffhandItem().isEmpty()) {
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (Gear.isShield(chest.getItem(i))) {
					swaps.add(new Swap(EquipmentSlot.OFFHAND, i));
					break;
				}
			}
		}
		return swaps;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		return upgradeWaiting && Camp.data(level.getServer()).chestPos().isPresent() && SupplyChest.of(level).isPresent();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<BlockPos> chestPos = Camp.data(level.getServer()).chestPos();
		if (chestPos.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(chestPos.get(), CHEST_REACH)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		Optional<Container> chest = SupplyChest.of(level);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(chestPos.get().getX() + 0.5, chestPos.get().getY() + 0.5, chestPos.get().getZ() + 0.5);
		List<Swap> swaps = plan(c, chest.get());
		upgradeWaiting = false;
		nextCheck = level.getGameTime() + CHECK_INTERVAL;
		if (swaps.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		// Take everything first so the chest slots in the plan stay valid, then put old gear back.
		List<ItemStack> replaced = new ArrayList<>();
		List<EquipmentSlot> replacedFrom = new ArrayList<>();
		for (Swap swap : swaps) {
			ItemStack taken = chest.get().removeItem(swap.chestSlot(), 1);
			if (taken.isEmpty()) {
				continue;
			}
			ItemStack old = c.getItemBySlot(swap.slot());
			c.setItemSlot(swap.slot(), taken);
			if (!old.isEmpty()) {
				replaced.add(old);
				replacedFrom.add(swap.slot());
			}
		}
		for (int i = 0; i < replaced.size(); i++) {
			stow(c, level, chest.get(), replaced.get(i), replacedFrom.get(i));
		}
		chest.get().setChanged();
		c.swingArm();
		c.equipBestWeapon();
		return TaskStatus.SUCCESS;
	}

	/** Old gear goes back into the chest; a non-weapon tool that was in hand stays in the backpack. */
	private static void stow(CompanionEntity c, ServerLevel level, Container chest, ItemStack old, EquipmentSlot from) {
		ItemStack left = old;
		if (from == EquipmentSlot.MAINHAND && !Gear.isWeapon(old)) {
			left = c.backpack().insert(left);
		}
		if (!left.isEmpty()) {
			left = SupplyChest.insert(chest, left);
		}
		if (!left.isEmpty()) {
			left = c.backpack().insert(left);
		}
		if (!left.isEmpty()) {
			c.spawnAtLocation(level, left);
		}
	}

	@Override
	public void stop(CompanionEntity c) {
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}
