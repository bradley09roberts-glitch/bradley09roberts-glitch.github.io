package com.grandline.world.landmark;

import com.grandline.registry.ArchBlocks;
import com.grandline.world.Island;
import com.grandline.world.build.Arch;
import com.grandline.world.build.Arch.Mats;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import static com.grandline.world.landmark.LandmarkKit.*;
import static com.grandline.world.landmark.LandmarksD1.*;

/** New World set pieces, part two: Wano, Onigashima, Baltigo, Egghead and Hachinosu. */
final class FinalSeaLandmarks {

    // ============================================================== Wano: the Flower Capital castle

    /**
     * Oden's castle over the Flower Capital: a four-tier tenshu of white plaster between dark posts, each tier under a
     * half-pitch kawara skirt with upturned corners and chidori gables, crowned by a hip-and-gable roof with golden
     * shachihoko, standing on a battered ishigaki platform. A yagura gatehouse and dobei walls close the front court
     * (stone lanterns, cherry trees, a raked-gravel garden and a koi pond); inside, the audience hall with the lord's
     * dais and golden screens, the armoury, the lord's chamber and the lookout, linked by stairs; a hatch in the hall
     * drops into a vault in the stone base whose escape tunnel opens in the east face.
     */
    static void flowerCapitalCastle(Builder b, Island island, BuildStyle st) {
        Mats m = wano(b);
        int c = 22; // 45 x 45, gate on the north (z = 0)
        BlockState planks = s(Blocks.SPRUCE_PLANKS), dark = s(Blocks.DARK_OAK_PLANKS), stairs = s(Blocks.DARK_OAK_STAIRS);
        BlockState redLantern = k(ArchBlocks.RED_PAPER_LANTERN);
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 44);
        // ---- approach and front court
        for (int z = 0; z <= 10; z++) {
            b.fill(c - 2, 0, z, c + 2, 0, z, Blocks.GRAVEL);
            b.fill(c - 1, 0, z, c + 1, 0, z, Blocks.POLISHED_ANDESITE);
        }
        dobei(b, m, 1, 1, 14, 1, 1, 3);
        dobei(b, m, 30, 1, 43, 1, 1, 3);
        dobei(b, m, 1, 2, 1, 16, 1, 3);
        dobei(b, m, 43, 2, 43, 16, 1, 3);
        dobei(b, m, 2, 16, 5, 16, 1, 3);
        dobei(b, m, 39, 16, 42, 16, 1, 3);
        // raked gravel garden with stones (west), koi pond (east), cherry trees, stone lanterns along the path
        b.fill(9, 0, 9, 16, 0, 13, Blocks.GRAVEL);
        for (int[] r : new int[][]{{11, 10}, {14, 12}, {15, 10}}) {
            b.set(r[0], 1, r[1], Blocks.MOSSY_COBBLESTONE);
            b.set(r[0] + 1, 1, r[1], Arch.slab(s(Blocks.MOSSY_COBBLESTONE_SLAB), SlabType.BOTTOM));
        }
        b.fill(28, -2, 9, 35, -2, 13, Blocks.CLAY);
        b.fill(29, -1, 10, 34, 0, 12, Blocks.WATER);
        b.fill(28, 0, 9, 35, 0, 9, k(ArchBlocks.FIELDSTONE));
        b.fill(28, 0, 13, 35, 0, 13, k(ArchBlocks.FIELDSTONE));
        b.fill(28, 0, 9, 28, 0, 13, k(ArchBlocks.FIELDSTONE));
        b.fill(35, 0, 9, 35, 0, 13, k(ArchBlocks.FIELDSTONE));
        for (int[] p : new int[][]{{30, 11}, {33, 10}}) b.set(p[0], 1, p[1], Blocks.LILY_PAD);
        tree(b, 6, 1, 11, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, 5, 3, 71, null);
        tree(b, 39, 1, 11, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, 5, 3, 97, null);
        for (int z : new int[]{9, 13}) {
            toro(b, c - 4, 1, z);
            toro(b, c + 4, 1, z);
        }
        npc(b, "resident", 12, 1, 11);
        npc(b, "guard", c - 3, 1, 8);
        npc(b, "guard", c + 3, 1, 8);
        b.marker("arc_anchor", c, 1, 6, tag("arc", "flower_capital"));
        // ---- the yagura gatehouse: two stone piers, a gate passage and a guard storey under a hip-and-gable roof
        ishigaki(b, 15, 1, 19, 7, 4, 0);
        ishigaki(b, 25, 1, 29, 7, 4, 0);
        b.fill(15, 5, 1, 29, 5, 7, dark);
        for (int x : new int[]{20, 24}) b.fill(x, 1, 4, x, 4, 4, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
        b.fill(20, 4, 4, 24, 4, 4, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
        wanoWalls(b, m, 15, 1, 29, 7, 6, 9, 4, true, true);
        irimoya(b, m, 15, 1, 29, 7, 10, 1, 2, m.wall, false);
        // a stair inside the west pier, entered from the gate passage, climbs to the guard storey
        flight(b, s(Blocks.STONE_BRICK_STAIRS), 17, 1, 6, Direction.NORTH, 5, 2, null, 1);
        b.bigDoor(19, 1, 6, m.door.getBlock(), Direction.EAST);
        run(b, 19, 6, 3, 19, 5, s(Blocks.DARK_OAK_FENCE));
        for (int x : new int[]{17, 27}) {
            hang(b, x, 9, 0, redLantern);
            hang(b, x, 9, 8, redLantern);
        }
        b.chest(27, 6, 3, Direction.WEST, Buildings.LOOT_MARINE);
        b.set(27, 6, 5, k(ArchBlocks.PLANK_TABLE));
        b.set(27, 7, 5, Arch.lantern(s(Blocks.LANTERN), false));
        for (int x = 21; x <= 23; x++) {
            b.set(x, 6, 2, s(Blocks.DARK_OAK_FENCE));
            b.set(x, 7, 2, Arch.connect(s(Blocks.IRON_BARS), false, x < 23, false, x > 21));
        }
        npc(b, "guard", 22, 6, 4);
        // ---- the ishigaki platform (top at y = 8) with a stone stair cut into its face
        ishigaki(b, 5, 15, 39, 42, 8, 3);
        b.fill(9, 8, 19, 35, 8, 38, Blocks.GRAVEL);
        flight(b, s(Blocks.STONE_BRICK_STAIRS), c + 2, 1, 11, Direction.SOUTH, 8, 5, k(ArchBlocks.FIELDSTONE), 1);
        for (int i = 0; i < 4; i++) {
            int z = 11 + i;
            for (int x : new int[]{c - 3, c + 3}) {
                b.fill(x, 1, z, x, 1 + i, z, k(ArchBlocks.FIELDSTONE));
                b.set(x, 2 + i, z, Arch.connect(k(ArchBlocks.FIELDSTONE_WALL), z > 11, false, true, false));
            }
        }
        // parapet along the platform rim, open where the stair arrives
        for (int x = 8; x <= 36; x++)
            for (int z : new int[]{18, 39}) {
                if (z == 18 && Math.abs(x - c) <= 2) continue;
                b.set(x, 9, z, m.wall);
                b.set(x, 10, z, Arch.slab(m.roofSlab, SlabType.BOTTOM));
            }
        for (int z = 19; z <= 38; z++)
            for (int x : new int[]{8, 36}) {
                b.set(x, 9, z, m.wall);
                b.set(x, 10, z, Arch.slab(m.roofSlab, SlabType.BOTTOM));
            }
        toro(b, c - 5, 9, 19);
        toro(b, c + 5, 9, 19);
        // ---- the tenshu: four tiers, each inset two blocks under a skirt; floors at y = 8, 14, 20, 25
        int[][] tier = {{13, 21, 31, 37}, {15, 23, 29, 35}, {17, 25, 27, 33}, {19, 27, 25, 31}};
        int[] fl = {8, 14, 20, 25}, top = {13, 19, 24, 31};
        for (int t = 0; t < 4; t++) {
            int[] r = tier[t];
            int f = fl[t];
            b.fill(r[0], f, r[1], r[2], f, r[3], t == 0 ? dark : planks);
            // dark sill beam under the walls
            b.fill(r[0], f, r[1], r[2], f, r[1], beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
            b.fill(r[0], f, r[3], r[2], f, r[3], beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
            b.fill(r[0], f, r[1], r[0], f, r[3], beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
            b.fill(r[2], f, r[1], r[2], f, r[3], beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
            b.clear(r[0] + 1, f + 1, r[1] + 1, r[2] - 1, t == 3 ? f + 4 : top[t], r[3] - 1);
            wanoWalls(b, m, r[0], r[1], r[2], r[3], f + 1, top[t], 4, true, true);
            if (t < 3) {
                int[] u = tier[t + 1];
                b.fill(u[0], fl[t + 1], u[1], u[2], fl[t + 1], u[3], planks); // floor of the tier above
                skirt(b, m, u[0], u[1], u[2], u[3], fl[t + 1], 2, 2, 4);
            }
        }
        // tatami in the hall
        b.fill(14, 8, 22, 30, 8, 36, planks);
        b.fill(19, 8, 23, 25, 8, 31, Blocks.BAMBOO_MOSAIC);
        b.fill(14, 8, 23, 17, 8, 29, Blocks.BAMBOO_MOSAIC);
        b.fill(27, 8, 23, 30, 8, 29, Blocks.BAMBOO_MOSAIC);
        // chidori gables on the skirts, a porch on two posts under the front one
        chidori(b, m, Direction.NORTH, 15, 23, 29, 35, c, fl[1], 2, 2, 3);
        chidori(b, m, Direction.SOUTH, 15, 23, 29, 35, c, fl[1], 2, 2, 3);
        chidori(b, m, Direction.WEST, 15, 23, 29, 35, 29, fl[1], 2, 2, 2);
        chidori(b, m, Direction.EAST, 15, 23, 29, 35, 29, fl[1], 2, 2, 2);
        chidori(b, m, Direction.NORTH, 17, 25, 27, 33, c, fl[2], 2, 2, 2);
        chidori(b, m, Direction.SOUTH, 17, 25, 27, 33, c, fl[2], 2, 2, 2);
        for (int x : new int[]{c - 3, c + 3}) b.fill(x, 9, 20, x, 12, 20, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
        b.clear(c - 1, 12, 20, c + 1, 12, 20);
        // T4 windows on the short sides and its ceiling, then the crowning roof
        for (int z : new int[]{28, 30})
            for (int x : new int[]{19, 25}) b.fill(x, 27, z, x, 28, z, Arch.along(m.window, Direction.Axis.Z));
        b.fill(20, 30, 28, 24, 30, 30, planks);
        irimoya(b, m, 19, 27, 25, 31, 30, 2, 3, m.wall, true);
        // ---- storey 1: the audience hall (floor 8, five clear)
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 9, 21, m.door.getBlock(), Direction.NORTH);
        carpet(b, c - 1, 9, 22, c + 1, 31, Blocks.RED_CARPET);
        for (int[] p : new int[][]{{18, 26}, {26, 26}, {18, 32}, {26, 32}}) b.fill(p[0], 9, p[1], p[0], 13, p[1], beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
        b.fill(19, 9, 33, 25, 9, 36, dark);
        for (int x = 19; x <= 25; x++) b.set(x, 9, 32, Arch.stair(stairs, Direction.SOUTH, Half.BOTTOM));
        for (int x = 19; x <= 25; x++)
            for (int y = 10; y <= 12; y++) b.set(x, y, 36, ((x + y) & 1) == 0 ? s(Blocks.GOLD_BLOCK) : s(Blocks.YELLOW_WOOL));
        b.fill(c - 1, 10, 33, c + 1, 10, 35, Blocks.RED_CARPET);
        b.set(c - 2, 10, 35, Blocks.DECORATED_POT);
        b.set(c + 2, 10, 35, facing(Blocks.LECTERN, Direction.NORTH));
        npc(b, "noble", c, 10, 35);
        npc(b, "guard", 19, 9, 29);
        npc(b, "guard", 25, 9, 29);
        for (int[] l : new int[][]{{18, 23}, {26, 23}, {18, 29}, {26, 29}, {c, 26}}) hang(b, l[0], 13, l[1], redLantern);
        b.set(29, 9, 33, Blocks.BARREL);
        b.set(30, 9, 33, Blocks.BARREL);
        b.set(30, 9, 32, k(ArchBlocks.CARGO_CRATE));
        flight(b, stairs, 16, 9, 35, Direction.NORTH, 6, 2, planks, 9);
        run(b, 18, 15, 31, 18, 33, s(Blocks.DARK_OAK_FENCE));
        run(b, 16, 15, 34, 17, 34, s(Blocks.DARK_OAK_FENCE));
        // ---- storey 2: the armoury (floor 14)
        b.set(19, 15, 24, Blocks.GRINDSTONE);
        b.set(20, 15, 24, Blocks.SMITHING_TABLE);
        b.set(21, 15, 24, Blocks.ANVIL);
        for (int x = 23; x <= 25; x++) {
            b.set(x, 15, 24, Arch.connect(s(Blocks.DARK_OAK_FENCE), false, x < 25, false, x > 23));
            b.set(x, 16, 24, Arch.connect(s(Blocks.IRON_BARS), false, x < 25, false, x > 23));
        }
        b.barrel(27, 15, 24, Buildings.LOOT_SMITH);
        b.barrel(28, 15, 24, Buildings.LOOT_SMITH);
        b.set(20, 15, 34, k(ArchBlocks.CARGO_CRATE));
        b.set(21, 15, 34, k(ArchBlocks.CARGO_CRATE));
        b.set(21, 16, 34, Arch.decor(ArchBlocks.CARGO_SACKS, Direction.NORTH));
        b.set(22, 15, 34, Blocks.TARGET);
        hang(b, 20, 19, 29, s(Blocks.LANTERN));
        hang(b, 24, 19, 29, s(Blocks.LANTERN));
        npc(b, "master", c, 15, 29);
        flight(b, stairs, 26, 15, 26, Direction.SOUTH, 6, 2, planks, 15);
        run(b, 24, 21, 28, 24, 30, s(Blocks.DARK_OAK_FENCE));
        // ---- storey 3: the lord's chamber (floor 20)
        b.bed(19, 21, 32, Blocks.RED_BED, Direction.EAST);
        b.chest(18, 21, 32, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(26, 21, 32, facing(Blocks.LECTERN, Direction.WEST));
        b.set(21, 21, 27, k(ArchBlocks.PLANK_TABLE));
        b.set(20, 21, 27, Blocks.RED_CARPET);
        b.set(22, 21, 27, Blocks.RED_CARPET);
        b.set(24, 21, 26, Blocks.POTTED_FLOWERING_AZALEA);
        b.set(18, 21, 26, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
        for (int y = 21; y <= 22; y++) b.set(23, y, 32, (y & 1) == 0 ? s(Blocks.GOLD_BLOCK) : s(Blocks.YELLOW_WOOL));
        hang(b, 21, 24, 28, redLantern);
        flight(b, stairs, 19, 21, 29, Direction.EAST, 5, 2, planks, 21);
        // ---- storey 4: the lookout (floor 25)
        hang(b, 24, 29, 30, s(Blocks.LANTERN));
        npc(b, "guard", 24, 26, 28);
        // ---- the vault inside the stone base, with an escape tunnel to the east face
        b.clear(25, 2, 23, 29, 5, 27);
        hatch(b, 29, 8, 27, 2, Direction.NORTH, Blocks.SPRUCE_TRAPDOOR, ArchBlocks.FIELDSTONE.get());
        b.chest(25, 2, 23, Direction.EAST, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 25, 2, 23);
        b.barrel(25, 2, 26, Buildings.LOOT_HOUSE);
        b.set(26, 2, 23, Blocks.GOLD_BLOCK);
        b.set(27, 2, 23, Blocks.GOLD_BLOCK);
        b.set(25, 2, 25, k(ArchBlocks.CARGO_CRATE));
        hang(b, 27, 5, 25, s(Blocks.LANTERN));
        b.clear(30, 2, 25, 37, 4, 25);
        b.door(38, 2, 25, Blocks.SPRUCE_DOOR, Direction.EAST);
        hang(b, 34, 4, 25, s(Blocks.SOUL_LANTERN));
    }

    // ============================================================== Wano: the waterfall gate

    /**
     * The gate where ships climb into Wano: a river spills off a cliff terrace into a plunge pool where a great red
     * torii stands in the water; a guard house (bansho) with a jetty watches the pool, a stone stair climbs the cliff
     * to a red arched bridge over the river and a vermilion shrine on the terrace, and a cave hides behind the falls.
     */
    static void waterfallGate(Builder b, Island island, BuildStyle st) {
        Mats m = wano(b);
        int w = b.w, d = b.d, c = 10; // 25 x 23; the cliff face at z = 11, terrace top y = 10
        BlockState redLantern = k(ArchBlocks.RED_PAPER_LANTERN);
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 28);
        // ---- the cliff and its terrace
        BlockState[] rock = {s(Blocks.STONE), s(Blocks.STONE), s(Blocks.ANDESITE), s(Blocks.STONE), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.TUFF)};
        for (int x = 0; x < w; x++)
            for (int z = 11; z < d; z++) {
                int face = z == 11 ? b.rand(x * 3 + 7, 3) : 0; // a ragged lip: the top course steps back here and there
                for (int y = 1; y <= 10; y++) {
                    if (z == 11 && y >= 9 && face == 0) continue;
                    b.set(x, y, z, y == 10 ? s(Blocks.GRASS_BLOCK) : rock[b.rand(x * 7 + y * 131 + z * 13, rock.length)]);
                }
            }
        // the river on the terrace, the lip and the falls
        b.clear(c - 2, 9, 11, c + 2, 10, d - 1);
        b.fill(c - 2, 8, 11, c + 2, 8, d - 1, Blocks.STONE);
        b.fill(c - 2, 9, 11, c + 2, 9, d - 1, Blocks.WATER);
        for (int z = 12; z < d; z++) {
            b.set(c - 3, 10, z, Blocks.MOSSY_COBBLESTONE);
            b.set(c + 3, 10, z, Blocks.MOSSY_COBBLESTONE);
        }
        BlockState falling = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
        b.fill(c - 2, 1, 10, c + 2, 9, 10, falling);
        // ---- the plunge pool (z 2..10) with its rim, rocks and lilies
        for (int x = 0; x < w; x++)
            for (int z = 0; z <= 10; z++) {
                double e = sq((x - c) / 7.6) + sq((z - 6.5) / 4.4);
                if (e > 1.25) continue;
                if (e > 1.0) {
                    b.set(x, 0, z, b.rand(x * 5 + z, 3) == 0 ? s(Blocks.MOSSY_COBBLESTONE) : k(ArchBlocks.FIELDSTONE));
                    continue;
                }
                b.set(x, -3, z, Blocks.GRAVEL);
                b.fill(x, -2, z, x, 0, z, Blocks.WATER);
            }
        b.fill(c - 2, -2, 10, c + 2, 0, 10, Blocks.WATER);
        for (int[] p : new int[][]{{6, 3}, {14, 8}, {5, 8}, {15, 4}}) b.set(p[0], 1, p[1], Blocks.LILY_PAD);
        for (int[] r : new int[][]{{3, 9}, {17, 9}}) {
            b.set(r[0], 0, r[1], Blocks.MOSSY_COBBLESTONE);
            b.set(r[0], 1, r[1], Arch.slab(s(Blocks.MOSSY_COBBLESTONE_SLAB), SlabType.BOTTOM));
        }
        // stepping stones along the cliff foot to the cave
        for (int x = 4; x <= 7; x++) b.set(x, 0, 10, k(ArchBlocks.FIELDSTONE));
        // ---- the great torii standing in the water
        torii(b, 4, 16, 5, 0, 11);
        for (int px : new int[]{4, 16}) b.fill(px, -2, 5, px, -1, 5, Blocks.RED_CONCRETE);
        hang(b, c, 7, 5, redLantern);
        // ---- the cave behind the falls
        b.clear(c - 3, 1, 11, c + 3, 4, 15);
        b.fill(c - 3, 0, 11, c + 3, 0, 15, Blocks.STONE);
        b.chest(c, 1, 15, Direction.NORTH, Buildings.LOOT_TREASURE);
        mark(b, "treasure", c, 1, 15);
        b.set(c - 3, 1, 15, Blocks.AMETHYST_CLUSTER);
        b.set(c + 2, 1, 15, Blocks.DECORATED_POT);
        b.set(c + 3, 1, 14, k(ArchBlocks.ROPE_COIL));
        b.set(c - 2, 1, 14, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        hang(b, c, 4, 13, s(Blocks.LANTERN));
        // ---- the guard house (bansho) on the east bank, a jetty into the pool
        b.fill(19, 0, 1, 23, 0, 7, k(ArchBlocks.FIELDSTONE));
        b.fill(20, 0, 2, 22, 0, 6, Blocks.SPRUCE_PLANKS);
        wanoWalls(b, m, 19, 1, 23, 7, 1, 4, 3, true, true);
        b.clear(20, 1, 2, 22, 4, 6);
        Arch.hip(b, m, 19, 1, 23, 7, 5, 1);
        b.fill(20, 4, 2, 22, 4, 6, Blocks.SPRUCE_PLANKS);
        b.clear(20, 4, 2, 22, 4, 6);
        b.bigDoor(19, 1, 4, m.door.getBlock(), Direction.WEST);
        b.bed(22, 1, 5, Blocks.WHITE_BED, Direction.SOUTH);
        b.barrel(22, 1, 2, Buildings.LOOT_HOUSE);
        b.set(20, 1, 2, k(ArchBlocks.PLANK_TABLE));
        b.set(20, 2, 2, Arch.lantern(s(Blocks.LANTERN), false));
        b.set(20, 1, 6, Arch.connect(s(Blocks.DARK_OAK_FENCE), false, false, false, false));
        b.set(20, 2, 6, Blocks.LIGHTNING_ROD);
        hang(b, 18, 4, 2, redLantern);
        hang(b, 18, 4, 6, redLantern);
        npc(b, "guard", 21, 1, 3);
        npc(b, "guard", 18, 1, 0);
        for (int x = 14; x <= 18; x++) {
            b.set(x, 0, 7, k(ArchBlocks.DOCK_PLANKS));
            b.set(x, 0, 8, k(ArchBlocks.DOCK_PLANKS));
        }
        for (int x : new int[]{14, 17}) b.fill(x, -2, 9, x, 0, 9, beam(ArchBlocks.WEATHERED_BEAM, Direction.Axis.Y));
        b.set(14, 1, 7, Arch.decor(ArchBlocks.BOLLARD, Direction.NORTH));
        b.set(18, 1, 8, Arch.decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        npc(b, "dockworker", 16, 1, 7);
        // a little ferry moored at the jetty
        for (int x = 13; x <= 17; x++) b.set(x, 0, 9, x == 13 || x == 17 ? s(Blocks.SPRUCE_SLAB) : s(Blocks.SPRUCE_PLANKS));
        b.set(15, 1, 9, Arch.stair(s(Blocks.SPRUCE_STAIRS), Direction.EAST, Half.BOTTOM));
        b.marker("legend", c, 1, 1, tag("story", "waterfall_gate"));
        // ---- the stone stair up the west side of the cliff
        flight(b, s(Blocks.STONE_BRICK_STAIRS), 1, 1, 8, Direction.SOUTH, 10, 2, s(Blocks.STONE_BRICKS), 1);
        // west bank path and lanterns
        for (int z = 0; z <= 7; z++) b.set(1, 0, z, Blocks.GRAVEL);
        toro(b, 2, 1, 2);
        // ---- on the terrace: a red arched bridge over the river, a small torii and the shrine
        int ty = 10;
        int[] arch = {1, 2, 3, 3, 3, 2, 1}; // half-blocks above the terrace, x = c-3 .. c+3
        for (int i = 0; i < arch.length; i++) {
            int x = c - 3 + i, hh = arch[i];
            for (int z = 16; z <= 17; z++) {
                b.set(x, ty + (hh - 1) / 2 + 1, z, Arch.slab(s(Blocks.MANGROVE_SLAB), hh % 2 == 1 ? SlabType.BOTTOM : SlabType.TOP));
            }
            int ry = ty + (hh - 1) / 2 + 2;
            b.set(x, ry, 15, Arch.connect(s(Blocks.MANGROVE_FENCE), false, i < arch.length - 1, false, i > 0));
            b.set(x, ry, 18, Arch.connect(s(Blocks.MANGROVE_FENCE), false, i < arch.length - 1, false, i > 0));
        }
        for (int z = 16; z <= 17; z++) {
            for (int x = 2; x <= c - 4; x++) b.set(x, ty, z, Blocks.GRAVEL);
            for (int x = c + 4; x <= 15; x++) b.set(x, ty, z, Blocks.GRAVEL);
        }
        torii(b, 15, 18, 13, ty, 6, false);
        // the shrine on a stone plinth, entered from the west
        b.fill(15, ty + 1, 13, 22, ty + 1, 20, Blocks.POLISHED_ANDESITE);
        b.set(14, ty + 1, 16, Arch.stair(s(Blocks.POLISHED_ANDESITE_STAIRS), Direction.EAST, Half.BOTTOM));
        b.set(14, ty + 1, 17, Arch.stair(s(Blocks.POLISHED_ANDESITE_STAIRS), Direction.EAST, Half.BOTTOM));
        int sx1 = 16, sx2 = 21, sz1 = 14, sz2 = 19, sy = ty + 2;
        b.fill(sx1, sy, sz1, sx2, sy + 3, sz2, m.wall);
        b.clear(sx1 + 1, sy, sz1 + 1, sx2 - 1, sy + 3, sz2 - 1);
        for (int[] p : new int[][]{{sx1, sz1}, {sx2, sz1}, {sx1, sz2}, {sx2, sz2}, {sx1, 16}, {sx1, 17}, {sx2, 16}, {sx2, 17}})
            b.fill(p[0], sy, p[1], p[0], sy + 3, p[1], Blocks.RED_CONCRETE);
        for (int x = sx1; x <= sx2; x++) {
            b.set(x, sy + 3, sz1, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
            b.set(x, sy + 3, sz2, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
        }
        for (int z = sz1; z <= sz2; z++) {
            b.set(sx1, sy + 3, z, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
            b.set(sx2, sy + 3, z, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
        }
        for (int x : new int[]{18, 19}) {
            b.set(x, sy + 1, sz1, Arch.along(m.window, Direction.Axis.X));
            b.set(x, sy + 1, sz2, Arch.along(m.window, Direction.Axis.X));
        }
        b.clear(sx1, sy, 16, sx1, sy + 2, 17);
        b.fill(sx1 + 1, sy + 4, sz1 + 1, sx2 - 1, sy + 4, sz2 - 1, Blocks.SPRUCE_PLANKS);
        Mats roof = wano(b);
        Arch.hip(b, roof, sx1, sz1, sx2, sz2, sy + 4, 2);
        b.set(20, sy, 16, Blocks.GOLD_BLOCK);
        b.set(20, sy, 17, Blocks.GOLD_BLOCK);
        b.set(20, sy + 1, 16, candles(Blocks.WHITE_CANDLE, 3));
        b.set(20, sy + 1, 17, Blocks.TINTED_GLASS);
        b.set(19, sy + 3, 16, bellCeiling());
        b.chest(20, sy, 15, Direction.WEST, Buildings.LOOT_SHOP);
        npc(b, "priest", 18, sy, 17);
        hang(b, 17, sy + 3, 18, redLantern);
        toro(b, 13, ty + 1, 14);
        toro(b, 13, ty + 1, 19);
        tree(b, 5, ty + 1, 18, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, 4, 3, 211, null);
        // a fence along the cliff edge on both sides of the river
        for (int x = 2; x <= c - 4; x++) b.set(x, ty + 1, 12, Arch.connect(s(Blocks.DARK_OAK_FENCE), false, x < c - 4, false, x > 2));
        for (int x = c + 4; x <= 23; x++) b.set(x, ty + 1, 12, Arch.connect(s(Blocks.DARK_OAK_FENCE), false, x < 23, false, x > c + 4));
    }

    // ============================================================== Onigashima: Skull Dome

    private static final double SK_CX = 25, SK_CY = 11, SK_CZ = 29, SK_RX = 19, SK_RY = 18, SK_RZ = 20, SK_P = 2.2;

    /** Superellipsoid value of the skull's cranium (<= 1 inside), shrunk by {@code in} blocks for the hollow. */
    private static double skullV(int x, int y, int z, double in) {
        return Math.pow(Math.abs(x - SK_CX) / (SK_RX - in), SK_P) + Math.pow(Math.abs(y - SK_CY) / (SK_RY - in), SK_P) + Math.pow(Math.abs(z - SK_CZ) / (SK_RZ - in), SK_P);
    }

    /** Point on one horn's centre line (cubic Bezier), side = -1 west / +1 east, t in [0,1]. */
    private static double[] hornAt(int side, double t) {
        double[][] p = {{14, 20, 28}, {23, 23, 27}, {24, 33, 25}, {19.5, 40, 23}};
        double u = 1 - t;
        double[] o = new double[3];
        for (int k = 0; k < 3; k++) o[k] = u * u * u * p[0][k] + 3 * u * u * t * p[1][k] + 3 * u * t * t * p[2][k] + t * t * t * p[3][k];
        o[0] = SK_CX + side * o[0];
        return o;
    }

    /**
     * Kaido's Skull Dome: a giant bone skull (a squared cranium with a flat face: deep eye sockets glazed black, a
     * nasal cavity, cheekbones, a heavy brow and a row of fangs over the mouth that is the gate) with two banded horns
     * sweeping up from its temples, and a Wano castle of two kawara-roofed tiers standing on its crown. Inside, the
     * Live Floor: banquet tables and sake barrels before a stage with drums and lights, a gallery round the walls
     * reached by two stairs, looking out through the eyes, and the overlord's throne deck over the stage; a vault
     * lies under the stage. Outside, a torii and red lantern posts line the approach.
     */
    static void skullDome(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 25; // 51 x 51, the face looks north
        Mats m = wano(b);
        BlockState bone = s(Blocks.CALCITE), boneB = s(Blocks.BONE_BLOCK), boneD = s(Blocks.DIORITE), fang = s(Blocks.SMOOTH_QUARTZ);
        BlockState fangSt = s(Blocks.SMOOTH_QUARTZ_STAIRS), dark = s(Blocks.BLACKSTONE), glass = s(Blocks.BLACK_STAINED_GLASS);
        BlockState redLantern = k(ArchBlocks.RED_PAPER_LANTERN);
        site(b, Blocks.BLACKSTONE, Blocks.STONE, 50);
        patchGround(b, 221, 0, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.POLISHED_BLACKSTONE);
        // ---- the skull: cranium cut flat at the face (z = 13) and at the crown (y = 27), hollow inside; the cranial
        // sutures run over it as grey zigzag seams
        for (int x = 0; x < w; x++)
            for (int z = 13; z < d; z++)
                for (int y = 1; y <= 27; y++) {
                    if (skullV(x, y, z, 0) > 1) continue;
                    boolean hollow = y <= 25 && z >= 16 && skullV(x, y, z, 2.6) <= 1;
                    if (hollow) {
                        b.set(x, y, z, Blocks.AIR);
                        continue;
                    }
                    boolean surface = skullV(x, y, z, 1.2) > 1;
                    int zig = (int) Math.round(1.2 * Math.sin(z * 1.3) + 0.6 * Math.sin(y * 2.1));
                    boolean suture = surface && z > 16 && y > 8 && (Math.abs(x - c - zig) == 0 && y > 18
                            || Math.abs(z - 24 - (int) Math.round(1.2 * Math.sin(x * 1.3) + 0.6 * Math.sin(y * 1.7))) == 0
                            || Math.abs(z - 40 - (int) Math.round(1.1 * Math.sin(x * 1.1 + 1))) == 0 && y > 12);
                    int r = b.rand(x * 7 + y * 131 + z * 13, 20);
                    b.set(x, y, z, suture ? s(Blocks.TUFF) : r < 3 ? boneB : r == 3 ? boneD : bone);
                }
        // ---- the face: eye sockets (glazed black, they are the gallery's windows), the nose, cheekbones and brow
        for (int side : new int[]{-1, 1}) {
            double ex = c + side * 6.5, ey = 15;
            for (int x = (int) ex - 5; x <= (int) ex + 5; x++)
                for (int y = 10; y <= 20; y++) {
                    double e = sq((x - ex) / 3.9) + sq((y - ey) / 3.9);
                    if (e > 1) continue;
                    b.clear(x, y, 13, x, y, 14);
                    b.set(x, y, 15, glass);
                    if (e > 0.62) b.set(x, y, 14, dark); // the socket's dark inner rim
                }
            // heavy brow over the eye, cheekbone below it
            for (int x = (int) Math.round(ex) - 4; x <= (int) Math.round(ex) + 4; x++) b.set(x, 19, 12, Arch.stair(fangSt, Direction.SOUTH, Half.TOP));
            for (int x = (int) Math.round(ex) - 3; x <= (int) Math.round(ex) + 3; x++) b.set(x, 20, 12, Arch.slab(s(Blocks.SMOOTH_QUARTZ_SLAB), SlabType.BOTTOM));
            int cbx = c + side * 10;
            b.fill(cbx - 1, 9, 12, cbx + 1, 10, 12, bone);
            b.set(cbx, 11, 12, Arch.slab(s(Blocks.SMOOTH_QUARTZ_SLAB), SlabType.BOTTOM));
            b.set(cbx - 1, 8, 12, Arch.stair(fangSt, Direction.SOUTH, Half.TOP));
            b.set(cbx, 8, 12, Arch.stair(fangSt, Direction.SOUTH, Half.TOP));
            b.set(cbx + 1, 8, 12, Arch.stair(fangSt, Direction.SOUTH, Half.TOP));
        }
        for (int x = c - 2; x <= c + 2; x++) b.set(x, 18, 12, Arch.stair(fangSt, Direction.SOUTH, Half.TOP));
        int[][] nose = {{12, 0}, {11, 1}, {10, 1}, {9, 2}, {8, 2}};
        for (int[] n : nose) {
            b.clear(c - n[1], n[0], 13, c + n[1], n[0], 14);
            b.fill(c - n[1], n[0], 15, c + n[1], n[0], 15, dark);
        }
        // ---- the mouth: the gate, under a row of fangs, with stubby lower teeth either side
        b.clear(c - 6, 1, 13, c + 6, 5, 15);
        b.fill(c - 6, 0, 13, c + 6, 0, 15, Blocks.POLISHED_BLACKSTONE);
        for (int x = c - 6; x <= c + 6; x++) {
            if (((x - c) & 1) != 0) continue;
            b.set(x, 5, 13, fang);
            b.set(x, 4, 13, Arch.stair(fangSt, Direction.SOUTH, Half.TOP));
        }
        for (int x = c - 7; x <= c + 7; x++) b.set(x, 6, 12, Arch.stair(fangSt, Direction.SOUTH, Half.TOP));
        for (int x : new int[]{c - 6, c - 4, c + 4, c + 6}) {
            b.set(x, 1, 13, fang);
            b.set(x, 2, 13, Arch.stair(fangSt, Direction.NORTH, Half.BOTTOM));
        }
        // ---- the horns: banded bone sweeping out and up from the temples, dark at the tips
        for (int side : new int[]{-1, 1}) {
            for (double t = 0; t <= 1.0001; t += 0.02) {
                double[] p = hornAt(side, t);
                double r = 4.6 - 3.8 * t;
                BlockState hb = t > 0.9 ? s(Blocks.BLACKSTONE) : ((int) (t * 16)) % 4 == 3 ? bone : boneB;
                int R = (int) Math.ceil(r);
                for (int dx = -R; dx <= R; dx++)
                    for (int dy = -R; dy <= R; dy++)
                        for (int dz = -R; dz <= R; dz++) {
                            if (dx * dx + dy * dy + dz * dz > r * r + 0.3) continue;
                            int x = (int) Math.round(p[0]) + dx, y = (int) Math.round(p[1]) + dy, z = (int) Math.round(p[2]) + dz;
                            if (x < 0 || x >= w || z < 0 || z >= d) continue;
                            if (skullV(x, y, z, 2.6) <= 1 && y <= 25 && z >= 16) continue; // never into the hall
                            b.set(x, y, z, hb);
                        }
            }
        }
        // ---- the castle on the crown: a low stone base and two tiers under kawara roofs
        ishigaki(sub(b, 0, 27, 0, w, d, Rotation.NONE), 16, 21, 34, 37, 2, 1);
        int[] t1 = {18, 23, 32, 35}, t2 = {20, 25, 30, 33};
        b.fill(t1[0], 29, t1[1], t1[2], 29, t1[3], Blocks.DARK_OAK_PLANKS);
        wanoWalls(b, m, t1[0], t1[1], t1[2], t1[3], 30, 32, 4, true, true);
        b.clear(t1[0] + 1, 30, t1[1] + 1, t1[2] - 1, 32, t1[3] - 1);
        b.fill(t2[0], 33, t2[1], t2[2], 33, t2[3], Blocks.DARK_OAK_PLANKS);
        skirt(b, m, t2[0], t2[1], t2[2], t2[3], 33, 2, 2, 4);
        wanoWalls(b, m, t2[0], t2[1], t2[2], t2[3], 34, 37, 4, true, true);
        b.clear(t2[0] + 1, 34, t2[1] + 1, t2[2] - 1, 36, t2[3] - 1);
        chidori(b, m, Direction.NORTH, t2[0], t2[1], t2[2], t2[3], c, 33, 2, 2, 3);
        chidori(b, m, Direction.SOUTH, t2[0], t2[1], t2[2], t2[3], c, 33, 2, 2, 3);
        irimoya(b, m, t2[0], t2[1], t2[2], t2[3], 36, 2, 3, m.wall, true);
        for (int x : new int[]{t1[0] - 1, t1[2] + 1})
            for (int z : new int[]{t1[1] - 1, t1[3] + 1}) {
                b.set(x, 30, z, s(Blocks.DARK_OAK_FENCE));
                b.set(x, 31, z, redLantern);
            }
        b.marker("arc_anchor", c, 1, 4, tag("arc", "skull_island"));
        // ---- the approach: a torii, red lantern posts, braziers and sake barrels
        torii(b, c - 7, c + 7, 4, 0, 11);
        for (int z = 0; z <= 12; z++) b.fill(c - 2, 0, z, c + 2, 0, z, Blocks.POLISHED_BLACKSTONE_BRICKS);
        for (int z = 1; z <= 11; z += 3)
            for (int x : new int[]{c - 4, c + 4}) {
                b.fill(x, 1, z, x, 3, z, Blocks.DARK_OAK_FENCE);
                b.set(x, 4, z, Arch.lantern(redLantern, false));
            }
        for (int x : new int[]{c - 11, c + 11}) {
            b.set(x, 1, 10, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.set(x, 2, 10, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        }
        for (int[] p : new int[][]{{8, 6}, {9, 6}, {8, 7}, {41, 6}, {42, 6}, {42, 7}}) b.set(p[0], 1, p[1], Blocks.BARREL);
        b.set(8, 2, 6, Blocks.BARREL);
        b.set(42, 2, 6, Blocks.BARREL);
        for (int x : new int[]{4, 46}) {
            b.fill(x, 1, 3, x, 9, 3, Blocks.DARK_OAK_FENCE);
            b.set(x, 9, 4, banner(Blocks.BLACK_WALL_BANNER, Direction.SOUTH));
            b.set(x, 8, 4, banner(Blocks.BLACK_WALL_BANNER, Direction.SOUTH));
        }
        npc(b, "pirate", c + 3, 1, 9);
        // ---- the Live Floor
        for (int x = 0; x < w; x++)
            for (int z = 16; z < d; z++)
                if (skullV(x, 1, z, 2.6) <= 1) b.set(x, 0, z, ((x * 3 + z) % 7 == 0) ? s(Blocks.POLISHED_BLACKSTONE) : s(Blocks.DARK_OAK_PLANKS));
        carpet(b, c - 1, 1, 13, c + 1, 36, Blocks.RED_CARPET);
        for (int tz : new int[]{20, 26, 32}) {
            longTableX(b, c - 12, c - 4, 1, tz, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
            longTableX(b, c + 4, c + 12, 1, tz, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
        }
        for (int z = 19; z <= 35; z += 4) {
            b.barrel(c - 14, 1, z, Buildings.LOOT_TAVERN);
            b.barrel(c + 14, 1, z, Buildings.LOOT_TAVERN);
        }
        npc(b, "pirate", c - 8, 1, 23);
        npc(b, "pirate", c + 7, 1, 23);
        npc(b, "pirate", c - 6, 1, 29);
        npc(b, "pirate", c + 9, 1, 29);
        npc(b, "pirate", c, 1, 17);
        // chandeliers: lanterns on chains from the vault of the dome
        for (int[] l : new int[][]{{c, 22}, {c, 31}, {c - 9, 26}, {c + 9, 26}}) {
            int topY = 25;
            while (topY > 12 && skullV(l[0], topY, l[1], 2.6) > 1) topY--;
            b.fill(l[0], 13, l[1], l[0], topY, l[1], Blocks.CHAIN);
            b.set(l[0], 12, l[1], Arch.lantern(s(Blocks.LANTERN), true));
        }
        // the stage, its lights, drums and curtain
        b.fill(c - 8, 1, 38, c + 8, 2, 44, Blocks.DARK_OAK_PLANKS);
        for (int x = c - 8; x <= c + 8; x++) b.set(x, 1, 37, Arch.stair(s(Blocks.DARK_OAK_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.set(c - 3, 3, 41, Blocks.NOTE_BLOCK);
        b.set(c + 3, 3, 41, Blocks.NOTE_BLOCK);
        b.set(c - 4, 3, 42, Blocks.NOTE_BLOCK);
        b.set(c + 4, 3, 42, Blocks.NOTE_BLOCK);
        b.set(c, 3, 43, Blocks.JUKEBOX);
        for (int x : new int[]{c - 8, c + 8}) {
            b.fill(x, 3, 38, x, 6, 38, Blocks.IRON_BARS);
            b.set(x, 7, 38, Blocks.SEA_LANTERN);
        }
        b.fill(c - 8, 3, 45, c + 8, 9, 45, Blocks.PURPLE_WOOL);
        b.set(c - 4, 7, 44, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        b.set(c + 4, 7, 44, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        npc(b, "pirate_captain", c, 3, 40);
        // ---- the gallery round the walls at y = 10, two stairs up, the throne deck over the stage
        for (int x = 0; x < w; x++)
            for (int z = 16; z < d; z++) {
                if (skullV(x, 11, z, 2.6) > 1) continue;
                boolean band = skullV(x, 11, z, 5.6) > 1 || z <= 18; // round the walls, and a balcony behind the eyes
                if (band) b.set(x, 10, z, Blocks.DARK_OAK_PLANKS);
                else if (skullV(x, 11, z, 6.6) > 1 || z == 19) b.set(x, 11, z, Arch.connect(s(Blocks.DARK_OAK_FENCE), false, false, false, false));
            }
        b.fill(c - 7, 10, 38, c + 7, 10, 46, Blocks.DARK_OAK_PLANKS);
        for (int x = c - 7; x <= c + 7; x++) b.set(x, 11, 38, Arch.connect(s(Blocks.DARK_OAK_FENCE), false, x < c + 7, false, x > c - 7));
        for (int side : new int[]{-1, 1}) {
            int sx = side < 0 ? c - 13 : c + 12; // two wide: sx, sx+1
            flight(b, s(Blocks.DARK_OAK_STAIRS), side < 0 ? sx + 1 : sx + 1, 1, 24, Direction.SOUTH, 10, 2, s(Blocks.DARK_OAK_PLANKS), 1);
            b.clear(sx, 11, 33, sx + 1, 12, 34);
        }
        b.fill(c - 2, 11, 43, c + 2, 11, 45, Blocks.GOLD_BLOCK);
        b.set(c, 12, 44, stair(Blocks.BLACKSTONE_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.fill(c, 12, 45, c, 14, 45, Blocks.RED_WOOL);
        b.set(c - 1, 12, 45, Blocks.WITHER_SKELETON_SKULL);
        b.set(c + 1, 12, 45, Blocks.WITHER_SKELETON_SKULL);
        b.chest(c - 5, 11, 44, Direction.EAST, Buildings.LOOT_TREASURE);
        mark(b, "treasure", c - 5, 11, 44);
        b.fill(c + 4, 11, 44, c + 5, 11, 45, Blocks.GOLD_BLOCK);
        b.set(c + 5, 11, 42, Blocks.BARREL);
        npc(b, "pirate_captain", c, 11, 41);
        npc(b, "lookout", c - 7, 11, 17);
        for (int[] l : new int[][]{{c - 12, 20}, {c + 12, 20}, {c - 14, 30}, {c + 14, 30}}) {
            int topY = 25;
            while (topY > 14 && skullV(l[0], topY, l[1], 2.6) > 1) topY--;
            b.fill(l[0], 15, l[1], l[0], topY, l[1], Blocks.CHAIN);
            b.set(l[0], 14, l[1], Arch.lantern(redLantern, true));
        }
        // ---- the vault under the stage (hatch at the stage's east corner)
        shell(b, c - 5, -7, 38, c + 5, -1, 44, Blocks.POLISHED_BLACKSTONE_BRICKS);
        hatch(b, c + 7, 2, 39, -6, Direction.NORTH, Blocks.DARK_OAK_TRAPDOOR, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.clear(c + 5, -6, 39, c + 7, -5, 39);
        for (int x = c - 4; x <= c + 2; x += 2) b.set(x, -6, 43, Blocks.GOLD_BLOCK);
        b.chest(c - 4, -6, 40, Direction.EAST, Buildings.LOOT_TREASURE);
        b.hanging(c, -2, 41, Blocks.SOUL_LANTERN);
        mark(b, "treasure", c - 4, -6, 40);
    }

    private static double sq(double v) {
        return v * v;
    }

    // ============================================================== Baltigo: the revolutionaries' headquarters

    /**
     * A square fortress tower: solid stone to {@code solidTo}, then a walled lookout with slit windows, an outward
     * corbel course of upside-down stairs carrying the parapet, and merlons on top (at {@code top}).
     */
    private static void fortTower(Builder b, int x1, int z1, int x2, int z2, int solidTo, int top, BlockState stone, BlockState trim) {
        if (solidTo >= 1) b.fill(x1, 1, z1, x2, solidTo, z2, stone);
        b.fill(x1, solidTo + 1, z1, x2, top - 2, z2, stone);
        b.clear(x1 + 1, solidTo + 1, z1 + 1, x2 - 1, top - 2, z2 - 1);
        b.fill(x1 + 1, solidTo, z1 + 1, x2 - 1, solidTo, z2 - 1, trim);
        BlockState cor = s(Blocks.STONE_BRICK_STAIRS);
        for (int x = x1; x <= x2; x++) {
            b.set(x, top - 2, z1 - 1, Arch.stair(cor, Direction.SOUTH, Half.TOP));
            b.set(x, top - 2, z2 + 1, Arch.stair(cor, Direction.NORTH, Half.TOP));
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1 - 1, top - 2, z, Arch.stair(cor, Direction.EAST, Half.TOP));
            b.set(x2 + 1, top - 2, z, Arch.stair(cor, Direction.WEST, Half.TOP));
        }
        rail(b, x1 - 1, top - 1, z1 - 1, x2 + 1, z2 + 1, stone);
        b.fill(x1 - 1, top - 1, z1 - 1, x2 + 1, top - 1, z1 - 1, stone);
        merlons(b, x1 - 1, z1 - 1, x2 + 1, z2 + 1, top, stone);
        b.fill(x1, top - 2, z1, x2, top - 2, z2, stone);
        b.fill(x1 + 1, top - 2, z1 + 1, x2 - 1, top - 2, z2 - 1, trim); // the lookout's roof deck
        b.fill(x1, top - 1, z1, x2, top - 1, z2, Blocks.AIR);
        rail(b, x1 - 1, top - 1, z1 - 1, x2 + 1, z2 + 1, stone);
        // slit windows
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        for (int y : new int[]{3, 6}) {
            if (y >= solidTo) continue;
            b.set(mx, y, z1, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
            b.set(mx, y, z2, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
            b.set(x1, y, mz, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.Z));
            b.set(x2, y, mz, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.Z));
        }
    }

    /**
     * Baltigo's grey stone fortress on its white rock: a crenellated curtain wall with corner towers and a twin-towered
     * gatehouse (raised portcullis), a drill yard and supply depot, and a three-storey keep with a balcony porch and a
     * signal belvedere on its roof. Ground floor: war room, infirmary, armoury; first floor: barracks and mess with
     * kitchen; second floor: the leader's office and the archive with its sealed records room; stairs throughout.
     */
    static void revolutionaryHq(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20; // 41 x 41
        BlockState stone = s(Blocks.STONE_BRICKS), band = s(Blocks.POLISHED_ANDESITE), cracked = s(Blocks.CRACKED_STONE_BRICKS);
        BlockState slab = Arch.slab(s(Blocks.SMOOTH_STONE_SLAB), SlabType.BOTTOM), win = k(ArchBlocks.MULLION_WINDOW);
        BlockState oakStairs = s(Blocks.SPRUCE_STAIRS), floorB = s(Blocks.SPRUCE_PLANKS);
        site(b, Blocks.CALCITE, Blocks.DIORITE, 40);
        patchGround(b, 117, 0, Blocks.CALCITE, Blocks.DIORITE, Blocks.WHITE_CONCRETE_POWDER);
        // ---- curtain wall (outer face at 2 / 38, three thick, walk at y = 8) and its parapet
        for (int x = 2; x <= 38; x++)
            for (int z = 2; z <= 38; z++) {
                boolean ring = x <= 4 || x >= 36 || z <= 4 || z >= 36;
                if (!ring) continue;
                for (int y = 1; y <= 8; y++) b.set(x, y, z, b.rand(x * 31 + y * 7 + z * 17, 14) == 0 ? cracked : stone);
                b.set(x, 8, z, band);
            }
        for (int x = 2; x <= 38; x++)
            for (int z = 2; z <= 38; z++) {
                boolean outer = x == 2 || x == 38 || z == 2 || z == 38;
                if (!outer) continue;
                b.set(x, 9, z, stone);
                if (((x + z) & 1) == 0) b.set(x, 10, z, stone);
            }
        for (int x = 2; x <= 38; x++) {
            b.set(x, 1, 1, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.SOUTH, Half.BOTTOM));
            b.set(x, 1, 39, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.NORTH, Half.BOTTOM));
        }
        for (int z = 2; z <= 38; z++) {
            b.set(1, 1, z, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.EAST, Half.BOTTOM));
            b.set(39, 1, z, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.WEST, Half.BOTTOM));
        }
        // stairs up to the walk on the inside of the west and east walls
        flight(b, s(Blocks.STONE_BRICK_STAIRS), 5, 1, 21, Direction.NORTH, 8, 2, stone, 1);
        flight(b, s(Blocks.STONE_BRICK_STAIRS), 34, 1, 21, Direction.NORTH, 8, 2, stone, 1);
        // corner towers and the gatehouse towers
        for (int[] t : new int[][]{{1, 1}, {34, 1}, {1, 34}, {34, 34}}) {
            fortTower(b, t[0], t[1], t[0] + 5, t[1] + 5, 8, 14, stone, band);
            // doorways from the walk
            b.clear(t[0] + (t[0] == 1 ? 5 : 0), 9, t[1] + 2, t[0] + (t[0] == 1 ? 5 : 0), 11, t[1] + 3);
            b.clear(t[0] + 2, 9, t[1] + (t[1] == 1 ? 5 : 0), t[0] + 3, 11, t[1] + (t[1] == 1 ? 5 : 0));
        }
        for (int[] t : new int[][]{{13, 1}, {23, 1}}) {
            fortTower(b, t[0], t[1], t[0] + 4, t[1] + 5, 8, 15, stone, band);
            b.clear(t[0], 9, 3, t[0], 11, 4);
            b.clear(t[0] + 4, 9, 3, t[0] + 4, 11, 4);
        }
        // the gate: a passage through the wall between the towers, raised portcullis, banners
        b.fill(18, 1, 1, 22, 8, 5, stone);
        b.clear(18, 1, 1, 22, 4, 5);
        b.fill(18, 0, 1, 22, 0, 5, band);
        b.clear(19, 5, 1, 21, 5, 5);
        run(b, 18, 5, 1, 22, 1, s(Blocks.IRON_BARS));
        b.fill(18, 6, 1, 22, 6, 1, band);
        b.fill(18, 9, 1, 22, 9, 1, stone);
        for (int x = 18; x <= 22; x += 2) b.set(x, 10, 1, stone);
        for (int x : new int[]{16, 24}) {
            b.set(x, 6, 0, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
            b.set(x, 5, 0, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        }
        npc(b, "revolutionary", 17, 1, 7);
        npc(b, "revolutionary", 23, 1, 7);
        // ---- the yard: a stone path, drill dummies and targets, a supply depot, a well
        for (int z = 6; z <= 17; z++) b.fill(c - 2, 0, z, c + 2, 0, z, Blocks.STONE_BRICKS);
        for (int x = 8; x <= 14; x += 3) {
            b.set(x, 1, 10, Arch.connect(s(Blocks.SPRUCE_FENCE), false, false, false, false));
            b.set(x, 2, 10, Blocks.HAY_BLOCK);
            b.set(x, 3, 10, Blocks.CARVED_PUMPKIN);
        }
        for (int x = 27; x <= 33; x += 3) {
            b.set(x, 1, 8, Blocks.TARGET);
            b.set(x, 2, 8, Blocks.TARGET);
        }
        Arch.leanTo(b, Mats.of(BuildStyle.STONE_TOWN, b.seed), 26, 11, 33, 14, 4, Direction.NORTH);
        for (int x : new int[]{26, 33}) b.fill(x, 1, 11, x, 2, 11, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        b.fill(26, 1, 15, 33, 4, 15, stone);
        for (int x = 27; x <= 32; x++) Arch.cargo(b, x, 1, 14, x * 3 + 1);
        b.set(27, 1, 12, k(ArchBlocks.CARGO_CRATE));
        b.set(28, 1, 12, Arch.decor(ArchBlocks.CARGO_SACKS, Direction.NORTH));
        b.fill(7, 1, 13, 9, 1, 15, Blocks.COBBLESTONE_WALL);
        b.set(8, 1, 14, Blocks.WATER);
        b.fill(7, 2, 13, 7, 3, 13, Blocks.SPRUCE_FENCE);
        b.fill(9, 2, 15, 9, 3, 15, Blocks.SPRUCE_FENCE);
        b.fill(7, 4, 13, 9, 4, 15, slab);
        npc(b, "revolutionary", 11, 1, 12);
        npc(b, "revolutionary", 30, 1, 10);
        b.marker("arc_anchor", c, 1, 13, tag("arc", "revolution"));
        // ---- the keep (x 10..30, z 18..34): floors at y = 0, 6, 12, roof deck at 18
        int x1 = 10, x2 = 30, z1 = 18, z2 = 34;
        for (int f = 0; f < 3; f++) {
            int y0 = f * 6;
            b.fill(x1 + 1, y0, z1 + 1, x2 - 1, y0, z2 - 1, f == 0 ? band : floorB);
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    if (!(x == x1 || x == x2 || z == z1 || z == z2)) continue;
                    for (int y = y0 + 1; y <= y0 + 5; y++) b.set(x, y, z, b.rand(x * 13 + y * 5 + z * 11, 16) == 0 ? cracked : stone);
                    b.set(x, y0 + 6, z, band);
                }
            b.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + 5, z2 - 1);
            // paired, three-tall mullion windows in every bay with stone sills
            for (int x = x1 + 2; x <= x2 - 2; x += 4) {
                if (f == 0 && Math.abs(x - c) <= 2) continue;
                for (int dx = 0; dx <= 1; dx++) {
                    b.fill(x + dx, y0 + 2, z1, x + dx, y0 + 4, z1, Arch.along(win, Direction.Axis.X));
                    b.fill(x + dx, y0 + 2, z2, x + dx, y0 + 4, z2, Arch.along(win, Direction.Axis.X));
                    b.set(x + dx, y0 + 1, z1 - 1, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.SOUTH, Half.TOP));
                    b.set(x + dx, y0 + 1, z2 + 1, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.NORTH, Half.TOP));
                }
            }
            for (int z = z1 + 2; z <= z2 - 3; z += 4)
                for (int dz = 0; dz <= 1; dz++) {
                    b.fill(x1, y0 + 2, z + dz, x1, y0 + 4, z + dz, Arch.along(win, Direction.Axis.Z));
                    b.fill(x2, y0 + 2, z + dz, x2, y0 + 4, z + dz, Arch.along(win, Direction.Axis.Z));
                    b.set(x1 - 1, y0 + 1, z + dz, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.EAST, Half.TOP));
                    b.set(x2 + 1, y0 + 1, z + dz, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.WEST, Half.TOP));
                }
        }
        for (int x = x1; x <= x2; x += 4)
            for (int z : new int[]{z1, z2}) b.fill(x, 1, z, x, 17, z, band); // pilasters
        for (int z = z1; z <= z2; z += 4)
            for (int x : new int[]{x1, x2}) b.fill(x, 1, z, x, 17, z, band);
        // roof deck, cornice, crenellated parapet
        b.fill(x1, 18, z1, x2, 18, z2, s(Blocks.SMOOTH_STONE));
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            b.set(x, 18, z1 - 1, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.SOUTH, Half.TOP));
            b.set(x, 18, z2 + 1, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.NORTH, Half.TOP));
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1 - 1, 18, z, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.EAST, Half.TOP));
            b.set(x2 + 1, 18, z, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.WEST, Half.TOP));
        }
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) if (x == x1 || x == x2 || z == z1 || z == z2) b.set(x, 19, z, stone);
        merlons(b, x1, z1, x2, z2, 20, stone);
        // the signal tower on the roof: radio room, a room above, a lookout deck and the mast with the flag
        Builder roof = sub(b, 0, 18, 0, w, d, Rotation.NONE);
        int bx1 = 17, bx2 = 23, bz1 = 25, bz2 = 30;
        fortTower(roof, bx1, bz1, bx2, bz2, 0, 11, stone, band);
        b.fill(bx1 + 1, 23, bz1 + 1, bx2 - 1, 23, bz2 - 1, floorB);
        for (int x = bx1 + 1; x <= bx2 - 1; x += 2)
            for (int y : new int[]{20, 24}) {
                b.fill(x, y, bz1, x, y + 1, bz1, Arch.along(win, Direction.Axis.X));
                b.fill(x, y, bz2, x, y + 1, bz2, Arch.along(win, Direction.Axis.X));
            }
        for (int y : new int[]{20, 24}) {
            b.fill(bx1, y, 27, bx1, y + 1, 28, Arch.along(win, Direction.Axis.Z));
            b.fill(bx2, y, 27, bx2, y + 1, 28, Arch.along(win, Direction.Axis.Z));
        }
        b.fill(bx1, 23, bz1, bx2, 23, bz2, band);
        b.fill(bx1 + 1, 23, bz1 + 1, bx2 - 1, 23, bz2 - 1, floorB);
        b.bigDoor(bx1, 19, 27, Blocks.SPRUCE_DOOR, Direction.WEST);
        ladder(b, 22, 19, 29, 8, Direction.WEST, null);
        b.set(22, 27, 29, trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.WEST, Half.TOP, false));
        b.set(18, 19, 26, Blocks.NOTE_BLOCK);
        b.set(18, 20, 26, facing(Blocks.OBSERVER, Direction.EAST));
        b.set(19, 19, 26, Blocks.CARTOGRAPHY_TABLE);
        b.set(22, 19, 26, facing(Blocks.LECTERN, Direction.WEST));
        b.set(18, 24, 29, k(ArchBlocks.PLANK_TABLE));
        b.set(19, 24, 26, Blocks.BARREL);
        hang(b, 20, 22, 28, s(Blocks.LANTERN));
        hang(b, 20, 26, 27, s(Blocks.LANTERN));
        b.fill(c, 28, 27, c, 37, 27, Blocks.IRON_BARS);
        b.fill(c - 3, 34, 27, c + 3, 34, 27, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
        b.set(c, 34, 27, Blocks.IRON_BARS);
        b.set(c, 38, 27, Blocks.LIGHTNING_ROD);
        b.set(c - 3, 35, 27, facing(Blocks.END_ROD, Direction.UP));
        b.set(c + 3, 35, 27, facing(Blocks.END_ROD, Direction.UP));
        b.set(c, 28, 27, Blocks.IRON_BLOCK);
        b.fill(c - 1, 29, 27, c - 1, 33, 27, Blocks.SPRUCE_FENCE);
        b.set(c - 1, 33, 28, banner(Blocks.BLACK_WALL_BANNER, Direction.SOUTH));
        b.set(c - 1, 32, 28, banner(Blocks.BLACK_WALL_BANNER, Direction.SOUTH));
        // ---- the balcony porch over the entrance
        for (int[] p : new int[][]{{17, 14}, {23, 14}, {17, 17}, {23, 17}}) b.fill(p[0], 1, p[1], p[0], 11, p[1], band);
        b.fill(17, 12, 14, 23, 12, 17, stone);
        for (int x = 16; x <= 24; x++) b.set(x, 12, 13, Arch.stair(s(Blocks.STONE_BRICK_STAIRS), Direction.SOUTH, Half.TOP));
        run(b, 17, 13, 14, 23, 14, s(Blocks.STONE_BRICK_WALL));
        run(b, 17, 13, 15, 17, 17, s(Blocks.STONE_BRICK_WALL));
        run(b, 23, 13, 15, 23, 17, s(Blocks.STONE_BRICK_WALL));
        b.set(17, 13, 14, Arch.connect(s(Blocks.STONE_BRICK_WALL), false, true, true, false));
        b.set(23, 13, 14, Arch.connect(s(Blocks.STONE_BRICK_WALL), false, false, true, true));
        b.set(c, 11, 13, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 1, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.bigDoor(c, 13, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        hang(b, c, 11, 16, s(Blocks.LANTERN));
        // ---- ground floor: war room (centre), infirmary (west), armoury (east)
        for (int x : new int[]{15, 25}) {
            b.fill(x, 1, z1 + 1, x, 5, z2 - 1, stone);
            b.clear(x, 1, 21, x, 3, 22);
        }
        for (int x = 17; x <= 23; x++)
            for (int z = 23; z <= 27; z++) {
                b.set(x, 1, z, Arch.slab(s(Blocks.DARK_OAK_SLAB), SlabType.TOP));
                int r = b.rand(x * 7 + z, 5);
                b.set(x, 2, z, r < 2 ? s(Blocks.BLUE_CARPET) : r < 4 ? s(Blocks.LIME_CARPET) : s(Blocks.YELLOW_CARPET));
            }
        for (int x = 17; x <= 23; x += 2) {
            chair(b, x, 1, 22, Blocks.SPRUCE_STAIRS, Direction.NORTH);
            chair(b, x, 1, 28, Blocks.SPRUCE_STAIRS, Direction.SOUTH);
        }
        b.set(16, 1, 30, Blocks.CARTOGRAPHY_TABLE);
        b.set(24, 1, 30, facing(Blocks.LECTERN, Direction.WEST));
        b.set(24, 3, 33, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        hang(b, c, 5, 25, s(Blocks.LANTERN));
        npc(b, "scholar", 18, 1, 30);
        npc(b, "revolutionary", 22, 1, 20);
        for (int z = 20; z <= 29; z += 3) b.bed(12, 1, z, Blocks.WHITE_BED, Direction.WEST);
        b.set(14, 1, 32, Blocks.BREWING_STAND);
        b.set(13, 1, 33, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        b.chest(11, 1, 33, Direction.EAST, Buildings.LOOT_HOUSE);
        hang(b, 13, 5, 26, s(Blocks.LANTERN));
        npc(b, "doctor", 13, 1, 31);
        for (int z = 20; z <= 32; z += 2) {
            b.set(x2 - 1, 1, z, Arch.connect(s(Blocks.SPRUCE_FENCE), false, false, false, false));
            b.set(x2 - 1, 2, z, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.Z));
        }
        b.set(27, 1, 19, Blocks.ANVIL);
        b.set(28, 1, 19, Blocks.GRINDSTONE);
        b.barrel(27, 1, 33, Buildings.LOOT_SMITH);
        b.barrel(28, 1, 33, Buildings.LOOT_SMITH);
        b.set(26, 1, 33, k(ArchBlocks.CARGO_CRATE));
        hang(b, 27, 5, 26, s(Blocks.LANTERN));
        npc(b, "revolutionary", 27, 1, 26);
        // stairs: war room -> first floor -> second floor -> roof (a dog-leg at the back of the keep)
        flight(b, oakStairs, 17, 1, 32, Direction.EAST, 6, 2, floorB, 1);
        flight(b, oakStairs, 24, 7, 30, Direction.WEST, 6, 2, floorB, 7);
        flight(b, oakStairs, 17, 13, 32, Direction.EAST, 6, 2, floorB, 13);
        run(b, 19, 7, 31, 21, 31, s(Blocks.SPRUCE_FENCE));
        run(b, 20, 13, 28, 22, 28, s(Blocks.SPRUCE_FENCE));
        run(b, 20, 13, 31, 22, 31, s(Blocks.SPRUCE_FENCE));
        run(b, 18, 19, 31, 21, 31, s(Blocks.IRON_BARS));
        run(b, 18, 19, 32, 18, 33, s(Blocks.IRON_BARS));
        // ---- first floor: barracks (west), mess and kitchen (east)
        for (int z = 20; z <= 29; z += 3) {
            b.bed(12, 7, z, Blocks.GRAY_BED, Direction.WEST);
            b.bed(15, 7, z, Blocks.GRAY_BED, Direction.EAST);
        }
        b.chest(11, 7, 33, Direction.EAST, Buildings.LOOT_MARINE);
        b.set(13, 7, 33, k(ArchBlocks.GOODS_SHELF));
        hang(b, 13, 11, 25, s(Blocks.LANTERN));
        longTableZ(b, 20, 27, 7, 27, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        kitchenX(b, 25, 7, 19, Direction.SOUTH, Buildings.LOOT_TAVERN);
        hang(b, 27, 11, 24, s(Blocks.LANTERN));
        npc(b, "cook", 26, 7, 21);
        npc(b, "revolutionary", c, 7, 23);
        // ---- second floor: the leader's office (east) and the archive (west) with the sealed records room
        carpet(b, 23, 13, 19, 29, 26, Blocks.GRAY_CARPET);
        table(b, 26, 13, 22, Blocks.DARK_OAK_SLAB);
        table(b, 27, 13, 22, Blocks.DARK_OAK_SLAB);
        chair(b, 26, 13, 23, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
        b.set(27, 14, 22, candles(Blocks.CANDLE, 2));
        b.set(29, 13, 19, facing(Blocks.LECTERN, Direction.SOUTH));
        b.chest(29, 13, 25, Direction.WEST, Buildings.LOOT_MARINE);
        b.set(28, 15, z1 + 1, banner(Blocks.BLACK_WALL_BANNER, Direction.SOUTH));
        hang(b, 26, 17, 22, s(Blocks.LANTERN));
        npc(b, "revolutionary", 25, 13, 24);
        for (int z = 20; z <= 26; z += 2) b.fill(11, 13, z, 14, 15, z, Blocks.BOOKSHELF);
        b.set(16, 13, 24, facing(Blocks.LECTERN, Direction.EAST));
        hang(b, 14, 17, 23, s(Blocks.LANTERN));
        npc(b, "scholar", 16, 13, 21);
        // the records room behind the shelves (x 11..14, z 29..33), its door hidden at the end of a shelf row
        b.fill(15, 13, 28, 15, 17, 33, Blocks.BOOKSHELF);
        b.fill(11, 13, 28, 14, 17, 28, Blocks.BOOKSHELF);
        b.door(15, 13, 30, Blocks.SPRUCE_DOOR, Direction.EAST);
        b.set(15, 15, 30, Blocks.BOOKSHELF);
        b.set(15, 13, 29, facing(Blocks.CHISELED_BOOKSHELF, Direction.EAST));
        b.chest(11, 13, 33, Direction.EAST, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 11, 13, 33);
        b.set(13, 13, 31, facing(Blocks.LECTERN, Direction.WEST));
        b.set(12, 17, 31, Blocks.SEA_LANTERN);
    }

    // ============================================================== Egghead: the future laboratory

    private static void sentinel(Builder b, int x, int z) {
        b.fill(x - 1, 1, z, x - 1, 3, z, Blocks.IRON_BLOCK);
        b.fill(x + 1, 1, z, x + 1, 3, z, Blocks.IRON_BLOCK);
        b.fill(x - 1, 4, z, x + 1, 6, z, Blocks.IRON_BLOCK);
        b.set(x, 5, z - 1, Blocks.CYAN_CONCRETE);
        b.fill(x - 2, 5, z, x - 2, 6, z, Blocks.IRON_BLOCK);
        b.fill(x + 2, 5, z, x + 2, 6, z, Blocks.IRON_BLOCK);
        b.set(x - 2, 4, z, facing(Blocks.END_ROD, Direction.DOWN));
        b.set(x + 2, 4, z, facing(Blocks.END_ROD, Direction.DOWN));
        b.set(x, 7, z, facing(Blocks.OBSERVER, Direction.SOUTH));
        b.set(x, 8, z, Blocks.LIGHTNING_ROD);
    }

    /** A small round lab pod: pastel walls with a cyan rim under a glass dome, a door facing {@code door}. */
    private static void pod(Builder b, int cx, int cz, BlockState wall, Direction door) {
        disc(b, cx, cz, 0, 3.4, s(Blocks.SMOOTH_QUARTZ));
        for (int y = 1; y <= 3; y++) ring(b, cx, cz, y, 3.5, y == 2 ? s(Blocks.LIGHT_BLUE_STAINED_GLASS) : wall);
        ring(b, cx, cz, 4, 3.5, s(Blocks.CYAN_CONCRETE));
        ellipsoid(b, cx, 4, cz, 3.6, 3.4, 3.6, s(Blocks.LIGHT_BLUE_STAINED_GLASS), true, 1);
        int dx = cx + door.getStepX() * 3, dz = cz + door.getStepZ() * 3;
        b.clear(dx, 1, dz, dx + door.getStepX(), 3, dz + door.getStepZ());
        b.door(dx, 1, dz, Blocks.IRON_DOOR, door);
        b.set(dx, 3, dz, wall);
        Direction side = door.getClockWise();
        b.set(dx + door.getStepX() + side.getStepX(), 2, dz + door.getStepZ() + side.getStepZ(), button(Blocks.POLISHED_BLACKSTONE_BUTTON, door));
        b.set(dx - door.getStepX() + side.getStepX(), 2, dz - door.getStepZ() + side.getStepZ(), button(Blocks.POLISHED_BLACKSTONE_BUTTON, door.getOpposite()));
        b.set(cx, 7, cz, s(Blocks.SEA_LANTERN));
    }

    /** Radius of the egg's outer surface at height y (0 outside its span). */
    private static double eggR(int y) {
        double dy = y - 13, ry = dy < 0 ? 8 : 18;
        double t = 1 - sq(dy / ry);
        return t <= 0 ? 0 : 12 * Math.sqrt(t);
    }

    /**
     * Vegapunk's laboratory: a great white egg sitting in a round two-tone "cup" building, its shell split by a
     * zigzag glass crack and pierced by portholes, on a white plaza with cyan rings, a landing pad and robot
     * sentinels at the glass entrance. Inside: the robot bay and labs in the cup, a lab floor in the egg, the
     * glowing pink data core above it and, under the dome, the chief scientist's office with a lever-locked vault;
     * straight stairs link every level, and a door opens from the egg onto the cup's roof terrace.
     */
    static void futureLab(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20, cz = 21; // 41 x 41
        BlockState shell = s(Blocks.WHITE_CONCRETE), plaster = k(ArchBlocks.WHITE_PLASTER), sky = k(ArchBlocks.SKY_PLASTER);
        BlockState cyan = s(Blocks.CYAN_CONCRETE), glass = s(Blocks.LIGHT_BLUE_STAINED_GLASS), quartz = s(Blocks.SMOOTH_QUARTZ);
        BlockState steps = s(Blocks.QUARTZ_STAIRS);
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 36);
        // ---- the plaza: white paving with cyan rings and spokes
        disc(b, c, cz, 0, 19.5, s(Blocks.SMOOTH_STONE));
        disc(b, c, cz, 0, 16.4, quartz);
        ring(b, c, cz, 0, 17.6, cyan);
        for (int z = 0; z <= cz - 15; z++) b.fill(c - 2, 0, z, c + 2, 0, z, quartz);
        for (int z = 0; z <= cz - 15; z++) {
            b.set(c - 3, 0, z, cyan);
            b.set(c + 3, 0, z, cyan);
        }
        // ---- the cup: a round two-storey drum (r = 15) with a ribbon window, a cyan rim and a glass-railed terrace
        for (int y = 1; y <= 6; y++) {
            BlockState s = y == 1 ? sky : y == 6 ? cyan : y == 5 ? plaster : glass;
            ring(b, c, cz, y, 15, s);
        }
        for (int k = 0; k < 24; k++) { // structural fins through the ribbon window
            double a = k * Math.PI / 12;
            int x = c + (int) Math.round(Math.cos(a) * 15), z = cz + (int) Math.round(Math.sin(a) * 15);
            b.fill(x, 2, z, x, 4, z, plaster);
        }
        disc(b, c, cz, 6, 14.6, quartz);
        ring(b, c, cz, 7, 15, glass);
        for (int y = 1; y <= 5; y++) disc(b, c, cz, y, 14.4, Blocks.AIR.defaultBlockState());
        disc(b, c, cz, 0, 14.4, quartz);
        // the glass entrance vestibule with sliding doors and call buttons
        b.fill(c - 3, 0, 2, c + 3, 0, 6, quartz);
        b.fill(c - 3, 1, 2, c + 3, 4, 6, glass);
        b.clear(c - 2, 1, 3, c + 2, 4, 8);
        for (int x : new int[]{c - 3, c + 3})
            for (int z : new int[]{2, 6}) b.fill(x, 1, z, x, 4, z, plaster);
        b.fill(c - 4, 5, 1, c + 4, 5, 7, plaster);
        b.fill(c - 4, 5, 1, c + 4, 5, 1, cyan);
        b.fill(c - 2, 1, 2, c + 2, 4, 2, glass);
        b.door(c - 1, 1, 2, Blocks.IRON_DOOR, Direction.NORTH);
        b.door(c + 1, 1, 2, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c, 1, 2, plaster);
        b.set(c, 2, 2, plaster);
        b.set(c - 2, 2, 1, button(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.NORTH));
        b.set(c + 2, 2, 1, button(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.NORTH));
        b.set(c - 2, 2, 3, button(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.SOUTH));
        b.set(c + 2, 2, 3, button(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.SOUTH));
        b.clear(c - 2, 1, 6, c + 2, 4, 8);
        sentinel(b, c - 7, 3);
        sentinel(b, c + 7, 3);
        npc(b, "guard", c - 4, 1, 0);
        npc(b, "guard", c + 4, 1, 0);
        b.marker("arc_anchor", c, 1, cz - 16, tag("arc", "future_island"));
        // three round satellite pods: a greenhouse, a power station and an observatory; lamp posts; the landing pad
        pod(b, 5, 6, k(ArchBlocks.SALMON_PLASTER), Direction.EAST);
        b.set(4, 1, 5, Blocks.POTTED_FLOWERING_AZALEA);
        b.set(4, 1, 7, Blocks.POTTED_AZALEA);
        b.set(3, 1, 6, Blocks.COMPOSTER);
        b.fill(4, 0, 4, 6, 0, 4, Blocks.MOSS_BLOCK);
        b.set(5, 1, 4, Blocks.FLOWERING_AZALEA);
        b.set(6, 1, 8, Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        pod(b, 35, 6, k(ArchBlocks.OCHRE_PLASTER), Direction.WEST);
        b.set(36, 1, 6, Blocks.COPPER_BLOCK);
        b.set(36, 2, 6, Blocks.LIGHTNING_ROD);
        b.set(36, 1, 5, facing(Blocks.OBSERVER, Direction.WEST));
        b.set(36, 1, 7, Blocks.IRON_BLOCK);
        b.set(35, 9, 6, Blocks.LIGHTNING_ROD);
        pod(b, 5, 36, k(ArchBlocks.SKY_PLASTER), Direction.EAST);
        b.set(4, 1, 35, facing(Blocks.LECTERN, Direction.EAST));
        b.set(4, 1, 37, Blocks.CARTOGRAPHY_TABLE);
        b.set(5, 1, 36, Blocks.LODESTONE);
        b.set(5, 2, 36, facing(Blocks.END_ROD, Direction.UP));
        for (int[] l : new int[][]{{37, 21}, {3, 21}}) {
            b.fill(l[0], 1, l[1], l[0], 3, l[1], Blocks.IRON_BARS);
            b.set(l[0], 4, l[1], Blocks.SEA_LANTERN);
            b.set(l[0], 5, l[1], facing(Blocks.END_ROD, Direction.UP));
        }
        disc(b, 35, 35, 0, 4.2, s(Blocks.GRAY_CONCRETE));
        ring(b, 35, 35, 0, 3, s(Blocks.YELLOW_CONCRETE));
        b.set(35, 0, 35, s(Blocks.YELLOW_CONCRETE));
        // ---- the egg (centre y 13, r 12) resting in a cup ring on the terrace; crack band and portholes
        for (int y = 6; y <= 31; y++) {
            double r = eggR(y);
            if (r <= 0.3) continue;
            for (int x = c - 13; x <= c + 13; x++)
                for (int z = cz - 13; z <= cz + 13; z++) {
                    double dd = Math.sqrt(sq(x - c) + sq(z - cz));
                    if (dd > r + 0.5) continue;
                    double ri = Math.max(0, r - 1.4);
                    if (dd < ri - 0.1 && y < 31) {
                        b.set(x, y, z, y < 8 ? shell : Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    double a = Math.atan2(z - cz, x - c);
                    double tri = Math.abs(((a / Math.PI * 4) % 2 + 2) % 2 - 1); // zigzag 0..1, eight teeth round the egg
                    int yc = 16 + (int) Math.round(tri * 3);
                    BlockState sb = (y >= yc && y <= yc + 2) ? glass : shell;
                    for (int p = 0; p < 8; p++) { // portholes on two levels
                        double pa = p * Math.PI / 4 + Math.PI / 8;
                        double ad = Math.abs(Math.atan2(Math.sin(a - pa), Math.cos(a - pa))) * r;
                        if ((sq(ad) + sq(y - 10) <= 2.3) || (sq(ad) + sq(y - 24) <= 2.3)) sb = glass;
                    }
                    b.set(x, y, z, sb);
                }
        }
        b.set(c, 31, cz, quartz);
        b.set(c, 32, cz, Blocks.LIGHTNING_ROD);
        // ---- floors: the cup's ground floor (0), the egg's levels at 8, 15 and 22; the cup ring round the egg's foot
        disc(b, c, cz, 8, eggR(8) - 1.2, quartz);
        disc(b, c, cz, 15, eggR(15) - 1.4, quartz);
        disc(b, c, cz, 22, eggR(22) - 1.4, quartz);
        ring(b, c, cz, 7, 9.5, sky);
        ring(b, c, cz, 8, 9.5, cyan);
        // a door from the egg onto the terrace (north): a step and a landing up to it
        b.clear(c - 1, 9, 11, c + 1, 11, 12);
        b.door(c, 9, 11, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c - 1, 9, 11, glass);
        b.set(c + 1, 9, 11, glass);
        b.set(c - 1, 10, 11, glass);
        b.set(c + 1, 10, 11, glass);
        b.fill(c - 1, 11, 11, c + 1, 11, 11, shell);
        b.fill(c - 1, 7, 10, c + 1, 8, 10, quartz);
        for (int x = c - 1; x <= c + 1; x++) b.set(x, 7, 9, Arch.stair(steps, Direction.SOUTH, Half.BOTTOM));
        b.set(c + 1, 10, 10, button(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.NORTH));
        b.set(c - 1, 10, 12, button(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.SOUTH));
        for (int[] p : new int[][]{{c - 9, cz - 9}, {c + 9, cz - 9}, {c - 11, cz + 6}, {c + 11, cz + 6}}) {
            b.set(p[0], 7, p[1], Arch.decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        }
        b.set(c + 6, 7, cz - 12, k(ArchBlocks.PLANK_BENCH));
        b.set(c - 6, 7, cz - 12, k(ArchBlocks.PLANK_BENCH));
        // ---- ground floor (the cup): reception, labs (west), robot bay (east)
        b.fill(c - 1, 1, cz - 4, c + 1, 3, cz - 2, quartz);
        b.set(c, 4, cz - 3, Blocks.SEA_LANTERN);
        for (int x = c - 11; x <= c - 6; x++) {
            table(b, x, 1, cz - 4, Blocks.SMOOTH_QUARTZ_SLAB);
            table(b, x, 1, cz + 3, Blocks.SMOOTH_QUARTZ_SLAB);
        }
        b.set(c - 10, 2, cz - 4, Blocks.BREWING_STAND);
        b.set(c - 7, 2, cz - 4, Blocks.BREWING_STAND);
        b.set(c - 9, 2, cz + 3, facing(Blocks.END_ROD, Direction.UP));
        b.set(c - 12, 1, cz, Blocks.CRAFTER);
        b.set(c - 12, 1, cz + 1, facing(Blocks.OBSERVER, Direction.EAST));
        for (int k = 0; k < 2; k++) {
            int rx = c + 7 + k * 4, rz = cz + 5;
            b.fill(rx, 1, rz, rx, 2, rz, Blocks.IRON_BLOCK);
            b.set(rx, 3, rz, Blocks.IRON_BLOCK);
            b.set(rx, 4, rz, facing(Blocks.OBSERVER, Direction.NORTH));
            b.set(rx, 1, rz - 1, Blocks.ANVIL);
        }
        b.set(c + 10, 1, cz - 4, facing(Blocks.PISTON, Direction.UP));
        b.set(c + 9, 1, cz - 4, Blocks.SMITHING_TABLE);
        b.barrel(c + 11, 1, cz - 3, Buildings.LOOT_SMITH);
        for (int[] l : new int[][]{{c - 8, cz}, {c + 8, cz}, {c, cz + 9}, {c, cz - 9}}) b.set(l[0], 5, l[1], Blocks.SEA_LANTERN);
        npc(b, "scholar", c - 8, 1, cz);
        npc(b, "scholar", c + 7, 1, cz - 1);
        // ---- stairs: cup -> egg level 1 -> level 2 -> level 3
        flight(b, steps, c + 3, 1, cz + 9, Direction.NORTH, 8, 2, quartz, 1);
        flight(b, steps, c - 3, 9, cz - 5, Direction.SOUTH, 7, 2, quartz, 9);
        flight(b, steps, c + 3, 16, cz + 5, Direction.NORTH, 7, 2, quartz, 16);
        run(b, c + 2, 9, cz + 3, c + 2, cz + 5, s(Blocks.IRON_BARS));
        run(b, c - 5, 16, cz - 2, c - 5, cz, s(Blocks.IRON_BARS));
        run(b, c + 2, 23, cz, c + 2, cz + 2, s(Blocks.IRON_BARS));
        // ---- egg level 1 (floor 8): a lab ring under the portholes
        for (int[] t : new int[][]{{c - 6, cz + 3}, {c - 5, cz + 4}, {c + 6, cz - 3}, {c + 7, cz - 2}}) table(b, t[0], 9, t[1], Blocks.SMOOTH_QUARTZ_SLAB);
        b.set(c - 6, 10, cz + 3, Blocks.BREWING_STAND);
        b.set(c + 6, 10, cz - 3, facing(Blocks.END_ROD, Direction.UP));
        b.set(c + 6, 9, cz + 3, Blocks.LODESTONE);
        b.set(c + 7, 9, cz + 3, Blocks.TARGET);
        b.set(c - 6, 9, cz - 2, Blocks.CARTOGRAPHY_TABLE);
        b.set(c, 14, cz, Blocks.SEA_LANTERN);
        // ---- egg level 2 (floor 15): the data core
        ellipsoid(b, c, 18, cz, 2.6, 2.6, 2.6, Blocks.PINK_STAINED_GLASS.defaultBlockState(), true, -100);
        b.fill(c - 1, 17, cz - 1, c + 1, 19, cz + 1, Blocks.SEA_LANTERN);
        b.fill(c, 16, cz, c, 16, cz, Blocks.QUARTZ_PILLAR);
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            int x = c + (int) Math.round(Math.cos(a) * 7), z = cz + (int) Math.round(Math.sin(a) * 7);
            if (Math.abs(x - (c + 3)) <= 1 || Math.abs(x - (c - 3)) <= 1) continue;
            b.set(x, 16, z, Blocks.NOTE_BLOCK);
            b.set(x, 17, z, facing(Blocks.OBSERVER, Direction.UP));
            b.set(x, 18, z, facing(Blocks.END_ROD, Direction.UP));
        }
        npc(b, "scholar", c - 6, 16, cz + 3);
        // ---- egg level 3 (floor 22): the chief scientist's office under the dome, the lever-locked vault
        table(b, c + 5, 23, cz - 5, Blocks.SMOOTH_QUARTZ_SLAB);
        table(b, c + 6, 23, cz - 5, Blocks.SMOOTH_QUARTZ_SLAB);
        chair(b, c + 5, 23, cz - 4, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
        b.set(c + 6, 24, cz - 5, facing(Blocks.END_ROD, Direction.UP));
        b.set(c + 1, 23, cz - 6, facing(Blocks.LECTERN, Direction.SOUTH));
        b.set(c - 3, 23, cz - 6, k(ArchBlocks.GOODS_SHELF));
        b.set(c - 2, 23, cz - 6, k(ArchBlocks.GOODS_SHELF));
        b.set(c, 30, cz, Blocks.SEA_LANTERN);
        npc(b, "guard", c - 1, 23, cz - 2);
        int vx1 = c - 5, vx2 = c - 2, vz1 = cz + 1, vz2 = cz + 4;
        b.fill(vx1, 23, vz1, vx2, 25, vz2, Blocks.WHITE_CONCRETE);
        b.clear(vx1 + 1, 23, vz1 + 1, vx2 - 1, 24, vz2 - 1);
        b.fill(vx1, 26, vz1, vx2, 26, vz2, Blocks.WHITE_CONCRETE);
        b.door(vx1 + 1, 23, vz1, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(vx1 + 2, 24, vz1 - 1, lever(Direction.NORTH));
        b.set(vx2 + 1, 24, vz1 + 2, lever(Direction.EAST));
        b.chest(vx1 + 2, 23, vz2 - 1, Direction.NORTH, Buildings.LOOT_TREASURE);
        b.set(vx1 + 1, 23, vz2 - 1, Blocks.SEA_LANTERN);
        mark(b, "treasure", vx1 + 2, 23, vz2 - 1);
    }

    // ============================================================== Hachinosu: the pirate hive

    private static final double HV_CX = 20, HV_CY = 10, HV_CZ = 25, HV_RX = 15, HV_RY = 20, HV_RZ = 13, HV_P = 2.3;

    /** Craggy superellipsoid of the skull rock (<= 1 inside), shrunk by {@code in}; the crags come from smooth noise. */
    private static double hiveV(int x, int y, int z, double in) {
        double v = Math.pow(Math.abs(x - HV_CX) / (HV_RX - in), HV_P) + Math.pow(Math.abs(y - HV_CY) / (HV_RY - in), HV_P)
                + Math.pow(Math.abs(z - HV_CZ) / (HV_RZ - in), HV_P);
        double n = Math.sin(x * 0.71 + y * 0.33) * Math.cos(z * 0.63 - y * 0.41) + 0.5 * Math.sin(y * 0.9 + x * 0.2);
        return in > 0 ? v : v - 0.07 * n;
    }

    /** A ramshackle timber shanty (weathered planks on dark posts, slate lean-to roof) clinging to the rock. */
    private static void shanty(Builder b, int x1, int y, int z1, int x2, int z2, Direction low, Block shutter) {
        b.fill(x1, y, z1, x2, y, z2, k(ArchBlocks.WEATHERED_PLANKS));
        b.fill(x1, y + 1, z1, x2, y + 3, z2, k(ArchBlocks.WEATHERED_PLANKS));
        b.clear(x1 + 1, y + 1, z1 + 1, x2 - 1, y + 3, z2 - 1);
        for (int[] p : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            b.fill(p[0], 1, p[1], p[0], y + 3, p[1], beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
        }
        Mats roof = Mats.of(BuildStyle.STONE_TOWN, 7);
        Arch.leanTo(b, roof, x1 - 1, z1 - 1, x2 + 1, z2 + 1, y + 5, low);
        // a shuttered window on each long side
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        b.set(mx, y + 2, z1, Arch.along(k(ArchBlocks.LATTICE_WINDOW), Direction.Axis.X));
        b.set(mx, y + 2, z2, Arch.along(k(ArchBlocks.LATTICE_WINDOW), Direction.Axis.X));
        b.set(mx - 1, y + 2, z1 - 1, Arch.shutterOpen(shutter, Direction.NORTH));
        b.set(mx + 1, y + 2, z1 - 1, Arch.shutterOpen(shutter, Direction.NORTH));
        b.set(mx - 1, y + 2, z2 + 1, Arch.shutterOpen(shutter, Direction.SOUTH));
        b.set(mx + 1, y + 2, z2 + 1, Arch.shutterOpen(shutter, Direction.SOUTH));
        // brackets under the floor
        for (int[] p : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.set(p[0], y - 1, p[1], beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
    }

    /**
     * Hachinosu, the pirates' hive: a craggy black skull of volcanic rock whose cave mouth, fanged with dripstone and
     * rimmed with honeycomb, opens on a harbour plaza (quay, cannons, bollards, market stalls, the bounty board).
     * The rock is honeycombed with levels: the tavern and black market (a hold hidden under the floor), the barracks
     * and cannon gallery behind a timber balcony, the captain's lair looking out through the glowing eye sockets, and
     * the lookout under the crown that flies the Jolly Roger; timber shanties cling to its flanks. Stairs throughout.
     */
    static void pirateHive(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20; // 41 x 41
        BlockState planks = s(Blocks.DARK_OAK_PLANKS), weathered = k(ArchBlocks.WEATHERED_PLANKS), comb = s(Blocks.HONEYCOMB_BLOCK);
        BlockState stairs = s(Blocks.DARK_OAK_STAIRS), fence = s(Blocks.DARK_OAK_FENCE);
        site(b, Blocks.BLACKSTONE, Blocks.STONE, 40);
        patchGround(b, 251, 0, Blocks.COBBLESTONE, Blocks.GRAVEL, Blocks.BLACKSTONE, Blocks.COBBLESTONE);
        // ---- the skull rock (face cut at z = 16), hollow inside behind z = 18
        for (int x = 0; x < w; x++)
            for (int z = 16; z < d; z++)
                for (int y = 1; y <= 31; y++) {
                    if (hiveV(x, y, z, 0) > 1) continue;
                    if (y <= 27 && z >= 18 && hiveV(x, y, z, 2.5) <= 1) {
                        b.set(x, y, z, Blocks.AIR);
                        continue;
                    }
                    double pn = Math.sin(x * 0.35 + z * 0.21) + Math.cos(y * 0.4 - x * 0.17) + Math.sin(z * 0.3 + y * 0.25);
                    BlockState rs = pn > 1.1 ? s(Blocks.ANDESITE) : pn < -1.1 ? s(Blocks.TUFF) : s(Blocks.STONE);
                    if (b.rand(x * 7 + y * 131 + z * 13, 9) == 0) rs = s(Blocks.COBBLESTONE);
                    if (hiveV(x, y, z, 0) > 0.93 && Math.sin(x * 1.3 + y * 0.7) * Math.cos(z * 1.1 - y * 0.5) > 0.75) rs = s(Blocks.DEEPSLATE); // dark crevices
                    b.set(x, y, z, rs);
                }
        // ---- floors at y = 7, 14, 21 inside the rock, the ground floor planked
        for (int x = 0; x < w; x++)
            for (int z = 18; z < d; z++) {
                if (hiveV(x, 1, z, 2.5) <= 1) b.set(x, 0, z, ((x + z) % 5 == 0) ? s(Blocks.SPRUCE_PLANKS) : planks);
                for (int fy : new int[]{7, 14, 21}) if (hiveV(x, fy, z, 2.5) <= 1) b.set(x, fy, z, ((x * 3 + z) % 7 == 0) ? weathered : planks);
            }
        // ---- the face: eye sockets glowing from the lair, a nose cave, the fanged mouth (the gate) rimmed with comb
        for (int side : new int[]{-1, 1}) {
            double ex = c + side * 6, ey = 17.5;
            for (int x = c + side * 1; side < 0 ? x >= c - 11 : x <= c + 11; x += side)
                for (int y = 12; y <= 23; y++) {
                    double e = sq((x - ex) / 3.6) + sq((y - ey) / 3.3);
                    if (e > 1.45) continue;
                    if (e > 1) {
                        if (hiveV(x, y, 16, 0) <= 1) b.set(x, y, 16, s(Blocks.BLACKSTONE));
                        continue;
                    }
                    b.clear(x, y, 16, x, y, 18);
                    if (y == 15) b.set(x, y, 18, Arch.along(s(Blocks.IRON_BARS), Direction.Axis.X));
                }
            b.set((int) ex, 15, 19, Arch.lantern(s(Blocks.LANTERN), false));
        }
        for (int[] n : new int[][]{{12, 0}, {11, 1}, {10, 1}}) {
            b.clear(c - n[1], n[0], 16, c + n[1], n[0], 17);
            b.fill(c - n[1], n[0], 18, c + n[1], n[0], 18, s(Blocks.MAGMA_BLOCK));
        }
        b.clear(c - 5, 1, 16, c + 5, 5, 18);
        b.clear(c - 3, 6, 16, c + 3, 6, 18);
        b.fill(c - 5, 0, 16, c + 5, 0, 18, Blocks.POLISHED_BLACKSTONE_BRICKS);
        for (int x = c - 6; x <= c + 6; x++) b.set(x, Math.abs(x - c) <= 3 ? 7 : 6, 16, comb);
        b.fill(c - 6, 1, 16, c - 6, 5, 16, comb);
        b.fill(c + 6, 1, 16, c + 6, 5, 16, comb);
        for (int x = c - 4; x <= c + 4; x += 2) {
            BlockState tip = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                    .setValue(PointedDripstoneBlock.THICKNESS, net.minecraft.world.level.block.state.properties.DripstoneThickness.TIP);
            b.set(x, Math.abs(x - c) <= 3 ? 6 : 5, 16, tip);
        }
        // ---- the timber cannon balcony across the face at level 1, reached from inside
        for (int x = c - 9; x <= c + 9; x++) {
            b.set(x, 7, 15, weathered);
            b.set(x, 7, 14, weathered);
            b.set(x, 8, 13, Arch.connect(fence, false, x < c + 9, false, x > c - 9));
        }
        for (int x : new int[]{c - 9, c - 3, c + 3, c + 9}) {
            b.fill(x, 1, 14, x, 6, 14, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Y));
            b.set(x, 6, 13, Arch.stair(stairs, Direction.SOUTH, Half.TOP));
        }
        for (int z = 13; z <= 15; z++) {
            b.set(c - 9, 8, z, Arch.connect(fence, z > 13, false, z < 15, false));
            b.set(c + 9, 8, z, Arch.connect(fence, z > 13, false, z < 15, false));
        }
        for (int x : new int[]{c - 4, c + 4}) b.set(x, 8, 14, Arch.decor(ArchBlocks.DECK_CANNON, Direction.NORTH));
        b.set(c - 5, 8, 15, Arch.decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        for (int x : new int[]{c - 7, c + 7}) b.clear(x, 8, 16, x, 10, 18); // doors onto the balcony from inside
        // ---- shanties on the flanks, with doorways cut into the rock
        shanty(b, 1, 7, 21, 5, 26, Direction.WEST, ArchBlocks.RED_SHUTTER.get());
        b.clear(5, 8, 23, 9, 10, 24);
        b.bigDoor(5, 8, 23, Blocks.SPRUCE_DOOR, Direction.WEST);
        shanty(b, 35, 14, 22, 39, 27, Direction.EAST, ArchBlocks.GREEN_SHUTTER.get());
        b.clear(31, 15, 24, 35, 17, 25);
        b.bigDoor(35, 15, 24, Blocks.SPRUCE_DOOR, Direction.EAST);
        b.bed(3, 8, 22, Blocks.RED_BED, Direction.SOUTH);
        b.set(2, 8, 25, Blocks.BARREL);
        b.set(37, 15, 23, k(ArchBlocks.PLANK_TABLE));
        b.set(38, 15, 26, Blocks.BARREL);
        b.set(37, 15, 26, k(ArchBlocks.CARGO_CRATE));
        // honeycomb cells in the rock around the shanties: the hive
        for (int[] p : new int[][]{{6, 12, 20}, {7, 13, 26}, {33, 19, 21}, {34, 13, 28}, {9, 18, 30}, {30, 8, 31}, {12, 24, 27}, {28, 24, 24}}) {
            if (hiveV(p[0], p[1], p[2], 0) <= 1.05) b.set(p[0], p[1], p[2], comb);
        }
        // ---- the harbour plaza: quay, cannons, bollards, cargo, stalls, the bounty board, flags
        for (int x = 0; x < w; x++)
            for (int z = 0; z <= 2; z++) b.set(x, 0, z, z == 0 ? k(ArchBlocks.HARBOUR_STONE) : k(ArchBlocks.DOCK_PLANKS));
        for (int x = 4; x <= 36; x += 8) b.set(x, 1, 1, Arch.decor(ArchBlocks.BOLLARD, Direction.NORTH));
        for (int x = 8; x <= 32; x += 8) b.set(x, 1, 2, Arch.decor(ArchBlocks.DECK_CANNON, Direction.NORTH));
        Arch.cargo(b, 2, 1, 4, 0);
        Arch.cargo(b, 3, 1, 4, 3);
        Arch.cargo(b, 37, 1, 4, 2);
        Arch.cargo(b, 38, 1, 5, 1);
        for (int[] stall : new int[][]{{4, 8}, {32, 8}}) {
            int sx = stall[0], sz = stall[1];
            for (int[] p : new int[][]{{sx, sz}, {sx + 4, sz}, {sx, sz + 2}, {sx + 4, sz + 2}}) b.fill(p[0], 1, p[1], p[0], 3, p[1], Blocks.SPRUCE_FENCE);
            for (int x = sx; x <= sx + 4; x++) {
                b.set(x, 4, sz, Arch.facing(sx < c ? k(ArchBlocks.RED_AWNING) : k(ArchBlocks.YELLOW_AWNING), Direction.NORTH));
                b.set(x, 4, sz + 1, Arch.facing(sx < c ? k(ArchBlocks.RED_AWNING) : k(ArchBlocks.YELLOW_AWNING), Direction.NORTH));
                b.set(x, 4, sz + 2, Arch.facing(sx < c ? k(ArchBlocks.RED_AWNING) : k(ArchBlocks.YELLOW_AWNING), Direction.NORTH));
            }
            for (int x = sx + 1; x <= sx + 3; x++) b.set(x, 1, sz, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
            b.set(sx + 1, 1, sz + 2, k(ArchBlocks.GOODS_SHELF));
            b.set(sx + 3, 1, sz + 2, Blocks.DECORATED_POT);
        }
        b.barrel(34, 1, 10, Buildings.LOOT_SHOP);
        npc(b, "merchant", 34, 1, 9);
        b.marker("bounty_board", 14, 2, 5, new CompoundTag());
        for (int x = 13; x <= 15; x += 2) b.set(x, 1, 5, Arch.connect(fence, false, false, false, false));
        b.set(14, 1, 5, Arch.connect(fence, false, false, false, false));
        b.fill(13, 2, 5, 15, 3, 5, Blocks.DARK_OAK_PLANKS);
        b.fill(13, 4, 5, 15, 4, 5, Arch.slab(s(Blocks.DARK_OAK_SLAB), SlabType.BOTTOM));
        b.set(14, 5, 5, Arch.lantern(s(Blocks.LANTERN), false));
        for (int x : new int[]{1, 39}) {
            b.fill(x, 1, 6, x, 10, 6, Blocks.DARK_OAK_FENCE);
            b.set(x, 10, 7, banner(Blocks.BLACK_WALL_BANNER, Direction.SOUTH));
            b.set(x, 9, 7, banner(Blocks.BLACK_WALL_BANNER, Direction.SOUTH));
        }
        for (int x : new int[]{c - 11, c + 11}) {
            b.set(x, 1, 12, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
            b.set(x, 2, 12, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        }
        npc(b, "pirate", 10, 1, 4);
        npc(b, "pirate", 26, 1, 3);
        b.marker("arc_anchor", c, 1, 6, tag("arc", "pirate_island"));
        // ---- ground level: the tavern (west) and the black market (east)
        b.fill(c - 9, 1, 24, c - 4, 1, 24, Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        for (int x = c - 9; x <= c - 5; x += 2) b.barrel(x, 1, 27, Buildings.LOOT_TAVERN);
        b.set(c - 8, 1, 26, k(ArchBlocks.GOODS_SHELF));
        b.set(c - 6, 1, 26, k(ArchBlocks.GOODS_SHELF));
        npc(b, "bartender", c - 7, 1, 25);
        for (int[] t : new int[][]{{c - 7, 19}, {c - 3, 21}, {c + 4, 19}}) {
            b.set(t[0], 1, t[1], k(ArchBlocks.PLANK_TABLE));
            b.set(t[0], 2, t[1], candles(Blocks.ORANGE_CANDLE, 2));
            chair(b, t[0] - 1, 1, t[1], Blocks.DARK_OAK_STAIRS, Direction.WEST);
            chair(b, t[0] + 1, 1, t[1], Blocks.DARK_OAK_STAIRS, Direction.EAST);
        }
        for (int[] sl : new int[][]{{c + 6, 22}, {c + 9, 26}}) {
            b.fill(sl[0], 1, sl[1], sl[0] + 2, 1, sl[1], Arch.decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
            b.set(sl[0], 1, sl[1] + 1, k(ArchBlocks.GOODS_SHELF));
            b.set(sl[0] + 2, 1, sl[1] + 1, Blocks.DECORATED_POT);
        }
        b.barrel(c + 7, 1, 23, Buildings.LOOT_SHOP);
        npc(b, "merchant", c + 7, 1, 21);
        npc(b, "pirate", c - 1, 1, 18);
        for (int[] l : new int[][]{{c - 6, 22}, {c + 6, 20}, {c, 27}}) hang(b, l[0], 6, l[1], s(Blocks.LANTERN));
        // the hidden hold under the tavern floor (a hatch under a rug behind the bar)
        shell(b, c - 9, -5, 25, c - 4, -1, 30, Blocks.POLISHED_BLACKSTONE_BRICKS);
        hatch(b, c - 4, 0, 26, -4, Direction.EAST, Blocks.DARK_OAK_TRAPDOOR, null);
        b.set(c - 4, 1, 26, Blocks.BROWN_CARPET);
        b.chest(c - 8, -4, 29, Direction.EAST, Buildings.LOOT_TREASURE);
        mark(b, "treasure", c - 8, -4, 29);
        b.set(c - 8, -4, 26, Blocks.GOLD_BLOCK);
        b.set(c - 7, -4, 26, Blocks.HONEY_BLOCK);
        b.set(c - 6, -4, 29, k(ArchBlocks.CARGO_CRATE));
        b.hanging(c - 6, -2, 27, Blocks.LANTERN);
        // ---- stairs between the levels
        flight(b, stairs, c + 6, 1, 31, Direction.NORTH, 7, 2, planks, 1);
        flight(b, stairs, c - 6, 8, 19, Direction.SOUTH, 7, 2, planks, 8);
        flight(b, stairs, c + 6, 15, 30, Direction.NORTH, 7, 2, planks, 15);
        run(b, c + 5, 8, 26, c + 5, 29, fence);
        run(b, c - 8, 15, 21, c - 8, 24, fence);
        run(b, c + 5, 22, 25, c + 5, 28, fence);
        // ---- level 1 (floor 7): barracks, armoury, cannons through the rock
        for (int k = 0; k < 4; k++) b.bed(c - 3 + k * 2, 8, 31, Blocks.BLACK_BED, Direction.SOUTH);
        b.set(c + 9, 8, 27, Blocks.ANVIL);
        b.set(c + 9, 8, 28, Blocks.GRINDSTONE);
        b.chest(c + 9, 8, 29, Direction.WEST, Buildings.LOOT_SMITH);
        for (int side : new int[]{-1, 1}) {
            int x = c + side * 10;
            for (int z = 20; z <= 28; z += 4) {
                if (hiveV(x, 9, z, 2.5) > 1) continue;
                int ox = x + side;
                while (hiveV(ox, 9, z, 0) <= 1 && Math.abs(ox - c) < 19) ox += side;
                b.clear(x + side, 9, z, ox - side, 9, z);
                b.set(x, 8, z, Arch.decor(ArchBlocks.DECK_CANNON, side < 0 ? Direction.WEST : Direction.EAST));
            }
        }
        hang(b, c - 4, 13, 24, s(Blocks.LANTERN));
        hang(b, c + 4, 13, 24, s(Blocks.LANTERN));
        npc(b, "pirate", c + 3, 8, 22);
        // ---- level 2 (floor 14): the captain's lair behind the eyes
        b.fill(c - 2, 15, 31, c + 2, 15, 32, Blocks.GOLD_BLOCK);
        b.set(c, 16, 31, stair(Blocks.BLACKSTONE_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.fill(c, 16, 32, c, 18, 32, Blocks.RED_WOOL);
        b.set(c - 1, 16, 32, Blocks.SKELETON_SKULL);
        b.set(c + 1, 16, 32, Blocks.SKELETON_SKULL);
        b.set(c - 5, 15, 22, Blocks.CARTOGRAPHY_TABLE);
        b.chest(c + 4, 15, 31, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(c + 3, 15, 31, Blocks.GOLD_BLOCK);
        carpet(b, c - 1, 15, 22, c + 1, 30, Blocks.RED_CARPET);
        hang(b, c, 20, 26, s(Blocks.LANTERN));
        npc(b, "pirate_captain", c, 15, 29);
        // ---- level 3 (floor 21): the lookout under the crown, the Jolly Roger above
        b.clear(c - 2, 23, 16, c + 2, 24, 18);
        run(b, c - 2, 22, 18, c + 2, 18, s(Blocks.IRON_BARS));
        hang(b, c, 27, 24, s(Blocks.LANTERN));
        npc(b, "lookout", c - 2, 22, 22);
        int top = 31;
        while (top > 22 && hiveV(c, top, 26, 0) > 1) top--;
        b.fill(c, top + 1, 26, c, top + 9, 26, Blocks.DARK_OAK_FENCE);
        for (int x = c + 1; x <= c + 6; x++)
            for (int y = top + 5; y <= top + 9; y++) {
                boolean skull = (y == top + 7 && (x == c + 3 || x == c + 4)) || (y == top + 8 && x >= c + 2 && x <= c + 5) || (y == top + 6 && (x == c + 2 || x == c + 5));
                b.set(x, y, 26, skull ? s(Blocks.WHITE_WOOL) : s(Blocks.BLACK_WOOL));
            }
    }


    private FinalSeaLandmarks() {}
}
