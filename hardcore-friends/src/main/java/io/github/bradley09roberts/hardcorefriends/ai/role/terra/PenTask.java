package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.BlueprintTask;
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
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Terra, who fences the camp, builds the animal pen once the camp is a Hamlet: a 9×9 ring of wooden fences with a
 * gate, from the {@link Blueprints#ANIMAL_PEN} plan, through the ordinary build path ({@code BuildJob}): fences and
 * the gate come from the supply chest or are crafted at a crafting table from planks, logs and sticks, and dips in
 * the ground are filled first. The site is found like any building's (level natural ground inside the camp, clear of
 * other sites and of anything a player built), and the gate faces the camp centre. The pen is optional: the camp
 * grows without it, but the farmer keeps no animals until it stands. Once it is finished, a fence or gate that goes
 * missing (a creeper, a player) is put back, so the animals stay in.
 */
public final class PenTask extends BlueprintTask {
	private static final int REPAIR_RESCAN = 600;
	private static final double BUILD_SCORE = 42;
	private static final double REPAIR_SCORE = 50;

	private boolean repairing;
	private long scannedAt = -100_000;
	private boolean damaged;

	@Override
	public String id() {
		return "terra.pen";
	}

	@Override
	protected WorldEditGuard.Reason reason() {
		return WorldEditGuard.Reason.LANDSCAPE;
	}

	@Override
	protected double baseScore() {
		return repairing ? REPAIR_SCORE : BUILD_SCORE;
	}

	@Override
	protected boolean repair() {
		return repairing;
	}

	@Override
	protected @Nullable Blueprint choose(CompanionEntity c, CampData data) {
		Blueprint plan = Blueprints.ANIMAL_PEN;
		if (data.stage() < Structures.get(Structures.ANIMAL_PEN).stage() || isSetAside(c, plan.id())) {
			return null;
		}
		if (!data.isCompleted(plan.id())) {
			repairing = false;
			return plan;
		}
		repairing = true;
		long now = c.level().getGameTime();
		if (now - scannedAt >= REPAIR_RESCAN || now < scannedAt) {
			scannedAt = now;
			damaged = needsRepair(c, data, plan);
		}
		return damaged ? plan : null;
	}

	/** True if a fence or the gate is missing and the camp has (or can make) what replaces it. */
	private static boolean needsRepair(CompanionEntity c, CampData data, Blueprint plan) {
		ServerLevel level = (ServerLevel) c.level();
		List<Placement> gaps = BuildJob.missing(level, data, plan, 4);
		Container chest = SupplyChest.of(level).orElse(null);
		for (Placement p : gaps) {
			Stock s = p.entry().material().stock();
			if (s == null || Supplies.canMake(c, chest, s, 1)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean start(CompanionEntity c) {
		boolean started = super.start(c);
		if (started && repairing) {
			scannedAt = -100_000; // look again after this run
		}
		return started;
	}
}
