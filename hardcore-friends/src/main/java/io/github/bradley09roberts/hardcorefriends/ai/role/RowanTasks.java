package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ClearSiteTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.ChopTreeTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.DeliverToBuilderTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.ForageContext;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.ForageTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.QuarryTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * The forager's routines (Rowan's, and anyone's who helps or stands in for her): felling natural trees outside the
 * camp and replanting, quarrying a bounded pit in the gathering ring, foraging berries and fallen goods, delivering
 * what the builder is short of, and clearing the trees off a building site in a forest camp. One friend's routines
 * share one {@link ForageContext}, so the land is searched once.
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
			new DeliverToBuilderTask(),
			// Felling the trees on a forest-camp building site comes before felling outside (at most 110).
			new ClearSiteTask(115));
	}
}
