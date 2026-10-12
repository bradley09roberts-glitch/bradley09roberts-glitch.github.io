package com.grandline.test;

import com.grandline.GrandLine;
import com.grandline.ship.ShipClass;
import com.grandline.ship.ShipInteriors;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.build.BlockSink;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.landmark.Landmarks;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Buildings;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Dev aid (env GL_SELFTEST=structures): writes every structure the mod generates to {@code structdump/*.json} in the
 * server directory, for tools/render_structure.py (isometric views and floor cut-aways) and the structure audit.
 * Landmarks are built for their host island, settlement buildings for every architectural style, island secrets and
 * ship interiors once each; with GL_DUMP_TOWNS=1 a few real generated towns are also read back from the world
 * (terrain, roads, docks and all). Each file holds a block palette, the blocks relative to the footprint origin
 * (y = 0 is the floor level), markers and loot chests.
 */
public final class StructureDump {
    private static final int GROUND = 80;

    /** In-memory sink over flat ground (grass on dirt) or open sea. */
    static final class Sink implements BlockSink {
        final boolean sea;
        final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        final List<String> markers = new ArrayList<>();
        final List<String> chests = new ArrayList<>();
        final int ox, oz;

        Sink(int ox, int oz, boolean sea) {
            this.ox = ox;
            this.oz = oz;
            this.sea = sea;
        }

        BlockState terrain(int y) {
            if (sea) return y < GROUND - 8 ? Blocks.STONE.defaultBlockState() : y < GROUND ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            return y < GROUND - 1 ? Blocks.DIRT.defaultBlockState() : y < GROUND ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public void set(int x, int y, int z, BlockState s) {
            blocks.put(BlockPos.asLong(x, y, z), s);
        }

        @Override
        public BlockState get(int x, int y, int z) {
            BlockState s = blocks.get(BlockPos.asLong(x, y, z));
            return s != null ? s : terrain(y);
        }

        @Override
        public void marker(String kind, BlockPos pos, CompoundTag data) {
            markers.add("{\"kind\":" + q(kind) + ",\"pos\":[" + (pos.getX() - ox) + "," + (pos.getY() - GROUND) + "," + (pos.getZ() - oz) + "],\"data\":" + q(data.toString()) + "}");
        }

        @Override
        public void lootChest(int x, int y, int z, BlockState chest, String lootTable) {
            set(x, y, z, chest);
            chests.add("{\"pos\":[" + (x - ox) + "," + (y - GROUND) + "," + (z - oz) + "],\"loot\":" + q(lootTable) + "}");
        }
    }

    public static void steps(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            run(s);
            return 0;
        });
    }

    static void run(MinecraftServer server) throws IOException {
        Path dir = Path.of("structdump");
        Files.createDirectories(dir);
        WorldPlan plan = WorldPlan.of(server);
        String only = System.getenv("GL_DUMP_ONLY"); // comma-separated id prefixes, e.g. "tavern,alubarna_palace"
        java.util.function.Predicate<String> wanted = id -> only == null || only.isBlank()
                || Arrays.stream(only.split(",")).anyMatch(o -> !o.isBlank() && id.startsWith(o.trim()));
        int n = 0;
        // landmarks, for the island that hosts them
        for (String id : Landmarks.ids()) {
            if (!wanted.test(id)) continue;
            Island host = null;
            Plot real = null;
            for (Island isl : plan.islands)
                for (Island.LandmarkPlacement lp : isl.landmarks)
                    if (lp.id().equals(id) && host == null) host = isl;
            if (host == null) host = plan.islands.get(0);
            for (Plot p : host.settlement().plots) if (id.equals(p.landmark())) real = p;
            Landmarks.Spec spec = Landmarks.spec(id);
            long seed = real != null ? real.seed() : id.hashCode() * 31L;
            Sink sink = new Sink(0, 0, spec.atSeaLevel());
            Plot plot = new Plot(BuildingType.LANDMARK, 0, GROUND, 0, spec.width(), spec.depth(), Rotation.NONE, seed, id, spec.flatten());
            try {
                Landmarks.build(plot, host, new Builder(sink, 0, GROUND, 0, spec.width(), spec.depth(), Rotation.NONE, seed));
            } catch (Throwable t) {
                GrandLine.LOGGER.error("[DUMP] landmark {} failed", id, t);
                continue;
            }
            write(dir, "landmark", id, host.id + " (" + host.style.name().toLowerCase(Locale.ROOT) + ")", spec.width(), spec.depth(), spec.atSeaLevel(), sink);
            n++;
        }
        // settlement buildings, every type in every style
        for (BuildingType t : BuildingType.values()) {
            if (t == BuildingType.LANDMARK) continue;
            boolean secret = t == BuildingType.BURIED_TREASURE || t == BuildingType.HIDDEN_CACHE || t == BuildingType.LOST_SHRINE || t == BuildingType.SHIPWRECK;
            for (BuildStyle st : BuildStyle.values()) {
                if (secret && st != BuildStyle.VILLAGE) continue;
                Sink sink = new Sink(0, 0, t == BuildingType.SHIPWRECK);
                long seed = 0x5EEDL + t.ordinal() * 131L + st.ordinal();
                Plot plot = new Plot(t, 0, GROUND, 0, t.w, t.d, Rotation.NONE, seed, "", true);
                try {
                    Buildings.build(plot, st, new Builder(sink, 0, GROUND, 0, t.w, t.d, Rotation.NONE, seed));
                } catch (Throwable ex) {
                    GrandLine.LOGGER.error("[DUMP] building {} {} failed", t, st, ex);
                    continue;
                }
                String id = t.name().toLowerCase(Locale.ROOT) + (secret ? "" : "__" + st.name().toLowerCase(Locale.ROOT));
                if (!wanted.test(id)) continue;
                write(dir, secret ? "secret" : "building", id, st.name().toLowerCase(Locale.ROOT), t.w, t.d, t == BuildingType.SHIPWRECK, sink);
                n++;
            }
        }
        // ship interiors
        for (ShipClass c : ShipClass.values()) {
            if (c == ShipClass.ROWBOAT || !wanted.test("ship_" + c.name().toLowerCase(Locale.ROOT))) continue;
            Sink sink = new Sink(0, 0, false);
            ShipInteriors.build(sink, new BlockPos(0, GROUND + 1, 0), c);
            write(dir, "interior", "ship_" + c.name().toLowerCase(Locale.ROOT), "", 0, 0, false, sink);
            n++;
        }
        GrandLine.LOGGER.info("[DUMP] wrote {} structures to {}", n, dir.toAbsolutePath());
        SelfTest.check("structure dump", n > 0, n + " structures");
        if (System.getenv("GL_DUMP_TOWNS") != null) towns(server, plan, dir);
    }

    /** Real generated towns read back from the world: one per architectural style that has one. */
    private static void towns(MinecraftServer server, WorldPlan plan, Path dir) throws IOException {
        ServerLevel l = server.overworld();
        Set<BuildStyle> done = EnumSet.noneOf(BuildStyle.class);
        String only = System.getenv("GL_DUMP_TOWNS");
        for (Island isl : plan.islands) {
            Settlement st = isl.settlement();
            if (st.plots.size() < 6 || !st.hasCenter) continue;
            if (only.length() > 1 ? !Arrays.asList(only.split(",")).contains(isl.id) : !done.add(isl.style)) continue;
            int x0 = st.minX - 4, z0 = st.minZ - 4, x1 = st.maxX + 4, z1 = st.maxZ + 4;
            if ((long) (x1 - x0) * (z1 - z0) > 260L * 260L) continue;
            for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
                for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) l.getChunk(cx, cz);
            Sink sink = new Sink(x0, z0, false);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            int lo = WorldPlan.SEA_LEVEL - 6;
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    int top = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                    for (int y = lo; y <= top; y++) {
                        BlockState s = l.getBlockState(m.set(x, y, z));
                        if (!s.isAir()) sink.set(x, y - WorldPlan.SEA_LEVEL + GROUND, z, s);
                    }
                }
            write(dir, "town", "town_" + isl.id, isl.id + " (" + isl.style.name().toLowerCase(Locale.ROOT) + ", " + isl.settlementType + ")", x1 - x0 + 1, z1 - z0 + 1, false, sink);
            GrandLine.LOGGER.info("[DUMP] town {}", isl.id);
        }
    }

    private static void write(Path dir, String kind, String id, String note, int w, int d, boolean sea, Sink sink) throws IOException {
        Map<BlockState, Integer> pal = new LinkedHashMap<>();
        StringBuilder blocks = new StringBuilder();
        int n = 0;
        for (var e : sink.blocks.long2ObjectEntrySet()) {
            BlockState s = e.getValue();
            if (s.isAir()) continue;
            long k = e.getLongKey();
            int pi = pal.computeIfAbsent(s, x -> pal.size());
            if (n++ > 0) blocks.append(',');
            blocks.append(BlockPos.getX(k) - sink.ox).append(',').append(BlockPos.getY(k) - GROUND).append(',').append(BlockPos.getZ(k) - sink.oz).append(',').append(pi);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":").append(q(id)).append(",\"kind\":").append(q(kind)).append(",\"note\":").append(q(note))
                .append(",\"w\":").append(w).append(",\"d\":").append(d).append(",\"sea\":").append(sea).append(",\"palette\":[");
        int i = 0;
        for (BlockState s : pal.keySet()) sb.append(i++ > 0 ? "," : "").append(q(BlockStateParser.serialize(s)));
        sb.append("],\"blocks\":[").append(blocks).append("],\"markers\":[").append(String.join(",", sink.markers))
                .append("],\"chests\":[").append(String.join(",", sink.chests)).append("]}\n");
        Files.writeString(dir.resolve(id + ".json"), sb.toString());
    }

    private static String q(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private StructureDump() {}
}
