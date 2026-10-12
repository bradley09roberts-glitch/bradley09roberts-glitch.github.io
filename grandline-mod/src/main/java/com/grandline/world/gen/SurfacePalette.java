package com.grandline.world.gen;

import com.grandline.world.IslandTheme;
import com.grandline.world.WorldPlan;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-column surface blocks for island terrain. Wide gentle slopes of a single block read as contour lines, so the
 * surface mixes theme-aware patches (coarse dirt, moss, podzol, gravel, sandstone crusts, sprinkles...) by noise,
 * roughens moderate slopes with rock, and gives steep faces horizontal rock strata. Pure function of the plan seed.
 */
public final class SurfacePalette {
    /** Slope (blocks of height per block) above which a column is a rock face. */
    public static final double STEEP = 1.6;
    /** Slope above which rock starts to show through the surface. */
    public static final double ROUGH = 0.85;

    private final SimplexNoise patch, fine;

    public SurfacePalette(long seed) {
        this.patch = new SimplexNoise(new LegacyRandomSource(seed ^ 0x5A7C4E11L));
        this.fine = new SimplexNoise(new LegacyRandomSource(seed ^ 0x0F1E2D3CL));
    }

    /** Result of {@link #surface}: the top block and whether the upper column is bare rock (strata). */
    public static final class Column {
        public BlockState top;
        public boolean rock;
    }

    /** Large patch field (about 20–40 blocks across), roughly -1.3..1.3. */
    public double patch(int x, int z) {
        return patch.getValue(x * 0.033, z * 0.033) + patch.getValue(x * 0.09 + 17.3, z * 0.09 - 5.1) * 0.35;
    }

    /** Small-scale field (3–6 blocks), roughly -1..1. */
    public double fine(int x, int z) {
        return fine.getValue(x * 0.17, z * 0.17);
    }

    /** Surface for a land column of the given theme (not beaches; those keep the theme's beach block). */
    public void surface(IslandTheme th, int x, int y, int z, double slope, Column out) {
        double pA = patch(x, z), pB = fine(x, z);
        out.rock = false;
        if (slope >= STEEP && th != IslandTheme.CLOUD) {
            out.rock = true;
            out.top = strata(th, x, y, z, pA * 2.2);
            return;
        }
        boolean rough = slope >= ROUGH;
        if (rough && th != IslandTheme.CLOUD && pB > 0.15 - (slope - ROUGH) * 0.6) {
            out.rock = true;
            out.top = strata(th, x, y, z, pA * 2.2);
            return;
        }
        out.top = flat(th, x, y, z, pA, pB, rough).defaultBlockState();
    }

