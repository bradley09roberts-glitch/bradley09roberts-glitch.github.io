package com.longnight.world;

import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The roads between settlements. Planned when the world's history is written (each settlement is linked
 * to its nearest neighbours), and laid lazily, chunk by chunk, as players come near: a three-wide dirt
 * path over natural ground only (never through claims, water or anything built), with a signpost where
 * it leaves a settlement. Caravans and news travel these routes in the simulation.
 */
public final class Roads {
    private Roads() {}

    /** Link every living settlement to its two nearest neighbours (within 1400 blocks). */
    public static void plan(WorldSim sim) {
        List<Settlement> places = new ArrayList<>();
        for (Settlement s : sim.settlements.values()) if (s.kind == Settlement.Kind.SETTLEMENT && s.center != null) places.add(s);
        for (Settlement a : places) {
            List<Settlement> others = new ArrayList<>(places);
            others.remove(a);
            others.sort(Comparator.comparingDouble(o -> o.center.distSqr(a.center)));
            for (int i = 0; i < Math.min(2, others.size()); i++) {
                Settlement b = others.get(i);
                if (b.center.distSqr(a.center) > 1400.0 * 1400.0) continue;
                if (!linked(sim, a, b)) sim.roads.add(new WorldSim.Road(a.id, b.id));
            }
        }
        sim.setDirty();
    }

    static boolean linked(WorldSim sim, Settlement a, Settlement b) {
        for (WorldSim.Road r : sim.roads) {
            if ((r.a.equals(a.id) && r.b.equals(b.id)) || (r.a.equals(b.id) && r.b.equals(a.id))) return true;
        }
        return false;
    }

    /** Lay road through loaded chunks near players. Budgeted: a few chunks per call. */
    public static void tick(ServerLevel level, WorldSim sim) {
        if (sim.roads.isEmpty() || level.players().isEmpty()) return;
        int budget = 3;
        for (WorldSim.Road r : sim.roads) {
            if (budget <= 0) return;
            Settlement a = sim.settlement(r.a), b = sim.settlement(r.b);
            if (a == null || b == null || a.center == null || b.center == null) continue;
            for (ServerPlayer p : level.players()) {
                if (budget <= 0) return;
                double t = project(p.getX(), p.getZ(), a.center, b.center);
                double px = a.center.getX() + (b.center.getX() - a.center.getX()) * t;
                double pz = a.center.getZ() + (b.center.getZ() - a.center.getZ()) * t;
                double d2 = (px - p.getX()) * (px - p.getX()) + (pz - p.getZ()) * (pz - p.getZ());
                if (d2 > 160 * 160) continue;
                // pave the chunks of the road within reach of this player
                double len = Math.sqrt(a.center.distSqr(b.center));
                for (double s = Math.max(0, t * len - 160); s <= Math.min(len, t * len + 160) && budget > 0; s += 8) {
                    int x = (int) Math.round(a.center.getX() + (b.center.getX() - a.center.getX()) * s / len);
                    int z = (int) Math.round(a.center.getZ() + (b.center.getZ() - a.center.getZ()) * s / len);
                    long chunk = ChunkPos.asLong(x >> 4, z >> 4);
                    // the chunk and all eight neighbours: setting a block updates its neighbours, and a neighbour in a
                    // chunk that is still generating would make the server thread wait for it
                    if (r.done.contains(chunk) || !readyAround(level, x >> 4, z >> 4)) continue;
                    pave(level, sim, r, a, b, x >> 4, z >> 4, len);
                    r.done.add(chunk);
                    budget--;
                    sim.setDirty();
                }
            }
        }
    }

