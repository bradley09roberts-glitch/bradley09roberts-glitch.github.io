package com.grandline.boss;

import com.grandline.combat.Loadouts;
import com.grandline.haki.Haki;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A boss's notes on its Haki ("observation", "armament" or "conqueror"), taken from it in defeat. Reading one
 * awakens that Haki if it was still asleep (Conqueror's only for those born with the trait), otherwise it trains it
 * a good deal.
 */
public class HakiScrollItem extends Item {
    public static final String TAG = "haki";
    static final int TRAINING = 2400;

    public HakiScrollItem(Properties p) {
        super(p);
    }

    public static String kind(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        String k = d == null ? "" : d.copyTag().getString(TAG);
        return switch (k) {
            case "observation", "armament", "conqueror" -> k;
            default -> "armament";
        };
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.grandline.haki_scroll.of", Component.translatable("grandline.haki.kind." + kind(stack)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        CharacterData d = Characters.get(sp);
        if (!d.created) return InteractionResultHolder.fail(stack);
        String key = read(sp, d, kind(stack));
        if (key == null) return InteractionResultHolder.fail(stack);
        sp.sendSystemMessage(Component.translatable(key, Component.translatable("grandline.haki.kind." + kind(stack))).withStyle(ChatFormatting.GOLD));
        Loadouts.refresh(sp, d);
        Haki.sendState(sp);
        CharacterManager.markDirty(sp);
        if (!sp.isCreative()) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    /** Apply a scroll's lesson. Returns the message key, or null when it teaches nothing. */
    public static String read(ServerPlayer sp, CharacterData d, String kind) {
        switch (kind) {
            case "observation" -> {
                if (!d.observationUnlocked) {
                    d.observationUnlocked = true;
                    return "grandline.haki_scroll.awakened";
                }
                d.addMastery(Haki.OBS, TRAINING);
            }
            case "conqueror" -> {
                if (!d.conquerorTrait) {
                    sp.sendSystemMessage(Component.translatable("grandline.haki_scroll.no_trait").withStyle(ChatFormatting.GRAY));
                    d.addMastery(Haki.ARM, TRAINING / 2); // the lesson is not lost entirely
                    return "grandline.haki_scroll.trained";
                }
                if (!d.conquerorAwakened) {
                    d.conquerorAwakened = true;
                    return "grandline.haki_scroll.awakened";
                }
                d.addMastery(Haki.COQ, TRAINING);
            }
            default -> {
                if (!d.armamentUnlocked) {
                    d.armamentUnlocked = true;
                    return "grandline.haki_scroll.awakened";
                }
                d.addMastery(Haki.ARM, TRAINING);
            }
        }
        return "grandline.haki_scroll.trained";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("grandline.tooltip.haki_scroll").withStyle(ChatFormatting.GRAY));
    }
}
