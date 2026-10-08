package io.github.bradley09roberts.hardcorefriends.ai.role.guard;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.combat.GearPlan;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Every friend's trip to the supply chest for better gear: armour, a shield for an empty off hand, a better sword or
 * axe, a bow and arrows, and a golden apple or healing potion for emergencies. Aegis's old gear job, now for the whole
 * team: what each friend may take is decided for the team at once ({@link GearPlan}), Aegis first, then whoever fights
 * at night, then everyone, so the chest's best pieces go where they matter most and nobody sets out for a piece
 * another will take. Replaced gear goes back into the chest for the next friend down. A sword for a friend whose own
 * tool is a sword (Aegis, Scout, Sage), or an axe for one whose own tool is an axe (Oak, Rowan), goes into their hand;
 * any other weapon is carried in the backpack for fights, and their own work tool is never put away. It goes on at
 * night too, and the friend on watch gears up first.
 */
public final class EquipGearTask implements CompanionTask {
	private static final int CHECK_INTERVAL = 100;
	private static final double CHEST_REACH = 2.0;
	/** The friend on watch gears up before standing watch. */
	private static final double ON_WATCH = 80;

	private long nextCheck;
	private boolean upgradeWaiting;

	@Override
	public String id() {
		return "combat.gear";
	}

	@Override
	public String describe() {
		return "checking gear";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.mode() != CompanionMode.WORK) {
			return 0;
		}
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		if (now >= nextCheck || now < nextCheck - CHECK_INTERVAL) {
			nextCheck = now + CHECK_INTERVAL;
			upgradeWaiting = !GearPlan.picksFor(c).isEmpty();
		}
		if (!upgradeWaiting) {
			return 0;
		}
		if (NightWatch.isOnWatch(c)) {
			return ON_WATCH;
		}
		return switch (GearPlan.priority(c)) {
			case 0 -> 65;
			case 1 -> 60;
			default -> 52;
		};
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
		Map<CompanionEntity, List<GearPlan.Pick>> plan = GearPlan.assign(level, chest.get());
		List<GearPlan.Pick> picks = plan.getOrDefault(c, List.of());
		upgradeWaiting = false;
		nextCheck = level.getGameTime() + CHECK_INTERVAL;
		if (picks.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		// Take everything first so the chest slots in the plan stay valid, then put old gear back.
		ItemStack[] taken = new ItemStack[picks.size()];
		for (int i = 0; i < picks.size(); i++) {
			taken[i] = chest.get().removeItem(picks.get(i).chestSlot(), picks.get(i).count());
		}
		ItemStack shown = ItemStack.EMPTY;
		for (int i = 0; i < picks.size(); i++) {
			if (taken[i].isEmpty()) {
				continue;
			}
			GearPlan.Pick pick = picks.get(i);
			switch (pick.kind()) {
				case ARMOUR -> wear(c, level, chest.get(), pick.slot(), taken[i]);
				case SHIELD -> wear(c, level, chest.get(), EquipmentSlot.OFFHAND, taken[i]);
				case WEAPON -> takeWeapon(c, level, chest.get(), taken[i]);
				default -> carry(c, level, chest.get(), taken[i]);
			}
			if (pick.kind() == GearPlan.Kind.ARMOUR || pick.kind() == GearPlan.Kind.WEAPON || shown.isEmpty()
				&& (pick.kind() == GearPlan.Kind.SHIELD || pick.kind() == GearPlan.Kind.BOW)) {
				shown = taken[i];
			}
		}
		chest.get().setChanged();
		c.swingArm();
		if (c.isFighter()) {
			c.equipBestWeapon();
		}
		if (!shown.isEmpty()) {
			Speech.say(c, Line.NEW_GEAR, shown.getHoverName().getString());
		}
		return TaskStatus.SUCCESS;
	}