    static boolean readyAround(ServerLevel level, int cx, int cz) {
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) if (!Places.ready(level, x, z)) return false;
        return true;
    }

    static double project(double x, double z, BlockPos a, BlockPos b) {
        double vx = b.getX() - a.getX(), vz = b.getZ() - a.getZ();
        double l = vx * vx + vz * vz;
        if (l == 0) return 0;
        return Math.max(0, Math.min(1, ((x - a.getX()) * vx + (z - a.getZ()) * vz) / l));
    }

    static void pave(ServerLevel level, WorldSim sim, WorldSim.Road r, Settlement a, Settlement b, int cx, int cz, double len) {
        double dx = (b.center.getX() - a.center.getX()) / len, dz = (b.center.getZ() - a.center.getZ()) / len;
        boolean signA = false, signB = false;
        for (double s = 0; s <= len; s += 0.7) {
            int x = (int) Math.round(a.center.getX() + dx * s);
            int z = (int) Math.round(a.center.getZ() + dz * s);
            if (x >> 4 != cx || z >> 4 != cz) continue;
            double fromA = s, fromB = len - s;
            if (fromA < a.radius + 6 || fromB < b.radius + 6) {
                // the settlement's own lanes take over here; mark the edge with a signpost once
                if (!signA && Math.abs(fromA - (a.radius + 6)) < 0.8) {
                    signpost(level, sim, x, z, -dx, -dz, b, (int) len);
                    signA = true;
                }
                if (!signB && Math.abs(fromB - (b.radius + 6)) < 0.8) {
                    signpost(level, sim, x, z, dx, dz, a, (int) len);
                    signB = true;
                }
                continue;
            }
            // rivers and shallow water are crossed on a plank bridge with rails; deep lakes and the sea are not
            BlockPos mid = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
            boolean bridge = crossable(level, mid);
            for (int w = -2; w <= 2; w++) {
                if (Math.abs(w) == 2 && !bridge) continue;
                int wx = (int) Math.round(x - dz * w), wz = (int) Math.round(z + dx * w);
                // the edge of a road may spill into the next chunk; only touch it if that chunk is already loaded
                if ((wx >> 4 != cx || wz >> 4 != cz) && !Places.ready(level, new BlockPos(wx, 0, wz))) continue;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(wx, 0, wz)).below();
                if (sim.claimed(top, 0)) continue;
                BlockState st = level.getBlockState(top);
                BlockState above = level.getBlockState(top.above());
                if (bridge) {
                    if (top.getY() != mid.getY() || !st.is(Blocks.WATER) || !above.isAir()) continue;
                    level.setBlock(top, Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
                    if (Math.abs(w) == 2) level.setBlock(top.above(), Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
                    continue;
                }
                if (!above.isAir() && !above.canBeReplaced()) continue;
                if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.COARSE_DIRT) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM)
                        || st.is(Blocks.ROOTED_DIRT)) {
                    if (above.canBeReplaced() && !above.isAir()) level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(top, Blocks.DIRT_PATH.defaultBlockState(), 2);
                } else if (st.is(BlockTags.SAND) || st.is(Blocks.SNOW_BLOCK)) {
                    level.setBlock(top, Blocks.GRAVEL.defaultBlockState(), 2);
                }
            }
        }
    }

    /** Water a road may bridge: a river, or water no more than three blocks deep. Never the sea or a deep lake. */
    static boolean crossable(ServerLevel level, BlockPos surface) {
        if (!level.getBlockState(surface).is(Blocks.WATER) || !level.getFluidState(surface).isSource()) return false;
        var biome = level.getBiome(surface);
        if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN)) return false;
        if (biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)) return true;
        for (int d = 1; d <= 3; d++) {
            if (!level.getFluidState(surface.below(d)).is(net.minecraft.tags.FluidTags.WATER)) return true;
        }
        return false;
    }

    /** A post at a settlement's edge, its sign naming where the road goes and how far. */
    static void signpost(ServerLevel level, WorldSim sim, int x, int z, double dirX, double dirZ, Settlement to, int distance) {
        int ox = (int) Math.round(x + dirZ * 2), oz = (int) Math.round(z - dirX * 2); // beside the road
        if (!Places.loadedAround(level, new BlockPos(ox, 0, oz), 1)) return;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(ox, 0, oz));
        if (sim.claimed(top, 0) || !level.getBlockState(top).isAir()) return;
        BlockState below = level.getBlockState(top.below());
        if (!Materials.isNaturalOrReplaceable(below) || !below.isSolid()) return;
        level.setBlock(top, Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
        // sign faces travellers coming out of the settlement (rotation 0 = facing south)
        double angle = Math.toDegrees(Math.atan2(-dirX, dirZ));
        int rot = (int) Math.floorMod(Math.round(angle / 22.5), 16);
        BlockPos signPos = top.above();
        level.setBlock(signPos, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, rot), 3);
        if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            SignText text = new SignText()
                    .setMessage(0, Component.literal("To"))
                    .setMessage(1, Component.literal(to.name))
                    .setMessage(2, Component.literal("~" + (distance / 50 * 50) + " blocks"));
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
        }
    }
}
