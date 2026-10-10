package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Putting up a bell of their own: once the supply chest holds a bell and the village has none at all (no old village
 * bell, and no town hall standing or going up, which hangs the chest's bell itself), the builder (anyone, in their spare time, when the builder is not about) takes it
 * from the chest and stands it on the ground at the square, a few blocks from the campfire, on a free spot that is no
 * building's site and nowhere near a player's build ({@link Bells#squareSpot}), through the edit guard like any
 * building block. The friends cannot make bells; they only hang one the camp has. By day, never while the alarm rings.
 */
final class PutUpBellTask implements CompanionTask {
	static final String ID = "defence.put_up_bell";
	private static final double SCORE = 35;

	private @Nullable BlockPos spot;
	private boolean fetched;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "putting up the village bell";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!FriendsConfig.get().villageDefence || c.isChild() || !(c.level() instanceof ServerLevel level)
			|| Camp.isNight(level) || Alarm.isActive() || !Area.atHome(c)) {
			return 0;
		}
		if (!Bells.known(level).isEmpty() || Bells.townHallPlanned(level)) {
			return 0; // a bell already, or the town hall will hang the chest's bell (the builder's repair job)
		}
		boolean carrying = c.backpack().has(s -> s.is(Items.BELL));
		return carrying || Bells.inChest(level) > 0 ? SCORE : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		spot = Bells.squareSpot(level, Camp.data(level.getServer()));
		fetched = c.backpack().has(s -> s.is(Items.BELL));
		return spot != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		BlockPos to = spot;
		if (to == null || !(c.level() instanceof ServerLevel level) || Alarm.isActive()) {
			return TaskStatus.FAILURE;
		}
		if (!fetched) {
			ChestWalk.State walk = ChestWalk.tick(c);
			if (walk == ChestWalk.State.FAILED) {
				return TaskStatus.FAILURE;
			}
			if (walk == ChestWalk.State.ARRIVED) {
				Optional<Container> chest = ChestWalk.chest(c);
				if (chest.isEmpty() || SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(Items.BELL), 1) < 1) {
					Bells.invalidate();
					return TaskStatus.FAILURE; // someone took it first
				}
				fetched = true;
			}
			return TaskStatus.RUNNING;
		}
		if (!Bells.known(level).isEmpty()) {
			return TaskStatus.SUCCESS; // a bell went up meanwhile; the one carried goes back with the next delivery
		}
		if (!c.actions().canReach(to)) {
			if (!c.actions().walkTo(to, 2.0) && c.actions().isStuck()) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		Direction facing = Direction.fromYRot(c.getYRot()).getOpposite();
		BlockState bell = Blocks.BELL.defaultBlockState()
			.setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR)
			.setValue(BellBlock.FACING, facing.getAxis().isHorizontal() ? facing : Direction.NORTH);
		if (!bell.canSurvive(level, to)) {
			return TaskStatus.FAILURE;
		}
		if (c.actions().place(to, bell, s -> s.is(Items.BELL), WorldEditGuard.Reason.BUILD)) {
			CampData data = Camp.data(level.getServer());
			Bells.rememberOwnBell(data, to);
			return TaskStatus.SUCCESS;
		}
		return TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		spot = null;
		fetched = false;
	}

	@Override
	public int failureCooldown() {
		return 20 * 120;
	}

	@Override
	public int successCooldown() {
		return 20 * 600;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
