package com.terracraft.item.modifier;

import com.terracraft.player.stats.PlayerStats;
import com.terracraft.player.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * A Terraria prefix ("Legendary", "Unreal", "Warding"...). Weapon prefixes scale the item's own stats
 * (damage, use speed, crit, knockback, velocity, mana cost); accessory prefixes add a small player bonus.
 * Percentages are fractions (0.15 = +15%).
 */
public record Modifier(String id, Category category, float damage, float speed, int crit, float knockback, float velocity, float mana,
                       float size, int defense, int maxMana, float accessoryDamage, float moveSpeed, float meleeSpeed) {
    public enum Category { UNIVERSAL, MELEE, RANGED, MAGIC, ACCESSORY }

    public String translationKey() {
        return "modifier.terracraft." + id;
    }

    public Component displayName() {
        return Component.translatable(translationKey());
    }

    /** Rough quality used for the value change and the name colour (Terraria tiers). */
    public float quality() {
        if (category == Category.ACCESSORY) {
            return defense * 0.25F + maxMana / 80.0F + crit * 0.12F + accessoryDamage * 6.0F + moveSpeed * 6.0F + meleeSpeed * 6.0F;
        }
        return damage * 2.0F + speed * 1.5F + crit * 0.05F + knockback * 0.5F + velocity * 0.5F - mana * 1.0F + size * 0.3F;
    }

    /** Player bonus of an accessory prefix. */
    public void applyAccessory(PlayerStats stats) {
        if (maxMana != 0) {
            stats.add(Stat.MAX_MANA, maxMana);
        }
        if (crit != 0) {
            stats.add(Stat.CRIT, crit);
        }
        if (accessoryDamage != 0) {
            stats.add(Stat.DAMAGE, accessoryDamage);
        }
        if (moveSpeed != 0) {
            stats.add(Stat.MOVE_SPEED, moveSpeed);
        }
        if (meleeSpeed != 0) {
            stats.add(Stat.MELEE_SPEED, meleeSpeed);
        }
    }

    /** Tooltip lines (green for bonuses, red for penalties), like Terraria's prefix lines. */
    public void describe(Consumer<Component> lines) {
        if (category == Category.ACCESSORY) {
            line(lines, defense, "defense", false);
            line(lines, maxMana, "max_mana", false);
            line(lines, crit, "crit", true);
            line(lines, Math.round(accessoryDamage * 100), "damage", true);
            line(lines, Math.round(moveSpeed * 100), "move_speed", true);
            line(lines, Math.round(meleeSpeed * 100), "melee_speed", true);
            return;
        }
        line(lines, Math.round(damage * 100), "damage", true);
        line(lines, Math.round(speed * 100), "speed", true);
        line(lines, crit, "crit", true);
        line(lines, Math.round(-mana * 100), "mana_cost_reduction", true);
        line(lines, Math.round(size * 100), "size", true);
        line(lines, Math.round(velocity * 100), "velocity", true);
        line(lines, Math.round(knockback * 100), "knockback", true);
    }

    private static void line(Consumer<Component> lines, int value, String key, boolean percent) {
        if (value == 0) {
            return;
        }
        String number = (value > 0 ? "+" : "") + value + (percent ? "%" : "");
        lines.accept(Component.translatable("modifier.terracraft.line." + key, number)
            .withStyle(value > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    static List<Category> categoriesFor(Category weaponType) {
        return weaponType == Category.ACCESSORY ? List.of(Category.ACCESSORY) : List.of(Category.UNIVERSAL, weaponType);
    }
}
