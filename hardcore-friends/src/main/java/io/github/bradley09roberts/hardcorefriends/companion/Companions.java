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

	/** Living, loaded team members in every level: the friends and recruited newcomers, not strangers. */
	public static synchronized List<CompanionEntity> all() {
		List<CompanionEntity> list = new ArrayList<>();
		for (CompanionEntity c : LOADED) {
			if (c.isAlive() && !c.isRemoved() && c.isTeamMember()) {
				list.add(c);
			}
		}
		return list;
	}

	/** Living, loaded companions in every level, strangers included. */
	public static synchronized List<CompanionEntity> everyone() {
		List<CompanionEntity> list = new ArrayList<>();
		for (CompanionEntity c : LOADED) {
			if (c.isAlive() && !c.isRemoved()) {
				list.add(c);
			}
		}
		return list;
	}

	/** Living, loaded strangers: newcomers met in the world who have not joined the team. */
	public static List<CompanionEntity> strangers() {
		List<CompanionEntity> list = new ArrayList<>();
		for (CompanionEntity c : everyone()) {
			if (!c.isTeamMember()) {
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

	/** The named friend {@code id} (never a newcomer who works like them), if loaded and on the team. */
	public static Optional<CompanionEntity> find(FriendId id) {
		for (CompanionEntity c : all()) {
			if (c.friendId() == id && !c.isSettler()) {
				return Optional.of(c);
			}
		}
		return Optional.empty();
	}

	/** Team members in the box (strangers are not counted). */
	public static List<CompanionEntity> near(ServerLevel level, AABB box) {
		return level.getEntitiesOfClass(CompanionEntity.class, box, c -> c.isAlive() && c.isTeamMember());
	}

	/** Every companion in the box, strangers included. */
	public static List<CompanionEntity> nearAnyone(ServerLevel level, AABB box) {
		return level.getEntitiesOfClass(CompanionEntity.class, box, CompanionEntity::isAlive);
	}
}
