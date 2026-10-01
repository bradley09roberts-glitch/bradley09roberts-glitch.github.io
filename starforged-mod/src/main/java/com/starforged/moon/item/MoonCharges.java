package com.starforged.moon.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Small helper for weapons that hold a few regenerating charges (stored on the stack). */
public final class MoonCharges {
    private MoonCharges() {
    }

    public static int get(ItemStack stack, int max) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("starforged_charges", max);
    }

    public static void set(ItemStack stack, int value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("starforged_charges", value));
    }

    /** Adds one charge every {@code interval} ticks (counted on the stack) up to {@code max}. */
    public static void regen(ItemStack stack, int max, int interval) {
        int charges = get(stack, max);
        if (charges >= max) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            int timer = tag.getIntOr("starforged_regen", 0) + 1;
            if (timer >= interval) {
                tag.putInt("starforged_charges", Math.min(max, charges + 1));
                timer = 0;
            }
            tag.putInt("starforged_regen", timer);
        });
    }
}
