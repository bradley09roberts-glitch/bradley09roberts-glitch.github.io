package com.grandline.activity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A cooked dish: eating it grants its meal buffs ({@link Meals}); the cook's skill and a crew cook decide its quality
 * when it comes out of the crafting grid or the furnace/smoker ({@link #onCraftedBy}).
 */
public class DishItem extends Item {
    public DishItem(Properties p) {
        super(p);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) Meals.eat(entity, stack);
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        if (!level.isClientSide) Meals.onCooked(stack, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        int q = Meals.quality(stack);
        if (q > 0) tip.add(Component.translatable("grandline.quality.dish." + q).withStyle(q == 2 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        for (Meals.Effect e : Meals.effects(stack)) {
            int s = Meals.scaledSeconds(e.seconds(), q);
            tip.add(Component.translatable("grandline.meal.line", Component.translatable("buff.grandline." + e.buff()), String.format("%d:%02d", s / 60, s % 60))
                    .withStyle(ChatFormatting.BLUE));
        }
    }
}
