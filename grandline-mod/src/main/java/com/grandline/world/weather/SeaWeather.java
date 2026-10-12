package com.grandline.world.weather;

import com.grandline.net.GLNetwork;
import com.grandline.ship.ShipEntity;
import com.grandline.world.Island;
import com.grandline.world.IslandTheme;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Local sea weather for the ocean world, replacing Minecraft's single global weather. Storm cells are deterministic
 * (seed + time) so every player and the server agree without saving anything: they form, drift and fade over ~10
 * minutes, with frequency and violence set by the sea zone — gentle rain in the Blues, sudden storms, fog banks and
 * snow squalls in Paradise, cyclones in the New World, and nothing at all in the Calm Belt. Winter islands keep their
 * own snowfall and desert islands stay dry, as in canon. Storms push ships, batter hulls under full sail and throw
 * lightning; navigators see them coming.
 */
public final class SeaWeather {
    public enum Type { CLEAR, RAIN, STORM, SNOW, FOG, CYCLONE }

    /** Weather at a point: type, intensity 0..1, and (for cyclones) the eye position for swirling winds. */
    public record Here(Type type, float intensity, double eyeX, double eyeZ) {
        static final Here CLEAR = new Here(Type.CLEAR, 0, 0, 0);
    }

    record Cell(Type type, double x0, double z0, double vx, double vz, double radius, float strength, long born, long life) {
        double x(long t) { return x0 + vx * (t - born); }
        double z(long t) { return z0 + vz * (t - born); }

        float intensity(long t, double px, double pz) {
            long age = t - born;
            if (age < 0 || age > life) return 0;
            double d = Math.hypot(px - x(t), pz - z(t));
            if (d > radius) return 0;
            double edge = d < radius * 0.55 ? 1 : 1 - (d - radius * 0.55) / (radius * 0.45);
            double lifeK = Math.sin(Math.PI * age / life);
            return (float) (strength * edge * Math.min(1, lifeK * 1.6));
        }
    }

    static final int GRID = 3200;
    static final long EPOCH = 6000;

