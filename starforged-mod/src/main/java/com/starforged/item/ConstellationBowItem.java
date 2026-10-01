package com.starforged.item;

import com.starforged.entity.projectile.StarboltEntity;
import com.starforged.registry.ModSounds;
import com.starforged.util.Targeting;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
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
 * Constellation Bow: arrows become living stars that seek out enemies. A full draw releases three.
 */
public class ConstellationBowItem extends BowItem {
    public ConstellationBowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean releaseUsing(ItemStack bow, Level level, LivingEntity entity, int remainingTime) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        ItemStack ammo = player.getProjectile(bow);
        if (ammo.isEmpty()) {
            return false;
        }
        int held = this.getUseDuration(bow, entity) - remainingTime;
        float power = getPowerForTime(held);
        if (power < 0.2F) {
            return false;
        }

        if (level instanceof ServerLevel server) {
            List<ItemStack> drawn = draw(bow, ammo, player);
            if (drawn.isEmpty()) {
                return false;
            }
            int count = power >= 1.0F ? 3 : 1;
            float damage = 4.0F + 4.0F * power;
            LivingEntity target = Targeting.homingTarget(server, player, 48.0, 0.8);
            Vec3 look = player.getViewVector(1.0F);
            for (int i = 0; i < count; i++) {
                double spread = (i - (count - 1) / 2.0) * 0.14;
                Vec3 dir = look.yRot((float) spread).add(0, Math.abs(spread) * 0.3, 0).normalize();
                Vec3 start = player.getEyePosition().add(dir.scale(0.6)).add(0, -0.1, 0);
                StarboltEntity.shoot(server, player, start, dir, 1.4F + 1.6F * power, damage, StarboltEntity.Variant.STAR, target);
            }
            bow.hurtAndBreak(1, player, player.getUsedItemHand());
            server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BOW_STARSHOT.get(), SoundSource.PLAYERS, 1.0F,
                0.9F + power * 0.3F);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.constellation_bow");
    }
}
