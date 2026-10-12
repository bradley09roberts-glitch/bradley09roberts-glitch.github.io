package com.grandline.npc;

import com.grandline.world.Island;
import com.grandline.world.build.BlockSink;
import com.grandline.world.build.Builder;
import com.grandline.world.landmark.Landmarks;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Buildings;
import com.grandline.world.settlement.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Recomputes gameplay markers (NPC spawns, cells, boards) by replaying a building generator without placing blocks. */
public final class MarkerCollector {
    public record Marker(String kind, BlockPos pos, CompoundTag data) {}

    private static final Map<Plot, List<Marker>> CACHE = new ConcurrentHashMap<>();

    public static List<Marker> markers(Island island, Plot plot) {
        return CACHE.computeIfAbsent(plot, p -> collect(island, p));
    }

    private static List<Marker> collect(Island island, Plot p) {
        List<Marker> out = new ArrayList<>();
        BlockSink sink = new BlockSink() {
            @Override
            public void set(int x, int y, int z, BlockState state) {}

            @Override
            public void marker(String kind, BlockPos pos, CompoundTag data) {
                out.add(new Marker(kind, pos, data));
            }

            @Override
            public void lootChest(int x, int y, int z, BlockState chest, String lootTable) {}
        };
        if (p.type() == BuildingType.LANDMARK) {
            Landmarks.Spec spec = Landmarks.spec(p.landmark());
            Landmarks.build(p, island, new Builder(sink, p.x(), p.y(), p.z(), spec.width(), spec.depth(), p.rotation(), p.seed()));
        } else {
            Buildings.build(p, island.style, new Builder(sink, p.x(), p.y(), p.z(), p.type().w, p.type().d, p.rotation(), p.seed()));
        }
        return out;
    }

    private MarkerCollector() {}
}
