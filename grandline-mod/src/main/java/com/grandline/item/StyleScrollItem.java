package com.grandline.item;

import com.grandline.combat.Loadouts;
import com.grandline.combat.style.FightingStyle;
import com.grandline.combat.style.FightingStyles;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Teaches a fighting style (found as loot or given by masters). */
public class StyleScrollItem extends Item {
    public StyleScrollItem(Properties p) {
        super(p);
    }

    @Override
    public Component getName(ItemStack stack) {
        String s = stack.get(ModDataComponents.STYLE.get());
        return s == null ? super.getName(stack) : Component.translatable("item.grandline.style_scroll.of", Component.translatable("style.grandline." + s));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        String id = stack.get(ModDataComponents.STYLE.get());
        FightingStyle style = id == null ? null : FightingStyles.get(id);
        if (style == null) return InteractionResultHolder.fail(stack);
        CharacterData d = Characters.get(sp);
        if (style.race != null && style.race != d.race) {
            sp.sendSystemMessage(Component.translatable("grandline.style.race_locked", Component.translatable(style.race.translationKey())).withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }
        if (!d.knownStyles.add(style.id)) {
            sp.sendSystemMessage(Component.translatable("grandline.style.known"));
            return InteractionResultHolder.fail(stack);
        }
        d.activeStyle = style.id;
        Loadouts.refresh(sp, d);
        CharacterManager.markDirty(sp);
        sp.sendSystemMessage(Component.translatable("grandline.style.learned", Component.translatable(style.translationKey())).withStyle(ChatFormatting.GOLD));
        if (!sp.isCreative()) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("grandline.tooltip.style_scroll").withStyle(ChatFormatting.GRAY));
    }
}
