package io.github.bradley09roberts.hardcorefriends.ai.role;

import net.minecraft.server.level.ServerPlayer;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Things a friend notices while doing anything else: Scout's hazard warnings and Sage's advice. Called every
 * server tick from the companion; implementations must throttle themselves.
 */
public final class RolePassives {
	private RolePassives() {
	}

	public static void tick(CompanionEntity companion) {
	}

	/** Sage's best piece of advice for this player right now. */
	public static String advice(ServerPlayer player) {
		return "Light up dark places, eat before you are hungry, and never dig straight down.";
	}
}
