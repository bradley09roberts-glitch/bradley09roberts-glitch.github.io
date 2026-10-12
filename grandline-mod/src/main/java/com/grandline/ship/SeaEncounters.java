package com.grandline.ship;

import com.grandline.npc.HumanoidNpc;
import com.grandline.player.Characters;
import com.grandline.registry.ModEntities;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sea encounters for sailing players: Marine patrols (more likely with a bounty) and pirate raiders, scaled by
 * the sea's danger. In the Grand Line Marines may sail as a squadron (a flagship and one or two escorts), and the
 * waters around Marine Headquarters and New Marineford send heavy squadrons after wanted pirates. Ships carry NPC
 * crews (sailors man the guns) and despawn when nobody is near. Rolled every 30 s per player, with a server-wide cap.
 */
public final class SeaEncounters {
    /** NPC ships allowed in the overworld at once: a floor, plus a share per player at sea. */
    static final int CAP_BASE = 4, CAP_PER_PLAYER = 1;
    /** Bounty that makes Headquarters send its heavy squadrons. */
    public static final long WANTED = 30_000_000L;
    static final double HQ_RADIUS = 1800;

    public static void tick(MinecraftServer server) {
        ServerLevel l = server.overworld();
        if (l.getGameTime() % 600 != 0 || WorldPlan.of(server) == null) return;
        int npcShips = -1;
        for (ServerPlayer sp : l.players()) {
            if (!(sp.getVehicle() instanceof ShipEntity) && !isAtSea(sp)) continue;
            if (sp.isCreative() || sp.isSpectator()) continue;
            if (!l.getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(250), s -> s.flag(ShipEntity.F_NPC)).isEmpty()) continue;
            SeaZone z = WorldLayout.zoneAt(sp.getBlockX(), sp.getBlockZ());
            if (z == SeaZone.CALM_BELT || z == SeaZone.RED_LINE) continue;
            if (inHarbour(sp)) continue; // patrols and raiders strike on the open sea, not in a town's harbour
            if (npcShips < 0) npcShips = countNpcShips(l);
            if (npcShips >= CAP_BASE + CAP_PER_PLAYER * l.players().size()) return;
            var d = Characters.get(sp);
            boolean hq = d.bounty >= WANTED && nearHeadquarters(server, sp.getX(), sp.getZ());
            double chance = 0.12 + (d.bounty > 0 ? 0.1 + Math.min(0.25, Math.log10(d.bounty + 1) / 40) : 0) + z.dangerTier * 0.02 + (hq ? 0.3 : 0);
            if (l.random.nextDouble() > chance) continue;
            boolean marine = hq || (d.bounty > 0 ? l.random.nextFloat() < 0.65f : l.random.nextFloat() < 0.3f);
            if (marine && (hq || (z.isGrandLine() && l.random.nextFloat() < 0.45f))) npcShips += spawnSquadron(l, sp, z, hq).size();
            else if (spawnRaider(l, sp, marine, z) != null) npcShips++;
        }
    }

    static int countNpcShips(ServerLevel l) {
        int n = 0;
        for (var e : l.getAllEntities()) if (e instanceof ShipEntity s && s.flag(ShipEntity.F_NPC)) n++;
        return n;
    }

    /** Within reach of Marine Headquarters (Marineford) or New Marineford. */
    public static boolean nearHeadquarters(MinecraftServer server, double x, double z) {
        WorldPlan plan = WorldPlan.of(server);
        if (plan == null) return false;
        for (String id : new String[]{"marineford", "new_marineford"}) {
            Island i = plan.byId(id);
            if (i != null && Math.hypot(x - i.x, z - i.z) < i.radius + HQ_RADIUS) return true;
        }
        return false;
    }

    static boolean inHarbour(ServerPlayer sp) {
        WorldPlan plan = WorldPlan.of(sp.server);
        if (plan == null) return false;
        for (var isl : plan.candidates(sp.getBlockX(), sp.getBlockZ()))
            if (isl.settlementType != com.grandline.world.settlement.SettlementType.NONE && isl.t(sp.getBlockX(), sp.getBlockZ()) < 1.35) return true;
        return false;
    }

    static boolean isAtSea(ServerPlayer sp) {
        return sp.level().getBlockState(sp.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.WATER) && sp.getY() < WorldPlan.SEA_LEVEL + 4;
    }

    @Nullable
    private static Vec3 openWaterAround(ServerLevel l, double cx, double cz, double dist, double ang) {
        for (int tries = 0; tries < 8; tries++) {
            double a = ang + tries * 0.8;
            int x = (int) (cx + Math.cos(a) * dist), z = (int) (cz + Math.sin(a) * dist);
            if (!l.hasChunkAt(new BlockPos(x, 64, z))) continue;
            Vec3 pos = Shipyard.findWater(l, new BlockPos(x, 64, z));
            if (pos != null) return pos;
        }
        return null;
    }

    public static ShipEntity spawnRaider(ServerLevel l, ServerPlayer near, boolean marine, SeaZone zone) {
        ShipClass c = zone.dangerTier >= 6 ? ShipClass.GALLEON : zone.dangerTier >= 3 ? ShipClass.BRIGANTINE : zone.isBlue() && l.random.nextBoolean() ? ShipClass.SLOOP : ShipClass.CARAVEL;
        double ang = l.random.nextDouble() * Math.PI * 2;
        Vec3 pos = openWaterAround(l, near.getX(), near.getZ(), 110, ang);
        if (pos == null) return null;
        ShipEntity ship = spawnShip(l, pos, (float) Math.toDegrees(ang) + 180, c, marine, Math.max(1, zone.dangerTier), marine ? "Marine Patrol" : "Raider");
        if (ship != null) near.sendSystemMessage(net.minecraft.network.chat.Component.translatable(marine ? "grandline.sea.marine_sighted" : "grandline.sea.pirates_sighted").withStyle(net.minecraft.ChatFormatting.YELLOW));
        return ship;
    }

    /**
     * A Marine squadron: a flagship and one or two escorts holding station on its quarters. Headquarters squadrons
     * (for wanted pirates near Marineford / New Marineford) are bigger ships with tougher crews and a Vice Admiral.
     */
    public static List<ShipEntity> spawnSquadron(ServerLevel l, ServerPlayer near, SeaZone zone, boolean headquarters) {
        double ang = l.random.nextDouble() * Math.PI * 2;
        Vec3 pos = openWaterAround(l, near.getX(), near.getZ(), 130, ang);
        if (pos == null) return new ArrayList<>();
        List<ShipEntity> out = spawnSquadronAt(l, pos, (float) Math.toDegrees(ang) + 180, zone, headquarters);
        if (!out.isEmpty()) near.sendSystemMessage(net.minecraft.network.chat.Component.translatable(headquarters ? "grandline.sea.hq_squadron" : "grandline.sea.marine_squadron", out.size())
                .withStyle(headquarters ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.YELLOW));
        return out;
    }

    /** A squadron with its flagship at {@code pos} heading {@code yaw} (escorts placed on open water astern). */
    public static List<ShipEntity> spawnSquadronAt(ServerLevel l, Vec3 pos, float yaw, SeaZone zone, boolean headquarters) {
        List<ShipEntity> out = new ArrayList<>();
        int tier = Math.max(1, zone.dangerTier) + (headquarters ? 2 : 0);
        ShipClass flag = headquarters ? (zone == SeaZone.NEW_WORLD || l.random.nextBoolean() ? ShipClass.FLAGSHIP : ShipClass.GALLEON) : zone.dangerTier >= 6 ? ShipClass.GALLEON : ShipClass.BRIGANTINE;
        ShipEntity lead = spawnShip(l, pos, yaw, flag, true, tier, headquarters ? "Marine HQ Flagship" : "Marine Squadron Flagship");
        if (lead == null) return out;
        out.add(lead);
        int escorts = headquarters ? 2 : 1 + l.random.nextInt(2);
        ShipClass ec = headquarters ? ShipClass.BRIGANTINE : zone.dangerTier >= 6 ? ShipClass.BRIGANTINE : ShipClass.CARAVEL;
        for (int i = 1; i <= escorts; i++) {
            int side = i % 2 == 1 ? -1 : 1;
            Vec3 spot = lead.toWorld(side * (flag.beam + ec.beam + 5), 0, -(flag.length / 2.0 + ec.length / 2.0 + 6));
            Vec3 p = Shipyard.findWater(l, BlockPos.containing(spot));
            if (p == null) continue;
            ShipEntity e = spawnShip(l, p, yaw, ec, true, tier - 1, "Marine Escort");
            if (e == null) continue;
            e.naval.leader = lead.shipId;
            e.naval.slot = i;
            out.add(e);
        }
        return out;
    }

    /** One NPC ship with its crew (tracked by the ship; sailors man about half the guns). */
    @Nullable
    public static ShipEntity spawnShip(ServerLevel l, Vec3 pos, float yaw, ShipClass c, boolean marine, int tier, String name) {
        ShipEntity ship = ModEntities.SHIPS.get(c).get().create(l);
        if (ship == null) return null;
        ship.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        ship.setFlag(ShipEntity.F_NPC, true);
        ship.npcFaction = marine ? "marine" : "pirate";
        ship.setHull(c.hull);
        ship.setColors(marine ? 0xF4F4F4 : 0x2A2A2A, marine ? 0xE8E8E8 : 0x5A3A22);
        ship.setLook(marine ? ShipLook.MARINE : ShipLook.randomPirate(l.random));
        ship.setShipName(name);
        l.addFreshEntity(ship);
        int crew = 2 + c.ordinal() + l.random.nextInt(3) + (name.startsWith("Marine HQ") ? 3 : 0);
        for (int i = 0; i < crew; i++) {
            HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
            if (n == null) continue;
            double lz = (l.random.nextDouble() - 0.5) * c.length * 0.6, lx = (l.random.nextDouble() - 0.5) * (c.beam - 2);
            Vec3 w = ship.toWorld(lx, c.freeboard + 0.1, lz);
            n.moveTo(w.x, w.y, w.z, 0, 0);
            String role = marine ? (i == 0 ? (name.startsWith("Marine HQ") ? "vice_admiral" : c.ordinal() >= ShipClass.GALLEON.ordinal() ? "marine_captain" : "marine_officer") : "marine")
                    : (i == 0 ? "pirate_captain" : "pirate");
            n.configure(role, Math.max(1, tier + (i == 0 ? 1 : 0)), "", BlockPos.ZERO, "", l.random);
            l.addFreshEntity(n);
            ship.naval.crew.add(n.getUUID());
        }
        NpcShipAi.manGuns(ship, l, Math.max(1, crew / 2));
        return ship;
    }

    private SeaEncounters() {}
}
