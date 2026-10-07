package io.github.bradley09roberts.hardcorefriends.ai.role;

import net.minecraft.server.level.ServerPlayer;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/**
 * Things a friend notices while doing anything else: Scout's hazard warnings and Sage's advice. Called every
 * server tick from the companion; each implementation throttles itself.
 */
public final class RolePassives {
	private RolePassives() {
	}

	public static void tick(CompanionEntity companion) {
		FriendId id = companion.friendId();
		if (id == FriendId.SCOUT) {
			ScoutSenses.tick(companion);
		} else if (id == FriendId.SAGE) {
			SageAdvisor.tick(companion);
		}
	}

	/** Sage's best piece of advice for this player right now (also used without Sage, as a plain tip). */
	public static String advice(ServerPlayer player) {
		return SageAdvisor.advice(player);
	}
}
