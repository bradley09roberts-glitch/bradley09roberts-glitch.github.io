package com.grandline.fruit.impl;

import com.grandline.GrandLine;
import com.grandline.fruit.DevilFruit;
import com.grandline.fruit.FruitRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game events needed by the third (canon) wave of fruits: Seiryu flight upkeep and the Bomu explosion immunity. */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class CanonEvents {
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp) SeiryuFruit.updateFlight(sp);
    }

    /** Bomu: a bomb body is unhurt by any explosion (TNT, creepers, cannons). */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!e.getSource().is(DamageTypeTags.IS_EXPLOSION)) return;
        DevilFruit f = FruitRules.fruitOf(e.getEntity());
        if (f != null && f.id.equals("bomu") && !FruitRules.weakened(e.getEntity())) e.setCanceled(true);
    }

    private CanonEvents() {}
}
