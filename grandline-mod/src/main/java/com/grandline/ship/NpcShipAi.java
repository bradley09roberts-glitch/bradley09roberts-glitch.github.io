package com.grandline.ship;

import com.grandline.npc.GLNpc;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Captaincy of NPC ships (Marine patrols, pirate raiders, bounty hunters, merchants): pick a target (re-evaluated
 * once a second, not every tick) among hostile players, their ships and the other NPC ships of the living sea, keep
 * squadron formation, close in and circle for broadsides fired by the sailors at the guns, ram smaller prey, throw
 * grapples and send boarding parties. Outmatched pirates run; merchants, raids and beaten hulks are handled by
 * {@link LivingSea}. A ship whose crew is beaten drifts and can be taken as a prize.
 */
final class NpcShipAi {
    /** How far an NPC ship sights another NPC ship it would fight (blocks). */
    static final double SIGHT = 180;

    static void tick(ShipEntity ship, ServerLevel sl) {
        NavalCombat.State n = ship.naval;
        if (ship.npcAiOff) return;
        long now = sl.getGameTime();
        int phase = Math.floorMod(ship.getId(), 20);
        if (now % 20 == phase || n.targetId == -2) {
            Player near = sl.getNearestPlayer(ship.getX(), ship.getY(), ship.getZ(), 320, false);
            if (near == null && !ship.noDespawn) {
                despawn(ship, sl);
                return;
            }
            n.targetId = pickTarget(ship, sl, near);
            if (now % 200 == phase * 10 % 200) manGuns(ship, sl, 2);
        }
        if (now % 10 == phase % 10) LivingSea.deckDiscipline(ship, sl);
        if (n.crewGone) {
            ship.setSail(0);
            ship.steer(0);
            LivingSea.hulk(ship, sl, now);
            return;
        }
        Entity target = n.targetId >= 0 ? sl.getEntity(n.targetId) : null;
        if (target != null && !target.isAlive()) target = null;
        if (LivingSea.override(ship, sl, target, now)) return; // merchants, raids, running from a stronger ship
        ship.setAnchored(false);
        if (target == null) {
            n.ramTicks = 0;
            ShipEntity lead = leader(ship, sl);
            if (lead != null) formation(ship, lead);
            else {
                ship.setSail(1);
                ship.steer((float) Math.sin(now / 400.0) * 0.3f);
            }
            return;
        }
        Vec3 to = target.position().subtract(ship.position());
        double dist = to.horizontalDistance();
        float bearing = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        ShipEntity ts = target instanceof ShipEntity s ? s : null;
        // pirates ram prey no bigger than themselves
        if (ts != null && n.ramTicks <= 0 && ship.npcFaction.equals("pirate") && dist < 45 && ship.cls.ordinal() >= ts.cls.ordinal()
                && ship.linkedId() < 0 && ship.getRandom().nextInt(400) == 0) n.ramTicks = 200;
        float want;
        if (n.ramTicks > 0) {
            n.ramTicks--;
            Vec3 lead = target.position().add(NavalCombat.velocity(target).scale(dist / Math.max(0.2, ship.cls.speed)));
            Vec3 tl = lead.subtract(ship.position());
            want = (float) Math.toDegrees(Math.atan2(-tl.x, tl.z));
            ship.setSail(3);
        } else {
            want = dist > 40 ? bearing : bearing + (Mth.wrapDegrees(bearing - ship.getYRot()) > 0 ? -90 : 90); // close, then circle to bring a broadside to bear
            // a chase dead astern brings no gun to bear: every so often yaw off to fire a broadside, then resume
            if (dist > 40 && dist < 78 && ts != null && Math.abs(Mth.wrapDegrees(bearing - ship.getYRot())) < 35) {
                if (now >= ship.life.yawAt + 140) ship.life.yawAt = now;
                if (now - ship.life.yawAt < 50) want = bearing + ((ship.getId() & 1) == 0 ? 75 : -75);
            }
            ship.setSail(dist > 25 ? 3 : 2);
        }
        ship.steer(Mth.wrapDegrees(want - ship.getYRot()) / 30f);
        fire(ship, sl, target, ts, dist, now);
        if (ts != null && now % 10 == Math.floorMod(ship.getId(), 10)) boarding(ship, sl, ts, now);
    }

