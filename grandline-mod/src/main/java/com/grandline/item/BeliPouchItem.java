package com.grandline.item;

import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A pouch of Beli found in chests and on defeated foes; use to add the coins to your purse. */
public class BeliPouchItem extends Item {
    public static final int DEFAULT = 50;

    public BeliPouchItem(Properties p) {
        super(p);
    }

    public static int amount(ItemStack s) {
        Integer v = s.get(ModDataComponents.BELI.get());
        return v == null ? DEFAULT : v;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(s);
        var d = Characters.get(sp);
        if (!d.created) return InteractionResultHolder.fail(s);
        int n = amount(s);
        d.beli += n;
        CharacterManager.markDirty(sp);
        sp.displayClientMessage(Component.translatable("grandline.beli.gained", n).withStyle(ChatFormatting.GOLD), true);
        level.playSound(null, sp.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1.6f);
        s.shrink(1);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.grandline.beli_pouch.amount", amount(s)).withStyle(ChatFormatting.GOLD));
    }
}
