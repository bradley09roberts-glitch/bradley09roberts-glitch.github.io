package io.github.bradley09roberts.hardcorefriends.ai.goal;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Melee combat with the best weapon in the backpack. Weapons lose durability on every hit. It stands aside while the
 * friend would rather shoot their target ({@link Archery#prefersBow}; the bow goal has the same priority and the two
 * never run together), and lowers a raised shield just before each blow.
 */
public class CompanionMeleeGoal extends MeleeAttackGoal {
	private final CompanionEntity companion;

	public CompanionMeleeGoal(CompanionEntity companion) {
		super(companion, 1.2, true);
		this.companion = companion;
	}

	@Override
	public boolean canUse() {
		return !companion.isRetreating() && withinFightingRange() && !shootsInstead() && super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return !companion.isRetreating() && withinFightingRange() && !shootsInstead() && super.canContinueToUse();
	}

	/** The friend would rather shoot their target from here (the bow goal takes over). */
	private boolean shootsInstead() {
		LivingEntity target = companion.getTarget();
		return target != null && Archery.prefersBow(companion, target);
	}

	@Override
	protected void checkAndPerformAttack(LivingEntity target) {
		if (companion.isUsingItem() && canPerformAttack(target)) {
			companion.stopUsingItem(); // the shield comes down to strike
		}
		super.checkAndPerformAttack(target);
	}

	/**
	 * Aegis pursues threats; an armed friend in FOLLOW mode defends their leader nearby; everyone else fights a threat
	 * they can stand up to within their reach ({@link CompanionEntity#canStandAndFight}: 8 blocks, or across the camp
	 * for an armed friend or the watcher inside it), and otherwise only fights back when cornered.
	 */
	private boolean withinFightingRange() {
		LivingEntity target = companion.getTarget();
		if (target == null) {
			return false;
		}
		if (companion.isFighter()) {
			return companion.canStandAndFight(target);
		}
		double d = companion.distanceTo(target);
		if (companion.mode() == CompanionMode.FOLLOW && companion.isArmed() && d <= 12) {
			return companion.canStandAndFight(target) || d <= 3;
		}
		return companion.canStandAndFight(target) || d <= 2.5;
	}

	@Override
	public void start() {
		super.start();
		companion.equipBestWeapon();
		if (companion.getMainHandItem().is(Items.BOW)) {
			// No sword or axe: a tool hits harder than a bow.
			companion.actions().equip(s -> s.is(ItemTags.PICKAXES) || s.is(ItemTags.SHOVELS) || s.is(ItemTags.HOES));
		}
		LivingEntity target = companion.getTarget();
		if (target != null) {
			Speech.say(companion, Line.FIGHT, target.getName().getString());
		}
	}
}
