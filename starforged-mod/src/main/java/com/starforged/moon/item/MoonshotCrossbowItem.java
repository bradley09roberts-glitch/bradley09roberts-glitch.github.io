package com.starforged.moon.item;

import com.starforged.item.LoreItem;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.entity.MoonshotBoltEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Moonshot Crossbow: fires bolts of hard moonlight that need no ammunition and ricochet off walls and enemies up to five
 * times. Sneak + right-click fires a <b>Gravity Beacon</b> that sticks where it lands and drags enemies into a cluster.
 */
public class MoonshotCrossbowItem extends Item {
    public MoonshotCrossbowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            boolean beacon = player.isShiftKeyDown();
            MoonshotBoltEntity.fire(server, player, player.getLookAngle(), beacon ? 4.0F : 7.0F, beacon);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, beacon ? 0.6F : 1.3F);
            if (beacon) {
                server.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.MATRIARCH_LANCE.get(), SoundSource.PLAYERS, 0.6F, 1.8F);
            }
            player.getCooldowns().addCooldown(stack, beacon ? 120 : 16);
            stack.hurtAndBreak(1, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.moonshot");
        LoreItem.addAbility(builder, "ability.starforged.gravity_beacon");
    }
}
