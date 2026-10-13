package com.grandline.npc.crew;

import com.grandline.combat.CombatState;
import com.grandline.combat.Cover;
import com.grandline.npc.Archetype;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.Characters;
import com.grandline.ship.ShipEntity;
import com.grandline.world.Island;
import com.grandline.world.TownProtection;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * What every companion system shares: who counts as a foe, whether the party is in a fight, what a safe place is,
 * what the captain is looking at, and the short lines companions answer orders with.
 */
public final class Companions {
    private Companions() {}

    // ================================================================== foes

    /**
     * Whether a companion may fight this. Only foes of the party: whoever is attacking the captain or a crewmate (or
     * hit one of them lately), people hostile to the captain on sight (aggressive and lawful NPCs that would attack the
     * captain anyway, villains), and monsters. Never the party itself, never neutral or friendly people (a passive
     * villager the captain shoved is still not a foe), never the downed, the surrendered or the arrested.
     */
    public static boolean isFoe(GLNpc companion, @Nullable LivingEntity e) {
        if (e == null || e == companion || !e.isAlive() || e.isRemoved() || e.isSpectator() || e instanceof ArmorStand) return false;
        if (e.level() != companion.level() || CrewManager.allied(companion, e)) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (e instanceof GLNpc g && (g.flag(GLNpc.FLAG_SURRENDER) || g.flag(GLNpc.FLAG_ARRESTED) || CompanionCare.isDowned(g))) return false;
        // attacking the party now
        if (e instanceof Mob mob) {
            LivingEntity t = mob.getTarget();
            if (t != null && t != e && t.isAlive() && CrewManager.allied(companion, t)) return true;
        }
        // hit the companion lately
        if (companion.getLastHurtByMob() == e) return true;
        ServerPlayer cap = CrewManager.captainOf(companion);
        if (cap == null) return false;
        if (cap.getLastHurtByMob() == e && cap.tickCount - cap.getLastHurtByMobTimestamp() < 200) return true;
        if (e instanceof GLNpc g) {
            // hostile to the captain on sight: aggressive or lawful people (and villains), not the passive or defensive
            Archetype.Hostility h = g.archetype().hostility();
            boolean onSight = h == Archetype.Hostility.AGGRESSIVE || h == Archetype.Hostility.LAWFUL;
            return onSight && g.distanceToSqr(cap) < 32 * 32 && g.isHostileTo(cap);
        }
        if (e instanceof Player) return false; // other captains: only when they attack (above)
        return e instanceof Enemy && !(e instanceof NeutralMob);
    }

    // ================================================================== the party

    /** Loaded bodies of the captain's active companions in the captain's level. */
    public static List<HumanoidNpc> activeBodies(ServerPlayer cap) {
        CrewManager.Crew c = CrewManager.crewOf(cap);
        if (c == null) return List.of();
        List<HumanoidNpc> out = new ArrayList<>(2);
        NotableState st = NotableState.get(cap.server);
        for (CrewManager.Member m : c.members.values()) {
            if (!m.active || m.stowed != null) continue;
            HumanoidNpc h = CrewManager.find(cap.server, st.entry(m.id).entity);
            if (h != null && h.level() == cap.level()) out.add(h);
        }
        return out;
    }

    /** Position of this companion among the active ones (0 or 1): which side of the formation it takes. */
    public static int slot(ServerPlayer cap, GLNpc npc) {
        int i = 0;
        CrewManager.Crew c = CrewManager.crewOf(cap);
        if (c == null) return 0;
        for (CrewManager.Member m : c.members.values()) {
            if (!m.active) continue;
            if (m.id.equals(npc.notableId())) return i;
            i++;
        }
        return 0;
    }

    /**
     * What the captain is fighting: the lock-on target, then whatever the captain struck in the last five seconds
     * (Combat Mode blows do not set vanilla's "last hurt mob", so the victims are asked who hurt them).
     */
    @Nullable
    public static LivingEntity captainTarget(ServerPlayer cap) {
        CombatState cs = CombatState.of(cap);
        if (cs.lockTarget >= 0 && cap.level().getEntity(cs.lockTarget) instanceof LivingEntity le && le.isAlive()) return le;
        return struck(cap);
    }

