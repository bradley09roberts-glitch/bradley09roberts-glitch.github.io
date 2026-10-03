package com.terracraft.data;

import com.terracraft.TerraCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;


/**
 * Registers the nested {@code packs/vanilla_overrides} datapack: a required pack pinned to the top of the
 * pack list, so its replacements (disabled diamond/netherite/enchanting/brewing recipes, removed villages
 * and strongholds) always win over vanilla and Forge, in new and existing worlds alike.
 */
public final class BuiltInPacks {
    private BuiltInPacks() {}

    public static void onAddPackFinders(AddPackFindersEvent event) {
        event.addPackFinders(TerraCraft.id("packs/vanilla_overrides"), PackType.SERVER_DATA,
            Component.literal("TerraCraft vanilla overrides"), PackSource.BUILT_IN, true, Pack.Position.TOP);
    }
}
