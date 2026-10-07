package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Tracks companions currently loaded on the server, so routines can find friends without scanning the world.
 * Filled from entity load/unload events.
 */
public final class Companions {
	private static final Set<CompanionEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

	private Companions() {
	}

	public static synchronized void track(CompanionEntity companion) {
		LOADED.add(companion);
	}

	public static synchronized void untrack(CompanionEntity companion) {
		LOADED.remove(companion);
	}

	public static synchronized void clear() {
		LOADED.clear();
	}

	/** Living, loaded companions in every level. */
	public static synchronized List<CompanionEntity> all() {
		List<CompanionEntity> list = new ArrayList<>();
		for (CompanionEntity c : LOADED) {
			if (c.isAlive() && !c.isRemoved()) {
				list.add(c);
			}
		}
		return list;
	}

	public static List<CompanionEntity> in(ServerLevel level) {
		List<CompanionEntity> list = new ArrayList<>();
		for (CompanionEntity c : all()) {
			if (c.level() == level) {
				list.add(c);
			}
		}
		return list;
	}

	public static Optional<CompanionEntity> find(FriendId id) {
		for (CompanionEntity c : all()) {
			if (c.friendId() == id) {
				return Optional.of(c);
			}
		}
		return Optional.empty();
	}

	public static List<CompanionEntity> near(ServerLevel level, AABB box) {
		return level.getEntitiesOfClass(CompanionEntity.class, box, CompanionEntity::isAlive);
	}
}
