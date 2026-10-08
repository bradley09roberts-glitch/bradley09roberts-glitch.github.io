package io.github.bradley09roberts.hardcorefriends.combat;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Credits a friend's blows to a player, the way vanilla credits a tamed wolf's to its owner
 * ({@code LivingEntity.resolvePlayerResponsibleForDamage}): the mob remembers that a player hurt it, for the same
 * 100 ticks, so when a friend kills it the drops only a player's kill gives (blaze rods, a wither skeleton's skull...)
 * and its experience still drop. The player is the friend's leader when they are following someone nearby, otherwise
 * the nearest player within {@value #RANGE} blocks of the friend. Nothing else changes: the kill is still the friend's.
 * Public, so other packages (the Nether expedition's blaze hunting) can use it too.
 */
public final class KillCredit {
	/** How near a player must be to be credited with a friend's kill. */
	public static final double RANGE = 32;
	/** How long the mob remembers, as for a wolf's or a player's own blow. */
	private static final int MEMORY = 100;

	private KillCredit() {
	}

	/** Marks the victim as hurt by the friend's leader or the nearest player (see the class description). */
	public static void credit(CompanionEntity friend, LivingEntity victim) {
		if (!(friend.level() instanceof ServerLevel level) || victim.level() != level) {
			return;
		}
		ServerPlayer player = creditedPlayer(friend, level);
		if (player != null) {
			victim.setLastHurtByPlayer(player, MEMORY);
		}
	}

	/** The friend's leader if near, else the nearest player within {@value #RANGE} blocks; null if nobody is. */
	public static @Nullable ServerPlayer creditedPlayer(CompanionEntity friend, ServerLevel level) {
		ServerPlayer leader = friend.leader();
		if (leader != null && leader.level() == level && leader.isAlive() && !leader.isSpectator()
			&& leader.distanceToSqr(friend) <= RANGE * RANGE * 4) {
			return leader;
		}
		ServerPlayer best = null;
		double bestDist = RANGE * RANGE;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || !p.isAlive()) {
				continue;
			}
			double d = p.distanceToSqr(friend);
			if (d <= bestDist) {
				bestDist = d;
				best = p;
			}
		}
		return best;
	}
}
