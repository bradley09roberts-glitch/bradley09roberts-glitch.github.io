package com.grandline.world.landmark;

import com.grandline.registry.ArchBlocks;
import com.grandline.registry.ModBlocks;
import com.grandline.world.Island;
import com.grandline.world.build.Arch;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import static com.grandline.world.landmark.LandmarksD2.*;

/**
 * Set pieces of the White Sea (sky dimension): Heaven's Gate, Angel Island's chapel, Shandora and its Golden Bell on
 * Giant Jack, the sacrificial altar, Weatheria's lab and the Cloud End. The deep-sea pieces of Fish-Man Island live in
 * {@link LandmarksD2}. Coordinates are local to each footprint; the front (approach) is local north (z = 0).
 */
final class DimensionLandmarks {
    interface Registrar {
        void reg(String id, Landmarks.Spec spec, Landmarks.LandmarkBuilder builder);
    }

    static void registerAll(Registrar r) {
        r.reg("heavens_gate", new Landmarks.Spec(23, 17, true, false, 0), DimensionLandmarks::heavensGate);
        r.reg("cloud_chapel", new Landmarks.Spec(19, 19, true, false, 0), DimensionLandmarks::cloudChapel);
        r.reg("shandora_ruins", new Landmarks.Spec(47, 47, true, false, 0), DimensionLandmarks::shandoraRuins);
        r.reg("golden_belfry", new Landmarks.Spec(17, 17, true, false, 0), DimensionLandmarks::goldenBelfry);
        r.reg("sacrificial_altar", new Landmarks.Spec(17, 17, true, false, 0), DimensionLandmarks::sacrificialAltar);
        r.reg("weather_lab", new Landmarks.Spec(21, 21, true, false, 0), DimensionLandmarks::weatherLab);
        r.reg("cloud_end_gate", new Landmarks.Spec(13, 11, true, false, 0), DimensionLandmarks::cloudEndGate);
        r.reg("ryugu_palace", new Landmarks.Spec(43, 43, true, false, 0), LandmarksD2::ryuguPalace);
        r.reg("mermaid_cove", new Landmarks.Spec(27, 27, false, true, 0), LandmarksD2::mermaidCove);
        r.reg("gyoncorde_plaza", new Landmarks.Spec(29, 29, true, false, 0), LandmarksD2::gyoncordePlaza);
        r.reg("sea_forest_graveyard", new Landmarks.Spec(37, 37, true, false, 0), LandmarksD2::seaForest);
    }

    /** NPC marker; its two cells are cleared so it always has room to stand. */
    private static void npc(Builder b, String role, int x, int y, int z) {
        b.clear(x, y, z, x, y + 1, z);
        b.marker("npc", x, y, z, LandmarkKit.role(role));
    }

    private static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;
    private static final Half BOT = Half.BOTTOM, TOP = Half.TOP;

    // ------------------------------------------------------------------ Heaven's Gate

    /**
     * Heaven's Gate: the white gate at the top of the cloud stair where every visitor to Skypiea pays the toll. Two
     * fluted pillars with sky-blue panels and gold bands carry an arched beam with its name plaque and a winged sun;
     * clouds wreathe the capitals; the toll booth with its teller window stands at the roadside.
     */
    static void heavensGate(Builder b, Island isl, BuildStyle st) {
        Arch.Mats m = Arch.Mats.of(BuildStyle.CLOUD, b.seed);
        BlockState white = s(ArchBlocks.WHITE_PLASTER), sky = s(ArchBlocks.SKY_PLASTER), gold = s(Blocks.GOLD_BLOCK), cl = cloud();
        BlockState quartz = s(Blocks.SMOOTH_QUARTZ), col = pillar(ArchBlocks.MARBLE_COLUMN), wst = s(ArchBlocks.WHITE_PLASTER_STAIRS);
        b.foundation(0, 0, 22, 16, ModBlocks.ISLAND_CLOUD.get(), 8);
        b.fill(0, 0, 0, 22, 0, 16, cl);
        b.clear(0, 1, 0, 22, 30, 16);
        // the road through the gate: quartz between sky-blue kerbs
        for (int z = 0; z <= 16; z++) for (int x = 8; x <= 14; x++) b.set(x, 0, z, x == 8 || x == 14 ? sky : quartz);
        // gate terrace: a sky-blue plinth course under white plaster, steps up from the front and down at the back
        b.fill(2, 1, 7, 20, 1, 12, sky);
        b.fill(2, 2, 7, 20, 2, 12, white);
        b.fill(7, 2, 7, 15, 2, 12, quartz);
        for (int x = 7; x <= 15; x++) {
            b.set(x, 1, 5, Arch.stair(wst, S, BOT));
            b.set(x, 1, 6, white);
            b.set(x, 2, 6, Arch.stair(wst, S, BOT));
            b.set(x, 1, 13, white);
            b.set(x, 2, 13, Arch.stair(wst, N, BOT));
            b.set(x, 1, 14, Arch.stair(wst, N, BOT));
        }
        // the two pillars: marble corner columns, sky panels framed by gold, moulded base and corbelled capital
        for (int px : new int[]{3, 16}) {
            int x1 = px, x2 = px + 3, z1 = 8, z2 = 11;
            stairRing(b, x1 - 1, z1 - 1, x2 + 1, z2 + 1, 3, wst, BOT);
            b.fill(x1, 3, z1, x2, 15, z2, white);
            for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(c[0], 3, c[1], c[0], 15, c[1], col);
            for (int k = 1; k <= 2; k++) {
                for (int[] f : new int[][]{{x1 + k, z1}, {x1 + k, z2}, {x1, z1 + k}, {x2, z1 + k}}) {
                    b.fill(f[0], 5, f[1], f[0], 13, f[1], sky);
                    b.set(f[0], 4, f[1], gold);
                    b.set(f[0], 14, f[1], gold);
                    b.set(f[0], 15, f[1], s(Blocks.PEARLESCENT_FROGLIGHT));
                }
            }
            stairRing(b, x1 - 1, z1 - 1, x2 + 1, z2 + 1, 16, wst, TOP);
            b.fill(x1, 16, z1, x2, 16, z2, white);
            b.fill(x1 - 1, 17, z1 - 1, x2 + 1, 17, z2 + 1, white);
        }
        // the beam, with a round arch sprung between the pillars
        b.fill(2, 18, 8, 20, 20, 11, white);
        int[] soffit = {16, 16, 16, 15, 14};
        for (int x = 7; x <= 15; x++) {
            int dx = Math.abs(x - 11), sy = soffit[dx];
            b.fill(x, sy + 1, 8, x, 17, 11, white);
            b.fill(x, sy, 8, x, sy, 11, dx >= 2 ? Arch.stair(wst, x < 11 ? W : E, TOP) : white);
        }
        b.fill(11, 16, 8, 11, 17, 11, gold); // keystone
        b.set(11, 15, 9, bellHanging(E));
        b.set(9, 15, 9, hangingLantern(ArchBlocks.BRASS_LANTERN));
        b.set(13, 15, 10, hangingLantern(ArchBlocks.BRASS_LANTERN));
        // cornice and the name plaques front and back (sky-blue field, gold frame)
        stairRing(b, 2, 8, 20, 11, 21, wst, BOT);
        b.fill(3, 21, 9, 19, 21, 10, white);
        for (int pz : new int[]{7, 12})
            for (int x = 6; x <= 16; x++)
                for (int y = 18; y <= 21; y++) b.set(x, y, pz, x == 6 || x == 16 || y == 18 || y == 21 ? gold : sky);
        // crest: the golden sun rising out of the clouds over the gate
        for (int x = 8; x <= 14; x++)
            for (int y = 22; y <= 26; y++) {
                int dx = x - 11, dy = y - 24;
                if (dx * dx + dy * dy <= 6) b.fill(x, y, 9, x, y, 10, gold);
            }
        b.fill(11, 24, 9, 11, 24, 10, s(Blocks.PEARLESCENT_FROGLIGHT));
        for (double a : new double[]{0.35, 0.95, Math.PI / 2, Math.PI - 0.95, Math.PI - 0.35})
            for (double rr = 3.0; rr <= 4.7; rr += 0.85) {
                int x = 11 + (int) Math.round(Math.cos(a) * rr), y = 24 + (int) Math.round(Math.sin(a) * rr);
                b.fill(x, y, 9, x, y, 10, gold);
            }
        cloudBall(b, 5.0, 22.6, 9.5, 4.2, 2.4, 2.8, 17, cl);
        cloudBall(b, 17.0, 22.6, 9.5, 4.2, 2.4, 2.8, 18, cl);
        cloudBall(b, 1.2, 16.8, 9.5, 1.9, 1.7, 3.2, 15, cl);
        cloudBall(b, 20.8, 16.8, 9.5, 1.9, 1.7, 3.2, 16, cl);
        // clouds banked round the foot of the terrace
        puff(b, 0.8, 1, 9.5, 2.4, 3.8, 2.4, 11, cl);
        puff(b, 21.2, 1, 9.5, 2.4, 3.8, 2.4, 12, cl);
        puff(b, 2.5, 1, 15.4, 3.6, 2.2, 1.8, 13, cl);
        puff(b, 19.5, 1, 15.4, 3.6, 2.2, 1.8, 14, cl);
        puff(b, 4.5, 1, 6.0, 2.6, 1.6, 1.0, 19, cl);

        // toll booth: white plaster under a sky-scale hip roof, a teller window on the road with a counter
        b.fill(17, 0, 1, 21, 0, 5, s(Blocks.BIRCH_PLANKS));
        b.fill(17, 1, 1, 21, 3, 5, white);
        b.clear(18, 1, 2, 20, 3, 4);
        for (int[] c : new int[][]{{17, 1}, {21, 1}, {17, 5}, {21, 5}}) b.fill(c[0], 1, c[1], c[0], 3, c[1], sky);
        b.fill(17, 4, 1, 21, 4, 5, sky);
        int roofTop = Arch.hip(b, m, 17, 1, 21, 5, 5, 1);
        b.set(19, roofTop + 1, 3, standingLantern(ArchBlocks.BRASS_LANTERN));
        for (int z = 2; z <= 4; z++) {
            b.set(17, 2, z, Blocks.AIR);
            b.set(18, 1, z, Arch.decor(ArchBlocks.SHOP_COUNTER, W));
            b.set(16, 3, z, Arch.facing(s(ArchBlocks.BLUE_AWNING), W));
        }
        b.set(18, 2, 3, s(Blocks.DECORATED_POT));
        b.bigDoor(19, 1, 5, Blocks.BIRCH_DOOR, S);
        Arch.window(b, m, 19, 2, 1, N, 2, false);
        Arch.window(b, m, 21, 2, 3, E, 2, false);
        b.set(20, 1, 2, LandmarkKit.facing(Blocks.LECTERN, W));
        b.chest(20, 1, 4, W, Buildings.LOOT_SHOP);
        b.set(20, 3, 3, hangingLantern(ArchBlocks.BRASS_LANTERN));
        npc(b, "keeper", 19, 1, 3);
        // waiting benches and planters for the pilgrims on the other side of the road
        b.set(2, 1, 2, Arch.decor(ArchBlocks.PLANK_BENCH, E));
        b.set(2, 1, 3, Arch.decor(ArchBlocks.PLANK_BENCH, E));
        b.set(2, 1, 1, Arch.decor(ArchBlocks.FLOWER_PLANTER, E));
        b.set(2, 1, 4, Arch.decor(ArchBlocks.FLOWER_PLANTER, E));
        LandmarkKit.lampPost(b, 6, 1, 2, Blocks.BIRCH_FENCE, 2, ArchBlocks.BRASS_LANTERN.get());
        LandmarkKit.lampPost(b, 15, 1, 0, Blocks.BIRCH_FENCE, 2, ArchBlocks.BRASS_LANTERN.get());
        LandmarkKit.lampPost(b, 7, 1, 16, Blocks.BIRCH_FENCE, 2, ArchBlocks.BRASS_LANTERN.get());
        LandmarkKit.lampPost(b, 15, 1, 16, Blocks.BIRCH_FENCE, 2, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "guard", 14, 3, 9);
    }

