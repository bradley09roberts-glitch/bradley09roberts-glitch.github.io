package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Striking an animal with a real weapon, and gathering what it drops: meat, leather, wool, feathers, rabbit hide and
 * feet that fell where it died. Only those drops are picked up, never anything a player threw down.
 */
final class Carcass {
	/** Ticks between blows: about a sword's swing time. */
	static final int SWING_TICKS = 12;
	/** How close a friend must be to strike, centre to centre. */
	static final double REACH = 2.3;
	private static final double DROP_RADIUS = 3.0;
	private static final int LOOK_TICKS = 10;
	private static final int ITEM_TIMEOUT = 100;

	private final List<ItemEntity> items = new ArrayList<>();
	private @Nullable Vec3 at;
	private int looked;
	private int itemTicks;
	private int collected;

	private Carcass() {
	}

	static Carcass none() {
		return new Carcass();
	}

	/** True when the friend is close enough to strike the animal. */
	static boolean inReach(CompanionEntity c, Animal a) {
		return c.distanceToSqr(a) <= REACH * REACH;
	}

	/** One blow with whatever the friend holds (their best sword or axe, equipped by the caller). */
	static boolean strike(CompanionEntity c, Animal a) {
		c.getLookControl().setLookAt(a, 30.0F, 30.0F);
		c.swingArm();
		return c.doHurtTarget((ServerLevel) c.level(), a);
	}

	/** Remembers where an animal died, so its drops can be gathered. */
	void died(Vec3 where) {
		at = where;
		items.clear();
		looked = 0;
		itemTicks = 0;
	}

	boolean pending() {
		return at != null;
	}

	/** How many items were picked up so far. */
	int collected() {
		return collected;
	}

	/** One tick of gathering the drops. Returns true once there is nothing more to pick up. */
	boolean gather(CompanionEntity c) {
		Vec3 where = at;
		if (where == null) {
			return true;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (items.isEmpty()) {
			if (looked++ > LOOK_TICKS) {
				at = null;
				return true;
			}
			items.addAll(level.getEntitiesOfClass(ItemEntity.class, new AABB(where, where).inflate(DROP_RADIUS, 2, DROP_RADIUS),
				Carcass::isDrop));
			items.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c)));
			return false;
		}
		ItemEntity item = items.getFirst();
		if (!isDrop(item) || ++itemTicks > ITEM_TIMEOUT) {
			items.removeFirst();
			itemTicks = 0;
			if (items.isEmpty()) {
				at = null;
				return true;
			}
			return false;
		}
		if (!c.actions().walkTo(item.blockPosition(), 1.2) && !c.actions().isStuck()) {
			return false;
		}
		ItemStack stack = item.getItem();
		int before = stack.getCount();
		ItemStack left = c.backpack().insert(stack.copy());
		int picked = before - left.getCount();
		if (picked > 0) {
			c.take(item, picked);
			collected += picked;
		}
		if (left.isEmpty()) {
			item.discard();
		} else {
			item.setItem(left);
		}
		items.removeFirst();
		itemTicks = 0;
		if (items.isEmpty()) {
			at = null;
			return true;
		}
		return false;
	}

	private static boolean isDrop(ItemEntity e) {
		return e.isAlive() && !e.isRemoved() && Livestock.isAnimalDrop(e.getItem()) && !(e.getOwner() instanceof Player);
	}
}
