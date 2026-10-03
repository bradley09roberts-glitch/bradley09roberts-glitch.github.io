package com.terracraft.registry;

import net.minecraftforge.eventbus.api.bus.BusGroup;

/**
 * Central place that attaches every DeferredRegister to the mod bus.
 * New registry holder classes must be added here.
 */
public final class ModRegistries {
    private ModRegistries() {}

    public static void register(BusGroup modBus) {
        com.terracraft.registry.content.TerraContent.init();
        ModDataComponents.COMPONENTS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModFeatures.FEATURES.register(modBus);
        ModFeatures.BIOME_SOURCES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModConditions.CONDITIONS.register(modBus);
    }
}