    // ------------------------------------------------------------------ Angel Island

    /**
     * Angel Island's chapel: a white rotunda with sky-blue pilasters and tall windows under a sky-scale dome with an
     * open bell cupola, a columned portico, pews facing a gilded altar, and a dial merchant's stall on the forecourt.
     */
    static void cloudChapel(Builder b, Island isl, BuildStyle st) {
        Arch.Mats m = Arch.Mats.of(BuildStyle.CLOUD, b.seed);
        int cx = 9, cz = 11, R = 6;
        BlockState white = s(ArchBlocks.WHITE_PLASTER), sky = s(ArchBlocks.SKY_PLASTER), gold = s(Blocks.GOLD_BLOCK), cl = cloud();
        BlockState quartz = s(Blocks.SMOOTH_QUARTZ), glass = s(Blocks.LIGHT_BLUE_STAINED_GLASS), birch = s(Blocks.BIRCH_PLANKS);
        b.foundation(0, 0, 18, 18, ModBlocks.ISLAND_CLOUD.get(), 6);
        b.fill(0, 0, 0, 18, 0, 18, cl);
        b.clear(0, 1, 0, 18, 26, 18);
        disc(b, cx, cz, 0, 8.4, quartz);
        b.fill(4, 0, 0, 14, 0, 5, quartz);
        // the drum: plinth course, white walls, sky frieze; pilasters stand proud, windows between them
        for (int x = cx - R - 2; x <= cx + R + 2; x++)
            for (int z = cz - R - 2; z <= cz + R + 2; z++) {
                double d = Math.hypot(x - cx, z - cz);
                double w = Math.atan2(z - cz, x - cx) / (Math.PI / 4);
                double winOff = Math.abs(w - Math.round(w)) * (Math.PI / 4) * d;            // arc distance to a window axis
                double pilOff = Math.abs(w - 0.5 - Math.round(w - 0.5)) * (Math.PI / 4) * d; // to a pilaster axis
                if (d < R - 0.5) {
                    b.set(x, 1, z, birch);
                } else if (d < R + 0.5) {
                    b.set(x, 1, z, sky);
                    b.fill(x, 2, z, x, 7, z, white);
                    b.set(x, 8, z, sky);
                    if (winOff < 0.5) b.fill(x, 3, z, x, 6, z, glass);
                } else if (d < R + 1.5) {
                    if (pilOff < 0.62) {
                        b.fill(x, 1, z, x, 8, z, sky);
                        b.set(x, 9, z, slab(ArchBlocks.WHITE_PLASTER_SLAB, SlabType.BOTTOM));
                    } else b.set(x, 8, z, slab(ArchBlocks.WHITE_PLASTER_SLAB, SlabType.TOP));
                }
            }
        // the dome with white ribs over the pilasters, and the open bell cupola
        int top = smoothDome(b, cx, 9, cz, R + 0.4, 7.2, s(ArchBlocks.SKY_ROOF_TILES), s(ArchBlocks.SKY_ROOF_TILE_STAIRS), s(ArchBlocks.SKY_ROOF_TILE_SLAB),
                white, s(ArchBlocks.WHITE_PLASTER_STAIRS), s(ArchBlocks.WHITE_PLASTER_SLAB), 8, Math.PI / 8);
        b.fill(cx - 1, top + 1, cz - 1, cx + 1, top + 1, cz + 1, white);
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.fill(cx + c[0], top + 2, cz + c[1], cx + c[0], top + 3, cz + c[1], pillar(ArchBlocks.MARBLE_COLUMN));
        b.fill(cx - 1, top + 4, cz - 1, cx + 1, top + 4, cz + 1, white);
        b.set(cx, top + 5, cz, gold);
        b.set(cx, top + 6, cz, s(Blocks.LIGHTNING_ROD));
        b.set(cx, top + 3, cz, bellHanging(N));
        // portico: four marble columns, an entablature and a pediment with a golden oculus
        b.fill(5, 1, 1, 13, 1, 4, quartz);
        for (int x = 5; x <= 13; x++) b.set(x, 1, 0, Arch.stair(s(Blocks.QUARTZ_STAIRS), S, BOT));
        for (int x : new int[]{5, 7, 11, 13}) b.fill(x, 2, 1, x, 6, 1, pillar(ArchBlocks.MARBLE_COLUMN));
        for (int x : new int[]{5, 13}) b.fill(x, 2, 4, x, 6, 4, pillar(ArchBlocks.MARBLE_COLUMN));
        b.fill(5, 7, 1, 13, 7, 4, white);
        b.fill(5, 7, 1, 13, 7, 1, sky);
        Arch.gable(b, m, 5, 1, 13, 4, 8, false, 1, white);
        b.set(9, 9, 1, gold);
        b.bigDoor(cx, 2, cz - R, Blocks.BIRCH_DOOR, N);
        b.set(cx, 5, cz - R, s(Blocks.YELLOW_STAINED_GLASS));
        b.set(7, 3, 2, hangingLantern(ArchBlocks.BRASS_LANTERN));
        b.set(11, 3, 2, hangingLantern(ArchBlocks.BRASS_LANTERN));
        b.fill(7, 4, 2, 7, 6, 2, s(Blocks.CHAIN));
        b.fill(11, 4, 2, 11, 6, 2, s(Blocks.CHAIN));
        // nave: pews facing the altar, a raised dais, golden altar, the altarpiece window
        for (int z : new int[]{8, 10, 12})
            for (int x : new int[]{5, 6, 7, 11, 12, 13})
                if (Math.hypot(x - cx, z - cz) < R - 0.6) b.set(x, 2, z, Arch.stair(s(Blocks.BIRCH_STAIRS), N, BOT));
        for (int x = 6; x <= 12; x++)
            for (int z = 14; z <= 16; z++) if (Math.hypot(x - cx, z - cz) < R - 0.5) b.set(x, 2, z, quartz);
        for (int x = 8; x <= 10; x++) b.set(x, 2, 13, Arch.stair(s(Blocks.QUARTZ_STAIRS), S, BOT));
        b.set(cx, 3, 14, gold);
        b.set(cx, 4, 14, candles(Blocks.WHITE_CANDLE, 3));
        b.set(10, 3, 15, LandmarkKit.facing(Blocks.LECTERN, N));
        b.fill(cx, 3, cz + R, cx, 7, cz + R, s(Blocks.YELLOW_STAINED_GLASS));
        b.chest(12, 3, 15, W, Buildings.LOOT_SHRINE);
        b.fill(cx, 11, cz, cx, 13, cz, s(Blocks.CHAIN));
        b.set(cx, 10, cz, s(Blocks.PEARLESCENT_FROGLIGHT));
        npc(b, "priest", cx, 3, 15);
        // the dial merchant's stall on the forecourt
        for (int[] c : new int[][]{{15, 0}, {18, 0}, {15, 3}, {18, 3}}) b.fill(c[0], 1, c[1], c[0], 3, c[1], s(Blocks.BIRCH_FENCE));
        for (int x = 15; x <= 18; x++) {
            b.set(x, 4, 0, Arch.facing(s(ArchBlocks.BLUE_AWNING), N));
            b.set(x, 4, 1, Arch.facing(s(ArchBlocks.BLUE_AWNING), N));
            b.set(x, 4, 2, Arch.facing(s(ArchBlocks.BLUE_AWNING), S));
            b.set(x, 4, 3, Arch.facing(s(ArchBlocks.BLUE_AWNING), S));
        }
        b.set(16, 1, 1, Arch.decor(ArchBlocks.SHOP_COUNTER, N));
        b.set(17, 1, 1, Arch.decor(ArchBlocks.SHOP_COUNTER, N));
        b.set(16, 2, 1, s(Blocks.DECORATED_POT));
        b.set(17, 2, 1, s(Blocks.SEA_PICKLE));
        b.set(16, 1, 3, Arch.decor(ArchBlocks.GOODS_SHELF, N));
        b.set(17, 1, 3, Arch.decor(ArchBlocks.GOODS_SHELF, N));
        npc(b, "merchant", 16, 1, 2);
        // a bench and planters in the west garden
        b.set(1, 1, 8, Arch.decor(ArchBlocks.PLANK_BENCH, E));
        b.set(1, 1, 9, Arch.decor(ArchBlocks.PLANK_BENCH, E));
        b.set(1, 1, 7, Arch.decor(ArchBlocks.FLOWER_PLANTER, E));
        b.set(1, 1, 10, Arch.decor(ArchBlocks.FLOWER_PLANTER, E));
        b.set(4, 1, 1, Arch.decor(ArchBlocks.FLOWER_PLANTER, N));
        b.set(14, 1, 1, Arch.decor(ArchBlocks.FLOWER_PLANTER, N));
        npc(b, "resident", 3, 1, 9);
        puff(b, 0.5, 1, 17.0, 3.4, 3.2, 1.3, 21, cl);
        puff(b, 17.5, 1, 17.0, 3.4, 3.2, 1.3, 22, cl);
        puff(b, 0.8, 1, 1.0, 2.8, 2.8, 1.1, 23, cl);
        puff(b, 17.8, 1, 8.0, 2.0, 3.0, 1.0, 24, cl);
    }

