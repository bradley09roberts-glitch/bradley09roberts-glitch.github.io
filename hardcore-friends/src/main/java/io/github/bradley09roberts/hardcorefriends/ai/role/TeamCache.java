package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;

/**
 * Scans the whole team shares, so that nine friends looking at the same camp cost no more than one: the farm survey,
 * the trees and berry bushes found around the camp, the dark spots and weeds in the camp, and the camp furnace. Each
 * value lives per dimension and is made afresh whenever the camp's memory is reset (a new world, or a game test
 * starting a new camp), so nothing learned about one camp is ever used for another.
 */
public final class TeamCache {
	/** Camp memory key holding this camp's cache epoch, which changes when the camp's memory is wiped. */
	private static final String MEMORY = "team.cache";

	private static final Map<String, Object> VALUES = new HashMap<>();
	private static long epoch;

	private TeamCache() {
	}

	/** The team's shared value for this key in this level's dimension, made with {@code make} the first time. */
	@SuppressWarnings("unchecked")
	public static <T> T get(ServerLevel level, String key, Supplier<T> make) {
		CampData data = Camp.data(level.getServer());
		CompoundTag mem = data.memory(MEMORY);
		long current = mem.getLongOr("epoch", 0L);
		if (current == 0L) {
			current = level.getRandom().nextLong() | 1L;
			mem.putLong("epoch", current);
			data.setDirty();
		}
		if (current != epoch) {
			VALUES.clear(); // a different camp, or this one was reset: nothing we knew still holds
			epoch = current;
		}
		return (T) VALUES.computeIfAbsent(key + "@" + Camp.dimensionId(level), k -> make.get());
	}
}