	/** Puts on a piece (armour or a shield); what was worn there goes back into the chest. */
	private static void wear(CompanionEntity c, ServerLevel level, Container chest, @Nullable EquipmentSlot slot, ItemStack piece) {
		if (slot == null) {
			carry(c, level, chest, piece);
			return;
		}
		ItemStack old = c.getItemBySlot(slot);
		c.setItemSlot(slot, piece);
		if (!old.isEmpty()) {
			stow(c, level, chest, old, true);
		}
	}

	/**
	 * Takes up a better weapon. Their own kind of tool (a sword for the sword carriers, an axe for the woodcutters) goes
	 * into the hand, the old one back into the chest; any other weapon goes into the backpack for fights, replacing
	 * the best weapon they carried before unless that is their own work tool.
	 */
	private static void takeWeapon(CompanionEntity c, ServerLevel level, Container chest, ItemStack weapon) {
		TagKey<Item> roleTool = KeepList.roleTool(c.friendId().role());
		boolean ownKind = roleTool != null && weapon.is(roleTool);
		if (ownKind) {
			ItemStack hand = c.getMainHandItem();
			c.setItemSlot(EquipmentSlot.MAINHAND, weapon);
			if (!hand.isEmpty()) {
				// A weapon in hand was the one replaced (back to the chest); a work tool goes into the backpack.
				stow(c, level, chest, hand, Gear.isWeapon(hand) && Gear.weaponRank(hand) >= packWeaponRank(c));
			}
			return;
		}
		int oldSlot = bestPackWeapon(c);
		ItemStack hand = c.getMainHandItem();
		boolean handIsBest = Gear.weaponRank(hand) > 0 && (oldSlot < 0 || Gear.weaponRank(hand) >= Gear.weaponRank(c.backpack().get(oldSlot)));
		ItemStack left = c.backpack().insert(weapon);
		if (!left.isEmpty()) {
			stow(c, level, chest, left, true); // no room after all: back where it came from
			return;
		}
		if (handIsBest) {
			if (roleTool == null || !hand.is(roleTool)) {
				c.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				stow(c, level, chest, hand, true);
			}
		} else if (oldSlot >= 0) {
			ItemStack old = c.backpack().get(oldSlot);
			if (roleTool == null || !old.is(roleTool)) {
				stow(c, level, chest, c.backpack().removeSlot(oldSlot), true);
			}
		}
	}

	/** The backpack slot of the best sword or axe carried, or -1. */
	private static int bestPackWeapon(CompanionEntity c) {
		int best = -1;
		int bestRank = 0;
		for (int i = 0; i < Backpack.MAX_SLOTS; i++) {
			int rank = Gear.weaponRank(c.backpack().get(i));
			if (rank > bestRank) {
				bestRank = rank;
				best = i;
			}
		}
		return best;
	}

	private static int packWeaponRank(CompanionEntity c) {
		int slot = bestPackWeapon(c);
		return slot < 0 ? 0 : Gear.weaponRank(c.backpack().get(slot));
	}

	/** Into the backpack (a bow, arrows, healing); what does not fit goes back into the chest. */
	private static void carry(CompanionEntity c, ServerLevel level, Container chest, ItemStack stack) {
		ItemStack left = c.backpack().insert(stack);
		if (!left.isEmpty()) {
			stow(c, level, chest, left, true);
		}
	}

	/**
	 * Puts an item away: into the chest first when {@code chestFirst} (old gear for the next friend), otherwise into
	 * the backpack first; whatever fits nowhere is dropped at the friend's feet.
	 */
	private static void stow(CompanionEntity c, ServerLevel level, Container chest, ItemStack item, boolean chestFirst) {
		ItemStack left = item;
		if (chestFirst) {
			left = SupplyChest.insert(chest, left);
			if (!left.isEmpty()) {
				left = c.backpack().insert(left);
			}
		} else {
			left = c.backpack().insert(left);
			if (!left.isEmpty()) {
				left = SupplyChest.insert(chest, left);
			}
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
