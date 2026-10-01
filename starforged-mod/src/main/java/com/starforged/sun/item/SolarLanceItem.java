package com.starforged.sun.item;

import com.starforged.item.LoreItem;
import com.starforged.sun.SunSounds;
import com.starforged.sun.event.SunAbilities;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Solar Lance: right-click to charge forward as a streak of sunfire, skewering and igniting everything in your path. */
public class SolarLanceItem extends Item {
    public static final int COOLDOWN = 60;

    public SolarLanceItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            Vec3 look = player.getLookAngle();
            Vec3 dir = new Vec3(look.x, Math.max(-0.3, Math.min(0.35, look.y)), look.z).normalize();
            SunAbilities.startDash(serverPlayer, dir, 10.0F);
            player.getCooldowns().addCooldown(stack, COOLDOWN);
            stack.hurtAndBreak(1, player, hand);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SunSounds.LANCE_DASH.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        target.igniteForSeconds(4.0F);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.solar_lance");
    }
}
