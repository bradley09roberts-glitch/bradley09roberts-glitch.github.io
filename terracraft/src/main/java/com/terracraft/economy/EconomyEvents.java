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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;


/** Coin conversion, enemy coin drops and Terraria's softcore coin death penalty. */
public final class EconomyEvents {
    private EconomyEvents() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(EconomyEvents.class);
    }

    @SubscribeEvent
    public static void onPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post event) {
        if (TerraConfig.COMMON.autoCompactCoins.get() && Coins.value(event.getOriginalStack()) > 0) {
            Coins.compact(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerTickMagnet(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 2 == 0
            && com.terracraft.player.TerraPlayerData.get(player).stats().has(com.terracraft.player.stats.Ability.COIN_MAGNET)) {
            // Gold Ring: coins within 12 blocks fly to the player
            for (net.minecraft.world.entity.item.ItemEntity item : player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    player.getBoundingBox().inflate(12), i -> Coins.value(i.getItem()) > 0)) {
                net.minecraft.world.phys.Vec3 pull = player.position().add(0, 0.8, 0).subtract(item.position());
                item.setDeltaMovement(pull.normalize().scale(Math.min(0.6, 0.15 + pull.length() * 0.03)));
                item.setNoPickUpDelay();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer && player.tickCount % 20 == 7 && TerraConfig.COMMON.autoCompactCoins.get()) {
            Coins.compact(player);
        }
    }

    /** Enemies drop coins worth their Terraria value (vanilla hostiles: based on health). */
    /** Lucky Coin: hitting an enemy has a 1 in 5 chance to knock a few coins out of it. */
    @SubscribeEvent
    public static void onDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !(victim instanceof net.minecraft.world.entity.monster.Enemy)
            || !(event.getSource().getEntity() instanceof ServerPlayer player) || level.getRandom().nextInt(5) != 0
            || !com.terracraft.player.TerraPlayerData.get(player).stats().has(com.terracraft.player.stats.Ability.LUCKY_COIN)) {
            return;
        }
        long value = level.getRandom().nextInt(10) == 0 ? Coins.SILVER * (1 + level.getRandom().nextInt(3)) : 10 + level.getRandom().nextInt(41);
        for (net.minecraft.world.item.ItemStack stack : Coins.toStacks(value)) {
            net.minecraft.world.entity.item.ItemEntity coin = new net.minecraft.world.entity.item.ItemEntity(level, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6,
                victim.getZ(), stack);
            coin.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.2, 0.25, (level.getRandom().nextDouble() - 0.5) * 0.2);
            level.addFreshEntity(coin);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !(entity.level() instanceof ServerLevel)) {
            return;
        }
        // Terraria pays out whenever a player damaged the enemy, even if the final blow was fire or a fall.
        if (!(event.getSource().getEntity() instanceof Player) && !event.isRecentlyHit()) {
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
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerDeath(LivingDeathEvent event) {
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
