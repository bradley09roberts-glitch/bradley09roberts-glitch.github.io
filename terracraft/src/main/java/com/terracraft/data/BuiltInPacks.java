package com.terracraft.data;

import com.terracraft.TerraCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Registers the nested {@code packs/vanilla_overrides} datapack: a required pack pinned to the top of the
 * pack list, so its replacements (disabled diamond/netherite/enchanting/brewing recipes, removed villages
 * and strongholds) always win over vanilla and Forge, in new and existing worlds alike.
 */
public final class BuiltInPacks {
    private BuiltInPacks() {}

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        Path root = ModList.getModFileById(TerraCraft.MODID).getFile().findResource("packs", "vanilla_overrides");
        PackLocationInfo info = new PackLocationInfo(TerraCraft.MODID + ":vanilla_overrides",
            Component.literal("TerraCraft vanilla overrides"), PackSource.BUILT_IN, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(root), PackType.SERVER_DATA,
            new PackSelectionConfig(true, Pack.Position.TOP, true));
        if (pack == null) {
            TerraCraft.LOGGER.error("Could not load the TerraCraft vanilla_overrides datapack from {}", root);
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
