package io.github.bradley09roberts.hardcorefriends.ai.task.needs;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/** The jobs a friend uses to look after their own needs: eating, sleeping, chatting, relaxing, warming up. */
public final class NeedsTasks {
	private NeedsTasks() {
	}

	/** Fresh task instances for one friend. */
	public static List<CompanionTask> create(FriendId id) {
		return List.of();
	}
}
