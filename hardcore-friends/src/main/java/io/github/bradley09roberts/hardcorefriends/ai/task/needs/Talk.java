package io.github.bradley09roberts.hardcorefriends.ai.task.needs;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;

/** Small helpers for choosing what a friend says while looking after their needs. */
final class Talk {
	private Talk() {
	}

	/** True when the friend said this line within its own cooldown, so saying it again now would be skipped. */
	static boolean saidRecently(CompanionEntity c, Line line) {
		Long last = c.speechMemory().get(line);
		return last != null && c.level().getGameTime() - last < line.cooldownTicks();
	}
}
