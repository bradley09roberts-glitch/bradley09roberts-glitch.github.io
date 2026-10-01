package com.starforged.client;

/**
 * Plain client-side state shared between packets, items and client event handlers.
 * Deliberately references no client-only classes so common code may call it safely.
 */
public final class ClientHooks {
    private static float shakeIntensity;
    private static int shakeTicks;
    private static int shakeDuration;
    private static boolean starfall;
    private static float eclipseTarget;
    private static float eclipse;
    private static boolean hammerDive;

    private ClientHooks() {
    }

    public static void shake(float intensity, int duration) {
        if (intensity >= shakeIntensity * (shakeTicks / (float) Math.max(1, shakeDuration))) {
            shakeIntensity = Math.min(3.0F, intensity);
            shakeTicks = duration;
            shakeDuration = duration;
        }
    }

    /** Current shake strength (0 when idle), decaying over the effect's duration. */
    public static float currentShake() {
        return shakeTicks <= 0 ? 0.0F : shakeIntensity * (shakeTicks / (float) shakeDuration);
    }

    public static void setSky(boolean isStarfall, float eclipseLevel) {
        starfall = isStarfall;
        eclipseTarget = eclipseLevel;
    }

    public static boolean isStarfall() {
        return starfall;
    }

    public static float eclipse() {
        return eclipse;
    }

    public static void startHammerDive() {
        hammerDive = true;
    }

    public static boolean isHammerDiving() {
        return hammerDive;
    }

    public static void endHammerDive() {
        hammerDive = false;
    }

    /** Called once per client tick. */
    public static void tick() {
        if (shakeTicks > 0) {
            shakeTicks--;
        }
        eclipse += (eclipseTarget - eclipse) * 0.05F;
        if (Math.abs(eclipseTarget - eclipse) < 0.002F) {
            eclipse = eclipseTarget;
        }
    }

    public static void reset() {
        shakeTicks = 0;
        starfall = false;
        eclipse = 0;
        eclipseTarget = 0;
        hammerDive = false;
    }
}