    static void despawn(ShipEntity ship, ServerLevel sl) {
        for (UUID u : ship.naval.crew) if (sl.getEntity(u) instanceof GLNpc m && !m.isPersistenceRequired()) m.discard();
        for (Entity e : sl.getEntitiesOfClass(GLNpc.class, ship.getBoundingBox().inflate(ship.cls.length), e -> ship.carries(e) || e.getVehicle() == ship)) e.discard();
        NavalCombat.unlink(ship);
        ship.discard();
    }

    /**
     * The ship to fight: hostile player ships within 220 blocks, hostile players at sea within 160, and the NPC
     * ships it would engage within {@link #SIGHT}; the nearest wins, but a current target is kept unless another
     * is much closer. Escorts share their flagship's target; merchants and raiders choose through {@link LivingSea}.
     */
    private static int pickTarget(ShipEntity ship, ServerLevel sl, @Nullable Player near) {
        NavalCombat.State n = ship.naval;
        ship.life.fleeFrom = -1;
        if (ship.npcFaction.equals(LivingSea.MERCHANT)) return LivingSea.merchantTarget(ship, sl);
        ShipEntity lead = leader(ship, sl);
        if (lead != null && lead.naval.targetId >= 0 && lead.distanceToSqr(ship) < 200 * 200) return lead.naval.targetId;
        boolean raid = !ship.life.raidIsland.isEmpty();
        Entity best = null;
        double bd = Double.MAX_VALUE;
        Entity cur = n.targetId >= 0 ? sl.getEntity(n.targetId) : null;
        double curD = -1;
        Entity threat = null;
        double threatD = Double.MAX_VALUE;
        for (ShipEntity s : sl.getEntitiesOfClass(ShipEntity.class, ship.getBoundingBox().inflate(220, 30, 220), s -> s != ship && !s.isSinking() && s.ferryTarget == null)) {
            double d = s.distanceToSqr(ship);
            boolean want;
            if (!s.flag(ShipEntity.F_NPC)) {
                // a raid crew ashore only answers ships that come at it
                want = NavalCombat.attackedBy(ship, s) || (!raid ? hostileToShip(ship, s) : hostileToShip(ship, s) && d < 80 * 80)
                        || (lead != null && LivingSea.escortsDefend(lead, s));
            } else {
                if (s.naval.crewGone || d > SIGHT * SIGHT) continue;
                int stance = LivingSea.stance(ship, s, lead);
                if (stance < 0) { // too strong to take on: run (unless cornered)
                    if (d < threatD) {
                        threatD = d;
                        threat = s;
                    }
                    want = d < 28 * 28;
                } else want = stance > 0 && (!raid || NavalCombat.attackedBy(ship, s));
            }
            if (!want) continue;
            if (s == cur) curD = d;
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        if (!raid && near != null && near.distanceToSqr(ship) < 160 * 160 && hostileToPlayer(ship, near) && !LivingSea.ashore(near)) {
            double d = near.distanceToSqr(ship);
            if (near == cur) curD = d;
            if (d < bd) {
                bd = d;
                best = near;
            }
        }
        if (cur != null && curD >= 0 && curD < bd * 1.6) best = cur; // keep the fight it is in
        if (best == null && threat != null) ship.life.fleeFrom = threat.getId();
        return best == null ? -1 : best.getId();
    }

    static boolean hostileToShip(ShipEntity ship, ShipEntity s) {
        if (s.owner == null) return false;
        Player p = ship.level().getPlayerByUUID(s.owner);
        return p == null ? !ship.npcFaction.equals(LivingSea.MERCHANT) && !ship.npcFaction.equals(LivingSea.HUNTER) : hostileToPlayer(ship, p);
    }

    static boolean hostileToPlayer(ShipEntity ship, Player p) {
        var d = Characters.get(p);
        if (ship.life.wronged(p)) return true;
        return switch (ship.npcFaction) {
            case LivingSea.MARINE -> d.bounty > 0 || d.faction == Faction.PIRATE || d.faction == Faction.REVOLUTIONARY;
            case LivingSea.HUNTER -> d.bounty >= LivingSea.HUNTED && d.faction != Faction.MARINE; // licensed hunters go after wanted heads
            case LivingSea.MERCHANT -> false;
            // raiders leave notorious pirates alone half the time (stable per ship and player) and spare raid allies
            default -> !ship.life.raidAlly(p) && (d.faction != Faction.PIRATE || d.bounty < 1_000_000 || ((ship.getId() ^ p.getId()) & 1) == 0);
        };
    }

    @Nullable
    static ShipEntity leader(ShipEntity ship, ServerLevel sl) {
        NavalCombat.State n = ship.naval;
        if (n.leader == null) return null;
        if (n.leaderEntity >= 0 && sl.getEntity(n.leaderEntity) instanceof ShipEntity l && l.isAlive() && n.leader.equals(l.shipId)) return l.flag(ShipEntity.F_NPC) ? l : null;
        n.leaderEntity = -1;
        for (ShipEntity s : sl.getEntitiesOfClass(ShipEntity.class, ship.getBoundingBox().inflate(250, 30, 250), s -> n.leader.equals(s.shipId))) {
            n.leaderEntity = s.getId();
            return s.flag(ShipEntity.F_NPC) ? s : null;
        }
        return null;
    }

    /** Escorts keep station on their flagship's quarters. */
    static void formation(ShipEntity ship, ShipEntity lead) {
        int side = ship.naval.slot % 2 == 1 ? -1 : 1;
        int rank = (ship.naval.slot + 1) / 2;
        Vec3 spot = lead.toWorld(side * (lead.cls.beam + ship.cls.beam + 5), 0, -rank * (lead.cls.length / 2.0 + ship.cls.length / 2.0 + 6));
        Vec3 to = spot.subtract(ship.position());
        double d = to.horizontalDistance();
        float want = d < 8 ? lead.getYRot() : (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        ship.steer(Mth.wrapDegrees(want - ship.getYRot()) / 30f);
        ship.setSail(d > 30 ? 3 : d > 10 ? Math.max(2, lead.sail()) : lead.sail());
    }

    /** Sailors at the guns fire when their side bears; a ship without gunners still fires a ragged broadside. */
    static void fire(ShipEntity ship, ServerLevel sl, Entity target, @Nullable ShipEntity ts, double dist, long now) {
        if (ship.cls.cannons == 0 || dist > 80) return;
        NavalCombat.State n = ship.naval;
        String ammo = NavalCombat.CANNONBALL;
        if (ts != null) {
            if (dist < 28 && ship.npcFaction.equals("pirate") && ship.getRandom().nextInt(3) == 0) ammo = NavalCombat.GRAPE_SHOT; // clear the deck before boarding
            else if (ts.speed() > ship.speed() + 0.05f && ts.rigging() > 0.4f && ship.getRandom().nextInt(2) == 0) ammo = NavalCombat.CHAIN_SHOT; // stop a runner
        }
        float base = 10f + ship.cls.ordinal() * 3f;
        boolean manned = false;
        for (int seat = 1; seat <= ship.cls.cannons; seat++) {
            if (!(ship.getFirstPassengerAtSeat(seat) instanceof GLNpc g) || !g.isAlive() || g.flag(GLNpc.FLAG_SURRENDER) || g.hasEffect(ModEffects.KNOCKED_OUT)) continue;
            manned = true;
            if (n.seatCd.getOrDefault(seat, 0L) > now) continue;
            if (NavalCombat.gunnerFire(ship, seat, g, target, ammo, base + g.tier(), g.tier()))
                n.seatCd.put(seat, now + 75 - Math.min(30, g.tier() * 3) + ship.getRandom().nextInt(30));
            else n.seatCd.put(seat, now + 10);
        }
        if (manned || now < n.fireTimer) return;
        // no one at the guns: deckhands run between cannons (slow, ragged volleys)
        n.fireTimer = now + 70;
        Vec3 aim = target.position().add(0, 1.5, 0).add(NavalCombat.velocity(target).scale(dist / 2.2)).subtract(ship.position());
        Vec3 starboard = ship.toWorld(1, 0, 0).subtract(ship.position()).normalize();
        boolean right = aim.normalize().dot(starboard) > 0;
        for (int seat = 1; seat <= ship.cls.cannons; seat++) {
            if ((ship.seatLocal(seat).x >= 0) != right || ship.getRandom().nextInt(4) != 0) continue;
            ship.fireFrom(seat, aim.add(ship.getRandom().nextGaussian() * 2, 0, ship.getRandom().nextGaussian() * 2), null, base);
        }
    }

    /** Seat idle crew at free guns, keeping {@code keep} hands free for boarding. */
    static void manGuns(ShipEntity ship, ServerLevel sl, int keep) {
        int free = 0;
        java.util.List<GLNpc> idle = new java.util.ArrayList<>();
        for (UUID u : ship.naval.crew) {
            if (sl.getEntity(u) instanceof GLNpc m && m.isAlive() && !m.isPassenger() && ship.carries(m) && !m.flag(GLNpc.FLAG_SURRENDER)
                    && !ship.naval.boarders.containsKey(m.getId()) && m.getTarget() == null) idle.add(m);
        }
        for (GLNpc m : idle) {
            if (free++ < keep) continue;
            int seat = NavalCombat.freeGun(ship);
            if (seat <= 0) return;
            ship.seatEntity(m, seat);
        }
    }

    /** Throw grapples at a target ship within reach; once alongside, send boarders across in waves. */
    private static void boarding(ShipEntity ship, ServerLevel sl, ShipEntity ts, long now) {
        NavalCombat.State n = ship.naval;
        if (ts.isSinking() || ship.cls == ShipClass.ROWBOAT) return;
        if (ship.linkedId() < 0) {
            Vec3 rel = ship.toLocal(ts.position());
            double gap = Math.abs(rel.x) - NavalCombat.beam(ship) - NavalCombat.beam(ts);
            boolean keen = ship.npcFaction.equals("pirate") || ship.npcFaction.equals(LivingSea.HUNTER)
                    || (ts.owner != null && sl.getPlayerByUUID(ts.owner) != null && Characters.get(sl.getPlayerByUUID(ts.owner)).bounty >= 10_000_000);
            if (gap < 14 && Math.abs(rel.z) < ship.cls.length && ts.linkedId() < 0 && ship.getRandom().nextInt(keen ? 4 : 12) == 0) {
                NavalCombat.link(ship, ts, false);
                NavalCombat.noteAggression(ts, ship);
                var msg = net.minecraft.network.chat.Component.translatable("grandline.naval.grappled_by", ship.shipName()).withStyle(net.minecraft.ChatFormatting.RED);
                for (var p : sl.players()) if (ts.carries(p) || p.getVehicle() == ts) p.displayClientMessage(msg, true);
                sl.playSound(null, ts.blockPosition(), net.minecraft.sounds.SoundEvents.CHAIN_PLACE, net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 0.6f);
            }
            return;
        }
        if (ship.linkedId() != ts.getId() || !NavalCombat.alongside(ship, ts) || now < n.boardCooldown) return;
        n.boardCooldown = now + 160;
        NavalCombat.launchBoarders(ship, ts, 3);
    }

    private NpcShipAi() {}
}
