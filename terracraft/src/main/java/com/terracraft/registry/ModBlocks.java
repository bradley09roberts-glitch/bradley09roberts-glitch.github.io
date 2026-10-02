package com.terracraft.registry;

import com.terracraft.TerraCraft;
import com.terracraft.item.TerraBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Block registry with automatic block items. Content lives in {@code com.terracraft.content}. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TerraCraft.MODID);

    private ModBlocks() {}

    /** Registers a block plus a matching {@link BlockItem} in the given creative tab. */
    public static <T extends Block> RegistryObject<T> register(String name, TabGroup tab, Function<BlockBehaviour.Properties, T> factory,
                                                               Supplier<BlockBehaviour.Properties> properties) {
        return register(name, tab, factory, properties, UnaryOperator.identity());
    }

    public static <T extends Block> RegistryObject<T> register(String name, TabGroup tab, Function<BlockBehaviour.Properties, T> factory,
                                                               Supplier<BlockBehaviour.Properties> properties,
                                                               UnaryOperator<Item.Properties> itemProperties) {
        RegistryObject<T> block = BLOCKS.register(name, () -> factory.apply(properties.get().setId(BLOCKS.key(name))));
        RegistryObject<BlockItem> item = ModItems.ITEMS.register(name, () -> new TerraBlockItem(block.get(),
            itemProperties.apply(new Item.Properties().setId(ModItems.ITEMS.key(name)).useBlockDescriptionPrefix())));
        ModItems.addToTab(tab, item);
        return block;
    }

    /** Registers a block without an item (technical blocks). */
    public static <T extends Block> RegistryObject<T> registerNoItem(String name, Function<BlockBehaviour.Properties, T> factory,
                                                                     Supplier<BlockBehaviour.Properties> properties) {
        return BLOCKS.register(name, () -> factory.apply(properties.get().setId(BLOCKS.key(name))));
    }
}
