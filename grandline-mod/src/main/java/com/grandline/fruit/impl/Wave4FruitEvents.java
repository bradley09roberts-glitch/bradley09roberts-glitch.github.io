package com.grandline.fruit.impl;

import com.grandline.GrandLine;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Game-wide effects of the fourth wave of ORIGINAL fruits that must react to damage from any source: an enemy branded
 * with a Hata Hata Jolly Roger takes 20% more from everyone, not only from the flag's owner.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class Wave4FruitEvents {

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        if (HataFruit.branded(v)) e.setAmount(e.getAmount() * 1.2f);
    }

    private Wave4FruitEvents() {}
}
