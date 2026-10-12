package com.longnight.game;

import com.longnight.entity.CitizenEntity;
import com.longnight.sim.Faction;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crimes are noticed only by witnesses. Assault, theft and murder cost reputation; serious crimes
 * make the watch hunt the culprit for a while. Killing outlaws earns gratitude.
 */
public final class Crime {
    private Crime() {}

    /** settlement -> player -> game time until which the watch hunts them */
    private static final Map<UUID, Map<UUID, Long>> WANTED = new HashMap<>();

    public static boolean isWanted(CitizenEntity guard, Player p) {
        if (guard.settlementId == null) return false;
        Map<UUID, Long> m = WANTED.get(guard.settlementId);
        if (m == null) return false;
        Long until = m.get(p.getUUID());
        return until != null && until > guard.level().getGameTime();
    }

    static int witnesses(ServerLevel level, BlockPos at, UUID settlement) {
        return level.getEntitiesOfClass(CitizenEntity.class, new net.minecraft.world.phys.AABB(at).inflate(16),
                c -> settlement.equals(c.settlementId) && !c.isHostile()).size();
    }

    static void want(ServerLevel level, UUID settlement, ServerPlayer p, long ticks) {
        WANTED.computeIfAbsent(settlement, k -> new HashMap<>()).merge(p.getUUID(), level.getGameTime() + ticks, Math::max);
    }

    public static void assault(ServerLevel level, ServerPlayer p, CitizenEntity victim) {
        WorldSim sim = WorldSim.get(level.getServer());
        Settlement s = sim.settlement(victim.settlementId);
        Person v = sim.person(victim.personId);
        if (s == null || v == null) return;
        v.changeOpinion(p.getUUID(), -15);
        if (witnesses(level, victim.blockPosition(), s.id) > 1) {
            s.changeReputation(p.getUUID(), -6);
            if (s.reputationOf(p.getUUID()) < -20) want(level, s.id, p, 2400);
            p.displayClientMessage(Component.literal("§cTownsfolk saw you strike " + v.given + "!"), true);
        }
        sim.setDirty();
    }

    public static void murder(ServerLevel level, ServerPlayer p, CitizenEntity victim, Person v) {
        WorldSim sim = WorldSim.get(level.getServer());
        Settlement s = sim.settlement(victim.settlementId);
        if (s == null) return;
        long day = WorldSim.dayOf(level.getServer().overworld());
        boolean seen = witnesses(level, victim.blockPosition(), s.id) > 0;
        if (seen) {
            s.changeReputation(p.getUUID(), -50);
            Faction f = sim.faction(s.faction);
            if (f != null) f.changePlayerRep(p.getUUID(), -30);
            want(level, s.id, p, 24000 * 3L);
            sim.addNews(day, "crime", p.getGameProfile().getName() + " murdered " + v.fullName() + " in " + s.name + ".", s, 3,
                    "person", v.fullName());
            p.sendSystemMessage(Component.literal("§4Murder! The watch of " + s.name + " will hunt you."));
        } else {
            sim.addNews(day, "death", v.fullName() + " was found dead in " + s.name + "; no one saw who did it.", s, 2, "person", v.fullName());
        }
        for (Person r : sim.residents(s)) if (r.opinionOf(v.id) > 30 && seen) r.changeOpinion(p.getUUID(), -60);
    }

    public static void slewOutlaw(ServerLevel level, ServerPlayer p, CitizenEntity victim, Person v) {
        WorldSim sim = WorldSim.get(level.getServer());
        long day = WorldSim.dayOf(level.getServer().overworld());
        Settlement target = sim.settlement(victim.raidTarget);
        Settlement camp = sim.settlement(victim.settlementId);
        for (Settlement s : sim.settlements.values()) {
            if (s.kind != Settlement.Kind.SETTLEMENT || s.center == null || camp == null || camp.center == null) continue;
            if (s == target || s.center.distSqr(camp.center) < 900 * 900) s.changeReputation(p.getUUID(), s == target ? 12 : 4);
        }
        if (target != null) {
            sim.addNews(day, "raid", p.getGameProfile().getName() + " cut down " + v.fullName() + ", an outlaw, defending " + target.name + ".",
                    target, 2, "person", p.getGameProfile().getName());
        }
        sim.setDirty();
    }

    /** When the last outlaw of a band falls, bounties posted against it are paid. */
    public static void afterOutlawDeath(ServerLevel level, ServerPlayer p, WorldSim sim, UUID campId) {
        Settlement camp = sim.settlement(campId);
        if (camp == null || camp.kind != Settlement.Kind.BANDIT_CAMP || !sim.residents(camp).isEmpty()) return;
        int total = 0;
        for (String paid : com.longnight.sim.Contracts.campDestroyed(sim, camp, p.getUUID())) total += Integer.parseInt(paid.split(":")[1]);
        if (total > 0) {
            com.longnight.game.Coins.give(p, total);
            p.sendSystemMessage(Component.literal("\u00a76The outlaws of " + camp.name + " are broken. Bounties paid: " + com.longnight.game.Coins.format(total) + "."));
        } else {
            p.sendSystemMessage(Component.literal("\u00a76The outlaws of " + camp.name + " are broken."));
        }
        sim.addNews(WorldSim.dayOf(level.getServer().overworld()), "bandit_camp", p.getGameProfile().getName() + " broke the outlaw band of " + camp.name + ".",
                sim.nearestSettlement(camp.center, Settlement.Kind.SETTLEMENT), 3, "person", p.getGameProfile().getName());
    }

    /** Breaking a settlement's buildings in front of witnesses is vandalism and theft. */
    public static void vandalism(ServerLevel level, ServerPlayer p, BlockPos pos) {
        WorldSim sim = WorldSim.get(level.getServer());
        Settlement s = sim.settlementAt(pos, 0);
        if (s == null || s.kind != Settlement.Kind.SETTLEMENT || s.playerFounded && p.getUUID().equals(s.founderPlayer)) return;
        boolean inBuilding = false;
        for (var b : s.buildings) if (b.origin != null && b.placed > 0 && b.contains(pos, 0)) inBuilding = true;
        if (!inBuilding) return;
        if (witnesses(level, pos, s.id) > 0) {
            s.changeReputation(p.getUUID(), -3);
            p.displayClientMessage(Component.literal("§cSomeone saw you damage their property."), true);
            if (s.reputationOf(p.getUUID()) < -25) want(level, s.id, p, 2400);
            sim.setDirty();
        }
    }

    public static void clear() {
        WANTED.clear();
    }
}
