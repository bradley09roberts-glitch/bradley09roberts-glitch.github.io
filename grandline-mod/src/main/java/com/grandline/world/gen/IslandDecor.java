package com.grandline.world.gen;

import com.grandline.registry.ModBlocks;
import com.grandline.world.DeepWorld;
import com.grandline.world.Island;
import com.grandline.world.IslandTheme;
import com.grandline.world.WorldPlan;
import com.grandline.world.build.BlockSink;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Theme-aware scattered island features (palms, boulders, rock outcrops, fallen logs, flowers, berry bushes, ruin bits,
 * cacti, ice spikes, basalt columns, candy, coral gardens, lamps...), placed during chunk decoration.
 * <p>
 * Features sit on a fixed 8×8 cell grid; each cell's feature is a pure function of the plan seed and the terrain, so
 * every chunk draws the part of each nearby feature that falls inside itself (writes outside the chunk are dropped by
 * the sink) and features join up seamlessly across chunk borders. Random numbers are always drawn before looking at
 * blocks so all chunks see the same sequence. Settlement plots, roads and docks are kept clear.
 */
public final class IslandDecor {
    private static final int CELL = 8;
    /** Largest horizontal extent of any feature from its cell point. */
    private static final int REACH = 7;
    private static final int SEA = WorldPlan.SEA_LEVEL;

    private enum Feat {
        NONE(false, false), BOULDER(true, false), OUTCROP(true, false), PALM(true, false), LOG(true, true), DRIFTWOOD(true, true),
        FLOWERS(false, false), BERRIES(false, false), BUSH(false, false), RUIN(true, false), CACTI(false, false), DEAD_BUSHES(false, false),
        ICE_SPIKE(true, false), BASALT(true, false), DEAD_TREE(true, false), LOLLIPOP(true, false), CANE(true, false), GUMDROP(true, false),
        CORAL_POOL(true, true), CORAL_BUSH(false, false), KELP(false, false), LAMP(false, false), PUFF(true, false), MUSHROOMS(false, false),
        PUDDLE(true, true), STONE_LANTERN(false, false), GRAVES(true, false), MELONS(false, false), MAGMA_VENT(true, true);

        /** Big features stay out of town; flat features need level ground. */
        final boolean big, flat;

        Feat(boolean big, boolean flat) {
            this.big = big;
            this.flat = flat;
        }
    }

    public static void decorate(WorldPlan plan, BlockSink sink, int minX, int minZ) {
        int maxX = minX + 15, maxZ = minZ + 15;
        WorldPlan.Sample s = new WorldPlan.Sample(), n = new WorldPlan.Sample();
        long salt = plan.seed * 0x9E3779B97F4A7C15L + 0xDEC0L;
        for (int gx = Math.floorDiv(minX - REACH, CELL); gx <= Math.floorDiv(maxX + REACH, CELL); gx++)
            for (int gz = Math.floorDiv(minZ - REACH, CELL); gz <= Math.floorDiv(maxZ + REACH, CELL); gz++) {
                long h = mix(salt, gx, gz);
                int px = gx * CELL + (int) (h & 7), pz = gz * CELL + (int) ((h >>> 3) & 7);
                if (px < minX - REACH || px > maxX + REACH || pz < minZ - REACH || pz > maxZ + REACH) continue;
                Rnd r = new Rnd(h);
                double roll = r.nextDouble();
                if (roll > 0.6) continue; // no table goes beyond this
                plan.sample(px, pz, s);
                Island isl = s.island;
                if (isl == null || s.redLine || s.canal || s.height < SEA) continue;
                int y = s.height;
                if (plan.kind == WorldPlan.Kind.DEEP && !DeepWorld.inAnyDome(px, y + 1, pz)) continue;
                IslandTheme th = isl.themeAt(px, pz);
                double t = isl.t(px, pz);
                boolean beach = th == IslandTheme.CLOUD ? t > 0.8 : t > 0.84 && y <= SEA + 3;
                // most cells hold nothing: only measure the slope when either table could pick a feature
                if (choose(th, beach, 0, roll) == Feat.NONE && choose(th, beach, SurfacePalette.STEEP, roll) == Feat.NONE) continue;
                double slope = 0;
                for (int[] d : NEIGHBOURS) {
                    plan.sample(px + d[0], pz + d[1], n);
                    slope = Math.max(slope, Math.abs(n.exact - s.exact));
                }
                Feat f = choose(th, beach, slope, roll);
                if (f == Feat.NONE) continue;
                if (f.flat && slope > 0.6) f = slope < 1.2 && th != IslandTheme.CLOUD && th != IslandTheme.CANDY ? Feat.BOULDER : Feat.NONE;
                if (f == Feat.NONE) continue;
                Settlement st = isl.settlement();
                if (f.big && st.bounds(px, pz)) continue;
                if (nearBuilt(st, px, pz, f.big ? REACH : 3)) continue;
                place(f, sink, px, y, pz, th, r);
            }
    }

    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // ------------------------------------------------------------------ choice tables

