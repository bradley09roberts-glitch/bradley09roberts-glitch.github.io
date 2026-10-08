package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/** Advances the camp to its next stage once the current stage's improvements are done and the bond is strong enough. */
public final class CampProgress {
	private CampProgress() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 200 != 13) {
			return;
		}
		CampData data = Camp.data(server);
		for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
			if (Camp.isCampLevel(level, data)) {
				io.github.bradley09roberts.hardcorefriends.ai.role.build.SupplyChestLink.dropIfBroken(level, data);
			}
		}
		if (data.campPos().isEmpty() || data.stage() >= Camp.MAX_STAGE) {
			return;
		}
		Set<Role> available = availableRoles(data);
		List<Structures.Entry> missing = Structures.missing(data, data.stage(), available::contains);
		int nextStage = data.stage() + 1;
		if (missing.isEmpty() && data.unity() >= Camp.STAGE_UNITY[nextStage]) {
			data.setStage(nextStage);
			String name = Camp.stageName(nextStage);
			List<CompanionEntity> friends = Companions.all();
			if (!friends.isEmpty()) {
				Speech.say(friends.get(server.getTickCount() % friends.size()), Line.CAMP_UP, name);
			}
			Speech.announce(server, Component.literal("Your camp has grown into a " + name + "! New projects: "
				+ describe(Structures.forStage(nextStage))).withStyle(ChatFormatting.GOLD));
			Unity.applyBackpackSizes();
		}
	}

	/** Roles of friends currently on the team (alive), whether or not they are loaded. */
	public static Set<Role> availableRoles(CampData data) {
		Set<Role> roles = EnumSet.noneOf(Role.class);
		for (FriendId id : FriendId.values()) {
			if (data.ledger(id).state == CampData.LifeState.ALIVE) {
				roles.add(id.role());
			}
		}
		return roles;
	}

	/** Marks an improvement finished, announces it and rewards the team. */
	public static void complete(CompanionEntity maker, String structureId) {
		CampData data = Camp.data(maker.level().getServer());
		if (data.isCompleted(structureId)) {
			return;
		}
		data.markCompleted(structureId);
		Structures.Entry entry = Structures.get(structureId);
		boolean contraption = entry.owner() == Role.INVENTOR;
		Speech.say(maker, contraption ? Line.CONTRAPTION_DONE : Line.BUILD_DONE, entry.displayName());
		Unity.add((net.minecraft.server.level.ServerLevel) maker.level(), Unity.BUILD, contraption ? 15 : 30, 0);
		data.addStat("improvements_built", 1);
	}

	private static String describe(List<Structures.Entry> entries) {
		StringBuilder sb = new StringBuilder();
		for (Structures.Entry e : entries) {
			if (!sb.isEmpty()) {
				sb.append(", ");
			}
			sb.append(e.displayName());
		}
		return sb.toString();
	}
}
