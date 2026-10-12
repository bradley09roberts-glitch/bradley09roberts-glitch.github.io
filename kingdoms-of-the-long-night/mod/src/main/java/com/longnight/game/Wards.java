package com.longnight.game;

import com.longnight.block.WardStoneBlock;
import com.longnight.registry.ModBlocks;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;

import java.util.Iterator;
import java.util.Map;

/**
 * Wards (hearth-witchery): a kindled ward stone stops night-spawn from rising within 24 blocks for
 * three days. Settlements also keep their streets lit and watched: most natural monster spawns
 * inside a materialized settlement fail, so towns are refuges rather than graveyards.
 */
public final class Wards {
    private Wards() {}

    public static final int RADIUS = 24;
    public static final int DAYS = 3;

    public static void kindle(ServerLevel level, BlockPos pos) {
        WorldSim sim = WorldSim.get(level.getServer());
        sim.wards.put(pos.asLong(), WorldSim.dayOf(level.getServer().overworld()) + DAYS);
        sim.setDirty();
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        WorldSim sim = WorldSim.get(level.getServer());
        if (sim.wards.remove(pos.asLong()) != null) sim.setDirty();
    }

    public static void daily(ServerLevel level, WorldSim sim, long day) {
        Iterator<Map.Entry<Long, Long>> it = sim.wards.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> e = it.next();
            if (e.getValue() >= day) continue;
            BlockPos p = BlockPos.of(e.getKey());
            if (level.isLoaded(p) && level.getBlockState(p).is(ModBlocks.WARD_STONE.get())) {
                level.setBlock(p, level.getBlockState(p).setValue(WardStoneBlock.ACTIVE, false), 3);
            }
            it.remove();
            sim.setDirty();
        }
    }

    /** Should this natural monster spawn be prevented? */
    public static boolean blocks(ServerLevel level, Mob mob, MobSpawnType type) {
        if (!(mob instanceof Enemy) || (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION)) return false;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        WorldSim sim = WorldSim.get(level.getServer());
        BlockPos p = mob.blockPosition();
        for (Long w : sim.wards.keySet()) {
            if (BlockPos.of(w).distSqr(p) < RADIUS * RADIUS) return true;
        }
        Settlement s = sim.settlementAt(p, 8);
        return s != null && s.materialized && s.kind == Settlement.Kind.SETTLEMENT && level.random.nextFloat() < 0.85f;
    }
}
