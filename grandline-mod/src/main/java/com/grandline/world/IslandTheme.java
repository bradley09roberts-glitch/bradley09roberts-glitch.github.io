package com.grandline.world;

import com.grandline.world.build.BuildStyle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Climate/terrain palette for an island. Biomes drive vanilla vegetation; blocks drive our terrain. */
public enum IslandTheme {
    TEMPERATE(Biomes.FOREST, Biomes.PLAINS, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.SAND, Blocks.STONE, BuildStyle.VILLAGE),
    MEADOW(Biomes.MEADOW, Biomes.PLAINS, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.SAND, Blocks.STONE, BuildStyle.VILLAGE),
    FLOWER(Biomes.FLOWER_FOREST, Biomes.SUNFLOWER_PLAINS, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.SAND, Blocks.STONE, BuildStyle.VILLAGE),
    TROPICAL(Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.SAND, Blocks.STONE, BuildStyle.JUNGLE),
    DESERT(Biomes.DESERT, Biomes.DESERT, Blocks.SAND, Blocks.SANDSTONE, Blocks.SAND, Blocks.SANDSTONE, BuildStyle.DESERT),
    BADLANDS(Biomes.WOODED_BADLANDS, Biomes.BADLANDS, Blocks.RED_SAND, Blocks.TERRACOTTA, Blocks.RED_SAND, Blocks.TERRACOTTA, BuildStyle.DESERT),
    SAVANNA(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.SAND, Blocks.STONE, BuildStyle.JUNGLE),
    SNOW(Biomes.SNOWY_TAIGA, Biomes.SNOWY_PLAINS, Blocks.SNOW_BLOCK, Blocks.DIRT, Blocks.GRAVEL, Blocks.STONE, BuildStyle.SNOW),
    TAIGA(Biomes.TAIGA, Biomes.TAIGA, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.GRAVEL, Blocks.STONE, BuildStyle.SNOW),
    SWAMP(Biomes.MANGROVE_SWAMP, Biomes.SWAMP, Blocks.MUD, Blocks.MUD, Blocks.MUD, Blocks.STONE, BuildStyle.JUNGLE),
    CHERRY(Biomes.CHERRY_GROVE, Biomes.CHERRY_GROVE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.SAND, Blocks.STONE, BuildStyle.WANO),
    DARK(Biomes.DARK_FOREST, Biomes.DARK_FOREST, Blocks.PODZOL, Blocks.DIRT, Blocks.GRAVEL, Blocks.DEEPSLATE, BuildStyle.GOTHIC),
    ROCKY(Biomes.WINDSWEPT_HILLS, Biomes.STONY_SHORE, Blocks.STONE, Blocks.STONE, Blocks.GRAVEL, Blocks.STONE, BuildStyle.STONE_TOWN),
    VOLCANIC(Biomes.BADLANDS, Biomes.BADLANDS, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.GRAVEL, Blocks.BLACKSTONE, BuildStyle.STONE_TOWN),
    CANDY(Biomes.FLOWER_FOREST, Biomes.MEADOW, Blocks.PINK_CONCRETE_POWDER, Blocks.WHITE_TERRACOTTA, Blocks.SAND, Blocks.PINK_TERRACOTTA, BuildStyle.CANDY),
    GIANT_FOREST(Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Blocks.PODZOL, Blocks.DIRT, Blocks.GRAVEL, Blocks.STONE, BuildStyle.GIANT),
    MUSHROOM(Biomes.MUSHROOM_FIELDS, Biomes.MUSHROOM_FIELDS, Blocks.MYCELIUM, Blocks.DIRT, Blocks.SAND, Blocks.STONE, BuildStyle.GOTHIC),
    FUTURE(Biomes.SNOWY_PLAINS, Biomes.PLAINS, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.SAND, Blocks.CALCITE, BuildStyle.FUTURE),
    ICE(Biomes.ICE_SPIKES, Biomes.SNOWY_PLAINS, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, BuildStyle.SNOW),
    WHITE_ROCK(Biomes.STONY_PEAKS, Biomes.STONY_SHORE, Blocks.CALCITE, Blocks.DIORITE, Blocks.SAND, Blocks.DIORITE, BuildStyle.STONE_TOWN),
    /** Island Cloud of the White Sea (blocks are swapped for grandline:island_cloud at generation time). */
    CLOUD(SkyBiomes.SKY_ISLAND, SkyBiomes.SKY_ISLAND, Blocks.WHITE_WOOL, Blocks.WHITE_WOOL, Blocks.WHITE_WOOL, Blocks.WHITE_WOOL, BuildStyle.CLOUD),
    /** Fish-Man Island: coral sand and reef rock under the bubble. */
    CORAL(SkyBiomes.FISHMAN_ISLAND, SkyBiomes.FISHMAN_ISLAND, Blocks.SAND, Blocks.SANDSTONE, Blocks.SAND, Blocks.PRISMARINE, BuildStyle.CORAL);

    /** Biome keys for the sky and deep-sea dimensions (kept here so enum constants can reference them). */
    public static final class SkyBiomes {
        public static final ResourceKey<Biome> SKY_ISLAND = ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.grandline.GrandLine.id("sky_island"));
        public static final ResourceKey<Biome> FISHMAN_ISLAND = ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, com.grandline.GrandLine.id("fishman_island"));
    }

    public final ResourceKey<Biome> wildBiome;
    public final ResourceKey<Biome> settledBiome;
    public final Block surface, subsurface, beach, stone;
    public final BuildStyle style;

    IslandTheme(ResourceKey<Biome> wildBiome, ResourceKey<Biome> settledBiome, Block surface, Block subsurface, Block beach, Block stone, BuildStyle style) {
        this.wildBiome = wildBiome;
        this.settledBiome = settledBiome;
        this.surface = surface;
        this.subsurface = subsurface;
        this.beach = beach;
        this.stone = stone;
        this.style = style;
    }
}
