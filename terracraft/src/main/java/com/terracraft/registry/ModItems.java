package com.terracraft.registry;

import com.terracraft.TerraCraft;
import com.terracraft.item.DevTabletItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Item registry. Content is declared in category classes ({@code com.terracraft.content.*}) through
 * {@link #register}, which also files every item into its creative tab.
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, TerraCraft.MODID);
    private static final Map<TabGroup, List<Supplier<? extends Item>>> TAB_CONTENTS = new EnumMap<>(TabGroup.class);

    public static final RegistryObject<Item> DEV_TABLET = register("dev_tablet", TabGroup.DEV, DevTabletItem::new,
        p -> p.stacksTo(1).rarity(Rarity.EPIC));

    private ModItems() {}

    /**
     * Registers an item.
     *
     * @param name       registry path
     * @param tab        creative tab group
     * @param factory    item constructor
     * @param properties extra properties (the registry id is already set)
     */
    public static <T extends Item> RegistryObject<T> register(String name, TabGroup tab, Function<Item.Properties, T> factory,
                                                              UnaryOperator<Item.Properties> properties) {
        RegistryObject<T> object = ITEMS.register(name, () -> factory.apply(properties.apply(new Item.Properties().setId(ITEMS.key(name)))));
        addToTab(tab, object);
        return object;
    }

    public static <T extends Item> RegistryObject<T> register(String name, TabGroup tab, Function<Item.Properties, T> factory) {
        return register(name, tab, factory, UnaryOperator.identity());
    }

    public static void addToTab(TabGroup tab, Supplier<? extends Item> item) {
        TAB_CONTENTS.computeIfAbsent(tab, t -> new ArrayList<>()).add(item);
    }

    public static List<Supplier<? extends Item>> tabContents(TabGroup tab) {
        return TAB_CONTENTS.getOrDefault(tab, List.of());
    }
}
