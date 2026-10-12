package com.grandline.client;

import com.grandline.GrandLine;
import com.grandline.career.CareerTrack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Career titles on the client: players' name tags read "Vice Admiral Name" from the title the server sends with the
 * other visuals (chat and the tab list are decorated on the server).
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class CareerClient {
    private static final Map<String, Component> PARSED = new HashMap<>();

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat e) {
        Player p = e.getEntity();
        if (!p.level().isClientSide) return;
        String json = ClientState.visuals(p.getId()).getString("careerTitle");
        if (json.isEmpty()) return;
        Component title = PARSED.get(json);
        if (title == null) {
            try {
                title = Component.Serializer.fromJson(json, p.registryAccess());
            } catch (RuntimeException ex) {
                title = null;
            }
            if (title == null) return;
            if (PARSED.size() > 256) PARSED.clear();
            PARSED.put(json, title);
        }
        e.setDisplayname(CareerTrack.decorate(title, e.getDisplayname()));
    }

    private CareerClient() {}
}
