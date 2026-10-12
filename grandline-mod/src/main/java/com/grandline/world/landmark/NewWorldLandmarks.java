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
import static com.grandline.world.landmark.LandmarksC.*;

/** New World set pieces, part one: Punk Hazard, Dressrosa, Green Bit, Zou and Whole Cake Island. */
final class NewWorldLandmarks {

    // ============================================================== Punk Hazard: the abandoned laboratory

    /** The government research facility split by catastrophe: a white two-storey lab block with sky-plaster upper floor,
     *  ribbon windows and a flat roof with a glass skylight dome, an entrance wing under a cantilevered canopy, a domed
     *  gas tank, industrial chimneys and pipes. The west half is scorched (soot spreading from a collapsed corner, fires,
     *  magma cracks), the east half frozen (frost creeping up the walls, icicles, ice spikes). Inside: lobby, burnt lab,
     *  the sealed gas laboratory, cryo lab; upstairs archive, director's office and staff bunks. */
    static void abandonedLab(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20; // 41 x 35
        site(b, Blocks.STONE, Blocks.STONE, 34);
        Mats m = Mats.of(BuildStyle.FUTURE, b.seed);
        m.door = S(Blocks.IRON_DOOR);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                int r = b.rand(x * 31 + z * 17, 8);
                int split = c + (int) Math.round(2 * Math.sin(z / 4.0));
                Block g = x < split ? (r < 3 ? Blocks.BLACKSTONE : r < 5 ? Blocks.BASALT : r < 6 ? Blocks.COARSE_DIRT : Blocks.GRAVEL)
                        : (r < 3 ? Blocks.SNOW_BLOCK : r < 5 ? Blocks.PACKED_ICE : r < 6 ? Blocks.ICE : Blocks.SNOW_BLOCK);
                b.set(x, 0, z, g);
                if (x < split - 3 && b.rand(x * 7 + z * 3, 23) == 0) b.set(x, 0, z, Blocks.MAGMA_BLOCK);
            }
        // ---- the lab block (x 6..34, z 10..28), two storeys, flat roof
        int x1 = 6, x2 = 34, z1 = 10, z2 = 28;
        storeys(b, m, x1, 0, z1, x2, z2, 2, 5, S(Blocks.SMOOTH_STONE));
        Arch.flat(b, m, x1, z1, x2, z2, 10, false, true);
        for (int fy : new int[]{0, 5}) {
            for (int x = x1 + 2; x <= x2 - 2; x += 2) {
                if (fy == 0 && x >= 16 && x <= 24) continue;
                Arch.window(b, m, x, fy + 2, z1, Direction.NORTH, 2, false);
                Arch.window(b, m, x, fy + 2, z2, Direction.SOUTH, 2, false);
            }
            for (int z = z1 + 2; z <= z2 - 2; z += 2) {
                Arch.window(b, m, x1, fy + 2, z, Direction.WEST, 2, false);
                Arch.window(b, m, x2, fy + 2, z, Direction.EAST, 2, false);
            }
        }
        // the entrance wing and its canopy
        storeys(b, m, 15, 0, 4, 25, 10, 1, 5, S(Blocks.POLISHED_ANDESITE));
        Arch.flat(b, m, 15, 4, 25, 10, 5, false, false);
        b.clear(18, 1, z1, 22, 3, z1);
        for (int x : new int[]{17, 23}) Arch.window(b, m, x, 2, 4, Direction.NORTH, 2, false);
        for (int z : new int[]{6, 8}) {
            Arch.window(b, m, 15, 2, z, Direction.WEST, 2, false);
            Arch.window(b, m, 25, 2, z, Direction.EAST, 2, false);
        }
        b.clear(c - 1, 1, 4, c + 1, 3, 4);
        b.fill(14, 5, 0, 26, 5, 3, slabS(S(Blocks.SMOOTH_QUARTZ_SLAB), SlabType.TOP));
        b.fill(14, 5, 0, 26, 5, 0, S(Blocks.CYAN_CONCRETE));
        for (int x : new int[]{15, 25}) b.fill(x, 1, 1, x, 4, 1, Blocks.LIGHT_GRAY_CONCRETE);
        b.set(c - 2, 1, 2, LandmarkKit.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, Half.BOTTOM, false));
        b.set(c + 3, 1, 3, Blocks.IRON_BARS);
        // ---- the gas tank (north-east) with its dome, the chimneys (south-west), pipes
        b.cylinder(36, 5, 1, 20, 4, Blocks.LIGHT_GRAY_CONCRETE, true);
        for (int y : new int[]{1, 7, 14, 20}) LandmarkKit.ring(b, 36, 5, y, 4, S(ArchBlocks.WHITE_PLASTER));
        Arch.dome(b, 36, 21, 5, 4, S(Blocks.LIGHT_GRAY_CONCRETE), S(Blocks.LIGHTNING_ROD), false);
        for (int y = 2; y <= 19; y++) b.set(31, y, 4, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        b.fill(31, 8, 6, 31, 8, 9, log(Blocks.STRIPPED_BIRCH_LOG, Direction.Axis.Z));
        b.fill(32, 8, 6, 32, 8, 6, Blocks.LIGHT_GRAY_CONCRETE);
        for (int[] ch : new int[][]{{9, 31}, {14, 31}}) {
            b.fill(ch[0] - 1, 1, ch[1] - 1, ch[0] + 1, 22, ch[1] + 1, Blocks.BRICKS);
            for (int y : new int[]{8, 15, 22}) b.fill(ch[0] - 1, y, ch[1] - 1, ch[0] + 1, y, ch[1] + 1, Blocks.RED_CONCRETE);
            b.set(ch[0], 22, ch[1], Blocks.HAY_BLOCK);
            b.set(ch[0], 23, ch[1], Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            b.fill(ch[0], 6, ch[1] - 2, ch[0], 6, z2 + 1, log(Blocks.STRIPPED_BIRCH_LOG, Direction.Axis.Z));
        }
        b.fill(9, 11, 27, 14, 11, 27, log(Blocks.STRIPPED_BIRCH_LOG, Direction.Axis.X));
        // the rooftop: skylight dome over the centre, vents, an antenna, a water tank
        LandmarkKit.ellipsoid(b, c, 10, 21, 4.2, 3.6, 4.2, S(Blocks.LIGHT_BLUE_STAINED_GLASS), true, 1);
        for (int[] p : new int[][]{{c - 4, 21}, {c + 4, 21}, {c, 17}, {c, 25}}) b.set(p[0], 11, p[1], Blocks.LIGHT_GRAY_CONCRETE);
        for (int x = 27; x <= 31; x += 2) b.set(x, 11, 13, LandmarkKit.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, Half.BOTTOM, false));
        b.fill(31, 11, 25, 31, 16, 25, Blocks.IRON_BARS);
        b.set(31, 17, 25, Blocks.LIGHTNING_ROD);
        b.fill(8, 11, 13, 9, 12, 14, Blocks.BARREL);
        // ---- the catastrophe: soot spreading from the burst north-west corner, frost over the east
        for (int x = x1; x <= x2; x++)
            for (int y = 1; y <= 11; y++)
                for (int z = z1; z <= z2; z++) {
                    boolean shell = x == x1 || x == x2 || z == z1 || z == z2;
                    if (!shell && y != 11) continue;
                    BlockState cur = null;
                    double dist = Math.sqrt((x - x1) * (x - x1) + (z - z1) * (z - z1) + (y - 4) * (y - 4));
                    int r = b.rand(x * 7 + y * 131 + z * 3, 100);
                    if (x < c && r < 70 - dist * 4) cur = r % 3 == 0 ? S(Blocks.COAL_BLOCK) : S(Blocks.BLACKSTONE);
                    if (x > c + 1 && shell && y <= 4 && r < (5 - y) * 14 + (x - c) * 2) cur = r % 4 == 0 ? S(Blocks.ICE) : S(Blocks.PACKED_ICE);
                    if (x > c + 1 && y == 11 && !shell && r < 50) cur = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + r % 3);
                    if (cur != null) b.set(x, !shell && x < c ? 10 : y, z, cur);
                }
        // the burst corner: a breach in the walls, fallen rubble, fires
        for (int x = x1; x <= x1 + 4; x++)
            for (int y = 3; y <= 11; y++) {
                if (y - 3 < 5 - (x - x1)) continue;
                b.set(x, y, z1, Blocks.AIR);
                b.set(x1, y, z1 + (x - x1), Blocks.AIR);
            }
        heap(b, 141, 4, 8, 1, 2, 2, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.COAL_BLOCK, Blocks.LIGHT_GRAY_CONCRETE);
        heap(b, 142, 2, 32, 1, 2, 1, Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK, Blocks.BASALT);
        for (int[] f : new int[][]{{9, 3}, {3, 20}, {17, 33}, {12, 6}}) b.set(f[0], 1, f[1], Blocks.CAMPFIRE);
        deadTree(b, 4, 1, 4, 5, 143, Blocks.CRIMSON_STEM);
        deadTree(b, 4, 1, 26, 4, 144, Blocks.CRIMSON_STEM);
        // ice spikes, drifts and icicles on the frozen side
        for (int[] s : new int[][]{{39, 13}, {38, 22}, {37, 31}, {29, 33}, {39, 28}}) {
            int h = 4 + b.rand(s[0] + s[1], 5);
            b.fill(s[0], 1, s[1], s[0], h, s[1], Blocks.PACKED_ICE);
            b.set(s[0] + 1, 1, s[1], Blocks.PACKED_ICE);
            b.set(s[0], 1, s[1] + 1, Blocks.BLUE_ICE);
            b.set(s[0] - 1, 1, s[1], Blocks.SNOW_BLOCK);
        }
        for (int x = c + 3; x <= x2; x += 3) {
            b.fill(x, 8, z1 - 1, x, 9, z1 - 1, Blocks.PACKED_ICE);
            b.fill(x, 8 + b.rand(x, 2), z2 + 1, x, 9, z2 + 1, Blocks.PACKED_ICE);
        }
        for (int z = z1 + 2; z <= z2; z += 3) b.fill(x2 + 1, 8 + b.rand(z, 2), z, x2 + 1, 9, z, Blocks.PACKED_ICE);
        scatter(b, 145, 16, c + 2, 0, w - 1, d - 1, 1, Blocks.SNOW.defaultBlockState());
        for (int y = 2; y <= 12; y++) if (b.rand(y * 3, 2) == 0) b.set(37 + b.rand(y, 3), y, 1 + b.rand(y * 5, 3), Blocks.PACKED_ICE);
        // ---- ground floor: lobby, corridor, burnt lab (west), sealed gas lab (centre), cryo lab (east)
        for (int x = 17; x <= 23; x++) b.set(x, 1, 8, decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        b.set(c, 1, 9, Blocks.CARTOGRAPHY_TABLE);
        b.set(16, 1, 5, decor(ArchBlocks.PLANK_BENCH, Direction.EAST));
        b.set(24, 1, 5, decor(ArchBlocks.PLANK_BENCH, Direction.WEST));
        b.hanging(c, 4, 6, Blocks.SEA_LANTERN);
        npc(b, "scholar", c + 2, 1, 6);
        b.fill(x1 + 1, 1, 13, x2 - 1, 4, 13, S(ArchBlocks.WHITE_PLASTER));
        b.fill(16, 1, 14, 16, 4, z2 - 1, S(ArchBlocks.WHITE_PLASTER));
        b.fill(24, 1, 14, 24, 4, z2 - 1, S(ArchBlocks.WHITE_PLASTER));
        b.clear(12, 1, 13, 12, 3, 13);
        b.clear(28, 1, 13, 28, 3, 13);
        flight(b, S(Blocks.STONE_BRICK_STAIRS), x1 + 5, 1, 11, Direction.WEST, 4, 1, S(ArchBlocks.WHITE_PLASTER));
        for (int x : new int[]{14, 26}) b.hanging(x, 4, 11, Blocks.SEA_LANTERN);
        // burnt lab
        for (int x = 8; x <= 14; x += 3) {
            b.set(x, 1, 15, Blocks.BREWING_STAND);
            b.set(x, 1, 18, Blocks.CAULDRON);
            b.fill(x, 1, 21, x, 3, 21, Blocks.LIME_STAINED_GLASS);
            b.set(x, 4, 21, Blocks.IRON_BLOCK);
        }
        b.set(11, 2, 21, Blocks.AIR);
        b.set(11, 3, 21, Blocks.AIR);
        b.fill(8, 1, 25, 14, 1, 25, slabS(S(Blocks.SMOOTH_STONE_SLAB), SlabType.TOP));
        b.set(9, 2, 25, Blocks.BLACKSTONE);
        b.chest(7, 1, 27, Direction.EAST, Buildings.LOOT_RUIN);
        b.hanging(11, 4, 17, Blocks.LANTERN);
        npc(b, "pirate", 10, 1, 23);
        // the sealed gas laboratory: a green glass front, iron door, gas tanks
        b.fill(17, 1, 13, 23, 4, 13, Blocks.GREEN_STAINED_GLASS);
        b.door(c, 1, 13, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c + 1, 2, 12, button(Blocks.STONE_BUTTON, Direction.NORTH));
        b.set(c - 1, 2, 14, button(Blocks.STONE_BUTTON, Direction.SOUTH));
        for (int x = 17; x <= 23; x += 2) {
            b.fill(x, 1, 27, x, 3, 27, Blocks.LIME_STAINED_GLASS);
            b.set(x, 4, 27, Blocks.OBSERVER);
        }
        b.set(17, 1, 20, Blocks.CAULDRON);
        b.set(23, 1, 20, facing(Blocks.PISTON, Direction.UP));
        b.set(c, 1, 22, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(c, 2, 22, Blocks.BREWING_STAND);
        b.hanging(c, 4, 18, Blocks.SEA_LANTERN);
        // cryo lab
        b.fill(25, 0, 14, 33, 0, z2 - 1, Blocks.PACKED_ICE);
        for (int x = 26; x <= 32; x += 3) {
            b.fill(x, 1, 17, x, 3, 17, Blocks.GLASS);
            b.set(x, 2, 17, Blocks.BLUE_ICE);
            b.fill(x, 1, 25, x, 3, 25, Blocks.GLASS);
            b.set(x, 2, 25, Blocks.PACKED_ICE);
        }
        b.barrel(33, 1, 14, Buildings.LOOT_WAREHOUSE);
        b.set(33, 1, 21, decor(ArchBlocks.CARGO_CRATE, Direction.NORTH));
        b.hanging(29, 4, 21, Blocks.LANTERN);
        npc(b, "pirate", 29, 1, 21);
        // ---- upper floor: archive (west), director's office (centre), staff bunks (east)
        b.fill(x1 + 1, 6, 13, x2 - 1, 9, 13, S(ArchBlocks.SKY_PLASTER));
        b.fill(16, 6, 14, 16, 9, z2 - 1, S(ArchBlocks.SKY_PLASTER));
        b.fill(24, 6, 14, 24, 9, z2 - 1, S(ArchBlocks.SKY_PLASTER));
        b.clear(12, 6, 13, 12, 8, 13);
        b.clear(28, 6, 13, 28, 8, 13);
        b.door(c, 6, 13, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c + 1, 7, 12, button(Blocks.STONE_BUTTON, Direction.NORTH));
        b.set(c - 1, 7, 14, button(Blocks.STONE_BUTTON, Direction.SOUTH));
        for (int z = 15; z <= 26; z += 3) {
            b.fill(8, 6, z, 13, 7, z, Blocks.BOOKSHELF);
            b.set(14, 6, z, facing(Blocks.LECTERN, Direction.EAST));
        }
        b.hanging(11, 9, 17, Blocks.LANTERN);
        npc(b, "scholar", 9, 6, 17);
        b.set(19, 6, 24, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(20, 6, 24, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(21, 6, 24, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(c, 6, 25, stairS(S(Blocks.QUARTZ_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.fill(23, 6, 27, 23, 8, 27, Blocks.PURPLE_STAINED_GLASS);
        b.set(17, 6, 26, Blocks.BREWING_STAND);
        b.chest(17, 6, 27, Direction.EAST, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 17, 6, 27);
        b.hanging(c, 9, 20, Blocks.SEA_LANTERN);
        npc(b, "pirate_captain", 21, 6, 20);
        for (int z = 16; z <= 25; z += 3) {
            b.bed(26, 6, z, Blocks.WHITE_BED, Direction.EAST);
            b.bed(31, 6, z, Blocks.WHITE_BED, Direction.EAST);
        }
        b.barrel(33, 6, 27, Buildings.LOOT_HOUSE);
        b.hanging(30, 9, 21, Blocks.LANTERN);
        npc(b, "pirate", 29, 6, 15);
        b.marker("arc_anchor", c, 1, 2, tag("arc", "fire_and_ice"));
    }

    // ============================================================== Dressrosa: the Corrida Colosseum

    /** Seat-row height of the Corrida stands at radius r (rows rise one block per ring from the moat barrier). */
    private static int corridaSeat(double r) {
        return 2 + (int) (r - 13.5);
    }

    /** The round colosseum: a salmon stucco ring wall of open arches, blind arches and windows under a cornice with
     *  flags, a gatehouse on the north, stands rising one row per ring over a vaulted concourse with four entrance
     *  tunnels, the royal box, the stone fighting ring inside its water moat, and the gladiators' underground (hall,
     *  cells, champion's corner, prize vault) with a ramp up into the ring. */
    static void corridaColosseum(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 25; // 51 x 51
        site(b, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, 30);
        BlockState salmon = S(ArchBlocks.SALMON_PLASTER), cream = S(ArchBlocks.CREAM_PLASTER), sand = S(ArchBlocks.DRESSED_SANDSTONE);
        BlockState sandSt = S(ArchBlocks.DRESSED_SANDSTONE_STAIRS), salmonSt = S(ArchBlocks.SALMON_PLASTER_STAIRS), tileSt = S(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS);
        BlockState hs = S(ArchBlocks.HARBOUR_STONE), fill = S(Blocks.SANDSTONE);
        // the gladiators' underground (built first: the ring and moat are its roof)
        shell(b, c - 11, -7, c - 11, c + 11, -1, c + 11, Blocks.STONE_BRICKS);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                double dx = x - c, dz = z - c, r = Math.sqrt(dx * dx + dz * dz);
                Direction out = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                double ang = Math.atan2(dz, dx), bay = Math.PI / 12;
                double s = r * (ang - Math.round(ang / bay) * bay); // arc offset from the nearest bay centre
                int bayIdx = (int) Math.floorMod(Math.round(ang / bay), 24);
                if (r > 25.5) {
                    if (((x + z) & 3) == 0 || ((x - z) & 3) == 0) b.set(x, 0, z, Blocks.CUT_SANDSTONE);
                    continue;
                }
                if (r > 24.5) {
                    b.set(x, 0, z, hs);
                    b.set(x, 19, z, stairS(sandSt, out.getOpposite(), Half.TOP));
                    continue;
                }
                if (r > 22.5) {
                    boolean outer = r > 23.5;
                    for (int y = 1; y <= 19; y++) {
                        BlockState bl = salmon;
                        double as = Math.abs(s);
                        if (y <= 2) bl = hs;
                        else if (y == 7 || y == 13 || y == 19) bl = sand;
                        else if (y <= 6) bl = as <= 2.1 ? sand : salmon;
                        else if (outer && as > 2.6) bl = sand;
                        if (y <= 6 && ((as <= 1.3 && y <= 5) || (as <= 0.7 && y == 6))) bl = null;
                        if (y >= 8 && y <= 12 && ((as <= 1.3 && y <= 11) || (as <= 0.7 && y == 12))) bl = outer ? null : cream;
                        if (y >= 15 && y <= 16 && as <= 0.6) bl = null;
                        if (bl == null) b.set(x, y, z, Blocks.AIR);
                        else b.set(x, y, z, bl);
                    }
                    b.set(x, 0, z, hs);
                    if (outer) b.set(x, 20, z, sand);
                    continue;
                }
                if (r > 13.5) {
                    int h = corridaSeat(r);
                    b.fill(x, 1, z, x, h - 1, z, fill);
                    boolean aisle = Math.abs(r * (ang - Math.round(ang / (Math.PI / 6)) * (Math.PI / 6))) < 0.7;
                    int row = (int) (r - 13.5);
                    b.set(x, h, z, aisle ? S(Blocks.CUT_SANDSTONE) : stairS((row & 1) == 0 ? sandSt : salmonSt, out, Half.BOTTOM));
                    if (r > 19.5) {
                        b.clear(x, 1, z, x, 4, z);
                        b.set(x, 0, z, hs);
                        b.set(x, 5, z, sand);
                    }
                    continue;
                }
                if (r > 12.5) {
                    b.fill(x, 1, z, x, 2, z, sand);
                    b.set(x, 3, z, Blocks.RED_TERRACOTTA);
                    b.set(x, 0, z, Blocks.STONE_BRICKS);
                    continue;
                }
                if (r > 10.5) {
                    b.set(x, -1, z, Blocks.STONE_BRICKS);
                    b.set(x, 0, z, Blocks.WATER);
                    continue;
                }
                b.set(x, 0, z, Blocks.STONE_BRICKS);
                b.set(x, 1, z, r > 9.6 ? S(Blocks.SMOOTH_STONE) : b.rand(x * 13 + z, 5) == 0 ? S(Blocks.COARSE_DIRT) : S(Blocks.SAND));
            }
        // flags on the parapet, every other bay
        for (int i = 0; i < 24; i += 2) {
            double a = i * Math.PI / 12 + Math.PI / 24;
            int fx = c + (int) Math.round(Math.cos(a) * 24.0), fz = c + (int) Math.round(Math.sin(a) * 24.0);
            double dx = c - fx, dz = c - fz;
            Direction in = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            flag(b, fx, 21, fz, 4, in, 2, (i & 2) == 0 ? S(Blocks.RED_WOOL) : S(Blocks.PINK_WOOL), S(Blocks.YELLOW_WOOL));
        }
        // four tunnels from the concourse down to the front walkway
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            Direction side = dir.getClockWise();
            for (int k = 14; k <= 23; k++)
                for (int sd = -1; sd <= 1; sd++) {
                    int tx = c + dir.getStepX() * k + side.getStepX() * sd, tz = c + dir.getStepZ() * k + side.getStepZ() * sd;
                    int fl = k <= 15 ? 2 : k == 16 ? 1 : 0;
                    if (fl > 0) b.fill(tx, 1, tz, tx, fl, tz, sand);
                    b.clear(tx, fl + 1, tz, tx, fl + 3, tz);
                    if (k == 16) b.set(tx, 1, tz, stairS(sandSt, dir.getOpposite(), Half.BOTTOM));
                }
        }
        // the gatehouse on the north
        int gx1 = c - 6, gx2 = c + 6;
        b.fill(gx1, 1, 1, gx2, 22, 6, salmon);
        b.fill(gx1, 1, 1, gx2, 2, 6, hs);
        for (int y : new int[]{7, 13, 19}) b.fill(gx1, y, 1, gx2, y, 6, sand);
        for (int x : new int[]{gx1, gx2}) b.fill(x, 3, 1, x, 22, 1, sand);
        archX(b, c, 2, 1, 1, 6, 6);
        for (int x = c - 3; x <= c + 3; x++) b.set(x, 9, 1, sand);
        b.fill(c - 3, 1, 1, c - 3, 8, 1, sand);
        b.fill(c + 3, 1, 1, c + 3, 8, 1, sand);
        for (int x : new int[]{c - 4, c + 4}) for (int y : new int[]{15, 16}) b.set(x, y, 1, Arch.along(S(ArchBlocks.MULLION_WINDOW), Direction.Axis.X));
        b.fill(c - 1, 15, 1, c + 1, 17, 1, Arch.along(S(ArchBlocks.MULLION_WINDOW), Direction.Axis.X));
        b.fill(c - 2, 14, 0, c + 2, 14, 0, slabS(S(ArchBlocks.DRESSED_SANDSTONE_SLAB), SlabType.TOP));
        line(b, c - 2, 15, 0, c + 2, 0, Blocks.IRON_BARS);
        for (int x = c - 2; x <= c + 2; x++) b.set(x, 11, 0, banner(x == c ? Blocks.YELLOW_WALL_BANNER : Blocks.RED_WALL_BANNER, Direction.NORTH));
        Mats gm = Mats.of(BuildStyle.VENETIAN, b.seed);
        roofTiles(gm, ArchBlocks.TERRACOTTA_ROOF_TILES, ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS, ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        int gt = Arch.hip(b, gm, gx1, 1, gx2, 6, 23, 1);
        flag(b, c, gt + 1, 3, 5, Direction.EAST, 3, S(Blocks.RED_WOOL), S(Blocks.YELLOW_WOOL));
        b.hanging(c, 8, 3, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "guard", c - 4, 1, 0);
        npc(b, "guard", c + 4, 1, 0);
        // the royal box on the north stands
        for (int x = c - 3; x <= c + 3; x++)
            for (int z = c - 22; z <= c - 17; z++) {
                double r = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
                if (r <= 13.5 || r > 22.5) continue;
                b.fill(x, 5, z, x, 9, z, sand);
                b.clear(x, 10, z, x, 13, z);
            }
        for (int x = c - 2; x <= c + 2; x++) b.set(x, 10, c - 19, stairS(S(Blocks.QUARTZ_STAIRS), Direction.NORTH, Half.BOTTOM));
        b.set(c, 10, c - 20, Blocks.GOLD_BLOCK);
        b.set(c, 11, c - 20, stairS(S(Blocks.QUARTZ_STAIRS), Direction.NORTH, Half.BOTTOM));
        for (int x : new int[]{c - 3, c + 3}) for (int z : new int[]{c - 17, c - 21}) b.fill(x, 10, z, x, 13, z, log(ArchBlocks.MARBLE_COLUMN, Direction.Axis.Y));
        b.fill(c - 3, 14, c - 22, c + 3, 14, c - 17, slabS(S(ArchBlocks.SALMON_PLASTER_SLAB), SlabType.BOTTOM));
        for (int x = c - 3; x <= c + 3; x++) b.set(x, 13, c - 16, decor(ArchBlocks.RED_AWNING, Direction.SOUTH));
        for (int x = c - 2; x <= c + 2; x++) b.set(x, 10, c - 17, decor(ArchBlocks.FLOWER_PLANTER, Direction.SOUTH));
        // spectators, gladiators and the arena marker
        for (double a : new double[]{Math.toRadians(40), Math.toRadians(130), Math.toRadians(220)}) {
            int px = c + (int) Math.round(Math.cos(a) * 17), pz = c + (int) Math.round(Math.sin(a) * 17);
            npc(b, "patron", px, corridaSeat(Math.sqrt((px - c) * (px - c) + (pz - c) * (pz - c))) + 1, pz);
        }
        npc(b, "lunarian_warrior", c - 5, 2, c - 4);
        npc(b, "buccaneer_brawler", c + 5, 2, c - 4);
        b.marker("arc_anchor", c, 2, c, tag("arc", "colosseum_tournament"));
        // the plaza: lamps, planters and the ticket booth in the north-west corner
        for (int[] p : new int[][]{{4, 10}, {46, 10}, {4, 40}, {46, 40}, {10, 46}, {40, 46}}) lampPost(b, p[0], 1, p[1], Blocks.IRON_BARS, 3, ArchBlocks.BRASS_LANTERN.get());
        for (int[] p : new int[][]{{1, 1}, {7, 1}, {1, 5}, {7, 5}}) b.fill(p[0], 1, p[1], p[0], 3, p[1], Blocks.SPRUCE_FENCE);
        for (int x = 1; x <= 7; x++) {
            b.set(x, 4, 1, decor(ArchBlocks.RED_AWNING, Direction.NORTH));
            b.set(x, 4, 5, decor(ArchBlocks.RED_AWNING, Direction.SOUTH));
        }
        b.fill(1, 4, 2, 7, 4, 4, S(Blocks.RED_WOOL));
        for (int x = 2; x <= 6; x++) b.set(x, 1, 2, decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        b.barrel(2, 1, 4, Buildings.LOOT_SHOP);
        b.set(6, 1, 4, decor(ArchBlocks.GOODS_SHELF, Direction.WEST));
        npc(b, "merchant", 4, 1, 4);
        for (int[] p : new int[][]{{12, 2}, {38, 2}, {2, 12}, {48, 12}}) b.set(p[0], 1, p[1], decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        // ---- underground: gladiators' hall, the ramp into the ring, cells (west), champion's corner, prize vault (north-east)
        b.fill(c - 10, -7, c - 10, c + 10, -7, c + 10, Blocks.POLISHED_ANDESITE);
        flight(b, S(Blocks.STONE_BRICK_STAIRS), c + 1, -6, c + 2, Direction.SOUTH, 8, 3, S(Blocks.STONE_BRICKS));
        line(b, c - 2, 2, c + 3, c - 2, c + 9, Blocks.IRON_BARS);
        line(b, c + 2, 2, c + 3, c + 2, c + 9, Blocks.IRON_BARS);
        for (int x = c - 4; x <= c + 4; x++) chair(b, x, -6, c - 9, Blocks.SPRUCE_STAIRS, Direction.NORTH);
        for (int z = c - 4; z <= c + 2; z += 2) {
            b.set(c + 4, -6, z, Blocks.GRINDSTONE);
            b.fill(c + 4, -5, z, c + 4, -4, z, Blocks.IRON_BARS);
        }
        b.barrel(c - 3, -6, c + 1, Buildings.LOOT_SMITH);
        b.set(c - 2, -6, c + 1, Blocks.ANVIL);
        b.set(c - 4, -6, c + 1, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        for (int[] l : new int[][]{{c, c - 5}, {c, c}, {c - 4, c + 6}, {c + 8, c + 4}}) b.hanging(l[0], -2, l[1], Blocks.LANTERN);
        npc(b, "pirate", c - 1, -6, c - 6);
        npc(b, "pirate", c + 2, -6, c - 1);
        for (int z = c - 9; z <= c + 3; z += 3) {
            b.fill(c - 10, -6, z, c - 7, -3, z, Blocks.STONE_BRICKS);
            if (z < c + 3) mark(b, "cell", c - 9, -6, z + 1);
        }
        for (int z = c - 9; z <= c + 3; z++) for (int y = -6; y <= -4; y++) b.set(c - 6, y, z, connected(Blocks.IRON_BARS, Direction.Axis.Z));
        b.fill(c - 10, -3, c - 9, c - 6, -3, c + 3, Blocks.STONE_BRICKS);
        b.bed(c - 9, -6, c + 6, Blocks.RED_BED, Direction.SOUTH);
        b.chest(c - 7, -6, c + 9, Direction.NORTH, Buildings.LOOT_HOUSE);
        b.set(c - 10, -6, c + 9, decor(ArchBlocks.GOODS_SHELF, Direction.EAST));
        npc(b, "master", c - 6, -6, c + 6);
        b.fill(c + 5, -6, c - 10, c + 5, -3, c - 4, Blocks.STONE_BRICKS);
        b.fill(c + 5, -6, c - 3, c + 10, -3, c - 3, Blocks.STONE_BRICKS);
        b.fill(c + 6, -3, c - 10, c + 10, -3, c - 4, Blocks.STONE_BRICKS);
        b.door(c + 7, -6, c - 3, Blocks.IRON_DOOR, Direction.SOUTH);
        b.set(c + 8, -5, c - 2, lever(Direction.SOUTH));
        b.set(c + 5, -5, c - 2, lever(Direction.SOUTH));
        b.set(c + 10, -4, c - 2, lever(Direction.SOUTH));
        b.fill(c + 7, -6, c - 9, c + 9, -6, c - 9, Blocks.GOLD_BLOCK);
        b.set(c + 8, -5, c - 9, Blocks.GLASS);
        b.chest(c + 6, -6, c - 7, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(c + 10, -6, c - 7, Direction.WEST, Buildings.LOOT_TREASURE);
        b.hanging(c + 8, -4, c - 6, Blocks.LANTERN);
        mark(b, "treasure", c + 8, -6, c - 7);
        npc(b, "guard", c + 7, -6, c);
    }

    // ============================================================== Dressrosa: the royal plateau

    /** The royal palace on its plateau: a flower plaza with a fountain and a toy stall at the foot of a layered rock
     *  plateau, a lift tower and a balustraded cliff stair up to a forecourt of flowers; the palace of white stucco with
     *  salmon quoins and bands, terracotta hip roofs, four round corner towers with tall cone roofs and a three-storey
     *  entrance pavilion with the king's balcony. Inside: throne room, guard hall, dining hall and kitchen, the royal
     *  suite and library; and hollowed out of the rock below, the secret toy factory (a hatch behind the throne, a cracked
     *  door in the cliff). */
    static void royalPlateau(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 22; // 45 x 45
        final int T = 14; // plateau top
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 52);
        Mats m = Mats.of(BuildStyle.PALACE, b.seed);
        m.wall = S(ArchBlocks.WHITE_PLASTER);
        m.upperWall = m.wall;
        m.frame = S(ArchBlocks.SALMON_PLASTER);
        m.trim = S(ArchBlocks.SALMON_PLASTER);
        m.base = S(ArchBlocks.HARBOUR_STONE);
        m.floor = S(Blocks.SPRUCE_PLANKS);
        roofTiles(m, ArchBlocks.TERRACOTTA_ROOF_TILES, ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS, ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.HIP;
        m.shutter = ArchBlocks.GREEN_SHUTTER.get();
        m.planters = true;
        m.eaveBrackets = false;
        m.door = S(Blocks.SPRUCE_DOOR);
        BlockState tiles = S(ArchBlocks.TERRACOTTA_ROOF_TILES), tileSt = S(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS);
        // ---- the plateau (x 3..41, z 9..43): layered rock, grass on top
        for (int x = 3; x <= 41; x++)
            for (int z = 9; z <= 43; z++)
                for (int y = 1; y <= T; y++) {
                    int r = b.rand(x * 7 + z * 13 + y * 131, 7);
                    boolean face = x == 3 || x == 41 || z == 9 || z == 43;
                    BlockState s = y == T ? S(Blocks.GRASS_BLOCK) : y % 4 == 0 ? S(Blocks.GRANITE) : r == 0 ? S(Blocks.ANDESITE) : face && r == 1 ? S(Blocks.MOSSY_COBBLESTONE) : S(Blocks.STONE);
                    b.set(x, y, z, s);
                }
        for (int x = 3; x <= 41; x += 1)
            if (b.rand(x * 11, 3) == 0) b.set(x, T, 9, S(Blocks.MOSS_BLOCK));
        // greenery draping down the cliff faces
        BlockState drape = leaves(Blocks.AZALEA_LEAVES);
        for (int x = 3; x <= 41; x++) {
            if (x >= 4 && x <= 24) continue;
            int len = b.rand(x * 13 + 1, 5);
            for (int k = 1; k <= len; k++) b.set(x, T - k + 1, 8, drape);
            int len2 = b.rand(x * 17 + 3, 5);
            for (int k = 1; k <= len2; k++) b.set(x, T - k + 1, 44, drape);
        }
        for (int z = 9; z <= 43; z++)
            for (int x : new int[]{2, 42}) {
                int len = b.rand(z * 19 + x, 5);
                for (int k = 1; k <= len; k++) b.set(x, T - k + 1, z, drape);
            }
        // ---- the flower plaza below: beds, a fountain, a toy stall
        for (int x = 1; x <= w - 2; x++)
            for (int z = 1; z <= 6; z++) {
                if (x >= 19 && x <= 25) continue;
                if ((x + z) % 3 == 0) b.set(x, 1, z, (x / 4) % 3 == 0 ? Blocks.RED_TULIP : (x / 4) % 3 == 1 ? Blocks.PINK_TULIP : Blocks.POPPY);
                else if ((x + z) % 3 == 1) b.set(x, 0, z, Blocks.COARSE_DIRT);
            }
        b.fill(30, 0, 1, 42, 0, 7, S(Blocks.SMOOTH_SANDSTONE));
        b.clear(30, 1, 1, 42, 1, 7);
        LandmarkKit.ring(b, 36, 4, 1, 2.2, S(ArchBlocks.DRESSED_SANDSTONE));
        LandmarkKit.disc(b, 36, 4, 0, 1.7, S(Blocks.WATER));
        b.fill(36, 1, 4, 36, 2, 4, Blocks.CHISELED_SANDSTONE);
        b.set(36, 3, 4, Blocks.SANDSTONE_WALL);
        b.set(36, 4, 4, stand(S(ArchBlocks.BRASS_LANTERN)));
        for (int[] p : new int[][]{{40, 1}, {40, 7}, {31, 1}, {31, 7}}) b.set(p[0], 1, p[1], decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        npc(b, "merchant", 33, 1, 3);
        npc(b, "child", 39, 1, 6);
        for (int[] p : new int[][]{{27, 1}, {29, 1}, {27, 3}, {29, 3}}) b.fill(p[0], 1, p[1], p[0], 3, p[1], Blocks.OAK_FENCE);
        for (int x = 27; x <= 29; x++) {
            b.set(x, 4, 1, decor(ArchBlocks.YELLOW_AWNING, Direction.NORTH));
            b.fill(x, 4, 2, x, 4, 3, S(Blocks.YELLOW_WOOL));
            b.set(x, 1, 2, decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        }
        b.set(28, 2, 2, Blocks.CARVED_PUMPKIN);
        // ---- the lift tower (iron frame, a ladder up the cliff) and the cliff stair with its balustrade
        for (int[] p : new int[][]{{20, 5}, {24, 5}, {20, 8}, {24, 8}}) b.fill(p[0], 1, p[1], p[0], T + 3, p[1], Blocks.IRON_BARS);
        b.fill(20, T + 4, 5, 24, T + 4, 8, Blocks.IRON_BLOCK);
        pyramid(b, 20, 5, 24, 8, T + 5, tileSt, tiles, S(Blocks.LIGHTNING_ROD));
        b.fill(21, 1, 6, 23, 1, 7, slabS(S(Blocks.SMOOTH_STONE_SLAB), SlabType.BOTTOM));
        b.fill(21, 6, 6, 23, 6, 7, slabS(S(Blocks.SMOOTH_STONE_SLAB), SlabType.TOP));
        b.fill(22, 7, 6, 22, T + 3, 6, Blocks.CHAIN);
        b.ladderUp(22, 1, 8, T, Direction.NORTH);
        b.hanging(22, T + 3, 7, Blocks.LANTERN);
        flight(b, S(Blocks.STONE_BRICK_STAIRS), 4, 1, 7, Direction.EAST, T, 2, S(Blocks.STONE_BRICKS));
        b.fill(18, 1, 7, 19, T, 8, Blocks.STONE_BRICKS);
        for (int i = 0; i < T; i++) {
            b.fill(4 + i, 1, 6, 4 + i, i + 1, 6, Blocks.STONE_BRICKS);
            b.set(4 + i, i + 2, 6, Blocks.STONE_BRICK_WALL);
        }
        b.fill(18, T + 1, 6, 19, T + 1, 6, Blocks.STONE_BRICK_WALL);
        // ---- the forecourt on top: flower beds either side of the path, lamps
        b.fill(c - 2, T, 9, c + 2, T, 16, S(Blocks.SMOOTH_SANDSTONE));
        for (int x = 5; x <= 39; x++)
            for (int z = 10; z <= 15; z++) {
                if (Math.abs(x - c) <= 3) continue;
                if ((x * 3 + z) % 4 == 0) b.set(x, T + 1, z, (x & 1) == 0 ? Blocks.RED_TULIP : Blocks.ORANGE_TULIP);
                else if ((x * 3 + z) % 4 == 2) b.set(x, T + 1, z, Blocks.PINK_TULIP);
            }
        for (int z : new int[]{10, 14}) for (int x : new int[]{c - 3, c + 3}) lampPost(b, x, T + 1, z, Blocks.IRON_BARS, 3, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "resident", 12, T + 1, 13);
        // ---- the palace: main block (x 10..34, z 20..38) two storeys of five
        int x1 = 10, x2 = 34, z1 = 20, z2 = 38;
        storeys(b, m, x1, T, z1, x2, z2, 2, 5, S(Blocks.POLISHED_GRANITE));
        Arch.hip(b, m, x1, z1, x2, z2, T + 11, 1);
        // the entrance pavilion (x 17..27, z 16..21), three storeys, gable to the front
        storeys(b, m, 17, T, 16, 27, 21, 3, 5, S(Blocks.POLISHED_GRANITE));
        Mats pm = Mats.of(BuildStyle.PALACE, b.seed + 1);
        pm.wall = m.wall;
        pm.upperWall = m.wall;
        pm.frame = m.frame;
        pm.trim = m.trim;
        roofTiles(pm, ArchBlocks.TERRACOTTA_ROOF_TILES, ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS, ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        pm.roofKind = Arch.RoofKind.GABLE;
        pm.eaveBrackets = false;
        int pt = Arch.gable(b, pm, 17, 16, 27, 21, T + 16, false, 1, m.wall);
        flag(b, c, pt + 1, 16, 4, Direction.EAST, 3, S(Blocks.PINK_WOOL), S(Blocks.WHITE_WOOL));
        // four round corner towers with tall cone roofs
        for (int[] t : new int[][]{{x1 - 1, z1 - 1}, {x2 + 1, z1 - 1}, {x1 - 1, z2 + 1}, {x2 + 1, z2 + 1}}) {
            b.cylinder(t[0], t[1], T + 1, T + 16, 3, Blocks.WHITE_CONCRETE, false);
            LandmarkKit.disc(b, t[0], t[1], T + 1, 3.0, S(ArchBlocks.HARBOUR_STONE));
            for (int y : new int[]{T + 5, T + 10, T + 16}) LandmarkKit.ring(b, t[0], t[1], y, 3, S(ArchBlocks.SALMON_PLASTER));
            LandmarkKit.ring(b, t[0], t[1], T + 2, 3, S(ArchBlocks.WHITE_PLASTER));
            int ox = t[0] < c ? -1 : 1, oz = t[1] < 30 ? -1 : 1;
            for (int y : new int[]{T + 7, T + 12}) {
                b.fill(t[0] + 3 * ox, y, t[1], t[0] + 3 * ox, y + 1, t[1], Arch.along(S(ArchBlocks.MULLION_WINDOW), Direction.Axis.Z));
                b.fill(t[0], y, t[1] + 3 * oz, t[0], y + 1, t[1] + 3 * oz, Arch.along(S(ArchBlocks.MULLION_WINDOW), Direction.Axis.X));
            }
            eaveRing(b, t[0], t[1], T + 17, 3.6, tileSt);
            spire(b, t[0], t[1], T + 17, 3.0, 3, tiles, S(Blocks.GOLD_BLOCK));
        }
        // windows, the doors, the king's balcony
        for (int fy : new int[]{T, T + 5}) {
            int y = fy + 2;
            Arch.window(b, m, 14, y, z1, Direction.NORTH, 2, true);
            Arch.window(b, m, 30, y, z1, Direction.NORTH, 2, true);
            winX(b, m, 14, 31, y, z2, Direction.SOUTH, 3, 2, true, -9, 0);
            winZ(b, m, 24, 34, y, x1, Direction.WEST, 3, 2, true, -9, 0);
            winZ(b, m, 24, 34, y, x2, Direction.EAST, 3, 2, true, -9, 0);
        }
        for (int fy : new int[]{T + 5, T + 10}) for (int x : new int[]{19, 25}) Arch.window(b, m, x, fy + 2, 16, Direction.NORTH, 2, true);
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, T + 1, 16, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.fill(c - 2, T + 4, 16, c + 2, T + 4, 16, S(ArchBlocks.DRESSED_SANDSTONE));
        for (int x : new int[]{c - 2, c + 2}) Arch.wallLantern(b, m, x, T + 2, 16, Direction.NORTH);
        b.fill(19, T + 10, 14, 25, T + 10, 15, S(ArchBlocks.DRESSED_SANDSTONE));
        for (int x = 19; x <= 25; x++) b.set(x, T + 9, 14, stairS(S(ArchBlocks.DRESSED_SANDSTONE_STAIRS), Direction.SOUTH, Half.TOP));
        line(b, 19, T + 11, 14, 25, 14, Blocks.IRON_BARS);
        for (int x : new int[]{19, 25}) b.set(x, T + 11, 15, connect(Blocks.IRON_BARS, true, false, false, false));
        b.door(c, T + 11, 16, Blocks.SPRUCE_DOOR, Direction.NORTH);
        for (int x = c - 1; x <= c + 1; x += 2) b.set(x, T + 13, 15, banner(Blocks.PINK_WALL_BANNER, Direction.NORTH));
        npc(b, "guard", c - 3, T + 1, 15);
        npc(b, "guard", c + 3, T + 1, 15);
        // the pavilion opens into the main block on both floors
        b.clear(18, T + 1, 21, 26, T + 4, 21);
        b.clear(18, T + 6, 21, 26, T + 9, 21);
        // ---- ground floor: throne room (centre), guard hall (west), dining hall and kitchen (east)
        for (int x : new int[]{17, 27}) {
            b.fill(x, T + 1, z1 + 1, x, T + 4, z2 - 1, m.wall);
            b.clear(x, T + 1, 29, x, T + 3, 30);
        }
        carpet(b, c - 1, T + 1, 17, c + 1, 33, Blocks.PINK_CARPET);
        b.fill(19, T + 1, 34, 25, T + 1, 37, Blocks.GOLD_BLOCK);
        b.set(c, T + 2, 36, stairS(S(Blocks.QUARTZ_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.fill(c, T + 2, 37, c, T + 4, 37, Blocks.PINK_WOOL);
        for (int z = 23; z <= 31; z += 4) {
            b.set(19, T + 1, z, Blocks.POTTED_RED_TULIP);
            b.set(25, T + 1, z, Blocks.POTTED_RED_TULIP);
        }
        b.hanging(c, T + 4, 28, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "noble", c - 1, T + 2, 35);
        b.bed(12, T + 1, 31, Blocks.RED_BED, Direction.SOUTH);
        b.bed(14, T + 1, 31, Blocks.RED_BED, Direction.SOUTH);
        b.chest(16, T + 1, 37, Direction.WEST, Buildings.LOOT_MARINE);
        b.set(11, T + 1, 26, Blocks.GRINDSTONE);
        b.set(11, T + 1, 27, Blocks.SMITHING_TABLE);
        b.hanging(14, T + 4, 30, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "guard", 14, T + 1, 27);
        longTableZ(b, 27, 34, T + 1, 30, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        kitchenX(b, 28, T + 1, 37, Direction.NORTH, Buildings.LOOT_TAVERN);
        b.hanging(30, T + 4, 29, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "cook", 32, T + 1, 35);
        flight(b, S(Blocks.SPRUCE_STAIRS), 29, T + 1, 22, Direction.SOUTH, 4, 2, m.floor);
        line(b, 30, T + 6, 22, 30, 25, Blocks.SPRUCE_FENCE);
        // first floor: the royal suite (centre) and the library (west); stairs up to the balcony room
        b.bed(c - 1, T + 6, 35, Blocks.PINK_BED, Direction.SOUTH);
        b.bed(c + 1, T + 6, 35, Blocks.PINK_BED, Direction.SOUTH);
        carpet(b, 19, T + 6, 28, 25, 34, Blocks.ORANGE_CARPET);
        b.chest(19, T + 6, 37, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(25, T + 6, 37, decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        b.fill(x1 + 1, T + 6, z1 + 4, x1 + 1, T + 8, z2 - 1, Blocks.BOOKSHELF);
        b.fill(13, T + 6, 33, 16, T + 7, 33, Blocks.BOOKSHELF);
        b.set(13, T + 6, 28, Blocks.ENCHANTING_TABLE);
        b.hanging(c, T + 9, 32, ArchBlocks.BRASS_LANTERN.get());
        b.hanging(13, T + 9, 30, ArchBlocks.BRASS_LANTERN.get());
        flight(b, S(Blocks.SPRUCE_STAIRS), 18, T + 6, 17, Direction.EAST, 4, 2, m.floor);
        b.set(24, T + 11, 18, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(25, T + 11, 18, stairS(S(Blocks.SPRUCE_STAIRS), Direction.EAST, Half.BOTTOM));
        b.hanging(c, T + 14, 19, ArchBlocks.BRASS_LANTERN.get());
        // ---- the secret factory inside the plateau: a hatch behind the throne, and a cracked door in the cliff
        b.clear(8, 2, 14, 36, 8, 36);
        b.fill(8, 1, 14, 36, 1, 36, Blocks.SMOOTH_STONE);
        hatch(b, 26, T, 36, 2, Direction.NORTH, Blocks.SPRUCE_TRAPDOOR, Blocks.STONE);
        b.clear(30, 2, 10, 30, 3, 13);
        b.set(30, 2, 9, Blocks.CRACKED_STONE_BRICKS);
        b.set(30, 3, 9, Blocks.CRACKED_STONE_BRICKS);
        for (int z = 16; z <= 34; z += 6) {
            b.fill(10, 2, z, 30, 2, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST));
            for (int x = 11; x <= 29; x += 3) b.set(x, 2, z + 1, (x & 1) == 0 ? facing(Blocks.BLAST_FURNACE, Direction.NORTH) : facing(Blocks.SMOKER, Direction.NORTH));
            b.set(32, 2, z, Blocks.HOPPER);
            b.barrel(33, 2, z, Buildings.LOOT_WAREHOUSE);
            b.set(34, 2, z, Blocks.CAULDRON);
            b.set(35, 2, z, decor(ArchBlocks.CARGO_CRATE, Direction.NORTH));
        }
        for (int x = 12; x <= 32; x += 10) for (int z = 18; z <= 33; z += 7) b.hanging(x, 8, z, Blocks.LANTERN);
        b.chest(9, 2, 35, Direction.EAST, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 9, 2, 35);
        npc(b, "pirate", 15, 2, 20);
        npc(b, "pirate", 28, 2, 32);
        npc(b, "pirate_captain", 24, 2, 26);
        b.marker("arc_anchor", c, T + 1, 12, tag("arc", "toy_kingdom"));
    }

    // ============================================================== Green Bit: the dwarves' village

    private static BlockState tinyWall(int i) {
        return switch (i) {
            case 0 -> S(ArchBlocks.CREAM_PLASTER);
            case 1 -> S(ArchBlocks.OCHRE_PLASTER);
            case 2 -> S(ArchBlocks.MINT_PLASTER);
            case 3 -> S(ArchBlocks.SALMON_PLASTER);
            case 4 -> S(ArchBlocks.SKY_PLASTER);
            default -> S(ArchBlocks.WHITE_PLASTER);
        };
    }

    /**
     * A doll's-house cottage of the tiny people (2 wide, 2 deep, 2 high walls) at (x, z) with its front toward
     * {@code face}: plaster walls on a fieldstone sill, a little gable of roof-tile stairs, an open shutter for a door, a
     * window, a chimney with a candle and a flower box. Purely decorative (players are far too big to get in).
     */
    private static void tinyHouse(Builder b, int x, int z, Direction face, int salt) {
        BlockState wall = tinyWall(b.rand(salt, 6));
        int roof = b.rand(salt + 1, 3);
        BlockState rs = roof == 0 ? S(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS) : roof == 1 ? S(ArchBlocks.TEAL_ROOF_TILE_STAIRS) : S(ArchBlocks.SKY_ROOF_TILE_STAIRS);
        b.fill(x, 1, z, x + 1, 2, z + 1, wall);
        b.fill(x, 1, z, x + 1, 1, z + 1, S(ArchBlocks.FIELDSTONE));
        boolean alongX = face.getAxis() == Direction.Axis.Z; // ridge parallel to the front
        if (alongX) {
            b.fill(x, 3, z, x + 1, 3, z, stairS(rs, Direction.SOUTH, Half.BOTTOM));
            b.fill(x, 3, z + 1, x + 1, 3, z + 1, stairS(rs, Direction.NORTH, Half.BOTTOM));
        } else {
            b.fill(x, 3, z, x, 3, z + 1, stairS(rs, Direction.EAST, Half.BOTTOM));
            b.fill(x + 1, 3, z, x + 1, 3, z + 1, stairS(rs, Direction.WEST, Half.BOTTOM));
        }
        // front cells
        int fx1, fz1, fx2, fz2;
        switch (face) {
            case NORTH -> { fx1 = x; fz1 = z; fx2 = x + 1; fz2 = z; }
            case SOUTH -> { fx1 = x; fz1 = z + 1; fx2 = x + 1; fz2 = z + 1; }
            case WEST -> { fx1 = x; fz1 = z; fx2 = x; fz2 = z + 1; }
            default -> { fx1 = x + 1; fz1 = z; fx2 = x + 1; fz2 = z + 1; }
        }
        b.set(fx1, 1, fz1, LandmarkKit.trapdoor(Blocks.OAK_TRAPDOOR, face, Half.BOTTOM, true));
        b.set(fx2, 2, fz2, Arch.along(S(Blocks.GLASS_PANE), face.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z));
        b.set(fx2 + face.getStepX(), 1, fz2 + face.getStepZ(), decor(ArchBlocks.FLOWER_PLANTER, face));
        int cx = face == Direction.EAST ? x : x + 1, cz = face == Direction.SOUTH ? z : z + 1;
        b.set(cx, 4, cz, Blocks.COBBLESTONE_WALL);
        b.set(cx, 5, cz, candles(Blocks.WHITE_CANDLE, 1));
    }

    /** The tiny people's kingdom among the roots of a great tree: the flared, buttressed trunk holds the royal meeting hall
     *  (and a storehouse down a stair below it); doll's-house cottages with tiled roofs and flower boxes cluster round the
     *  roots, along candle-lit paths, by a stream with plank bridges; a vegetable patch and a toadstool house. */
    static void tontattaVillage(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 14; // 29 x 29
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 30);
        scatter(b, 151, 40, 0, 0, w - 1, d - 1, 1, Blocks.DANDELION.defaultBlockState());
        scatter(b, 152, 30, 0, 0, w - 1, d - 1, 1, Blocks.PINK_TULIP.defaultBlockState());
        scatter(b, 153, 20, 0, 0, w - 1, d - 1, 1, Blocks.CORNFLOWER.defaultBlockState());
        scatter(b, 154, 14, 0, 0, w - 1, d - 1, 1, Blocks.AZURE_BLUET.defaultBlockState());
        BlockState bark = S(Blocks.OAK_WOOD), plank = S(Blocks.OAK_PLANKS);
        // a stream across the south with plank bridges
        for (int x = 0; x < w; x++) {
            b.set(x, 0, 25, Blocks.WATER);
            b.set(x, -1, 25, Blocks.CLAY);
            b.clear(x, 1, 25, x, 1, 25);
            if (b.rand(x * 5, 3) == 0) b.set(x, 0, 24, Blocks.MOSS_BLOCK);
        }
        for (int x : new int[]{6, 20}) {
            b.set(x, 1, 25, slabS(S(ArchBlocks.WEATHERED_PLANK_SLAB), SlabType.BOTTOM));
        }
        // little paths (packed mud) with candle posts
        for (int z = 0; z <= 9; z++) b.set(c, 0, z, Blocks.PACKED_MUD);
        for (int x = 2; x <= 26; x++) b.set(x, 0, 23, Blocks.PACKED_MUD);
        for (int[] p : new int[][]{{c - 1, 3}, {c + 1, 6}, {11, 22}, {17, 22}, {3, 22}, {25, 22}}) {
            b.set(p[0], 1, p[1], Blocks.OAK_FENCE);
            b.set(p[0], 2, p[1], candles(Blocks.YELLOW_CANDLE, 1));
        }
        // ---- the great tree: flared trunk, buttress roots, hollow hall, canopy
        for (int y = 1; y <= 18; y++) {
            int r = y == 1 ? 6 : y == 2 ? 5 : 4;
            b.cylinder(c, c, y, y, r, Blocks.OAK_WOOD, false);
        }
        b.cylinder(c, c, 1, 6, 3, Blocks.AIR, false);
        b.cylinder(c, c, 0, 0, 3, Blocks.MOSS_BLOCK, false);
        b.cylinder(c, c, 7, 7, 3, Blocks.OAK_PLANKS, false);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            for (int k = 4; k <= 9; k++) {
                int rx = c + (int) Math.round(Math.cos(a) * k), rz = c + (int) Math.round(Math.sin(a) * k);
                int h = k <= 5 ? 3 : k <= 7 ? 2 : 1;
                b.fill(rx, 1, rz, rx, h, rz, bark);
                if (k == 9) b.set(rx, 0, rz, Blocks.ROOTED_DIRT);
            }
        }
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++)
                for (int y = 15; y <= 25; y++) {
                    double v = (x * x + z * z) / 110.0 + Math.pow((y - 20) / 5.0, 2);
                    if (v > 1 || b.rand(x * 131 + z * 17 + y, 8) == 0) continue;
                    if (Math.abs(x) <= 4 && Math.abs(z) <= 4 && y <= 18) continue;
                    b.set(c + x, y, c + z, leaves(y > 21 && b.rand(x + z * 3, 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : y < 18 && b.rand(x * 3 + z, 4) == 0 ? Blocks.AZALEA_LEAVES : Blocks.OAK_LEAVES));
                }
        for (int i = 0; i < 5; i++) {
            double a = i * 2 * Math.PI / 5 + 0.3;
            for (int k = 4; k <= 8; k++) b.set(c + (int) Math.round(Math.cos(a) * k), 15 + k / 3, c + (int) Math.round(Math.sin(a) * k), log(Blocks.OAK_LOG, Math.abs(Math.cos(a)) > 0.7 ? Direction.Axis.X : Direction.Axis.Z));
        }
        // the door (north) with a little porch roof and lamps
        b.clear(c, 1, c - 6, c, 3, c - 4);
        b.set(c, 0, c - 5, Blocks.PACKED_MUD);
        b.set(c, 0, c - 6, Blocks.PACKED_MUD);
        b.bigDoor(c, 1, c - 4, Blocks.OAK_DOOR, Direction.NORTH);
        b.fill(c - 1, 4, c - 5, c + 1, 4, c - 5, slabS(S(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB), SlabType.BOTTOM));
        b.set(c - 2, 1, c - 7, stand(S(Blocks.LANTERN)));
        b.set(c + 2, 1, c - 7, stand(S(Blocks.LANTERN)));
        // the royal hall: little throne, tiny tables with candles, banners
        b.set(c, 1, c + 3, Blocks.GOLD_BLOCK);
        b.set(c, 2, c + 3, Blocks.POTTED_DANDELION);
        b.set(c - 1, 1, c + 3, candles(Blocks.YELLOW_CANDLE, 3));
        b.set(c + 1, 1, c + 3, candles(Blocks.YELLOW_CANDLE, 3));
        for (int[] t : new int[][]{{c - 2, c + 2}, {c + 2, c + 2}}) {
            b.set(t[0], 1, t[1], decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
            b.set(t[0], 2, t[1], candles(Blocks.CANDLE, 2));
        }
        b.set(c - 3, 3, c, banner(Blocks.YELLOW_WALL_BANNER, Direction.EAST));
        b.set(c + 3, 3, c, banner(Blocks.YELLOW_WALL_BANNER, Direction.WEST));
        b.set(c + 1, 1, c - 3, facing(Blocks.LECTERN, Direction.SOUTH));
        b.hanging(c, 6, c, Blocks.LANTERN);
        npc(b, "elder", c, 1, c + 2);
        npc(b, "guard", c - 1, 1, c - 2);
        npc(b, "guard", c + 2, 1, c - 9);
        // the storehouse below the hall, down a stair across its floor
        shell(b, c - 4, -5, c - 3, c + 4, 0, c + 2, Blocks.OAK_PLANKS);
        b.cylinder(c, c, 0, 0, 3, Blocks.MOSS_BLOCK, false);
        flight(b, S(Blocks.OAK_STAIRS), c + 2, -4, c, Direction.WEST, 5, 1, plank);
        b.chest(c + 3, -4, c + 1, Direction.WEST, Buildings.LOOT_TREASURE);
        mark(b, "treasure", c + 3, -4, c + 1);
        b.barrel(c + 3, -4, c - 2, Buildings.LOOT_HOUSE);
        b.fill(c - 3, -4, c - 2, c - 3, -3, c - 2, Blocks.HAY_BLOCK);
        b.set(c - 3, -4, c + 1, decor(ArchBlocks.CARGO_SACKS, Direction.EAST));
        b.hanging(c, -1, c + 1, Blocks.LANTERN);
        line(b, c - 1, 1, c - 1, c + 2, c - 1, Blocks.OAK_FENCE);
        line(b, c - 1, 1, c + 1, c + 2, c + 1, Blocks.OAK_FENCE);
        b.set(c + 3, 1, c, connect(Blocks.OAK_FENCE, true, false, true, false));
        // ---- the tiny cottages among the roots and along the paths
        int s = 0;
        Object[][] houses = {{2, 2, Direction.SOUTH}, {6, 1, Direction.SOUTH}, {19, 1, Direction.SOUTH}, {24, 2, Direction.SOUTH},
                {1, 9, Direction.EAST}, {1, 13, Direction.EAST}, {26, 10, Direction.WEST}, {5, 20, Direction.NORTH}, {9, 20, Direction.NORTH},
                {17, 20, Direction.NORTH}, {21, 20, Direction.NORTH}, {6, 27, Direction.NORTH}, {21, 27, Direction.NORTH}};
        for (Object[] h : houses) tinyHouse(b, (int) h[0], (int) h[1], (Direction) h[2], 160 + s++);
        npc(b, "child", 13, 1, 22);
        // the vegetable patch (west) and the toadstool house (east)
        b.fill(3, 0, 17, 6, 0, 18, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        for (int x = 3; x <= 6; x++)
            for (int z = 17; z <= 18; z++) b.set(x, 1, z, (x + z) % 2 == 0 ? Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7) : Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE, 7));
        b.set(3, 0, 17, Blocks.WATER);
        b.set(3, 1, 17, Blocks.AIR);
        outline(b, 2, 1, 16, 7, 19, Blocks.OAK_FENCE);
        b.set(7, 1, 17, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true).setValue(FenceGateBlock.FACING, Direction.EAST));
        npc(b, "farmer", 8, 1, 18);
        int mx = 22, mz = 8;
        b.fill(mx - 2, 1, mz - 2, mx + 2, 3, mz + 2, Blocks.MUSHROOM_STEM);
        b.clear(mx - 1, 1, mz - 1, mx + 1, 3, mz + 1);
        b.fill(mx - 2, 0, mz - 2, mx + 2, 0, mz + 2, Blocks.SPRUCE_PLANKS);
        LandmarkKit.ellipsoid(b, mx, 4, mz, 3.6, 2.4, 3.6, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), false, 0);
        b.clear(mx - 1, 4, mz - 1, mx + 1, 4, mz + 1);
        for (int[] p : new int[][]{{mx - 2, mz - 1}, {mx + 1, mz - 3}, {mx + 3, mz + 1}, {mx - 1, mz + 3}}) b.set(p[0], 6, p[1], Blocks.MUSHROOM_STEM);
        b.door(mx, 1, mz - 2, Blocks.OAK_DOOR, Direction.NORTH);
        b.set(mx - 1, 2, mz + 2, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
        b.bed(mx - 1, 1, mz, Blocks.RED_BED, Direction.SOUTH);
        b.barrel(mx + 1, 1, mz + 1, Buildings.LOOT_HOUSE);
        b.hanging(mx, 4, mz, Blocks.LANTERN);
        b.set(mx, 5, mz, Blocks.RED_MUSHROOM_BLOCK);
        npc(b, "resident", mx + 1, 1, mz - 1);
        b.marker("legend", c, 1, c - 7, tag("story", "dwarf_kingdom"));
    }

    // ============================================================== Zou: the gate of the great legs

    /** Grey wrinkled elephant skin at a column cell (bands every three blocks, tuff speckle). */
    private static BlockState skin(Builder b, int x, int y, int z) {
        int r = b.rand(x * 31 + y * 131 + z * 17, 9);
        boolean crease = (y + (b.rand(x * 7 + z * 3, 3) == 0 ? 1 : 0)) % 4 == 0;
        return crease ? S(Blocks.TUFF) : r == 0 ? S(Blocks.STONE) : r == 1 ? S(Blocks.TUFF) : S(Blocks.ANDESITE);
    }

    /** Two colossal elephant legs with broad wrinkled feet and white toenails, joined by the arched belly (a walkway on
     *  its back, reached by a spiral stair inside the hollow west leg; a hidden alcove in the belly), a palisade gate
     *  with watch posts between them, the lord's thatched lodge in a great tree and mink houses. */
    static void giantLegs(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20; // 41 x 41
        site(b, Blocks.PODZOL, Blocks.DIRT, 42);
        patchGround(b, 171, 0, Blocks.PODZOL, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK);
        scatter(b, 172, 24, 1, 12, w - 2, d - 2, 1, Blocks.FERN.defaultBlockState());
        int lz = 22;
        // ---- the legs: columns to the belly top, broad feet, toenails on the front
        for (int lx : new int[]{11, 29}) {
            for (int y = 1; y <= 32; y++) {
                double r = y <= 2 ? 5.6 : y <= 4 ? 5.0 : 4.6;
                for (int x = -6; x <= 6; x++)
                    for (int z = -6; z <= 6; z++) {
                        if (Math.sqrt(x * x + z * z) > r) continue;
                        b.set(lx + x, y, lz + z, skin(b, lx + x, y, lz + z));
                    }
            }
            for (int deg : new int[]{245, 270, 295}) {
                double a = Math.toRadians(deg);
                int nx = lx + (int) Math.round(Math.cos(a) * 5.2), nz = lz + (int) Math.round(Math.sin(a) * 5.2);
                b.fill(nx, 1, nz, nx, 2, nz, Blocks.CALCITE);
                int ix = lx + (int) Math.round(Math.cos(a) * 4.4), iz = lz + (int) Math.round(Math.sin(a) * 4.4);
                b.set(ix, 3, iz, Blocks.CALCITE);
            }
        }
        // ---- the belly between them (arched underside), a mossy walkway on its back
        for (int x = 11; x <= 29; x++) {
            int under = 24 + (int) Math.round(4 * Math.sin(Math.PI * (x - 11) / 18.0));
            for (int z = 18; z <= 26; z++)
                for (int y = under; y <= 32; y++) b.set(x, y, z, y == under ? S(Blocks.TUFF) : skin(b, x, y + 1, z));
        }
        for (int x = 11; x <= 29; x++) for (int z = 19; z <= 25; z++) if (b.rand(x * 5 + z * 3, 4) == 0) b.set(x, 33, z, Blocks.MOSS_CARPET);
        line(b, 11, 33, 18, 29, 18, Blocks.SPRUCE_FENCE);
        line(b, 11, 33, 26, 29, 26, Blocks.SPRUCE_FENCE);
        for (int x = 20; x <= 26; x += 6) {
            b.set(x, 33, 22, Blocks.SPRUCE_FENCE);
            b.set(x, 34, 22, stand(S(ArchBlocks.RED_PAPER_LANTERN)));
        }
        b.hanging(c, 27, 22, Blocks.LANTERN);
        npc(b, "resident", c, 33, 23);
        // ---- the hollow west leg: door, spiral stair up through the belly to the back
        b.cylinder(11, lz, 1, 31, 3, Blocks.AIR, false);
        b.cylinder(11, lz, 0, 0, 3, Blocks.SPRUCE_PLANKS, false);
        spiral(b, 9, lz - 2, 13, lz + 2, 0, 33, S(Blocks.SPRUCE_STAIRS), S(Blocks.SPRUCE_PLANKS));
        b.clear(5, 1, lz, 7, 3, lz);
        b.fill(5, 0, lz, 6, 0, lz, Blocks.SPRUCE_PLANKS);
        b.bigDoor(7, 1, lz, Blocks.SPRUCE_DOOR, Direction.WEST);
        for (int y = 8; y <= 26; y += 9) b.set(11, y, lz + 4, Blocks.SHROOMLIGHT);
        // the hidden alcove in the belly behind a tuff patch beside the stair
        b.set(15, 29, lz, Blocks.TUFF);
        b.set(15, 30, lz, Blocks.TUFF);
        b.clear(16, 29, lz - 1, 18, 31, lz + 1);
        b.chest(18, 29, lz, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(16, 31, lz + 1, Blocks.SHROOMLIGHT);
        mark(b, "treasure", 18, 29, lz);
        // ---- the palisade gate between the legs, with watch posts
        for (int x = 16; x <= 24; x++) if (x < 19 || x > 21) b.fill(x, 1, lz, x, 4, lz, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
        for (int x = 16; x <= 24; x += 2) if (x < 19 || x > 21) b.set(x, 5, lz, Blocks.SPRUCE_FENCE);
        b.fill(16, 6, lz, 24, 6, lz, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        b.set(19, 1, lz, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));
        b.set(21, 1, lz, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));
        for (int x : new int[]{18, 22}) b.fill(x, 5, lz, x, 7, lz, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        b.fill(18, 8, lz, 22, 8, lz, slabS(S(ArchBlocks.THATCH_SLAB), SlabType.BOTTOM));
        b.set(c, 5, lz, hang(S(ArchBlocks.RED_PAPER_LANTERN)));
        for (int x : new int[]{18, 22}) flag(b, x, 9, lz, 3, Direction.SOUTH, 2, S(Blocks.YELLOW_WOOL), S(Blocks.ORANGE_WOOL));
        npc(b, "guard", 18, 1, 20);
        npc(b, "guard", 22, 1, 20);
        for (int z = 12; z <= 20; z += 4) {
            b.set(17, 1, z, Blocks.SPRUCE_FENCE);
            b.set(17, 2, z, stand(S(ArchBlocks.RED_PAPER_LANTERN)));
            b.set(23, 1, z, Blocks.SPRUCE_FENCE);
            b.set(23, 2, z, stand(S(ArchBlocks.RED_PAPER_LANTERN)));
        }
        for (int z = 11; z <= 21; z++) for (int x = 19; x <= 21; x++) b.set(x, 0, z, Blocks.PACKED_MUD);
        // ---- the lord's lodge in a great tree (south)
        tree(b, c, 1, 34, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, 14, 6, 181, null);
        b.fill(c, 1, 34, c + 1, 14, 35, Blocks.JUNGLE_LOG);
        b.fill(12, 7, 29, 28, 7, 39, S(ArchBlocks.WEATHERED_PLANKS));
        b.clear(12, 8, 29, 28, 13, 39);
        for (int[] p : new int[][]{{12, 29}, {28, 29}, {12, 39}, {28, 39}, {c, 29}, {c, 39}}) b.fill(p[0], 1, p[1], p[0], 6, p[1], log(Blocks.JUNGLE_LOG, Direction.Axis.Y));
        b.fill(c, 8, 34, c + 1, 13, 35, Blocks.JUNGLE_LOG);
        flight(b, S(Blocks.JUNGLE_STAIRS), 13, 1, 38, Direction.NORTH, 7, 2, S(Blocks.JUNGLE_PLANKS));
        Mats lm = Mats.of(BuildStyle.GIANT, b.seed);
        lm.wall = S(ArchBlocks.WEATHERED_PLANKS);
        lm.upperWall = lm.wall;
        lm.frame = log(Blocks.JUNGLE_LOG, Direction.Axis.Y);
        lm.base = S(ArchBlocks.WEATHERED_PLANKS);
        lm.floor = S(Blocks.JUNGLE_PLANKS);
        roofTiles(lm, ArchBlocks.THATCH, ArchBlocks.THATCH_STAIRS, ArchBlocks.THATCH_SLAB);
        lm.roofKind = Arch.RoofKind.GABLE;
        lm.eaveBrackets = true;
        lm.window = S(ArchBlocks.LATTICE_WINDOW);
        lm.shutter = null;
        lm.planters = false;
        lm.lantern = S(ArchBlocks.RED_PAPER_LANTERN);
        storeys(b, lm, 16, 7, 30, 26, 38, 1, 4, S(Blocks.JUNGLE_PLANKS));
        b.fill(c, 8, 34, c + 1, 10, 35, Blocks.JUNGLE_LOG);
        Arch.gable(b, lm, 16, 30, 26, 38, 12, true, 1, lm.wall);
        b.bigDoor(c, 8, 30, Blocks.JUNGLE_DOOR, Direction.NORTH);
        for (int z : new int[]{32, 36}) {
            Arch.window(b, lm, 16, 9, z, Direction.WEST, 2, false);
            Arch.window(b, lm, 26, 9, z, Direction.EAST, 2, false);
        }
        Arch.window(b, lm, 18, 9, 30, Direction.NORTH, 2, false);
        Arch.window(b, lm, 24, 9, 30, Direction.NORTH, 2, false);
        line(b, 12, 8, 29, 28, 29, Blocks.JUNGLE_FENCE);
        b.clear(c, 8, 29, c, 8, 29);
        b.clear(13, 8, 29, 14, 8, 29);
        line(b, 12, 8, 30, 12, 39, Blocks.JUNGLE_FENCE);
        line(b, 28, 8, 30, 28, 39, Blocks.JUNGLE_FENCE);
        b.bed(17, 8, 36, Blocks.YELLOW_BED, Direction.SOUTH);
        b.chest(17, 8, 31, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(23, 8, 32, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(23, 9, 32, candles(Blocks.CANDLE, 2));
        b.set(24, 8, 32, stairS(S(Blocks.JUNGLE_STAIRS), Direction.EAST, Half.BOTTOM));
        b.set(25, 10, 34, banner(Blocks.YELLOW_WALL_BANNER, Direction.WEST));
        b.hanging(23, 10, 36, ArchBlocks.RED_PAPER_LANTERN.get());
        npc(b, "elder", 23, 8, 35);
        // mink houses
        Buildings.house(sub(b, 1, 0, 2, 9, 9, Rotation.NONE), BuildStyle.JUNGLE, 1, Buildings.Interior.HOME);
        Buildings.house(sub(b, 31, 0, 2, 9, 9, Rotation.NONE), BuildStyle.JUNGLE, 1, Buildings.Interior.SHOP);
        npc(b, "child", 20, 1, 12);
        b.marker("legend", c, 1, 14, tag("story", "great_legs"));
    }

    // ============================================================== Whole Cake Island: the cake chateau

    /** Sponge, filling and cream courses of a cake tier (0 vanilla and strawberry, 1 chocolate, 2 strawberry and mint). */
    private static BlockState cakeLayer(int tier, int y) {
        int m = Math.floorMod(y, 4);
        return switch (tier) {
            case 0 -> m == 0 ? S(ArchBlocks.SALMON_PLASTER) : m == 2 ? S(ArchBlocks.WHITE_PLASTER) : S(ArchBlocks.CREAM_PLASTER);
            case 1 -> m == 0 ? S(ArchBlocks.WHITE_PLASTER) : S(Blocks.BROWN_TERRACOTTA);
            default -> m == 0 ? S(ArchBlocks.WHITE_PLASTER) : m == 2 ? S(ArchBlocks.MINT_PLASTER) : S(ArchBlocks.SALMON_PLASTER);
        };
    }

    /** One round tier of the cake from y1 to y2 (walls of radius r, hollow), a cream top at y2+1 with a piped rim,
     *  cream dripping down its side and pink windows. */
    private static void cakeTier(Builder b, int c, int r, int y1, int y2, int tier, int windows, int salt) {
        for (int y = y1; y <= y2; y++) {
            LandmarkKit.ring(b, c, c, y, r, cakeLayer(tier, y));
            b.cylinder(c, c, y, y, r - 1, Blocks.AIR, false);
        }
        LandmarkKit.disc(b, c, c, y2 + 1, r + 0.4, S(ArchBlocks.WHITE_PLASTER));
        LandmarkKit.ring(b, c, c, y2 + 2, r, slabS(S(ArchBlocks.WHITE_PLASTER_SLAB), SlabType.BOTTOM));
        int n = (int) Math.round(2 * Math.PI * r / 2.0);
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n;
            int x = c + (int) Math.round(Math.cos(a) * r), z = c + (int) Math.round(Math.sin(a) * r);
            int len = 1 + b.rand(salt + i * 7, 4);
            b.fill(x, y2 + 1 - len, z, x, y2, z, S(ArchBlocks.WHITE_PLASTER));
            if (i % 3 == 0) {
                b.set(x, y2 + 2, z, S(ArchBlocks.WHITE_PLASTER));
                b.set(x, y2 + 3, z, i % 6 == 0 ? S(Blocks.RED_CONCRETE) : S(Blocks.PINK_CONCRETE));
            }
        }
        for (int k = 0; k < windows; k++) {
            double a = (k + 0.5) * 2 * Math.PI / windows;
            int x = c + (int) Math.round(Math.cos(a) * r), z = c + (int) Math.round(Math.sin(a) * r);
            int wy = y1 + (y2 - y1) / 2 - 1;
            b.fill(x, wy, z, x, wy + 2, z, Blocks.PINK_STAINED_GLASS);
            b.set(x, wy + 3, z, S(ArchBlocks.WHITE_PLASTER));
        }
    }

    /** A pastel turret on a cake terrace: a white drum with a pink band under a needle roof of sky or teal tiles. */
    private static void cakeTurret(Builder b, int x, int z, int y, int h, boolean sky) {
        b.cylinder(x, z, y, y + h - 1, 1, Blocks.WHITE_CONCRETE, false);
        LandmarkKit.disc(b, x, z, y + h - 3, 1.5, S(Blocks.PINK_CONCRETE));
        LandmarkKit.disc(b, x, z, y, 1.5, S(ArchBlocks.WHITE_PLASTER));
        BlockState tiles = sky ? S(ArchBlocks.SKY_ROOF_TILES) : S(ArchBlocks.TEAL_ROOF_TILES);
        eaveRing(b, x, z, y + h, 1.9, sky ? S(ArchBlocks.SKY_ROOF_TILE_STAIRS) : S(ArchBlocks.TEAL_ROOF_TILE_STAIRS));
        spire(b, x, z, y + h + 1, 1.2, 3, tiles, S(Blocks.GOLD_BLOCK));
    }

    /** Whole Cake Chateau: a castle that is a three-tier cake (vanilla-strawberry, chocolate, strawberry-mint) with cream
     *  drips, piped rims and strawberries, pastel turrets on the terraces, giant lit candles, and a pink keep with a
     *  sky-tile spire on the top; a candy garden and a candy-cane portico in front. Inside: the banquet hall and kitchen,
     *  the throne room with the mirror vault, the private chamber, and stairs all the way up to the keep. */
    static void cakeChateau(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 25; // 51 x 51
        site(b, Blocks.PINK_TERRACOTTA, Blocks.DIRT, 56);
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) if (((x >> 2) + (z >> 2)) % 2 == 0) b.set(x, 0, z, Blocks.WHITE_TERRACOTTA);
        // ---- candy garden: lollipop trees, candy canes, cookies, gumdrops; a cookie path to the door
        BlockState[] candy = {S(Blocks.PINK_CONCRETE), S(Blocks.LIGHT_BLUE_CONCRETE), S(Blocks.LIME_CONCRETE), S(Blocks.YELLOW_CONCRETE), S(Blocks.MAGENTA_CONCRETE)};
        int i = 0;
        for (int[] p : new int[][]{{3, 3}, {47, 3}, {3, 47}, {47, 47}, {8, 12}, {42, 12}, {6, 30}, {44, 30}}) {
            b.fill(p[0], 1, p[1], p[0], 5, p[1], Blocks.WHITE_CONCRETE);
            LandmarkKit.ellipsoid(b, p[0], 7, p[1], 2.2, 2.2, 1.2, candy[i % candy.length], false, -100);
            LandmarkKit.ring(b, p[0], p[1], 7, 1.2, S(Blocks.WHITE_CONCRETE));
            i++;
        }
        for (int[] p : new int[][]{{13, 3}, {37, 3}, {2, 22}, {48, 22}}) {
            for (int y = 1; y <= 6; y++) b.set(p[0], y, p[1], (y & 1) == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
            b.set(p[0] + 1, 7, p[1], Blocks.RED_CONCRETE);
            b.set(p[0], 7, p[1], Blocks.WHITE_CONCRETE);
            b.set(p[0] + 2, 6, p[1], Blocks.WHITE_CONCRETE);
        }
        for (int[] p : new int[][]{{6, 41}, {44, 41}, {17, 5}, {33, 5}}) {
            LandmarkKit.disc(b, p[0], p[1], 1, 1.8, S(Blocks.BROWN_TERRACOTTA));
            b.set(p[0], 1, p[1], Blocks.BROWN_CONCRETE);
        }
        for (int[] p : new int[][]{{10, 20}, {40, 20}, {12, 38}, {38, 38}, {20, 46}, {30, 46}}) {
            LandmarkKit.ellipsoid(b, p[0], 1, p[1], 1.3, 1.6, 1.3, candy[(p[0] + p[1]) % candy.length], false, 0);
            b.set(p[0], 0, p[1], Blocks.WHITE_TERRACOTTA);
        }
        for (int z = 0; z <= 6; z++) for (int x = c - 2; x <= c + 2; x++) b.set(x, 0, z, ((x + z) & 1) == 0 ? Blocks.BROWN_TERRACOTTA : Blocks.WHITE_TERRACOTTA);
        npc(b, "resident", 10, 1, 6);
        npc(b, "child", 40, 1, 7);
        // ---- the tiers
        cakeTier(b, c, 18, 1, 9, 0, 8, 301);
        b.cylinder(c, c, 0, 0, 17, Blocks.CHERRY_PLANKS, false);
        cakeTier(b, c, 13, 11, 19, 1, 8, 302);
        cakeTier(b, c, 8, 21, 27, 2, 4, 303);
        // strawberries on the terraces
        for (int k = 0; k < 12; k++) {
            double a = (k + 0.5) * Math.PI / 6;
            int sx = c + (int) Math.round(Math.cos(a) * 16.5), sz = c + (int) Math.round(Math.sin(a) * 16.5);
            b.set(sx, 11, sz, Blocks.RED_CONCRETE);
            b.set(sx, 12, sz, Blocks.GREEN_CARPET);
        }
        // pastel turrets on the first terrace (one stands over the door)
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(30 + 60 * k);
            cakeTurret(b, c + (int) Math.round(Math.cos(a) * 15.8), c + (int) Math.round(Math.sin(a) * 15.8), 11, 7, (k & 1) == 0);
        }
        // giant candles on the second terrace
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(54 + 72 * k);
            int cx = c + (int) Math.round(Math.cos(a) * 10.5), cz = c + (int) Math.round(Math.sin(a) * 10.5);
            for (int y = 21; y <= 30; y++) b.set(cx, y, cz, (y % 3 == 0) ? S(Blocks.PINK_CONCRETE) : S(Blocks.WHITE_CONCRETE));
            b.set(cx, 31, cz, candles(Blocks.YELLOW_CANDLE, 1));
        }
        // the keep on the top tier
        b.cylinder(c, c, 29, 37, 3, Blocks.PINK_CONCRETE, true);
        for (int y : new int[]{29, 33, 37}) LandmarkKit.ring(b, c, c, y, 3, S(ArchBlocks.WHITE_PLASTER));
        b.cylinder(c, c, 38, 38, 3, Blocks.WHITE_CONCRETE, false);
        b.clear(c, 29, c - 3, c, 31, c - 3);
        for (int[] p : new int[][]{{c + 3, c}, {c - 3, c}, {c, c + 3}}) b.fill(p[0], 32, p[1], p[0], 34, p[1], Blocks.PINK_STAINED_GLASS);
        eaveRing(b, c, c, 39, 4.0, S(ArchBlocks.SKY_ROOF_TILE_STAIRS));
        int top = spire(b, c, c, 39, 3.3, 2, S(ArchBlocks.SKY_ROOF_TILES), S(Blocks.GOLD_BLOCK));
        flag(b, c, top, c, 4, Direction.EAST, 3, S(Blocks.PINK_WOOL), S(Blocks.WHITE_WOOL));
        b.set(c, 29, c, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(c, 30, c, candles(Blocks.PINK_CANDLE, 3));
        b.hanging(c, 37, c, Blocks.LANTERN);
        // ---- the candy-cane portico and the door
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 1, c - 18, Blocks.CHERRY_DOOR, Direction.NORTH);
        for (int x : new int[]{c - 3, c + 3}) {
            for (int y = 1; y <= 6; y++) b.set(x, y, c - 19, (y & 1) == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
        }
        b.fill(c - 3, 7, c - 19, c + 3, 7, c - 19, S(Blocks.PINK_CONCRETE));
        b.fill(c - 2, 7, c - 18, c + 2, 7, c - 18, S(Blocks.PINK_CONCRETE));
        for (int x = c - 2; x <= c + 2; x++) b.set(x, 6, c - 19, (x & 1) == 0 ? S(ArchBlocks.WHITE_PLASTER) : S(Blocks.AIR));
        b.fill(c - 3, 8, c - 19, c + 3, 8, c - 19, slabS(S(ArchBlocks.WHITE_PLASTER_SLAB), SlabType.BOTTOM));
        b.set(c, 8, c - 19, S(Blocks.RED_CONCRETE));
        b.hanging(c, 6, c - 19, Blocks.LANTERN);
        // ---- bottom tier: banquet hall and kitchen
        carpet(b, c - 1, 1, c - 17, c + 1, c - 12, Blocks.PINK_CARPET);
        for (int z = c - 9; z <= c + 7; z++) {
            b.set(c, 1, z, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
            if ((z & 1) == 0) {
                chair(b, c - 1, 1, z, Blocks.CHERRY_STAIRS, Direction.WEST);
                chair(b, c + 1, 1, z, Blocks.CHERRY_STAIRS, Direction.EAST);
            }
            b.set(c, 2, z, (z % 3 == 0) ? Blocks.CANDLE_CAKE.defaultBlockState().setValue(CandleCakeBlock.LIT, true) : Blocks.CAKE.defaultBlockState());
        }
        for (int[] p : new int[][]{{c - 7, c - 7}, {c + 7, c - 7}, {c - 7, c + 4}, {c + 7, c + 4}}) {
            for (int y = 1; y <= 9; y++) b.set(p[0], y, p[1], (y & 1) == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
        }
        for (int z = c - 8; z <= c + 6; z += 7) b.hanging(c, 9, z, Blocks.LANTERN);
        b.hanging(c - 8, 9, c, Blocks.LANTERN);
        npc(b, "waiter", c + 3, 1, c - 4);
        npc(b, "pirate", c - 4, 1, c + 2);
        b.fill(c - 12, 1, c + 10, c + 6, 9, c + 10, S(ArchBlocks.CREAM_PLASTER));
        b.door(c - 3, 1, c + 10, Blocks.CHERRY_DOOR, Direction.NORTH);
        kitchenX(b, c - 7, 1, c + 15, Direction.NORTH, Buildings.LOOT_TAVERN);
        b.barrel(c - 2, 1, c + 15, Buildings.LOOT_TAVERN);
        b.barrel(c - 1, 1, c + 15, Buildings.LOOT_TAVERN);
        b.set(c - 4, 1, c + 12, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(c - 4, 2, c + 12, Blocks.CAKE);
        b.set(c + 1, 1, c + 12, decor(ArchBlocks.GOODS_SHELF, Direction.WEST));
        b.set(c + 1, 2, c + 12, decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        b.hanging(c - 4, 9, c + 13, Blocks.LANTERN);
        npc(b, "cook", c - 6, 1, c + 12);
        npc(b, "cook", c - 1, 1, c + 13);
        flight(b, S(Blocks.CHERRY_STAIRS), c + 10, 1, c + 4, Direction.NORTH, 9, 2, S(Blocks.CHERRY_PLANKS));
        line(b, c + 9, 11, c - 4, c + 9, c - 1, Blocks.CHERRY_FENCE);
        // ---- middle tier: throne room and the mirror vault
        b.cylinder(c, c, 10, 10, 12, Blocks.CHERRY_PLANKS, false);
        b.clear(c + 10, 10, c - 4, c + 11, 10, c - 1);
        carpet(b, c - 1, 11, c - 8, c + 1, c + 7, Blocks.MAGENTA_CARPET);
        b.fill(c - 3, 11, c + 8, c + 3, 11, c + 11, Blocks.GOLD_BLOCK);
        b.fill(c - 2, 12, c + 10, c + 2, 12, c + 11, Blocks.PINK_WOOL);
        b.set(c, 13, c + 10, stairS(S(Blocks.PURPUR_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.fill(c - 2, 13, c + 11, c + 2, 16, c + 11, Blocks.PINK_WOOL);
        b.fill(c - 1, 17, c + 11, c + 1, 17, c + 11, Blocks.GOLD_BLOCK);
        b.set(c, 18, c + 11, Blocks.GOLD_BLOCK);
        for (int k = 0; k < 6; k++) {
            int px = c + (k < 3 ? -6 : 6), pz = c - 6 + (k % 3) * 6;
            for (int y = 11; y <= 19; y++) b.set(px, y, pz, (y & 1) == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
        }
        b.hanging(c, 19, c, Blocks.LANTERN);
        b.hanging(c, 19, c + 6, Blocks.LANTERN);
        npc(b, "pirate_captain", c, 12, c + 8);
        npc(b, "pirate", c - 3, 11, c + 5);
        npc(b, "pirate", c + 2, 11, c + 5);
        b.fill(c - 12, 11, c - 3, c - 8, 15, c - 3, S(ArchBlocks.CREAM_PLASTER));
        b.fill(c - 12, 11, c + 3, c - 8, 15, c + 3, S(ArchBlocks.CREAM_PLASTER));
        b.fill(c - 12, 15, c - 2, c - 8, 15, c + 2, S(ArchBlocks.CREAM_PLASTER));
        b.fill(c - 8, 11, c - 2, c - 8, 14, c + 2, Blocks.TINTED_GLASS);
        b.chest(c - 11, 11, c, Direction.EAST, Buildings.LOOT_TREASURE);
        b.set(c - 11, 11, c - 2, Blocks.GOLD_BLOCK);
        b.set(c - 11, 11, c + 2, Blocks.AMETHYST_BLOCK);
        b.set(c - 10, 14, c, Blocks.SHROOMLIGHT);
        mark(b, "treasure", c - 11, 11, c);
        flight(b, S(Blocks.CHERRY_STAIRS), c + 3, 11, c + 4, Direction.NORTH, 9, 2, S(Blocks.CHERRY_PLANKS));
        // ---- top tier: the private chamber, stairs up to the roof terrace and the keep
        b.cylinder(c, c, 20, 20, 7, Blocks.CHERRY_PLANKS, false);
        // the stairwell of the flight from the throne room (its top three steps rise through this floor), railed
        b.clear(c + 3, 20, c - 4, c + 4, 20, c - 2);
        line(b, c + 2, 21, c - 4, c + 2, c - 1, Blocks.CHERRY_FENCE);
        line(b, c + 3, 21, c - 1, c + 4, c - 1, Blocks.CHERRY_FENCE);
        b.bed(c + 4, 21, c + 2, Blocks.PINK_BED, Direction.SOUTH);
        b.chest(c - 5, 21, c + 1, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(c - 1, 21, c - 5, facing(Blocks.LOOM, Direction.SOUTH));
        b.set(c + 1, 21, c + 5, decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        carpet(b, c, 21, c, c + 3, c + 4, Blocks.PINK_CARPET);
        b.hanging(c, 27, c, Blocks.LANTERN);
        flight(b, S(Blocks.CHERRY_STAIRS), c - 3, 21, c + 3, Direction.NORTH, 7, 1, S(Blocks.CHERRY_PLANKS));
        b.marker("arc_anchor", c, 1, c - 20, tag("arc", "tea_party"));
    }

    private NewWorldLandmarks() {}
}
