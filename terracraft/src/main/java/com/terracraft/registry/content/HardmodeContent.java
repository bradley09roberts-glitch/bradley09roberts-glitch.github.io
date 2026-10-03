package com.terracraft.registry.content;

import com.terracraft.block.CraftingStationBlock;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Hardmode blocks and materials: the Hallow's blocks (see {@code world.hardmode.HardmodeWorld}), the Hardmode ores
 * blessed into the world by smashing altars, their bars, and the Hardmode crafting stations.
 */
public final class HardmodeContent {
    // ---------------------------------------------------------------- the Hallow
    public static final RegistryObject<Block> PEARLSTONE = ModBlocks.register("pearlstone", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE));
    public static final RegistryObject<Block> HALLOWED_GRASS = ModBlocks.register("hallowed_grass", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.6F).sound(SoundType.GRASS));
    public static final RegistryObject<Block> PEARLSAND = ModBlocks.register("pearlsand", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.5F).sound(SoundType.SAND));
    public static final RegistryObject<RotatedPillarBlock> PEARLWOOD = ModBlocks.register("pearlwood", TabGroup.BLOCKS, RotatedPillarBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0F).sound(SoundType.WOOD));
    public static final RegistryObject<Block> HALLOWED_LEAVES = ModBlocks.register("hallowed_leaves", TabGroup.BLOCKS, Block::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.2F).sound(SoundType.GRASS).noOcclusion()
            .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false));

    // ---------------------------------------------------------------- Hardmode ores (one of each pair per world)
    public static final List<Metal> METALS = new ArrayList<>();
    public static final Metal COBALT = metal("cobalt", MapColor.COLOR_BLUE, TerraRarity.LIGHT_RED, 2100);
    public static final Metal PALLADIUM = metal("palladium", MapColor.COLOR_ORANGE, TerraRarity.LIGHT_RED, 2600);
    public static final Metal MYTHRIL = metal("mythril", MapColor.COLOR_CYAN, TerraRarity.LIGHT_RED, 3300);
    public static final Metal ORICHALCUM = metal("orichalcum", MapColor.COLOR_MAGENTA, TerraRarity.LIGHT_RED, 3800);
    public static final Metal ADAMANTITE = metal("adamantite", MapColor.COLOR_RED, TerraRarity.LIGHT_RED, 4500);
    public static final Metal TITANIUM = metal("titanium", MapColor.METAL, TerraRarity.LIGHT_RED, 5000);

    public record Metal(String name, RegistryObject<Block> ore, RegistryObject<TerraItem> raw, RegistryObject<TerraItem> bar) {}

    // ---------------------------------------------------------------- Hardmode stations
    private static final VoxelShape ANVIL = Shapes.or(Block.box(2, 0, 3, 14, 4, 13), Block.box(5, 4, 5, 11, 9, 11), Block.box(0, 9, 2, 16, 15, 14));
    public static final RegistryObject<CraftingStationBlock> MYTHRIL_ANVIL = station("mythril_anvil", MapColor.COLOR_CYAN, ANVIL, 0);
    public static final RegistryObject<CraftingStationBlock> ORICHALCUM_ANVIL = station("orichalcum_anvil", MapColor.COLOR_MAGENTA, ANVIL, 0);
    public static final RegistryObject<CraftingStationBlock> ADAMANTITE_FORGE = station("adamantite_forge", MapColor.COLOR_RED,
        Block.box(0, 0, 1, 16, 14, 15), 13);
    public static final RegistryObject<CraftingStationBlock> TITANIUM_FORGE = station("titanium_forge", MapColor.METAL,
        Block.box(0, 0, 1, 16, 14, 15), 13);

    private HardmodeContent() {}

    public static void init() {}

    private static Metal metal(String name, MapColor color, TerraRarity rarity, int barValue) {
        RegistryObject<Block> ore = ModBlocks.register(name + "_ore", TabGroup.BLOCKS, Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.STONE));
        RegistryObject<TerraItem> raw = ModItems.register("raw_" + name, TabGroup.MATERIALS, TerraItem::new,
            p -> WeaponProperties.stats(p, CoreItems.stats(rarity, barValue / 4)));
        RegistryObject<TerraItem> bar = ModItems.register(name + "_bar", TabGroup.MATERIALS, TerraItem::new,
            p -> WeaponProperties.stats(p, CoreItems.stats(rarity, barValue)));
        Metal metal = new Metal(name, ore, raw, bar);
        METALS.add(metal);
        return metal;
    }

    private static RegistryObject<CraftingStationBlock> station(String name, MapColor color, VoxelShape shape, int light) {
        return ModBlocks.register(name, TabGroup.BLOCKS, p -> new CraftingStationBlock(p, shape),
            () -> BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(5.0F, 1200.0F).sound(SoundType.ANVIL)
                .noOcclusion().lightLevel(s -> light));
    }

    public static Metal byName(String name) {
        for (Metal metal : METALS) {
            if (metal.name().equals(name)) {
                return metal;
            }
        }
        throw new IllegalArgumentException(name);
    }
}
