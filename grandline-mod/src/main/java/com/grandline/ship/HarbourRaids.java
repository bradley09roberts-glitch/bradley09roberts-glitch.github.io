package com.grandline.ship;

import com.grandline.core.Chunks;
import com.grandline.navigation.Ferries;
import com.grandline.npc.Bounties;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.registry.ModEffects;
import com.grandline.registry.ModEntities;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Settlement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Harbour raids: a pirate ship sails in along a settled island's dock (the ferries' course-keeping brings her round
 * the coast), anchors off the dock and lands raiders on the quay. Town guards, Marines in town (and, if there are
 * too few, a Marine detachment sent from the town) go for them. Raiders who are still standing after two minutes
 * row back with their plunder; if they are all beaten first the raid is repelled. Players who fought the raiders
 * earn the island's thanks; players who join the raid (talk to a raider) take a bounty and a share of the plunder.
 * Nothing here breaks blocks: the raid ship does not fire on the town (only on ships that come at her), and every
 * explosion already respects town protection.
 */
public final class HarbourRaids {
    public static final int APPROACH = 0, ASHORE = 1, LEAVING = 2;
    /** Raiders stay ashore this long. */
    static final long RAID_TICKS = 2400;
    /** A ship that cannot work into the harbour lands her raiders from where she is (if near) or gives up. */
    static final long APPROACH_MAX = 1800;

    /** A pirate ship sailing in to raid {@code isl}, out along its dock and within {@code maxDist} of {@code observer}. */
    @Nullable
    public static ShipEntity start(ServerLevel l, Island isl, Vec3 observer, int maxDist) {
        Settlement st = isl.settlement();
        if (!st.hasDock) return null;
        double ux = st.dockDirX / 1000.0, uz = st.dockDirZ / 1000.0, ul = Math.max(1e-6, Math.sqrt(ux * ux + uz * uz));
        ux /= ul;
        uz /= ul;
        var r = l.random;
        ShipClass cls = isl.zone.isBlue() ? (r.nextBoolean() ? ShipClass.SLOOP : ShipClass.CARAVEL) : r.nextInt(3) == 0 ? ShipClass.BRIGANTINE : ShipClass.CARAVEL;
        for (int out = 110; out >= 40; out -= 10)
            for (int a : new int[]{0, 20, -20, 40, -40}) {
                double ang = Math.toRadians(a), c = Math.cos(ang), s = Math.sin(ang);
                double dx = ux * c - uz * s, dz = ux * s + uz * c;
                double x = st.dockTipX() + dx * out, z = st.dockTipZ() + dz * out;
                if (Math.hypot(x - observer.x, z - observer.z) > maxDist + 10) continue;
                Vec3 w = LivingSea.openWater(l, x, z, cls.length / 2 + 3);
                if (w == null) continue;
                ShipEntity ship = SeaEncounters.spawnShip(l, w, LivingSea.yawTo(w, Ferries.dockOf(isl)), cls, LivingSea.PIRATE, Math.max(1, isl.tier), LivingSea.name(LivingSea.PIRATE, r));
                if (ship == null) return null;
                ship.life.raidIsland = isl.id;
                ship.life.raidPhase = APPROACH;
                ship.life.phaseAt = l.getGameTime();
                LivingSea.announce(l, new Vec3(st.dockX, 64, st.dockZ), Component.translatable("grandline.living_sea.raid_sighted", ship.shipName(), MerchantShips.islandName(isl)).withStyle(ChatFormatting.RED));
                return ship;
            }
        return null;
    }

