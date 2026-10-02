package com.terracraft.item.weapon;

import com.terracraft.TerraCraft;
import com.terracraft.combat.DamageCalc;
import com.terracraft.item.TerraItemStats;
import com.terracraft.registry.ModDataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Builds item properties from {@link TerraItemStats}. */
public final class WeaponProperties {
    public static final Identifier KNOCKBACK_ID = TerraCraft.id("weapon_knockback");
    /** Vanilla players have 1 base attack damage and 4 base attack speed. */
    private static final double PLAYER_BASE_DAMAGE = 1.0;
    private static final double PLAYER_BASE_SPEED = 4.0;

    private WeaponProperties() {}

    /** Stats component only (non-melee items, accessories, materials). */
    public static Item.Properties stats(Item.Properties properties, TerraItemStats stats) {
        return properties.component(ModDataComponents.STATS, stats);
    }

    /**
     * Melee weapons (and tools, which are weak melee weapons in Terraria): swing damage, swing speed and
     * knockback become vanilla main-hand attribute modifiers so vanilla melee combat uses Terraria numbers.
     */
    public static Item.Properties melee(Item.Properties properties, TerraItemStats stats) {
        ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                Math.max(0, stats.damage() - PLAYER_BASE_DAMAGE), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                stats.attacksPerSecond() - PLAYER_BASE_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        if (stats.knockback() > 0) {
            attributes.add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(KNOCKBACK_ID,
                stats.knockback() * DamageCalc.KNOCKBACK_SCALE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        return stats(properties, stats).attributes(attributes.build()).stacksTo(1);
    }
}
