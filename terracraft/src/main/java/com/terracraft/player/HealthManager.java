package com.terracraft.player;

import com.terracraft.TerraCraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Maps Terraria life onto Minecraft's health attribute.
 * <p>
 * Player life uses Terraria numbers directly (100 at the start, up to 500), so one vanilla "heart" equals
 * 2 life. A single permanent attribute modifier lifts MAX_HEALTH from vanilla's 20 to the Terraria value;
 * the HUD draws Terraria hearts (20 life each) instead of vanilla hearts.
 */
public final class HealthManager {
    public static final Identifier MAX_LIFE_MODIFIER = TerraCraft.id("terraria_max_life");

    private HealthManager() {}

    /** Applies the computed max life to the MAX_HEALTH attribute. */
    public static void applyMaxLife(Player player, int maxLife) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double wanted = maxLife - attribute.getBaseValue();
        AttributeModifier existing = attribute.getModifier(MAX_LIFE_MODIFIER);
        if (existing == null || existing.amount() != wanted) {
            // Permanent so it is saved with the player: on load the saved health must not be clamped to 20.
            attribute.addOrReplacePermanentModifier(new AttributeModifier(MAX_LIFE_MODIFIER, wanted, AttributeModifier.Operation.ADD_VALUE));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static void healToFull(Player player) {
        player.setHealth(player.getMaxHealth());
    }
}