    /** The raid ship's behaviour for its phase (true: handled). */
    static boolean tickShip(ShipEntity ship, ServerLevel sl, @Nullable Entity target, long now) {
        SeaLife life = ship.life;
        WorldPlan plan = WorldPlan.forLevel(sl);
        Island isl = plan == null ? null : plan.byId(life.raidIsland);
        if (isl == null) {
            life.raidIsland = "";
            return false;
        }
        Vec3 dock = Ferries.dockOf(isl);
        switch (life.raidPhase) {
            case APPROACH -> {
                if (target != null) return false; // answering a ship that came at her
                double d = dock.subtract(ship.position()).horizontalDistance();
                boolean late = now - life.phaseAt > APPROACH_MAX;
                life.stall = ship.speed() < 0.03f ? life.stall + 1 : 0;
                // in close, or held up by the quays and rocks of the harbour: the boats row the raiders in from here
                if (d < 30 || (life.stall > 100 && d < 70) || (late && d < 90)) {
                    if (land(ship, sl, isl, now)) return true;
                } else if (late) {
                    leave(ship, now);
                    return true;
                }
                ship.setAnchored(false);
                float diff = Ferries.helm(ship, sl, dock, isl.id);
                ship.setSail(Math.abs(diff) > 70 || d < 70 ? 1 : 2);
                return true;
            }
            case ASHORE -> {
                ship.setSail(0);
                ship.steer(0);
                ship.setAnchored(true);
                if (target != null) { // guns answer a ship that comes at her; never the town
                    ShipEntity ts = target instanceof ShipEntity s ? s : null;
                    if (ts != null) NpcShipAi.fire(ship, sl, target, ts, target.distanceTo(ship), now);
                }
                if (now % 20 == Math.floorMod(ship.getId(), 20)) raidTick(ship, sl, isl, now);
                return true;
            }
            default -> {
                if (target != null) return false;
                Vec3 out = dock.subtract(isl.x, dock.y, isl.z).multiply(1, 0, 1).normalize().scale(260).add(dock);
                ship.setAnchored(false);
                ship.setSail(3);
                Ferries.helm(ship, sl, out, "");
                if (!ship.noDespawn && ship.position().distanceTo(dock) > 200 && sl.getNearestPlayer(ship.getX(), ship.getY(), ship.getZ(), 96, false) == null)
                    NpcShipAi.despawn(ship, sl);
                return true;
            }
        }
    }

