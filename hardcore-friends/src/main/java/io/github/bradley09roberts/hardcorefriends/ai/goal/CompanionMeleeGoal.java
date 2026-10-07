package io.github.bradley09roberts.hardcorefriends.ai.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
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
		return !companion.isRetreating() && super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return !companion.isRetreating() && super.canContinueToUse();
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
