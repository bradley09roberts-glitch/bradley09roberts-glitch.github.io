package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Lost in a cave: find the way, spot by spot, to the nearest place under open sky the friend can walk to
 * ({@link Ways#toOpenSky}, at most {@value #SEARCH_RADIUS} blocks away), then walk it a few spots at a time, putting a
 * torch down now and then in the dark if they carry any (as a player marks the way). Done once they stand under the
 * sky; given up when the way stops working (the next plan digs up instead).
 */
final class CaveExitPlan implements Plan {
	static final int SEARCH_RADIUS = 48;
	private static final int SEARCH_SPOTS = 3000;
	/** How far along the way each leg aims, in spots. */
	private static final int LEG = 6;
	/** No spot of the way passed for this long: the way does not work. */
	private static final int NO_PROGRESS = 200;
	/** A torch every this many spots along a dark way. */
	private static final int TORCH_EVERY = 10;

	private @Nullable List<BlockPos> way;
	private int reached;
	private int sinceProgress;
	private int ticks;
	private int repath;
	private int lastTorchAt = -TORCH_EVERY;

	@Override
	public Kind kind() {
		return Kind.CAVE_EXIT;
	}

	@Override
	public boolean start(CompanionEntity c, ServerLevel level) {
		way = Ways.toOpenSky(level, c.blockPosition(), SEARCH_RADIUS, SEARCH_SPOTS);
		if (way == null || way.size() < 2) {
			return false;
		}
		Speech.say(c, Line.LOST_IN_CAVE);
		return true;
	}

	@Override
	public Status tick(CompanionEntity c, ServerLevel level) {
		List<BlockPos> w = way;
		if (w == null || ++ticks > 400 + w.size() * 30) {
			return Status.FAILED;
		}
		BlockPos feet = c.blockPosition();
		if (!Terrain.underground(level, feet) && level.getBrightness(LightLayer.SKY, feet) >= 10) {
			Speech.say(c, Line.FOUND_WAY_OUT);
			return Status.DONE;
		}
		int before = reached;
		for (int i = reached; i < Math.min(w.size(), reached + LEG * 2); i++) {
			if (w.get(i).distSqr(feet) <= 2) {
				reached = i;
			}
		}
		if (reached > before) {
			sinceProgress = 0;
			Wayfinder.progress(c);
			if (reached - lastTorchAt >= TORCH_EVERY && Senses.dark(c) && c.onGround() && placeTorch(c, level, feet)) {
				lastTorchAt = reached;
			}
		} else if (++sinceProgress > NO_PROGRESS) {
			return Status.FAILED;
		}
		if (reached >= w.size() - 1) {
			return Status.DONE; // at the end of the way (the sky is just above)
		}
		BlockPos aim = w.get(Math.min(w.size() - 1, reached + LEG));
		if (--repath <= 0 || c.getNavigation().isDone()) {
			repath = 10;
			if (!c.getNavigation().moveTo(aim.getX() + 0.5, aim.getY(), aim.getZ() + 0.5, 1.0)) {
				// No path for the search to agree on (a tight squeeze): head straight for the next spot of the way.
				BlockPos step = w.get(Math.min(w.size() - 1, reached + 1));
				c.getMoveControl().setWantedPosition(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 1.0);
				if (step.getY() > feet.getY() && c.onGround()) {
					c.getJumpControl().jump();
				}
			}
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(aim));
		return Status.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.getNavigation().stop();
	}

	/**
	 * Puts a carried torch down where the friend stands, if it can stay there (a floor to stand on) and the edit rules
	 * allow (never near anything a player built). Torches mark the way, as a player's do, and are left.
	 */
	static boolean placeTorch(CompanionEntity c, ServerLevel level, BlockPos at) {
		if (!c.backpack().has(s -> s.is(Items.TORCH))) {
			return false;
		}
		BlockState torch = Blocks.TORCH.defaultBlockState();
		if (!level.getBlockState(at).isAir() || !torch.canSurvive(level, at)) {
			return false;
		}
		return c.actions().place(at, torch, s -> s.is(Items.TORCH), WorldEditGuard.Reason.SURVIVAL);
	}
}
