package com.grandline.test;

import com.grandline.world.DeepWorld;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.islands.CanonIslands;
import com.grandline.world.settlement.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

/**
 * Self-test checks for island terrain polish (surface variety, decoration, flat pads, unchanged beaches), the canon
 * islands added later (present, land, landmark blocks) and chunk generation speed. Runs first in phase 1 so the timed
 * chunks are freshly generated.
 */
final class WorldPolishTests {
    private static final int SEA = WorldPlan.SEA_LEVEL;

    /** Added canon island → (landmark id, signature block the landmark builder places, minimum count). */
    private static final Object[][] LANDMARKS = {
            {"tequila_wolf", "endless_bridge", Blocks.POLISHED_ANDESITE, 100},
            {"germa_kingdom", "germa_fleet", Blocks.BLUE_CONCRETE, 30},
            {"rommel_kingdom", "rommel_keep", Blocks.STONE_BRICK_WALL, 20},
            {"oykot_kingdom", "oykot_palace", Blocks.QUARTZ_PILLAR, 20},
            {"god_valley", "god_valley_ruins", Blocks.CHISELED_STONE_BRICKS, 8},
            {"kyuka_island", "kyuka_resort", Blocks.WHITE_TERRACOTTA, 50},
            {"lulusia_kingdom", "lulusia_memorial", Blocks.POLISHED_DEEPSLATE, 8},
            {"prodence_kingdom", "prodence_castle", Blocks.TARGET, 3},
            {"karai_bari_island", "mercenary_guild_hall", Blocks.RED_WOOL, 30},
            {"mogaro_kingdom", "mogaro_royal_gate", Blocks.GOLD_BLOCK, 10},
    };

