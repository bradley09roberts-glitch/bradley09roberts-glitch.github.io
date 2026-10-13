package com.longnight.world;

import com.longnight.content.Content;
import com.longnight.sim.Building;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Lumberjacks only fell NATURAL trees: a trunk standing on soil and crowned with naturally grown
 * (non-persistent) leaves, outside any player's claim and outside the settlement's buildings.
 * Log walls of player houses are never touched. Every felled tree is replanted.
 */
public final class Trees {
    private Trees() {}

    public static boolean isLog(BlockState s) {
        return s.is(BlockTags.LOGS);
    }

    public static BlockPos findNaturalTree(ServerLevel level, WorldSim sim, Settlement s, BlockPos around, int radius, Random r) {
        for (int i = 0; i < 48; i++) {
            int x = around.getX() + r.nextInt(radius * 2 + 1) - radius;
            int z = around.getZ() + r.nextInt(radius * 2 + 1) - radius;
            BlockPos col = new BlockPos(x, around.getY(), z);
            if (!Places.loadedAround(level, col, 4)) continue; // the crown and trunk are read up to 4 blocks away
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos p = new BlockPos(x, top, z);
            if (!isLog(level.getBlockState(p))) continue;
            BlockPos base = p;
            while (isLog(level.getBlockState(base.below())) && p.getY() - base.getY() < 30) base = base.below();
            BlockState soil = level.getBlockState(base.below());
            if (!soil.is(BlockTags.DIRT)) continue;
            if (sim.claimed(base, 3)) continue;
            if (insideBuilding(s, base)) continue;
            if (!naturalCrown(level, p)) continue;
            return base;
        }
        return null;
    }

    static boolean insideBuilding(Settlement s, BlockPos p) {
        for (Building b : s.buildings) if (b.origin != null && b.contains(p, 2)) return true;
        return false;
    }

    static boolean naturalCrown(ServerLevel level, BlockPos top) {
        int natural = 0;
        for (BlockPos q : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2))) {
            BlockState st = level.getBlockState(q);
            if (st.is(BlockTags.LEAVES) && st.hasProperty(LeavesBlock.PERSISTENT) && !st.getValue(LeavesBlock.PERSISTENT)) natural++;
            if (natural >= 4) return true;
        }
        return false;
    }

    /** The connected logs of one tree, ordered bottom to top (chopped from the end). */
    public static List<BlockPos> trunk(ServerLevel level, BlockPos base) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(base);
        while (!q.isEmpty() && out.size() < 64) {
            BlockPos p = q.poll();
            if (!seen.add(p)) continue;
            if (!isLog(level.getBlockState(p))) continue;
            if (Math.abs(p.getX() - base.getX()) > 4 || Math.abs(p.getZ() - base.getZ()) > 4) continue;
            out.add(p.immutable());
            for (Direction d : Direction.values()) if (d != Direction.DOWN) q.add(p.relative(d));
            q.add(p.above().north());
            q.add(p.above().south());
            q.add(p.above().east());
            q.add(p.above().west());
        }
        out.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        java.util.Collections.reverse(out);
        return out;
    }

    public static void replant(ServerLevel level, Settlement s, BlockPos base) {
        if (base == null || !Places.ready(level, base)) return;
        if (!level.getBlockState(base).isAir() || !level.getBlockState(base.below()).is(BlockTags.DIRT)) return;
        String id = Content.culture(s.culture).block("sapling", "minecraft:oak_sapling");
        Block sapling = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        if (sapling == Blocks.AIR) sapling = Blocks.OAK_SAPLING;
        level.setBlock(base, sapling.defaultBlockState(), 3);
    }
}
