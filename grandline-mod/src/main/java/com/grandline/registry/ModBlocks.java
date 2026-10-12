package com.grandline.registry;

import com.grandline.GrandLine;
import com.grandline.block.FruitBloomBlock;
import com.grandline.block.PoneglyphBlock;
import com.grandline.block.SeastoneBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GrandLine.MODID);

    public static final DeferredBlock<Block> SEASTONE = BLOCKS.register("seastone", () -> new SeastoneBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN).strength(6f, 1200f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE)));
    public static final DeferredBlock<Block> SEASTONE_BRICKS = BLOCKS.register("seastone_bricks", () -> new SeastoneBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN).strength(6f, 1200f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS)));
    public static final DeferredBlock<Block> SEASTONE_ORE = BLOCKS.register("seastone_ore", () -> new SeastoneBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(4.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final DeferredBlock<Block> PONEGLYPH = BLOCKS.register("poneglyph", () -> new PoneglyphBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).strength(-1f, 3600000f).noLootTable().sound(SoundType.STONE)));
    public static final DeferredBlock<Block> FRUIT_BLOOM = BLOCKS.register("fruit_bloom", () -> new FruitBloomBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE).strength(0.6f).noOcclusion().sound(SoundType.AZALEA_LEAVES).lightLevel(s -> 6)));

    public static final DeferredBlock<Block> SHIP_HATCH = BLOCKS.register("ship_hatch", () -> new com.grandline.block.ShipHatchBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(-1f, 3600000f).noLootTable().sound(SoundType.WOOD)));

    public static final DeferredBlock<Block> ISLAND_CLOUD = BLOCKS.register("island_cloud", () -> new com.grandline.block.IslandCloudBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW).strength(0.4f).sound(SoundType.WOOL)));
    public static final DeferredBlock<Block> BUBBLE_MEMBRANE = BLOCKS.register("bubble_membrane", () -> new com.grandline.block.BubbleMembraneBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1f, 3600000f).noLootTable().noCollission().noOcclusion().sound(SoundType.HONEY_BLOCK)
            .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).lightLevel(s -> 4)));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        for (DeferredBlock<Block> b : java.util.List.of(SEASTONE, SEASTONE_BRICKS, SEASTONE_ORE, PONEGLYPH, FRUIT_BLOOM, SHIP_HATCH, ISLAND_CLOUD, BUBBLE_MEMBRANE)) {
            ModItems.ITEMS.registerSimpleBlockItem(b);
        }
    }

    private ModBlocks() {}
}
