package com.squidgame.registry;

import com.squidgame.SquidGameMod;
import com.squidgame.block.SquidBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Custom blocks (names and shapes are fixed by docs/ASSET_CONTRACT.md section 2.1). */
public final class ModBlocks {
    /** All registered blocks by path, in registration order (used by the creative tab and loot/tag generation). */
    public static final Map<String, Block> ALL = new LinkedHashMap<>();

    public static final String[] PASTEL_COLORS = {"pink", "mint", "yellow", "sky", "lilac", "peach", "cream"};
    private static final MapColor[] PASTEL_MAP = {MapColor.COLOR_PINK, MapColor.EMERALD, MapColor.COLOR_YELLOW,
            MapColor.COLOR_LIGHT_BLUE, MapColor.COLOR_PURPLE, MapColor.TERRACOTTA_ORANGE, MapColor.SAND};

    public static final Block BRIDGE_GLASS = reg("bridge_glass", p -> new SquidBlocks.BridgeGlassBlock(
            p.mapColor(MapColor.ICE).strength(-1.0f, 3600000.0f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, g, pos, t) -> false).isRedstoneConductor((s, g, pos) -> false)
                    .isSuffocating((s, g, pos) -> false).isViewBlocking((s, g, pos) -> false).noLootTable()));

    public static final Block CASH_BLOCK = plain("cash_block", MapColor.COLOR_LIGHT_GREEN, SoundType.WOOL, 0);
    public static final Block PANEL_LIGHT_WHITE = plain("panel_light_white", MapColor.QUARTZ, SoundType.GLASS, 15);
    public static final Block PANEL_LIGHT_WARM = plain("panel_light_warm", MapColor.COLOR_YELLOW, SoundType.GLASS, 15);
    public static final Block PANEL_LIGHT_PINK = plain("panel_light_pink", MapColor.COLOR_PINK, SoundType.GLASS, 15);
    public static final Block PLAYGROUND_GROUND = plain("playground_ground", MapColor.SAND, SoundType.SAND, 0);
    public static final Block TILE_PINK = plain("tile_pink", MapColor.COLOR_PINK, SoundType.STONE, 0);
    public static final Block TILE_WHITE = plain("tile_white", MapColor.QUARTZ, SoundType.STONE, 0);
    public static final Block TILE_BLACK = plain("tile_black", MapColor.COLOR_BLACK, SoundType.STONE, 0);
    public static final Block SYMBOL_CIRCLE = plain("symbol_circle", MapColor.COLOR_BLACK, SoundType.STONE, 0);
    public static final Block SYMBOL_TRIANGLE = plain("symbol_triangle", MapColor.COLOR_BLACK, SoundType.STONE, 0);
    public static final Block SYMBOL_SQUARE = plain("symbol_square", MapColor.COLOR_BLACK, SoundType.STONE, 0);

    public static final Block INVISIBLE_WALL = reg("invisible_wall", p -> new SquidBlocks.InvisibleWallBlock(
            p.mapColor(MapColor.NONE).strength(-1.0f, 3600000.0f).noOcclusion().noLootTable()
                    .isValidSpawn((s, g, pos, t) -> false).isRedstoneConductor((s, g, pos) -> false)
                    .isSuffocating((s, g, pos) -> false).isViewBlocking((s, g, pos) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    public static final Block MONITOR = reg("monitor", p -> new SquidBlocks.MonitorBlock(
            p.mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).lightLevel(s -> 9).noOcclusion().noLootTable()));

    public static final Block REGISTRATION_TERMINAL = reg("registration_terminal", p -> new SquidBlocks.RegistrationTerminalBlock(
            p.mapColor(MapColor.COLOR_PINK).strength(-1.0f, 3600000.0f).lightLevel(s -> 8).noOcclusion().noLootTable()));

    public static final Block DALGONA_STATION = reg("dalgona_station", p -> new SquidBlocks.DalgonaStationBlock(
            p.mapColor(MapColor.WOOD).strength(-1.0f, 3600000.0f).sound(SoundType.WOOD).noOcclusion().noLootTable()));

    public static final Block[] PASTEL = new Block[PASTEL_COLORS.length];
    public static final Block[] PASTEL_STAIRS = new Block[PASTEL_COLORS.length];
    public static final Block[] PASTEL_SLABS = new Block[PASTEL_COLORS.length];

    static {
        for (int i = 0; i < PASTEL_COLORS.length; i++) {
            String c = PASTEL_COLORS[i];
            Block base = plain("pastel_" + c, PASTEL_MAP[i], SoundType.STONE, 0);
            PASTEL[i] = base;
            PASTEL_STAIRS[i] = reg("pastel_" + c + "_stairs", p -> new StairBlock(base.defaultBlockState(),
                    p.mapColor(base.defaultMapColor()).strength(-1.0f, 3600000.0f).sound(SoundType.STONE).noLootTable()));
            PASTEL_SLABS[i] = reg("pastel_" + c + "_slab", p -> new SlabBlock(
                    p.mapColor(base.defaultMapColor()).strength(-1.0f, 3600000.0f).sound(SoundType.STONE).noLootTable()));
        }
    }

    private ModBlocks() {
    }

    private static Block plain(String id, MapColor color, SoundType sound, int light) {
        return reg(id, p -> new SquidBlocks.PlainBlock(
                p.mapColor(color).strength(-1.0f, 3600000.0f).sound(sound).lightLevel(s -> light).noLootTable()));
    }

    private static Block reg(String id, Function<BlockBehaviour.Properties, Block> factory) {
        Block b = factory.apply(BlockBehaviour.Properties.of());
        Registry.register(BuiltInRegistries.BLOCK, SquidGameMod.id(id), b);
        ALL.put(id, b);
        return b;
    }

    /** Blocks whose model needs the translucent render layer. */
    public static Block[] translucent() {
        return new Block[]{BRIDGE_GLASS};
    }

    /** Forces class loading (and therefore registration). */
    public static void init() {
        // static initialisers do the work; this also avoids "unused import" lint for Blocks
        assert Blocks.AIR != null;
    }
}
