package com.starforged.event;

import com.starforged.boss.EclipseSummoning;
import com.starforged.command.StarforgedCommand;
import com.starforged.registry.ModItems;
import com.starforged.registry.ModTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Registers every game-bus listener used by Starforged.
 */
public final class CommonEvents {
    private CommonEvents() {
    }

    public static void register() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(CommonEvents::onServerTick);
        TickEvent.PlayerTickEvent.Post.BUS.addListener(CommonEvents::onPlayerTick);
        LivingFallEvent.BUS.addListener(CommonEvents::onFall);
        LivingHurtEvent.BUS.addListener(CommonEvents::onHurt);
        LivingDeathEvent.BUS.addListener(CommonEvents::onDeath);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(CommonEvents::onLogin);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(CommonEvents::onLogout);
        RegisterCommandsEvent.BUS.addListener(event -> StarforgedCommand.register(event.getDispatcher(), event.getBuildContext()));
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        for (ServerLevel level : event.server().getAllLevels()) {
            EclipseFields.tick(level);
            GravityGrips.tickThrown(level);
            EclipseSummoning.tick(level);
            if (level.dimension() == Level.OVERWORLD) {
                StarfallManager.tick(level);
            }
        }
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.player() instanceof ServerPlayer player) {
            HammerSlams.tick(player);
            ArmorAbilities.tick(player);
        }
    }

    private static boolean onFall(LivingFallEvent event) {
        float multiplier = ArmorAbilities.fallMultiplier(event.getEntity());
        if (multiplier <= 0.0F) {
            event.getEntity().resetFallDistance();
            return true;
        }
        event.setDamageMultiplier(event.getDamageMultiplier() * multiplier);
        return false;
    }

    private static boolean onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();

        // Starlight weapons burn the voidborn.
        if (victim.getType().is(ModTags.VOIDBORN) && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.is(ModItems.STARMETAL_SWORD.get()) || weapon.is(ModItems.ECLIPSE_BLADE.get()) || weapon.is(ModItems.METEOR_HAMMER.get())
                || weapon.is(ModItems.STARMETAL_AXE.get())) {
                event.setAmount(event.getAmount() * 1.5F);
            }
        }
        // The Eclipse Crown shields its wearer from the Sovereign's servants.
        if (source.getEntity() != null && source.getEntity().getType().is(ModTags.VOIDBORN) && ArmorAbilities.hasEclipseCrown(victim)) {
            event.setAmount(event.getAmount() * 0.75F);
        }
        ArmorAbilities.onHurt(victim, source);
        return false;
    }

    private static boolean onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            HammerSlams.clear(player);
            GravityGrips.clear(player);
        }
        return false;
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StarfallManager.syncSky(player);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HammerSlams.clear(event.getEntity());
        GravityGrips.clear(event.getEntity());
        ArmorAbilities.clear(event.getEntity());
    }
}
