package com.grandline.test;

import com.grandline.npc.Archetypes;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.build.BlockSink;
import com.grandline.world.build.Builder;
import com.grandline.world.landmark.Landmarks;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Plot;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

/**
 * Deterministic verification of every registered landmark builder. Each landmark is built into an in-memory
 * {@link BlockSink} (flat stone ground, or open water for sea-level pieces) in all four rotations and checked for:
 * no exception, footprint respected (nothing written outside width×depth), enough blocks placed, identical output
 * on a rebuild, markers independent of terrain (as {@code MarkerCollector} replays them), known NPC roles, NPCs
 * standing on a floor with headroom, and content (NPC markers / loot chests). The hand-designed canon set pieces
 * are additionally required to have a chest, NPCs that can all be walked to from outside the footprint, and an
 * enterable roofed interior. Finally a few landmarks are compared against the blocks actually stamped into the
 * generated world through {@code ChunkSink}.
 */
public final class LandmarkCheck {
    public interface Reporter {
        void check(String name, boolean ok, String detail);
    }

    /** Set pieces that replaced the placeholder plinths; held to the stricter standard. */
    static final Set<String> SET_PIECES = Set.of(
            "gray_terminal", "hilltop_mansion", "tangerine_grove", "white_city", "explorers_monument", "junkyard_fort", "royal_castle",
            "welcome_arch", "giant_duel_ground", "volcano_vent", "alubarna_palace", "nanohana_port", "rainbase_casino", "yuba_oasis",
            "cricket_house", "galley_la_docks", "gates_of_justice", "haunted_mansion", "ruined_castle", "mangrove_grove", "auction_house",
            "kuja_palace", "abandoned_lab", "corrida_colosseum", "royal_plateau", "tontatta_village", "giant_legs", "cake_chateau",
            "flower_capital_castle", "waterfall_gate", "skull_dome", "revolutionary_hq", "future_lab", "pirate_hive");

    /** Landmarks that are pure markers by design (a sea current, a fruit spawn) and need no NPC or chest. */
    static final Set<String> MARKER_ONLY = Set.of("knock_up_marker", "fruit_tree_grove");

    /** Landmarks additionally verified against the real generated world. */
    static final List<String> WORLD_SAMPLES = List.of("alubarna_palace", "galley_la_docks", "flower_capital_castle", "white_city");

    private static final int ORIGIN_Y = 80;

    public static List<String> run(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        run(server, (n, ok, d) -> out.add((ok ? "PASS " : "FAIL ") + n + (d.isEmpty() ? "" : " — " + d)));
        return out;
    }

    /** Runs every check, reporting through {@code r}; returns 0 (ticks to wait) so it can be a self-test step. */
    public static int run(MinecraftServer server, Reporter r) {
        WorldPlan plan = WorldPlan.of(server);
        // 1) no canon landmark still falls back to the placeholder plinth
        Set<String> missing = new TreeSet<>();
        for (Island isl : plan.islands) for (Island.LandmarkPlacement lp : isl.landmarks) if (!Landmarks.implemented(lp.id())) missing.add(lp.id());
        r.check("landmarks: no placeholder set pieces remain", missing.isEmpty(), missing.isEmpty() ? Landmarks.ids().size() + " registered builders" : "placeholders: " + missing);
        // 2) every registered builder, in memory
        for (String id : Landmarks.ids()) {
            List<String> problems = new ArrayList<>();
            String detail;
            try {
                detail = checkOne(plan, id, problems);
            } catch (Throwable t) {
                problems.add("exception " + t);
                detail = "";
            }
            r.check("landmark " + id, problems.isEmpty(), problems.isEmpty() ? detail : String.join("; ", problems) + " | " + detail);
        }
        // 3) a few against the generated world
        ServerLevel level = server.overworld();
        for (String id : WORLD_SAMPLES) {
            if (!Landmarks.implemented(id)) continue;
            try {
                worldCheck(plan, level, id, r);
            } catch (Throwable t) {
                r.check("landmark " + id + " stamped into the world", false, t.toString());
            }
        }
        // 4) every placed set piece fits between the build limits of its level (a chunk write above the limit is
        //    dropped silently: Mary Geoise's domes at y 326+ were never generated before 0.2.0)
        ceiling(server, r);
        return 0;
    }

