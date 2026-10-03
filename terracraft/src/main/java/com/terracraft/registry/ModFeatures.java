package com.terracraft.registry;

import com.terracraft.TerraCraft;
import com.terracraft.world.gen.LootChestFeature;
import com.terracraft.world.gen.PairedOreFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** World generation feature types (the features themselves are datapack JSON). */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, TerraCraft.MODID);

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

    private ModFeatures() {}
}
