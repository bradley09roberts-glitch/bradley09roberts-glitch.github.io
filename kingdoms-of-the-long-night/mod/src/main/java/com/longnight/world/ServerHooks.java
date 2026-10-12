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

    @Override
    public void personMoved(WorldSim sim, Person p) {
        Embodiment.discardBody(level(), p.id);
    }
}