    private static Block flat(IslandTheme th, int x, int y, int z, double pA, double pB, boolean rough) {
        return switch (th) {
            case TEMPERATE, FLOWER -> pA > 0.62 ? Blocks.COARSE_DIRT : pA < -0.66 && pB > -0.2 ? Blocks.MOSS_BLOCK
                    : pB > 0.78 && pA > 0.25 ? Blocks.ROOTED_DIRT : rough && pA > 0.3 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
            case MEADOW -> pA < -0.6 ? Blocks.MOSS_BLOCK : pA > 0.8 && pB > 0 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
            case FUTURE -> pA > 0.7 ? Blocks.MOSS_BLOCK : pA < -0.75 && pB > 0.2 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
            case CHERRY -> pA > 0.6 ? Blocks.COARSE_DIRT : pA < -0.7 ? Blocks.MOSS_BLOCK : pB > 0.8 ? Blocks.PODZOL : Blocks.GRASS_BLOCK;
            case TROPICAL -> pA > 0.42 ? Blocks.MOSS_BLOCK : pA < -0.72 ? Blocks.COARSE_DIRT : pB > 0.74 ? Blocks.PODZOL : Blocks.GRASS_BLOCK;
            case SAVANNA -> pA > 0.22 ? (pB > 0.55 ? Blocks.PACKED_MUD : Blocks.COARSE_DIRT) : pA < -0.75 ? Blocks.ROOTED_DIRT : Blocks.GRASS_BLOCK;
            case TAIGA -> pA > 0.3 ? Blocks.PODZOL : pA < -0.6 ? Blocks.COARSE_DIRT : pB > 0.75 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK;
            case GIANT_FOREST -> pA > 0.4 ? Blocks.GRASS_BLOCK : pA < -0.5 ? Blocks.COARSE_DIRT : pB > 0.6 ? Blocks.MOSS_BLOCK : Blocks.PODZOL;
            case DARK -> pA > 0.5 ? Blocks.COARSE_DIRT : pA < -0.6 ? Blocks.MOSS_BLOCK : pB > 0.72 ? Blocks.ROOTED_DIRT : Blocks.PODZOL;
            case MUSHROOM -> pA > 0.55 ? Blocks.PODZOL : pA < -0.7 ? Blocks.COARSE_DIRT : Blocks.MYCELIUM;
            case SWAMP -> pA > 0.35 ? Blocks.GRASS_BLOCK : pA < -0.55 ? Blocks.MOSS_BLOCK : pB > 0.75 ? Blocks.CLAY : pB < -0.75 ? Blocks.PACKED_MUD : Blocks.MUD;
            case DESERT -> pA > 0.55 || (rough && pA > 0.1) ? Blocks.SANDSTONE : pA < -0.64 ? Blocks.RED_SAND : pB > 0.84 ? Blocks.SMOOTH_SANDSTONE : Blocks.SAND;
            case BADLANDS -> pA > 0.5 ? badlandBand(x, y, z, pA) : pA < -0.6 ? Blocks.COARSE_DIRT : pB > 0.8 ? Blocks.TERRACOTTA : Blocks.RED_SAND;
            case SNOW -> pA > 0.68 ? Blocks.PACKED_ICE : pA < -0.7 && pB > 0.1 ? Blocks.STONE : Blocks.SNOW_BLOCK;
            case ICE -> pA > 0.35 ? Blocks.PACKED_ICE : pA < -0.7 && pB > 0 ? Blocks.BLUE_ICE : Blocks.SNOW_BLOCK;
            case ROCKY -> pA > 0.35 ? Blocks.ANDESITE : pA < -0.45 && !rough ? Blocks.GRASS_BLOCK : pB > 0.7 ? Blocks.GRAVEL : pB < -0.75 ? Blocks.COBBLESTONE : Blocks.STONE;
            case VOLCANIC -> pA > 0.3 ? Blocks.BASALT : pA < -0.55 ? Blocks.TUFF : pB > 0.84 && pA > 0.1 ? Blocks.MAGMA_BLOCK : pB < -0.7 ? Blocks.SMOOTH_BASALT : Blocks.BLACKSTONE;
            case CANDY -> pA > 0.55 ? Blocks.WHITE_CONCRETE_POWDER : pA < -0.6 ? Blocks.BROWN_CONCRETE_POWDER
                    : pB > 0.78 ? sprinkle(x, z) : Blocks.PINK_CONCRETE_POWDER;
            case WHITE_ROCK -> pA > 0.3 ? Blocks.DIORITE : pA < -0.55 && !rough ? Blocks.GRASS_BLOCK : pB < -0.78 ? Blocks.ANDESITE : Blocks.CALCITE;
            case CORAL -> y <= WorldPlan.SEA_LEVEL + 1 ? (pA > 0.5 ? Blocks.SANDSTONE : Blocks.SAND)
                    : pA > 0.5 ? Blocks.WHITE_CONCRETE_POWDER : pA < -0.5 ? Blocks.MOSS_BLOCK : pB > 0.76 ? Blocks.SMOOTH_SANDSTONE : Blocks.SAND;
            case CLOUD -> Blocks.WHITE_WOOL; // swapped for Island Cloud by the sky generator
        };
    }

    private static Block sprinkle(int x, int z) {
        int h = Math.floorMod(x * 734287 + z * 912931 ^ (x >> 2) * 31, 5);
        return switch (h) {
            case 0 -> Blocks.LIGHT_BLUE_CONCRETE_POWDER;
            case 1 -> Blocks.YELLOW_CONCRETE_POWDER;
            case 2 -> Blocks.LIME_CONCRETE_POWDER;
            case 3 -> Blocks.MAGENTA_CONCRETE_POWDER;
            default -> Blocks.WHITE_CONCRETE_POWDER;
        };
    }

    private static final Block[] BADLAND_BANDS = {Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
            Blocks.BROWN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.RED_TERRACOTTA};

    private static Block badlandBand(int x, int y, int z, double wobble) {
        return BADLAND_BANDS[Math.floorMod((int) Math.floor((y + wobble * 2) / 2.0), BADLAND_BANDS.length)];
    }

    /** Rock face block at height y: horizontal strata that undulate with the patch noise. */
    public BlockState strata(IslandTheme th, int x, int y, int z) {
        return strata(th, x, y, z, patch(x, z) * 2.2);
    }

