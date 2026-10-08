package io.github.bradley09roberts.hardcorefriends.combat;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Reach;
import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Fighting as a squad rather than each friend alone.
 * <ul>
 * <li><b>Focus fire.</b> Choosing among several threats, a friend favours one a teammate is already fighting (one they
 * can get at): two on one ends a fight sooner than two separate duels. It counts as {@value #FOCUS_BONUS} blocks
 * nearer than it is.</li>
 * <li><b>Tag-out.</b> When a friend falls back hurt, the healthiest armed friend within {@value #TAG_RANGE} blocks
 * who can stand up to their attacker takes it over, so it does not follow them home.</li>
 * </ul>
 * Aegis keeps his own guard duty, and nobody turns berserker: these only choose among fights the friends would take on
 * anyway ({@code canStandAndFight}), so non-fighters still keep away from creepers and archers at close quarters.
 */
public final class Tactics {
	/** How much nearer a threat a teammate is already fighting counts as. */
	public static final double FOCUS_BONUS = 4;
	/** How far a friend comes to take over a hurt friend's fight. */
	private static final double TAG_RANGE = 16;

	private Tactics() {
	}

	/** True if another friend nearby is already fighting this threat. */
	public static boolean teammateFighting(CompanionEntity c, LivingEntity threat) {
		if (!(c.level() instanceof ServerLevel level)) {
			return false;
		}
		for (CompanionEntity other : Companions.in(level)) {
			if (other != c && other.getTarget() == threat && other.distanceToSqr(threat) <= 24 * 24) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The distance by which to rank a threat for this friend: its real distance, less {@value #FOCUS_BONUS} when a
	 * teammate is already fighting it and this friend can get at it (or shoot it).
	 */
	public static double rankDistance(CompanionEntity c, LivingEntity threat, double dist) {
		if (!teammateFighting(c, threat)) {
			return dist;
		}
		boolean canHelp = Archery.wouldShoot(c, threat) || Reach.check(c, threat) != Reach.Answer.NO;
		return canHelp ? Math.max(0, dist - FOCUS_BONUS) : dist;
	}

	/** A hurt friend is falling back from {@code attacker}: the healthiest armed friend nearby takes over the fight. */
	public static void tagOut(CompanionEntity hurt, LivingEntity attacker) {
		if (!(hurt.level() instanceof ServerLevel level) || !Threats.isThreat(attacker)) {
			return;
		}
		CompanionEntity relief = relief(hurt, attacker, level);
		if (relief == null) {
			return;
		}
		relief.setTarget(attacker);
		Speech.say(relief, Line.TAKE_OVER, hurt.displayName(), attacker.getName().getString());
	}

	private static @Nullable CompanionEntity relief(CompanionEntity hurt, LivingEntity attacker, ServerLevel level) {
		CompanionEntity best = null;
		double bestHealth = 0;
		for (CompanionEntity f : Companions.in(level)) {
			if (f == hurt || f.mode() == CompanionMode.STAY || f.isRetreating() || f.isAsleep()
				|| f.distanceToSqr(hurt) > TAG_RANGE * TAG_RANGE) {
				continue;
			}
			LivingEntity current = f.getTarget();
			if (current != null && current != attacker && current.isAlive()) {
				continue; // busy with a fight of their own
			}
			if (!(f.isArmed() || Archery.canShoot(f)) || !f.canStandAndFight(attacker) || f.hasGivenUpOn(attacker)) {
				continue;
			}
			double health = f.getHealth() / f.getMaxHealth();
			if (health > bestHealth) {
				bestHealth = health;
				best = f;
			}
		}
		return best;
	}
}
