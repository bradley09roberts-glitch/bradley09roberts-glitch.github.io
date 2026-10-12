package com.grandline.fruit.impl;

import com.grandline.GrandLine;
import com.grandline.registry.ModDamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-wide effects of the third-wave ORIGINAL fruits that must react to damage from any source:
 * Nemu sleepers wake when hurt, Kusari chain links echo damage, and Abura's oil makes any fire burn hotter.
 * Also expires Funa's living ships.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class OriginalFruitEvents {

    static boolean isFire(DamageSource src) {
        return src.is(DamageTypeTags.IS_FIRE) || src.is(ModDamageTypes.FIRE);
    }

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        // oil burns: any fire on an oiled body hits 75% harder and keeps it burning
        if (isFire(e.getSource()) && AburaFruit.oiled(v)) {
            e.setAmount(e.getAmount() * 1.75f);
            v.igniteForSeconds(5);
        }
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || e.getNewDamage() <= 0) return;
        NemuFruit.onDamaged(v);
        KusariFruit.echo(v, e.getNewDamage());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 20 == 0) FunaFruit.tickLivingShips(e.getServer());
    }

    private OriginalFruitEvents() {}
}
