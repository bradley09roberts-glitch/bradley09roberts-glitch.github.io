package com.squidgame.client;

import com.mojang.serialization.Lifecycle;
import com.squidgame.world.ArenaWorld;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldStem;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.WorldData;

/** Decides whether Minecraft's "experimental settings" confirmation for a world is caused by this mod's dimension alone. */
public final class ExperimentalWorldWarning {
    private ExperimentalWorldWarning() {
    }

    public static boolean onlyBecauseOfArenaDimension(WorldStem stem) {
        try {
            WorldData data = stem.worldData();
            if (data.worldGenSettingsLifecycle() == Lifecycle.stable() || data.worldGenOptions().isOldCustomizedWorld()) {
                return false;
            }
            if (FeatureFlags.isExperimental(data.enabledFeatures())) {
                return false;
            }
            Registry<LevelStem> stems = stem.registries().compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
            return stems.containsKey(ResourceKey.create(Registries.LEVEL_STEM, ArenaWorld.DIMENSION.location()));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
