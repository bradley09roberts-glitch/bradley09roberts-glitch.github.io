package com.grandline.ship;

import com.grandline.npc.HumanoidNpc;
import com.grandline.player.Characters;
import com.grandline.registry.ModEntities;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Sea encounters for sailing players: Marine patrols (more likely with a bounty) and pirate raiders, scaled by
 * the sea's danger. Ships carry NPC crews on deck and despawn when nobody is near.
 */
public final class SeaEncounters {
    public static void tick(MinecraftServer server) {
        ServerLevel l = server.overworld();
        if (l.getGameTime() % 600 != 0 || WorldPlan.of(server) == null) return;
        for (ServerPlayer sp : l.players()) {
            if (!(sp.getVehicle() instanceof ShipEntity) && !isAtSea(sp)) continue;
            if (sp.isCreative() || sp.isSpectator()) continue;
            if (!l.getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(250), s -> s.flag(ShipEntity.F_NPC)).isEmpty()) continue;
            SeaZone z = WorldLayout.zoneAt(sp.getBlockX(), sp.getBlockZ());
            if (z == SeaZone.CALM_BELT || z == SeaZone.RED_LINE) continue;
            var d = Characters.get(sp);
            double chance = 0.12 + (d.bounty > 0 ? 0.1 + Math.min(0.25, Math.log10(d.bounty + 1) / 40) : 0) + z.dangerTier * 0.02;
            if (l.random.nextDouble() > chance) continue;
            boolean marine = d.bounty > 0 ? l.random.nextFloat() < 0.65f : l.random.nextFloat() < 0.3f;
            spawnRaider(l, sp, marine, z);
        }
    }

    static boolean isAtSea(ServerPlayer sp) {
        if (!com.grandline.core.Chunks.ready(sp.level(), sp.getBlockX(), sp.getBlockZ())) return false; // not loaded yet: do not wait for it
        return sp.level().getBlockState(sp.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.WATER) && sp.getY() < WorldPlan.SEA_LEVEL + 4;
    }

    public static ShipEntity spawnRaider(ServerLevel l, ServerPlayer near, boolean marine, SeaZone zone) {
        ShipClass c = zone.dangerTier >= 6 ? ShipClass.GALLEON : zone.dangerTier >= 3 ? ShipClass.BRIGANTINE : zone.isBlue() && l.random.nextBoolean() ? ShipClass.SLOOP : ShipClass.CARAVEL;
        double ang = l.random.nextDouble() * Math.PI * 2;
        Vec3 pos = null;
        for (int tries = 0; tries < 8 && pos == null; tries++) {
            double a = ang + tries * 0.8;
            int x = near.getBlockX() + (int) (Math.cos(a) * 110), z = near.getBlockZ() + (int) (Math.sin(a) * 110);
            if (!l.hasChunkAt(new BlockPos(x, 64, z))) continue;
            pos = Shipyard.findWater(l, new BlockPos(x, 64, z));
        }
        if (pos == null) return null;
        ShipEntity ship = ModEntities.SHIPS.get(c).get().create(l);
        if (ship == null) return null;
        ship.moveTo(pos.x, pos.y, pos.z, (float) Math.toDegrees(ang) + 180, 0);
        ship.setFlag(ShipEntity.F_NPC, true);
        ship.npcFaction = marine ? "marine" : "pirate";
        ship.setHull(c.hull);
        ship.setColors(marine ? 0xF4F4F4 : 0x2A2A2A, marine ? 0xE8E8E8 : 0x5A3A22);
        ship.setShipName(marine ? "Marine Patrol" : "Raider");
        l.addFreshEntity(ship);
        int crew = 2 + c.ordinal() + l.random.nextInt(3);
        for (int i = 0; i < crew; i++) {
            HumanoidNpc n = ModEntities.HUMANOID_NPC.get().create(l);
            if (n == null) continue;
            double lz = (l.random.nextDouble() - 0.5) * c.length * 0.6, lx = (l.random.nextDouble() - 0.5) * (c.beam - 2);
            Vec3 w = ship.toWorld(lx, c.freeboard + 0.1, lz);
            n.moveTo(w.x, w.y, w.z, 0, 0);
            String role = marine ? (i == 0 ? "marine_officer" : "marine") : (i == 0 ? "pirate_captain" : "pirate");
            n.configure(role, Math.max(1, zone.dangerTier + (i == 0 ? 1 : 0)), "", BlockPos.ZERO, "", l.random);
            l.addFreshEntity(n);
        }
        near.sendSystemMessage(net.minecraft.network.chat.Component.translatable(marine ? "grandline.sea.marine_sighted" : "grandline.sea.pirates_sighted").withStyle(net.minecraft.ChatFormatting.YELLOW));
        return ship;
    }

    private SeaEncounters() {}
}