    /** Spots on land at the root of the dock (fully loaded chunks only; empty when not ready yet). */
    public static List<BlockPos> landingSpots(ServerLevel sl, Island isl) {
        Settlement st = isl.settlement();
        double ux = st.dockDirX / 1000.0, uz = st.dockDirZ / 1000.0, ul = Math.max(1e-6, Math.sqrt(ux * ux + uz * uz));
        ux /= ul;
        uz /= ul;
        List<BlockPos> out = new ArrayList<>();
        for (int back = 1; back <= 16 && out.size() < 10; back += 3)
            for (int side = -4; side <= 4; side += 2) {
                int x = (int) Math.round(st.dockX - ux * back - uz * side), z = (int) Math.round(st.dockZ - uz * back + ux * side);
                if (!Chunks.ready(sl, x, z)) continue;
                int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y <= WorldPlan.SEA_LEVEL) continue;
                BlockPos b = new BlockPos(x, y, z);
                if (!sl.getBlockState(b.below()).getFluidState().isEmpty() || !sl.getBlockState(b).isAir() || !sl.getBlockState(b.above()).isAir()) continue;
                out.add(b);
            }
        return out;
    }

    /** Anchor and put the landing party ashore (all hands but one, gunners too if few are free). */
    static boolean land(ShipEntity ship, ServerLevel sl, Island isl, long now) {
        List<BlockPos> spots = landingSpots(sl, isl);
        if (spots.isEmpty()) return false;
        List<GLNpc> free = new ArrayList<>(), gunners = new ArrayList<>();
        for (UUID u : ship.naval.crew) {
            if (!(sl.getEntity(u) instanceof GLNpc m) || !able(m)) continue;
            if (m.isPassenger()) gunners.add(m);
            else if (ship.carries(m)) free.add(m);
        }
        if (free.size() < 3) free.addAll(gunners.subList(0, Math.min(gunners.size(), 3 - free.size())));
        if (free.size() > 1) free.remove(free.size() - 1); // one hand minds the ship
        if (free.isEmpty()) return false;
        int i = 0;
        for (GLNpc m : free) {
            BlockPos b = spots.get(i++ % spots.size());
            m.stopRiding();
            m.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
            m.setDeltaMovement(Vec3.ZERO);
            m.fallDistance = 0;
            m.getNavigation().stop();
            m.setTarget(null);
            m.addTag(LivingSea.RAIDER);
            m.setAllegiance(m.faction(), b); // they roam the quay and the town around it
            sl.sendParticles(ParticleTypes.SPLASH, b.getX() + 0.5, b.getY() + 0.2, b.getZ() + 0.5, 12, 0.4, 0.2, 0.4, 0.1);
        }
        ship.life.raidPhase = ASHORE;
        ship.life.landedAt = now;
        ship.life.phaseAt = now;
        ship.setSail(0);
        ship.setAnchored(true);
        sl.playSound(null, spots.get(0), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3f, 1f);
        LivingSea.announce(sl, Vec3.atCenterOf(spots.get(0)), Component.translatable("grandline.living_sea.raid_landed", ship.shipName(), MerchantShips.islandName(isl)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        raidTick(ship, sl, isl, now);
        return true;
    }

    static boolean able(GLNpc m) {
        return m.isAlive() && !m.isRemoved() && !m.flag(GLNpc.FLAG_SURRENDER) && !m.flag(GLNpc.FLAG_ARRESTED) && !m.hasEffect(ModEffects.KNOCKED_OUT);
    }

    /** The raiders this ship put ashore. */
    public static List<GLNpc> raiders(ShipEntity ship, ServerLevel sl) {
        List<GLNpc> out = new ArrayList<>();
        for (UUID u : ship.naval.crew) if (sl.getEntity(u) instanceof GLNpc m && m.getTags().contains(LivingSea.RAIDER)) out.add(m);
        return out;
    }

    /** Every second ashore: the town's defenders go for the raiders, raiders go for the townsfolk; the raid ends when beaten or done. */
    static void raidTick(ShipEntity ship, ServerLevel sl, Island isl, long now) {
        SeaLife life = ship.life;
        List<GLNpc> raiders = raiders(ship, sl);
        List<GLNpc> standing = new ArrayList<>();
        for (GLNpc m : raiders) if (able(m)) standing.add(m);
        if (standing.isEmpty()) {
            repelled(ship, sl, isl, now);
            return;
        }
        if (now - life.landedAt > RAID_TICKS) {
            plundered(ship, sl, isl, standing, now);
            return;
        }
        RaidPlunder.tick(ship, sl, isl, standing, now - life.landedAt); // raiders carry off the market's goods (ship/RaidPlunder)
        Vec3 at = standing.get(0).position();
        AABB town = new AABB(at, at).inflate(80, 24, 80);
        int defenders = 0;
        for (GLNpc g : sl.getEntitiesOfClass(GLNpc.class, town, g -> !g.getTags().contains(LivingSea.CREW) && able(g)
                && (Faction.isLaw(g.faction()) || g.role().equals("guard") || g.getTags().contains(LivingSea.RESPONDER)))) {
            defenders++;
            if (g.getTarget() == null || !g.getTarget().isAlive()) g.setTarget(nearest(g, standing));
        }
        if (!life.responded && defenders < 2 && now - life.landedAt > 60) {
            life.responded = true;
            respond(ship, sl, isl, standing);
        }
        for (GLNpc m : standing) {
            if (m.getTarget() != null && m.getTarget().isAlive()) continue;
            LivingEntity prey = null;
            double bd = 40 * 40;
            for (HumanoidNpc o : sl.getEntitiesOfClass(HumanoidNpc.class, m.getBoundingBox().inflate(40, 12, 40), o -> o != m && !o.getTags().contains(LivingSea.RAIDER)
                    && o.notableId().isEmpty() && able(o) && m.isHostileTo(o))) {
                double d = o.distanceToSqr(m);
                if (d < bd) {
                    bd = d;
                    prey = o;
                }
            }
            if (prey != null) m.setTarget(prey);
        }
    }

    @Nullable
    private static GLNpc nearest(Entity from, List<GLNpc> of) {
        GLNpc best = null;
        double bd = Double.MAX_VALUE;
        for (GLNpc m : of) {
            double d = m.distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** Too few defenders: a Marine detachment turns out from the town. */
    private static void respond(ShipEntity ship, ServerLevel sl, Island isl, List<GLNpc> raiders) {
        List<BlockPos> spots = landingSpots(sl, isl);
        if (spots.isEmpty()) return;
        int n = 2 + Math.min(2, isl.tier / 3);
        for (int i = 0; i < n; i++) {
            BlockPos b = spots.get(spots.size() - 1 - i % spots.size()); // the far end of the quay: they come from the town
            HumanoidNpc m = ModEntities.HUMANOID_NPC.get().create(sl);
            if (m == null) continue;
            m.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, sl.random.nextFloat() * 360, 0);
            m.configure(i == 0 ? "marine_officer" : "marine", Math.max(1, isl.tier), isl.id, b, "", sl.random);
            m.addTag(LivingSea.TRANSIENT);
            m.addTag(LivingSea.RESPONDER);
            sl.addFreshEntity(m);
            m.setTarget(nearest(m, raiders));
        }
        LivingSea.announce(sl, Vec3.atCenterOf(spots.get(0)), Component.translatable("grandline.living_sea.raid_marines", MerchantShips.islandName(isl)).withStyle(ChatFormatting.AQUA));
    }

    /** All raiders beaten: the island thanks those who fought them; the ship slips her cable and runs. */
    private static void repelled(ShipEntity ship, ServerLevel sl, Island isl, long now) {
        SeaLife life = ship.life;
        leave(ship, now);
        RaidPlunder.recover(ship, sl); // the beaten raiders drop the goods they carried
        LivingSea.announce(sl, Ferries.dockOf(isl), Component.translatable("grandline.living_sea.raid_repelled", MerchantShips.islandName(isl)).withStyle(ChatFormatting.GOLD));
        for (var en : life.fought.entrySet()) {
            if (now - en.getValue() > RAID_TICKS + 1200 || life.raidAllies.contains(en.getKey())) continue;
            ServerPlayer sp = LivingSea.resolve(sl, en.getKey(), life);
            if (sp == null) continue;
            CharacterData d = Characters.get(sp);
            d.addReputation("island/" + isl.id, 40);
            d.addReputation(Faction.CIVILIAN.id(), 10);
            if (Faction.isLaw(d.faction)) d.addReputation(Faction.MARINE.id(), 10);
            long reward = 400L * Math.max(1, isl.tier);
            d.beli += reward;
            sp.sendSystemMessage(Component.translatable("grandline.living_sea.raid_thanks", MerchantShips.islandName(isl), reward).withStyle(ChatFormatting.GOLD));
            CharacterManager.markDirty(sp);
        }
    }

    /** Time is up with raiders still standing: they row back with the plunder and share it with those who joined them. */
    private static void plundered(ShipEntity ship, ServerLevel sl, Island isl, List<GLNpc> standing, long now) {
        SeaLife life = ship.life;
        for (GLNpc m : standing) {
            Vec3 deck = LivingSea.deckSpot(ship, m.getRandom());
            m.removeTag(LivingSea.RAIDER);
            m.setTarget(null);
            m.setAllegiance(m.faction(), BlockPos.ZERO);
            m.teleportTo(deck.x, deck.y, deck.z);
            m.getNavigation().stop();
        }
        ship.naval.crewGone = false;
        leave(ship, now);
        LivingSea.announce(sl, Ferries.dockOf(isl), Component.translatable("grandline.living_sea.raid_plundered", MerchantShips.islandName(isl)).withStyle(ChatFormatting.RED));
        for (UUID u : life.raidAllies) {
            ServerPlayer sp = LivingSea.resolve(sl, u, life);
            if (sp == null) continue;
            long share = 1500L * Math.max(1, isl.tier);
            Characters.get(sp).beli += share;
            sp.sendSystemMessage(Component.translatable("grandline.living_sea.raid_share", share).withStyle(ChatFormatting.GOLD));
            CharacterManager.markDirty(sp);
        }
    }

    private static void leave(ShipEntity ship, long now) {
        ship.life.raidPhase = LEAVING;
        ship.life.phaseAt = now;
        ship.ferryWaypoint = null;
        ship.setAnchored(false);
    }

    /** A player struck one of this ship's raiders (defending the town). */
    static void foughtBy(ShipEntity ship, ServerPlayer sp) {
        ship.life.fought.put(sp.getUUID(), sp.level().getGameTime());
        ship.life.who.put(sp.getUUID(), sp);
    }

    /** A raider (or a hand of a ship on her way in) may take on a player who wants to join the raid. */
    public static boolean mayJoin(ServerPlayer sp, GLNpc npc) {
        if (!npc.getTags().contains(LivingSea.CREW) || npc.flag(GLNpc.FLAG_SURRENDER) || npc.getTarget() == sp) return false;
        ShipEntity ship = LivingSea.shipOfCrew(npc);
        return ship != null && !ship.life.raidIsland.isEmpty() && ship.life.raidPhase != LEAVING && !ship.life.raidAlly(sp);
    }

    /** Join the raid: the raiders take you for one of theirs; the island and the Marines will remember it. */
    public static boolean join(ServerPlayer sp, GLNpc npc) {
        if (!mayJoin(sp, npc)) return false;
        ShipEntity ship = LivingSea.shipOfCrew(npc);
        WorldPlan plan = WorldPlan.forLevel(sp.level());
        Island isl = plan == null ? null : plan.byId(ship.life.raidIsland);
        if (isl == null) return false;
        ship.life.raidAllies.add(sp.getUUID());
        ship.life.who.put(sp.getUUID(), sp);
        CharacterData d = Characters.get(sp);
        Bounties.raise(sp, d, 1_500_000L * Math.max(1, isl.tier), Component.translatable("grandline.living_sea.crime.raid"));
        d.addReputation("island/" + isl.id, -150);
        d.addReputation(Faction.CIVILIAN.id(), -30);
        for (UUID u : ship.naval.crew)
            if (sp.serverLevel().getEntity(u) instanceof GLNpc m && m.getTarget() == sp) m.setTarget(null);
        sp.sendSystemMessage(Component.translatable("grandline.living_sea.raid_joined", ship.shipName(), MerchantShips.islandName(isl)).withStyle(ChatFormatting.RED));
        CharacterManager.markDirty(sp);
        return true;
    }

    private HarbourRaids() {}
}
