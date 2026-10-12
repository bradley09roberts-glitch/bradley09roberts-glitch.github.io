package com.grandline.client;

import com.grandline.GrandLine;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Renders the server's local sea weather (see SeaWeather) with vanilla's own rain, snow and thunder by easing the
 * client level's rain/thunder levels toward the local values, plus fog banks through the fog event.
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class ClientWeather {
    // ordinals of SeaWeather.Type: CLEAR, RAIN, STORM, SNOW, FOG, CYCLONE
    private static int type;
    private static float targetRain, targetThunder, targetFog;
    private static float rain, thunder, fog;
    private static long lastUpdate;

    static void update(int t, float intensity, float fogDensity) {
        type = t;
        targetRain = switch (t) {
            case 1, 3 -> intensity * 0.9f;
            case 2, 5 -> Math.min(1, 0.4f + intensity);
            default -> 0;
        };
        targetThunder = t == 2 || t == 5 ? intensity : 0;
        targetFog = fogDensity;
        lastUpdate = ClientState.clientTicks;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level.dimension() != Level.OVERWORLD || ClientState.clientTicks - lastUpdate > 100) {
            if (lastUpdate == 0) return; // never received: leave vanilla weather alone (e.g. other world types)
            targetRain = targetThunder = targetFog = 0;
        }
        rain = Mth.approach(rain, targetRain, 0.01f);
        thunder = Mth.approach(thunder, targetThunder, 0.01f);
        fog = Mth.approach(fog, targetFog, 0.01f);
        level.setRainLevel(rain);
        level.setThunderLevel(thunder);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog e) {
        if (fog < 0.02f) return;
        float far = Mth.lerp(fog, e.getFarPlaneDistance(), 20f);
        e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), far * 0.1f));
        e.setFarPlaneDistance(far);
        e.setCanceled(true);
    }

    private ClientWeather() {}
}