    private static void ceiling(MinecraftServer server, Reporter r) {
        List<String> over = new ArrayList<>();
        int placed = 0, top = Integer.MIN_VALUE;
        String topId = "";
        for (ServerLevel lv : server.getAllLevels()) {
            WorldPlan plan = WorldPlan.forLevel(lv);
            if (plan == null) continue;
            for (Island isl : plan.islands) {
                if (isl.landmarks.isEmpty()) continue;
                for (Plot p : isl.settlement().plots) {
                    String id = p.landmark();
                    if (id == null || id.isEmpty() || !Landmarks.implemented(id)) continue;
                    Landmarks.Spec spec = Landmarks.spec(id);
                    MemSink m = new MemSink(p.x(), p.z(), p.x() + p.sizeX() - 1, p.z() + p.sizeZ() - 1, p.y(), spec.atSeaLevel());
                    Landmarks.build(p, isl, new Builder(m, p.x(), p.y(), p.z(), spec.width(), spec.depth(), p.rotation(), p.seed()));
                    int hi = Integer.MIN_VALUE, lo = Integer.MAX_VALUE;
                    for (var e : m.blocks.long2ObjectEntrySet()) {
                        if (e.getValue().isAir()) continue;
                        int y = BlockPos.getY(e.getLongKey());
                        hi = Math.max(hi, y);
                        lo = Math.min(lo, y);
                    }
                    placed++;
                    if (lv.getMaxBuildHeight() - 1 - hi < (top == Integer.MIN_VALUE ? Integer.MAX_VALUE : top)) {
                        top = lv.getMaxBuildHeight() - 1 - hi;
                        topId = id + " on " + isl.id + " (top y " + hi + ")";
                    }
                    if (hi >= lv.getMaxBuildHeight() || lo < lv.getMinBuildHeight())
                        over.add(id + " on " + isl.id + " in " + lv.dimension().location().getPath() + ": y " + lo + ".." + hi
                                + " outside " + lv.getMinBuildHeight() + ".." + (lv.getMaxBuildHeight() - 1));
                }
            }
        }
        r.check("landmarks: every placed set piece is inside its level's build height", placed > 0 && over.isEmpty(),
                over.isEmpty() ? placed + " placements; least headroom " + top + " blocks: " + topId : String.join("; ", over));
    }

    // ------------------------------------------------------------------ in-memory build

    static final class MemSink implements BlockSink {
        final int minX, minZ, maxX, maxZ, groundY;
        final boolean sea;
        final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        final List<Object[]> markers = new ArrayList<>();
        final List<BlockPos> chests = new ArrayList<>();
        final Map<BlockPos, String> lootOf = new HashMap<>();
        int outside;
        String firstOutside;
        int maxY = Integer.MIN_VALUE;

        MemSink(int minX, int minZ, int maxX, int maxZ, int groundY, boolean sea) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.groundY = groundY;
            this.sea = sea;
        }

