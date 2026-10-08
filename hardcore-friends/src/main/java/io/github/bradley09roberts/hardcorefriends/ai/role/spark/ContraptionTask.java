package io.github.bradley09roberts.hardcorefriends.ai.role.spark;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.BlueprintTask;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Part;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.camp.build.Supplies;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Spark's main job: build the next working redstone contraption for the camp's stage. The automatic door waits for
 * the cabin, the drop-off hopper for a supply chest with room on top, and the night lamps for nether materials.
 */
public final class ContraptionTask extends BlueprintTask {
	@Override
	public String id() {
		return "spark.contraption";
	}

	@Override
	protected WorldEditGuard.Reason reason() {
		return WorldEditGuard.Reason.INVENT;
	}

	@Override
	protected double baseScore() {
		return 55;
	}

	@Override
	protected @Nullable Blueprint choose(CompanionEntity c, CampData data) {
		ServerLevel level = (ServerLevel) c.level();
		for (Structures.Entry next : pending(data, Role.INVENTOR)) {
			Blueprint plan = Blueprints.forId(next.id()).orElse(null);
			if (plan == null || isSetAside(c, plan.id())) {
				continue;
			}
			if (plan == Blueprints.AUTO_DOOR && (!data.isCompleted(Structures.CABIN) || data.site(Structures.CABIN).isEmpty())) {
				continue; // waits for Oak's cabin
			}
			if (plan == Blueprints.HOPPER_DROPOFF && SupplyChest.of(level).isEmpty()) {
				continue;
			}
			if (plan == Blueprints.LAMP_POSTS && !lampMaterials(c, SupplyChest.of(level).orElse(null))) {
				continue; // optional: only once nether materials have turned up
			}
			return plan;
		}
		return null;
	}

	/** Two lamp posts need redstone, glowstone, quartz and glass. */
	private static boolean lampMaterials(CompanionEntity c, @Nullable Container chest) {
		int posts = Blueprints.LAMP_POSTS.parts();
		return Supplies.canMake(c, chest, Stock.REDSTONE_LAMP, posts) && Supplies.canMake(c, chest, Stock.DAYLIGHT_DETECTOR, posts);
	}

	@Override
	protected boolean finishWithoutBuilding(CompanionEntity c, CampData data, Blueprint plan) {
		if (plan == Blueprints.HOPPER_DROPOFF && data.site(plan.id()).isEmpty()) {
			List<Part> spot = SiteFinder.fixedParts((ServerLevel) c.level(), data, plan);
			if (spot != null && spot.isEmpty()) {
				// Something sits on top of the chest; it is not ours to move, so this contraption is skipped.
				data.markCompleted(plan.id());
				return true;
			}
		}
		return false;
	}
}
