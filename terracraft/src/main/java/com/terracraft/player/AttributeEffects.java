package com.terracraft.player;

import com.terracraft.TerraCraft;
import com.terracraft.player.stats.Ability;
import com.terracraft.player.stats.PlayerStats;
import com.terracraft.player.stats.Stat;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Pushes the attribute-backed parts of {@link PlayerStats} into vanilla attributes, so Minecraft's own
 * movement, mining and knockback code honours Terraria accessories (Hermes Boots, Shiny Red Balloon,
 * Cobalt Shield, Lucky Horseshoe, mining potions...).
 * <p>
 * It also neutralises vanilla armor damage reduction for players: Terraria defense (computed from all
 * armor, including converted vanilla armor) is applied by the TerraCraft damage pipeline instead.
 */
public final class AttributeEffects {
    private static final Identifier MOVE_SPEED = TerraCraft.id("stat_move_speed");
    private static final Identifier JUMP = TerraCraft.id("stat_jump");
    private static final Identifier MELEE_SPEED = TerraCraft.id("stat_melee_speed");
    private static final Identifier KNOCKBACK_IMMUNITY = TerraCraft.id("stat_knockback_immunity");
    private static final Identifier FALL_DAMAGE = TerraCraft.id("stat_fall_damage");
    private static final Identifier MINING_SPEED = TerraCraft.id("stat_mining_speed");
    private static final Identifier REACH = TerraCraft.id("stat_reach");
    private static final Identifier SAFE_FALL = TerraCraft.id("stat_safe_fall");
    private static final Identifier NO_VANILLA_ARMOR = TerraCraft.id("terraria_defense_replaces_armor");
    private static final Identifier NO_VANILLA_TOUGHNESS = TerraCraft.id("terraria_defense_replaces_toughness");

    private AttributeEffects() {}

    public static void apply(Player player, PlayerStats stats) {
        set(player, Attributes.MOVEMENT_SPEED, MOVE_SPEED, stats.get(Stat.MOVE_SPEED), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.JUMP_STRENGTH, JUMP, stats.get(Stat.JUMP_HEIGHT), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.ATTACK_SPEED, MELEE_SPEED, stats.get(Stat.MELEE_SPEED), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_IMMUNITY, stats.has(Ability.KNOCKBACK_IMMUNE) ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        double fall = stats.has(Ability.NO_FALL_DAMAGE) ? -1.0 : Math.max(-1.0, stats.get(Stat.FALL_DAMAGE));
        set(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_DAMAGE, fall, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        // Jump boosts also raise the height a player can safely fall from.
        set(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL, stats.get(Stat.JUMP_HEIGHT) * 3.0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.BLOCK_BREAK_SPEED, MINING_SPEED, stats.get(Stat.MINING_SPEED), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.BLOCK_INTERACTION_RANGE, REACH, stats.get(Stat.REACH), AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ARMOR, NO_VANILLA_ARMOR, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(player, Attributes.ARMOR_TOUGHNESS, NO_VANILLA_TOUGHNESS, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void set(Player player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount == 0.0) {
            if (instance.getModifier(id) != null) {
                instance.removeModifier(id);
            }
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing == null || existing.amount() != amount || existing.operation() != operation) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }
}
