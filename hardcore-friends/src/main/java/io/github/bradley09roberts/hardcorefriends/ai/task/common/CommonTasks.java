package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/** Upkeep jobs every friend shares: depositing, restocking, sharing, eating, going home at night. */
public final class CommonTasks {
	private CommonTasks() {
	}

	public static List<CompanionTask> create(FriendId id) {
		return List.of();
	}
}