    private static Feat choose(IslandTheme th, boolean beach, double slope, double roll) {
        if (slope >= SurfacePalette.STEEP) {
            return switch (th) {
                case CLOUD -> pick(roll, Feat.PUFF, 0.18);
                case CANDY -> pick(roll, Feat.GUMDROP, 0.08);
                case ICE -> pick(roll, Feat.ICE_SPIKE, 0.06, Feat.BOULDER, 0.06);
                case CORAL -> pick(roll, Feat.CORAL_BUSH, 0.08, Feat.OUTCROP, 0.06);
                default -> pick(roll, Feat.OUTCROP, 0.1, Feat.BOULDER, 0.07);
            };
        }
        if (beach) {
            return switch (th) {
                case TROPICAL, SAVANNA, CORAL -> pick(roll, Feat.PALM, 0.3, Feat.BOULDER, 0.05, Feat.DRIFTWOOD, 0.04);
                case CLOUD -> pick(roll, Feat.PALM, 0.16, Feat.PUFF, 0.2);
                case DESERT -> pick(roll, Feat.PALM, 0.08, Feat.BOULDER, 0.06, Feat.DRIFTWOOD, 0.03);
                case TEMPERATE, FLOWER -> pick(roll, Feat.BOULDER, 0.07, Feat.DRIFTWOOD, 0.05, Feat.PALM, 0.04);
                case SNOW, ICE, TAIGA, ROCKY, DARK, VOLCANIC, WHITE_ROCK -> pick(roll, Feat.BOULDER, 0.1, Feat.DRIFTWOOD, 0.05);
                default -> pick(roll, Feat.BOULDER, 0.07, Feat.DRIFTWOOD, 0.05);
            };
        }
        return switch (th) {
            case TEMPERATE -> pick(roll, Feat.FLOWERS, 0.14, Feat.BOULDER, 0.07, Feat.LOG, 0.05, Feat.BERRIES, 0.04, Feat.BUSH, 0.05, Feat.OUTCROP, 0.03, Feat.RUIN, 0.012);
            case MEADOW, FLOWER -> pick(roll, Feat.FLOWERS, 0.22, Feat.BOULDER, 0.06, Feat.BUSH, 0.05, Feat.LOG, 0.03, Feat.OUTCROP, 0.02, Feat.RUIN, 0.01);
            case TROPICAL -> pick(roll, Feat.PALM, 0.07, Feat.BUSH, 0.08, Feat.FLOWERS, 0.06, Feat.BOULDER, 0.05, Feat.LOG, 0.05, Feat.MELONS, 0.025, Feat.RUIN, 0.015);
            case SAVANNA -> pick(roll, Feat.BOULDER, 0.07, Feat.BUSH, 0.08, Feat.OUTCROP, 0.04, Feat.PALM, 0.03, Feat.FLOWERS, 0.04, Feat.LOG, 0.02);
            case DESERT -> pick(roll, Feat.CACTI, 0.11, Feat.DEAD_BUSHES, 0.07, Feat.BOULDER, 0.06, Feat.OUTCROP, 0.02, Feat.RUIN, 0.02, Feat.PALM, 0.01);
            case BADLANDS -> pick(roll, Feat.BOULDER, 0.07, Feat.DEAD_TREE, 0.04, Feat.CACTI, 0.04, Feat.DEAD_BUSHES, 0.06, Feat.OUTCROP, 0.03);
            case SNOW -> pick(roll, Feat.BOULDER, 0.07, Feat.BERRIES, 0.04, Feat.LOG, 0.04, Feat.ICE_SPIKE, 0.015, Feat.OUTCROP, 0.03, Feat.FLOWERS, 0.02);
            case TAIGA -> pick(roll, Feat.BERRIES, 0.07, Feat.LOG, 0.06, Feat.BOULDER, 0.07, Feat.FLOWERS, 0.05, Feat.OUTCROP, 0.02, Feat.RUIN, 0.01);
            case SWAMP -> pick(roll, Feat.LOG, 0.06, Feat.PUDDLE, 0.06, Feat.MUSHROOMS, 0.05, Feat.BUSH, 0.03, Feat.BOULDER, 0.02);
            case CHERRY -> pick(roll, Feat.FLOWERS, 0.15, Feat.BOULDER, 0.05, Feat.BUSH, 0.05, Feat.LOG, 0.03, Feat.STONE_LANTERN, 0.025);
            case DARK -> pick(roll, Feat.DEAD_TREE, 0.07, Feat.BOULDER, 0.05, Feat.MUSHROOMS, 0.05, Feat.GRAVES, 0.03, Feat.RUIN, 0.015);
            case ROCKY -> pick(roll, Feat.BOULDER, 0.09, Feat.OUTCROP, 0.06, Feat.BUSH, 0.05, Feat.FLOWERS, 0.04, Feat.RUIN, 0.015);
            case VOLCANIC -> pick(roll, Feat.BASALT, 0.08, Feat.BOULDER, 0.05, Feat.DEAD_TREE, 0.03, Feat.MAGMA_VENT, 0.03, Feat.OUTCROP, 0.03);
            case CANDY -> pick(roll, Feat.LOLLIPOP, 0.06, Feat.CANE, 0.05, Feat.GUMDROP, 0.07, Feat.FLOWERS, 0.04);
            case GIANT_FOREST -> pick(roll, Feat.BOULDER, 0.06, Feat.LOG, 0.07, Feat.BERRIES, 0.04, Feat.MUSHROOMS, 0.04, Feat.OUTCROP, 0.02);
            case MUSHROOM -> pick(roll, Feat.MUSHROOMS, 0.12, Feat.BOULDER, 0.03, Feat.LOG, 0.03);
            case FUTURE -> pick(roll, Feat.BUSH, 0.06, Feat.FLOWERS, 0.07, Feat.BOULDER, 0.05, Feat.LAMP, 0.02);
            case ICE -> pick(roll, Feat.ICE_SPIKE, 0.08, Feat.BOULDER, 0.05);
            case WHITE_ROCK -> pick(roll, Feat.BOULDER, 0.07, Feat.BUSH, 0.06, Feat.FLOWERS, 0.03, Feat.OUTCROP, 0.03, Feat.RUIN, 0.01);
            case CLOUD -> pick(roll, Feat.PUFF, 0.24, Feat.PALM, 0.05, Feat.FLOWERS, 0.06);
            case CORAL -> pick(roll, Feat.CORAL_BUSH, 0.11, Feat.KELP, 0.09, Feat.CORAL_POOL, 0.06, Feat.LAMP, 0.04, Feat.BOULDER, 0.03, Feat.PALM, 0.03);
        };
    }

    private static Feat pick(double roll, Object... table) {
        double acc = 0;
        for (int i = 0; i < table.length; i += 2) {
            acc += (Double) table[i + 1];
            if (roll < acc) return (Feat) table[i];
        }
        return Feat.NONE;
    }

    private static boolean nearBuilt(Settlement st, int x, int z, int margin) {
        for (Plot p : st.secrets) if (p.contains(x, z, margin)) return true;
        if (st.plots.isEmpty() && !st.hasDock) return false;
        if (x < st.minX - margin || x > st.maxX + margin || z < st.minZ - margin || z > st.maxZ + margin) return false;
        for (Plot p : st.plots) if (p.contains(x, z, margin)) return true;
        for (int[] r : st.roads) if (segDist(r[0], r[1], r[2], r[3], x, z) < margin) return true;
        return st.hasDock && segDist(st.dockX, st.dockZ, st.dockTipX(), st.dockTipZ(), x, z) < margin + 3;
    }

