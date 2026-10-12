package com.grandline.npc.crew;

import com.grandline.npc.notable.NotableCharacters;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.ship.ShipEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What a recruited crew does for its captain at sea (every 2 s while the captain is aboard their ship):
 * shipwright/engineer repair the hull, helmsman adds speed, cook keeps everyone fed, doctor heals, musician restores
 * stamina, sniper/quartermaster make the cannons hit harder, lookout/stargazer extend sight at night.
 * (The navigator's weather warnings live in SeaWeather.) Crew left waiting at home give no bonus.
 */
public final class CrewBonuses {
    public static Set<String> activeRoles(ServerPlayer sp) {
        Set<String> roles = new HashSet<>();
        // crews are kept per character id (CrewManager.captainCharacter only answers for crew NPCs)
        UUID cid = Characters.get(sp).characterId;
        if (cid == null) return roles;
        List<String> members = CrewManager.members(sp.server, cid);
        for (String id : members) {
            var n = NotableCharacters.get(id);
            if (n != null && n.crewRole() != null) roles.add(n.crewRole());
        }
        return roles;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 0) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ShipEntity ship = sp.getVehicle() instanceof ShipEntity s ? s : null;
            if (ship == null) {
                for (ShipEntity s : sp.level().getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(40), s -> s.carries(sp))) ship = s;
            }
            if (ship == null || !ship.mayCommand(sp)) continue;
            Set<String> roles = activeRoles(sp);
            if (roles.isEmpty()) {
                ship.crewSpeed = 1f;
                ship.crewCannon = 1f;
                continue;
            }
            CharacterData d = Characters.get(sp);
            if (roles.contains("shipwright") || roles.contains("engineer")) {
                ship.repair(roles.contains("shipwright") && roles.contains("engineer") ? 6 : 3);
                if (ship.rigging() < 1f) ship.setRigging(ship.rigging() + 0.01f); // splice torn rigging
            }
            ship.crewSpeed = roles.contains("helmsman") ? 1.15f : 1f;
            ship.crewCannon = (roles.contains("sniper") ? 1.3f : 1f) * (roles.contains("quartermaster") ? 1.1f : 1f);
            if (roles.contains("cook") && sp.getFoodData().getFoodLevel() < 20) sp.getFoodData().eat(1, 0.5f);
            if (roles.contains("doctor") && sp.getHealth() < sp.getMaxHealth() && !d.isKnockedDown()) sp.heal(1f);
            if (roles.contains("musician") && d.stamina < 100) {
                d.stamina = Math.min(100, d.stamina + 6);
                CharacterManager.markDirty(sp);
            }
            if ((roles.contains("lookout") || roles.contains("stargazer")) && sp.level().isNight() && sp.level().dimension() == Level.OVERWORLD)
                sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
        }
    }

    private CrewBonuses() {}
}
