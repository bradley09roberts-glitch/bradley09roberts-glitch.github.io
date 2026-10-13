package com.longnight.world;

import com.longnight.entity.CitizenEntity;
import com.longnight.registry.ModEntities;
import com.longnight.sim.Commodity;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.Threats;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Embodied raids. A raid due today waits until dusk. If players are still near the target, the
 * band's real members walk in from the direction of their camp; otherwise the clash is resolved
 * in the abstract. A raid ends when the raiders are dead or routed, or at dawn.
 */
public final class RaidManager {
    private RaidManager() {}

    static final class Raid {
        UUID target, source;
        final List<UUID> bodies = new ArrayList<>();
        int startCount;
        long started;
        int plundered;
        boolean spawned;
    }

    private static final Map<UUID, Raid> RAIDS = new HashMap<>();

    public static void schedule(Settlement target, Settlement source) {
        Raid r = new Raid();
        r.target = target.id;
        r.source = source.id;
        RAIDS.put(target.id, r);
    }

    public static boolean active(Settlement s) {
        Raid r = RAIDS.get(s.id);
        return r != null && r.spawned;
    }

    public static void tick(ServerLevel level, WorldSim sim) {
        long time = level.getDayTime() % 24000L;
        long day = WorldSim.dayOf(level);
        Iterator<Map.Entry<UUID, Raid>> it = RAIDS.entrySet().iterator();
        while (it.hasNext()) {
            Raid r = it.next().getValue();
            Settlement s = sim.settlement(r.target);
            Settlement camp = sim.settlement(r.source);
            if (s == null) {
                it.remove();
                continue;
            }
            if (!r.spawned) {
                if (time < 12000 || time > 23000) continue; // wait for dusk
                if (!Embodiment.isActive(s) || camp == null) {
                    if (camp != null) Threats.resolveAbstract(sim, s, camp, day);
                    else Threats.clearRaid(s);
                    it.remove();
                    continue;
                }
                spawn(level, sim, s, camp, r);
                continue;
            }
            int alive = 0;
            for (UUID id : r.bodies) {
                if (level.getEntity(id) instanceof CitizenEntity c && c.isAlive()) alive++;
            }
            boolean dawn = time > 23000 || time < 1000;
            boolean routed = alive <= r.startCount / 3;
            if (alive == 0 || routed || dawn) {
                // survivors withdraw to their camp
                for (UUID id : r.bodies) {
                    if (level.getEntity(id) instanceof CitizenEntity c && c.isAlive()) {
                        c.raidTarget = null;
                        c.discard();
                    }
                }
                boolean repelled = alive == 0 || routed;
                Threats.endPhysicalRaid(sim, s, camp, day, repelled, r.plundered);
                Embodiment.announce(level, s, repelled ? "The raiders break and flee into the dark!" : "The raiders melt away at dawn with their plunder.");
                it.remove();
            }
        }
    }

    static void spawn(ServerLevel level, WorldSim sim, Settlement s, Settlement camp, Raid r) {
        double dx = camp.center.getX() - s.center.getX(), dz = camp.center.getZ() - s.center.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        int dist = s.radius + 18;
        BlockPos entry = Places.ground(level, s.center.offset((int) (dx / len * dist), 0, (int) (dz / len * dist)));
        if (!level.isLoaded(entry) || !level.isPositionEntityTicking(entry)) {
            Threats.resolveAbstract(sim, s, camp, WorldSim.dayOf(level));
            r.spawned = true;
            r.startCount = 0;
            return;
        }
        long day = WorldSim.dayOf(level);
        for (Person p : sim.residents(camp)) {
            if (!sim.isAdult(p, day)) continue;
            Embodiment.discardBody(level, p.id);
            CitizenEntity e = ModEntities.CITIZEN.get().create(level);
            if (e == null) continue;
            BlockPos at = Places.ground(level, entry.offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2));
            e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
            e.bind(p, camp, day, sim);
            e.raidTarget = s.id;
            e.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
            level.addFreshEntity(e);
            r.bodies.add(e.getUUID());
            Embodiment.trackRaider(p.id, e.getUUID());
        }
        r.startCount = r.bodies.size();
        r.started = level.getGameTime();
        r.spawned = true;
        s.raidWarning = 3;
        level.playSound(null, s.center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64f, 1f);
        Embodiment.announce(level, s, "Bells ring in " + s.name + "! Outlaws of " + camp.name + " are attacking from the "
                + compass(dx, dz) + "!");
    }

    static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        String[] dirs = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return dirs[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }

    /** A raider at the stores carries off goods. */
    public static void plunder(ServerLevel level, WorldSim sim, Settlement s, CitizenEntity e, Person p) {
        Raid r = RAIDS.get(s.id);
        if (r == null) return;
        Settlement camp = sim.settlement(r.source);
        for (Commodity c : new Commodity[]{Commodity.BREAD, Commodity.MEAT, Commodity.GRAIN, Commodity.TOOLS, Commodity.IRON}) {
            double q = Math.min(s.stock.get(c), 6);
            if (q >= 1 && s.stock.take(c, q)) {
                if (camp != null) camp.stock.add(c, q);
                r.plundered += (int) q;
                level.players().forEach(pl -> {
                    if (pl.distanceToSqr(e) < 40 * 40) pl.displayClientMessage(Component.literal(p.given + " is carrying off " + c.displayName.toLowerCase() + "!"), true);
                });
                break;
            }
        }
    }

    /** Server stopping: a raid in progress ends with the raiders withdrawing, so it is never fought twice. */
    public static void clear() {
        RAIDS.clear();
    }

    public static void endAllOnStop(WorldSim sim) {
        for (Raid r : RAIDS.values()) {
            Settlement s = sim.settlement(r.target);
            if (s != null && r.spawned) Threats.endPhysicalRaid(sim, s, sim.settlement(r.source), sim.lastDay, false, r.plundered);
            else if (s != null) Threats.clearRaid(s);
        }
        RAIDS.clear();
    }
}
