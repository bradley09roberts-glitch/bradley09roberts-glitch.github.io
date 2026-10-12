package com.grandline.creature;

import com.grandline.registry.ModEntities;
import com.grandline.ship.ShipEntity;
import com.grandline.world.Island;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side Sea King spawning around players at sea. Runs every 5 seconds over the player list only (cheap for
 * ~20 players). The Calm Belts (4,000 &lt; |z| &lt; 6,500, windless) are Sea King nests: ships there are approached
 * often, and more and bigger Sea Kings gather near the nest sites. They are rare in the Grand Line and very rare in
 * the four Blues. Reverse Mountain and settled harbours are never entered or attacked.
 */
public final class SeaKingSpawner {
    public static final int NEST_SPACING = 3000, NEST_RADIUS = 550, REVERSE_MOUNTAIN_SAFE = 1400;

    public static void tick(MinecraftServer server) {
        ServerLevel l = server.overworld();
        if (l.getGameTime() % 100 != 50) return;
        WorldPlan plan = WorldPlan.of(server);
        if (plan == null) return;
        SeaKing.LIVE.removeIf(Entity::isRemoved);
        int cap = Math.min(24, 3 + l.players().size() * 2);
        for (ServerPlayer sp : l.players()) {
            if (SeaKing.LIVE.size() >= cap) return;
            if (sp.isSpectator() || sp.isCreative()) continue;
            int x = sp.getBlockX(), z = sp.getBlockZ();
            SeaZone zone = WorldLayout.zoneAt(x, z);
            boolean nest = nearNest(x, z);
            double chance = chance(zone, nest);
            if (chance <= 0 || l.random.nextDouble() > chance) continue;
            if (!atSea(sp, plan) || isSafe(plan, x, z)) continue;
            int near = 0;
            for (SeaKing k : SeaKing.LIVE) if (k.distanceToSqr(sp) < 160 * 160) near++;
            if (near >= (nest ? 3 : zone == SeaZone.CALM_BELT ? 2 : 1)) continue;
            spawnNear(l, plan, sp.position(), zone, nest, l.random);
        }
    }

    /** Spawn chance per 5-second check for a player at sea. */
    public static double chance(SeaZone zone, boolean nest) {
        return switch (zone) {
            case CALM_BELT -> nest ? 0.45 : 0.2;
            case NEW_WORLD -> 0.006;
            case PARADISE -> 0.004;
            case WORLD_EDGE -> 0.003;
            case EAST_BLUE, WEST_BLUE, NORTH_BLUE, SOUTH_BLUE -> 0.0007;
            default -> 0;
        };
    }

    /** Sea King nests: one per 3,000 blocks of each Calm Belt, at a fixed hashed spot. */
    public static boolean nearNest(int x, int z) {
        Vec3 n = nestFor(x, z);
        return n != null && Math.hypot(n.x - x, n.z - z) < NEST_RADIUS;
    }

