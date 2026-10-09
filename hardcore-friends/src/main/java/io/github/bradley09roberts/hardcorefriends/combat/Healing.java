package io.github.bradley09roberts.hardcorefriends.combat;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.role.guard.Gear;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Emergency healing. In a fight (a target, hurt by a monster or other attacker in the last five seconds, or falling
 * back from danger; hunger pangs, falls, fire and drowning do not count, so a famine does not use up the camp's
 * potions) at low health (below {@value #LOW_FRACTION} of their health, or {@value #LOW_HEALTH} points), a friend uses
 * the best thing in their backpack: a potion of healing (it works at once), a golden apple (regeneration and
 * absorption), a potion of regeneration, and only when nearly dead ({@value #DIRE_FRACTION}) an enchanted golden
 * apple. A friend already regenerating waits for it to work unless nearly dead. A potion of fire resistance is drunk
 * when on fire or in lava.
 * The items are really used up and their real effects applied, the way the game applies them when a player eats or
 * drinks ({@link ItemStack#finishUsingItem}); the empty bottle goes back into the backpack. Normal food is not touched:
 * that is for hunger. Checked every half second per friend.
 */
public final class Healing {
	/** Below this share of their health a friend in a fight heals. */
	private static final double LOW_FRACTION = 0.4;
	/** At or below this many health points a friend in a fight heals, whatever their maximum. */
	private static final float LOW_HEALTH = 6.0F;
	/** Nearly dead: an enchanted golden apple is worth it now. */
	private static final double DIRE_FRACTION = 0.25;
	/** A pause between two uses (eating or drinking takes a moment). */
	private static final int COOLDOWN_TICKS = 40;
	private static final int CHECK_TICKS = 10;

	private static final Map<CompanionEntity, Long> NEXT_USE = new WeakHashMap<>();

	private Healing() {
	}

	/** Called every tick for every friend (a {@code CompanionEvents.TICK} hook); works every half second. */
	static void tick(CompanionEntity c, ServerLevel level) {
		if ((c.tickCount + c.getId()) % CHECK_TICKS != 0 || !c.isAlive() || !c.isTeamMember()) {
			return;
		}
		long now = level.getGameTime();
		Long next = NEXT_USE.get(c);
		if (next != null && now < next) {
			return;
		}
		if ((c.isOnFire() || c.isInLava()) && !c.hasEffect(MobEffects.FIRE_RESISTANCE)) {
			if (use(c, level, Gear::isFireResistance)) {
				return;
			}
		}
		float health = c.getHealth();
		double fraction = health / c.getMaxHealth();
		if (fraction >= LOW_FRACTION && health > LOW_HEALTH || !fighting(c)) {
			return;
		}
		boolean dire = fraction < DIRE_FRACTION;
		if (c.hasEffect(MobEffects.REGENERATION) && !dire) {
			return; // already healing: let it work
		}
		if (use(c, level, s -> Gear.potionWith(s, MobEffects.INSTANT_HEALTH))
			|| use(c, level, s -> s.is(Items.GOLDEN_APPLE))
			|| use(c, level, s -> Gear.potionWith(s, MobEffects.REGENERATION))) {
			return;
		}
		if (dire) {
			use(c, level, s -> s.is(Items.ENCHANTED_GOLDEN_APPLE));
		}
	}

	/**
	 * In a fight: a target, falling back, or hurt by an attacker (a monster, or whoever shot or threw at them) in the
	 * last five seconds. Damage with nobody behind it (hunger, a fall, fire, drowning) is not a fight: natural healing
	 * and food see to that, and the emergency items are kept for when something is trying to kill them.
	 */
	private static boolean fighting(CompanionEntity c) {
		if (c.getTarget() != null) {
			return true;
		}
		if (c.tickCount - c.getLastHurtByMobTimestamp() < 100 && c.getLastHurtByMob() != null) {
			return true;
		}
		// Falling back to rest after a fall is no fight; falling back from danger is.
		return c.isRetreating() && Threats.nearest(c, 16) != null;
	}

	/** Eats or drinks one matching item from the backpack, with its real effects. False if none is carried. */
	private static boolean use(CompanionEntity c, ServerLevel level, Predicate<ItemStack> match) {
		ItemStack item = c.backpack().take(match, 1);
		if (item.isEmpty()) {
			return false;
		}
		String name = item.getHoverName().getString();
		if (item.has(net.minecraft.core.component.DataComponents.FOOD)) {
			c.needs().add(Needs.Need.HUNGER, CompanionEntity.hungerValue(item));
		}
		ItemStack left = item.finishUsingItem(level, c); // the effects, sounds and particles; a potion leaves a bottle
		if (!left.isEmpty()) {
			ItemStack spill = c.backpack().insert(left);
			if (!spill.isEmpty()) {
				c.spawnAtLocation(level, spill);
			}
		}
		c.swingArm();
		NEXT_USE.put(c, level.getGameTime() + COOLDOWN_TICKS);
		Speech.say(c, Line.EMERGENCY_HEAL, name);
		return true;
	}
}
