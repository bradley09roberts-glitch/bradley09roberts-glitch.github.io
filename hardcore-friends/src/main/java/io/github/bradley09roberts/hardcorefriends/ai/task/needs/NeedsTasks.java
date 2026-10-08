package io.github.bradley09roberts.hardcorefriends.ai.task.needs;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/**
 * The jobs a friend uses to look after their own needs: eating, sleeping, chatting, relaxing, warming up. Each scores
 * higher the lower its need, so a desperate need beats any work while a mild one waits for the job in hand to end.
 * Their ids start with {@code needs.}, which counts as time off rather than work.
 */
public final class NeedsTasks {
	private NeedsTasks() {
	}

	/** Fresh task instances for one friend. */
	public static List<CompanionTask> create(FriendId id) {
		return List.of(
			new EatTask(),
			new SleepTask(),
			new SocializeTask(),
			new LeisureTask(id),
			new ComfortTask());
	}
}