    private static long hash(long a, long b, long c, long seed) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (c * 0x165667B19E3779F9L);
        h ^= h >>> 31;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return h;
    }

    private static double unit(long h, int shift) {
        return ((h >>> shift) & 0xFFFF) / 65535.0;
    }

    /** Storm cells that may cover (x, z) at time t. */
    static List<Cell> cells(long seed, long t, double x, double z) {
        List<Cell> out = new ArrayList<>(4);
        int gx0 = Math.floorDiv((int) x, GRID), gz0 = Math.floorDiv((int) z, GRID);
        long e0 = Math.floorDiv(t, EPOCH);
        for (int gx = gx0 - 1; gx <= gx0 + 1; gx++)
            for (int gz = gz0 - 1; gz <= gz0 + 1; gz++)
                for (long e = e0 - 1; e <= e0; e++) {
                    long h = hash(gx, gz, e, seed);
                    double cx = (gx + unit(h, 0)) * GRID, cz = (gz + unit(h, 16)) * GRID;
                    SeaZone zone = WorldLayout.zoneAt((int) cx, (int) cz);
                    double chance = switch (zone) {
                        case CALM_BELT -> 0;
                        case EAST_BLUE, WEST_BLUE, NORTH_BLUE, SOUTH_BLUE -> 0.35;
                        case PARADISE -> 0.55;
                        case NEW_WORLD -> 0.65;
                        default -> 0.2;
                    };
                    if (unit(h, 32) >= chance) continue;
                    double roll = unit(h, 48);
                    Type type = switch (zone) {
                        case NEW_WORLD -> roll < 0.22 ? Type.CYCLONE : roll < 0.6 ? Type.STORM : roll < 0.75 ? Type.FOG : roll < 0.85 ? Type.SNOW : Type.RAIN;
                        case PARADISE -> roll < 0.4 ? Type.STORM : roll < 0.65 ? Type.RAIN : roll < 0.82 ? Type.FOG : Type.SNOW;
                        case NORTH_BLUE -> roll < 0.15 ? Type.STORM : roll < 0.45 ? Type.SNOW : roll < 0.6 ? Type.FOG : Type.RAIN;
                        default -> roll < 0.2 ? Type.STORM : roll < 0.35 ? Type.FOG : Type.RAIN;
                    };
                    long h2 = hash(gx, gz, e, seed ^ 0x57A7EL);
                    double ang = unit(h2, 0) * Math.PI * 2, spd = 0.04 + unit(h2, 16) * 0.12;
                    double radius = (type == Type.CYCLONE ? 450 : 260) + unit(h2, 32) * (type == Type.CYCLONE ? 450 : 420);
                    float strength = (float) (0.55 + unit(h2, 48) * 0.45);
                    out.add(new Cell(type, cx, cz, Math.cos(ang) * spd, Math.sin(ang) * spd, radius, strength, e * EPOCH, EPOCH * 2));
                }
        return out;
    }

    public static Here at(Level level, double x, double z) {
        if (level.dimension() != Level.OVERWORLD || !(level instanceof ServerLevel sl)) return Here.CLEAR; // server-side only
        WorldPlan plan = WorldPlan.of(sl.getServer());
        if (plan == null) return Here.CLEAR;
        long seed = plan.seed;
        // island climates
        if (plan != null) {
            Island isl = plan.islandAt((int) x, (int) z);
            if (isl != null && isl.t((int) x, (int) z) < 1.05) {
                IslandTheme th = isl.themeAt((int) x, (int) z);
                if (th == IslandTheme.DESERT || th == IslandTheme.BADLANDS) return Here.CLEAR;
                if (th == IslandTheme.SNOW || th == IslandTheme.ICE) return new Here(Type.SNOW, 0.45f, 0, 0);
            }
        }
        long t = level.getGameTime();
        Here best = Here.CLEAR;
        for (Cell c : cells(seed, t, x, z)) {
            float i = c.intensity(t, x, z);
            if (i > best.intensity()) best = new Here(c.type(), i, c.x(t), c.z(t));
        }
        return best;
    }

    /** Extra wind from the local weather: storms gust, cyclones swirl round their eye. */
    public static Vec3 gust(Level level, double x, double z, Vec3 base) {
        Here w = at(level, x, z);
        if (w.intensity() < 0.05) return base;
        return switch (w.type()) {
            case STORM -> base.scale(1 + w.intensity() * 0.8).add(Math.sin(level.getGameTime() / 37.0) * w.intensity() * 0.5, 0, Math.cos(level.getGameTime() / 53.0) * w.intensity() * 0.5);
            case CYCLONE -> {
                double dx = x - w.eyeX(), dz = z - w.eyeZ(), d = Math.max(1, Math.hypot(dx, dz));
                yield new Vec3(-dz / d, 0, dx / d).scale(0.6 + w.intensity() * 1.4);
            }
            case FOG -> base.scale(1 - w.intensity() * 0.6);
            default -> base.scale(1 + w.intensity() * 0.25);
        };
    }

    // ------------------------------------------------------------------ server tick

    private static final Map<UUID, Long> WARNED = new HashMap<>();

    public static void tick(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        WorldPlan plan = WorldPlan.of(server);
        if (plan == null) return;
        long t = ow.getGameTime();
        if (t % 200 == 0 && ow.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
            // local weather replaces the global cycle in the ocean world
            ow.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            ow.setWeatherParameters(24000 * 100, 0, false, false);
        }
        if (t % 20 != 0) return;
        for (ServerPlayer sp : ow.players()) {
            Here w = at(ow, sp.getX(), sp.getZ());
            PacketDistributor.sendToPlayer(sp, new GLNetwork.Fx("weather", sp.getX(), sp.getY(), sp.getZ(), w.type().ordinal(), w.intensity(), fogFor(w)));
            boolean violent = (w.type() == Type.STORM || w.type() == Type.CYCLONE) && w.intensity() > 0.5f;
            if (violent && ow.random.nextFloat() < 0.06f * w.intensity()) strike(ow, sp);
            if (violent && t % 40 == 0 && sp.getVehicle() instanceof ShipEntity ship && ship.sail() >= 2 && !ship.anchored()) {
                ship.hurtHull(1.5f + w.intensity() * 3, null);
                sp.displayClientMessage(Component.translatable("grandline.weather.waves").withStyle(ChatFormatting.AQUA), true);
            }
            if (w.intensity() < 0.2f) warnAhead(ow, sp, plan, t);
        }
    }

    private static void strike(ServerLevel l, ServerPlayer sp) {
        double ang = l.random.nextDouble() * Math.PI * 2, r = 18 + l.random.nextDouble() * 70;
        int x = (int) (sp.getX() + Math.cos(ang) * r), z = (int) (sp.getZ() + Math.sin(ang) * r);
        int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
        if (bolt == null) return;
        bolt.moveTo(x + 0.5, y, z + 0.5);
        // real strikes only over open water (no wildfires); over land the flash is visual
        bolt.setVisualOnly(!l.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z)).is(Blocks.WATER));
        l.addFreshEntity(bolt);
    }

    /** Warn sailors of weather ahead; a navigator in the crew spots it much earlier and reads its course. */
    private static void warnAhead(ServerLevel l, ServerPlayer sp, WorldPlan plan, long t) {
        Long last = WARNED.get(sp.getUUID());
        if (last != null && t - last < 1200) return;
        boolean navigator = hasNavigator(sp);
        Vec3 dir = sp.getVehicle() instanceof ShipEntity ship ? ship.heading() : sp.getLookAngle().multiply(1, 0, 1).normalize();
        int range = navigator ? 700 : 260;
        for (int d = 100; d <= range; d += 80) {
            Here w = at(l, sp.getX() + dir.x * d, sp.getZ() + dir.z * d);
            if (w.intensity() < 0.35f || w.type() == Type.CLEAR || w.type() == Type.RAIN) continue;
            WARNED.put(sp.getUUID(), t);
            Component kind = Component.translatable("grandline.weather." + w.type().name().toLowerCase(java.util.Locale.ROOT));
            if (navigator) sp.sendSystemMessage(Component.translatable("grandline.weather.navigator", kind, d).withStyle(ChatFormatting.YELLOW));
            else sp.sendSystemMessage(Component.translatable("grandline.weather.ahead", kind).withStyle(ChatFormatting.GRAY));
            return;
        }
    }

    private static boolean hasNavigator(ServerPlayer sp) {
        try {
            UUID cid = com.grandline.npc.crew.CrewManager.captainCharacter(sp);
            if (cid == null) return false;
            for (String id : com.grandline.npc.crew.CrewManager.members(sp.server, cid)) {
                var n = com.grandline.npc.notable.NotableCharacters.get(id);
                if (n != null && "navigator".equals(n.crewRole())) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static float fogFor(Here w) {
        return w.type() == Type.FOG ? Mth.clamp(w.intensity(), 0, 1) : w.type() == Type.CYCLONE || w.type() == Type.STORM ? w.intensity() * 0.35f : 0;
    }

    private SeaWeather() {}
}
