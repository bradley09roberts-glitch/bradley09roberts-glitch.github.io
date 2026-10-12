package com.grandline.boss;

import com.grandline.registry.ModCreativeTabs;
import com.grandline.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Items of the boss fights, registered into the shared item register (the asset pipeline reads the ids from this
 * file: {@code ITEMS.register("…")}). Relics are not items of their own: they are named, tagged stacks of existing
 * weapons ({@link BossRewards#relic}).
 */
public final class BossItems {
    public static final DeferredItem<Item> HAKI_SCROLL = ModItems.ITEMS.register("haki_scroll",
            () -> new HakiScrollItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /** Loads the class (and so registers the items) during mod construction. */
    public static void register(IEventBus modBus) {
        modBus.addListener(BossItems::tab);
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != ModCreativeTabs.MAIN.getKey()) return;
        for (String k : new String[] {"observation", "armament", "conqueror"}) e.accept(BossRewards.hakiScroll(k));
    }

    private BossItems() {}
}
