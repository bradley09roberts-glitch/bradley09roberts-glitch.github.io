package com.grandline.world.settlement;

import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Procedural building generators with furnished interiors. Every building writes in local coordinates with the
 * entrance on the local north side (z = 0). Doorways are 3 blocks tall so large races fit.
 */
public final class Buildings {
    public static final String LOOT_HOUSE = "grandline:chests/house";
    public static final String LOOT_TAVERN = "grandline:chests/tavern";
    public static final String LOOT_SHOP = "grandline:chests/shop";
    public static final String LOOT_SMITH = "grandline:chests/blacksmith";
    public static final String LOOT_MARINE = "grandline:chests/marine";
    public static final String LOOT_WAREHOUSE = "grandline:chests/warehouse";
    public static final String LOOT_RUIN = "grandline:chests/ruin";
    public static final String LOOT_TREASURE = "grandline:chests/treasure";

    public static void build(Plot plot, BuildStyle style, Builder b) {
        switch (plot.type()) {
            case HOUSE_SMALL, HOUSE -> house(b, style, 1, Interior.HOME);
            case HOUSE_LARGE -> house(b, style, 2, Interior.HOME);
            case MANSION -> house(b, style, 2, Interior.MANSION);
            case TAVERN -> house(b, style, 1, Interior.TAVERN);
            case SHOP -> house(b, style, 1, Interior.SHOP);
            case BLACKSMITH -> house(b, style, 1, Interior.SMITH);
            case INN -> house(b, style, 2, Interior.INN);
            case CHURCH -> church(b, style);
            case MARINE_POST -> marinePost(b);
            case WATCHTOWER -> watchtower(b, style);
            case LIGHTHOUSE -> lighthouse(b, style);
            case WAREHOUSE -> house(b, style, 1, Interior.WAREHOUSE);
            case MARKET_STALL -> stall(b, style);
            case WELL -> well(b, style);
            case PLAZA -> plaza(b, style);
            case FARM -> farm(b, style);
            case RUINED_HOUSE -> ruin(b, style);
            case BOUNTY_BOARD -> bountyBoard(b, style);
            case DOJO -> house(b, style, 1, Interior.DOJO);
            case SHIPWRIGHT -> shipwright(b, style);
            case HUT -> hut(b, style);
            case LANDMARK -> {}
        }
    }

    public enum Interior { HOME, TAVERN, SHOP, SMITH, INN, MANSION, WAREHOUSE, DOJO }

    private static CompoundTag npc(String role) {
        CompoundTag t = new CompoundTag();
        t.putString("role", role);
        return t;
    }

    // ------------------------------------------------------------------ generic house

