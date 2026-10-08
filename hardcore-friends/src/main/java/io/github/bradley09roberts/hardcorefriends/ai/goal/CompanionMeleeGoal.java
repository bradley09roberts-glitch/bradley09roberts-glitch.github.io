package io.github.bradley09roberts.hardcorefriends.ai.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/** Melee combat with the best weapon in the backpack. Weapons lose durability on every hit. */
public class CompanionMeleeGoal extends MeleeAttackGoal {
	private final CompanionEntity companion;

	public CompanionMeleeGoal(CompanionEntity companion) {
		super(companion, 1.2, true);
		this.companion = companion;
	}

	@Override
	public boolean canUse() {
		return !companion.isRetreating() && withinFightingRange() && super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return !companion.isRetreating() && withinFightingRange() && super.canContinueToUse();
	}

	/**
	 * Aegis pursues threats; an armed friend in FOLLOW mode defends their leader nearby; everyone else only fights
	 * back when cornered (the attacker is right next to them) instead of chasing.
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
		LivingEntity target = companion.getTarget();
		if (target != null) {
			Speech.say(companion, Line.FIGHT, target.getName().getString());
		}
	}
}
