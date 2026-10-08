package io.github.bradley09roberts.hardcorefriends.ai.role.scout;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import io.github.bradley09roberts.hardcorefriends.ai.role.ScoutSenses;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.EntityApproach;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * After exploring, Scout walks over to a player who is in camp and sums up what was found since the last report
 * (ore spots, trees, villages, lava). The summary uses the DISCOVERY line, or is told directly if Scout spoke
 * about a discovery very recently. Nobody else can tell what Scout saw, so once there is news and someone to tell,
 * the report comes before any other work Scout might be helping with.
 */
public final class ReportTask implements CompanionTask {
	/** Main work: above anything Scout would do outside exploring (see {@code SpecialityTask}). */
	private static final double SCORE = 60;
	private static final double TALK_DISTANCE = 3.5;

	private @Nullable UUID listener;
	private final EntityApproach approach = new EntityApproach();

	@Override
	public String id() {
		return "scout.report";
	}

	@Override
	public String describe() {
		return "reporting to the team";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		ScoutLog log = ScoutLog.of(Camp.data(level.getServer()));
		if (!log.exploredSinceReport() || log.unreportedTotal() == 0) {
			return 0;
		}
		return nearestPlayerInCamp(c, level) != null ? SCORE : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerPlayer player = nearestPlayerInCamp(c, (ServerLevel) c.level());
		listener = player == null ? null : player.getUUID();
		approach.reset();
		return listener != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		ServerPlayer player = listener == null ? null : level.getServer().getPlayerList().getPlayer(listener);
		if (player == null || player.level() != level || player.isSpectator() || !ScoutSenses.isNearCamp(level, player)) {
			return TaskStatus.FAILURE;
		}
		if (!approach.walk(c, player, TALK_DISTANCE)) {
			return approach.isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(player);
		ScoutLog log = ScoutLog.of(Camp.data(level.getServer()));
		String summary = player.getName().getString() + ", I " + log.summary() + ".";
		if (!Speech.say(c, Line.DISCOVERY, summary) && !"quiet".equals(FriendsConfig.get().chatter)) {
			Speech.tell(player, FriendId.SCOUT, "Report: I " + log.summary() + ".");
		}
		log.reported();
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		listener = null;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}

	private static @Nullable ServerPlayer nearestPlayerInCamp(CompanionEntity c, ServerLevel level) {
		ServerPlayer best = null;
		double bestDist = Double.MAX_VALUE;
		for (ServerPlayer p : ScoutSenses.playersNearCamp(level)) {
			double d = p.distanceToSqr(c);
			if (d < bestDist) {
				bestDist = d;
				best = p;
			}
		}
		return best;
	}
}
