package com.longnight.world;

import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldHooks;
import com.longnight.sim.WorldSim;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Connects the simulation to the running server. */
public class ServerHooks implements WorldHooks {
    private final MinecraftServer server;

    public ServerHooks(MinecraftServer server) {
        this.server = server;
    }

    ServerLevel level() {
        return server.overworld();
    }

    @Override
    public void personDied(WorldSim sim, Person p, Settlement s) {
        Embodiment.discardBody(level(), p.id);
        if (s != null && s.materialized && Embodiment.isActive(s)) Placer.bury(level(), sim, s, p);
    }

    @Override
    public boolean isEmbodied(Settlement s) {
        return Embodiment.isActive(s);
    }

    @Override
    public boolean spawnRaid(WorldSim sim, Settlement target, Settlement source, int strength) {
        // every raid waits for dusk; at dusk it is fought in the world if a player is there, otherwise abstractly
        RaidManager.schedule(target, source);
        return true;
    }

    @Override
    public void announce(WorldSim sim, Settlement s, String message, int importance) {
        Embodiment.announce(level(), s, message);
    }

    /** terrain noise is costly with large terrain mods: the server thread may spend at most this much on it per tick */
    static final long NOISE_BUDGET_NANOS = 20_000_000L;
    private long noiseTick = -1, noiseNanos;
    private boolean guessed;

    @Override
    public boolean lastSurveyWasGuess() {
        return guessed;
    }

    @Override
    public boolean plotLooksBuildable(net.minecraft.core.BlockPos origin, int w, int d) {
        guessed = false;
        if (Placer.testGroundY != null) return true; // GameTest arena (roofed with barriers)
        ServerLevel level = level();
        boolean onThread0 = server.isSameThread();
        if (onThread0) {
            if (noiseTick != server.getTickCount()) {
                noiseTick = server.getTickCount();
                noiseNanos = 0;
            }
            if (noiseNanos > NOISE_BUDGET_NANOS && !Places.ready(level, origin)) {
                // out of time this tick and no real terrain to look at: say yes for now; the survey on the ground
                // (Placer.prepare) checks the plot before a single block is placed and moves the building if need be
                guessed = true;
                return true;
            }
        }
        long t0 = System.nanoTime();
        try {
            return survey(level, origin, w, d, onThread0);
        } finally {
            if (onThread0) noiseNanos += System.nanoTime() - t0;
        }
    }

    boolean survey(ServerLevel level, net.minecraft.core.BlockPos origin, int w, int d, boolean onThread) {
        var gen = level.getChunkSource().getGenerator();
        var rs = level.getChunkSource().randomState();
        int[][] pts = {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}, {w / 2, d / 2}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0;
        for (int i = 0; i < pts.length; i++) {
            int wx = origin.getX() + pts[i][0], wz = origin.getZ() + pts[i][1];
            int floor, surface;
            if (onThread && Places.ready(level, new net.minecraft.core.BlockPos(wx, 0, wz))) {
                // real terrain is available: use it (cheap)
                floor = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, wx, wz);
                surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz);
            } else {
                floor = gen.getBaseHeight(wx, wz, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                surface = i >= 3 ? gen.getBaseHeight(wx, wz, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, rs) : floor;
            }
            if (floor < surface - 1) wet++;
            min = Math.min(min, floor);
            max = Math.max(max, floor);
        }
        return max - min <= Placer.MAX_SPREAD && wet <= 1;
    }

    @Override
    public void personMoved(WorldSim sim, Person p) {
        Embodiment.discardBody(level(), p.id);
    }
}
