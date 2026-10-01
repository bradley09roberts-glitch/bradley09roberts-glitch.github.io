package com.starforged.registry;

import com.starforged.Starforged;
import com.starforged.world.CraterFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Starforged.MODID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> METEOR_CRATER = FEATURES.register("meteor_crater",
        () -> new CraterFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> FLOATING_ISLAND = FEATURES.register("floating_island",
        () -> new com.starforged.moon.world.FloatingIslandFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {
    }
}
