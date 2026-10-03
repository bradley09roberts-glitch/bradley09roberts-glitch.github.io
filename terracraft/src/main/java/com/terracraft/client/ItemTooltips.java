package com.terracraft.client;

import com.terracraft.combat.DamageCalc;
import com.terracraft.economy.Coins;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.item.armor.TerrariaArmorItem;
import com.terracraft.item.weapon.AmmoItem;
import com.terracraft.item.weapon.RangedWeaponItem;
import com.terracraft.mining.ToolPowers;
import com.terracraft.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Terraria-style item tooltips generated from {@link TerraItemStats} and item effects. */
public final class ItemTooltips {
    private ItemTooltips() {}

    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> lines = new ArrayList<>();
        TerraItemStats stats = stack.get(ModDataComponents.STATS);
        if (stats != null) {
            describe(stack, stats, lines);
        } else {
            int power = ToolPowers.pickaxePower(stack);
            if (power > 0) {
                lines.add(Component.translatable("tooltip.terracraft.pickaxe_power", power).withStyle(ChatFormatting.GRAY));
            }
        }
        if (!lines.isEmpty()) {
            List<Component> tooltip = event.getToolTip();
            tooltip.addAll(Math.min(1, tooltip.size()), lines);
        }
    }

    private static void describe(ItemStack stack, TerraItemStats stats, List<Component> lines) {
        if (stats.damage() > 0) {
            lines.add(Component.translatable("tooltip.terracraft.damage", stats.damage(), stats.damageClass().displayName())
                .withStyle(ChatFormatting.GRAY));
            if (!(stack.getItem() instanceof AmmoItem)) {
                lines.add(Component.translatable("tooltip.terracraft.crit", DamageCalc.BASE_CRIT + stats.crit()).withStyle(ChatFormatting.GRAY));
            }
            if (stats.useTime() > 0) {
                lines.add(Component.translatable("tooltip.terracraft.speed." + speed(stats.useTime())).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("tooltip.terracraft.knockback." + knockback(stats.knockback())).withStyle(ChatFormatting.GRAY));
        }
        if (stats.mana() > 0) {
            lines.add(Component.translatable("tooltip.terracraft.mana", stats.mana()).withStyle(ChatFormatting.GRAY));
        }
        if (stack.getItem() instanceof RangedWeaponItem ranged) {
            lines.add(Component.translatable("tooltip.terracraft.uses_ammo",
                Component.translatable("ammo.terracraft." + ranged.ammoType().name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (stack.getItem() instanceof AmmoItem) {
            lines.add(Component.translatable("tooltip.terracraft.ammo").withStyle(ChatFormatting.GRAY));
        }
        if (stats.pickPower() > 0) {
            lines.add(Component.translatable("tooltip.terracraft.pickaxe_power", stats.pickPower()).withStyle(ChatFormatting.GRAY));
        }
        if (stats.axePower() > 0) {
            lines.add(Component.translatable("tooltip.terracraft.axe_power", stats.axePower()).withStyle(ChatFormatting.GRAY));
        }
        if (stats.hammerPower() > 0) {
            lines.add(Component.translatable("tooltip.terracraft.hammer_power", stats.hammerPower()).withStyle(ChatFormatting.GRAY));
        }
        if (stack.getItem() instanceof TerrariaArmorItem armor) {
            lines.add(Component.translatable("tooltip.terracraft.equipable").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("tooltip.terracraft.defense", stats.defense()).withStyle(ChatFormatting.GRAY));
            armor.pieceEffects().appendTooltip(lines::add);
            lines.add(Component.translatable("tooltip.terracraft.set_bonus", armor.set().bonusDescription()).withStyle(ChatFormatting.DARK_AQUA));
        } else if (stack.getItem() instanceof AccessoryItem accessory) {
            lines.add(Component.translatable("tooltip.terracraft.equipable").withStyle(ChatFormatting.GRAY));
            if (stats.defense() > 0) {
                lines.add(Component.translatable("tooltip.terracraft.defense", stats.defense()).withStyle(ChatFormatting.GRAY));
            }
            accessory.effects().appendTooltip(lines::add);
        }
        com.terracraft.item.modifier.Modifier modifier = com.terracraft.item.modifier.Modifiers.of(stack);
        if (modifier != null) {
            modifier.describe(lines::add);
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String descriptionKey = "item." + id.getNamespace() + "." + id.getPath() + ".tooltip";
        if (net.minecraft.locale.Language.getInstance().has(descriptionKey)) {
            lines.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.BLUE));
        }
        if (stats.sellValue() > 0) {
            lines.add(Component.translatable("tooltip.terracraft.sell", Coins.format((long) stats.sellValue() * stack.getCount())).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Terraria's speed descriptions by use time. */
    static String speed(int useTime) {
        if (useTime <= 8) return "insanely_fast";
        if (useTime <= 20) return "very_fast";
        if (useTime <= 25) return "fast";
        if (useTime <= 30) return "average";
        if (useTime <= 35) return "slow";
        if (useTime <= 45) return "very_slow";
        if (useTime <= 55) return "extremely_slow";
        return "snail";
    }

    /** Terraria's knockback descriptions. */
    static String knockback(float kb) {
        if (kb <= 0) return "none";
        if (kb <= 1.5F) return "extremely_weak";
        if (kb <= 3) return "very_weak";
        if (kb <= 4) return "weak";
        if (kb <= 6) return "average";
        if (kb <= 7) return "strong";
        if (kb <= 9) return "very_strong";
        if (kb <= 11) return "extremely_strong";
        return "insane";
    }
}
