package com.terracraft.registry.content;

import com.terracraft.item.TerraRarity;
import com.terracraft.item.consumable.EventSummonItem;
import com.terracraft.item.consumable.PresentItem;
import com.terracraft.item.weapon.WeaponProperties;
import com.terracraft.registry.ModItems;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.TabGroup;
import com.terracraft.world.event.TerrariaEvents;

/**
 * The Frost Legion (Hardmode): Presents drop from enemies (more often in the snow and in late December), and one in
 * ten Hardmode presents holds the Snow Globe that calls the legion of snowmen.
 */
public final class FrostLegionContent {
    public static final RegistryObject<PresentItem> PRESENT = ModItems.register("present", TabGroup.CONSUMABLES, PresentItem::new,
        p -> WeaponProperties.stats(p.stacksTo(99), CoreItems.stats(TerraRarity.WHITE, 0)));
    public static final RegistryObject<EventSummonItem> SNOW_GLOBE = ModItems.register("snow_globe", TabGroup.CONSUMABLES,
        p -> new EventSummonItem(p, () -> TerrariaEvents.FROST_LEGION, true), p -> WeaponProperties.stats(p.stacksTo(20), CoreItems.stats(TerraRarity.PINK, 0)));

    private FrostLegionContent() {}

    public static void init() {}
}
