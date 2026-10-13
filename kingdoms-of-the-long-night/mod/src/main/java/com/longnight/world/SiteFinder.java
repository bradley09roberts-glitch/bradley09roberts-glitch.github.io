package com.longnight.world;

import com.longnight.Config;
import com.longnight.LongNight;
import com.longnight.content.Content;
import com.longnight.content.Culture;
import com.longnight.sim.WorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Surveys terrain without generating chunks (noise heights and biomes only) to find places
 * people would actually settle: fairly level ground, near water, with a biome suited to a culture.
 */
public final class SiteFinder {
    private SiteFinder() {}

    public static List<WorldBuilder.Site> survey(ServerLevel level, BlockPos origin, long seed) {
        long t0 = System.currentTimeMillis();
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        int sea = gen.getSeaLevel();
        int maxR = Config.WORLD_RADIUS.get();
        int minR = Config.SAFE_SPAWN_RADIUS.get();
        Random r = new Random(seed ^ 0x51735L);
        List<WorldBuilder.Site> out = new ArrayList<>();
        // a sea-heavy world may offer few dry, level sites near spawn: then look further out, ring by ring
        int needed = 2 * (Config.START_SETTLEMENTS.get() + Config.START_CAMPS.get() + Config.START_BANDIT_CAMPS.get() + 3);
        int inner = minR, outer = maxR, rounds = 0;
        while (rounds < 4) {
            rounds++;
            sample(level, gen, rs, sea, origin, inner, outer, r, out);
            if (out.size() >= needed) break;
            inner = outer;
            outer = outer * 3 / 2;
        }
        LongNight.LOG.info("Surveyed {} candidate sites within {} blocks ({} round(s)) in {} ms", out.size(), outer, rounds,
                System.currentTimeMillis() - t0);
        return out;
    }

    static void sample(ServerLevel level, ChunkGenerator gen, RandomState rs, int sea, BlockPos origin, int inner, int outer, Random r,
                       List<WorldBuilder.Site> out) {
        int samples = 150;
        for (int i = 0; i < samples; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt((double) inner * inner + r.nextDouble() * ((double) outer * outer - (double) inner * inner)); // even over the ring's area
            int x = origin.getX() + (int) (Math.cos(ang) * dist);
            int z = origin.getZ() + (int) (Math.sin(ang) * dist);
            int[][] offs = {{0, 0}, {-28, -28}, {28, -28}, {-28, 28}, {28, 28}};
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, centre = 0;
            for (int o = 0; o < offs.length; o++) {
                int h = gen.getBaseHeight(x + offs[o][0], z + offs[o][1], Heightmap.Types.WORLD_SURFACE_WG, level, rs);
                if (o == 0) centre = h;
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
            if (centre <= sea + 1) continue;                  // in water
            int spread = max - min;
            Holder<Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(centre), QuartPos.fromBlock(z));
            String bid = biome.unwrapKey().map(key -> key.location().getPath()).orElse("plains");
            boolean water = bid.contains("river") || bid.contains("beach") || bid.contains("swamp") || bid.contains("shore");
            int[][] wo = {{-56, 0}, {56, 0}, {0, -56}, {0, 56}};
            for (int o = 0; o < wo.length && !water; o++) {
                int sx = x + wo[o][0], sz = z + wo[o][1];
                if (gen.getBaseHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, level, rs) <= sea + 1) water = true;
            }
            boolean hills = centre > sea + 30 || spread > 10;
            boolean forest = bid.contains("forest") || bid.contains("taiga") || bid.contains("grove") || bid.contains("jungle");
            String culture = bestCulture(bid, hills, water, r);
            double flat = Math.max(0, 1.0 - spread / 14.0);
            if (spread > (culture.equals("dunmar") ? 22 : 14)) continue;
            if (bid.contains("ocean") || bid.contains("river") && spread > 6) continue;
            double quality = flat * 2 + (water ? 0.8 : 0) + (forest ? 0.2 : 0) + r.nextDouble() * 0.5;
            out.add(new WorldBuilder.Site(new BlockPos(x, centre, z), culture, water, forest, hills, quality));
        }
    }

    static String bestCulture(String biome, boolean hills, boolean water, Random r) {
        String best = "valemen";
        double bestScore = -1;
        for (Culture c : Content.CULTURES.values()) {
            double score = 0;
            for (String k : c.biomeKeywords) if (biome.contains(k)) score += 1;
            for (String k : c.avoidBiomeKeywords) if (biome.contains(k)) score -= 2;
            if (c.id.equals("dunmar") && hills) score += 1.5;
            if (c.id.equals("tidefolk") && water) score += 0.8;
            if (c.id.equals("skrell")) score -= 0.8; // goblin tribes are rarer settlers
            score += r.nextDouble() * 0.9;
            if (score > bestScore) {
                bestScore = score;
                best = c.id;
            }
        }
        return best;
    }
}
