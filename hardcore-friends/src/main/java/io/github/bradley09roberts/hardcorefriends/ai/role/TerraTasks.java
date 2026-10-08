package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.terra.FenceTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.terra.LightTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.terra.PathsTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.terra.PlantTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.terra.TidyTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * Terra's own routines: laying dirt paths between camp features, lighting dark spots with torches, planting saplings
 * and flowers, tidying grass and holes in the camp core, and fencing the farm.
 */
public final class TerraTasks {
	private TerraTasks() {
	}

	/** Fresh task instances for one Terra. */
	public static List<CompanionTask> create() {
		return List.of(new PathsTask(), new LightTask(), new PlantTask(), new TidyTask(), new FenceTask());
	}
}
