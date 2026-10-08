package io.github.bradley09roberts.hardcorefriends.ai.role.build;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.BuildJob;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.camp.build.Supplies;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Oak puts back blocks that have gone missing from finished buildings (a torch knocked off, a wall plank taken by
 * a creeper), using the same materials as the plan. Only empty spots are refilled; nothing is ever replaced. The
 * buildings are checked every ten seconds.
 */
public final class RepairTask extends BlueprintTask {
	private static final int RESCAN = 200;

	private long scannedAt = -100_000;
	private @Nullable Blueprint damaged;

	@Override
	public String id() {
		return "oak.repair";
	}

	@Override
	protected WorldEditGuard.Reason reason() {
		return WorldEditGuard.Reason.BUILD;
	}

	@Override
	protected double baseScore() {
		return 40;
	}

	@Override
	protected boolean repair() {
		return true;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	protected @Nullable Blueprint choose(CompanionEntity c, CampData data) {
		long now = c.level().getGameTime();
		if (now - scannedAt < RESCAN && now >= scannedAt) {
			return damaged;
		}
		scannedAt = now;
		damaged = null;
		ServerLevel level = (ServerLevel) c.level();
		Container chest = SupplyChest.of(level).orElse(null);
		for (Structures.Entry e : Structures.ALL) {
			if (e.owner() != Role.BUILDER || e.id().equals(Structures.SUPPLY_CHEST) || !data.isCompleted(e.id()) || isSetAside(c, e.id())) {
				continue;
			}
			Optional<Blueprint> bp = Blueprints.forId(e.id());
			if (bp.isEmpty() || data.site(e.id()).isEmpty()) {
				continue;
			}
			List<Placement> gaps = BuildJob.missing(level, data, bp.get(), 8);
			for (Placement p : gaps) {
				Stock s = p.entry().material().stock();
				if (s == null || Supplies.canMake(c, chest, s, 1)) {
					damaged = bp.get();
					return damaged;
				}
			}
		}
		return null;
	}

	@Override
	public boolean start(CompanionEntity c) {
		scannedAt = -100_000; // look again now
		return super.start(c);
	}
}