    /** As {@link #strata(IslandTheme, int, int, int)} with the column's band offset precomputed. */
    public static BlockState strata(IslandTheme th, int x, int y, int z, double wobble) {
        Block[] bands = switch (th) {
            case BADLANDS -> BADLAND_BANDS;
            case DESERT -> DESERT_BANDS;
            case SNOW -> SNOW_BANDS;
            case ICE -> ICE_BANDS;
            case VOLCANIC -> VOLCANIC_BANDS;
            case CANDY -> CANDY_BANDS;
            case WHITE_ROCK, FUTURE -> WHITE_BANDS;
            case DARK -> DARK_BANDS;
            case CORAL -> CORAL_BANDS;
            case TROPICAL, GIANT_FOREST, SWAMP -> MOSSY_BANDS;
            case CHERRY, SAVANNA -> WARM_BANDS;
            case CLOUD -> CLOUD_BANDS;
            default -> STONE_BANDS;
        };
        int bandH = th == IslandTheme.CANDY ? 3 : 2;
        Block b = bands[Math.floorMod((int) Math.floor((y + wobble) / bandH), bands.length)];
        // break up the bands a little so faces are not perfectly striped
        if (th != IslandTheme.CANDY && th != IslandTheme.BADLANDS && (hash(x, y, z) & 7) == 0) b = bands[0];
        return b.defaultBlockState();
    }

    /** Band offset of a column for {@link #strata(IslandTheme, int, int, int, double)}. */
    public double wobble(int x, int z) {
        return patch(x, z) * 2.2;
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    private static final Block[] STONE_BANDS = {Blocks.STONE, Blocks.STONE, Blocks.ANDESITE, Blocks.STONE, Blocks.TUFF, Blocks.STONE, Blocks.COBBLESTONE};
    private static final Block[] MOSSY_BANDS = {Blocks.STONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE, Blocks.ANDESITE, Blocks.STONE, Blocks.TUFF};
    private static final Block[] WARM_BANDS = {Blocks.STONE, Blocks.GRANITE, Blocks.STONE, Blocks.ANDESITE, Blocks.STONE, Blocks.GRANITE, Blocks.TUFF};
    private static final Block[] DESERT_BANDS = {Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.WHITE_TERRACOTTA};
    private static final Block[] SNOW_BANDS = {Blocks.STONE, Blocks.ANDESITE, Blocks.STONE, Blocks.PACKED_ICE, Blocks.STONE, Blocks.DIORITE};
    private static final Block[] ICE_BANDS = {Blocks.PACKED_ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.STONE};
    private static final Block[] VOLCANIC_BANDS = {Blocks.BLACKSTONE, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.SMOOTH_BASALT, Blocks.TUFF, Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK};
    private static final Block[] CANDY_BANDS = {Blocks.PINK_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA};
    private static final Block[] WHITE_BANDS = {Blocks.CALCITE, Blocks.DIORITE, Blocks.CALCITE, Blocks.TUFF, Blocks.CALCITE, Blocks.ANDESITE};
    private static final Block[] DARK_BANDS = {Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF, Blocks.DEEPSLATE, Blocks.STONE};
    private static final Block[] CORAL_BANDS = {Blocks.PRISMARINE, Blocks.SANDSTONE, Blocks.DARK_PRISMARINE, Blocks.SMOOTH_SANDSTONE, Blocks.PRISMARINE};
    private static final Block[] CLOUD_BANDS = {Blocks.WHITE_WOOL};

    /** Natural terrain blocks: roads, plants and decorations may replace or sit on these. */
    public static final Set<Block> NATURAL = new HashSet<>();

    static {
        for (Block[] a : new Block[][]{STONE_BANDS, MOSSY_BANDS, WARM_BANDS, DESERT_BANDS, SNOW_BANDS, ICE_BANDS, VOLCANIC_BANDS, CANDY_BANDS, WHITE_BANDS, DARK_BANDS, CORAL_BANDS, BADLAND_BANDS})
            NATURAL.addAll(java.util.List.of(a));
        NATURAL.addAll(java.util.List.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, Blocks.MYCELIUM,
                Blocks.MUD, Blocks.PACKED_MUD, Blocks.CLAY, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.SNOW_BLOCK, Blocks.CALCITE, Blocks.DIORITE,
                Blocks.ANDESITE, Blocks.PINK_CONCRETE_POWDER, Blocks.WHITE_CONCRETE_POWDER, Blocks.BROWN_CONCRETE_POWDER, Blocks.LIGHT_BLUE_CONCRETE_POWDER,
                Blocks.YELLOW_CONCRETE_POWDER, Blocks.LIME_CONCRETE_POWDER, Blocks.MAGENTA_CONCRETE_POWDER, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE));
    }

    public static boolean natural(BlockState s) {
        return s != null && (NATURAL.contains(s.getBlock()) || s.is(com.grandline.registry.ModBlocks.ISLAND_CLOUD.get()));
    }
}
