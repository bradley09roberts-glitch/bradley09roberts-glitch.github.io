package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.forage.ChopTreeTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.DeliverToBuilderTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.ForageContext;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.ForageTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.QuarryTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * Rowan's own routines: felling natural trees outside the camp and replanting, quarrying a bounded pit in the
 * gathering ring, foraging berries and fallen goods, and delivering what the builder is short of. The routines share
 * one {@link ForageContext}, so the land is searched once.
 */
public final class RowanTasks {
	private RowanTasks() {
	}

	public static List<CompanionTask> create() {
		ForageContext forage = new ForageContext();
		return List.of(
			new ChopTreeTask(forage),
			new QuarryTask(),
			new ForageTask(forage),
			new DeliverToBuilderTask());
	}
}
