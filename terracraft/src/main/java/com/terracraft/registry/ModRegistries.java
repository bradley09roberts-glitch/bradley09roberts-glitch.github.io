package com.terracraft.registry;

import net.neoforged.bus.api.IEventBus;

/**
 * Central place that attaches every DeferredRegister to the mod bus.
 * New registry holder classes must be added here.
 */
public final class ModRegistries {
    private ModRegistries() {}

    public static void register(IEventBus modBus) {
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
        com.terracraft.player.TerraAttachments.register(modBus);
    }
}
