package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.spark.ContraptionTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.spark.TorchTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/** Spark's own routines: building redstone contraptions and keeping the camp stocked with torches. */
public final class SparkTasks {
	private SparkTasks() {
	}

	public static List<CompanionTask> create() {
		return List.of(new ContraptionTask(), new TorchTask());
	}
}
