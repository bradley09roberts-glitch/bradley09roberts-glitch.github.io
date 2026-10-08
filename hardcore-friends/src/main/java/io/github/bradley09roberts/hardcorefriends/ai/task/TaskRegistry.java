package io.github.bradley09roberts.hardcorefriends.ai.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.bradley09roberts.hardcorefriends.ai.role.AegisTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.FernTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.FlintTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.OakTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.RowanTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.SageTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.ScoutTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.SparkTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.TerraTasks;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.CommonTasks;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.NeedsTasks;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Role;

/**
 * Builds the job list for each friend: shared upkeep jobs, the jobs that look after their own needs, and every
 * role's work, wrapped so that anyone can do it but specialists come first ({@link SpecialityTask}). Their own
 * speciality's jobs are listed first. Guarding, planning and reporting stay with the specialist.
 */
public final class TaskRegistry {
	/** Jobs only the specialist does: these are what the specialist is, not chores anyone could pick up. */
	public static final Set<String> SPECIALIST_ONLY = Set.of(
		"aegis.equip_gear", "aegis.guard", "sage.observe", "sage.review_stores", "scout.report");

	private TaskRegistry() {
	}

	public static List<CompanionTask> create(FriendId id) {
		List<CompanionTask> tasks = new ArrayList<>(CommonTasks.create(id));
		tasks.addAll(NeedsTasks.create(id));
		List<Role> order = new ArrayList<>(List.of(Role.values()));
		order.remove(id.role());
		order.addFirst(id.role());
		for (Role role : order) {
			for (CompanionTask task : roleTasks(role)) {
				if (role != id.role() && SPECIALIST_ONLY.contains(task.id())) {
					continue;
				}
				tasks.add(new SpecialityTask(task, role));
			}
		}
		return tasks;
	}

	/** Fresh instances of one role's own routines. */
	private static List<CompanionTask> roleTasks(Role role) {
		return switch (role) {
			case FARMER -> FernTasks.create();
			case BUILDER -> OakTasks.create();
			case MINER -> FlintTasks.create();
			case EXPLORER -> ScoutTasks.create();
			case INVENTOR -> SparkTasks.create();
			case WARRIOR -> AegisTasks.create();
			case STRATEGIST -> SageTasks.create();
			case LANDSCAPER -> TerraTasks.create();
			case FORAGER -> RowanTasks.create();
		};
	}
}
