package com.grandline.item;

import com.grandline.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Vivre Card: made from a person's nails; points to them and burns as their life fades. Sneak-use to make your own. */
public class VivreCardItem extends Item {
    public VivreCardItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        String owner = stack.get(ModDataComponents.VIVRE.get());
        if (owner == null || sp.isShiftKeyDown()) {
            stack.set(ModDataComponents.VIVRE.get(), sp.getUUID().toString());
            sp.sendSystemMessage(Component.translatable("grandline.vivre.made"));
            return InteractionResultHolder.success(stack);
        }
        ServerPlayer target = sp.server.getPlayerList().getPlayer(UUID.fromString(owner));
        if (target == null || target.level() != sp.level()) {
            sp.sendSystemMessage(Component.translatable("grandline.vivre.faint").withStyle(ChatFormatting.GRAY));
        } else {
            double dx = target.getX() - sp.getX(), dz = target.getZ() - sp.getZ();
            String dir = com.grandline.navigation.Navigation.compass(dx, dz);
            float life = target.getHealth() / target.getMaxHealth();
            sp.sendSystemMessage(Component.translatable("grandline.vivre.points", target.getDisplayName(), dir, (int) Math.sqrt(dx * dx + dz * dz),
                    Component.translatable(life > 0.6 ? "grandline.vivre.strong" : life > 0.25 ? "grandline.vivre.burning" : "grandline.vivre.ash")));
        }
        return InteractionResultHolder.success(stack);
    }
}
