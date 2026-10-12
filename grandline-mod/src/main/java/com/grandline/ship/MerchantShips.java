package com.grandline.ship;

import com.grandline.navigation.Ferries;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Merchant traffic of the living sea. A merchantman sails between settled islands of one sea with the ferries'
 * course-keeping ({@link Ferries#helm}: islands on the way are rounded, docks and rocks swept for), heaves to when a
 * player comes alongside or aboard (her captain trades over the rail: a "sea_merchant" shop), runs from pirates and
 * from anyone who attacked her, fights back when she outguns them, and strikes her colours when caught by a
 * stronger ship: her crew surrender, her cargo goes over the side as salvage and she may be taken as a prize. In the
 * Grand Line some sail in convoys escorted by Marine ships keeping station on her quarters.
 */
public final class MerchantShips {
    static final String[] NAMES = {"Golden Gull", "Salt Ledger", "Spice Wind", "Lucky Barrel", "Silver Herring", "Merry Tern", "Amber Bale", "Fair Profit"};

    /** A merchantman (or a Marine-escorted convoy of up to {@code maxShips}) on open water {@code dist} blocks from {@code near}. */
    public static List<ShipEntity> spawnNear(ServerLevel l, Vec3 near, SeaZone zone, int dist, boolean convoy, int maxShips, RandomSource r) {
        List<ShipEntity> out = new ArrayList<>();
        if (maxShips < 1) return out;
        for (int tries = 0; tries < 6; tries++) {
            double ang = r.nextDouble() * Math.PI * 2;
            ShipClass cls = convoy ? (zone == SeaZone.NEW_WORLD ? ShipClass.GALLEON : ShipClass.BRIGANTINE) : LivingSea.classFor(zone, r, 0);
            Vec3 p = LivingSea.openWater(l, near.x + Math.cos(ang) * dist, near.z + Math.sin(ang) * dist, cls.length / 2 + 4);
            if (p == null) continue;
            // a course across the observer's view rather than straight at them
            Vec3 across = new Vec3(-Math.sin(ang), 0, Math.cos(ang)).scale(r.nextBoolean() ? 1 : -1);
            WorldPlan plan = WorldPlan.forLevel(l);
            Island dest = plan == null ? null : destination(plan, p, zone, across, null, r);
            if (dest == null) continue;
            ShipEntity m = spawn(l, p, LivingSea.yawTo(p, Ferries.dockOf(dest)), cls, zone, dest, r);
            if (m == null) continue;
            out.add(m);
            if (convoy) {
                m.life.convoy = true;
                int escorts = Math.min(maxShips - 1, 1 + r.nextInt(2));
                ShipClass ec = zone == SeaZone.NEW_WORLD ? ShipClass.BRIGANTINE : ShipClass.CARAVEL;
                for (int i = 1; i <= escorts; i++) {
                    int side = i % 2 == 1 ? -1 : 1;
                    Vec3 spot = m.toWorld(side * (cls.beam + ec.beam + 5), 0, -(cls.length / 2.0 + ec.length / 2.0 + 6));
                    Vec3 w = LivingSea.openWater(l, spot.x, spot.z, ec.length / 2 + 2);
                    if (w == null) continue;
                    ShipEntity e = SeaEncounters.spawnShip(l, w, m.getYRot(), ec, LivingSea.MARINE, Math.max(1, zone.dangerTier), "Convoy Escort");
                    if (e == null) continue;
                    e.naval.leader = m.shipId;
                    e.naval.slot = i;
                    out.add(e);
                }
            }
            Island from = m.life.origin.isEmpty() || plan == null ? null : plan.byId(m.life.origin);
            LivingSea.announce(l, p, Component.translatable(convoy ? "grandline.living_sea.convoy" : "grandline.living_sea.merchant",
                    m.shipName(), from == null ? Component.translatable("grandline.living_sea.far_port") : islandName(from), islandName(dest)).withStyle(ChatFormatting.AQUA));
            return out;
        }
        return out;
    }

    /** One merchantman bound for {@code dest} (chosen when null). */
    @Nullable
    static ShipEntity spawn(ServerLevel l, Vec3 pos, float yaw, ShipClass cls, SeaZone zone, @Nullable Island dest, RandomSource r) {
        WorldPlan plan = WorldPlan.forLevel(l);
        if (plan == null) return null;
        if (dest == null) dest = destination(plan, pos, zone, new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw))), null, r);
        if (dest == null) return null;
        ShipEntity m = SeaEncounters.spawnShip(l, pos, yaw, cls, LivingSea.MERCHANT, Math.max(1, zone.dangerTier), NAMES[r.nextInt(NAMES.length)]);
        if (m == null) return null;
        m.life.route = dest.id;
        Island from = null;
        double bd = Double.MAX_VALUE;
        for (Island i : plan.islands) {
            if (i == dest || !port(i) || i.zone != zone) continue;
            double d = i.distance((int) pos.x, (int) pos.z);
            if (d < bd) {
                bd = d;
                from = i;
            }
        }
        m.life.origin = from == null ? "" : from.id;
        m.setSail(2);
        return m;
    }

    /** Public entry for tests and commands: a merchantman at {@code pos} bound for {@code dest}. */
    @Nullable
    public static ShipEntity spawnBound(ServerLevel l, Vec3 pos, ShipClass cls, Island dest) {
        return spawn(l, pos, LivingSea.yawTo(pos, Ferries.dockOf(dest)), cls, WorldLayout.zoneAt((int) pos.x, (int) pos.z), dest, l.random);
    }

    static boolean port(Island i) {
        return i.settlementType != SettlementType.NONE && i.settlementType != SettlementType.RUINS && i.settlement().hasDock;
    }

    /**
     * A settled island with a dock in the same sea, 300–4000 blocks off, reachable without crossing the Calm Belt or a
     * Red Line; islands ahead (along {@code dir}) are preferred.
     */
    @Nullable
    static Island destination(WorldPlan plan, Vec3 from, SeaZone zone, Vec3 dir, @Nullable Island not, RandomSource r) {
        List<Island> c = new ArrayList<>();
        for (Island i : plan.islands) {
            if (i == not || i.zone != zone || !port(i)) continue;
            double d = i.distance((int) from.x, (int) from.z);
            if (d < 300 || d > 4000 || !clearWay(from, Ferries.dockOf(i), zone)) continue;
            c.add(i);
        }
        if (c.isEmpty()) return null;
        c.sort(Comparator.comparingDouble(i -> {
            Vec3 to = new Vec3(i.x - from.x, 0, i.z - from.z);
            double d = to.length();
            return d * (1.6 - to.normalize().dot(dir)); // ahead and near first
        }));
        return c.get(r.nextInt(Math.min(3, c.size())));
    }

    /** The straight course stays in one sea (never through the Calm Belt or over a Red Line). */
    static boolean clearWay(Vec3 a, Vec3 b, SeaZone zone) {
        double len = a.distanceTo(b);
        int steps = (int) Math.ceil(len / 120);
        for (int k = 0; k <= steps; k++) {
            double t = steps == 0 ? 0 : k / (double) steps;
            int x = (int) (a.x + (b.x - a.x) * t), z = (int) (a.z + (b.z - a.z) * t);
            if (WorldLayout.zoneAt(x, z) != zone) return false;
        }
        return true;
    }

    static Component islandName(Island i) {
        return i.name != null && !i.canon ? Component.literal(i.name) : Component.translatable(i.translationKey());
    }

    // ------------------------------------------------------------------ behaviour

    /** The ship (or player) she must deal with; returns it as the target only when she means to fight. */
    static int pickTarget(ShipEntity ship, ServerLevel sl) {
        SeaLife life = ship.life;
        life.threatId = -1;
        if (life.yielded) return -1;
        Entity threat = null;
        double bd = 130 * 130;
        for (ShipEntity s : sl.getEntitiesOfClass(ShipEntity.class, ship.getBoundingBox().inflate(130, 30, 130), s -> s != ship && !s.isSinking() && !s.naval.crewGone && s.ferryTarget == null)) {
            double d = s.distanceToSqr(ship);
            boolean bad = NavalCombat.attackedBy(ship, s)
                    || (s.flag(ShipEntity.F_NPC) && s.npcFaction.equals(LivingSea.PIRATE) && (s.naval.targetId == ship.getId() || d < 70 * 70))
                    || (!s.flag(ShipEntity.F_NPC) && s.owner != null && life.wronged.contains(s.owner));
            if (bad && d < bd) {
                bd = d;
                threat = s;
            }
        }
        for (ServerPlayer p : sl.players()) {
            if (!life.wronged(p) || ship.carries(p) || p.getVehicle() == ship || NavalCombat.shipOf(p) != null) continue;
            double d = p.distanceToSqr(ship);
            if (d < Math.min(bd, 60 * 60)) {
                bd = d;
                threat = p;
            }
        }
        if (threat == null) return -1;
        life.threatId = threat.getId();
        boolean escorted = escorted(ship, sl);
        boolean fight = ship.cls.cannons > 0 && !escorted
                && (!(threat instanceof ShipEntity ts) || LivingSea.strength(ship) >= LivingSea.strength(ts) * 1.1f);
        return fight ? threat.getId() : -1;
    }

    static boolean escorted(ShipEntity ship, ServerLevel sl) {
        return !sl.getEntitiesOfClass(ShipEntity.class, ship.getBoundingBox().inflate(150, 30, 150),
                e -> ship.shipId.equals(e.naval.leader) && e.flag(ShipEntity.F_NPC) && !e.naval.crewGone && !e.isSinking()).isEmpty();
    }

    /** Merchant behaviour (true: handled; false: she fights her target with the plain combat code). */
    static boolean tick(ShipEntity ship, ServerLevel sl, @Nullable Entity target, long now) {
        SeaLife life = ship.life;
        if (life.yielded) {
            ship.setSail(0);
            ship.setAnchored(true);
            return true;
        }
        if (target != null) return false;
        Entity threat = life.threatId >= 0 ? sl.getEntity(life.threatId) : null;
        if (threat != null && threat.isAlive()) {
            if (threat instanceof ShipEntity && threat.distanceToSqr(ship) < 34 * 34 && !escorted(ship, sl)) yieldTo(ship, sl, threat); // caught by a stronger ship
            else LivingSea.runFrom(ship, sl, threat.position());
            return true;
        }
        // heave to for anyone alongside or aboard who means her no harm (hail her captain to trade)
        if (now % 20 == Math.floorMod(ship.getId(), 20)) {
            for (ServerPlayer p : sl.players()) {
                if (p.isSpectator() || life.wronged(p) || p.distanceToSqr(ship) > 32 * 32) continue;
                life.heaveTo = now + 200;
                if (life.greeted.add(p.getUUID()))
                    p.displayClientMessage(Component.translatable("grandline.living_sea.heave_to", ship.shipName()).withStyle(ChatFormatting.AQUA), true);
            }
        }
        if (life.heaveTo > now || life.portUntil > now) {
            ship.setSail(0);
            ship.steer(0);
            ship.setAnchored(life.portUntil > now);
            return true;
        }
        WorldPlan plan = WorldPlan.forLevel(sl);
        Island dest = plan == null ? null : plan.byId(life.route);
        if (dest == null) {
            if (plan != null && now % 100 == Math.floorMod(ship.getId(), 100)) {
                Island next = destination(plan, ship.position(), WorldLayout.zoneAt(ship.getBlockX(), ship.getBlockZ()), ship.heading(), null, ship.getRandom());
                if (next != null) life.route = next.id;
            }
            ship.setAnchored(false);
            ship.setSail(1);
            return true;
        }
        Vec3 dock = Ferries.dockOf(dest);
        double d = dock.subtract(ship.position()).horizontalDistance();
        if (d < 34) {
            arrive(ship, sl, dest, now);
            return true;
        }
        ship.setAnchored(false);
        float diff = Ferries.helm(ship, sl, dock, dest.id);
        ship.setSail(Math.abs(diff) > 70 ? 1 : d < 90 ? 2 : 3);
        return true;
    }

    /** Made port: with someone to see it she lies at anchor a while, then sails for the next port; otherwise she is gone. */
    private static void arrive(ShipEntity ship, ServerLevel sl, Island dest, long now) {
        SeaLife life = ship.life;
        life.arrived = true;
        boolean watched = sl.getNearestPlayer(ship.getX(), ship.getY(), ship.getZ(), 64, false) != null;
        if (!watched && !ship.noDespawn) {
            for (ShipEntity e : sl.getEntitiesOfClass(ShipEntity.class, ship.getBoundingBox().inflate(200, 30, 200), e -> ship.shipId.equals(e.naval.leader)))
                NpcShipAi.despawn(e, sl);
            NpcShipAi.despawn(ship, sl);
            return;
        }
        WorldPlan plan = WorldPlan.forLevel(sl);
        Island next = plan == null ? null : destination(plan, ship.position(), dest.zone, ship.heading().scale(-1), dest, ship.getRandom());
        life.origin = dest.id;
        life.route = next == null ? "" : next.id;
        life.portUntil = now + 1200;
        ship.ferryWaypoint = null;
    }

    /**
     * Attacked by a player (from their ship, or swimming). A boarder on her own deck has her crew to deal with; a
     * stronger ship makes her strike her colours (unless escorted); otherwise she runs or fights back.
     */
    static void attacked(ShipEntity ship, ServerLevel sl, ServerPlayer sp, Entity via) {
        SeaLife life = ship.life;
        if (life.yielded) return;
        if (ship.carries(sp) || sp.getVehicle() == ship) {
            for (UUID u : ship.naval.crew)
                if (sl.getEntity(u) instanceof GLNpc m && m.isAlive() && !m.isPassenger() && ship.carries(m) && !m.flag(GLNpc.FLAG_SURRENDER)) m.setTarget(sp);
            return;
        }
        ShipEntity theirs = via instanceof ShipEntity s ? s : NavalCombat.shipOf(sp);
        if (theirs != null && theirs != ship && !escorted(ship, sl) && LivingSea.strength(theirs) > LivingSea.strength(ship) * 1.1f) {
            yieldTo(ship, sl, sp);
            return;
        }
        life.threatId = (theirs != null && theirs != ship ? theirs : sp).getId();
        ship.naval.targetId = -2; // choose again (fight or run) on the next tick
    }

    /** She strikes her colours: the crew surrender, the cargo goes over the side, the ship lies hove-to (a prize for the taking). */
    static void yieldTo(ShipEntity ship, ServerLevel sl, @Nullable Entity by) {
        SeaLife life = ship.life;
        if (life.yielded) return;
        life.yielded = true;
        life.threatId = -1;
        ship.naval.targetId = -1;
        for (UUID u : ship.naval.crew) {
            if (!(sl.getEntity(u) instanceof GLNpc m) || !m.isAlive()) continue;
            m.stopRiding();
            m.addTag(LivingSea.YIELDED);
            m.setFlag(GLNpc.FLAG_SURRENDER, true);
            m.setTarget(null);
            GLNetwork.anim(m, "surrender");
        }
        ship.setSail(0);
        ship.setAnchored(true);
        if (!life.salvaged) {
            life.salvaged = true;
            LivingSea.salvage(sl, ship, "chests/warehouse");
        }
        LivingSea.announce(sl, ship.position(), Component.translatable("grandline.living_sea.merchant_yields", ship.shipName()).withStyle(ChatFormatting.GOLD));
    }

    /**
     * A merchant that yielded, left alone: pirates close by plunder and scuttle her (false: let her founder);
     * otherwise, after a while, her crew take heart and she sails on (true: spared).
     */
    static boolean spare(ShipEntity ship, ServerLevel sl, long now) {
        if (!sl.getEntitiesOfClass(ShipEntity.class, ship.getBoundingBox().inflate(150, 30, 150),
                s -> s.flag(ShipEntity.F_NPC) && s.npcFaction.equals(LivingSea.PIRATE) && !s.naval.crewGone && !s.isSinking()).isEmpty()) return false;
        if (now - ship.life.beatenAt < 900) return true;
        ship.life.yielded = false;
        ship.life.beatenAt = 0;
        for (UUID u : ship.naval.crew) {
            if (!(sl.getEntity(u) instanceof GLNpc m) || !m.isAlive()) continue;
            m.removeTag(LivingSea.YIELDED);
            m.setFlag(GLNpc.FLAG_SURRENDER, false);
        }
        ship.naval.crewGone = false;
        return true;
    }

    private MerchantShips() {}
}