    // ------------------------------------------------------------------ Upper Yard: Shandora

    /**
     * Shandora, the City of Gold, overgrown in the Upper Yard jungle: a broken city wall with its gate pylons, an avenue
     * of golden-stone columns to the plaza of the poneglyph (the sealed vault of gold beneath it), ruined stone houses,
     * giant jungle trees, and a stepped temple whose gilded summit shrine is the focal point.
     */
    static void shandoraRuins(Builder b, Island isl, BuildStyle st) {
        int n = 46;
        b.foundation(0, 0, n, n, Blocks.DIRT, 10);
        for (int x = 0; x <= n; x++) for (int z = 0; z <= n; z++) b.set(x, 0, z, jungleGround(b, x * 7919 + z * 104729));
        b.clear(0, 1, 0, n, 30, n);
        BlockState gold = s(Blocks.GOLD_BLOCK), chis = s(Blocks.CHISELED_STONE_BRICKS), andes = s(Blocks.POLISHED_ANDESITE);
        BlockState column = pillar(ArchBlocks.SANDSTONE_COLUMN), fs = s(ArchBlocks.FIELDSTONE);
        // ---- paving: the avenue, the plaza and the temple forecourt
        for (int z = 1; z <= 28; z++)
            for (int x = 20; x <= 26; x++) b.set(x, 0, z, x == 20 || x == 26 ? chis : ruinPave(b, x, z));
        for (int x = 14; x <= 32; x++)
            for (int z = 14; z <= 28; z++) {
                double d = Math.hypot(x - 23, z - 21);
                if (d < 7.5) b.set(x, 0, z, d >= 6.5 ? chis : d >= 3.5 && d < 4.5 ? andes : ruinPave(b, x, z));
            }
        // ---- the city wall: two thick, broken to irregular heights, with breaches
        for (int i = 0; i <= n; i++)
            for (int t = 1; t <= 2; t++)
                for (int[] p : new int[][]{{i, t}, {i, n - t}, {t, i}, {n - t, i}}) {
                    if (p[0] < 1 || p[0] > n - 1 || p[1] < 1 || p[1] > n - 1) continue;
                    boolean northGate = p[1] <= 2 && p[0] >= 15 && p[0] <= 31;
                    if (northGate) continue;
                    boolean breach = (p[0] <= 2 && p[1] >= 19 && p[1] <= 24) || (p[0] >= n - 2 && p[1] >= 29 && p[1] <= 33) || (p[1] >= n - 2 && p[0] >= 8 && p[0] <= 11);
                    int h = 3 + (int) Math.round(3 * (Math.sin(i * 0.37 + p[0] * 0.11) + 1) / 2) + b.rand(i * 31 + p[0] * 7 + p[1], 3);
                    if (breach) h = b.rand(i * 5 + t, 2);
                    for (int y = 1; y <= h; y++) b.set(p[0], y, p[1], y == 4 && t == 1 ? chis : ruin(b, p[0] * 131 + y * 17 + p[1] * 7));
                    if (h >= 4 && b.rand(p[0] * 3 + p[1] * 11, 3) == 0) b.set(p[0], h + 1, p[1], slab(Blocks.STONE_BRICK_SLAB, SlabType.BOTTOM));
                }
        // rubble where the wall fell
        LandmarkKit.heap(b, 501, 3, 21, 1, 2, 1, Blocks.COBBLESTONE, ArchBlocks.FIELDSTONE.get(), Blocks.MOSSY_COBBLESTONE);
        LandmarkKit.heap(b, 502, n - 3, 31, 1, 2, 1, Blocks.COBBLESTONE, ArchBlocks.FIELDSTONE.get(), Blocks.MOSSY_STONE_BRICKS);
        // vines on the inner faces of the wall
        for (int i = 4; i <= n - 4; i += 3) {
            if (b.rand(i * 13, 2) == 0) b.fill(i, 2, 3, i, 4, 3, vine(N));
            if (b.rand(i * 17, 2) == 0) b.fill(3, 2, i, 3, 3, i, vine(W));
            if (b.rand(i * 19, 2) == 0) b.fill(n - 3, 2, i, n - 3, 4, i, vine(E));
        }
        // ---- the north gate: two battered pylons with the golden sun of Shandora, a lintel broken in the middle
        for (int px : new int[]{15, 27}) {
            for (int x = px; x <= px + 4; x++)
                for (int z = 0; z <= 4; z++)
                    for (int y = 1; y <= 10; y++) b.set(x, y, z, y <= 2 ? fs : y == 9 ? chis : ruin(b, x * 41 + y * 13 + z));
            stairRing(b, px, 0, px + 4, 4, 11, s(Blocks.STONE_BRICK_STAIRS), BOT);
            b.fill(px + 1, 11, 1, px + 3, 11, 3, chis);
            b.set(px + 2, 12, 2, gold);
            for (int x = px; x <= px + 4; x++)
                for (int y = 4; y <= 8; y++) {
                    int ex = x - (px + 2), ey = y - 6;
                    int r2 = ex * ex + ey * ey;
                    if (r2 == 0) b.set(x, y, 0, gold);
                    else if (r2 >= 4 && r2 <= 5) b.set(x, y, 0, gold);
                    else if (r2 <= 2) b.set(x, y, 0, chis);
                }
        }
        for (int x = 20; x <= 26; x++) if (x < 23 || x > 24) b.fill(x, 10, 1, x, 11, 3, ruin(b, x * 3 + 77));
        b.fill(20, 9, 1, 20, 9, 3, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), W, TOP));
        b.fill(26, 9, 1, 26, 9, 3, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), E, TOP));
        b.fill(23, 1, 4, 24, 1, 5, s(Blocks.MOSSY_STONE_BRICKS)); // the fallen piece of the lintel
        b.set(25, 1, 5, slab(Blocks.MOSSY_STONE_BRICK_SLAB, SlabType.BOTTOM));
        // ---- the avenue of golden-stone columns (some intact with gold capitals, some broken, one fallen)
        for (int z = 6; z <= 14; z += 4)
            for (int x : new int[]{18, 28}) {
                int h = 2 + b.rand(z * 11 + x, 6);
                b.set(x, 1, z, chis);
                b.fill(x, 2, z, x, 1 + h, z, column);
                if (h >= 6) {
                    b.set(x, 2 + h, z, gold);
                    b.set(x, 3 + h, z, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
                }
            }
        for (int k = 0; k < 5; k++) b.set(29 + k, 1, 11, LandmarkKit.axis(ArchBlocks.SANDSTONE_COLUMN.get(), Direction.Axis.X));
        b.set(28, 1, 11, chis);
        // ---- the plaza: the poneglyph on its stepped dais; the vault of gold beneath it
        b.fill(19, 1, 17, 27, 1, 25, chis);
        b.fill(20, 2, 18, 26, 2, 24, andes);
        b.fill(23, 3, 21, 23, 5, 21, ModBlocks.PONEGLYPH.get());
        b.marker("poneglyph", 23, 3, 21);
        for (int[] c : new int[][]{{19, 17}, {27, 17}, {19, 25}, {27, 25}}) {
            b.set(c[0], 2, c[1], s(Blocks.STONE_BRICK_WALL));
            b.set(c[0], 3, c[1], gold);
        }
        for (int x = 19; x <= 27; x += 8) for (int z = 19; z <= 23; z += 2) b.set(x, 2, z, slab(Blocks.STONE_BRICK_SLAB, SlabType.BOTTOM));
        for (int x = 21; x <= 25; x++) b.set(x, 1, 16, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), S, BOT));
        b.fill(21, 2, 17, 25, 2, 17, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), S, BOT));
        // vault: ashlar shell, a gold-veined floor, stacked gold along the walls, the two treasure chests
        b.fill(19, -6, 17, 27, -1, 25, s(Blocks.STONE_BRICKS));
        b.clear(20, -5, 18, 26, -2, 24);
        for (int x = 20; x <= 26; x++) for (int z = 18; z <= 24; z++) if ((x + z) % 3 == 0) b.set(x, -6, z, gold);
        for (int x = 22; x <= 26; x += 2) {
            b.set(x, -5, 18, gold);
            b.set(x, -4, 18, s(Blocks.RAW_GOLD_BLOCK));
        }
        b.fill(26, -5, 20, 26, -5, 22, s(Blocks.RAW_GOLD_BLOCK));
        b.set(26, -4, 21, gold);
        b.fill(22, -5, 24, 26, -5, 24, chis);
        b.chest(23, -4, 24, N, Buildings.LOOT_TREASURE);
        b.chest(25, -4, 24, N, Buildings.LOOT_TREASURE);
        b.marker("treasure", 23, -4, 24);
        b.set(23, -2, 21, hangingLantern(Blocks.LANTERN));
        // the way down: a cracked flagstone on the dais over a ladder shaft against the vault's west wall
        b.clear(20, -1, 18, 20, 1, 18);
        b.ladderUp(20, -5, 18, 7, E);
        b.set(20, 2, 18, s(Blocks.CRACKED_STONE_BRICKS));
        // ---- the stepped temple and its golden summit shrine
        temple(b);
        // ---- ruined houses round the quarters (one still roofed with its dome)
        house(b, 5, 5, S, 11, true, true);
        house(b, 34, 5, S, 12, false, true);
        house(b, 4, 33, E, 13, false, false);
        house(b, 35, 33, W, 14, false, true);
        // ---- the jungle that swallowed the city
        bigTree(b, 9, 21, 15, 41);
        bigTree(b, 38, 22, 13, 42);
        bigTree(b, 7, 42, 10, 43);
        LandmarkKit.tree(b, 33, 1, 18, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, 6, 3, 44, null);
        LandmarkKit.tree(b, 15, 1, 14, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, 5, 2, 45, null);
        for (int i = 0; i < 40; i++) {
            int x = 4 + b.rand(600 + i * 2, n - 7), z = 4 + b.rand(601 + i * 2, n - 7);
            if (x >= 19 && x <= 27 && z <= 30) continue;
            b.set(x, 1, z, i % 3 == 0 ? leaves(Blocks.JUNGLE_LEAVES) : s(Blocks.MOSS_CARPET));
        }
        npc(b, "ruin_guardian", 23, 1, 9);
        npc(b, "ruin_guardian", 14, 1, 22);
        npc(b, "ruin_guardian", 32, 1, 24);
    }

    private static BlockState ruinPave(Builder b, int x, int z) {
        int r = b.rand(x * 977 + z * 131, 12);
        return r < 2 ? s(Blocks.MOSSY_STONE_BRICKS) : r < 4 ? s(Blocks.CRACKED_STONE_BRICKS) : r < 5 ? s(Blocks.MOSS_BLOCK) : s(Blocks.STONE_BRICKS);
    }

    /** Shandora's stepped temple (x 13..33, z 29..43) with a grand stair and the golden shrine on its summit. */
    private static void temple(Builder b) {
        BlockState gold = s(Blocks.GOLD_BLOCK), chis = s(Blocks.CHISELED_STONE_BRICKS);
        int[][] tiers = {{13, 29, 33, 43, 1, 3}, {15, 31, 31, 43, 4, 6}, {17, 33, 29, 43, 7, 9}};
        for (int[] t : tiers) {
            for (int x = t[0]; x <= t[2]; x++)
                for (int z = t[1]; z <= t[3]; z++)
                    for (int y = t[4]; y <= t[5]; y++) b.set(x, y, z, y == t[5] && (x == t[0] || x == t[2] || z == t[1]) ? chis : ruin(b, x * 57 + y * 911 + z * 3));
            // gold inlay band along the front edge of each tier
            for (int x = t[0] + 2; x <= t[2] - 2; x += 3) b.set(x, t[5], t[1], gold);
        }
        // grand stair x 21..25 from the forecourt to the summit, with cheek walls
        for (int i = 0; i <= 8; i++) {
            int z = 27 + i, y = 1 + i;
            for (int x = 21; x <= 25; x++) {
                b.fill(x, 1, z, x, y - 1, z, s(Blocks.STONE_BRICKS));
                b.set(x, y, z, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), S, BOT));
                b.clear(x, y + 1, z, x, y + 3, z);
            }
            for (int x : new int[]{20, 26}) {
                b.fill(x, 1, z, x, y, z, chis);
                b.set(x, y + 1, z, LandmarkKit.connected(Blocks.STONE_BRICK_WALL, Direction.Axis.Z));
            }
        }
        // summit shrine x 19..27, z 37..43: gold corner pillars, ashlar walls, flat roof and a gold crown
        int y0 = 10;
        b.fill(18, 9, 36, 28, 9, 43, s(Blocks.POLISHED_ANDESITE));
        LandmarkKit.room(b, 19, 9, 37, 27, 43, 5, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS);
        for (int[] c : new int[][]{{19, 37}, {27, 37}, {19, 43}, {27, 43}, {21, 37}, {25, 37}}) b.fill(c[0], y0, c[1], c[0], y0 + 4, c[1], gold);
        b.clear(22, y0, 37, 24, y0 + 2, 37);
        b.set(22, y0 + 2, 37, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), W, TOP));
        b.set(24, y0 + 2, 37, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), E, TOP));
        b.fill(22, y0 + 3, 37, 24, y0 + 3, 37, gold);
        for (int z = 39; z <= 41; z += 2) {
            b.set(19, y0 + 2, z, Blocks.AIR);
            b.set(27, y0 + 2, z, Blocks.AIR);
        }
        LandmarkKit.hipRoof(b, 18, 36, 28, 44, y0 + 5, Blocks.STONE_BRICK_STAIRS, Blocks.GOLD_BLOCK);
        b.fill(21, y0 + 8, 39, 25, y0 + 8, 41, gold);
        b.fill(22, y0 + 9, 40, 24, y0 + 9, 40, gold);
        b.set(23, y0 + 10, 40, gold);
        b.set(23, y0 + 11, 40, s(Blocks.LIGHTNING_ROD));
        // inside: the golden sun on the back wall over a gilded altar, and the shrine's chest
        for (int x = 21; x <= 25; x++)
            for (int y = y0; y <= y0 + 4; y++) {
                int dx = x - 23, dy = y - (y0 + 2);
                if (dx * dx + dy * dy <= 4) b.set(x, y, 43, gold);
            }
        b.set(23, y0 + 2, 43, s(Blocks.RAW_GOLD_BLOCK));
        b.fill(22, y0, 42, 24, y0, 42, chis);
        b.set(23, y0 + 1, 42, candles(Blocks.CANDLE, 4));
        b.chest(20, y0, 42, E, Buildings.LOOT_RUIN);
        b.set(23, y0 + 4, 40, hangingLantern(Blocks.LANTERN));
        // tier-top braziers
        for (int x : new int[]{16, 30}) b.set(x, 4, 30, s(Blocks.CAMPFIRE));
    }

    /**
     * A ruined Shandoran house (8x8 from (x0, z0)): fieldstone and ashlar walls broken toward one corner, a round-headed
     * doorway facing {@code front}, slit windows, and either a surviving stone dome (roofed) or open sky with rubble.
     */
    private static void house(Builder b, int x0, int z0, Direction front, int salt, boolean roofed, boolean chest) {
        int x1 = x0, z1 = z0, x2 = x0 + 7, z2 = z0 + 7;
        BlockState fs = s(ArchBlocks.FIELDSTONE);
        // the corner that fell in lies on the back wall, away from the doorway
        int colX = front == Direction.EAST ? x1 : front == Direction.WEST ? x2 : (salt % 2 == 0 ? x1 : x2);
        int colZ = front == Direction.SOUTH ? z1 : front == Direction.NORTH ? z2 : (salt % 2 == 0 ? z1 : z2);
        b.fill(x1, 0, z1, x2, 0, z2, s(Blocks.STONE_BRICKS));
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, fs);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (x != x1 && x != x2 && z != z1 && z != z2) continue;
                int h = 5;
                if (!roofed) h = (int) Math.max(1, Math.min(5, Math.round(Math.hypot(x - colX, z - colZ) * 0.6) + b.rand(salt + x * 7 + z * 3, 2)));
                for (int y = 1; y <= h; y++) b.set(x, y, z, y == 1 || (x + y + z) % 5 == 0 ? fs : ruin(b, salt * 97 + x * 31 + y * 7 + z));
                if (h == 5 && (x == x1 || x == x2) && (z == z1 || z == z2)) b.set(x, 5, z, s(Blocks.CHISELED_STONE_BRICKS));
            }
        // slit windows on the side walls, then the round-headed doorway (3 tall, 2 wide) in the front wall
        for (int k : new int[]{2, 5}) {
            b.set(x1, 3, z1 + k, Blocks.AIR);
            b.set(x2, 3, z1 + k, Blocks.AIR);
            b.set(x1 + k, 3, z1, Blocks.AIR);
            b.set(x1 + k, 3, z2, Blocks.AIR);
        }
        int dx = front.getAxis() == Direction.Axis.Z ? x1 + 3 : front == Direction.EAST ? x2 : x1;
        int dz = front.getAxis() == Direction.Axis.X ? z1 + 3 : front == Direction.SOUTH ? z2 : z1;
        Direction along = front.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
        for (int k = 0; k <= 1; k++) b.clear(dx + along.getStepX() * k, 1, dz + along.getStepZ() * k, dx + along.getStepX() * k, 3, dz + along.getStepZ() * k);
        b.set(dx, 4, dz, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), along.getOpposite(), TOP));
        b.set(dx + along.getStepX(), 4, dz + along.getStepZ(), Arch.stair(s(Blocks.STONE_BRICK_STAIRS), along, TOP));
        int ox = dx + front.getStepX(), oz = dz + front.getStepZ();
        b.set(ox, 0, oz, s(Blocks.STONE_BRICKS));
        b.set(ox + along.getStepX(), 0, oz + along.getStepZ(), s(Blocks.STONE_BRICKS));
        if (roofed) {
            b.fill(x1, 6, z1, x2, 6, z2, s(Blocks.STONE_BRICKS));
            stairRing(b, x1, z1, x2, z2, 6, s(Blocks.STONE_BRICK_STAIRS), BOT);
            dome(b, x1 + 3.5, 7, z1 + 3.5, 3.2, 2.6, s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), 4, Math.PI / 4);
            b.set(x1 + 3, 5, z1 + 3, hangingLantern(Blocks.LANTERN));
            b.set(x1 + 2, 1, z1 + 2, Arch.decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
            b.set(x1 + 1, 1, z1 + 2, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), E, BOT));
            b.set(x1 + 6, 1, z1 + 1, s(Blocks.DECORATED_POT));
            b.set(x1 + 6, 1, z1 + 2, s(Blocks.DECORATED_POT));
            b.set(x1 + 5, 1, z1 + 1, s(Blocks.GOLD_BLOCK));
        } else {
            int hx = colX == x1 ? x1 + 2 : x2 - 2, hz = colZ == z1 ? z1 + 2 : z2 - 2;
            LandmarkKit.heap(b, salt * 5, hx, hz, 1, 2, 1, ArchBlocks.FIELDSTONE.get(), Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_STONE_BRICKS);
            b.set(x1 + 3, 1, z1 + 3, s(Blocks.MOSS_CARPET));
            b.set(x1 + 4, 1, z1 + 4, s(Blocks.FERN));
        }
        // the chest stands in the front corner beside the door
        int chx = front == Direction.WEST ? x1 + 1 : front == Direction.EAST ? x2 - 1 : x1 + 1;
        int chz = front == Direction.SOUTH ? z2 - 1 : front == Direction.NORTH ? z1 + 1 : z1 + 1;
        if (chest) b.chest(chx, 1, chz, front.getAxis() == Direction.Axis.X ? front.getOpposite() : E, Buildings.LOOT_RUIN);
        else b.set(chx, 1, chz, s(Blocks.DECORATED_POT));
    }

    /** A giant Upper Yard jungle tree: 2x2 trunk, buttress roots along the ground and a broad two-layer crown. */
    private static void bigTree(Builder b, int x, int z, int h, int salt) {
        BlockState log = s(Blocks.JUNGLE_LOG), lv = leaves(Blocks.JUNGLE_LEAVES);
        for (int y = 1; y <= h; y++) b.fill(x, y, z, x + 1, y, z + 1, log);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int len = 3 + b.rand(salt + d.ordinal(), 2);
            for (int k = 1; k <= len; k++) {
                int rx = x + (d.getStepX() > 0 ? 1 : 0) + d.getStepX() * k, rz = z + (d.getStepZ() > 0 ? 1 : 0) + d.getStepZ() * k;
                put(b, rx, 1, rz, LandmarkKit.axis(Blocks.JUNGLE_LOG, d.getAxis()));
                if (k == 1) put(b, rx, 2, rz, log);
            }
        }
        ball(b, x + 0.5, h - 1, z + 0.5, 5.5, 2.2, 5.5, lv, 2);
        ball(b, x + 0.5, h + 2, z + 0.5, 3.5, 2.0, 3.5, lv, 2);
        for (int i = 0; i < 6; i++) {
            int vx = x - 4 + b.rand(salt + 50 + i, 10), vz = z - 4 + b.rand(salt + 60 + i, 10);
            if (in(b, vx, vz)) b.fill(vx, h - 5, vz, vx, h - 3, vz, s(Blocks.CAVE_VINES_PLANT));
        }
        b.fill(x, 1, z, x + 1, h, z + 1, log);
    }

    // ------------------------------------------------------------------ Upper Yard: Giant Jack and the Golden Bell

    /**
     * Giant Jack, the colossal beanstalk, with a plank way spiralling up it to the cloud at its crown, where the ruined
     * bell tower of Shandora still holds the Golden Bell (hollow, with a real bell for a clapper, so it can be rung).
     */
    static void goldenBelfry(Builder b, Island isl, BuildStyle st) {
        final int c = 8, T = 96, P = 16;
        final double TAU = Math.PI * 2;
        b.foundation(0, 0, 16, 16, Blocks.DIRT, 10);
        for (int x = 0; x <= 16; x++) for (int z = 0; z <= 16; z++) b.set(x, 0, z, jungleGround(b, x * 7919 + z * 104729 + 3));
        b.clear(0, 1, 0, 16, T + 24, 16);
        BlockState core = s(Blocks.GREEN_TERRACOTTA), cl = cloud();
        BlockState[] strand = {s(Blocks.MOSS_BLOCK), s(Blocks.LIME_TERRACOTTA), s(Blocks.MOSS_BLOCK)};
        // buttress roots
        for (double a : new double[]{2.0, 3.2, 4.4, 5.5}) {
            for (double r = 3; r <= 8; r += 0.5) {
                int x = (int) Math.round(c + Math.cos(a) * r), z = (int) Math.round(c + Math.sin(a) * r);
                int top = (int) Math.round(5 - (r - 3) * 0.9);
                for (int y = 1; y <= Math.max(1, top); y++) put(b, x, y, z, core);
                put(b, x, Math.max(1, top) + 1, z, strand[0]);
            }
        }
        // the stalk: three strands twisting round a darker core
        for (int y = 1; y <= T; y++) {
            double R = y < 8 ? 4.2 + (8 - y) * 0.25 : 4.2 - 0.9 * (y - 8) / (double) (T - 8);
            disc(b, c, c, y, R * 0.55, core);
            for (int k = 0; k < 3; k++) {
                double a = y * 0.075 + k * TAU / 3;
                disc(b, c + Math.cos(a) * R * 0.45, c + Math.sin(a) * R * 0.45, y, R * 0.58, strand[k]);
            }
        }
        // great leaves sprouting between the turns of the way
        for (int k = 0; k <= 4; k++) {
            double a = (k * 2.4) % TAU;
            int yp = (int) Math.round(1 + (a + TAU * k) / TAU * P + P / 2.0);
            if (yp > T - 6) continue;
            for (double t = 3.5; t <= 8.0; t += 0.5) {
                double w = 0.5 + 1.4 * Math.sin(Math.PI * (t - 3.5) / 4.8);
                int yy = yp + (t > 7.0 ? 1 : 0);
                disc(b, c + Math.cos(a) * t, c + Math.sin(a) * t, yy, w, leaves(Blocks.JUNGLE_LEAVES));
                put(b, (int) Math.round(c + Math.cos(a) * t), yy, (int) Math.round(c + Math.sin(a) * t), strand[0]);
            }
        }
        // the cloud at the crown, paved with the flagstones of upper Shandora
        for (int y = T - 4; y <= T; y++) disc(b, c, c, y, 8.2 - (T - y) * 1.3, cl);
        ball(b, c + 4.5, T - 3, c - 1, 2.6, 1.6, 2.4, cl, 0);
        ball(b, c - 3, T - 3, c + 4, 2.4, 1.5, 2.6, cl, 0);
        for (int x = 0; x <= 16; x++)
            for (int z = 0; z <= 16; z++) if (Math.hypot(x - c, z - c) <= 6.6) b.set(x, T, z, ruinPave(b, x, z));
        for (int k = 0; k < 9; k++) {
            double a = k * TAU / 9 + 0.3;
            cloudBall(b, c + Math.cos(a) * 7.0, T + 0.4, c + Math.sin(a) * 7.0, 2.3, 1.5, 2.3, 70 + k, cl);
            cloudBall(b, c + Math.cos(a + 0.35) * 6.0, T - 3.0, c + Math.sin(a + 0.35) * 6.0, 2.6, 1.6, 2.6, 80 + k, cl);
        }
        // broken columns and wall stubs of the upper city
        for (double a : new double[]{1.2, 2.3, 3.5}) {
            int x = (int) Math.round(c + Math.cos(a) * 6.2), z = (int) Math.round(c + Math.sin(a) * 6.2);
            int h = 2 + b.rand((int) (a * 100), 3);
            b.fill(x, T + 1, z, x, T + h, z, pillar(ArchBlocks.SANDSTONE_COLUMN));
            if (h == 4) b.set(x, T + h + 1, z, s(Blocks.GOLD_BLOCK));
        }
        b.set(3, T + 1, 9, s(Blocks.MOSS_CARPET));
        b.set(9, T + 1, 13, leaves(Blocks.JUNGLE_LEAVES));
        b.set(4, T + 1, 11, s(Blocks.MOSSY_COBBLESTONE));
        // the bell tower (x 5..11, z 5..11): a stone room, the open belfry with round arches, a stepped crown
        int y0 = T + 1;
        BlockState chis = s(Blocks.CHISELED_STONE_BRICKS), gold = s(Blocks.GOLD_BLOCK), wst = s(Blocks.STONE_BRICK_STAIRS);
        for (int x = 5; x <= 11; x++)
            for (int z = 5; z <= 11; z++) {
                boolean edge = x == 5 || x == 11 || z == 5 || z == 11, corner = (x == 5 || x == 11) && (z == 5 || z == 11);
                if (edge) for (int y = y0; y <= y0 + 3; y++) b.set(x, y, z, corner ? chis : ruin(b, x * 311 + y * 7 + z * 13));
                b.set(x, y0 + 4, z, edge ? gold : s(Blocks.STONE_BRICKS));
                if (corner) b.fill(x, y0 + 5, z, x, y0 + 13, z, chis);
                else if (edge) {
                    boolean xFace = z == 5 || z == 11;
                    int u = xFace ? x - 8 : z - 8; // -2..2 across the opening
                    b.set(x, y0 + 5, z, LandmarkKit.connected(Blocks.STONE_BRICK_WALL, xFace ? Direction.Axis.X : Direction.Axis.Z));
                    Direction out = u < 0 ? (xFace ? W : N) : (xFace ? E : S);
                    if (Math.abs(u) == 2) {
                        b.set(x, y0 + 11, z, Arch.stair(wst, out, TOP));
                        b.set(x, y0 + 12, z, ruin(b, x * 7 + z * 3));
                    } else if (Math.abs(u) == 1) b.set(x, y0 + 12, z, Arch.stair(wst, out, TOP));
                    b.set(x, y0 + 13, z, ruin(b, x * 3 + z * 29));
                }
            }
        b.fill(5, y0 + 14, 5, 11, y0 + 14, 11, s(Blocks.STONE_BRICKS));
        stairRing(b, 4, 4, 12, 12, y0 + 14, wst, TOP);
        LandmarkKit.hipRoof(b, 5, 5, 11, 11, y0 + 15, Blocks.STONE_BRICK_STAIRS, Blocks.GOLD_BLOCK);
        b.set(c, y0 + 19, c, s(Blocks.LIGHTNING_ROD));
        giantBell(b, c, y0 + 13, c, gold, N);
        // ground room: arched doorway to the east (where the way arrives), ladder up to the bell
        b.clear(11, y0, 8, 11, y0 + 2, 8);
        b.set(11, y0 + 3, 8, chis);
        for (int[] q : new int[][]{{8, 5}, {5, 8}, {8, 11}}) b.set(q[0], y0 + 1, q[1], Blocks.AIR);
        b.set(8, y0 + 1, 11, s(Blocks.STONE_BRICKS));
        LandmarkKit.ladder(b, 8, y0, 10, 5, N, null);
        b.chest(6, y0, 6, S, Buildings.LOOT_TREASURE);
        b.marker("treasure", 6, y0, 6);
        b.set(6, y0, 10, chis);
        b.set(6, y0 + 1, 10, candles(Blocks.CANDLE, 3));
        b.set(10, y0, 6, s(Blocks.DECORATED_POT));
        b.set(7, y0 + 3, 8, hangingLantern(Blocks.LANTERN));
        npc(b, "master", 8, y0, 7);
        // the way up: weathered planks (half-steps) spiralling round the stalk, a hedge of leaves on the outside
        BlockState plank = s(ArchBlocks.WEATHERED_PLANKS), pslab = slab(ArchBlocks.WEATHERED_PLANK_SLAB, SlabType.BOTTOM), rail = leaves(Blocks.JUNGLE_LEAVES);
        for (int x = 0; x <= 16; x++)
            for (int z = 0; z <= 16; z++) {
                double d = Math.hypot(x - c, z - c);
                if (d < 4.6 || d >= 7.6) continue;
                boolean outer = d >= 6.6;
                double a = Math.atan2(z - c, x - c);
                if (a < 0) a += TAU;
                for (int k = 0; ; k++) {
                    double sH = 1 + (a + TAU * k) / TAU * P;
                    if (sH > T + 1) break;
                    int h2 = (int) Math.floor(sH * 2), feet;
                    if (h2 % 2 == 0) {
                        feet = h2 / 2;
                        b.set(x, feet - 1, z, plank);
                    } else {
                        feet = h2 / 2 + 1;
                        b.set(x, feet - 1, z, pslab);
                    }
                    b.clear(x, feet, z, x, feet + 2, z);
                    if (!outer && d < 5.4 && feet > 2) b.set(x, feet - 2, z, strand[1]);
                    if (outer && sH > 3 && sH < T - 1) b.set(x, feet, z, rail);
                }
            }
    }

    // ------------------------------------------------------------------ Upper Yard: the sacrificial altar

    /**
     * The sacrificial altar of the Upper Yard: a three-tier stepped platform of fieldstone and ashlar with a grand stair,
     * the offering table on its summit before an obelisk crowned by a lightning rod, soul braziers, corner obelisks and a
     * small crypt of offerings inside the base.
     */
    static void sacrificialAltar(Builder b, Island isl, BuildStyle st) {
        b.foundation(0, 0, 16, 16, Blocks.DIRT, 8);
        for (int x = 0; x <= 16; x++) for (int z = 0; z <= 16; z++) b.set(x, 0, z, jungleGround(b, x * 7919 + z * 104729 + 5));
        b.clear(0, 1, 0, 16, 18, 16);
        BlockState fs = s(ArchBlocks.FIELDSTONE), chis = s(Blocks.CHISELED_STONE_BRICKS), andes = s(Blocks.POLISHED_ANDESITE), gold = s(Blocks.GOLD_BLOCK);
        // fieldstone apron round the base
        for (int x = 1; x <= 15; x++) for (int z = 0; z <= 16; z++) if (x <= 1 || x >= 15 || z <= 2 || z >= 16) b.set(x, 0, z, b.rand(x * 3 + z * 7, 4) == 0 ? s(Blocks.MOSS_BLOCK) : fs);
        // three tiers: fieldstone, ashlar, polished summit; chiselled lip, gold inlay on the front
        int[][] tiers = {{2, 3, 14, 15, 1, 2}, {4, 5, 12, 13, 3, 4}, {6, 7, 10, 11, 5, 6}};
        for (int ti = 0; ti < 3; ti++) {
            int[] t = tiers[ti];
            for (int x = t[0]; x <= t[2]; x++)
                for (int z = t[1]; z <= t[3]; z++)
                    for (int y = t[4]; y <= t[5]; y++) {
                        boolean lip = y == t[5] && (x == t[0] || x == t[2] || z == t[1] || z == t[3]);
                        BlockState body = ti == 0 ? fs : ti == 1 ? ruin(b, x * 31 + y * 7 + z * 3) : andes;
                        BlockState lipS = ti == 0 ? s(Blocks.STONE_BRICKS) : ti == 1 ? andes : chis;
                        b.set(x, y, z, lip ? lipS : body);
                    }
        }
        for (int x : new int[]{5, 11}) b.set(x, 4, 5, gold);
        // grand stair (x 7..9) from the ground to the summit, between cheek walls
        for (int i = 0; i <= 5; i++) {
            int z = 1 + i, y = 1 + i;
            for (int x = 7; x <= 9; x++) {
                if (y > 1) b.fill(x, 1, z, x, y - 1, z, s(Blocks.STONE_BRICKS));
                b.set(x, y, z, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), S, BOT));
                b.clear(x, y + 1, z, x, y + 3, z);
            }
            for (int x : new int[]{6, 10}) {
                b.fill(x, 1, z, x, y, z, andes);
                b.set(x, y + 1, z, LandmarkKit.connected(Blocks.STONE_BRICK_WALL, Direction.Axis.Z));
            }
        }
        // summit: the offering table with the chest upon it, the lightning obelisk, soul braziers
        b.fill(7, 7, 9, 9, 7, 9, chis);
        b.set(7, 7, 9, gold);
        b.set(9, 7, 9, gold);
        b.chest(8, 8, 9, N, Buildings.LOOT_SHRINE);
        b.marker("secret", 8, 8, 9);
        b.fill(8, 7, 11, 8, 11, 11, andes);
        b.set(8, 12, 11, gold);
        b.set(8, 13, 11, s(Blocks.LIGHTNING_ROD));
        b.set(6, 7, 11, s(Blocks.SOUL_CAMPFIRE));
        b.set(10, 7, 11, s(Blocks.SOUL_CAMPFIRE));
        b.set(6, 7, 7, standingLantern(ArchBlocks.BRASS_LANTERN));
        b.set(10, 7, 7, standingLantern(ArchBlocks.BRASS_LANTERN));
        // corner obelisks on the lowest tier
        for (int[] c : new int[][]{{2, 3}, {14, 3}, {2, 15}, {14, 15}}) {
            b.fill(c[0], 3, c[1], c[0], 4, c[1], s(ArchBlocks.FIELDSTONE_WALL));
            b.set(c[0], 5, c[1], chis);
            b.set(c[0], 6, c[1], s(Blocks.SOUL_LANTERN));
        }
        // the crypt of offerings in the base, entered from the back
        b.clear(6, 1, 9, 10, 3, 12);
        b.clear(8, 1, 13, 8, 2, 15);
        b.fill(7, 3, 15, 9, 3, 15, chis);
        b.set(8, 3, 14, chis);
        for (int[] q : new int[][]{{6, 12}, {10, 12}, {6, 11}}) b.set(q[0], 1, q[1], s(Blocks.BONE_BLOCK));
        b.set(6, 2, 12, s(Blocks.SKELETON_SKULL));
        b.set(10, 2, 12, candles(Blocks.CANDLE, 4));
        b.set(6, 1, 9, gold);
        b.set(10, 1, 9, s(Blocks.RAW_GOLD_BLOCK));
        b.set(8, 1, 9, s(Blocks.DECORATED_POT));
        b.set(8, 3, 10, hangingLantern(Blocks.SOUL_LANTERN));
        // vines over the stones
        for (int x = 3; x <= 13; x += 2) if (b.rand(x * 7, 2) == 0) b.set(x, 2, 2, vine(S));
        for (int z = 4; z <= 14; z += 3) {
            b.set(1, 2, z, vine(E));
            b.set(15, 1, z, vine(W));
        }
        npc(b, "ruin_guardian", 4, 1, 1);
    }

    // ------------------------------------------------------------------ Weatheria

    /**
     * Weatheria's laboratory: a two-storey white lab under a glass observatory dome, with two little science huts riding
     * their own clouds under striped balloons, a tethered weather balloon and instruments on the roof.
     */
    static void weatherLab(Builder b, Island isl, BuildStyle st) {
        Arch.Mats m = Arch.Mats.of(BuildStyle.CLOUD, b.seed);
        m.sill = null;
        BlockState white = s(ArchBlocks.WHITE_PLASTER), sky = s(ArchBlocks.SKY_PLASTER), quartz = s(Blocks.SMOOTH_QUARTZ), cl = cloud();
        BlockState birch = s(Blocks.BIRCH_PLANKS);
        b.foundation(0, 0, 20, 20, ModBlocks.ISLAND_CLOUD.get(), 6);
        b.fill(0, 0, 0, 20, 0, 20, cl);
        b.clear(0, 1, 0, 20, 30, 20);
        for (int z = 0; z <= 8; z++) for (int x = 9; x <= 11; x++) b.set(x, 0, z, quartz);
        // ---- the lab (x 6..14, z 9..17): two storeys, sky corners and floor band, quartz roof deck
        b.fill(6, 1, 9, 14, 7, 17, white);
        b.clear(7, 1, 10, 13, 7, 16);
        b.fill(6, 1, 9, 14, 1, 17, sky);
        b.clear(7, 1, 10, 13, 1, 16);
        b.fill(7, 0, 10, 13, 0, 16, birch);
        b.fill(7, 4, 10, 13, 4, 16, birch);
        band(b, 6, 9, 14, 17, 4, sky);
        for (int[] c : new int[][]{{6, 9}, {14, 9}, {6, 17}, {14, 17}}) b.fill(c[0], 1, c[1], c[0], 8, c[1], sky);
        b.fill(6, 8, 9, 14, 8, 17, sky);
        b.fill(7, 8, 10, 13, 8, 16, quartz);
        LandmarkKit.outline(b, 6, 9, 9, 14, 17, Blocks.BIRCH_FENCE);
        // observatory dome over the upper floor
        for (int x = 8; x <= 12; x++) for (int z = 11; z <= 15; z++) if (Math.hypot(x - 10, z - 13) < 2.6) b.set(x, 8, z, Blocks.AIR);
        int dt = dome(b, 10, 8, 13, 3.6, 4.2, s(Blocks.LIGHT_BLUE_STAINED_GLASS), white, 6, 0);
        b.set(10, dt + 1, 13, s(Blocks.LIGHTNING_ROD));
        // door with a little porch, windows on every side
        b.bigDoor(10, 1, 9, Blocks.BIRCH_DOOR, N);
        b.fill(9, 4, 7, 11, 4, 8, slab(ArchBlocks.SKY_ROOF_TILE_SLAB, SlabType.BOTTOM));
        b.fill(9, 1, 7, 9, 3, 7, s(Blocks.BIRCH_FENCE));
        b.fill(11, 1, 7, 11, 3, 7, s(Blocks.BIRCH_FENCE));
        Arch.wallLantern(b, m, 12, 3, 9, N);
        for (int x : new int[]{8, 12}) {
            Arch.window(b, m, x, 2, 9, N, 2, false);
            Arch.window(b, m, x, 2, 17, S, 2, false);
            Arch.window(b, m, x, 5, 9, N, 2, false);
            Arch.window(b, m, x, 5, 17, S, 2, false);
        }
        Arch.window(b, m, 10, 5, 9, N, 2, false);
        Arch.window(b, m, 10, 2, 17, S, 2, false);
        for (int z : new int[]{11, 15}) {
            Arch.window(b, m, 6, 2, z, W, 2, false);
            Arch.window(b, m, 14, 2, z, E, 2, false);
            Arch.window(b, m, 6, 5, z, W, 2, false);
            Arch.window(b, m, 14, 5, z, E, 2, false);
        }
        // ground floor: the weather laboratory
        b.set(13, 1, 11, s(Blocks.BREWING_STAND));
        b.set(13, 1, 12, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        b.set(13, 1, 13, Arch.decor(ArchBlocks.PLANK_TABLE, W));
        b.set(13, 2, 13, s(Blocks.POTTED_BLUE_ORCHID));
        b.chest(13, 1, 15, W, Buildings.LOOT_SHRINE);
        b.barrel(13, 1, 16, Buildings.LOOT_SHOP);
        b.set(12, 1, 16, s(Blocks.CARTOGRAPHY_TABLE));
        b.set(11, 1, 16, LandmarkKit.facing(Blocks.LECTERN, N));
        b.set(10, 1, 16, s(Blocks.LOOM));
        b.set(10, 3, 12, hangingLantern(ArchBlocks.BRASS_LANTERN));
        b.set(11, 3, 15, hangingLantern(ArchBlocks.BRASS_LANTERN));
        npc(b, "scholar", 9, 1, 12);
        npc(b, "scholar", 11, 1, 13);
        // stair up the west wall to the observatory floor
        Arch.staircase(b, s(Blocks.BIRCH_STAIRS), 7, 1, 16, N, 4);
        for (int z = 13; z <= 16; z++) b.set(8, 5, z, LandmarkKit.connected(Blocks.BIRCH_FENCE, Direction.Axis.Z));
        // upper floor: books, a desk, a bed, the telescope under the dome
        for (int x = 9; x <= 12; x++) b.fill(x, 5, 16, x, 6, 16, s(Blocks.BOOKSHELF));
        b.bed(13, 5, 11, Blocks.LIGHT_BLUE_BED, N);
        b.set(13, 5, 14, Arch.decor(ArchBlocks.PLANK_TABLE, W));
        b.set(12, 5, 14, Arch.stair(s(Blocks.BIRCH_STAIRS), W, BOT));
        b.set(13, 6, 14, candles(Blocks.WHITE_CANDLE, 2));
        b.set(10, 5, 13, s(Blocks.BIRCH_FENCE));
        b.set(10, 6, 13, LandmarkKit.facing(Blocks.LIGHTNING_ROD, N));
        b.set(9, 5, 11, s(Blocks.CARTOGRAPHY_TABLE));
        // ---- two science huts on their own clouds, each held up by a striped balloon
        scienceHut(b, m, 1, 2, 31, s(Blocks.LIGHT_BLUE_WOOL));
        scienceHut(b, m, 15, 2, 32, s(Blocks.YELLOW_WOOL));
        // ---- a tethered weather balloon and the instruments
        b.fill(18, 1, 18, 18, 11, 18, s(Blocks.CHAIN));
        ball(b, 18, 14, 18, 1.8, 2.6, 1.8, s(Blocks.WHITE_WOOL), 12);
        b.fill(18, 12, 18, 18, 16, 18, s(Blocks.ORANGE_WOOL));
        b.set(18, 14, 16, s(Blocks.ORANGE_WOOL));
        b.set(18, 14, 20, s(Blocks.ORANGE_WOOL));
        b.set(18, 0, 18, sky);
        b.fill(7, 9, 10, 7, 10, 10, s(Blocks.BIRCH_FENCE));
        b.set(7, 11, 10, LandmarkKit.trapdoor(Blocks.BIRCH_TRAPDOOR, N, Half.BOTTOM, false));
        b.fill(13, 9, 16, 13, 10, 16, s(Blocks.BIRCH_FENCE));
        b.set(13, 11, 16, s(Blocks.LIGHTNING_ROD));
        b.set(12, 1, 7, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 2));
        // clouds drifting round the island
        puff(b, 2.0, 1, 16.5, 3.6, 4.0, 1.8, 33, cl);
        puff(b, 18.5, 1, 11.0, 2.4, 3.4, 1.5, 34, cl);
        puff(b, 4.0, 1, 0.5, 3.0, 1.6, 1.0, 35, cl);
    }

    /** Horizontal band in the four wall planes of the box [x1..x2]x[z1..z2] at y. */
    private static void band(Builder b, int x1, int z1, int x2, int z2, int y, BlockState s) {
        b.fill(x1, y, z1, x2, y, z1, s);
        b.fill(x1, y, z2, x2, y, z2, s);
        b.fill(x1, y, z1, x1, y, z2, s);
        b.fill(x2, y, z1, x2, y, z2, s);
    }

    /** A 5x5 hut (from (x0, z0)) on a floating cloud with cloud steps down, a hip roof and a striped balloon above. */
    private static void scienceHut(Builder b, Arch.Mats m, int x0, int z0, int salt, BlockState stripe) {
        int x1 = x0, z1 = z0, x2 = x0 + 4, z2 = z0 + 4, cxh = x0 + 2, czh = z0 + 2;
        BlockState white = s(ArchBlocks.WHITE_PLASTER), sky = s(ArchBlocks.SKY_PLASTER), cl = cloud();
        ball(b, cxh, 2, czh, 3.4, 1.4, 3.4, cl, 2);
        b.fill(x1, 3, z1, x2, 3, z2, s(Blocks.BIRCH_PLANKS));
        b.fill(x1, 4, z1, x2, 6, z2, white);
        b.clear(x1 + 1, 4, z1 + 1, x2 - 1, 6, z2 - 1);
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.fill(c[0], 4, c[1], c[0], 6, c[1], sky);
        int top = Arch.hip(b, m, x1, z1, x2, z2, 7, 1);
        b.door(cxh, 4, z2, Blocks.BIRCH_DOOR, S);
        Arch.window(b, m, cxh, 5, z1, N, 1, false);
        Arch.window(b, m, x1, 5, czh, W, 1, false);
        Arch.window(b, m, x2, 5, czh, E, 1, false);
        // cloud steps down to the island
        for (int i = 0; i < 3; i++) {
            int z = z2 + 1 + i, y = 3 - i;
            b.fill(cxh - 1, 1, z, cxh + 1, y - 1, z, cl);
            b.set(cxh, y, z, Arch.stair(s(Blocks.QUARTZ_STAIRS), N, Half.BOTTOM));
            b.set(cxh - 1, y, z, cl);
            b.set(cxh + 1, y, z, cl);
        }
        // inside: a workbench of instruments
        b.set(x1 + 1, 4, z1 + 1, s(Blocks.CRAFTING_TABLE));
        b.set(x1 + 3, 4, z1 + 1, s(Blocks.BREWING_STAND));
        b.set(x1 + 1, 4, z1 + 3, Arch.decor(ArchBlocks.GOODS_SHELF, E));
        b.set(cxh, 6, czh, hangingLantern(ArchBlocks.BRASS_LANTERN));
        // the balloon: gores of white and colour, tied down by four ropes
        int by0 = top + 3, bh = 8;
        double by = by0 + 3;
        for (int i = 0; i < bh; i++) {
            double t = (i + 0.5) / bh; // envelope: narrow at the mouth, widest high up, domed crown
            double rr = t < 0.62 ? 1.0 + 2.4 * Math.sin(t / 0.62 * Math.PI / 2) : 3.4 * Math.sqrt(Math.max(0, 1 - Math.pow((t - 0.62) / 0.38, 2)));
            for (int x = cxh - 4; x <= cxh + 4; x++)
                for (int z = czh - 4; z <= czh + 4; z++) {
                    double dd = Math.hypot(x - cxh, z - czh);
                    if (dd > rr + 0.35) continue;
                    double a = Math.atan2(z - czh, x - cxh);
                    int gore = (int) Math.floor((a + Math.PI) / (Math.PI / 4) + 0.5);
                    put(b, x, by0 + i, z, gore % 2 == 0 ? s(Blocks.WHITE_WOOL) : stripe);
                }
        }
        for (int[] q : new int[][]{{cxh - 2, czh}, {cxh + 2, czh}, {cxh, czh - 2}, {cxh, czh + 2}})
            for (int y = top - 1; y < by0 + 1; y++) put(b, q[0], y, q[1], s(Blocks.CHAIN));
        for (int y = top + 1; y < by0; y++) b.set(cxh, y, czh, s(Blocks.CHAIN));
    }

    // ------------------------------------------------------------------ the Cloud End

    /**
     * The Cloud End: where the White Sea stops and ships drop back to the Blue Sea. A weathered cloud arch with a
     * warning bell and red lanterns, a gold arrow pointing down, a roofed lookout where the guide waits, and a signpost.
     */
    static void cloudEndGate(Builder b, Island isl, BuildStyle st) {
        Arch.Mats m = Arch.Mats.of(BuildStyle.CLOUD, b.seed);
        BlockState sky = s(ArchBlocks.SKY_PLASTER), white = s(ArchBlocks.WHITE_PLASTER), cl = cloud(), quartz = s(Blocks.SMOOTH_QUARTZ);
        b.foundation(0, 0, 12, 10, ModBlocks.ISLAND_CLOUD.get(), 6);
        b.fill(0, 0, 0, 12, 0, 10, cl);
        b.clear(0, 1, 0, 12, 16, 10);
        for (int z = 0; z <= 10; z++) for (int x = 5; x <= 7; x++) b.set(x, 0, z, quartz);
        // the arch: sky-plaster cores wrapped in cloud, a cloud lintel with a plaque and a gold arrow
        for (int px : new int[]{2, 10}) {
            for (int y = 1; y <= 8; y++) {
                double r = 1.3 + 0.35 * Math.sin(y * 1.3 + px);
                disc(b, px, 4, y, r, y == 1 || y == 6 ? sky : cl);
            }
        }
        cloudBall(b, 6, 8.6, 4, 6.4, 2.2, 2.0, 51, cl);
        cloudBall(b, 2.2, 9.2, 4, 2.2, 1.8, 2.2, 54, cl);
        cloudBall(b, 9.8, 9.4, 4, 2.4, 1.9, 2.2, 55, cl);
        b.fill(4, 8, 2, 8, 9, 2, sky);
        for (int[] q : new int[][]{{6, 8}, {5, 9}, {6, 9}, {7, 9}}) b.set(q[0], q[1], 2, s(Blocks.GOLD_BLOCK));
        b.set(6, 7, 4, bellHanging(E));
        b.set(4, 7, 4, hangingLantern(ArchBlocks.RED_PAPER_LANTERN));
        b.set(8, 7, 4, hangingLantern(ArchBlocks.RED_PAPER_LANTERN));
        // the guide's lookout: four posts, a sky-scale hip roof, benches facing the edge
        b.fill(1, 0, 6, 4, 0, 9, s(Blocks.BIRCH_PLANKS));
        for (int[] c : new int[][]{{1, 6}, {4, 6}, {1, 9}, {4, 9}}) b.fill(c[0], 1, c[1], c[0], 3, c[1], white);
        b.fill(1, 4, 6, 4, 4, 9, sky);
        Arch.hip(b, m, 1, 6, 4, 9, 5, 1);
        b.set(2, 1, 8, Arch.decor(ArchBlocks.PLANK_BENCH, N));
        b.set(3, 1, 8, Arch.decor(ArchBlocks.PLANK_BENCH, N));
        b.set(2, 3, 7, hangingLantern(ArchBlocks.BRASS_LANTERN));
        b.set(3, 1, 9, Blocks.BARREL.defaultBlockState());
        b.set(2, 1, 9, Arch.decor(ArchBlocks.ROPE_COIL, W));
        // signpost and the edge lamps
        b.fill(9, 1, 8, 9, 2, 8, s(Blocks.BIRCH_FENCE));
        b.set(9, 3, 8, s(Blocks.BIRCH_SIGN));
        LandmarkKit.lampPost(b, 4, 1, 1, Blocks.BIRCH_FENCE, 2, ArchBlocks.RED_PAPER_LANTERN.get());
        LandmarkKit.lampPost(b, 8, 1, 1, Blocks.BIRCH_FENCE, 2, ArchBlocks.RED_PAPER_LANTERN.get());
        puff(b, 11.5, 1, 9.0, 2.2, 2.4, 1.4, 52, cl);
        puff(b, 0.5, 1, 1.0, 2.2, 2.2, 1.2, 53, cl);
        puff(b, 12.0, 1, 1.5, 1.8, 2.6, 1.0, 56, cl);
        npc(b, "canal_guide", 6, 1, 1);
    }

    private DimensionLandmarks() {}
}
