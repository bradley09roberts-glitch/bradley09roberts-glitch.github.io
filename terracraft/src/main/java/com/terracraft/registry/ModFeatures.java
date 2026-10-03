package com.terracraft.registry;

import com.terracraft.TerraCraft;
import com.terracraft.world.gen.LootChestFeature;
import com.terracraft.world.gen.PairedOreFeature;
import net.minecraft.world.level.levelgen.feature.Feature;

/** World generation feature types (the features themselves are datapack JSON). */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(net.minecraft.core.registries.Registries.FEATURE, TerraCraft.MODID);

    public static final RegistryObject<PairedOreFeature> PAIRED_ORE = FEATURES.register("paired_ore", PairedOreFeature::new);
    public static final RegistryObject<LootChestFeature> LOOT_CHEST = FEATURES.register("loot_chest", LootChestFeature::new);

    public static final RegistryObject<com.terracraft.world.evil.EvilBiomeFeature> EVIL_BIOME = FEATURES.register("evil_biome",
        com.terracraft.world.evil.EvilBiomeFeature::new);

    public static final RegistryObject<com.terracraft.world.dungeon.DungeonFeature> DUNGEON = FEATURES.register("dungeon",
        com.terracraft.world.dungeon.DungeonFeature::new);

    public static final RegistryObject<com.terracraft.world.jungle.JungleFeature> JUNGLE = FEATURES.register("jungle",
        com.terracraft.world.jungle.JungleFeature::new);

    public static final RegistryObject<com.terracraft.world.underworld.UnderworldFeature> UNDERWORLD = FEATURES.register("underworld",
        com.terracraft.world.underworld.UnderworldFeature::new);

    /** Biome source types (registered on the same mod bus as the features). */
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.biome.BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(net.minecraft.core.registries.Registries.BIOME_SOURCE, TerraCraft.MODID);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.terracraft.world.biome.TerrariaBiomeSource>> TERRARIA_BIOMES =
        BIOME_SOURCES.register("terraria", () -> com.terracraft.world.biome.TerrariaBiomeSource.CODEC);

    private ModFeatures() {}
}
