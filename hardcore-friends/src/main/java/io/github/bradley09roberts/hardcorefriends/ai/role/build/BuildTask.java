package io.github.bradley09roberts.hardcorefriends.ai.role.build;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.CampProgress;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.CampFeatures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Oak's main job: build the next improvement the settlement plan gives the builder, one batch at a time. When there
 * is no crafting table by the supply chest yet, Oak makes that first, because most recipes need it. A supply chest
 * the player already linked counts as built, and so does a crafting table or furnace already standing by it.
 */
public final class BuildTask extends BlueprintTask {
	private static final int TABLE_RECHECK = 200;

	private long tableCheckedAt = -100_000;
	private boolean tableNearChest;

	@Override
	public String id() {
		return "oak.build";
	}

	@Override
	protected WorldEditGuard.Reason reason() {
		return WorldEditGuard.Reason.BUILD;
	}

	@Override
	protected double baseScore() {
		return 60 * CampNeeds.weight(CampNeeds.Need.BUILD);
	}

	@Override
	protected @Nullable Blueprint choose(CompanionEntity c, CampData data) {
		for (Structures.Entry next : pending(data, Role.BUILDER)) {
			boolean trivialChest = next.id().equals(Structures.SUPPLY_CHEST) && data.chestPos().isPresent();
			if (!trivialChest && !next.id().equals(Structures.CRAFTING_TABLE) && !data.isCompleted(Structures.CRAFTING_TABLE)
				&& !hasTable(c, data)) {
				// Nearly every recipe needs a crafting table, so that comes first. (A finished one that went missing is
				// put back by the repair job instead.)
				return isSetAside(c, Structures.CRAFTING_TABLE) ? null : Blueprints.CRAFTING_TABLE;
			}
			Blueprint plan = Blueprints.forId(next.id()).orElse(null);
			if (plan != null && !isSetAside(c, plan.id())) {
				return plan;
			}
		}
		return null;
	}

	@Override
	protected boolean finishWithoutBuilding(CompanionEntity c, CampData data, Blueprint plan) {
		ServerLevel level = (ServerLevel) c.level();
		if (plan == Blueprints.SUPPLY_CHEST && data.chestPos().isPresent() && SupplyChest.of(level).isPresent()) {
			CampProgress.complete(c, Structures.SUPPLY_CHEST); // the player linked one already
			return true;
		}
		if (data.site(plan.id()).isEmpty() && !data.isCompleted(plan.id())) {
			Optional<BlockPos> chest = data.chestPos();
			if (chest.isPresent()) {
				if (plan == Blueprints.CRAFTING_TABLE && CampFeatures.find(level, chest.get(), 6, s -> s.is(Blocks.CRAFTING_TABLE)) != null
					|| plan == Blueprints.FURNACE && CampFeatures.find(level, chest.get(), 6, s -> s.is(Blocks.FURNACE)) != null) {
					CampProgress.complete(c, plan.id()); // the camp already has one by the chest
					return true;
				}
			}
		}
		return false;
	}

	/** Is there a crafting table by the supply chest (or by where it will go)? Rechecked every 10 seconds. */
	private boolean hasTable(CompanionEntity c, CampData data) {
		long now = c.level().getGameTime();
		if (now - tableCheckedAt >= TABLE_RECHECK || now < tableCheckedAt) {
			tableCheckedAt = now;
			BlockPos near = data.chestPos().orElseGet(() -> {
				int[] o = Blueprints.SUPPLY_CHEST.offsets().getFirst();
				return data.campPos().orElse(c.blockPosition()).offset(o[0], 0, o[1]);
			});
			tableNearChest = CampFeatures.find((ServerLevel) c.level(), near, 6, s -> s.is(Blocks.CRAFTING_TABLE)) != null;
		}
		return tableNearChest;
	}

	@Override
	public boolean start(CompanionEntity c) {
		tableCheckedAt = -100_000; // look again with fresh eyes
		return super.start(c);
	}
}