    static void phase1(Deque<SelfTest.Step> steps) {
        // generation speed: fresh 5x5-chunk areas on three busy islands
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            if (p == null) return 0;
            ServerLevel l = s.overworld();
            String[][] areas = {{"dawn_island", "-250", "150"}, {"alabasta", "400", "400"}, {"whole_cake_island", "300", "0"}};
            long total = 0;
            int chunks = 0;
            long fill0 = com.grandline.world.gen.ArchipelagoChunkGenerator.FILL_NANOS.sum(), fillN0 = com.grandline.world.gen.ArchipelagoChunkGenerator.FILL_CHUNKS.sum();
            long dec0 = com.grandline.world.gen.ArchipelagoChunkGenerator.DECOR_NANOS.sum(), decN0 = com.grandline.world.gen.ArchipelagoChunkGenerator.DECOR_CHUNKS.sum();
            StringBuilder per = new StringBuilder();
            for (String[] a : areas) {
                Island i = p.byId(a[0]);
                if (i == null) continue;
                int cx = (i.x + Integer.parseInt(a[1])) >> 4, cz = (i.z + Integer.parseInt(a[2])) >> 4;
                long t0 = System.nanoTime();
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) l.getChunk(cx + dx, cz + dz);
                long dt = System.nanoTime() - t0;
                total += dt;
                chunks += 25;
                per.append(String.format(" %s %.1f", a[0], dt / 25e6));
            }
            double ms = chunks == 0 ? 0 : total / (chunks * 1e6);
            long fillN = com.grandline.world.gen.ArchipelagoChunkGenerator.FILL_CHUNKS.sum() - fillN0, decN = com.grandline.world.gen.ArchipelagoChunkGenerator.DECOR_CHUNKS.sum() - decN0;
            double fillMs = fillN == 0 ? 0 : (com.grandline.world.gen.ArchipelagoChunkGenerator.FILL_NANOS.sum() - fill0) / (fillN * 1e6);
            double decMs = decN == 0 ? 0 : (com.grandline.world.gen.ArchipelagoChunkGenerator.DECOR_NANOS.sum() - dec0) / (decN * 1e6);
            // wall time depends on machine load; the CPU time of our own terrain and decoration stages is the stable measure
            SelfTest.check("island chunks generate quickly", chunks > 0 && ms < 250 && fillMs + decMs < 15,
                    String.format("%.1f ms/chunk wall over %d fresh chunks (incl. neighbours);%s | CPU per chunk: terrain fill %.2f ms (%d chunks), island decoration %.2f ms (%d chunks)",
                            ms, chunks, per, fillMs, fillN, decMs, decN));
            return 0;
        });
        // surface variety: wide slopes are no longer a single block
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            if (p == null) return 0;
            ServerLevel l = s.overworld();
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            long total = 0;
            for (String[] a : new String[][]{{"dawn_island", "-250", "150"}, {"alabasta", "400", "400"}, {"marineford", "-200", "180"}}) {
                Island i = p.byId(a[0]);
                int x0 = i.x + Integer.parseInt(a[1]) - 32, z0 = i.z + Integer.parseInt(a[2]) - 32;
                Map<Block, Integer> tops = new HashMap<>();
                for (int x = x0; x < x0 + 64; x += 2)
                    for (int z = z0; z < z0 + 64; z += 2) {
                        l.getChunk(x >> 4, z >> 4);
                        int y = l.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                        if (y < SEA) continue;
                        Block top = l.getBlockState(new BlockPos(x, y, z)).getBlock();
                        if (com.grandline.world.gen.SurfacePalette.NATURAL.contains(top)) tops.merge(top, 1, Integer::sum);
                    }
                long kinds = tops.values().stream().filter(c -> c >= 3).count();
                ok &= kinds >= 2;
                total += kinds;
                sb.append(' ').append(a[0]).append('=').append(kinds);
            }
            SelfTest.check("island surfaces mix several ground blocks", ok && total >= 7, "kinds of natural top block (3+ columns):" + sb);
            return 0;
        });
        // pads stay flat and beaches/coasts are untouched by the small-scale relief
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            if (p == null) return 0;
            int padCells = 0, padBad = 0, beachCells = 0, beachBad = 0;
            for (String id : List.of("shiokaze_isle", "loguetown", "cocoyasi", "alabasta", "kyuka_island", "oykot_kingdom")) {
                Island i = p.byId(id);
                List<Plot> pads = new ArrayList<>(i.settlement().plots);
                pads.addAll(i.settlement().secrets);
                for (Plot pl : i.settlement().plots) {
                    if (!pl.flatten()) continue;
                    for (int x = pl.x(); x < pl.x() + pl.sizeX(); x += 3)
                        for (int z = pl.z(); z < pl.z() + pl.sizeZ(); z += 3) {
                            // cells within another pad's blend margin are shared between pads (unchanged behaviour)
                            final int fx = x, fz = z;
                            if (pads.stream().anyMatch(q -> q != pl && q.flatten() && q.outsideDistance(fx, fz) <= 4)) continue;
                            padCells++;
                            if (Math.abs(i.height(x, z) - (pl.y() - 1)) > 1e-6) padBad++;
                        }
                }
                Random r = new Random(id.hashCode());
                for (int k = 0; k < 4000; k++) {
                    int x = i.x + r.nextInt(i.radius * 3) - i.radius * 3 / 2, z = i.z + r.nextInt(i.radius * 3) - i.radius * 3 / 2;
                    double nat = i.naturalHeight(x, z);
                    if (Double.isNaN(nat) || nat > SEA + 2.5) continue;
                    if (i.settlement().bounds(x, z) || !i.settlement().secrets.isEmpty() && near(i.settlement().secrets, x, z)) continue;
                    beachCells++;
                    if (Math.abs(i.height(x, z) - nat) > 1e-9) beachBad++;
                }
            }
            SelfTest.check("settlement pads stay flat", padCells > 100 && padBad == 0, padBad + "/" + padCells + " pad cells off level");
            SelfTest.check("beaches and coasts keep their height", beachCells > 1000 && beachBad == 0, beachBad + "/" + beachCells + " low coastal cells changed");
            return 0;
        });
        // the added canon islands
        steps.add(s -> {
            WorldPlan p = WorldPlan.of(s);
            if (p == null) return 0;
            List<String> missing = new ArrayList<>();
            for (var spec : CanonIslands.additions()) if (p.byId(spec.id) == null || !p.byId(spec.id).canon) missing.add(spec.id);
            SelfTest.check("added canon islands present", missing.isEmpty(), CanonIslands.additions().size() + " added" + (missing.isEmpty() ? "" : ", missing " + missing));
            List<String> crowded = new ArrayList<>();
            for (var a : CanonIslands.additions())
                for (Island o : p.islands)
                    if (!o.canon && Math.hypot(a.x - o.x, a.z - o.z) < a.radius + o.radius + 800) crowded.add(a.id + "~" + o.id);
            SelfTest.check("original islands keep clear of added canon islands", crowded.isEmpty(), crowded.isEmpty() ? p.islands.size() + " islands in plan" : String.join(", ", crowded));
            // existing worlds unchanged: the core roster keeps its plan order, so its island seeds (terrain noise, towns)
            List<String> moved = new ArrayList<>();
            List<com.grandline.world.IslandSpec> core = CanonIslands.core();
            for (int k = 0; k < core.size(); k++) {
                Island i = p.byId(core.get(k).id);
                long want = p.seed * 31 + (core.get(k).id.hashCode() * 0x9E3779B97F4A7C15L) + k;
                if (i == null || i.seed != want || p.islands.indexOf(i) != k) moved.add(core.get(k).id);
            }
            SelfTest.check("core islands keep their plan order and seeds", moved.isEmpty(), moved.isEmpty() ? core.size() + " core canon islands" : "changed: " + moved);
            return 0;
        });
        for (Object[] lm : LANDMARKS) {
            String id = (String) lm[0];
            steps.add(s -> {
                WorldPlan p = WorldPlan.of(s);
                Island i = p == null ? null : p.byId(id);
                if (i == null) {
                    SelfTest.check("added island " + id, false, "missing");
                    return 0;
                }
                ServerLevel l = s.overworld();
                if (i.shape == com.grandline.world.IslandShape.SEA) {
                    l.getChunk(i.x >> 4, i.z >> 4);
                    SelfTest.check("added island " + id + " rides on the open sea", l.getBlockState(new BlockPos(i.x - 25, SEA, i.z - 25)).is(Blocks.WATER), "");
                } else if (i.shape == com.grandline.world.IslandShape.BLAST) {
                    int centre = surface(l, i.x, i.z);
                    int rim = surface(l, i.x - (int) (i.radius * 0.66), i.z);
                    int edge = surface(l, i.x, i.z + (int) (i.radius * 0.58));
                    SelfTest.check("added island " + id + " is a sea-filled crater with a sheer rim", centre < SEA - 10 && rim > SEA + 8 && edge < SEA - 10
                            && l.getBlockState(new BlockPos(i.x, SEA, i.z)).is(Blocks.WATER), "centre y=" + centre + " rim y=" + rim + " inside wall y=" + edge);
                } else {
                    int y = surface(l, i.x, i.z);
                    SelfTest.check("added island " + id + " has land at its centre", y > SEA, "surface y=" + y + " block=" + l.getBlockState(new BlockPos(i.x, y - 1, i.z)).getBlock().getName().getString());
                }
                String lmId = (String) lm[1];
                Plot pl = i.settlement().plots.stream().filter(q -> lmId.equals(q.landmark())).findFirst().orElse(null);
                if (pl == null) {
                    SelfTest.check("landmark " + lmId + " placed", false, "no plot");
                    return 0;
                }
                Block sig = (Block) lm[2];
                int count = 0;
                BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
                for (int x = pl.x(); x < pl.x() + pl.sizeX(); x++)
                    for (int z = pl.z(); z < pl.z() + pl.sizeZ(); z++) {
                        if (((x | z) & 15) == 0 || (x == pl.x() && z == pl.z())) l.getChunk(x >> 4, z >> 4);
                        for (int y = pl.y() - 6; y <= pl.y() + 34; y++) if (l.getBlockState(mp.set(x, y, z)).is(sig)) count++;
                    }
                SelfTest.check("landmark " + lmId + " built on " + id, count >= (Integer) lm[3], count + " " + sig.getName().getString() + " blocks");
                return 0;
            });
        }
        // Fish-Man Island is no longer plain sand: coral gardens, kelp-like greenery and lamps under the bubble
        steps.add(s -> {
            ServerLevel deep = s.getLevel(DeepWorld.LEVEL);
            if (deep == null) {
                SelfTest.check("Fish-Man Island decorated", false, "no deep-sea level");
                return 0;
            }
            Map<String, Integer> seen = new TreeMap<>();
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int cx = -12; cx <= 11; cx += 3)
                for (int cz = -12; cz <= 11; cz += 3) {
                    var ch = deep.getChunk(cx, cz);
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++)
                            for (int y = 64; y < 140; y++) {
                                BlockState st = ch.getBlockState(mp.set(cx * 16 + x, y, cz * 16 + z));
                                String k = st.is(Blocks.TWISTING_VINES_PLANT) ? "vines" : st.is(Blocks.PEARLESCENT_FROGLIGHT) || st.is(Blocks.OCHRE_FROGLIGHT) || st.is(Blocks.VERDANT_FROGLIGHT) ? "coral_tips"
                                        : st.is(Blocks.SEA_PICKLE) || st.is(Blocks.KELP) ? "pools" : st.is(Blocks.PRISMARINE_WALL) ? "lamps" : st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.WHITE_CONCRETE_POWDER) ? "ground" : null;
                                if (k != null) seen.merge(k, 1, Integer::sum);
                            }
                }
            SelfTest.check("Fish-Man Island has coral gardens, greenery and lamps", seen.size() >= 3, seen.toString());
            return 0;
        });
    }

    private static boolean near(List<Plot> plots, int x, int z) {
        for (Plot pl : plots) if (pl.contains(x, z, 6)) return true;
        return false;
    }

    private static int surface(ServerLevel l, int x, int z) {
        l.getChunk(x >> 4, z >> 4);
        return l.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
    }

    private WorldPolishTests() {}
}
