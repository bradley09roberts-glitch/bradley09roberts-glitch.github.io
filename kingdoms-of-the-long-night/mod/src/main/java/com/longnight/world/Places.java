package com.longnight.world;

import com.longnight.sim.Building;
import com.longnight.sim.BuildingType;
import com.longnight.sim.Construction;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Where things are in the world: work spots, beds, doors, the stores, the fields. */
public final class Places {
    private Places() {}

    public static BlockPos mark(ServerLevel level, Settlement s, Building b, String name) {
        if (b.origin == null) return null;
        Blueprint bp = Construction.blueprint(s, b);
        BlockPos m = bp.marks.get(name);
        if (m == null) return null;
        return Blueprint.toWorld(b.origin, b.rotation, bp.width, bp.depth, m.getX(), m.getY(), m.getZ());
    }

    /**
     * True only if the chunk is already loaded and complete. Level.isLoaded is NOT that: it is true as soon as a chunk is
     * scheduled to load, and reading a block there makes the server thread wait until it has been generated.
     */
    public static boolean ready(ServerLevel level, BlockPos p) {
        return ready(level, p.getX() >> 4, p.getZ() >> 4);
    }

    public static boolean ready(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    /**
     * True if every chunk within r blocks (r up to 7) of p is loaded. Reading a block in an unloaded chunk makes the
     * server thread generate it on the spot and wait for everything queued before it: seconds, on fresh land.
     */
    public static boolean loadedAround(ServerLevel level, BlockPos p, int r) {
        return ready(level, p.offset(-r, 0, -r)) && ready(level, p.offset(r, 0, -r))
                && ready(level, p.offset(-r, 0, r)) && ready(level, p.offset(r, 0, r));
    }

    public static BlockPos ground(ServerLevel level, BlockPos p) {
        if (!ready(level, p)) return p;
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
    }

    public static List<BlockPos> beds(ServerLevel level, Settlement s, Building b) {
        List<BlockPos> out = new ArrayList<>();
        if (b.origin == null) return out;
        Blueprint bp = Construction.blueprint(s, b);
        if (bp.bedHeads == null) {
            List<Blueprint.Step> heads = new ArrayList<>();
            for (Blueprint.Step st : bp.steps) {
                BlockState state = st.state();
                if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) heads.add(st);
            }
            bp.bedHeads = heads;
        }
        for (Blueprint.Step st : bp.bedHeads) out.add(Blueprint.toWorld(b.origin, b.rotation, bp.width, bp.depth, st.x(), st.y(), st.z()));
        return out;
    }

    public static BlockPos bedFor(ServerLevel level, Settlement s, Building home, Person p) {
        List<BlockPos> beds = beds(level, s, home);
        if (beds.isEmpty()) return null;
        int idx = Math.max(0, home.residents.indexOf(p.id));
        return beds.get(idx % beds.size());
    }

    public static BlockPos storeOrCentre(ServerLevel level, Settlement s) {
        for (Building b : s.buildings) {
            if (b.type == BuildingType.STOREHOUSE && b.usable() && b.placed >= b.total) {
                BlockPos m = mark(level, s, b, "work");
                if (m != null) return m;
            }
        }
        return ground(level, s.center);
    }

    public static BlockPos findCrop(ServerLevel level, Settlement s, Building farm, Random r) {
        if (farm.origin == null) return null;
        List<BlockPos> crops = new ArrayList<>();
        BlockPos mature = null;
        int w = farm.width(), d = farm.depth();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                BlockPos p = farm.origin.offset(x, 1, z);
                if (!ready(level, p)) continue;
                BlockState st = level.getBlockState(p);
                if (st.getBlock() instanceof CropBlock crop) {
                    crops.add(p);
                    if (crop.isMaxAge(st) && (mature == null || r.nextInt(3) == 0)) mature = p;
                }
            }
        }
        if (mature != null) return mature;
        return crops.isEmpty() ? null : crops.get(r.nextInt(crops.size()));
    }
}
