package com.terracraft.economy;

import com.terracraft.combat.HasTerrariaDefense;
import com.terracraft.config.TerraConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

import java.lang.invoke.MethodHandles;

/** Coin conversion, enemy coin drops and Terraria's softcore coin death penalty. */
public final class EconomyEvents {
    private EconomyEvents() {}

    public static void register() {
        BusGroup.DEFAULT.register(MethodHandles.lookup(), EconomyEvents.class);
    }

    @SubscribeEvent
    static void onPickup(PlayerEvent.ItemPickupEvent event) {
        if (TerraConfig.COMMON.autoCompactCoins.get() && Coins.value(event.getStack()) > 0) {
            Coins.compact(event.getEntity());
        }
    }

    @SubscribeEvent
    static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        Player player = event.player();
        if (player instanceof ServerPlayer && player.tickCount % 20 == 7 && TerraConfig.COMMON.autoCompactCoins.get()) {
            Coins.compact(player);
        }
    }

    /** Enemies drop coins worth their Terraria value (vanilla hostiles: based on health). */
    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !(entity.level() instanceof ServerLevel)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        long value = 0;
        if (entity instanceof CoinValue valued) {
            value = valued.coinValue();
        } else if (entity instanceof Enemy && !(entity instanceof HasTerrariaDefense) && TerraConfig.COMMON.vanillaMobsDropCoins.get()) {
            value = Math.round(entity.getMaxHealth() * 3.0);
        }
        if (value <= 0) {
            return;
        }
        // Terraria randomises coin drops between 75% and 125% of the value.
        value = Math.max(1, Math.round(value * (0.75 + entity.getRandom().nextDouble() * 0.5)));
        for (ItemStack stack : Coins.toStacks(value)) {
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ(), stack));
        }
    }

    /** Softcore: keep items, drop a share of coins where the player died. */
    @SubscribeEvent(priority = Priority.LOW)
    static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !TerraConfig.COMMON.softcoreDeaths.get()) {
            return;
        }
        long total = Coins.total(player);
        long dropped = switch (TerraConfig.COMMON.coinDeathPenalty.get()) {
            case NONE -> 0;
            case HALF -> total / 2;
            case ALL -> total;
        };
        if (dropped <= 0) {
            return;
        }
        long removed = Coins.remove(player, dropped);
        for (ItemStack stack : Coins.toStacks(removed)) {
            ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
            item.setDefaultPickUpDelay();
            player.level().addFreshEntity(item);
        }
        player.sendSystemMessage(Component.translatable("message.terracraft.death.coins", Coins.format(removed)).withStyle(ChatFormatting.GOLD));
    }

    /** Implemented by TerraCraft enemies: their Terraria coin value in copper. */
    public interface CoinValue {
        long coinValue();
    }
}
