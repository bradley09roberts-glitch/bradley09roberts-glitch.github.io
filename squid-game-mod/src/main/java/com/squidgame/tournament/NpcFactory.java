package com.squidgame.tournament;

import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.GuardEntity;
import com.squidgame.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Spawns and removes the NPC bodies (contestants and guards). Everything spawned here is tagged for clean-up. */
public final class NpcFactory {
    public static final String TAG_NPC = "squidgame_npc";
    public static final String TAG_GUARD = "squidgame_guard";

    private NpcFactory() {
    }

    public static ContestantEntity spawnContestant(ServerLevel level, Contestant c, Vec3 pos, float yaw) {
        level.getChunk(net.minecraft.core.BlockPos.containing(pos));
        ContestantEntity e = ModEntities.CONTESTANT.create(level);
        if (e == null) {
            return null;
        }
        e.configure(c.number, c.name, c.personality, c.appearance, c.id.getLeastSignificantBits() ^ c.id.getMostSignificantBits());
        e.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.addTag(TAG_NPC);
        e.setPersistenceRequired();
        level.addFreshEntity(e);
        c.setBodyEntity(e);
        return e;
    }

    public static GuardEntity spawnGuard(ServerLevel level, Vec3 pos, float yaw, int rank) {
        level.getChunk(net.minecraft.core.BlockPos.containing(pos));
        GuardEntity g = ModEntities.GUARD.create(level);
        if (g == null) {
            return null;
        }
        g.setRank(rank);
        g.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
        g.setYHeadRot(yaw);
        g.setYBodyRot(yaw);
        g.addTag(TAG_GUARD);
        g.setPersistenceRequired();
        level.addFreshEntity(g);
        return g;
    }

    /** Removes every NPC / guard (and static game entities) in the level. Returns how many were removed. */
    public static int purge(ServerLevel level) {
        int n = 0;
        List<net.minecraft.world.entity.Entity> victims = new ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e instanceof ContestantEntity || e instanceof GuardEntity
                    || e instanceof com.squidgame.entity.DollEntity || e instanceof com.squidgame.entity.RopeEntity
                    || e instanceof com.squidgame.entity.MarbleProjectile
                    || e.getTags().contains(TAG_NPC) || e.getTags().contains(TAG_GUARD)
                    || e.getTags().contains("squidgame_door") || e.getTags().contains("squidgame_temp")) {
                victims.add(e);
            }
        }
        for (var e : victims) {
            e.discard();
            n++;
        }
        return n;
    }
}
