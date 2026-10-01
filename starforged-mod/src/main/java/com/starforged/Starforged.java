package com.starforged;

import com.mojang.logging.LogUtils;
import com.starforged.client.ClientSetup;
import com.starforged.event.CommonEvents;
import com.starforged.network.ModNetwork;
import com.starforged.registry.ModBlocks;
import com.starforged.registry.ModCreativeTabs;
import com.starforged.registry.ModEntities;
import com.starforged.registry.ModFeatures;
import com.starforged.registry.ModItems;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.registry.ModStructures;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Starforged - when the stars fall, legends are forged.
 */
@Mod(Starforged.MODID)
public final class Starforged {
    public static final String MODID = "starforged";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Starforged(FMLJavaModLoadingContext context) {
        var modBus = context.getModBusGroup();

        ModSounds.SOUNDS.register(modBus);
        com.starforged.sun.SunSounds.SOUNDS.register(modBus);
        com.starforged.moon.MoonSounds.SOUNDS.register(modBus);
        com.starforged.tempest.TempestSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        com.starforged.sun.SunBlocks.BLOCKS.register(modBus);
        com.starforged.moon.MoonBlocks.BLOCKS.register(modBus);
        com.starforged.tempest.TempestBlocks.BLOCKS.register(modBus);
        com.starforged.tempest.TempestBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        com.starforged.sun.SunEntities.ENTITIES.register(modBus);
        com.starforged.moon.MoonEntities.ENTITIES.register(modBus);
        com.starforged.tempest.TempestEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        com.starforged.sun.SunItems.ITEMS.register(modBus);
        com.starforged.moon.MoonItems.ITEMS.register(modBus);
        com.starforged.tempest.TempestItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModStructures.STRUCTURE_TYPES.register(modBus);
        ModStructures.PIECE_TYPES.register(modBus);
        ModFeatures.FEATURES.register(modBus);

        FMLCommonSetupEvent.getBus(modBus).addListener(this::commonSetup);
        EntityAttributeCreationEvent.BUS.addListener(ModEntities::registerAttributes);
        EntityAttributeCreationEvent.BUS.addListener(com.starforged.sun.SunEntities::registerAttributes);
        EntityAttributeCreationEvent.BUS.addListener(com.starforged.moon.MoonEntities::registerAttributes);
        EntityAttributeCreationEvent.BUS.addListener(com.starforged.tempest.TempestEntities::registerAttributes);
        SpawnPlacementRegisterEvent.BUS.addListener(ModEntities::registerSpawnPlacements);
        SpawnPlacementRegisterEvent.BUS.addListener(com.starforged.sun.SunEntities::registerSpawnPlacements);
        SpawnPlacementRegisterEvent.BUS.addListener(com.starforged.moon.MoonEntities::registerSpawnPlacements);
        SpawnPlacementRegisterEvent.BUS.addListener(com.starforged.tempest.TempestEntities::registerSpawnPlacements);

        context.registerConfig(ModConfig.Type.COMMON, StarforgedConfig.SPEC);

        ModNetwork.init();
        CommonEvents.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Starforged: the sky is watching.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