    private static double segDist(int ax, int az, int bx, int bz, int x, int z) {
        double vx = bx - ax, vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double k = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / len2));
        double dx = ax + vx * k - x, dz = az + vz * k - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ dispatch

    private static void place(Feat f, BlockSink k, int x, int y, int z, IslandTheme th, Rnd r) {
        switch (f) {
            case BOULDER -> boulder(k, x, y, z, 1 + r.nextInt(3), rocks(th), mossy(th), r);
            case OUTCROP -> outcrop(k, x, y, z, th, r);
            case PALM -> palm(k, x, y, z, r);
            case LOG -> fallenLog(k, x, y, z, r, log(th), mossy(th) || th == IslandTheme.TEMPERATE);
            case DRIFTWOOD -> fallenLog(k, x, y, z, r, th == IslandTheme.SNOW || th == IslandTheme.TAIGA ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.STRIPPED_OAK_LOG, false);
            case FLOWERS -> flowers(k, x, y, z, r, flowerSet(th), th == IslandTheme.CLOUD || th == IslandTheme.CANDY);
            case BERRIES -> berries(k, x, y, z, r);
            case BUSH -> bush(k, x, y, z, r, th);
            case RUIN -> ruin(k, x, y, z, r, th);
            case CACTI -> cacti(k, x, y, z, r);
            case DEAD_BUSHES -> deadBushes(k, x, y, z, r);
            case ICE_SPIKE -> iceSpike(k, x, y, z, r);
            case BASALT -> basalt(k, x, y, z, r);
            case DEAD_TREE -> deadTree(k, x, y, z, r, th == IslandTheme.BADLANDS ? Blocks.ACACIA_LOG : th == IslandTheme.VOLCANIC ? Blocks.SPRUCE_LOG : Blocks.DARK_OAK_LOG);
            case LOLLIPOP -> lollipop(k, x, y, z, r);
            case CANE -> cane(k, x, y, z, r);
            case GUMDROP -> gumdrop(k, x, y, z, r);
            case CORAL_POOL -> coralPool(k, x, y, z, r);
            case CORAL_BUSH -> coralBush(k, x, y, z, r);
            case KELP -> kelpGrove(k, x, y, z, r);
            case LAMP -> lamp(k, x, y, z, th);
            case PUFF -> puff(k, x, y, z, r);
            case MUSHROOMS -> mushrooms(k, x, y, z, r);
            case PUDDLE -> puddle(k, x, y, z, r);
            case STONE_LANTERN -> stoneLantern(k, x, y, z);
            case GRAVES -> graves(k, x, y, z, r);
            case MELONS -> melons(k, x, y, z, r);
            case MAGMA_VENT -> magmaVent(k, x, y, z, r);
            case NONE -> {}
        }
    }

    // ------------------------------------------------------------------ palettes

    private static Block[] rocks(IslandTheme th) {
        return switch (th) {
            case TROPICAL, GIANT_FOREST, SWAMP, TAIGA, MUSHROOM -> new Block[]{Blocks.STONE, Blocks.MOSSY_COBBLESTONE, Blocks.ANDESITE, Blocks.COBBLESTONE};
            case CHERRY, SAVANNA -> new Block[]{Blocks.STONE, Blocks.GRANITE, Blocks.ANDESITE, Blocks.STONE};
            case DESERT -> new Block[]{Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA};
            case BADLANDS -> new Block[]{Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.BROWN_TERRACOTTA};
            case SNOW -> new Block[]{Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.STONE};
            case ICE -> new Block[]{Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.PACKED_ICE, Blocks.SNOW_BLOCK};
            case VOLCANIC -> new Block[]{Blocks.BLACKSTONE, Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.BLACKSTONE};
            case WHITE_ROCK, FUTURE -> new Block[]{Blocks.CALCITE, Blocks.DIORITE, Blocks.CALCITE, Blocks.TUFF};
            case DARK -> new Block[]{Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF, Blocks.MOSSY_COBBLESTONE};
            case CORAL -> new Block[]{Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.SANDSTONE, Blocks.PRISMARINE};
            case CLOUD -> new Block[]{ModBlocks.ISLAND_CLOUD.get()};
            case CANDY -> new Block[]{Blocks.PINK_CONCRETE, Blocks.WHITE_CONCRETE};
            default -> new Block[]{Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE};
        };
    }

    private static boolean mossy(IslandTheme th) {
        return th == IslandTheme.TROPICAL || th == IslandTheme.GIANT_FOREST || th == IslandTheme.SWAMP || th == IslandTheme.TAIGA || th == IslandTheme.DARK;
    }

    private static Block log(IslandTheme th) {
        return switch (th) {
            case TAIGA, SNOW, GIANT_FOREST -> Blocks.SPRUCE_LOG;
            case TROPICAL -> Blocks.JUNGLE_LOG;
            case SAVANNA -> Blocks.ACACIA_LOG;
            case SWAMP -> Blocks.MANGROVE_LOG;
            case CHERRY -> Blocks.CHERRY_LOG;
            case DARK, MUSHROOM -> Blocks.DARK_OAK_LOG;
            case MEADOW, FLOWER -> Blocks.BIRCH_LOG;
            default -> Blocks.OAK_LOG;
        };
    }

    private static Block[] flowerSet(IslandTheme th) {
        return switch (th) {
            case MEADOW -> new Block[]{Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.DANDELION};
            case FLOWER -> new Block[]{Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.ALLIUM, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY};
            case TROPICAL, SAVANNA -> new Block[]{Blocks.POPPY, Blocks.ORANGE_TULIP, Blocks.RED_TULIP, Blocks.ROSE_BUSH, Blocks.PEONY, Blocks.DANDELION};
            case CHERRY -> new Block[]{Blocks.PINK_PETALS, Blocks.PINK_PETALS, Blocks.PINK_PETALS, Blocks.WHITE_TULIP, Blocks.PINK_TULIP};
            case TAIGA, SNOW -> new Block[]{Blocks.LILY_OF_THE_VALLEY, Blocks.CORNFLOWER, Blocks.FERN, Blocks.FERN};
            case CLOUD -> new Block[]{Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.CORNFLOWER, Blocks.SUNFLOWER, Blocks.LILAC, Blocks.AZURE_BLUET};
            case CANDY -> new Block[]{Blocks.PINK_TULIP, Blocks.ALLIUM, Blocks.PEONY, Blocks.LILAC, Blocks.PINK_PETALS};
            case FUTURE, ROCKY, WHITE_ROCK -> new Block[]{Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.DANDELION};
            default -> new Block[]{Blocks.POPPY, Blocks.DANDELION, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.CORNFLOWER};
        };
    }

    // ------------------------------------------------------------------ block helpers

    private static boolean soft(BlockState s) {
        return s != null && (s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty()));
    }

    /** Place only into air or replaceable plants (never over trees, buildings or water). */
    private static void put(BlockSink k, int x, int y, int z, BlockState st) {
        if (soft(k.get(x, y, z))) k.set(x, y, z, st);
    }

    private static void put(BlockSink k, int x, int y, int z, Block b) {
        put(k, x, y, z, b.defaultBlockState());
    }

    /** Place into air, plants, leaves or natural terrain (rocks that embed into the ground). */
    private static void embed(BlockSink k, int x, int y, int z, BlockState st) {
        BlockState c = k.get(x, y, z);
        if (c == null) return;
        if (soft(c) || c.is(BlockTags.LEAVES) || SurfacePalette.natural(c) || c.is(Blocks.DIRT) || c.is(Blocks.STONE)) k.set(x, y, z, st);
    }

    /** Replace a natural ground block (to put soil under plants, moss under vines...). */
    private static void soil(BlockSink k, int x, int y, int z, Block b) {
        BlockState c = k.get(x, y, z);
        if (c != null && SurfacePalette.natural(c)) k.set(x, y, z, b.defaultBlockState());
    }

    /**
     * Ground height of a column inside the sink's chunk near {@code hint}: the topmost natural terrain block with open
     * space above, or MIN_VALUE (outside the chunk, under a tree or building, or in water).
     */
    private static int ground(BlockSink k, int x, int z, int hint) {
        for (int y = hint + 4; y >= hint - 5; y--) {
            BlockState c = k.get(x, y, z);
            if (c == null) return Integer.MIN_VALUE;
            if (soft(c)) continue;
            if (SurfacePalette.natural(c) || c.is(Blocks.DIRT)) return y;
            return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static BlockState leaves(Block b) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true) : s;
    }

    private static BlockState axis(Block log, Direction.Axis a) {
        BlockState s = log.defaultBlockState();
        return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, a) : s;
    }

    private static int hash3(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    // ------------------------------------------------------------------ rocks

    private static void boulder(BlockSink k, int x, int y, int z, int rad, Block[] mats, boolean mossTop, Rnd r) {
        double sx = 1 + r.nextDouble() * 0.6, sz = 1 + r.nextDouble() * 0.6;
        double cy = y + 1 - rad * 0.35;
        int ext = rad + 1;
        for (int dx = -ext; dx <= ext; dx++)
            for (int dz = -ext; dz <= ext; dz++) {
                int lowest = Integer.MAX_VALUE;
                for (int dy = -rad - 1; dy <= rad + 1; dy++) {
                    int yy = (int) Math.floor(cy) + dy;
                    double ex = dx / sx, ez = dz / sz, ey = (yy + 0.5 - cy) * 1.25;
                    double d = Math.sqrt(ex * ex + ey * ey + ez * ez);
                    int hh = hash3(x + dx, yy, z + dz);
                    if (d + ((hh & 255) / 255.0 - 0.5) * 0.7 > rad) continue;
                    Block b = mats[(hh >>> 8 & 0x7fff) % mats.length];
                    // moss on top surfaces of damp-climate rocks
                    double above = Math.sqrt(ex * ex + (ey + 1.25) * (ey + 1.25) + ez * ez);
                    if (mossTop && above > rad - 0.2 && ((hh >>> 4) & 3) != 0) b = Blocks.MOSS_BLOCK;
                    embed(k, x + dx, yy, z + dz, b.defaultBlockState());
                    lowest = Math.min(lowest, yy);
                }
                // never float: extend the lowest block down to the ground
                if (lowest != Integer.MAX_VALUE)
                    for (int yy = lowest - 1; yy > lowest - 4; yy--) {
                        BlockState c = k.get(x + dx, yy, z + dz);
                        if (!soft(c)) break;
                        k.set(x + dx, yy, z + dz, mats[0].defaultBlockState());
                    }
            }
    }

    private static void outcrop(BlockSink k, int x, int y, int z, IslandTheme th, Rnd r) {
        Block[] mats = rocks(th);
        int n = 2 + r.nextInt(3);
        boulder(k, x, y, z, 2 + r.nextInt(2), mats, mossy(th), r);
        for (int i = 0; i < n; i++) {
            int ox = r.nextInt(7) - 3, oz = r.nextInt(7) - 3, rad = 1 + r.nextInt(2);
            boulder(k, x + ox, y - 1 + r.nextInt(2), z + oz, rad, mats, mossy(th), r);
        }
    }

    private static void puff(BlockSink k, int x, int y, int z, Rnd r) {
        Block cloud = ModBlocks.ISLAND_CLOUD.get();
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            int ox = i == 0 ? 0 : r.nextInt(7) - 3, oz = i == 0 ? 0 : r.nextInt(7) - 3, rad = 2 + r.nextInt(3);
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++)
                    for (int dy = 0; dy <= rad; dy++)
                        if (dx * dx + dz * dz + dy * dy * 2 <= rad * rad + 1) embed(k, x + ox + dx, y + dy, z + oz + dz, cloud.defaultBlockState());
        }
    }

    // ------------------------------------------------------------------ trees and wood

    private static final int[][] DIRS8 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static void palm(BlockSink k, int x, int y, int z, Rnd r) {
        int height = 6 + r.nextInt(4);
        int[] dir = DIRS8[r.nextInt(8)];
        double lean = 1.2 + r.nextDouble() * 1.6;
        int[] len = new int[8];
        for (int i = 0; i < 8; i++) len[i] = (i < 4 ? 4 : 3) + r.nextInt(2);
        boolean nuts = r.nextBoolean();
        BlockState log = Blocks.JUNGLE_LOG.defaultBlockState();
        BlockState leaf = leaves(Blocks.JUNGLE_LEAVES);
        int tx = x, tz = z;
        for (int i = 0; i < height; i++) {
            double f = (double) i / height;
            tx = x + (int) Math.round(dir[0] * lean * f * f);
            tz = z + (int) Math.round(dir[1] * lean * f * f);
            BlockState c = k.get(tx, y + 1 + i, tz);
            if (soft(c) || (c != null && c.is(BlockTags.LEAVES))) k.set(tx, y + 1 + i, tz, log);
        }
        int ty = y + height;
        put(k, tx, ty + 1, tz, leaf);
        for (int i = 0; i < 8; i++) {
            int[] d = DIRS8[i];
            for (int s = 1; s <= len[i]; s++) {
                int ly = ty + 1 - (s >= len[i] - 1 ? 1 : 0) - (s == len[i] ? 1 : 0);
                put(k, tx + d[0] * s, ly, tz + d[1] * s, leaf);
            }
        }
        if (nuts) {
            put(k, tx + 1, ty - 1, tz, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.WEST).setValue(CocoaBlock.AGE, 2));
            put(k, tx - 1, ty - 1, tz, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.EAST).setValue(CocoaBlock.AGE, 2));
        }
    }

    private static void fallenLog(BlockSink k, int x, int y, int z, Rnd r, Block wood, boolean moss) {
        boolean alongX = r.nextBoolean();
        int len = 4 + r.nextInt(4);
        long mossBits = r.next();
        BlockState log = axis(wood, alongX ? Direction.Axis.X : Direction.Axis.Z);
        for (int i = 0; i < len; i++) {
            int lx = x + (alongX ? i - len / 2 : 0), lz = z + (alongX ? 0 : i - len / 2);
            int g = ground(k, lx, lz, y);
            if (g == Integer.MIN_VALUE || Math.abs(g - y) > 1) continue;
            put(k, lx, y + 1, lz, log);
            if (moss && ((mossBits >>> i) & 1) == 0) put(k, lx, y + 2, lz, Blocks.MOSS_CARPET);
        }
    }

    private static void deadTree(BlockSink k, int x, int y, int z, Rnd r, Block wood) {
        int height = 4 + r.nextInt(4);
        int branches = 2 + r.nextInt(2);
        int[][] br = new int[branches][3];
        for (int i = 0; i < branches; i++) {
            br[i][0] = r.nextInt(4);
            br[i][1] = 2 + r.nextInt(height - 2);
            br[i][2] = 1 + r.nextInt(3);
        }
        for (int i = 1; i <= height; i++) put(k, x, y + i, z, wood.defaultBlockState());
        for (int[] b : br) {
            int[] d = DIRS8[b[0]];
            Direction.Axis a = d[0] != 0 ? Direction.Axis.X : Direction.Axis.Z;
            for (int s = 1; s <= b[2]; s++) put(k, x + d[0] * s, y + b[1] + (s > 1 ? 1 : 0), z + d[1] * s, axis(wood, a));
        }
    }

    private static void bush(BlockSink k, int x, int y, int z, Rnd r, IslandTheme th) {
        Block leafBlock = switch (th) {
            case CHERRY -> Blocks.CHERRY_LEAVES;
            case WHITE_ROCK, ROCKY, FUTURE, MEADOW, FLOWER -> r.nextBoolean() ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES;
            case TROPICAL -> Blocks.JUNGLE_LEAVES;
            case SAVANNA -> Blocks.ACACIA_LEAVES;
            case SWAMP -> Blocks.MANGROVE_LEAVES;
            default -> Blocks.OAK_LEAVES;
        };
        int rad = 1 + r.nextInt(2);
        int hh = r.nextInt(2);
        soil(k, x, y, z, Blocks.GRASS_BLOCK);
        put(k, x, y + 1, z, log(th).defaultBlockState());
        BlockState leaf = leaves(leafBlock);
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++)
                for (int dy = 1; dy <= 2 + hh; dy++) {
                    double d = dx * dx + dz * dz + (dy - 1.5) * (dy - 1.5) * 1.5;
                    if (d > rad * rad + 0.8) continue;
                    if ((hash3(x + dx, y + dy, z + dz) & 7) == 0) continue;
                    put(k, x + dx, y + dy, z + dz, leaf);
                }
    }

    // ------------------------------------------------------------------ plants

    private static void flowers(BlockSink k, int x, int y, int z, Rnd r, Block[] set, boolean makeSoil) {
        int n = 6 + r.nextInt(7);
        for (int i = 0; i < n; i++) {
            int ox = r.nextInt(7) - 3, oz = r.nextInt(7) - 3;
            Block b = set[r.nextInt(set.length)];
            int amount = 1 + r.nextInt(4);
            int g = ground(k, x + ox, z + oz, y);
            if (g == Integer.MIN_VALUE) continue;
            BlockState soil = k.get(x + ox, g, z + oz);
            if (!soil.is(BlockTags.DIRT)) {
                if (!makeSoil) continue;
                k.set(x + ox, g, z + oz, Blocks.GRASS_BLOCK.defaultBlockState());
            }
            plant(k, x + ox, g + 1, z + oz, b, amount);
        }
    }

    private static void plant(BlockSink k, int x, int y, int z, Block b, int amount) {
        BlockState s = b.defaultBlockState();
        if (s.hasProperty(DoublePlantBlock.HALF)) {
            if (!soft(k.get(x, y, z)) || !soft(k.get(x, y + 1, z))) return;
            k.set(x, y, z, s);
            k.set(x, y + 1, z, s.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            return;
        }
        if (s.hasProperty(PinkPetalsBlock.AMOUNT)) s = s.setValue(PinkPetalsBlock.AMOUNT, amount);
        put(k, x, y, z, s);
    }

    private static void berries(BlockSink k, int x, int y, int z, Rnd r) {
        int n = 3 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            int ox = r.nextInt(5) - 2, oz = r.nextInt(5) - 2, age = 2 + r.nextInt(2);
            int g = ground(k, x + ox, z + oz, y);
            if (g == Integer.MIN_VALUE || !k.get(x + ox, g, z + oz).is(BlockTags.DIRT)) continue;
            put(k, x + ox, g + 1, z + oz, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, age));
        }
    }

    private static void mushrooms(BlockSink k, int x, int y, int z, Rnd r) {
        int n = 3 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            int ox = r.nextInt(5) - 2, oz = r.nextInt(5) - 2;
            boolean red = r.nextBoolean();
            int g = ground(k, x + ox, z + oz, y);
            if (g == Integer.MIN_VALUE || !soft(k.get(x + ox, g + 1, z + oz))) continue;
            BlockState c = k.get(x + ox, g, z + oz);
            if (!c.is(Blocks.MYCELIUM)) soil(k, x + ox, g, z + oz, Blocks.PODZOL);
            put(k, x + ox, g + 1, z + oz, red ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM);
        }
    }

    private static void melons(BlockSink k, int x, int y, int z, Rnd r) {
        int n = 2 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            int ox = r.nextInt(5) - 2, oz = r.nextInt(5) - 2;
            int g = ground(k, x + ox, z + oz, y);
            if (g == Integer.MIN_VALUE) continue;
            put(k, x + ox, g + 1, z + oz, Blocks.MELON);
        }
    }

    private static final int[][] CACTUS_SPOTS = {{0, 0}, {2, 1}, {-1, 2}, {1, -2}, {-2, -1}, {3, -1}};

    private static void cacti(BlockSink k, int x, int y, int z, Rnd r) {
        int n = 1 + r.nextInt(3);
        int first = r.nextInt(CACTUS_SPOTS.length);
        int[] heights = {2 + r.nextInt(2), 1 + r.nextInt(3), 2 + r.nextInt(2)};
        for (int i = 0; i < n; i++) {
            int[] o = CACTUS_SPOTS[(first + i * 2) % CACTUS_SPOTS.length];
            int cx = x + o[0], cz = z + o[1];
            int g = ground(k, cx, cz, y);
            if (g == Integer.MIN_VALUE) continue;
            BlockState soil = k.get(cx, g, cz);
            if (!soil.is(Blocks.SAND) && !soil.is(Blocks.RED_SAND)) continue;
            for (int j = 1; j <= heights[i]; j++) {
                boolean clear = true;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockState side = k.get(cx + d.getStepX(), g + j, cz + d.getStepZ());
                    if (side != null && !side.isAir()) clear = false;
                }
                if (!clear) break;
                put(k, cx, g + j, cz, Blocks.CACTUS);
            }
        }
        deadBushes(k, x, y, z, r);
    }

    private static void deadBushes(BlockSink k, int x, int y, int z, Rnd r) {
        int n = 2 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            int ox = r.nextInt(7) - 3, oz = r.nextInt(7) - 3;
            int g = ground(k, x + ox, z + oz, y);
            if (g == Integer.MIN_VALUE) continue;
            BlockState soil = k.get(x + ox, g, z + oz);
            if (!(soil.is(BlockTags.SAND) || soil.is(BlockTags.TERRACOTTA) || soil.is(BlockTags.DIRT))) continue;
            BlockState above = k.get(x + ox, g + 1, z + oz);
            if (above == null || !above.isAir()) continue;
            // keep cacti valid: no dead bush right beside a cactus
            boolean nearCactus = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockState side = k.get(x + ox + d.getStepX(), g + 1, z + oz + d.getStepZ());
                if (side != null && side.is(Blocks.CACTUS)) nearCactus = true;
            }
            if (!nearCactus) k.set(x + ox, g + 1, z + oz, Blocks.DEAD_BUSH.defaultBlockState());
        }
    }

    private static void kelpGrove(BlockSink k, int x, int y, int z, Rnd r) {
        int n = 4 + r.nextInt(5);
        for (int i = 0; i < n; i++) {
            int ox = r.nextInt(7) - 3, oz = r.nextInt(7) - 3, hgt = 2 + r.nextInt(5);
            int g = ground(k, x + ox, z + oz, y);
            if (g == Integer.MIN_VALUE) continue;
            soil(k, x + ox, g, z + oz, Blocks.MOSS_BLOCK);
            boolean room = true;
            for (int j = 1; j <= hgt; j++) if (!soft(k.get(x + ox, g + j, z + oz))) room = false;
            if (!room) continue;
            for (int j = 1; j < hgt; j++) k.set(x + ox, g + j, z + oz, Blocks.TWISTING_VINES_PLANT.defaultBlockState());
            k.set(x + ox, g + hgt, z + oz, Blocks.TWISTING_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 25));
        }
        // a few moss carpets around the stems
        for (int i = 0; i < 4; i++) {
            int ox = r.nextInt(5) - 2, oz = r.nextInt(5) - 2;
            int g = ground(k, x + ox, z + oz, y);
            if (g != Integer.MIN_VALUE) put(k, x + ox, g + 1, z + oz, Blocks.MOSS_CARPET);
        }
    }

    // ------------------------------------------------------------------ man-made bits

    private static void ruin(BlockSink k, int x, int y, int z, Rnd r, IslandTheme th) {
        Block[] m = switch (th) {
            case DESERT, BADLANDS -> new Block[]{Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.SMOOTH_SANDSTONE};
            case VOLCANIC -> new Block[]{Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.BLACKSTONE};
            case DARK -> new Block[]{Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.CHISELED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE};
            case WHITE_ROCK, FUTURE -> new Block[]{Blocks.QUARTZ_BRICKS, Blocks.CALCITE, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR};
            case CORAL -> new Block[]{Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE};
            default -> new Block[]{Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS};
        };
        int pillars = 3 + r.nextInt(3);
        for (int i = 0; i < pillars; i++) {
            int ox = r.nextInt(9) - 4, oz = r.nextInt(9) - 4, hgt = 1 + r.nextInt(4);
            int g = ground(k, x + ox, z + oz, y);
            if (g == Integer.MIN_VALUE) continue;
            for (int j = 1; j <= hgt; j++)
                put(k, x + ox, g + j, z + oz, (j == hgt && hgt >= 3 ? m[2] : m[(hash3(x + ox, g + j, z + oz) & 1) == 0 ? 0 : 3]).defaultBlockState());
        }
        boolean alongX = r.nextBoolean();
        int wl = 3 + r.nextInt(3), wo = r.nextInt(5) - 2;
        int[] wh = new int[wl];
        for (int i = 0; i < wl; i++) wh[i] = 1 + r.nextInt(3);
        for (int i = 0; i < wl; i++) {
            int wx = x + (alongX ? i - wl / 2 : wo), wz = z + (alongX ? wo : i - wl / 2);
            int g = ground(k, wx, wz, y);
            if (g == Integer.MIN_VALUE) continue;
            for (int j = 1; j <= wh[i]; j++) put(k, wx, g + j, wz, m[(hash3(wx, g + j, wz) % 3 == 0) ? 1 : 0].defaultBlockState());
        }
    }

    private static void stoneLantern(BlockSink k, int x, int y, int z) {
        put(k, x, y + 1, z, Blocks.ANDESITE_WALL);
        put(k, x, y + 2, z, Blocks.ANDESITE_WALL);
        put(k, x, y + 3, z, Blocks.LANTERN);
        put(k, x, y + 4, z, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
    }

    private static void lamp(BlockSink k, int x, int y, int z, IslandTheme th) {
        Block post = th == IslandTheme.CORAL ? Blocks.PRISMARINE_WALL : Blocks.IRON_BARS;
        for (int j = 1; j <= 3; j++) put(k, x, y + j, z, post);
        put(k, x, y + 4, z, th == IslandTheme.CORAL ? Blocks.SEA_LANTERN : Blocks.PEARLESCENT_FROGLIGHT);
    }

    private static void graves(BlockSink k, int x, int y, int z, Rnd r) {
        int n = 2 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            int ox = (i % 3) * 2 - 2, oz = (i / 3) * 3 - 1;
            boolean tall = r.nextBoolean();
            int g = ground(k, x + ox, z + oz, y);
            if (g == Integer.MIN_VALUE) continue;
            put(k, x + ox, g + 1, z + oz, Blocks.COBBLED_DEEPSLATE_WALL);
            if (tall) put(k, x + ox, g + 2, z + oz, Blocks.COBBLED_DEEPSLATE_WALL);
            soil(k, x + ox, g, z + oz + 1, Blocks.COARSE_DIRT);
        }
    }

    // ------------------------------------------------------------------ climate set pieces

    private static void iceSpike(BlockSink k, int x, int y, int z, Rnd r) {
        int height = 7 + r.nextInt(9);
        double base = height > 11 ? 2.2 : 1.4;
        for (int i = -1; i <= height; i++) {
            double rad = base * (1 - Math.max(0, i) / (double) height) + 0.35;
            int ri = (int) Math.ceil(rad);
            for (int dx = -ri; dx <= ri; dx++)
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > rad * rad) continue;
                    Block b = (hash3(x + dx, y + i, z + dz) % 5 == 0) ? Blocks.BLUE_ICE : Blocks.PACKED_ICE;
                    embed(k, x + dx, y + 1 + i, z + dz, b.defaultBlockState());
                }
        }
    }

    private static void basalt(BlockSink k, int x, int y, int z, Rnd r) {
        int n = 4 + r.nextInt(5);
        for (int i = 0; i < n; i++) {
            int ox = r.nextInt(7) - 3, oz = r.nextInt(7) - 3, hgt = 2 + r.nextInt(5);
            boolean polished = r.nextInt(3) == 0;
            int g = ground(k, x + ox, z + oz, y);
            if (g == Integer.MIN_VALUE) continue;
            for (int j = 1; j <= hgt; j++) put(k, x + ox, g + j, z + oz, polished ? Blocks.POLISHED_BASALT : Blocks.BASALT);
        }
    }

    private static void magmaVent(BlockSink k, int x, int y, int z, Rnd r) {
        int rad = 1 + r.nextInt(2);
        for (int dx = -rad - 1; dx <= rad + 1; dx++)
            for (int dz = -rad - 1; dz <= rad + 1; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= rad * rad) soil(k, x + dx, y, z + dz, Blocks.MAGMA_BLOCK);
                else if (d2 <= (rad + 1) * (rad + 1) && (hash3(x + dx, y, z + dz) & 1) == 0) put(k, x + dx, y + 1, z + dz, Blocks.BLACKSTONE);
            }
    }

    private static final Block[][] CANDY_PAIRS = {{Blocks.PINK_CONCRETE, Blocks.WHITE_CONCRETE}, {Blocks.RED_CONCRETE, Blocks.YELLOW_CONCRETE},
            {Blocks.MAGENTA_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE}, {Blocks.LIME_CONCRETE, Blocks.ORANGE_CONCRETE}};

    private static void lollipop(BlockSink k, int x, int y, int z, Rnd r) {
        Block[] c = CANDY_PAIRS[r.nextInt(CANDY_PAIRS.length)];
        int stick = 3 + r.nextInt(2);
        boolean alongX = r.nextBoolean();
        for (int j = 1; j <= stick; j++) put(k, x, y + j, z, Blocks.BIRCH_FENCE);
        int cy = y + stick + 2;
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++) {
                int d2 = a * a + b * b;
                if (d2 > 5) continue;
                Block bl = c[(int) Math.round(Math.sqrt(d2)) % 2];
                put(k, x + (alongX ? a : 0), cy + b, z + (alongX ? 0 : a), bl.defaultBlockState());
            }
    }

    private static void cane(BlockSink k, int x, int y, int z, Rnd r) {
        int hgt = 5 + r.nextInt(3);
        int[] d = DIRS8[r.nextInt(4)];
        int i = 0;
        for (int j = 1; j <= hgt; j++, i++) put(k, x, y + j, z, (i % 2 == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE).defaultBlockState());
        int[][] hook = {{0, 1}, {1, 2}, {2, 1}, {2, 0}};
        for (int[] h : hook) {
            put(k, x + d[0] * h[0], y + hgt + h[1], z + d[1] * h[0], (i % 2 == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE).defaultBlockState());
            i++;
        }
    }

    private static void gumdrop(BlockSink k, int x, int y, int z, Rnd r) {
        Block[] colors = {Blocks.LIME_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.RED_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE};
        Block b = colors[r.nextInt(colors.length)];
        int rad = 1 + r.nextInt(2);
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++)
                for (int dy = 0; dy <= rad + 1; dy++)
                    if (dx * dx + dz * dz + dy * dy <= rad * rad + 1) embed(k, x + dx, y + dy, z + dz, b.defaultBlockState());
        put(k, x, y + rad + 2, z, Blocks.WHITE_CONCRETE);
    }

    // ------------------------------------------------------------------ Fish-Man Island

    private static final Block[] CORAL_BLOCKS = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
    private static final Block[] CORAL_PLANTS = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL,
            Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN};

    /** A sunken coral pool: live coral, kelp and glowing sea pickles in real water, held by a prismarine rim. */
    private static void coralPool(BlockSink k, int x, int y, int z, Rnd r) {
        int rad = 2 + r.nextInt(2);
        Block rim = r.nextBoolean() ? Blocks.PRISMARINE_BRICKS : Blocks.SMOOTH_SANDSTONE;
        BlockState water = Blocks.WATER.defaultBlockState();
        int ext = rad + 2;
        for (int dx = -ext; dx <= ext; dx++)
            for (int dz = -ext; dz <= ext; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int wx = x + dx, wz = z + dz;
                int hh = hash3(wx, y, wz);
                if (d <= rad + 0.4) {
                    if (k.get(wx, y, wz) == null) continue;
                    k.set(wx, y - 3, wz, ((hh & 3) == 0 ? CORAL_BLOCKS[(hh >>> 2 & 0xff) % CORAL_BLOCKS.length] : Blocks.SAND).defaultBlockState());
                    for (int yy = y - 2; yy <= y; yy++) k.set(wx, yy, wz, water);
                    for (int yy = y + 1; yy <= y + 3; yy++) {
                        BlockState c = k.get(wx, yy, wz);
                        if (c != null && (SurfacePalette.natural(c) || soft(c))) k.set(wx, yy, wz, Blocks.AIR.defaultBlockState());
                    }
                    int roll = (hh >>> 10) & 15;
                    if (roll < 5) k.set(wx, y - 2, wz, CORAL_PLANTS[(hh >>> 14 & 0xff) % CORAL_PLANTS.length].defaultBlockState());
                    else if (roll < 7) k.set(wx, y - 2, wz, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 2 + (roll & 1) * 2));
                    else if (roll < 9) {
                        k.set(wx, y - 2, wz, Blocks.KELP_PLANT.defaultBlockState());
                        k.set(wx, y - 1, wz, Blocks.KELP.defaultBlockState().setValue(KelpBlock.AGE, 25));
                    } else if (roll < 11) k.set(wx, y - 2, wz, Blocks.SEAGRASS.defaultBlockState());
                } else if (d <= rad + 1.45) {
                    if (k.get(wx, y, wz) == null) continue;
                    BlockState top = ((hh >>> 6) & 7) == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : rim.defaultBlockState();
                    for (int yy = y - 3; yy < y; yy++) k.set(wx, yy, wz, rim.defaultBlockState());
                    k.set(wx, y, wz, top);
                    for (int yy = y + 1; yy <= y + 2; yy++) {
                        BlockState c = k.get(wx, yy, wz);
                        if (c != null && soft(c)) k.set(wx, yy, wz, Blocks.AIR.defaultBlockState());
                    }
                }
            }
    }

    /** Branching "coral" made of bright concrete with glowing froglight tips (real coral would die in the bubble's air). */
    private static void coralBush(BlockSink k, int x, int y, int z, Rnd r) {
        Block[] colors = {Blocks.PINK_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.RED_CONCRETE, Blocks.PURPLE_CONCRETE};
        Block[] tips = {Blocks.PEARLESCENT_FROGLIGHT, Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT};
        Block c = colors[r.nextInt(colors.length)];
        Block tip = tips[r.nextInt(tips.length)];
        int trunk = 1 + r.nextInt(2);
        int branches = 3 + r.nextInt(3);
        int[][] bs = new int[branches][3];
        for (int i = 0; i < branches; i++) {
            bs[i][0] = r.nextInt(8);
            bs[i][1] = 2 + r.nextInt(3);
            bs[i][2] = r.nextInt(3);
        }
        for (int j = 1; j <= trunk; j++) put(k, x, y + j, z, c);
        for (int[] b : bs) {
            int[] d = DIRS8[b[0]];
            int bx = x, by = y + trunk, bz = z;
            for (int s = 1; s <= b[1]; s++) {
                if (s % 2 == 1) {
                    bx += d[0];
                    bz += d[1];
                }
                by++;
                put(k, bx, by, bz, s == b[1] && b[2] == 0 ? tip : c);
            }
        }
    }

    private static void puddle(BlockSink k, int x, int y, int z, Rnd r) {
        int rad = 1 + r.nextInt(2);
        long pads = r.next();
        for (int dx = -rad - 1; dx <= rad + 1; dx++)
            for (int dz = -rad - 1; dz <= rad + 1; dz++) {
                int d2 = dx * dx + dz * dz;
                int wx = x + dx, wz = z + dz;
                if (k.get(wx, y, wz) == null) continue;
                if (d2 <= rad * rad) {
                    k.set(wx, y - 1, wz, Blocks.MUD.defaultBlockState());
                    k.set(wx, y, wz, Blocks.WATER.defaultBlockState());
                    BlockState above = k.get(wx, y + 1, wz);
                    if (soft(above)) k.set(wx, y + 1, wz, ((pads >>> ((dx + 3) * 7 + dz + 3)) & 3) == 0 ? Blocks.LILY_PAD.defaultBlockState() : Blocks.AIR.defaultBlockState());
                } else if (d2 <= (rad + 1) * (rad + 1) + 1) {
                    BlockState c = k.get(wx, y, wz);
                    if (soft(c)) k.set(wx, y, wz, Blocks.MUD.defaultBlockState());
                    BlockState below = k.get(wx, y - 1, wz);
                    if (soft(below)) k.set(wx, y - 1, wz, Blocks.MUD.defaultBlockState());
                }
            }
    }

    // ------------------------------------------------------------------ hashing

    static long mix(long seed, int a, int b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /** Small deterministic generator (SplitMix64). */
    static final class Rnd {
        private long s;

        Rnd(long seed) {
            this.s = seed;
        }

        long next() {
            long z = (s += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        int nextInt(int bound) {
            return (int) Math.floorMod(next(), (long) bound);
        }

        double nextDouble() {
            return (next() >>> 11) * 0x1.0p-53;
        }

        boolean nextBoolean() {
            return (next() & 1) == 0;
        }
    }

    private IslandDecor() {}
}
