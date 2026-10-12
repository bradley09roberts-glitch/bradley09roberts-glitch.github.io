package com.grandline.ship;

import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Sea winds. The Blues have steady, slowly turning trade winds; the Grand Line's winds change direction and
 * strength abruptly (and storms); the Calm Belt has none at all.
 */
public final class Wind {
    /** Wind vector (direction * strength 0..1.5) at a position. */
    public static Vec3 at(Level level, double x, double z) {
        // the White Sea has a steady westerly toward the Cloud End; the deep sea a steady eastward current
        if (level.dimension() == com.grandline.world.SkyWorld.LEVEL) return new Vec3(0.95, 0, Math.sin(level.getGameTime() / 4000.0) * 0.2);
        if (level.dimension() == com.grandline.world.DeepWorld.LEVEL) return new Vec3(0.85, 0, 0);
        SeaZone zone = WorldLayout.zoneAt((int) x, (int) z);
        long t = level.getGameTime();
        double ang, str;
        switch (zone) {
            case CALM_BELT -> {
                return Vec3.ZERO;
            }
            case PARADISE, NEW_WORLD -> {
                long epoch = t / 2400; // new wind every 2 minutes
                long h = epoch * 0x9E3779B97F4A7C15L + (long) (x / 3000) * 31 + (zone == SeaZone.NEW_WORLD ? 7 : 0);
                h ^= h >>> 29;
                ang = (h & 0xFFFF) / 65535.0 * Math.PI * 2;
                str = 0.4 + ((h >>> 16) & 0xFF) / 255.0 * (zone == SeaZone.NEW_WORLD ? 1.1 : 0.8);
                if (level.isThundering()) str += 0.4;
            }
            default -> {
                ang = (t / 24000.0) * Math.PI * 0.5 + Math.sin(t / 6000.0) * 0.6;
                str = 0.75 + Math.sin(t / 3000.0) * 0.15;
                if (level.isRaining()) str += 0.2;
            }
        }
        return com.grandline.world.weather.SeaWeather.gust(level, x, z, new Vec3(Math.cos(ang) * str, 0, Math.sin(ang) * str));
    }

    /** 0..1.5 effective push for a heading (fair winds behind or abeam help; head winds barely do). */
    public static double factor(Vec3 wind, float yawDeg) {
        double str = wind.length();
        if (str < 1e-3) return 0;
        double hx = -Math.sin(Math.toRadians(yawDeg)), hz = Math.cos(Math.toRadians(yawDeg));
        double cos = (wind.x * hx + wind.z * hz) / str;
        double eff = 0.35 + 0.65 * Mth.clamp((cos + 0.6) / 1.6, 0, 1); // ships can tack: even head winds give some speed
        return str * eff;
    }

    private Wind() {}
}