    /** Whatever the captain struck in the last five seconds (nearest first), or null. */
    @Nullable
    public static LivingEntity struck(ServerPlayer cap) {
        LivingEntity lh = cap.getLastHurtMob();
        if (lh != null && lh.isAlive() && cap.tickCount - cap.getLastHurtMobTimestamp() < 100) return lh;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : cap.level().getEntitiesOfClass(LivingEntity.class, cap.getBoundingBox().inflate(24),
                e -> e != cap && e.isAlive() && e.getLastHurtByMob() == cap && e.tickCount - e.getLastHurtByMobTimestamp() < 100)) {
            double d = e.distanceToSqr(cap);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    // ================================================================== fights

    /** Per captain (character id): is the party in a fight, since when, and how many foes it has felled. */
    static final class Fight {
        boolean inFight;
        long lastCombat = -100000, startedAt;
        int kills;
    }

    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    /** Ticks without combat before a fight counts as over. */
    public static final int FIGHT_OVER = 100;

    static Fight fight(UUID characterId) {
        return FIGHTS.computeIfAbsent(characterId, k -> new Fight());
    }

    public static boolean inCombat(ServerPlayer cap) {
        Fight f = FIGHTS.get(Characters.get(cap).characterId);
        return f != null && f.inFight;
    }

    /** Ticks since the party's current fight started (0 out of a fight). */
    public static long fightAge(ServerPlayer cap, long now) {
        Fight f = FIGHTS.get(Characters.get(cap).characterId);
        return f != null && f.inFight ? now - f.startedAt : 0;
    }

    /** Called every half second per online captain: fights start and end here (heals per fight, fights won). */
    static void updateFight(ServerPlayer cap, UUID charId, CrewManager.Crew crew, long now) {
        Fight f = fight(charId);
        if (detectCombat(cap)) {
            if (!f.inFight) begin(f, crew, now);
            f.lastCombat = now;
        } else if (f.inFight && now - f.lastCombat >= FIGHT_OVER) {
            f.inFight = false;
            if (f.kills > 0) CompanionCredit.fightWon(cap, crew);
            f.kills = 0;
        }
    }

    /** A new fight: every support's heals and encore are fresh again. */
    private static void begin(Fight f, CrewManager.Crew crew, long now) {
        f.inFight = true;
        f.kills = 0;
        f.startedAt = now;
        for (CrewManager.Member m : crew.members.values()) {
            m.fightHeals = 0;
            m.encoreUsed = false;
        }
    }

    /** Something happened that is certainly a fight (the party just felled a foe): start or extend it now. */
    static Fight markCombat(ServerPlayer cap, long now) {
        Fight f = fight(Characters.get(cap).characterId);
        CrewManager.Crew c = CrewManager.crewOf(cap);
        if (!f.inFight && c != null) begin(f, c, now);
        f.lastCombat = now;
        return f;
    }

    /** Self-test hook: the current fight is over now (the next combat starts a new one). */
    public static void endFightForTest(ServerPlayer cap) {
        Fight f = FIGHTS.get(Characters.get(cap).characterId);
        if (f != null) {
            f.inFight = false;
            f.kills = 0;
            f.lastCombat = -100000;
        }
    }

    /** Self-test hook: run the fight bookkeeping now. */
    public static void refreshFight(ServerPlayer cap) {
        CrewManager.Crew c = CrewManager.crewOf(cap);
        if (c != null) updateFight(cap, Characters.get(cap).characterId, c, cap.level().getGameTime());
    }

    /** Someone is fighting the party, or the party someone, right now. */
    public static boolean detectCombat(ServerPlayer cap) {
        LivingEntity by = cap.getLastHurtByMob();
        if (by != null && by.isAlive() && cap.tickCount - cap.getLastHurtByMobTimestamp() < FIGHT_OVER) return true;
        LivingEntity hit = struck(cap);
        if (hit != null && !(hit instanceof GLNpc g && CrewManager.isCrewOf(g, cap))) return true;
        for (HumanoidNpc b : activeBodies(cap)) {
            LivingEntity t = b.getTarget();
            if (t != null && t.isAlive() && !CompanionCare.isDowned(b)) return true;
        }
        for (Mob mob : cap.level().getEntitiesOfClass(Mob.class, cap.getBoundingBox().inflate(24), m -> m.isAlive() && m.getTarget() != null)) {
            LivingEntity t = mob.getTarget();
            if (t == cap || t instanceof GLNpc g && CrewManager.isCrewOf(g, cap) && !CompanionCare.isDowned(g)) return true;
        }
        return false;
    }

    // ================================================================== places

    /**
     * A safe place to change who is active: inside a settlement (its streets and plots, the area town protection
     * covers) or aboard the captain's own ship.
     */
    public static boolean safePlace(ServerPlayer sp) {
        for (ShipEntity s : sp.level().getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(64)))
            if (s.owner != null && s.mayCommand(sp) && (sp.getVehicle() == s || s.carries(sp))) return true;
        WorldPlan plan = WorldPlan.forLevel(sp.level());
        if (plan == null) return false;
        int x = sp.getBlockX(), z = sp.getBlockZ();
        for (Island isl : plan.around(x, z))
            if (isl != null && isl.settlementType != SettlementType.NONE && isl.settlement().bounds(x, z)) return true;
        return TownProtection.protectedAt(plan, x, z);
    }

