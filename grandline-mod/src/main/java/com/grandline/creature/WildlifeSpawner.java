package com.grandline.creature;

import com.grandline.registry.ModEntities;
import com.grandline.world.Island;
import com.grandline.world.IslandTheme;
import com.grandline.world.TownProtection;
import com.grandline.world.WorldPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Island wildlife by theme, around players out in the wilds (never inside towns): Kung-Fu Dugongs on desert
 * coasts, Lapahns on snow islands, Banana-gators in tropical swamps, giant Sea Cats along warm shores.
 * Checks each player every 12 seconds; at most 4 wild beasts around a player.
 */
public final class WildlifeSpawner {
    public static void tick(MinecraftServer server) {
        ServerLevel l = server.overworld();
        if (l.getGameTime() % 240 != 120) return;
        WorldPlan plan = WorldPlan.of(server);
        if (plan == null) return;
        for (ServerPlayer sp : l.players()) {
            if (sp.isSpectator() || l.random.nextFloat() > 0.4f) continue;
            int x = sp.getBlockX(), z = sp.getBlockZ();
            Island isl = plan.islandAt(x, z);
            if (isl == null || TownProtection.protectedAt(plan, x, z) || isl.settlement().bounds(x, z)) continue;
            WildBeast.Kind kind = kindFor(isl.themeAt(x, z), isl.t(x, z) > 0.72, l.random);
            if (kind == null) continue;
            if (l.getEntitiesOfClass(WildBeast.class, sp.getBoundingBox().inflate(64)).size() >= 4) continue;
            int group = kind == WildBeast.Kind.KUNGFU_DUGONG ? 2 + l.random.nextInt(2) : kind == WildBeast.Kind.LAPAHN ? 1 + l.random.nextInt(2) : 1;
            spawnGroup(l, plan, sp, kind, group);
        }
    }

    /** Which beast lives here (null for none). Coastal = near the island's shoreline. */
    @Nullable
    public static WildBeast.Kind kindFor(IslandTheme theme, boolean coastal, RandomSource r) {
        float f = r.nextFloat();
        return switch (theme) {
            case DESERT, BADLANDS -> coastal ? (f < 0.75f ? WildBeast.Kind.KUNGFU_DUGONG : WildBeast.Kind.SEA_CAT) : (f < 0.3f ? WildBeast.Kind.KUNGFU_DUGONG : null);
            case SAVANNA -> coastal ? (f < 0.5f ? WildBeast.Kind.SEA_CAT : WildBeast.Kind.KUNGFU_DUGONG) : null;
            case SNOW, ICE, TAIGA -> WildBeast.Kind.LAPAHN;
            case SWAMP -> WildBeast.Kind.BANANA_GATOR;
            case TROPICAL -> coastal ? (f < 0.4f ? WildBeast.Kind.SEA_CAT : WildBeast.Kind.BANANA_GATOR) : (f < 0.5f ? WildBeast.Kind.BANANA_GATOR : null);
            default -> null;
        };
    }

    static void spawnGroup(ServerLevel l, WorldPlan plan, ServerPlayer sp, WildBeast.Kind kind, int count) {
        double ang = l.random.nextDouble() * Math.PI * 2;
        double dist = 18 + l.random.nextDouble() * 12;
        int bx = sp.getBlockX() + (int) (Math.cos(ang) * dist), bz = sp.getBlockZ() + (int) (Math.sin(ang) * dist);
        for (int i = 0; i < count; i++) {
            int x = bx + l.random.nextInt(5) - 2, z = bz + l.random.nextInt(5) - 2;
            if (!l.hasChunkAt(new BlockPos(x, 64, z)) || TownProtection.protectedAt(plan, x, z)) continue;
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y < WorldPlan.SEA_LEVEL) continue;
            if (!kind.swimmer && !l.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) continue;
            if (l.getNearestPlayer(x, y, z, 12, false) != null) continue;
            spawn(l, kind, x + 0.5, y, z + 0.5, l.random.nextFloat() * 360);
        }
    }

    @Nullable
    public static WildBeast spawn(ServerLevel l, WildBeast.Kind kind, double x, double y, double z, float yaw) {
        WildBeast b = ModEntities.WILDLIFE.get(kind).get().create(l);
        if (b == null) return null;
        b.moveTo(x, y, z, yaw, 0);
        b.setYHeadRot(yaw);
        l.addFreshEntity(b);
        return b;
    }

    private WildlifeSpawner() {}
}
