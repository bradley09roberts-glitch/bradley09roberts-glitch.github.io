package com.grandline.world.landmark;

import com.grandline.registry.ArchBlocks;
import com.grandline.registry.ModBlocks;
import com.grandline.world.Island;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Arch;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Shared shapes for the White Sea and deep-sea set pieces ({@link DimensionLandmarks} and the Fish-Man Island pieces in this file): cumulus
 * puffs, discs and rings clipped to the footprint, onion and scallop domes, coral trees, a giant bell, a helical ramp
 * and the ribs of a wrecked hull. Everything goes through the {@link Builder}'s local frame (rotation safe) and is
 * deterministic (randomness only through {@link Builder#rand}).
 */
final class LandmarksD2 {
    private LandmarksD2() {}

    // ------------------------------------------------------------------ states

    static BlockState s(Block b) {
        return b.defaultBlockState();
    }

    static BlockState s(DeferredBlock<Block> b) {
        return b.get().defaultBlockState();
    }

    static BlockState cloud() {
        return ModBlocks.ISLAND_CLOUD.get().defaultBlockState();
    }

    static BlockState st(DeferredBlock<Block> b, Direction f, Half h) {
        return LandmarkKit.stair(b.get(), f, h);
    }

    static BlockState st(Block b, Direction f, Half h) {
        return LandmarkKit.stair(b, f, h);
    }

    static BlockState slab(DeferredBlock<Block> b, SlabType t) {
        return slab(b.get(), t);
    }

    static BlockState slab(Block b, SlabType t) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(SlabBlock.TYPE) ? s.setValue(SlabBlock.TYPE, t) : s;
    }

    static BlockState leaves(Block b) {
        return LandmarkKit.leaves(b);
    }

    static BlockState pillar(Block b) {
        return LandmarkKit.axis(b, Direction.Axis.Y);
    }

    static BlockState pillar(DeferredBlock<Block> b) {
        return LandmarkKit.axis(b.get(), Direction.Axis.Y);
    }

    static BlockState bellHanging(Direction f) {
        return Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING).setValue(BellBlock.FACING, f);
    }

    static BlockState hangingLantern(Block l) {
        return LandmarkKit.lantern(l, true);
    }

    static BlockState hangingLantern(DeferredBlock<Block> l) {
        return LandmarkKit.lantern(l.get(), true);
    }

    static BlockState standingLantern(DeferredBlock<Block> l) {
        return LandmarkKit.lantern(l.get(), false);
    }

    static BlockState candles(Block c, int n) {
        return LandmarkKit.candles(c, n);
    }

    // ------------------------------------------------------------------ clipped writes

    static boolean in(Builder b, int x, int z) {
        return x >= 0 && z >= 0 && x < b.w && z < b.d;
    }

    static void put(Builder b, int x, int y, int z, BlockState s) {
        if (in(b, x, z)) b.set(x, y, z, s);
    }

    /** Filled disc of radius r centred on (cx, cz) at y, clipped to the footprint. */
    static void disc(Builder b, double cx, double cz, int y, double r, BlockState s) {
        for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++)
            for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++) {
                double dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz <= r * r + 0.25) put(b, x, y, z, s);
            }
    }

    /** One-block ring of radius r (cells whose distance lies in [r-0.5, r+0.5)). */
    static void ring(Builder b, double cx, double cz, int y, double r, BlockState s) {
        for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r + 1); x++)
            for (int z = (int) Math.floor(cz - r - 1); z <= (int) Math.ceil(cz + r + 1); z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d >= r - 0.5 && d < r + 0.5) put(b, x, y, z, s);
            }
    }

    /** A snow layer of 1..8 eighths (used to round off the tops of clouds). */
    static BlockState snow(int eighths) {
        return Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.max(1, Math.min(8, eighths)));
    }

    /**
     * Cumulus puff: a soft, flat-bottomed mound of {@code s} over the ellipse (rx, rz) around (cx, cz), from y0 up to
     * about y0+h in the middle, sloping gently to the rim; the top of every column is rounded off with snow layers.
     * Clipped to the footprint.
     */
    static void puff(Builder b, double cx, int y0, double cz, double rx, double rz, double h, int salt, BlockState s) {
        for (int x = (int) Math.floor(cx - rx); x <= (int) Math.ceil(cx + rx); x++)
            for (int z = (int) Math.floor(cz - rz); z <= (int) Math.ceil(cz + rz); z++) {
                double dx = (x - cx) / rx, dz = (z - cz) / rz, d = dx * dx + dz * dz;
                if (d > 1.0) continue;
                double hh = h * Math.pow(1 - d, 0.7) + (b.rand(salt + x * 31 + z * 17, 4) - 1.5) * 0.12;
                column(b, x, y0, z, hh, s);
            }
    }

    /** Fills a column from y0 with {@code s} to height hh (blocks), the fraction as a snow layer. */
    static void column(Builder b, int x, int y0, int z, double hh, BlockState s) {
        if (!in(b, x, z) || hh <= 0.1) return;
        int full = (int) Math.floor(hh);
        for (int y = y0; y < y0 + full; y++) b.set(x, y, z, s);
        int eighths = (int) Math.round((hh - full) * 8);
        if (eighths >= 1) b.set(x, y0 + full, z, eighths >= 8 ? s : snow(eighths));
    }

    /**
     * Floating cumulus: a cloud ball with a flattish underside and a rounded, snow-softened top, centred on (cx, cy, cz)
     * with radii rx, ry (up) and rz.
     */
    static void cloudBall(Builder b, double cx, double cy, double cz, double rx, double ry, double rz, int salt, BlockState s) {
        for (int x = (int) Math.floor(cx - rx); x <= (int) Math.ceil(cx + rx); x++)
            for (int z = (int) Math.floor(cz - rz); z <= (int) Math.ceil(cz + rz); z++) {
                double dx = (x - cx) / rx, dz = (z - cz) / rz, d = dx * dx + dz * dz;
                if (d > 1.0) continue;
                double k = Math.sqrt(1 - d);
                double lo = cy - ry * 0.45 * k, hi = cy + ry * k + (b.rand(salt + x * 13 + z * 7, 3) - 1) * 0.15;
                int y0 = (int) Math.round(lo);
                column(b, x, y0, z, hi - y0, s);
            }
    }

    /** Ellipsoid (solid) clipped to the footprint; cells below {@code yMin} are skipped. */
    static void ball(Builder b, double cx, double cy, double cz, double rx, double ry, double rz, BlockState s, int yMin) {
        for (int x = (int) Math.floor(cx - rx); x <= (int) Math.ceil(cx + rx); x++)
            for (int y = Math.max(yMin, (int) Math.floor(cy - ry)); y <= (int) Math.ceil(cy + ry); y++)
                for (int z = (int) Math.floor(cz - rz); z <= (int) Math.ceil(cz + rz); z++) {
                    double dx = (x - cx) / rx, dy = (y - cy) / ry, dz = (z - cz) / rz;
                    if (dx * dx + dy * dy + dz * dz <= 1.0) put(b, x, y, z, s);
                }
    }

    /**
     * Hemispherical (or squashed) dome shell centred on (cx, cz) with its base at y: radius r across, height hgt; the
     * top course is closed. {@code ribs} (nullable) replaces the shell on {@code nRibs} meridians starting at angle
     * {@code ribOffset} (radians). Returns the y of the top course.
     */
    static int dome(Builder b, double cx, int y, double cz, double r, double hgt, BlockState shell, BlockState ribs, int nRibs, double ribOffset) {
        int top = y;
        int n = (int) Math.floor(hgt);
        for (int dy = 0; dy <= n; dy++) {
            double t = dy / hgt;
            double rr = r * Math.sqrt(Math.max(0, 1 - t * t));
            double inner = dy == n ? -1 : Math.max(-1, rr - 1.25);
            if (dy == n && rr < 0.5) rr = 0.5;
            for (int x = (int) Math.floor(cx - r - 1); x <= (int) Math.ceil(cx + r + 1); x++)
                for (int z = (int) Math.floor(cz - r - 1); z <= (int) Math.ceil(cz + r + 1); z++) {
                    double dd = Math.hypot(x - cx, z - cz);
                    if (dd > rr + 0.5 || dd < inner) continue;
                    BlockState s = shell;
                    if (ribs != null && nRibs > 0 && dd > 0.7) {
                        double step = 2 * Math.PI / nRibs;
                        double a = Math.atan2(z - cz, x - cx) - ribOffset;
                        double k = a / step;
                        if (Math.abs(k - Math.round(k)) * step * dd < 0.55) s = ribs;
                    }
                    put(b, x, y + dy, z, s);
                }
            top = y + dy;
        }
        return top;
    }

    /**
     * Heightmap dome over the disc of radius r round (cx, cz), base at y, height hgt: courses of full blocks, each
     * column capped with a stair facing the centre (or a slab at the crown) where the surface passes half a block, so
     * the dome reads smooth; hollow under an inner surface about a block in. {@code nRibs} meridians (from angle
     * {@code ribOffset}) use the rib blocks. Returns the top y.
     */
    static int smoothDome(Builder b, double cx, int y, double cz, double r, double hgt, BlockState full, BlockState stairs, BlockState slab,
                          BlockState ribFull, BlockState ribStairs, BlockState ribSlab, int nRibs, double ribOffset) {
        int top = y;
        double ro = r + 0.3, ri = r - 1.1, hi = hgt - 1.1;
        for (int x = (int) Math.floor(cx - ro); x <= (int) Math.ceil(cx + ro); x++)
            for (int z = (int) Math.floor(cz - ro); z <= (int) Math.ceil(cz + ro); z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > ro) continue;
                double h = hgt * Math.sqrt(Math.max(0, 1 - (d / ro) * (d / ro)));
                double hin = d < ri ? hi * Math.sqrt(Math.max(0, 1 - (d / ri) * (d / ri))) : 0;
                boolean rib = false;
                if (nRibs > 0 && d > 0.9) {
                    double step = 2 * Math.PI / nRibs, a = Math.atan2(z - cz, x - cx) - ribOffset, kk = a / step;
                    rib = Math.abs(kk - Math.round(kk)) * step * d < 0.5;
                }
                BlockState F = rib ? ribFull : full, St = rib ? ribStairs : stairs, Sl = rib ? ribSlab : slab;
                int hf = (int) Math.floor(h);
                double frac = h - hf;
                for (int k = (int) Math.floor(hin); k < hf; k++) put(b, x, y + k, z, F);
                if (frac >= 0.3) {
                    BlockState capS;
                    if (frac >= 0.8) capS = F;
                    else if (d < 1.0) capS = slab(Sl, SlabType.BOTTOM);
                    else {
                        double ddx = cx - x, ddz = cz - z;
                        Direction toC = Math.abs(ddx) >= Math.abs(ddz) ? (ddx > 0 ? Direction.EAST : Direction.WEST) : (ddz > 0 ? Direction.SOUTH : Direction.NORTH);
                        capS = com.grandline.world.build.Arch.stair(St, toC, Half.BOTTOM);
                    }
                    put(b, x, y + hf, z, capS);
                    top = Math.max(top, y + hf);
                } else top = Math.max(top, y + hf - 1);
            }
        return top;
    }

    static BlockState slab(BlockState s, SlabType t) {
        return s.hasProperty(SlabBlock.TYPE) ? s.setValue(SlabBlock.TYPE, t) : s;
    }

    /** Onion cap (bulb on a neck) for spires: radius r, total height about 2.2r, finial on top. Returns the top y. */
    static int onion(Builder b, int cx, int y, int cz, double r, BlockState shell, BlockState tip) {
        int h = (int) Math.round(r * 2.2) + 1;
        int top = y;
        for (int dy = 0; dy < h; dy++) {
            double t = dy / (double) (h - 1);
            double rr = t < 0.35 ? r * (0.75 + 0.25 * Math.sin(t / 0.35 * Math.PI / 2)) : r * Math.cos((t - 0.35) / 0.65 * Math.PI / 2);
            if (rr < 0.35) rr = 0.35;
            disc(b, cx, cz, y + dy, rr, shell);
            top = y + dy;
        }
        put(b, cx, top + 1, cz, tip);
        return top + 1;
    }

    // ------------------------------------------------------------------ props

    /**
     * A giant bell (e.g. Shandora's Golden Bell): profile radii from the crown down to the lip, hollow underneath, with
     * a real bell hung inside as the clapper (so it can be rung). {@code crownY} is the top course.
     */
    static void giantBell(Builder b, int cx, int crownY, int cz, BlockState metal, Direction facing) {
        double[] prof = {0.5, 1.0, 1.5, 1.9, 2.3};
        for (int i = 0; i < prof.length; i++) {
            int y = crownY - i;
            double r = prof[i];
            for (int x = cx - 3; x <= cx + 3; x++)
                for (int z = cz - 3; z <= cz + 3; z++) {
                    double dd = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                    if (dd > r * r + 0.5) continue;
                    boolean hollow = i >= 3 && dd < (r - 1.0) * (r - 1.0) + 0.3 && !(x == cx && z == cz && i == 3);
                    b.set(x, y, z, hollow ? Blocks.AIR.defaultBlockState() : metal);
                }
        }
        // the clapper: a real bell hanging from the solid heart of the bell
        b.set(cx, crownY - prof.length + 1, cz, Blocks.AIR.defaultBlockState());
        b.set(cx, crownY - 4, cz, bellHanging(facing));
    }

    /**
     * Coral tree (the bubble-corals of Fish-Man Island): a short trunk of {@code trunk} that forks twice into upswept
     * limbs, each tip carrying a small round crown of {@code crown}; {@code tipAccent} (a lamp) sits in the first fork.
     * Deterministic per salt; clipped to the footprint.
     */
    static void coralTree(Builder b, int x0, int y0, int z0, int h, double r, int salt, BlockState trunk, BlockState crown, BlockState tipAccent) {
        for (int y = 0; y < h; y++) disc(b, x0, z0, y0 + y, y < 2 ? 1.2 : 0.4, trunk);
        int n = 3 + b.rand(salt, 2);
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n + b.rand(salt + 7 * i, 80) / 100.0;
            coralLimb(b, x0, y0 + h - 1 - b.rand(salt + 3 * i, 2), z0, a, r * 0.45, 1, salt + 31 * i, trunk, crown);
        }
        ball(b, x0, y0 + h + r * 0.9, z0, 1.7, 1.3, 1.7, crown, -999);
        put(b, x0, y0 + h, z0, tipAccent);
    }

    private static void coralLimb(Builder b, double x, double y, double z, double a, double len, int depth, int salt, BlockState trunk, BlockState crown) {
        int steps = Math.max(2, (int) Math.round(len));
        for (int k = 0; k < steps; k++) {
            x += Math.cos(a) * 0.6;
            z += Math.sin(a) * 0.6;
            y += 0.9;
            put(b, (int) Math.round(x), (int) Math.round(y), (int) Math.round(z), trunk);
        }
        if (depth <= 0) {
            ball(b, x, y + 0.7, z, 1.3, 1.0, 1.3, crown, -999);
            return;
        }
        double spread = 0.35 + b.rand(salt, 25) / 100.0;
        coralLimb(b, x, y, z, a - spread, len * 0.8, depth - 1, salt * 3 + 1, trunk, crown);
        coralLimb(b, x, y, z, a + spread, len * 0.8, depth - 1, salt * 3 + 2, trunk, crown);
    }

    // ------------------------------------------------------------------ mouldings and texture

    /** Ring of stairs round the rectangle [x1..x2]x[z1..z2] at y, all facing inward (a plinth skirt, or a corbel if TOP). */
    static void stairRing(Builder b, int x1, int z1, int x2, int z2, int y, BlockState stairs, Half half) {
        for (int x = x1 + 1; x < x2; x++) {
            b.set(x, y, z1, com.grandline.world.build.Arch.stair(stairs, Direction.SOUTH, half));
            b.set(x, y, z2, com.grandline.world.build.Arch.stair(stairs, Direction.NORTH, half));
        }
        for (int z = z1 + 1; z < z2; z++) {
            b.set(x1, y, z, com.grandline.world.build.Arch.stair(stairs, Direction.EAST, half));
            b.set(x2, y, z, com.grandline.world.build.Arch.stair(stairs, Direction.WEST, half));
        }
        b.set(x1, y, z1, com.grandline.world.build.Arch.stair(stairs, Direction.SOUTH, half, StairsShape.OUTER_LEFT));
        b.set(x2, y, z1, com.grandline.world.build.Arch.stair(stairs, Direction.SOUTH, half, StairsShape.OUTER_RIGHT));
        b.set(x1, y, z2, com.grandline.world.build.Arch.stair(stairs, Direction.NORTH, half, StairsShape.OUTER_RIGHT));
        b.set(x2, y, z2, com.grandline.world.build.Arch.stair(stairs, Direction.NORTH, half, StairsShape.OUTER_LEFT));
    }

    /** A vine hanging on the face of the block on its {@code wallSide}. */
    static BlockState vine(Direction wallSide) {
        return Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(wallSide), true);
    }

    /** Weathered ashlar: stone bricks with mossy and cracked ones mixed in. */
    static BlockState ruin(Builder b, int salt) {
        int r = b.rand(salt, 10);
        return r < 3 ? s(Blocks.MOSSY_STONE_BRICKS) : r < 5 ? s(Blocks.CRACKED_STONE_BRICKS) : s(Blocks.STONE_BRICKS);
    }

    /** Jungle floor: grass with moss, coarse dirt and podzol patches. */
    static BlockState jungleGround(Builder b, int salt) {
        int r = b.rand(salt, 12);
        return r < 2 ? s(Blocks.MOSS_BLOCK) : r < 3 ? s(Blocks.COARSE_DIRT) : r < 4 ? s(Blocks.PODZOL) : s(Blocks.GRASS_BLOCK);
    }

    // ================================================================== Fish-Man Island and the Sea Forest
    // These are written through FloodedSink: cleared air at or below the harbour line (local y <= 0 at sea level) or
    // outside the air bubbles becomes sea water. Ryugu Palace, Gyoncorde Plaza and the Sea Forest stand inside a
    // bubble above the harbour line; the Mermaid Cove sits on the harbour line itself, its lagoon is the real sea.

    private static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    private static void npc(Builder b, String role, int x, int y, int z) {
        b.clear(x, y, z, x, y + 1, z);
        b.marker("npc", x, y, z, LandmarkKit.role(role));
    }

    /**
     * Ryugu Palace on the crown of Fish-Man Island, inside its own bubble: a salmon-and-white coral palace on a
     * prismarine podium. The great hall is a drum under a ribbed scallop-shell dome crowned with a pearl; a tall entrance
     * pavilion carries a shell crest; two wings (the royal chambers and the banquet hall) and four slender coral towers
     * with onion caps frame it. Inside: the throne on its golden dais before a shell fan, the royal guard and the
     * treasury chests.
     */
    static void ryuguPalace(Builder b, Island isl, BuildStyle st) {
        Arch.Mats m = Arch.Mats.of(BuildStyle.CORAL, b.seed);
        final int n = 42, cx = 21, hz = 23;
        BlockState salmon = s(ArchBlocks.SALMON_PLASTER), white = s(ArchBlocks.WHITE_PLASTER), pris = s(Blocks.PRISMARINE_BRICKS), dpris = s(Blocks.DARK_PRISMARINE);
        BlockState gold = s(Blocks.GOLD_BLOCK), lamp = s(Blocks.SEA_LANTERN), quartz = s(Blocks.SMOOTH_QUARTZ), sand = s(Blocks.SMOOTH_SANDSTONE);
        BlockState glass = s(Blocks.LIGHT_BLUE_STAINED_GLASS), col = pillar(ArchBlocks.MARBLE_COLUMN), pstair = s(Blocks.PRISMARINE_BRICK_STAIRS);
        b.foundation(0, 0, n, n, Blocks.PRISMARINE_BRICKS, 14);
        for (int x = 0; x <= n; x++)
            for (int z = 0; z <= n; z++) {
                double d = Math.hypot(x - cx, z - cx);
                b.set(x, 0, z, d < 20 ? ((int) Math.floor(d) % 5 == 0 ? pris : sand) : b.rand(x * 31 + z * 17, 3) == 0 ? s(Blocks.SAND) : sand);
            }
        b.clear(0, 1, 0, n, 44, n);
        // the palace bubble: a skin of membrane (no collision; it only shows where the palace's own air ends)
        BlockState skin = ModBlocks.BUBBLE_MEMBRANE.get().defaultBlockState();
        for (int x = 0; x <= n; x++)
            for (int z = 0; z <= n; z++)
                for (int y = 1; y <= 32; y++)
                    if (inBubble(x, y, z) && (!inBubble(x + 1, y, z) || !inBubble(x - 1, y, z) || !inBubble(x, y + 1, z) || !inBubble(x, y, z + 1) || !inBubble(x, y, z - 1)))
                        b.set(x, y, z, skin);
        // podium with the grand stair on the north
        b.fill(8, 1, 9, 34, 1, 34, dpris);
        b.fill(8, 2, 9, 34, 2, 34, pris);
        b.fill(9, 2, 10, 33, 2, 33, quartz);
        for (int x = 10; x <= 32; x += 4) if (x < 16 || x > 26) b.set(x, 1, 9, lamp);
        for (int x = 16; x <= 26; x++) {
            b.set(x, 1, 7, Arch.stair(pstair, S, Half.BOTTOM));
            b.set(x, 1, 8, pris);
            b.set(x, 2, 8, Arch.stair(pstair, S, Half.BOTTOM));
        }
        // four coral towers at the corners of the podium
        coralTower(b, m, 10, 12, 14);
        coralTower(b, m, 32, 12, 14);
        coralTower(b, m, 10, 32, 12);
        coralTower(b, m, 32, 32, 12);
        // the wings: royal chambers (west) and banquet hall (east)
        for (int wx : new int[]{9, 28}) {
            int x1 = wx, x2 = wx + 5, z1 = 17, z2 = 29;
            b.fill(x1, 3, z1, x2, 9, z2, salmon);
            b.clear(x1 + 1, 3, z1 + 1, x2 - 1, 9, z2 - 1);
            b.fill(x1, 3, z1, x2, 3, z2, dpris);
            b.clear(x1 + 1, 3, z1 + 1, x2 - 1, 3, z2 - 1);
            b.fill(x1, 9, z1, x2, 9, z2, white);
            b.clear(x1 + 1, 9, z1 + 1, x2 - 1, 9, z2 - 1);
            for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(c[0], 3, c[1], c[0], 9, c[1], col);
            int outer = wx == 9 ? x1 : x2;
            for (int z = 19; z <= 27; z += 4) {
                b.fill(outer, 4, z, outer, 7, z, LandmarkKit.connected(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Direction.Axis.Z));
                b.set(outer, 8, z, white);
            }
            for (int z : new int[]{z1, z2}) b.fill(x1 + 2, 4, z, x1 + 3, 7, z, LandmarkKit.connected(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Direction.Axis.X));
            Arch.hip(b, m, x1, z1, x2, z2, 10, 1);
        }
        // royal chambers: the princess's great bed, a vanity, flowers
        b.bed(11, 3, 26, Blocks.PINK_BED, S);
        b.bed(12, 3, 26, Blocks.PINK_BED, S);
        b.set(10, 3, 28, LandmarkKit.facing(Blocks.CHISELED_BOOKSHELF, E));
        b.set(13, 3, 28, Arch.decor(ArchBlocks.FLOWER_PLANTER, W));
        b.fill(10, 3, 19, 13, 3, 23, s(Blocks.PINK_CARPET));
        b.set(10, 3, 18, lamp);
        b.set(13, 3, 18, lamp);
        b.set(10, 3, 20, Arch.decor(ArchBlocks.GOODS_SHELF, E));
        // banquet hall: the long table and a kitchen corner
        LandmarkKit.longTableZ(b, 19, 26, 3, 31, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.PRISMARINE_BRICK_STAIRS, true);
        b.set(30, 3, 28, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, N));
        b.set(31, 3, 28, s(Blocks.CRAFTING_TABLE));
        b.set(32, 3, 28, Blocks.BARREL.defaultBlockState());
        b.set(29, 3, 18, lamp);
        b.set(32, 3, 18, lamp);
        // entrance pavilion (x 16..26, z 10..16): a tall portal between marble columns, the shell crest above
        b.fill(16, 3, 10, 26, 15, 16, salmon);
        b.clear(17, 3, 11, 25, 15, 15);
        b.fill(16, 3, 10, 26, 3, 16, dpris);
        b.clear(17, 3, 11, 25, 3, 15);
        b.fill(16, 14, 10, 26, 15, 16, white);
        for (int[] c : new int[][]{{16, 10}, {26, 10}, {16, 16}, {26, 16}, {18, 10}, {24, 10}}) b.fill(c[0], 3, c[1], c[0], 13, c[1], col);
        b.clear(19, 3, 10, 23, 10, 10);
        b.clear(20, 11, 10, 22, 11, 10);
        b.set(19, 11, 10, Arch.stair(s(ArchBlocks.WHITE_PLASTER_STAIRS), W, Half.TOP));
        b.set(23, 11, 10, Arch.stair(s(ArchBlocks.WHITE_PLASTER_STAIRS), E, Half.TOP));
        b.fill(19, 12, 10, 23, 12, 10, white);
        b.set(21, 12, 10, gold);
        for (int x : new int[]{17, 25}) {
            b.fill(x, 5, 10, x, 9, 10, glass);
            b.set(x, 4, 10, white);
            b.set(x, 10, 10, white);
            b.set(x, 12, 10, lamp);
        }
        for (int z : new int[]{12, 14}) {
            b.fill(16, 5, z, 16, 9, z, glass);
            b.fill(26, 5, z, 26, 9, z, glass);
        }
        b.fill(16, 16, 10, 26, 16, 16, white);
        LandmarkKit.outline(b, 16, 17, 10, 26, 16, Blocks.PRISMARINE_WALL);
        for (int x = 14; x <= 28; x++)
            for (int y = 17; y <= 24; y++) {
                int dx = x - cx, dy = y - 17;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > 6.3) continue;
                double a = Math.atan2(dy, dx);
                int ray = (int) Math.floor(a / (Math.PI / 9));
                BlockState c = d < 1.5 ? gold : d >= 5.4 ? white : (ray % 2 == 0 ? salmon : white);
                b.set(x, y, 10, c);
                if (d < 5.4) b.set(x, y, 11, salmon);
            }
        b.set(cx, 24, 10, lamp);
        b.set(cx, 25, 10, gold);
        b.set(cx, 13, 13, hangingLantern(ArchBlocks.BRASS_LANTERN));
        b.fill(cx, 14, 13, cx, 14, 13, s(Blocks.CHAIN));
        // carve the great hall (drum and dome) out of whatever the wings put there
        for (int x = cx - 8; x <= cx + 8; x++)
            for (int z = hz - 8; z <= hz + 8; z++) {
                double d = Math.hypot(x - cx, z - hz);
                if (d < 7.0) b.clear(x, 3, z, x, 21, z);
            }
        // drum: prismarine plinth, salmon walls, white pilasters and frieze, tall windows
        for (int x = cx - 8; x <= cx + 8; x++)
            for (int z = hz - 8; z <= hz + 8; z++) {
                double d = Math.hypot(x - cx, z - hz);
                if (d < 7.0 || d >= 8.0) continue;
                double a = Math.atan2(z - hz, x - cx), w = a / (Math.PI / 6);
                double off = Math.abs(w - Math.round(w)) * (Math.PI / 6) * d;
                double offWin = Math.abs(w - 0.5 - Math.round(w - 0.5)) * (Math.PI / 6) * d;
                b.set(x, 3, z, dpris);
                b.fill(x, 4, z, x, 10, z, off < 0.6 ? col : salmon);
                if (offWin < 0.55) b.fill(x, 5, z, x, 9, z, glass);
                b.set(x, 11, z, white);
            }
        ring(b, cx, hz, 11, 8.4, slab(ArchBlocks.WHITE_PLASTER_SLAB, SlabType.TOP));
        // the scallop-shell dome, ribbed white over salmon, with the pearl on top
        int top = scallopDome(b, cx, 12, hz, 8.3, 9.0, 10, white, s(ArchBlocks.WHITE_PLASTER_STAIRS), s(ArchBlocks.WHITE_PLASTER_SLAB),
                salmon, s(ArchBlocks.SALMON_PLASTER_STAIRS), s(ArchBlocks.SALMON_PLASTER_SLAB));
        disc(b, cx, hz, top + 1, 1.6, gold);
        ball(b, cx, top + 3, hz, 1.6, 1.6, 1.6, s(Blocks.WHITE_CONCRETE), top + 2);
        // openings: hall to pavilion, hall to the wings
        b.clear(19, 3, 13, 23, 9, 16);
        b.clear(20, 10, 15, 22, 10, 16);
        b.set(cx, 13, 13, hangingLantern(ArchBlocks.BRASS_LANTERN));
        b.clear(13, 3, 22, 14, 5, 24);
        b.clear(28, 3, 22, 29, 5, 24);
        // hall floor: prismarine rings in the quartz, a carpet to the throne
        for (int x = cx - 7; x <= cx + 7; x++)
            for (int z = hz - 7; z <= hz + 7; z++) {
                double d = Math.hypot(x - cx, z - hz);
                if (d < 7.0) b.set(x, 2, z, (int) d == 3 || (int) d == 6 ? pris : quartz);
            }
        b.set(cx, 2, hz, gold);
        b.fill(20, 3, 10, 22, 3, 24, s(Blocks.LIGHT_BLUE_CARPET));
        // throne on its golden dais before a shell fan
        for (int x = 18; x <= 24; x++)
            for (int z = 26; z <= 28; z++) if (Math.hypot(x - cx, z - hz) < 6.9) b.set(x, 3, z, gold);
        for (int x = 19; x <= 23; x++) b.set(x, 3, 25, Arch.stair(s(Blocks.QUARTZ_STAIRS), S, Half.BOTTOM));
        b.set(cx, 4, 28, Arch.stair(s(Blocks.QUARTZ_STAIRS), S, Half.BOTTOM));
        b.set(cx - 1, 4, 28, slab(Blocks.SMOOTH_QUARTZ_SLAB, SlabType.BOTTOM));
        b.set(cx + 1, 4, 28, slab(Blocks.SMOOTH_QUARTZ_SLAB, SlabType.BOTTOM));
        // the shell fan behind the throne: a screen of gold and white rays round a glowing pearl
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int y = 4; y <= 10; y++) {
                int dx = x - cx, dy = y - 4;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > 6.3 || Math.hypot(x - cx, 29 - hz) >= 7.0) continue;
                int ray = (int) Math.floor(Math.atan2(dy, dx) / (Math.PI / 7));
                b.set(x, y, 29, d < 1.5 ? lamp : ray % 2 == 0 ? gold : white);
            }
        // columns of dark prismarine with glowing capitals
        for (int[] c : new int[][]{{16, 20}, {26, 20}, {16, 26}, {26, 26}}) {
            b.fill(c[0], 3, c[1], c[0], 9, c[1], dpris);
            b.set(c[0], 10, c[1], lamp);
            b.set(c[0], 11, c[1], pris);
        }
        b.fill(cx, 16, hz, cx, 18, hz, s(Blocks.CHAIN));
        b.set(cx, 15, hz, lamp);
        b.chest(16, 3, 27, E, Buildings.LOOT_TREASURE);
        b.chest(26, 3, 27, W, Buildings.LOOT_TREASURE);
        b.marker("treasure", 16, 3, 27);
        npc(b, "noble", cx, 4, 27);
        npc(b, "fishman_guard", 18, 3, 23);
        npc(b, "fishman_guard", 24, 3, 23);
        // the sea gate on the north edge of the bubble: coral pillars and a salmon arch with a gold shell
        for (int px : new int[]{16, 26}) {
            b.fill(px, 1, 2, px, 7, 2, pris);
            b.set(px, 4, 2, lamp);
            b.set(px, 8, 2, white);
            onion(b, px, 9, 2, 1.3, s(ArchBlocks.SKY_ROOF_TILES), gold);
        }
        for (int x = 17; x <= 25; x++) {
            int y = 8 + (int) Math.round(2.2 * Math.sin(Math.PI * (x - 16) / 10.0));
            b.set(x, y, 2, salmon);
            b.set(x, y - 1, 2, Arch.stair(s(ArchBlocks.SALMON_PLASTER_STAIRS), x < cx ? E : x > cx ? W : S, Half.TOP));
        }
        b.set(cx, 11, 2, gold);
        b.set(cx, 12, 2, gold);
        npc(b, "fishman_guard", 19, 1, 3);
        npc(b, "fishman_guard", 23, 1, 3);
        // coral gardens: in the court and in the corners outside the bubble
        coralTree(b, 4, 1, 21, 6, 3.2, 901, s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.FIRE_CORAL_BLOCK), lamp);
        coralTree(b, 38, 1, 21, 6, 3.2, 902, s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.HORN_CORAL_BLOCK), lamp);
        coralTree(b, 5, 1, 37, 5, 2.6, 903, s(Blocks.TUBE_CORAL_BLOCK), s(Blocks.BRAIN_CORAL_BLOCK), lamp);
        coralTree(b, 37, 1, 37, 5, 2.6, 904, s(Blocks.TUBE_CORAL_BLOCK), s(Blocks.FIRE_CORAL_BLOCK), lamp);
        coralTree(b, 4, 1, 4, 4, 2.2, 905, s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.HORN_CORAL_BLOCK), lamp);
        coralTree(b, 38, 1, 4, 4, 2.2, 906, s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.TUBE_CORAL_BLOCK), lamp);
        for (int[] q : new int[][]{{13, 6}, {29, 6}, {6, 13}, {36, 13}}) {
            b.fill(q[0], 1, q[1], q[0], 2, q[1], LandmarkKit.connect(Blocks.PRISMARINE_WALL, false, false, false, false));
            b.set(q[0], 3, q[1], lamp);
        }
    }

    private static boolean inBubble(int x, int y, int z) {
        double dx = (x - 21) / 20.6, dy = y / 31.0, dz = (z - 21) / 20.6;
        return y >= 1 && dx * dx + dy * dy + dz * dz < 1.0;
    }

    /** A slender coral tower: white shaft banded in salmon, slit windows, a balcony ring and an onion cap with gold. */
    private static void coralTower(Builder b, Arch.Mats m, int x, int z, int top) {
        BlockState salmon = s(ArchBlocks.SALMON_PLASTER), white = s(ArchBlocks.WHITE_PLASTER);
        for (int y = 1; y <= top; y++) disc(b, x, z, y, 2.2, y <= 2 ? s(Blocks.DARK_PRISMARINE) : y % 4 == 2 ? salmon : white);
        for (int y : new int[]{4, 5, 8, 9})
            for (Direction d : Direction.Plane.HORIZONTAL) b.set(x + d.getStepX() * 2, y, z + d.getStepZ() * 2, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
        for (Direction d : Direction.Plane.HORIZONTAL) b.set(x + d.getStepX() * 2, top - 1, z + d.getStepZ() * 2, s(Blocks.SEA_LANTERN));
        ring(b, x, z, top, 3.0, slab(Blocks.PRISMARINE_BRICK_SLAB, SlabType.TOP));
        disc(b, x, z, top + 1, 1.6, salmon);
        onion(b, x, top + 2, z, 2.4, s(ArchBlocks.SKY_ROOF_TILES), s(Blocks.GOLD_BLOCK));
    }

    /**
     * Ribbed scallop-shell dome: a heightmap dome whose rim is scalloped into {@code lobes} lobes, ribs (rib blocks) on
     * the lobes and grooves between, the surface smoothed with stairs facing the centre. Returns the top y.
     */
    static int scallopDome(Builder b, double cx, int y, double cz, double r, double hgt, int lobes, BlockState rib, BlockState ribSt, BlockState ribSl,
                           BlockState groove, BlockState grSt, BlockState grSl) {
        int top = y;
        for (int x = (int) Math.floor(cx - r - 2); x <= (int) Math.ceil(cx + r + 2); x++)
            for (int z = (int) Math.floor(cz - r - 2); z <= (int) Math.ceil(cz + r + 2); z++) {
                double d = Math.hypot(x - cx, z - cz), a = Math.atan2(z - cz, x - cx);
                double ph = Math.cos(lobes * a);
                double ro = (r + 0.3) * (1 + 0.06 * ph);
                if (d > ro) continue;
                double h = hgt * Math.sqrt(Math.max(0, 1 - (d / ro) * (d / ro))) + 0.6 * ph * Math.min(1, d / 3.0);
                double ri = ro - 1.2, hin = d < ri ? (hgt - 1.2) * Math.sqrt(Math.max(0, 1 - (d / ri) * (d / ri))) : 0;
                boolean isRib = ph > 0.6 || d < 1.5;
                BlockState F = isRib ? rib : groove, St = isRib ? ribSt : grSt, Sl = isRib ? ribSl : grSl;
                int hf = (int) Math.floor(h);
                double frac = h - hf;
                for (int k = (int) Math.floor(hin); k < hf; k++) put(b, x, y + k, z, F);
                if (frac >= 0.3) {
                    BlockState cap;
                    if (frac >= 0.8) cap = F;
                    else if (d < 1.0) cap = slab(Sl, SlabType.BOTTOM);
                    else {
                        double ddx = cx - x, ddz = cz - z;
                        Direction toC = Math.abs(ddx) >= Math.abs(ddz) ? (ddx > 0 ? E : W) : (ddz > 0 ? S : N);
                        cap = Arch.stair(St, toC, Half.BOTTOM);
                    }
                    put(b, x, y + hf, z, cap);
                    top = Math.max(top, y + hf);
                } else top = Math.max(top, y + hf - 1);
            }
        return top;
    }

    /**
     * The Mermaid Cove at the harbour line: a rock-rimmed lagoon open to the sea, rocks where the merfolk sit, and the
     * Mermaid Café: a giant pink scallop shell opening onto a deck on pilings, with the bar, tables and parasols.
     */
    static void mermaidCove(Builder b, Island isl, BuildStyle st) {
        final int n = 26;
        final double lx = 13, lz = 10, lr = 9.5;
        BlockState water = s(Blocks.WATER), sand = s(Blocks.SAND), sst = s(Blocks.SANDSTONE), smooth = s(Blocks.SMOOTH_SANDSTONE);
        BlockState salmon = s(ArchBlocks.SALMON_PLASTER), white = s(ArchBlocks.WHITE_PLASTER), lamp = s(Blocks.SEA_LANTERN);
        BlockState dock = s(ArchBlocks.DOCK_PLANKS), beam = pillar(ArchBlocks.WEATHERED_BEAM);
        b.clear(0, 1, 0, n, 16, n);
        for (int x = 0; x <= n; x++)
            for (int z = 0; z <= n; z++) {
                double d = Math.hypot(x - lx, z - lz);
                boolean mouth = z <= 3 && Math.abs(x - lx) <= 3.5;
                b.set(x, -6, z, sand);
                if (d < lr || mouth) {
                    // the lagoon: sea water over a sandy floor with coral, seagrass and pickles
                    b.set(x, -5, z, sand);
                    b.fill(x, -4, z, x, 0, z, water);
                    int r = b.rand(x * 7 + z * 3, 11);
                    if (r == 0) b.set(x, -4, z, s(Blocks.BRAIN_CORAL_BLOCK));
                    else if (r == 1) b.set(x, -4, z, s(Blocks.TUBE_CORAL_BLOCK));
                    else if (r < 5) b.set(x, -4, z, s(Blocks.SEAGRASS));
                    else if (r == 5) b.set(x, -4, z, s(Blocks.SEA_PICKLE));
                } else if (d < lr + 2.6) {
                    // the rocky rim: a rounded ridge, coral-crusted on its inner face
                    double t = (d - lr) / 2.6;
                    int h = (int) Math.round(0.4 + 1.6 * Math.sin(Math.PI * t)) + (b.rand(x * 31 + z, 6) == 0 ? 1 : 0);
                    for (int y = -5; y <= h; y++) b.set(x, y, z, y == h ? smooth : sst);
                    if (t < 0.35 && b.rand(x * 13 + z * 7, 3) == 0) b.set(x, h, z, s(Blocks.BRAIN_CORAL_BLOCK));
                } else {
                    // the beach beyond the rim
                    b.fill(x, -5, z, x, 0, z, sand);
                    if (b.rand(x * 11 + z * 13, 9) == 0) b.set(x, 1, z, s(Blocks.SANDSTONE_SLAB));
                }
            }
        // sea stacks on the rim
        for (int[] q : new int[][]{{2, 13, 4}, {24, 11, 5}, {19, 1, 3}}) {
            for (int y = -5; y <= q[2]; y++) disc(b, q[0], q[1], y, y > q[2] - 2 ? 0.7 : 1.3, y == q[2] ? smooth : sst);
            put(b, q[0], q[2] + 1, q[1], s(Blocks.TUBE_CORAL_BLOCK));
        }
        // rocks in the lagoon where the merfolk sit
        for (int[] c : new int[][]{{6, 8}, {19, 7}, {10, 3}}) {
            b.fill(c[0], -5, c[1], c[0] + 1, 0, c[1] + 1, sst);
            b.fill(c[0], 1, c[1], c[0] + 1, 1, c[1] + 1, smooth);
            b.set(c[0] + 1, 1, c[1] + 1, s(Blocks.SANDSTONE));
        }
        // ---- the café: a rock shelf (z 17..26), the scallop shell over it, opening north onto the deck
        for (int x = 3; x <= 23; x++)
            for (int z = 17; z <= n; z++) {
                b.fill(x, -5, z, x, 0, z, sst);
                double dd = Math.sqrt((x - 13) * (x - 13) + (z - 18) * (z - 18));
                boolean edge = x == 3 || x == 23 || z == 17;
                b.set(x, 1, z, edge ? s(Blocks.PRISMARINE_BRICKS) : dd < 7.0 ? ((x + z) % 2 == 0 ? s(ArchBlocks.WHITE_PLASTER) : s(ArchBlocks.SALMON_PLASTER)) : smooth);
            }
        final int scx = 13, scz = 18;
        for (int x = 4; x <= 22; x++)
            for (int z = scz; z <= n; z++)
                for (int y = 2; y <= 10; y++) {
                    double dx = x - scx, dy = y - 1, dz = z - scz, D = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double a = Math.atan2(dy, dx);
                    int ray = (int) Math.floor(a / (Math.PI / 11));
                    boolean rib = ray % 2 == 0;
                    double outer = 8.0 + (z == scz && rib ? 0.7 : 0);
                    if (D >= 7.0 && D < outer) b.set(x, y, z, rib ? salmon : white);
                }
        b.set(scx, 9, scz, lamp);
        // the bar, the shelves of bottles, the tavern barrel, tables, lamps
        for (int x = 9; x <= 17; x++) b.set(x, 2, 22, Arch.decor(ArchBlocks.SHOP_COUNTER, N));
        for (int x = 11; x <= 15; x++) {
            b.set(x, 2, 24, Arch.decor(ArchBlocks.GOODS_SHELF, N));
            b.set(x, 3, 24, Arch.decor(ArchBlocks.GOODS_SHELF, N));
        }
        b.set(10, 3, 22, s(Blocks.SEA_PICKLE));
        b.set(16, 3, 22, s(Blocks.DECORATED_POT));
        b.barrel(16, 2, 23, Buildings.LOOT_TAVERN);
        b.set(10, 2, 23, Blocks.BARREL.defaultBlockState());
        npc(b, "bartender", 13, 2, 23);
        for (int x : new int[]{8, 18}) {
            b.set(x, 2, 19, Arch.decor(ArchBlocks.PLANK_TABLE, N));
            b.set(x - 1, 2, 19, Arch.stair(s(Blocks.CHERRY_STAIRS), W, Half.BOTTOM));
            b.set(x + 1, 2, 19, Arch.stair(s(Blocks.CHERRY_STAIRS), E, Half.BOTTOM));
            b.set(x, 3, 19, candles(Blocks.PINK_CANDLE, 2));
        }
        for (int[] q : new int[][]{{10, 6, 20}, {16, 6, 20}, {13, 7, 21}}) b.set(q[0], q[1], q[2], hangingLantern(ArchBlocks.WHITE_PAPER_LANTERN));
        // ---- the deck on pilings over the lagoon, a jetty with steps down into the water
        b.fill(5, 1, 13, 21, 1, 16, dock);
        for (int x = 5; x <= 21; x += 4)
            for (int z : new int[]{13, 16}) b.fill(x, -5, z, x, 0, z, beam);
        for (int x = 5; x <= 21; x++) if (x < 12 || x > 14) b.set(x, 2, 13, LandmarkKit.connected(Blocks.DARK_OAK_FENCE, Direction.Axis.X));
        for (int z = 13; z <= 16; z++) {
            b.set(5, 2, z, LandmarkKit.connected(Blocks.DARK_OAK_FENCE, Direction.Axis.Z));
            b.set(21, 2, z, LandmarkKit.connected(Blocks.DARK_OAK_FENCE, Direction.Axis.Z));
        }
        b.fill(12, 1, 8, 14, 1, 12, dock);
        for (int z : new int[]{8, 11}) for (int x : new int[]{12, 14}) b.fill(x, -5, z, x, 0, z, beam);
        for (int x = 12; x <= 14; x++) b.set(x, 0, 7, Arch.stair(s(ArchBlocks.DOCK_PLANK_STAIRS), S, Half.BOTTOM));
        b.set(12, 2, 8, s(ArchBlocks.BOLLARD));
        b.set(14, 2, 9, Arch.decor(ArchBlocks.ROPE_COIL, N));
        for (int[] p : new int[][]{{8, 15}, {18, 15}}) parasolTable(b, p[0], 2, p[1]);
        for (int[] q : new int[][]{{5, 17}, {21, 17}}) {
            b.fill(q[0], 2, q[1], q[0], 3, q[1], LandmarkKit.connect(Blocks.PRISMARINE_WALL, false, false, false, false));
            b.set(q[0], 4, q[1], lamp);
        }
        npc(b, "resident", 6, 2, 8);
        npc(b, "resident", 19, 2, 7);
        npc(b, "child", 13, 2, 15);
    }

    /** A café table on a deck with two stools and a striped parasol over it. */
    private static void parasolTable(Builder b, int x, int y, int z) {
        b.set(x, y, z, Arch.decor(ArchBlocks.PLANK_TABLE, N));
        b.set(x - 1, y, z, Arch.stair(s(Blocks.CHERRY_STAIRS), W, Half.BOTTOM));
        b.set(x + 1, y, z, Arch.stair(s(Blocks.CHERRY_STAIRS), E, Half.BOTTOM));
        b.fill(x, y + 1, z, x, y + 2, z, s(Blocks.BIRCH_FENCE));
        b.set(x, y + 3, z, s(Blocks.WHITE_WOOL));
        for (Direction d : Direction.Plane.HORIZONTAL) b.set(x + d.getStepX(), y + 3, z + d.getStepZ(), Arch.facing(s(ArchBlocks.RED_AWNING), d));
    }

    /**
     * Gyoncorde Plaza, the great square of Fish-Man Island: a mosaic of rings and rays round a three-tier fountain, the
     * speakers' stage under a shell canopy on the south, an entrance arch with the quest board, a market row with
     * awnings, the bounty board, lamps and coral trees.
     */
    static void gyoncordePlaza(Builder b, Island isl, BuildStyle st) {
        final int n = 28, c = 14;
        BlockState pris = s(Blocks.PRISMARINE_BRICKS), dpris = s(Blocks.DARK_PRISMARINE), sand = s(Blocks.SMOOTH_SANDSTONE), lamp = s(Blocks.SEA_LANTERN);
        BlockState sky = s(ArchBlocks.SKY_PLASTER), white = s(ArchBlocks.WHITE_PLASTER), mint = s(ArchBlocks.MINT_PLASTER), salmon = s(ArchBlocks.SALMON_PLASTER);
        b.foundation(0, 0, n, n, Blocks.SMOOTH_SANDSTONE, 8);
        for (int x = 0; x <= n; x++)
            for (int z = 0; z <= n; z++) {
                double d = Math.hypot(x - c, z - c);
                double w = Math.atan2(z - c, x - c) / (Math.PI / 4);
                double off = Math.abs(w - Math.round(w)) * (Math.PI / 4) * d;
                BlockState g = d > 13.6 ? s(Blocks.SAND) : (int) Math.round(d) % 4 == 0 ? pris : (off < 0.5 && d > 5) ? dpris : sand;
                b.set(x, 0, z, g);
            }
        b.clear(0, 1, 0, n, 16, n);
        // the fountain: a wide basin, two water-filled bowls on a coral column, a pearl crown
        for (int x = c - 5; x <= c + 5; x++)
            for (int z = c - 5; z <= c + 5; z++) {
                double d = Math.hypot(x - c, z - c);
                if (d < 4.5) {
                    b.set(x, 0, z, dpris);
                    b.set(x, 1, z, s(Blocks.WATER));
                }
            }
        ring(b, c, c, 1, 4.5, pris);
        ring(b, c, c, 2, 4.5, slab(Blocks.PRISMARINE_BRICK_SLAB, SlabType.BOTTOM));
        b.fill(c, 1, c, c, 7, c, dpris);
        disc(b, c, c, 3, 2.2, pris);
        ring(b, c, c, 4, 2.2, pris);
        disc(b, c, c, 4, 1.6, s(Blocks.WATER));
        b.fill(c, 4, c, c, 6, c, dpris);
        disc(b, c, c, 6, 1.2, pris);
        ring(b, c, c, 7, 1.2, slab(Blocks.PRISMARINE_BRICK_SLAB, SlabType.BOTTOM));
        b.set(c, 7, c, lamp);
        ball(b, c, 9, c, 1.2, 1.2, 1.2, s(Blocks.WHITE_CONCRETE), 8);
        for (Direction d : Direction.Plane.HORIZONTAL) b.set(c + d.getStepX() * 3, 1, c + d.getStepZ() * 3, s(Blocks.SEA_PICKLE));
        // the stage on the south under a shell canopy
        for (int x = 6; x <= 22; x++)
            for (int z = 21; z <= 27; z++) {
                if (Math.hypot(x - c, z - 21) > 7.4 && z > 21) continue;
                b.set(x, 1, z, z == 21 || Math.hypot(x - c, z - 21) > 6.5 ? pris : s(Blocks.SMOOTH_QUARTZ));
            }
        for (int x = 9; x <= 19; x++) b.set(x, 1, 20, Arch.stair(s(Blocks.PRISMARINE_BRICK_STAIRS), S, Half.BOTTOM));
        for (int x = 6; x <= 22; x++)
            for (int z = 21; z <= n; z++)
                for (int y = 2; y <= 9; y++) {
                    double dx = x - c, dy = y - 1, dz = z - 21, D = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    int ray = (int) Math.floor(Math.atan2(dy, dx) / (Math.PI / 9));
                    boolean rib = ray % 2 == 0;
                    double outer = 7.4 + (z == 21 && rib ? 0.6 : 0);
                    if (D >= 6.4 && D < outer) b.set(x, y, z, rib ? sky : white);
                }
        b.set(c, 8, 21, lamp);
        b.set(c, 8, 20, s(Blocks.GOLD_BLOCK));
        b.set(c, 2, 25, LandmarkKit.facing(Blocks.LECTERN, N));
        for (int x : new int[]{10, 18}) {
            b.set(x, 2, 23, s(ArchBlocks.MARBLE_COLUMN));
            b.set(x, 3, 23, lamp);
        }
        // the entrance arch on the north with the quest board beneath it
        for (int px : new int[]{9, 19}) {
            b.fill(px, 1, 1, px, 6, 1, pris);
            b.set(px, 3, 1, lamp);
            b.set(px, 7, 1, mint);
        }
        for (int x = 10; x <= 18; x++) {
            int y = 7 + (int) Math.round(1.6 * Math.sin(Math.PI * (x - 9) / 10.0));
            b.set(x, y, 1, mint);
            b.set(x, y - 1, 1, Arch.stair(s(ArchBlocks.MINT_PLASTER_STAIRS), x < c ? E : x > c ? W : S, Half.TOP));
        }
        b.set(c, 9, 1, s(Blocks.GOLD_BLOCK));
        b.fill(12, 1, 3, 12, 3, 3, s(Blocks.DARK_OAK_FENCE));
        b.fill(16, 1, 3, 16, 3, 3, s(Blocks.DARK_OAK_FENCE));
        b.fill(13, 2, 3, 15, 3, 3, s(Blocks.SPRUCE_PLANKS));
        b.fill(12, 4, 3, 16, 4, 3, slab(ArchBlocks.SKY_ROOF_TILE_SLAB, SlabType.BOTTOM));
        b.set(14, 2, 2, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.WallSignBlock.FACING, N));
        b.marker("quest_board", 14, 2, 3);
        // the bounty board on the west side
        b.fill(1, 1, 12, 1, 3, 12, s(Blocks.DARK_OAK_FENCE));
        b.fill(1, 1, 16, 1, 3, 16, s(Blocks.DARK_OAK_FENCE));
        b.fill(1, 2, 13, 1, 3, 15, s(Blocks.DARK_OAK_PLANKS));
        b.fill(1, 4, 12, 1, 4, 16, slab(ArchBlocks.SKY_ROOF_TILE_SLAB, SlabType.BOTTOM));
        b.set(2, 2, 14, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.WallSignBlock.FACING, E));
        b.marker("bounty_board", 1, 2, 14);
        // market row on the east: two stalls with striped awnings
        for (int sz : new int[]{9, 16}) {
            b.fill(25, 1, sz, 25, 3, sz, s(Blocks.WARPED_FENCE));
            b.fill(25, 1, sz + 3, 25, 3, sz + 3, s(Blocks.WARPED_FENCE));
            b.fill(27, 1, sz, 27, 3, sz + 3, salmon);
            for (int z = sz; z <= sz + 3; z++) {
                b.set(25, 4, z, Arch.facing(s(sz == 9 ? ArchBlocks.BLUE_AWNING : ArchBlocks.GREEN_AWNING), W));
                b.set(26, 4, z, Arch.facing(s(sz == 9 ? ArchBlocks.BLUE_AWNING : ArchBlocks.GREEN_AWNING), W));
                b.set(27, 4, z, salmon);
            }
            for (int z = sz + 1; z <= sz + 2; z++) b.set(25, 1, z, Arch.decor(ArchBlocks.SHOP_COUNTER, W));
            b.set(27, 2, sz + 1, Arch.decor(ArchBlocks.GOODS_SHELF, W));
            b.set(27, 2, sz + 2, Arch.decor(ArchBlocks.GOODS_SHELF, W));
            b.set(25, 2, sz + 1, s(Blocks.SEA_PICKLE));
            b.set(25, 2, sz + 2, s(Blocks.DECORATED_POT));
        }
        b.set(26, 1, 14, Arch.decor(ArchBlocks.CARGO_CRATE, W));
        b.set(26, 2, 14, Arch.decor(ArchBlocks.CARGO_SACKS, W));
        npc(b, "merchant", 26, 1, 10);
        // lamps round the square, benches, coral trees in the corners
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 8 + k * Math.PI / 4;
            int x = (int) Math.round(c + Math.cos(a) * 9.5), z = (int) Math.round(c + Math.sin(a) * 9.5);
            if (z >= 19) continue;
            b.fill(x, 1, z, x, 2, z, LandmarkKit.connect(Blocks.PRISMARINE_WALL, false, false, false, false));
            b.set(x, 3, z, lamp);
        }
        for (int[] q : new int[][]{{8, 8, 0}, {20, 8, 0}, {8, 19, 1}, {20, 19, 1}}) b.set(q[0], 1, q[1], Arch.decor(ArchBlocks.PLANK_BENCH, q[2] == 0 ? N : S));
        coralTree(b, 2, 1, 2, 5, 2.4, 911, s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.FIRE_CORAL_BLOCK), lamp);
        coralTree(b, 26, 1, 2, 5, 2.4, 912, s(Blocks.TUBE_CORAL_BLOCK), s(Blocks.HORN_CORAL_BLOCK), lamp);
        coralTree(b, 2, 1, 26, 5, 2.4, 913, s(Blocks.HORN_CORAL_BLOCK), s(Blocks.BRAIN_CORAL_BLOCK), lamp);
        coralTree(b, 26, 1, 26, 5, 2.4, 914, s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.TUBE_CORAL_BLOCK), lamp);
        npc(b, "resident", 18, 1, 17);
        npc(b, "fishman_guard", 20, 1, 6);
        npc(b, "child", 9, 1, 18);
    }

    /**
     * The Sea Forest: the graveyard of ships that sank into the deep, under giant coral trees, inside its bubble. A
     * half-buried galleon and a broken brig with their cargo, Queen Otohime's memorial pavilion with its shrine chest,
     * rows of sailors' graves, the keeper's lean-to, and the poneglyph standing among the wrecks.
     */
    static void seaForest(Builder b, Island isl, BuildStyle st) {
        final int n = 36;
        BlockState sand = s(Blocks.SAND), lamp = s(Blocks.SEA_LANTERN), white = s(ArchBlocks.WHITE_PLASTER), salmon = s(ArchBlocks.SALMON_PLASTER);
        b.foundation(0, 0, n, n, Blocks.SAND, 8);
        for (int x = 0; x <= n; x++)
            for (int z = 0; z <= n; z++) {
                int r = b.rand(x * 7919 + z * 104729, 14);
                b.set(x, 0, z, r == 0 ? s(Blocks.GRAVEL) : r == 1 ? s(Blocks.DEAD_BRAIN_CORAL_BLOCK) : r == 2 ? s(Blocks.SANDSTONE) : sand);
            }
        b.clear(0, 1, 0, n, 34, n);
        // giant coral trees: the forest the place is named for
        bigCoral(b, 25, 13, 15, 5.5, 921);
        bigCoral(b, 8, 6, 10, 3.6, 922);
        coralTree(b, 31, 1, 30, 6, 2.8, 923, s(Blocks.DEAD_TUBE_CORAL_BLOCK), s(Blocks.TUBE_CORAL_BLOCK), lamp);
        coralTree(b, 3, 1, 22, 5, 2.4, 924, s(Blocks.DEAD_FIRE_CORAL_BLOCK), s(Blocks.FIRE_CORAL_BLOCK), lamp);
        // the galleon: half sunk in the sand, listing, masts broken
        wreck(b, 2, 29, 20, 3, 931);
        b.chest(9, 1, 29, N, Buildings.LOOT_WRECK);
        // the brig: smaller, bow up on the sand
        wreckZ(b, 32, 2, 11, 2, 932);
        b.chest(32, 1, 6, W, Buildings.LOOT_WRECK);
        // Queen Otohime's memorial: a white pavilion with a salmon dome, flowers and the shrine chest
        int mx = 12, mz = 16;
        b.fill(mx - 4, 1, mz - 4, mx + 4, 1, mz + 4, s(Blocks.SMOOTH_QUARTZ));
        LandmarksD2.stairRing(b, mx - 4, mz - 4, mx + 4, mz + 4, 1, s(Blocks.QUARTZ_STAIRS), Half.BOTTOM);
        for (int[] q : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) b.fill(mx + q[0], 2, mz + q[1], mx + q[0], 5, mz + q[1], pillar(ArchBlocks.MARBLE_COLUMN));
        b.fill(mx - 3, 6, mz - 3, mx + 3, 6, mz + 3, white);
        stairRing(b, mx - 4, mz - 4, mx + 4, mz + 4, 6, s(ArchBlocks.WHITE_PLASTER_STAIRS), Half.TOP);
        int dt = smoothDome(b, mx, 7, mz, 3.2, 3.4, salmon, s(ArchBlocks.SALMON_PLASTER_STAIRS), s(ArchBlocks.SALMON_PLASTER_SLAB),
                white, s(ArchBlocks.WHITE_PLASTER_STAIRS), s(ArchBlocks.WHITE_PLASTER_SLAB), 4, Math.PI / 4);
        b.set(mx, dt + 1, mz, s(Blocks.GOLD_BLOCK));
        for (int x = mx - 3; x <= mx + 3; x++) for (int z = mz - 3; z <= mz + 3; z++) if (x == mx - 3 || x == mx + 3 || z == mz + 3) if (Math.abs(x - mx) != 3 || Math.abs(z - mz) != 3) b.set(x, 2, z, LandmarkKit.connected(Blocks.PRISMARINE_WALL, x == mx - 3 || x == mx + 3 ? Direction.Axis.Z : Direction.Axis.X));
        b.fill(mx - 1, 2, mz + 1, mx + 1, 2, mz + 1, s(Blocks.POLISHED_DIORITE));
        b.set(mx, 3, mz + 1, s(Blocks.POLISHED_DIORITE));
        b.set(mx, 4, mz + 1, s(Blocks.PEARLESCENT_FROGLIGHT));
        b.set(mx - 1, 3, mz + 1, candles(Blocks.WHITE_CANDLE, 3));
        b.set(mx + 1, 3, mz + 1, candles(Blocks.WHITE_CANDLE, 3));
        b.set(mx - 2, 2, mz + 2, s(Blocks.POTTED_WHITE_TULIP));
        b.set(mx + 2, 2, mz + 2, s(Blocks.POTTED_ALLIUM));
        b.chest(mx, 2, mz + 2, N, Buildings.LOOT_SHRINE);
        b.marker("treasure", mx, 2, mz + 2);
        b.set(mx, 5, mz, hangingLantern(Blocks.LANTERN));
        // rows of sailors' graves before the memorial
        for (int row = 0; row < 2; row++)
            for (int i = 0; i < 6; i++) {
                int gx = 4 + i * 3 + (row == 1 ? 1 : 0), gz = 24 - row * 3;
                if (gx >= 18) continue;
                b.set(gx, 0, gz + 1, s(Blocks.COARSE_DIRT));
                if ((i + row) % 3 == 0) {
                    b.set(gx, 1, gz, s(Blocks.DARK_OAK_FENCE));
                    b.set(gx, 2, gz, LandmarkKit.connected(Blocks.DARK_OAK_FENCE, Direction.Axis.X));
                    b.set(gx, 3, gz, s(Blocks.DARK_OAK_FENCE));
                } else {
                    b.set(gx, 1, gz, LandmarkKit.connected(Blocks.STONE_BRICK_WALL, Direction.Axis.X));
                    b.set(gx, 2, gz, candles(Blocks.CANDLE, 1 + (i % 3)));
                }
            }
        // the keeper's hut, built of ship's timbers: plank walls on dark beams, a lean-to roof, a door to the east
        BlockState wp = s(ArchBlocks.WEATHERED_PLANKS), db = pillar(ArchBlocks.DARK_BEAM);
        b.fill(18, 0, 2, 22, 0, 6, s(Blocks.SPRUCE_PLANKS));
        b.fill(18, 1, 2, 22, 3, 6, wp);
        b.clear(19, 1, 3, 21, 3, 5);
        for (int[] q : new int[][]{{18, 2}, {22, 2}, {18, 6}, {22, 6}}) b.fill(q[0], 1, q[1], q[0], 3, q[1], db);
        Arch.Mats hm = Arch.Mats.of(BuildStyle.GIANT, b.seed);
        hm.roof = wp;
        hm.roofStairs = s(ArchBlocks.WEATHERED_PLANK_STAIRS);
        hm.roofSlab = s(ArchBlocks.WEATHERED_PLANK_SLAB);
        hm.roofKind = Arch.RoofKind.GABLE;
        hm.eaveBrackets = false;
        Arch.gable(b, hm, 18, 2, 22, 6, 4, true, 1, wp);
        b.door(22, 1, 4, Blocks.SPRUCE_DOOR, E);
        b.set(20, 2, 6, LandmarkKit.connected(Blocks.GLASS_PANE, Direction.Axis.X));
        b.set(18, 2, 4, LandmarkKit.connected(Blocks.GLASS_PANE, Direction.Axis.Z));
        b.set(19, 1, 3, Arch.decor(ArchBlocks.PLANK_TABLE, S));
        b.set(19, 2, 3, candles(Blocks.CANDLE, 2));
        b.set(20, 1, 3, Arch.stair(s(Blocks.SPRUCE_STAIRS), E, Half.BOTTOM));
        b.set(21, 1, 5, Blocks.BARREL.defaultBlockState());
        b.bed(19, 1, 5, Blocks.BROWN_BED, E);
        b.set(20, 3, 4, hangingLantern(Blocks.LANTERN));
        b.set(23, 2, 3, Arch.decor(ArchBlocks.FISHING_NET, E));
        b.set(23, 1, 5, Arch.decor(ArchBlocks.ROPE_COIL, E));
        npc(b, "keeper", 14, 1, 23);
        // the stone the late queen died defending
        b.fill(19, 1, 20, 21, 1, 22, s(Blocks.STONE_BRICKS));
        b.fill(20, 2, 21, 20, 4, 21, ModBlocks.PONEGLYPH.get());
        b.marker("poneglyph", 20, 2, 21);
        // scattered cargo and remains in the sand
        Arch.cargo(b, 24, 1, 27, 1);
        Arch.cargo(b, 26, 1, 24, 2);
        b.set(5, 1, 3, s(Blocks.SKELETON_SKULL));
        b.set(33, 1, 17, Arch.decor(ArchBlocks.DECK_CANNON, W));
        b.set(30, 1, 22, Blocks.BARREL.defaultBlockState());
    }

    /** A giant coral tree: a gnarled pale trunk with buttress roots, forking limbs and crowns of living coral. */
    private static void bigCoral(Builder b, int x0, int z0, int h, double spread, int salt) {
        BlockState trunk = s(Blocks.DEAD_BRAIN_CORAL_BLOCK);
        BlockState[] crowns = {s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.FIRE_CORAL_BLOCK), s(Blocks.HORN_CORAL_BLOCK), s(Blocks.TUBE_CORAL_BLOCK)};
        for (int y = 1; y <= h; y++) disc(b, x0, z0, y, y < 3 ? 2.2 : y < h * 0.6 ? 1.6 : 1.1, trunk);
        for (int k = 0; k < 5; k++) {
            double a = k * 2 * Math.PI / 5 + 0.4;
            for (double r = 2; r <= 4.5; r += 0.5) put(b, (int) Math.round(x0 + Math.cos(a) * r), 1 + (r < 3 ? 1 : 0), (int) Math.round(z0 + Math.sin(a) * r), trunk);
        }
        int limbs = 6;
        for (int i = 0; i < limbs; i++) {
            double a = i * 2 * Math.PI / limbs + b.rand(salt + i, 60) / 100.0;
            int y0 = (int) (h * 0.55) + b.rand(salt + 11 * i, Math.max(1, h / 3));
            double len = spread * (0.75 + 0.25 * b.rand(salt + 5 * i, 100) / 100.0);
            double px = x0, pz = z0;
            int py = y0;
            for (int k = 1; k <= (int) Math.round(len); k++) {
                px = x0 + Math.cos(a) * k;
                pz = z0 + Math.sin(a) * k;
                py = y0 + (int) Math.round(k * 0.7);
                put(b, (int) Math.round(px), py, (int) Math.round(pz), trunk);
                put(b, (int) Math.round(px), py - 1, (int) Math.round(pz), trunk);
            }
            ball(b, px, py + 1, pz, 2.3, 1.6, 2.3, crowns[(i + salt) % crowns.length], -999);
            if (i % 3 == 0) put(b, (int) Math.round(px), py - 1, (int) Math.round(pz), s(Blocks.SEA_LANTERN));
        }
        ball(b, x0, h + 1, z0, 3.0, 2.0, 3.0, crowns[salt % crowns.length], -999);
    }

    /**
     * A galleon wreck along local x from x0 (stern) for len blocks, centred on z0, half sunk and listing: planked on
     * the low side, ribs bare on the high side, a raised sterncastle, broken masts and a fallen yard.
     */
    private static void wreck(Builder b, int x0, int z0, int len, int halfW, int salt) {
        BlockState plank = s(Blocks.DARK_OAK_PLANKS), wale = s(ArchBlocks.WEATHERED_PLANKS), rib = LandmarkKit.axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y);
        BlockState beam = LandmarkKit.axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z), sand = s(Blocks.SAND);
        for (int i = 0; i < len; i++) {
            double t = i / (double) (len - 1);
            double w = halfW * (t > 0.6 ? Math.max(0.2, (1 - t) / 0.4) : t < 0.1 ? 0.85 : 1.0);
            int hw = Math.max(1, (int) Math.round(w));
            int x = x0 + i;
            boolean bare = i >= len / 2 && i <= len / 2 + 3;
            int stern = i < 4 ? 3 : 0;
            for (int k = -hw; k <= hw; k++) b.set(x, 0, z0 + k, plank);
            // low (port) side planked to the rail, high (starboard) side open to the ribs amidships
            int lowTop = 2 + stern, highTop = 3 + stern + (t > 0.85 ? 1 : 0);
            for (int y = 1; y <= lowTop; y++) b.set(x, y, z0 - hw, y == 2 ? wale : plank);
            for (int y = 1; y <= highTop; y++) {
                if (bare && (i % 2 == 1 || y > 1)) {
                    if (i % 2 == 0) b.set(x, y, z0 + hw, rib);
                    continue;
                }
                b.set(x, y, z0 + hw, y == 2 ? wale : plank);
            }
            if (i % 3 == 0 && !bare && hw > 1) for (int k = -hw + 1; k <= hw - 1; k++) b.set(x, lowTop, z0 + k, beam);
            // sand drifted up against the low side and into the hold
            put(b, x, 1, z0 - hw - 1, sand);
            if (b.rand(salt + i, 2) == 0) put(b, x, 2, z0 - hw - 1, slab(Blocks.SANDSTONE_SLAB, SlabType.BOTTOM));
            if (b.rand(salt + 5 * i, 3) == 0 && hw > 1) b.set(x, 1, z0 + b.rand(salt + 7 * i, 2 * hw - 1) - hw + 1, sand);
        }
        // sterncastle: a deck, a rail and the captain's window
        b.fill(x0, 5, z0 - halfW + 1, x0 + 3, 5, z0 + halfW - 1, plank);
        for (int k = -halfW + 1; k <= halfW - 1; k++) b.set(x0, 6, z0 + k, LandmarkKit.connected(Blocks.DARK_OAK_FENCE, Direction.Axis.Z));
        b.fill(x0, 3, z0 - 1, x0, 4, z0 + 1, s(Blocks.GLASS_PANE));
        b.set(x0 + 1, 4, z0, hangingLantern(Blocks.LANTERN));
        // masts: one standing with its yard and a torn sail, one broken off and fallen across the sand
        int m1 = x0 + len / 3;
        b.fill(m1, 1, z0, m1, 10, z0, s(Blocks.DARK_OAK_LOG));
        b.fill(m1, 8, z0 - 3, m1, 8, z0 + 3, beam);
        b.fill(m1, 6, z0 - 2, m1, 7, z0 - 1, s(Blocks.WHITE_WOOL));
        b.set(m1, 5, z0 - 2, s(Blocks.WHITE_WOOL));
        int m2 = x0 + 2 * len / 3;
        b.fill(m2, 1, z0, m2, 3, z0, s(Blocks.DARK_OAK_LOG));
        for (int k = 1; k <= 6; k++) put(b, m2 + k, 1, z0 + halfW + 1 + (k > 3 ? 1 : 0), LandmarkKit.axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        // bow and bowsprit
        b.set(x0 + len, 2, z0, LandmarkKit.axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        b.set(x0 + len + 1, 3, z0, LandmarkKit.axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        b.set(x0 + len, 1, z0, plank);
    }

    /** A smaller brig wreck along local z (bow at z0), its bow pushed up onto the sand. */
    private static void wreckZ(Builder b, int x0, int z0, int len, int halfW, int salt) {
        BlockState plank = s(Blocks.SPRUCE_PLANKS), weathered = s(ArchBlocks.WEATHERED_PLANKS);
        for (int i = 0; i < len; i++) {
            double t = i / (double) (len - 1);
            int hw = t < 0.25 ? Math.max(1, (int) Math.round(halfW * (0.4 + t / 0.25 * 0.6))) : halfW;
            int z = z0 + i, lift = i < 3 ? 3 - i : 0;
            for (int k = -hw; k <= hw; k++) b.set(x0 + k, lift, z, plank);
            for (int y = 1; y <= 3; y++) {
                b.set(x0 - hw, y + lift, z, (y + i) % 4 == 0 ? weathered : plank);
                if (i % 3 != 1 || y == 1) b.set(x0 + hw, y + lift, z, (y + i) % 4 == 1 ? weathered : plank);
            }
            if (lift > 0) b.fill(x0 - hw, 0, z, x0 + hw, lift - 1, z, s(Blocks.SAND));
        }
        b.fill(x0, 1, z0 + len / 2, x0, 6, z0 + len / 2, s(Blocks.SPRUCE_LOG));
        b.set(x0, 7, z0 + len / 2, s(Blocks.SPRUCE_FENCE));
        b.set(x0, 1, z0 + len - 2, Arch.decor(ArchBlocks.CARGO_CRATE, N));
    }
}
