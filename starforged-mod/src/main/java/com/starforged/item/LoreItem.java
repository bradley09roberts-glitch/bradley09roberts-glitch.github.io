package com.starforged.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An item with flavour/usage lines in its tooltip, read from {@code item.starforged.<id>.desc<N>} lang keys.
 */
public class LoreItem extends Item {
    private final int lines;

    public LoreItem(Item.Properties properties, int lines) {
        super(properties);
        this.lines = lines;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        addLore(this.getDescriptionId(), this.lines, builder);
    }

    public static void addLore(String descriptionId, int lines, Consumer<Component> builder) {
        for (int i = 0; i < lines; i++) {
            builder.accept(Component.translatable(descriptionId + ".desc" + i).withStyle(i == 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_PURPLE));
        }
    }

    /** Adds an ability line: gold title followed by gray description. */
    public static void addAbility(Consumer<Component> builder, String key) {
        builder.accept(Component.translatable(key + ".title").withStyle(ChatFormatting.GOLD));
        builder.accept(Component.literal("  ").append(Component.translatable(key + ".text")).withStyle(ChatFormatting.GRAY));
    }
}
