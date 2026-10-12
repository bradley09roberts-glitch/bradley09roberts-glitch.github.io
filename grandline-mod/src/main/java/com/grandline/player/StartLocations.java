package com.grandline.player;

import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/** Places new characters on their home Blue's starter island. */
public final class StartLocations {
    @Nullable
    public static BlockPos startPos(ServerLevel level, Blue blue) {
        WorldPlan plan = WorldPlan.of(level.getServer());
        if (plan == null) return null;
        Island isl = plan.starterIsland(blue);
        if (isl == null) return null;
        Settlement s = isl.settlement();
        int x = s.hasCenter ? s.centerX + 3 : isl.x, z = s.hasCenter ? s.centerZ + 6 : isl.z;
        level.getChunk(x >> 4, z >> 4); // generate
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    public static void sendToStart(ServerPlayer sp, CharacterData d) {
        ServerLevel ow = sp.server.overworld();
        BlockPos p = startPos(ow, d.origin);
        if (p == null) return;
        if (sp.level() != ow) sp.teleportTo(ow, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sp.getYRot(), sp.getXRot());
        else sp.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        sp.setRespawnPosition(ow.dimension(), p, 0, true, false);
        sp.fallDistance = 0;
    }

    private StartLocations() {}
}
