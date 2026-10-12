package com.grandline.item;

import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.DevilFruits;
import com.grandline.fruit.FruitManager;
import com.grandline.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A Devil Fruit. Eating grants its power (server validated); eating a second fruit is fatal after a warning. */
public class DevilFruitItem extends Item {
    public DevilFruitItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
                .food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().build()));
    }

    @Nullable
    public static DevilFruit fruit(ItemStack stack) {
        String id = stack.get(ModDataComponents.FRUIT.get());
        return id == null ? null : DevilFruits.get(id);
    }

    public static ItemStack of(String fruitId) {
        ItemStack s = new ItemStack(com.grandline.registry.ModItems.DEVIL_FRUIT.get());
        s.set(ModDataComponents.FRUIT.get(), fruitId);
        return s;
    }

    @Override
    public Component getName(ItemStack stack) {
        DevilFruit f = fruit(stack);
        if (f == null) return Component.translatable("item.grandline.devil_fruit.unknown");
        return Component.translatable(f.translationKey()).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (fruit(stack) == null) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp && !FruitManager.confirmEat(sp, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) {
            DevilFruit f = fruit(stack);
            if (f != null) FruitManager.eat(sp, f);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 48;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        DevilFruit f = fruit(stack);
        if (f == null) return;
        tip.add(Component.translatable("fruit_type.grandline." + f.type.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.DARK_PURPLE));
        tip.add(Component.translatable(f.translationKey() + ".desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("grandline.tooltip.fruit_warning").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }
}
