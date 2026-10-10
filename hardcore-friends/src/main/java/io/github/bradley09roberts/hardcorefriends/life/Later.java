package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.MinecraftServer;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * A few things to happen a moment later on the server thread (birthday wishes one after another, rather than all in
 * one breath). Each runnable checks its own facts again when it runs. Kept in memory only, at most {@value #MAX}.
 */
final class Later {
	private static final int MAX = 128;

	private record Job(long at, Runnable run) {
	}

	private static final List<Job> JOBS = new ArrayList<>();

	private Later() {
	}

	/** Runs {@code run} about {@code ticks} ticks from now. */
	static void run(MinecraftServer server, int ticks, Runnable run) {
		if (JOBS.size() < MAX) {
			JOBS.add(new Job(server.getTickCount() + Math.max(1, ticks), run));
		}
	}

	/** Every server tick: runs what is due. */
	static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		List<Runnable> due = new ArrayList<>();
		for (Iterator<Job> it = JOBS.iterator(); it.hasNext();) {
			Job job = it.next();
			if (now >= job.at()) {
				due.add(job.run());
				it.remove();
			}
		}
		for (Runnable r : due) {
			try {
				r.run();
			} catch (RuntimeException e) {
				HardcoreFriends.LOGGER.error("A delayed village life event failed", e);
			}
		}
	}

	static void clear() {
		JOBS.clear();
	}
}
