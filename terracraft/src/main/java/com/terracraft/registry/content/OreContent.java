package com.terracraft.registry.content;

import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraRarity;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Terraria ores that Minecraft lacks. Copper, Iron and Gold reuse the vanilla ores and ingots (vanilla
 * redstone and building recipes keep working); Tin, Lead, Silver, Tungsten and Platinum are added here.
 * Ores drop raw metal which smelts into bars in a Furnace, exactly like vanilla metals.
 */
public final class OreContent {
    public static final List<Metal> METALS = new ArrayList<>();

    public static final Metal TIN = metal("tin", MapColor.TERRACOTTA_LIGHT_GRAY, TerraRarity.WHITE, 300);
    public static final Metal LEAD = metal("lead", MapColor.TERRACOTTA_BLUE, TerraRarity.WHITE, 600);
    public static final Metal SILVER = metal("silver", MapColor.METAL, TerraRarity.WHITE, 900);
    public static final Metal TUNGSTEN = metal("tungsten", MapColor.TERRACOTTA_GREEN, TerraRarity.WHITE, 1050);
    public static final Metal PLATINUM = metal("platinum", MapColor.SNOW, TerraRarity.WHITE, 1350);

    /** All blocks/items of one metal. */
    public record Metal(String name, RegistryObject<Block> ore, RegistryObject<Block> deepslateOre,
                        RegistryObject<TerraItem> raw, RegistryObject<TerraItem> bar) {}

    private OreContent() {}

    public static void init() {}

    private static Metal metal(String name, MapColor color, TerraRarity rarity, int barValue) {
        RegistryObject<Block> ore = ModBlocks.register(name + "_ore", TabGroup.BLOCKS, Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(3.0F, 3.0F).sound(SoundType.STONE));
        RegistryObject<Block> deepslate = ModBlocks.register("deepslate_" + name + "_ore", TabGroup.BLOCKS, Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
        RegistryObject<TerraItem> raw = ModItems.register("raw_" + name, TabGroup.MATERIALS, TerraItem::new,
            p -> WeaponProperties.stats(p, CoreItems.stats(rarity, barValue / 4)));
        RegistryObject<TerraItem> bar = ModItems.register(name + "_bar", TabGroup.MATERIALS, TerraItem::new,
            p -> WeaponProperties.stats(p, CoreItems.stats(rarity, barValue)));
        Metal metal = new Metal(name, ore, deepslate, raw, bar);
        METALS.add(metal);
        return metal;
    }
}
