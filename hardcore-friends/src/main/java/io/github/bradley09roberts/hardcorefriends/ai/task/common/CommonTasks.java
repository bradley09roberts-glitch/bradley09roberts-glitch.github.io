package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/**
 * Upkeep jobs every friend shares: depositing, restocking, crafting tools, sharing, going home at night and tidying.
 * Eating, sleeping and the other everyday needs have their own jobs (see {@code NeedsTasks}).
 */
public final class CommonTasks {
	private CommonTasks() {
	}

	/** Fresh task instances for one friend (tasks keep per-friend state, so they are never shared). */
	public static List<CompanionTask> create(FriendId id) {
		return List.of(
			new DepositTask(),
			new RestockTask(),
			new CraftToolTask(),
			new ShareTask(),
			new FeedPlayerTask(),
			new ReturnHomeTask(),
			new CollectItemsTask(),
			new IdleTask());
	}
}