    @Nullable
    public static Vec3 nestFor(int x, int z) {
        int az = Math.abs(z);
        if (az <= WorldLayout.GRAND_LINE_HALF || az >= WorldLayout.CALM_BELT_OUTER) return null;
        int cell = Math.floorDiv(x, NEST_SPACING);
        long h = (cell * 0x9E3779B97F4A7C15L) ^ (z < 0 ? 0x5DEECE66DL : 0xB5297A4DL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        int nx = cell * NEST_SPACING + 500 + (int) Math.floorMod(h, NEST_SPACING - 1000);
        int nz = (4600 + (int) Math.floorMod(h >>> 20, 1300)) * (z < 0 ? -1 : 1);
        return new Vec3(nx, WorldPlan.SEA_LEVEL, nz);
    }

    /** Places Sea Kings never go: Reverse Mountain, the Red Line, and the waters of settled islands. */
    public static boolean isSafe(WorldPlan plan, int x, int z) {
        if ((long) x * x + (long) z * z < (long) REVERSE_MOUNTAIN_SAFE * REVERSE_MOUNTAIN_SAFE) return true;
        if (WorldLayout.redLineDistance(x) < WorldLayout.RED_LINE_HALF_WIDTH + 120) return true;
        for (Island i : plan.candidates(x, z)) {
            if (i.settlementType == SettlementType.NONE && i.landmarks.isEmpty()) continue;
            if (i.distance(x, z) < i.radius + 72) return true;
        }
        return false;
    }

    /** The ship an entity is aboard (riding a station or standing on the deck), or null. */
    @Nullable
    public static ShipEntity onShip(Entity e) {
        if (e.getVehicle() instanceof ShipEntity s) return s;
        List<ShipEntity> ships = e.level().getEntitiesOfClass(ShipEntity.class, e.getBoundingBox().inflate(24, 4, 24), s -> s.carries(e));
        return ships.isEmpty() ? null : ships.get(0);
    }

    static boolean atSea(Player p, WorldPlan plan) {
        WorldPlan.Sample smp = new WorldPlan.Sample();
        plan.sample(p.getBlockX(), p.getBlockZ(), smp);
        if (smp.height > WorldPlan.SEA_LEVEL - 8 || smp.canal) return false;
        if (p.getVehicle() instanceof ShipEntity) return true;
        if (p.isInWater() && p.getY() < WorldPlan.SEA_LEVEL + 2) return true;
        return onShip(p) != null;
    }

    public static int pickTier(SeaZone zone, boolean nest, RandomSource r) {
        float f = r.nextFloat();
        return switch (zone) {
            case CALM_BELT -> nest ? (f < 0.15f ? 1 : f < 0.6f ? 2 : 3) : (f < 0.4f ? 1 : f < 0.78f ? 2 : 3);
            case NEW_WORLD, WORLD_EDGE -> f < 0.3f ? 1 : f < 0.7f ? 2 : 3;
            case PARADISE -> f < 0.55f ? 1 : f < 0.9f ? 2 : 3;
            default -> f < 0.9f ? 1 : 2;
        };
    }

    public static float pickLength(int tier, RandomSource r) {
        return switch (tier) {
            case 3 -> Mth.nextFloat(r, 60, 80);
            case 2 -> Mth.nextFloat(r, 40, 55);
            default -> Mth.nextFloat(r, 25, 35);
        };
    }

    /** Spawn a Sea King 55–90 blocks from a point, in deep open water and out of sight of players. */
    @Nullable
    public static SeaKing spawnNear(ServerLevel l, WorldPlan plan, Vec3 center, SeaZone zone, boolean nest, RandomSource r) {
        int tier = pickTier(zone, nest, r);
        float length = pickLength(tier, r);
        int variant = r.nextInt(SeaKing.VARIANTS.length);
        float s = length / SeaKing.BASE_LENGTH;
        WorldPlan.Sample smp = new WorldPlan.Sample();
        double ang0 = r.nextDouble() * Math.PI * 2;
        for (int tries = 0; tries < 14; tries++) {
            double ang = ang0 + tries * 2.4;
            double dist = 55 + r.nextDouble() * 35;
            int x = (int) (center.x + Math.cos(ang) * dist), z = (int) (center.z + Math.sin(ang) * dist);
            if (!l.hasChunkAt(new BlockPos(x, WorldPlan.SEA_LEVEL, z))) continue;
            plan.sample(x, z, smp);
            if (smp.height > WorldPlan.SEA_LEVEL - (6 + 2.5 * s) || smp.canal || smp.redLine || isSafe(plan, x, z)) continue;
            if (l.getNearestPlayer(x, WorldPlan.SEA_LEVEL, z, 40, false) != null) continue;
            float yaw = (float) Math.toDegrees(Math.atan2(-(center.x - x), center.z - z));
            return spawn(l, x + 0.5, z + 0.5, yaw, variant, tier, length, Math.max(smp.height + 2 + s, WorldPlan.SEA_LEVEL - 6 - 2.4 * s));
        }
        return null;
    }

    /** Create and add a configured Sea King with its head centre at headY. */
    @Nullable
    public static SeaKing spawn(ServerLevel l, double x, double z, float yaw, int variant, int tier, float length, double headY) {
        SeaKing k = ModEntities.SEA_KING.get().create(l);
        if (k == null) return null;
        k.configure(variant, tier, length);
        k.moveTo(x, headY - k.getBbHeight() / 2, z, yaw, 0);
        k.setYHeadRot(yaw);
        k.yBodyRot = yaw;
        l.addFreshEntity(k);
        return k;
    }

    private SeaKingSpawner() {}
}
