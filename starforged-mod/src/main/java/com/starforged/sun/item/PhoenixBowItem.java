package com.starforged.sun.item;

import com.starforged.item.LoreItem;
import com.starforged.sun.SunSounds;
import com.starforged.sun.entity.SolarFlareEntity;
import com.starforged.util.Targeting;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Phoenix Bow: needs no arrows - every shot is a phoenix of flame that hunts its target and bursts on impact.
 * A full draw looses three.
 */
public class PhoenixBowItem extends BowItem {
    public PhoenixBowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack bow, Level level, LivingEntity entity, int remainingTime) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        int held = this.getUseDuration(bow, entity) - remainingTime;
        float power = getPowerForTime(held);
        if (power < 0.2F) {
            return false;
        }
        if (level instanceof ServerLevel server) {
            int count = power >= 1.0F ? 3 : 1;
            float damage = 5.0F + 5.0F * power;
            LivingEntity target = Targeting.homingTarget(server, player, 48.0, 0.8);
            Vec3 look = player.getViewVector(1.0F);
            for (int i = 0; i < count; i++) {
                double spread = (i - (count - 1) / 2.0) * 0.16;
                Vec3 dir = look.yRot((float) spread).add(0, Math.abs(spread) * 0.3, 0).normalize();
                Vec3 start = player.getEyePosition().add(dir.scale(0.8)).add(0, -0.1, 0);
                SolarFlareEntity.shoot(server, player, start, dir, 1.3F + 1.3F * power, damage, SolarFlareEntity.Kind.PHOENIX, target);
            }
            bow.hurtAndBreak(1, player, player.getUsedItemHand());
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SunSounds.PHOENIX_BOW_SHOOT.get(), SoundSource.PLAYERS, 1.0F,
                0.9F + power * 0.3F);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.phoenix_bow");
    }
}
