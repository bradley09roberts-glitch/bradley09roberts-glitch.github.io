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
        TickEvent.LevelTickEvent.Pre.BUS.addListener(event -> {
            com.starforged.sun.world.SunlandsTravel.suppressWeather(event.level());
            com.starforged.moon.world.PaleReachTravel.suppressWeather(event.level());
            com.starforged.tempest.world.StormreachTravel.forceStorm(event.level());
        });
        net.minecraftforge.event.entity.EntityJoinLevelEvent.BUS.addListener(CommonEvents::onJoinLevel);
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
            com.starforged.sun.boss.SunSummoning.tick(level);
            com.starforged.sun.event.SunAbilities.tickLevel(level);
            com.starforged.moon.boss.MatriarchSummoning.tick(level);
            com.starforged.moon.event.MoonAbilities.tickLevel(level);
            com.starforged.moon.world.MoonTides.tick(level);
            com.starforged.tempest.world.StormreachStorms.tick(level);
            com.starforged.tempest.event.TempestAbilities.tickLevel(level);
            com.starforged.tempest.boss.RegentSummoning.tick(level);
            if (level.dimension() == Level.OVERWORLD) {
                StarfallManager.tick(level);
            }
        }
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (event.player() instanceof ServerPlayer player) {
            HammerSlams.tick(player);
            ArmorAbilities.tick(player);
            com.starforged.sun.event.SunAbilities.tick(player);
            com.starforged.moon.event.MoonAbilities.tick(player);
            com.starforged.tempest.event.TempestAbilities.tick(player);
        }
    }

    private static boolean onJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        com.starforged.moon.world.MoonGravity.onJoin(event.getEntity());
        if (event.getEntity() instanceof net.minecraft.world.entity.LightningBolt bolt && event.getLevel() instanceof ServerLevel level) {
            com.starforged.tempest.event.TempestAbilities.onBolt(level, bolt);
        }
        return false;
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
        if (victim.is(ModTags.VOIDBORN) && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.is(ModItems.STARMETAL_SWORD.get()) || weapon.is(ModItems.ECLIPSE_BLADE.get()) || weapon.is(ModItems.METEOR_HAMMER.get())
                || weapon.is(ModItems.STARMETAL_AXE.get())) {
                event.setAmount(event.getAmount() * 1.5F);
            }
        }
        // The Eclipse Crown shields its wearer from the Sovereign's servants.
        if (source.getEntity() != null && source.getEntity().is(ModTags.VOIDBORN) && ArmorAbilities.hasEclipseCrown(victim)) {
            event.setAmount(event.getAmount() * 0.75F);
        }
        ArmorAbilities.onHurt(victim, source);
        com.starforged.sun.event.SunAbilities.onHurt(victim, source);
        event.setAmount(event.getAmount() * com.starforged.moon.event.MoonAbilities.onHurt(victim, source, event.getAmount()));
        float storm = com.starforged.tempest.event.TempestAbilities.onHurt(victim, source, event.getAmount());
        if (storm <= 0.0F) {
            return true;
        }
        event.setAmount(event.getAmount() * storm);
        return false;
    }

    private static boolean onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && com.starforged.sun.event.SunAbilities.tryRebirth(player)) {
            return true;
        }
        if (event.getEntity() instanceof Player player) {
            com.starforged.sun.event.SunAbilities.clear(player);
            com.starforged.moon.event.MoonAbilities.clear(player);
            com.starforged.tempest.event.TempestAbilities.clear(player);
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
        com.starforged.moon.event.MoonAbilities.clear(event.getEntity());
        com.starforged.tempest.event.TempestAbilities.clear(event.getEntity());
    }
}
