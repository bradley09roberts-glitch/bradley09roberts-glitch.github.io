package com.squidgame.tournament;

import com.squidgame.build.Marker;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.game.GameContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Moves contestants (human or AI) between positions, keeping anti-teleport checks and chunk loading consistent. */
public final class Teleporter {
    private Teleporter() {
    }

    public static void teleport(GameContext ctx, Contestant c, Vec3 pos, float yaw) {
        teleport(ctx.level, c, pos, yaw);
    }

    public static void teleport(ServerLevel level, Contestant c, Vec3 pos, float yaw) {
        level.getChunk(net.minecraft.core.BlockPos.containing(pos));
        if (c.isHumanControlled()) {
            ServerPlayer p = c.player(level.getServer());
            if (p != null) {
                p.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0f);
                p.setDeltaMovement(Vec3.ZERO);
                p.hurtMarked = true;
                p.fallDistance = 0;
                Restrictions.noteTeleport(p);
                return;
            }
        }
        ContestantEntity e = c.npc(level);
        if (e != null) {
            e.teleportSafely(pos);
            e.faceYaw(yaw);
        }
    }

    /** Assigns contestants to slots in order (wrapping if there are fewer slots than contestants). */
    public static void spread(ServerLevel level, List<Contestant> contestants, List<Marker> slots) {
        if (slots.isEmpty()) {
            return;
        }
        for (int i = 0; i < contestants.size(); i++) {
            Marker m = slots.get(i % slots.size());
            double jitter = i >= slots.size() ? 0.35 * (i / slots.size()) : 0.0;
            teleport(level, contestants.get(i), new Vec3(m.x() + jitter, m.y(), m.z()), m.yaw());
        }
    }
}