    public static void house(Builder b, BuildStyle st, int floors, Interior in) {
        int w = b.w, d = b.d;
        int wallH = 4;
        int top = wallH * floors;
        b.foundation(0, 0, w - 1, d - 1, st.foundation, 14);
        b.clear(0, 1, 0, w - 1, top + w / 2 + 3, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, st.foundation);
        b.fill(1, 0, 1, w - 2, 0, d - 2, st.floor);
        for (int f = 0; f < floors; f++) {
            int y0 = 1 + f * wallH;
            b.walls(0, y0, 0, w - 1, y0 + wallH - 1, d - 1, st.wall);
            // corner frames
            for (int y = y0; y < y0 + wallH; y++) {
                b.set(0, y, 0, st.frame);
                b.set(w - 1, y, 0, st.frame);
                b.set(0, y, d - 1, st.frame);
                b.set(w - 1, y, d - 1, st.frame);
            }
            // windows
            if (st.window != Blocks.AIR) {
                for (int x = 2; x < w - 2; x += 3) {
                    if (f == 0 && Math.abs(x - w / 2) <= 1) continue;
                    b.fill(x, y0 + 1, 0, x, y0 + 2, 0, st.window);
                    b.fill(x, y0 + 1, d - 1, x, y0 + 2, d - 1, st.window);
                }
                for (int z = 2; z < d - 2; z += 3) {
                    b.fill(0, y0 + 1, z, 0, y0 + 2, z, st.window);
                    b.fill(w - 1, y0 + 1, z, w - 1, y0 + 2, z, st.window);
                }
            } else {
                for (int x = 2; x < w - 2; x += 3) {
                    if (f == 0 && Math.abs(x - w / 2) <= 1) continue;
                    b.clear(x, y0 + 1, d - 1, x, y0 + 1, d - 1);
                }
            }
            if (f > 0) {
                b.fill(1, y0 - 1, 1, w - 2, y0 - 1, d - 2, st.floor);
                // stair well with ladder
                for (int y = y0 - 4; y < y0; y++) b.set(w - 2, y, d - 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
                b.set(w - 2, y0 - 1, d - 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
            }
            b.hanging(w / 2, y0 + wallH - 1, d / 2, st.light);
        }
        // entrance (3 tall) + step
        b.bigDoor(w / 2, 1, 0, st.door, Direction.NORTH);
        b.set(w / 2, 4, 0, st.frame);
        b.stairs(w / 2, 0, -1, st.roofStairs, Direction.SOUTH, Half.BOTTOM);
        b.set(w / 2 - 1, 3, -1, st.light.defaultBlockState().hasProperty(LanternBlock.HANGING) ? st.light.defaultBlockState() : Blocks.LANTERN.defaultBlockState());
        // roof
        b.gableRoof(-1, -1, w, d, top + 1, st.roofStairs, st.roofSlab, st.wall);
        furnish(b, st, in, floors, wallH);
    }

    private static void furnish(Builder b, BuildStyle st, Interior in, int floors, int wallH) {
        int w = b.w, d = b.d;
        switch (in) {
            case HOME -> {
                b.bed(1, 1, d - 3, Blocks.RED_BED, Direction.SOUTH);
                b.set(w - 2, 1, 1, Blocks.CRAFTING_TABLE);
                b.set(w - 2, 1, 2, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.WEST));
                b.chest(1, 1, 1, Direction.EAST, LOOT_HOUSE);
                b.set(w / 2, 1, d / 2, st.fence);
                b.set(w / 2, 2, d / 2, Blocks.OAK_PRESSURE_PLATE);
                b.stairs(w / 2 - 1, 1, d / 2, Blocks.OAK_STAIRS, Direction.EAST, Half.BOTTOM);
                b.stairs(w / 2 + 1, 1, d / 2, Blocks.OAK_STAIRS, Direction.WEST, Half.BOTTOM);
                b.set(w - 2, 1, d - 2, Blocks.BOOKSHELF);
                b.set(w - 2, 2, d - 2, Blocks.POTTED_POPPY);
                b.marker("npc", w / 2, 1, d / 2 + 1, npc("resident"));
                if (floors > 1) {
                    b.bed(1, wallH + 1, d - 3, Blocks.BLUE_BED, Direction.SOUTH);
                    b.bed(3, wallH + 1, d - 3, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
                    b.chest(1, wallH + 1, 1, Direction.EAST, LOOT_HOUSE);
                }
            }
            case TAVERN -> {
                // bar counter across the back
                for (int x = 2; x < w - 2; x++) b.set(x, 1, d - 4, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                for (int x = 1; x < w - 1; x += 2) b.barrel(x, 1, d - 2, LOOT_TAVERN);
                b.marker("npc", w / 2, 1, d - 3, npc("bartender"));
                for (int tx = 2; tx < w - 2; tx += 4) {
                    b.set(tx, 1, 3, st.fence);
                    b.set(tx, 2, 3, Blocks.SPRUCE_PRESSURE_PLATE);
                    b.stairs(tx - 1, 1, 3, Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM);
                    b.stairs(tx + 1, 1, 3, Blocks.SPRUCE_STAIRS, Direction.WEST, Half.BOTTOM);
                }
                b.marker("npc", 3, 1, 4, npc("patron"));
                b.marker("npc", w - 4, 1, 4, npc("patron"));
                b.marker("quest_board", 1, 2, 2, new CompoundTag());
            }
            case SHOP -> {
                for (int x = 2; x < w - 2; x++) b.set(x, 1, d - 4, st.frame == Blocks.AIR ? Blocks.OAK_PLANKS.defaultBlockState() : st.floor.defaultBlockState());
                b.barrel(1, 1, d - 2, LOOT_SHOP);
                b.barrel(w - 2, 1, d - 2, LOOT_SHOP);
                b.set(2, 1, d - 2, Blocks.BOOKSHELF);
                b.set(w - 3, 1, d - 2, Blocks.BOOKSHELF);
                b.marker("npc", w / 2, 1, d - 3, npc("shopkeeper"));
            }
            case SMITH -> {
                b.set(2, 1, d - 2, Blocks.ANVIL);
                b.set(3, 1, d - 2, Blocks.SMITHING_TABLE);
                b.set(w - 2, 1, d - 2, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlastFurnaceBlock.FACING, Direction.NORTH));
                b.set(w - 3, 1, d - 2, Blocks.GRINDSTONE);
                b.set(w - 2, 1, 2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
                b.chest(1, 1, 1, Direction.EAST, LOOT_SMITH);
                b.marker("npc", w / 2, 1, d - 3, npc("blacksmith"));
            }
            case INN -> {
                b.marker("npc", w / 2, 1, d - 3, npc("innkeeper"));
                for (int x = 1; x < w - 2; x += 2) b.bed(x, wallH + 1, d - 3, Blocks.WHITE_BED, Direction.SOUTH);
                b.set(2, 1, d - 3, Blocks.BARREL);
            }
            case MANSION -> {
                b.fill(2, 1, d - 2, w - 3, 1, d - 2, Blocks.BOOKSHELF);
                b.set(w / 2, 1, d / 2, Blocks.RED_CARPET);
                b.set(w / 2 - 1, 1, d / 2, Blocks.RED_CARPET);
                b.set(w / 2 + 1, 1, d / 2, Blocks.RED_CARPET);
                b.chest(1, wallH + 1, 1, Direction.EAST, LOOT_TREASURE);
                b.bed(2, wallH + 1, d - 3, Blocks.PURPLE_BED, Direction.SOUTH);
                b.marker("npc", w / 2, 1, d / 2 + 1, npc("noble"));
                b.marker("npc", 2, 1, 2, npc("guard"));
            }
            case WAREHOUSE -> {
                for (int x = 1; x < w - 1; x += 2)
                    for (int z = 2; z < d - 1; z += 3) {
                        b.barrel(x, 1, z, LOOT_WAREHOUSE);
                        if ((x + z) % 4 == 0) b.barrel(x, 2, z, LOOT_WAREHOUSE);
                    }
                b.marker("npc", w / 2, 1, 2, npc("dockworker"));
            }
            case DOJO -> {
                b.fill(1, 0, 1, w - 2, 0, d - 2, Blocks.BIRCH_PLANKS);
                for (int x = 2; x < w - 2; x += 3) b.set(x, 1, d - 2, Blocks.TARGET);
                b.marker("npc", w / 2, 1, d - 3, npc("master"));
                b.marker("npc", 3, 1, 4, npc("student"));
            }
        }
    }

    // ------------------------------------------------------------------ special buildings

    static void church(Builder b, BuildStyle st) {
        house(b, st, 1, Interior.HOME);
        int w = b.w, d = b.d;
        // clear the furniture placed by the generic interior and add pews + altar
        b.clear(1, 1, 1, w - 2, 3, d - 2);
        for (int z = 3; z < d - 4; z += 2) {
            b.stairs(2, 1, z, Blocks.SPRUCE_STAIRS, Direction.NORTH, Half.BOTTOM);
            b.stairs(w - 3, 1, z, Blocks.SPRUCE_STAIRS, Direction.NORTH, Half.BOTTOM);
        }
        b.set(w / 2, 1, d - 3, Blocks.CHISELED_STONE_BRICKS);
        b.set(w / 2, 2, d - 3, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
        b.set(w / 2, 1, d - 2, Blocks.BELL);
        // steeple
        b.fill(w / 2 - 1, 6, 0, w / 2 + 1, 12, 2, st.wall);
        b.clear(w / 2, 9, 0, w / 2, 10, 2);
        b.fill(w / 2 - 1, 13, 0, w / 2 + 1, 13, 2, st.roofSlab);
        b.set(w / 2, 14, 1, st.fence);
        b.marker("npc", w / 2, 1, d - 4, npc("priest"));
    }

    static void marinePost(Builder b) {
        BuildStyle st = BuildStyle.MARINE;
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, Blocks.STONE_BRICKS, 14);
        b.clear(0, 1, 0, w - 1, 12, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.SMOOTH_STONE);
        b.walls(0, 1, 0, w - 1, 8, d - 1, Blocks.WHITE_CONCRETE);
        b.fill(0, 5, 0, w - 1, 5, 0, Blocks.BLUE_CONCRETE);
        b.fill(0, 5, d - 1, w - 1, 5, d - 1, Blocks.BLUE_CONCRETE);
        b.fill(1, 4, 1, w - 2, 4, d - 2, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        b.flatRoof(0, 0, w - 1, d - 1, 9, Blocks.SMOOTH_STONE, Blocks.WHITE_CONCRETE_POWDER);
        for (int x = 2; x < w - 2; x += 3) {
            b.fill(x, 2, 0, x, 3, 0, Blocks.GLASS_PANE);
            b.fill(x, 6, 0, x, 7, 0, Blocks.GLASS_PANE);
            b.fill(x, 6, d - 1, x, 7, d - 1, Blocks.GLASS_PANE);
        }
        b.bigDoor(w / 2, 1, 0, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(w / 2 + 1, 2, -1, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.NORTH));
        b.set(w / 2 - 1, 2, 1, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.SOUTH));
        // holding cell
        b.fill(1, 1, d - 4, 4, 3, d - 4, Blocks.IRON_BARS);
        b.marker("cell", 2, 1, d - 2, new CompoundTag());
        // desk and records
        b.set(w - 3, 1, d - 3, Blocks.LECTERN);
        b.chest(w - 2, 1, d - 2, Direction.WEST, LOOT_MARINE);
        b.set(w - 2, 1, 1, Blocks.CARTOGRAPHY_TABLE);
        // flag pole
        b.fill(w - 1, 10, 0, w - 1, 15, 0, Blocks.IRON_BARS);
        b.set(w - 1, 15, -1, Blocks.BLUE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
        b.ladderUp(w - 2, 1, d - 2, 9, Direction.NORTH);
        b.hanging(w / 2, 3, d / 2, Blocks.LANTERN);
        b.hanging(w / 2, 8, d / 2, Blocks.LANTERN);
        b.marker("npc", w / 2, 1, 3, npc("marine"));
        b.marker("npc", w / 2 - 2, 1, d / 2, npc("marine"));
        b.marker("npc", w / 2, 5, d / 2, npc("marine_officer"));
        b.marker("bounty_board", w / 2 + 2, 2, 1, new CompoundTag());
    }

    static void watchtower(Builder b, BuildStyle st) {
        int h = 14;
        b.foundation(0, 0, 4, 4, st.foundation, 14);
        b.clear(0, 1, 0, 4, h + 4, 4);
        b.fill(0, 0, 0, 4, 0, 4, st.foundation);
        for (int y = 1; y <= h; y++) {
            b.set(0, y, 0, st.frame);
            b.set(4, y, 0, st.frame);
            b.set(0, y, 4, st.frame);
            b.set(4, y, 4, st.frame);
        }
        b.fill(0, h, 0, 4, h, 4, st.floor);
        b.walls(0, h + 1, 0, 4, h + 1, 4, st.fence);
        b.fill(0, h + 4, 0, 4, h + 4, 4, st.roofSlab);
        for (int y = h + 2; y <= h + 3; y++) {
            b.set(0, y, 0, st.fence);
            b.set(4, y, 0, st.fence);
            b.set(0, y, 4, st.fence);
            b.set(4, y, 4, st.fence);
        }
        b.fill(2, 1, 3, 2, h, 3, st.frame);
        b.ladderUp(2, 1, 2, h, Direction.NORTH);
        b.clear(2, h, 2, 2, h, 2);
        b.set(2, h + 3, 2, st.light.defaultBlockState().hasProperty(LanternBlock.HANGING) ? st.light.defaultBlockState().setValue(LanternBlock.HANGING, true) : st.light.defaultBlockState());
        b.marker("npc", 1, h + 1, 1, npc("lookout"));
    }

    static void lighthouse(Builder b, BuildStyle st) {
        b.foundation(0, 0, 6, 6, Blocks.STONE_BRICKS, 16);
        b.clear(0, 1, 0, 6, 24, 6);
        b.cylinder(3, 3, 0, 0, 3, Blocks.STONE_BRICKS, false);
        for (int y = 1; y <= 16; y++) b.cylinder(3, 3, y, y, 3, (y / 3) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.RED_CONCRETE, true);
        b.cylinder(3, 3, 17, 17, 3, Blocks.SMOOTH_STONE, false);
        b.set(3, 18, 3, Blocks.GLOWSTONE);
        b.set(3, 19, 3, Blocks.SEA_LANTERN);
        b.cylinder(3, 3, 18, 19, 2, Blocks.GLASS, true);
        b.cylinder(3, 3, 20, 20, 2, Blocks.SMOOTH_STONE_SLAB, false);
        b.ladderUp(3, 1, 1, 16, Direction.SOUTH);
        b.door(3, 1, 0, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.marker("npc", 3, 1, 3, npc("keeper"));
    }

    static void stall(Builder b, BuildStyle st) {
        b.foundation(0, 0, 4, 4, st.foundation, 6);
        b.fill(0, 0, 0, 4, 0, 4, st.floor);
        b.clear(0, 1, 0, 4, 4, 4);
        for (int[] c : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) b.fill(c[0], 1, c[1], c[0], 3, c[1], st.fence);
        BlockState[] wool = {Blocks.RED_WOOL.defaultBlockState(), Blocks.WHITE_WOOL.defaultBlockState()};
        for (int x = 0; x <= 4; x++) b.fill(x, 4, 0, x, 4, 4, wool[x % 2]);
        b.fill(1, 1, 1, 3, 1, 1, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        b.barrel(1, 1, 3, LOOT_SHOP);
        b.barrel(3, 1, 3, LOOT_SHOP);
        b.marker("npc", 2, 1, 2, npc("merchant"));
    }

    static void well(Builder b, BuildStyle st) {
        b.foundation(0, 0, 2, 2, st.foundation, 6);
        b.fill(0, -3, 0, 2, 0, 2, st.foundation);
        b.set(1, -2, 1, Blocks.WATER);
        b.set(1, -1, 1, Blocks.WATER);
        b.set(1, 0, 1, Blocks.WATER);
        b.clear(0, 1, 0, 2, 4, 2);
        for (int[] c : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) b.fill(c[0], 1, c[1], c[0], 2, c[1], st.fence);
        b.fill(0, 3, 0, 2, 3, 2, st.roofSlab);
        b.marker("npc", 1, 1, -1, npc("villager"));
    }

    static void plaza(Builder b, BuildStyle st) {
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, st.foundation, 8);
        b.fill(0, 0, 0, w - 1, 0, d - 1, st.foundation);
        b.clear(0, 1, 0, w - 1, 5, d - 1);
        // fountain
        int cx = w / 2, cz = d / 2;
        b.fill(cx - 2, 1, cz - 2, cx + 2, 1, cz + 2, Blocks.STONE_BRICK_WALL);
        b.fill(cx - 1, 1, cz - 1, cx + 1, 1, cz + 1, Blocks.WATER);
        b.set(cx, 1, cz, Blocks.STONE_BRICKS);
        b.set(cx, 2, cz, Blocks.STONE_BRICK_WALL);
        b.set(cx, 3, cz, Blocks.WATER);
        for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
            b.fill(c[0], 1, c[1], c[0], 2, c[1], st.fence);
            b.set(c[0], 3, c[1], Blocks.LANTERN);
        }
        b.marker("npc", 1, 1, cz, npc("villager"));
        b.marker("npc", w - 2, 1, cz, npc("villager"));
        b.marker("npc", cx, 1, 1, npc("child"));
        b.marker("town_center", cx, 1, cz, new CompoundTag());
    }

    static void farm(Builder b, BuildStyle st) {
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, Blocks.DIRT, 6);
        b.clear(0, 1, 0, w - 1, 3, d - 1);
        b.walls(0, 0, 0, w - 1, 0, d - 1, Blocks.OAK_LOG);
        for (int x = 1; x < w - 1; x++)
            for (int z = 1; z < d - 1; z++) {
                if (x == w / 2) {
                    b.set(x, 0, z, Blocks.WATER);
                    continue;
                }
                b.set(x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                Block crop = (x < w / 2) ? Blocks.WHEAT : Blocks.CARROTS;
                b.set(x, 1, z, crop.defaultBlockState().setValue(CropBlock.AGE, 3 + b.rand(x * 31 + z, 5)));
            }
        b.marker("npc", 0, 1, -1, npc("farmer"));
    }

    static void ruin(Builder b, BuildStyle st) {
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, Blocks.COBBLESTONE, 10);
        b.clear(0, 1, 0, w - 1, 8, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.MOSSY_COBBLESTONE);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (!edge) continue;
                int hgt = 1 + b.rand(x * 13 + z * 7, 4);
                for (int y = 1; y <= hgt; y++) b.set(x, y, z, b.rand(x + y * 3 + z, 3) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS);
            }
        b.set(w / 2, 1, d / 2, Blocks.COBWEB);
        b.chest(1, 1, d - 2, Direction.EAST, LOOT_RUIN);
        b.marker("npc", w / 2, 1, 2, npc("bandit"));
        b.marker("npc", w / 2 + 1, 1, d - 3, npc("bandit"));
    }

    static void bountyBoard(Builder b, BuildStyle st) {
        b.foundation(0, 0, 2, 2, st.foundation, 4);
        b.clear(0, 1, 0, 2, 3, 2);
        b.set(0, 1, 1, st.fence);
        b.set(2, 1, 1, st.fence);
        b.fill(0, 2, 1, 2, 3, 1, st.frame == Blocks.AIR ? Blocks.SPRUCE_PLANKS : Blocks.SPRUCE_PLANKS);
        b.fill(0, 4, 1, 2, 4, 1, st.roofSlab);
        b.marker("bounty_board", 1, 2, 0, new CompoundTag());
    }

    static void shipwright(Builder b, BuildStyle st) {
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, st.foundation, 14);
        b.clear(0, 1, 0, w - 1, 10, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.SPRUCE_PLANKS);
        // open-front shed
        for (int x = 0; x < w; x += 4) {
            b.fill(x, 1, d - 1, x, 6, d - 1, Blocks.SPRUCE_LOG);
            b.fill(x, 1, 0, x, 6, 0, Blocks.SPRUCE_LOG);
        }
        b.fill(0, 1, d - 1, w - 1, 6, d - 1, Blocks.SPRUCE_PLANKS);
        b.gableRoof(-1, -1, w, d, 7, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS);
        b.fill(2, 1, d / 2 - 1, w - 3, 1, d / 2 + 1, Blocks.SCAFFOLDING);
        b.set(1, 1, d - 2, Blocks.ANVIL);
        b.set(2, 1, d - 2, Blocks.CRAFTING_TABLE);
        b.set(3, 1, d - 2, Blocks.STONECUTTER);
        b.chest(w - 2, 1, d - 2, Direction.WEST, LOOT_WAREHOUSE);
        b.hanging(w / 2, 6, d / 2, Blocks.LANTERN);
        b.marker("npc", w / 2, 1, 2, npc("shipwright"));
        b.marker("ship_spawn", w / 2, 0, -8, new CompoundTag());
    }

    static void hut(Builder b, BuildStyle st) {
        b.foundation(0, 0, 4, 4, st.foundation, 8);
        b.clear(0, 1, 0, 4, 6, 4);
        b.fill(0, 0, 0, 4, 0, 4, st.floor);
        b.walls(0, 1, 0, 4, 3, 4, st.wall);
        for (int y = 1; y <= 3; y++) {
            b.set(0, y, 0, st.frame);
            b.set(4, y, 0, st.frame);
            b.set(0, y, 4, st.frame);
            b.set(4, y, 4, st.frame);
        }
        b.bigDoor(2, 1, 0, st.door, Direction.NORTH);
        b.set(2, 4, 0, st.frame);
        b.gableRoof(-1, -1, 5, 5, 4, st.roofStairs, st.roofSlab, st.wall);
        b.bed(1, 1, 2, Blocks.BROWN_BED, Direction.SOUTH);
        b.barrel(3, 1, 3, LOOT_HOUSE);
        b.marker("npc", 2, 1, 2, npc("resident"));
    }

    private Buildings() {}
}
