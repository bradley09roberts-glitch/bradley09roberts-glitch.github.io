package com.grandline.base;

import com.grandline.registry.ModBlocks;
import com.grandline.registry.ModCreativeTabs;
import com.grandline.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;

/**
 * Registration of crew bases: the crew flag (planted at the heart of an uninhabited island to claim it) and the base
 * chest (the crew's shared warehouse). Both go into the shared block and item registers; the asset pipeline reads the
 * block ids from this file too (tools/assetgen/source.py).
 */
public final class BaseRegistry {
    /** Planted at an island's heart, claims it for the crew; use it to open the base menu. */
    public static final DeferredBlock<Block> CREW_FLAG = ModBlocks.BLOCKS.register("crew_flag", () -> new CrewFlagBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).strength(-1f, 3600000f).noOcclusion().noLootTable().sound(SoundType.WOOD)));
    /** The crew's warehouse: every base chest in a base opens the same shared store. */
    public static final DeferredBlock<Block> BASE_CHEST = ModBlocks.BLOCKS.register("base_chest", () -> new BaseChestBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(2.5f, 1200f).sound(SoundType.WOOD)));

    public static final DeferredItem<Item> CREW_FLAG_ITEM = ModItems.ITEMS.register("crew_flag",
            () -> new CrewFlagItem(CREW_FLAG.get(), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BASE_CHEST_ITEM = ModItems.ITEMS.registerSimpleBlockItem(BASE_CHEST);

    /** Shop offers: shipwrights and general stores sell the crew flag; shipwrights the base chest. */
    public record Offer(String item, int count, long price) {}

    public static List<Offer> shopExtras(String role) {
        return switch (role) {
            case "shipwright" -> List.of(new Offer("grandline:crew_flag", 1, 8000), new Offer("grandline:base_chest", 1, 1500));
            case "merchant", "shopkeeper" -> List.of(new Offer("grandline:crew_flag", 1, 9000));
            default -> List.of();
        };
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(BaseRegistry::tab);
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != ModCreativeTabs.MAIN.getKey()) return;
        e.accept(CREW_FLAG_ITEM.get());
        e.accept(BASE_CHEST_ITEM.get());
    }

    private BaseRegistry() {}
}