    /** The living thing the player is looking at within {@code range} blocks, in the open (never through walls). */
    @Nullable
    public static LivingEntity lookedAt(ServerPlayer sp, double range) {
        Vec3 eye = sp.getEyePosition(), look = sp.getViewVector(1f);
        Vec3 end = eye.add(look.scale(range));
        var block = sp.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        double len = eye.distanceTo(end);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        AABB box = new AABB(eye, end).inflate(2);
        for (LivingEntity e : sp.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != sp && e.isAlive() && !e.isSpectator())) {
            // a little slack for far targets: aiming at someone 20 blocks off should not need pixel precision
            double d = e.getBoundingBox().getCenter().distanceTo(eye);
            AABB b = e.getBoundingBox().inflate(0.3 + Math.min(1.0, d * 0.03));
            var hit = b.clip(eye, end);
            if (hit.isEmpty() && !b.contains(eye)) continue;
            if (d > len + 1 || d >= bd || !Cover.canSee(sp, e)) continue;
            bd = d;
            best = e;
        }
        return best;
    }

    /** Put a companion next to the captain (a safe spot within a few blocks). */
    public static boolean teleportNear(GLNpc e, ServerPlayer cap) {
        BlockPos spot = NotableSpawner.safeSpotNear(cap.serverLevel(), cap.blockPosition());
        if (spot == null || !cap.serverLevel().isPositionEntityTicking(spot)) return false; // never into a chunk that is still loading
        e.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        e.getNavigation().stop();
        e.fallDistance = 0;
        return true;
    }

    // ================================================================== lines

    /** A companion answers the captain in chat: {@code Name: "line"}. */
    public static void say(GLNpc npc, ServerPlayer cap, String lineKey) {
        cap.sendSystemMessage(Component.translatable("grandline.crew.say", npc.getDisplayName(), Component.translatable(lineKey))
                .withStyle(ChatFormatting.WHITE));
    }

    /** The order acknowledgement line for a companion's role ({@code grandline.crew.line.<order>.<role>}). */
    public static void ack(GLNpc npc, ServerPlayer cap, String order) {
        say(npc, cap, "grandline.crew.line." + order + "." + CompanionRole.of(npc).id());
    }

    static boolean near(Entity a, Entity b, double r) {
        return a.level() == b.level() && a.distanceToSqr(b) <= r * r;
    }
}
