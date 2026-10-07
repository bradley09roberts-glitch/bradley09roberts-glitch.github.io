package io.github.bradley09roberts.hardcorefriends.ai.role;

import net.minecraft.server.level.ServerPlayer;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/** Sage's observations: contextual survival advice and team planning announcements. Throttles itself. */
public final class SageAdvisor {
	private SageAdvisor() {
	}

	public static void tick(CompanionEntity sage) {
	}

	public static String advice(ServerPlayer player) {
		return "Light up dark places, eat before you are hungry, and never dig straight down.";
	}
}
