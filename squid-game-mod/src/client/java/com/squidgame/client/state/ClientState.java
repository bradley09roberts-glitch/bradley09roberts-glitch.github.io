package com.squidgame.client.state;

import com.squidgame.net.HudPayload;
import com.squidgame.net.ResultsPayload;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side mirror of the tournament state pushed by the server (HUD, fades, danger cue, results, player numbers). */
public final class ClientState {
    private ClientState() {
    }

    public static volatile HudPayload hud;
    public static volatile long hudReceivedAt;
    public static volatile ResultsPayload results;
    public static volatile long resultsEndsAt;
    public static volatile Map<UUID, Integer> numbers = new ConcurrentHashMap<>();

    // fade: in -> hold -> out
    private static long fadeStart = -1;
    private static int fadeIn, fadeHold, fadeOut, fadeColor;

    // danger vignette
    private static long dangerStart = -1;
    private static int dangerTicks, dangerPulses, dangerColor;
    private static float dangerIntensity;

    public static long tick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void startFade(int in, int hold, int out, int argb) {
        fadeStart = System.nanoTime();
        fadeIn = in;
        fadeHold = hold;
        fadeOut = out;
        fadeColor = argb;
    }

    /** @return alpha 0..1 of the current fade, 0 when idle. */
    public static float fadeAlpha() {
        if (fadeStart < 0) {
            return 0f;
        }
        double ticks = (System.nanoTime() - fadeStart) / 50_000_000.0;
        if (ticks < fadeIn) {
            return (float) (ticks / Math.max(1, fadeIn));
        }
        if (ticks < fadeIn + fadeHold) {
            return 1f;
        }
        if (ticks < fadeIn + fadeHold + fadeOut) {
            return (float) (1.0 - (ticks - fadeIn - fadeHold) / Math.max(1, fadeOut));
        }
        fadeStart = -1;
        return 0f;
    }

    public static int fadeColor() {
        return fadeColor;
    }

    public static void startDanger(float intensity, int ticks, int pulses, int argb) {
        dangerStart = System.nanoTime();
        dangerIntensity = intensity;
        dangerTicks = Math.max(1, ticks);
        dangerPulses = pulses;
        dangerColor = argb;
    }

    /** @return vignette strength 0..1 (already includes pulsing), 0 when idle. */
    public static float dangerStrength() {
        if (dangerStart < 0) {
            return 0f;
        }
        double ticks = (System.nanoTime() - dangerStart) / 50_000_000.0;
        if (ticks >= dangerTicks) {
            dangerStart = -1;
            return 0f;
        }
        double f = ticks / dangerTicks;
        double envelope = Math.sin(Math.PI * f);
        double pulse = dangerPulses <= 0 ? 1.0 : 0.55 + 0.45 * Math.sin(2 * Math.PI * dangerPulses * f);
        return (float) (dangerIntensity * Math.max(0, envelope) * pulse);
    }

    public static int dangerColor() {
        return dangerColor;
    }

    public static void reset() {
        hud = null;
        results = null;
        numbers = new ConcurrentHashMap<>();
        fadeStart = -1;
        dangerStart = -1;
    }
}
