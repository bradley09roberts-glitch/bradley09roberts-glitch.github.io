package com.grandline.ship;

import com.grandline.GrandLine;
import com.grandline.registry.ModCreativeTabs;
import com.grandline.registry.ModItems;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Naval combat items: the grappling hook (boarding and towing) and the special cannon shot. Registered into the
 * shared item register while the mod is constructed (event subscribers are class-initialised then), so the shared
 * registry classes stay untouched; the asset pipeline reads the ids from this file too.
 */
@EventBusSubscriber(modid = GrandLine.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NavalItems {
    public static final DeferredItem<Item> GRAPPLING_HOOK = ModItems.ITEMS.register("grappling_hook", () -> new GrapplingHookItem(new Item.Properties().stacksTo(1).durability(128)));
    /** Two balls joined by a chain: shreds rigging (the target slows down) but barely holes the hull. */
    public static final DeferredItem<Item> CHAIN_SHOT = ModItems.ITEMS.register("chain_shot", () -> new Item(new Item.Properties().stacksTo(32)));
    /** A canister of iron pellets: sweeps the enemy deck (anti-crew), little hull damage. */
    public static final DeferredItem<Item> GRAPE_SHOT = ModItems.ITEMS.register("grape_shot", () -> new Item(new Item.Properties().stacksTo(32)));

    @SubscribeEvent
    public static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != ModCreativeTabs.MAIN.getKey()) return;
        e.accept(GRAPPLING_HOOK.get());
        e.accept(CHAIN_SHOT.get());
        e.accept(GRAPE_SHOT.get());
    }

    private NavalItems() {}
}
