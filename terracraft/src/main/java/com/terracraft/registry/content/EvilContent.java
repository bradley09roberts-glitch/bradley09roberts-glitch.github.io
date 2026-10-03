package com.terracraft.registry.content;

import com.terracraft.block.AltarBlock;
import com.terracraft.block.OrbBlock;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import com.terracraft.registry.RegistryObject;

/** Blocks of the Corruption and the Crimson (worldgen: {@code world.evil.EvilBiomeFeature}). */
public final class EvilContent {
    public static final RegistryObject<Block> EBONSTONE = stone("ebonstone", MapColor.COLOR_PURPLE);
    public static final RegistryObject<Block> CRIMSTONE = stone("crimstone", MapColor.CRIMSON_NYLIUM);
    public static final RegistryObject<Block> CORRUPT_GRASS = grass("corrupt_grass", MapColor.COLOR_PURPLE);
    public static final RegistryObject<Block> CRIMSON_GRASS = grass("crimson_grass", MapColor.COLOR_RED);
    public static final RegistryObject<Block> DEMONITE_ORE = ore("demonite_ore", MapColor.COLOR_PURPLE);
    public static final RegistryObject<Block> CRIMTANE_ORE = ore("crimtane_ore", MapColor.COLOR_RED);

    public static final RegistryObject<OrbBlock> SHADOW_ORB = ModBlocks.register("shadow_orb", TabGroup.BLOCKS, p -> new OrbBlock(p, false),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 6.0F).lightLevel(s -> 9).noOcclusion()
            .sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<OrbBlock> CRIMSON_HEART = ModBlocks.register("crimson_heart", TabGroup.BLOCKS, p -> new OrbBlock(p, true),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0F, 6.0F).lightLevel(s -> 9).noOcclusion()
            .sound(SoundType.SLIME_BLOCK).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<AltarBlock> DEMON_ALTAR = ModBlocks.register("demon_altar", TabGroup.BLOCKS, AltarBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(50.0F, 3600.0F).noOcclusion()
            .sound(SoundType.STONE).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<AltarBlock> CRIMSON_ALTAR = ModBlocks.register("crimson_altar", TabGroup.BLOCKS, AltarBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(50.0F, 3600.0F).noOcclusion()
            .sound(SoundType.STONE).pushReaction(PushReaction.BLOCK));
    public static final RegistryObject<Block> VILE_MUSHROOM = mushroom("vile_mushroom", MapColor.COLOR_PURPLE);
    public static final RegistryObject<Block> VICIOUS_MUSHROOM = mushroom("vicious_mushroom", MapColor.COLOR_RED);

    public static final RegistryObject<net.minecraft.world.level.block.RotatedPillarBlock> EBONWOOD = log("ebonwood", MapColor.COLOR_PURPLE);
    public static final RegistryObject<net.minecraft.world.level.block.RotatedPillarBlock> SHADEWOOD = log("shadewood", MapColor.COLOR_RED);
    public static final RegistryObject<Block> EBONWOOD_LEAVES = leaves("ebonwood_leaves", MapColor.COLOR_PURPLE);
    public static final RegistryObject<Block> SHADEWOOD_LEAVES = leaves("shadewood_leaves", MapColor.COLOR_RED);

    private EvilContent() {}

    private static RegistryObject<net.minecraft.world.level.block.RotatedPillarBlock> log(String name, MapColor color) {
        return ModBlocks.register(name, TabGroup.BLOCKS, net.minecraft.world.level.block.RotatedPillarBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(color).strength(2.0F).sound(SoundType.WOOD));
    }

    private static RegistryObject<Block> leaves(String name, MapColor color) {
        return ModBlocks.register(name, TabGroup.BLOCKS, Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(color).strength(0.2F).sound(SoundType.GRASS).noOcclusion()
                .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false));
    }

    public static void init() {}

    private static RegistryObject<Block> stone(String name, MapColor color) {
        return ModBlocks.register(name, TabGroup.BLOCKS, Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE));
    }

    private static RegistryObject<Block> grass(String name, MapColor color) {
        return ModBlocks.register(name, TabGroup.BLOCKS, Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(color).strength(0.6F).sound(SoundType.GRASS));
    }

    private static RegistryObject<Block> ore(String name, MapColor color) {
        return ModBlocks.register(name, TabGroup.BLOCKS, Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(3.0F, 3.0F).sound(SoundType.STONE).lightLevel(s -> 2));
    }

    private static RegistryObject<Block> mushroom(String name, MapColor color) {
        return ModBlocks.register(name, TabGroup.MATERIALS, com.terracraft.block.PlantBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(color).noCollision().instabreak().sound(SoundType.GRASS).noOcclusion()
                .pushReaction(PushReaction.DESTROY));
    }
}