        BlockState terrain(int y) {
            if (sea) return y <= groundY ? (y > groundY - 8 ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState()) : Blocks.AIR.defaultBlockState();
            return y < groundY ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                if (outside++ == 0) firstOutside = (x - minX) + "," + (y - groundY) + "," + (z - minZ);
                return;
            }
            blocks.put(BlockPos.asLong(x, y, z), state);
            if (!state.isAir()) maxY = Math.max(maxY, y);
        }

        @Override
        public BlockState get(int x, int y, int z) {
            BlockState s = blocks.get(BlockPos.asLong(x, y, z));
            return s != null ? s : terrain(y);
        }

        @Override
        public void marker(String kind, BlockPos pos, CompoundTag data) {
            markers.add(new Object[]{kind, pos.immutable(), data.copy()});
        }

        @Override
        public void lootChest(int x, int y, int z, BlockState chest, String lootTable) {
            set(x, y, z, chest);
            if (!(x < minX || x > maxX || z < minZ || z > maxZ)) {
                chests.add(new BlockPos(x, y, z));
                lootOf.put(new BlockPos(x, y, z), lootTable);
            }
        }
    }

    private static Island host(WorldPlan plan, String id) {
        for (Island isl : plan.islands) for (Island.LandmarkPlacement lp : isl.landmarks) if (lp.id().equals(id)) return isl;
        return plan.islands.get(0);
    }

    private static Plot realPlot(Island isl, String id) {
        for (Plot p : isl.settlement().plots) if (id.equals(p.landmark())) return p;
        return null;
    }

    private static MemSink build(Island isl, String id, Rotation rot, long seed, int ox, int oy, int oz) {
        Landmarks.Spec spec = Landmarks.spec(id);
        boolean swap = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
        int sx = swap ? spec.depth() : spec.width(), sz = swap ? spec.width() : spec.depth();
        MemSink sink = new MemSink(ox, oz, ox + sx - 1, oz + sz - 1, oy, spec.atSeaLevel());
        Plot plot = new Plot(BuildingType.LANDMARK, ox, oy, oz, sx, sz, rot, seed, id, spec.flatten());
        Landmarks.build(plot, isl, new Builder(sink, ox, oy, oz, spec.width(), spec.depth(), rot, seed));
        return sink;
    }

    private static String checkOne(WorldPlan plan, String id, List<String> problems) {
        Landmarks.Spec spec = Landmarks.spec(id);
        Island isl = host(plan, id);
        Plot real = realPlot(isl, id);
        long seed = real != null ? real.seed() : id.hashCode() * 31L;
        boolean strict = SET_PIECES.contains(id);
        // all four rotations: no exception, footprint respected, same block count
        MemSink base = null;
        for (Rotation rot : Rotation.values()) {
            MemSink m = build(isl, id, rot, seed, 0, ORIGIN_Y, 0);
            if (m.outside > 0) problems.add(rot + ": " + m.outside + " writes outside the footprint (first at local " + m.firstOutside + ")");
            if (base == null) base = m;
            else if (nonAir(m) != nonAir(base)) problems.add(rot + ": block count differs from unrotated build");
        }
        MemSink m = base;
        if (System.getenv("GL_LANDMARK_DUMP") != null) dumpBlocks(id, m);
        int nonAir = nonAir(m);
        int minBlocks = Math.max(20, spec.width() * spec.depth() / 2);
        if (nonAir < minBlocks) problems.add("only " + nonAir + " blocks (< " + minBlocks + ")");
        // determinism
        MemSink again = build(isl, id, Rotation.NONE, seed, 0, ORIGIN_Y, 0);
        if (!again.blocks.equals(m.blocks) || !sameMarkers(again.markers, m.markers)) problems.add("rebuild with the same seed differs");
        // markers must not depend on terrain reads (MarkerCollector replays with an unknown world)
        List<Object[]> replay = new ArrayList<>();
        BlockSink blind = new BlockSink() {
            @Override
            public void set(int x, int y, int z, BlockState state) {}

            @Override
            public void marker(String kind, BlockPos pos, CompoundTag data) {
                replay.add(new Object[]{kind, pos.immutable(), data.copy()});
            }

            @Override
            public void lootChest(int x, int y, int z, BlockState chest, String lootTable) {}
        };
        Plot plot = new Plot(BuildingType.LANDMARK, 0, ORIGIN_Y, 0, spec.width(), spec.depth(), Rotation.NONE, seed, id, spec.flatten());
        Landmarks.build(plot, isl, new Builder(blind, 0, ORIGIN_Y, 0, spec.width(), spec.depth(), Rotation.NONE, seed));
        if (!sameMarkers(replay, m.markers)) problems.add("markers differ when replayed without terrain");
        // markers: roles, standing room, reachability
        Reach reach = new Reach(m, spec.width(), spec.depth());
        int npcs = 0, reachable = 0;
        List<String> badRoles = new ArrayList<>(), blocked = new ArrayList<>(), unreachable = new ArrayList<>();
        for (Object[] mk : m.markers) {
            if (!"npc".equals(mk[0])) continue;
            npcs++;
            String role = ((CompoundTag) mk[2]).getString("role");
            BlockPos p = (BlockPos) mk[1];
            String at = role + "@" + p.getX() + "," + (p.getY() - ORIGIN_Y) + "," + p.getZ();
            if (!Archetypes.has(role)) badRoles.add(role);
            if (!reach.standable(p.getX(), p.getY(), p.getZ())) blocked.add(at + "[" + name(m.get(p.getX(), p.getY(), p.getZ())) + "/" + name(m.get(p.getX(), p.getY() + 1, p.getZ())) + " on " + name(m.get(p.getX(), p.getY() - 1, p.getZ())) + "]");
            else if (reach.reached(p.getX(), p.getY(), p.getZ())) reachable++;
            else {
                unreachable.add(at + " nearest walkable " + reach.nearest(p));
                debugDump(id, m, reach, p);
            }
        }
        if (!badRoles.isEmpty()) problems.add("unknown NPC roles " + badRoles);
        if (!blocked.isEmpty()) problems.add("NPC markers without floor/headroom " + blocked);
        int chests = m.chests.size();
        if (MARKER_ONLY.contains(id)) {
            if (m.markers.isEmpty()) problems.add("no markers");
        } else if (npcs + chests == 0) problems.add("no NPC marker or loot chest");
        if (strict) {
            if (chests == 0) problems.add("no loot chest");
            if (npcs == 0) problems.add("no NPC markers");
            if (!unreachable.isEmpty()) problems.add("NPCs not reachable on foot from outside " + unreachable);
            if (reach.interior < 20) problems.add("no enterable interior (" + reach.interior + " roofed walkable cells reachable)");
            List<String> boxes = containerProblems(m, reach);
            if (!boxes.isEmpty()) problems.add("loot containers a player cannot use " + boxes);
        }
        return spec.width() + "x" + spec.depth() + (spec.atSeaLevel() ? " (sea level)" : "") + ", " + nonAir + " blocks, " + npcs + " NPCs (" + reachable + " reachable), "
                + chests + " chests, " + m.markers.size() + " markers, interior " + reach.interior + " cells, 4 rotations in footprint";
    }

    /**
     * Loot containers a player can actually use: an open face (side or top) within the 4.5-block interaction range of
     * a spot the player can walk to from outside, and for chests a lid that can lift (Minecraft refuses to open a chest
     * under a solid block). Treasure chests at a "treasure" marker are exempt: they are dug up on purpose. Returns one
     * entry per container that fails, as local offsets. Barrels open whatever stands around them.
     */
    static List<String> containerProblems(MemSink m, Reach reach) {
        List<String> out = new ArrayList<>();
        List<BlockPos> dig = new ArrayList<>();
        for (Object[] mk : m.markers) if ("treasure".equals(mk[0])) dig.add((BlockPos) mk[1]);
        for (BlockPos c : m.chests) {
            // a treasure chest at a "treasure" marker is buried on purpose: story quests and maps send players to dig
            if (m.lootOf.getOrDefault(c, "").endsWith("/treasure") && dig.stream().anyMatch(t -> t.distManhattan(c) <= 2)) continue;
            BlockState st = m.get(c.getX(), c.getY(), c.getZ());
            String at = name(st) + "@" + (c.getX() - m.minX) + "," + (c.getY() - m.groundY) + "," + (c.getZ() - m.minZ);
            if (st.getBlock() instanceof ChestBlock && m.get(c.getX(), c.getY() + 1, c.getZ()).isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                out.add(at + " lid blocked by " + name(m.get(c.getX(), c.getY() + 1, c.getZ())) + " (" + m.lootOf.getOrDefault(c, "") + ")");
                continue;
            }
            boolean usable = false, open = false;
            for (Direction dir : Direction.values()) {
                if (dir == Direction.DOWN) continue;
                if (!passable(m.get(c.getX() + dir.getStepX(), c.getY() + dir.getStepY(), c.getZ() + dir.getStepZ()))) continue;
                open = true;
                // a walkable cell whose eye (1.62 above the feet) is within the 4.5-block interaction range of the
                // container and on the side its open face looks to
                for (int dx = -5; dx <= 5 && !usable; dx++)
                    for (int dz = -5; dz <= 5 && !usable; dz++)
                        for (int dy = -6; dy <= 4 && !usable; dy++) {
                            int sx = c.getX() + dx, sy = c.getY() + dy, sz = c.getZ() + dz;
                            if (!reach.reached(sx, sy, sz)) continue;
                            double ex = sx + 0.5 - (c.getX() + 0.5), ey = sy + 1.62 - (c.getY() + 0.5), ez = sz + 0.5 - (c.getZ() + 0.5);
                            double toFace = ex * dir.getStepX() + ey * dir.getStepY() + ez * dir.getStepZ();
                            double cx = Math.max(0, Math.abs(ex) - 0.5), cy = Math.max(0, Math.abs(ey) - 0.5), cz = Math.max(0, Math.abs(ez) - 0.5);
                            if (toFace > 0.5 && cx * cx + cy * cy + cz * cz <= 4.5 * 4.5) usable = true;
                        }
                if (usable) break;
            }
            String loot = m.lootOf.getOrDefault(c, "");
            if (!open) out.add(at + " sealed in on every side (" + loot + ")");
            else if (!usable) out.add(at + " out of reach of every walkable spot (" + loot + ")");
        }
        return out;
    }

    /** Writes ASCII slices around an unreachable NPC to landmark-debug.txt (server dir): '@' NPC, '*' reached, '#' solid, 'H' climbable, '.' air. */
    private static void debugDump(String id, MemSink m, Reach reach, BlockPos p) {
        StringBuilder sb = new StringBuilder();
        sb.append("== ").append(id).append(" NPC at local ").append(p.getX() - m.minX).append(',').append(p.getY() - m.groundY).append(',').append(p.getZ() - m.minZ).append('\n');
        for (int y = p.getY() + 2; y >= p.getY() - 3; y--) {
            sb.append("y=").append(y - m.groundY).append('\n');
            for (int z = p.getZ() - 6; z <= p.getZ() + 6; z++) {
                for (int x = p.getX() - 8; x <= p.getX() + 8; x++) sb.append(cell(m, reach, p, x, y, z));
                sb.append("   z=").append(z - m.minZ).append('\n');
            }
        }
        // vertical sections (x across, y down) through the NPC's row and its neighbours
        for (int z = p.getZ() - 2; z <= p.getZ() + 2; z++) {
            sb.append("section z=").append(z - m.minZ).append('\n');
            for (int y = p.getY() + 2; y >= m.groundY - 2; y--) {
                for (int x = p.getX() - 8; x <= p.getX() + 8; x++) sb.append(cell(m, reach, p, x, y, z));
                sb.append("   y=").append(y - m.groundY).append('\n');
            }
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("landmark-debug.txt"), sb.toString(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {
        }
    }

    /** Dev aid (GL_LANDMARK_DUMP=1): writes "x y z block" lines of the unrotated build to landmark-dumps/&lt;id&gt;.txt for offline previews. */
    private static void dumpBlocks(String id, MemSink m) {
        StringBuilder sb = new StringBuilder();
        for (var e : m.blocks.long2ObjectEntrySet()) {
            if (e.getValue().isAir()) continue;
            long k = e.getLongKey();
            sb.append(BlockPos.getX(k) - m.minX).append(' ').append(BlockPos.getY(k) - m.groundY).append(' ').append(BlockPos.getZ(k) - m.minZ).append(' ').append(name(e.getValue())).append('\n');
        }
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("landmark-dumps"));
            java.nio.file.Files.writeString(java.nio.file.Path.of("landmark-dumps", id + ".txt"), sb.toString());
        } catch (java.io.IOException ignored) {
        }
    }

    private static char cell(MemSink m, Reach reach, BlockPos p, int x, int y, int z) {
        BlockState s = m.get(x, y, z);
        return x == p.getX() && y == p.getY() && z == p.getZ() ? '@' : reach.reached(x, y, z) ? '*' : climb(s) ? 'H' : passable(s) ? '.' : '#';
    }

    private static int nonAir(MemSink m) {
        int n = 0;
        for (BlockState s : m.blocks.values()) if (!s.isAir()) n++;
        return n;
    }

    private static boolean sameMarkers(List<Object[]> a, List<Object[]> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i)[0].equals(b.get(i)[0]) || !a.get(i)[1].equals(b.get(i)[1]) || !a.get(i)[2].equals(b.get(i)[2])) return false;
        }
        return true;
    }

    private static String name(BlockState s) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
    }

    // ------------------------------------------------------------------ walkability

    /** Breadth-first walk from the ring just outside the footprint (feet at ground / water level). */
    static final class Reach {
        final MemSink m;
        final LongOpenHashSet seen = new LongOpenHashSet();
        final int x0, z0, x1, z1, yMin, yMax;
        int interior;

        Reach(MemSink m, int w, int d) {
            this.m = m;
            this.x0 = m.minX - 1;
            this.z0 = m.minZ - 1;
            this.x1 = m.minX + w;
            this.z1 = m.minZ + d;
            this.yMin = m.groundY - 40;
            this.yMax = Math.max(m.groundY + 4, m.maxY + 3);
            LongArrayFIFOQueue q = new LongArrayFIFOQueue();
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                    if (standable(x, m.groundY, z)) push(q, x, m.groundY, z);
                }
            while (!q.isEmpty()) {
                long p = q.dequeueLong();
                int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
                boolean inside = x >= m.minX && x <= m.maxX && z >= m.minZ && z <= m.maxZ;
                if (inside) {
                    for (int k = 2; k <= 14; k++) {
                        if (!passable(m.get(x, y + k, z))) {
                            interior++;
                            break;
                        }
                    }
                }
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    int nx = x + dir.getStepX(), nz = z + dir.getStepZ();
                    if (nx < x0 || nx > x1 || nz < z0 || nz > z1) continue;
                    // step up (jump) needs headroom above the current cell
                    if (passable(m.get(x, y + 2, z)) && standable(nx, y + 1, nz)) push(q, nx, y + 1, nz);
                    if (standable(nx, y, nz)) {
                        push(q, nx, y, nz);
                        continue;
                    }
                    if (!passable(m.get(nx, y, nz)) || !passable(m.get(nx, y + 1, nz))) continue;
                    for (int dy = 1; dy <= 3; dy++) {
                        if (!passable(m.get(nx, y - dy, nz))) break;
                        if (standable(nx, y - dy, nz)) {
                            push(q, nx, y - dy, nz);
                            break;
                        }
                    }
                }
                BlockState feet = m.get(x, y, z);
                if (climb(feet) && standable(x, y + 1, z)) push(q, x, y + 1, z);
                if (climb(m.get(x, y - 1, z)) && standable(x, y - 1, z)) push(q, x, y - 1, z);
            }
        }

        private void push(LongArrayFIFOQueue q, int x, int y, int z) {
            if (y < yMin || y > yMax) return;
            long k = BlockPos.asLong(x, y, z);
            if (seen.add(k)) q.enqueue(k);
        }

        boolean reached(int x, int y, int z) {
            return seen.contains(BlockPos.asLong(x, y, z));
        }

        /** Closest reached cell to {@code p} (for diagnostics), as local offsets. */
        String nearest(BlockPos p) {
            long best = Long.MAX_VALUE;
            int bd = Integer.MAX_VALUE;
            for (long k : seen) {
                int dd = Math.abs(BlockPos.getX(k) - p.getX()) + Math.abs(BlockPos.getY(k) - p.getY()) * 2 + Math.abs(BlockPos.getZ(k) - p.getZ());
                if (dd < bd) {
                    bd = dd;
                    best = k;
                }
            }
            return best == Long.MAX_VALUE ? "none" : (BlockPos.getX(best) - m.minX) + "," + (BlockPos.getY(best) - m.groundY) + "," + (BlockPos.getZ(best) - m.minZ);
        }

        boolean standable(int x, int y, int z) {
            BlockState f = m.get(x, y, z), h = m.get(x, y + 1, z), below = m.get(x, y - 1, z);
            if (!passable(f) || !passable(h)) return false;
            if (climb(f)) return true;
            if (below.getFluidState().is(FluidTags.LAVA) || below.getBlock() instanceof BaseFireBlock) return false;
            return !passable(below) || climb(below);
        }
    }

    static boolean climb(BlockState s) {
        return s.is(BlockTags.CLIMBABLE) || s.getFluidState().is(FluidTags.WATER);
    }

    static boolean passable(BlockState s) {
        if (s.isAir() || climb(s)) return true;
        if (s.getFluidState().is(FluidTags.LAVA) || s.getBlock() instanceof BaseFireBlock) return false;
        Block b = s.getBlock();
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) return true;
        VoxelShape shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.1875;
    }

    // ------------------------------------------------------------------ against the generated world

    private static void worldCheck(WorldPlan plan, ServerLevel level, String id, Reporter r) {
        Island isl = host(plan, id);
        Plot p = realPlot(isl, id);
        if (p == null) {
            r.check("landmark " + id + " stamped into the world", false, "no plot on " + isl.id);
            return;
        }
        Landmarks.Spec spec = Landmarks.spec(id);
        MemSink m = new MemSink(p.x(), p.z(), p.x() + p.sizeX() - 1, p.z() + p.sizeZ() - 1, p.y(), spec.atSeaLevel());
        Landmarks.build(p, isl, new Builder(m, p.x(), p.y(), p.z(), spec.width(), spec.depth(), p.rotation(), p.seed()));
        for (int cx = p.x() >> 4; cx <= (p.x() + p.sizeX() - 1) >> 4; cx++)
            for (int cz = p.z() >> 4; cz <= (p.z() + p.sizeZ() - 1) >> 4; cz++) level.getChunk(cx, cz);
        int total = 0, match = 0;
        String firstMiss = "";
        List<Long> keys = new ArrayList<>();
        for (var e : m.blocks.long2ObjectEntrySet()) {
            BlockState want = e.getValue();
            if (BlockPos.getY(e.getLongKey()) >= p.y() + 1 && !want.isAir() && want.getFluidState().isEmpty()) keys.add(e.getLongKey());
        }
        Collections.sort(keys);
        int stride = Math.max(1, keys.size() / 4000);
        for (int i = 0; i < keys.size(); i += stride) {
            long k = keys.get(i);
            BlockState want = m.blocks.get(k);
            total++;
            BlockState got = level.getBlockState(BlockPos.of(k));
            if (got.getBlock() == want.getBlock()) match++;
            else if (firstMiss.isEmpty()) firstMiss = " first miss " + BlockPos.of(k).toShortString() + " want " + name(want) + " got " + name(got);
        }
        double ratio = total == 0 ? 0 : match / (double) total;
        r.check("landmark " + id + " stamped into the world", total > 100 && ratio >= 0.9,
                String.format("%s on %s at %d,%d,%d: %d/%d sampled blocks match (%.1f%%)%s", p.rotation(), isl.id, p.x(), p.y(), p.z(), match, total, ratio * 100, ratio >= 0.9 ? "" : firstMiss));
    }

    private LandmarkCheck() {}
}
