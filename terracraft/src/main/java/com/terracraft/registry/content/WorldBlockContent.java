package com.terracraft.registry.content;

import com.terracraft.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/** Blocks placed by world generation. */
public final class WorldBlockContent {
    /** The glowing heart found in caves; breaking it drops a Life Crystal. */
    public static final RegistryObject<Block> LIFE_CRYSTAL_BLOCK = ModBlocks.registerNoItem("life_crystal_block", Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5F).sound(SoundType.AMETHYST).lightLevel(s -> 10).noOcclusion());

    private WorldBlockContent() {}

    public static void init() {}
}
