package io.github.bradley09roberts.hardcorefriends.ai.role.build;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;

/**
 * Notices a supply chest that has been broken (by a creeper, or by a player tidying up). The dead link is dropped,
 * so the camp no longer points everyone at an empty spot and Oak builds and links a new chest. If the chest the
 * friends built earlier still stands, that one is linked again instead. Nothing is decided while the chest's chunk
 * is unloaded.
 */
public final class SupplyChestLink {
	private SupplyChestLink() {
	}

	/** Returns true if the link changed because the linked chest is gone. */
	public static boolean dropIfBroken(ServerLevel level, CampData data) {
		Optional<BlockPos> linked = data.chestPos();
		if (linked.isEmpty() || !Camp.isCampLevel(level, data) || !level.isLoaded(linked.get())
			|| SupplyChest.isValidStorage(level, linked.get())) {
			return false;
		}
		Optional<CampData.Site> own = data.site(Structures.SUPPLY_CHEST);
		if (own.isPresent() && !own.get().origin.equals(linked.get()) && level.isLoaded(own.get().origin)
			&& SupplyChest.isValidStorage(level, own.get().origin)) {
			data.setChestPos(own.get().origin); // the chest the friends built is still there
			return true;
		}
		data.setChestPos(null);
		data.removeSite(Structures.SUPPLY_CHEST); // a new chest gets a fresh site
		return true;
	}

	/** True when the camp has no linked chest although one was made before: the builder should make a new one. */
	public static boolean needsNewChest(CampData data) {
		return data.chestPos().isEmpty() && data.isCompleted(Structures.SUPPLY_CHEST);
	}
}
