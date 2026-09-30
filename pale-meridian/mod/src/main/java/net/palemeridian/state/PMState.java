package net.palemeridian.state;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Read/write access to the campaign state, which lives in the scoreboard objective {@code pm.world}
 * (fake players). The data pack owns the campaign logic; Java only reads flags and serves requests.
 */
public final class PMState {
	public static final String WORLD = "pm.world";

	private PMState() {
	}

	public static Objective world(MinecraftServer server) {
		Scoreboard sb = server.getScoreboard();
		Objective obj = sb.getObjective(WORLD);
		if (obj == null) {
			// Same definition the data pack's load function uses; harmless if created first here.
			obj = sb.addObjective(WORLD, ObjectiveCriteria.DUMMY, Component.literal(WORLD), ObjectiveCriteria.RenderType.INTEGER, false, null);
		}
		return obj;
	}

	public static int get(MinecraftServer server, String holder) {
		ReadOnlyScoreInfo info = server.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), world(server));
		return info == null ? 0 : info.value();
	}

	public static void set(MinecraftServer server, String holder, int value) {
		server.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), world(server)).set(value);
	}
}
