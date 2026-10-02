package com.terracraft.entity.projectile;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/** Who counts as "friendly" for player and enemy attacks (town NPCs, pets...). */
public final class TargetRules {
    private TargetRules() {}

    public static boolean isFriendlyToPlayers(LivingEntity entity) {
        if (entity instanceof FriendlyToPlayers) {
            return true;
        }
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
            return true;
        }
        return entity instanceof AbstractVillager;
    }

    /** Marker for TerraCraft town NPCs and minions. */
    public interface FriendlyToPlayers {
    }
}
